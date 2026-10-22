package com.moonligh.consult.agent.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moonligh.consult.agent.AgentApplication;
import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 连 application.yml 的 MySQL，跑完整咨询到下单。
 * 本地执行：mvn -Dit.mysql=true -Dtest=JdbcConsultFlowIT test
 */
@EnabledIfSystemProperty(named = "it.mysql", matches = "true")
class JdbcConsultFlowIT {

    private static final String ORDINARY = "女，32岁，干咳半年，运动后加重";

    @Test
    void fullFlowWritesOrderToMysql() throws IOException {
        try (ConfigurableApplicationContext context = start()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ensureOrderTable(jdbc);
            ConsultationService service = context.getBean(ConsultationService.class);

            ConsultationRes card = service.acceptMessage(null, ORDINARY);
            assertEquals(ConsultationStatus.CARD_READY, card.status());
            String id = card.consultationId();

            ConsultationRes confirmed = service.confirm(id);
            assertEquals(ConsultationStatus.CONFIRMED, confirmed.status());
            assertEquals(4, confirmed.packages().size());
            assertNull(confirmed.order());

            ConsultationRes ordered = service.placeOrder(id, 3);
            assertEquals(3, ordered.order().packageCode());
            assertEquals("三甲医生", ordered.order().packageName());
            assertEquals(2, ordered.order().payStatus().getCode());

            List<Map<String, Object>> rows = jdbc.queryForList(
                    """
                    SELECT package_code, package_name, pay_status, pay_amount
                    FROM consult_order
                    WHERE consultation_id = ? AND is_deleted = 0
                    """,
                    id);
            assertEquals(1, rows.size());
            assertEquals(3, ((Number) rows.get(0).get("package_code")).intValue());
            assertEquals("三甲医生", rows.get(0).get("package_name"));
            assertEquals(2, ((Number) rows.get(0).get("pay_status")).intValue());

            ConsultationRes again = service.placeOrder(id, 3);
            assertEquals(ordered.order().orderId(), again.order().orderId());

            BusinessException other = assertThrows(BusinessException.class, () -> service.placeOrder(id, 4));
            assertEquals(ErrorCode.ALREADY_ORDERED, other.getErrorCode());

            deleteConsultation(jdbc, id);
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(AgentApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.sql.init.mode=never")
                .run();
    }

    /** 按仓库里的脚本建表。已经有表就跳过。 */
    private void ensureOrderTable(JdbcTemplate jdbc) throws IOException {
        Integer exists = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'consult_order'
                """,
                Integer.class);
        if (exists != null && exists > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("db/V0.3.20261022__consult_order.sql").getInputStream()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            jdbc.execute(stripComments(sql));
        }
        assertNotNull(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'consult_order'
                """,
                Integer.class));
    }

    private String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private void deleteConsultation(JdbcTemplate jdbc, String consultationId) {
        jdbc.update("DELETE FROM consult_order WHERE consultation_id = ?", consultationId);
        jdbc.update("DELETE FROM safety_decision_audit WHERE consultation_id = ?", consultationId);
        jdbc.update("DELETE FROM consultation_message WHERE consultation_id = ?", consultationId);
        jdbc.update("DELETE FROM consultation_session WHERE id = ?", consultationId);
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consultation_session WHERE id = ?",
                Integer.class,
                consultationId) == 0);
    }
}
