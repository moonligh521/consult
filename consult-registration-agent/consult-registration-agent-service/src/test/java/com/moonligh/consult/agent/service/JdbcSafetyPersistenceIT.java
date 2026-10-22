package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moonligh.consult.agent.AgentApplication;
import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 连 application.yml 里的 MySQL，核对重启和写库失败。
 * 默认不跑。本地执行：mvn -Dit.mysql=true -Dtest=JdbcSafetyPersistenceIT test
 */
@EnabledIfSystemProperty(named = "it.mysql", matches = "true")
class JdbcSafetyPersistenceIT {

    private static final String URGENT_PROMPT = "请立即拨打 120，或前往最近的实体医院急诊。";
    private static final String ORDINARY = "女，32岁，干咳半年，运动后加重";
    private static final String FAIL_TRIGGER = "trg_it_fail_session_update";

    /** SF-09：急症写入真实库后关掉 Spring，再启动仍是急症。 */
    @Test
    void sf09UrgentSurvivesProcessRestart() {
        String consultationId;
        try (ConfigurableApplicationContext first = start()) {
            ConsultationService service = first.getBean(ConsultationService.class);
            ConsultationRes urgent = service.acceptMessage(null, "胸痛");
            assertEquals(ConsultationStatus.URGENT, urgent.status());
            assertEquals(URGENT_PROMPT, urgent.prompt());
            consultationId = urgent.consultationId();
            Boolean latched = first.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT urgent_latched FROM consultation_session WHERE id = ? AND is_deleted = 0",
                    Boolean.class,
                    consultationId);
            assertEquals(Boolean.TRUE, latched);
        }

        try (ConfigurableApplicationContext second = start()) {
            ConsultationService service = second.getBean(ConsultationService.class);
            ConsultationRes again = service.acceptMessage(consultationId, "还痛");
            assertEquals(ConsultationStatus.URGENT, again.status());
            assertEquals(URGENT_PROMPT, again.prompt());
            assertNull(again.card());
            Boolean latched = second.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT urgent_latched FROM consultation_session WHERE id = ? AND is_deleted = 0",
                    Boolean.class,
                    consultationId);
            assertEquals(Boolean.TRUE, latched);
            deleteConsultation(second.getBean(JdbcTemplate.class), consultationId);
        }
    }

    /** SF-30：写库失败时，本轮急症仍回 120；普通病情回 1000、不给卡片。 */
    @Test
    void sf30PersistFailureOnMysql() {
        try (ConfigurableApplicationContext context = start()) {
            ConsultationService service = context.getBean(ConsultationService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            installFailTrigger(jdbc);
            try {
                ConsultationRes urgent = service.acceptMessage(null, "胸痛");
                assertEquals(ConsultationStatus.URGENT, urgent.status());
                assertEquals(URGENT_PROMPT, urgent.prompt());
                Integer urgentRows = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM consultation_session WHERE id = ? AND is_deleted = 0",
                        Integer.class,
                        urgent.consultationId());
                assertEquals(0, urgentRows);

                BusinessException error = assertThrows(BusinessException.class,
                        () -> service.acceptMessage(null, ORDINARY));
                assertEquals(ErrorCode.UNKNOWN_ERROR, error.getErrorCode());
                assertEquals(1000, error.getCode());
            } finally {
                dropFailTrigger(jdbc);
            }
        }
    }

    /** 不占 18081 端口。数据源用 src/main/resources/application.yml。 */
    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(AgentApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.sql.init.mode=never")
                .run();
    }

    /** 让 UPDATE 会话失败，模拟真实写库故障。测完必须删掉。 */
    private void installFailTrigger(JdbcTemplate jdbc) {
        dropFailTrigger(jdbc);
        jdbc.execute("""
                CREATE TRIGGER trg_it_fail_session_update
                BEFORE UPDATE ON consultation_session
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'it persist fail'
                """);
    }

    private void dropFailTrigger(JdbcTemplate jdbc) {
        jdbc.execute("DROP TRIGGER IF EXISTS " + FAIL_TRIGGER);
    }

    /** 测完删掉本轮数据，不留病情原文。 */
    private void deleteConsultation(JdbcTemplate jdbc, String consultationId) {
        jdbc.update("DELETE FROM safety_decision_audit WHERE consultation_id = ?", consultationId);
        jdbc.update("DELETE FROM consultation_message WHERE consultation_id = ?", consultationId);
        jdbc.update("DELETE FROM consultation_session WHERE id = ?", consultationId);
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consultation_session WHERE id = ?",
                Integer.class,
                consultationId) == 0);
    }
}
