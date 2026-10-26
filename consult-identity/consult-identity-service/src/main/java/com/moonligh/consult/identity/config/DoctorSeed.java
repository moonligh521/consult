package com.moonligh.consult.identity.config;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorSaveReq;
import com.moonligh.consult.identity.api.ScheduleAdminRes;
import com.moonligh.consult.identity.api.ScheduleSaveReq;
import com.moonligh.consult.identity.service.DoctorAdminService;
import com.moonligh.consult.identity.store.DoctorStore;

/**
 * 演示医生。一级科室、二级科室、擅长分列；库空时排 7 天号。
 */
@Component
@Order(20)
public class DoctorSeed implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DoctorSeed.class);

    private final DoctorStore doctors;
    private final DoctorAdminService service;

    public DoctorSeed(DoctorStore doctors, DoctorAdminService service) {
        this.doctors = doctors;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (doctors.count() == 0) {
            seed("doc-zhou-ning", "周宁", 1, 1, "呼吸内科", "干咳、咳嗽、哮喘、肺部感染", "演示医院呼吸内科主治医师。");
            seed("doc-zhao-heng", "赵衡", 1, 1, "普内科", "高血压、常见内科随访", "演示医院普内科主治医师。");
            seed("doc-sun-yue", "孙悦", 2, 1, "普内科", "糖尿病、高血脂", "演示医院普内科主治医师。");
            seed("doc-chen-lan", "陈岚", 2, 2, "普外科", "腹股沟疝、甲状腺结节", "演示医院普外科主治医师。");
            seed("doc-su-min", "苏敏", 2, 6, "妇科", "月经不调、妇科炎症", "演示医院妇科主治医师。");
            seed("doc-lin-xiao", "林晓", 2, 8, "儿科", "小儿咳嗽、发热", "演示医院儿科主治医师。");
            seed("doc-wang-qi", "王启明", 1, 4, "全科", "常见病初诊分流", "演示医院全科主治医师。");
            seed("doc-liu-yan", "刘岩", 1, 5, "骨科", "颈肩腰腿痛", "演示医院骨科主治医师。");
            seed("doc-han-shuang", "韩霜", 2, 11, "皮肤科", "皮炎、痤疮", "演示医院皮肤科主治医师。");
            seed("doc-wu-tong", "吴桐", 1, 9, "耳鼻咽喉科", "鼻炎、咽喉炎", "演示医院耳鼻咽喉科主治医师。");
            int slots = 0;
            for (var doctor : doctors.list()) {
                slots += fillWeek(doctor.id());
            }
            log.info("已写入演示医生 {} 位、排班 {} 条", doctors.count(), slots);
        }
        doctors.backfillSkills();
        ensureDoctor("doc-zhao-heng", "赵衡", 1, 1, "普内科", "高血压、常见内科随访", "演示医院普内科主治医师。");
        ensureDoctor("doc-sun-yue", "孙悦", 2, 1, "普内科", "糖尿病、高血脂", "演示医院普内科主治医师。");
        ensureDoctor("doc-zhou-ning", "周宁", 1, 1, "呼吸内科", "干咳、咳嗽、哮喘、肺部感染", "演示医院呼吸内科主治医师。");
    }

    private void ensureDoctor(
            String id,
            String name,
            int gender,
            int dept,
            String specialty,
            String skills,
            String intro) {
        DoctorAdminRes row = doctors.list().stream()
                .filter(item -> name.equals(item.name()) || id.equals(item.id()))
                .findFirst()
                .orElse(null);
        if (row == null) {
            seed(id, name, gender, dept, specialty, skills, intro);
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

    private void seed(
            String id,
            String name,
            int gender,
            int dept,
            String specialty,
            String skills,
            String intro) {
        doctors.seed(id, name, gender, "主治医师", "演示医院", dept, specialty, skills, intro);
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
}
