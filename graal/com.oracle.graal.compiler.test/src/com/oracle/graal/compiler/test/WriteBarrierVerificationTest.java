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
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.Lowerable.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.graph.ReentrantNodeIterator.*;
import com.oracle.graal.phases.tiers.*;

public class WriteBarrierVerificationTest extends GraalCompilerTest {

    public static int barrierIndex;

    public static class Container {

        public Container a;
        public Container b;
    }

    private static native void safepoint();

    public static void test1Snippet() {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        barrierIndex = 0;
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        safepoint();
        barrierIndex = 2;
        main.b = temp2;
        safepoint();
    }

    public static void test2Snippet() {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        barrierIndex = 0;
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        barrierIndex = 2;
        main.b = temp2;
        safepoint();
    }

    public static void test3Snippet(boolean test) {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        barrierIndex = 0;
        safepoint();
        for (int i = 0; i < 10; i++) {
            if (test) {
                barrierIndex = 1;
                main.a = temp1;
                barrierIndex = 2;
                main.b = temp2;
            } else {
                barrierIndex = 3;
                main.a = temp1;
                barrierIndex = 4;
                main.b = temp2;
            }
        }
    }

    public static void test4Snippet(boolean test) {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        for (int i = 0; i < 10; i++) {
            if (test) {
                barrierIndex = 2;
                main.a = temp1;
                barrierIndex = 3;
                main.b = temp2;
            } else {
                barrierIndex = 4;
                main.a = temp2;
                barrierIndex = 5;
                main.b = temp1;
            }
        }
    }

    public static void test5Snippet() {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        if (main.a == main.b) {
            barrierIndex = 2;
            main.a = temp1;
            barrierIndex = 3;
            main.b = temp2;
        } else {
            barrierIndex = 4;
            main.a = temp2;
            barrierIndex = 5;
            main.b = temp1;
        }
        safepoint();
    }

    public static void test6Snippet(boolean test) {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        if (test) {
            barrierIndex = 2;
            main.a = temp1;
            barrierIndex = 3;
            main.b = temp1.a.a;
        } else {
            barrierIndex = 4;
            main.a = temp2;
            barrierIndex = 5;
            main.b = temp2.a.a;
        }
        safepoint();
    }

    public static void test7Snippet(boolean test) {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        barrierIndex = 1;
        main.a = temp1;
        if (test) {
            barrierIndex = 2;
            main.a = temp1;
        }
        barrierIndex = 3;
        main.b = temp2;
        safepoint();
    }

    public static void test8Snippet(boolean test) {
        Container main = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        if (test) {
            barrierIndex = 1;
            main.a = temp1;
        }
        barrierIndex = 2;
        main.b = temp2;
        safepoint();
    }

    public static void test9Snippet(boolean test) {
        Container main1 = new Container();
        Container main2 = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        if (test) {
            barrierIndex = 1;
            main1.a = temp1;
        } else {
            barrierIndex = 2;
            main2.a = temp1;
        }
        barrierIndex = 3;
        main1.b = temp2;
        barrierIndex = 4;
        main2.b = temp2;
        safepoint();
    }

    public static void test10Snippet(boolean test) {
        Container main1 = new Container();
        Container main2 = new Container();
        Container temp1 = new Container();
        Container temp2 = new Container();
        safepoint();
        if (test) {
            barrierIndex = 1;
            main1.a = temp1;
            barrierIndex = 2;
            main2.a = temp2;
        } else {
            barrierIndex = 3;
            main2.a = temp1;
        }
        barrierIndex = 4;
        main1.b = temp2;
        barrierIndex = 5;
        main2.b = temp2;
        safepoint();
    }

    @Test(expected = AssertionError.class)
    public void test1() {
        test("test1Snippet", 2, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test2() {
        test("test1Snippet", 2, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test3() {
        test("test2Snippet", 2, new int[]{1});
    }

    @Test
    public void test4() {
        test("test2Snippet", 2, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test5() {
        test("test3Snippet", 4, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test6() {
        test("test3Snippet", 4, new int[]{3, 4});
    }

    @Test(expected = AssertionError.class)
    public void test7() {
        test("test3Snippet", 4, new int[]{1});
    }

    @Test
    public void test8() {
        test("test3Snippet", 4, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test9() {
        test("test3Snippet", 4, new int[]{3});
    }

    @Test
    public void test10() {
        test("test3Snippet", 4, new int[]{4});
    }

    @Test
    public void test11() {
        test("test4Snippet", 5, new int[]{2, 3});
    }

    @Test
    public void test12() {
        test("test4Snippet", 5, new int[]{4, 5});
    }

    @Test(expected = AssertionError.class)
    public void test13() {
        test("test4Snippet", 5, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test14() {
        test("test5Snippet", 5, new int[]{1});
    }

    @Test
    public void test15() {
        test("test5Snippet", 5, new int[]{2});
    }

    @Test
    public void test16() {
        test("test5Snippet", 5, new int[]{4});
    }

    @Test
    public void test17() {
        test("test5Snippet", 5, new int[]{3});
    }

    @Test
    public void test18() {
        test("test5Snippet", 5, new int[]{5});
    }

    @Test
    public void test19() {
        test("test5Snippet", 5, new int[]{2, 3});
    }

    @Test
    public void test20() {
        test("test5Snippet", 5, new int[]{4, 5});
    }

    @Test(expected = AssertionError.class)
    public void test21() {
        test("test6Snippet", 5, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test22() {
        test("test6Snippet", 5, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test23() {
        test("test6Snippet", 5, new int[]{3});
    }

    @Test
    public void test24() {
        test("test6Snippet", 5, new int[]{4});
    }

    @Test
    public void test25() {
        test("test7Snippet", 3, new int[]{2});
    }

    @Test
    public void test26() {
        test("test7Snippet", 3, new int[]{3});
    }

    @Test
    public void test27() {
        test("test7Snippet", 3, new int[]{2, 3});
    }

    @Test(expected = AssertionError.class)
    public void test28() {
        test("test7Snippet", 3, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test29() {
        test("test8Snippet", 2, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test30() {
        test("test8Snippet", 2, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test31() {
        test("test8Snippet", 2, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test32() {
        test("test9Snippet", 4, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test33() {
        test("test9Snippet", 4, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test34() {
        test("test9Snippet", 4, new int[]{3});
    }

    @Test(expected = AssertionError.class)
    public void test35() {
        test("test9Snippet", 4, new int[]{4});
    }

    @Test(expected = AssertionError.class)
    public void test36() {
        test("test9Snippet", 4, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test37() {
        test("test9Snippet", 4, new int[]{3, 4});
    }

    @Test(expected = AssertionError.class)
    public void test38() {
        test("test10Snippet", 5, new int[]{1});
    }

    @Test(expected = AssertionError.class)
    public void test39() {
        test("test10Snippet", 5, new int[]{2});
    }

    @Test(expected = AssertionError.class)
    public void test40() {
        test("test10Snippet", 5, new int[]{3});
    }

    @Test(expected = AssertionError.class)
    public void test41() {
        test("test10Snippet", 5, new int[]{4});
    }

    @Test
    public void test42() {
        test("test10Snippet", 5, new int[]{5});
    }

    @Test(expected = AssertionError.class)
    public void test43() {
        test("test10Snippet", 5, new int[]{1, 2});
    }

    @Test(expected = AssertionError.class)
    public void test44() {
        test("test10Snippet", 5, new int[]{1, 2, 3});
    }

    @Test(expected = AssertionError.class)
    public void test45() {
        test("test10Snippet", 5, new int[]{3, 4});
    }

    private void test(final String snippet, final int expectedBarriers, final int... removedBarrierIndices) {
        Debug.scope("WriteBarrierVerificationTest", new DebugDumpScope(snippet), new Runnable() {

            public void run() {
                final StructuredGraph graph = parse(snippet);
                HighTierContext highTierContext = new HighTierContext(runtime(), new Assumptions(false), replacements);
                MidTierContext midTierContext = new MidTierContext(runtime(), new Assumptions(false), replacements, runtime().getTarget());

                new LoweringPhase(LoweringType.BEFORE_GUARDS).apply(graph, highTierContext);
                new GuardLoweringPhase().apply(graph, midTierContext);
                new SafepointInsertionPhase().apply(graph);
                new WriteBarrierAdditionPhase().apply(graph);

                final int barriers = graph.getNodes(SerialWriteBarrier.class).count();

                Assert.assertTrue(expectedBarriers == barriers);
                class State {

                    boolean removeBarrier = false;

                }
                NodeIteratorClosure<State> closure = new NodeIteratorClosure<State>() {

                    @Override
                    protected void processNode(FixedNode node, State currentState) {
                        if (node instanceof WriteNode) {
                            WriteNode write = (WriteNode) node;
                            Object obj = write.getLocationIdentities()[0];
                            if (obj instanceof ResolvedJavaField) {
                                if (((ResolvedJavaField) obj).getName().equals("barrierIndex")) {
                                    if (eliminateBarrier(write.value().asConstant().asInt(), removedBarrierIndices)) {
                                        currentState.removeBarrier = true;
                                    }
                                }
                            }
                        } else if (node instanceof SerialWriteBarrier) {
                            if (currentState.removeBarrier) {
                                graph.removeFixed(((SerialWriteBarrier) node));
                                currentState.removeBarrier = false;
                            }
                        }
                    }

                    private boolean eliminateBarrier(int index, int[] map) {
                        for (int i = 0; i < map.length; i++) {
                            if (map[i] == index) {
                                return true;
                            }
                        }
                        return false;
                    }

                    @Override
                    protected Map<LoopExitNode, State> processLoop(LoopBeginNode loop, State initialState) {
                        return ReentrantNodeIterator.processLoop(this, loop, initialState).exitStates;
                    }

                    @Override
                    protected State merge(MergeNode merge, List<State> states) {
                        return new State();
                    }

                    @Override
                    protected State afterSplit(BeginNode node, State oldState) {
                        return new State();
                    }
                };

                try {
                    ReentrantNodeIterator.apply(closure, graph.start(), new State(), null);
                    new WriteBarrierVerificationPhase().apply(graph);
                } catch (AssertionError error) {
                    Assert.assertTrue(error.getMessage().equals("Write barrier must be present"));
                    throw new AssertionError();
                }

            }
        });
    }
}
