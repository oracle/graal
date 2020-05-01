/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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
package org.graalvm.compiler.nodes.gc;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ArrayRangeWrite;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess.BarrierType;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.type.StampTool;

public class ShenandoahBarrierSet implements BarrierSet {
    private final ResolvedJavaType objectArrayType;
    private final ResolvedJavaField referentField;

    public ShenandoahBarrierSet(GraalHotSpotVMConfig config, ResolvedJavaType objectArrayType, ResolvedJavaField referentField) {
        this.objectArrayType = objectArrayType;
        this.referentField = referentField;
    }

    @Override
    public void addBarriers(FixedAccessNode n) {
        if (n instanceof ReadNode) {
            addReadNodeBarriers((ReadNode) n);
        } else if (n instanceof WriteNode) {
            WriteNode write = (WriteNode) n;
            addWriteBarriers(write, write.value(), null, true, write.getNullCheck());
        } else if (n instanceof LoweredAtomicReadAndWriteNode) {
            LoweredAtomicReadAndWriteNode atomic = (LoweredAtomicReadAndWriteNode) n;
            addWriteBarriers(atomic, atomic.getNewValue(), null, true, atomic.getNullCheck());
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
        ValueNode value = node;
        if (node.getBarrierType() != BarrierType.NONE) {
            StructuredGraph graph = node.graph();
            ShenandoahLoadReferenceBarrier lrb = graph.add(new ShenandoahLoadReferenceBarrier(node));
            value = lrb;
            node.graph().addAfterFixed(node, lrb);
            node.replaceAtMatchingUsages(lrb, n -> n != lrb);
        }
//        if (node.getBarrierType() == BarrierType.WEAK_FIELD || node.getBarrierType() == BarrierType.MAYBE_WEAK_FIELD) {
//            StructuredGraph graph = node.graph();
//            ShenandoahReferentFieldReadBarrier barrier = graph.add(new ShenandoahReferentFieldReadBarrier(node.getAddress(), value, node.getBarrierType() == BarrierType.MAYBE_WEAK_FIELD));
//            graph.addAfterFixed(node, barrier);
//        }
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
                        addPreWriteBarrier(node, node.getAddress(), expectedValue, doLoad, nullCheck, graph);
                    }
                }
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    private static void addArrayRangeBarriers(ArrayRangeWrite write) {
        if (write.writesObjectArray()) {
            StructuredGraph graph = write.asNode().graph();
            if (!write.isInitialization()) {
                // The pre barrier does nothing if the value being read is null, so it can
                // be explicitly skipped when this is an initializing store.
                ShenandoahArrayRangePreWriteBarrier arrayRangePreWriteBarrier = graph.add(new ShenandoahArrayRangePreWriteBarrier(write.getAddress(), write.getLength(), write.getElementStride()));
                graph.addBeforeFixed(write.asNode(), arrayRangePreWriteBarrier);
            }
        }
    }

    private static void addPreWriteBarrier(FixedAccessNode node, AddressNode address, ValueNode value, boolean doLoad, boolean nullCheck, StructuredGraph graph) {
        ShenandoahPreWriteBarrier preBarrier = graph.add(new ShenandoahPreWriteBarrier(address, value, doLoad, nullCheck));
        preBarrier.setStateBefore(node.stateBefore());
        node.setNullCheck(false);
        node.setStateBefore(null);
        graph.addBeforeFixed(node, preBarrier);
    }

    private static boolean isObjectValue(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp;
    }

    @Override
    public BarrierType fieldLoadBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        if (field.getJavaKind() == JavaKind.Object) {
            if (field.equals(referentField)) {
                return BarrierType.WEAK_FIELD;
            } else {
                return BarrierType.FIELD;
            }
        }
        return BarrierType.NONE;
    }

    @Override
    public BarrierType fieldStoreBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.FIELD : BarrierType.NONE;
    }

    @Override
    public BarrierType readBarrierType(RawLoadNode load) {
        if (load.object().getStackKind() == JavaKind.Object &&
                load.accessKind() == JavaKind.Object &&
                !StampTool.isPointerAlwaysNull(load.object())) {
            long referentOffset = referentField.getOffset();
            assert referentOffset > 0;

            if (load.offset().isJavaConstant() && referentOffset != load.offset().asJavaConstant().asLong()) {
                // Reading at a constant offset which is different than the referent field.
                return BarrierType.FIELD;
            }
            ResolvedJavaType referenceType = referentField.getDeclaringClass();
            ResolvedJavaType type = StampTool.typeOrNull(load.object());
            if (type != null && referenceType.isAssignableFrom(type)) {
                // It's definitely a field of a Reference type
                if (load.offset().isJavaConstant() && referentOffset == load.offset().asJavaConstant().asLong()) {
                    // Exactly Reference.referent
                    return BarrierType.WEAK_FIELD;
                }
                // An unknown offset into Reference
                return BarrierType.MAYBE_WEAK_FIELD;
            }
            if (type == null || type.isAssignableFrom(referenceType)) {
                // The object is a supertype of Reference with an unknown offset or a constant
                // offset which is the same as Reference.referent.
                return BarrierType.MAYBE_WEAK_FIELD;
            }
        }
        return BarrierType.NONE;
    }

    @Override
    public BarrierType storeBarrierType(RawStoreNode store) {
        return store.needsBarrier() ? guessStoreBarrierType(store.object(), store.value()) : BarrierType.NONE;
   }

    @Override
    public BarrierType arrayStoreBarrierType(JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.ARRAY : BarrierType.NONE;
    }

    @Override
    public BarrierType guessStoreBarrierType(ValueNode object, ValueNode value) {
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
}
