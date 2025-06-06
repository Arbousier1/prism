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
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.UUID;

import network.darkhelmet.prism.api.PaginatedResults;
import network.darkhelmet.prism.api.actions.ActionData;
import network.darkhelmet.prism.api.actions.types.ActionTypeRegistry;
import network.darkhelmet.prism.api.activities.AbstractActivity;
import network.darkhelmet.prism.api.activities.Activity;
import network.darkhelmet.prism.api.activities.ActivityQuery;
import network.darkhelmet.prism.api.activities.GroupedActivity;
import network.darkhelmet.prism.api.storage.ActivityBatch;
import network.darkhelmet.prism.api.storage.StorageAdapter;
import network.darkhelmet.prism.api.util.Coordinate;
import network.darkhelmet.prism.api.util.Pair;
import network.darkhelmet.prism.core.injection.factories.SqlActivityQueryBuilderFactory;
import network.darkhelmet.prism.core.services.cache.CacheService;
import network.darkhelmet.prism.core.storage.HikariConfigFactory;
import network.darkhelmet.prism.core.storage.dbo.DefaultCatalog;
import network.darkhelmet.prism.core.storage.dbo.Indexes;
import network.darkhelmet.prism.core.storage.dbo.PrismDatabase;
import network.darkhelmet.prism.core.storage.dbo.records.PrismActionsRecord;
import network.darkhelmet.prism.core.storage.dbo.records.PrismEntityTypesRecord;
import network.darkhelmet.prism.core.storage.dbo.records.PrismMaterialsRecord;
import network.darkhelmet.prism.core.storage.dbo.records.PrismWorldsRecord;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismActions;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismActivities;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismCauses;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismEntityTypes;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismMaterials;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismMeta;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismPlayers;
import network.darkhelmet.prism.core.storage.dbo.tables.PrismWorlds;
import network.darkhelmet.prism.loader.services.configuration.ConfigurationService;
import network.darkhelmet.prism.loader.services.logging.LoggingService;

import org.jooq.DSLContext;
import org.jooq.Index;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.UShort;

import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.constraint;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;

public abstract class AbstractSqlStorageAdapter implements StorageAdapter {
    /**
     * The prism database object model.
     */
    public static PrismDatabase PRISM_DATABASE;

    /**
     * The actions dbo.
     */
    public static PrismActions PRISM_ACTIONS;

    /**
     * The activities dbo.
     */
    public static PrismActivities PRISM_ACTIVITIES;

    /**
     * The causes dbo.
     */
    public static PrismCauses PRISM_CAUSES;

    /**
     * The entity types dbo.
     */
    public static PrismEntityTypes PRISM_ENTITY_TYPES;

    /**
     * The materials dbo.
     */
    public static PrismMaterials PRISM_MATERIALS;

    /**
     * The meta dbo.
     */
    public static PrismMeta PRISM_META;

    /**
     * The players dbo.
     */
    public static PrismPlayers PRISM_PLAYERS;

    /**
     * The worlds dbo.
     */
    public static PrismWorlds PRISM_WORLDS;

    /**
     * The serializer version.
     */
    protected final short serializerVersion;

    /**
     * The logging service.
     */
    protected final LoggingService loggingService;

    /**
     * The configuration service.
     */
    protected final ConfigurationService configurationService;

    /**
     * The action type registry.
     */
    protected final ActionTypeRegistry actionRegistry;

    /**
     * The schema updater.
     */
    protected final SqlSchemaUpdater schemaUpdater;

    /**
     * The query factory.
     */
    protected final SqlActivityQueryBuilderFactory queryBuilderFactory;

    /**
     * The query builder.
     */
    protected SqlActivityQueryBuilder queryBuilder;

    /**
     * The cache service.
     */
    protected final CacheService cacheService;

    /**
     * The hikari data source.
     */
    protected HikariDataSource dataSource;

    /**
     * The dsl context.
     */
    protected DSLContext create;

    /**
     * The aliases materials table.
     */
    protected final PrismMaterials OLD_MATERIALS;

    /**
     * The schema/table prefix.
     */
    protected String prefix;

    /**
     * Toggle whether this storage system is enabled and ready.
     */
    protected boolean ready = false;

    /**
     * Constructor.
     *
     * @param loggingService The logging service
     * @param configurationService The configuration service
     * @param actionRegistry The action type registry
     * @param schemaUpdater The schema updater
     * @param cacheService The cache service
     * @param queryBuilderFactory The query builder
     * @param serializerVersion The serializer version
     */
    @Inject
    public AbstractSqlStorageAdapter(
            LoggingService loggingService,
            ConfigurationService configurationService,
            ActionTypeRegistry actionRegistry,
            SqlSchemaUpdater schemaUpdater,
            SqlActivityQueryBuilderFactory queryBuilderFactory,
            CacheService cacheService,
            @Named("serializerVersion") short serializerVersion) {
        this.loggingService = loggingService;
        this.configurationService = configurationService;
        this.actionRegistry = actionRegistry;
        this.schemaUpdater = schemaUpdater;
        this.cacheService = cacheService;
        this.queryBuilderFactory = queryBuilderFactory;
        this.serializerVersion = serializerVersion;

        this.prefix = configurationService.storageConfig().primaryDataSource().prefix();
        loggingService.info("Catalog {0}; Schema {1}; Prefix {2}",
            configurationService.storageConfig().primaryDataSource().catalog(),
            configurationService.storageConfig().primaryDataSource().schema(),
            prefix);

        var catalog = new DefaultCatalog(configurationService.storageConfig().primaryDataSource().catalog());

        // Initialize all of our DBOs
        PRISM_ACTIONS = new PrismActions(prefix);
        PRISM_ACTIVITIES = new PrismActivities(prefix);
        PRISM_CAUSES = new PrismCauses(prefix);
        PRISM_ENTITY_TYPES = new PrismEntityTypes(prefix);
        PRISM_MATERIALS = new PrismMaterials(prefix);
        PRISM_META = new PrismMeta(prefix);
        PRISM_PLAYERS = new PrismPlayers(prefix);
        PRISM_WORLDS = new PrismWorlds(prefix);
        PRISM_DATABASE = new PrismDatabase(
                catalog,
                configurationService.storageConfig().primaryDataSource().schema(),
                Arrays.asList(
            PRISM_ACTIONS,
            PRISM_ACTIVITIES,
            PRISM_CAUSES,
            PRISM_ENTITY_TYPES,
            PRISM_MATERIALS,
            PRISM_META,
            PRISM_PLAYERS,
            PRISM_WORLDS
        ));

        // Table aliases
        OLD_MATERIALS = PRISM_MATERIALS.as("old_materials");

        // Turn off jooq crap. Lame
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");

        // Load any drivers
        HikariConfigFactory.loadDriver(configurationService.storageConfig().primaryStorageType());
        listDrivers();
    }

    /**
     * Connect to the data source.
     *
     * @param hikariConfig The hikari config
     */
    protected boolean connect(HikariConfig hikariConfig, SQLDialect sqlDialect) {
        String url = hikariConfig.getDataSourceProperties().getProperty("url");
        if (url == null) {
            url = hikariConfig.getJdbcUrl();
        }

        loggingService.info("Connecting to {0}", url);

        try {
            dataSource = new HikariDataSource(hikariConfig);

            create = DSL.using(dataSource.getConnection(), sqlDialect);
            if (queryBuilderFactory != null) {
                this.queryBuilder = queryBuilderFactory.create(create);
            }

            return true;
        }  catch (Exception e) {
            String msg = "Failed to connect to your database server. Please check:\n"
                + "- the ip/address\n"
                + "- the port\n"
                + "- your username/password\n"
                + "- any firewall rules\n"
                + "- that the database server is running\n";
            loggingService.warn(msg);
        }

        return false;
    }

    /**
     * List all drivers available in the driver manager.
     */
    protected void listDrivers() {
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        if (!drivers.hasMoreElements()) {
            loggingService.info("No database drivers detected!");
        }
        while (drivers.hasMoreElements()) {
            loggingService.info("Database driver: {0}", drivers.nextElement().getClass());
        }
    }

    /**
     * Detect version and other information regarding the database.
     *
     * @throws SQLException The database exception
     */
    protected void describeDatabase(boolean usingHikariProperties) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();

            String databaseProduct = databaseMetaData.getDatabaseProductName();
            String databaseVersion = databaseMetaData.getDatabaseProductVersion();

            loggingService.info("Database: {0} {1}", databaseProduct, databaseVersion);
        }
    }

    /**
     * Create tables.
     *
     * @throws SQLException The database exception
     */
    protected void prepareSchema() throws Exception {
        // Create the metadata table
        create.createTableIfNotExists(PRISM_META)
            .column(PRISM_META.META_ID)
            .column(PRISM_META.K)
            .column(PRISM_META.V)
            .primaryKey(PRISM_META.META_ID)
            .unique(PRISM_META.K)
            .execute();

        // Query for an existing schema version
        String schemaVersion = create
            .select(PRISM_META.V)
            .from(PRISM_META)
            .where(PRISM_META.K.eq("schema_ver"))
            .fetchOne(PRISM_META.V);

        if (schemaVersion != null) {
            loggingService.info("Prism schema version: {0}", schemaVersion);

            updateSchemas(schemaVersion);
        } else {
            // Insert the schema version
            create.insertInto(PRISM_META, PRISM_META.K, PRISM_META.V)
                .values("schema_ver", "400")
                .execute();
        }

        // Create the players table
        create.createTableIfNotExists(PRISM_PLAYERS)
            .column(PRISM_PLAYERS.PLAYER_ID)
            .column(PRISM_PLAYERS.PLAYER)
            .column(PRISM_PLAYERS.PLAYER_UUID)
            .primaryKey(PRISM_PLAYERS.PLAYER_ID)
            .unique(PRISM_PLAYERS.PLAYER_UUID)
            .execute();

        // Create the causes table
        create.createTableIfNotExists(PRISM_CAUSES)
            .column(PRISM_CAUSES.CAUSE_ID)
            .column(PRISM_CAUSES.CAUSE)
            .column(PRISM_CAUSES.PLAYER_ID)
            .primaryKey(PRISM_CAUSES.CAUSE_ID)
            .unique(PRISM_CAUSES.CAUSE)
            .constraints(
                constraint("playerId").foreignKey(PRISM_CAUSES.PLAYER_ID)
                    .references(PRISM_PLAYERS, PRISM_PLAYERS.PLAYER_ID).onDeleteCascade()
            )
            .execute();

        // Create the entity types table
        create.createTableIfNotExists(PRISM_ENTITY_TYPES)
            .column(PRISM_ENTITY_TYPES.ENTITY_TYPE_ID)
            .column(PRISM_ENTITY_TYPES.ENTITY_TYPE)
            .primaryKey(PRISM_ENTITY_TYPES.ENTITY_TYPE_ID)
            .unique(PRISM_ENTITY_TYPES.ENTITY_TYPE)
            .execute();

        // Create actions table
        create.createTableIfNotExists(PRISM_ACTIONS)
            .column(PRISM_ACTIONS.ACTION_ID)
            .column(PRISM_ACTIONS.ACTION)
            .primaryKey(PRISM_ACTIONS.ACTION_ID)
            .unique(PRISM_ACTIONS.ACTION)
            .execute();

        // Create the material data table
        create.createTableIfNotExists(PRISM_MATERIALS)
            .column(PRISM_MATERIALS.MATERIAL_ID)
            .column(PRISM_MATERIALS.MATERIAL)
            .column(PRISM_MATERIALS.DATA)
            .primaryKey(PRISM_MATERIALS.MATERIAL_ID)
            .unique(PRISM_MATERIALS.MATERIAL, PRISM_MATERIALS.DATA)
            .execute();

        // Create the worlds table
        create.createTableIfNotExists(PRISM_WORLDS)
            .column(PRISM_WORLDS.WORLD_ID)
            .column(PRISM_WORLDS.WORLD)
            .column(PRISM_WORLDS.WORLD_UUID)
            .primaryKey(PRISM_WORLDS.WORLD_ID)
            .unique(PRISM_WORLDS.WORLD_UUID)
            .execute();

        // Create the activities table. This one's the fatso.
        create.createTableIfNotExists(PRISM_ACTIVITIES)
            .column(PRISM_ACTIVITIES.ACTIVITY_ID)
            .column(PRISM_ACTIVITIES.TIMESTAMP)
            .column(PRISM_ACTIVITIES.WORLD_ID)
            .column(PRISM_ACTIVITIES.X)
            .column(PRISM_ACTIVITIES.Y)
            .column(PRISM_ACTIVITIES.Z)
            .column(PRISM_ACTIVITIES.ACTION_ID)
            .column(PRISM_ACTIVITIES.MATERIAL_ID)
            .column(PRISM_ACTIVITIES.OLD_MATERIAL_ID)
            .column(PRISM_ACTIVITIES.ENTITY_TYPE_ID)
            .column(PRISM_ACTIVITIES.CAUSE_ID)
            .column(PRISM_ACTIVITIES.DESCRIPTOR)
            .column(PRISM_ACTIVITIES.METADATA)
            .column(PRISM_ACTIVITIES.SERIALIZER_VERSION)
            .column(PRISM_ACTIVITIES.SERIALIZED_DATA)
            .column(PRISM_ACTIVITIES.REVERSED)
            .primaryKey(PRISM_ACTIVITIES.ACTIVITY_ID)
            .constraints(
                constraint("actionId").foreignKey(PRISM_ACTIVITIES.ACTION_ID)
                    .references(PRISM_ACTIONS, PRISM_ACTIONS.ACTION_ID).onDeleteCascade(),
                constraint("causeId").foreignKey(PRISM_ACTIVITIES.CAUSE_ID)
                    .references(PRISM_CAUSES, PRISM_CAUSES.CAUSE_ID).onDeleteCascade(),
                constraint("entityTypeId").foreignKey(PRISM_ACTIVITIES.ENTITY_TYPE_ID)
                    .references(PRISM_ENTITY_TYPES, PRISM_ENTITY_TYPES.ENTITY_TYPE_ID).onDeleteCascade(),
                constraint("materialId").foreignKey(PRISM_ACTIVITIES.MATERIAL_ID)
                    .references(PRISM_MATERIALS, PRISM_MATERIALS.MATERIAL_ID).onDeleteCascade(),
                constraint("oldMaterialId").foreignKey(PRISM_ACTIVITIES.OLD_MATERIAL_ID)
                    .references(PRISM_MATERIALS, PRISM_MATERIALS.MATERIAL_ID).onDeleteCascade(),
                constraint("worldId").foreignKey(PRISM_ACTIVITIES.WORLD_ID)
                    .references(PRISM_WORLDS, PRISM_WORLDS.WORLD_ID).onDeleteCascade()
            )
            .execute();

        // Jooq doesn't support creating indexes inline with create table.
        List<Index> indexes = create.meta(PRISM_DATABASE).getIndexes();
        if (!indexes.contains(Indexes.PRISM_ACTIVITIES_COORDINATE)) {
            create.createIndex("coordinate")
                .on(PRISM_ACTIVITIES, PRISM_ACTIVITIES.X, PRISM_ACTIVITIES.Y, PRISM_ACTIVITIES.Z).execute();
        }
    }

    /**
     * Caching often-used object->primary-key lookups greatly reduce the number of queries/network requests.
     *
     * <p>Note: Player UUIDs are cached as needed and removed on disconnect.</p>
     */
    protected void prepareCache() {
        // Actions
        List<PrismActionsRecord> actions = create
            .select(PRISM_ACTIONS.ACTION, PRISM_ACTIONS.ACTION_ID)
            .from(PRISM_ACTIONS)
            .fetchInto(PrismActionsRecord.class);

        for (PrismActionsRecord actionsRecord : actions) {
            byte actionId = actionsRecord.getActionId().byteValue();
            cacheService.actionKeyPkMap().put(actionsRecord.getAction(), actionId);
        }

        // Entity Types
        List<PrismEntityTypesRecord> entityTypes = create
            .select(PRISM_ENTITY_TYPES.ENTITY_TYPE, PRISM_ENTITY_TYPES.ENTITY_TYPE_ID)
            .from(PRISM_ENTITY_TYPES)
            .fetchInto(PrismEntityTypesRecord.class);

        for (PrismEntityTypesRecord entityTypesRecord : entityTypes) {
            int entityTypeId = entityTypesRecord.getEntityTypeId().intValue();
            cacheService.entityTypePkMap().put(entityTypesRecord.getEntityType(), entityTypeId);
        }

        // Materials (base, no data)
        List<PrismMaterialsRecord> materials = create
            .select(PRISM_MATERIALS.MATERIAL, PRISM_MATERIALS.MATERIAL_ID)
            .from(PRISM_MATERIALS)
            .where(PRISM_MATERIALS.DATA.isNull())
            .fetchInto(PrismMaterialsRecord.class);

        for (PrismMaterialsRecord material : materials) {
            int materialId = material.getMaterialId().intValue();
            cacheService.materialDataPkMap().put(material.getMaterial(), materialId);
        }

        // World
        List<PrismWorldsRecord> worlds = create
            .select(PRISM_WORLDS.WORLD_UUID, PRISM_WORLDS.WORLD_ID)
            .from(PRISM_WORLDS)
            .fetchInto(PrismWorldsRecord.class);

        for (PrismWorldsRecord worldsRecord : worlds) {
            byte worldId = worldsRecord.getWorldId().byteValue();
            UUID worldUuid = UUID.fromString(worldsRecord.getWorldUuid());
            cacheService.worldUuidPkMap().put(worldUuid, worldId);
        }
    }

    /**
     * Update the schema as needed.
     *
     * @throws SQLException The database exception
     */
    protected void updateSchemas(String schemaVersion) throws Exception {}

    @Override
    public List<Activity> queryActivities(ActivityQuery query) throws Exception {
        var results = activityMapper(queryBuilder.queryActivities(query), query);

        List<Activity> activities = new ArrayList<>();
        for (var result : results) {
            if (result instanceof Activity activity) {
                activities.add(activity);
            }
        }

        return activities;
    }

    @Override
    public PaginatedResults<AbstractActivity> queryActivitiesPaginated(ActivityQuery query) throws Exception {
        Result<org.jooq.Record> result = queryBuilder.queryActivities(query);

        int totalResults = result.size();
        if (!result.isEmpty()) {
            totalResults = result.get(0).getValue("totalrows", Integer.class);
        }

        int currentPage = (query.offset() / query.limit()) + 1;

        return new PaginatedResults<>(activityMapper(result, query), query.limit(), totalResults, currentPage);
    }

    /**
     * Maps activity data to an action and activity record.
     *
     * @param result The result
     * @param query The original query
     * @return The activity list
     */
    protected List<AbstractActivity> activityMapper(Result<org.jooq.Record> result, ActivityQuery query) {
        List<AbstractActivity> activities = new ArrayList<>();

        for (org.jooq.Record r : result) {
            String actionKey = r.getValue(PRISM_ACTIONS.ACTION);
            var optionalActionType = actionRegistry.actionType(actionKey);
            if (optionalActionType.isEmpty()) {
                loggingService.warn("Failed to find action type: {0}", actionKey);
                continue;
            }

            var actionType = optionalActionType.get();

            // World
            UUID worldUuid = UUID.fromString(r.getValue(PRISM_WORLDS.WORLD_UUID));
            var world = new Pair<>(worldUuid, r.getValue(PRISM_WORLDS.WORLD));

            // Location
            int x = 0;
            int y = 0;
            int z = 0;
            if (query.grouped()) {
                x = r.getValue(avg(PRISM_ACTIVITIES.X)).intValue();
                y = r.getValue(avg(PRISM_ACTIVITIES.Y)).intValue();
                z = r.getValue(avg(PRISM_ACTIVITIES.Z)).intValue();
            } else {
                x = r.getValue(PRISM_ACTIVITIES.X);
                y = r.getValue(PRISM_ACTIVITIES.Y);
                z = r.getValue(PRISM_ACTIVITIES.Z);
            }

            var coordinate = new Coordinate(x, y, z);

            // Entity type
            String entityType = null;
            String entityTypeName = r.getValue(PRISM_ENTITY_TYPES.ENTITY_TYPE);
            if (entityTypeName != null) {
                entityType = entityTypeName.toUpperCase(Locale.ENGLISH);
            }

            // Material
            String material = null;
            String materialName = r.getValue(PRISM_MATERIALS.MATERIAL);
            if (materialName != null) {
                material = materialName.toUpperCase(Locale.ENGLISH);
            }

            // Cause
            String cause = r.getValue(PRISM_CAUSES.CAUSE);
            Pair<UUID, String> player = null;

            // Player
            if (r.getValue(PRISM_PLAYERS.PLAYER_UUID) != null) {
                String playerName = r.getValue(PRISM_PLAYERS.PLAYER);
                UUID playerUuid = UUID.fromString(r.getValue(PRISM_PLAYERS.PLAYER_UUID));

                player = new Pair<>(playerUuid, playerName);
            }

            String descriptor = query.lookup() ? r.getValue(PRISM_ACTIVITIES.DESCRIPTOR) : null;
            String metadata = query.lookup() ? r.getValue(PRISM_ACTIVITIES.METADATA) : null;

            long timestamp;
            if (query.grouped()) {
                timestamp = r.getValue(avg(PRISM_ACTIVITIES.TIMESTAMP)).longValue();
            } else {
                timestamp = r.getValue(PRISM_ACTIVITIES.TIMESTAMP).longValue();
            }

            if (!query.grouped() && query.modification()) {
                long activityId = r.getValue(PRISM_ACTIVITIES.ACTIVITY_ID).longValue();

                String materialData = r.getValue(PRISM_MATERIALS.DATA);
                String customData = r.getValue(PRISM_ACTIVITIES.SERIALIZED_DATA);
                UShort customDataVersion = r.getValue(PRISM_ACTIVITIES.SERIALIZER_VERSION);

                // Material/serialization data
                String replacedMaterial = null;
                String replacedMaterialName = r.getValue(OLD_MATERIALS.MATERIAL);
                if (replacedMaterialName != null) {
                    replacedMaterial = replacedMaterialName.toUpperCase(Locale.ENGLISH);
                }

                String replacedMaterialData = r.getValue(OLD_MATERIALS.DATA);

                // Build the action data
                ActionData actionData = new ActionData(
                    material, materialData, replacedMaterial, replacedMaterialData,
                    entityType, customData, descriptor, metadata, customDataVersion.shortValue());

                // Build the activity
                try {
                    var activity = new Activity(activityId, actionType.createAction(actionData),
                        world, coordinate, cause, player, timestamp);

                    // Add to result list
                    activities.add(activity);
                } catch (Exception e) {
                    loggingService.handleException(e);
                }
            } else if (!query.grouped()) {
                long activityId = r.getValue(PRISM_ACTIVITIES.ACTIVITY_ID).longValue();

                // Build the action data
                ActionData actionData = new ActionData(
                    material, null, null, null,
                    entityType, null, descriptor, metadata, (short) 0);

                // Build the activity
                try {
                    var activity = new Activity(activityId, actionType.createAction(actionData),
                        world, coordinate, cause, player, timestamp);

                    // Add to result list
                    activities.add(activity);
                } catch (Exception e) {
                    loggingService.handleException(e);
                }
            } else {
                // Build the action data
                ActionData actionData = new ActionData(
                    material, null, null, null,
                    entityType, null, descriptor, metadata, (short) 0);

                // Count
                int count = r.getValue("groupcount", Integer.class);

                // Build the grouped activity
                try {
                    var activity = new GroupedActivity(
                        actionType.createAction(actionData), world, coordinate, cause, player, timestamp, count);

                    // Add to result list
                    activities.add(activity);
                } catch (Exception e) {
                    loggingService.handleException(e);
                }
            }
        }

        return activities;
    }

    @Override
    public ActivityBatch createActivityBatch() {
        return new SqlActivityBatch(loggingService, create, serializerVersion, cacheService);
    }

    @Override
    public int deleteActivities(ActivityQuery query, int cycleMinPrimaryKey, int cycleMaxPrimaryKey) {
        return queryBuilder.deleteActivities(query, cycleMinPrimaryKey, cycleMaxPrimaryKey);
    }

    @Override
    public Pair<Integer, Integer> getActivitiesPkBounds() {
        Record2<UInteger, UInteger> result = create
            .select(coalesce(min(PRISM_ACTIVITIES.ACTIVITY_ID), DSL.val(0)),
                    coalesce(max(PRISM_ACTIVITIES.ACTIVITY_ID), DSL.val(0)))
            .from(PRISM_ACTIVITIES).fetchOne();

        int minPk = result != null ? result.value1().intValue() : 0;
        int maxPk = result != null ? result.value2().intValue() : 0;

        return new Pair<>(minPk, maxPk);
    }

    @Override
    public void markReversed(List<Long> activityIds, boolean reversed) throws Exception {
        if (activityIds.isEmpty()) {
            return;
        }

        create.update(PRISM_ACTIVITIES)
            .set(PRISM_ACTIVITIES.REVERSED, reversed)
            .where(PRISM_ACTIVITIES.ACTIVITY_ID.in(activityIds))
            .execute();
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public boolean ready() {
        return ready;
    }

    /**
     * Loads sql files from the resource folder and replaces the prefix placeholder.
     *
     * @param storageType The storage type
     * @param sqlFileName The file for the needed sql
     * @param prefix The schema/table prefix
     * @return Sql statement string
     * @throws IOException File exception
     */
    protected String loadSqlFromResourceFile(String storageType, String sqlFileName, String prefix) throws IOException {
        var sql = loadResourceFileAsString(String.format("sql/%s/%s.sql", storageType, sqlFileName));
        return sql.replaceAll("%prefix%", prefix);
    }

    /**
     * Loads a file from the resource path.
     *
     * @param filePath The file path
     * @return The file contents as a string
     * @throws IOException File exception
     */
    private String loadResourceFileAsString(String filePath) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(filePath);

        if (inputStream == null) {
            throw new IOException("Could not find resource file: " + filePath);
        }

        StringBuilder stringBuilder = new StringBuilder();
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine()).append("\n");
            }
        }
        return stringBuilder.toString();
    }
}
