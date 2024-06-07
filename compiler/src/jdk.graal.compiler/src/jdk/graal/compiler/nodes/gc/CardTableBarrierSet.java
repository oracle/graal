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
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.FixedWithNextNode;
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
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
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
    public void addBarriers(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            // nothing to do
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            addWriteBarrier(write, write.value());
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            addWriteBarrier(atomic, atomic.getNewValue());
        } else if (n instanceof AbstractCompareAndSwapNode) {
            AbstractCompareAndSwapNode cmpSwap = (AbstractCompareAndSwapNode) n;
            addWriteBarrier(cmpSwap, cmpSwap.getNewValue());
        } else if (n instanceof ArrayRangeWrite) {
            addArrayRangeBarriers((ArrayRangeWrite) n);
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
            return barrier instanceof SerialWriteBarrierNode && matches(n, (SerialWriteBarrierNode) barrier);
        } else if (n instanceof ArrayRangeWrite) {
            return barrier instanceof SerialArrayRangeWriteBarrierNode && matches((ArrayRangeWrite) n, (SerialArrayRangeWriteBarrierNode) barrier);
        } else {
            throw GraalError.shouldNotReachHere("Unexpected node: " + n.getClass()); // ExcludeFromJacocoGeneratedReport
        }
    }

    public void addArrayRangeBarriers(ArrayRangeWrite write) {
        if (arrayRangeWriteRequiresBarrier(write)) {
            StructuredGraph graph = write.asNode().graph();
            SerialArrayRangeWriteBarrierNode serialArrayRangeWriteBarrier = graph.add(new SerialArrayRangeWriteBarrierNode(write.getAddress(), write.getLength(), write.getElementStride()));
            graph.addAfterFixed(write.postBarrierInsertionPosition(), serialArrayRangeWriteBarrier);
        }
    }

    private void addWriteBarrier(FixedAccessNode node, ValueNode writtenValue) {
        if (needsWriteBarrier(node, writtenValue)) {
            addSerialPostWriteBarrier(node, node.getAddress(), node.graph());
        }
    }

    public boolean needsWriteBarrier(FixedAccessNode node, ValueNode writtenValue) {
        assert !(node instanceof ArrayRangeWrite) : Assertions.errorMessageContext("node", node, "val", writtenValue);
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                return false;
            case FIELD:
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
        FixedWithNextNode node = write.asFixedWithNextNode();
        return node.next() instanceof SerialArrayRangeWriteBarrierNode && matches(write, (SerialArrayRangeWriteBarrierNode) node.next());
    }

    private static void addSerialPostWriteBarrier(FixedAccessNode node, AddressNode address, StructuredGraph graph) {
        // Use a precise barrier for everything that might be an array write. Being too precise with
        // the barriers does not cause any correctness issues.
        boolean precise = node.getBarrierType() != BarrierType.FIELD;
        graph.addAfterFixed(node, graph.add(new SerialWriteBarrierNode(address, precise)));
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
