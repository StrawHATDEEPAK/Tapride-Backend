package com.tapride.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS (not raw WebSocket) - SockJS gives automatic fallback to
 * long-polling for any environment/proxy that blocks WebSocket upgrades, which
 * matters more for a demo running behind whatever network setup a viewer has
 * than it would in a controlled production environment.
 *
 * Two broadcast destinations, both fed by the same relay:
 *   /topic/events        - the full firehose, every event from every ride -
 *                           this is what a dashboard-style "live order feed" subscribes to
 *   /topic/rides/{rideId} - just one ride's events - this is what a single
 *                           ride's detail view / map subscribes to, so the
 *                           frontend doesn't have to filter the firehose client-side
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker - fine for a single-instance demo service;
        // a multi-instance deployment would need a real broker relay (e.g.
        // RabbitMQ STOMP plugin) so messages reach clients connected to a
        // DIFFERENT instance than the one that received the Kafka message.
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // demo project - loosened deliberately, not for production
                .withSockJS();
    }
}
