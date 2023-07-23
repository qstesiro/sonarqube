/*
 * SonarQube
 * Copyright (C) 2009-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.ce.configuration;

import org.sonar.api.config.Configuration;

import static org.sonar.process.ProcessProperties.Property.CE_WORKER_COUNT;
import static org.sonar.process.ProcessProperties.Property.CE_CORE_MULTIPLE;

/**
 * When an implementation of this interface is available in Pico, the Compute Engine will use the value returned by
 * {@link #get()} as the number of worker the Compute Engine should run on.
 */
public class WorkerCountProviderImpl implements WorkerCountProvider {

    private final Configuration config;

    public WorkerCountProviderImpl() {
        config = null;
    }

    public WorkerCountProviderImpl(Configuration config) {
        this.config = config;
    }

    private static final String WORKER_COUNT = "SONAR_WORKER_COUNT";

    @Override
    public int get() {
        if (config != null) {
            int value = config.getInt(CE_WORKER_COUNT.getKey()).orElse(0);
            if (value == 0) {
                value = getWorkers();
            }
            return value;
        } else {
            try {
                return Integer.parseInt(System.getenv(WORKER_COUNT));
            } catch(NumberFormatException e) {
                return getWorkers();
            }
        }
    }

    private static final String CORE_MULTIPLE = "SONAR_CORE_MULTIPLE";

    private static final int CORE_MULTIPLE_DEF = 1;
    private static final int CORE_MULTIPLE_MAX = 3;

    private int getWorkers() {
        if (config != null) {
            int value = config.getInt(CE_CORE_MULTIPLE.getKey()).orElse(0);
            if (value < CORE_MULTIPLE_DEF || value > CORE_MULTIPLE_MAX) {
                value = CORE_MULTIPLE_DEF;
            }
            return Runtime.getRuntime().availableProcessors() * value;
        } else {
            int value = CORE_MULTIPLE_DEF;
            try {
                value = Integer.parseInt(System.getenv(CORE_MULTIPLE));
                if (value < CORE_MULTIPLE_DEF || value > CORE_MULTIPLE_MAX) {
                    value = CORE_MULTIPLE_DEF;
                }
                return Runtime.getRuntime().availableProcessors() * value;
            } catch(NumberFormatException e) {
                value = CORE_MULTIPLE_DEF;
            }
            return Runtime.getRuntime().availableProcessors() * value;
        }
    }
}
