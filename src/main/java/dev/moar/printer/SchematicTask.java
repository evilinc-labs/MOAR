package dev.moar.printer;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.nio.file.Path;
import java.util.UUID;

/**
 * Represents a single schematic build task in the queue.
 * Tracks metadata, progress, and state for one schematic placement.
 */
public final class SchematicTask {

    private final UUID id;
    private final Path schematicPath;
    private final BlockPos anchor;
    private final String displayName;
    private final boolean autoDetected;
    private final long queuedTime;

    // Progress tracking
    private int blocksPlaced;
    private TaskState state;
    private String failureReason;
    private long startedTime;
    private long completedTime;

    public enum TaskState {
        QUEUED,      // Waiting to start
        ACTIVE,      // Currently building
        PAUSED,      // Temporarily paused (dimension change, etc.)
        COMPLETED,   // Successfully finished
        FAILED       // Build failed
    }

    private SchematicTask(UUID id, Path schematicPath, BlockPos anchor, 
                         String displayName, boolean autoDetected) {
        this.id = id;
        this.schematicPath = schematicPath;
        this.anchor = anchor;
        this.displayName = displayName;
        this.autoDetected = autoDetected;
        this.queuedTime = System.currentTimeMillis();
        this.state = TaskState.QUEUED;
        this.blocksPlaced = 0;
    }

    // Factory methods

    public static SchematicTask create(Path schematicPath, BlockPos anchor, String displayName) {
        return new SchematicTask(
            UUID.randomUUID(),
            schematicPath,
            anchor,
            displayName,
            false
        );
    }

    public static SchematicTask createAutoDetected(Path schematicPath, BlockPos anchor, String displayName) {
        return new SchematicTask(
            UUID.randomUUID(),
            schematicPath,
            anchor,
            displayName,
            true
        );
    }

    // State transitions

    public void markActive() {
        if (state == TaskState.QUEUED || state == TaskState.PAUSED) {
            state = TaskState.ACTIVE;
            if (startedTime == 0) {
                startedTime = System.currentTimeMillis();
            }
        }
    }

    public void markPaused() {
        if (state == TaskState.ACTIVE) {
            state = TaskState.PAUSED;
        }
    }

    public void markCompleted() {
        state = TaskState.COMPLETED;
        completedTime = System.currentTimeMillis();
    }

    public void markFailed(String reason) {
        state = TaskState.FAILED;
        failureReason = reason;
        completedTime = System.currentTimeMillis();
    }

    // Progress tracking

    public void updateProgress(int blocksPlaced) {
        this.blocksPlaced = blocksPlaced;
    }

    // Getters

    public UUID getId() { return id; }
    public Path getSchematicPath() { return schematicPath; }
    public BlockPos getAnchor() { return anchor; }
    public String getDisplayName() { return displayName; }
    public boolean isAutoDetected() { return autoDetected; }
    public long getQueuedTime() { return queuedTime; }
    public int getBlocksPlaced() { return blocksPlaced; }
    public TaskState getState() { return state; }
    public String getFailureReason() { return failureReason; }
    public long getStartedTime() { return startedTime; }
    public long getCompletedTime() { return completedTime; }

    public boolean isActive() { return state == TaskState.ACTIVE; }
    public boolean isQueued() { return state == TaskState.QUEUED; }
    public boolean isComplete() { return state == TaskState.COMPLETED; }
    public boolean isFailed() { return state == TaskState.FAILED; }
    public boolean isFinished() { return state == TaskState.COMPLETED || state == TaskState.FAILED; }

    public String getFileName() {
        return schematicPath.getFileName().toString();
    }

    public String getShortId() {
        return id.toString().substring(0, 8);
    }

    public String formatElapsedTime() {
        long elapsed;
        if (startedTime == 0) {
            return "not started";
        } else if (completedTime > 0) {
            elapsed = completedTime - startedTime;
        } else {
            elapsed = System.currentTimeMillis() - startedTime;
        }
        
        long seconds = elapsed / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    @Override
    public String toString() {
        return String.format("[%s] %s @ (%d, %d, %d) - %s - %d blocks",
            getShortId(), displayName, 
            anchor.getX(), anchor.getY(), anchor.getZ(),
            state, blocksPlaced);
    }
}
