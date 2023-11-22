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
package org.graalvm.profdiff.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.core.optimization.OptimizationTreeNode;
import org.graalvm.profdiff.core.optimization.Position;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class OptimizationTreeTest {
    private static final class MockCompilationUnit {

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

        private MockCompilationUnit() {
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
            Optimization optimization2 = new Optimization("Optimization1", "Event1", Position.of("method", 1), null);
            Optimization optimization3 = new Optimization("Optimization1", "Event1", Position.of("method", 1), EconomicMap.of("key", 0));
            Optimization optimization4 = new Optimization("Optimization1", "Event1", Position.of("method", 2), null);
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

            InliningTreeNode inliningTreeRoot = new InliningTreeNode("root", 0, true, null, false, null, false);
            InliningTreeNode method1 = new InliningTreeNode("method1", 1, true, null, false, null, false);
            InliningTreeNode method2 = new InliningTreeNode("method1", 2, true, null, false, null, false);
            InliningTreeNode method3 = new InliningTreeNode("method2", 2, true, null, false, null, false);
            InliningTreeNode method3First = new InliningTreeNode("method", 1, true, null, false, null, false);
            InliningTreeNode method3Second = new InliningTreeNode("method", 2, true, null, false, null, false);
            method3.addChild(method3Second);
            method3.addChild(method3First);
            InliningTreeNode method4 = new InliningTreeNode("method1", 3, true, null, false, null, false);
            InliningTreeNode method5 = new InliningTreeNode("method2", 3, false, null, false, null, true);
            InliningTreeNode method6 = new InliningTreeNode("method2", 3, true, null, false, null, false);
            inliningTreeRoot.addChild(method4);
            inliningTreeRoot.addChild(method6);
            inliningTreeRoot.addChild(method1);
            inliningTreeRoot.addChild(method5);
            inliningTreeRoot.addChild(method3);
            inliningTreeRoot.addChild(method2);

            optimizationTree = new OptimizationTree(rootPhase);
            optimizationTreePreorderAfterSort = List.of(rootPhase, controlPhase, controlPhaseChild2, controlPhaseChild1,
                            unorderedDetailedPhase, optimization1, optimization2, optimization3, optimization4, optimization5,
                            optimization6, subphase1, subphase2);
            optimizationTreePreorderAfterRemoval = List.of(rootPhase, controlPhase, controlPhaseChild2, controlPhaseChild1);
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
        assertEquals(mockCompilationUnit.optimizationTreePreorderAfterRemoval, actualPreorder);
    }

    @Test
    public void sortUnorderedPhases() {
        MockCompilationUnit mockCompilationUnit = new MockCompilationUnit();
        mockCompilationUnit.optimizationTree.sortUnorderedPhases();
        List<OptimizationTreeNode> actualPreorder = treeInPreorder(mockCompilationUnit.optimizationTree.getRoot());
        assertEquals(mockCompilationUnit.optimizationTreePreorderAfterSort, actualPreorder);
    }

    @Test
    public void recursiveOptimizationList() {
        OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
        OptimizationPhase phaseA = new OptimizationPhase("A");
        OptimizationPhase phaseB = new OptimizationPhase("B");
        OptimizationPhase phaseC = new OptimizationPhase("C");
        OptimizationPhase phaseD = new OptimizationPhase("D");
        Optimization optimization1 = new Optimization("foo", "1", null, null);
        Optimization optimization2 = new Optimization("foo", "2", null, null);
        Optimization optimization3 = new Optimization("foo", "3", null, null);
        Optimization optimization4 = new Optimization("foo", "4", null, null);
        Optimization optimization5 = new Optimization("foo", "5", null, null);

        rootPhase.addChild(optimization1);
        rootPhase.addChild(phaseA);
        phaseA.addChild(optimization2);
        rootPhase.addChild(optimization3);
        rootPhase.addChild(phaseB);
        phaseB.addChild(phaseC);
        phaseC.addChild(optimization4);
        phaseC.addChild(optimization5);
        phaseB.addChild(phaseD);

        List<Optimization> expected = List.of(optimization1, optimization2, optimization3, optimization4, optimization5);
        assertEquals(expected, rootPhase.getOptimizationsRecursive());
    }

    @Test
    public void optimizationEqualsAndHashCode() {
        Optimization opt1 = new Optimization("Opt", "Event", null, EconomicMap.create());
        assertEquals(opt1, opt1);
        assertNotEquals(opt1, null);
        Optimization opt2 = new Optimization("Opt", "Event", Position.EMPTY, null);
        assertEquals(opt1, opt2);
        assertEquals(opt1.hashCode(), opt1.hashCode());
        Optimization opt3 = new Optimization("Opt", "Event", null, EconomicMap.of("prop", 1));
        assertNotEquals(opt1, opt3);
    }

    @Test
    public void writeOptimizationTree() {
        OptimizationPhase root = new OptimizationPhase("RootPhase");
        OptimizationPhase child = new OptimizationPhase("ChildPhase");
        root.addChild(child);
        child.addChild(new Optimization("Opt", "Foo", Position.create(List.of("foo()", "bar()"), List.of(1, 2)), null));
        root.addChild(new Optimization("Opt", "Bar", Position.create(List.of("baz()", "bar()"), List.of(3, 4)), EconomicMap.of("prop1", 1, "prop2", "value")));
        root.addChild(new Optimization("Opt", "Baz", null, EconomicMap.of("prop1", null, "prop2", false)));

        OptimizationTree tree = new OptimizationTree(root);

        var writer = Writer.stringBuilder(new OptionValues());
        tree.write(writer);
        assertEquals("""
                        Optimization tree
                            RootPhase
                                ChildPhase
                                    Opt Foo at bci 2
                                Opt Bar at bci 4 with {prop1: 1, prop2: value}
                                Opt Baz with {prop1: null, prop2: false}
                        """, writer.getOutput());

        writer = Writer.stringBuilder(OptionValues.builder().withBCILongForm(true).build());
        tree.write(writer);
        assertEquals("""
                        Optimization tree
                            RootPhase
                                ChildPhase
                                    Opt Foo at bci {foo(): 1, bar(): 2}
                                Opt Bar at bci {baz(): 3, bar(): 4} with {prop1: 1, prop2: value}
                                Opt Baz with {prop1: null, prop2: false}
                        """, writer.getOutput());
    }
}
