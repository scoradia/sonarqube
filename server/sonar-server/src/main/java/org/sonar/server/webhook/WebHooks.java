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
package org.sonar.server.webhook;

import com.google.common.base.Objects;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.config.Configuration;

import static java.util.Objects.requireNonNull;

public interface WebHooks {
  void sendProjectAnalysisUpdate(Configuration configuration, Analysis analysis, Supplier<WebhookPayload> payloadSupplier);

  final class Analysis {
    private final String projectUuid;
    private final String ceTaskUuid;
    private final String analysisUuid;

    public Analysis(String projectUuid, String analysisUuid, @Nullable  String ceTaskUuid) {
      this.projectUuid = requireNonNull(projectUuid, "projectUuid can't be null");
      this.analysisUuid = requireNonNull(analysisUuid, "analysisUuid can't be null");
      this.ceTaskUuid = ceTaskUuid;
    }

    public String getProjectUuid() {
      return projectUuid;
    }

    @CheckForNull
    public String getCeTaskUuid() {
      return ceTaskUuid;
    }

    public String getAnalysisUuid() {
      return analysisUuid;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Analysis)) {
        return false;
      }
      Analysis analysis = (Analysis) o;
      return Objects.equal(projectUuid, analysis.projectUuid) &&
        Objects.equal(ceTaskUuid, analysis.ceTaskUuid) &&
        Objects.equal(analysisUuid, analysis.analysisUuid);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(projectUuid, ceTaskUuid, analysisUuid);
    }
  }
}
