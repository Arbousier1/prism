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

package network.darkhelmet.prism.providers;

import com.google.inject.Guice;
import com.google.inject.Injector;

import lombok.Getter;

import network.darkhelmet.prism.PrismBukkit;
import network.darkhelmet.prism.injection.PrismModule;
import network.darkhelmet.prism.loader.services.logging.LoggingService;

public class InjectorProvider {
    /**
     * The injector.
     */
    @Getter
    private Injector injector;

    public InjectorProvider(PrismBukkit prism, LoggingService loggingService) {
        this.injector = Guice.createInjector(new PrismModule(prism, loggingService));
    }
}
