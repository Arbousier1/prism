/*
 * prism
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

package network.darkhelmet.prism.api.storage;

import java.util.List;

import network.darkhelmet.prism.api.PaginatedResults;
import network.darkhelmet.prism.api.activities.AbstractActivity;
import network.darkhelmet.prism.api.activities.Activity;
import network.darkhelmet.prism.api.activities.ActivityQuery;
import network.darkhelmet.prism.api.util.Pair;

public interface StorageAdapter {
    /**
     * Close any connections. May not be applicable to the chosen storage.
     */
    void close();

    /**
     * Creates a new batch manager.
     *
     * @return The batch
     */
    ActivityBatch createActivityBatch();

    /**
     * Delete records from the activities table.
     *
     * @param query The query
     * @param cycleMinPrimaryKey The minimum primary key for this cycle
     * @param cycleMaxPrimaryKey The maximum primary key for this cycle
     * @return The number of deleted records
     */
    int deleteActivities(ActivityQuery query, int cycleMinPrimaryKey, int cycleMaxPrimaryKey);

    /**
     * Get the min/max primary keys for the activities table.
     *
     * @return A min/max pair.
     */
    Pair<Integer, Integer> getActivitiesPkBounds();

    /**
     * Set the reversed bit for activities.
     *
     * @param activityIds The activity ids
     * @param reversed Whether the activity was reversed
     * @throws Exception Storage layer exception
     */
    void markReversed(List<Long> activityIds, boolean reversed) throws Exception;

    /**
     * Query activities in a non-paginated format (needed for world modification).
     *
     * @param query The activity query
     * @return List of activities
     * @throws Exception Storage layer exception
     */
    List<Activity> queryActivities(ActivityQuery query) throws Exception;

    /**
     * Query activities in a format intended for information display.
     *
     * @param query The activity query
     * @return Paginated list of activities
     * @throws Exception Storage layer exception
     */
    PaginatedResults<AbstractActivity> queryActivitiesPaginated(ActivityQuery query) throws Exception;

    /**
     * Check whether this storage system is enabled and ready.
     *
     * @return True if successfully initialized.
     */
    boolean ready();
}
