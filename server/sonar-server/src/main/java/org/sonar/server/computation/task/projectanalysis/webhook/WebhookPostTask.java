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

import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonar.api.ce.posttask.PostProjectAnalysisTask;
import org.sonar.api.config.Configuration;
import org.sonar.server.computation.task.projectanalysis.component.ConfigurationRepository;
import org.sonar.server.webhook.Branch;
import org.sonar.server.webhook.CeTask;
import org.sonar.server.webhook.Project;
import org.sonar.server.webhook.QualityGate;
import org.sonar.server.webhook.WebHooks;
import org.sonar.server.webhook.WebhookPayloadFactory;

public class WebhookPostTask implements PostProjectAnalysisTask {

  private final ConfigurationRepository configRepository;
  private final WebhookPayloadFactory payloadFactory;
  private final WebHooks webHooks;

  public WebhookPostTask(ConfigurationRepository configRepository, WebhookPayloadFactory payloadFactory, WebHooks webHooks) {
    this.configRepository = configRepository;
    this.payloadFactory = payloadFactory;
    this.webHooks = webHooks;
  }

  @Override
  public void finished(ProjectAnalysis analysis) {
    Configuration config = configRepository.getConfiguration();

    webHooks.sendProjectAnalysisUpdate(
      config,
      new WebHooks.Analysis(analysis.getProject().getUuid(), analysis.getCeTask().getId()),
      () -> payloadFactory.create(convert(analysis)));
  }

  private static org.sonar.server.webhook.ProjectAnalysis convert(ProjectAnalysis analysis) {
    return new org.sonar.server.webhook.ProjectAnalysis(
      new CeTask(analysis.getCeTask().getId(), CeTask.Status.valueOf(analysis.getCeTask().getStatus().name())),
      new Project(analysis.getProject().getUuid(), analysis.getProject().getKey(), analysis.getProject().getName()),
      analysis.getBranch().map(b -> new Branch(b.isMain(), b.getName().orElse(null), Branch.Type.valueOf(b.getType().name()))).orElse(null),
      Optional.ofNullable(analysis.getQualityGate())
        .map(qg -> new QualityGate(
          qg.getId(),
          qg.getName(),
          QualityGate.Status.valueOf(qg.getStatus().name()),
          qg.getConditions().stream()
            .map(c -> new QualityGate.Condition(QualityGate.EvaluationStatus.valueOf(c.getStatus().name()), c.getMetricKey(), QualityGate.Operator.valueOf(c.getOperator().name()),
              c.getErrorThreshold(), c.getWarningThreshold(), c.isOnLeakPeriod(),
              c.getStatus() == org.sonar.api.ce.posttask.QualityGate.EvaluationStatus.NO_VALUE ? null : c.getValue()))
            .collect(Collectors.toSet())))
        .orElse(null),
      analysis.getAnalysisDate().map(Date::getTime).orElse(null),
      analysis.getScannerContext().getProperties());
  }

}
