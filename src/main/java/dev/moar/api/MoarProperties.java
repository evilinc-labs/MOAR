package dev.moar.api;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// Loads and persists API/webhook settings from config/moar/moar.properties.
public final class MoarProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Config");

    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
            .resolve("moar.properties");

    // API server
    private volatile boolean apiEnabled;
    private volatile String apiBindAddress;
    private volatile int apiPort;
    private volatile String apiKey;

    // Outbound webhooks
    private volatile boolean webhookEnabled;
    private volatile String webhookUrl;
    private volatile boolean webhookDiscord;
    private volatile boolean webhookNavigation;
    private volatile boolean webhookDisconnect;
    private volatile boolean webhookArrival;
    private volatile boolean webhookAbort;
    private volatile boolean webhookScan;
    private volatile boolean webhookIncludeCoordinates;

    // Elytra resupply
    private volatile int elytraResupplyCount;

    private MoarProperties() {}

    // Defaults
    private static final boolean API_ENABLED = false;
    private static final String API_BIND = "127.0.0.1";
    private static final int API_PORT = 8585;
    private static final String API_KEY = "";
    private static final String WEBHOOK_URL = "";
    private static final boolean WEBHOOK_DISCORD = false;
    private static final int ELYTRA_RESUPPLY_COUNT = 1;

    public static MoarProperties load() {
        MoarProperties cfg = new MoarProperties();
        Properties props = new Properties();

        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            } catch (IOException e) {
                LOGGER.warn("Failed to read {}, using defaults", FILE, e);
            }
        }

        cfg.apiEnabled = Boolean.parseBoolean(props.getProperty("api.enabled",
                String.valueOf(API_ENABLED)));
        cfg.apiBindAddress = props.getProperty("api.bind", API_BIND);
        cfg.apiPort = parsePort(props.getProperty("api.port",
                String.valueOf(API_PORT)));
        cfg.apiKey = props.getProperty("api.key", API_KEY);
        cfg.webhookUrl = props.getProperty("webhook.url", WEBHOOK_URL).trim();
        cfg.webhookEnabled = Boolean.parseBoolean(props.getProperty("webhook.enabled",
                String.valueOf(!cfg.webhookUrl.isBlank())));
        cfg.webhookDiscord = booleanProperty(props, "webhook.discord", WEBHOOK_DISCORD);
        cfg.webhookNavigation = booleanProperty(props, "webhook.event.navigation", true);
        cfg.webhookDisconnect = booleanProperty(props, "webhook.event.disconnect", true);
        cfg.webhookArrival = booleanProperty(props, "webhook.event.arrival", true);
        cfg.webhookAbort = booleanProperty(props, "webhook.event.abort", true);
        cfg.webhookScan = booleanProperty(props, "webhook.event.scan", true);
        cfg.webhookIncludeCoordinates = booleanProperty(
                props, "webhook.include_coordinates", false);
        try {
            int v = Integer.parseInt(props.getProperty("elytra.resupply.count",
                    String.valueOf(ELYTRA_RESUPPLY_COUNT)));
            cfg.elytraResupplyCount = Math.max(1, Math.min(27, v));
        } catch (NumberFormatException ignored) {
            cfg.elytraResupplyCount = ELYTRA_RESUPPLY_COUNT;
        }

        // Write defaults on first run so users can see the available keys
        if (!Files.exists(FILE)) {
            cfg.save();
        }

        LOGGER.info("Loaded config from {}", FILE);
        return cfg;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties props = new Properties();
            props.setProperty("api.enabled", String.valueOf(apiEnabled));
            props.setProperty("api.bind", apiBindAddress);
            props.setProperty("api.port", String.valueOf(apiPort));
            props.setProperty("api.key", apiKey);
            props.setProperty("webhook.enabled", String.valueOf(webhookEnabled));
            props.setProperty("webhook.url", webhookUrl);
            props.setProperty("webhook.discord", String.valueOf(webhookDiscord));
            props.setProperty("webhook.event.navigation", String.valueOf(webhookNavigation));
            props.setProperty("webhook.event.disconnect", String.valueOf(webhookDisconnect));
            props.setProperty("webhook.event.arrival", String.valueOf(webhookArrival));
            props.setProperty("webhook.event.abort", String.valueOf(webhookAbort));
            props.setProperty("webhook.event.scan", String.valueOf(webhookScan));
            props.setProperty("webhook.include_coordinates",
                    String.valueOf(webhookIncludeCoordinates));
            props.setProperty("elytra.resupply.count", String.valueOf(elytraResupplyCount));

            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "MOAR API / webhook configuration");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", FILE, e);
        }
    }

    // Getters

    public boolean isApiEnabled()    { return apiEnabled; }
    public String getApiBindAddress() { return apiBindAddress; }
    public int getApiPort()          { return apiPort; }
    public String getApiKey()        { return apiKey; }
    public boolean isWebhookEnabled() { return webhookEnabled; }
    public String getWebhookUrl()    { return webhookUrl; }
    public boolean isWebhookDiscord() { return webhookDiscord; }
    public boolean isWebhookNavigation() { return webhookNavigation; }
    public boolean isWebhookDisconnect() { return webhookDisconnect; }
    public boolean isWebhookArrival() { return webhookArrival; }
    public boolean isWebhookAbort() { return webhookAbort; }
    public boolean isWebhookScan() { return webhookScan; }
    public boolean isWebhookIncludeCoordinates() { return webhookIncludeCoordinates; }
    public int getElytraResupplyCount() { return elytraResupplyCount; }
    public boolean hasWebhookUrl() { return webhookUrl != null && !webhookUrl.isBlank(); }

    public boolean isWebhookEventEnabled(WebhookEvent.Type type) {
        return switch (type) {
            case NAVIGATION_CHANGED -> webhookNavigation;
            case TRAVEL_DISCONNECT -> webhookDisconnect;
            case DESTINATION_REACHED -> webhookArrival;
            case TRAVEL_ABORTED -> webhookAbort;
            case STASH_SCAN_COMPLETE -> webhookScan;
            case TEST -> true;
        };
    }

    // Setters (mutate + persist)

    public void setApiEnabled(boolean v) { apiEnabled = v; save(); }
    public void setApiBindAddress(String v) { apiBindAddress = v; save(); }
    public void setApiPort(int v) { apiPort = v; save(); }
    public void setApiKey(String v) { apiKey = v; save(); }
    public void setWebhookEnabled(boolean v) { webhookEnabled = v; save(); }
    public void setWebhookUrl(String v) { webhookUrl = v == null ? "" : v.trim(); save(); }
    public void setWebhookDiscord(boolean v) { webhookDiscord = v; save(); }
    public void setWebhookNavigation(boolean v) { webhookNavigation = v; save(); }
    public void setWebhookDisconnect(boolean v) { webhookDisconnect = v; save(); }
    public void setWebhookArrival(boolean v) { webhookArrival = v; save(); }
    public void setWebhookAbort(boolean v) { webhookAbort = v; save(); }
    public void setWebhookScan(boolean v) { webhookScan = v; save(); }
    public void setWebhookIncludeCoordinates(boolean v) { webhookIncludeCoordinates = v; save(); }
    public void setElytraResupplyCount(int v) { elytraResupplyCount = Math.max(1, Math.min(27, v)); save(); }

    private static boolean booleanProperty(Properties props, String key, boolean fallback) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(fallback)));
    }

    private static int parsePort(String s) {
        try {
            int p = Integer.parseInt(s);
            if (p > 0 && p <= 65535) return p;
        } catch (NumberFormatException ignored) {}
        return API_PORT;
    }
}
