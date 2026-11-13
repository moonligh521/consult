package com.moonligh.consult.agent.infrastructure.clinicalrecord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 医生回复归档到临床数据仓库。没配地址时不调用。
 * 这里只记医生写下的摘要，不把导诊草稿当成诊断。
 */
@Component
public class ClinicalChartArchive {

    private final String baseUrl;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public ClinicalChartArchive(
            @Value("${consult.clinical-record.base-url:}") String baseUrl,
            @Value("${consult.clinical-record.token:}") String token) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.token = token == null ? "" : token;
    }

    public boolean enabled() {
        return !baseUrl.isBlank();
    }

    public void archiveDoctorNote(String patientId, String consultationId, int seq, String text) {
        if (!enabled() || patientId == null || patientId.isBlank()) {
            return;
        }
        String commandId = "registration:" + consultationId + ":" + seq;
        String body = """
                {"commandId":"%s","patientId":"%s","facts":[{"factType":"NOTE","statement":"PRESENT","author":"DOCTOR","basis":"%s","displayName":"%s"}]}
                """.formatted(commandId, escape(patientId), escape(consultationId), escape(text));
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/facts"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!token.isBlank()) {
                builder.header("X-Internal-Token", token);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300
                    || mapper.readTree(response.body()).path("code").asInt() != 200) {
                throw new BusinessException(ErrorCode.CHART_UNAVAILABLE);
            }
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.CHART_UNAVAILABLE);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
