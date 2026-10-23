package com.gateflow.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 过滤器 0/4（顺序：AdminToken -> Audit -> JWT -> RateLimit）：
 * 管理端点（/admin/**：令牌签发/审计流水/统计）必须携带 X-Admin-Token 请求头，
 * 值等于部署时配置的 gateflow.admin-token。此前 /admin/token 无鉴权经 nginx 公开，
 * 任何人可铸造合法 JWT——审查报告严重 2 的修复。
 * 比较用 MessageDigest.isEqual（恒定时间）：逐字节比较不因前缀正确提前返回，
 * 防侧信道计时推断口令。
 */
@Component
@Order(0)
public class AdminTokenFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public AdminTokenFilter(@Value("${gateflow.admin-token:dev-admin-token}") String adminToken) {
        this.expected = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader("X-Admin-Token");
        byte[] given = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, expected)) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"admin token required\",\"header\":\"X-Admin-Token\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
