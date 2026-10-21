package com.gateflow.gateway;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发用令牌签发端点（W3）：演示"网关签发-客户端持有-每次携带"的令牌流转。
 * 生产环境此端点应加管理员鉴权（当前为本地开发便利保留）。
 */
@RestController
public class TokenController {

    private final JwtService jwtService;

    public TokenController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/admin/token")
    public Map<String, Object> token(@RequestParam("client") String client) {
        return jwtService.issue(client);
    }
}
