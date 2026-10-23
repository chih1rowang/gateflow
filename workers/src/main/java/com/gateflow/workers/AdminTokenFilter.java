package com.gateflow.workers;

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
 * 管理口令过滤器：/admin/**（知识库热重载）与 /stream/**（流式问答，每次调用消耗
 * DeepSeek 余额）必须携带 X-Admin-Token 请求头（审查报告严重 1 的组成部分）。
 * /api/** 不在本过滤器范围内——工具接口只允许网关经容器内部网络调用，
 * nginx 不转发 /api/、compose 不向宿主机发布 workers 端口，双层保证外部不可达。
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
        String uri = request.getRequestURI();
        return !(uri.startsWith("/admin/") || uri.startsWith("/stream/"));
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
