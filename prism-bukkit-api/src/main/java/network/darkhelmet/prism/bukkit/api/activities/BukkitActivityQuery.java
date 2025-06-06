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

package network.darkhelmet.prism.bukkit.api.activities;

import lombok.experimental.SuperBuilder;

import network.darkhelmet.prism.api.activities.ActivityQuery;

import org.bukkit.Location;

@SuperBuilder(toBuilder = true)
public class BukkitActivityQuery extends ActivityQuery {
    public abstract static class BukkitActivityQueryBuilder
            <C extends BukkitActivityQuery, B extends BukkitActivityQueryBuilder<C, B>>
            extends ActivityQueryBuilder<C, B> {
        /**
         * Set the world uuid and coordinate from a Location.
         *
         * @param location Location
         * @return The builder
         */
        public B location(Location location) {
            this.worldUuid(location.getWorld().getUID());
            this.coordinate(location.getX(), location.getY(), location.getZ());
            return self();
        }
    }
}
