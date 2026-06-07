package dev.moar.travel.plan;

import dev.moar.travel.highway.HighwayDetectorBridge;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Plan highway, mining, and optional flight legs.
public final class HighwayPlanner {

    // Default highway floor Y before runtime refinement.
    private static final int DEFAULT_NETHER_FLOOR_Y = 120;
    // Confidence for planner-created highway segments.
    private static final float SYNTHETIC_CONFIDENCE = 0.75f;
    // Push entry past a junction so Baritone follows the planned branch.
    private static final int INTERSECTION_DIRECTIONAL_OFFSET = 8;
    // Mine this far off the highway before launch.
    private static final int FREENETHER_TAKEOFF_DISTANCE = 48;
    // Split off-ramp mining into short legs.
    private static final int FREENETHER_MINING_LEG_LENGTH = 12;

    public static final class Options {
        // Optional highway floor Y hint.
        public Integer expectedFloorY = null;
        // Add a flight leg beyond this XZ distance.
        public int freeNetherFlightThreshold = 1500;
        // Allow planner-created flight legs.
        public boolean allowFlight = true;
        // Enable ring-road detection.
        public boolean detectRings = true;
        // Enable diamond-road detection.
        public boolean detectDiamonds = false;
        // Minimum coordinate confidence.
        public float minConfidence = 0.15f;

        public Options expectedFloorY(int y)                { this.expectedFloorY = y;               return this; }
        public Options freeNetherFlightThreshold(int v)     { this.freeNetherFlightThreshold = v;    return this; }
        public Options allowFlight(boolean v)               { this.allowFlight = v;                  return this; }
        public Options detectRings(boolean v)               { this.detectRings = v;                  return this; }
        public Options detectDiamonds(boolean v)            { this.detectDiamonds = v;               return this; }
        public Options minConfidence(float v)               { this.minConfidence = v;                return this; }
    }

    private record RoutePlan(HighwayCandidate primary,
                             List<HighwayRoute.Leg> legs,
                             double totalCost,
                             int travelDx,
                             int travelDz) {}

    private record CandidateRoute(HighwayGeometry.GeometryCandidate geometry,
                                  RoutePlan route,
                                  int[] onRampXZ,
                                  int[] exitXZ) {}

    private record RingSegment(BlockPos start,
                               BlockPos end,
                               HighwayCandidate.Axis axis,
                               HighwayCandidate.RingSide side) {}

    public Optional<HighwayRoute> plan(BlockPos origin, BlockPos destination, Options opts) {
        if (origin == null || destination == null) return Optional.empty();
        if (opts == null) opts = new Options();

        int floorY = opts.expectedFloorY != null ? opts.expectedFloorY : DEFAULT_NETHER_FLOOR_Y;

        int ox = origin.getX(),      oz = origin.getZ();
        int dx = destination.getX(), dz = destination.getZ();

        List<HighwayGeometry.GeometryCandidate> candidates =
                HighwayGeometry.rankCandidates(dx, dz, opts.detectRings, opts.detectDiamonds);

        HighwayGeometry.GeometryCandidate bestFallback = null;
        for (HighwayGeometry.GeometryCandidate c : candidates) {
            if (c.confidence >= opts.minConfidence) {
                bestFallback = c;
                break;
            }
        }
        if (bestFallback == null) bestFallback = fallbackAxis(ox, oz, dx, dz);

        CandidateRoute bestDirect = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (HighwayGeometry.GeometryCandidate candidate : candidates) {
            int[] onRampXZ = HighwayGeometry.projectOnto(candidate, ox, oz);
            int[] exitXZ   = HighwayGeometry.projectOnto(candidate, dx, dz);
            RoutePlan route = buildDirectRoute(origin, destination, opts, candidate, floorY, onRampXZ, exitXZ);
            if (route == null || route.primary == null || route.legs.isEmpty()) continue;

            double score = routeScore(route, candidate);
            if (score < bestScore) {
                bestScore = score;
                bestDirect = new CandidateRoute(candidate, route, onRampXZ, exitXZ);
            }
        }

        if (bestDirect == null) {
            int[] onRampXZ = HighwayGeometry.projectOnto(bestFallback, ox, oz);
            int[] exitXZ   = HighwayGeometry.projectOnto(bestFallback, dx, dz);
            RoutePlan route = buildDirectRoute(origin, destination, opts, bestFallback, floorY, onRampXZ, exitXZ);
            if (route == null || route.primary == null || route.legs.isEmpty()) return Optional.empty();
            bestDirect = new CandidateRoute(bestFallback, route, onRampXZ, exitXZ);
        }

        RoutePlan plan = shouldUseSafeRingRoute(origin, destination,
                bestDirect.geometry(), bestDirect.onRampXZ(), bestDirect.exitXZ())
                ? buildSafeRingRoute(origin, destination, opts, floorY)
                : bestDirect.route();
        if (plan == null || plan.primary == null || plan.legs.isEmpty()) return Optional.empty();

        return Optional.of(new HighwayRoute(
                plan.primary, plan.legs, plan.totalCost, plan.travelDx, plan.travelDz));
    }

    private static double routeScore(RoutePlan route, HighwayGeometry.GeometryCandidate candidate) {
        // Prefer the cheapest route from the player's actual origin, while giving
        // a small edge to stronger geometric matches when costs are close.
        return route.totalCost + (1.0 - candidate.confidence) * 64.0;
    }

    private RoutePlan buildDirectRoute(BlockPos origin,
                                       BlockPos destination,
                                       Options opts,
                                       HighwayGeometry.GeometryCandidate best,
                                       int floorY,
                                       int[] onRampXZ,
                                       int[] exitXZ) {
        int refinedFloorY = floorY;

        BlockPos onRamp = new BlockPos(onRampXZ[0], refinedFloorY, onRampXZ[1]);
        BlockPos exitColumn = new BlockPos(exitXZ[0], refinedFloorY, exitXZ[1]);

        Optional<HighwayDetectorBridge.ScanResult> scan =
                HighwayDetectorBridge.get().scanAt(new BlockPos(origin.getX(), refinedFloorY, origin.getZ()), best.axis);
        if (scan.isPresent()) {
            refinedFloorY = scan.get().floorY();
            onRamp = new BlockPos(scan.get().centerX(), refinedFloorY, scan.get().centerZ());
            exitColumn = new BlockPos(exitXZ[0], refinedFloorY, exitXZ[1]);
        }

        HighwayCandidate primary = new HighwayCandidate(
                best.axis, best.category, refinedFloorY, onRamp, exitColumn, best.confidence,
                best.ringOrDiamondDist, best.ringSide, best.diamondSegment,
                scan.map(HighwayDetectorBridge.ScanResult::width).orElse(0),
                scan.map(HighwayDetectorBridge.ScanResult::hasLeftRail).orElse(false),
                scan.map(HighwayDetectorBridge.ScanResult::hasRightRail).orElse(false));

        int[] travelDir = travelDirection(best, point(primary.entry), point(primary.exit));
        double originToOnRamp = HighwayGeometry.horizontalDistance(
                origin.getX(), origin.getZ(), primary.entry.getX(), primary.entry.getZ());
        double approachThreshold = best.axis.diagonal ? 3.0 : 10.0;
        boolean requiresIngressTravel = needsIngressTravel(originToOnRamp, approachThreshold, opts.allowFlight);
        if (requiresIngressTravel) {
            BlockPos directionalEntry = directionalAnchor(primary.entry, travelDir[0], travelDir[1]);
            primary = new HighwayCandidate(
                    primary.axis, primary.category, primary.floorY, directionalEntry, primary.exit, primary.confidence,
                    primary.ringOrDiamondDist, primary.ringSide, primary.diamondSegment,
                    primary.width, primary.hasLeftRail, primary.hasRightRail);
        }

        List<HighwayRoute.Leg> legs = new ArrayList<>();
        double totalCost = 0.0;
        totalCost += addIngressLeg(legs, originToOnRamp, primary.entry, approachThreshold, opts.allowFlight);

        appendBounceLeg(legs, primary, travelDir[0], travelDir[1]);
        double bounceLength = HighwayGeometry.horizontalDistance(
                primary.entry.getX(), primary.entry.getZ(), primary.exit.getX(), primary.exit.getZ());
        totalCost += bounceLength;

        double exitToDest = HighwayGeometry.horizontalDistance(
                primary.exit.getX(), primary.exit.getZ(), destination.getX(), destination.getZ());
        if (exitToDest > opts.freeNetherFlightThreshold && opts.allowFlight) {
            BlockPos takeoffPoint = computeTakeoffPoint(primary.exit, destination, refinedFloorY);
            double exitToTakeoff = HighwayGeometry.horizontalDistance(
                    primary.exit.getX(), primary.exit.getZ(), takeoffPoint.getX(), takeoffPoint.getZ());
            if (exitToTakeoff > 2.0) {
                legs.add(new HighwayRoute.OffRampLeg(primary.exit));
                appendMiningLegs(legs, primary.exit, takeoffPoint);
                totalCost += exitToTakeoff;
            }
            legs.add(new HighwayRoute.FlightLeg(destination));
            totalCost += HighwayGeometry.horizontalDistance(
                    takeoffPoint.getX(), takeoffPoint.getZ(), destination.getX(), destination.getZ());
        } else if (exitToDest > 2.0) {
            legs.add(new HighwayRoute.OffRampLeg(primary.exit));
            appendMiningLegs(legs, primary.exit, destination);
            totalCost += exitToDest;
        }

        return new RoutePlan(primary, legs, totalCost, travelDir[0], travelDir[1]);
    }

    private RoutePlan buildSafeRingRoute(BlockPos origin,
                                         BlockPos destination,
                                         Options opts,
                                         int floorYHint) {
        List<HighwayRoute.Leg> legs = new ArrayList<>();
        double totalCost = 0.0;

        BlockPos originJunction = ringJunctionForPoint(origin, destination, floorYHint);
        BlockPos destinationJunction = ringJunctionForPoint(destination, origin, floorYHint);

        HighwayCandidate primary = null;
        int primaryDx = 0;
        int primaryDz = 0;
        int floorY = floorYHint;

        if (!isWithinSafeRing(origin)) {
            HighwayCandidate.Axis originAxis = spokeAxisForJunction(originJunction);
            BlockPos originOnRamp = projectOntoSpoke(originAxis, origin, floorY);
            floorY = refineFloorY(originOnRamp, originAxis, floorY);
            originJunction = withY(originJunction, floorY);
            destinationJunction = withY(destinationJunction, floorY);
            originOnRamp = projectOntoSpoke(originAxis, origin, floorY);

            double originToOnRamp = HighwayGeometry.horizontalDistance(
                    origin.getX(), origin.getZ(), originOnRamp.getX(), originOnRamp.getZ());
            boolean requiresIngressTravel = needsIngressTravel(originToOnRamp, 10.0, opts.allowFlight);
            int[] travelDir = travelDirection(point(originOnRamp), point(originJunction), originAxis);
            BlockPos spokeEntry = requiresIngressTravel
                    ? directionalAnchor(originOnRamp, travelDir[0], travelDir[1])
                    : originOnRamp;
            totalCost += addIngressLeg(legs, originToOnRamp, spokeEntry, 10.0, opts.allowFlight);

            HighwayCandidate spoke = syntheticCandidate(
                    originAxis, HighwayCandidate.Category.CARDINAL, floorY,
                    spokeEntry, originJunction, null, 0.0);
            travelDir = travelDirection(point(spoke.entry), point(spoke.exit), originAxis);
            appendBounceLeg(legs, spoke, travelDir[0], travelDir[1]);
            totalCost += HighwayGeometry.horizontalDistance(
                    spoke.entry.getX(), spoke.entry.getZ(), spoke.exit.getX(), spoke.exit.getZ());
            primary = spoke;
            primaryDx = travelDir[0];
            primaryDz = travelDir[1];
        } else {
            originJunction = withY(originJunction, floorY);
            destinationJunction = withY(destinationJunction, floorY);
            double originToRing = HighwayGeometry.horizontalDistance(
                    origin.getX(), origin.getZ(), originJunction.getX(), originJunction.getZ());
            List<RingSegment> previewSegments = planRingSegments(originJunction, destinationJunction, destination);
            BlockPos ingressTarget = originJunction;
            if (!previewSegments.isEmpty() && needsIngressTravel(originToRing, 10.0, opts.allowFlight)) {
                RingSegment first = previewSegments.get(0);
                int[] ingressDir = travelDirection(point(first.start), point(first.end), first.axis);
                ingressTarget = directionalAnchor(originJunction, ingressDir[0], ingressDir[1]);
            }
            totalCost += addIngressLeg(legs, originToRing, ingressTarget, 10.0, opts.allowFlight);
        }

        List<RingSegment> ringSegments = planRingSegments(originJunction, destinationJunction, destination);
        for (int i = 0; i < ringSegments.size(); i++) {
            RingSegment segment = ringSegments.get(i);
            int[] travelDir = travelDirection(point(segment.start), point(segment.end), segment.axis);
            BlockPos segmentEntry = segment.start;
            if (i > 0 || isWithinSafeRing(origin)) {
                segmentEntry = directionalAnchor(segment.start, travelDir[0], travelDir[1]);
            }
            HighwayCandidate ring = syntheticCandidate(
                    segment.axis, HighwayCandidate.Category.RING, floorY,
                    segmentEntry, segment.end, segment.side, HighwayRoute.SAFE_RING_RADIUS);
            appendBounceLeg(legs, ring, travelDir[0], travelDir[1]);
            totalCost += HighwayGeometry.horizontalDistance(
                    ring.entry.getX(), ring.entry.getZ(), ring.exit.getX(), ring.exit.getZ());
            if (primary == null) {
                primary = ring;
                primaryDx = travelDir[0];
                primaryDz = travelDir[1];
            }
        }

        BlockPos egressAnchor = destinationJunction;
        if (!isWithinSafeRing(destination)) {
            HighwayCandidate.Axis destinationAxis = spokeAxisForJunction(destinationJunction);
            BlockPos projectedDestination = projectOntoSpoke(destinationAxis, destination, floorY);
            double ringToDestinationSpoke = HighwayGeometry.horizontalDistance(
                    destinationJunction.getX(), destinationJunction.getZ(),
                    projectedDestination.getX(), projectedDestination.getZ());
            if (ringToDestinationSpoke > 2.0) {
                int[] travelDir = travelDirection(point(destinationJunction), point(projectedDestination), destinationAxis);
                BlockPos spokeEntry = directionalAnchor(destinationJunction, travelDir[0], travelDir[1]);
                HighwayCandidate spoke = syntheticCandidate(
                        destinationAxis, HighwayCandidate.Category.CARDINAL, floorY,
                        spokeEntry, projectedDestination, null, 0.0);
                appendBounceLeg(legs, spoke, travelDir[0], travelDir[1]);
                totalCost += ringToDestinationSpoke;
                egressAnchor = projectedDestination;
                if (primary == null) {
                    primary = spoke;
                    primaryDx = travelDir[0];
                    primaryDz = travelDir[1];
                }
            }
        }

        double egressToDestination = HighwayGeometry.horizontalDistance(
                egressAnchor.getX(), egressAnchor.getZ(), destination.getX(), destination.getZ());
        if (egressToDestination > opts.freeNetherFlightThreshold && opts.allowFlight) {
            BlockPos takeoffPoint = computeTakeoffPoint(egressAnchor, destination, floorY);
            double egressToTakeoff = HighwayGeometry.horizontalDistance(
                    egressAnchor.getX(), egressAnchor.getZ(), takeoffPoint.getX(), takeoffPoint.getZ());
            if (egressToTakeoff > 2.0) {
                legs.add(new HighwayRoute.OffRampLeg(egressAnchor));
                appendMiningLegs(legs, egressAnchor, takeoffPoint);
                totalCost += egressToTakeoff;
            }
            legs.add(new HighwayRoute.FlightLeg(destination));
            totalCost += HighwayGeometry.horizontalDistance(
                    takeoffPoint.getX(), takeoffPoint.getZ(), destination.getX(), destination.getZ());
        } else if (egressToDestination > 2.0) {
            legs.add(new HighwayRoute.OffRampLeg(egressAnchor));
            appendMiningLegs(legs, egressAnchor, destination);
            totalCost += egressToDestination;
        }

        if (primary == null) {
            primary = syntheticCandidate(
                    spokeAxisForJunction(originJunction), HighwayCandidate.Category.CARDINAL, floorY,
                    originJunction, destinationJunction, null, 0.0);
            int[] travelDir = travelDirection(point(primary.entry), point(primary.exit), primary.axis);
            primaryDx = travelDir[0];
            primaryDz = travelDir[1];
        }

        return new RoutePlan(primary, legs, totalCost, primaryDx, primaryDz);
    }

    private static double addIngressLeg(List<HighwayRoute.Leg> legs,
                                        double distance,
                                        BlockPos target,
                                        double approachThreshold,
                                        boolean allowFlight) {
        // Do not use open-nether flight for the initial highway ingress.
        // At planning time we have not verified a launchable corridor yet,
        // and short ingress targets can incorrectly "arrive" immediately.
        if (distance > approachThreshold) {
            legs.add(new HighwayRoute.ApproachLeg(target));
            return distance;
        }
        return 0.0;
    }

    private static boolean needsIngressTravel(double distance, double approachThreshold, boolean allowFlight) {
        if (allowFlight) return distance > 2.0;
        return distance > approachThreshold;
    }

    private static void appendBounceLeg(List<HighwayRoute.Leg> legs,
                                        HighwayCandidate highway,
                                        int travelDx,
                                        int travelDz) {
        if (!legs.isEmpty()) {
            HighwayRoute.Leg previous = legs.get(legs.size() - 1);
            if (previous instanceof HighwayRoute.BounceLeg prevBounce
                    && (prevBounce.travelDx() != travelDx || prevBounce.travelDz() != travelDz)) {
                legs.add(new HighwayRoute.TurnLeg(highway.entry));
            }
        }
        legs.add(new HighwayRoute.BounceLeg(highway, highway.exit, travelDx, travelDz));
    }

    private static void appendMiningLegs(List<HighwayRoute.Leg> legs, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int horizontalDistance = Math.max(Math.abs(dx), Math.abs(dz));
        if (horizontalDistance <= 2) return;

        int segments = Math.max(1, (int) Math.ceil(horizontalDistance / (double) FREENETHER_MINING_LEG_LENGTH));
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            int x = from.getX() + (int) Math.round(dx * t);
            int y = from.getY() + (int) Math.round((to.getY() - from.getY()) * t);
            int z = from.getZ() + (int) Math.round(dz * t);
            legs.add(new HighwayRoute.MineLeg(new BlockPos(x, y, z)));
        }
    }

    private static boolean shouldUseSafeRingRoute(BlockPos origin,
                                                  BlockPos destination,
                                                  HighwayGeometry.GeometryCandidate best,
                                                  int[] onRampXZ,
                                                  int[] exitXZ) {
        if (best.category == HighwayCandidate.Category.RING) return false;
        if (isWithinSafeRing(origin) || isWithinSafeRing(destination)) return true;
        return segmentDistanceToOrigin(onRampXZ[0], onRampXZ[1], exitXZ[0], exitXZ[1])
                < HighwayRoute.SAFE_RING_RADIUS;
    }

    private static boolean isWithinSafeRing(BlockPos pos) {
        return Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ())) < HighwayRoute.SAFE_RING_RADIUS;
    }

    private static double segmentDistanceToOrigin(int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lenSq = dx * dx + dz * dz;
        if (lenSq == 0.0) return Math.sqrt((double) x1 * x1 + (double) z1 * z1);

        double t = -((double) x1 * dx + (double) z1 * dz) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double px = x1 + dx * t;
        double pz = z1 + dz * t;
        return Math.sqrt(px * px + pz * pz);
    }

    private static BlockPos ringJunctionForPoint(BlockPos point, BlockPos fallback, int floorY) {
        int x = point.getX();
        int z = point.getZ();
        if (x == 0 && z == 0 && fallback != null) {
            x = fallback.getX();
            z = fallback.getZ();
        }

        int absX = Math.abs(x);
        int absZ = Math.abs(z);
        if (absX >= absZ) {
            int sx = x >= 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
            return new BlockPos(sx, floorY, 0);
        }
        int sz = z >= 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
        return new BlockPos(0, floorY, sz);
    }

    private static HighwayCandidate.Axis spokeAxisForJunction(BlockPos junction) {
        if (junction.getX() > 0) return HighwayCandidate.Axis.PLUS_X;
        if (junction.getX() < 0) return HighwayCandidate.Axis.MINUS_X;
        return junction.getZ() >= 0 ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z;
    }

    private static BlockPos projectOntoSpoke(HighwayCandidate.Axis axis, BlockPos point, int floorY) {
        return switch (axis) {
            case PLUS_X, MINUS_X -> new BlockPos(point.getX(), floorY, 0);
            case PLUS_Z, MINUS_Z -> new BlockPos(0, floorY, point.getZ());
            default -> new BlockPos(point.getX(), floorY, point.getZ());
        };
    }

    private static List<RingSegment> planRingSegments(BlockPos start,
                                                      BlockPos end,
                                                      BlockPos destination) {
        List<RingSegment> segments = new ArrayList<>();
        if (start.getX() == end.getX() && start.getZ() == end.getZ()) return segments;

        if (start.getX() == 0 && end.getX() == 0) {
            int sideX = preferredRingX(start, end, destination);
            BlockPos sideA = new BlockPos(sideX, start.getY(), start.getZ());
            BlockPos sideB = new BlockPos(sideX, start.getY(), end.getZ());
            segments.add(new RingSegment(start, sideA, sideX > 0 ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X,
                    start.getZ() >= 0 ? HighwayCandidate.RingSide.SOUTH : HighwayCandidate.RingSide.NORTH));
            segments.add(new RingSegment(sideA, sideB, sideX > 0 ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z,
                    sideX > 0 ? HighwayCandidate.RingSide.EAST : HighwayCandidate.RingSide.WEST));
            segments.add(new RingSegment(sideB, end, sideB.getZ() >= 0 ? HighwayCandidate.Axis.MINUS_X : HighwayCandidate.Axis.PLUS_X,
                    end.getZ() >= 0 ? HighwayCandidate.RingSide.SOUTH : HighwayCandidate.RingSide.NORTH));
            return compactSegments(segments);
        }

        if (start.getZ() == 0 && end.getZ() == 0) {
            int sideZ = preferredRingZ(start, end, destination);
            BlockPos sideA = new BlockPos(start.getX(), start.getY(), sideZ);
            BlockPos sideB = new BlockPos(end.getX(), start.getY(), sideZ);
            segments.add(new RingSegment(start, sideA, sideZ > 0 ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z,
                    start.getX() >= 0 ? HighwayCandidate.RingSide.EAST : HighwayCandidate.RingSide.WEST));
            segments.add(new RingSegment(sideA, sideB, sideZ > 0 ? HighwayCandidate.Axis.MINUS_X : HighwayCandidate.Axis.PLUS_X,
                    sideZ > 0 ? HighwayCandidate.RingSide.SOUTH : HighwayCandidate.RingSide.NORTH));
            segments.add(new RingSegment(sideB, end, end.getX() >= 0 ? HighwayCandidate.Axis.MINUS_Z : HighwayCandidate.Axis.PLUS_Z,
                    end.getX() >= 0 ? HighwayCandidate.RingSide.EAST : HighwayCandidate.RingSide.WEST));
            return compactSegments(segments);
        }

        if (start.getX() == end.getX()) {
            segments.add(new RingSegment(start, end,
                    Integer.compare(end.getZ(), start.getZ()) >= 0
                            ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z,
                    start.getX() > 0 ? HighwayCandidate.RingSide.EAST : HighwayCandidate.RingSide.WEST));
            return segments;
        }

        if (start.getZ() == end.getZ()) {
            segments.add(new RingSegment(start, end,
                    Integer.compare(end.getX(), start.getX()) >= 0
                            ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X,
                    start.getZ() > 0 ? HighwayCandidate.RingSide.SOUTH : HighwayCandidate.RingSide.NORTH));
            return segments;
        }

        BlockPos corner = new BlockPos(
                start.getX() == 0 ? end.getX() : start.getX(),
                start.getY(),
                start.getZ() == 0 ? end.getZ() : start.getZ());
        if (start.getX() == 0) {
            segments.add(new RingSegment(start, corner,
                    Integer.compare(corner.getX(), start.getX()) >= 0
                            ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X,
                    start.getZ() > 0 ? HighwayCandidate.RingSide.SOUTH : HighwayCandidate.RingSide.NORTH));
            segments.add(new RingSegment(corner, end,
                    Integer.compare(end.getZ(), corner.getZ()) >= 0
                            ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z,
                    corner.getX() > 0 ? HighwayCandidate.RingSide.EAST : HighwayCandidate.RingSide.WEST));
        } else {
            segments.add(new RingSegment(start, corner,
                    Integer.compare(corner.getZ(), start.getZ()) >= 0
                            ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z,
                    start.getX() > 0 ? HighwayCandidate.RingSide.EAST : HighwayCandidate.RingSide.WEST));
            segments.add(new RingSegment(corner, end,
                    Integer.compare(end.getX(), corner.getX()) >= 0
                            ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X,
                    corner.getZ() > 0 ? HighwayCandidate.RingSide.SOUTH : HighwayCandidate.RingSide.NORTH));
        }
        return compactSegments(segments);
    }

    private static List<RingSegment> compactSegments(List<RingSegment> segments) {
        List<RingSegment> compacted = new ArrayList<>();
        for (RingSegment segment : segments) {
            if (segment.start.getX() == segment.end.getX() && segment.start.getZ() == segment.end.getZ()) continue;
            compacted.add(segment);
        }
        return compacted;
    }

    private static int preferredRingX(BlockPos start, BlockPos end, BlockPos destination) {
        if (destination != null && destination.getX() != 0) {
            return destination.getX() > 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
        }
        if (start.getX() != 0) return start.getX() > 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
        return end.getX() > 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
    }

    private static int preferredRingZ(BlockPos start, BlockPos end, BlockPos destination) {
        if (destination != null && destination.getZ() != 0) {
            return destination.getZ() > 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
        }
        if (start.getZ() != 0) return start.getZ() > 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
        return end.getZ() > 0 ? HighwayRoute.SAFE_RING_RADIUS : -HighwayRoute.SAFE_RING_RADIUS;
    }

    private static HighwayCandidate syntheticCandidate(HighwayCandidate.Axis axis,
                                                       HighwayCandidate.Category category,
                                                       int floorY,
                                                       BlockPos entry,
                                                       BlockPos exit,
                                                       HighwayCandidate.RingSide ringSide,
                                                       double ringDistance) {
        return new HighwayCandidate(
                axis, category, floorY, entry, exit, SYNTHETIC_CONFIDENCE,
                ringDistance, ringSide, null, 0, false, false);
    }

    private static int refineFloorY(BlockPos probe, HighwayCandidate.Axis axis, int fallbackFloorY) {
        return HighwayDetectorBridge.get().scanAt(probe, axis)
                .map(HighwayDetectorBridge.ScanResult::floorY)
                .orElse(fallbackFloorY);
    }

    private static BlockPos computeTakeoffPoint(BlockPos exit, BlockPos destination, int floorY) {
        int dx = destination.getX() - exit.getX();
        int dz = destination.getZ() - exit.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.0001) return new BlockPos(exit.getX(), floorY, exit.getZ());

        double distance = Math.min(FREENETHER_TAKEOFF_DISTANCE, length);
        int tx = exit.getX() + (int) Math.round(dx / length * distance);
        int tz = exit.getZ() + (int) Math.round(dz / length * distance);
        return new BlockPos(tx, floorY, tz);
    }

    private static BlockPos withY(BlockPos pos, int y) {
        return new BlockPos(pos.getX(), y, pos.getZ());
    }

    private static BlockPos directionalAnchor(BlockPos pos, int dx, int dz) {
        if (dx == 0 && dz == 0) return pos;
        return new BlockPos(
                pos.getX() + dx * INTERSECTION_DIRECTIONAL_OFFSET,
                pos.getY(),
                pos.getZ() + dz * INTERSECTION_DIRECTIONAL_OFFSET);
    }

    private static int[] point(BlockPos pos) {
        return new int[]{pos.getX(), pos.getZ()};
    }

    private static int[] travelDirection(HighwayGeometry.GeometryCandidate c,
                                         int[] onRampXZ,
                                         int[] exitXZ) {
        return travelDirection(onRampXZ, exitXZ, c.axis);
    }

    private static int[] travelDirection(int[] onRampXZ,
                                         int[] exitXZ,
                                         HighwayCandidate.Axis axis) {
        int tx = Integer.compare(exitXZ[0], onRampXZ[0]);
        int tz = Integer.compare(exitXZ[1], onRampXZ[1]);
        if (tx == 0 && tz == 0) {
            tx = axis.stepDx;
            tz = axis.stepDz;
        }
        return new int[]{tx, tz};
    }

    private static HighwayGeometry.GeometryCandidate fallbackAxis(int ox, int oz, int dx, int dz) {
        int absDx = Math.abs(dx - ox);
        int absDz = Math.abs(dz - oz);
        if (absDx > 1000 && absDz > 1000) {
            int smaller = Math.min(absDx, absDz);
            int larger  = Math.max(absDx, absDz);
            if (smaller > larger * 0.7f) {
                boolean px = (dx - ox) >= 0;
                boolean pz = (dz - oz) >= 0;
                HighwayCandidate.Axis diag = px && pz  ? HighwayCandidate.Axis.DIAG_PX_PZ
                        : (!px && !pz)                 ? HighwayCandidate.Axis.DIAG_MX_MZ
                        : px                           ? HighwayCandidate.Axis.DIAG_PX_MZ
                        :                                HighwayCandidate.Axis.DIAG_MX_PZ;
                return new HighwayGeometry.GeometryCandidate(diag, 0.05f);
            }
        }
        HighwayCandidate.Axis axis = absDx >= absDz
                ? (dx >= ox ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X)
                : (dz >= oz ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z);
        return new HighwayGeometry.GeometryCandidate(axis, 0.05f);
    }
}
