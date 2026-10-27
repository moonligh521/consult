package com.moonligh.consult.agent.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.req.DepartmentSaveReq;
import com.moonligh.consult.agent.api.req.SpecialtySaveReq;
import com.moonligh.consult.agent.api.res.CatalogImpactRes;
import com.moonligh.consult.agent.api.res.DepartmentTreeRes;
import com.moonligh.consult.agent.api.res.SpecialtyNodeRes;
import com.moonligh.consult.agent.config.CatalogProperties;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentCatalogStore;
import com.moonligh.consult.agent.domain.department.DepartmentCatalogWriter;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSpecialty;
import com.moonligh.consult.agent.domain.department.DoctorCoverage;
import com.moonligh.consult.agent.domain.department.DoctorCoverageClient;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 目录变更闭环：开通门禁、停用保护兜底、写库走 Writer 回填缓存。
 */
@Service
public class CatalogChangeService {

    private final DepartmentCatalog catalog;
    private final DepartmentCatalogStore store;
    private final DepartmentCatalogWriter writer;
    private final DoctorCoverageClient coverage;
    private final CatalogProperties properties;
    private final ConsultationMapper mapper;

    public CatalogChangeService(
            DepartmentCatalog catalog,
            DepartmentCatalogStore store,
            DepartmentCatalogWriter writer,
            DoctorCoverageClient coverage,
            CatalogProperties properties,
            ConsultationMapper mapper) {
        this.catalog = catalog;
        this.store = store;
        this.writer = writer;
        this.coverage = coverage;
        this.properties = properties;
        this.mapper = mapper;
    }

    public List<DepartmentTreeRes> patientTree() {
        List<DepartmentTreeRes> rows = new ArrayList<>();
        for (DepartmentRecord dept : catalog.snapshot().enabled()) {
            List<SpecialtyNodeRes> specs = new ArrayList<>();
            for (DepartmentSpecialty spec : dept.specialties()) {
                specs.add(new SpecialtyNodeRes(spec.id(), spec.name(), spec.treatDesc(), true));
            }
            if (!specs.isEmpty()) {
                rows.add(new DepartmentTreeRes(dept.code(), dept.name(), true, dept.fallback(), specs));
            }
        }
        return rows;
    }

    public List<DepartmentTreeRes> adminTree() {
        List<DepartmentTreeRes> rows = new ArrayList<>();
        for (DepartmentRecord dept : store.loadRecords()) {
            List<SpecialtyNodeRes> specs = new ArrayList<>();
            for (DepartmentSpecialty spec : dept.specialties()) {
                specs.add(new SpecialtyNodeRes(spec.id(), spec.name(), spec.treatDesc(), spec.enabled()));
            }
            rows.add(new DepartmentTreeRes(dept.code(), dept.name(), dept.enabled(), dept.fallback(), specs));
        }
        return rows;
    }

    public int addDepartment(DepartmentSaveReq req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        return writer.addDepartment(
                req.name().trim(),
                req.genderScope() == null ? 0 : req.genderScope(),
                req.childAgeLt(),
                req.treatDesc());
    }

    public void patchDepartment(int code, DepartmentSaveReq req) {
        DepartmentRecord rec = record(code);
        if (req != null && req.enabled() != null) {
            setDepartmentEnabled(rec, req.enabled());
        }
    }

    public long addSpecialty(int departmentCode, SpecialtySaveReq req) {
        record(departmentCode);
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        return writer.addSpecialty(departmentCode, req.name().trim(), req.treatDesc() == null ? "" : req.treatDesc());
    }

    public void patchSpecialty(int departmentCode, long specialtyId, SpecialtySaveReq req) {
        DepartmentRecord rec = record(departmentCode);
        DepartmentSpecialty spec = specialty(rec, specialtyId);
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        if (req.name() != null && !req.name().isBlank() && !req.name().trim().equals(spec.name())) {
            writer.renameSpecialty(specialtyId, req.name().trim());
        }
        if (req.enabled() != null) {
            setSpecialtyEnabled(rec, spec, req.enabled());
        }
    }

    public CatalogImpactRes impact(int departmentCode, Long specialtyId) {
        DepartmentRecord rec = record(departmentCode);
        if (specialtyId == null) {
            int cards = 0;
            int unpaid = 0;
            int doctors = 0;
            int onDuty = 0;
            for (DepartmentSpecialty spec : rec.specialties()) {
                CatalogImpactRes one = impactSpecialty(rec, spec);
                cards += one.cardReady();
                unpaid += one.confirmedUnpaid();
                doctors += one.doctors();
                onDuty += one.onDutyToday();
            }
            return new CatalogImpactRes(cards, unpaid, doctors, onDuty, true, rec.fallback() ? "兜底全科不能停用" : null);
        }
        return impactSpecialty(rec, specialty(rec, specialtyId));
    }

    private void setDepartmentEnabled(DepartmentRecord rec, boolean enabled) {
        if (!enabled && rec.fallback()) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_FALLBACK);
        }
        if (enabled) {
            boolean any = false;
            for (DepartmentSpecialty spec : rec.specialties()) {
                if (spec.enabled()) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                throw new BusinessException(ErrorCode.INVALID_DEPARTMENT);
            }
        }
        writer.setEnabled(rec.code(), enabled);
    }

    private void setSpecialtyEnabled(DepartmentRecord rec, DepartmentSpecialty spec, boolean enabled) {
        if (!enabled && rec.fallback() && "全科".equals(spec.name())) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_FALLBACK);
        }
        if (enabled) {
            DoctorCoverage cover = coverage.coverage(rec.code(), spec.name());
            if (!cover.enough() && properties.isProvisionDemo()) {
                cover = coverage.ensureDemo(rec.code(), spec.name());
            }
            if (!cover.enough()) {
                throw new BusinessException(ErrorCode.SPECIALTY_NO_COVERAGE);
            }
            writer.setSpecialtyEnabled(spec.id(), true);
            if (!rec.enabled()) {
                writer.setEnabled(rec.code(), true);
            }
            return;
        }
        writer.setSpecialtyEnabled(spec.id(), false);
    }

    private CatalogImpactRes impactSpecialty(DepartmentRecord rec, DepartmentSpecialty spec) {
        DoctorCoverage cover = coverage.coverage(rec.code(), spec.name());
        int liveSiblings = 0;
        for (DepartmentSpecialty item : rec.specialties()) {
            if (item.enabled() && (spec.id() == null || !spec.id().equals(item.id()))) {
                liveSiblings++;
            }
        }
        boolean hides = liveSiblings == 0;
        return new CatalogImpactRes(
                mapper.countCardReady(spec.name()),
                mapper.countConfirmedUnpaid(spec.name()),
                cover.doctorCount(),
                cover.onDutyToday(),
                hides,
                hides ? "停用后患者树将隐藏整个一级" : null);
    }

    private DepartmentRecord record(int code) {
        for (DepartmentRecord rec : store.loadRecords()) {
            if (rec.code() == code) {
                return rec;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_DEPARTMENT);
    }

    private static DepartmentSpecialty specialty(DepartmentRecord rec, long specialtyId) {
        for (DepartmentSpecialty spec : rec.specialties()) {
            if (spec.id() != null && spec.id() == specialtyId) {
                return spec;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_DEPARTMENT);
    }
}
