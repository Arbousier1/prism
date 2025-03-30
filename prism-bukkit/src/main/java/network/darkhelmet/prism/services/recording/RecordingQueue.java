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

package network.darkhelmet.prism.services.recording;

import java.util.concurrent.LinkedBlockingQueue;

import network.darkhelmet.prism.api.activities.ISingleActivity;

public class RecordingQueue {
    /**
     * Queue of activities.
     */
    private static final LinkedBlockingQueue<ISingleActivity> queue = new LinkedBlockingQueue<>();

    /**
     * Add an activity to the recording queue.
     *
     * @param activity Activity
     */
    public static void addToQueue(final ISingleActivity activity) {
        if (activity == null) {
            return;
        }

        queue.add(activity);
    }

    /**
     * Get the queue.
     *
     * @return the queue
     */
    public static LinkedBlockingQueue<ISingleActivity> getQueue() {
        return queue;
    }
}