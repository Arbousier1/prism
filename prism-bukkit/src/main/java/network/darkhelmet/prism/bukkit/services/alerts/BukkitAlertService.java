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

package network.darkhelmet.prism.bukkit.services.alerts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.TextColor;

import network.darkhelmet.prism.bukkit.actions.types.BukkitActionTypeRegistry;
import network.darkhelmet.prism.bukkit.api.activities.BukkitActivityQuery;
import network.darkhelmet.prism.bukkit.services.lookup.LookupService;
import network.darkhelmet.prism.bukkit.services.messages.MessageService;
import network.darkhelmet.prism.bukkit.utils.BlockUtils;
import network.darkhelmet.prism.bukkit.utils.CustomTag;
import network.darkhelmet.prism.bukkit.utils.ListUtils;
import network.darkhelmet.prism.bukkit.utils.VeinScanner;
import network.darkhelmet.prism.loader.services.configuration.ConfigurationService;
import network.darkhelmet.prism.loader.services.configuration.alerts.AlertConfiguration;
import network.darkhelmet.prism.loader.services.configuration.alerts.BlockAlertConfiguration;
import network.darkhelmet.prism.loader.services.configuration.cache.CacheConfiguration;
import network.darkhelmet.prism.loader.services.logging.LoggingService;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

@Singleton
public class BukkitAlertService {
    /**
     * Cache all block break alerts.
     */
    private final List<BlockBreakAlert> blockBreakAlerts = new ArrayList<>();

    /**
     * The configuration service.
     */
    private final ConfigurationService configurationService;

    /**
     * The logging service.
     */
    private final LoggingService loggingService;

    /**
     * The lookup service.
     */
    private final LookupService lookupService;

    /**
     * The message service.
     */
    private final MessageService messageService;

    /**
     * Cache locations.
     *
     * <p>We essentially ignore the player for now,
     * because all we care about is the location.</p>
     */
    private final Cache<Location, Player> locations;

    /**
     * Constructor.
     *
     * @param configurationService The configuration service
     * @param loggingService The logging service
     * @param lookupService The lookup service
     * @param messageService The message service
     */
    @Inject
    public BukkitAlertService(
            ConfigurationService configurationService,
            LoggingService loggingService,
            LookupService lookupService,
            MessageService messageService) {
        this.configurationService = configurationService;
        this.loggingService = loggingService;
        this.lookupService = lookupService;
        this.messageService = messageService;

        CacheConfiguration cacheConfiguration = configurationService.prismConfig().cache();

        locations = Caffeine.newBuilder()
            .maximumSize(cacheConfiguration.alertedLocations().maxSize())
            .expireAfterAccess(cacheConfiguration.alertedLocations().expiresAfterAccess().duration(),
                cacheConfiguration.alertedLocations().expiresAfterAccess().timeUnit())
            .evictionListener((key, value, cause) -> {
                String msg = "Evicting alerted location from cache: Key: %s, Value: %s, Removal Cause: %s";
                loggingService.debug(String.format(msg, key, value, cause));
            })
            .removalListener((key, value, cause) -> {
                String msg = "Removing alerted location from cache: Key: %s, Value: %s, Removal Cause: %s";
                loggingService.debug(String.format(msg, key, value, cause));
            }).build();

        loadAlerts();
    }

    /**
     * Trigger any configured alerts for a block break.
     *
     * @param block The block
     * @param player The player
     */
    public void alertBlockBreak(Block block, Player player) {
        // Ignore creative
        if (configurationService.prismConfig().alerts().ignoreCreative()
                && player.getGameMode().equals(GameMode.CREATIVE)) {
            return;
        }

        // Let players bypass
        if (player.hasPermission("prism.alert.bypass")) {
            return;
        }

        // Check location for recent alerts
        var lastTriggerer = locations.getIfPresent(block.getLocation());
        if (lastTriggerer != null) {
            return;
        }

        // Find the alert for this block type
        var alert = getBlockBreakAlert(block.getType());
        if (alert == null) {
            return;
        }

        // Apply light level bounds
        int lightLevel = BlockUtils.getLightLevel(block);
        if (lightLevel < configurationService.prismConfig().alerts().blockBreakAlerts().minLightLevel()
                || lightLevel > configurationService.prismConfig().alerts().blockBreakAlerts().maxLightLevel()) {
            return;
        }

        // Cache the block state as it's being changed
        var blockState = block.getState();

        var query = BukkitActivityQuery.builder()
            .grouped(false)
            .actionType(BukkitActionTypeRegistry.BLOCK_PLACE)
            .material(blockState.getType().toString().toLowerCase(Locale.ENGLISH))
            .location(blockState.getLocation())
            .limit(1)
            .build();

        lookupService.lookup(query, results -> {
            if (!results.isEmpty()) {
                return;
            }

            VeinScanner veinScanner = new VeinScanner(blockState, alert.materialTag(), alert.config().maxScanCount());
            List<Location> vein = veinScanner.scan();

            // Cache the vein locations
            for (var blockLocation : vein) {
                locations.put(blockLocation, player);
            }

            String blockName = blockState.getType().toString().replace("_", " ")
                .toLowerCase(Locale.ROOT).replace("glowing", " ");
            TextColor color = TextColor.fromCSSHexString(alert.config().hexColor());
            String count = vein.size() + (vein.size() >= alert.config().maxScanCount() ? "+" : "");

            boolean usingNightVision = false;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.NIGHT_VISION)) {
                    usingNightVision = true;
                    break;
                }
            }

            var data = new BlockBreakAlertData(
                player.getName(), blockName, color, count, lightLevel,
                    Key.key(blockState.getType().getKey().toString()));

            for (CommandSender receiver : getReceivers(player)) {
                if (usingNightVision) {
                    messageService.alertBlockBreakNightVision(receiver, data);
                } else {
                    messageService.alertBlockBreak(receiver, data);
                }
            }
        });
    }

    /**
     * Get an alert for this material, if any.
     *
     * @param material The material
     * @return The alert, if any
     */
    protected BlockBreakAlert getBlockBreakAlert(Material material) {
        for (var alert : blockBreakAlerts) {
            if (alert.materialTag().isTagged(material)) {
                return alert;
            }
        }

        return null;
    }

    /**
     * Load all alerts from the config.
     */
    public void loadAlerts() {
        blockBreakAlerts.clear();

        for (var config : configurationService.prismConfig().alerts().blockBreakAlerts().alerts()) {
            blockBreakAlerts.add(new BlockBreakAlert(config, loadMaterialTags(config)));
        }
    }

    /**
     * Load material tags.
     */
    protected Tag<Material> loadMaterialTags(AlertConfiguration config) {
        CustomTag<Material> materialTag = new CustomTag<>(Material.class);

        // Materials
        if (!ListUtils.isNullOrEmpty(config.materials())) {
            for (String materialKey : config.materials()) {
                try {
                    materialTag.append(Material.valueOf(materialKey.toUpperCase(Locale.ENGLISH)));
                } catch (IllegalArgumentException e) {
                    loggingService.warn(
                        "Alert config error: No material matching {0}", materialKey);
                }
            }
        }

        // Block material tags
        if (config instanceof BlockAlertConfiguration blockAlertConfiguration) {
            if (!ListUtils.isNullOrEmpty(blockAlertConfiguration.blockTags())) {
                for (String blockTag : blockAlertConfiguration.blockTags()) {
                    var namespacedKey = NamespacedKey.fromString(blockTag);
                    if (namespacedKey != null) {
                        var tag = Bukkit.getTag("blocks", namespacedKey, Material.class);
                        if (tag != null) {
                            materialTag.append(tag);

                            continue;
                        }
                    }

                    loggingService.warn("Alert config error: Invalid block tag {0}", blockTag);
                }
            }
        }

        return materialTag;
    }

    /**
     * Get all receivers of an alert.
     *
     * @param causingPlayer The player who triggered the alert
     * @return All receivers
     */
    protected List<CommandSender> getReceivers(Player causingPlayer) {
        List<CommandSender> receivers = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("prism.alerts.receive")
                    || (configurationService.prismConfig().alerts().ignoreSelf() && player.equals(causingPlayer))) {
                continue;
            }

            receivers.add(player);
        }

        return receivers;
    }
}
