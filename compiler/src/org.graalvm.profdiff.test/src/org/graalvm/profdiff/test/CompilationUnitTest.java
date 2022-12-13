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
package org.graalvm.profdiff.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.core.optimization.OptimizationTreeNode;
import org.junit.Assert;
import org.junit.Test;

public class CompilationUnitTest {
    private static class MockCompilationUnit {
        public final InliningTree inliningTree;

        public final OptimizationTree optimizationTree;

        /**
         * The expected preorder of the compilation units's optimization tree after
         * {@link org.graalvm.profdiff.core.optimization.OptimizationTree#sortUnorderedPhases()}.
         */
        public final List<OptimizationTreeNode> optimizationTreePreorderAfterSort;

        /**
         * The expected preorder of the compilation units's optimization tree after
         * {@link org.graalvm.profdiff.core.optimization.OptimizationTree#removeVeryDetailedPhases()}.
         */
        public final List<OptimizationTreeNode> optimizationTreePreorderAfterRemoval;

        /**
         * The expected preorder of the compilation units's inlining tree after
         * {@link org.graalvm.profdiff.core.inlining.InliningTree#sortInliningTree()}.
         */
        public final List<InliningTreeNode> inliningTreeNodePreorderAfterSort;

        MockCompilationUnit() {
            OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
            OptimizationPhase controlPhase = new OptimizationPhase("ControlPhase$");
            assert !controlPhase.isUnorderedCategory() && !controlPhase.isVeryDetailedCategory();
            rootPhase.addChild(controlPhase);
            OptimizationPhase controlPhaseChild1 = new OptimizationPhase("ControlPhaseChild1$");
            OptimizationPhase controlPhaseChild2 = new OptimizationPhase("ControlPhaseChild2$");
            controlPhase.addChild(controlPhaseChild2);
            controlPhase.addChild(controlPhaseChild1);
            OptimizationPhase unorderedDetailedPhase = new OptimizationPhase("CanonicalizerPhase");
            assert unorderedDetailedPhase.isUnorderedCategory() && unorderedDetailedPhase.isVeryDetailedCategory();
            rootPhase.addChild(unorderedDetailedPhase);
            Optimization optimization1 = new Optimization("Optimization1", "Event1", null, null);
            Optimization optimization2 = new Optimization("Optimization1", "Event1", EconomicMap.of("method", 1), null);
            Optimization optimization3 = new Optimization("Optimization1", "Event1", EconomicMap.of("method", 1), EconomicMap.of("key", 0));
            Optimization optimization4 = new Optimization("Optimization1", "Event1", EconomicMap.of("method", 2), null);
            Optimization optimization5 = new Optimization("Optimization1", "Event2", null, null);
            Optimization optimization6 = new Optimization("Optimization2", "Event1", null, null);
            OptimizationPhase subphase1 = new OptimizationPhase("Subphase1$");
            OptimizationPhase subphase2 = new OptimizationPhase("Subphase2$");
            unorderedDetailedPhase.addChild(optimization4);
            unorderedDetailedPhase.addChild(subphase1);
            unorderedDetailedPhase.addChild(optimization2);
            unorderedDetailedPhase.addChild(optimization6);
            unorderedDetailedPhase.addChild(optimization3);
            unorderedDetailedPhase.addChild(subphase2);
            unorderedDetailedPhase.addChild(optimization5);
            unorderedDetailedPhase.addChild(optimization1);

            InliningTreeNode inliningTreeRoot = new InliningTreeNode("root", 0, true, null);
            InliningTreeNode method1 = new InliningTreeNode("method1", 1, true, null);
            InliningTreeNode method2 = new InliningTreeNode("method1", 2, true, null);
            InliningTreeNode method3 = new InliningTreeNode("method2", 2, true, null);
            InliningTreeNode method3First = new InliningTreeNode("method", 1, true, null);
            InliningTreeNode method3Second = new InliningTreeNode("method", 2, true, null);
            method3.addChild(method3Second);
            method3.addChild(method3First);
            InliningTreeNode method4 = new InliningTreeNode("method1", 3, true, null);
            InliningTreeNode method5 = new InliningTreeNode("method2", 3, false, null);
            InliningTreeNode method6 = new InliningTreeNode("method2", 3, true, null);
            inliningTreeRoot.addChild(method4);
            inliningTreeRoot.addChild(method6);
            inliningTreeRoot.addChild(method1);
            inliningTreeRoot.addChild(method5);
            inliningTreeRoot.addChild(method3);
            inliningTreeRoot.addChild(method2);

            inliningTree = new InliningTree(inliningTreeRoot);
            optimizationTree = new OptimizationTree(rootPhase);
            optimizationTreePreorderAfterSort = List.of(rootPhase, controlPhase, controlPhaseChild2, controlPhaseChild1,
                            unorderedDetailedPhase, optimization1, optimization2, optimization3, optimization4, optimization5,
                            optimization6, subphase1, subphase2);
            optimizationTreePreorderAfterRemoval = List.of(rootPhase, controlPhase, controlPhaseChild2, controlPhaseChild1);
            inliningTreeNodePreorderAfterSort = List.of(inliningTreeRoot, method1, method2, method3, method3First, method3Second,
                            method4, method5, method6);
        }
    }

    private static <T extends TreeNode<T>> List<T> treeInPreorder(T root) {
        List<T> preorder = new ArrayList<>();
        root.forEach(preorder::add);
        return preorder;
    }

    @Test
    public void removeVeryDetailedPhases() {
        MockCompilationUnit mockCompilationUnit = new MockCompilationUnit();
        mockCompilationUnit.optimizationTree.removeVeryDetailedPhases();
        List<OptimizationTreeNode> actualPreorder = treeInPreorder(mockCompilationUnit.optimizationTree.getRoot());
        Assert.assertEquals(mockCompilationUnit.optimizationTreePreorderAfterRemoval, actualPreorder);
    }

    @Test
    public void sortUnorderedPhases() {
        MockCompilationUnit mockCompilationUnit = new MockCompilationUnit();
        mockCompilationUnit.optimizationTree.sortUnorderedPhases();
        List<OptimizationTreeNode> actualPreorder = treeInPreorder(mockCompilationUnit.optimizationTree.getRoot());
        Assert.assertEquals(mockCompilationUnit.optimizationTreePreorderAfterSort, actualPreorder);
    }

    @Test
    public void sortInliningTree() {
        MockCompilationUnit mockCompilationUnit = new MockCompilationUnit();
        mockCompilationUnit.inliningTree.sortInliningTree();
        List<InliningTreeNode> actualPreorder = treeInPreorder(mockCompilationUnit.inliningTree.getRoot());
        Assert.assertEquals(mockCompilationUnit.inliningTreeNodePreorderAfterSort, actualPreorder);
    }
}
