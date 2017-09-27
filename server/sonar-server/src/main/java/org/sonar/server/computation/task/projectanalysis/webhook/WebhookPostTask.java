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
package org.sonar.server.computation.task.projectanalysis.webhook;

import java.util.Optional;
import org.sonar.api.ce.posttask.PostProjectAnalysisTask;
import org.sonar.api.config.Configuration;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.server.computation.task.projectanalysis.component.ConfigurationRepository;
import org.sonar.server.webhook.WebHooks;

public class WebhookPostTask implements PostProjectAnalysisTask {

  private final ConfigurationRepository configRepository;
  private final WebhookPayloadFactory payloadFactory;
  private final WebHooks webHooks;
  private final DbClient dbClient;

  public WebhookPostTask(ConfigurationRepository configRepository, WebhookPayloadFactory payloadFactory, WebHooks webHooks, DbClient dbClient) {
    this.configRepository = configRepository;
    this.payloadFactory = payloadFactory;
    this.webHooks = webHooks;
    this.dbClient = dbClient;
  }

  @Override
  public void finished(ProjectAnalysis analysis) {
    Configuration config = configRepository.getConfiguration();

    String analysisUuid = null;
    try (DbSession dbSession = dbClient.openSession(false)) {
      Optional<CeActivityDto> ceActivityDto = dbClient.ceActivityDao().selectByUuid(dbSession, analysis.getCeTask().getId());
      if (ceActivityDto.isPresent()) {
        analysisUuid = ceActivityDto.get().getAnalysisUuid();
      }
    }

    webHooks.sendProjectAnalysisUpdate(
      config,
      new WebHooks.Analysis(analysis.getProject().getUuid(), analysisUuid, analysis.getCeTask().getId()),
      () -> payloadFactory.create(analysis));
  }
}
