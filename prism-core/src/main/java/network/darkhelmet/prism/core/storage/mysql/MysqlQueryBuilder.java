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

package network.darkhelmet.prism.core.storage.mysql;

import co.aikar.idb.DB;
import co.aikar.idb.DbRow;

import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import network.darkhelmet.prism.api.actions.types.IActionType;
import network.darkhelmet.prism.api.actions.types.IActionTypeRegistry;
import network.darkhelmet.prism.api.activities.ActivityQuery;
import network.darkhelmet.prism.core.services.logging.LoggingService;
import network.darkhelmet.prism.core.utils.TypeUtils;

import org.intellij.lang.annotations.Language;

public class MysqlQueryBuilder {
    /**
     * The action registry.
     */
    private final IActionTypeRegistry actionRegistry;

    /**
     * The logger.
     */
    private final LoggingService loggingService;

    /**
     * Construct a new query builder.
     *
     * @param actionRegistry The action registry
     * @param loggingService The logging service
     */
    @Inject
    public MysqlQueryBuilder(IActionTypeRegistry actionRegistry, LoggingService loggingService) {
        this.actionRegistry = actionRegistry;
        this.loggingService = loggingService;
    }

    /**
     * Query the activities table given an activity query object.
     *
     * @param query The activity query
     * @param prefix The table prefix
     * @return A list of DbRow results
     * @throws SQLException Database exception
     */
    public List<DbRow> queryActivities(ActivityQuery query, String prefix) throws SQLException {
        // Add all fields that are used in both grouping and non-grouping queries
        List<String> fields = new ArrayList<>();
        fields.add("HEX(`world_uuid`) AS worldUuid");
        fields.add("`action`");
        fields.add("`materials`.`material`");
        fields.add("`entity_type`");
        fields.add("`cause`");
        fields.add("`descriptor`");
        fields.add("HEX(`player_uuid`) AS playerUuid");
        fields.add("`player`");
        fields.add("COUNT(*) OVER() AS totalRows");

        if (query.grouped()) {
            // Add fields for grouped queries
            fields.add("AVG(`x`) AS `x`");
            fields.add("AVG(`y`) AS `y`");
            fields.add("AVG(`z`) AS `z`");
            fields.add("AVG(`timestamp`) AS `timestamp`");
            fields.add("COUNT(*) AS groupCount");
        } else {
            // Add fields for non-grouped queries
            fields.add("`timestamp`");
            fields.add("`x`");
            fields.add("`y`");
            fields.add("`z`");
            fields.add("`materials`.`data` AS material_data");
            fields.add("`oldMaterials`.`material` AS old_material");
            fields.add("`oldMaterials`.`data` AS old_material_data");
            fields.add("`custom_data`.`data` AS custom_data");
            fields.add("COALESCE(`custom_data`.`version`, 0) AS `data_version`");
        }

        @Language("SQL") String sql = "SELECT " + String.join(", ", fields) + " ";

        @Language("SQL") String from = "FROM " + prefix + "activities AS activities "
            + "JOIN " + prefix + "actions AS actions ON `actions`.`action_id` = `activities`.`action_id` "
            + "JOIN " + prefix + "causes AS causes ON `causes`.`cause_id` = `activities`.`cause_id` "
            + "JOIN " + prefix + "worlds AS worlds ON `worlds`.`world_id` = `activities`.`world_id` "
            + "LEFT JOIN " + prefix + "players AS players ON `players`.`player_id` = `causes`.`player_id` "
            + "LEFT JOIN " + prefix + "entity_types AS entity_types "
                + "ON `entity_types`.`entity_type_id` = `activities`.`entity_type_id` "
            + "LEFT JOIN " + prefix + "materials AS materials "
                + "ON `materials`.`material_id` = `activities`.`material_id` ";

        if (!query.grouped()) {
            @Language("SQL") String customData = "LEFT JOIN " + prefix + "activities_custom_data AS custom_data "
                + "ON `custom_data`.`activity_id` = `activities`.`activity_id` ";
            from += customData;

            @Language("SQL") String oldMats = "LEFT JOIN " + prefix + "materials AS oldMaterials "
                + "ON `oldMaterials`.`material_id` = `activities`.`old_material_id` ";
            from += oldMats;
        }

        sql += from;

        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        // Locations
        if (query.location() != null) {
            conditions.add("(`x` = ? AND `y` = ? AND `z` = ?)");
            parameters.add(query.location().intX());
            parameters.add(query.location().intY());
            parameters.add(query.location().intZ());
        } else if (query.minCoordinate() != null && query.maxCoordinate() != null) {
            conditions.add("(`x` BETWEEN ? AND ?)");
            conditions.add("(`y` BETWEEN ? AND ?)");
            conditions.add("(`z` BETWEEN ? AND ?)");

            parameters.add(query.minCoordinate().intX());
            parameters.add(query.maxCoordinate().intX());
            parameters.add(query.minCoordinate().intY());
            parameters.add(query.maxCoordinate().intY());
            parameters.add(query.minCoordinate().intZ());
            parameters.add(query.maxCoordinate().intZ());
        }

        // World
        if (query.worldUuid() != null) {
            conditions.add("`world_uuid` = UNHEX(?)");
            parameters.add(TypeUtils.uuidToDbString(query.worldUuid()));
        }

        // Actions
        if (!query.actionTypes().isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            for (IActionType actionType : query.actionTypes()) {
                if (query.lookup() || actionType.reversible()) {
                    placeholders.add("?");
                    parameters.add(actionType.key());
                }
            }

            conditions.add(String.format("`action` IN (%s)", String.join(",", placeholders)));
        }

        // Materials
        if (!query.materials().isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            for (String material : query.materials()) {
                placeholders.add("?");
                parameters.add(material.toLowerCase(Locale.ENGLISH));
            }

            conditions.add(String.format("`material` IN (%s)", String.join(",", placeholders)));
        }

        // Entity Types
        if (!query.entityTypes().isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            for (String entityType : query.entityTypes()) {
                placeholders.add("?");
                parameters.add(entityType.toLowerCase(Locale.ENGLISH));
            }

            conditions.add(String.format("`entity_type` IN (%s)", String.join(",", placeholders)));
        }

        // Players by name
        if (!query.playerNames().isEmpty()) {
            List<String> placeholders = new ArrayList<>();
            for (String playerName : query.playerNames()) {
                placeholders.add("?");
                parameters.add(playerName);
            }

            conditions.add(String.format("`player` IN (%s)", String.join(",", placeholders)));
        }

        // Cause
        if (query.cause() != null) {
            conditions.add("`cause` = ?");
            parameters.add(query.cause());
        }

        // Timestamps
        if (query.after() != null && query.before() != null) {
            conditions.add("`timestamp` BETWEEN ? AND ?");
            parameters.add(query.after());
            parameters.add(query.before());
        } else if (query.after() != null) {
            conditions.add("`timestamp` > ?");
            parameters.add(query.after());
        } else if (query.before() != null) {
            conditions.add("`timestamp` < ?");
            parameters.add(query.before());
        }

        if (!query.lookup() && query.actionTypes().isEmpty()) {
            // Include *only* reversible activities
            List<String> placeholders = new ArrayList<>();
            for (IActionType actionType : actionRegistry.actionTypes()) {
                if (actionType.reversible()) {
                    placeholders.add("?");
                    parameters.add(actionType.key());
                }
            }

            conditions.add(String.format("`action` IN (%s)", String.join(",", placeholders)));
        }

        if (conditions.size() > 0) {
            sql += "WHERE " + String.join(" AND ", conditions) + " ";
        }

        if (query.grouped()) {
            @Language("SQL") String groupBy = "GROUP BY `world_uuid`, `activities`.`action_id`, "
                + "`materials`.`material`, `entity_type`, `cause`, `player_uuid`, `descriptor` ";
            sql += groupBy;
        }

        // Order by
        String sortDir = "DESC";
        if (query.sort().equals(ActivityQuery.Sort.ASCENDING)) {
            sortDir = "ASC";
        }

        if (query.lookup() && query.grouped()) {
            @Language("SQL") String orderBy = " ORDER BY AVG(`timestamp`) %s ";
            sql += String.format(orderBy, sortDir);
        } else if (query.lookup()) {
            @Language("SQL") String orderBy = " ORDER BY `timestamp` %s ";
            sql += String.format(orderBy, sortDir);
        } else {
            // Most rollbacks "build up" but some hanging blocks need to be "built down" or they just break.
            // In order to do this, we tell hanging blocks to sort *after* everything else,
            // then we sort everything by `y asc` and sort these hanging blocks by `y desc`.
            // cave_vines are sorted to come after cave_vines_plant so the plant is rebuilt first.
            @Language("SQL") String orderBy = " ORDER BY "
                + "CASE `materials`.`material` WHEN 'cave_vines' THEN 1 ELSE -1 END ASC, "
                + "CASE `materials`.`material` WHEN 'cave_vines_plant' THEN 1 ELSE -1 END ASC, "
                + "CASE WHEN `materials`.`material` IN ('vine', 'pointed_dripstone') THEN 1 ELSE -1 END ASC, "
                + "`x` ASC, "
                + "`z` ASC, "
                + "CASE WHEN `materials`.`material` IN ('pointed_dripstone', 'cave_vines_plant', 'vine') "
                    + "THEN y END DESC, "
                + "CASE WHEN `materials`.`material` NOT IN ('pointed_dripstone', 'cave_vines_plant', 'vine') "
                    + "THEN y END ASC ";
            sql += orderBy;
        }

        // Limits
        if (query.limit() > 0) {
            sql += "LIMIT ?, ?";
            parameters.add(query.offset());
            parameters.add(query.limit());
        }

        loggingService.debug("Querying activities: %s", sql);
        for (int i = 0; i < parameters.size(); i++) {
            loggingService.debug("param[%d] %s", i, parameters.get(i));
        }

        return DB.getResults(sql, parameters.toArray());
    }
}
