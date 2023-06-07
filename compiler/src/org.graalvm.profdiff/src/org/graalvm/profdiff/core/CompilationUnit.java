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

import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.parser.ExperimentParserError;

/**
 * Represents an executed Graal compilation of a method.
 *
 * A compilation unit contains holds only its identifiers (compilation ID, multi-method key, root
 * method) and simple scalar metadata (execution period, hot flag). The most resource-heavy parts,
 * i.e. an {@link OptimizationTree} and {@link InliningTree}, can be loaded on demand. This design
 * is necessary, because a whole experiment might not fit in memory.
 */
public class CompilationUnit {
    /**
     * A pair of an {@link OptimizationTree} and {@link InliningTree} belonging to a compilation
     * unit.
     */
    public static class TreePair {
        private final OptimizationTree optimizationTree;

        private final InliningTree inliningTree;

        public TreePair(OptimizationTree optimizationTree, InliningTree inliningTree) {
            this.optimizationTree = optimizationTree;
            this.inliningTree = inliningTree;
        }

        public InliningTree getInliningTree() {
            return inliningTree;
        }

        public OptimizationTree getOptimizationTree() {
            return optimizationTree;
        }
    }

    /**
     * Loads the {@link OptimizationTree} and the {@link InliningTree} of a compilation unit on
     * demand.
     */
    public interface TreeLoader {
        /**
         * Loads and returns a pair of trees associated with a compilation unit.
         *
         * @return the loaded pair of trees
         * @throws ExperimentParserError the trees could not be loaded
         */
        TreePair load() throws ExperimentParserError;
    }

    /**
     * The root method of this compilation unit.
     */
    private final Method method;

    /**
     * The compilation ID of this compilation unit as reported in the optimization log. Matches
     * {@code compileId} in the proftool output.
     */
    private final String compilationId;

    /**
     * The period of execution as reported by proftool.
     */
    private final long period;

    /**
     * The hot flag of this compilation unit.
     */
    private boolean hot;

    /**
     * The tree loader associated with this compilation unit.
     */
    private final TreeLoader loader;

    /**
     * The key of the compiled multi-method. {@code null} if this is not a compilation of a
     * multi-method. If the key contains an additional suffix due to name collision prevention, the
     * suffix is a part of the multi-method key.
     */
    private final String multiMethodKey;

    public CompilationUnit(Method method, String compilationId, long period, TreeLoader loader, String multiMethodKey) {
        this.method = method;
        this.compilationId = compilationId;
        this.period = period;
        this.loader = loader;
        this.multiMethodKey = multiMethodKey;
    }

    public CompilationUnit(Method method, String compilationId, long period, TreeLoader loader) {
        this(method, compilationId, period, loader, null);
    }

    /**
     * Gets the root method of this compilation unit.
     */
    public Method getMethod() {
        return method;
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
     * Loads the {@link OptimizationTree} and the {@link InliningTree} of this compilation units.
     *
     * @return a pair of the loaded trees
     * @throws ExperimentParserError the trees could not be loaded
     */
    public TreePair loadTrees() throws ExperimentParserError {
        return loader.load();
    }

    /**
     * Gets the key of the compiled multi-method or {@code null} if this is not a compilation of a
     * multi-method.
     */
    public String getMultiMethodKey() {
        return multiMethodKey;
    }

    /**
     * Returns the kind of this compilation (whether it is a compilation unit or a fragment).
     */
    protected String getCompilationKind() {
        return "unit";
    }

    /**
     * Formats a header identifying this compilation unit.
     *
     * Includes the {@link #getCompilationKind() kind}, {@link #getMultiMethodKey() multi-method
     * key}, {@link #createExecutionSummary() exeuction summary}, and {@link ExperimentId}. For
     * example:
     *
     * <pre>
     * Compilation unit 15614 consumed 27.77% of Graal execution,  1.37% of total in experiment 1
     * </pre>
     *
     * @return a header identifying this compilation unit
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Compilation ").append(getCompilationKind()).append(' ').append(String.format("%5s", getCompilationId()));
        if (multiMethodKey != null) {
            sb.append(" of multi-method ").append(multiMethodKey);
        }
        if (method.getExperiment().isProfileAvailable()) {
            sb.append(" consumed ").append(createExecutionSummary());
        }
        sb.append(" in experiment ").append(method.getExperiment().getExperimentId());
        return sb.toString();
    }

    /**
     * Creates and returns a summary that specifies the execution time of this compilation unit
     * relative to the total execution time and relative to the execution time of all graal-compiled
     * methods. This is only possible when proftool data is {@link Experiment#isProfileAvailable()
     * available} to the experiment.
     *
     * Example of a returned string:
     *
     * <pre>
     * 10.05% of Graal execution,  4.71% of total
     * </pre>
     *
     * @return a summary of the relative execution time
     */
    public String createExecutionSummary() {
        assert method.getExperiment().isProfileAvailable();
        double graalPercent = (double) period / method.getExperiment().getGraalPeriod() * 100;
        double totalPercent = (double) period / method.getExperiment().getTotalPeriod() * 100;
        return String.format("%5.2f%% of Graal execution, %5.2f%% of total", graalPercent, totalPercent);
    }

    /**
     * Writes the {@link #toString() header} of this compilation unit and either the
     * optimization-context tree or the optimization and inlining tree to the destination writer.
     *
     * @param writer the destination writer
     */
    public void write(Writer writer) throws ExperimentParserError {
        writer.writeln(toString());
        writer.increaseIndent();
        TreePair treePair = loader.load();
        treePair.getInliningTree().preprocess(writer.getOptionValues());
        treePair.getOptimizationTree().preprocess(writer.getOptionValues());
        if (writer.getOptionValues().isOptimizationContextTreeEnabled()) {
            OptimizationContextTree.createFrom(treePair.getInliningTree(), treePair.getOptimizationTree()).write(writer);
        } else {
            treePair.getInliningTree().write(writer);
            treePair.getOptimizationTree().write(writer);
        }
        writer.decreaseIndent();
    }
}
