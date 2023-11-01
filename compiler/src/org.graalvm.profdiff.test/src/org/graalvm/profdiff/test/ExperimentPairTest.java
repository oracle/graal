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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.nodes.OptimizationLogImpl;
import org.graalvm.profdiff.command.ExperimentMatcher;
import org.graalvm.profdiff.core.CompilationFragment;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.ProftoolMethod;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.core.optimization.Position;
import org.graalvm.profdiff.core.pair.CompilationUnitPair;
import org.graalvm.profdiff.core.pair.ExperimentPair;
import org.graalvm.profdiff.core.pair.MethodPair;
import org.graalvm.profdiff.parser.ExperimentParser;
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
        InliningTreeNode a1 = new InliningTreeNode(a, -1, true, null, false, null, false);
        InliningTreeNode b1 = new InliningTreeNode(b, 1, true, null, false, null, false);
        a1.addChild(b1);
        InliningTree inliningTree1 = new InliningTree(a1);
        experiment1.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree1)).setHot(true);

        Experiment experiment2 = new Experiment(ExperimentId.TWO, Experiment.CompilationKind.JIT);
        InliningTreeNode b2 = new InliningTreeNode(b, -1, true, null, false, null, false);
        InliningTree inliningTree2 = new InliningTree(b2);
        experiment2.addCompilationUnit(b, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree2)).setHot(true);

        ExperimentPair experimentPair = new ExperimentPair(experiment1, experiment2);
        experimentPair.createCompilationFragments();
        List<CompilationFragment> fragments = asList(experiment1.getMethodOrCreate(b).getCompilationFragments());
        assertEquals(1, fragments.size());
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
     *      Compilation unit of b()
     *          b()
     * </pre>
     *
     * The fragment for {@code b()} should be created, because {@code b()} is hot.
     */
    @Test
    public void fragmentCreationWithMultipleCompilations() throws ExperimentParserError {
        String a = "a()";
        String b = "b()";
        Experiment experiment1 = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        InliningTreeNode a1 = new InliningTreeNode(a, -1, true, null, false, null, false);
        InliningTreeNode b1 = new InliningTreeNode(b, 1, true, null, false, null, false);
        a1.addChild(b1);
        InliningTree inliningTree1 = new InliningTree(a1);
        experiment1.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree1)).setHot(true);

        Experiment experiment2 = new Experiment(ExperimentId.TWO, Experiment.CompilationKind.JIT);

        InliningTreeNode a2 = new InliningTreeNode(a, -1, true, null, false, null, false);
        InliningTreeNode b2 = new InliningTreeNode(b, 1, true, null, false, null, false);
        a2.addChild(b2);
        InliningTree inliningTree2 = new InliningTree(a2);
        experiment2.addCompilationUnit(a, "1", 0, () -> new CompilationUnit.TreePair(null, inliningTree2)).setHot(true);

        InliningTreeNode b3 = new InliningTreeNode(b, -1, true, null, false, null, false);
        InliningTree inliningTree3 = new InliningTree(b3);
        experiment2.addCompilationUnit(b, "2", 0, () -> new CompilationUnit.TreePair(null, inliningTree3)).setHot(true);

        ExperimentPair experimentPair = new ExperimentPair(experiment1, experiment2);
        experimentPair.createCompilationFragments();
        List<CompilationFragment> fragments1 = asList(experiment1.getMethodOrCreate(b).getCompilationFragments());
        assertEquals(1, fragments1.size());
        List<CompilationFragment> fragments2 = asList(experiment2.getMethodOrCreate(b).getCompilationFragments());
        assertEquals(1, fragments2.size());
    }

    private static ExperimentPair mockExperimentPair() {
        List<ProftoolMethod> proftoolMethods1 = List.of(new ProftoolMethod("10000", "foo()", ExperimentParser.GRAAL_COMPILER_LEVEL, 70),
                        new ProftoolMethod("30000", "bar()", ExperimentParser.GRAAL_COMPILER_LEVEL, 30));
        Experiment experiment1 = new Experiment("1000", ExperimentId.ONE, Experiment.CompilationKind.JIT, 100, proftoolMethods1);

        InliningTreeNode foo1 = new InliningTreeNode("foo()", -1, true, null, false, null, false);
        foo1.addChild(new InliningTreeNode("bar()", 1, true, null, false, null, false));
        InliningTree inliningTree1 = new InliningTree(foo1);

        OptimizationPhase rootPhase1 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        rootPhase1.addChild(new Optimization("Opt", "Bar", Position.create(List.of("bar()", "foo()"), List.of(2, 1)), null));
        rootPhase1.addChild(new Optimization("Opt", "Foo", Position.create(List.of("foo()"), List.of(2)), null));
        OptimizationTree optimizationTree1 = new OptimizationTree(rootPhase1);

        experiment1.addCompilationUnit("foo()", "10000", 70, () -> new CompilationUnit.TreePair(optimizationTree1, inliningTree1)).setHot(true);

        InliningTreeNode bar1 = new InliningTreeNode("bar()", -1, true, null, false, null, false);
        InliningTree inliningTree3 = new InliningTree(bar1);

        OptimizationPhase rootPhase3 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        rootPhase3.addChild(new Optimization("Opt", "Bar", Position.create(List.of("bar()"), List.of(2)), null));
        OptimizationTree optimizationTree3 = new OptimizationTree(rootPhase3);

        experiment1.addCompilationUnit("bar()", "30000", 30, () -> new CompilationUnit.TreePair(optimizationTree3, inliningTree3)).setHot(true);

        List<ProftoolMethod> proftoolMethods2 = List.of(new ProftoolMethod("20000", "foo()", ExperimentParser.GRAAL_COMPILER_LEVEL, 95),
                        new ProftoolMethod("40000", "bar()", ExperimentParser.GRAAL_COMPILER_LEVEL, 5));
        Experiment experiment2 = new Experiment("2000", ExperimentId.TWO, Experiment.CompilationKind.JIT, 100, proftoolMethods2);

        InliningTreeNode foo2 = new InliningTreeNode("foo()", -1, true, null, false, null, false);
        foo2.addChild(new InliningTreeNode("bar()", 1, true, null, false, null, false));
        InliningTree inliningTree2 = new InliningTree(foo2);

        OptimizationPhase rootPhase2 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        rootPhase2.addChild(new Optimization("Opt", "Bar", Position.create(List.of("bar()", "foo()"), List.of(2, 1)), null));
        rootPhase2.addChild(new Optimization("Opt", "Foo", Position.create(List.of("foo()"), List.of(2)), null));
        OptimizationTree optimizationTree2 = new OptimizationTree(rootPhase2);

        experiment2.addCompilationUnit("foo()", "20000", 95, () -> new CompilationUnit.TreePair(optimizationTree2, inliningTree2)).setHot(true);

        InliningTreeNode bar2 = new InliningTreeNode("bar()", -1, true, null, false, null, false);
        InliningTree inliningTree4 = new InliningTree(bar2);

        OptimizationPhase rootPhase4 = new OptimizationPhase(OptimizationLogImpl.ROOT_PHASE_NAME);
        rootPhase4.addChild(new Optimization("Opt", "Bar", Position.create(List.of("bar()"), List.of(2)), null));
        OptimizationTree optimizationTree4 = new OptimizationTree(rootPhase4);

        experiment2.addCompilationUnit("bar()", "40000", 5, () -> new CompilationUnit.TreePair(optimizationTree4, inliningTree4));

        return new ExperimentPair(experiment1, experiment2);
    }

    @Test
    public void experimentMatcherWithoutDiffing() throws ExperimentParserError {
        ExperimentPair pair = mockExperimentPair();
        var writer = Writer.stringBuilder(new OptionValues());
        new ExperimentMatcher(writer).match(pair);
        assertEquals("""
                        Method foo()
                            In experiment 1
                                1 compilations (1 of which are hot)
                                Compilations
                                    10000 consumed 70.00% of Graal execution, 70.00% of total *hot*
                            In experiment 2
                                1 compilations (1 of which are hot)
                                Compilations
                                    20000 consumed 95.00% of Graal execution, 95.00% of total *hot*
                            Compilation unit 10000 consumed 70.00% of Graal execution, 70.00% of total in experiment 1
                                Inlining tree
                                    (root) foo()
                                        (inlined) bar() at bci 1
                                Optimization tree
                                    RootPhase
                                        Opt Bar at bci 1
                                        Opt Foo at bci 2
                            Compilation unit 20000 consumed 95.00% of Graal execution, 95.00% of total in experiment 2
                                Inlining tree
                                    (root) foo()
                                        (inlined) bar() at bci 1
                                Optimization tree
                                    RootPhase
                                        Opt Bar at bci 1
                                        Opt Foo at bci 2

                        Method bar() is hot only in experiment 1
                            In experiment 1
                                1 compilations (1 of which are hot)
                                Compilations
                                    30000 consumed 30.00% of Graal execution, 30.00% of total *hot*
                            In experiment 2
                                1 compilations (0 of which are hot)
                                Compilations
                                    40000 consumed  5.00% of Graal execution,  5.00% of total
                            Compilation unit 30000 consumed 30.00% of Graal execution, 30.00% of total in experiment 1
                                Inlining tree
                                    (root) bar()
                                Optimization tree
                                    RootPhase
                                        Opt Bar at bci 2
                        """, writer.getOutput());
    }

    @Test
    public void matchOptimizationContextTrees() throws ExperimentParserError {
        ExperimentPair pair = mockExperimentPair();
        OptionValues optionValues = OptionValues.builder().withDiffCompilations(true).withOptimizationContextTreeEnabled(true).build();
        var writer = Writer.stringBuilder(optionValues);
        new ExperimentMatcher(writer).match(pair);
        assertEquals("""
                        Method foo()
                            In experiment 1
                                1 compilations (1 of which are hot)
                                Compilations
                                    10000 consumed 70.00% of Graal execution, 70.00% of total *hot*
                            In experiment 2
                                1 compilations (1 of which are hot)
                                Compilations
                                    20000 consumed 95.00% of Graal execution, 95.00% of total *hot*
                            Compilation unit 10000 consumed 70.00% of Graal execution, 70.00% of total in experiment 1 vs
                            Compilation unit 20000 consumed 95.00% of Graal execution, 95.00% of total in experiment 2
                                . Optimization-context tree
                                    . (root) foo()
                                        . Opt Foo at bci 2
                                        . (inlined) bar() at bci 1
                                            . Opt Bar at bci 2

                        Method bar() is hot only in experiment 1
                            In experiment 1
                                1 compilations (1 of which are hot)
                                Compilations
                                    30000 consumed 30.00% of Graal execution, 30.00% of total *hot*
                            In experiment 2
                                1 compilations (0 of which are hot)
                                Compilations
                                    40000 consumed  5.00% of Graal execution,  5.00% of total
                        """, writer.getOutput());
    }

    @Test
    public void matchTreesTooBigToCompare() throws ExperimentParserError {
        ExperimentPair pair = mockExperimentPair();
        OptionValues optionValues = OptionValues.builder().withDiffCompilations(true).build();
        var writer = Writer.stringBuilder(optionValues);
        new ExperimentMatcher(writer, 0).match(pair);
        String output = writer.getOutput();
        assertTrue(output.contains("The inlining trees are too big to compare"));
        assertTrue(output.contains("The optimization trees are too big to compare"));

        optionValues = OptionValues.builder().withDiffCompilations(true).withOptimizationContextTreeEnabled(true).build();
        writer = Writer.stringBuilder(optionValues);
        new ExperimentMatcher(writer, 0).match(pair);
        assertTrue(writer.getOutput().contains("The optimization-context trees are too big to compare"));
    }
}
