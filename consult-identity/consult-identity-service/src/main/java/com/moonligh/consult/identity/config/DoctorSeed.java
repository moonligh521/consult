package com.moonligh.consult.identity.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorSaveReq;
import com.moonligh.consult.identity.api.ScheduleAdminRes;
import com.moonligh.consult.identity.api.ScheduleSaveReq;
import com.moonligh.consult.identity.service.DoctorAdminService;
import com.moonligh.consult.identity.store.DoctorStore;

/**
 * 演示医生：每个开通二级科至少 2 位，并补近 7 天排班。
 * 目录来自 classpath:department/demo-specialties.json。
 */
@Component
@Order(20)
public class DoctorSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DoctorSeed.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HOSPITAL = "互联网医院";
    /** 演示姓名池：姓×名组合，禁止再拼数字后缀。 */
    private static final String[] SURNAMES = {
            "周", "赵", "孙", "陈", "苏", "林", "王", "刘", "韩", "吴",
            "郑", "冯", "蒋", "沈", "杨", "朱", "秦", "尤", "许", "何",
            "吕", "施", "张", "孔", "曹", "严", "华", "金", "魏", "陶",
            "姜", "戚", "谢", "邹", "喻", "柏", "水", "窦", "章", "云"
    };
    private static final String[] GIVEN = {
            "宁", "衡", "悦", "岚", "敏", "晓", "启明", "岩", "霜", "桐",
            "博", "涵", "轩", "然", "清", "远", "安", "成", "辰", "浩",
            "婷", "倩", "佳", "琳", "慧", "静", "文", "杰", "峰", "磊",
            "鹏", "飞", "雪", "梅", "芳", "丽", "强", "勇", "军", "平"
    };

    private final DoctorStore doctors;
    private final DoctorAdminService service;

    public DoctorSeed(DoctorStore doctors, DoctorAdminService service) {
        this.doctors = doctors;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        doctors.backfillSkills();
        // 先保住分诊演示用的具名医生
        ensureNamed("doc-zhou-ning", "周宁", 1, 1, "呼吸内科", "干咳、咳嗽、哮喘、肺部感染", HOSPITAL + "呼吸内科主治医师。");
        ensureNamed("doc-zhao-heng", "赵衡", 1, 1, "普内科", "高血压、常见内科随访", HOSPITAL + "普内科主治医师。");
        ensureNamed("doc-sun-yue", "孙悦", 2, 1, "普内科", "糖尿病、高血脂", HOSPITAL + "普内科主治医师。");
        ensureNamed("doc-chen-lan", "陈岚", 2, 2, "普外科", "腹股沟疝、甲状腺结节", HOSPITAL + "普外科主治医师。");
        ensureNamed("doc-su-min", "苏敏", 2, 6, "妇科", "月经不调、妇科炎症", HOSPITAL + "妇科主治医师。");
        ensureNamed("doc-lin-xiao", "林晓", 2, 8, "儿科", "小儿咳嗽、发热", HOSPITAL + "儿科主治医师。");
        ensureNamed("doc-wang-qi", "王启明", 1, 4, "全科", "常见病初诊分流", HOSPITAL + "全科主治医师。");
        ensureNamed("doc-liu-yan", "刘岩", 1, 5, "骨科", "颈肩腰腿痛", HOSPITAL + "骨科主治医师。");
        ensureNamed("doc-han-shuang", "韩霜", 2, 11, "皮肤科", "皮炎、痤疮", HOSPITAL + "皮肤科主治医师。");
        ensureNamed("doc-wu-tong", "吴桐", 1, 9, "耳鼻咽喉科", "鼻炎、咽喉炎", HOSPITAL + "耳鼻咽喉科主治医师。");

        int renamed = renameAwkwardDemoNames();
        int hospitalFixed = backfillHospitalName();
        Coverage coverage = loadCoverage();
        int addedDoctors = 0;
        int addedSlots = 0;
        Set<String> usedNames = new HashSet<>();
        for (DoctorAdminRes row : doctors.list()) {
            if (row.name() != null && !row.name().isBlank()) {
                usedNames.add(row.name());
            }
        }
        for (SpecialtyRow row : coverage.specialties()) {
            int[] added = ensureRow(row.departmentCode(), row.specialty(), coverage.doctorsPerSpecialty(), usedNames);
            addedDoctors += added[0];
            addedSlots += added[1];
        }
        log.info(
                "演示医生覆盖完成：目录二级科 {} 个，每科至少 {} 人；本次新增医生 {}、排班 {}、更正姓名 {}、医院名 {}",
                coverage.specialties().size(),
                coverage.doctorsPerSpecialty(),
                addedDoctors,
                addedSlots,
                renamed,
                hospitalFixed);
    }

    /** 把历史种子里的「何敏2」这类名字改成正常中文名。 */
    private int renameAwkwardDemoNames() {
        int changed = 0;
        Set<String> used = new HashSet<>();
        List<DoctorAdminRes> all = doctors.list();
        for (DoctorAdminRes row : all) {
            if (row.name() != null && !row.name().isBlank() && !looksNumbered(row.name())) {
                used.add(row.name());
            }
        }
        int seq = 0;
        for (DoctorAdminRes row : all) {
            if (row.name() == null || !looksNumbered(row.name())) {
                continue;
            }
            seq++;
            String specialty = row.specialty() == null || row.specialty().isBlank() ? "全科" : row.specialty();
            String next = uniqueDemoName(specialty, seq, used);
            used.add(next);
            service.updateDoctor(row.id(), new DoctorSaveReq(
                    next,
                    row.gender(),
                    row.title(),
                    HOSPITAL,
                    row.departmentCode(),
                    specialty,
                    row.skills(),
                    HOSPITAL + specialty + "主治医师。",
                    row.status()));
            changed++;
            log.info("演示医生改名 {} → {}", row.name(), next);
        }
        return changed;
    }

    /** 旧演示医院名统一成「互联网医院」。 */
    private int backfillHospitalName() {
        int changed = 0;
        for (DoctorAdminRes row : doctors.list()) {
            if (!needsHospitalBackfill(row)) {
                continue;
            }
            String specialty = row.specialty() == null || row.specialty().isBlank() ? "全科" : row.specialty();
            String intro = row.intro() == null || needsHospitalRewrite(row.intro())
                    ? HOSPITAL + specialty + "主治医师。"
                    : row.intro();
            service.updateDoctor(row.id(), new DoctorSaveReq(
                    row.name(),
                    row.gender(),
                    row.title(),
                    HOSPITAL,
                    row.departmentCode(),
                    specialty,
                    row.skills(),
                    intro,
                    row.status()));
            changed++;
        }
        return changed;
    }

    private static boolean needsHospitalBackfill(DoctorAdminRes row) {
        return needsHospitalRewrite(row.hospital()) || needsHospitalRewrite(row.intro());
    }

    private static boolean needsHospitalRewrite(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        return text.contains("演示医院") || text.contains("市第一人民医院");
    }

    private static boolean looksNumbered(String name) {
        return name.matches(".*\\d+$");
    }

    private void ensureNamed(
            String id,
            String name,
            int gender,
            int dept,
            String specialty,
            String skills,
            String intro) {
        DoctorAdminRes row = doctors.find(id);
        if (row == null) {
            row = doctors.list().stream()
                    .filter(item -> name.equals(item.name()))
                    .findFirst()
                    .orElse(null);
        }
        if (row == null) {
            doctors.seed(id, name, gender, "主治医师", HOSPITAL, dept, specialty, skills, intro);
            fillWeek(id);
            log.info("已补演示医生 {}", name);
            return;
        }
        boolean skillsGap = row.skills() == null || !containsAll(row.skills(), skills);
        boolean introSameAsSkills = row.intro() != null && row.intro().equals(row.skills());
        if (skillsGap || introSameAsSkills) {
            service.updateDoctor(row.id(), new DoctorSaveReq(
                    row.name(),
                    row.gender(),
                    row.title(),
                    row.hospital(),
                    row.departmentCode(),
                    row.specialty() == null || row.specialty().isBlank() ? specialty : row.specialty(),
                    skillsGap ? skills : row.skills(),
                    introSameAsSkills ? intro : row.intro(),
                    row.status()));
        }
        int added = fillWeek(row.id());
        if (added > 0) {
            log.info("已补 {} 近 7 天排班 {} 条", name, added);
        }
    }

    private int fillWeek(String doctorId) {
        LocalDate today = LocalDate.now();
        String from = today.toString();
        String to = today.plusDays(6).toString();
        Set<String> existing = new HashSet<>();
        for (ScheduleAdminRes row : service.listSchedules(doctorId, from, to)) {
            existing.add(row.visitDate() + "|" + row.period());
        }
        int added = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.plusDays(i);
            added += slot(doctorId, existing, day, "上午", "08:00", "12:00", 20);
            added += slot(doctorId, existing, day, "下午", "14:00", "17:30", 16);
        }
        return added;
    }

    private int slot(
            String doctorId,
            Set<String> existing,
            LocalDate day,
            String period,
            String start,
            String end,
            int limit) {
        String key = day + "|" + period;
        if (existing.contains(key)) {
            return 0;
        }
        try {
            service.createSchedule(new ScheduleSaveReq(
                    doctorId, day.toString(), period, start, end, "图文", limit));
            existing.add(key);
            return 1;
        } catch (BusinessException error) {
            if (error.getErrorCode() == ErrorCode.SCHEDULE_CONFLICT) {
                existing.add(key);
                return 0;
            }
            throw error;
        }
    }

    /** 开通某二级时补齐演示医生和当日班。 */
    public void ensureSpecialty(int departmentCode, String specialty) {
        Set<String> usedNames = new HashSet<>();
        for (DoctorAdminRes row : doctors.list()) {
            if (row.name() != null && !row.name().isBlank()) {
                usedNames.add(row.name());
            }
        }
        ensureRow(departmentCode, specialty, 2, usedNames);
    }

    /** 每科至少留一位主任，供排队超时后提档到专家。 */
    private void diversifyTitles(List<DoctorAdminRes> roster) {
        if (roster.size() < 2) {
            return;
        }
        for (DoctorAdminRes row : roster) {
            if ("主任医师".equals(row.title())) {
                return;
            }
        }
        DoctorAdminRes second = roster.get(1);
        String specialty = second.specialty() == null || second.specialty().isBlank() ? "全科" : second.specialty();
        service.updateDoctor(second.id(), new DoctorSaveReq(
                second.name(),
                second.gender(),
                "主任医师",
                HOSPITAL,
                second.departmentCode(),
                specialty,
                second.skills(),
                HOSPITAL + specialty + "主任医师。",
                second.status()));
    }

    private int[] ensureRow(int departmentCode, String specialty, int per, Set<String> usedNames) {
        List<DoctorAdminRes> existing = doctors.listBySpecialty(specialty);
        int addedDoctors = 0;
        int addedSlots = 0;
        int need = Math.max(0, per - existing.size());
        for (int i = 0; i < need; i++) {
            int seq = existing.size() + i + 1;
            String id = stableId(specialty, seq);
            if (doctors.exists(id)) {
                continue;
            }
            String name = uniqueDemoName(specialty, seq, usedNames);
            usedNames.add(name);
            int gender = seq % 2 == 0 ? 2 : 1;
            String title = seq % 2 == 0 ? "主任医师" : "主治医师";
            doctors.seed(
                    id,
                    name,
                    gender,
                    title,
                    HOSPITAL,
                    departmentCode,
                    specialty,
                    specialty + "常见病",
                    HOSPITAL + specialty + title + "。");
            addedDoctors++;
        }
        List<DoctorAdminRes> roster = doctors.listBySpecialty(specialty);
        diversifyTitles(roster);
        for (DoctorAdminRes doctor : roster) {
            addedSlots += fillWeek(doctor.id());
        }
        return new int[] {addedDoctors, addedSlots};
    }

    private Coverage loadCoverage() {
        List<SpecialtyRow> fromCatalog = loadCatalogSpecialties();
        if (!fromCatalog.isEmpty()) {
            return new Coverage(2, fromCatalog);
        }
        try (InputStream in = new ClassPathResource("department/demo-specialties.json").getInputStream()) {
            JsonNode root = MAPPER.readTree(in);
            int per = root.path("doctorsPerSpecialty").asInt(2);
            List<SpecialtyRow> rows = new ArrayList<>();
            for (JsonNode node : root.path("specialties")) {
                rows.add(new SpecialtyRow(node.path("departmentCode").asInt(), node.path("specialty").asText()));
            }
            return new Coverage(Math.max(2, per), List.copyOf(rows));
        } catch (Exception error) {
            throw new IllegalStateException("读不到 department/demo-specialties.json", error);
        }
    }

    private List<SpecialtyRow> loadCatalogSpecialties() {
        List<SpecialtyRow> rows = new ArrayList<>();
        for (String[] item : doctors.listEnabledSpecialties()) {
            rows.add(new SpecialtyRow(Integer.parseInt(item[0]), item[1]));
        }
        return rows;
    }

    private static String stableId(String specialty, int seq) {
        String raw = specialty + "#" + seq;
        String hex = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return "doc-" + hex.substring(0, 16);
    }

    private static String uniqueDemoName(String specialty, int seq, Set<String> used) {
        int base = Math.floorMod(specialty.hashCode(), SURNAMES.length * GIVEN.length);
        for (int offset = 0; offset < SURNAMES.length * GIVEN.length; offset++) {
            int index = Math.floorMod(base + (seq - 1) * 17 + offset, SURNAMES.length * GIVEN.length);
            String name = SURNAMES[index % SURNAMES.length] + GIVEN[index / SURNAMES.length];
            if (!used.contains(name)) {
                return name;
            }
        }
        // 池子用尽时退回带序号，正常演示覆盖不到
        return SURNAMES[Math.floorMod(seq, SURNAMES.length)] + GIVEN[Math.floorMod(seq, GIVEN.length)] + "·" + seq;
    }

    private static boolean containsAll(String current, String expected) {
        for (String term : expected.split("[、，,;；]+")) {
            String item = term.trim();
            if (!item.isEmpty() && !current.contains(item)) {
                return false;
            }
        }
        return true;
    }

    private record Coverage(int doctorsPerSpecialty, List<SpecialtyRow> specialties) {
    }

    private record SpecialtyRow(int departmentCode, String specialty) {
    }
}
