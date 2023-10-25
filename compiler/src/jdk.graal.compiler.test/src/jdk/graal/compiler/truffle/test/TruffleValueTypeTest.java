/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class TruffleValueTypeTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").build());
    }

    public abstract static class TestNode extends com.oracle.truffle.api.nodes.Node {
        abstract Object execute(VirtualFrame frame);
    }

    public static volatile TestType staticObject;
    public static volatile boolean b1 = true;
    public static volatile boolean b2 = false;
    public static volatile boolean b3 = true;
    public static volatile int i1 = 100;

    @ValueType
    public static final class TestType {
        int i;
        Object o;

        public TestType(int i, Object o) {
            this.i = i;
            this.o = o;
        }
    }

    @Test
    public void test1() {
        testValueType("test1", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t = new TestType(0, "foo");
                return t.i;
            }
        });
    }

    @Test
    public void test2() {
        testValueType("test2", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t = new TestType(0, "foo");
                return t.o;
            }
        });
    }

    @Test
    public void test3() {
        testValueType("test3", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t;
                if (b1) {
                    t = new TestType(0, "foo");
                } else {
                    t = new TestType(1, "bar");
                }
                return t.o;
            }
        });
    }

    @Test
    public void test4() {
        testValueType("test4", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t = new TestType(0, "foo");
                if (b1) {
                    t = new TestType(1, "bar");
                }
                if (b2) {
                    t = new TestType(2, "baz");
                }
                if (b3) {
                    t = new TestType(3, "ba");
                }
                return t.o;
            }
        });
    }

    @Test
    public void test5() {
        testValueType("test5", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t = new TestType(0, "foo");
                TestType t2 = t;
                if (b1) {
                    t = new TestType(1, "bar");
                }
                if (b2) {
                    t = new TestType(2, "baz");
                }
                if (b3) {
                    t = new TestType(3, "ba");
                }
                return t.o == t2.o;
            }
        });
    }

    @Test
    public void test6() {
        testValueType("test6", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t = new TestType(0, "foo");
                TestType t1 = new TestType(1, "bar");
                TestType t2 = new TestType(2, "baz");
                TestType t3 = new TestType(3, "ba");
                if (b1) {
                    t = t1;
                }
                if (b2) {
                    t = t2;
                }
                if (b3) {
                    t = t3;
                }
                return t1.i + t2.i + t3.i + t.i;
            }
        });
    }

    @Test
    public void test7() {
        testValueType("test7", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t = new TestType(0, "foo");
                TestType tSum = new TestType(0, "foo");

                for (int i = 0; i < i1; i++) {
                    tSum.i += t.i;
                    if (b1) {
                        t = new TestType(1, "bar");
                    }
                }
                return t.i + tSum.i;
            }
        });
    }

    @Test
    public void test8() {
        testValueType("test8", new TestNode() {
            @Override
            public Object execute(VirtualFrame frame) {
                TestType t = new TestType(0, "foo");
                TestType t1 = new TestType(1, "bar");
                TestType tSum = new TestType(0, "foo");

                for (int i = 0; i < i1; i++) {
                    tSum.i += t.i;
                    if (b1) {
                        t = t1;
                    }
                }
                return t.i + tSum.i;
            }
        });
    }

    private void testValueType(String name, TestNode node) {
        RootNode rootNode = new RootNode(null) {
            @Child private TestNode child = node;

            @Override
            public String toString() {
                return name;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(frame);
            }
        };

        StructuredGraph graph = partialEval(rootNode, new Object[0]);

        new PartialEscapePhase(false, false, createCanonicalizerPhase(), null, graph.getOptions()).apply(graph, getDefaultHighTierContext());

        // there should not be any allocations that aren't used directly by a return
        Assert.assertEquals(0, graph.getNodes().filter(n -> {
            if (n instanceof CommitAllocationNode) {
                for (Node alloc : n.usages()) {
                    if (alloc.usages().filter(u -> !(u instanceof ReturnNode)).count() > 0) {
                        return true;
                    }
                }
            } else if (n instanceof VirtualizableAllocation) {
                if (n.usages().filter(u -> !(u instanceof ReturnNode)).count() > 0) {
                    return true;
                }
            }
            return false;
        }).count());
    }
}
