/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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
package jdk.graal.compiler.nodes.gc;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ArrayRangeWrite;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AbstractCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class G1BarrierSet implements BarrierSet {
    private final ResolvedJavaType objectArrayType;
    private final ResolvedJavaField referentField;

    public G1BarrierSet(ResolvedJavaType objectArrayType, ResolvedJavaField referentField) {
        this.objectArrayType = objectArrayType;
        this.referentField = referentField;
    }

    @Override
    public BarrierType readBarrierType(LocationIdentity location, ValueNode address, Stamp loadStamp) {
        return BarrierType.NONE;
    }

    @Override
    public BarrierType writeBarrierType(RawStoreNode store) {
        return store.needsBarrier() ? guessReadWriteBarrier(store.object(), store.value()) : BarrierType.NONE;
    }

    @Override
    public BarrierType fieldReadBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        if (field.getJavaKind() == JavaKind.Object && field.equals(referentField)) {
            /*
             * We should not encounter field load of Reference.referent except compiling
             * Reference.get(), which won't be executed anyway given the intrinsification. We cannot
             * distinguish between PhantomReference and other References. Yet returning
             * BarrierType.REFERENCE_GET is fine for G1 since the inserted barriers for both
             * REFERENCE_GET and PHANTOM_REFERS_TO are identical.
             */
            return BarrierType.REFERENCE_GET;
        }
        return BarrierType.NONE;
    }

    @Override
    public BarrierType fieldWriteBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.FIELD : BarrierType.NONE;
    }

    @Override
    public BarrierType arrayWriteBarrierType(JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.ARRAY : BarrierType.NONE;
    }

    @Override
    public BarrierType guessReadWriteBarrier(ValueNode object, ValueNode value) {
        if (value.getStackKind() == JavaKind.Object && object.getStackKind() == JavaKind.Object) {
            ResolvedJavaType type = StampTool.typeOrNull(object);
            if (type != null && type.isArray()) {
                return BarrierType.ARRAY;
            } else if (type == null || type.isAssignableFrom(objectArrayType)) {
                return BarrierType.UNKNOWN;
            } else {
                return BarrierType.FIELD;
            }
        }
        return BarrierType.NONE;
    }

    @Override
    public boolean hasWriteBarrier() {
        return true;
    }

    @Override
    public boolean hasReadBarrier() {
        return false;
    }

    @Override
    public void addBarriers(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            addReadNodeBarriers((ReadNode) n);
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            addWriteBarriers(write, write.value(), null, true, write.getUsedAsNullCheck());
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            addWriteBarriers(atomic, atomic.getNewValue(), null, true, atomic.getUsedAsNullCheck());
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            addWriteBarriers(cmpSwap, cmpSwap.getNewValue(), cmpSwap.getExpectedValue(), false, false);
        } else if (n instanceof ArrayRangeWrite) {
            addArrayRangeBarriers((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
        }
    }

    private static void addReadNodeBarriers(ReadNode node) {
        if (node.getBarrierType() == BarrierType.REFERENCE_GET) {
            StructuredGraph graph = node.graph();
            G1ReferentFieldReadBarrier barrier = graph.add(new G1ReferentFieldReadBarrier(node.getAddress(), node));
            graph.addAfterFixed(node, barrier);
        }
    }

    private void addWriteBarriers(FixedAccessNode node, ValueNode writtenValue, ValueNode expectedValue, boolean doLoad, boolean nullCheck) {
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case FIELD:
            case ARRAY:
            case UNKNOWN:
                if (isObjectValue(writtenValue)) {
                    StructuredGraph graph = node.graph();
                    boolean init = node.getLocationIdentity().isInit();
                    if (!init) {
                        // The pre barrier does nothing if the value being read is null, so it can
                        // be explicitly skipped when this is an initializing store.
                        addG1PreWriteBarrier(node, node.getAddress(), expectedValue, doLoad, nullCheck, graph);
                    }
                    if (writeRequiresPostBarrier(node, writtenValue)) {
                        // Use a precise barrier for everything that might be an array write. Being
                        // too precise with the barriers does not cause any correctness issues.
                        boolean precise = barrierType != BarrierType.FIELD;
                        addG1PostWriteBarrier(node, node.getAddress(), writtenValue, precise, graph);
                    }
                }
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    @SuppressWarnings("unused")
    protected boolean writeRequiresPostBarrier(FixedAccessNode node, ValueNode writtenValue) {
        // Without help from the runtime all writes (except null writes) require an explicit post
        // barrier.
        assert isObjectValue(writtenValue);
        return !StampTool.isPointerAlwaysNull(writtenValue);
    }

    private void addArrayRangeBarriers(ArrayRangeWrite write) {
        if (write.writesObjectArray()) {
            StructuredGraph graph = write.asNode().graph();
            if (!write.isInitialization()) {
                // The pre barrier does nothing if the value being read is null, so it can
                // be explicitly skipped when this is an initializing store.
                G1ArrayRangePreWriteBarrier g1ArrayRangePreWriteBarrier = graph.add(new G1ArrayRangePreWriteBarrier(write.getAddress(), write.getLength(), write.getElementStride()));
                graph.addBeforeFixed(write.preBarrierInsertionPosition(), g1ArrayRangePreWriteBarrier);
            }
            if (arrayRangeWriteRequiresPostBarrier(write)) {
                G1ArrayRangePostWriteBarrier g1ArrayRangePostWriteBarrier = graph.add(new G1ArrayRangePostWriteBarrier(write.getAddress(), write.getLength(), write.getElementStride()));
                graph.addAfterFixed(write.postBarrierInsertionPosition(), g1ArrayRangePostWriteBarrier);
            }
        }
    }

    @SuppressWarnings("unused")
    protected boolean arrayRangeWriteRequiresPostBarrier(ArrayRangeWrite write) {
        return true;
    }

    private static void addG1PreWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean doLoad, boolean nullCheck, StructuredGraph graph) {
        G1PreWriteBarrier preBarrier = graph.add(new G1PreWriteBarrier(address, value, doLoad, nullCheck));
        preBarrier.setStateBefore(node.stateBefore());
        node.setUsedAsNullCheck(false);
        node.setStateBefore(null);
        graph.addBeforeFixed(node, preBarrier);
    }

    private static void addG1PostWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean precise, StructuredGraph graph) {
        final boolean alwaysNull = StampTool.isPointerAlwaysNull(value);
        graph.addAfterFixed(node, graph.add(new G1PostWriteBarrier(address, value, precise, alwaysNull)));
    }

    private static boolean isObjectValue(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp;
    }

    @Override
    public boolean mayNeedPreWriteBarrier(JavaKind storageKind) {
        return arrayWriteBarrierType(storageKind) != BarrierType.NONE;
    }
}
