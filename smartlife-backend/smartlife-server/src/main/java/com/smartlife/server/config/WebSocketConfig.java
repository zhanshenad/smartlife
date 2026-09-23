package com.smartlife.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 支持：把 @ServerEndpoint 注解的端点注册进内嵌容器的扫描器。
 * MOCK 测试环境没有 ServerContainer，注册会直接失败，
 * 所以留了个开关（surefire 的 argLine 统一 -Dsmartlife.ws.enabled=false）。
 */
@Configuration
public class WebSocketConfig {

    @Bean
    @ConditionalOnProperty(name = "smartlife.ws.enabled", havingValue = "true", matchIfMissing = true)
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
