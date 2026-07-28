package dev.moar.api;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Immutable event accepted by the outbound webhook API.
public record WebhookEvent(
        Type type,
        String title,
        String message,
        Severity severity,
        Map<String, String> fields,
        Instant timestamp
) {
    public enum Type {
        NAVIGATION_CHANGED,
        TRAVEL_DISCONNECT,
        DESTINATION_REACHED,
        TRAVEL_ABORTED,
        STASH_SCAN_COMPLETE,
        TEST
    }

    public enum Severity {
        INFO(0x3498DB),
        SUCCESS(0x2ECC71),
        WARNING(0xF1C40F),
        ERROR(0xE74C3C);

        private final int color;

        Severity(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }
    }

    public WebhookEvent {
        if (type == null) throw new IllegalArgumentException("type");
        if (severity == null) severity = Severity.INFO;
        title = sanitize(title, 256);
        message = sanitize(message, 4096);
        timestamp = timestamp == null ? Instant.now() : timestamp;

        Map<String, String> safeFields = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((name, value) -> {
                if (safeFields.size() >= 25) return;
                String safeName = sanitize(name, 256);
                String safeValue = sanitize(value, 1024);
                if (!safeName.isBlank() && !safeValue.isBlank()) {
                    safeFields.put(safeName, safeValue);
                }
            });
        }
        fields = Collections.unmodifiableMap(safeFields);
    }

    public static WebhookEvent of(Type type, String title, String message,
                                  Severity severity, Map<String, String> fields) {
        return new WebhookEvent(type, title, message, severity, fields, Instant.now());
    }

    private static String sanitize(String value, int limit) {
        if (value == null) return "";
        String clean = value.replace('\u0000', ' ').strip();
        return clean.length() <= limit ? clean : clean.substring(0, limit);
    }
}
