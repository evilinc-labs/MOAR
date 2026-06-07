package dev.moar.travel.plan;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

// Planner-selected highway candidate.
public final class HighwayCandidate {

    // ── Classification ────────────────────────────────────────────
    public enum Category { CARDINAL, DIAGONAL, RING, DIAMOND }

    // Side of a ring-road square.
    public enum RingSide { NORTH, SOUTH, EAST, WEST }

    // Side of a diamond-road diamond.
    public enum DiamondSegment { NE, NW, SW, SE }

    // ── Axis ──────────────────────────────────────────────────────
    // Axis metadata used for direction, strafe, and yaw math.
    public enum Axis {
        PLUS_X(false, +1,  0),
        MINUS_X(false, -1,  0),
        PLUS_Z(false,  0, +1),
        MINUS_Z(false,  0, -1),
        DIAG_PX_PZ(true, +1, +1),
        DIAG_PX_MZ(true, +1, -1),
        DIAG_MX_PZ(true, -1, +1),
        DIAG_MX_MZ(true, -1, -1);

        public final boolean diagonal;
        public final int stepDx;
        public final int stepDz;

        Axis(boolean diagonal, int stepDx, int stepDz) {
            this.diagonal = diagonal;
            this.stepDx = stepDx;
            this.stepDz = stepDz;
        }

        // Get the perpendicular X step.
        public int perpDx() {
            return diagonal ? stepDz : (stepDz == 0 ? 0 : -stepDz);
        }

        public int perpDz() {
            return diagonal ? -stepDx : (stepDx == 0 ? 0 : stepDx);
        }

        // Get the matching Minecraft yaw.
        public float expectedYaw() {
            return switch (this) {
                case PLUS_X     -> 270f;
                case MINUS_X    ->  90f;
                case PLUS_Z     ->   0f;
                case MINUS_Z    -> 180f;
                case DIAG_PX_PZ -> 315f; // SE
                case DIAG_PX_MZ -> 225f; // NE
                case DIAG_MX_PZ ->  45f; // SW
                case DIAG_MX_MZ -> 135f; // NW
            };
        }
    }

    // ── Fields ────────────────────────────────────────────────────
    public final Axis axis;
    public final Category category;
    // Planned floor Y, or Integer.MIN_VALUE when unknown.
    public final int floorY;
    // Projected highway entry.
    public final BlockPos entry;
    // Projected highway exit.
    public final BlockPos exit;
    // Combined coordinate and scan confidence.
    public final float confidence;
    // Ring or diamond distance, or 0 for straight highways.
    public final double ringOrDiamondDist;
    public final RingSide ringSide;               // null for non-ring
    public final DiamondSegment diamondSegment;    // null for non-diamond
    // Observed width, or 0 when unscanned.
    public final int width;
    public final boolean hasLeftRail;
    public final boolean hasRightRail;

    // ── Constructors ──────────────────────────────────────────────
    public HighwayCandidate(Axis axis, Category category, int floorY,
                            BlockPos entry, BlockPos exit, float confidence,
                            double ringOrDiamondDist, RingSide ringSide,
                            DiamondSegment diamondSegment, int width,
                            boolean hasLeftRail, boolean hasRightRail) {
        this.axis = axis;
        this.category = category;
        this.floorY = floorY;
        this.entry = entry;
        this.exit = exit;
        this.confidence = confidence;
        this.ringOrDiamondDist = ringOrDiamondDist;
        this.ringSide = ringSide;
        this.diamondSegment = diamondSegment;
        this.width = width;
        this.hasLeftRail = hasLeftRail;
        this.hasRightRail = hasRightRail;
    }

    // Create a candidate without ring, diamond, or scan metadata.
    public HighwayCandidate(Axis axis, Category category, int floorY,
                            BlockPos entry, BlockPos exit, float confidence) {
        this(axis, category, floorY, entry, exit, confidence, 0, null, null, 0, false, false);
    }

    @Override
    public String toString() {
        String base = "HighwayCandidate{axis=" + axis + ", cat=" + category
                + ", floorY=" + (floorY == Integer.MIN_VALUE ? "?" : floorY)
                + ", conf=" + String.format("%.2f", confidence);
        if (category == Category.RING)
            base += ", ring=" + (int) ringOrDiamondDist + " side=" + ringSide;
        else if (category == Category.DIAMOND)
            base += ", diamond=" + (int) ringOrDiamondDist + " seg=" + diamondSegment;
        return base + ", entry=" + entry.toShortString()
                + ", exit=" + exit.toShortString() + "}";
    }
}
