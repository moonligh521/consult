package com.moonligh.consult.agent.domain.department;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CatalogSeedGateTest {

    @Test
    void untouchedSpecialtyMissingFromDatabaseBlocksPublish() {
        DepartmentSnapshot seed = DepartmentSeed.load();
        DepartmentSnapshot drifted = seed.withoutSpecialty("针灸科");

        var gaps = CatalogSeedGate.missingUntouched(seed, drifted.enabled(), Set.of());

        assertTrue(gaps.stream().anyMatch(item -> item.contains("针灸科")));
    }

    @Test
    void adminPatchedSpecialtyMayDifferFromSeed() {
        DepartmentSnapshot seed = DepartmentSeed.load();
        DepartmentSnapshot drifted = seed.withoutSpecialty("针灸科");
        SpecialtyRef ref = seed.refOfSpecialty("针灸科");

        var gaps = CatalogSeedGate.missingUntouched(
                seed, drifted.enabled(), Set.of(ref.departmentCode() + "\n针灸科"));

        assertTrue(gaps.stream().noneMatch(item -> item.contains("针灸科")));
    }

    @Test
    void matchingCatalogHasNoGaps() {
        DepartmentSnapshot seed = DepartmentSeed.load();

        assertTrue(CatalogSeedGate.missingUntouched(seed, seed.enabled(), Set.of()).isEmpty());
    }

    @Test
    void disabledSpecialtyIsPresentNotDrift() {
        DepartmentSnapshot seed = DepartmentSeed.load();

        var gaps = CatalogSeedGate.missingUntouched(seed, disableSpecialty(seed.enabled(), "针灸科"), Set.of());

        assertTrue(gaps.stream().noneMatch(item -> item.contains("针灸科")));
    }

    @Test
    void disabledDepartmentIsPresentNotDrift() {
        DepartmentSnapshot seed = DepartmentSeed.load();
        SpecialtyRef ref = seed.refOfSpecialty("针灸科");

        var gaps = CatalogSeedGate.missingUntouched(
                seed, disableDepartment(seed.enabled(), ref.departmentCode()), Set.of());

        assertTrue(gaps.stream().noneMatch(item -> item.contains("针灸科")));
    }

    @Test
    void renamedSpecialtyWithDepartmentTouchIsNotDrift() {
        DepartmentSnapshot seed = DepartmentSeed.load();
        SpecialtyRef ref = seed.refOfSpecialty("针灸科");
        List<DepartmentRecord> renamed = renameSpecialty(seed.enabled(), "针灸科", "针灸推拿科");

        var gaps = CatalogSeedGate.missingUntouched(
                seed, renamed, Set.of(ref.departmentCode() + "\n针灸推拿科"));

        assertTrue(gaps.stream().noneMatch(item -> item.contains("针灸科")));
    }

    private static List<DepartmentRecord> disableSpecialty(List<DepartmentRecord> records, String name) {
        List<DepartmentRecord> copy = new ArrayList<>();
        for (DepartmentRecord rec : records) {
            List<DepartmentSpecialty> specs = new ArrayList<>();
            for (DepartmentSpecialty spec : rec.specialties()) {
                if (name.equals(spec.name())) {
                    specs.add(new DepartmentSpecialty(
                            spec.id(), spec.name(), spec.treatDesc(), false,
                            spec.routingKeywords(), spec.diseaseTerms(), spec.audience()));
                } else {
                    specs.add(spec);
                }
            }
            copy.add(rec.withSpecialties(specs));
        }
        return copy;
    }

    private static List<DepartmentRecord> disableDepartment(List<DepartmentRecord> records, int code) {
        List<DepartmentRecord> copy = new ArrayList<>();
        for (DepartmentRecord rec : records) {
            if (rec.code() != code) {
                copy.add(rec);
                continue;
            }
            copy.add(new DepartmentRecord(
                    rec.code(), rec.name(), false, rec.sort(), rec.fallback(), rec.genderScope(),
                    rec.childAgeLt(), rec.routingKeywords(), rec.overrideKeywords(), rec.treatDesc(),
                    rec.specialties()));
        }
        return copy;
    }

    private static List<DepartmentRecord> renameSpecialty(
            List<DepartmentRecord> records, String from, String to) {
        List<DepartmentRecord> copy = new ArrayList<>();
        for (DepartmentRecord rec : records) {
            List<DepartmentSpecialty> specs = new ArrayList<>();
            for (DepartmentSpecialty spec : rec.specialties()) {
                if (from.equals(spec.name())) {
                    specs.add(new DepartmentSpecialty(
                            spec.id(), to, spec.treatDesc(), spec.enabled(),
                            spec.routingKeywords(), spec.diseaseTerms(), spec.audience()));
                } else {
                    specs.add(spec);
                }
            }
            copy.add(rec.withSpecialties(specs));
        }
        return copy;
    }
}
