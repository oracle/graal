/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.profdiff.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.util.Writer;

/**
 * An experiment consisting of all graal-compiled methods and metadata. Additionally, this class
 * allows its {@link CompilationUnit executed methods} to be added incrementally. This is necessary
 * to be able to create an instance of the experiment first and then bind the {@link CompilationUnit
 * executed methods} with their {@link Experiment}.
 */
public class Experiment {
    /**
     * The list of graal-compiled executed methods belonging to this experiment. The list is empty
     * initially and gets built incrementally via {@link #addCompilationUnit(CompilationUnit)}.
     */
    private final List<CompilationUnit> compilationUnits;

    /**
     * The execution ID of this experiment.
     */
    private final String executionId;

    /**
     * The ID of this experiment.
     */
    private final ExperimentId experimentId;

    /**
     * {@code true} if proftool data of the experiment is available, i.e., {@link #totalPeriod} and
     * {@link CompilationUnit#getPeriod()} contain any useful information. The {@link #executionId}
     * is also sourced from proftool.
     */
    private final boolean profileAvailable;

    /**
     * The total period of all executed methods including non-graal executions.
     */
    private final long totalPeriod;

    /**
     * The total number of methods collected by proftool.
     */
    private final int totalProftoolMethods;

    /**
     * A cached sum of execution periods of the {@link #compilationUnits}. Initially {@code null}
     * and computed on demand.
     */
    private Long graalPeriod;

    /**
     * Maps {@link CompilationUnit#getCompilationMethodName() compilation method names} to methods
     * with a matching name in this experiment.
     */
    private final EconomicMap<String, List<CompilationUnit>> compilationUnitsByName;

    /**
     * Constructs an experiment with an execution profile.
     *
     * @param executionId the execution ID of the experiment
     * @param experimentId the ID of the experiment
     * @param totalPeriod the total period of all executed methods including non-graal executions
     * @param totalProftoolMethods the total number of methods collected by proftool
     */
    public Experiment(
                    String executionId,
                    ExperimentId experimentId,
                    long totalPeriod,
                    int totalProftoolMethods) {
        this.compilationUnits = new ArrayList<>();
        this.executionId = executionId;
        this.experimentId = experimentId;
        this.profileAvailable = true;
        this.totalPeriod = totalPeriod;
        this.totalProftoolMethods = totalProftoolMethods;
        this.compilationUnitsByName = EconomicMap.create();
    }

    /**
     * Constructs an experiment without execution profile.
     *
     * @param experimentId the ID of the experiment
     */
    public Experiment(ExperimentId experimentId) {
        this.compilationUnits = new ArrayList<>();
        this.executionId = null;
        this.experimentId = experimentId;
        this.profileAvailable = false;
        this.totalPeriod = 0;
        this.totalProftoolMethods = 0;
        this.compilationUnitsByName = EconomicMap.create();
    }

    /**
     * Gets the experiment ID.
     */
    public ExperimentId getExperimentId() {
        return experimentId;
    }

    /**
     * Gets the execution ID.
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * Gets the total period of all executed code including non-graal executions.
     *
     * @return the total period of execution
     */
    public long getTotalPeriod() {
        return totalPeriod;
    }

    /**
     * Gets the sum of the periods of all graal-compiled methods.
     *
     * @return the total period of graal-compiled methods
     */
    public long getGraalPeriod() {
        if (graalPeriod != null) {
            return graalPeriod;
        }
        graalPeriod = compilationUnits.stream().mapToLong(CompilationUnit::getPeriod).sum();
        return graalPeriod;
    }

    /**
     * Gets the list of all compilation units, including non-hot methods.
     *
     * @return the list of compilation units
     */
    public List<CompilationUnit> getCompilationUnits() {
        return compilationUnits;
    }

    public EconomicMap<String, List<CompilationUnit>> getCompilationUnitsByName() {
        return compilationUnitsByName;
    }

    /**
     * Returns whether proftool data is available to the experiment. If it is not, there is no
     * information about the period of execution of the experiment and individual compilation units.
     * We also do not have the {@link #getExecutionId() executionId}, which also comes from
     * proftool.
     *
     * @return {@code true} if proftool data is available
     */
    public boolean isProfileAvailable() {
        return profileAvailable;
    }

    /**
     * Groups hot compilation units by method name.
     *
     * @see CompilationUnit#getCompilationMethodName()
     * @return a map of lists of compilation units grouped by method name
     */
    public EconomicMap<String, List<CompilationUnit>> groupHotCompilationUnitsByMethod() {
        EconomicMap<String, List<CompilationUnit>> map = EconomicMap.create();
        for (CompilationUnit compilationUnit : compilationUnits) {
            if (compilationUnit.isHot()) {
                List<CompilationUnit> methods;
                if (map.containsKey(compilationUnit.getCompilationMethodName())) {
                    methods = map.get(compilationUnit.getCompilationMethodName());
                } else {
                    methods = new ArrayList<>();
                    map.put(compilationUnit.getCompilationMethodName(), methods);
                }
                methods.add(compilationUnit);
            }
        }
        return map;
    }

    /**
     * Writes a summary of the experiment. Includes the number of methods collected (proftool and
     * optimization log), relative period of graal-compiled methods, the number and relative period
     * of hot methods.
     *
     * @param writer the destination writer
     */
    public void writeExperimentSummary(Writer writer) {
        String graalExecutionPercent = String.format("%.2f", (double) getGraalPeriod() / totalPeriod * 100);
        String graalHotExecutionPercent = String.format("%.2f", (double) countHotGraalPeriod() / totalPeriod * 100);
        writer.write("Experiment " + experimentId);
        if (executionId != null) {
            writer.writeln(" with execution ID " + executionId);
        } else {
            writer.writeln();
        }
        writer.increaseIndent();
        writer.writeln("Collected optimization logs for " + compilationUnits.size() + " methods");
        if (profileAvailable) {
            writer.writeln("Collected proftool data for " + totalProftoolMethods + " methods");
            writer.writeln("Graal-compiled methods account for " + graalExecutionPercent + "% of execution");
            writer.writeln(countHotCompilationUnits() + " hot compilation units account for " + graalHotExecutionPercent + "% of execution");
        }
        writer.decreaseIndent();
    }

    /**
     * Writes the list of compilations (including compilation ID, share of the execution and hotness
     * for each compilation) for a method with header identifying this experiment. If there is no
     * {@link #profileAvailable}, information about execution is omitted.
     *
     * @param writer the destination writer
     * @param compilationMethodName the compilation method name to summarize
     */
    public void writeCompilationUnits(Writer writer, String compilationMethodName) {
        writer.writeln("In experiment " + experimentId);
        writer.increaseIndent();
        List<CompilationUnit> methods = compilationUnitsByName.get(compilationMethodName);
        if (methods == null) {
            writer.writeln("No compilations");
            writer.decreaseIndent();
            return;
        }
        methods = methods.stream().sorted(Comparator.comparingLong(compilationUnit -> -compilationUnit.getPeriod())).collect(Collectors.toList());
        writer.write(methods.size() + " compilations");
        if (profileAvailable) {
            long hotMethodCount = methods.stream().filter(CompilationUnit::isHot).count();
            writer.writeln(" (" + hotMethodCount + " of which are hot)");
        } else {
            writer.writeln();
        }
        writer.writeln("Compilations");
        writer.increaseIndent();
        for (CompilationUnit compilationUnit : methods) {
            writer.write(compilationUnit.getCompilationId());
            if (profileAvailable) {
                writer.writeln(" (" + compilationUnit.createExecutionSummary() + ((compilationUnit.isHot()) ? ") *hot*" : ")"));
            } else {
                writer.writeln();
            }
        }
        writer.decreaseIndent(2);
    }

    private long countHotCompilationUnits() {
        return compilationUnits.stream().filter(CompilationUnit::isHot).count();
    }

    private long countHotGraalPeriod() {
        return compilationUnits.stream().filter(CompilationUnit::isHot).mapToLong(CompilationUnit::getPeriod).sum();
    }

    /**
     * Add a compilation unit to this experiment. The compilation unit's experiment must be set to
     * this instance.
     *
     * @param compilationUnit the compilation unit to be added
     */
    public void addCompilationUnit(CompilationUnit compilationUnit) {
        assert compilationUnit.getExperiment() == this;
        graalPeriod = null;
        compilationUnits.add(compilationUnit);
        List<CompilationUnit> methods = compilationUnitsByName.get(compilationUnit.getCompilationMethodName());
        if (methods == null) {
            methods = new ArrayList<>();
            compilationUnitsByName.put(compilationUnit.getCompilationMethodName(), methods);
        }
        methods.add(compilationUnit);
    }

    /**
     * Preprocess the compilation units of the experiment according to the provided
     * {@link VerbosityLevel}. Preprocessing includes removing some branches from optimization
     * and/or inlining trees, and partially sorting them. The intention is to show less output and
     * produce fewer differences after tree matching for lower verbosity levels.
     *
     * @param verbosityLevel the verbosity level to designate the level of preprocessing
     */
    public void preprocessCompilationUnits(VerbosityLevel verbosityLevel) {
        compilationUnits.forEach(compilationUnit -> {
            if (verbosityLevel.shouldSortInliningTree()) {
                compilationUnit.sortInliningTree();
            }
            if (verbosityLevel.shouldRemoveVeryDetailedPhases()) {
                compilationUnit.removeVeryDetailedPhases();
            }
            if (verbosityLevel.shouldSortUnorderedPhases()) {
                compilationUnit.sortUnorderedPhases();
            }
        });
    }
}
