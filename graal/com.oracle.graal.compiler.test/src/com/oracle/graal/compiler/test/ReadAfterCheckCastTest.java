/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.test;

import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.Lowerable.*;
import com.oracle.graal.phases.common.*;

/* consider
 *     B b = (B) a;
 *     return b.x10;
 *
 * With snippets a typecheck is performed and if it was successful, a UnsafeCastNode is created.
 * For the read node, however, there is only a dependency to the UnsafeCastNode, but not to the
 * typecheck itself. With special crafting, it's possible to get the scheduler moving the
 * FloatingReadNode before the typecheck. Assuming the object is of the wrong type (here for
 * example A), an invalid field read is done.
 *
 * In order to avoid this situation, an anchor node is introduced in CheckCastSnippts.
 */

public class ReadAfterCheckCastTest extends GraphScheduleTest {

    public static long foo = 0;

    public static class A {

        public long x1;
    }

    public static class B extends A {

        public long x10;
    }

    public static long test1Snippet(A a) {
        if (foo > 4) {
            B b = (B) a;
            b.x10 += 1;
            return b.x10;
        } else {
            B b = (B) a;
            b.x10 += 1;
            return b.x10;
        }
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    private void test(final String snippet) {
        Debug.scope("FloatingReadTest", new DebugDumpScope(snippet), new Runnable() {

            // check shape of graph, with lots of assumptions. will probably fail if graph
            // structure changes significantly
            public void run() {
                StructuredGraph graph = parse(snippet);
                new LoweringPhase(runtime(), replacements, new Assumptions(false), LoweringType.BEFORE_GUARDS).apply(graph);
                new FloatingReadPhase().apply(graph);
                new EliminatePartiallyRedundantGuardsPhase(true, false).apply(graph);
                new ReadEliminationPhase().apply(graph);
                new CanonicalizerPhase.Instance(runtime(), null).apply(graph);

                Debug.dump(graph, "After lowering");

                ArrayList<MergeNode> merges = new ArrayList<>();
                ArrayList<FloatingReadNode> reads = new ArrayList<>();
                for (Node n : graph.getNodes()) {
                    if (n instanceof MergeNode) {
                        // check shape
                        MergeNode merge = (MergeNode) n;

                        if (merge.inputs().count() == 2) {
                            for (EndNode m : merge.forwardEnds()) {
                                if (m.predecessor() != null && m.predecessor() instanceof BeginNode && m.predecessor().predecessor() instanceof IfNode) {
                                    IfNode o = (IfNode) m.predecessor().predecessor();
                                    if (o.falseSuccessor().next() instanceof DeoptimizeNode) {
                                        merges.add(merge);
                                    }
                                }
                            }
                        }
                    }
                    if (n instanceof IntegerAddNode) {
                        IntegerAddNode ian = (IntegerAddNode) n;

                        Assert.assertTrue(ian.y() instanceof ConstantNode);
                        Assert.assertTrue(ian.x() instanceof FloatingReadNode);
                        reads.add((FloatingReadNode) ian.x());
                    }
                }

                Assert.assertTrue(merges.size() >= reads.size());
                for (int i = 0; i < reads.size(); i++) {
                    assertOrderedAfterSchedule(graph, merges.get(i), reads.get(i));
                }
            }
        });
    }
}
