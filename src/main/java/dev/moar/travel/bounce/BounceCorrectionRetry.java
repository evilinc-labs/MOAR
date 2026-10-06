package dev.moar.travel.bounce;

// Permit one flight retry after a correction storm, then hold ground travel
// for the rest of this highway leg if the retried flight is corrected.
final class BounceCorrectionRetry {
    private boolean retryArmed;
    private boolean retryAccepted;
    private boolean locked;
    private int correctionBaseline;

    void reset() {
        retryArmed = false;
        retryAccepted = false;
        locked = false;
        correctionBaseline = 0;
    }

    void arm(int totalCorrectionEpisodes) {
        if (locked) return;
        retryArmed = true;
        retryAccepted = false;
        correctionBaseline = totalCorrectionEpisodes;
    }

    void cancel() {
        retryArmed = false;
        retryAccepted = false;
    }

    void flightAccepted(int totalCorrectionEpisodes) {
        if (!retryArmed || retryAccepted) return;
        retryAccepted = true;
        correctionBaseline = totalCorrectionEpisodes;
    }

    boolean correctionObserved(int totalCorrectionEpisodes) {
        if (!retryArmed || !retryAccepted
                || totalCorrectionEpisodes <= correctionBaseline) return false;
        cancel();
        locked = true;
        return true;
    }

    boolean isLocked() { return locked; }
}
