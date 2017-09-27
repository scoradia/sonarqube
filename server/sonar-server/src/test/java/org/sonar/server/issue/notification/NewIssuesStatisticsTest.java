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

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.CheckForNull;
import org.junit.Test;
import org.sonar.api.issue.Issue;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.utils.Duration;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.server.issue.notification.NewIssuesStatistics.Metric;

import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

public class NewIssuesStatisticsTest {

  NewIssuesStatistics underTest = new NewIssuesStatistics(Issue::isNew);

  @Test
  public void add_issues_with_correct_global_statistics() {
    DefaultIssue issue = new DefaultIssue()
      .setAssignee("maynard")
      .setComponentUuid("file-uuid")
      .setNew(true)
      .setSeverity(Severity.INFO)
      .setRuleKey(RuleKey.of("SonarQube", "rule-the-world"))
      .setTags(Lists.newArrayList("bug", "owasp"))
      .setEffort(Duration.create(5L));

    underTest.add(issue);
    underTest.add(issue.setAssignee("james"));
    underTest.add(issue.setAssignee("keenan"));

    assertThat(countDistributionTotal(Metric.ASSIGNEE, "maynard")).isEqualTo(1);
    assertThat(countDistributionTotal(Metric.ASSIGNEE, "james")).isEqualTo(1);
    assertThat(countDistributionTotal(Metric.ASSIGNEE, "keenan")).isEqualTo(1);
    assertThat(countDistributionTotal(Metric.ASSIGNEE, "wrong.login")).isNull();
    assertThat(countDistributionTotal(Metric.COMPONENT, "file-uuid")).isEqualTo(3);
    assertThat(countDistributionTotal(Metric.COMPONENT, "wrong-uuid")).isNull();
    assertThat(countDistributionTotal(Metric.SEVERITY, Severity.INFO)).isEqualTo(3);
    assertThat(countDistributionTotal(Metric.SEVERITY, Severity.CRITICAL)).isNull();
    assertThat(countDistributionTotal(Metric.TAG, "owasp")).isEqualTo(3);
    assertThat(countDistributionTotal(Metric.TAG, "wrong-tag")).isNull();
    assertThat(countDistributionTotal(Metric.RULE, "SonarQube:rule-the-world")).isEqualTo(3);
    assertThat(countDistributionTotal(Metric.RULE, "SonarQube:has-a-fake-rule")).isNull();
    assertThat(underTest.globalStatistics().effort().getTotal()).isEqualTo(15L);
    assertThat(underTest.globalStatistics().hasIssues()).isTrue();
    assertThat(underTest.hasIssues()).isTrue();
    assertThat(underTest.assigneesStatistics().get("maynard").hasIssues()).isTrue();
  }

  @Test
  public void add_counts_issue_per_severity_on_leak_globally_and_per_assignee() {
    String assignee = randomAlphanumeric(10);
    Severity.ALL.stream()
      .map(severity -> new DefaultIssue().setSeverity(severity).setAssignee(assignee).setNew(true))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.SEVERITY);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.SEVERITY);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertStats(distribution, Severity.INFO, 1, 0, 1);
        assertStats(distribution, Severity.MAJOR, 1, 0, 1);
        assertStats(distribution, Severity.CRITICAL, 1, 0, 1);
        assertStats(distribution, Severity.MINOR, 1, 0, 1);
        assertStats(distribution, Severity.BLOCKER, 1, 0, 1);
      });
  }

  @Test
  public void add_counts_issue_per_severity_off_leak_globally_and_per_assignee() {
    String assignee = randomAlphanumeric(10);
    Severity.ALL.stream()
      .map(severity -> new DefaultIssue().setSeverity(severity).setAssignee(assignee).setNew(false))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.SEVERITY);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.SEVERITY);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertStats(distribution, Severity.INFO, 0, 1, 1);
        assertStats(distribution, Severity.MAJOR, 0, 1, 1);
        assertStats(distribution, Severity.CRITICAL, 0, 1, 1);
        assertStats(distribution, Severity.MINOR, 0, 1, 1);
        assertStats(distribution, Severity.BLOCKER, 0, 1, 1);
      });
  }

  @Test
  public void add_counts_severity_if_null_globally_and_per_assignee_as_it_should_not_be_null() {
    String assignee = randomAlphanumeric(10);
    underTest.add(new DefaultIssue().setSeverity(null).setAssignee(assignee).setNew(new Random().nextBoolean()));

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.SEVERITY);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.SEVERITY);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertThat(distribution.getTotal()).isEqualTo(1);
        assertThat(distribution.getForLabel(null).isPresent()).isTrue();
      });
  }

  @Test
  public void add_counts_issue_per_component_on_leak_globally_and_per_assignee() {
    List<String> componentUuids = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    String assignee = randomAlphanumeric(10);
    componentUuids.stream()
      .map(componentUuid -> new DefaultIssue().setComponentUuid(componentUuid).setAssignee(assignee).setNew(true))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.COMPONENT);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.COMPONENT);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> componentUuids.forEach(componentUuid -> assertStats(distribution, componentUuid, 1, 0, 1)));
  }

  @Test
  public void add_counts_issue_per_component_off_leak_globally_and_per_assignee() {
    List<String> componentUuids = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    String assignee = randomAlphanumeric(10);
    componentUuids.stream()
      .map(componentUuid -> new DefaultIssue().setComponentUuid(componentUuid).setAssignee(assignee).setNew(false))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.COMPONENT);
    NewIssuesStatistics.Stats stats = underTest.assigneesStatistics().get(assignee);
    DistributedMetricStatsInt assigneeDistribution = stats.getDistributedMetricStats(Metric.COMPONENT);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> componentUuids.forEach(componentUuid -> assertStats(distribution, componentUuid, 0, 1, 1)));
  }

  @Test
  public void add_counts_component_if_null_globally_and_per_assignee_as_it_should_not_be_null() {
    String assignee = randomAlphanumeric(10);
    underTest.add(new DefaultIssue().setComponentUuid(null).setAssignee(assignee).setNew(new Random().nextBoolean()));

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.COMPONENT);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.COMPONENT);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertThat(distribution.getTotal()).isEqualTo(1);
        assertThat(distribution.getForLabel(null).isPresent()).isTrue();
      });
  }

  @Test
  public void add_counts_issue_per_ruleKey_on_leak_globally_and_per_assignee() {
    String repository = randomAlphanumeric(3);
    List<String> ruleKeys = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    String assignee = randomAlphanumeric(10);
    ruleKeys.stream()
      .map(ruleKey -> new DefaultIssue().setRuleKey(RuleKey.of(repository, ruleKey)).setAssignee(assignee).setNew(true))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.RULE);
    NewIssuesStatistics.Stats stats = underTest.assigneesStatistics().get(assignee);
    DistributedMetricStatsInt assigneeDistribution = stats.getDistributedMetricStats(Metric.RULE);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> ruleKeys.forEach(ruleKey -> assertStats(distribution, RuleKey.of(repository, ruleKey).toString(), 1, 0, 1)));
  }

  @Test
  public void add_counts_issue_per_ruleKey_off_leak_globally_and_per_assignee() {
    String repository = randomAlphanumeric(3);
    List<String> ruleKeys = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    String assignee = randomAlphanumeric(10);
    ruleKeys.stream()
      .map(ruleKey -> new DefaultIssue().setRuleKey(RuleKey.of(repository, ruleKey)).setAssignee(assignee).setNew(false))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.RULE);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.RULE);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> ruleKeys.forEach(ruleKey -> assertStats(distribution, RuleKey.of(repository, ruleKey).toString(), 0, 1, 1)));
  }

  @Test
  public void add_does_not_count_ruleKey_if_neither_neither_globally_nor_per_assignee() {
    String assignee = randomAlphanumeric(10);
    underTest.add(new DefaultIssue().setRuleKey(null).setAssignee(assignee).setNew(new Random().nextBoolean()));

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.RULE);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.RULE);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertThat(distribution.getTotal()).isEqualTo(0);
        assertThat(distribution.getForLabel(null).isPresent()).isFalse();
      });
  }

  @Test
  public void add_counts_issue_per_assignee_on_leak_globally_and_per_assignee() {
    List<String> assignees = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    assignees.stream()
      .map(assignee -> new DefaultIssue().setAssignee(assignee).setNew(true))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.ASSIGNEE);
    assignees.forEach(assignee -> assertStats(globalDistribution, assignee, 1, 0, 1));
    assignees.forEach(assignee -> {
      NewIssuesStatistics.Stats stats = underTest.assigneesStatistics().get(assignee);
      DistributedMetricStatsInt assigneeStats = stats.getDistributedMetricStats(Metric.ASSIGNEE);
      assertThat(assigneeStats.getOnLeak()).isEqualTo(1);
      assertThat(assigneeStats.getOffLeak()).isEqualTo(0);
      assertThat(assigneeStats.getTotal()).isEqualTo(1);
      assignees.forEach(s -> {
        Optional<MetricStatsInt> forLabelOpts = assigneeStats.getForLabel(s);
        if (s.equals(assignee)) {
          assertThat(forLabelOpts.isPresent()).isTrue();
          MetricStatsInt forLabel = forLabelOpts.get();
          assertThat(forLabel.getOnLeak()).isEqualTo(1);
          assertThat(forLabel.getOffLeak()).isEqualTo(0);
          assertThat(forLabel.getTotal()).isEqualTo(1);
        } else {
          assertThat(forLabelOpts.isPresent()).isFalse();
        }
      });
    });
  }

  @Test
  public void add_counts_issue_per_assignee_off_leak_globally_and_per_assignee() {
    List<String> assignees = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    assignees.stream()
      .map(assignee -> new DefaultIssue().setAssignee(assignee).setNew(false))
      .forEach(underTest::add);

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.ASSIGNEE);
    assignees.forEach(assignee -> assertStats(globalDistribution, assignee, 0, 1, 1));
    assignees.forEach(assignee -> {
      NewIssuesStatistics.Stats stats = underTest.assigneesStatistics().get(assignee);
      DistributedMetricStatsInt assigneeStats = stats.getDistributedMetricStats(Metric.ASSIGNEE);
      assertThat(assigneeStats.getOnLeak()).isEqualTo(0);
      assertThat(assigneeStats.getOffLeak()).isEqualTo(1);
      assertThat(assigneeStats.getTotal()).isEqualTo(1);
      assignees.forEach(s -> {
        Optional<MetricStatsInt> forLabelOpts = assigneeStats.getForLabel(s);
        if (s.equals(assignee)) {
          assertThat(forLabelOpts.isPresent()).isTrue();
          MetricStatsInt forLabel = forLabelOpts.get();
          assertThat(forLabel.getOnLeak()).isEqualTo(0);
          assertThat(forLabel.getOffLeak()).isEqualTo(1);
          assertThat(forLabel.getTotal()).isEqualTo(1);
        } else {
          assertThat(forLabelOpts.isPresent()).isFalse();
        }
      });
    });
  }

  @Test
  public void add_does_not_assignee_if_empty_neither_globally_nor_per_assignee() {
    underTest.add(new DefaultIssue().setAssignee(null).setNew(new Random().nextBoolean()));

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.ASSIGNEE);
    assertThat(globalDistribution.getTotal()).isEqualTo(0);
    assertThat(globalDistribution.getForLabel(null).isPresent()).isFalse();
    assertThat(underTest.assigneesStatistics()).isEmpty();
  }

  @Test
  public void add_counts_issue_per_tags_on_leak_globally_and_per_assignee() {
    List<String> tags = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    String assignee = randomAlphanumeric(10);
    underTest.add(new DefaultIssue().setTags(tags).setAssignee(assignee).setNew(true));

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.TAG);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.TAG);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> tags.forEach(tag -> assertStats(distribution, tag, 1, 0, 1)));
  }

  @Test
  public void add_counts_issue_per_tags_off_leak_globally_and_per_assignee() {
    List<String> tags = IntStream.range(0, 1 + new Random().nextInt(10)).mapToObj(i -> randomAlphabetic(3)).collect(Collectors.toList());
    String assignee = randomAlphanumeric(10);
    underTest.add(new DefaultIssue().setTags(tags).setAssignee(assignee).setNew(false));

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.TAG);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.TAG);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> tags.forEach(tag -> assertStats(distribution, tag, 0, 1, 1)));
  }

  @Test
  public void add_does_not_count_tags_if_empty_neither_globally_nor_per_assignee() {
    String assignee = randomAlphanumeric(10);
    underTest.add(new DefaultIssue().setTags(Collections.emptyList()).setAssignee(assignee).setNew(new Random().nextBoolean()));

    DistributedMetricStatsInt globalDistribution = underTest.globalStatistics().getDistributedMetricStats(Metric.TAG);
    DistributedMetricStatsInt assigneeDistribution = underTest.assigneesStatistics().get(assignee).getDistributedMetricStats(Metric.TAG);
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertThat(distribution.getTotal()).isEqualTo(0);
        assertThat(distribution.getForLabel(null).isPresent()).isFalse();
      });
  }

  @Test
  public void add_sums_effort_on_leak_globally_and_per_assignee() {
    Random random = new Random();
    List<Integer> efforts = IntStream.range(0, 1 + random.nextInt(10)).mapToObj(i -> 10_000 * i).collect(Collectors.toList());
    int expected = efforts.stream().mapToInt(s -> s).sum();
    String assignee = randomAlphanumeric(10);
    efforts.stream()
      .map(effort -> new DefaultIssue().setEffort(Duration.create(effort)).setAssignee(assignee).setNew(true))
      .forEach(underTest::add);

    MetricStatsLong globalDistribution = underTest.globalStatistics().effort();
    MetricStatsLong assigneeDistribution = underTest.assigneesStatistics().get(assignee).effort();
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertThat(distribution.getOnLeak()).isEqualTo(expected);
        assertThat(distribution.getOffLeak()).isEqualTo(0);
        assertThat(distribution.getTotal()).isEqualTo(expected);
      });
  }

  @Test
  public void add_sums_effort_off_leak_globally_and_per_assignee() {
    Random random = new Random();
    List<Integer> efforts = IntStream.range(0, 1 + random.nextInt(10)).mapToObj(i -> 10_000 * i).collect(Collectors.toList());
    int expected = efforts.stream().mapToInt(s -> s).sum();
    String assignee = randomAlphanumeric(10);
    efforts.stream()
      .map(effort -> new DefaultIssue().setEffort(Duration.create(effort)).setAssignee(assignee).setNew(false))
      .forEach(underTest::add);

    MetricStatsLong globalDistribution = underTest.globalStatistics().effort();
    MetricStatsLong assigneeDistribution = underTest.assigneesStatistics().get(assignee).effort();
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> {
        assertThat(distribution.getOnLeak()).isEqualTo(0);
        assertThat(distribution.getOffLeak()).isEqualTo(expected);
        assertThat(distribution.getTotal()).isEqualTo(expected);
      });
  }

  @Test
  public void add_does_not_sum_effort_if_null_neither_globally_nor_per_assignee() {
    String assignee = randomAlphanumeric(10);
    underTest.add(new DefaultIssue().setEffort(null).setAssignee(assignee).setNew(new Random().nextBoolean()));

    MetricStatsLong globalDistribution = underTest.globalStatistics().effort();
    MetricStatsLong assigneeDistribution = underTest.assigneesStatistics().get(assignee).effort();
    Stream.of(globalDistribution, assigneeDistribution)
      .forEach(distribution -> assertThat(distribution.getTotal()).isEqualTo(0));
  }

  private void assertStats(DistributedMetricStatsInt distribution, String label, int onLeak, int offLeak, int total) {
    Optional<MetricStatsInt> statsOption = distribution.getForLabel(label);
    assertThat(statsOption.isPresent()).describedAs("distribution for label %s not found", label).isTrue();
    MetricStatsInt stats = statsOption.get();
    assertThat(stats.getOnLeak()).isEqualTo(onLeak);
    assertThat(stats.getOffLeak()).isEqualTo(offLeak);
    assertThat(stats.getTotal()).isEqualTo(total);
  }

  @Test
  public void add_counts_issue_per_severity_per_assignee() {
    String assignee = randomAlphanumeric(20);
    Severity.ALL.stream().map(severity -> new DefaultIssue()
      .setSeverity(severity)
      .setAssignee(assignee)).forEach(underTest::add);

    assertThat(underTest.globalStatistics()
      .getDistributedMetricStats(Metric.SEVERITY)
      .getForLabel(Severity.INFO)
      .map(MetricStatsInt::getTotal)
      .orElse(null)).isEqualTo(1);
    assertThat(countDistributionTotal(Metric.SEVERITY, Severity.MINOR)).isEqualTo(1);
    assertThat(countDistributionTotal(Metric.SEVERITY, Severity.CRITICAL)).isEqualTo(1);
    assertThat(countDistributionTotal(Metric.SEVERITY, Severity.BLOCKER)).isEqualTo(1);
    assertThat(countDistributionTotal(Metric.SEVERITY, Severity.MAJOR)).isEqualTo(1);
  }

  @Test
  public void do_not_have_issues_when_no_issue_added() {
    assertThat(underTest.globalStatistics().hasIssues()).isFalse();
  }

  @CheckForNull
  private Integer countDistributionTotal(Metric metric, String label) {
    return underTest.globalStatistics()
      .getDistributedMetricStats(metric)
      .getForLabel(label)
      .map(MetricStatsInt::getTotal)
      .orElse(null);
  }

  @CheckForNull
  private Integer countDistributionOnLeak(Metric metric, String label) {
    return underTest.globalStatistics()
      .getDistributedMetricStats(metric)
      .getForLabel(label)
      .map(MetricStatsInt::getOnLeak)
      .orElse(null);
  }

  @CheckForNull
  private Integer countDistributionOffLeak(Metric metric, String label) {
    return underTest.globalStatistics()
      .getDistributedMetricStats(metric)
      .getForLabel(label)
      .map(MetricStatsInt::getOffLeak)
      .orElse(null);
  }

  private DefaultIssue defaultIssue() {
    return new DefaultIssue()
      .setAssignee("maynard")
      .setComponentUuid("file-uuid")
      .setNew(true)
      .setSeverity(Severity.INFO)
      .setRuleKey(RuleKey.of("SonarQube", "rule-the-world"))
      .setTags(Lists.newArrayList("bug", "owasp"))
      .setEffort(Duration.create(5L));
  }
}
