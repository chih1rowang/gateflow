package com.gateflow.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发与校验（HS256）。
 * JWT 结构：header.payload.signature——payload 携带声明（claim），签名用密钥生成，
 * 服务端只凭密钥验签即可确认身份与有效期，无需存储会话（与 Session 的本质区别）。
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlHours;

    public JwtService(@Value("${gateflow.jwt.secret}") String secret,
                      @Value("${gateflow.jwt.ttl-hours}") long ttlHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlHours = ttlHours;
    }

    /** 签发：sub=客户端身份（限流与审计的 key），exp=过期时间 */
    public Map<String, Object> issue(String client) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlHours * 3600);
        String token = Jwts.builder()
                .subject(client)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return Map.of("token", token, "client", client, "expiresAt", exp.toString());
    }

    /** 校验：签名或过期非法抛 JwtException；通过则返回 sub（客户端身份） */
    public String verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
