/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;

import jdk.vm.ci.code.BailoutException;
import org.graalvm.polyglot.Context;

public class TruffleEnsureVirtualizedTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").build());
    }

    private abstract class TestNode extends AbstractTestNode {
        @Override
        public int execute(VirtualFrame frame) {
            executeVoid(frame);
            return 0;
        }

        public abstract void executeVoid(VirtualFrame frame);
    }

    private void testEnsureVirtualized(boolean bailoutExpected, TestNode node) {
        RootTestNode rootNode = new RootTestNode(new FrameDescriptor(), "ensureVirtualized", node);
        try {
            compileHelper("ensureVirtualized", rootNode, new Object[0]);
            if (bailoutExpected) {
                Assert.fail("Expected bailout exception due to ensureVirtualized");
            }
        } catch (BailoutException e) {
            if (!bailoutExpected) {
                throw e;
            }
        }
    }

    public static int intField;
    public static boolean booleanField;
    public static Object field;

    @Test
    public void test1() {
        testEnsureVirtualized(false, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                GraalDirectives.ensureVirtualized(object);
            }
        });
    }

    @Test
    public void test2() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                GraalDirectives.ensureVirtualized(object);
                field = object; // assert here
            }
        });
    }

    @Test
    public void test3() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                field = object;
                GraalDirectives.ensureVirtualized(object); // assert here
            }
        });
    }

    @Test
    public void testHere1() {
        testEnsureVirtualized(false, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                GraalDirectives.ensureVirtualizedHere(object);
            }
        });
    }

    @Test
    public void testHere2() {
        testEnsureVirtualized(false, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                GraalDirectives.ensureVirtualizedHere(object);
                field = object;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static Integer newInteger(int value) {
        return new Integer(value);
    }

    @Test
    public void testHere3() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                field = object;
                GraalDirectives.ensureVirtualizedHere(object); // assert here
            }
        });
    }

    @Test
    public void testBoxing1() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = intField;
                GraalDirectives.ensureVirtualizedHere(object); // assert here
            }
        });
    }

    @Test
    public void testBoxing2() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = intField;
                GraalDirectives.ensureVirtualized(object); // assert here
                field = object;
            }
        });
    }

    @Test
    public void testControlFlow1() {
        testEnsureVirtualized(false, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                if (booleanField) {
                    GraalDirectives.ensureVirtualized(object);
                }
                field = object;
            }
        });
    }

    @Test
    public void testControlFlow2() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                if (booleanField) {
                    GraalDirectives.ensureVirtualized(object);
                } else {
                    GraalDirectives.ensureVirtualized(object);
                }
                field = object; // assert here
            }
        });
    }

    @Test
    public void testControlFlow3() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                GraalDirectives.ensureVirtualized(object);
                if (booleanField) {
                    field = 1;
                } else {
                    field = 2;
                }
                field = object; // assert here
            }
        });
    }

    @Test
    public void testControlFlow4() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                if (booleanField) {
                    field = object;
                } else {
                    field = 2;
                }
                GraalDirectives.ensureVirtualized(object); // assert here
            }
        });
    }

    @Test
    public void testControlFlow5() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                if (booleanField) {
                    field = object;
                } else {
                    field = 2;
                }
                GraalDirectives.ensureVirtualizedHere(object); // assert here
            }
        });
    }

    public static final class TestClass {
        Object a;
        Object b;
    }

    @Test
    public void testIndirect1() {
        testEnsureVirtualized(true, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                TestClass t = new TestClass();
                t.a = object;
                GraalDirectives.ensureVirtualized(object);

                if (booleanField) {
                    field = t; // assert here
                } else {
                    field = 2;
                }
            }
        });
    }

    @Test
    public void testIndirect2() {
        testEnsureVirtualized(false, new TestNode() {
            @Override
            public void executeVoid(VirtualFrame frame) {
                Integer object = newInteger(intField);
                TestClass t = new TestClass();
                t.a = object;
                GraalDirectives.ensureVirtualized(t);

                if (booleanField) {
                    field = object;
                } else {
                    field = 2;
                }
            }
        });
    }
}
