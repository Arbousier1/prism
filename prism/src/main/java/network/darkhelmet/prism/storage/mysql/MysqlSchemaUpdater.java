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

package network.darkhelmet.prism.storage.mysql;

import co.aikar.idb.DB;

import com.google.inject.Inject;

import java.sql.SQLException;

import network.darkhelmet.prism.services.configuration.StorageConfiguration;
import network.darkhelmet.prism.utils.TypeUtils;

import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.intellij.lang.annotations.Language;

public class MysqlSchemaUpdater {
    /**
     * The logger.
     */
    private final Logger logger;

    /**
     * Construct the updater.
     *
     * @param logger The logger
     */
    @Inject
    public MysqlSchemaUpdater(Logger logger) {
        this.logger = logger;
    }

    /**
     * Updates schema from 8 (Prism 2.x & 3.x) to v4 (4.x).
     *
     * @param storageConfig The storage config
     * @throws SQLException The database exception
     */
    public boolean update_8_to_v4(
        StorageConfiguration storageConfig) throws SQLException {
        logger.info("Beginning database schema update to v4. This make take some time...");

        // -------------
        // ACTIONS TABLE
        // -------------

        // Decrease the size of the action_id col
        @Language("SQL") String actionPk = "ALTER TABLE `" + storageConfig.prefix() + "actions`"
            + "CHANGE COLUMN `action_id` `action_id` TINYINT UNSIGNED NOT NULL AUTO_INCREMENT ";
        DB.executeUpdate(actionPk);

        // ----------------
        // DATA/EXTRA TABLE
        // ----------------

        // Rename data table
        @Language("SQL") String renameData = "ALTER TABLE `" + storageConfig.prefix() + "data` "
            + "RENAME TO `" + storageConfig.prefix() + "activities`";
        DB.executeUpdate(renameData);

        // Drop foreign key in extra table
        @Language("SQL") String dropExtraFk = "ALTER TABLE `" + storageConfig.prefix() + "data_extra` "
            + "DROP FOREIGN KEY `" + storageConfig.prefix() + "data_extra_ibfk_1`;";
        DB.executeUpdate(dropExtraFk);

        // Update data table (don't drop player_id yet)
        @Language("SQL") String updateData = "ALTER TABLE `" + storageConfig.prefix() + "activities`"
            + "DROP COLUMN `block_subid`,"
            + "DROP COLUMN `old_block_subid`,"
            + "CHANGE COLUMN `action_id` `action_id` TINYINT UNSIGNED NOT NULL AFTER `z`,"
            + "CHANGE COLUMN `id` `activity_id` INT UNSIGNED NOT NULL AUTO_INCREMENT,"
            + "CHANGE COLUMN `epoch` `timestamp` INT UNSIGNED NOT NULL,"
            + "CHANGE COLUMN `world_id` `world_id` TINYINT UNSIGNED NOT NULL ,"
            + "CHANGE COLUMN `block_id` `material_id` SMALLINT UNSIGNED NULL,"
            + "CHANGE COLUMN `old_block_id` `old_material_id` SMALLINT UNSIGNED NULL DEFAULT NULL,"
            + "CHANGE COLUMN `player_id` `player_id` INT UNSIGNED NOT NULL AFTER `old_material_id`,"
            + "ADD COLUMN `cause_id` INT UNSIGNED NOT NULL AFTER `player_id`,"
            + "ADD COLUMN `entity_type_id` SMALLINT UNSIGNED NULL AFTER `old_material_id`;";
        DB.executeUpdate(updateData);

        // Rename data extra table
        @Language("SQL") String renameDataExtra = "ALTER TABLE `" + storageConfig.prefix() + "data_extra` "
            + "RENAME TO `" + storageConfig.prefix() + "activities_custom_data`";
        DB.executeUpdate(renameDataExtra);

        // Update activities data table
        @Language("SQL") String updateDataExtra = "ALTER TABLE `" + storageConfig.prefix() + "activities_custom_data`"
            + "DROP INDEX `data_id`,"
            + "CHANGE COLUMN `extra_id` `extra_id` INT UNSIGNED NOT NULL AUTO_INCREMENT,"
            + "CHANGE COLUMN `data_id` `activity_id` INT UNSIGNED NOT NULL,"
            + "ADD COLUMN `version` SMALLINT NULL AFTER `activity_id`,"
            + "ADD UNIQUE INDEX `activity_id_UNIQUE` (`activity_id` ASC);";
        DB.executeUpdate(updateDataExtra);

        // ------------
        // ID MAP TABLE
        // ------------

        // Drop block subid column from id mapping. What a relic.
        @Language("SQL") String updateIdMap = "ALTER TABLE `" + storageConfig.prefix() + "id_map`"
            + "DROP COLUMN `block_subid`;";
        DB.executeUpdate(updateIdMap);

        // Rename id map table
        @Language("SQL") String renameId = "ALTER TABLE `" + storageConfig.prefix() + "id_map` "
            + "RENAME TO `" + storageConfig.prefix() + "materials`";
        DB.executeUpdate(renameId);

        // Change material data schema
        @Language("SQL") String materialSchema = "ALTER TABLE `" + storageConfig.prefix() + "materials` "
            + "CHANGE COLUMN `block_id` `material_id` SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT FIRST,"
            + "CHANGE COLUMN `material` `material` VARCHAR(45) NULL,"
            + "CHANGE COLUMN `state` `data` VARCHAR(155) NULL,"
            + "DROP PRIMARY KEY,"
            + "DROP INDEX `block_id`,"
            + "ADD PRIMARY KEY (`material_id`),"
            + "ADD UNIQUE INDEX `materialData` (`material` ASC, `data` ASC);";
        DB.executeUpdate(materialSchema);

        // ----------
        // META TABLE
        // ----------

        // Change material data schema
        @Language("SQL") String metaSchema = "ALTER TABLE `" + storageConfig.prefix() + "meta` "
            + "CHANGE COLUMN `id` `meta_id` TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,"
            + "ADD UNIQUE INDEX `k` (`k` ASC);";
        DB.executeUpdate(metaSchema);

        // -------------
        // PLAYERS TABLE
        // -------------

        // Populate the causes table. Prism <= v3 treated non-players as fake players and there's
        // sadly no way to separate them here. Clean databases on v4 will have them separated.
        @Language("SQL") String causesPopulator = "INSERT INTO `" + storageConfig.prefix() + "causes` "
            + "(cause, player_id) SELECT null, player_id FROM `" + storageConfig.prefix() + "players` ";
        DB.executeUpdate(causesPopulator);

        // Convert activities table player_ids into cause_ids
        @Language("SQL") String causeIdPopulator = "UPDATE `" + storageConfig.prefix() + "activities` "
            + "SET cause_id = (SELECT cause_id FROM prism_causes "
            + "WHERE prism_causes.player_id = prism_activities.player_id);";
        DB.executeUpdate(causeIdPopulator);

        // Drop player_id col from activities
        @Language("SQL") String dropPlayerId = "ALTER TABLE `" + storageConfig.prefix() + "activities`"
            + "DROP COLUMN `player_id`;";
        DB.executeUpdate(dropPlayerId);

        // Update players table
        @Language("SQL") String updatePlayers = "ALTER TABLE `" + storageConfig.prefix() + "players`"
            + "DROP INDEX `player`,"
            + "ADD INDEX `player` (`player` ASC)";
        DB.executeUpdate(updatePlayers);

        // ------------
        // WORLDS TABLE
        // ------------

        // Add world_uuid column (but allow nulls, as no values exist
        @Language("SQL") String updateWorlds = "ALTER TABLE `" + storageConfig.prefix() + "worlds`"
            + "CHANGE COLUMN `world_id` `world_id` TINYINT UNSIGNED NOT NULL AUTO_INCREMENT,"
            + "ADD COLUMN `world_uuid` BINARY(16) NULL AFTER `world`,"
            + "ADD UNIQUE INDEX `world_uuid` (`world_uuid` ASC);";
        DB.executeUpdate(updateWorlds);

        // Update schema with world UUIDs
        for (World world : Bukkit.getServer().getWorlds()) {
            @Language("SQL") String sql = "UPDATE `" + storageConfig.prefix() + "worlds`"
                + "SET world_uuid = UNHEX(?) WHERE world = ?";

            String worldUid = TypeUtils.uuidToDbString(world.getUID());
            DB.executeInsert(sql, worldUid, world.getName());
        }

        // Delete worlds without a UUID
        @Language("SQL") String deleteWorlds = "DELETE FROM`" + storageConfig.prefix() + "worlds`"
            + "WHERE world_uuid IS NULL";
        int deletions = DB.executeUpdate(deleteWorlds);

        String worldMsg = "Deleted {} worlds from the database that are no longer present.";
        logger.info(worldMsg, deletions);

        // Make uuid non-null
        @Language("SQL") String removeWorldNames = "ALTER TABLE `" + storageConfig.prefix() + "worlds`"
            + "CHANGE COLUMN `world_uuid` `world_uuid` BINARY(16) NOT NULL;";
        DB.executeUpdate(removeWorldNames);

        // ------------
        // FOREIGN KEYS
        // ------------

        @Language("SQL") String addActivityFk = "ALTER TABLE `" + storageConfig.prefix() + "activities`"
            + "ADD INDEX `actionId_idx` (`action_id`),"
            + "ADD INDEX `causeId_idx` (`cause_id`),"
            + "ADD INDEX `entityTypeId_idx` (`entity_type_id`),"
            + "ADD INDEX `materialId_idx` (`material_id`),"
            + "ADD INDEX `oldMaterialId_idx` (`old_material_id`),"
            + "ADD INDEX `worldId_idx` (`world_id`),"
            + "ADD CONSTRAINT `actionId` FOREIGN KEY (`action_id`) REFERENCES `"
                + storageConfig.prefix() + "actions` (`action_id`),"
            + "ADD CONSTRAINT `causeId` FOREIGN KEY (`cause_id`) REFERENCES `"
                + storageConfig.prefix() + "causes` (`cause_id`),"
            + "ADD CONSTRAINT `entityTypeId` FOREIGN KEY (`entity_type_id`) REFERENCES `"
                + storageConfig.prefix() + "entity_types` (`entity_type_id`),"
            + "ADD CONSTRAINT `materialId` FOREIGN KEY (`material_id`) REFERENCES `"
                + storageConfig.prefix() + "materials` (`material_id`),"
            + "ADD CONSTRAINT `oldMaterialId` FOREIGN KEY (`old_material_id`) REFERENCES `"
                + storageConfig.prefix() + "materials` (`material_id`),"
            + "ADD CONSTRAINT `worldId` FOREIGN KEY (`world_id`) REFERENCES `"
                + storageConfig.prefix() + "worlds` (`world_id`);";
        DB.executeUpdate(addActivityFk);

        // --------------
        // SCHEMA VERSION
        // --------------

        // Update the schema version
        @Language("SQL") String updateSchema = "UPDATE " + storageConfig.prefix() + "meta "
            + "SET v = ? WHERE k = ?";
        DB.executeUpdate(updateSchema, "v4", "schema_ver");

        logger.info("Updated database schema to version: v4");

        return true;
    }
}
