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

package network.darkhelmet.prism.core.storage.adapters.sql;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.List;

import network.darkhelmet.prism.api.activities.ActivityQuery;
import network.darkhelmet.prism.core.storage.dbo.records.PrismActivitiesRecord;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismMaterials;
import network.darkhelmet.prism.loader.services.configuration.ConfigurationService;
import network.darkhelmet.prism.loader.services.configuration.storage.StorageConfiguration;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DeleteQuery;
import org.jooq.JoinType;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectQuery;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;

import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_ACTIONS;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_ACTIVITIES;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_CAUSES;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_ENTITY_TYPES;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_MATERIALS;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_PLAYERS;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_WORLDS;
import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;

public class SqlActivityQueryBuilder {
    /**
     * The configuration service.
     */
    protected final ConfigurationService configurationService;

    /**
     * The storage configuration.
     */
    protected final StorageConfiguration storageConfiguration;

    /**
     * The dsl context.
     */
    protected final DSLContext create;

    /**
     * The aliased old materials table.
     */
    protected final PrismMaterials OLD_MATERIALS;

    /**
     * Construct a new query builder.
     *
     * @param configurationService The configuration service
     * @param create The DSL context
     */
    @Inject
    public SqlActivityQueryBuilder(
            ConfigurationService configurationService,
            @Assisted DSLContext create) {
        this.configurationService = configurationService;
        storageConfiguration = configurationService.storageConfig();
        this.create = create;
        this.OLD_MATERIALS = PRISM_MATERIALS.as("old_materials");
    }

    /**
     * Delete records from the activities table.
     *
     * @param query The query
     * @param cycleMinPrimaryKey The min primary key
     * @param cycleMaxPrimaryKey The max primary key
     * @return The number of rows deleted
     */
    public int deleteActivities(ActivityQuery query, int cycleMinPrimaryKey, int cycleMaxPrimaryKey) {
        DeleteQuery<PrismActivitiesRecord> queryBuilder = create.deleteQuery(PRISM_ACTIVITIES);

        if (!query.actionTypes().isEmpty() || !query.actionTypeKeys().isEmpty()) {
            queryBuilder.addUsing(PRISM_ACTIONS);
            queryBuilder.addConditions(PRISM_ACTIVITIES.ACTION_ID.equal(PRISM_ACTIONS.ACTION_ID));
        }

        if (query.cause() != null) {
            queryBuilder.addUsing(PRISM_CAUSES);
            queryBuilder.addConditions(PRISM_ACTIVITIES.CAUSE_ID.equal(PRISM_CAUSES.CAUSE_ID));
        }

        if (!query.entityTypes().isEmpty()) {
            queryBuilder.addUsing(PRISM_ENTITY_TYPES);
            queryBuilder.addConditions(PRISM_ACTIVITIES.ENTITY_TYPE_ID.equal(PRISM_ENTITY_TYPES.ENTITY_TYPE_ID));
        }

        if (!query.materials().isEmpty()) {
            queryBuilder.addUsing(PRISM_MATERIALS);
            queryBuilder.addConditions(PRISM_ACTIVITIES.MATERIAL_ID.equal(PRISM_MATERIALS.MATERIAL_ID));
        }

        if (!query.playerNames().isEmpty()) {
            queryBuilder.addUsing(PRISM_CAUSES);
            queryBuilder.addUsing(PRISM_PLAYERS);
            queryBuilder.addConditions(PRISM_ACTIVITIES.CAUSE_ID.equal(PRISM_CAUSES.CAUSE_ID));
            queryBuilder.addConditions(PRISM_CAUSES.PLAYER_ID.equal(PRISM_PLAYERS.PLAYER_ID));
        }

        if (query.worldUuid() != null) {
            queryBuilder.addUsing(PRISM_WORLDS);
            queryBuilder.addConditions(PRISM_ACTIVITIES.WORLD_ID.equal(PRISM_WORLDS.WORLD_ID));
        }

        // Add conditions
        queryBuilder.addConditions(conditions(query));

        // Limit
        queryBuilder.addConditions(PRISM_ACTIVITIES.ACTIVITY_ID
            .between(UInteger.valueOf(cycleMinPrimaryKey), UInteger.valueOf(cycleMaxPrimaryKey)));

        return queryBuilder.execute();
    }

    /**
     * Query the activities table with a given activity query.
     *
     * @param query The activity query
     * @return A list of DbRow results
     */
    public Result<Record> queryActivities(ActivityQuery query) {
        SelectQuery<Record> queryBuilder = create.selectQuery();

        // Add fields useful for all query types
        queryBuilder.addSelect(
            PRISM_WORLDS.WORLD_UUID,
            PRISM_WORLDS.WORLD,
            PRISM_MATERIALS.MATERIAL,
            PRISM_ENTITY_TYPES.ENTITY_TYPE,
            PRISM_ACTIONS.ACTION,
            PRISM_CAUSES.CAUSE,
            PRISM_PLAYERS.PLAYER_UUID,
            PRISM_PLAYERS.PLAYER);

        // Add fields useful only for lookups
        if (query.lookup()) {
            queryBuilder.addSelect(PRISM_ACTIVITIES.DESCRIPTOR);
            queryBuilder.addSelect(PRISM_ACTIVITIES.METADATA);
            queryBuilder.addSelect(count().over().as("totalrows"));
        }

        if (query.grouped()) {
            // Add fields for grouped queries
            queryBuilder.addSelect(
                avg(PRISM_ACTIVITIES.X),
                avg(PRISM_ACTIVITIES.Y),
                avg(PRISM_ACTIVITIES.Z),
                avg(PRISM_ACTIVITIES.TIMESTAMP),
                count().as("groupcount"));
        } else {
            // Add fields for non-grouped queries
            queryBuilder.addSelect(
                PRISM_ACTIVITIES.ACTIVITY_ID,
                PRISM_ACTIVITIES.TIMESTAMP,
                PRISM_ACTIVITIES.X,
                PRISM_ACTIVITIES.Y,
                PRISM_ACTIVITIES.Z
            );
        }

        // Add fields only needed for modifications
        if (query.modification()) {
            queryBuilder.addSelect(
                PRISM_MATERIALS.DATA,
                PRISM_ACTIVITIES.SERIALIZED_DATA,
                coalesce(PRISM_ACTIVITIES.SERIALIZER_VERSION, 1).as("serializer_version"),
                OLD_MATERIALS.MATERIAL,
                OLD_MATERIALS.DATA
            );
        }

        queryBuilder.addFrom(PRISM_ACTIVITIES);
        queryBuilder.addJoin(PRISM_ACTIONS, PRISM_ACTIONS.ACTION_ID.equal(PRISM_ACTIVITIES.ACTION_ID));
        queryBuilder.addJoin(PRISM_WORLDS, PRISM_WORLDS.WORLD_ID.equal(PRISM_ACTIVITIES.WORLD_ID));
        queryBuilder.addJoin(PRISM_ENTITY_TYPES, JoinType.LEFT_OUTER_JOIN, PRISM_ENTITY_TYPES.ENTITY_TYPE_ID
            .equal(PRISM_ACTIVITIES.ENTITY_TYPE_ID));
        queryBuilder.addJoin(PRISM_MATERIALS, JoinType.LEFT_OUTER_JOIN, PRISM_MATERIALS.MATERIAL_ID
            .equal(PRISM_ACTIVITIES.MATERIAL_ID));
        queryBuilder.addJoin(PRISM_CAUSES, PRISM_CAUSES.CAUSE_ID.equal(PRISM_ACTIVITIES.CAUSE_ID));
        queryBuilder.addJoin(PRISM_PLAYERS, JoinType.LEFT_OUTER_JOIN, PRISM_PLAYERS.PLAYER_ID
            .equal(PRISM_CAUSES.PLAYER_ID));

        if (query.modification()) {
            queryBuilder.addJoin(OLD_MATERIALS, JoinType.LEFT_OUTER_JOIN, OLD_MATERIALS.MATERIAL_ID
                .equal(PRISM_ACTIVITIES.OLD_MATERIAL_ID));
        }

        // Add all conditions
        queryBuilder.addConditions(conditions(query));

        if (query.grouped()) {
            queryBuilder.addGroupBy(
                PRISM_ACTIONS.ACTION,
                PRISM_WORLDS.WORLD_UUID,
                PRISM_WORLDS.WORLD,
                PRISM_ACTIVITIES.ACTION_ID,
                PRISM_MATERIALS.MATERIAL,
                PRISM_ENTITY_TYPES.ENTITY_TYPE,
                PRISM_CAUSES.CAUSE,
                PRISM_PLAYERS.PLAYER,
                PRISM_PLAYERS.PLAYER_UUID,
                PRISM_ACTIVITIES.DESCRIPTOR,
                PRISM_ACTIVITIES.METADATA);
        }

        // Order by
        if (query.lookup() && query.grouped()) {
            if (query.sort().equals(ActivityQuery.Sort.ASCENDING)) {
                queryBuilder.addOrderBy(avg(PRISM_ACTIVITIES.TIMESTAMP).asc());
            } else {
                queryBuilder.addOrderBy(avg(PRISM_ACTIVITIES.TIMESTAMP).desc());
            }
        } else {
            if (query.sort().equals(ActivityQuery.Sort.ASCENDING)) {
                queryBuilder.addOrderBy(PRISM_ACTIVITIES.TIMESTAMP.asc());
            } else {
                queryBuilder.addOrderBy(PRISM_ACTIVITIES.TIMESTAMP.desc());
            }

            // Most rollbacks "build up" but some hanging blocks need to be "built down" or they just break.
            // In order to do this, we tell hanging blocks to sort *after* everything else,
            // then we sort everything by `y asc` and sort these hanging blocks by `y desc`.
            // cave_vines are sorted to come after cave_vines_plant so the plant is rebuilt first.
            queryBuilder.addOrderBy(DSL.decode()
                .when(PRISM_MATERIALS.MATERIAL.in("cave_vines", "weeping_vines"), 1)
                .else_(-1).asc());
            queryBuilder.addOrderBy(DSL.decode()
                .when(PRISM_MATERIALS.MATERIAL.in("cave_vines_plant", "weeping_vines_plant"), 1)
                .else_(-1).asc());

            queryBuilder.addOrderBy(DSL.decode()
                .when(PRISM_MATERIALS.MATERIAL
                .in("vine", "pointed_dripstone"), 1)
                .else_(-1).asc());

            queryBuilder.addOrderBy(PRISM_ACTIVITIES.X.asc());
            queryBuilder.addOrderBy(PRISM_ACTIVITIES.Z.asc());

            List<String> materialsToBuildUp = List.of(
                "pointed_dripstone",
                "cave_vines_plant",
                "weeping_vines_plant",
                "vine");

            queryBuilder.addOrderBy(DSL.decode()
                .when(PRISM_MATERIALS.MATERIAL
                .in(materialsToBuildUp), PRISM_ACTIVITIES.Y)
                .desc());

            queryBuilder.addOrderBy(DSL.decode()
                .when(PRISM_MATERIALS.MATERIAL
                .notIn(materialsToBuildUp), PRISM_ACTIVITIES.Y)
                .asc());
        }

        // Limits
        if (query.limit() > 0) {
            queryBuilder.addLimit(query.offset(), query.limit());
        }

        return queryBuilder.fetch();
    }

    /**
     * Get all conditions for the query.
     *
     * @param query The query
     * @return All conditions
     */
    protected List<Condition> conditions(ActivityQuery query) {
        List<Condition> conditions = new ArrayList<>();

        // Action Types
        if (!query.actionTypes().isEmpty()) {
            List<String> actionTypeKeys = new ArrayList<>();
            for (var actionType : query.actionTypes()) {
                if (query.lookup() || actionType.reversible()) {
                    actionTypeKeys.add(actionType.key());
                }
            }

            conditions.add(PRISM_ACTIONS.ACTION.in(actionTypeKeys));
        }

        // Action Type Keys
        if (!query.actionTypeKeys().isEmpty()) {
            conditions.add(PRISM_ACTIONS.ACTION.in(query.actionTypeKeys()));
        }

        // Activity IDs
        if (query.activityIds() != null && !query.activityIds().isEmpty()) {
            conditions.add(PRISM_ACTIVITIES.ACTIVITY_ID.in(query.activityIds()));
        }

        // Cause
        if (query.cause() != null) {
            conditions.add(PRISM_CAUSES.CAUSE.equal(query.cause()));
        }

        // Entity Types
        if (!query.entityTypes().isEmpty()) {
            conditions.add(PRISM_ENTITY_TYPES.ENTITY_TYPE.in(query.entityTypes()));
        }

        // Locations
        if (query.coordinate() != null) {
            conditions.add(PRISM_ACTIVITIES.X.equal(query.coordinate().intX()));
            conditions.add(PRISM_ACTIVITIES.Y.equal(query.coordinate().intY()));
            conditions.add(PRISM_ACTIVITIES.Z.equal(query.coordinate().intZ()));
        } else if (query.minCoordinate() != null && query.maxCoordinate() != null) {
            conditions.add(PRISM_ACTIVITIES.X
                .between(query.minCoordinate().intX(), query.maxCoordinate().intX()));
            conditions.add(PRISM_ACTIVITIES.Y
                .between(query.minCoordinate().intY(), query.maxCoordinate().intY()));
            conditions.add(PRISM_ACTIVITIES.Z
                .between(query.minCoordinate().intZ(), query.maxCoordinate().intZ()));
        }

        // Materials
        if (!query.materials().isEmpty()) {
            conditions.add(PRISM_MATERIALS.MATERIAL.in(query.materials()));
        }

        // Players by name
        if (!query.playerNames().isEmpty()) {
            conditions.add(PRISM_PLAYERS.PLAYER.in(query.playerNames()));
        }

        // Reversed
        if (query.reversed() != null) {
            conditions.add(PRISM_ACTIVITIES.REVERSED.eq(query.reversed()));
        }

        // Timestamps
        if (query.after() != null && query.before() != null) {
            conditions.add(PRISM_ACTIVITIES.TIMESTAMP
                    .between(UInteger.valueOf(query.after()), UInteger.valueOf(query.before())));
        } else if (query.after() != null) {
            conditions.add(PRISM_ACTIVITIES.TIMESTAMP.greaterThan(UInteger.valueOf(query.after())));
        } else if (query.before() != null) {
            conditions.add(PRISM_ACTIVITIES.TIMESTAMP.lessThan(UInteger.valueOf(query.before())));
        }

        // World
        if (query.worldUuid() != null) {
            conditions.add(PRISM_WORLDS.WORLD_UUID.equal(query.worldUuid().toString()));
        }

        return conditions;
    }
}
