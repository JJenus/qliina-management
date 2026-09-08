package com.jjenus.qliina_management.integration;

import com.jjenus.qliina_management.common.websocket.WebSocketPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real-socket STOMP authorization checks against the WebSocketConfig channel
 * interceptor:
 *  - CONNECT without / with an invalid JWT is rejected (no session).
 *  - SUBSCRIBE is tenant-scoped: own /topic/business.{id}.* works, a cross-
 *    tenant destination is dropped.
 *
 * Runs on a random port so WebSocketStompClient can talk to the real server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketAuthIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    WebSocketPublisher webSocketPublisher;

    @Test
    void connect_withoutToken_isRejected() {
        assertThrows(Exception.class, () ->
                stompClient().connectAsync("http://localhost:" + port + "/ws",
                                (org.springframework.web.socket.WebSocketHttpHeaders) null,
                                (StompHeaders) null,
                                new StompSessionHandlerAdapter() {})
                        .get(8, TimeUnit.SECONDS));
    }

    @Test
    void connect_withInvalidToken_isRejected() {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer not-a-real-token");
        assertThrows(Exception.class, () ->
                stompClient().connectAsync("http://localhost:" + port + "/ws",
                                (org.springframework.web.socket.WebSocketHttpHeaders) null,
                                headers,
                                new StompSessionHandlerAdapter() {})
                        .get(8, TimeUnit.SECONDS));
    }

    @Test
    void ownTenantTopic_receivesMessages_butForeignTenantTopic_isDropped() throws Exception {
        AuthContext ownerA = registerBusinessAndOwner();
        AuthContext ownerB = registerBusinessAndOwner();

        StompSession sessionA = connect(ownerA.accessToken());

        // Positive control: A subscribes to A's own topic and receives the ping.
        // (The Spring 7 simple broker does not send RECEIPT frames, so delivery is
        // proven functionally by a send-until-received loop.)
        BlockingQueue<String> ownInbox = new LinkedBlockingQueue<>();
        sessionA.subscribe("/topic/business." + ownerA.businessId() + ".orders", stringHandler(ownInbox));
        String pingA = "ping-" + counter.incrementAndGet();
        String received = deliverAndAwaitAck(
                "/topic/business." + ownerA.businessId() + ".orders", pingA, ownInbox);
        assertEquals(pingA, received);

        // Cross-tenant: A subscribes to B's orders topic. The interceptor drops
        // the frame, so A must never receive B's events.
        BlockingQueue<String> foreignInbox = new LinkedBlockingQueue<>();
        sessionA.subscribe("/topic/business." + ownerB.businessId() + ".orders",
                stringHandler(foreignInbox));
        Thread.sleep(1000); // give the (would-be) subscribe frame time to land server side
        String pingB = "ping-" + counter.incrementAndGet();
        messagingTemplate.convertAndSend(
                "/topic/business." + ownerB.businessId() + ".orders", pingB);
        assertNull(foreignInbox.poll(3, TimeUnit.SECONDS));

        // A's own topic still works after the rejected foreign subscribe.
        String pingA2 = "ping-" + counter.incrementAndGet();
        assertEquals(pingA2, deliverAndAwaitAck(
                "/topic/business." + ownerA.businessId() + ".orders", pingA2, ownInbox));

        sessionA.disconnect();
    }

    @Test
    void queueNotifications_userScoped_isDeliveredByUsername() throws Exception {
        AuthContext owner = registerBusinessAndOwner();
        StompSession session = connect(owner.accessToken());

        BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/notifications", stringHandler(inbox));

        // Exercises the real application path: WebSocketPublisher resolves the
        // userId to the STOMP principal name (username) and delivers to the
        // user-scoped queue.
        String payload = "notice-" + counter.incrementAndGet();
        String received = deliverToUserAndAwaitAck(owner.userId(), payload, inbox);
        assertEquals(payload, received);

        session.disconnect();
    }

    /** Publishes and waits up to ~5s for delivery; returns the received payload. */
    private String deliverAndAwaitAck(String destination, String expected,
                                      BlockingQueue<String> inbox) throws InterruptedException {
        for (int attempt = 0; attempt < 10; attempt++) {
            messagingTemplate.convertAndSend(destination, expected);
            String got = inbox.poll(500, TimeUnit.MILLISECONDS);
            if (got != null) return got;
        }
        return null;
    }

    /** Publishes a user-scoped notification via the real publisher and awaits it. */
    private String deliverToUserAndAwaitAck(java.util.UUID userId, String expected,
                                            BlockingQueue<String> inbox) throws InterruptedException {
        for (int attempt = 0; attempt < 10; attempt++) {
            webSocketPublisher.publishUserNotification(userId, expected);
            String got = inbox.poll(500, TimeUnit.MILLISECONDS);
            if (got != null) return got;
        }
        return null;
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient()
                .connectAsync("http://localhost:" + port + "/ws",
                        (org.springframework.web.socket.WebSocketHttpHeaders) null,
                        headers,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);
    }

    private WebSocketStompClient stompClient() {
        org.springframework.web.socket.sockjs.client.SockJsClient sockJs = new org.springframework.web.socket.sockjs.client.SockJsClient(
                java.util.List.of(new org.springframework.web.socket.sockjs.client.WebSocketTransport(
                        new StandardWebSocketClient())));
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJs);
        stompClient.setMessageConverter(new org.springframework.messaging.converter.StringMessageConverter());
        return stompClient;
    }

    private StompFrameHandler stringHandler(BlockingQueue<String> inbox) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.offer((String) payload);
            }
        };
    }
}