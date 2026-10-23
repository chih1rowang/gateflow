package com.gateflow.gateway;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面板数据接口（W5）：审计流水分页查询 + 调用/成本统计聚合。
 * 面板经 nginx 访问 /admin/audit 与 /admin/stats（同源免跨域）。
 */
@RestController
@RequestMapping("/admin")
public class PanelController {

    private final JdbcTemplate jdbc;

    public PanelController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 审计流水：分页 + 可选按客户端过滤，倒序（page/size 夹取边界：page<1 按 1，size 夹 1..200） */
    @GetMapping("/audit")
    public Map<String, Object> audit(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String client) {
        page = Math.max(1, page);
        size = Math.min(200, Math.max(1, size));
        String where = (client == null || client.isBlank()) ? "" : " WHERE client_name = ?";
        Object[] args = (client == null || client.isBlank())
                ? new Object[]{size, (page - 1) * size}
                : new Object[]{client, size, (page - 1) * size};

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs" + where, Long.class,
                (client == null || client.isBlank()) ? new Object[]{} : new Object[]{client});
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, client_name, http_status, rpc_method, tool_name, rejected_reason, latency_ms, created_at "
                        + "FROM audit_logs" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args);
        return Map.of("total", total == null ? 0 : total, "rows", rows);
    }

    /** 统计：调用总量/按工具/按客户端/平均耗时 + LLM token 用量 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> out = new HashMap<>();
        out.put("totalCalls", jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class));
        out.put("rejected", jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE rejected_reason != 'none'", Long.class));
        out.put("avgLatencyMs", jdbc.queryForObject(
                "SELECT COALESCE(ROUND(AVG(latency_ms)),0) FROM audit_logs", Long.class));
        out.put("byTool", jdbc.queryForList(
                "SELECT COALESCE(tool_name, rpc_method) AS name, COUNT(*) AS calls, ROUND(AVG(latency_ms)) AS avg_ms "
                        + "FROM audit_logs GROUP BY name ORDER BY calls DESC"));
        out.put("byClient", jdbc.queryForList(
                "SELECT client_name, COUNT(*) AS calls FROM audit_logs GROUP BY client_name ORDER BY calls DESC LIMIT 10"));
        Map<String, Object> llm = new HashMap<>();
        llm.put("calls", jdbc.queryForObject("SELECT COUNT(*) FROM llm_usage", Long.class));
        llm.put("inputTokens", jdbc.queryForObject("SELECT COALESCE(SUM(input_tokens),0) FROM llm_usage", Long.class));
        llm.put("outputTokens", jdbc.queryForObject("SELECT COALESCE(SUM(output_tokens),0) FROM llm_usage", Long.class));
        llm.put("totalTokens", jdbc.queryForObject("SELECT COALESCE(SUM(total_tokens),0) FROM llm_usage", Long.class));
        out.put("llm", llm);
        return out;
    }
}
