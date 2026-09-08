package com.jjenus.qliina_management.common.config;

import com.jjenus.qliina_management.identity.model.User;
import com.jjenus.qliina_management.identity.repository.UserRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * WebSocket / STOMP configuration.
 *
 * Endpoint   : /ws  (SockJS fallback enabled)
 *
 * Broker prefixes:
 *   /topic  -- broadcast destinations (business-wide feeds)
 *   /queue  -- messages queued for a specific user session
 *   /app    -- prefix for @MessageMapping methods (client -> server)
 *
 * Topic hierarchy (subscribe from the client):
 *   /topic/business.{businessId}.orders     -- order status changes
 *   /topic/business.{businessId}.inventory  -- low-stock alerts
 *   /topic/business.{businessId}.dashboard  -- KPI refresh ticks
 *   /topic/business.{businessId}.quality    -- quality-check updates
 *   /user/queue/notifications               -- user's own in-app notifications
 *
 * User destinations use the canonical '/user' prefix: the STOMP client
 * subscribes to /user/queue/... and the broker (UserDestinationMessageHandler)
 * scopes those subscriptions to the authenticated session principal.
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
    private final UserRepository      userRepository;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Intercept STOMP commands:
     *  - CONNECT: JWT must be present AND valid, otherwise the session is
     *    rejected outright (missing/invalid/expired tokens used to fall
     *    through as an unauthenticated session).
     *  - SUBSCRIBE: tenant scoping. Only /topic/business.{user'sBusinessId}.*
     *    is allowed; /queue/* is user-scoped by the broker. Cross-tenant
     *    subscription attempts are dropped.
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
                    Authentication auth = authenticateConnect(accessor);
                    if (auth == null) {
                        log.warn("WebSocket CONNECT rejected (missing / invalid / expired JWT)");
                        return null; // reject connection
                    }
                    accessor.setUser(auth);
                    return message;
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (accessor.getUser() == null || !canSubscribe(accessor)) {
                        log.warn("WebSocket SUBSCRIBE rejected for destination '{}'",
                                accessor.getDestination());
                        return null; // drop frame
                    }
                }
                return message;
            }
        });
    }

    private Authentication authenticateConnect(StompHeaderAccessor accessor) {
        List<String> auth = accessor.getNativeHeader("Authorization");
        String jwt = (auth != null && !auth.isEmpty()) ? auth.get(0) : null;
        if (jwt == null || !jwt.startsWith("Bearer ")) {
            return null;
        }
        jwt = jwt.substring(7);
        try {
            String username = jwtProvider.extractUsername(jwt);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            if (!jwtProvider.isTokenValid(jwt, user)) {
                log.warn("WebSocket CONNECT token invalid/expired for user: {}", username);
                return null;
            }
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            Object userIdClaim = jwtProvider.extractClaim(jwt, claims -> claims.get("userId"));
            if (userIdClaim != null) {
                authToken.setDetails(UUID.fromString(userIdClaim.toString()));
            }
            return authToken;
        } catch (Exception e) {
            log.warn("WebSocket JWT validation failed: {}", e.getMessage());
            return null; // reject
        }
    }

    private boolean canSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null) return false;
        if (dest.startsWith("/user/")) {
            // User destinations (/user/queue/...) are scoped by the broker to the
            // authenticated session principal, so only the subscriber's own queue
            // is reachable.
            return true;
        }
        if (!dest.startsWith("/topic/business.")) {
            return false;
        }
        String rest = dest.substring("/topic/business.".length());
        int dot = rest.indexOf('.');
        if (dot <= 0) return false;
        UUID businessId;
        try {
            businessId = UUID.fromString(rest.substring(0, dot));
        } catch (IllegalArgumentException e) {
            return false;
        }

        Principal principal = accessor.getUser();
        if (!(principal instanceof Authentication auth) || auth.getName() == null) return false;
        return userRepository.findByIdentity(auth.getName())
                .map(User::getBusinessId)
                .map(businessId::equals)
                .orElse(false);
    }
}
