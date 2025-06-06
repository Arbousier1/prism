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

package network.darkhelmet.prism.bukkit.actions.types;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;

import network.darkhelmet.prism.api.actions.Action;
import network.darkhelmet.prism.api.actions.ActionData;
import network.darkhelmet.prism.api.actions.types.ActionResultType;
import network.darkhelmet.prism.api.actions.types.ActionType;
import network.darkhelmet.prism.bukkit.actions.BukkitEntityAction;

import org.bukkit.entity.EntityType;

import static network.darkhelmet.prism.bukkit.actions.BukkitAction.ObjectMapper;

public class EntityActionType extends ActionType {
    /**
     * Construct a new entity action type.
     *
     * @param key The key
     * @param resultType The result type
     * @param reversible If action is reversible
     */
    public EntityActionType(
            String key, ActionResultType resultType, boolean reversible) {
        super(key, resultType, reversible);
    }

    /**
     * Construct a new entity action type.
     *
     * @param key The key
     * @param resultType The result type
     * @param reversible If action is reversible
     * @param metadataClass The metadata class
     */
    public EntityActionType(
            String key, ActionResultType resultType, boolean reversible, Class<? extends Record> metadataClass) {
        super(key, resultType, reversible, metadataClass);
    }

    @Override
    public Action createAction(ActionData actionData) {
        ReadWriteNBT readWriteNbt = null;
        if (actionData.customData() != null && actionData.customDataVersion() > 0) {
            readWriteNbt = NBT.parseNBT(actionData.customData());
        }

        EntityType type = EntityType.valueOf(actionData.entityType());

        Record metadata = null;
        if (actionData.metadata() != null) {
            try {
                metadata = ObjectMapper.readValue(actionData.metadata(), metadataClass());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        return new BukkitEntityAction(this, type, readWriteNbt, actionData.descriptor(), metadata);
    }
}
