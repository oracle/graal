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

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
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
     * It is assumed that the experiments have their hot methods already marked. Each method present
     * in at least one of the experiments is listed. Hot compilation units of the method are paired.
     * Their optimization/inlining trees are either printed or diffed, depending on the verbosity
     * level. Unmatched compilation units are also listed, possibly including their
     * optimization/inlining trees.
     *
     * @param experimentPair the pair of experiments to be matched
     */
    public void match(ExperimentPair experimentPair) throws Exception {
        VerbosityLevel verbosityLevel = writer.getVerbosityLevel();
        for (MethodPair methodPair : experimentPair.getHotMethodPairsByDescendingPeriod()) {
            writer.writeln();
            methodPair.writeHeaderAndCompilationList(writer);
            writer.increaseIndent();
            if (verbosityLevel.shouldPrintOptimizationTree()) {
                for (CompilationUnit compilationUnit : methodPair.getMethod1().getCompilationUnits()) {
                    compilationUnit.write(writer);
                }
                for (CompilationUnit compilationUnit : methodPair.getMethod2().getCompilationUnits()) {
                    compilationUnit.write(writer);
                }
            }
            for (CompilationUnitPair compilationUnitPair : methodPair.getHotCompilationUnitPairsByDescendingPeriod()) {
                writer.writeln(compilationUnitPair.formatHeaderForHotCompilations());
                writer.increaseIndent();
                if (compilationUnitPair.bothHot()) {
                    CompilationUnit.TreePair treePair1 = compilationUnitPair.getCompilationUnit1().loadTrees();
                    CompilationUnit.TreePair treePair2 = compilationUnitPair.getCompilationUnit2().loadTrees();
                    matchInliningTrees(treePair1.getInliningTree(), treePair2.getInliningTree());
                    matchOptimizationTrees(treePair1.getOptimizationTree(), treePair2.getOptimizationTree());
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
     */
    private void matchInliningTrees(InliningTree inliningTree1, InliningTree inliningTree2) {
        InliningTreeNode inliningTreeRoot1 = inliningTree1.getRoot();
        InliningTreeNode inliningTreeRoot2 = inliningTree2.getRoot();
        if (inliningTreeRoot1 != null && inliningTreeRoot2 != null) {
            writer.writeln("Inlining tree matching");
            inliningTree1.preprocess(writer.getVerbosityLevel());
            inliningTree2.preprocess(writer.getVerbosityLevel());
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
     */
    private void matchOptimizationTrees(OptimizationTree optimizationTree1, OptimizationTree optimizationTree2) {
        writer.writeln("Optimization tree matching");
        optimizationTree1.preprocess(writer.getVerbosityLevel());
        optimizationTree2.preprocess(writer.getVerbosityLevel());
        EditScript<OptimizationTreeNode> optimizationTreeMatching = optimizationTreeMatcher.match(optimizationTree1.getRoot(), optimizationTree2.getRoot());
        if (writer.getVerbosityLevel().shouldShowOnlyDiff()) {
            DeltaTree<OptimizationTreeNode> optimizationDeltaTree = DeltaTree.fromEditScript(optimizationTreeMatching);
            optimizationDeltaTree.pruneIdentities();
            optimizationTreeMatching = optimizationDeltaTree.asEditScript();
        }
        optimizationTreeMatching.write(writer);
    }
}
