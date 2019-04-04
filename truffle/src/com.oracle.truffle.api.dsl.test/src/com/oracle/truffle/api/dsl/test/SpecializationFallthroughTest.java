/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.array;
import static com.oracle.truffle.api.dsl.test.TestHelper.assertRuns;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest0Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest1Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest2Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest3Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest4Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest5Factory;
import com.oracle.truffle.api.dsl.test.TestHelper.TestExecutionListener;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class SpecializationFallthroughTest {

    @Test
    public void testFallthrough0() {
        assertRuns(FallthroughTest0Factory.getInstance(), //
                        array(0, 0, 1, 2), //
                        array(0, 0, 1, 2), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                if (!last) {
                                    return;
                                }
                                if (FallthroughTest0.fallthroughCount > 1) {
                                    Assert.fail("The fallthrough case must never be triggered twice. Therfore count must be <= 1, but is not.");
                                }
                            }
                        });
    }

    @NodeChildren({@NodeChild("a")})
    static class FallthroughTest0 extends ValueNode {

        static int fallthroughCount = 0;

        FallthroughTest0() {
            fallthroughCount = 0;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthroughCount++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Fallback
        Object doFallback(Object a) {
            return a;
        }
    }

    /*
     * Tests that the fall through is never triggered twice for monomorphic cases.
     */
    @Test
    public void testFallthrough1() {
        assertRuns(FallthroughTest1Factory.getInstance(), //
                        array(0, 0, 0, 1, 2), //
                        array(0, 0, 0, 1, 2), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                if (!last) {
                                    return;
                                }
                                if (FallthroughTest1.fallthroughCount > 1) {
                                    Assert.fail("The fallthrough case must never be triggered twice. Therfore count must be <= 1, but is not.");
                                }
                            }
                        });
    }

    /* TODO assert falltrough do1 before do2 */
    @NodeChildren({@NodeChild("a")})
    static class FallthroughTest1 extends ValueNode {

        static int fallthroughCount;

        FallthroughTest1() {
            fallthroughCount = 0;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthroughCount++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization
        int do2(int a) {
            return a;
        }

    }

    /*
     * Tests that the fall through is never triggered twice with two falltrhoughs in one operation.
     */
    @Test
    public void testFallthrough2() {
        assertRuns(FallthroughTest2Factory.getInstance(), //
                        array(0, 0, 1, 1, 2, 2), //
                        array(0, 0, 1, 1, 2, 2), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                if (!last) {
                                    return;
                                }
                                if (FallthroughTest2.fallthrough1 > 1) {
                                    Assert.fail();
                                }
                                if (FallthroughTest2.fallthrough2 > 1) {
                                    Assert.fail();
                                }
                                FallthroughTest2.fallthrough1 = 0;
                                FallthroughTest2.fallthrough2 = 0;
                            }
                        });
    }

    @NodeChildren({@NodeChild("a")})
    static class FallthroughTest2 extends ValueNode {

        static int fallthrough1;
        static int fallthrough2;

        @Specialization(rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthrough1++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int do2(int a) throws ArithmeticException {
            if (a == 1) {
                fallthrough2++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization
        int do3(int a) {
            return a;
        }
    }

    /*
     * Tests that the fall through is never triggered twice. In this case mixed fallthrough with
     * normal specializations.
     */
    @Test
    public void testFallthrough3() {
        assertRuns(FallthroughTest3Factory.getInstance(), //
                        array(0, 0, 1, 1, 2, 2), //
                        array(0, 0, 1, 1, 2, 2), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                if (!last) {
                                    return;
                                }
                                if (FallthroughTest3.fallthrough1 > 1) {
                                    Assert.fail(String.valueOf(FallthroughTest3.fallthrough1));
                                }
                                FallthroughTest3.fallthrough1 = 0;
                            }
                        });
    }

    @NodeChildren({@NodeChild("a")})
    static class FallthroughTest3 extends ValueNode {

        static int fallthrough1;

        boolean guard0(int a) {
            return a == 1;
        }

        @Specialization(guards = "guard0(a)")
        int do2(int a) {
            return a;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthrough1++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization
        int do3(int a) {
            return a;
        }

    }

    @Test
    public void testFallthrough4() {
        assertRuns(FallthroughTest4Factory.getInstance(), //
                        array(0, 0, 1, 1, 2, 2), //
                        array(0, 0, 1, 1, 2, 2), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                if (!last) {
                                    return;
                                }
                                if (FallthroughTest4.fallthrough1 > 1) {
                                    Assert.fail(String.valueOf(FallthroughTest4.fallthrough1));
                                }
                                if (FallthroughTest4.fallthrough2 > 1) {
                                    Assert.fail(String.valueOf(FallthroughTest4.fallthrough1));
                                }
                                FallthroughTest4.fallthrough1 = 0;
                                FallthroughTest4.fallthrough2 = 0;
                            }
                        });
    }

    @NodeChildren({@NodeChild("a")})
    static class FallthroughTest4 extends ValueNode {

        static int fallthrough1;
        static int fallthrough2;

        @Specialization(rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthrough1++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int do2(int a) throws ArithmeticException {
            if (a == 1) {
                fallthrough2++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization
        int do3(int a) {
            return a;
        }

    }

    @Test
    public void testFallthrough5() {
        assertRuns(FallthroughTest5Factory.getInstance(), //
                        array(0, 0, 1, 1, 2, 2), //
                        array(0, 0, 1, 1, 2, 2), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                if (!last) {
                                    return;
                                }
                                if (FallthroughTest5.fallthrough1 > 1) {
                                    Assert.fail(String.valueOf(FallthroughTest5.fallthrough1));
                                }
                                FallthroughTest5.fallthrough1 = 0;
                            }
                        });
    }

    @NodeChildren({@NodeChild("a")})
    static class FallthroughTest5 extends ValueNode {

        static int fallthrough1;

        @Specialization(guards = "isDo1(a)", rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthrough1++;
                throw new ArithmeticException();
            }
            return a;
        }

        protected static boolean isDo1(int a) {
            return a == 0 || a == 1;
        }

        @Specialization(guards = "isDo1(a)")
        int do2(int a) {
            return a;
        }

        @Specialization
        int do3(int a) {
            return a;
        }

    }

    /* Throwing RuntimeExceptions without rewriteOn is allowed. */
    @NodeChildren({@NodeChild("a")})
    static class FallthroughExceptionType0 extends ValueNode {

        @Specialization
        int do4(int a) throws RuntimeException {
            return a;
        }

    }

    /* Non runtime exceptions must be verified. */
    @NodeChildren({@NodeChild("a")})
    static class FallthroughExceptionType1 extends ValueNode {

        @ExpectError("A rewriteOn checked exception was specified but not thrown in the method's throws clause. The @Specialization method must specify a throws clause with the exception type 'java.lang.Throwable'.")
        @Specialization(rewriteOn = Throwable.class)
        int do4(int a) {
            return a;
        }

    }

    /* Checked exception must be verified. */
    @NodeChildren({@NodeChild("a")})
    static class FallthroughExceptionType2 extends ValueNode {

        @ExpectError("Specialization guard method or cache initializer declares an undeclared checked exception [java.lang.Throwable]. Only checked exceptions are allowed that were declared in the execute signature. Allowed exceptions are: [].")
        @Specialization
        int do4(int a) throws Throwable {
            return a;
        }

    }

}
