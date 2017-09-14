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
package org.sonar.server.issue.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.sonar.api.config.EmailSettings;
import org.sonar.api.notifications.Notification;
import org.sonar.core.i18n.DefaultI18n;
import org.sonar.plugins.emailnotifications.api.EmailMessage;
import org.sonar.server.user.index.UserDoc;
import org.sonar.server.user.index.UserIndex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.server.issue.notification.NewIssuesStatistics.Metric.ASSIGNEE;
import static org.sonar.server.issue.notification.NewIssuesStatistics.Metric.COMPONENT;
import static org.sonar.server.issue.notification.NewIssuesStatistics.Metric.EFFORT;
import static org.sonar.server.issue.notification.NewIssuesStatistics.Metric.RULE;
import static org.sonar.server.issue.notification.NewIssuesStatistics.Metric.RULE_TYPE;
import static org.sonar.server.issue.notification.NewIssuesStatistics.Metric.TAG;

public class NewIssuesEmailTemplateTest {

  NewIssuesEmailTemplate template;
  DefaultI18n i18n;
  UserIndex userIndex;

  @Before
  public void setUp() {
    EmailSettings settings = mock(EmailSettings.class);
    when(settings.getServerBaseURL()).thenReturn("http://nemo.sonarsource.org");
    i18n = mock(DefaultI18n.class);
    userIndex = mock(UserIndex.class);
    // returns the login passed in parameter
    when(userIndex.getNullableByLogin(anyString()))
      .thenAnswer((Answer<UserDoc>) invocationOnMock -> new UserDoc().setName((String) invocationOnMock.getArguments()[0]));
    when(i18n.message(any(Locale.class), eq("rule_type.CODE_SMELL"), anyString())).thenReturn("Code Smell");
    when(i18n.message(any(Locale.class), eq("rule_type.VULNERABILITY"), anyString())).thenReturn("Vulnerability");
    when(i18n.message(any(Locale.class), eq("rule_type.BUG"), anyString())).thenReturn("Bug");

    template = new NewIssuesEmailTemplate(settings, i18n);
  }

  @Test
  public void no_format_is_not_the_correct_notification() {
    Notification notification = new Notification("my-new-issues");
    EmailMessage message = template.format(notification);
    assertThat(message).isNull();
  }

  @Test
  public void message_id() {
    Notification notification = newNotification();

    EmailMessage message = template.format(notification);

    assertThat(message.getMessageId()).isEqualTo("new-issues/org.apache:struts");
  }

  @Test
  public void subject() {
    Notification notification = newNotification();

    EmailMessage message = template.format(notification);

    assertThat(message.getSubject()).isEqualTo("Struts: 32 new issues (new debt: 1d3h)");
  }

  @Test
  public void format_email_with_all_fields_filled() throws Exception {
    Notification notification = newNotification();
    addAssignees(notification);
    addRules(notification);
    addTags(notification);
    addComponents(notification);

    EmailMessage message = template.format(notification);

    // TODO datetime to be completed when test is isolated from JVM timezone
    assertThat(message.getMessage())
      .startsWith("Project: Struts\n" +
        "\n" +
        "32 new issues (new debt: 1d3h)\n" +
        "\n" +
        "    Type\n" +
        "        Bug: 1    Vulnerability: 10    Code Smell: 3\n" +
        "\n" +
        "    Assignees\n" +
        "        robin.williams: 5\n" +
        "        al.pacino: 7\n" +
        "\n" +
        "    Rules\n" +
        "        Rule the Universe (Clojure): 42\n" +
        "        Rule the World (Java): 5\n" +
        "\n" +
        "    Tags\n" +
        "        oscar: 3\n" +
        "        cesar: 10\n" +
        "\n" +
        "    Most impacted files\n" +
        "        /path/to/file: 3\n" +
        "        /path/to/directory: 7\n" +
        "\n" +
        "See it in SonarQube: http://nemo.sonarsource.org/project/issues?id=org.apache%3Astruts&createdAt=2010-05-1");
  }

  @Test
  public void format_email_with_no_assignees_tags_nor_components() throws Exception {
    Notification notification = newNotification();

    EmailMessage message = template.format(notification);

    // TODO datetime to be completed when test is isolated from JVM timezone
    assertThat(message.getMessage())
      .startsWith("Project: Struts\n" +
        "\n" +
        "32 new issues (new debt: 1d3h)\n" +
        "\n" +
        "    Type\n" +
        "        Bug: 1    Vulnerability: 10    Code Smell: 3\n" +
        "\n" +
        "See it in SonarQube: http://nemo.sonarsource.org/project/issues?id=org.apache%3Astruts&createdAt=2010-05-1");
  }

  @Test
  public void format_email_with_issue_on_branch() throws Exception {
    Notification notification = newNotification()
      .setFieldValue("branch", "feature1");

    EmailMessage message = template.format(notification);

    // TODO datetime to be completed when test is isolated from JVM timezone
    assertThat(message.getMessage())
      .startsWith("Project: Struts\n" +
        "\n" +
        "32 new issues (new debt: 1d3h)\n" +
        "\n" +
        "    Type\n" +
        "        Bug: 1    Vulnerability: 10    Code Smell: 3\n" +
        "\n" +
        "See it in SonarQube: http://nemo.sonarsource.org/project/issues?id=org.apache%3Astruts&branch=feature1&createdAt=2010-05-1");
  }

  @Test
  public void do_not_add_footer_when_properties_missing() {
    Notification notification = new Notification(NewIssuesNotification.TYPE)
      .setFieldValue(RULE_TYPE + ".count", "32")
      .setFieldValue("projectName", "Struts");

    EmailMessage message = template.format(notification);

    assertThat(message.getMessage()).doesNotContain("See it");
  }

  private Notification newNotification() {
    return new Notification(NewIssuesNotification.TYPE)
      .setFieldValue("projectName", "Struts")
      .setFieldValue("projectKey", "org.apache:struts")
      .setFieldValue("projectUuid", "ABCDE")
      .setFieldValue("projectDate", "2010-05-18T14:50:45+0000")
      .setFieldValue(EFFORT + ".count", "1d3h")
      .setFieldValue(RULE_TYPE + ".count", "32")
      .setFieldValue(RULE_TYPE + ".BUG.count", "1")
      .setFieldValue(RULE_TYPE + ".CODE_SMELL.count", "3")
      .setFieldValue(RULE_TYPE + ".VULNERABILITY.count", "10");
  }

  private void addAssignees(Notification notification) {
    notification
      .setFieldValue(ASSIGNEE + ".1.label", "robin.williams")
      .setFieldValue(ASSIGNEE + ".1.count", "5")
      .setFieldValue(ASSIGNEE + ".2.label", "al.pacino")
      .setFieldValue(ASSIGNEE + ".2.count", "7");
  }

  private void addTags(Notification notification) {
    notification
      .setFieldValue(TAG + ".1.label", "oscar")
      .setFieldValue(TAG + ".1.count", "3")
      .setFieldValue(TAG + ".2.label", "cesar")
      .setFieldValue(TAG + ".2.count", "10");
  }

  private void addComponents(Notification notification) {
    notification
      .setFieldValue(COMPONENT + ".1.label", "/path/to/file")
      .setFieldValue(COMPONENT + ".1.count", "3")
      .setFieldValue(COMPONENT + ".2.label", "/path/to/directory")
      .setFieldValue(COMPONENT + ".2.count", "7");
  }

  private void addRules(Notification notification) {
    notification
      .setFieldValue(RULE + ".1.label", "Rule the Universe (Clojure)")
      .setFieldValue(RULE + ".1.count", "42")
      .setFieldValue(RULE + ".2.label", "Rule the World (Java)")
      .setFieldValue(RULE + ".2.count", "5");
  }

  private void assertStartsWithFile(String message, String resourcePath) throws IOException {
    String fileContent = IOUtils.toString(getClass().getResource(resourcePath), StandardCharsets.UTF_8);
    assertThat(sanitizeString(message)).startsWith(sanitizeString(fileContent));
  }

  /**
   * sanitize EOL and tabs if git clone is badly configured
   */
  private static String sanitizeString(String s) {
    return s.replaceAll("\\r\\n|\\r|\\s+", "");
  }
}
