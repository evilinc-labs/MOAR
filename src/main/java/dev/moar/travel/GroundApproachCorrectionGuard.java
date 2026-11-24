package dev.moar.travel;

// Stop a ground approach when the server repeatedly rejects the same short movement.
final class GroundApproachCorrectionGuard {
    static final int MAX_CORRECTIONS_WITHOUT_PROGRESS = 5;
    private static final double PROGRESS_BLOCKS = 4.0;

    private double anchorX;
    private double anchorZ;
    private int correctionsAtAnchor;
    private boolean active;

    void start(double x, double z, int totalCorrections) {
        anchorX = x;
        anchorZ = z;
        correctionsAtAnchor = totalCorrections;
        active = true;
    }

    void clear() {
        active = false;
    }

    boolean rejected(double x, double z, int totalCorrections) {
        if (!active) {
            start(x, z, totalCorrections);
            return false;
        }
        if (Math.hypot(x - anchorX, z - anchorZ) >= PROGRESS_BLOCKS) {
            start(x, z, totalCorrections);
            return false;
        }
        return totalCorrections - correctionsAtAnchor >= MAX_CORRECTIONS_WITHOUT_PROGRESS;
    }
}
