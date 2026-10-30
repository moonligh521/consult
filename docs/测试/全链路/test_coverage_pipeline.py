# -*- coding: utf-8 -*-
import pathlib
import sys
import unittest
from contextlib import redirect_stdout
from io import StringIO

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from clinical_gold import (
    agreement_report,
    eligible_gold_rows,
    validate_clinical_gold,
)
from coverage_model import (
    AGE_BANDS,
    CLINICAL_CATEGORIES,
    COVERAGE_WEIGHTS,
    _engineering_coverage_status,
    coverage_report,
    enrich_coverage_fields,
    load_taxonomy,
    normalize_text,
    wilson_interval,
)
from quota_model import load_quota, validate_quota
from release_gates import evaluate_release_gates
from score_triage_cases import metrics, release_assessment
from rule_replica_v2 import Replica
from slang import EXPRESSION_METADATA, LEXICON_VERSION


class CoverageQuotaTest(unittest.TestCase):

    def test_quota_contract_has_seven_exclusive_lanes_and_200k_total(self):
        quota = load_quota()
        expected = {
            "catalog_routine": (50000, 137, True),
            "safety": (45000, 396, True),
            "population": (30000, 210, True),
            "expression": (30000, 330, True),
            "complex_multiturn": (25000, 180, True),
            "state_machine": (15000, 25, True),
            "capacity": (5000, 0, False),
        }
        actual = {
            lane["lane"]: (
                lane["quota"],
                lane["minimum_independent_intents"],
                lane["coverage_eligible"],
            )
            for lane in quota["lanes"]
        }
        self.assertEqual(expected, actual)
        self.assertEqual(200000, quota["total_quota"])
        self.assertEqual(200000, sum(lane["quota"] for lane in quota["lanes"]))
        self.assertAlmostEqual(1.0, sum(lane["ratio"] for lane in quota["lanes"]))
        self.assertEqual(quota, validate_quota(quota))

    def test_quota_validation_rejects_invalid_contracts(self):
        quota = load_quota()
        invalid_cases = []

        wrong_total = dict(quota)
        wrong_total["total_quota"] = 199999
        invalid_cases.append(wrong_total)

        duplicate_lane = dict(quota)
        duplicate_lane["lanes"] = [dict(lane) for lane in quota["lanes"]]
        duplicate_lane["lanes"][1]["lane"] = duplicate_lane["lanes"][0]["lane"]
        invalid_cases.append(duplicate_lane)

        eligible_capacity = dict(quota)
        eligible_capacity["lanes"] = [dict(lane) for lane in quota["lanes"]]
        eligible_capacity["lanes"][-1]["coverage_eligible"] = True
        invalid_cases.append(eligible_capacity)

        invalid_ratio = dict(quota)
        invalid_ratio["lanes"] = [dict(lane) for lane in quota["lanes"]]
        invalid_ratio["lanes"][0]["ratio"] = -0.1
        invalid_cases.append(invalid_ratio)

        invalid_minimum = dict(quota)
        invalid_minimum["lanes"] = [dict(lane) for lane in quota["lanes"]]
        invalid_minimum["lanes"][0]["minimum_independent_intents"] = 50001
        invalid_cases.append(invalid_minimum)

        for invalid in invalid_cases:
            with self.subTest(invalid=invalid):
                with self.assertRaises(ValueError):
                    validate_quota(invalid)


class CoverageModelTest(unittest.TestCase):

    def test_taxonomy_weights_are_complete(self):
        self.assertEqual(12, len(CLINICAL_CATEGORIES))
        self.assertAlmostEqual(1.0, sum(COVERAGE_WEIGHTS.values()))
        self.assertIn("40-59", AGE_BANDS)
        taxonomy = load_taxonomy()
        self.assertEqual(12, len(taxonomy["clinical_categories"]))
        self.assertEqual(137, taxonomy["targets"]["catalog_specialties"])
        self.assertNotIn("raw_rows", taxonomy["coverage_dimensions"])

    def test_normalize_text_collapses_demographic_and_duration_wrappers(self):
        self.assertEqual("胸痛", normalize_text("女，32岁，胸痛三天"))
        self.assertEqual("胸痛", normalize_text("男35岁 胸痛 3 天"))
        self.assertEqual("胸痛", normalize_text("胸痛半年。"))
        self.assertEqual("胸痛", normalize_text("胸痛半个月"))
        self.assertEqual("胸痛", normalize_text("医生您好，麻烦问下，胸痛三天"))

    def test_engineering_status_is_incomplete_when_forbidden_imbalance_remains(self):
        self.assertEqual(
            "INCOMPLETE",
            _engineering_coverage_status(
                {"catalog": 1.0, "safety": 1.0, "population": 1.0,
                 "expression": 1.0, "complexity": 1.0},
                {"all": []},
                [],
                ["二级科 呼吸内科 超限"],
            ),
        )

    def test_imbalance_rules_use_their_dedicated_lane_not_state_duplicates(self):
        expression_families = list(load_taxonomy()["expression_families"])
        rows = [
            {
                "suite": "S1", "input": f"常规主诉{index}",
                "primary_lane": "catalog_routine", "coverage_eligible": "1",
                "expression_family": "standard",
            }
            for index in range(100)
        ]
        rows.extend({
            "suite": "S21", "input": f"表达主诉{index}",
            "primary_lane": "expression", "coverage_eligible": "1",
            "expression_family": family,
        } for index, family in enumerate(expression_families))
        report = coverage_report(rows)
        self.assertFalse(any(
            warning.startswith("表达族")
            for warning in report["imbalance_warnings"]
        ))

    def test_normalize_text_removes_duration_before_trailing_punctuation(self):
        self.assertEqual(normalize_text("胸痛三天"), normalize_text("胸痛三天。"))
        self.assertEqual("胸痛", normalize_text("胸痛三天。"))

    def test_enrichment_assigns_stable_intent_and_population(self):
        first = enrich_coverage_fields({
            "input": "女，45岁，胸痛三天",
            "expect_safety_status": "URGENT",
            "safety_rule_id": "URG-BASE-001",
            "variant_tone": "TONE00-裸述",
            "slot_age": "45",
            "slot_gender": "女",
            "gold_specialty": "",
        })
        second = enrich_coverage_fields({
            "input": "男45岁胸痛3天",
            "expect_safety_status": "URGENT",
            "safety_rule_id": "URG-BASE-001",
            "variant_tone": "TONE00-裸述",
            "slot_age": "45",
            "slot_gender": "男",
            "gold_specialty": "",
        })
        self.assertEqual("cardiovascular", first["clinical_category"])
        self.assertEqual("40-59", first["population_group"])
        self.assertEqual(first["intent_id"], second["intent_id"])
        self.assertNotEqual(first["scenario_id"], second["scenario_id"])

    def test_coverage_counts_unique_intents_not_duplicate_rows(self):
        row = enrich_coverage_fields({
            "input": "女，32岁，胸痛三天",
            "expect_safety_status": "URGENT",
            "safety_rule_id": "URG-BASE-001",
            "variant_tone": "TONE00-裸述",
            "slot_age": "32",
            "slot_gender": "女",
            "gold_specialty": "",
            "annotator": "replica",
        })
        report = coverage_report([row, dict(row), dict(row)])
        self.assertEqual(3, report["raw_rows"])
        self.assertEqual(1, report["unique_intents"])
        self.assertEqual(1, report["effective_samples"])
        self.assertEqual("EXPERT_PRIOR_ONLY", report["coverage_status"])
        self.assertFalse(report["publishable"])
        self.assertNotIn("raw_rows", report["dimension_coverage"])

    def test_state_machine_transitions_are_distinct_effective_scenarios(self):
        base = {
            "input": "女，32岁，咳嗽三天",
            "primary_lane": "state_machine",
            "coverage_eligible": "1",
            "expression_family": "standard",
        }
        report = coverage_report([
            dict(base, action="CONFIRM", scenario="CONFIRM-AS-IS"),
            dict(base, action="ORDER", scenario="ORDER-BEFORE-CONFIRM"),
        ])
        self.assertEqual(2, report["effective_samples"])
        self.assertEqual(1, report["max_duplicate_cluster_size"])

    def test_population_clusters_distinguish_age_band_scenarios_not_wrappers(self):
        base = {
            "input": "咳嗽三天",
            "primary_lane": "population",
            "coverage_eligible": "1",
            "expression_family": "standard",
            "slot_gender": "女",
        }
        report = coverage_report([
            dict(base, slot_age="8", population_group="child"),
            dict(base, input="医生您好，咳嗽三天",
                 slot_age="8", population_group="child"),
            dict(base, slot_age="45", population_group="40-59"),
        ])
        self.assertEqual(2, report["effective_samples"])
        self.assertEqual(2, report["max_duplicate_cluster_size"])

    def test_coverage_excludes_capacity_from_coverage_and_clinical_evidence(self):
        eligible = {
            "input": "胸痛",
            "intent_id": "I-eligible",
            "scenario_id": "S-eligible",
            "primary_lane": "safety",
            "coverage_eligible": "1",
            "clinical_category": "cardiovascular",
            "expression_family": "standard",
            "slot_age": "45",
            "slot_gender": "男",
            "gold_specialty": "心血管内科",
        }
        capacity = {
            "input": "呼吸困难",
            "intent_id": "I-capacity",
            "scenario_id": "S-capacity",
            "primary_lane": "capacity",
            "coverage_eligible": "0",
            "clinical_category": "respiratory",
            "expression_family": "dialect",
            "slot_age": "80",
            "slot_gender": "女",
            "gold_specialty": "呼吸内科",
            "annotator": "clinician",
            "review_status": "REVIEWED",
            "source_kind": "real-traffic-deidentified",
        }
        report = coverage_report([eligible, capacity])
        self.assertEqual(2, report["raw_rows"])
        self.assertEqual(1, report["eligible_rows"])
        self.assertEqual(1, report["unique_intents"])
        self.assertEqual(1, report["dimensions"]["safety"])
        self.assertEqual(0, report["clinical_gold_rows"])
        self.assertEqual(0, report["real_traffic_rows"])
        self.assertFalse(report["publishable"])
        self.assertEqual({"safety": 1, "capacity": 1}, report["lane_counts"])

    def test_coverage_reports_trajectories_duplicates_demographics_and_cross_gaps(self):
        rows = [
            {
                "input": "更喘了",
                "prior_input": "咳嗽|开始喘",
                "turn": "3",
                "intent_id": "I-1",
                "scenario_id": "S-1",
                "primary_lane": "complex_multiturn",
                "coverage_eligible": "1",
                "clinical_category": "respiratory",
                "expression_family": "multiturn",
                "complexity_type": "chronic_acute_exacerbation",
                "slot_age": "45",
                "slot_gender": "女",
            },
            {
                "input": "更喘了",
                "prior_input": "咳嗽|开始喘",
                "turn": "3",
                "intent_id": "I-1",
                "scenario_id": "S-1",
                "primary_lane": "complex_multiturn",
                "coverage_eligible": "1",
                "clinical_category": "respiratory",
                "expression_family": "multiturn",
                "complexity_type": "chronic_acute_exacerbation",
                "age_band": "40-59",
                "gender": "女",
            },
        ]
        report = coverage_report(rows)
        self.assertEqual(1, report["unique_multiturn_trajectories"])
        self.assertEqual(2, report["max_duplicate_cluster_size"])
        self.assertEqual(1.0, report["max_duplicate_cluster_share"])
        self.assertEqual({"40-59": 2}, report["age_distribution"])
        self.assertEqual({"女": 2}, report["gender_distribution"])
        self.assertTrue(report["imbalance_warnings"])
        self.assertIn("safety_category_expression", report["taxonomy_cross_cell_gaps"])
        self.assertEqual(
            2 / 25000,
            report["lane_completion"]["complex_multiturn"],
        )
        self.assertTrue(report["quota_anomalies"])
        self.assertEqual("INCOMPLETE", report["engineering_coverage_status"])
        self.assertEqual("EXPERT_PRIOR_ONLY", report["clinical_coverage_status"])

    def test_coverage_excludes_unknown_specialties_and_complexity_types(self):
        report = coverage_report([
            {
                "suite": "S2-规格金标137",
                "input": "咳嗽",
                "gold_specialty": "呼吸内科",
                "complexity_type": "single_typical",
            },
            {
                "suite": "S41-复杂临床",
                "input": "虚构症状",
                "gold_specialty": "火星医学科",
                "complexity_type": "invented_complexity",
            },
        ])
        self.assertEqual(1, report["dimensions"]["catalog"])
        self.assertEqual(1, report["dimensions"]["complexity"])
        self.assertEqual(["火星医学科"], report["unknown_specialties"])
        self.assertEqual(["invented_complexity"], report["unknown_complexity_types"])

    def test_score_metrics_excludes_capacity_from_all_top1_denominators(self):
        base = {
            "suite": "S1",
            "countable": "1",
            "synthetic_mapping_only": "0",
            "gold_department": "内科",
            "expect_department": "内科",
            "expect_status_name": "卡片待确认",
            "expect_route_reason": "CANON_TERM",
            "node": "N6",
            "gold_specialty": "呼吸内科",
            "expect_specialty": "呼吸内科",
            "gold_safety_status": "PASS",
            "expect_safety_status": "PASS",
        }
        eligible = dict(
            base,
            case_id="eligible",
            input="咳嗽",
            gold_hit="1",
            primary_lane="catalog_routine",
            coverage_eligible="1",
        )
        capacity = dict(
            base,
            case_id="capacity",
            input="胸痛",
            gold_hit="0",
            primary_lane="capacity",
            coverage_eligible="0",
        )
        output = StringIO()
        with redirect_stdout(output):
            metrics([eligible, capacity])
        text = output.getvalue()
        self.assertIn("出卡且计入分母的行 1，二级科命中 1（Top1 100.00%）", text)
        self.assertIn("S1", text)
        self.assertIn("行数      2  计入      1  Top1 100.00%", text)

    def test_wilson_interval_contains_observed_rate(self):
        low, high = wilson_interval(95, 100)
        self.assertLess(low, 0.95)
        self.assertGreater(high, 0.95)

    def test_expression_lexicon_is_versioned_and_tracks_provenance(self):
        self.assertRegex(LEXICON_VERSION, r"^\d{4}\.\d{2}\.\d{2}$")
        for family in ("pinyin", "english", "typo", "dialect", "asr", "family_report"):
            self.assertIn(family, EXPRESSION_METADATA)
            self.assertIn("source", EXPRESSION_METADATA[family])
            self.assertIn("review_status", EXPRESSION_METADATA[family])
            self.assertIn("region", EXPRESSION_METADATA[family])


class ClinicalGoldTest(unittest.TestCase):

    def test_gold_requires_two_reviewers_and_adjudication_for_disagreement(self):
        row = {
            "case_id": "C1",
            "clinical_reviewer": "doctor-a",
            "reviewer_secondary": "doctor-b",
            "review_date": "2026-10-29",
            "gold_safety_status": "URGENT",
            "gold_action": "EMERGENCY",
            "acceptable_specialties": "急诊科",
            "key_evidence": "胸痛",
            "prohibited_outputs": "不用急诊",
            "reviewer_primary_label": "URGENT",
            "reviewer_secondary_label": "PASS",
            "adjudicator": "",
            "adjudicated_label": "",
        }
        errors = validate_clinical_gold(row)
        self.assertIn("分歧样本必须由第三人裁决", errors)
        row["adjudicator"] = "doctor-c"
        row["adjudicated_label"] = "URGENT"
        self.assertEqual([], validate_clinical_gold(row))
        row["review_status"] = "ADJUDICATED"
        row["annotator"] = "clinician"
        self.assertEqual([row], eligible_gold_rows([row]))

    def test_agreement_report_computes_kappa(self):
        report = agreement_report([
            {"reviewer_primary_label": "URGENT", "reviewer_secondary_label": "URGENT"},
            {"reviewer_primary_label": "PASS", "reviewer_secondary_label": "PASS"},
            {"reviewer_primary_label": "URGENT", "reviewer_secondary_label": "PASS"},
        ])
        self.assertEqual(3, report["double_reviewed"])
        self.assertAlmostEqual(2 / 3, report["observed_agreement"])
        self.assertLess(report["cohen_kappa"], 1.0)

    def test_release_review_is_incomplete_when_any_reviewed_clinical_row_is_invalid(self):
        valid = {
            "case_id": "valid",
            "annotator": "clinician",
            "review_status": "REVIEWED",
            "clinical_reviewer": "doctor-a",
            "reviewer_secondary": "doctor-b",
            "review_date": "2026-10-29",
            "gold_safety_status": "PASS",
            "gold_action": "ROUTE",
            "acceptable_specialties": "呼吸内科",
            "key_evidence": "咳嗽",
            "prohibited_outputs": "确诊",
            "reviewer_primary_label": "PASS",
            "reviewer_secondary_label": "PASS",
        }
        invalid = dict(valid, case_id="invalid", reviewer_secondary="")
        output = StringIO()
        with redirect_stdout(output):
            release_assessment([valid, invalid])
        self.assertIn("'review_complete': False", output.getvalue())


class ReleaseGateTest(unittest.TestCase):

    def test_safety_gate_fails_when_upper_confidence_bound_exceeds_limit(self):
        result = evaluate_release_gates({
            "urgent_reference": 100,
            "urgent_missed": 1,
            "danger_underestimated": 0,
            "danger_reference": 100,
            "wrong_booking_reference": 100,
            "wrong_booking_blocked": 100,
            "change_reference": 100,
            "change_responded": 100,
            "clinical_gold_rows": 500,
            "clinical_kappa": 0.85,
            "clinical_review_complete": True,
        })
        self.assertFalse(result["passed"])
        self.assertIn("high_risk_miss_rate", result["failed_gates"])

    def test_release_requires_signed_gate_version_even_when_metrics_pass(self):
        metrics = {
            "urgent_reference": 1000,
            "urgent_missed": 0,
            "danger_underestimated": 0,
            "danger_reference": 1000,
            "wrong_booking_reference": 1000,
            "wrong_booking_blocked": 1000,
            "change_reference": 1000,
            "change_responded": 1000,
            "clinical_gold_rows": 500,
            "clinical_kappa": 0.85,
            "clinical_review_complete": True,
        }
        unsigned = evaluate_release_gates(metrics)
        self.assertIn("governance_signatures", unsigned["failed_gates"])
        signed = evaluate_release_gates(metrics, {
            "high_risk_miss_rate_max": 0.01,
            "danger_underestimate_rate_max": 0.01,
            "wrong_booking_block_rate_min": 0.95,
            "change_response_rate_min": 0.95,
            "clinical_gold_rows_min": 500,
            "clinical_kappa_min": 0.80,
            "status": "SIGNED",
            "medical_owner": "medical-owner",
            "product_safety_owner": "safety-owner",
            "signed_at": "2026-10-29",
        })
        self.assertTrue(signed["passed"])


class ReplicaInformalModelTest(unittest.TestCase):
    """规则 NO_HIT 后必须走进词表模型，而不是直接 MODEL_UNAVAILABLE。"""

    @classmethod
    def setUpClass(cls):
        cls.replica = Replica()

    def test_pinyin_cough_uses_model_slot(self):
        result = self.replica.run("kesou三天", gender=2, age=32)
        self.assertEqual("NO_HIT", result["route_rule_first_pass"]["reason"])
        self.assertEqual("MODEL", result["route"]["source"])
        self.assertEqual("呼吸内科", result["route"]["specialty"])
        self.assertNotEqual("MODEL_UNAVAILABLE", result["route"]["reason"])

    def test_english_cough_uses_model_slot(self):
        result = self.replica.run("cough three days", gender=2, age=32)
        self.assertEqual("呼吸内科", result["route"]["specialty"])
        self.assertEqual("MODEL", result["route"]["source"])

    def test_dialect_headache_uses_model_slot(self):
        result = self.replica.run("脑壳痛三天", gender=2, age=32)
        self.assertEqual("神经内科", result["route"]["specialty"])

    def test_unknown_latin_goes_to_model_slot(self):
        from complaint_llm import live_enabled
        result = self.replica.run("asdfghjklqwerty", gender=2, age=32)
        self.assertEqual("NO_HIT", result["route_rule_first_pass"]["reason"])
        self.assertNotEqual("RULE", result["route"]["source"])
        if not live_enabled():
            self.assertEqual("MODEL_UNAVAILABLE", result["route"]["reason"])
            self.assertEqual("全科", result["route"]["specialty"])

    def test_unlisted_english_typo_uses_live_model(self):
        """词表没有的英文错字必须进大模型，且能定科时不要落全科。"""
        from complaint_llm import live_enabled
        result = self.replica.run("fevr three days", gender=2, age=32)
        self.assertEqual("NO_HIT", result["route_rule_first_pass"]["reason"])
        self.assertNotEqual("RULE", result["route"]["source"])
        if live_enabled():
            self.assertEqual("MODEL", result["route"]["source"])
            self.assertEqual("deepseek-v4-flash", result["route"].get("model_name"))
            self.assertNotEqual("全科", result["route"]["specialty"])


class ComplaintLlmParseTest(unittest.TestCase):
    def test_illegal_terms_are_stripped_specialty_kept(self):
        from complaint_llm import _parse
        got = _parse(
            '{"terms":[{"term":"咳嗽","evidence":"kesou"}],"specialty":"呼吸内科","specialtyEvidence":"kesou"}',
            ["哮喘"],
            ["呼吸内科", "全科"],
            "kesou三天",
        )
        self.assertIsNotNone(got)
        self.assertEqual([], got["terms"])
        self.assertEqual("呼吸内科", got["specialty"])

    def test_missing_term_evidence_keeps_specialty(self):
        from complaint_llm import _parse
        got = _parse(
            '{"terms":[{"term":"哮喘"}],"specialty":"呼吸内科"}',
            ["哮喘"],
            ["呼吸内科", "全科"],
            "kesou三天",
        )
        self.assertIsNotNone(got)
        self.assertEqual([], got["terms"])
        self.assertEqual("呼吸内科", got["specialty"])

    def test_missing_terms_array_keeps_specialty(self):
        import inspect
        from complaint_llm import MAX_TOKENS, TIMEOUT_SEC, _chat, _parse
        self.assertEqual(2000, MAX_TOKENS)
        self.assertEqual(30, TIMEOUT_SEC)
        self.assertIn("json_object", inspect.getsource(_chat))
        got = _parse(
            '{"specialty":"呼吸内科","specialtyEvidence":"kesou"}',
            ["哮喘"],
            ["呼吸内科", "全科"],
            "kesou三天",
        )
        self.assertIsNotNone(got)
        self.assertEqual([], got["terms"])
        self.assertEqual("呼吸内科", got["specialty"])
        self.assertEqual("kesou", got["evidence"])


class ComplaintPromptTest(unittest.TestCase):
    def test_shared_prompt_forbids_symptom_and_avoids_general(self):
        from complaint_llm import PROMPT_PATH, PROMPT_VERSION
        text = PROMPT_PATH.read_text(encoding="utf-8")
        self.assertEqual("complaint-v3.1", PROMPT_VERSION)
        self.assertIn("不是病名", text)
        self.assertIn("不要选全科", text)
        self.assertIn("kesou", text)
        self.assertIn("脑壳痛", text)
        self.assertIn("咳漱", text)
        self.assertIn('"term"', text)
        self.assertIn("方言", text)


if __name__ == "__main__":
    unittest.main()
