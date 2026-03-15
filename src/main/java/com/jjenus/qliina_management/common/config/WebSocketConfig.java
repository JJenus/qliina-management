package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.identity.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.UUID;

/**
 * WebSocket / STOMP configuration.
 *
 * Endpoint   : /ws  (SockJS fallback enabled)
 *
 * Broker prefixes:
 *   /topic  -- broadcast destinations (business-wide feeds)
 *   /queue  -- user-specific destinations
 *   /app    -- prefix for @MessageMapping methods (client -> server)
 *
 * Topic hierarchy (subscribe from the client):
 *   /topic/business.{businessId}.orders     -- order status changes
 *   /topic/business.{businessId}.inventory  -- low-stock alerts
 *   /topic/business.{businessId}.dashboard  -- KPI refresh ticks
 *   /topic/business.{businessId}.quality    -- quality-check updates
 *   /queue/notifications                    -- user's own in-app notifications
 *
 * Authentication: JWT token in the STOMP CONNECT frame under
 * the 'Authorization' header (Bearer <token>).
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtProvider        jwtProvider;
    private final UserDetailsService  userDetailsService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/queue");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Intercept STOMP CONNECT to validate JWT and set the Spring Security
     * principal. All subsequent SEND/SUBSCRIBE commands inherit this principal.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> auth = accessor.getNativeHeader("Authorization");
                    String jwt = (auth != null && !auth.isEmpty()) ? auth.get(0) : null;

                    if (jwt == null || !jwt.startsWith("Bearer ")) {
                        log.warn("WebSocket CONNECT without Authorization header -- rejected");
                        return null; // reject connection
                    }

                    jwt = jwt.substring(7);
                    try {
                        String username = jwtProvider.extractUsername(jwt);
                        if (username != null) {
                            UserDetails user = userDetailsService.loadUserByUsername(username);
                            if (jwtProvider.isTokenValid(jwt, user)) {
                                UsernamePasswordAuthenticationToken authToken =
                                        new UsernamePasswordAuthenticationToken(
                                                user, null, user.getAuthorities());
                                Object userIdClaim = jwtProvider.extractClaim(
                                        jwt, claims -> claims.get("userId"));
                                if (userIdClaim != null) {
                                    authToken.setDetails(UUID.fromString(userIdClaim.toString()));
                                }
                                accessor.setUser(authToken);
                                log.debug("WebSocket CONNECT authenticated: {}", username);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("WebSocket JWT validation failed: {}", e.getMessage());
                        return null; // reject
                    }
                }
                return message;
            }
        });
    }
}
