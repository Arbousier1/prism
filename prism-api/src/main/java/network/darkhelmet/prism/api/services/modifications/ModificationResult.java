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

package network.darkhelmet.prism.api.services.modifications;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import network.darkhelmet.prism.api.activities.IActivity;

@Builder
@Getter
public final class ModificationResult {
    /**
     * The original activity.
     */
    @NonNull private final IActivity activity;

    /**
     * The state changes.
     */
    private final StateChange<?> stateChange;

    /**
     * The modification result status.
     */
    @NonNull
    @Builder.Default
    private final ModificationResultStatus status = ModificationResultStatus.SKIPPED;

    public static class ModificationResultBuilder {
        /**
         * Set the status to APPLIED.
         *
         * @return The builder
         */
        public ModificationResultBuilder applied() {
            status(ModificationResultStatus.APPLIED);
            return this;
        }

        /**
         * Set the status to PLANNED.
         *
         * @return The builder
         */
        public ModificationResultBuilder planned() {
            status(ModificationResultStatus.PLANNED);
            return this;
        }

        /**
         * Set the status to SKIPPED.
         *
         * @return The builder
         */
        public ModificationResultBuilder skipped() {
            status(ModificationResultStatus.SKIPPED);
            return this;
        }
    }
}
