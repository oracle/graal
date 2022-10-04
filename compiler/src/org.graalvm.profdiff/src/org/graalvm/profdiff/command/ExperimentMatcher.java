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
package org.graalvm.profdiff.command;

import java.util.List;

import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.InliningTreeNode;
import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.core.optimization.OptimizationTreeNode;
import org.graalvm.profdiff.core.pair.CompilationUnitPair;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.core.pair.MethodPair;
import org.graalvm.profdiff.matching.tree.DeltaTree;
import org.graalvm.profdiff.matching.tree.EditScript;
import org.graalvm.profdiff.matching.tree.InliningDeltaTreeWriterVisitor;
import org.graalvm.profdiff.matching.tree.InliningTreeEditPolicy;
import org.graalvm.profdiff.matching.tree.OptimizationTreeEditPolicy;
import org.graalvm.profdiff.matching.tree.SelkowTreeMatcher;
import org.graalvm.profdiff.util.Writer;

/**
 * Matches and compares the methods and compilations of two experiments, writing the results
 * according to the given verbosity level to a {@link Writer}.
 */
public class ExperimentMatcher {
    /**
     * Matches optimization trees of two compilation units.
     */
    private final SelkowTreeMatcher<OptimizationTreeNode> optimizationTreeMatcher = new SelkowTreeMatcher<>(new OptimizationTreeEditPolicy());

    /**
     * Matches inlining trees of two compilation units.
     */
    private final SelkowTreeMatcher<InliningTreeNode> inliningTreeMatcher = new SelkowTreeMatcher<>(new InliningTreeEditPolicy());

    /**
     * The destination writer of the output.
     */
    private final Writer writer;

    public ExperimentMatcher(Writer writer) {
        this.writer = writer;
    }

    /**
     * Matches and compares the provided experiments, writing out the results.
     *
     * It is assumed that the experiments have already their hot methods marked. The experiments are
     * {@link Experiment#preprocessCompilationUnits(VerbosityLevel) preprocessed} first and their
     * {@link Experiment#writeExperimentSummary(Writer) summaries} are written.
     *
     * After that, the methods and hot compilation units of the experiments are matched. Each
     * matched method pair is printed out and its hot compilation units are either printed or their
     * optimization/inlining trees diffed, depending on the verbosity level. Unmatched methods are
     * similarly listed, possibly including the optimization/inlining trees of their hot compilation
     * units.
     *
     * @param experimentPair the pair of experiments to be matched
     */
    public void match(ExperimentPair experimentPair) {
        VerbosityLevel verbosityLevel = writer.getVerbosityLevel();

        for (Experiment experiment : List.of(experimentPair.getExperiment1(), experimentPair.getExperiment2())) {
            experiment.preprocessCompilationUnits(verbosityLevel);
            experiment.writeExperimentSummary(writer);
            writer.writeln();
        }

        ExplanationWriter explanationWriter = new ExplanationWriter(writer);
        explanationWriter.explain();

        for (MethodPair methodPair : experimentPair.getHotMethodPairsByDescendingPeriod()) {
            writer.writeln();
            methodPair.writeHeaderAndCompilationList(writer);
            writer.increaseIndent();
            if (verbosityLevel.shouldPrintOptimizationTree()) {
                methodPair.getMethod1().getCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
                methodPair.getMethod2().getCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
            }
            for (CompilationUnitPair compilationUnitPair : methodPair.getHotCompilationUnitPairsByDescendingPeriod()) {
                writer.writeln(compilationUnitPair.formatHeaderForHotCompilations());
                writer.increaseIndent();
                if (compilationUnitPair.bothHot()) {
                    matchInliningTrees(compilationUnitPair);
                    matchOptimizationTrees(compilationUnitPair);
                } else if (!verbosityLevel.shouldShowOnlyDiff()) {
                    compilationUnitPair.firstNonNull().write(writer);
                }
                writer.decreaseIndent();
            }
            writer.decreaseIndent();
        }
    }

    /**
     * Matches the inlining trees of the compilation units and writes the result.
     *
     * @param compilationUnitPair the pair of compilations to be matched
     */
    private void matchInliningTrees(CompilationUnitPair compilationUnitPair) {
        InliningTreeNode inliningTreeRoot1 = compilationUnitPair.getCompilationUnit1().getInliningTreeRoot();
        InliningTreeNode inliningTreeRoot2 = compilationUnitPair.getCompilationUnit2().getInliningTreeRoot();
        if (inliningTreeRoot1 != null && inliningTreeRoot2 != null) {
            writer.writeln("Inlining tree matching");
            EditScript<InliningTreeNode> inliningTreeMatching = inliningTreeMatcher.match(inliningTreeRoot1, inliningTreeRoot2);
            DeltaTree<InliningTreeNode> inliningDeltaTree = DeltaTree.fromEditScript(inliningTreeMatching);
            if (writer.getVerbosityLevel().shouldShowOnlyDiff()) {
                inliningDeltaTree.pruneIdentities();
            }
            InliningDeltaTreeWriterVisitor inliningDeltaTreeWriter = new InliningDeltaTreeWriterVisitor(writer);
            inliningDeltaTree.accept(inliningDeltaTreeWriter);
        } else {
            writer.writeln("Inlining trees are not available");
        }
    }

    /**
     * Matches the optimization trees of the compilation units and writes the result.
     *
     * @param compilationUnitPair the pair of compilations to be matched
     */
    private void matchOptimizationTrees(CompilationUnitPair compilationUnitPair) {
        writer.writeln("Optimization tree matching");
        EditScript<OptimizationTreeNode> optimizationTreeMatching = optimizationTreeMatcher.match(
                        compilationUnitPair.getCompilationUnit1().getRootPhase(),
                        compilationUnitPair.getCompilationUnit2().getRootPhase());
        if (writer.getVerbosityLevel().shouldShowOnlyDiff()) {
            DeltaTree<OptimizationTreeNode> optimizationDeltaTree = DeltaTree.fromEditScript(optimizationTreeMatching);
            optimizationDeltaTree.pruneIdentities();
            optimizationTreeMatching = optimizationDeltaTree.asEditScript();
        }
        optimizationTreeMatching.write(writer);
    }
}
