/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.truffle.test;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.virtual.EscapeObjectState;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.virtual.nodes.MaterializedObjectState;
import org.graalvm.compiler.virtual.nodes.VirtualObjectState;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ClearFrameSlotTest extends PartialEvaluationTest {
    private static final Consumer<StructuredGraph> voidChecker = (g) -> {
    };
    private static final Object[] emptyArgs = new Object[]{};
    private static final Object[] trueArg = new Object[]{true};

    interface FSChecker {
        void check(ValueNode tag, ValueNode object, ValueNode primitive);
    }

    private static final FSChecker regularFSChecker = new FSChecker() {
        @Override
        public void check(ValueNode tag, ValueNode object, ValueNode primitive) {
            if (tag == null) {
                return;
            }
            if (tag.isJavaConstant()) {
                if (tag.asJavaConstant().asInt() == (int) FrameSlotKind.Illegal.tag) {
                    assertTrue(object.isNullConstant());
                    assertTrue(primitive.isJavaConstant());
                } else if (tag.asJavaConstant().asInt() == (int) FrameSlotKind.Object.tag) {
                    assertTrue(primitive.isJavaConstant());
                } else {
                    assertTrue(object.isNullConstant());
                }
            }
        }
    };

    private static final FSChecker noClearPhiChecker = new FSChecker() {
        @Override
        public void check(ValueNode tag, ValueNode object, ValueNode primitive) {
            if (tag == null) {
                return;
            }
            if (tag.isJavaConstant()) {
                return;
            }
            PrimitiveStamp stamp = (PrimitiveStamp) tag.stamp(NodeView.DEFAULT);
            if (!stamp.join(StampFactory.forInteger(stamp.getBits(), (int) FrameSlotKind.Object.tag, (int) FrameSlotKind.Object.tag)).isEmpty()) {
                assertFalse(object.isNullConstant());
                assertFalse(primitive.asJavaConstant() == null || primitive.asJavaConstant().asInt() != 0);
            }
        }
    };

    @CompilerDirectives.TruffleBoundary
    static void boundary() {
    }

    private static Consumer<StructuredGraph> graphFSChecker(FSChecker fsChecker) {
        return new Consumer<StructuredGraph>() {
            int tagArrayIndex = -1;
            int objectArrayIndex = -1;
            int primitiveArrayIndex = -1;

            @Override
            public void accept(StructuredGraph graph) {
                for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
                    EconomicMap<VirtualObjectNode, EscapeObjectState> objectStates = getObjectStateMappings(fs);
                    for (EscapeObjectState objectState : objectStates.getValues()) {
                        if ((objectState instanceof VirtualObjectState) && objectState.object().type().getName().contains("FrameWithoutBoxing")) {
                            initIndexes(objectState.object().type());
                            VirtualObjectState vObjState = (VirtualObjectState) objectState;
                            ValueNode tagArrayValue = vObjState.values().get(tagArrayIndex);
                            if ((tagArrayValue instanceof VirtualArrayNode) &&
                                            (vObjState.values().get(objectArrayIndex) instanceof VirtualArrayNode) &&
                                            (vObjState.values().get(primitiveArrayIndex) instanceof VirtualArrayNode)) {
                                // make sure everything is virtual

                                EscapeObjectState tagArrayVirtual = objectStates.get((VirtualArrayNode) tagArrayValue);
                                EscapeObjectState objectArrayVirtual = objectStates.get((VirtualArrayNode) vObjState.values().get(objectArrayIndex));
                                EscapeObjectState primitiveArrayVirtual = objectStates.get((VirtualArrayNode) vObjState.values().get(primitiveArrayIndex));

                                assert tagArrayVirtual instanceof VirtualObjectState && objectArrayVirtual instanceof VirtualObjectState && primitiveArrayVirtual instanceof VirtualObjectState;

                                int length = ((VirtualArrayNode) tagArrayValue).entryCount();
                                for (int i = 0; i < length; i++) {
                                    fsChecker.check(((VirtualObjectState) tagArrayVirtual).values().get(i),
                                                    ((VirtualObjectState) objectArrayVirtual).values().get(i),
                                                    ((VirtualObjectState) primitiveArrayVirtual).values().get(i));
                                }
                            }
                        }
                    }
                }
            }

            private void initIndexes(ResolvedJavaType type) {
                if (tagArrayIndex == -1) {
                    ResolvedJavaField[] instanceFields = type.getInstanceFields(true);
                    for (int i = 0; i < instanceFields.length; i++) {
                        ResolvedJavaField f = instanceFields[i];
                        if (f.getName().equals("tags")) {
                            tagArrayIndex = i;
                        }
                        if (f.getName().equals("locals")) {
                            objectArrayIndex = i;
                        }
                        if (f.getName().equals("primitiveLocals")) {
                            primitiveArrayIndex = i;
                        }
                    }
                }
                Assert.assertNotEquals(tagArrayIndex, -1);
                Assert.assertNotEquals(objectArrayIndex, -1);
                Assert.assertNotEquals(primitiveArrayIndex, -1);
            }
        };
    }

    private static RootNode clearedAccessRoot() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot slot = fd.addFrameSlot("test");
        return new RootNode(null, fd) {
            @Override
            public Object execute(VirtualFrame frame) {
                frame.clear(slot);
                return FrameUtil.getObjectSafe(frame, slot); // IllegalStateException
            }
        };
    }

    @Test
    public void clearAccess() {
        doTest(ClearFrameSlotTest::clearedAccessRoot,
                        voidChecker, emptyArgs, false, true);
    }

    private static RootNode unbalancedClearRoot() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot slot = fd.addFrameSlot("test");
        return new RootNode(null, fd) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.clear(slot);
                }
                // Expected CompilationError
                boundary();
                return null;
            }
        };
    }

    @Test
    public void unbalancedClear() {
        doTest(ClearFrameSlotTest::unbalancedClearRoot,
                        voidChecker, trueArg, true, false);
    }

    private static RootNode setClearsPrimitive() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot slot = fd.addFrameSlot("test");
        return new RootNode(null, fd) {
            @Override
            public Object execute(VirtualFrame frame) {
                frame.setLong(slot, 1L);
                frame.setObject(slot, new Object()); // clears primitive slot
                boundary();
                return null;
            }
        };
    }

    @Test
    public void setClearsPrim() {
        doTest(ClearFrameSlotTest::setClearsPrimitive,
                        graphFSChecker(regularFSChecker), emptyArgs, false, false);
    }

    private static RootNode setClearsObject() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot slot = fd.addFrameSlot("test");
        return new RootNode(null, fd) {
            @Override
            public Object execute(VirtualFrame frame) {
                frame.setObject(slot, new Object());
                frame.setLong(slot, 1L); // clears object slot
                boundary();
                return null;
            }
        };
    }

    @Test
    public void setClearsObj() {
        doTest(ClearFrameSlotTest::setClearsObject,
                        graphFSChecker(regularFSChecker), emptyArgs, false, false);
    }

    private static RootNode setNotClearPhi() {
        FrameDescriptor fd = new FrameDescriptor();
        FrameSlot slot = fd.addFrameSlot("test");
        return new RootNode(null, fd) {
            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                if ((boolean) args[0]) {
                    frame.setLong(slot, 1L);
                } else {
                    frame.setObject(slot, new Object());
                }
                boundary();
                return null;
            }
        };
    }

    @Test
    public void setNotClear() {
        doTest(ClearFrameSlotTest::setClearsObject,
                        graphFSChecker(noClearPhiChecker), trueArg, false, false);
    }

    private void doTest(Supplier<RootNode> rootProvider, Consumer<StructuredGraph> graphChecker, Object[] args, boolean peFails, boolean executionFails) {
        RootNode rootNode = rootProvider.get();
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        StructuredGraph graph = null;
        try {
            callTarget.call(args);
        } catch (Throwable e) {
            Assert.assertTrue(executionFails);
            return;
        }
        try {
            graph = partialEval((OptimizedCallTarget) callTarget, args, CompilationIdentifier.INVALID_COMPILATION_ID);
        } catch (Throwable e) {
            Assert.assertTrue(peFails);
            return;
        }
        graphChecker.accept(graph);
    }

    private static EconomicMap<VirtualObjectNode, EscapeObjectState> getObjectStateMappings(FrameState fs) {
        EconomicMap<VirtualObjectNode, EscapeObjectState> objectStates = EconomicMap.create(Equivalence.IDENTITY);
        FrameState current = fs;
        do {
            if (current.virtualObjectMappingCount() > 0) {
                for (EscapeObjectState state : current.virtualObjectMappings()) {
                    if (!objectStates.containsKey(state.object())) {
                        if (!(state instanceof MaterializedObjectState) || ((MaterializedObjectState) state).materializedValue() != state.object()) {
                            objectStates.put(state.object(), state);
                        }
                    }
                }
            }
            current = current.outerFrameState();
        } while (current != null);
        return objectStates;
    }
}
