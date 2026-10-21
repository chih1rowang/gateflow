package com.gateflow.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 过滤器 1/3（最外层：Audit -> JWT -> RateLimit，仅拦 /mcp）：
 * 记录每次调用的 client/状态码/JSON-RPC 方法/工具名/参数摘要/耗时/拒绝原因，同步写 audit_logs。
 * 同步写是刻意的演进起点——W4 压测暴露瓶颈后改 Kafka 异步（commit 历史即证据链）。
 */
@Component
@org.springframework.core.annotation.Order(1)
public class AuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogMapper auditLogMapper;

    public AuditFilter(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        // 包装请求以便在 doFilter 之后仍能读体（流只能读一次）
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        try {
            chain.doFilter(wrapped, response);
        } finally {
            int latency = (int) (System.currentTimeMillis() - start);
            saveAudit(wrapped, response, latency);
        }
    }

    private void saveAudit(ContentCachingRequestWrapper request, HttpServletResponse response, int latency) {
        try {
            AuditLog row = new AuditLog();
            row.setClientName(resolveClient(request, response));
            row.setHttpStatus(response.getStatus());
            row.setLatencyMs(latency);
            if (response.getStatus() == 401) {
                row.setRejectedReason("unauthorized");
            } else if (response.getStatus() == 429) {
                row.setRejectedReason("rate_limited");
            } else {
                row.setRejectedReason("none");
            }
            String body = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (!body.isBlank()) {
                JsonNode root = MAPPER.readTree(body);
                String method = root.path("method").asText("");
                row.setRpcMethod(method);
                if ("tools/call".equals(method)) {
                    row.setToolName(root.path("params").path("name").asText(""));
                    String params = root.path("params").path("arguments").toString();
                    row.setParamsSnippet(params.length() > 500 ? params.substring(0, 500) : params);
                }
            }
            auditLogMapper.insert(row);
        } catch (Exception e) {
            // 审计失败不阻断主链路（本过滤器已在 finally 中）；W4 交由 Kafka 解耦后天然隔离
            log.error("audit write failed: {}", e.getMessage());
        }
    }

    private String resolveClient(HttpServletRequest request, HttpServletResponse response) {
        if (response.getStatus() != 401) {
            Object client = request.getAttribute("client");
            if (client != null) {
                return client.toString();
            }
        }
        return "unknown";
    }
}
