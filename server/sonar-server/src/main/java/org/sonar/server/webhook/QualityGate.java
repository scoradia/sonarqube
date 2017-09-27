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

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.measures.Metric;

import static java.util.Objects.requireNonNull;

public final class QualityGate {
  private final String id;
  private final String name;
  private final Status status;
  private final Set<Condition> conditions;

  public QualityGate(String id, String name, Status status, Set<Condition> conditions) {
    this.id = requireNonNull(id, "id can't be null");
    this.name = requireNonNull(name, "name can't be null");
    this.status = requireNonNull(status, "status can't be null");
    this.conditions = ImmutableSet.copyOf(requireNonNull(conditions, "conditions can't be null"));
  }

  /**
   * The unique identifier of the Quality Gate.
   */
  String getId() {
    return id;
  }

  /**
   * Name of the Quality Gate.
   */
  String getName() {
    return name;
  }

  /**
   * Status of the Quality Gate for the current project processing.
   */
  Status getStatus() {
    return status;
  }

  /**
   * Conditions of the Quality Gate.
   */
  Collection<Condition> getConditions() {
    return conditions;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    QualityGate that = (QualityGate) o;
    return Objects.equals(id, that.id) &&
      Objects.equals(name, that.name) &&
      status == that.status &&
      Objects.equals(conditions, that.conditions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, status, conditions);
  }

  @Override
  public String toString() {
    return "QualityGate{" +
      "id='" + id + '\'' +
      ", name='" + name + '\'' +
      ", status=" + status +
      ", conditions=" + conditions +
      '}';
  }

  public enum Status {
    /** at least one threshold is defined, no threshold is reached */
    OK,
    /** at least one warning threshold is reached, no error threshold is reached */
    WARN,
    /** at least one error threshold is reached */
    ERROR
  }

  public static final class Condition {
    private final EvaluationStatus status;
    private final String metricKey;
    private final Operator operator;
    private final String errorThreshold;
    private final String warnThreshold;
    private final boolean onLeakPeriod;
    private final String value;

    public Condition(EvaluationStatus status, String metricKey, Operator operator,
      @Nullable String errorThreshold, @Nullable String warnThreshold,
      boolean onLeakPeriod, @Nullable String value) {
      this.status = requireNonNull(status, "status can't be null");
      this.metricKey = requireNonNull(metricKey, "metricKey can't be null");
      this.operator = requireNonNull(operator, "operator can't be null");
      this.errorThreshold = errorThreshold;
      this.warnThreshold = warnThreshold;
      this.onLeakPeriod = onLeakPeriod;
      this.value = value;
    }

    /**
     * Evaluation status of this condition
     */
    EvaluationStatus getStatus() {
      return status;
    }

    /**
     * The key of the metric this condition has been evaluated on.
     * <p>
     * The {@link org.sonar.api.measures.Metric} for the returned key can be retrieved using a
     * {@link org.sonar.api.measures.MetricFinder} instance.
     *
     *
     * @see org.sonar.api.batch.measure.MetricFinder#findByKey(String)
     */
    String getMetricKey() {
      return metricKey;
    }

    /**
     * The operator used to evaluate the error and/or warning thresholds against the value of the measure
     */
    Operator getOperator() {
      return operator;
    }

    /**
     * <p>
     * At least one of {@link #getErrorThreshold()} and {@link #getWarningThreshold()} is guaranteed to be non {@code null}.
     *
     *
     * @see #getWarningThreshold()
     */
    Optional<String> getErrorThreshold() {
      return Optional.ofNullable(errorThreshold);
    }

    /**
     *
     * <p>
     * At least one of {@link #getErrorThreshold()} and {@link #getWarningThreshold()} is guaranteed to be non {@code null}.
     *
     *
     * @see #getErrorThreshold()
     */
    Optional<String> getWarningThreshold() {
      return Optional.ofNullable(warnThreshold);
    }

    /**
     * Whether this condition is defined on the leak period or on an absolute value
     */
    boolean isOnLeakPeriod() {
      return onLeakPeriod;
    }

    /**
     * The value of the measure.
     * <p>
     * If the type of the metric (which key is provided by {@link #getMetricKey()}) is numerical, the value can be parsed
     * using {@link Integer#valueOf(String)}, {@link Long#valueOf(String)} or {@link Double#valueOf(String)}.
     *
     *
     * @throws IllegalStateException if {@link #getStatus()} is {@link org.sonar.api.ce.posttask.QualityGate.EvaluationStatus#NO_VALUE}
     *
     * @see Metric#getType()
     */
    Optional<String> getValue() {
      if (status == EvaluationStatus.NO_VALUE) {
        return Optional.empty();
      }
      return Optional.of(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Condition condition = (Condition) o;
      return onLeakPeriod == condition.onLeakPeriod &&
        status == condition.status &&
        Objects.equals(metricKey, condition.metricKey) &&
        operator == condition.operator &&
        Objects.equals(errorThreshold, condition.errorThreshold) &&
        Objects.equals(warnThreshold, condition.warnThreshold) &&
        Objects.equals(value, condition.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(status, metricKey, operator, errorThreshold, warnThreshold, onLeakPeriod, value);
    }

    @Override
    public String toString() {
      return "Condition{" +
        "status=" + status +
        ", metricKey='" + metricKey + '\'' +
        ", operator=" + operator +
        ", errorThreshold='" + errorThreshold + '\'' +
        ", warnThreshold='" + warnThreshold + '\'' +
        ", onLeakPeriod=" + onLeakPeriod +
        ", value='" + value + '\'' +
        '}';
    }
  }

  /**
   * Quality Gate condition operator.
   */
  public enum Operator {
    EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN
  }

  /**
   * Quality gate condition evaluation status.
   */
  public enum EvaluationStatus {
    /**
     * No measure found or measure had no value. The condition has not been evaluated and therefor ignored in
     * the computation of the Quality Gate status.
     */
    NO_VALUE,
    /**
     * Condition evaluated as OK, neither error nor warning thresholds have been reached.
     */
    OK,
    /**
     * Condition evaluated as WARN, only warning thresholds has been reached.
     */
    WARN,
    /**
     * Condition evaluated as ERROR, error thresholds has been reached (and most likely warning thresholds too).
     */
    ERROR
  }
}
