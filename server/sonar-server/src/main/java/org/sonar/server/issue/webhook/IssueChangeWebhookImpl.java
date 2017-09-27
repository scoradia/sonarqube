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
package org.sonar.server.issue.webhook;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.api.config.Configuration;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.rules.RuleType;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.ce.CeActivityDto;
import org.sonar.db.component.BranchDto;
import org.sonar.db.component.BranchType;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.webhook.Branch;
import org.sonar.server.webhook.CeTask;
import org.sonar.server.webhook.Project;
import org.sonar.server.webhook.ProjectAnalysis;
import org.sonar.server.webhook.WebhookPayloadFactory;
import org.sonar.server.issue.ws.SearchResponseData;
import org.sonar.server.webhook.WebHooks;
import org.sonar.server.webhook.WebhookPayload;

import static org.sonar.core.util.stream.MoreCollectors.toSet;
import static org.sonar.core.util.stream.MoreCollectors.uniqueIndex;

public class IssueChangeWebhookImpl implements IssueChangeWebhook {
  private static final Set<String> MEANINGFUL_TRANSITIONS = ImmutableSet.of(
    DefaultTransitions.RESOLVE, DefaultTransitions.FALSE_POSITIVE, DefaultTransitions.WONT_FIX, DefaultTransitions.REOPEN);
  private final DbClient dbClient;
  private final WebHooks webhooks;
  private final Configuration configuration;
  private final WebhookPayloadFactory webhookPayloadFactory;

  public IssueChangeWebhookImpl(DbClient dbClient, WebHooks webhooks, Configuration configuration, WebhookPayloadFactory webhookPayloadFactory) {
    this.dbClient = dbClient;
    this.webhooks = webhooks;
    this.configuration = configuration;
    this.webhookPayloadFactory = webhookPayloadFactory;
  }

  @Override
  public void onTypeChange(SearchResponseData searchResponseData, RuleType ruleType, IssueChangeContext context) {
    if (isEmpty(searchResponseData) || !isUserChangeContext(context)) {
      return;
    }

    callWebHook(searchResponseData);
  }

  @Override
  public void onTransition(SearchResponseData searchResponseData, String transitionKey, IssueChangeContext context) {
    if (isEmpty(searchResponseData) || !isMeaningfulTransition(transitionKey) || !isUserChangeContext(context)) {
      return;
    }

    callWebHook(searchResponseData);
  }

  private static boolean isEmpty(SearchResponseData searchResponseData) {
    return searchResponseData.getIssues().isEmpty();
  }

  private static boolean isUserChangeContext(IssueChangeContext context) {
    return context.login() != null;
  }

  private static boolean isMeaningfulTransition(String transitionKey) {
    return MEANINGFUL_TRANSITIONS.contains(transitionKey);
  }

  private void callWebHook(SearchResponseData searchResponseData) {
    if (!webhooks.isEnabled(configuration)) {
      return;
    }

    Set<String> componentUuids = searchResponseData.getIssues().stream()
      .map(IssueDto::getComponentUuid)
      .collect(toSet());
    try (DbSession dbSession = dbClient.openSession(false)) {
      Map<String, ComponentDto> branchesByUuid = getBranchComponents(dbSession, componentUuids, searchResponseData);
      if (branchesByUuid.isEmpty()) {
        return;
      }

      Set<String> branchProjectUuids = branchesByUuid.values().stream()
        .map(ComponentDto::uuid)
        .collect(toSet(branchesByUuid.size()));
      Set<BranchDto> shortBranches = dbClient.branchDao().selectByUuids(dbSession, branchProjectUuids)
        .stream()
        .filter(branchDto -> branchDto.getBranchType() == BranchType.SHORT)
        .collect(toSet(branchesByUuid.size()));
      if (shortBranches.isEmpty()) {
        return;
      }

      Map<String, SnapshotDto> analysisByProjectUuid = dbClient.snapshotDao().selectLastAnalysesByRootComponentUuids(
        dbSession,
        shortBranches.stream().map(BranchDto::getProjectUuid).collect(toSet(shortBranches.size())))
        .stream()
        .collect(uniqueIndex(SnapshotDto::getComponentUuid));
      Map<String, CeActivityDto> ceActivityDtoByAnalysisUuid = dbClient.ceActivityDao().selectByAnalysisUuids(
        dbSession,
        analysisByProjectUuid.values().stream().map(SnapshotDto::getUuid).collect(toSet(analysisByProjectUuid.size())))
        .stream()
        .collect(uniqueIndex(CeActivityDto::getAnalysisUuid));
      shortBranches
        .forEach(shortBranch -> {
          ComponentDto branch = branchesByUuid.get(shortBranch.getUuid());
          SnapshotDto analysis = analysisByProjectUuid.get(shortBranch.getProjectUuid());
          if (branch != null && analysis != null) {
            CeActivityDto ceActivityDto = ceActivityDtoByAnalysisUuid.get(analysis.getUuid());
            if (ceActivityDto != null) {
              webhooks.sendProjectAnalysisUpdate(
                configuration,
                new WebHooks.Analysis(shortBranch.getProjectUuid(), ceActivityDto.getUuid()),
                () -> buildWebHookPayload(branch, ceActivityDto));
            }
          }
        });
    }
  }

  private WebhookPayload buildWebHookPayload(ComponentDto branch, CeActivityDto ceActivity) {
    ProjectAnalysis projectAnalysis = new ProjectAnalysis(
      new CeTask(ceActivity.getUuid(), CeTask.Status.valueOf(ceActivity.getStatus().name())),
      new Project(branch.uuid(), branch.getDbKey(), branch.name()),
      new Branch(false, branch.name(), Branch.Type.SHORT),
      null,
      null,
      Collections.emptyMap());
    return webhookPayloadFactory.create(projectAnalysis);
  }

  private Map<String, ComponentDto> getBranchComponents(DbSession dbSession, Set<String> componentUuids, SearchResponseData searchResponseData) {
    Set<String> missingComponentUuids = ImmutableSet.copyOf(Sets.difference(
      componentUuids,
      searchResponseData.getComponents()
        .stream()
        .map(ComponentDto::uuid)
        .collect(Collectors.toSet())));
    if (missingComponentUuids.isEmpty()) {
      return searchResponseData.getComponents()
        .stream()
        .collect(uniqueIndex(ComponentDto::uuid));
    }
    return Stream.concat(
      searchResponseData.getComponents().stream(),
      dbClient.componentDao().selectByUuids(dbSession, missingComponentUuids).stream())
      .filter(componentDto -> componentDto.getMainBranchProjectUuid() != null)
      .collect(uniqueIndex(ComponentDto::uuid));
  }
}
