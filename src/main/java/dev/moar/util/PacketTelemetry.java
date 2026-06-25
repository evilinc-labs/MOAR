package dev.moar.util;

import dev.moar.MoarMod;
import dev.moar.world.SetbackMonitor;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
/*?}*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Short-lived packet trace for debugging anti-cheat placement rollbacks.
public final class PacketTelemetry {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Packets");
    private static final String TRACE_BUILD = "fix22-placement-queue";
    private static final int MAX_EVENTS = 768;
    private static final int MAX_FIELDS = 14;
    private static final int MAX_VALUE_LENGTH = 220;
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> QUIET_PACKET_NAMES = Set.of(
            "class_2859", // advancement tab noise
            "class_6374", // recipe/advancement acknowledgement noise
            "class_9836"  // recipe book noise
    );

    private static final Event[] EVENTS = new Event[MAX_EVENTS];
    private static boolean enabled;
    private static long sequence;
    private static int head;
    private static int size;
    private static long lastTick = Long.MIN_VALUE;

    private record Event(long sequence, long tick, long deltaTicks, String line) {}

    private PacketTelemetry() {}

    public static void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            mark("enabled build=" + TRACE_BUILD);
        } else {
            mark("disabled");
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int size() {
        return size;
    }

    public static void clear() {
        for (int i = 0; i < EVENTS.length; i++) {
            EVENTS[i] = null;
        }
        head = 0;
        size = 0;
        sequence = 0;
        lastTick = Long.MIN_VALUE;
        LOGGER.info("[PacketTrace] cleared");
    }

    public static void mark(String label) {
        if (!enabled && size == 0) {
            return;
        }
        append("MARK " + safe(label), true);
    }

    public static void markSetback(int totalSetbacks, int ticksSinceSetback) {
        if (!enabled) {
            return;
        }
        append("SETBACK total=" + totalSetbacks + " calmTicks=" + ticksSinceSetback, true);
    }

    public static void recordOutgoing(Object packet) {
        if (!enabled || packet == null) {
            return;
        }
        if (shouldSuppress(packet)) {
            return;
        }
        append("OUT " + describePacket(packet), false);
    }

    public static void recordIncoming(Object packet) {
        if (!enabled || packet == null) {
            return;
        }
        if (!isPlacementFeedbackPacket(packet)) {
            return;
        }
        append("IN " + describePacket(packet), false);
    }

    public static Path dumpToFile() throws IOException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("moar");
        Files.createDirectories(dir);
        Path file = dir.resolve("packet-trace-" + LocalDateTime.now().format(FILE_TS) + ".log");
        Files.writeString(file, dumpText());
        LOGGER.info("[PacketTrace] wrote {}", file);
        return file;
    }

    public static String dumpText() {
        StringBuilder out = new StringBuilder(32_768);
        out.append("MOAR packet trace events=").append(size)
                .append(" enabled=").append(enabled).append('\n');
        for (Event event : snapshot()) {
            out.append('#').append(event.sequence)
                    .append(" t=").append(event.tick)
                    .append(" dt=").append(event.deltaTicks)
                    .append(' ')
                    .append(event.line)
                    .append('\n');
        }
        return out.toString();
    }

    private static List<Event> snapshot() {
        ArrayList<Event> result = new ArrayList<>(size);
        int start = (head - size + EVENTS.length) % EVENTS.length;
        for (int i = 0; i < size; i++) {
            Event event = EVENTS[(start + i) % EVENTS.length];
            if (event != null) {
                result.add(event);
            }
        }
        return result;
    }

    private static void append(String message, boolean logLive) {
        long tick = currentTick();
        long delta = lastTick == Long.MIN_VALUE || tick < 0 || lastTick < 0
                ? 0
                : tick - lastTick;
        lastTick = tick;
        String context = context();
        String line = context.isEmpty() ? message : message + " | " + context;
        Event event = new Event(++sequence, tick, delta, line);
        EVENTS[head] = event;
        head = (head + 1) % EVENTS.length;
        if (size < EVENTS.length) {
            size++;
        }
        if (logLive) {
            LOGGER.info("[PacketTrace] #{} t={} dt={} {}", event.sequence, event.tick,
                    event.deltaTicks, event.line);
        }
    }

    private static String describePacket(Object packet) {
        String className = packet.getClass().getName();
        String simpleName = packet.getClass().getSimpleName();
        String type = packetType(packet);
        String fields = fieldSummary(packet);
        String text = safe(packet.toString());
        StringBuilder sb = new StringBuilder(512);
        sb.append(simpleName.isEmpty() ? className : simpleName);
        if (!type.isEmpty()) {
            sb.append(" type=").append(type);
        }
        if (!fields.isEmpty()) {
            sb.append(" fields={").append(fields).append('}');
        }
        if (!text.isEmpty() && !text.equals(simpleName)) {
            sb.append(" str=").append(text);
        }
        return trim(sb.toString());
    }

    private static boolean shouldSuppress(Object packet) {
        String simpleName = packet.getClass().getSimpleName();
        return QUIET_PACKET_NAMES.contains(simpleName);
    }

    private static boolean isPlacementFeedbackPacket(Object packet) {
        String className = packet.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        String simpleName = packet.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        String type = packetType(packet).toLowerCase(java.util.Locale.ROOT);
        return className.contains("blockchangedack")
                || className.contains("blockupdate")
                || className.contains("sectionblocksupdate")
                || simpleName.equals("class_4463")
                || simpleName.equals("class_2626")
                || simpleName.equals("class_2637")
                || type.contains("block_changed_ack")
                || type.contains("block_update")
                || type.contains("section_blocks_update");
    }

    private static String packetType(Object packet) {
        Object value = invokeNoArg(packet, "getPacketType");
        if (value == null) {
            value = invokeNoArg(packet, "getType");
        }
        return value == null ? "" : safe(value.toString());
    }

    private static String fieldSummary(Object packet) {
        StringBuilder sb = new StringBuilder(384);
        int count = 0;
        for (Class<?> type = packet.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (count >= MAX_FIELDS) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append("...");
                    return sb.toString();
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(packet);
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(field.getName()).append('=').append(describeValue(value));
                    count++;
                } catch (Throwable ignored) {
                    // Some packet internals are intentionally inaccessible.
                }
            }
        }
        return sb.toString();
    }

    private static Object invokeNoArg(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Try superclass.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        Class<?> type = value.getClass();
        if (type.isEnum() || value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
            return trim(String.valueOf(value));
        }
        if (isBlockHitResult(type)) {
            String fields = nestedFieldSummary(value, 8);
            if (!fields.isEmpty()) {
                return trim(type.getSimpleName() + '{' + fields + '}');
            }
        }
        String className = type.getSimpleName();
        String text = safe(value.toString());
        if (text.isEmpty() || text.equals(className)) {
            return className;
        }
        return trim(text);
    }

    private static boolean isBlockHitResult(Class<?> type) {
        String name = type.getName();
        String simple = type.getSimpleName();
        return "class_3965".equals(simple) || name.endsWith("BlockHitResult");
    }

    private static String nestedFieldSummary(Object value, int maxFields) {
        StringBuilder sb = new StringBuilder(192);
        int count = 0;
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (count >= maxFields) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append("...");
                    return sb.toString();
                }
                try {
                    field.setAccessible(true);
                    Object nested = field.get(value);
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(field.getName()).append('=').append(trim(safe(String.valueOf(nested))));
                    count++;
                } catch (Throwable ignored) {
                    // Leave inaccessible nested fields out of the compact trace.
                }
            }
        }
        return sb.toString();
    }

    private static String context() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        if (mc == null || mc.player == null) {
            return "";
        }
        SetbackMonitor setbacks = SetbackMonitor.get();
        String printerState = "";
        if (MoarMod.getPrinter() != null) {
            printerState = " printer=" + MoarMod.getPrinter().getAutoStateName();
        }
        return "phase=" + PlacementEngine.getPhase()
                + printerState
                + " calm=" + setbacks.ticksSinceSetback()
                + " setbacks=" + setbacks.totalSetbacks()
                + " pos=" + fmt(mc.player.getX()) + "," + fmt(mc.player.getY()) + "," + fmt(mc.player.getZ())
                /*? if >=26.1 {*//*
                + " yaw=" + fmt(mc.player.getYRot())
                + " pitch=" + fmt(mc.player.getXRot());
                *//*?} else {*/
                + " yaw=" + fmt(mc.player.getYaw())
                + " pitch=" + fmt(mc.player.getPitch());
                /*?}*/
    }

    private static long currentTick() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return -1L;
        return mc.level.getGameTime();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return -1L;
        return mc.world.getTime();
        /*?}*/
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String trim(String value) {
        if (value.length() <= MAX_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }
}
