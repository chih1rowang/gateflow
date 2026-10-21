package com.gateflow.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 过滤器 3/3（顺序：Audit -> JWT -> RateLimit，仅拦 /mcp）：
 * Redis 滑动窗口限流（ZSET + Lua 原子执行）。
 * Lua 脚本在 Redis 单线程中一次执行完"清窗外+计数+写入"，天然原子——
 * 这是绕开"先读后写"竞态的标准做法（也是"Redis 为什么快/怎么保证原子"八股的落点）。
 */
@Component
@org.springframework.core.annotation.Order(3)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final DefaultRedisScript<Long> SLIDING_WINDOW = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local max = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= max then
                return -1
            end
            redis.call('ZADD', key, now, member)
            redis.call('EXPIRE', key, window)
            return count + 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long windowSeconds;
    private final long maxRequests;

    public RateLimitFilter(StringRedisTemplate redis,
                           @org.springframework.beans.factory.annotation.Value("${gateflow.ratelimit.window-seconds}") long windowSeconds,
                           @org.springframework.beans.factory.annotation.Value("${gateflow.ratelimit.max-requests}") long maxRequests) {
        this.redis = redis;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = (String) request.getAttribute("client");
        String key = "ratelimit:" + client;
        long now = System.currentTimeMillis();
        Long result = redis.execute(SLIDING_WINDOW,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowSeconds * 1000),
                String.valueOf(maxRequests),
                now + "-" + UUID.randomUUID());
        if (result != null && result == -1) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\",\"window\":\"" + windowSeconds + "s\",\"max\":" + maxRequests + "}");
            return;
        }
        chain.doFilter(request, response);
    }
}
