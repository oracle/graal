/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;

import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.virtual.AllocatedObjectNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.SpeculationLog;

public class FrameAccessVerificationTest extends PartialEvaluationTest {

    @CompilerDirectives.TruffleBoundary
    static void boundary() {
    }

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

    @SuppressWarnings("deprecation")
    @Test
    public void invalidMergeLegacy() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "invalidMergeLegacy";
            }

            @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Test
    public void testIllegalLegacy() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "testIllegalLegacy";
            }

            @SuppressWarnings("deprecation")
            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * "Illegal" needs to be treated like "long".
                 */
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setLong(slot, (long) args[2]);
                } else {
                    // empty
                }
                boundary();
                if ((boolean) args[1]) {
                    return frame.getLong(slot);
                } else {
                    return 0;
                }
            }
        };
        Consumer<StructuredGraph> graphChecker = graph -> {
            assertDeoptCount(graph, 0);
            assertReturn(graph);
            assertAllocations(graph);
        };
        doTest(root, graphChecker, result(1L, true, true, 1L), result(0, false, false, 1L), result(FrameSlotTypeException.class, false, true, 1L), result(0, true, false, 1L));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testLoopLegacy1() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "testLoopLegacy1";
            }

            @SuppressWarnings("deprecation")
            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * Merging at the loop header.
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

    @SuppressWarnings("deprecation")
    @Test
    public void testLoopLegacy2() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Int);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "testLoopLegacy2";
            }

            @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Test
    public void testDeoptLoopLegacy() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "testDeoptLoopLegacy";
            }

            @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Test
    public void objectIntMergeLegacy1() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "invalidMergeLegacy1";
            }

            @SuppressWarnings("deprecation")
            @Override
            public Object execute(VirtualFrame frame) {
                /*
                 * "int" conflicts with the FrameSlot's "illegal" kind.
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

    @SuppressWarnings("deprecation")
    @Test
    public void objectIntMergeLegacy2() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Int);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "invalidMergeLegacy2";
            }

            @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Test
    public void switchKindsLegacy() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "switchKindsLegacy";
            }

            @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Test
    public void clearBeforeMergeLegacy() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "clearBeforeMergeLegacy";
            }

            @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    @Test
    public void autoInitializeAtMergeLegacy1() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Illegal);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "autoInitializeAtMergeLegacy1";
            }

            @SuppressWarnings("deprecation")
            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setFloat(slot, 1f);
                    boundary();
                } else {
                    /*
                     * "float" conflicts with the FrameSlot's "illegal" kind.
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

    @SuppressWarnings("deprecation")
    @Test
    public void autoInitializeAtMergeLegacy2() {
        FrameDescriptor fd = new FrameDescriptor();
        com.oracle.truffle.api.frame.FrameSlot slot = fd.addFrameSlot("slot", FrameSlotKind.Float);
        RootNode root = new RootNode(null, fd) {
            @Override
            public String toString() {
                return "autoInitializeAtMergeLegacy2";
            }

            @SuppressWarnings("deprecation")
            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setFloat(slot, 1f);
                    boundary();
                } else {
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

    private static void assertDeoptCount(StructuredGraph graph, int expected) {
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

    private static void assertAllocations(StructuredGraph graph) {
        Assert.assertTrue("no frame object allocations", graph.getNodes().filter(CommitAllocationNode.class).isEmpty());
        Assert.assertTrue("no frame object allocations", graph.getNodes().filter(AllocatedObjectNode.class).isEmpty());
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
                Assert.assertEquals(result.result, value);
            } catch (Throwable e) {
                if (result.result != e.getClass()) {
                    e.printStackTrace();
                    Assert.assertEquals(result.result, e.getClass());
                }
            }
        }
    }
}
