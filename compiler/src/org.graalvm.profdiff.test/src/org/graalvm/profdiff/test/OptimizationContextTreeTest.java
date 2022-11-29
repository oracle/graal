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

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.OptimizationContextTree;
import org.graalvm.profdiff.core.OptimizationContextTreeNode;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OptimizationContextTreeTest {
    /**
     * Tests that {@link OptimizationContextTree#createFrom(InliningTree, OptimizationTree)} creates
     * the optimization-context tree correctly.
     *
     * The method is tested on the following inlining and optimization tree:
     *
     * <pre>
     * Inlining tree
     *     a() at bci -1
     *         b() at bci 1
     *             d() at bci 3
     *         b() at bci 1
     *             d() at bci 3
     *         c() at bci 2
     *         (abstract) e() at bci 3
     *             f() at bci 1
     * Optimization tree
     *      RootPhase
     *          Tier1
     *              Optimization1 at null
     *              Optimization2 at {a(): 5}
     *              Optimization3 at {a(): 1, b(): 2}
     *          Tier2
     *              Optimization4 at {a(): 1, b(): 3, d(): 1}
     *              Optimization5 at {a(): 2, c(): 4}
     *              Optimization6 at {a(): 3, f(): 2}
     * </pre>
     *
     * The expected result is:
     *
     * <pre>
     * Optimization-context tree
     *     Optimization1 at null
     *     a() at bci -1
     *         Optimization2 at {a(): 5}
     *         b() at bci 1
     *             Warning
     *             Optimization3 at {a(): 1, b(): 2}
     *             d() at bci 3
     *                 Warning
     *                 Optimization4 at {a(): 1, b(): 3, d(): 1}
     *         b() at bci 1
     *             Warning
     *             d() at bci 3
     *                 Warning
     *         c() at bci 2
     *             Optimization5 at {a(): 2, c(): 4}
     *         (abstract) e() at bci 3
     *             f() at bci 1
     *                 Optimization6 at {a(): 3, f(): 2}
     * </pre>
     */
    @Test
    public void createdCorrectly() {
        InliningTreeNode a = new InliningTreeNode("a()", -1, true, null, false, null);
        InliningTreeNode b1 = new InliningTreeNode("b()", 1, true, null, false, null);
        InliningTreeNode d1 = new InliningTreeNode("d()", 3, true, null, false, null);
        InliningTreeNode b2 = new InliningTreeNode("b()", 1, true, null, false, null);
        InliningTreeNode d2 = new InliningTreeNode("d()", 3, true, null, false, null);
        InliningTreeNode c = new InliningTreeNode("c()", 2, true, null, false, null);
        InliningTreeNode e = new InliningTreeNode("e()", 3, false, null, true, null);
        InliningTreeNode f = new InliningTreeNode("f()", 1, true, null, false, null);

        a.addChild(b1);
        a.addChild(b2);
        a.addChild(c);
        a.addChild(e);
        b1.addChild(d1);
        b2.addChild(d2);
        e.addChild(f);

        InliningTree inliningTree = new InliningTree(a);

        OptimizationPhase rootPhase = new OptimizationPhase("RootPhase");
        OptimizationPhase tier1 = new OptimizationPhase("Tier1");
        OptimizationPhase tier2 = new OptimizationPhase("Tier2");
        Optimization o1 = new Optimization("Optimization1", "", null, null);
        Optimization o2 = new Optimization("Optimization2", "", EconomicMap.of("a()", 5), null);
        Optimization o3 = new Optimization("Optimization3", "", EconomicMap.of("a()", 1, "b()", 2), null);
        EconomicMap<String, Integer> o4Position = EconomicMap.create();
        o4Position.put("a()", 1);
        o4Position.put("b()", 3);
        o4Position.put("d()", 1);
        Optimization o4 = new Optimization("Optimization4", "", o4Position, null);
        Optimization o5 = new Optimization("Optimization5", "", EconomicMap.of("a()", 2, "c()", 4), null);
        Optimization o6 = new Optimization("Optimization6", "", EconomicMap.of("a()", 3, "f()", 2), null);

        rootPhase.addChild(tier1);
        rootPhase.addChild(tier2);
        tier1.addChild(o1);
        tier1.addChild(o2);
        tier1.addChild(o3);
        tier2.addChild(o4);
        tier2.addChild(o5);
        tier2.addChild(o6);

        OptimizationTree optimizationTree = new OptimizationTree(rootPhase);

        OptimizationContextTree actual = OptimizationContextTree.createFrom(inliningTree, optimizationTree);

        OptimizationContextTreeNode root = new OptimizationContextTreeNode();
        OptimizationContextTreeNode ca = new OptimizationContextTreeNode(a);
        OptimizationContextTreeNode cb1 = new OptimizationContextTreeNode(b1);
        OptimizationContextTreeNode cb2 = new OptimizationContextTreeNode(b2);
        OptimizationContextTreeNode cd1 = new OptimizationContextTreeNode(d1);
        OptimizationContextTreeNode cd2 = new OptimizationContextTreeNode(d2);
        OptimizationContextTreeNode cc = new OptimizationContextTreeNode(c);
        OptimizationContextTreeNode ce = new OptimizationContextTreeNode(e);
        OptimizationContextTreeNode cf = new OptimizationContextTreeNode(f);
        OptimizationContextTreeNode co1 = new OptimizationContextTreeNode(o1);
        OptimizationContextTreeNode co2 = new OptimizationContextTreeNode(o2);
        OptimizationContextTreeNode co3 = new OptimizationContextTreeNode(o3);
        OptimizationContextTreeNode co4 = new OptimizationContextTreeNode(o4);
        OptimizationContextTreeNode co5 = new OptimizationContextTreeNode(o5);
        OptimizationContextTreeNode co6 = new OptimizationContextTreeNode(o6);

        cb1.addChild(OptimizationContextTreeNode.duplicatePathWarning());
        cd1.addChild(OptimizationContextTreeNode.duplicatePathWarning());
        cb2.addChild(OptimizationContextTreeNode.duplicatePathWarning());
        cd2.addChild(OptimizationContextTreeNode.duplicatePathWarning());

        ca.addChild(cb1);
        ca.addChild(cb2);
        ca.addChild(cc);
        ca.addChild(ce);
        cb1.addChild(cd1);
        cb2.addChild(cd2);
        ce.addChild(cf);

        root.addChild(co1);
        ca.addChild(co2);
        cb1.addChild(co3);
        cd1.addChild(co4);
        cc.addChild(co5);
        cf.addChild(co6);

        assertEquals(root, actual.getRoot());
    }
}
