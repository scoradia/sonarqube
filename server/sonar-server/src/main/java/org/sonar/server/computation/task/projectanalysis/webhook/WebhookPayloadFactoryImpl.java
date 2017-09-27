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

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.Date;
import javax.annotation.Nullable;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.ce.posttask.Branch;
import org.sonar.api.ce.posttask.CeTask;
import org.sonar.api.ce.posttask.PostProjectAnalysisTask;
import org.sonar.api.ce.posttask.Project;
import org.sonar.api.ce.posttask.QualityGate;
import org.sonar.api.ce.posttask.ScannerContext;
import org.sonar.api.platform.Server;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.server.webhook.WebhookPayload;

import static java.lang.String.format;
import static org.sonar.core.config.WebhookProperties.ANALYSIS_PROPERTY_PREFIX;

@ComputeEngineSide
public class WebhookPayloadFactoryImpl implements WebhookPayloadFactory {

  private final Server server;
  private final System2 system2;

  public WebhookPayloadFactoryImpl(Server server, System2 system2) {
    this.server = server;
    this.system2 = system2;
  }

  @Override
  public WebhookPayload create(PostProjectAnalysisTask.ProjectAnalysis analysis) {
    Writer string = new StringWriter();
    try (JsonWriter writer = JsonWriter.of(string)) {
      writer.beginObject();
      writeServer(writer);
      writeTask(writer, analysis.getCeTask());
      writeDates(writer, analysis, system2);
      writeProject(analysis, writer, analysis.getProject());
      analysis.getBranch().ifPresent(b -> writeBranch(writer, analysis.getProject(), b));
      writeQualityGate(writer, analysis.getQualityGate());
      writeAnalysisProperties(writer, analysis.getScannerContext());
      writer.endObject().close();
      return new WebhookPayload(analysis.getProject().getKey(), string.toString());
    }
  }

  private void writeServer(JsonWriter writer) {
    writer.prop("serverUrl", server.getPublicRootUrl());
  }

  private static void writeDates(JsonWriter writer, PostProjectAnalysisTask.ProjectAnalysis analysis, System2 system2) {
    analysis.getAnalysis().ifPresent(a -> writer.propDateTime("analysedAt", a.getDate()));
    if (analysis.getAnalysis().isPresent()) {
      writer.propDateTime("changedAt", analysis.getAnalysis().get().getDate());
    } else {
      writer.propDateTime("changedAt", new Date(system2.now()));
    }

  }

  private void writeProject(PostProjectAnalysisTask.ProjectAnalysis analysis, JsonWriter writer, Project project) {
    writer
      .name("project")
      .beginObject()
      .prop("key", project.getKey())
      .prop("name", analysis.getProject().getName())
      .prop("url", projectUrlOf(project))
      .endObject();
  }

  private static void writeAnalysisProperties(JsonWriter writer, ScannerContext scannerContext) {
    writer
      .name("properties")
      .beginObject();
    scannerContext.getProperties().entrySet()
      .stream()
      .filter(prop -> prop.getKey().startsWith(ANALYSIS_PROPERTY_PREFIX))
      .forEach(prop -> writer.prop(prop.getKey(), prop.getValue()));
    writer.endObject();
  }

  private static void writeTask(JsonWriter writer, CeTask ceTask) {
    writer
      .prop("taskId", ceTask.getId())
      .prop("status", ceTask.getStatus().toString());
  }

  private void writeBranch(JsonWriter writer, Project project, Branch branch) {
    writer
      .name("branch")
      .beginObject()
      .prop("name", branch.getName().orElse(null))
      .prop("type", branch.getType().name())
      .prop("isMain", branch.isMain())
      .prop("url", branchUrlOf(project, branch))
      .endObject();
  }

  private String projectUrlOf(Project project) {
    return format("%s/project/dashboard?id=%s", server.getPublicRootUrl(), encode(project.getKey()));
  }

  private String branchUrlOf(Project project, Branch branch) {
    if (branch.getType() == Branch.Type.LONG) {
      if (branch.isMain()) {
        return projectUrlOf(project);
      }
      return format("%s/project/dashboard?branch=%s&id=%s",
        server.getPublicRootUrl(), encode(branch.getName().orElse("")), encode(project.getKey()));
    } else {
      return format("%s/project/issues?branch=%s&id=%s&resolved=false",
        server.getPublicRootUrl(), encode(branch.getName().orElse("")), encode(project.getKey()));
    }
  }

  private static void writeQualityGate(JsonWriter writer, @Nullable QualityGate gate) {
    if (gate != null) {
      writer
        .name("qualityGate")
        .beginObject()
        .prop("name", gate.getName())
        .prop("status", gate.getStatus().toString())
        .name("conditions")
        .beginArray();
      for (QualityGate.Condition condition : gate.getConditions()) {
        writer
          .beginObject()
          .prop("metric", condition.getMetricKey())
          .prop("operator", condition.getOperator().name());
        if (condition.getStatus() != QualityGate.EvaluationStatus.NO_VALUE) {
          writer.prop("value", condition.getValue());
        }
        writer
          .prop("status", condition.getStatus().name())
          .prop("onLeakPeriod", condition.isOnLeakPeriod())
          .prop("errorThreshold", condition.getErrorThreshold())
          .prop("warningThreshold", condition.getWarningThreshold())
          .endObject();
      }
      writer
        .endArray()
        .endObject();
    }
  }

  private static String encode(String toEncode) {
    try {
      return URLEncoder.encode(toEncode, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Encoding not supported", e);
    }
  }
}
