package dev.moar.util;

// Compatibility shim for callers that still use the old printer/pathing API.
public final class PrinterNetworkCoordinator {

    public enum Lane {
        MOVEMENT,
        LOOK,
        INVENTORY,
        INTERACTION,
        MINING
    }

    private PrinterNetworkCoordinator() {}

    public static void beginClientTick() {
        MoarNetworkManager.beginClientTick();
    }

    public static boolean tryAcquire(Lane lane, String owner) {
        return tryAcquire(lane, owner, 1, 0);
    }

    public static boolean tryAcquire(Lane lane, String owner, int packetCost, int cooldownTicks) {
        return MoarNetworkManager.tryAcquire(toLane(lane), owner, packetCost, cooldownTicks);
    }

    public static boolean tryLease(String owner, int cooldownTicks) {
        return MoarNetworkManager.tryLease(owner, cooldownTicks);
    }

    public static boolean canAct(String owner) {
        return MoarNetworkManager.canAct(owner);
    }

    public static boolean hasOwnerOtherThan(String owner) {
        return MoarNetworkManager.hasOwnerOtherThan(owner);
    }

    public static String snapshot() {
        return MoarNetworkManager.snapshot();
    }

    private static MoarNetworkManager.Lane toLane(Lane lane) {
        if (lane == null) {
            return null;
        }
        return switch (lane) {
            case MOVEMENT -> MoarNetworkManager.Lane.MOVEMENT;
            case LOOK -> MoarNetworkManager.Lane.LOOK;
            case INVENTORY -> MoarNetworkManager.Lane.INVENTORY;
            case INTERACTION -> MoarNetworkManager.Lane.INTERACTION;
            case MINING -> MoarNetworkManager.Lane.MINING;
        };
    }
}
