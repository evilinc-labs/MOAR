package dev.moar.travel.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure coordinate geometry for nether highway planning. */
public final class HighwayGeometry {

    private HighwayGeometry() {}

    // ── Distance tables (ported from anarchy-client HighwayDetector) ──────
    /** Ring-road axis-aligned square distances from origin. */
    public static final double[] RING_DISTANCES = {
            500, 1000, 1500, 2000, 2500, 7500.5,
            55000, 62500, 100000, 125000, 250000, 500000,
            750000, 1_000_000, 1_250_000, 1_875_000, 2_500_000, 3_750_000
    };

    /** Diamond-road manhattan-distance (|x|+|z|) distances from origin. */
    public static final double[] DIAMOND_DISTANCES = {
            2500, 5000, 25000, 50000, 125_000, 250_000, 500_000, 3_750_000
    };

    /** Match tolerance in blocks for ring/diamond coordinate snap. */
    public static final double RING_DIAMOND_TOLERANCE = 5.0;

    // ── GeometryCandidate ────────────────────────────────────────────────
    /** Coordinate-only highway candidate. */
    public static final class GeometryCandidate {
        public final HighwayCandidate.Axis axis;
        public final HighwayCandidate.Category category;
        public final float confidence;
        public final double ringOrDiamondDist;
        public final HighwayCandidate.RingSide ringSide;
        public final HighwayCandidate.DiamondSegment diamondSegment;

        GeometryCandidate(HighwayCandidate.Axis axis, float confidence) {
            this.axis = axis;
            this.confidence = confidence;
            this.category = axis.diagonal
                    ? HighwayCandidate.Category.DIAGONAL
                    : HighwayCandidate.Category.CARDINAL;
            this.ringOrDiamondDist = 0;
            this.ringSide = null;
            this.diamondSegment = null;
        }

        GeometryCandidate(HighwayCandidate.Axis axis, float confidence,
                          HighwayCandidate.Category cat, double dist,
                          HighwayCandidate.RingSide side,
                          HighwayCandidate.DiamondSegment seg) {
            this.axis = axis;
            this.confidence = confidence;
            this.category = cat;
            this.ringOrDiamondDist = dist;
            this.ringSide = side;
            this.diamondSegment = seg;
        }

        @Override
        public String toString() {
            return "GeometryCandidate{" + axis + " " + category
                    + " conf=" + String.format("%.2f", confidence) + "}";
        }
    }

    // ── Coordinate ranking (Signal 1) ────────────────────────────────────
    /** Rank highway candidates by coordinate fit. */
    public static List<GeometryCandidate> rankCandidates(int x, int z,
                                                          boolean detectRings,
                                                          boolean detectDiamonds) {
        List<GeometryCandidate> result = new ArrayList<>();
        int absX = Math.abs(x);
        int absZ = Math.abs(z);

        // ── Cardinal ──────────────────────────────────────────────
        if (absZ < 50 && absX > 100) {
            float conf = 1f - (absZ / 50f);
            result.add(new GeometryCandidate(
                    x > 0 ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X, conf));
        }
        if (absX < 50 && absZ > 100) {
            float conf = 1f - (absX / 50f);
            result.add(new GeometryCandidate(
                    z > 0 ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z, conf));
        }

        // ── Diagonal ──────────────────────────────────────────────
        int diffXZ = x - z;
        int sumXZ  = x + z;
        if (Math.abs(diffXZ) < 50 && absX > 100 && absZ > 100) {
            float conf = 1f - (Math.abs(diffXZ) / 50f);
            if (x > 0 && z > 0)
                result.add(new GeometryCandidate(HighwayCandidate.Axis.DIAG_PX_PZ, conf));
            else if (x < 0 && z < 0)
                result.add(new GeometryCandidate(HighwayCandidate.Axis.DIAG_MX_MZ, conf));
        }
        if (Math.abs(sumXZ) < 50 && absX > 100 && absZ > 100) {
            float conf = 1f - (Math.abs(sumXZ) / 50f);
            if (x > 0 && z < 0)
                result.add(new GeometryCandidate(HighwayCandidate.Axis.DIAG_PX_MZ, conf));
            else if (x < 0 && z > 0)
                result.add(new GeometryCandidate(HighwayCandidate.Axis.DIAG_MX_PZ, conf));
        }

        // ── Ring roads ────────────────────────────────────────────
        if (detectRings) {
            for (double D : RING_DISTANCES) {
                if (Math.abs(absX - D) < RING_DIAMOND_TOLERANCE && x > 0) {
                    float conf = 1f - (float)(Math.abs(absX - D) / RING_DIAMOND_TOLERANCE);
                    result.add(new GeometryCandidate(
                            z > 0 ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z,
                            conf * 0.8f, HighwayCandidate.Category.RING, D,
                            HighwayCandidate.RingSide.EAST, null));
                }
                if (Math.abs(absX - D) < RING_DIAMOND_TOLERANCE && x < 0) {
                    float conf = 1f - (float)(Math.abs(absX - D) / RING_DIAMOND_TOLERANCE);
                    result.add(new GeometryCandidate(
                            z > 0 ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z,
                            conf * 0.8f, HighwayCandidate.Category.RING, D,
                            HighwayCandidate.RingSide.WEST, null));
                }
                if (Math.abs(absZ - D) < RING_DIAMOND_TOLERANCE && z > 0) {
                    float conf = 1f - (float)(Math.abs(absZ - D) / RING_DIAMOND_TOLERANCE);
                    result.add(new GeometryCandidate(
                            x > 0 ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X,
                            conf * 0.8f, HighwayCandidate.Category.RING, D,
                            HighwayCandidate.RingSide.SOUTH, null));
                }
                if (Math.abs(absZ - D) < RING_DIAMOND_TOLERANCE && z < 0) {
                    float conf = 1f - (float)(Math.abs(absZ - D) / RING_DIAMOND_TOLERANCE);
                    result.add(new GeometryCandidate(
                            x > 0 ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X,
                            conf * 0.8f, HighwayCandidate.Category.RING, D,
                            HighwayCandidate.RingSide.NORTH, null));
                }
            }
        }

        // ── Diamond roads ─────────────────────────────────────────
        if (detectDiamonds) {
            double manhattan = (double) absX + absZ;
            for (double D : DIAMOND_DISTANCES) {
                if (Math.abs(manhattan - D) < RING_DIAMOND_TOLERANCE) {
                    float conf = 1f - (float)(Math.abs(manhattan - D) / RING_DIAMOND_TOLERANCE);
                    HighwayCandidate.Axis axis;
                    HighwayCandidate.DiamondSegment seg;
                    if (x >= 0 && z >= 0) {
                        seg = HighwayCandidate.DiamondSegment.NE;
                        axis = HighwayCandidate.Axis.DIAG_MX_PZ;
                    } else if (x < 0 && z >= 0) {
                        seg = HighwayCandidate.DiamondSegment.NW;
                        axis = HighwayCandidate.Axis.DIAG_MX_MZ;
                    } else if (x < 0) {
                        seg = HighwayCandidate.DiamondSegment.SW;
                        axis = HighwayCandidate.Axis.DIAG_PX_MZ;
                    } else {
                        seg = HighwayCandidate.DiamondSegment.SE;
                        axis = HighwayCandidate.Axis.DIAG_PX_PZ;
                    }
                    result.add(new GeometryCandidate(axis, conf * 0.8f,
                            HighwayCandidate.Category.DIAMOND, D, null, seg));
                }
            }
        }

        // ── Weak fallback for every axis ──────────────────────────
        // Ensures block scan + yaw can still detect side highways or
        // any highway not near a main coordinate axis.
        for (HighwayCandidate.Axis axis : HighwayCandidate.Axis.values()) {
            boolean already = result.stream().anyMatch(c -> c.axis == axis
                    && c.category == (axis.diagonal
                        ? HighwayCandidate.Category.DIAGONAL
                        : HighwayCandidate.Category.CARDINAL));
            if (!already) result.add(new GeometryCandidate(axis, 0.05f));
        }

        result.sort(Comparator.comparingDouble((GeometryCandidate c) -> -c.confidence));
        return result;
    }

    // ── Highway-axis projection ───────────────────────────────────────────
    /** Project an X/Z point onto the selected highway line. */
    public static int[] projectOnto(GeometryCandidate c, int x, int z) {
        return switch (c.axis) {
            case PLUS_X, MINUS_X -> {
                int fixedZ = fixedPerpCoord(c, false, z);
                yield new int[]{ x, fixedZ };
            }
            case PLUS_Z, MINUS_Z -> {
                int fixedX = fixedPerpCoord(c, true, x);
                yield new int[]{ fixedX, z };
            }
            case DIAG_PX_PZ, DIAG_MX_MZ -> {
                // Line x = z; nearest point: avg of (x+z)/2
                int avg = (x + z) / 2;
                yield new int[]{ avg, avg };
            }
            case DIAG_PX_MZ, DIAG_MX_PZ -> {
                // Line x = -z; nearest point:
                int avg = (x - z) / 2;
                yield new int[]{ avg, -avg };
            }
        };
    }

    private static int fixedPerpCoord(GeometryCandidate c, boolean isXAxis, int currentCoord) {
        if (c.category == HighwayCandidate.Category.RING && c.ringOrDiamondDist > 0) {
            if (!isXAxis) {
                // PLUS_X/MINUS_X highway: Z is fixed
                return switch (c.ringSide) {
                    case SOUTH -> +(int) c.ringOrDiamondDist;
                    case NORTH -> -(int) c.ringOrDiamondDist;
                    default    ->  0; // shouldn't happen
                };
            } else {
                // PLUS_Z/MINUS_Z highway: X is fixed
                return switch (c.ringSide) {
                    case EAST  -> +(int) c.ringOrDiamondDist;
                    case WEST  -> -(int) c.ringOrDiamondDist;
                    default    ->  0;
                };
            }
        }
        return 0; // cardinal main highway runs through origin
    }

    // ── Yaw confidence (Signal 3) ─────────────────────────────────────────
    /** Score how well the player's yaw matches this axis. */
    public static float yawConfidence(float playerYaw, HighwayCandidate.Axis axis) {
        float expected = axis.expectedYaw();
        float diff = wrapDegrees(playerYaw - expected);
        float absDiff = Math.abs(diff);
        // Also accept opposite direction (player may face either way on the highway)
        float oppDiff = Math.abs(wrapDegrees(playerYaw - wrapDegrees(expected + 180f)));
        absDiff = Math.min(absDiff, oppDiff);

        if (absDiff < 15f) return 1.0f;
        if (absDiff < 30f) return 0.7f;
        if (absDiff < 45f) return 0.4f;
        if (absDiff < 60f) return 0.2f;
        return 0f;
    }

    // ── Utilities ─────────────────────────────────────────────────────────
    public static double horizontalDistance(int x1, int z1, int x2, int z2) {
        double dx = x1 - x2, dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float wrapDegrees(float v) {
        v = v % 360f;
        if (v < -180f) v += 360f;
        if (v >=  180f) v -= 360f;
        return v;
    }
}
