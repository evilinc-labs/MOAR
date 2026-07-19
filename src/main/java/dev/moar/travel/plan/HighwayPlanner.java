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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Plan highway, mining, and optional flight legs.
public final class HighwayPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/HighwayPlanner");

    // Default highway floor Y before runtime refinement.
    private static final int DEFAULT_NETHER_FLOOR_Y = 120;
    // Confidence for planner-created highway segments.
    private static final float SYNTHETIC_CONFIDENCE = 0.75f;
    // Push entry past a junction so Baritone follows the planned branch.
    private static final int INTERSECTION_DIRECTIONAL_OFFSET = 8;
    // Favor staying on the current highway over leaving it for a better-looking road.
    private static final double CURRENT_HIGHWAY_STAY_BONUS = 1_024.0;
    private static final double OFF_NETWORK_INGRESS_PENALTY = 25_000.0;
    private static final double SAME_AXIS_INGRESS_PENALTY = 256.0;
    // Only fly highway ingress when the hop is meaningfully long.
    private static final int INGRESS_FLIGHT_MIN_DISTANCE = 64;
    // Mine this far off the highway before launch.
    private static final int FREENETHER_TAKEOFF_DISTANCE = 48;
    // Split off-ramp mining into short legs.
    private static final int FREENETHER_MINING_LEG_LENGTH = 12;

    // Avoid direct routes through configured hazard zones.
    private record HazardZone(int cx, int cz, int radius) {}
    private static final List<HazardZone> HAZARD_ZONES =
            List.of(new HazardZone(0, 0, HighwayRoute.SAFE_RING_RADIUS));

    // Force fallback waypoints to make forward progress.
    private static final int MIN_WAYPOINT_PROGRESS = 500;

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

    private record OriginHighway(HighwayCandidate.Axis axis,
                                 HighwayDetectorBridge.ScanResult scan) {}

    private record RingSegment(BlockPos start,
                               BlockPos end,
                               HighwayCandidate.Axis axis,
                               HighwayCandidate.RingSide side) {}

    private record RouteSafety(boolean safe, String reason) {}

    public Optional<HighwayRoute> plan(BlockPos origin, BlockPos destination, Options opts) {
        if (origin == null || destination == null) return Optional.empty();
        if (opts == null) opts = new Options();

        int floorY = opts.expectedFloorY != null ? opts.expectedFloorY : DEFAULT_NETHER_FLOOR_Y;

        int ox = origin.getX(),      oz = origin.getZ();
        int dx = destination.getX(), dz = destination.getZ();
        OriginHighway originHighway = detectOriginHighway(origin);

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

            double score = routeScore(route, candidate, originHighway);
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
                bestDirect.onRampXZ(), bestDirect.exitXZ())
                ? buildSafeRingRoute(origin, destination, opts, floorY, originHighway)
                : bestDirect.route();
        if (plan == null || plan.primary == null || plan.legs.isEmpty()) return Optional.empty();

        // Enforce the spawn boundary against the route that will execute.
        RouteSafety safety = inspectRouteSafety(origin, plan);
        if (!isWithinSafeRing(origin) && !isWithinSafeRing(destination) && !safety.safe()) {
            LOGGER.warn("[Travel] rejected unsafe direct route: {}", safety.reason());
            plan = buildSafeRingRoute(origin, destination, opts, floorY, originHighway);
            if (plan == null || plan.primary == null || plan.legs.isEmpty()) {
                LOGGER.warn("[Travel] safe-ring route construction failed");
                return Optional.empty();
            }
            safety = inspectRouteSafety(origin, plan);
            if (!safety.safe()) {
                LOGGER.warn("[Travel] rejected unsafe safe-ring route: {} route={}", safety.reason(), plan.legs());
                return Optional.empty();
            }
        }

        return Optional.of(new HighwayRoute(
                plan.primary, plan.legs, plan.totalCost, plan.travelDx, plan.travelDz));
    }

    // Suggest an interim highway waypoint when scanning cannot confirm one.
    public BlockPos suggestFlightWaypoint(BlockPos origin, BlockPos destination, Options opts) {
        if (opts == null) opts = new Options();
        int ox = origin.getX(), oz = origin.getZ();
        int dx = destination.getX(), dz = destination.getZ();

        List<HighwayGeometry.GeometryCandidate> candidates =
                HighwayGeometry.rankCandidates(dx, dz, opts.detectRings, opts.detectDiamonds);
        HighwayGeometry.GeometryCandidate best = null;
        for (HighwayGeometry.GeometryCandidate c : candidates) {
            if (c.confidence >= opts.minConfidence) {
                best = c;
                break;
            }
        }
        if (best == null) best = fallbackAxis(ox, oz, dx, dz);

        int[] onRampXZ = HighwayGeometry.projectOnto(best, ox, oz);

        // Push stalled waypoints farther toward the destination.
        if (HighwayGeometry.horizontalDistance(ox, oz, onRampXZ[0], onRampXZ[1]) < MIN_WAYPOINT_PROGRESS) {
            int stepDx = Integer.compare(dx, ox);
            int stepDz = Integer.compare(dz, oz);
            if (stepDx == 0 && stepDz == 0) {
                stepDx = best.axis.stepDx;
                stepDz = best.axis.stepDz;
            }
            onRampXZ = new int[]{ox + stepDx * MIN_WAYPOINT_PROGRESS, oz + stepDz * MIN_WAYPOINT_PROGRESS};
        }

        for (HazardZone zone : HAZARD_ZONES) {
            if (segmentDistanceToPoint(ox, oz, onRampXZ[0], onRampXZ[1], zone.cx(), zone.cz()) < zone.radius()) {
                int ringRadius = selectSafeRingDistance();
                BlockPos boundary = nearestSafeRingPoint(origin, origin.getY(), ringRadius);
                if (HighwayGeometry.horizontalDistance(
                        origin.getX(), origin.getZ(), boundary.getX(), boundary.getZ()) > 16.0) {
                    return boundary;
                }
                BlockPos destinationJunction = chooseRingJunctions(
                        origin, destination, origin.getY(), ringRadius)[1];
                List<RingSegment> segments = planRingSegments(boundary, destinationJunction, ringRadius);
                return segments.isEmpty() ? boundary : segments.get(0).end();
            }
        }
        return new BlockPos(onRampXZ[0], origin.getY(), onRampXZ[1]);
    }

    private static double routeScore(RoutePlan route,
                                     HighwayGeometry.GeometryCandidate candidate,
                                     OriginHighway originHighway) {
        // Prefer low-cost routes and break ties by confidence.
        double score = route.totalCost + (1.0 - candidate.confidence) * 64.0;
        if (originHighway == null) return score;

        HighwayRoute.Leg firstLeg = route.legs.isEmpty() ? null : route.legs.get(0);
        boolean startsOffNetwork = firstLeg instanceof HighwayRoute.ApproachLeg
                || firstLeg instanceof HighwayRoute.FlightLeg;
        boolean sameAxis = candidate.axis == originHighway.axis;

        if (startsOffNetwork) {
            score += sameAxis ? SAME_AXIS_INGRESS_PENALTY : OFF_NETWORK_INGRESS_PENALTY;
        } else if (sameAxis) {
            score -= CURRENT_HIGHWAY_STAY_BONUS;
        }
        return score;
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

        Optional<HighwayDetectorBridge.ScanResult> scan = confirmHighway(origin, best.axis, onRamp, floorY);
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
        boolean alreadyOnHighway = isReadyForIngressBounce(origin, primary, travelDir[0], travelDir[1]);
        boolean requiresIngressTravel = !alreadyOnHighway
                && needsIngressTravel(originToOnRamp, approachThreshold, opts.allowFlight);
        if (requiresIngressTravel) {
            BlockPos directionalEntry = directionalAnchor(primary.entry, travelDir[0], travelDir[1]);
            primary = new HighwayCandidate(
                    primary.axis, primary.category, primary.floorY, directionalEntry, primary.exit, primary.confidence,
                    primary.ringOrDiamondDist, primary.ringSide, primary.diamondSegment,
                    primary.width, primary.hasLeftRail, primary.hasRightRail);
        }

        List<HighwayRoute.Leg> legs = new ArrayList<>();
        double totalCost = 0.0;
        totalCost += addIngressLeg(legs, origin, originToOnRamp, primary.entry, approachThreshold, opts.allowFlight);

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
                                         int floorYHint,
                                         OriginHighway originHighway) {
        List<HighwayRoute.Leg> legs = new ArrayList<>();
        double totalCost = 0.0;

        int ringRadius = selectSafeRingDistance();
        BlockPos[] ringJunctions = chooseRingJunctions(origin, destination, floorYHint, ringRadius);
        BlockPos originJunction = ringJunctions[0];
        BlockPos destinationJunction = ringJunctions[1];
        boolean preserveOriginHighway = canRideOriginHighwayToRing(origin, originHighway);
        if (preserveOriginHighway) {
            originJunction = ringIntersection(originHighway.axis(), origin, floorYHint, ringRadius);
        }

        HighwayCandidate primary = null;
        int primaryDx = 0;
        int primaryDz = 0;
        int floorY = floorYHint;

        if (!isWithinSafeRing(origin)) {
            HighwayCandidate.Axis originAxis = preserveOriginHighway
                    ? originHighway.axis()
                    : spokeAxisForJunction(originJunction);
            BlockPos originOnRamp = preserveOriginHighway
                    ? new BlockPos(originHighway.scan().centerX(), originHighway.scan().floorY(),
                            originHighway.scan().centerZ())
                    : projectOntoSpoke(originAxis, origin, floorY);
            Optional<HighwayDetectorBridge.ScanResult> originSpokeScan = confirmHighway(origin, originAxis, originOnRamp, floorY);
            if (originSpokeScan.isPresent()) {
                floorY = originSpokeScan.get().floorY();
            }
            originJunction = withY(originJunction, floorY);
            destinationJunction = withY(destinationJunction, floorY);
            originOnRamp = preserveOriginHighway
                    ? new BlockPos(originHighway.scan().centerX(), floorY, originHighway.scan().centerZ())
                    : projectOntoSpoke(originAxis, origin, floorY);

            double originToOnRamp = HighwayGeometry.horizontalDistance(
                    origin.getX(), origin.getZ(), originOnRamp.getX(), originOnRamp.getZ());
            int[] travelDir = travelDirection(point(originOnRamp), point(originJunction), originAxis);
            HighwayCandidate provisionalSpoke = syntheticCandidate(
                    originAxis, originAxis.diagonal ? HighwayCandidate.Category.DIAGONAL : HighwayCandidate.Category.CARDINAL,
                    floorY, originOnRamp, originJunction, null, 0.0);
            boolean requiresIngressTravel = !isReadyForIngressBounce(origin, provisionalSpoke, travelDir[0], travelDir[1])
                    && needsIngressTravel(originToOnRamp, 10.0, opts.allowFlight);
            BlockPos spokeEntry = requiresIngressTravel
                    ? directionalAnchor(originOnRamp, travelDir[0], travelDir[1])
                    : originOnRamp;
            totalCost += addIngressLeg(legs, origin, originToOnRamp, spokeEntry, 10.0, opts.allowFlight);

            HighwayCandidate spoke = syntheticCandidate(
                    originAxis, originAxis.diagonal ? HighwayCandidate.Category.DIAGONAL : HighwayCandidate.Category.CARDINAL,
                    floorY, spokeEntry, originJunction, null, 0.0);
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
            List<RingSegment> previewSegments = planRingSegments(originJunction, destinationJunction, ringRadius);
            BlockPos ingressTarget = originJunction;
            if (!previewSegments.isEmpty() && needsIngressTravel(originToRing, 10.0, opts.allowFlight)) {
                RingSegment first = previewSegments.get(0);
                int[] ingressDir = travelDirection(point(first.start), point(first.end), first.axis);
                ingressTarget = directionalAnchor(originJunction, ingressDir[0], ingressDir[1]);
            }
            totalCost += addIngressLeg(legs, origin, originToRing, ingressTarget, 10.0, opts.allowFlight);
        }

        List<RingSegment> ringSegments = planRingSegments(originJunction, destinationJunction, ringRadius);
        for (int i = 0; i < ringSegments.size(); i++) {
            RingSegment segment = ringSegments.get(i);
            if (!isValidRingSegment(segment, ringRadius)) return null;
            int[] travelDir = travelDirection(point(segment.start), point(segment.end), segment.axis);
            BlockPos segmentEntry = segment.start;
            if (i > 0 || isWithinSafeRing(origin)) {
                segmentEntry = directionalAnchor(segment.start, travelDir[0], travelDir[1]);
            }
            if (primary == null) {
                Optional<HighwayDetectorBridge.ScanResult> ringScan = confirmHighway(origin, segment.axis, segmentEntry, floorY);
                if (ringScan.isPresent()) {
                    floorY = ringScan.get().floorY();
                }
            }
            HighwayCandidate ring = syntheticCandidate(
                    segment.axis, HighwayCandidate.Category.RING, floorY,
                    segmentEntry, segment.end, segment.side, ringRadius);
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
                                        BlockPos origin,
                                        double distance,
                                        BlockPos target,
                                        double approachThreshold,
                                        boolean allowFlight) {
        boolean mostlyVertical = isMostlyVerticalGap(origin, target);
        if (allowFlight && !mostlyVertical && distance > Math.max(approachThreshold, INGRESS_FLIGHT_MIN_DISTANCE)) {
            legs.add(new HighwayRoute.FlightLeg(target));
            return distance;
        }
        if (distance > approachThreshold || mostlyVertical) {
            legs.add(new HighwayRoute.ApproachLeg(target));
            return distance;
        }
        return 0.0;
    }

    private static boolean needsIngressTravel(double distance, double approachThreshold, boolean allowFlight) {
        if (allowFlight) return distance > 2.0;
        return distance > approachThreshold;
    }

    // Keep steep ingress hops under Baritone control.
    private static boolean isMostlyVerticalGap(BlockPos origin, BlockPos target) {
        int horiz = (int) Math.round(HighwayGeometry.horizontalDistance(
                origin.getX(), origin.getZ(), target.getX(), target.getZ()));
        int vert = Math.abs(target.getY() - origin.getY());
        return vert > 8 && horiz < Math.max(16, vert / 2);
    }

    private static OriginHighway detectOriginHighway(BlockPos origin) {
        if (origin == null) return null;
        OriginHighway best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (HighwayCandidate.Axis axis : HighwayCandidate.Axis.values()) {
            Optional<HighwayDetectorBridge.ScanResult> scan = HighwayDetectorBridge.get().scanAt(origin, axis);
            if (scan.isEmpty()) continue;
            HighwayDetectorBridge.ScanResult result = scan.get();
            float score = result.blockConfidence();
            if (result.hasLeftRail()) score += 0.15f;
            if (result.hasRightRail()) score += 0.15f;
            score += Math.min(0.2f, result.width() * 0.03f);
            if (score > bestScore) {
                bestScore = score;
                best = new OriginHighway(axis, result);
            }
        }
        return best;
    }

    private static boolean isReadyForIngressBounce(BlockPos origin,
                                                   HighwayCandidate highway,
                                                   int travelDx,
                                                   int travelDz) {
        if (origin == null || highway == null || highway.entry == null) return false;
        if (highway.floorY > Integer.MIN_VALUE && Math.abs(origin.getY() - highway.floorY) > 2) {
            return false;
        }
        int dx = origin.getX() - highway.entry.getX();
        int dz = origin.getZ() - highway.entry.getZ();
        int perpDot = dx * highway.axis.perpDx() + dz * highway.axis.perpDz();
        int alongDot = dx * travelDx + dz * travelDz;
        int perpLimit = highway.axis.diagonal ? 4 : 3;
        if (Math.abs(perpDot) > perpLimit) return false;
        return alongDot >= -3;
    }

    private static void appendBounceLeg(List<HighwayRoute.Leg> legs,
                                        HighwayCandidate highway,
                                        int travelDx,
                                        int travelDz) {
        int[] normalized = normalizeTravelDirection(highway.axis, travelDx, travelDz);
        travelDx = normalized[0];
        travelDz = normalized[1];
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
                                                  int[] onRampXZ,
                                                  int[] exitXZ) {
        if (isWithinSafeRing(origin) || isWithinSafeRing(destination)) return true;
        for (HazardZone zone : HAZARD_ZONES) {
            if (segmentDistanceToPoint(onRampXZ[0], onRampXZ[1], exitXZ[0], exitXZ[1], zone.cx(), zone.cz())
                    < zone.radius()) {
                return true;
            }
        }
        return false;
    }

    private static RouteSafety inspectRouteSafety(BlockPos origin, RoutePlan route) {
        BlockPos cursor = origin;
        for (HighwayRoute.Leg leg : route.legs) {
            if (leg instanceof HighwayRoute.ApproachLeg approach) {
                RouteSafety safety = inspectSegment(cursor, approach.onRamp(), "approach");
                if (!safety.safe()) return safety;
                cursor = approach.onRamp();
            } else if (leg instanceof HighwayRoute.BounceLeg bounce) {
                RouteSafety safety = inspectSegment(cursor, bounce.highway().entry, "bounce-ingress");
                if (!safety.safe()) return safety;
                safety = inspectSegment(bounce.highway().entry, bounce.exitColumn(), "bounce");
                if (!safety.safe()) return safety;
                cursor = bounce.exitColumn();
            } else if (leg instanceof HighwayRoute.TurnLeg turn) {
                RouteSafety safety = inspectSegment(cursor, turn.branchTarget(), "turn");
                if (!safety.safe()) return safety;
                cursor = turn.branchTarget();
            } else if (leg instanceof HighwayRoute.OffRampLeg offRamp) {
                RouteSafety safety = inspectSegment(cursor, offRamp.handoffPoint(), "offramp");
                if (!safety.safe()) return safety;
                cursor = offRamp.handoffPoint();
            } else if (leg instanceof HighwayRoute.MineLeg mine) {
                RouteSafety safety = inspectSegment(cursor, mine.freeNetherTarget(), "mine");
                if (!safety.safe()) return safety;
                cursor = mine.freeNetherTarget();
            } else if (leg instanceof HighwayRoute.FlightLeg flight) {
                RouteSafety safety = inspectSegment(cursor, flight.destination(), "flight");
                if (!safety.safe()) return safety;
                cursor = flight.destination();
            }
        }
        return new RouteSafety(true, "clear");
    }

    private static RouteSafety inspectSegment(BlockPos from, BlockPos to, String kind) {
        for (HazardZone zone : HAZARD_ZONES) {
            double clearance = segmentDistanceToPoint(
                    from.getX(), from.getZ(), to.getX(), to.getZ(), zone.cx(), zone.cz());
            if (clearance < zone.radius()) {
                return new RouteSafety(false, kind + " " + from.toShortString() + " -> "
                        + to.toShortString() + " clearance=" + String.format("%.1f", clearance));
            }
        }
        return new RouteSafety(true, "clear");
    }

    private static boolean isWithinSafeRing(BlockPos pos) {
        for (HazardZone zone : HAZARD_ZONES) {
            if (Math.max(Math.abs(pos.getX() - zone.cx()), Math.abs(pos.getZ() - zone.cz())) < zone.radius()) {
                return true;
            }
        }
        return false;
    }

    // Keep a visible spawn highway as the ring ingress.
    private static boolean canRideOriginHighwayToRing(BlockPos origin, OriginHighway highway) {
        if (origin == null || highway == null || highway.scan() == null) return false;
        int x = highway.scan().centerX();
        int z = highway.scan().centerZ();
        int tolerance = Math.max(16, highway.scan().width() + 4);
        return switch (highway.axis()) {
            case PLUS_X, MINUS_X -> Math.abs(z) <= tolerance;
            case PLUS_Z, MINUS_Z -> Math.abs(x) <= tolerance;
            case DIAG_PX_PZ, DIAG_MX_MZ -> Math.abs(x - z) <= tolerance;
            case DIAG_PX_MZ, DIAG_MX_PZ -> Math.abs(x + z) <= tolerance;
        };
    }

    private static BlockPos ringIntersection(HighwayCandidate.Axis axis,
                                             BlockPos origin,
                                             int floorY,
                                             int ringRadius) {
        return switch (axis) {
            case PLUS_X, MINUS_X -> new BlockPos(origin.getX() >= 0 ? ringRadius : -ringRadius, floorY, 0);
            case PLUS_Z, MINUS_Z -> new BlockPos(0, floorY, origin.getZ() >= 0 ? ringRadius : -ringRadius);
            case DIAG_PX_PZ, DIAG_MX_MZ -> {
                int sign = origin.getX() + origin.getZ() >= 0 ? 1 : -1;
                yield new BlockPos(sign * ringRadius, floorY, sign * ringRadius);
            }
            case DIAG_PX_MZ, DIAG_MX_PZ -> {
                int sign = origin.getX() - origin.getZ() >= 0 ? 1 : -1;
                yield new BlockPos(sign * ringRadius, floorY, -sign * ringRadius);
            }
        };
    }
    private static double segmentDistanceToPoint(int x1, int z1, int x2, int z2, int px, int pz) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lenSq = dx * dx + dz * dz;
        if (lenSq == 0.0) {
            double ddx0 = x1 - px, ddz0 = z1 - pz;
            return Math.sqrt(ddx0 * ddx0 + ddz0 * ddz0);
        }
        double t = ((px - x1) * dx + (pz - z1) * dz) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double qx = x1 + dx * t;
        double qz = z1 + dz * t;
        double ddx = qx - px, ddz = qz - pz;
        return Math.sqrt(ddx * ddx + ddz * ddz);
    }

    // Refine a projected route from visible highway data when available.
    private static Optional<HighwayDetectorBridge.ScanResult> confirmHighway(
            BlockPos origin, HighwayCandidate.Axis axis, BlockPos onRamp, int floorYHint) {
        HighwayDetectorBridge bridge = HighwayDetectorBridge.get();
        Optional<HighwayDetectorBridge.ScanResult> scan = bridge.scanAt(origin, axis);
        if (scan.isPresent()) return scan;
        if (onRamp != null) {
            scan = bridge.scanAt(onRamp, axis);
            if (scan.isPresent()) return scan;
            BlockPos midpoint = new BlockPos(
                    (origin.getX() + onRamp.getX()) / 2, origin.getY(), (origin.getZ() + onRamp.getZ()) / 2);
            scan = bridge.scanAt(midpoint, axis);
            if (scan.isPresent()) return scan;
        }
        if (Math.abs(origin.getY() - floorYHint) > 4) {
            scan = bridge.scanAt(new BlockPos(origin.getX(), floorYHint, origin.getZ()), axis);
            if (scan.isPresent() || onRamp == null) return scan;
            scan = bridge.scanAt(new BlockPos(onRamp.getX(), floorYHint, onRamp.getZ()), axis);
        }
        return scan;
    }

    // Use the configured spawn boundary as the bypass ring.
    private static int selectSafeRingDistance() {
        return HighwayRoute.SAFE_RING_RADIUS;
    }

    private static BlockPos ringJunctionOnAxis(BlockPos point, boolean xAxis, int floorY, int ringRadius) {
        if (xAxis) {
            int sx = point.getX() >= 0 ? ringRadius : -ringRadius;
            return new BlockPos(sx, floorY, 0);
        }
        int sz = point.getZ() >= 0 ? ringRadius : -ringRadius;
        return new BlockPos(0, floorY, sz);
    }

    private static BlockPos nearestSafeRingPoint(BlockPos point, int floorY, int ringRadius) {
        int x = point.getX();
        int z = point.getZ();
        if (Math.abs(x) >= Math.abs(z)) {
            int boundaryX = x >= 0 ? ringRadius : -ringRadius;
            return new BlockPos(boundaryX, floorY, Math.max(-ringRadius, Math.min(ringRadius, z)));
        }
        int boundaryZ = z >= 0 ? ringRadius : -ringRadius;
        return new BlockPos(Math.max(-ringRadius, Math.min(ringRadius, x)), floorY, boundaryZ);
    }

    // Measure travel between square-ring junctions.
    private static double ringArcDistance(BlockPos a, BlockPos b, int ringRadius) {
        if (a.getX() == b.getX() && a.getZ() == b.getZ()) return 0.0;
        boolean aOnXAxis = a.getZ() == 0;
        boolean bOnXAxis = b.getZ() == 0;
        return aOnXAxis == bOnXAxis ? 4.0 * ringRadius : 2.0 * ringRadius;
    }

    // Choose the junction pair with the lowest total route cost.
    private static BlockPos[] chooseRingJunctions(BlockPos origin, BlockPos destination, int floorY, int ringRadius) {
        BlockPos originRef = (origin.getX() == 0 && origin.getZ() == 0) ? destination : origin;
        BlockPos destRef = (destination.getX() == 0 && destination.getZ() == 0) ? origin : destination;

        BlockPos[] originOptions = {
                ringJunctionOnAxis(originRef, true, floorY, ringRadius),
                ringJunctionOnAxis(originRef, false, floorY, ringRadius)
        };
        BlockPos[] destOptions = {
                ringJunctionOnAxis(destRef, true, floorY, ringRadius),
                ringJunctionOnAxis(destRef, false, floorY, ringRadius)
        };

        BlockPos bestOrigin = originOptions[0];
        BlockPos bestDest = destOptions[0];
        double bestCost = Double.POSITIVE_INFINITY;
        for (BlockPos oJ : originOptions) {
            double originLeg = HighwayGeometry.horizontalDistance(origin.getX(), origin.getZ(), oJ.getX(), oJ.getZ());
            for (BlockPos dJ : destOptions) {
                double destLeg = HighwayGeometry.horizontalDistance(
                        destination.getX(), destination.getZ(), dJ.getX(), dJ.getZ());
                double total = originLeg + ringArcDistance(oJ, dJ, ringRadius) + destLeg;
                if (total < bestCost) {
                    bestCost = total;
                    bestOrigin = oJ;
                    bestDest = dJ;
                }
            }
        }
        return new BlockPos[]{bestOrigin, bestDest};
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

    private static List<RingSegment> planRingSegments(BlockPos start, BlockPos end, int ringRadius) {
        long perimeter = 8L * ringRadius;
        long startOffset = ringOffset(start, ringRadius);
        long endOffset = ringOffset(end, ringRadius);
        if (startOffset < 0 || endOffset < 0 || startOffset == endOffset) return List.of();

        long clockwiseDistance = Math.floorMod(endOffset - startOffset, perimeter);
        if (clockwiseDistance <= perimeter - clockwiseDistance) {
            return boundarySegments(clockwisePoints(start, end, startOffset, endOffset, ringRadius));
        }

        List<BlockPos> reverse = clockwisePoints(end, start, endOffset, startOffset, ringRadius);
        java.util.Collections.reverse(reverse);
        return boundarySegments(reverse);
    }

    private static List<BlockPos> clockwisePoints(BlockPos start,
                                                   BlockPos end,
                                                   long startOffset,
                                                   long endOffset,
                                                   int ringRadius) {
        long perimeter = 8L * ringRadius;
        long edgeLength = 2L * ringRadius;
        long adjustedEnd = endOffset <= startOffset ? endOffset + perimeter : endOffset;
        List<BlockPos> points = new ArrayList<>();
        points.add(start);
        for (long corner = ((startOffset / edgeLength) + 1) * edgeLength;
             corner < adjustedEnd;
             corner += edgeLength) {
            points.add(ringPoint(corner % perimeter, start.getY(), ringRadius));
        }
        points.add(end);
        return points;
    }

    private static List<RingSegment> boundarySegments(List<BlockPos> points) {
        List<RingSegment> segments = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            BlockPos start = points.get(i - 1);
            BlockPos end = points.get(i);
            if (start.getX() == end.getX() && start.getZ() == end.getZ()) continue;

            HighwayCandidate.Axis axis;
            HighwayCandidate.RingSide side;
            if (start.getZ() == end.getZ()) {
                axis = end.getX() > start.getX() ? HighwayCandidate.Axis.PLUS_X : HighwayCandidate.Axis.MINUS_X;
                side = start.getZ() > 0 ? HighwayCandidate.RingSide.SOUTH : HighwayCandidate.RingSide.NORTH;
            } else if (start.getX() == end.getX()) {
                axis = end.getZ() > start.getZ() ? HighwayCandidate.Axis.PLUS_Z : HighwayCandidate.Axis.MINUS_Z;
                side = start.getX() > 0 ? HighwayCandidate.RingSide.EAST : HighwayCandidate.RingSide.WEST;
            } else {
                return List.of();
            }
            segments.add(new RingSegment(start, end, axis, side));
        }
        return segments;
    }

    private static long ringOffset(BlockPos point, int ringRadius) {
        int x = point.getX();
        int z = point.getZ();
        if (z == -ringRadius && x >= -ringRadius && x <= ringRadius) return x + (long) ringRadius;
        if (x == ringRadius && z >= -ringRadius && z <= ringRadius) return 2L * ringRadius + z + ringRadius;
        if (z == ringRadius && x >= -ringRadius && x <= ringRadius) return 4L * ringRadius + ringRadius - x;
        if (x == -ringRadius && z >= -ringRadius && z <= ringRadius) return 6L * ringRadius + ringRadius - z;
        return -1;
    }

    private static BlockPos ringPoint(long offset, int y, int ringRadius) {
        long edgeLength = 2L * ringRadius;
        if (offset < edgeLength) {
            return new BlockPos((int) (offset - ringRadius), y, -ringRadius);
        }
        if (offset < edgeLength * 2) {
            return new BlockPos(ringRadius, y, (int) (offset - edgeLength - ringRadius));
        }
        if (offset < edgeLength * 3) {
            return new BlockPos((int) (ringRadius - (offset - edgeLength * 2)), y, ringRadius);
        }
        return new BlockPos(-ringRadius, y, (int) (ringRadius - (offset - edgeLength * 3)));
    }

    private static boolean isValidRingSegment(RingSegment segment, int ringRadius) {
        boolean cardinal = segment.start.getX() == segment.end.getX()
                || segment.start.getZ() == segment.end.getZ();
        if (!cardinal) return false;
        if (ringOffset(segment.start, ringRadius) < 0 || ringOffset(segment.end, ringRadius) < 0) return false;
        return segmentDistanceToPoint(
                segment.start.getX(), segment.start.getZ(),
                segment.end.getX(), segment.end.getZ(), 0, 0) >= ringRadius;
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
        int dx = Integer.compare(exitXZ[0], onRampXZ[0]);
        int dz = Integer.compare(exitXZ[1], onRampXZ[1]);
        return normalizeTravelDirection(axis, dx, dz);
    }

    // Constrain movement to the selected highway axis.
    private static int[] normalizeTravelDirection(HighwayCandidate.Axis axis,
                                                  int requestedDx,
                                                  int requestedDz) {
        int projection = requestedDx * axis.stepDx + requestedDz * axis.stepDz;
        int direction = Integer.compare(projection, 0);
        if (direction == 0) direction = 1;
        return new int[]{axis.stepDx * direction, axis.stepDz * direction};
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
