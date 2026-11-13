package com.moonligh.consult.agent.infrastructure.clinicalrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.sun.net.httpserver.HttpServer;

class ClinicalChartArchiveTest {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void blankAddressDoesNothing() {
        assertFalse(new ClinicalChartArchive("", "").enabled());
    }

    @Test
    void postsDoctorNoteAndRejectsWhenArchiveIsDown() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/facts", exchange -> {
            byte[] body = "{\"code\":200,\"data\":{\"snapshotVersion\":1}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ClinicalChartArchive archive = new ClinicalChartArchive(
                "http://127.0.0.1:" + server.getAddress().getPort(), "");
        archive.archiveDoctorNote("pat-1", "consult-1", 3, "复诊");

        server.stop(0);
        server = null;
        BusinessException error = assertThrows(BusinessException.class,
                () -> archive.archiveDoctorNote("pat-1", "consult-1", 4, "复诊"));
        assertEquals(ErrorCode.CHART_UNAVAILABLE, error.getErrorCode());
    }
}
