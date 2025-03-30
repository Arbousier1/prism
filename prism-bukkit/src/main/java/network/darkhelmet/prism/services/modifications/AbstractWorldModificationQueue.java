/*
 * Prism (Refracted)
 *
 * Copyright (c) 2022 M Botsko (viveleroi)
 *                    Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package network.darkhelmet.prism.services.modifications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import network.darkhelmet.prism.PrismBukkit;
import network.darkhelmet.prism.api.activities.IActivity;
import network.darkhelmet.prism.api.services.modifications.IModificationQueue;
import network.darkhelmet.prism.api.services.modifications.ModificationQueueResult;
import network.darkhelmet.prism.api.services.modifications.ModificationQueueState;
import network.darkhelmet.prism.api.services.modifications.ModificationResult;
import network.darkhelmet.prism.api.services.modifications.ModificationResultStatus;
import network.darkhelmet.prism.api.services.modifications.StateChange;
import network.darkhelmet.prism.services.modifications.state.BlockStateChange;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public abstract class AbstractWorldModificationQueue implements IModificationQueue {
    /**
     * Manage a queue of pending modifications.
     */
    private final List<IActivity> modificationsQueue = Collections.synchronizedList(new LinkedList<>());

    /**
     * The onComplete handler.
     */
    private final Consumer<ModificationQueueResult> onComplete;

    /**
     * The period duration between executions of tasks.
     * @todo Move this to config
     */
    private final long taskPeriod = 5;

    /**
     * The maximum number of queue activities read per task run.
     * @todo Move this to config
     */
    private final int maxPerTask = 1000;

    /**
     * Toggle preview mode.
     */
    private boolean isPreview = false;

    /**
     * The owner.
     */
    private Object owner;

    /**
     * Cache the bukkit task id.
     */
    private int taskId;

    /**
     * Count how many were read from the queue.
     */
    private int countModificationsRead;

    /**
     * Count how many were applied.
     */
    private int countApplied = 0;

    /**
     * Count how many were planned.
     */
    private int countPlanned = 0;

    /**
     * Count how many were skipped.
     */
    private int countSkipped = 0;

    /**
     * A cache of resulting state changes.
     */
    private final List<StateChange<?>> stateChanges = new ArrayList<>();

    /**
     * Construct a new world modification.
     *
     * @param modifications A list of all modifications
     * @param onComplete The complete callback
     */
    public AbstractWorldModificationQueue(
            Object owner, final List<IActivity> modifications, Consumer<ModificationQueueResult> onComplete) {
        modificationsQueue.addAll(modifications);
        this.owner = owner;
        this.onComplete = onComplete;
    }

    /**
     * Apply a modification.
     *
     * @param activity The activity
     * @return The modification result
     */
    protected ModificationResult applyModification(IActivity activity) {
        return new ModificationResult(ModificationResultStatus.SKIPPED, null);
    }

    @Override
    public Object owner() {
        return owner;
    }

    /**
     * Apply any post-modification tasks.
     */
    protected void postProcess() {}

    @Override
    public void preview() {
        this.isPreview = true;
        execute();
    }

    @Override
    public boolean isPreview() {
        return isPreview;
    }

    @Override
    public void apply() {
        this.isPreview = false;
        execute();
    }

    protected void execute() {
        String queueSizeMsg = "Modification queue beginning application. Queue size: %d";
        PrismBukkit.getInstance().debug(String.format(queueSizeMsg, modificationsQueue.size()));

        if (!modificationsQueue.isEmpty()) {
            // Schedule a new sync task
            taskId = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(PrismBukkit.getInstance(), () -> {
                PrismBukkit.getInstance().debug("New modification run beginning...");

                int iterationCount = 0;
                final int currentQueueOffset = countModificationsRead;

                if (currentQueueOffset < modificationsQueue.size()) {
                    for (final Iterator<IActivity> iterator = modificationsQueue.listIterator(currentQueueOffset);
                         iterator.hasNext();) {
                        final IActivity activity = iterator.next();

                        // Simulate queue pointer advancement for previews
                        if (isPreview) {
                            countModificationsRead++;
                        }

                        // Limit the absolute max number of steps per execution of this task
                        if (++iterationCount >= maxPerTask) {
                            break;
                        }

                        // Delegate the modifications to the actions
                        ModificationResult result = applyModification(activity);
                        if (result.status().equals(ModificationResultStatus.PLANNED)) {
                            countPlanned++;
                        } else if (result.status().equals(ModificationResultStatus.APPLIED)) {
                            countApplied++;
                        } else {
                            countSkipped++;
                        }

                        // Remove from the queue if we're not previewing
                        if (!isPreview) {
                            iterator.remove();
                        }

                        if (result.stateChange() != null) {
                            stateChanges.add(result.stateChange());
                        }
                    }
                }

                // The task for this action is done being used
                if (modificationsQueue.isEmpty() || countModificationsRead >= modificationsQueue.size()) {
                    PrismBukkit.getInstance().debug("Modification queue now empty, finishing up.");

                    // Cancel the repeating task
                    Bukkit.getServer().getScheduler().cancelTask(taskId);

                    ModificationQueueState state = isPreview
                        ? ModificationQueueState.INCOMPLETE : ModificationQueueState.COMPLETE;

                    ModificationQueueResult result = new ModificationQueueResult(state,
                        countSkipped, countPlanned, countApplied);

                    // Execute the callback.
                    onComplete.accept(result);

                    // Post process
                    postProcess();
                }
            }, 0, taskPeriod);
        }
    }

    @Override
    public void cancel() {
        for (final Iterator<StateChange<?>> iterator = stateChanges.listIterator(); iterator.hasNext();) {
            final StateChange<?> stateChange = iterator.next();

            if (stateChange instanceof BlockStateChange blockStateChange) {
                ((Player) owner).sendBlockChange(
                    blockStateChange.oldState().getLocation(), blockStateChange.oldState().getBlockData());
            }

            iterator.remove();
        }

        stateChanges.clear();

        ModificationQueueResult result = new ModificationQueueResult(ModificationQueueState.COMPLETE, 0, 0, 0);

        // Execute the callback.
        onComplete.accept(result);
    }
}
