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

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.CompilationFragment;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.core.optimization.OptimizationTreeNode;
import org.graalvm.profdiff.core.optimization.Position;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.junit.Test;

public class CompilationFragmentTest {
    /**
     * Tests that {@link CompilationFragment compilation fragments} are correctly created, i.e.,
     * {@link CompilationFragment#loadTrees() the lazily-loaded trees} conform to expectations.
     *
     * The tested parent compilation unit consists of the following trees:
     *
     * <pre>
     * Inlining tree
     *     a() at bci -1
     *         b() at bci 1
     *             d() at bci 3
     *         c() at bci 2
     *         (abstract) e() at bci 3
     *             f() at bci 3
     * Optimization tree
     *      RootPhase
     *          Tier1
     *              Optimization1 at null
     *              Optimization2 at {a(): 5}
     *              Optimization3 at {b(): 2, a(): 1}
     *          Tier2
     *              Optimization4 at {d(): 1, b(): 3, a(): 1}
     *              Optimization5 at {c(): 4, a(): 2}
     *              Optimization6 at {f(): 2, a(): 3}
     *              Optimization7 at {x(): 2, a(): 3}
     *              Optimization8 at {y(): 3, c(): 5, a(): 2}
     *              Optimization9 at {z(): 6, b(): 4, a(): 1}
     *          Tier3
     * </pre>
     *
     * The expected fragment rooted in {@code b()} is:
     *
     * <pre>
     * Inlining tree
     *      b() at bci -1
     *          d() at bci 3
     * Optimization tree
     *      RootPhase
     *          Tier1
     *              Optimization3 at {b(): 2}
     *          Tier2
     *              Optimization4 at {d(): 1, b(): 3}
     *              Optimization9 at {z(): 6, b(): 4}
     *          Tier3
     * </pre>
     */
    @Test
    public void compilationFragmentTreeCreation() throws ExperimentParserError {
        InliningTreeNode a = new InliningTreeNode("a()", -1, true, null, false, null, false);
        InliningTreeNode b = new InliningTreeNode("b()", 1, true, null, false, null, false);
        InliningTreeNode d = new InliningTreeNode("d()", 3, true, null, false, null, false);
        InliningTreeNode c = new InliningTreeNode("c()", 2, true, null, false, null, false);
        InliningTreeNode e = new InliningTreeNode("e()", 3, false, null, true, null, true);
        InliningTreeNode f = new InliningTreeNode("f()", 3, true, null, false, null, false);
        a.addChild(b);
        a.addChild(c);
        a.addChild(e);
        b.addChild(d);
        e.addChild(f);
        InliningTree inliningTree = new InliningTree(a);

        OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
        OptimizationPhase tier1 = new OptimizationPhase("Tier1");
        OptimizationPhase tier2 = new OptimizationPhase("Tier2");
        OptimizationPhase tier3 = new OptimizationPhase("Tier3");
        Optimization o1 = new Optimization("Optimization1", "", null, null);
        Optimization o2 = new Optimization("Optimization2", "", Position.of("a()", 5), null);
        Optimization o3 = new Optimization("Optimization3", "", Position.of("b()", 2, "a()", 1), null);
        EconomicMap<String, Integer> o4Position = EconomicMap.create();
        o4Position.put("d()", 1);
        o4Position.put("b()", 3);
        o4Position.put("a()", 1);
        Optimization o4 = new Optimization("Optimization4", "", Position.fromMap(o4Position), null);
        Optimization o5 = new Optimization("Optimization5", "", Position.of("c()", 4, "a()", 2), null);
        Optimization o6 = new Optimization("Optimization6", "", Position.of("f()", 2, "a()", 3), null);
        Optimization o7 = new Optimization("Optimization7", "", Position.of("x()", 2, "a()", 3), null);
        EconomicMap<String, Integer> o8Position = EconomicMap.create();
        o8Position.put("y()", 3);
        o8Position.put("c()", 5);
        o8Position.put("a()", 2);
        Optimization o8 = new Optimization("Optimization8", "", Position.fromMap(o8Position), null);
        EconomicMap<String, Integer> o9Position = EconomicMap.create();
        o9Position.put("z()", 6);
        o9Position.put("b()", 4);
        o9Position.put("a()", 1);
        Optimization o9 = new Optimization("Optimization9", "", Position.fromMap(o9Position), null);
        rootPhase.addChild(tier1);
        rootPhase.addChild(tier2);
        rootPhase.addChild(tier3);
        tier1.addChild(o1);
        tier1.addChild(o2);
        tier1.addChild(o3);
        tier2.addChild(o4);
        tier2.addChild(o5);
        tier2.addChild(o6);
        tier2.addChild(o7);
        tier2.addChild(o8);
        tier2.addChild(o9);
        OptimizationTree optimizationTree = new OptimizationTree(rootPhase);

        Experiment experiment = new Experiment(ExperimentId.ONE, Experiment.CompilationKind.JIT);
        CompilationUnit.TreeLoader mockTreeLoader = () -> new CompilationUnit.TreePair(optimizationTree, inliningTree);
        CompilationUnit compilationUnit = experiment.addCompilationUnit("a()", "foo", 0, mockTreeLoader);
        CompilationFragment compilationFragment = new CompilationFragment(experiment.getMethodOrCreate("b()"), compilationUnit, b);
        CompilationUnit.TreePair treePair = compilationFragment.loadTrees();
        InliningTreeNode actualInliningTreeRoot = treePair.getInliningTree().getRoot();
        OptimizationTreeNode actualOptimizationTreeRoot = treePair.getOptimizationTree().getRoot();

        InliningTreeNode fb = new InliningTreeNode("b()", -1, true, null, false, null, false);
        InliningTreeNode fd = new InliningTreeNode("d()", 3, true, null, false, null, false);
        fb.addChild(fd);
        assertEquals(fb, actualInliningTreeRoot);

        OptimizationPhase fragmentRootPhase = new OptimizationPhase("RootPhase");
        OptimizationPhase fragmentTier1 = new OptimizationPhase("Tier1");
        OptimizationPhase fragmentTier2 = new OptimizationPhase("Tier2");
        OptimizationPhase fragmentTier3 = new OptimizationPhase("Tier3");
        Optimization fo3 = new Optimization("Optimization3", "", Position.of("b()", 2), null);
        Optimization fo4 = new Optimization("Optimization4", "", Position.of("d()", 1, "b()", 3), null);
        Optimization fo9 = new Optimization("Optimization9", "", Position.of("z()", 6, "b()", 4), null);
        fragmentRootPhase.addChild(fragmentTier1);
        fragmentRootPhase.addChild(fragmentTier2);
        fragmentRootPhase.addChild(fragmentTier3);
        fragmentTier1.addChild(fo3);
        fragmentTier2.addChild(fo4);
        fragmentTier2.addChild(fo9);
        assertEquals(fragmentRootPhase, actualOptimizationTreeRoot);
    }
}
