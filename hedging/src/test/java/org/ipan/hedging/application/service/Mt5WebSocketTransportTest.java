package org.ipan.hedging.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Mt5WebSocketTransportTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);
    private final WebSocket.Builder builder = mock(WebSocket.Builder.class, RETURNS_SELF);
    private final WebSocket socket = mock(WebSocket.class);
    private final AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();

    private Mt5WebSocketTransport transport() {
        when(http.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(), any())).thenAnswer(invocation -> {
            listener.set(invocation.getArgument(1));
            listener.get().onOpen(socket);
            return CompletableFuture.completedFuture(socket);
        });
        return new Mt5WebSocketTransport(URI.create("ws://localhost:8010/ws"), "x".repeat(32), http);
    }

    @Test
    void correlatesFragmentedRepliesAndReusesAuthenticatedConnection() throws Exception {
        try (var transport = transport()) {
            when(socket.sendText(anyString(), eq(true))).thenAnswer(invocation -> {
                var request = mapper.readTree((String) invocation.getArgument(0));
                assertEquals("account-info", request.path("action").asText());
                assertEquals(11, request.path("params").path("account_number").asInt());
                // An unsolicited event must not complete the pending request.
                listener.get().onText(socket, "{\"type\":\"heartbeat\"}", true);
                String reply = mapper.writeValueAsString(Map.of("id", request.path("id").asText(),
                        "ok", true, "result", Map.of("balance", 123.5, "unused_sdk_field", 1)));
                listener.get().onText(socket, reply.substring(0, 12), false);
                listener.get().onText(socket, reply.substring(12), true);
                return CompletableFuture.completedFuture(socket);
            });
            for (int i = 0; i < 2; i++) {
                var result = transport.request("account-info", Map.of("account_number", 11),
                        Mt5ServiceImpl.BalanceDetails.class);
                assertEquals(123.5, result.getBalance());
            }
            verify(builder).header("Authorization", "Bearer " + "x".repeat(32));
            verify(builder, times(1)).buildAsync(any(), any());
            verify(socket, times(2)).sendText(anyString(), eq(true));
        }
    }

    @Test
    void lostConnectionDoesNotRetryAnOrder() {
        try (var transport = transport()) {
            when(socket.sendText(anyString(), eq(true))).thenAnswer(invocation -> {
                listener.get().onError(socket, new RuntimeException("lost connection"));
                return CompletableFuture.completedFuture(socket);
            });
            var error = assertThrows(Mt5WebSocketTransport.GatewayException.class,
                    () -> transport.request("place-short-order", Map.of("volume", 0.1), Object.class));
            assertEquals("CONNECTION_LOST", error.getCode());
            verify(socket, times(1)).sendText(anyString(), eq(true));
        }
    }

    @Test
    void preservesGatewayErrorCode() throws Exception {
        try (var transport = transport()) {
            when(socket.sendText(anyString(), eq(true))).thenAnswer(invocation -> {
                var request = mapper.readTree((String) invocation.getArgument(0));
                String reply = mapper.writeValueAsString(Map.of("id", request.path("id").asText(),
                        "ok", false, "error", Map.of("code", "EXECUTION_UNKNOWN", "message", "Reconcile history")));
                listener.get().onText(socket, reply, true);
                return CompletableFuture.completedFuture(socket);
            });
            var error = assertThrows(Mt5WebSocketTransport.GatewayException.class,
                    () -> transport.request("place-short-order", Map.of(), Object.class));
            assertEquals("EXECUTION_UNKNOWN", error.getCode());
            verify(socket, times(1)).sendText(anyString(), eq(true));
        }
    }
}
