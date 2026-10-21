package com.gateflow.gateway;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 过滤器 2/3（顺序：Audit -> JWT -> RateLimit，仅拦 /mcp）：
 * 校验 Authorization: Bearer <JWT>，通过后把客户端身份放入请求属性供下游使用。
 */
@Component
@org.springframework.core.annotation.Order(2)
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            write401(response, "missing token");
            return;
        }
        try {
            String client = jwtService.verify(header.substring(7));
            request.setAttribute("client", client);
        } catch (JwtException | IllegalArgumentException e) {
            write401(response, "invalid or expired token");
            return;
        }
        chain.doFilter(request, response);
    }

    private void write401(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"unauthorized\",\"reason\":\"" + reason + "\"}");
    }
}
