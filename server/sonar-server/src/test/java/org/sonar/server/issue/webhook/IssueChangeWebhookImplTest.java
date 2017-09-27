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
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.issue.DefaultTransitions;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.System2;
import org.sonar.core.issue.IssueChangeContext;
import org.sonar.db.DbClient;
import org.sonar.db.DbTester;
import org.sonar.db.issue.IssueDto;
import org.sonar.server.issue.ws.SearchResponseData;
import org.sonar.server.webhook.WebHooks;
import org.sonar.server.webhook.WebhookPayloadFactory;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class IssueChangeWebhookImplTest {
  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);

  private DbClient dbClient = dbTester.getDbClient();

  private Random random = new Random();
  private RuleType randomRuleType = RuleType.values()[random.nextInt(RuleType.values().length)];
  private IssueChangeContext scanChangeContext = IssueChangeContext.createScan(new Date());
  private IssueChangeContext userChangeContext = IssueChangeContext.createUser(new Date(), "userLogin");
  private WebHooks webHooks = mock(WebHooks.class);
  private MapSettings settings = new MapSettings();
  private WebhookPayloadFactory webhookPayloadFactory = mock(WebhookPayloadFactory.class);
  private IssueChangeWebhookImpl underTest = new IssueChangeWebhookImpl(dbClient, webHooks, settings.asConfig(), webhookPayloadFactory);
  private DbClient mockedDbClient = mock(DbClient.class);
  private Configuration mockedConfiguration = mock(Configuration.class);
  private IssueChangeWebhookImpl mockedUnderTest = new IssueChangeWebhookImpl(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);

  @Before
  public void setUp() throws Exception {
    when(webHooks.isEnabled(mockedConfiguration)).thenReturn(true);
  }

  @Test
  public void onTypeChange_has_no_effect_if_SearchResponseData_has_no_issue() {
    mockedUnderTest.onTypeChange(new SearchResponseData(Collections.emptyList()), randomRuleType, userChangeContext);

    verifyZeroInteractions(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);
  }

  @Test
  public void onTypeChange_has_no_effect_if_scan_IssueChangeContext() {
    mockedUnderTest.onTypeChange(new SearchResponseData(Collections.emptyList()), randomRuleType, scanChangeContext);

    verifyZeroInteractions(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);
  }

  @Test
  public void onTypeChange_has_no_effect_if_webhooks_are_disabled() {
    when(webHooks.isEnabled(mockedConfiguration)).thenReturn(false);

    underTest.onTypeChange(new SearchResponseData(singletonList(new IssueDto())), randomRuleType, userChangeContext);

    verifyZeroInteractions(mockedDbClient, mockedConfiguration, webhookPayloadFactory);
  }

  @Test
  public void onTransition_has_no_effect_if_SearchResponseData_has_no_issue() {
    mockedUnderTest.onTransition(new SearchResponseData(Collections.emptyList()), randomAlphanumeric(12), userChangeContext);

    verifyZeroInteractions(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);
  }

  @Test
  public void onTransition_has_no_effect_if_transition_key_is_null() {
    onTransitionHasNoEffectForTransitionKey(null);
  }

  @Test
  public void onTransition_has_no_effect_if_transition_key_is_empty() {
    onTransitionHasNoEffectForTransitionKey("");
  }

  private void onTransitionHasNoEffectForTransitionKey(@Nullable String transitionKey) {
    reset(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);
    when(webHooks.isEnabled(mockedConfiguration)).thenReturn(true);

    mockedUnderTest.onTransition(new SearchResponseData(singletonList(new IssueDto())), transitionKey, userChangeContext);

    verifyZeroInteractions(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);
  }

  @Test
  public void onTransition_has_no_effect_if_scan_IssueChangeContext() {
    when(webHooks.isEnabled(mockedConfiguration)).thenReturn(true);

    mockedUnderTest.onTransition(new SearchResponseData(singletonList(new IssueDto())), randomAlphanumeric(12), scanChangeContext);

    verifyZeroInteractions(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);
  }

  @Test
  public void onTransition_has_no_effect_if_webhooks_are_disabled() {
    when(webHooks.isEnabled(mockedConfiguration)).thenReturn(false);

    mockedUnderTest.onTransition(new SearchResponseData(singletonList(new IssueDto())), randomAlphanumeric(12), userChangeContext);

    verifyZeroInteractions(mockedDbClient, webHooks, mockedConfiguration, webhookPayloadFactory);
  }

  @Test
  public void onTransition_has_no_effect_if_transition_key_is_ignored_default_transition_key() {
    Set<String> supportedDefaultTransitionKeys = ImmutableSet.of(
      DefaultTransitions.RESOLVE, DefaultTransitions.FALSE_POSITIVE, DefaultTransitions.WONT_FIX, DefaultTransitions.REOPEN);
    DefaultTransitions.ALL.stream()
      .filter(s -> !supportedDefaultTransitionKeys.contains(s))
      .forEach(this::onTransitionHasNoEffectForTransitionKey);
  }
}
