package dev.moar.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// Serializes outbound webhook delivery away from the client thread.
public final class WebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Webhook");
    private static final WebhookService INSTANCE = new WebhookService();
    private static final int CRITICAL_QUEUE_CAPACITY = 32;
    private static final int ROUTINE_QUEUE_CAPACITY = 96;
    private static final int TIMEOUT_MS = 10_000;

    public record Status(boolean enabled, boolean configured, boolean discord,
                         int queued, long delivered, long failed, long dropped) {}

    private record DeliveryResult(int status, long retryAfterMillis) {}

    private final BlockingQueue<WebhookEvent> criticalQueue =
            new ArrayBlockingQueue<>(CRITICAL_QUEUE_CAPACITY);
    private final BlockingQueue<WebhookEvent> routineQueue =
            new ArrayBlockingQueue<>(ROUTINE_QUEUE_CAPACITY);
    private final AtomicLong delivered = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile MoarProperties config;

    public static WebhookService get() {
        return INSTANCE;
    }

    private WebhookService() {
        Thread.ofVirtual().name("moar-webhook-dispatch").start(this::run);
    }

    public void configure(MoarProperties config) {
        this.config = config;
    }

    public boolean publish(WebhookEvent event) {
        MoarProperties current = config;
        if (current == null || !current.isWebhookEnabled()
                || !current.hasWebhookUrl()
                || !current.isWebhookEventEnabled(event.type())) {
            return false;
        }
        return enqueue(event);
    }

    public boolean sendTest() {
        MoarProperties current = config;
        if (current == null || !current.hasWebhookUrl()) return false;
        return enqueue(WebhookEvent.of(
                WebhookEvent.Type.TEST,
                "MOAR webhook test",
                "Webhook delivery is configured and operational.",
                WebhookEvent.Severity.SUCCESS,
                Map.of("Format", current.isWebhookDiscord() ? "Discord" : "Generic JSON")));
    }

    public Status status() {
        MoarProperties current = config;
        return new Status(
                current != null && current.isWebhookEnabled(),
                current != null && current.hasWebhookUrl(),
                current != null && current.isWebhookDiscord(),
                criticalQueue.size() + routineQueue.size(),
                delivered.get(),
                failed.get(),
                dropped.get());
    }

    public static boolean isDiscordWebhookUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || path == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            boolean discordHost = normalizedHost.equals("discord.com")
                    || normalizedHost.endsWith(".discord.com")
                    || normalizedHost.equals("discordapp.com")
                    || normalizedHost.endsWith(".discordapp.com");
            return discordHost && path.startsWith("/api/webhooks/");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean enqueue(WebhookEvent event) {
        BlockingQueue<WebhookEvent> target = isCritical(event.type())
                ? criticalQueue
                : routineQueue;
        if (target.offer(event)) return true;
        dropped.incrementAndGet();
        LOGGER.warn("Webhook {} queue full; dropped event {}",
                isCritical(event.type()) ? "critical" : "routine", event.type());
        return false;
    }

    private void run() {
        while (true) {
            try {
                WebhookEvent event = criticalQueue.poll();
                if (event == null) {
                    event = routineQueue.poll(250, TimeUnit.MILLISECONDS);
                }
                if (event != null) deliver(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                failed.incrementAndGet();
                LOGGER.warn("Webhook dispatcher recovered from an unexpected failure", e);
            }
        }
    }

    private static boolean isCritical(WebhookEvent.Type type) {
        return type == WebhookEvent.Type.TRAVEL_DISCONNECT
                || type == WebhookEvent.Type.DESTINATION_REACHED
                || type == WebhookEvent.Type.TRAVEL_ABORTED;
    }

    private void deliver(WebhookEvent event) {
        MoarProperties current = config;
        if (current == null || !current.hasWebhookUrl()) {
            dropped.incrementAndGet();
            return;
        }
        if (event.type() != WebhookEvent.Type.TEST
                && (!current.isWebhookEnabled()
                || !current.isWebhookEventEnabled(event.type()))) {
            dropped.incrementAndGet();
            return;
        }

        String configuredUrl = current.getWebhookUrl();
        boolean discord = current.isWebhookDiscord();
        String body = discord
                ? discordPayload(event)
                : genericPayload(event);
        try {
            DeliveryResult result = post(configuredUrl, body, discord);
            if (result.status() == 429 && result.retryAfterMillis() > 0
                    && isCritical(event.type())) {
                Thread.sleep(Math.min(result.retryAfterMillis(), 60_000));
                result = post(configuredUrl, body, discord);
            }
            if (result.status() >= 200 && result.status() < 300) {
                delivered.incrementAndGet();
                LOGGER.info("Webhook delivered event={} status={}", event.type(), result.status());
            } else {
                failed.incrementAndGet();
                LOGGER.warn("Webhook rejected event={} status={}", event.type(), result.status());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            failed.incrementAndGet();
            LOGGER.warn("Webhook delivery failed event={} cause={}",
                    event.type(), e.getClass().getSimpleName());
        }
    }

    private DeliveryResult post(String configuredUrl, String body, boolean discord) throws Exception {
        if (discord && !isDiscordWebhookUrl(configuredUrl)) {
            throw new IOException("invalid Discord webhook URL");
        }
        URI uri = URI.create(discord ? withDiscordConfirmation(configuredUrl) : configuredUrl);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IOException("unsupported webhook scheme");
        }

        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "MOAR-Webhook/1");
            try (var out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            return new DeliveryResult(status, parseRetryAfter(connection.getHeaderField("Retry-After")));
        } finally {
            connection.disconnect();
        }
    }

    private static String withDiscordConfirmation(String url) {
        return url + (url.contains("?") ? "&" : "?") + "wait=true";
    }

    private static long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            double seconds = Double.parseDouble(value);
            return Math.max(0, (long) Math.ceil(seconds * 1000.0));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String genericPayload(WebhookEvent event) {
        StringBuilder json = new StringBuilder(768).append('{');
        field(json, "event", eventName(event.type()));
        field(json, "title", event.title());
        field(json, "message", event.message());
        field(json, "severity", event.severity().name().toLowerCase());
        numberField(json, "timestamp", event.timestamp().toEpochMilli());
        if (event.type() == WebhookEvent.Type.STASH_SCAN_COMPLETE) {
            numericStringField(json, "containers_found", event.fields().get("Found"));
            numericStringField(json, "containers_indexed", event.fields().get("Indexed"));
            numericStringField(json, "containers_failed", event.fields().get("Skipped"));
        }
        json.append("\"fields\":{");
        int index = 0;
        for (Map.Entry<String, String> field : event.fields().entrySet()) {
            if (index++ > 0) json.append(',');
            quoted(json, field.getKey()).append(':');
            quoted(json, field.getValue());
        }
        json.append("}}");
        return json.toString();
    }

    private static String eventName(WebhookEvent.Type type) {
        return type == WebhookEvent.Type.STASH_SCAN_COMPLETE
                ? "scan_complete"
                : type.name().toLowerCase(Locale.ROOT);
    }

    private static String discordPayload(WebhookEvent event) {
        StringBuilder json = new StringBuilder(1024)
                .append("{\"username\":\"MOAR\",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{");
        field(json, "title", event.title());
        field(json, "description", event.message());
        numberField(json, "color", event.severity().color());
        field(json, "timestamp", event.timestamp().toString());
        json.append("\"fields\":[");
        int index = 0;
        for (Map.Entry<String, String> field : event.fields().entrySet()) {
            if (index++ > 0) json.append(',');
            json.append('{');
            field(json, "name", field.getKey());
            field(json, "value", field.getValue());
            json.append("\"inline\":true}");
        }
        json.append("]}]}");
        return json.toString();
    }

    private static void field(StringBuilder json, String key, String value) {
        quoted(json, key).append(':');
        quoted(json, value);
        json.append(',');
    }

    private static void numberField(StringBuilder json, String key, long value) {
        quoted(json, key).append(':').append(value).append(',');
    }

    private static void numericStringField(StringBuilder json, String key, String value) {
        try {
            numberField(json, key, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            numberField(json, key, 0);
        }
    }

    private static StringBuilder quoted(StringBuilder json, String value) {
        json.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> json.append("\\\"");
                    case '\\' -> json.append("\\\\");
                    case '\b' -> json.append("\\b");
                    case '\f' -> json.append("\\f");
                    case '\n' -> json.append("\\n");
                    case '\r' -> json.append("\\r");
                    case '\t' -> json.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            json.append(String.format("\\u%04x", (int) c));
                        } else {
                            json.append(c);
                        }
                    }
                }
            }
        }
        return json.append('"');
    }
}
