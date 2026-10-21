package com.gateflow.workers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具 HTTP 接口（W3）：gateway 的 @McpTool 经这些端点调用工具实现。
 * 直接复用 worker 方法（RagTasks/DbTasks/TicketTasks）——同一实现服务
 * 两条消费路径：Conductor workflow（@WorkerTask）与网关转发（本 REST 接口）。
 */
@RestController
public class ToolsApiController {

    private final RagTasks ragTasks;
    private final DbTasks dbTasks;
    private final TicketTasks ticketTasks;

    public ToolsApiController(RagTasks ragTasks, DbTasks dbTasks, TicketTasks ticketTasks) {
        this.ragTasks = ragTasks;
        this.dbTasks = dbTasks;
        this.ticketTasks = ticketTasks;
    }

    @GetMapping("/api/search")
    public List<String> search(@RequestParam("q") String q) {
        return ragTasks.retrieve(q);
    }

    @PostMapping("/api/db-query")
    public Map<String, Object> dbQuery(@RequestBody Map<String, String> body) {
        try {
            return Map.of("rows", dbTasks.query(body.get("sql")));
        } catch (IllegalArgumentException e) {
            // 守卫拒绝不是服务器错误：以业务错误返回，让网关工具层拿到可读信息
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/api/ticket")
    public Map<String, Long> ticket(@RequestBody Map<String, String> body) {
        Long id = ticketTasks.create(body.get("title"), body.get("content"));
        return Map.of("ticketId", id);
    }
}
