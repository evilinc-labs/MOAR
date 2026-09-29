package dev.moar.util;

import dev.moar.MoarMod;
import dev.moar.travel.TravelManager;
import dev.moar.world.SetbackMonitor;
import net.fabricmc.loader.api.FabricLoader;
/*? if >=26.1 {*//*
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
*//*?} else {*/
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
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

// Trace packet ordering during server validation failures.
public final class PacketTelemetry {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Packets");
    private static final String TRACE_BUILD = "bounce-ground-handoff-trace";
    // Movement packets arrive every tick; retain several minutes of travel.
    private static final int MAX_EVENTS = 20_000;
    private static final int MAX_FIELDS = 14;
    private static final int MAX_VALUE_LENGTH = 220;
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> QUIET_PACKET_NAMES = Set.of(
            "class_2859",
            "class_6374",
            "class_9836"
    );

    private static final Event[] EVENTS = new Event[MAX_EVENTS];
    public enum Mode { GENERAL, TRAVEL }

    private static volatile boolean enabled;
    private static volatile Mode mode = Mode.GENERAL;
    private static long sequence;
    private static volatile long dropped;
    private static volatile long captureStartNanos = System.nanoTime();
    private static int head;
    private static int size;
    private static long lastTick = Long.MIN_VALUE;

    private record Event(long sequence, long tick, long deltaTicks, long elapsedMs, String line) {}

    private PacketTelemetry() {}

    public static void setEnabled(boolean value) {
        if (value) mode = Mode.GENERAL;
        enabled = value;
        if (value) {
            mark("enabled mode=" + mode + " build=" + TRACE_BUILD);
        } else {
            mark("disabled");
        }
    }

    public static void startTravel() {
        enabled = false;
        clear();
        mode = Mode.TRAVEL;
        enabled = true;
        mark("enabled mode=TRAVEL build=" + TRACE_BUILD);
    }

    public static Mode mode() {
        return mode;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static synchronized int size() {
        return size;
    }

    public static synchronized void clear() {
        for (int i = 0; i < EVENTS.length; i++) {
            EVENTS[i] = null;
        }
        head = 0;
        size = 0;
        sequence = 0;
        dropped = 0;
        captureStartNanos = System.nanoTime();
        lastTick = Long.MIN_VALUE;
        LOGGER.info("[PacketTrace] cleared");
    }

    public static void mark(String label) {
        if (!enabled && size == 0) {
            return;
        }
        append("MARK " + safe(label), true);
    }

    public static void markTravel(String label) {
        if (enabled && mode == Mode.TRAVEL) {
            append("BOUNCE " + safe(label), true);
        }
    }

    public static void markSetback(int totalSetbacks, int ticksSinceSetback) {
        markSetback(totalSetbacks, ticksSinceSetback, "position-delta");
    }

    public static void markSetback(int totalSetbacks, int ticksSinceSetback, String source) {
        if (!enabled) {
            return;
        }
        append("SETBACK source=" + safe(source) + " total=" + totalSetbacks
                + " calmTicks=" + ticksSinceSetback, true);
    }

    public static void markCorrectionAcknowledged(int count) {
        if (!enabled) {
            return;
        }
        append("CORRECTION_ACK count=" + count, true);
    }

    public static void recordOutgoing(Object packet) {
        if (!enabled || packet == null) {
            return;
        }
        Mode captureMode = mode;
        if (shouldSuppress(packet)
                || (captureMode == Mode.TRAVEL && !isTravelOutgoingPacket(packet))) {
            return;
        }
        append("OUT " + (captureMode == Mode.TRAVEL
                ? describeTravelOutgoing(packet) : describePacket(packet)), false);
    }

    public static void recordIncoming(Object packet) {
        if (!enabled || packet == null) {
            return;
        }
        Mode captureMode = mode;
        if (!(captureMode == Mode.TRAVEL
                ? isTravelIncomingPacket(packet)
                : isRelevantIncomingPacket(packet))) {
            return;
        }
        append("IN " + (captureMode == Mode.TRAVEL
                ? describeTravelIncoming(packet) : describePacket(packet)), false);
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
        List<Event> events = snapshot();
        StringBuilder out = new StringBuilder(32_768);
        out.append("MOAR packet trace events=").append(events.size())
                .append(" dropped=").append(dropped)
                .append(" mode=").append(mode)
                .append(" enabled=").append(enabled).append('\n');
        for (Event event : events) {
            out.append('#').append(event.sequence)
                    .append(" t=").append(event.tick)
                    .append(" dt=").append(event.deltaTicks)
                    .append(" ms=").append(event.elapsedMs)
                    .append(' ')
                    .append(event.line)
                    .append('\n');
        }
        return out.toString();
    }

    private static synchronized List<Event> snapshot() {
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
        long elapsedMs = (System.nanoTime() - captureStartNanos) / 1_000_000L;
        String context = context();
        String line = context.isEmpty() ? message : message + " | " + context;
        Event event;
        synchronized (PacketTelemetry.class) {
            long delta = lastTick == Long.MIN_VALUE || tick < 0 || lastTick < 0
                    ? 0
                    : tick - lastTick;
            lastTick = tick;
            event = new Event(++sequence, tick, delta, elapsedMs, line);
            if (size == EVENTS.length) dropped++;
            EVENTS[head] = event;
            head = (head + 1) % EVENTS.length;
            if (size < EVENTS.length) {
                size++;
            }
        }
        if (logLive) {
            LOGGER.info("[PacketTrace] #{} t={} dt={} ms={} {}", event.sequence,
                    event.tick, event.deltaTicks, event.elapsedMs, event.line);
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

    private static boolean isTravelOutgoingPacket(Object packet) {
        /*? if >=26.1 {*//*
        return packet instanceof ServerboundMovePlayerPacket
                || packet instanceof ServerboundPlayerCommandPacket
                || packet instanceof ServerboundPlayerInputPacket;
        *//*?} else {*/
        return packet instanceof PlayerMoveC2SPacket
                || packet instanceof ClientCommandC2SPacket
                || packet instanceof PlayerInputC2SPacket;
        /*?}*/
    }

    private static boolean isTravelIncomingPacket(Object packet) {
        /*? if >=26.1 {*//*
        return packet instanceof ClientboundPlayerPositionPacket
                || packet instanceof ClientboundPlayerAbilitiesPacket
                || packet instanceof ClientboundSetEntityDataPacket data
                        && data.id() == localEntityId() && hasEntityFlags(data.packedItems())
                || packet instanceof ClientboundSetEntityMotionPacket motion
                        && motion.id() == localEntityId();
        *//*?} else {*/
        return packet instanceof PlayerPositionLookS2CPacket
                || packet instanceof PlayerAbilitiesS2CPacket
                || packet instanceof EntityTrackerUpdateS2CPacket data
                        && data.id() == localEntityId() && hasEntityFlags(data.trackedValues())
                || packet instanceof EntityVelocityUpdateS2CPacket motion
                        && motion.getEntityId() == localEntityId();
        /*?}*/
    }

    private static boolean hasEntityFlags(List<?> entries) {
        for (Object entry : entries) {
            /*? if >=26.1 {*//*
            if (entry instanceof net.minecraft.network.syncher.SynchedEntityData.DataValue<?> value
                    && value.id() == 0) return true;
            *//*?} else {*/
            if (entry instanceof net.minecraft.entity.data.DataTracker.SerializedEntry<?> value
                    && value.id() == 0) return true;
            /*?}*/
        }
        return false;
    }

    private static int localEntityId() {
        /*? if >=26.1 {*//*
        Minecraft mc = Minecraft.getInstance();
        *//*?} else {*/
        MinecraftClient mc = MinecraftClient.getInstance();
        /*?}*/
        return mc == null || mc.player == null ? Integer.MIN_VALUE : mc.player.getId();
    }

    private static String describeTravelOutgoing(Object packet) {
        /*? if >=26.1 {*//*
        if (packet instanceof ServerboundMovePlayerPacket move) {
            StringBuilder out = new StringBuilder("MOVEMENT ground=").append(move.isOnGround());
            if (move.hasPosition()) {
                out.append(" pos=").append(fmt(move.getX(0.0))).append(',')
                        .append(fmt(move.getY(0.0))).append(',')
                        .append(fmt(move.getZ(0.0)));
            }
            if (move.hasRotation()) {
                out.append(" yaw=").append(fmt(move.getYRot(0.0f)))
                        .append(" pitch=").append(fmt(move.getXRot(0.0f)));
            }
            return out.toString();
        }
        if (packet instanceof ServerboundPlayerCommandPacket command) {
            return "PLAYER_COMMAND action=" + command.getAction();
        }
        if (packet instanceof ServerboundPlayerInputPacket input) {
            return "PLAYER_INPUT forward=" + input.input().forward()
                    + " sprint=" + input.input().sprint()
                    + " jump=" + input.input().jump();
        }
        *//*?} else {*/
        if (packet instanceof PlayerMoveC2SPacket move) {
            StringBuilder out = new StringBuilder("MOVEMENT ground=").append(move.isOnGround());
            if (move.changesPosition()) {
                out.append(" pos=").append(fmt(move.getX(0.0))).append(',')
                        .append(fmt(move.getY(0.0))).append(',')
                        .append(fmt(move.getZ(0.0)));
            }
            if (move.changesLook()) {
                out.append(" yaw=").append(fmt(move.getYaw(0.0f)))
                        .append(" pitch=").append(fmt(move.getPitch(0.0f)));
            }
            return out.toString();
        }
        if (packet instanceof ClientCommandC2SPacket command) {
            return "PLAYER_COMMAND action=" + command.getMode();
        }
        if (packet instanceof PlayerInputC2SPacket input) {
            return "PLAYER_INPUT forward=" + input.input().forward()
                    + " sprint=" + input.input().sprint()
                    + " jump=" + input.input().jump();
        }
        /*?}*/
        return "OTHER";
    }

    private static String describeTravelIncoming(Object packet) {
        /*? if >=26.1 {*//*
        if (packet instanceof ClientboundPlayerPositionPacket correction) {
            var pos = correction.change().position();
            return "POSITION_CORRECTION target=" + fmt(pos.x) + ',' + fmt(pos.y)
                    + ',' + fmt(pos.z) + " relative=" + correction.relatives();
        }
        if (packet instanceof ClientboundPlayerAbilitiesPacket) return "PLAYER_ABILITIES";
        if (packet instanceof ClientboundSetEntityDataPacket data) {
            for (var value : data.packedItems()) {
                if (value.id() == 0 && value.value() instanceof Byte flags) {
                    return "ENTITY_FLAGS gliding=" + ((flags & 0x80) != 0)
                            + " sprint=" + ((flags & 0x08) != 0);
                }
            }
        }
        if (packet instanceof ClientboundSetEntityMotionPacket motion) {
            var velocity = motion.movement();
            return "ENTITY_VELOCITY vel=" + fmtMotion(velocity.x) + ','
                    + fmtMotion(velocity.y) + ',' + fmtMotion(velocity.z);
        }
        *//*?} else {*/
        if (packet instanceof PlayerPositionLookS2CPacket correction) {
            var pos = correction.change().position();
            return "POSITION_CORRECTION target=" + fmt(pos.x) + ',' + fmt(pos.y)
                    + ',' + fmt(pos.z) + " relative=" + correction.relatives();
        }
        if (packet instanceof PlayerAbilitiesS2CPacket) return "PLAYER_ABILITIES";
        if (packet instanceof EntityTrackerUpdateS2CPacket data) {
            for (var value : data.trackedValues()) {
                if (value.id() == 0 && value.value() instanceof Byte flags) {
                    return "ENTITY_FLAGS gliding=" + ((flags & 0x80) != 0)
                            + " sprint=" + ((flags & 0x08) != 0);
                }
            }
        }
        if (packet instanceof EntityVelocityUpdateS2CPacket motion) {
            /*? if >=1.21.10 {*//*
            var velocity = motion.getVelocity();
            return "ENTITY_VELOCITY vel=" + fmtMotion(velocity.x) + ','
                    + fmtMotion(velocity.y) + ',' + fmtMotion(velocity.z);
            *//*?} else {*/
            return "ENTITY_VELOCITY vel=" + fmtMotion(motion.getVelocityX()) + ','
                    + fmtMotion(motion.getVelocityY()) + ','
                    + fmtMotion(motion.getVelocityZ());
            /*?}*/
        }
        /*?}*/
        return "OTHER";
    }

    private static boolean isRelevantIncomingPacket(Object packet) {
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
                || type.contains("section_blocks_update")
                || className.contains("playerpositionlook")
                || className.contains("clientboundplayerposition")
                || type.contains("player_position");
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
                    // Skip inaccessible packet fields.
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
                // Continue through the class hierarchy.
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
                    // Skip inaccessible nested fields.
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
        if (mode == Mode.TRAVEL) {
            /*? if >=26.1 {*//*
            var velocity = mc.player.getDeltaMovement();
            boolean gliding = mc.player.isFallFlying();
            boolean onGround = mc.player.onGround();
            float yaw = mc.player.getYRot();
            float pitch = mc.player.getXRot();
            *//*?} else {*/
            var velocity = mc.player.getVelocity();
            boolean gliding = mc.player.isGliding();
            boolean onGround = mc.player.isOnGround();
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            /*?}*/
            return "travelPhase=" + TravelManager.get().currentPhase()
                    + " calm=" + setbacks.ticksSinceSetback()
                    + " corrections=" + setbacks.totalServerCorrections()
                    + " pos=" + fmt(mc.player.getX()) + ',' + fmt(mc.player.getY())
                    + ',' + fmt(mc.player.getZ())
                    + " vel=" + fmtMotion(velocity.x) + ',' + fmtMotion(velocity.y)
                    + ',' + fmtMotion(velocity.z)
                    + " ground=" + onGround
                    + " sprint=" + mc.player.isSprinting()
                    + " gliding=" + gliding
                    + " yaw=" + fmt(yaw) + " pitch=" + fmt(pitch);
        }
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

    private static String fmtMotion(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
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
