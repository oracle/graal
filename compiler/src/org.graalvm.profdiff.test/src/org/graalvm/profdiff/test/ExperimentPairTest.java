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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.profdiff.core.CompilationFragment;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.pair.CompilationUnitPair;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.core.pair.MethodPair;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.junit.Test;

public class ExperimentPairTest {
    private static <T> List<T> asList(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        for (T elem : iterable) {
            list.add(elem);
        }
        return list;
    }

    @Test
    public void methodMatching() {
        Experiment experiment1 = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        CompilationUnit bar1 = experiment1.addCompilationUnit("bar", "bar1", 0, null);
        experiment1.addCompilationUnit("foo", "foo1", 1, null);
        CompilationUnit foo2 = experiment1.addCompilationUnit("foo", "foo2", 2, null);
        CompilationUnit foo3 = experiment1.addCompilationUnit("foo", "foo3", 3, null);

        Experiment experiment2 = new Experiment(ExperimentId.TWO, Experiment.CompilationKind.JIT);
        CompilationUnit foo4 = experiment2.addCompilationUnit("foo", "foo4", 0, null);
        CompilationUnit bar2 = experiment2.addCompilationUnit("bar", "bar2", 10, null);
        CompilationUnit baz1 = experiment2.addCompilationUnit("baz", "baz1", 20, null);

        ExperimentPair pair = new ExperimentPair(experiment1, experiment2);
        List<MethodPair> methodPairs = asList(pair.getHotMethodPairsByDescendingPeriod());

        // only hot methods should be considered, therefore we expect zero pairs
        assertEquals(0, methodPairs.size());

        foo2.setHot(true);
        foo3.setHot(true);
        foo4.setHot(true);
        bar1.setHot(true);
        bar2.setHot(true);
        baz1.setHot(true);

        // expected result:
        // baz: baz1 unpaired
        // bar: bar1 matched with bar2
        // foo: foo3 matched with foo4, foo2 matched with foo 4
        methodPairs = asList(pair.getHotMethodPairsByDescendingPeriod());
        assertEquals(3, methodPairs.size());

        List<CompilationUnitPair> bazCompilations = asList(methodPairs.get(0).getHotCompilationUnitPairsByDescendingPeriod());
        assertTrue(bazCompilations.isEmpty());

        List<CompilationUnitPair> barCompilations = asList(methodPairs.get(1).getHotCompilationUnitPairsByDescendingPeriod());
        assertEquals(1, barCompilations.size());
        assertEquals(bar1, barCompilations.get(0).getCompilationUnit1());
        assertEquals(bar2, barCompilations.get(0).getCompilationUnit2());

        List<CompilationUnitPair> fooCompilations = asList(methodPairs.get(2).getHotCompilationUnitPairsByDescendingPeriod());
        assertEquals(2, fooCompilations.size());
        assertEquals(foo3, fooCompilations.get(0).getCompilationUnit1());
        assertEquals(foo4, fooCompilations.get(0).getCompilationUnit2());
        assertEquals(foo2, fooCompilations.get(1).getCompilationUnit1());
        assertEquals(foo4, fooCompilations.get(1).getCompilationUnit2());
    }

    /**
     * Tests that {@link ExperimentPair#createCompilationFragments()} creates compilation fragments
     * for the most basic scenario.
     *
     * Let us have the following hot compilation units:
     *
     * <pre>
     * Compilation unit in experiment 1
     *      a()
     *          b()
     * Compilation unit in experiment 2
     *      b()
     * </pre>
     *
     * Then, the fragment below should be created:
     *
     * <pre>
     * Compilation fragment in experiment 1
     *      b()
     * </pre>
     */
    @Test
    public void basicCompilationFragmentCreation() throws ExperimentParserError {
        String a = "a()";
        String b = "b()";
        Experiment experiment1 = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        InliningTreeNode a1 = new InliningTreeNode(a, -1, true, null, false, null);
        InliningTreeNode b1 = new InliningTreeNode(b, 1, true, null, false, null);
        a1.addChild(b1);
        InliningTree inliningTree1 = new InliningTree(a1);
        experiment1.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree1)).setHot(true);

        Experiment experiment2 = new Experiment(ExperimentId.TWO, Experiment.CompilationKind.JIT);
        InliningTreeNode b2 = new InliningTreeNode(b, -1, true, null, false, null);
        InliningTree inliningTree2 = new InliningTree(b2);
        experiment2.addCompilationUnit(b, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree2)).setHot(true);

        ExperimentPair experimentPair = new ExperimentPair(experiment1, experiment2);
        experimentPair.createCompilationFragments();
        List<CompilationFragment> fragments = asList(experiment1.getMethodOrCreate(b).getCompilationFragments());
        assertEquals(1, fragments.size());
    }

    /**
     * Tests that {@link ExperimentPair#createCompilationFragments()} does not create unnecessary
     * fragments.
     *
     * Let us have the following compilation units:
     *
     * <pre>
     * Experiment 1
     *      Compilation unit of a() (hot)
     *          a()
     *              b() at bci 1
     *              c() at bci 2
     *              d() at bci 3
     *              d() at bci 3
     *              e() at bci 4
     * Experiment 2
     *      Compilation unit of a() (hot)
     *          a()
     *              b()
     *      Compilation unit of b() (hot)
     *          b()
     *      Compilation unit of c() (not hot)
     *          c()
     *      Compilation unit of d() (hot)
     *          d()
     * </pre>
     *
     * There should be no fragments created, because:
     *
     * <ul>
     * <li>{@code b()} is inlined in the compilation units of {@code a()} (there is no inlining
     * difference),</li>
     * <li>{@code c()} is not hot in any of the experiments,</li>
     * <li>{@code d()} does not have a unique path from root,</li>
     * <li>{@code e()} does not have any compilation unit and thus it is not hot.</li>
     * </ul>
     */
    @Test
    public void unnecessaryFragmentsAreNotCreated() throws ExperimentParserError {
        String a = "a()";
        String b = "b()";
        String c = "c()";
        String d = "d()";
        String e = "e()";

        Experiment experiment1 = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        InliningTreeNode a1 = new InliningTreeNode(a, -1, true, null, false, null);
        InliningTreeNode b1 = new InliningTreeNode(b, 1, true, null, false, null);
        InliningTreeNode c1 = new InliningTreeNode(c, 2, true, null, false, null);
        InliningTreeNode d1 = new InliningTreeNode(d, 3, true, null, false, null);
        InliningTreeNode d2 = new InliningTreeNode(d, 3, true, null, false, null);
        InliningTreeNode e1 = new InliningTreeNode(e, 4, true, null, false, null);
        a1.addChild(b1);
        a1.addChild(c1);
        a1.addChild(d1);
        a1.addChild(d2);
        a1.addChild(e1);
        InliningTree inliningTree1 = new InliningTree(a1);
        experiment1.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree1)).setHot(true);

        Experiment experiment2 = new Experiment(ExperimentId.TWO, Experiment.CompilationKind.JIT);

        InliningTreeNode a2 = new InliningTreeNode(a, -1, true, null, false, null);
        InliningTreeNode b2 = new InliningTreeNode(b, 1, true, null, false, null);
        a2.addChild(b2);
        InliningTree inliningTree2 = new InliningTree(a2);
        experiment2.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree2)).setHot(true);

        InliningTreeNode b3 = new InliningTreeNode(b, -1, true, null, false, null);
        InliningTree inliningTree3 = new InliningTree(b3);
        experiment2.addCompilationUnit(b, "2", 0, () -> new CompilationUnit.TreePair(null, inliningTree3)).setHot(true);

        InliningTreeNode c2 = new InliningTreeNode(c, -1, true, null, false, null);
        InliningTree inliningTree4 = new InliningTree(c2);
        experiment2.addCompilationUnit(c, "3", 0, () -> new CompilationUnit.TreePair(null, inliningTree4));

        InliningTreeNode d3 = new InliningTreeNode(d, -1, true, null, false, null);
        InliningTree inliningTree5 = new InliningTree(d3);
        experiment2.addCompilationUnit(d, "4", 0, () -> new CompilationUnit.TreePair(null, inliningTree5)).setHot(true);

        ExperimentPair experimentPair = new ExperimentPair(experiment1, experiment2);
        experimentPair.createCompilationFragments();

        for (Experiment experiment : List.of(experiment1, experiment2)) {
            for (String methodName : List.of(a, b, c, d, e)) {
                assertFalse(experiment.getMethodOrCreate(methodName).getCompilationFragments().iterator().hasNext());
            }
        }
    }

    /**
     * Tests that {@link ExperimentPair#createCompilationFragments()} creates a compilation fragment
     * in a scenario with multiple compilations.
     *
     * Let us have the following hot compilation units:
     *
     * <pre>
     * Experiment 1
     *      Compilation unit of a()
     *          a()
     *              b()
     * Experiment 2
     *      Compilation unit of a()
     *          a()
     *              b()
     *      Compilation unit of a()
     *          a()
     *      Compilation unit of b()
     *          b()
     * </pre>
     *
     * The fragment for {@code b()} should be created in experiment 1, because {@code b()} is hot in
     * experiment 2 and there exists a compilation unit where {@code b()} is not inlined.
     */
    @Test
    public void fragmentCreationWithMultipleCompilations() throws ExperimentParserError {
        String a = "a()";
        String b = "b()";
        Experiment experiment1 = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        InliningTreeNode a1 = new InliningTreeNode(a, -1, true, null, false, null);
        InliningTreeNode b1 = new InliningTreeNode(b, 1, true, null, false, null);
        a1.addChild(b1);
        InliningTree inliningTree1 = new InliningTree(a1);
        experiment1.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree1)).setHot(true);

        Experiment experiment2 = new Experiment(ExperimentId.TWO, Experiment.CompilationKind.JIT);

        InliningTreeNode a2 = new InliningTreeNode(a, -1, true, null, false, null);
        InliningTreeNode b2 = new InliningTreeNode(b, 1, true, null, false, null);
        a2.addChild(b2);
        InliningTree inliningTree2 = new InliningTree(a2);
        experiment2.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree2)).setHot(true);

        InliningTreeNode a3 = new InliningTreeNode(a, -1, true, null, false, null);
        InliningTree inliningTree3 = new InliningTree(a3);
        experiment2.addCompilationUnit(a, "2", 0, () -> new CompilationUnit.TreePair(null, inliningTree3)).setHot(true);

        InliningTreeNode b3 = new InliningTreeNode(b, -1, true, null, false, null);
        InliningTree inliningTree4 = new InliningTree(b3);
        experiment2.addCompilationUnit(b, "3", 0, () -> new CompilationUnit.TreePair(null, inliningTree4)).setHot(true);

        ExperimentPair experimentPair = new ExperimentPair(experiment1, experiment2);
        experimentPair.createCompilationFragments();
        List<CompilationFragment> fragments = asList(experiment1.getMethodOrCreate(b).getCompilationFragments());
        assertEquals(1, fragments.size());
    }
}
