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

package network.darkhelmet.prism.actions;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import network.darkhelmet.prism.actions.types.BlockActionType;
import network.darkhelmet.prism.actions.types.EntityActionType;
import network.darkhelmet.prism.actions.types.ItemActionType;
import network.darkhelmet.prism.api.actions.IActionRegistry;
import network.darkhelmet.prism.api.actions.IBlockAction;
import network.darkhelmet.prism.api.actions.IEntityAction;
import network.darkhelmet.prism.api.actions.IItemAction;
import network.darkhelmet.prism.api.actions.types.ActionResultType;
import network.darkhelmet.prism.api.actions.types.ActionType;
import network.darkhelmet.prism.api.actions.types.IActionType;

import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

public class ActionRegistry implements IActionRegistry {
    /**
     * Cache of action types by key.
     */
    private final Map<String, IActionType> actionsTypes = new HashMap<>();

    /**
     * Static cache of Prism action types.
     */
    public static final ActionType BLOCK_BREAK =
        new BlockActionType("block-break", ActionResultType.REMOVES, true);
    public static final ActionType BLOCK_PLACE =
        new BlockActionType("block-place", ActionResultType.CREATES, true);
    public static final ActionType ENTITY_KILL =
        new EntityActionType("entity-kill", ActionResultType.REMOVES, true);
    public static final ActionType HANGING_BREAK =
        new EntityActionType("hanging-break", ActionResultType.REMOVES, true);
    public static final ActionType ITEM_DROP =
        new ItemActionType("item-drop", ActionResultType.REMOVES, true);
    public static final ActionType VEHICLE_ENTER =
        new EntityActionType("vehicle-enter", ActionResultType.NONE, false);
    public static final ActionType VEHICLE_PLACE =
        new EntityActionType("vehicle-place", ActionResultType.CREATES, false);

    /**
     * Construct the action registry.
     */
    public ActionRegistry() {
        // Register Prism actions
        registerAction(BLOCK_BREAK);
        registerAction(BLOCK_PLACE);
        registerAction(ENTITY_KILL);
        registerAction(HANGING_BREAK);
        registerAction(ITEM_DROP);
        registerAction(VEHICLE_ENTER);
        registerAction(VEHICLE_PLACE);
    }

    @Override
    public Collection<IActionType> actionTypes() {
        return actionsTypes.values();
    }

    @Override
    public Collection<IActionType> actionTypesInFamily(String family) {
        return actionsTypes.values().stream().filter(
            a -> a.familyKey().equalsIgnoreCase(family)).collect(Collectors.toList());
    }

    @Override
    public IBlockAction createBlockAction(IActionType type, BlockState blockState) {
        return createBlockAction(type, blockState, null);
    }

    @Override
    public IBlockAction createBlockAction(IActionType type, BlockState blockState, BlockState replaced) {
        if (!(type instanceof BlockActionType)) {
            throw new IllegalArgumentException(
                "Block change actions cannot be made from non-block change action types.");
        }

        return new BlockStateAction(type, blockState, replaced);
    }

    @Override
    public IItemAction createItemStackAction(IActionType type, ItemStack itemStack) {
        if (!(type instanceof ItemActionType)) {
            throw new IllegalArgumentException("Item actions cannot be made from non-item action types.");
        }

        return new ItemStackAction(type, itemStack);
    }

    @Override
    public IEntityAction createEntityAction(IActionType type, Entity entity) {
        if (!(type instanceof EntityActionType)) {
            throw new IllegalArgumentException("Entity actions cannot be made from non-entity action types.");
        }

        return new EntityAction(type, entity);
    }

    @Override
    public void registerAction(IActionType actionType) {
        if (actionsTypes.containsKey(actionType.key())) {
            throw new IllegalArgumentException("Registry already has an action type with that key.");
        }

        actionsTypes.put(actionType.key(), actionType);
    }

    @Override
    public Optional<IActionType> getActionType(String key) {
        if (actionsTypes.containsKey(key)) {
            return Optional.of(actionsTypes.get(key));
        }

        return Optional.empty();
    }
}
