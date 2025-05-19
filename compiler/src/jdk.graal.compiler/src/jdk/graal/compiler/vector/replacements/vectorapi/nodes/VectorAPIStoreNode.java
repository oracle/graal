/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements.vectorapi.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIBoxingUtils;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIType;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;

/**
 * Intrinsic node for the {@code VectorSupport.store} method. This operation performs a store of a
 * vector to a given memory address. This operation does not take a mask. The separate
 * {@code storeMasked} method is not supported yet by Graal.
 */
@NodeInfo
public class VectorAPIStoreNode extends VectorAPISinkNode implements Canonicalizable {

    public static final NodeClass<VectorAPIStoreNode> TYPE = NodeClass.create(VectorAPIStoreNode.class);

    @Input(Association) AddressNode address;

    /** The stamp of the value to be stored. */
    private final SimdStamp inputStamp;
    private final VectorAPIType storeType;
    private final LocationIdentity location;

    /* Indices into the macro argument list for relevant input values. */
    private static final int VCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 1;
    private static final int LENGTH_ARG_INDEX = 2;
    /* JDK 22+27 added the fromSegment flag before the vector to be stored. */
    private static final int VECTOR_ARG_INDEX = 6;

    public VectorAPIStoreNode(MacroParams p, SimdStamp inputStamp, VectorAPIType storeType, AddressNode address, LocationIdentity location) {
        this(p, inputStamp, storeType, address, location, null);
    }

    private VectorAPIStoreNode(MacroParams p, SimdStamp inputStamp, VectorAPIType storeType, AddressNode address, LocationIdentity location, FrameState stateAfter) {
        super(TYPE, p);
        this.inputStamp = inputStamp;
        this.storeType = storeType;
        this.address = address;
        this.location = location;
        this.stateAfter = stateAfter;
    }

    private ValueNode getVector() {
        return getArgument(VECTOR_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(getVector());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (inputStamp != null) {
            /* Nothing to improve. */
            return this;
        }

        SimdStamp newInputStamp = improveVectorStamp(inputStamp, toArgumentArray(), VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        AddressNode newAddress = improveAddress(address);
        if (newInputStamp != inputStamp || newAddress != address) {
            ValueNode vClass = arguments.get(VCLASS_ARG_INDEX);
            VectorAPIType newStoreType = VectorAPIType.ofConstant(vClass, tool);
            return new VectorAPIStoreNode(copyParams(), newInputStamp, newStoreType, newAddress, location, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (storeType == null) {
            return false;
        }
        SimdStamp actualStamp = inputStamp;
        if (storeType.isMask) {
            /* This is a mask. To store it, we must turn it into a boolean vector using a blend. */
            Stamp booleanPayloadStamp = IntegerStamp.create(8);
            if (vectorArch.getSupportedVectorBlendLength(booleanPayloadStamp, actualStamp.getVectorLength()) != actualStamp.getVectorLength()) {
                return false;
            }
            if (!VectorAPIBoxingUtils.canConvertLogicToBooleans(actualStamp, vectorArch)) {
                return false;
            }
            actualStamp = SimdStamp.broadcast(booleanPayloadStamp, actualStamp.getVectorLength());
        }

        return actualStamp != null && vectorArch.getSupportedVectorMoveLength(actualStamp.getComponent(0), actualStamp.getVectorLength()) == actualStamp.getVectorLength();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        ValueNode vector = getVector();
        ValueNode expandedVector = expanded.get(vector);
        if (storeType.isMask) {
            expandedVector = vector.graph().addOrUniqueWithInputs(VectorAPIBoxingUtils.logicAsBooleans(expandedVector, vectorArch));
        }
        WriteNode write = vector.graph().add(new WriteNode(address, location, expandedVector, BarrierType.NONE, MemoryOrderMode.PLAIN));
        write.setStateAfter(stateAfter());
        return write;
    }
}
