/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;

import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.vm.ci.meta.SpeculationLog;

public class FrameAccessVerificationTest extends PartialEvaluationTest {

    @CompilerDirectives.TruffleBoundary
    static void boundary() {
    }

    public volatile Object sink;

    private static final class Result {
        final Object result;
        final Object[] arguments;

        Result(Object result, Object[] arguments) {
            this.result = result;
            this.arguments = arguments;
        }
    }

    private static Result result(Object result, Object... args) {
        return new Result(result, args);
    }

    @Test
    public void invalidMerge() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "invalidMerge";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * Merging different types leads to inefficient code that needs to deopt and
                 * invalidate the frame intrinsics speculation.
                 */
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setInt(slot, 1);
                } else {
                    frame.setDouble(slot, 2d);
                }
                boundary();
                if ((boolean) args[1]) {
                    return frame.getInt(slot);
                } else {
                    return frame.getDouble(slot);
                }
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 1);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1, true, true), result(2d, false, false), result(FrameSlotTypeException.class, false, true), result(FrameSlotTypeException.class, true, false));
    }

    @Test
    public void repeatedClear() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "repeatedClear";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * Merging different types leads to inefficient code that needs to deopt and
                 * invalidate the frame intrinsics speculation.
                 */
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setInt(slot, 1);
                }
                boundary();
                frame.clear(slot);
                if ((boolean) args[0]) {
                    frame.setDouble(slot, 1);
                }
                boundary();
                frame.clear(slot);
                if ((boolean) args[0]) {
                    frame.setFloat(slot, 1);
                }
                boundary();
                frame.clear(slot);
                if ((boolean) args[0]) {
                    frame.setObject(slot, 1);
                }
                boundary();
                frame.clear(slot);
                if ((boolean) args[0]) {
                    frame.setInt(slot, 1);
                }
                boundary();
                if ((boolean) args[1]) {
                    return frame.getInt(slot);
                } else {
                    return null;
                }
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1, true, true), result(null, false, false), result(FrameSlotTypeException.class, false, true), result(null, true, false));
    }

    @Test
    public void testLoop() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "testLoop";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * Merging at the loop header - the analysis needs to recognize that the starting
                 * type is int.
                 */
                Object[] args = frame.getArguments();
                for (int i = 0; i < (int) args[0]; i++) {
                    frame.setInt(slot, 16 * i);
                }
                boundary();
                return frame.getInt(slot);
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(0, 1), result(16, 2), result(FrameSlotTypeException.class, 0));
    }

    @Test
    public void testDeoptLoop() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "testDeoptLoop";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * This deopts either at the LoopBeginNode or at the LoopEndNode.
                 */
                Object[] args = frame.getArguments();
                frame.setLong(slot, -1);
                for (int i = 0; i < (int) args[0]; i++) {
                    frame.setInt(slot, 16 * i);
                }
                boundary();
                return frame.getInt(slot);
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 1);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(0, 1), result(16, 2), result(FrameSlotTypeException.class, 0));
    }

    @Test
    public void objectIntMerge() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "invalidMerge";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * Merging an object and a primitive type is acceptable.
                 */
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setInt(slot, 1);
                } else {
                    frame.setObject(slot, 2d);
                }
                boundary();
                if ((boolean) args[1]) {
                    return frame.getInt(slot);
                } else {
                    return frame.getObject(slot);
                }
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1, true, true), result(2d, false, false), result(FrameSlotTypeException.class, false, true), result(FrameSlotTypeException.class, true, false));
    }

    @Test
    public void switchKinds() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "switchKinds";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * Switching between types in a linear piece of code is possible.
                 */
                Object[] args = frame.getArguments();
                double total = 0;
                frame.setByte(slot, (byte) args[0]);
                boundary();
                total += frame.getByte(slot);
                boundary();
                frame.setInt(slot, (int) args[1]);
                boundary();
                total += frame.getInt(slot);
                boundary();
                frame.setLong(slot, (long) args[2]);
                boundary();
                total += frame.getLong(slot);
                boundary();
                frame.setDouble(slot, (double) args[3]);
                boundary();
                total += frame.getDouble(slot);
                boundary();
                frame.setLong(slot, (long) args[4]);
                boundary();
                total += frame.getLong(slot);
                boundary();
                frame.setFloat(slot, (float) args[5]);
                boundary();
                total += frame.getFloat(slot);
                boundary();
                return total;
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(21d, (byte) 1, 2, 3L, 4d, 5L, 6f));
    }

    @Test
    public void clearBeforeMerge() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "clearBeforeMerge";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * The same frame slot is used with different types in the branches, but clearing it
                 * before the merge (as a liveness analysis might do) prevents issues at the merge.
                 */
                Object[] args = frame.getArguments();
                Object v;
                if ((boolean) args[0]) {
                    frame.setInt(slot, 1);
                    boundary();
                    v = frame.getInt(slot);
                    frame.clear(slot);
                } else {
                    frame.setObject(slot, "foo");
                    boundary();
                    v = frame.getObject(slot);
                    frame.clear(slot);
                }
                boundary();
                if ((boolean) args[1]) {
                    return (int) v;
                } else {
                    return v;
                }
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1, true, true), result("foo", false, false), result(ClassCastException.class, false, true));
    }

    @Test
    public void autoInitializeAtMerge() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "autoInitializeAtMerge";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setFloat(slot, 1f);
                    boundary();
                } else {
                    /*
                     * The analysis needs to detect that the slot must be initialized with "float".
                     */
                    boundary();
                }
                boundary();
                if ((boolean) args[1]) {
                    return frame.getFloat(slot);
                } else {
                    return null;
                }
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1f, true, true), result(null, false, false), result(FrameSlotTypeException.class, false, true), result(null, true, false));
    }

    @Test
    public void getValueSimple() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot1 = builder.addSlot(FrameSlotKind.Illegal, null, null);
        int slot2 = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "getValueSimple";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                frame.setInt(slot1, 0);
                boundary();
                sink = frame.getValue(slot1);
                boundary();
                frame.setObject(slot2, "foo");
                boundary();
                return null;
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(null, true));
    }

    @Test
    public void getValueComplex() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot1 = builder.addSlot(FrameSlotKind.Illegal, null, null);
        int slot2 = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "getValueComplex";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                if ((boolean) frame.getArguments()[0]) {
                    frame.setFloat(slot1, 1);
                } else {
                    frame.setInt(slot1, 2);
                }
                boundary();
                Object result = frame.getValue(slot1);
                boundary();
                frame.setObject(slot2, "foo");
                boundary();
                return result;
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 1);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1f, true), result(2, false));
    }

    @Test
    public void getValue() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot1 = builder.addSlot(FrameSlotKind.Illegal, null, null);
        int slot2 = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "getValue";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                Object[] ret = new Object[7];
                frame.setInt(slot1, 0);
                boundary();
                ret[0] = frame.getValue(slot1);
                boundary();
                frame.setBoolean(slot1, true);
                boundary();
                ret[1] = frame.getValue(slot1);
                boundary();
                frame.setLong(slot1, 2);
                boundary();
                ret[2] = frame.getValue(slot1);
                boundary();
                frame.setFloat(slot1, 3);
                boundary();
                ret[3] = frame.getValue(slot1);
                boundary();
                frame.setDouble(slot1, 4);
                boundary();
                ret[4] = frame.getValue(slot1);
                boundary();
                frame.setByte(slot1, (byte) 5);
                boundary();
                ret[5] = frame.getValue(slot1);
                boundary();
                frame.setObject(slot1, "six");
                boundary();
                ret[6] = frame.getValue(slot1);
                boundary();
                frame.setObject(slot2, "foo");
                boundary();
                return ret;
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(new Object[]{0, true, 2L, 3F, 4D, (byte) 5, "six"}, true));
    }

    @Test
    public void materializedWrite() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot1 = builder.addSlot(FrameSlotKind.Int, null, null);
        FrameDescriptor fd = builder.build();
        // materialize the frame
        Truffle.getRuntime().createVirtualFrame(new Object[0], fd).materialize();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "materializedWrite";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                frame.setInt(slot1, (int) frame.getArguments()[0]);
                boundary();
                return frame.getInt(slot1);
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1, 1));
    }

    @Test
    public void materializedMerge() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot1 = builder.addSlot(FrameSlotKind.Int, null, null);
        FrameDescriptor fd = builder.build();
        // materialize the frame
        Truffle.getRuntime().createVirtualFrame(new Object[0], fd).materialize();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "materializedMerge";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                if (((int) frame.getArguments()[0]) == 1) {
                    frame.setLong(slot1, (int) frame.getArguments()[0]);
                } else {
                    frame.setInt(slot1, (int) frame.getArguments()[0]);
                }
                boundary();
                return 1;
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1, 1), result(1, 2));
    }

    @Test
    public void materializedLoop() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot1 = builder.addSlot(FrameSlotKind.Int, null, null);
        int slot2 = builder.addSlot(FrameSlotKind.Illegal, null, null);
        FrameDescriptor fd = builder.build();
        // materialize the frame
        Truffle.getRuntime().createVirtualFrame(new Object[0], fd).materialize();
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "materializedLoop";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                frame.setInt(slot1, 0);
                frame.setFloat(slot2, 0);
                int count = (int) frame.getArguments()[0];
                while (frame.getInt(slot1) < count) {
                    frame.setFloat(slot2, frame.getFloat(slot2) + count);
                    frame.setInt(slot1, frame.getInt(slot1) + 1);
                    boundary();
                }
                return frame.getFloat(slot2);
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertNoConverts(graph);
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1f, 1), result(25f, 5));
    }

    private static void assertDeoptCount(StructuredGraph graph, int expected) {
        if (expected == 0) {
            graph.getNodes(DeoptimizeNode.TYPE) //
                            .filter(n -> !(n.predecessor() instanceof ExceptionObjectNode)) //
                            .filter(n -> !((DeoptimizeNode) n).getSpeculation().equals(SpeculationLog.NO_SPECULATION)).forEach(n -> System.out.println(n));
            graph.getNodes(FixedGuardNode.TYPE) //
                            .filter(n -> !((FixedGuardNode) n).getSpeculation().equals(SpeculationLog.NO_SPECULATION)).forEach(n -> System.out.println(n));
        }
        int deoptCount = graph.getNodes(DeoptimizeNode.TYPE) //
                        .filter(n -> !(n.predecessor() instanceof ExceptionObjectNode)) //
                        .filter(n -> !((DeoptimizeNode) n).getSpeculation().equals(SpeculationLog.NO_SPECULATION)).count();
        int guardCount = graph.getNodes(FixedGuardNode.TYPE) //
                        .filter(n -> !((FixedGuardNode) n).getSpeculation().equals(SpeculationLog.NO_SPECULATION)).count();
        Assert.assertEquals("number of non-exception deoptimizations with speculation", expected, deoptCount + guardCount);
    }

    private static void assertReturn(StructuredGraph graph) {
        Assert.assertTrue("return node needs to exist", graph.getNodes(ReturnNode.TYPE).isNotEmpty());
    }

    private static void assertNoConverts(StructuredGraph graph) {
        Assert.assertEquals(0, graph.getNodes().filter(ReinterpretNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(NarrowNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(ZeroExtendNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(SignExtendNode.class).count());
    }

    private static void assertAllocations(StructuredGraph graph) {
        Assert.assertTrue("no frame object allocations", graph.getNodes().filter(AllocatedObjectNode.class) //
                        .filter(n -> n.getUsageCount() != 1 || !(n.usages().first() instanceof ReturnNode)).isEmpty());
        Assert.assertTrue("no frame object allocations", graph.getNodes().filter(AbstractNewObjectNode.class).isEmpty());
    }

    private void doTest(RootNode rootNode, Consumer<StructuredGraph> graphChecker, Result... results) {
        Assume.assumeFalse("speculations only apply for first compilation, CompileImmediately causes repeated compilations", System.getProperties().containsKey("polyglot.engine.CompileImmediately"));
        OptimizedCallTarget callTarget = (OptimizedCallTarget) rootNode.getCallTarget();
        executeAndCheck(callTarget, results);

        StructuredGraph graph = partialEval(callTarget, results[0].arguments);
        graphChecker.accept(graph);
        compile(callTarget, graph);
        Assert.assertTrue("CallTarget is valid", callTarget.isValid());
        executeAndCheck(callTarget, results);
    }

    private static void executeAndCheck(RootCallTarget callTarget, Result... results) {
        for (Result result : results) {
            try {
                Object value = callTarget.call(result.arguments);
                if (result.result instanceof Object[]) {
                    Object[] expected = (Object[]) result.result;
                    Object[] actual = (Object[]) value;
                    Assert.assertEquals(expected.length, actual.length);
                    for (int i = 0; i < expected.length; i++) {
                        Assert.assertEquals(expected[i].getClass(), actual[i].getClass());
                        Assert.assertEquals(expected[i], actual[i]);
                    }
                } else {
                    if (result.result != null && value != null) {
                        Assert.assertEquals(result.result.getClass(), value.getClass());
                    }
                    Assert.assertEquals(result.result, value);
                }
            } catch (Throwable e) {
                if (result.result != e.getClass()) {
                    e.printStackTrace();
                    Assert.assertEquals(result.result, e.getClass());
                }
            }
        }
    }
}
