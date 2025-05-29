/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.nodes.spi.CoreProviders;
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
        if (store.object().isNullConstant()) {
            return BarrierType.NONE;
        }
        return store.needsBarrier() ? readWriteBarrier(store.object(), store.value()) : BarrierType.NONE;
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
    public BarrierType readWriteBarrier(ValueNode object, ValueNode value) {
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
    public void addBarriers(FixedAccessNode n, CoreProviders context) {
        if (n instanceof ReadNode) {
            addReadNodeBarriers((ReadNode) n);
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            addWriteBarriers(write, write.value(), null, true);
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            addWriteBarriers(atomic, atomic.getNewValue(), null, true);
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            addWriteBarriers(cmpSwap, cmpSwap.getNewValue(), cmpSwap.getExpectedValue(), false);
        } else if (n instanceof ArrayRangeWrite) {
            addArrayRangeBarriers((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
        }
    }

    private void addReadNodeBarriers(ReadNode node) {
        if (node.getBarrierType() == BarrierType.REFERENCE_GET) {
            StructuredGraph graph = node.graph();
            G1ReferentFieldReadBarrierNode barrier = graph.add(new G1ReferentFieldReadBarrierNode(node.getAddress(), maybeUncompressExpectedValue(node)));
            graph.addAfterFixed(node, barrier);
        } else if (node.getBarrierType() == BarrierType.WEAK_REFERS_TO || node.getBarrierType() == BarrierType.PHANTOM_REFERS_TO) {
            // No barrier node required
        } else {
            GraalError.guarantee(node.getBarrierType() == BarrierType.NONE, "invalid barrier on %s %s", node, node.getBarrierType());
        }
    }

    /**
     * The expected value argument might be in compressed form so allow subclasses to uncompress the
     * value if that's the preferred form by the backend implementation of the barrier.
     */
    protected ValueNode maybeUncompressExpectedValue(ValueNode value) {
        return value;
    }

    private void addWriteBarriers(FixedAccessNode node, ValueNode writtenValue, ValueNode expectedValue, boolean doLoad) {
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case FIELD:
            case ARRAY:
            case UNKNOWN:
            case AS_NO_KEEPALIVE_WRITE:
                if (isObjectValue(writtenValue)) {
                    StructuredGraph graph = node.graph();
                    boolean init = node.getLocationIdentity().isInit();
                    if (!init && barrierType != BarrierType.AS_NO_KEEPALIVE_WRITE) {
                        // The pre barrier does nothing if the value being read is null, so it can
                        // be explicitly skipped when this is an initializing store.
                        // No keep-alive means no need for the pre-barrier.
                        addG1PreWriteBarrier(node, node.getAddress(), expectedValue, doLoad, graph);
                    }
                    if (writeRequiresPostBarrier(node, writtenValue)) {
                        // Use a precise barrier for everything that might be an array write. Being
                        // too precise with the barriers does not cause any correctness issues.
                        ValueNode object = null;
                        if (barrierType == BarrierType.FIELD) {
                            object = node.getAddress().getBase();
                            assert object != null;
                        }
                        addG1PostWriteBarrier(node, node.getAddress(), writtenValue, object, graph);
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
                G1ArrayRangePreWriteBarrierNode g1ArrayRangePreWriteBarrier = graph.add(new G1ArrayRangePreWriteBarrierNode(write.getAddress(), write.getLength(), write.getElementStride()));
                graph.addBeforeFixed(write.preBarrierInsertionPosition(), g1ArrayRangePreWriteBarrier);
            }
            if (arrayRangeWriteRequiresPostBarrier(write)) {
                G1ArrayRangePostWriteBarrierNode g1ArrayRangePostWriteBarrier = graph.add(new G1ArrayRangePostWriteBarrierNode(write.getAddress(), write.getLength(), write.getElementStride()));
                graph.addAfterFixed(write.postBarrierInsertionPosition(), g1ArrayRangePostWriteBarrier);
            }
        }
    }

    @SuppressWarnings("unused")
    protected boolean arrayRangeWriteRequiresPostBarrier(ArrayRangeWrite write) {
        return true;
    }

    private void addG1PreWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean doLoad, StructuredGraph graph) {
        G1PreWriteBarrierNode preBarrier = graph.add(new G1PreWriteBarrierNode(address, maybeUncompressExpectedValue(value), doLoad));
        GraalError.guarantee(!node.getUsedAsNullCheck(), "trapping null checks are inserted after write barrier insertion: ", node);
        node.setStateBefore(null);
        graph.addBeforeFixed(node, preBarrier);
    }

    private void addG1PostWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, ValueNode object, StructuredGraph graph) {
        final boolean alwaysNull = StampTool.isPointerAlwaysNull(value);
        graph.addAfterFixed(node, graph.add(new G1PostWriteBarrierNode(address, maybeUncompressExpectedValue(value), object, alwaysNull)));
    }

    private static boolean isObjectValue(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp;
    }

    @Override
    public boolean mayNeedPreWriteBarrier(JavaKind storageKind) {
        return arrayWriteBarrierType(storageKind) != BarrierType.NONE;
    }
}
