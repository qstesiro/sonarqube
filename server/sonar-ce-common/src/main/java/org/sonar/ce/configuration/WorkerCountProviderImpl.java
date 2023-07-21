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

/**
 * When an implementation of this interface is available in Pico, the Compute Engine will use the value returned by
 * {@link #get()} as the number of worker the Compute Engine should run on.
 */
public class WorkerCountProviderImpl implements WorkerCountProvider {

    private static final String WORKER_COUNT = "SONAR_WORKER_COUNT";

    @Override
    public int get() {
        try {
            return Integer.parseInt(System.getenv(WORKER_COUNT));
        } catch(NumberFormatException e) {
            return getWorkers();
        }
    }

    private static final String CORE_MUL = "SONAR_CORE_MUL";
    private static final int CORE_MUL_DEF = 1;
    private static final int CORE_MUL_MAX = 3;

    private int getWorkers() {
        int mul = CORE_MUL_DEF;
        try {
            mul = Integer.parseInt(System.getenv(CORE_MUL));
            if (mul < CORE_MUL_DEF || mul > CORE_MUL_MAX) {
                mul = CORE_MUL_DEF;
            }
        } catch(NumberFormatException e) {
        }
        return Runtime.getRuntime().availableProcessors() * mul;
    }
}
