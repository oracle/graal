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

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.OptimizationContextTree;
import org.graalvm.profdiff.core.OptimizationContextTreeNode;
import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.inlining.ReceiverTypeProfile;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationPhase;
import org.graalvm.profdiff.core.optimization.OptimizationTree;
import org.graalvm.profdiff.core.optimization.Position;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

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
     *              Optimization9 at {z(): 4, f(): 1, a(): 3}
     * </pre>
     *
     * The expected result is:
     *
     * <pre>
     * Optimization-context tree
     *     Optimization1 at null
     *     a() at bci -1
     *         Optimization2 at {a(): 5}
     *         Optimization7 at {x(): 2, a(): 3}
     *         b() at bci 1
     *             Warning
     *             Optimization3 at {b(): 2, a(): 1}
     *             d() at bci 3
     *                 Warning
     *                 Optimization4 at {d(): 1, b(): 3, a(): 1}
     *         b() at bci 1
     *             Warning
     *             Optimization3 at {b(): 2, a(): 1}
     *             d() at bci 3
     *                 Warning
     *                 Optimization4 at {d(): 1, b(): 3, a(): 1}
     *         c() at bci 2
     *            Optimization5 at {c(): 4, a(): 2}
     *            Optimization8 at {y(): 3, c(): 5, a(): 2}
     *         (abstract) e() at bci 3
     *             f() at bci 3
     *                 Optimization6 at {f(): 2, a(): 3}
     *                 Optimization9 at {z(): 4, f(): 1, a(): 3}
     * </pre>
     */
    @Test
    public void createdCorrectly() {
        InliningTreeNode a = new InliningTreeNode("a()", -1, true, null, false, null, false);
        InliningTreeNode b1 = new InliningTreeNode("b()", 1, true, null, false, null, false);
        InliningTreeNode d1 = new InliningTreeNode("d()", 3, true, null, false, null, false);
        InliningTreeNode b2 = new InliningTreeNode("b()", 1, true, null, false, null, false);
        InliningTreeNode d2 = new InliningTreeNode("d()", 3, true, null, false, null, false);
        InliningTreeNode c = new InliningTreeNode("c()", 2, true, null, false, null, false);
        InliningTreeNode e = new InliningTreeNode("e()", 3, false, null, true, null, true);
        InliningTreeNode f = new InliningTreeNode("f()", 3, true, null, false, null, false);

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
        o9Position.put("z()", 4);
        o9Position.put("f()", 1);
        o9Position.put("a()", 3);
        Optimization o9 = new Optimization("Optimization9", "", Position.fromMap(o9Position), null);

        rootPhase.addChild(tier1);
        rootPhase.addChild(tier2);
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
        OptimizationContextTreeNode co3d = new OptimizationContextTreeNode(o3);
        OptimizationContextTreeNode co4 = new OptimizationContextTreeNode(o4);
        OptimizationContextTreeNode co4d = new OptimizationContextTreeNode(o4);
        OptimizationContextTreeNode co5 = new OptimizationContextTreeNode(o5);
        OptimizationContextTreeNode co6 = new OptimizationContextTreeNode(o6);
        OptimizationContextTreeNode co7 = new OptimizationContextTreeNode(o7);
        OptimizationContextTreeNode co8 = new OptimizationContextTreeNode(o8);
        OptimizationContextTreeNode co9 = new OptimizationContextTreeNode(o9);

        cb1.addChild(OptimizationContextTreeNode.duplicatePathWarning());
        cd1.addChild(OptimizationContextTreeNode.duplicatePathWarning());
        cb2.addChild(OptimizationContextTreeNode.duplicatePathWarning());
        cd2.addChild(OptimizationContextTreeNode.duplicatePathWarning());

        root.addChild(co1);
        ca.addChild(co2);
        ca.addChild(co7);
        cb1.addChild(co3);
        cd1.addChild(co4);
        cb2.addChild(co3d);
        cd2.addChild(co4d);
        cc.addChild(co5);
        cc.addChild(co8);
        cf.addChild(co6);
        cf.addChild(co9);

        root.addChild(ca);
        ca.addChild(cb1);
        ca.addChild(cb2);
        ca.addChild(cc);
        ca.addChild(ce);
        cb1.addChild(cd1);
        cb2.addChild(cd2);
        ce.addChild(cf);

        assertEquals(root, actual.getRoot());
    }

    @Test
    public void nodeEqualsAndHashCode() {
        OptimizationContextTreeNode node1 = new OptimizationContextTreeNode();
        OptimizationContextTreeNode node2 = new OptimizationContextTreeNode();
        assertEquals(node1, node1);
        assertEquals(node1, node2);
        assertEquals(node1.hashCode(), node2.hashCode());
        assertNotEquals(node1, null);
        assertNotEquals(node1, new Object());

        InliningTreeNode inliningTreeNode1 = new InliningTreeNode("foo()", 0, true, null, false, null, false);
        InliningTreeNode inliningTreeNode2 = new InliningTreeNode("foo()", 0, true, null, false, null, false);
        assertEquals(new OptimizationContextTreeNode(inliningTreeNode1), new OptimizationContextTreeNode(inliningTreeNode2));

        Optimization optimization1 = new Optimization("Opt", "Event", null, null);
        Optimization optimization2 = new Optimization("Opt", "Event", null, null);
        assertEquals(new OptimizationContextTreeNode(optimization1), new OptimizationContextTreeNode(optimization2));
    }

    @Test
    public void writeOptimizationContextTree() {
        var writer = Writer.stringBuilder(new OptionValues());
        new OptimizationContextTree(null).write(writer);
        assertTrue(writer.getOutput().isEmpty());

        InliningTreeNode inliningRoot = new InliningTreeNode("foo()", -1, true, null, false, null, false);
        InliningTreeNode inliningChild = new InliningTreeNode("bar()", 1, false, List.of("not inlined"), true, new ReceiverTypeProfile(false, null), true);
        inliningRoot.addChild(inliningChild);

        Optimization optimization = new Optimization("Opt", "Foo", null, null);

        OptimizationContextTreeNode root = new OptimizationContextTreeNode();
        OptimizationContextTreeNode optimizationNode = new OptimizationContextTreeNode(optimization);
        root.addChild(optimizationNode);
        OptimizationContextTreeNode foo = new OptimizationContextTreeNode(inliningRoot);
        root.addChild(foo);
        OptimizationContextTreeNode bar = new OptimizationContextTreeNode(inliningChild);
        foo.addChild(bar);
        OptimizationContextTree tree = new OptimizationContextTree(root);

        writer = Writer.stringBuilder(new OptionValues());
        tree.write(writer);
        assertEquals("""
                        Optimization-context tree
                            Opt Foo
                            (root) foo()
                                (indirect) bar() at bci 1
                                    |_ immature receiver-type profile
                        """, writer.getOutput());

        writer = Writer.stringBuilder(OptionValues.builder().withAlwaysPrintInlinerReasoning(true).build());
        tree.write(writer);
        assertEquals("""
                        Optimization-context tree
                            Opt Foo
                            (root) foo()
                                (indirect) bar() at bci 1
                                    |_ reasoning: not inlined
                                    |_ immature receiver-type profile
                        """, writer.getOutput());
    }
}
