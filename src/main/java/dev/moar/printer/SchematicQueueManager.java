package dev.moar.printer;

import dev.moar.schematic.LitematicaDetector;
import dev.moar.util.ChatHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*? if >=26.1 {*//*
import net.minecraft.core.BlockPos;
*//*?} else {*/
import net.minecraft.util.math.BlockPos;
/*?}*/

import java.io.IOException;
import java.util.*;

/**
 * Manages a queue of schematic build tasks.
 * Builds one schematic at a time, automatically advancing to the next on completion.
 */
public final class SchematicQueueManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Queue");

    private final LinkedList<SchematicTask> queue = new LinkedList<>();
    private SchematicTask activeTask = null;
    private boolean autoAdvance = true;
    private boolean notifyOnCompletion = true;

    // Integration with SchematicPrinter
    private final SchematicPrinter printer;

    public SchematicQueueManager(SchematicPrinter printer) {
        this.printer = printer;
    }

    // Queue management

    /**
     * Add a task to the end of the queue.
     */
    public void enqueue(SchematicTask task) {
        queue.add(task);
        LOGGER.info("Enqueued task: {} ({} total in queue)", task.getDisplayName(), queue.size());
    }

    /**
     * Add a task at a specific position in the queue.
     * Position 0 = front of queue (next to build).
     */
    public void enqueueAt(SchematicTask task, int position) {
        int insertPos = Math.max(0, Math.min(position, queue.size()));
        queue.add(insertPos, task);
        LOGGER.info("Enqueued task at position {}: {}", insertPos, task.getDisplayName());
    }

    /**
     * Remove a task from the queue by ID.
     * Cannot remove the active task.
     */
    public boolean remove(UUID taskId) {
        if (activeTask != null && activeTask.getId().equals(taskId)) {
            LOGGER.warn("Cannot remove active task: {}", activeTask.getDisplayName());
            return false;
        }
        
        boolean removed = queue.removeIf(t -> t.getId().equals(taskId));
        if (removed) {
            LOGGER.info("Removed task from queue: {}", taskId);
        }
        return removed;
    }

    /**
     * Clear all queued tasks (not including active task).
     */
    public void clear() {
        int count = queue.size();
        queue.clear();
        LOGGER.info("Cleared {} tasks from queue", count);
    }

    /**
     * Move to the next task in the queue.
     * Stops the current task if one is active.
     */
    public boolean advanceQueue() {
        if (queue.isEmpty()) {
            LOGGER.debug("Queue is empty, cannot advance");
            return false;
        }

        // Stop current task if needed
        if (activeTask != null) {
            if (!activeTask.isFinished()) {
                LOGGER.warn("Force-advancing queue while task is still active: {}", 
                    activeTask.getDisplayName());
                printer.stopForQueueTransition();
                activeTask.markPaused();
                queue.addLast(activeTask);
            }
            activeTask = null;
        }

        // Start next task
        SchematicTask nextTask = queue.poll();
        if (nextTask == null) return false;

        return startTask(nextTask);
    }

    /**
     * Start the first queued task only when nothing is currently active.
     */
    public boolean startNextIfIdle() {
        if (activeTask != null) {
            LOGGER.debug("Queue already has an active task: {}", activeTask.getDisplayName());
            return false;
        }
        return advanceQueue();
    }

    /**
     * Skip the currently active task and move to the next.
     * Marks the current task as failed.
     */
    public boolean skipCurrent(String reason) {
        if (activeTask == null) {
            LOGGER.warn("No active task to skip");
            return false;
        }

        LOGGER.info("Skipping task: {} - Reason: {}", activeTask.getDisplayName(), reason);
        printer.stopForQueueTransition();
        activeTask.markFailed(reason != null ? reason : "Skipped by user");
        
        if (notifyOnCompletion) {
            ChatHelper.info("§e⊗ Skipped build: §f" + activeTask.getDisplayName() 
                + " §7(" + reason + ")");
        }

        activeTask = null;

        if (autoAdvance) {
            return advanceQueue();
        }
        return true;
    }

    /**
     * Pause the active queue task without advancing.
     */
    public boolean pauseActiveTask() {
        if (activeTask == null || activeTask.isFinished()) {
            return false;
        }

        printer.stopForQueueTransition();
        activeTask.markPaused();
        LOGGER.info("Paused active queue task: {}", activeTask.getDisplayName());
        return true;
    }

    // Task lifecycle

    private boolean startTask(SchematicTask task) {
        LOGGER.info("Starting task: {} @ ({}, {}, {})", 
            task.getDisplayName(),
            task.getAnchor().getX(),
            task.getAnchor().getY(), 
            task.getAnchor().getZ());

        try {
            if (printer.isEnabled()) {
                printer.stopForQueueTransition();
            }
            printer.loadSchematic(task.getSchematicPath(), task.getAnchor());
            task.markActive();
            activeTask = task;

            printer.setEnabled(true);

            ChatHelper.info("§a▶ Building: §f" + task.getDisplayName() 
                + " §7(" + queue.size() + " remaining in queue)");
            
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to load schematic for task: {}", task.getDisplayName(), e);
            task.markFailed("Failed to load schematic: " + e.getMessage());
            
            ChatHelper.info("§c✗ Failed to load: §f" + task.getDisplayName());
            
            // Try next task
            if (autoAdvance) {
                return advanceQueue();
            }
            return false;
        }
    }

    /**
     * Called every tick to check if the active task is complete.
     * Automatically advances to next task if enabled.
     */
    public void tick() {
        if (activeTask == null) return;

        if (!printer.isLoaded() && !printer.isEnabled()) {
            LOGGER.warn("Active queue task lost its loaded schematic; re-queueing: {}",
                activeTask.getDisplayName());
            activeTask.markPaused();
            queue.addFirst(activeTask);
            activeTask = null;
            return;
        }

        // Update progress from printer
        if (printer.isLoaded()) {
            activeTask.updateProgress(printer.getBlocksPlaced());
        }

        SchematicPrinter.BuildResult result = printer.consumeBuildResult();
        if (result != SchematicPrinter.BuildResult.NONE) {
            completeActiveTask(result);
        }
    }

    private void completeActiveTask(SchematicPrinter.BuildResult result) {
        if (activeTask == null) return;

        activeTask.markCompleted();
        
        LOGGER.info("Task completed: {} - {} blocks in {}", 
            activeTask.getDisplayName(),
            activeTask.getBlocksPlaced(),
            activeTask.formatElapsedTime());

        if (notifyOnCompletion) {
            String completionLabel = result == SchematicPrinter.BuildResult.COMPLETED_WITH_MISSING_MATERIALS
                ? "§e✓ Queue finished with missing materials: §f"
                : "§a✓ Completed: §f";
            ChatHelper.info(completionLabel + activeTask.getDisplayName()
                + " §7(" + activeTask.getBlocksPlaced() + " blocks in "
                + activeTask.formatElapsedTime() + ")");
            
            if (!queue.isEmpty()) {
                ChatHelper.info("§7" + queue.size() + " build" 
                    + (queue.size() == 1 ? "" : "s") + " remaining in queue");
            } else {
                ChatHelper.info("§a🎉 All builds complete!");
            }
        }

        activeTask = null;

        // Auto-advance to next task
        if (autoAdvance && !queue.isEmpty()) {
            advanceQueue();
        } else {
            printer.stopForQueueTransition();
        }
    }

    // Queue reordering

    /**
     * Move a task to a specific position in the queue (0-indexed).
     * Position 0 = front of queue (next to build).
     * Cannot move the active task.
     * 
     * @return true if successful, false if task not found or is active
     */
    public boolean moveTask(UUID taskId, int newPosition) {
        if (activeTask != null && activeTask.getId().equals(taskId)) {
            LOGGER.warn("Cannot reorder active task");
            return false;
        }

        // Find the task
        SchematicTask task = null;
        int currentIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(taskId)) {
                task = queue.get(i);
                currentIndex = i;
                break;
            }
        }

        if (task == null) {
            LOGGER.warn("Task not found in queue: {}", taskId);
            return false;
        }

        // Clamp position to valid range
        int targetPos = Math.max(0, Math.min(newPosition, queue.size() - 1));
        
        if (currentIndex == targetPos) {
            LOGGER.debug("Task already at position {}", targetPos);
            return true;
        }

        // Remove from current position and insert at new position
        queue.remove(currentIndex);
        queue.add(targetPos, task);
        
        LOGGER.info("Moved task '{}' from position {} to {}", 
            task.getDisplayName(), currentIndex, targetPos);
        return true;
    }

    /**
     * Move a task up one position in the queue (towards front).
     * 
     * @return true if successful, false if already at front or not found
     */
    public boolean moveTaskUp(UUID taskId) {
        int currentIndex = findTaskIndex(taskId);
        if (currentIndex < 0) {
            LOGGER.warn("Task not found: {}", taskId);
            return false;
        }
        
        if (currentIndex == 0) {
            LOGGER.debug("Task already at front of queue");
            return false;
        }
        
        return moveTask(taskId, currentIndex - 1);
    }

    /**
     * Move a task down one position in the queue (towards back).
     * 
     * @return true if successful, false if already at back or not found
     */
    public boolean moveTaskDown(UUID taskId) {
        int currentIndex = findTaskIndex(taskId);
        if (currentIndex < 0) {
            LOGGER.warn("Task not found: {}", taskId);
            return false;
        }
        
        if (currentIndex >= queue.size() - 1) {
            LOGGER.debug("Task already at back of queue");
            return false;
        }
        
        return moveTask(taskId, currentIndex + 1);
    }

    /**
     * Move a task to the front of the queue (will be built next).
     * 
     * @return true if successful, false if not found
     */
    public boolean moveTaskToFront(UUID taskId) {
        if (findTaskIndex(taskId) < 0) {
            LOGGER.warn("Task not found: {}", taskId);
            return false;
        }
        return moveTask(taskId, 0);
    }

    /**
     * Move a task to the back of the queue (will be built last).
     * 
     * @return true if successful, false if not found
     */
    public boolean moveTaskToBack(UUID taskId) {
        if (findTaskIndex(taskId) < 0) {
            LOGGER.warn("Task not found: {}", taskId);
            return false;
        }
        return moveTask(taskId, queue.size() - 1);
    }

    /**
     * Find a task by ID and return its short ID (first 8 chars).
     * Returns null if not found.
     */
    public String getTaskShortId(UUID taskId) {
        // Check active task
        if (activeTask != null && activeTask.getId().equals(taskId)) {
            return activeTask.getShortId();
        }
        
        // Check queue
        for (SchematicTask task : queue) {
            if (task.getId().equals(taskId)) {
                return task.getShortId();
            }
        }
        
        return null;
    }

    /**
     * Find a task by short ID (first 8 characters of UUID).
     * Returns null if not found or ambiguous.
     */
    public SchematicTask findTaskByShortId(String shortId) {
        SchematicTask found = null;
        
        // Check active task
        if (activeTask != null && activeTask.getShortId().startsWith(shortId)) {
            found = activeTask;
        }
        
        // Check queue
        for (SchematicTask task : queue) {
            if (task.getShortId().startsWith(shortId)) {
                if (found != null) {
                    // Ambiguous - multiple matches
                    LOGGER.warn("Ambiguous short ID: {} matches multiple tasks", shortId);
                    return null;
                }
                found = task;
            }
        }
        
        return found;
    }

    private int findTaskIndex(UUID taskId) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(taskId)) {
                return i;
            }
        }
        return -1;
    }

    // Queue inspection

    public List<SchematicTask> getTasks() {
        List<SchematicTask> all = new ArrayList<>();
        if (activeTask != null) {
            all.add(activeTask);
        }
        all.addAll(queue);
        return Collections.unmodifiableList(all);
    }

    public List<SchematicTask> getQueuedTasks() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    public SchematicTask getActiveTask() {
        return activeTask;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty() && activeTask == null;
    }

    public boolean hasActiveTask() {
        return activeTask != null;
    }

    public boolean hasQueuedTasks() {
        return !queue.isEmpty();
    }

    // Settings

    public void setAutoAdvance(boolean autoAdvance) {
        this.autoAdvance = autoAdvance;
        LOGGER.debug("Auto-advance: {}", autoAdvance);
    }

    public boolean isAutoAdvance() {
        return autoAdvance;
    }

    public void setNotifyOnCompletion(boolean notify) {
        this.notifyOnCompletion = notify;
    }

    public boolean isNotifyOnCompletion() {
        return notifyOnCompletion;
    }

    public void onDisconnect() {
        queue.clear();
        activeTask = null;
    }

    // Auto-detection integration

    /**
     * Detect all Litematica placements and add them to the queue.
     * Filters out placements with unsupported transforms.
     */
    public int enqueueFromDetection() {
        List<LitematicaDetector.DetectedPlacement> placements = 
            LitematicaDetector.detectPlacements();
        
        if (placements.isEmpty()) {
            LOGGER.info("No Litematica placements detected");
            return 0;
        }

        int added = 0;
        for (LitematicaDetector.DetectedPlacement p : placements) {
            // Skip unsupported transforms
            if (p.hasUnsupportedTransform()) {
                LOGGER.warn("Skipping placement with unsupported transform: {} ({})",
                    p.name(), p.unsupportedTransformSummary());
                continue;
            }

            try {
                // Load schematic temporarily to get offset
                var schematic = dev.moar.schematic.LitematicaSchematic.load(p.schematicPath());
                
                // Create anchor point with schematic offset
                BlockPos anchor = new BlockPos(
                    p.originX() + schematic.getOriginOffsetX(),
                    p.originY() + schematic.getOriginOffsetY(),
                    p.originZ() + schematic.getOriginOffsetZ()
                );

                SchematicTask task = SchematicTask.createAutoDetected(
                    p.schematicPath(),
                    anchor,
                    p.name()
                );

                enqueue(task);
                added++;
            } catch (Exception e) {
                LOGGER.warn("Failed to load schematic for queueing: {} - {}", 
                    p.name(), e.getMessage());
            }
        }

        LOGGER.info("Enqueued {} placement(s) from detection", added);
        return added;
    }

    /**
     * Format queue status for display.
     */
    public String formatStatus() {
        if (isEmpty()) {
            return "Queue is empty";
        }

        StringBuilder sb = new StringBuilder();
        
        if (activeTask != null) {
            sb.append("§a▶ Active: §f").append(activeTask.getDisplayName())
              .append(" §7(").append(activeTask.getBlocksPlaced()).append(" blocks, ")
              .append(activeTask.formatElapsedTime()).append(")");
        }

        if (!queue.isEmpty()) {
            if (activeTask != null) sb.append("\n");
            sb.append("§7Queued (").append(queue.size()).append("):");
            
            int index = 1;
            for (SchematicTask task : queue) {
                if (index > 5) {
                    sb.append("\n  §7... and ").append(queue.size() - 5).append(" more");
                    break;
                }
                sb.append("\n  §7").append(index).append(". §f")
                  .append(task.getDisplayName())
                  .append(" §8[").append(task.getShortId()).append("]");
                index++;
            }
        }

        return sb.toString();
    }
}
