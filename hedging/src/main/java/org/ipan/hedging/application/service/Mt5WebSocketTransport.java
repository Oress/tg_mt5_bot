package org.ipan.hedging.application.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** A persistent connection to one terminal. Requests are never retried implicitly. */
public final class Mt5WebSocketTransport implements AutoCloseable {
    private final URI uri;
    private final String token;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private Connection connection;

    public Mt5WebSocketTransport(URI uri, String token) {
        this(uri, token, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    Mt5WebSocketTransport(URI uri, String token, HttpClient client) {
        if (token == null || token.length() < 32) {
            throw new IllegalArgumentException("APP_MT5_GATEWAY_TOKEN must contain at least 32 characters");
        }
        if (!"ws".equals(uri.getScheme()) && !"wss".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Expected a ws:// or wss:// gateway URL");
        }
        this.uri = uri;
        this.token = token;
        this.client = client;
    }

    public synchronized <T> T request(String action, Object params, Class<T> resultType) {
        String id = UUID.randomUUID().toString();
        try {
            if (connection == null || connection.closed) {
                Connection next = new Connection();
                next.socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token).buildAsync(uri, next)
                        .get(10, TimeUnit.SECONDS);
                connection = next;
            }
            CompletableFuture<JsonNode> response = new CompletableFuture<>();
            connection.pending.put(id, response);
            String payload = mapper.writeValueAsString(Map.of("id", id, "action", action, "params", params));
            connection.socket.sendText(payload, true).get(10, TimeUnit.SECONDS);
            JsonNode reply = response.get(40, TimeUnit.SECONDS);
            if (!reply.path("ok").asBoolean()) {
                throw new GatewayException(reply.path("error").path("code").asText("GATEWAY_ERROR"),
                        reply.path("error").path("message").asText("Gateway rejected the request"));
            }
            return mapper.treeToValue(reply.get("result"), resultType);
        } catch (GatewayException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            close();
            throw new GatewayException("CONNECTION_LOST", "MT5 request interrupted; reconcile any submitted order");
        } catch (Exception error) {
            close();
            throw new GatewayException("CONNECTION_LOST", "MT5 response unavailable; reconcile any submitted order before retrying");
        } finally {
            if (connection != null) connection.pending.remove(id);
        }
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            connection.fail();
            if (connection.socket != null) connection.socket.abort();
            connection = null;
        }
    }

    public static final class GatewayException extends RuntimeException {
        private final String code;

        GatewayException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() { return code; }
    }

    private final class Connection implements WebSocket.Listener {
        private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        private final StringBuilder buffer = new StringBuilder();
        private volatile boolean closed;
        private WebSocket socket;

        @Override
        public void onOpen(WebSocket socket) {
            this.socket = socket;
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            try {
                if (buffer.length() + text.length() > 4 * 1024 * 1024) {
                    throw new IllegalStateException("MT5 response too large");
                }
                buffer.append(text);
                if (last) {
                    JsonNode reply = mapper.readTree(buffer.toString());
                    buffer.setLength(0);
                    CompletableFuture<JsonNode> result = pending.remove(reply.path("id").asText());
                    if (result != null) result.complete(reply);
                }
                socket.request(1);
            } catch (Exception error) {
                fail();
                socket.abort();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            fail();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) { fail(); }

        private void fail() {
            closed = true;
            pending.values().forEach(future -> future.completeExceptionally(
                    new IllegalStateException("MT5 connection closed")));
            pending.clear();
        }
    }
}
