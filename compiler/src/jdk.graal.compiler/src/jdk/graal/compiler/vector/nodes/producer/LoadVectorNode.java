/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.producer;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.type.Vector.BooleanVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ByteVector;
import jdk.graal.compiler.vector.nodes.type.Vector.CharVector;
import jdk.graal.compiler.vector.nodes.type.Vector.DoubleVector;
import jdk.graal.compiler.vector.nodes.type.Vector.FloatVector;
import jdk.graal.compiler.vector.nodes.type.Vector.IntVector;
import jdk.graal.compiler.vector.nodes.type.Vector.LongVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ObjectVector;
import jdk.graal.compiler.vector.nodes.type.Vector.ShortVector;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.StructuralInput.Memory;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Load the contents of an array into a vector. This is the vector equivalent of a high-level
 * {@link jdk.graal.compiler.nodes.java.LoadIndexedNode} and will be lowered to a
 * {@link VectorReadNode}.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot reason about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot reason about vector nodes statically.")
//@formatter:on
public final class LoadVectorNode extends FixedWithNextNode implements Lowerable, VectorNode, GuardedNode, MemoryAccess {
    public static final NodeClass<LoadVectorNode> TYPE = NodeClass.create(LoadVectorNode.class);

    protected final JavaKind elementKind;

    @OptionalInput(InputType.Guard) GuardingNode guard;
    @Input ValueNode array;
    @Input ValueNode index;

    @OptionalInput(InputType.Memory) MemoryKill lastLocationAccess;

    protected final LocationIdentity locationIdentity;

    public LoadVectorNode(JavaKind elementKind, ValueNode array, ValueNode index, ValueNode memory) {
        this(elementKind, fromArrayStamp(elementKind, (ObjectStamp) array.stamp(NodeView.DEFAULT)), array, index, (MemoryKill) memory, null, NamedLocationIdentity.getArrayLocation(elementKind));
    }

    private static VectorStamp fromArrayStamp(JavaKind elementKind, ObjectStamp array) {
        assert array.type() == null || array.type().getComponentType().getJavaKind() == elementKind : Assertions.errorMessage(array, elementKind);
        if (elementKind.isPrimitive()) {
            return new VectorStamp(StampFactory.forKind(elementKind));
        } else {
            ResolvedJavaType arrayType = array.type();
            if (arrayType != null) {
                ResolvedJavaType elementType = arrayType.getComponentType();
                return new VectorStamp(StampFactory.object(TypeReference.createWithoutAssumptions(elementType)));
            } else {
                return new VectorStamp(StampFactory.object());
            }
        }
    }

    public LoadVectorNode(JavaKind elementKind, VectorStamp stamp, ValueNode array, ValueNode index, MemoryKill lastLocationAccess, GuardingNode guard, LocationIdentity locationIdentity) {
        super(TYPE, stamp);
        this.guard = guard;
        this.array = array;
        this.index = index;
        this.lastLocationAccess = lastLocationAccess;
        this.locationIdentity = locationIdentity;
        this.elementKind = elementKind;
    }

    public ValueNode getArray() {
        return array;
    }

    public ValueNode getIndex() {
        return index;
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.guard, guard);
        this.guard = guard;
    }

    @Override
    public boolean inferStamp() {
        Stamp s = array.stamp(NodeView.DEFAULT);
        if (s instanceof ObjectStamp && ((ObjectStamp) s).type() != null) {
            return updateStamp(fromArrayStamp(elementKind, (ObjectStamp) s));
        } else {
            return false;
        }
    }

    @NodeIntrinsic
    public static native BooleanVector loadVector(@ConstantNodeParameter JavaKind kind, boolean[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native ByteVector loadVector(@ConstantNodeParameter JavaKind kind, byte[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native ShortVector loadVector(@ConstantNodeParameter JavaKind kind, short[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native CharVector loadVector(@ConstantNodeParameter JavaKind kind, char[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native IntVector loadVector(@ConstantNodeParameter JavaKind kind, int[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native LongVector loadVector(@ConstantNodeParameter JavaKind kind, long[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native FloatVector loadVector(@ConstantNodeParameter JavaKind kind, float[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native DoubleVector loadVector(@ConstantNodeParameter JavaKind kind, double[] array, int index, Memory memory);

    @NodeIntrinsic
    public static native ObjectVector loadVector(@ConstantNodeParameter JavaKind kind, Object[] array, int index, Memory memory);

    @Override
    public VectorStamp getVectorStamp() {
        return (VectorStamp) stamp(NodeView.DEFAULT);
    }
}
