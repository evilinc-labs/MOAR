package dev.moar.travel.plan;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

/** Candidate highway selected by the planner. */
public final class HighwayCandidate {

    // ── Classification ────────────────────────────────────────────
    public enum Category { CARDINAL, DIAGONAL, RING, DIAMOND }

    /** Which side of a ring-road square the player is on. */
    public enum RingSide { NORTH, SOUTH, EAST, WEST }

    /** Which side of a diamond-road square. */
    public enum DiamondSegment { NE, NW, SW, SE }

    // ── Axis ──────────────────────────────────────────────────────
    /**
     * Named to match anarchy-client (DIAG_PX_PZ, etc.) so diffs stay
     * readable. Carries its own perpendicular-direction and yaw math.
     */
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

        /** Perpendicular direction (right-hand rule from step direction). */
        public int perpDx() {
            return diagonal ? stepDz : (stepDz == 0 ? 0 : -stepDz);
        }

        public int perpDz() {
            return diagonal ? -stepDx : (stepDx == 0 ? 0 : stepDx);
        }

        /**
         * Expected yaw (degrees, MC convention: 0=South +Z, 90=West -X,
         * 180=North -Z, 270=East +X). Mirrors anarchy-client verbatim.
         */
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
    /** Floor Y detected at plan time, or Integer.MIN_VALUE if unknown. */
    public final int floorY;
    /** Projected entry point on the highway. */
    public final BlockPos entry;
    /** Projected exit point on the highway (closest column to destination). */
    public final BlockPos exit;
    /** 0..1 coordinate + scan confidence. */
    public final float confidence;
    /** Ring/diamond radius (0 for cardinal/diagonal). */
    public final double ringOrDiamondDist;
    public final RingSide ringSide;               // null for non-ring
    public final DiamondSegment diamondSegment;    // null for non-diamond
    /** Physical width in blocks (0 = not scanned). */
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

    /** Convenience constructor — no ring/diamond metadata, no scan data. */
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
