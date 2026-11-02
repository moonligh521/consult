package com.moonligh.consult.agent.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.DepartmentSpecialty;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.department.SpecialtyRef;
import com.moonligh.consult.agent.domain.triage.CanonResult;
import com.moonligh.consult.agent.domain.triage.CanonTerm;
import com.moonligh.consult.agent.domain.triage.Certainty;
import com.moonligh.consult.agent.domain.triage.ComplaintCanon;
import com.moonligh.consult.agent.domain.triage.DecisionSource;
import com.moonligh.consult.agent.domain.triage.RouteDecision;
import com.moonligh.consult.agent.domain.triage.RouteHit;
import com.moonligh.consult.agent.domain.triage.RouteInput;
import com.moonligh.consult.agent.domain.triage.Span;
import com.moonligh.consult.agent.domain.triage.SymptomHit;

/**
 * 按目录关键词推荐二级挂号科。一级只是分类。
 * 明确中医诉求优先；儿科按年龄或儿童标记后在儿科二级里再分；其余谁先出现跟谁。
 * 对不上时落到全科。不能覆盖急症分流的结论。
 */
public class DepartmentRouter {

    private static final Pattern AGE = Pattern.compile("(\\d{1,3})岁");

    private final DepartmentCatalog catalog;

    public DepartmentRouter() {
        this(MemoryDepartmentCatalog.seed());
    }

    public DepartmentRouter(DepartmentCatalog catalog) {
        this.catalog = catalog;
    }

    /** 推荐科室，并带上命中的关键词。年龄、性别从描述里抽。 */
    public Advice advise(String description) {
        return advise(description, null, null);
    }

    /** 推荐二级挂号科。会话里已有的年龄、性别优先于从描述再抽一次。 */
    public Advice advise(String description, Integer age, Gender gender) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        if (description == null || description.isBlank()) {
            return fallbackAdvice(snapshot.forAge(age));
        }
        String haystack = SpokenNormalizer.forMatch(description, snapshot);
        Integer resolvedAge = age != null ? age : parseAge(haystack);
        snapshot = snapshot.forAge(resolvedAge);
        if (SpokenNormalizer.uncertainDepartment(description, snapshot)) {
            return fallbackAdvice(snapshot);
        }
        haystack = SpokenNormalizer.forMatch(description, snapshot);
        List<Hit> ignored = new ArrayList<>();
        Hit override = firstOverride(haystack, snapshot, false, ignored);
        if (override != null) {
            Hit inner = firstRouting(haystack, snapshot, snapshot.recordOf(override.dept.code()), null, true, ignored);
            return clamp(snapshot, inner != null ? inner : override);
        }
        Gender resolvedGender = gender != null ? gender : parseGender(haystack);
        Hit best = firstRouting(haystack, snapshot, null, resolvedGender, isChild(snapshot, resolvedAge), ignored);
        if (best == null) {
            return fallbackAdvice(snapshot);
        }
        return clamp(snapshot, best);
    }

    /** N6 挂科：带能否自己定。定不了时给出候选，交模型挑。 */
    public RouteDecision route(RouteInput in) {
        return route(in, catalog.snapshot().forAge(in.age()));
    }

    public RouteDecision route(RouteInput in, DepartmentSnapshot snapshot) {
        String description = in.description();
        CanonResult canon = in.canon();
        List<Hit> found = new ArrayList<>();
        if (description == null || description.isBlank() || SpokenNormalizer.uncertainDepartment(description, snapshot)) {
            return decided(fallbackAdvice(snapshot), RouteDecision.UNCERTAIN_MARKER, null, found);
        }
        String haystack = SpokenNormalizer.forMatch(description, snapshot);
        Gender gender = in.gender() != null ? in.gender() : parseGender(haystack);
        Integer age = in.age() != null ? in.age() : parseAge(haystack);
        boolean child = isChild(snapshot, age);
        List<CanonTerm> terms = new ArrayList<>();
        List<SpecialtyRef> termRefs = new ArrayList<>();
        if (canon != null) {
            for (CanonTerm term : canon.terms()) {
                SpecialtyRef ref = snapshot.diseaseIndex().get(term.term());
                if (ref == null || skipByGender(snapshot.recordOf(ref.departmentCode()), gender)) {
                    continue;
                }
                if (!child && pediatricNamed(ref.specialty())) {
                    continue;
                }
                terms.add(term);
                termRefs.add(ref);
            }
        }
        Hit override = firstOverride(haystack, snapshot, false, found);
        if (override != null) {
            DepartmentRecord rec = snapshot.recordOf(override.dept.code());
            Hit inner = null;
            for (int i = 0; i < termRefs.size(); i++) {
                if (termRefs.get(i).departmentCode() == rec.code()) {
                    inner = new Hit(override.dept, termRefs.get(i).specialty(), 0, evidenceOf(haystack, terms.get(i)));
                    found.add(inner);
                    break;
                }
            }
            if (inner == null) {
                inner = firstRouting(haystack, snapshot, rec, null, true, found);
            }
            return decided(clamp(snapshot, inner != null ? inner : override), RouteDecision.OVERRIDE,
                    inner != null ? inner : override, found);
        }
        List<SymptomHit> clear = new ArrayList<>();
        List<SpecialtyRef> clearRefs = new ArrayList<>();
        if (canon != null) {
            for (SymptomHit hit : canon.symptoms()) {
                if (hit.ambiguous()) {
                    continue;
                }
                for (SpecialtyRef ref : hit.refs()) {
                    if (skipByGender(snapshot.recordOf(ref.departmentCode()), gender)) {
                        continue;
                    }
                    if (!child && pediatricNamed(ref.specialty())) {
                        continue;
                    }
                    clear.add(hit);
                    clearRefs.add(ref);
                    break;
                }
            }
        }
        if (!termRefs.isEmpty()) {
            SpecialtyRef primary = termRefs.get(0);
            Set<String> specs = new LinkedHashSet<>();
            for (SpecialtyRef ref : termRefs) {
                specs.add(ref.specialty());
            }
            boolean onlyOne = specs.size() == 1;
            for (SpecialtyRef ref : clearRefs) {
                if (ref.departmentCode() != primary.departmentCode()) {
                    specs.add(ref.specialty());
                    onlyOne = false;
                }
            }
            if (onlyOne) {
                Hit hit = new Hit(
                        snapshot.recordOf(primary.departmentCode()),
                        primary.specialty(),
                        indexOf(haystack, evidenceOf(haystack, terms.get(0))),
                        evidenceOf(haystack, terms.get(0)));
                found.add(hit);
                return decided(adviceOf(snapshot, primary, evidenceOf(haystack, terms.get(0))),
                        RouteDecision.CANON_TERM, hit, found);
            }
            return undecided(snapshot, RouteDecision.CONFLICT, new ArrayList<>(specs), null, found);
        }
        if (clear.isEmpty()) {
            if (canon != null && !canon.certain()) {
                String reason = ComplaintCanon.AMBIGUOUS_ONLY.equals(canon.reason())
                        ? RouteDecision.AMBIGUOUS_ONLY
                        : RouteDecision.NO_HIT;
                return undecided(snapshot, reason, snapshot.enabledSpecialtyNames(), null, found);
            }
            Hit named = firstRouting(haystack, snapshot, null, gender, child, found);
            if (named != null) {
                return decidedHit(snapshot, child, named, found);
            }
            return decided(fallbackAdvice(snapshot), RouteDecision.NO_HIT_SHORT, null, found);
        }
        Set<String> specs = new LinkedHashSet<>();
        Set<Integer> levelOne = new LinkedHashSet<>();
        for (SpecialtyRef ref : clearRefs) {
            specs.add(ref.specialty());
            levelOne.add(ref.departmentCode());
        }
        if (specs.size() == 1) {
            Hit hit = new Hit(
                    snapshot.recordOf(clearRefs.get(0).departmentCode()),
                    clearRefs.get(0).specialty(),
                    indexOf(haystack, clear.get(0).phrase()),
                    clear.get(0).phrase());
            found.add(hit);
            return decided(adviceOf(snapshot, clearRefs.get(0), clear.get(0).phrase()),
                    RouteDecision.SINGLE_SPECIALTY, hit, found);
        }
        if (levelOne.size() == 1) {
            int best = 0;
            for (int i = 1; i < clear.size(); i++) {
                if (phraseLength(clear.get(i)) > phraseLength(clear.get(best))) {
                    best = i;
                }
            }
            Hit hit = new Hit(
                    snapshot.recordOf(clearRefs.get(best).departmentCode()),
                    clearRefs.get(best).specialty(),
                    indexOf(haystack, clear.get(best).phrase()),
                    clear.get(best).phrase());
            found.add(hit);
            return decided(adviceOf(snapshot, clearRefs.get(best), clear.get(best).phrase()),
                    RouteDecision.SAME_L1_EARLIEST, hit, found);
        }
        return undecided(snapshot, RouteDecision.CONFLICT, new ArrayList<>(specs), null, found);
    }

    /** 按病名重新挂科，用于模型给出病名后的「规则路由模型输出」。 */
    public static Advice adviceOf(DepartmentSnapshot snapshot, SpecialtyRef ref, String evidence) {
        if (ref == null || !snapshot.isEnabled(ref.departmentCode())) {
            return fallbackAdvice(snapshot);
        }
        DepartmentRecord rec = snapshot.recordOf(ref.departmentCode());
        String specialty = rec.hasSpecialty(ref.specialty()) ? ref.specialty() : rec.defaultSpecialty();
        return Advice.of(rec, specialty, evidence);
    }

    public static Advice fallbackOf(DepartmentSnapshot snapshot) {
        return fallbackAdvice(snapshot);
    }

    private static String evidenceOf(String haystack, CanonTerm term) {
        Span span = term.evidence();
        if (span != null && span.start() >= 0 && span.end() <= haystack.length() && span.start() < span.end()) {
            return haystack.substring(span.start(), span.end());
        }
        return term.term();
    }

    private static int phraseLength(SymptomHit hit) {
        return hit == null || hit.phrase() == null ? 0 : hit.phrase().length();
    }

    private static RouteDecision decidedHit(DepartmentSnapshot snapshot, boolean child, Hit hit, List<Hit> found) {
        Advice advice = clamp(snapshot, hit);
        String reason = child && !"全科".equals(advice.specialty())
                ? RouteDecision.PEDIATRIC
                : RouteDecision.SINGLE_SPECIALTY;
        return decided(advice, reason, hit, found);
    }

    private static RouteDecision decided(Advice advice, String reason, Hit hit, List<Hit> found) {
        return new RouteDecision(advice, Certainty.CERTAIN, DecisionSource.RULE, reason, List.of(),
                hitOf(hit, found, reason));
    }

    private static RouteDecision undecided(
            DepartmentSnapshot snapshot, String reason, List<String> specialties, Hit hit, List<Hit> found) {
        List<String> candidates = new ArrayList<>(specialties);
        String general = snapshot.fallback().defaultSpecialty();
        if (!candidates.contains(general)) {
            candidates.add(general);
        }
        return new RouteDecision(fallbackAdvice(snapshot), Certainty.UNCERTAIN, DecisionSource.RULE, reason, candidates,
                hitOf(hit, found, reason));
    }

    private static RouteHit hitOf(Hit hit, List<Hit> found, String reason) {
        List<String> compared = new ArrayList<>();
        for (Hit item : found) {
            if (item.specialty() != null && (hit == null || !item.specialty().equals(hit.specialty()))
                    && !compared.contains(item.specialty())) {
                compared.add(item.specialty());
            }
        }
        if (hit == null) {
            return new RouteHit(null, -1, null, compared, reason);
        }
        return new RouteHit(hit.keyword(), hit.at(), hit.specialty(), compared, reason);
    }

    private static int indexOf(String haystack, String keyword) {
        if (haystack == null || keyword == null || keyword.isBlank()) {
            return -1;
        }
        return haystack.indexOf(keyword);
    }

    private static Advice fallbackAdvice(DepartmentSnapshot snapshot) {
        DepartmentRecord fallback = snapshot.fallback();
        return Advice.of(fallback, fallback.defaultSpecialty(), null);
    }

    private static Advice clamp(DepartmentSnapshot snapshot, Hit hit) {
        if (!snapshot.isEnabled(hit.dept.code())) {
            return fallbackAdvice(snapshot);
        }
        DepartmentRecord rec = snapshot.recordOf(hit.dept.code());
        String specialty = rec != null && rec.hasSpecialty(hit.specialty)
                ? hit.specialty
                : rec == null ? snapshot.fallback().defaultSpecialty() : rec.defaultSpecialty();
        return Advice.of(hit.dept, specialty, hit.keyword);
    }

    private static Hit firstOverride(String haystack, DepartmentSnapshot snapshot, boolean pediatricOnly, List<Hit> found) {
        Hit best = null;
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (dept.pediatricCatch() != pediatricOnly) {
                continue;
            }
            for (String keyword : dept.overrideKeywords()) {
                int at = haystack.indexOf(keyword);
                if (at < 0) {
                    continue;
                }
                Hit current = new Hit(dept, dept.defaultSpecialty(), at, keyword);
                found.add(current);
                if (best == null || at < best.at || (at == best.at && keyword.length() > best.keyword.length())) {
                    best = current;
                }
            }
        }
        return best;
    }

    private static boolean pediatricNamed(String specialty) {
        return specialty != null && (specialty.startsWith("小儿") || specialty.startsWith("新生儿"));
    }

    private static boolean isChild(DepartmentSnapshot snapshot, Integer age) {
        DepartmentRecord pediatric = pediatricDept(snapshot);
        return pediatric != null && age != null && age < pediatric.childAgeLt();
    }

    private static DepartmentRecord pediatricDept(DepartmentSnapshot snapshot) {
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (dept.pediatricCatch()) {
                return dept;
            }
        }
        return null;
    }

    private static Hit firstRouting(
            String haystack,
            DepartmentSnapshot snapshot,
            DepartmentRecord only,
            Gender gender,
            boolean child,
            List<Hit> found) {
        Hit best = null;
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (only != null && dept.code() != only.code()) {
                continue;
            }
            if (only == null && skipByGender(dept, gender)) {
                continue;
            }
            Set<String> used = new HashSet<>();
            for (DepartmentSpecialty spec : dept.specialties()) {
                boolean skipNamed = !child && only == null && pediatricNamed(spec.name());
                for (String keyword : spec.matchTerms()) {
                    used.add(keyword);
                    if (skipNamed) {
                        continue;
                    }
                    Hit hit = consider(best, haystack, dept, spec.name(), keyword, found);
                    if (hit != null) {
                        best = hit;
                    }
                }
            }
            String fallbackSpec = dept.defaultSpecialty();
            for (String keyword : dept.routingKeywords()) {
                if (used.contains(keyword)) {
                    continue;
                }
                if (!child && only == null && dept.pediatricCatch()) {
                    continue;
                }
                Hit hit = consider(best, haystack, dept, fallbackSpec, keyword, found);
                if (hit != null) {
                    best = hit;
                }
            }
            if (!used.contains(dept.name()) && dept.name().length() >= 2) {
                Hit hit = consider(best, haystack, dept, fallbackSpec, dept.name(), found);
                if (hit != null) {
                    best = hit;
                }
            }
        }
        return best;
    }

    private static Hit consider(
            Hit best,
            String haystack,
            DepartmentRecord dept,
            String specialty,
            String keyword,
            List<Hit> found) {
        int at = haystack.indexOf(keyword);
        if (at < 0) {
            return null;
        }
        Hit current = new Hit(dept, specialty, at, keyword);
        found.add(current);
        if (best == null || at < best.at || (at == best.at && keyword.length() > best.keyword.length())) {
            return current;
        }
        if (dept.code() == best.dept.code() && keyword.length() > best.keyword.length()) {
            return current;
        }
        return null;
    }

    private static boolean skipByGender(DepartmentRecord dept, Gender gender) {
        if (dept == null) {
            return true;
        }
        if (gender == Gender.FEMALE && dept.maleOnly()) {
            return true;
        }
        if (gender == Gender.MALE && dept.femaleOnly()) {
            return true;
        }
        return false;
    }

    private static Integer parseAge(String haystack) {
        Matcher matcher = AGE.matcher(haystack);
        if (!matcher.find()) {
            return null;
        }
        int value = Integer.parseInt(matcher.group(1));
        if (value < 1 || value > 120) {
            return null;
        }
        return value;
    }

    private static Gender parseGender(String haystack) {
        int maleAt = haystack.indexOf('男');
        int femaleAt = haystack.indexOf('女');
        if (maleAt < 0 && femaleAt < 0) {
            return null;
        }
        if (maleAt < 0) {
            return Gender.FEMALE;
        }
        if (femaleAt < 0) {
            return Gender.MALE;
        }
        return maleAt < femaleAt ? Gender.MALE : Gender.FEMALE;
    }

    /**
     * 科室建议。departmentCode 是一级类，specialty 是真正挂号的二级科。
     * evidence 是命中的关键词，没有则为空。
     */
    public record Advice(int departmentCode, String departmentName, String specialty, String evidence) {
        public Advice(Department department, String specialty, String evidence) {
            this(department.getCode(), department.getLabel(), specialty, evidence);
        }

        public Advice(Department department, String evidence) {
            this(department, null, evidence);
        }

        public static Advice of(DepartmentRecord rec, String specialty, String evidence) {
            return new Advice(rec.code(), rec.name(), specialty, evidence);
        }

        public static Advice fromCard(Integer departmentCode, String specialty, String evidence) {
            int code = departmentCode == null ? 0 : departmentCode;
            Department dept = Department.tryFromCode(code);
            return new Advice(code, dept == null ? "" : dept.getLabel(), specialty, evidence);
        }

        public Department department() {
            return Department.tryFromCode(departmentCode);
        }
    }

    private record Hit(DepartmentRecord dept, String specialty, int at, String keyword) {
    }
}
