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

package network.darkhelmet.prism.core.storage.adapters.h2;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import network.darkhelmet.prism.api.activities.ActivityQuery;
import network.darkhelmet.prism.core.storage.adapters.sql.SqlActivityQueryBuilder;
import network.darkhelmet.prism.core.storage.dbo.records.PrismActivitiesRecord;
import network.darkhelmet.prism.loader.services.configuration.ConfigurationService;

import org.jooq.DSLContext;
import org.jooq.DeleteQuery;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.types.UInteger;

import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_ACTIONS;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_ACTIVITIES;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_CAUSES;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_ENTITY_TYPES;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_MATERIALS;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_PLAYERS;
import static network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter.PRISM_WORLDS;

public class H2ActivityQueryBuilder extends SqlActivityQueryBuilder {
    /**
     * Construct a new query builder.
     *
     * @param configurationService The configuration service
     * @param create The DSL context
     */
    @Inject
    public H2ActivityQueryBuilder(
            ConfigurationService configurationService,
            @Assisted DSLContext create) {
        super(configurationService, create);
    }

    /**
     * Deletes activity records in batches.
     *
     * <p>Because H2 doesn't support the USING clause, this builds a DELETE ... WHERE activity_id IN
     * with a SELECT subquery. This is not worth using for MySQL/Postgres because it's slower
     * than USING.</p>
     *
     * @param query The query
     * @param cycleMinPrimaryKey The min primary key
     * @param cycleMaxPrimaryKey The max primary key
     * @return The count of deleted rows
     */
    @Override
    public int deleteActivities(ActivityQuery query, int cycleMinPrimaryKey, int cycleMaxPrimaryKey) {
        SelectQuery<Record> selectQueryBuilder = create.selectQuery();
        selectQueryBuilder.addSelect(PRISM_ACTIVITIES.ACTIVITY_ID);
        selectQueryBuilder.addFrom(PRISM_ACTIVITIES);

        if (!query.actionTypes().isEmpty() || !query.actionTypeKeys().isEmpty()) {
            selectQueryBuilder.addJoin(PRISM_ACTIONS, PRISM_ACTIONS.ACTION_ID.equal(PRISM_ACTIVITIES.ACTION_ID));
        }

        if (query.cause() != null) {
            selectQueryBuilder.addJoin(PRISM_CAUSES, PRISM_CAUSES.CAUSE_ID.equal(PRISM_ACTIVITIES.CAUSE_ID));
        }

        if (!query.entityTypes().isEmpty()) {
            selectQueryBuilder.addJoin(
                PRISM_ENTITY_TYPES, PRISM_ENTITY_TYPES.ENTITY_TYPE_ID.equal(PRISM_ACTIVITIES.ENTITY_TYPE_ID));
        }

        if (!query.materials().isEmpty()) {
            selectQueryBuilder.addJoin(
                PRISM_MATERIALS, PRISM_MATERIALS.MATERIAL_ID.equal(PRISM_ACTIVITIES.MATERIAL_ID));
        }

        if (!query.playerNames().isEmpty()) {
            selectQueryBuilder.addJoin(PRISM_CAUSES, PRISM_CAUSES.CAUSE_ID.equal(PRISM_ACTIVITIES.CAUSE_ID));
            selectQueryBuilder.addJoin(PRISM_PLAYERS, PRISM_PLAYERS.PLAYER_ID.equal(PRISM_CAUSES.PLAYER_ID));
        }

        if (query.worldUuid() != null) {
            selectQueryBuilder.addJoin(PRISM_WORLDS, PRISM_WORLDS.WORLD_ID.equal(PRISM_ACTIVITIES.WORLD_ID));
        }

        // Add conditions
        selectQueryBuilder.addConditions(conditions(query));

        // Limit
        selectQueryBuilder.addConditions(PRISM_ACTIVITIES.ACTIVITY_ID
            .between(UInteger.valueOf(cycleMinPrimaryKey), UInteger.valueOf(cycleMaxPrimaryKey)));

        // Build the delete query
        DeleteQuery<PrismActivitiesRecord> deleteQueryBuilder = create.deleteQuery(PRISM_ACTIVITIES);
        deleteQueryBuilder
            .addConditions(PRISM_ACTIVITIES.ACTIVITY_ID.in(selectQueryBuilder.asField()));

        return deleteQueryBuilder.execute();
    }
}
