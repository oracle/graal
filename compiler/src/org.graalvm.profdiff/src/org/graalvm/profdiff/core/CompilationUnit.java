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

import java.util.List;

import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.util.Writer;

/**
 * Represents an executed Graal compilation of a method.
 */
public class CompilationUnit {
    /**
     * The compilation ID of this compilation unit as reported in the optimization log. Matches
     * {@code compileId} in the proftool output.
     */
    private final String compilationId;

    /**
     * The full signature of the compiled root method including parameter types as reported in the
     * optimization log.
     */
    private final String compilationMethodName;

    /**
     * The root optimization phase of this compilation unit, which holds all optimization phases
     * applied in this compilation.
     */
    private final OptimizationPhase rootPhase;

    /**
     * The period of execution as reported by proftool.
     */
    private final long period;

    /**
     * The experiment to which compilation unit belongs.
     */
    private final Experiment experiment;

    /**
     * The hot flag of this compilation unit.
     */
    private boolean hot;

    public CompilationUnit(String compilationId,
                    String compilationMethodName,
                    OptimizationPhase rootPhase,
                    long period,
                    Experiment experiment) {
        this.compilationId = compilationId;
        this.compilationMethodName = compilationMethodName;
        this.period = period;
        this.rootPhase = rootPhase;
        this.experiment = experiment;
    }

    /**
     * Gets the experiment to which this compilation unit belongs.
     */
    public Experiment getExperiment() {
        return experiment;
    }

    /**
     * Creates and returns a summary that specifies the execution time of this compilation unit
     * relative to the total execution time and relative to the execution time of all graal-compiled
     * methods.
     *
     * Example of a returned string:
     *
     * <pre>
     * 10.05% of Graal execution, 4.71% of total
     * </pre>
     *
     * @return a summary of the relative execution time
     */
    public String createExecutionSummary() {
        String graalPercent = String.format("%.2f", (double) period / experiment.getGraalPeriod() * 100);
        String totalPercent = String.format("%.2f", (double) period / experiment.getTotalPeriod() * 100);
        return graalPercent + "% of Graal execution, " + totalPercent + "% of total";
    }

    /**
     * Gets the compilation ID of this compilation unit as reported in the optimization log. Matches
     * {@code compileId} in the proftool output.
     *
     * @return the compilation ID
     */
    public String getCompilationId() {
        return compilationId;
    }

    /**
     * Gets the full signature of the root method of this compilation unit including parameter types
     * as reported in the optimization log. Multiple compilation units may share the same
     * compilation method name, because a method may get compiled several times. It can be used to
     * identify a group of compilations of a single Java method.
     *
     * Example of a returned string:
     *
     * <pre>
     * java.lang.Math.max(int, int)
     * </pre>
     *
     * @return the compilation method name
     */
    public String getCompilationMethodName() {
        return compilationMethodName;
    }

    /**
     * Gets the root optimization phase of this compilation unit, which holds all optimization
     * phases applied in this compilation.
     *
     * @return the root optimization phase
     */
    public OptimizationPhase getRootPhase() {
        return rootPhase;
    }

    /**
     * Gets the hot flag of this compilation unit. The hotness is not included in the proftool
     * output.
     *
     * @return the hot status of this compilation unit
     * @see HotCompilationUnitPolicy
     */
    public boolean isHot() {
        return hot;
    }

    /**
     * Sets the hot status of this compilation unit.
     *
     * @param hot the hot status of this compilation unit
     */
    public void setHot(boolean hot) {
        this.hot = hot;
    }

    /**
     * Gets the period of execution as reported by proftool.
     *
     * @return the period of execution
     */
    public long getPeriod() {
        return period;
    }

    /**
     * Creates and returns a list of all optimizations performed during the compilation of this
     * compilation unit, preserving the order.
     *
     * @return the list of optimizations
     */
    public List<Optimization> getOptimizationsRecursive() {
        return rootPhase.getOptimizationsRecursive();
    }

    /**
     * Writes the header of the compilation unit (compilation ID, execution summary, experiment ID)
     * and the optimization tree to the destination writer.
     *
     * @param writer the destination writer
     */
    public void write(Writer writer) {
        writer.writeln("Compilation " + compilationId + " (" + createExecutionSummary() + ") in experiment " + experiment.getExperimentId());
        writer.increaseIndent();
        rootPhase.writeRecursive(writer);
        writer.decreaseIndent();
    }
}
