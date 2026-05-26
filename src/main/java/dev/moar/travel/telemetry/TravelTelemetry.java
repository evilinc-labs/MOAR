package dev.moar.travel.telemetry;

import dev.moar.travel.MovementOwner;
import dev.moar.travel.TravelPhase;
import dev.moar.travel.highway.IntegrityReport;
import dev.moar.travel.plan.HighwayCandidate;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

/** Immutable snapshot of the travel system's state. */
public record TravelTelemetry(
        long missionId,
        TravelPhase phase,
        MovementOwner owner,
        int ticksInPhase,
        int missionTicks,
        BlockPos destination,
        BlockPos currentTarget,
        HighwayCandidate selectedHighway,
        String lastTransitionReason,
        String abortReason,
        boolean baritonePathing,
        boolean baritoneStuck,
        // ── Highway verifier ──────────────────────────────────────
        IntegrityReport integrityReport,
        // ── Reserved for later milestones ─────────────────────────
        double currentSpeed,
        double baselineSpeed,
        int rocketsLastMinute,
        String rocketMode,
        boolean baritoneElytraOwning,
        String launchPhase
) {
    public static TravelTelemetry idle() {
        return new TravelTelemetry(
                0, TravelPhase.IDLE, MovementOwner.NONE, 0, 0,
                null, null, null, "", "",
                false, false,
                IntegrityReport.insufficient(),
                0.0, 0.0, 0, "n/a", false, "n/a"
        );
    }
}
