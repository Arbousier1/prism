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

package network.darkhelmet.prism.loader.services.configuration.storage;

import lombok.Getter;

import network.darkhelmet.prism.loader.storage.StorageType;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
@Getter
public class StorageConfiguration {
    @Comment("""
            Enable query spy. This logs queries and helpful debug information.
            Used primarily for development and debugging. Use carefully.
            """)
    private boolean spy = false;

    @Comment("""
            Set which storage system to use.
            Available options: H2, MARIADB, MYSQL, POSTGRES
            NOTE: Only one storage system may be used at a time.
            Transferring data from one to another is not yet supported.
            """)
    private StorageType primaryStorageType = StorageType.H2;

    @Comment("""
            Settings for H2 file-based databases. While supported, we do not recommend H2.
            H2 databases will be stored in the prism configuration directory.
            """)
    private DataSourceConfiguration h2 = new DataSourceConfiguration();

    @Comment("Settings for MariaDB")
    private MariaDbDataSourceConfiguration mariadb = new MariaDbDataSourceConfiguration();

    @Comment("Settings for MySQL")
    private MysqlDataSourceConfiguration mysql = new MysqlDataSourceConfiguration();

    @Comment("Settings for Postgres")
    private SqlDataSourceConfiguration postgres = new PostgresDataSourceConfiguration();

    /**
     * Get the primary data source.
     *
     * @return The primary data source
     */
    public DataSourceConfiguration primaryDataSource() {
        return switch (primaryStorageType) {
            case H2 -> h2;
            case MARIADB -> mariadb;
            case MYSQL -> mysql;
            case POSTGRES -> postgres;
        };
    }
}
