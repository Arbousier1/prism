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

package network.darkhelmet.prism.bukkit.listeners.entity;

import com.google.inject.Inject;

import network.darkhelmet.prism.bukkit.actions.BukkitEntityAction;
import network.darkhelmet.prism.bukkit.actions.types.BukkitActionTypeRegistry;
import network.darkhelmet.prism.bukkit.api.activities.BukkitActivity;
import network.darkhelmet.prism.bukkit.listeners.AbstractListener;
import network.darkhelmet.prism.bukkit.services.expectations.ExpectationService;
import network.darkhelmet.prism.bukkit.services.recording.BukkitRecordingService;
import network.darkhelmet.prism.loader.services.configuration.ConfigurationService;

import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPlaceEvent;

public class EntityPlaceListener extends AbstractListener implements Listener {
    /**
     * Construct the listener.
     *
     * @param configurationService The configuration service
     * @param expectationService The expectation service
     * @param recordingService The recording service
     */
    @Inject
    public EntityPlaceListener(
            ConfigurationService configurationService,
            ExpectationService expectationService,
            BukkitRecordingService recordingService) {
        super(configurationService, expectationService, recordingService);
    }

    /**
     * Listens for entity place events.
     *
     * @param event The event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(final EntityPlaceEvent event) {
        if (event.getEntity() instanceof Vehicle vehicle) {
            // Ignore if this event is disabled
            if (!configurationService.prismConfig().actions().vehiclePlace()) {
                return;
            }

            var action = new BukkitEntityAction(BukkitActionTypeRegistry.VEHICLE_PLACE, vehicle);

            var activity = BukkitActivity.builder()
                .action(action)
                .player(event.getPlayer())
                .location(vehicle.getLocation())
                .build();

            recordingService.addToQueue(activity);
        } else {
            // Ignore if this event is disabled
            if (!configurationService.prismConfig().actions().entityPlace()) {
                return;
            }

            var action = new BukkitEntityAction(BukkitActionTypeRegistry.ENTITY_PLACE, event.getEntity());

            var activity = BukkitActivity.builder()
                .action(action)
                .player(event.getPlayer())
                .location(event.getEntity().getLocation())
                .build();

            recordingService.addToQueue(activity);
        }
    }
}
