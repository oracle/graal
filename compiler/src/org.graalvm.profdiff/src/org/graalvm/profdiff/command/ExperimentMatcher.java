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
import org.graalvm.profdiff.core.OptimizationContextTree;
import org.graalvm.profdiff.core.OptimizationContextTreeNode;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.core.optimization.OptimizationTreeNode;
import org.graalvm.profdiff.core.pair.CompilationUnitPair;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.core.pair.MethodPair;
import org.graalvm.profdiff.diff.DeltaTree;
import org.graalvm.profdiff.diff.DeltaTreeWriterVisitor;
import org.graalvm.profdiff.diff.EditScript;
import org.graalvm.profdiff.diff.InliningDeltaTreeWriterVisitor;
import org.graalvm.profdiff.diff.InliningTreeEditPolicy;
import org.graalvm.profdiff.diff.OptimizationContextTreeEditPolicy;
import org.graalvm.profdiff.diff.OptimizationContextTreeWriterVisitor;
import org.graalvm.profdiff.diff.OptimizationTreeEditPolicy;
import org.graalvm.profdiff.diff.SelkowTreeMatcher;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.graalvm.profdiff.core.Writer;

/**
 * Matches and compares the methods and compilations of two experiments, writing the results
 * according to the given {@link org.graalvm.profdiff.core.OptionValues} to a {@link Writer}.
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

    private final SelkowTreeMatcher<OptimizationContextTreeNode> optimizationContextTreeMatcher = new SelkowTreeMatcher<>(new OptimizationContextTreeEditPolicy());

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
     * It is assumed that the experiments have their hot methods already marked. Compilation
     * fragments are created if they are enabled in the
     * {@link org.graalvm.profdiff.core.OptionValues}. Each method present in at least one of the
     * experiments is listed. Hot compilations of the method are paired. Their
     * optimization/inlining/optimization-context trees are either printed or diffed, depending on
     * the option values.
     *
     * @param experimentPair the pair of experiments to be matched
     */
    public void match(ExperimentPair experimentPair) throws ExperimentParserError {
        if (writer.getOptionValues().shouldCreateFragments()) {
            experimentPair.createCompilationFragments();
        }
        for (MethodPair methodPair : experimentPair.getHotMethodPairsByDescendingPeriod()) {
            writer.writeln();
            methodPair.writeHeaderAndCompilationList(writer);
            writer.increaseIndent();
            if (writer.getOptionValues().shouldDiffCompilations()) {
                for (CompilationUnitPair compilationUnitPair : methodPair.getHotCompilationUnitPairsByDescendingPeriod()) {
                    writer.writeln(compilationUnitPair.formatHeaderForHotCompilations());
                    writer.increaseIndent();
                    if (compilationUnitPair.bothHot()) {
                        CompilationUnit.TreePair treePair1 = compilationUnitPair.getCompilationUnit1().loadTrees();
                        CompilationUnit.TreePair treePair2 = compilationUnitPair.getCompilationUnit2().loadTrees();
                        if (writer.getOptionValues().isOptimizationContextTreeEnabled()) {
                            createOptimizationContextTreeAndMatch(treePair1, treePair2);
                        } else {
                            matchInliningTrees(treePair1.getInliningTree(), treePair2.getInliningTree());
                            matchOptimizationTrees(treePair1.getOptimizationTree(), treePair2.getOptimizationTree());
                        }
                    } else if (!writer.getOptionValues().shouldPruneIdentities()) {
                        compilationUnitPair.firstNonNull().write(writer);
                    }
                    writer.decreaseIndent();
                }
            } else {
                for (CompilationUnit compilationUnit : methodPair.getMethod1().getCompilationUnits()) {
                    compilationUnit.write(writer);
                }
                for (CompilationUnit compilationUnit : methodPair.getMethod2().getCompilationUnits()) {
                    compilationUnit.write(writer);
                }
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
        writer.writeln("Inlining tree matching");
        inliningTree1.preprocess(writer.getOptionValues());
        inliningTree2.preprocess(writer.getOptionValues());
        EditScript<InliningTreeNode> inliningTreeMatching = inliningTreeMatcher.match(inliningTreeRoot1, inliningTreeRoot2);
        DeltaTree<InliningTreeNode> inliningDeltaTree = DeltaTree.fromEditScript(inliningTreeMatching);
        if (writer.getOptionValues().shouldPruneIdentities()) {
            inliningDeltaTree.pruneIdentities();
        }
        inliningDeltaTree.expand();
        writer.increaseIndent();
        InliningDeltaTreeWriterVisitor inliningDeltaTreeWriter = new InliningDeltaTreeWriterVisitor(writer);
        inliningDeltaTree.accept(inliningDeltaTreeWriter);
        writer.decreaseIndent();
    }

    /**
     * Matches the optimization trees of the compilation units and writes the result.
     */
    private void matchOptimizationTrees(OptimizationTree optimizationTree1, OptimizationTree optimizationTree2) {
        writer.writeln("Optimization tree matching");
        optimizationTree1.preprocess(writer.getOptionValues());
        optimizationTree2.preprocess(writer.getOptionValues());
        EditScript<OptimizationTreeNode> optimizationTreeMatching = optimizationTreeMatcher.match(optimizationTree1.getRoot(), optimizationTree2.getRoot());
        DeltaTree<OptimizationTreeNode> optimizationDeltaTree = DeltaTree.fromEditScript(optimizationTreeMatching);
        if (writer.getOptionValues().shouldPruneIdentities()) {
            optimizationDeltaTree.pruneIdentities();
        }
        optimizationDeltaTree.expand();
        writer.increaseIndent();
        DeltaTreeWriterVisitor<OptimizationTreeNode> optimizationDeltaTreeWriter = new DeltaTreeWriterVisitor<>(writer);
        optimizationDeltaTree.accept(optimizationDeltaTreeWriter);
        writer.decreaseIndent();
    }

    /**
     * Creates an optimization-context tree for 2 tree pairs, matches them, and prints out the
     * results.
     *
     * @param treePair1 the tree pair from the first experiment
     * @param treePair2 the tree pair from the second experiment
     */
    private void createOptimizationContextTreeAndMatch(CompilationUnit.TreePair treePair1, CompilationUnit.TreePair treePair2) {
        treePair1.getInliningTree().preprocess(writer.getOptionValues());
        treePair2.getInliningTree().preprocess(writer.getOptionValues());
        treePair1.getOptimizationTree().preprocess(writer.getOptionValues());
        treePair2.getOptimizationTree().preprocess(writer.getOptionValues());
        OptimizationContextTree optimizationContextTree1 = OptimizationContextTree.createFrom(treePair1.getInliningTree(), treePair1.getOptimizationTree());
        OptimizationContextTree optimizationContextTree2 = OptimizationContextTree.createFrom(treePair2.getInliningTree(), treePair2.getOptimizationTree());
        EditScript<OptimizationContextTreeNode> optimizationContextTreeMatching = optimizationContextTreeMatcher.match(optimizationContextTree1.getRoot(), optimizationContextTree2.getRoot());
        DeltaTree<OptimizationContextTreeNode> deltaTree = DeltaTree.fromEditScript(optimizationContextTreeMatching);
        if (writer.getOptionValues().shouldPruneIdentities()) {
            deltaTree.pruneIdentities();
        }
        deltaTree.expand();
        OptimizationContextTreeWriterVisitor optimizationContextTreeWriterVisitor = new OptimizationContextTreeWriterVisitor(writer);
        deltaTree.accept(optimizationContextTreeWriterVisitor);
    }
}
