package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest0Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest1Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest2Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest3Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest4Factory;
import com.oracle.truffle.api.dsl.test.SpecializationFallthroughTestFactory.FallthroughTest5Factory;
import com.oracle.truffle.api.dsl.test.TestHelper.ExecutionListener;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;

public class SpecializationFallthroughTest {

    @Test
    public void testFallthrough0() {
        assertRuns(FallthroughTest0Factory.getInstance(), //
                        array(0, 0, 1, 2), //
                        array(0, 0, 1, 2),//
                        new ExecutionListener() {
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

        public FallthroughTest0() {
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

        @Generic
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
                        array(0, 0, 0, 1, 2),//
                        new ExecutionListener() {
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

        public FallthroughTest1() {
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
                        array(0, 0, 1, 1, 2, 2),//
                        new ExecutionListener() {
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

        @Specialization(order = 1, rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthrough1++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization(order = 2, rewriteOn = ArithmeticException.class)
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
                        array(0, 0, 1, 1, 2, 2),//
                        new ExecutionListener() {
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

        @Specialization(guards = "guard0")
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
                        array(0, 0, 1, 1, 2, 2),//
                        new ExecutionListener() {
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

        @Specialization(order = 1, rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            if (a == 0) {
                fallthrough1++;
                throw new ArithmeticException();
            }
            return a;
        }

        @Specialization(order = 2, rewriteOn = ArithmeticException.class)
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
                        array(0, 0, 1, 1, 2, 2),//
                        new ExecutionListener() {
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

        @Specialization(guards = "isDo1", rewriteOn = ArithmeticException.class)
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

        @Specialization(guards = "isDo1")
        int do2(int a) {
            return a;
        }

        @Specialization
        int do3(int a) {
            return a;
        }

    }

    @NodeChildren({@NodeChild("a")})
    static class FallthroughTest6 extends ValueNode {

        static int fallthrough1;
        static int fallthrough2;
        static int fallthrough3;
        static int fallthrough4;

        @Specialization(order = 1, rewriteOn = ArithmeticException.class)
        int do4(int a) throws ArithmeticException {
            return a;
        }

        @Specialization(order = 2, rewriteOn = ArithmeticException.class)
        int do2(int a) throws ArithmeticException {
            return a;
        }

        @Specialization(order = 3, rewriteOn = ArithmeticException.class)
        int do3(int a) throws ArithmeticException {
            return a;
        }

        @Specialization(order = 4, rewriteOn = ArithmeticException.class)
        int do1(int a) throws ArithmeticException {
            return a;
        }

        @Specialization
        double do5(double a) {
            return a;
        }

    }

}
