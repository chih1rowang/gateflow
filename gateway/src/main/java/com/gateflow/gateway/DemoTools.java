package com.gateflow.gateway;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * W1 Day4 风险 spike 的验证工具：证明 @McpTool 注解的方法能被 MCP 客户端
 * 经 Streamable HTTP 端点发现并调用。W3 起替换为三个正式工具
 * （doc_search / db_query / ticket_create）。
 */
@Service
public class DemoTools {

    @McpTool(name = "hello", description = "Spike tool: returns a greeting, used to verify MCP endpoint connectivity")
    public String hello(@McpToolParam(description = "who to greet") String name) {
        return "hello " + name + " from gateflow-gateway";
    }
}
