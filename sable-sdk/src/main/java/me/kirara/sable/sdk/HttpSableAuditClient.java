package me.kirara.sable.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 基于 JDK HttpClient 的审计事件上报实现。 */
public class HttpSableAuditClient implements SableAuditClient {

    private final String endpoint;
    private final String accessToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    /**
     * @param serverBaseUrl Sablé 服务地址，如 http://localhost:8080
     * @param accessToken   租户访问令牌
     */
    public HttpSableAuditClient(String serverBaseUrl, String accessToken) {
        this.endpoint = trimSlash(serverBaseUrl) + "/audit/events";
        this.accessToken = accessToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public boolean report(AuditEvent event) {
        try {
            String body = objectMapper.writeValueAsString(event);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", accessToken == null ? "" : "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
