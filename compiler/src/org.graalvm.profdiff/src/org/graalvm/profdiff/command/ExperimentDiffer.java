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
import org.graalvm.profdiff.matching.method.GreedyMethodMatcher;
import org.graalvm.profdiff.matching.method.MatchedCompilationUnit;
import org.graalvm.profdiff.matching.method.MatchedMethod;
import org.graalvm.profdiff.matching.method.MethodMatcher;
import org.graalvm.profdiff.matching.method.MethodMatching;
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
public class ExperimentDiffer {
    /**
     * Matches methods and compilation units of two experiments.
     */
    private final MethodMatcher matcher = new GreedyMethodMatcher();

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

    public ExperimentDiffer(Writer writer) {
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
     * @param experiment1 the first experiment to be diffed
     * @param experiment2 the second experiment to be diffed
     */
    public void diff(Experiment experiment1, Experiment experiment2) {
        VerbosityLevel verbosityLevel = writer.getVerbosityLevel();

        for (Experiment experiment : List.of(experiment1, experiment2)) {
            experiment.preprocessCompilationUnits(verbosityLevel);
            experiment.writeExperimentSummary(writer);
            writer.writeln();
        }

        MethodMatching matching = matcher.match(experiment1, experiment2);

        for (MatchedMethod matchedMethod : matching.getMatchedMethods()) {
            writer.writeln();
            matchedMethod.writeHeaderAndCompilationUnits(writer, experiment1, experiment2);
            writer.increaseIndent();
            if (verbosityLevel.shouldPrintOptimizationTree()) {
                matchedMethod.getFirstHotCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
                matchedMethod.getSecondHotCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
            }
            if (verbosityLevel.shouldDiffCompilations()) {
                for (MatchedCompilationUnit matchedCompilationUnit : matchedMethod.getMatchedCompilationUnits()) {
                    matchedCompilationUnit.writeHeader(writer);
                    writer.increaseIndent();
                    InliningTreeNode inliningTreeRoot1 = matchedCompilationUnit.getFirstCompilationUnit().getInliningTreeRoot();
                    InliningTreeNode inliningTreeRoot2 = matchedCompilationUnit.getSecondCompilationUnit().getInliningTreeRoot();
                    if (inliningTreeRoot1 != null && inliningTreeRoot2 != null) {
                        writer.writeln("Inlining tree matching");
                        EditScript<InliningTreeNode> inliningTreeMatching = inliningTreeMatcher.match(inliningTreeRoot1, inliningTreeRoot2);
                        DeltaTree<InliningTreeNode> inliningDeltaTree = DeltaTree.fromEditScript(inliningTreeMatching);
                        if (verbosityLevel.shouldShowOnlyDiff()) {
                            inliningDeltaTree.pruneIdentities();
                        }
                        InliningDeltaTreeWriterVisitor inliningDeltaTreeWriter = new InliningDeltaTreeWriterVisitor(writer);
                        inliningDeltaTree.accept(inliningDeltaTreeWriter);
                    } else {
                        writer.writeln("Inlining trees are not available");
                    }
                    writer.writeln("Optimization tree matching");
                    EditScript<OptimizationTreeNode> optimizationTreeMatching = optimizationTreeMatcher.match(
                                    matchedCompilationUnit.getFirstCompilationUnit().getRootPhase(),
                                    matchedCompilationUnit.getSecondCompilationUnit().getRootPhase());
                    if (verbosityLevel.shouldShowOnlyDiff()) {
                        DeltaTree<OptimizationTreeNode> optimizationDeltaTree = DeltaTree.fromEditScript(optimizationTreeMatching);
                        optimizationDeltaTree.pruneIdentities();
                        optimizationTreeMatching = optimizationDeltaTree.asEditScript();
                    }
                    optimizationTreeMatching.write(writer);
                    writer.decreaseIndent();
                }
                if (!verbosityLevel.shouldShowOnlyDiff()) {
                    matchedMethod.getUnmatchedCompilationUnits().forEach(compilationUnit -> compilationUnit.write(writer));
                }
            }
            writer.decreaseIndent();
        }
        matching.getUnmatchedMethods().forEach(unmatchedMethod -> {
            writer.writeln();
            unmatchedMethod.write(writer, experiment1, experiment2);
        });
    }
}
