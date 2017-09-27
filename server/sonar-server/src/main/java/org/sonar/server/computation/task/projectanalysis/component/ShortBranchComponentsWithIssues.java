/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonar.server.computation.task.projectanalysis.component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.KeyWithUuidDto;
import org.sonar.server.computation.task.projectanalysis.analysis.AnalysisMetadataHolder;
import org.sonar.server.computation.task.projectanalysis.analysis.Branch;

/**
 * Cache a map of component key -> uuid in short branches that have issues with status either RESOLVED or CONFIRMED.
 *
 */
public class ShortBranchComponentsWithIssues {
  private final String uuid;
  private final Branch branch;
  private final DbClient dbClient;

  private Map<String, Set<String>> uuidsByKey;

  public ShortBranchComponentsWithIssues(AnalysisMetadataHolder analysisMetadataHolder, DbClient dbClient) {
    this.uuid = analysisMetadataHolder.getUuid();
    this.branch = analysisMetadataHolder.getBranch().get();
    this.dbClient = dbClient;
  }

  private void loadUuidsByKey() {
    if (branch.getType() != BranchType.LONG) {
      uuidsByKey = Collections.emptyMap();
      return;
    }

    uuidsByKey = new HashMap<>();
    try (DbSession dbSession = dbClient.openSession(false)) {
      List<KeyWithUuidDto> components = dbClient.componentDao().selectComponentKeysHavingIssuesToMerge(dbSession, uuid);
      for (KeyWithUuidDto dto : components) {
        uuidsByKey.computeIfAbsent(removeBranchFromKey(dto.key()), s -> new HashSet<>()).add(dto.uuid());
      }
    }
  }

  public Set<String> getUuids(String componentKey) {
    if (uuidsByKey == null) {
      loadUuidsByKey();
    }

    return uuidsByKey.getOrDefault(componentKey, Collections.emptySet());
  }

  private static String removeBranchFromKey(String componentKey) {
    return StringUtils.substringBeforeLast(componentKey, ComponentDto.BRANCH_KEY_SEPARATOR);
  }
}
