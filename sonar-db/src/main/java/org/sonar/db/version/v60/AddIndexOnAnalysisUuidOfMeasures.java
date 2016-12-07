/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.db.version.v60;

import java.sql.SQLException;
import org.sonar.db.Database;
import org.sonar.db.version.CreateIndexBuilder;
import org.sonar.db.version.DdlChange;

import static org.sonar.db.version.IntegerColumnDef.newIntegerColumnDefBuilder;
import static org.sonar.db.version.VarcharColumnDef.newVarcharColumnDefBuilder;

public class AddIndexOnAnalysisUuidOfMeasures extends DdlChange {

  public AddIndexOnAnalysisUuidOfMeasures(Database db) {
    super(db);
  }

  @Override
  public void execute(Context context) throws SQLException {
    // this index must be present for the performance of next migration
    context.execute(new CreateIndexBuilder(getDialect())
      .setTable("project_measures")
      .setName("measures_analysis_metric")
      .addColumn(newVarcharColumnDefBuilder().setColumnName("analysis_uuid").setLimit(50).build())
      .addColumn(newIntegerColumnDefBuilder().setColumnName("metric_id").build())
      .build());
  }
}
