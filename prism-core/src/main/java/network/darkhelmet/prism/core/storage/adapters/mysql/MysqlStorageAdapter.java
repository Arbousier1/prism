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

package network.darkhelmet.prism.core.storage.adapters.mysql;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.zaxxer.hikari.HikariConfig;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import network.darkhelmet.prism.api.actions.types.ActionTypeRegistry;
import network.darkhelmet.prism.api.storage.ActivityBatch;
import network.darkhelmet.prism.core.injection.factories.SqlActivityQueryBuilderFactory;
import network.darkhelmet.prism.core.services.cache.CacheService;
import network.darkhelmet.prism.core.storage.HikariConfigFactory;
import network.darkhelmet.prism.core.storage.adapters.sql.AbstractSqlStorageAdapter;
import network.darkhelmet.prism.core.storage.adapters.sql.SqlActivityProcedureBatch;
import network.darkhelmet.prism.core.storage.adapters.sql.SqlSchemaUpdater;
import network.darkhelmet.prism.loader.services.configuration.ConfigurationService;
import network.darkhelmet.prism.loader.services.logging.LoggingService;

import org.jooq.SQLDialect;

@Singleton
public class MysqlStorageAdapter extends AbstractSqlStorageAdapter {
    /**
     * Constructor.
     *
     * @param loggingService The logging service
     * @param configurationService The configuration service
     * @param actionRegistry The action type registry
     * @param schemaUpdater The schema updater
     * @param cacheService The cache service
     * @param queryBuilderFactory The query builder factory
     * @param serializerVersion The serializer version
     * @param dataPath The plugin file path
     */
    @Inject
    public MysqlStorageAdapter(
            LoggingService loggingService,
            ConfigurationService configurationService,
            ActionTypeRegistry actionRegistry,
            SqlSchemaUpdater schemaUpdater,
            SqlActivityQueryBuilderFactory queryBuilderFactory,
            CacheService cacheService,
            @Named("serializerVersion") short serializerVersion,
            Path dataPath) {
        super(
            loggingService,
            configurationService,
            actionRegistry,
            schemaUpdater,
            queryBuilderFactory,
            cacheService,
            serializerVersion);

        try {
            // First, try to use any hikari.properties
            File hikariPropertiesFile = new File(dataPath.toFile(), "hikari.properties");
            if (hikariPropertiesFile.exists()) {
                loggingService.info("Using hikari.properties over storage.conf");

                if (connect(new HikariConfig(hikariPropertiesFile.getPath()), SQLDialect.MYSQL)) {
                    describeDatabase(true);
                    prepareSchema();

                    if (!configurationService.storageConfig().mysql().useStoredProcedures()) {
                        prepareCache();
                    }

                    ready = true;
                }
            } else {
                loggingService.info("Reading storage.conf. There is no hikari.properties file.");

                if (connect(HikariConfigFactory.mysql(configurationService.storageConfig()), SQLDialect.MYSQL)) {
                    describeDatabase(false);
                    prepareSchema();

                    if (!configurationService.storageConfig().mysql().useStoredProcedures()) {
                        prepareCache();
                    }

                    ready = true;
                }
            }
        } catch (Exception e) {
            loggingService.handleException(e);
        }
    }

    @Override
    protected void describeDatabase(boolean usingHikariProperties) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();

            String databaseProduct = databaseMetaData.getDatabaseProductName();
            String databaseVersion = databaseMetaData.getDatabaseProductVersion();

            loggingService.info("Database: {0} {1}", databaseProduct, databaseVersion);

            int majorVersion = databaseMetaData.getDatabaseMajorVersion();
            int minorVersion = databaseMetaData.getDatabaseMinorVersion();

            if (majorVersion < 8 || (majorVersion == 8 && minorVersion < 20)) {
                loggingService.warn("Your database version appears to be older than prism supports.");
            }

            var usingStoredProcedures = false;
            if (configurationService.storageConfig().mysql().useStoredProcedures()) {
                boolean supportsProcedures = databaseMetaData.supportsStoredProcedures();
                loggingService.info("supports procedures: {0}", supportsProcedures);

                List<String> grants = create.fetch("SHOW GRANTS FOR CURRENT_USER();").into(String.class);
                boolean canCreateRoutines = grants.get(0).contains("CREATE ROUTINE")
                    || grants.get(0).contains("GRANT ALL PRIVILEGES ON *.*")
                    || grants.get(0).contains("GRANT ALL PRIVILEGES ON "
                        + configurationService.storageConfig().mysql().database());
                loggingService.info("can create routines: {0}", canCreateRoutines);

                usingStoredProcedures = supportsProcedures && canCreateRoutines
                    && configurationService.storageConfig().mysql().useStoredProcedures();

                if (!usingStoredProcedures) {
                    configurationService.storageConfig().mysql().disallowStoredProcedures();
                }
            }

            loggingService.info("using stored procedures: {0}", usingStoredProcedures);

            Map<String, String> dbVars = create.fetch("SHOW VARIABLES").intoMap(
                r -> r.get(0, String.class),
                r -> r.get(1, String.class)
            );

            long innodbSizeMb = Long.parseLong(dbVars.get("innodb_buffer_pool_size")) / 1024 / 1024;
            loggingService.info("innodb_buffer_pool_size: {0}", innodbSizeMb);
            if (innodbSizeMb < 1024) {
                loggingService.info("We recommend setting a higher innodb_buffer_pool_size.");
                loggingService.info(
                    "See: https://prism.readthedocs.io/en/latest/purges.html#purges-and-databases");
            }

            loggingService.info("sql_mode: {0}", dbVars.get("sql_mode"));

            if (!usingHikariProperties) {
                boolean usrHikariOptimizations = configurationService.storageConfig().mysql().useHikariOptimizations();
                loggingService.info("use hikari optimizations: {0}", usrHikariOptimizations);
            }
        }
    }

    @Override
    protected void prepareSchema() throws Exception {
        super.prepareSchema();

        if (configurationService.storageConfig().mysql().useStoredProcedures()) {
            try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
                // Drop procedures first because MySQL doesn't support OR REPLACE in CREATE PROCEDURE
                stmt.execute(String.format("DROP PROCEDURE IF EXISTS %screate_activity", prefix));
                stmt.execute(String.format("DROP PROCEDURE IF EXISTS %sget_or_create_action", prefix));
                stmt.execute(String.format("DROP PROCEDURE IF EXISTS %sget_or_create_cause", prefix));
                stmt.execute(String.format("DROP PROCEDURE IF EXISTS %sget_or_create_entity_type", prefix));
                stmt.execute(String.format("DROP PROCEDURE IF EXISTS %sget_or_create_material", prefix));
                stmt.execute(String.format("DROP PROCEDURE IF EXISTS %sget_or_create_player", prefix));
                stmt.execute(String.format("DROP PROCEDURE IF EXISTS %sget_or_create_world", prefix));

                // Create all procedures
                stmt.execute(loadSqlFromResourceFile("mysql", "prism_create_activity", prefix));
                stmt.execute(loadSqlFromResourceFile("mysql", "prism_get_or_create_action", prefix));
                stmt.execute(loadSqlFromResourceFile("mysql", "prism_get_or_create_cause", prefix));
                stmt.execute(loadSqlFromResourceFile("mysql", "prism_get_or_create_entity_type", prefix));
                stmt.execute(loadSqlFromResourceFile("mysql", "prism_get_or_create_material", prefix));
                stmt.execute(loadSqlFromResourceFile("mysql", "prism_get_or_create_player", prefix));
                stmt.execute(loadSqlFromResourceFile("mysql", "prism_get_or_create_world", prefix));
            }
        }
    }

    @Override
    public ActivityBatch createActivityBatch() {
        if (configurationService.storageConfig().mysql().useStoredProcedures()) {
            return new SqlActivityProcedureBatch(loggingService, dataSource, serializerVersion, prefix);
        }

        return super.createActivityBatch();
    }
}
