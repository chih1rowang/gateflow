package com.gateflow.gateway;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三个正式 MCP 工具（W3 上线，替代 spike 期的 hello）。
 * 架构决策：gateway 是纯门口（鉴权/限流/审计/工具注册），工具实现全部在 workers 进程，
 * 本类只经 HTTP 转发——与 agentgateway"代理到后端"形态一致，工具本体与门口解耦。
 * 转发带连接 5s/读取 60s 超时：workers 僵死时 tools/call 明确失败而不是无限挂起（审查报告中等 8）。
 */
@Service
public class GateflowTools {

    private final RestClient workers;

    public GateflowTools(@Value("${gateflow.workers-base-url}") String workersBaseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(60_000);
        this.workers = RestClient.builder().baseUrl(workersBaseUrl).requestFactory(factory).build();
    }

    @McpTool(name = "doc_search",
            description = "Search the internal policy knowledge base (club expense/venue/ticket rules). Returns the most relevant document snippets.")
    public String docSearch(@McpToolParam(description = "the question to search for, e.g. single reimbursement cap") String query) {
        if (query == null || query.isBlank()) {
            return "query is required";
        }
        List<String> chunks = workers.get()
                .uri("/api/search?q={q}", query)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<String>>() {});
        if (chunks == null || chunks.isEmpty()) {
            return "no relevant snippets found";
        }
        return String.join("\n----\n", chunks);
    }

    @McpTool(name = "db_query",
            description = "Run a READ-ONLY SQL SELECT against the expense records database. Only single SELECT statements are allowed.")
    public String dbQuery(@McpToolParam(description = "a single SELECT statement") String sql) {
        if (sql == null || sql.isBlank()) {
            return "rejected: sql is required";
        }
        Map<String, Object> result = workers.post()
                .uri("/api/db-query")
                .body(Map.of("sql", sql))
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        if (result == null) {
            return "no result";
        }
        if (result.containsKey("error")) {
            return "rejected: " + result.get("error");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        StringBuilder sb = new StringBuilder("rows: " + rows.size() + "\n");
        for (Map<String, Object> row : rows) {
            sb.append(row).append("\n");
        }
        return sb.toString();
    }

    @McpTool(name = "ticket_create",
            description = "Create an internal work ticket (e.g. reimbursement request). Returns the new ticket id.")
    public String ticketCreate(@McpToolParam(description = "short ticket title") String title,
                               @McpToolParam(description = "ticket content / full request text") String content) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return "rejected: title and content are both required";
        }
        Map<String, String> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        Map<?, ?> result = workers.post()
                .uri("/api/ticket")
                .body(body)
                .retrieve()
                .body(Map.class);
        return "ticket created, id=" + (result == null ? "?" : result.get("ticketId"));
    }
}
