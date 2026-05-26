package dev.moar.travel.highway;

/** Immutable snapshot of a highway integrity sample. */
public record IntegrityReport(
        Status status,
        /** 0–1: how reliable is this report (decreases when many cells are unloaded). */
        float confidence,
        int sampledCells,
        int griefedCells,
        int unloadedCells,
        /** Along-axis step offset where grief begins, or −1 if no grief. */
        int griefStartOffset,
        /** Along-axis step offset where grief ends, or −1 if no grief. */
        int griefEndOffset
) {
    public enum Status { OK, GRIEFED, UNLOADED, INSUFFICIENT_DATA }

    public static IntegrityReport insufficient() {
        return new IntegrityReport(Status.INSUFFICIENT_DATA, 0f, 0, 0, 0, -1, -1);
    }

    public static IntegrityReport ok(int samples) {
        return new IntegrityReport(Status.OK, 1f, samples, 0, 0, -1, -1);
    }

    @Override
    public String toString() {
        return "IntegrityReport{" + status
                + " conf=" + String.format("%.2f", confidence)
                + " samples=" + sampledCells
                + " griefed=" + griefedCells
                + " unloaded=" + unloadedCells
                + (griefStartOffset >= 0 ? " griefRange=[" + griefStartOffset + "," + griefEndOffset + "]" : "")
                + "}";
    }
}
