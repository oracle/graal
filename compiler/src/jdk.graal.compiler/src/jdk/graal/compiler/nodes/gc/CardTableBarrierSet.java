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
import jdk.graal.compiler.debug.Assertions;
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
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CardTableBarrierSet implements BarrierSet {
    private final ResolvedJavaType objectArrayType;

    public CardTableBarrierSet(ResolvedJavaType objectArrayType) {
        this.objectArrayType = objectArrayType;
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
            // nothing to do
        } else if (n instanceof WriteNode write) {
            addWriteBarrier(write, write.value(), context);
        } else if (n instanceof LoweredAtomicReadAndWriteNode atomic) {
            addWriteBarrier(atomic, atomic.getNewValue(), context);
        } else if (n instanceof AbstractCompareAndSwapNode cmpSwap) {
            addWriteBarrier(cmpSwap, cmpSwap.getNewValue(), context);
        } else if (n instanceof ArrayRangeWrite rangeWrite) {
            addArrayRangeBarriers(rangeWrite, context);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
        }
    }

    public boolean needsBarrier(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            return false;
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            return needsWriteBarrier(write, write.value());
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            return needsWriteBarrier(atomic, atomic.getNewValue());
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            return needsWriteBarrier(cmpSwap, cmpSwap.getNewValue());
        } else if (n instanceof ArrayRangeWrite) {
            return arrayRangeWriteRequiresBarrier((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
            return false;
        }
    }

    public boolean hasBarrier(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            return false;
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            return hasWriteBarrier(write);
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            return hasWriteBarrier(atomic);
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            return hasWriteBarrier(cmpSwap);
        } else if (n instanceof ArrayRangeWrite) {
            return hasWriteBarrier((ArrayRangeWrite) n);
        } else {
            GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
            return false;
        }
    }

    public boolean isMatchingBarrier(FixedAccessNode n, WriteBarrierNode barrier) {
        if (n instanceof ReadNode) {
            return false;
        } else if (n instanceof WriteNode || n instanceof LoweredAtomicReadAndWriteNode || n instanceof AbstractCompareAndSwapNode) {
            return barrier instanceof SerialWriteBarrierNode s && matches(n, s);
        } else if (n instanceof ArrayRangeWrite w) {
            return (barrier instanceof SerialArrayRangeWriteBarrierNode r && matches(w, r)) || (barrier instanceof SerialWriteBarrierNode s && !s.usePrecise() && matches(n, s));
        } else {
            throw GraalError.shouldNotReachHere("Unexpected node: " + n.getClass()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private void addArrayRangeBarriers(ArrayRangeWrite write, CoreProviders context) {
        if (arrayRangeWriteRequiresBarrier(write)) {
            addSerialPostRangeWriteBarrier(write, context);
        }
    }

    private void addWriteBarrier(FixedAccessNode node, ValueNode writtenValue, CoreProviders context) {
        if (needsWriteBarrier(node, writtenValue)) {
            addSerialPostWriteBarrier(node, context);
        }
    }

    public boolean needsWriteBarrier(FixedAccessNode node, ValueNode writtenValue) {
        assert !(node instanceof ArrayRangeWrite) : Assertions.errorMessageContext("node", node, "val", writtenValue);
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                return false;
            case FIELD:
            case AS_NO_KEEPALIVE_WRITE:
            case ARRAY:
            case UNKNOWN:
                return writeRequiresBarrier(node, writtenValue);
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    @SuppressWarnings("unused")
    protected boolean writeRequiresBarrier(FixedAccessNode node, ValueNode writtenValue) {
        // Null writes can skip the card mark.
        return isNonNullObjectValue(writtenValue);
    }

    protected boolean arrayRangeWriteRequiresBarrier(ArrayRangeWrite write) {
        return write.writesObjectArray();
    }

    private static boolean hasWriteBarrier(FixedAccessNode node) {
        return node.next() instanceof SerialWriteBarrierNode && matches(node, (SerialWriteBarrierNode) node.next());
    }

    private static boolean hasWriteBarrier(ArrayRangeWrite write) {
        FixedAccessNode node = (FixedAccessNode) write;
        return (node.next() instanceof SerialArrayRangeWriteBarrierNode r && matches(write, r)) || (node.next() instanceof SerialWriteBarrierNode s && !s.usePrecise() && matches(node, s));
    }

    private void addSerialPostRangeWriteBarrier(ArrayRangeWrite write, CoreProviders context) {
        FixedAccessNode node = (FixedAccessNode) write;
        StructuredGraph graph = node.graph();
        if (!barrierPrecise(node, write.getAddress().getBase(), context)) {
            SerialWriteBarrierNode barrier = graph.add(new SerialWriteBarrierNode(node.getAddress(), false));
            graph.addAfterFixed(node, barrier);
            return;
        }

        SerialArrayRangeWriteBarrierNode barrier = graph.add(new SerialArrayRangeWriteBarrierNode(write.getAddress(), write.getLength(), write.getElementStride()));
        graph.addAfterFixed(write.postBarrierInsertionPosition(), barrier);
    }

    private void addSerialPostWriteBarrier(FixedAccessNode node, CoreProviders context) {
        StructuredGraph graph = node.graph();
        boolean precise = barrierPrecise(node, node.getAddress().getBase(), context);
        graph.addAfterFixed(node, graph.add(new SerialWriteBarrierNode(node.getAddress(), precise)));
    }

    /**
     * Decide if a precise barrier is needed for an access.
     */
    protected boolean barrierPrecise(FixedAccessNode node, @SuppressWarnings("unused") ValueNode base, @SuppressWarnings("unused") CoreProviders context) {
        return node.getBarrierType() != BarrierType.FIELD && node.getBarrierType() != BarrierType.AS_NO_KEEPALIVE_WRITE;
    }

    private static boolean isNonNullObjectValue(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp && !StampTool.isPointerAlwaysNull(value);
    }

    private static boolean matches(FixedAccessNode node, SerialWriteBarrierNode barrier) {
        if (!barrier.usePrecise()) {
            if (barrier.getAddress() instanceof OffsetAddressNode && node.getAddress() instanceof OffsetAddressNode) {
                return GraphUtil.unproxify(((OffsetAddressNode) barrier.getAddress()).getBase()) == GraphUtil.unproxify(((OffsetAddressNode) node.getAddress()).getBase());
            }
        }
        return barrier.getAddress() == node.getAddress();
    }

    private static boolean matches(ArrayRangeWrite node, SerialArrayRangeWriteBarrierNode barrier) {
        return barrier.getAddress() == node.getAddress() && node.getLength() == barrier.getLength() && node.getElementStride() == barrier.getElementStride();
    }

    @Override
    public boolean mayNeedPreWriteBarrier(JavaKind storageKind) {
        return false;
    }
}
