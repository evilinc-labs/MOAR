package dev.moar.util;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// Software-wide packet arbiter for MOAR automation.
public final class MoarNetworkManager {

    public enum Lane {
        MOVEMENT,
        LOOK,
        INVENTORY,
        INTERACTION,
        MINING,
        CONTAINER
    }

    public static final String OWNER_PRINTER = "printer";
    public static final String OWNER_PATH_WALKER = "path-walker";
    public static final String OWNER_SPAWN_PROOFER = "spawn-proofer";
    public static final String OWNER_CHEST_MANAGER = "chest-manager";
    public static final String OWNER_STASH_MANAGER = "stash-manager";
    public static final String OWNER_STASH_ORGANIZER = "stash-organizer";
    public static final String OWNER_STASH_RETRIEVER = "stash-retriever";
    public static final String OWNER_LANE_SORTER = "lane-sorter";
    public static final String OWNER_TRAVEL = "travel";
    public static final String OWNER_ELYTRA = "elytra";
    public static final String OWNER_FLIGHT = "flight";
    public static final String OWNER_BOUNCE = "bounce";

    private static final int MAX_PACKET_COST_PER_TICK = 3;
    private static final EnumMap<Lane, String> laneOwners = new EnumMap<>(Lane.class);
    private static final EnumMap<Lane, Integer> laneCooldowns = new EnumMap<>(Lane.class);
    private static final Map<String, Integer> ownerLeases = new HashMap<>();

    private static String tickOwner;
    private static int packetCostThisTick;
    private static long logicalTick;

    private MoarNetworkManager() {}

    public static void beginClientTick() {
        logicalTick++;
        tickOwner = null;
        laneOwners.clear();
        packetCostThisTick = 0;
        decrementLaneCooldowns();
        decrementOwnerLeases();
    }

    public static boolean tryAcquire(Lane lane, String owner) {
        return tryAcquire(lane, owner, 1, 0);
    }

    public static boolean tryAcquire(Lane lane, String owner, int packetCost, int settleTicks) {
        if (lane == null || isBlank(owner)) {
            return false;
        }
        if (!ownerCanAct(owner)) {
            return false;
        }
        if (laneCooldowns.getOrDefault(lane, 0) > 0) {
            return false;
        }
        String laneOwner = laneOwners.get(lane);
        if (laneOwner != null && !owner.equals(laneOwner)) {
            return false;
        }
        int cost = Math.max(0, packetCost);
        if (packetCostThisTick + cost > MAX_PACKET_COST_PER_TICK) {
            return false;
        }

        tickOwner = owner;
        laneOwners.put(lane, owner);
        packetCostThisTick += cost;
        if (settleTicks > 0) {
            laneCooldowns.put(lane, settleTicks);
            holdOwner(owner, settleTicks);
        }
        return true;
    }

    public static boolean tryLease(String owner, int settleTicks) {
        if (isBlank(owner) || !ownerCanAct(owner)) {
            return false;
        }
        tickOwner = owner;
        holdOwner(owner, Math.max(1, settleTicks));
        return true;
    }

    public static boolean canAct(String owner) {
        return !isBlank(owner) && ownerCanAct(owner);
    }

    public static boolean hasOwnerOtherThan(String owner) {
        if (isBlank(owner)) {
            return tickOwner != null || !ownerLeases.isEmpty() || !laneOwners.isEmpty();
        }
        if (tickOwner != null && !owner.equals(tickOwner)) {
            return true;
        }
        for (String activeOwner : laneOwners.values()) {
            if (!owner.equals(activeOwner)) {
                return true;
            }
        }
        for (String leasedOwner : ownerLeases.keySet()) {
            if (!owner.equals(leasedOwner)) {
                return true;
            }
        }
        return false;
    }

    public static String snapshot() {
        return "tick=" + logicalTick
                + " owner=" + tickOwner
                + " cost=" + packetCostThisTick
                + " lanes=" + laneOwners
                + " laneCooldowns=" + laneCooldowns
                + " leases=" + ownerLeases;
    }

    private static boolean ownerCanAct(String owner) {
        if (tickOwner != null && !owner.equals(tickOwner)) {
            return false;
        }
        for (String activeOwner : laneOwners.values()) {
            if (!owner.equals(activeOwner)) {
                return false;
            }
        }
        for (String leasedOwner : ownerLeases.keySet()) {
            if (!owner.equals(leasedOwner)) {
                return false;
            }
        }
        return true;
    }

    private static void holdOwner(String owner, int ticks) {
        if (ticks <= 0) {
            return;
        }
        ownerLeases.merge(owner, ticks, Math::max);
    }

    private static void decrementLaneCooldowns() {
        for (Map.Entry<Lane, Integer> entry : laneCooldowns.entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            }
        }
    }

    private static void decrementOwnerLeases() {
        Iterator<Map.Entry<String, Integer>> iterator = ownerLeases.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
