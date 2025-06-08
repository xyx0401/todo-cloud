package com.example.gatewayservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;

/**
 * 网关配置类
 * 提供全局过滤器和CORS配置
 */
@Configuration
public class GatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    /**
     * 全局请求日志过滤器
     */
    @Bean
    public GlobalFilter customGlobalFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            String method = request.getMethod().toString();
            String remoteAddress = request.getRemoteAddress() != null ? 
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
            
            log.info("Gateway处理请求: {} {} from {}", method, path, remoteAddress);
            
            // 记录请求开始时间
            long startTime = System.currentTimeMillis();
            
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                long endTime = System.currentTimeMillis();
                ServerHttpResponse response = exchange.getResponse();
                HttpStatus statusCode = response.getStatusCode();
                
                log.info("Gateway完成请求: {} {} -> {} ({}ms)", 
                    method, path, statusCode, (endTime - startTime));
            }));
        };
    }

    /**
     * Session转发过滤器
     * 确保Session信息正确转发到后端服务
     */
    @Bean
    public GlobalFilter sessionForwardFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            // 确保Cookie和Session相关头部正确转发
            ServerHttpRequest.Builder builder = request.mutate();
            
            // 添加一些调试头部
            builder.header("X-Gateway-Timestamp", String.valueOf(System.currentTimeMillis()));
            builder.header("X-Gateway-Path", request.getPath().value());
            
            return chain.filter(exchange.mutate().request(builder.build()).build());
        };
    }

    /**
     * CORS配置
     * 解决跨域问题
     */
    @Bean
    public CorsWebFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的源
        config.addAllowedOriginPattern("*");
        
        // 允许的方法
        config.setAllowedMethods(Arrays.asList(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
        ));
        
        // 允许的头部
        config.addAllowedHeader("*");
        
        // 允许携带认证信息
        config.setAllowCredentials(true);
        
        // 预检请求的缓存时间
        config.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsWebFilter(source);
    }

    /**
     * 错误处理过滤器
     */
    @Bean
    public GlobalFilter errorHandlingFilter() {
        return (exchange, chain) -> {
            return chain.filter(exchange)
                .onErrorResume(throwable -> {
                    log.error("Gateway处理请求时发生错误: {}", throwable.getMessage(), throwable);
                    
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                    response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
                    
                    String errorMessage = "{\"error\":\"网关处理请求失败\",\"message\":\"" + 
                        throwable.getMessage() + "\"}";
                    
                    return response.writeWith(Mono.just(
                        response.bufferFactory().wrap(errorMessage.getBytes())
                    ));
                });
        };
    }
} 