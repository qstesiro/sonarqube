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
package org.sonar.server.projectanalysis.ws;

public enum ScanType {

    builtin_go("builtin_go"),
    builtin_node("builtin_node"),
    builtin_python("builtin_python"),
    builtin_cpp("builtin_cpp"),
    builtin_mvn("builtin_mvn"),
    builtin_gradle("builtin_gradle"),
    builtin_dotnet("builtin_dotnet"),
    seczone_cs("seczone_cs"),
    seczone_sca("seczone_sca"),
    sql("sql");

    private final String type;

    ScanType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static ScanType fromLabel(String type) {
        for (ScanType e : values()) {
            if (e.getType().equals(type)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown scan type '" + type + "'");
    }
}
