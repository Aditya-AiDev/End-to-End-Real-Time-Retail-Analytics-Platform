package com.apex.storeintelligence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocketConfig
 * ================
 * WHY STOMP over raw WebSocket:
 *   STOMP gives us topic-based pub/sub (/topic/events/{storeId},
 *   /topic/anomalies/{storeId}) without writing routing code manually.
 *   The React dashboard subscribes to these topics for live updates.
 *
 * WHY in-memory broker (not RabbitMQ/Kafka):
 *   For the challenge scope, in-memory is sufficient. Production would use
 *   RabbitMQ as the STOMP broker for horizontal scaling.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry r) {
        r.enableSimpleBroker("/topic");       // server → client broadcasts
        r.setApplicationDestinationPrefixes("/app"); // client → server messages
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry r) {
        r.addEndpoint("/ws")
         .setAllowedOriginPatterns("*")
         .withSockJS();   // SockJS fallback for browsers that block WebSocket
    }
}


/**
 * CorsConfig — allows React dev server (port 3000) to call Spring Boot (port 8080)
 */
@Configuration
class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry r) {
        r.addMapping("/**")
         .allowedOriginPatterns("*")
         .allowedMethods("GET","POST","PUT","DELETE","OPTIONS")
         .allowedHeaders("*")
         .allowCredentials(true);
    }
}
