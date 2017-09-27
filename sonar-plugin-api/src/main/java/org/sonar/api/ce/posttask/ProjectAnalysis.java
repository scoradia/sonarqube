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

package org.sonar.api.ce.posttask;

import java.util.Date;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ProjectAnalysis implements PostProjectAnalysisTask.ProjectAnalysis {
  private final CeTask ceTask;
  private final Project project;
  private final Branch branch;
  private final QualityGate qualityGate;
  private final org.sonar.api.ce.posttask.Analysis analysis;
  private final ScannerContext scannerContext;
  private final Date date;

  public ProjectAnalysis(CeTask ceTask, Project project, @Nullable Branch branch, QualityGate qualityGate,
    @Nullable org.sonar.api.ce.posttask.Analysis analysis, ScannerContext scannerContext, Date date) {
    this.ceTask = ceTask;
    this.project = project;
    this.branch = branch;
    this.qualityGate = qualityGate;
    this.analysis = analysis;
    this.scannerContext = scannerContext;
    this.date = date;
  }

  @Override
  public CeTask getCeTask() {
    return ceTask;
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public Optional<Branch> getBranch() {
    return Optional.ofNullable(branch);
  }

  @CheckForNull
  @Override
  public QualityGate getQualityGate() {
    return qualityGate;
  }

  @Override
  public Date getDate() {
    return date;
  }

  @Override
  public Optional<org.sonar.api.ce.posttask.Analysis> getAnalysis() {
    return Optional.ofNullable(analysis);
  }

  @Override
  public ScannerContext getScannerContext() {
    return scannerContext;
  }

  @Override
  public String toString() {
    return "ProjectAnalysis{" +
      "ceTask=" + ceTask +
      ", project=" + project +
      ", date=" + date.getTime() +
      ", analysisDate=" + date.getTime() +
      ", qualityGate=" + qualityGate +
      '}';
  }

  public static class Analysis implements org.sonar.api.ce.posttask.Analysis {
    private final String analysisUuid;
    private final Date date;

    public Analysis(String analysisUuid, Date date) {
      this.analysisUuid = analysisUuid;
      this.date = date;
    }

    @Override
    public String getAnalysisUuid() {
      return analysisUuid;
    }

    @Override
    public Date getDate() {
      return date;
    }
  }
}
