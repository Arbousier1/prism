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

package network.darkhelmet.prism.api.activities;

import java.util.UUID;

import lombok.Getter;

import network.darkhelmet.prism.api.actions.Action;
import network.darkhelmet.prism.api.util.Coordinate;
import network.darkhelmet.prism.api.util.Pair;

/**
 * A grouped activity represents multiple activities being read
 * from storage and displayed to a user with a group count.
 */
@Getter
public final class GroupedActivity extends AbstractActivity {
    /**
     * The count.
     */
    private final int count;

    /**
     * Constructor.
     *
     * @param action The action
     * @param world The world
     * @param coordinate The average coordinate
     * @param cause The cause
     * @param player The player
     * @param timestamp The timestamp
     * @param count The count
     */
    public GroupedActivity(
            Action action,
            Pair<UUID, String> world,
            Coordinate coordinate,
            String cause,
            Pair<UUID, String> player,
            long timestamp,
            int count) {
        super(action, world, coordinate, cause, player, timestamp);
        this.count = count;
    }
}
