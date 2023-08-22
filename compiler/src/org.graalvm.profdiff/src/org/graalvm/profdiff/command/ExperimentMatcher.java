/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.profdiff.core.TreeNode;
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
import org.graalvm.profdiff.diff.TreeMatcher;
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
    private final TreeMatcher<OptimizationTreeNode> optimizationTreeMatcher = new TreeMatcher<>(new OptimizationTreeEditPolicy());

    /**
     * Matches inlining trees of two compilation units.
     */
    private final TreeMatcher<InliningTreeNode> inliningTreeMatcher = new TreeMatcher<>(new InliningTreeEditPolicy());

    private final TreeMatcher<OptimizationContextTreeNode> optimizationContextTreeMatcher = new TreeMatcher<>(new OptimizationContextTreeEditPolicy());

    /**
     * The destination writer of the output.
     */
    private final Writer writer;

    /**
     * If the product of the number of nodes of two trees exceeds the square of this threshold, the
     * trees cannot be compared.
     */
    private final long treeComparisonThreshold;

    public ExperimentMatcher(Writer writer) {
        this(writer, 10_000);
    }

    public ExperimentMatcher(Writer writer, long treeComparisonThreshold) {
        this.writer = writer;
        this.treeComparisonThreshold = treeComparisonThreshold;
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
        boolean first = true;
        for (MethodPair methodPair : experimentPair.getHotMethodPairsByDescendingPeriod()) {
            if (first) {
                first = false;
            } else {
                writer.writeln();
            }
            methodPair.writeHeaderAndCompilationList(writer);
            writer.increaseIndent();
            if (writer.getOptionValues().shouldDiffCompilations()) {
                for (CompilationUnitPair compilationUnitPair : methodPair.getHotCompilationUnitPairsByDescendingPeriod()) {
                    compilationUnitPair.writeHeaders(writer);
                    writer.increaseIndent();
                    CompilationUnit.TreePair treePair1 = compilationUnitPair.getCompilationUnit1().loadTrees();
                    CompilationUnit.TreePair treePair2 = compilationUnitPair.getCompilationUnit2().loadTrees();
                    if (writer.getOptionValues().isOptimizationContextTreeEnabled()) {
                        createOptimizationContextTreeAndMatch(treePair1, treePair2);
                    } else {
                        matchInliningTrees(treePair1.getInliningTree(), treePair2.getInliningTree());
                        matchOptimizationTrees(treePair1.getOptimizationTree(), treePair2.getOptimizationTree());
                    }
                    writer.decreaseIndent();
                }
            } else {
                for (CompilationUnit compilationUnit : methodPair.getMethod1().getHotCompilationUnits()) {
                    compilationUnit.write(writer);
                }
                for (CompilationUnit compilationUnit : methodPair.getMethod2().getHotCompilationUnits()) {
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
        inliningTree1.preprocess(writer.getOptionValues());
        inliningTree2.preprocess(writer.getOptionValues());
        InliningTreeNode root1 = inliningTree1.getRoot();
        InliningTreeNode root2 = inliningTree2.getRoot();
        if (tooBigToCompare(root1, root2)) {
            writer.writeln("The inlining trees are too big to compare");
            return;
        }
        writer.writeln("Inlining tree matching");
        EditScript<InliningTreeNode> inliningTreeMatching = inliningTreeMatcher.match(root1, root2);
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
        optimizationTree1.preprocess(writer.getOptionValues());
        optimizationTree2.preprocess(writer.getOptionValues());
        OptimizationTreeNode root1 = optimizationTree1.getRoot();
        OptimizationTreeNode root2 = optimizationTree2.getRoot();
        if (tooBigToCompare(root1, root2)) {
            writer.writeln("The optimization trees are too big to compare");
            return;
        }
        writer.writeln("Optimization tree matching");
        EditScript<OptimizationTreeNode> optimizationTreeMatching = optimizationTreeMatcher.match(root1, root2);
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
        OptimizationContextTreeNode root1 = optimizationContextTree1.getRoot();
        OptimizationContextTreeNode root2 = optimizationContextTree2.getRoot();
        if (tooBigToCompare(root1, root2)) {
            writer.writeln("The optimization-context trees are too big to compare");
            return;
        }
        EditScript<OptimizationContextTreeNode> optimizationContextTreeMatching = optimizationContextTreeMatcher.match(root1, root2);
        DeltaTree<OptimizationContextTreeNode> deltaTree = DeltaTree.fromEditScript(optimizationContextTreeMatching);
        if (writer.getOptionValues().shouldPruneIdentities()) {
            deltaTree.pruneIdentities();
        }
        deltaTree.expand();
        OptimizationContextTreeWriterVisitor optimizationContextTreeWriterVisitor = new OptimizationContextTreeWriterVisitor(writer);
        deltaTree.accept(optimizationContextTreeWriterVisitor);
    }

    /**
     * Returns whether the product of the sizes of the given trees exceeds the comparison threshold.
     *
     * @param root1 the root of the first tree
     * @param root2 the root of the second tree
     * @return {@code true} if the trees are too big to be compared
     */
    private <T extends TreeNode<T>> boolean tooBigToCompare(TreeNode<T> root1, TreeNode<T> root2) {
        long[] size = new long[2];
        root1.forEach((node) -> ++size[0]);
        root2.forEach((node) -> ++size[1]);
        return size[0] * size[1] > treeComparisonThreshold * treeComparisonThreshold;
    }
}
