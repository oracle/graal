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

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdMaskedWriteNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIType;

/**
 * Intrinsic node for the {@code VectorSupport.storeMasked} method. This operation performs a write
 * to a given memory address but only at the selected elements. The non-selected elements not
 * accessed.
 */
@NodeInfo
public class VectorAPIStoreMaskedNode extends VectorAPISinkNode implements Canonicalizable {

    public static final NodeClass<VectorAPIStoreMaskedNode> TYPE = NodeClass.create(VectorAPIStoreMaskedNode.class);

    @Input(Association) AddressNode address;

    /** The stamp of the value to be stored. */
    private final VectorAPIType storeType;
    private final LocationIdentity location;

    /* Indices into the macro argument list for relevant input values. */
    private static final int VCLASS_ARG_INDEX = 0;
    private static final int V_ARG_INDEX = 7;
    private static final int M_ARG_INDEX = 8;

    protected VectorAPIStoreMaskedNode(MacroParams p, VectorAPIType storeType, AddressNode address, LocationIdentity location, FrameState stateAfter) {
        super(TYPE, p);
        this.storeType = storeType;
        this.address = address;
        this.location = location;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIStoreMaskedNode create(MacroParams p, VectorAPIType storeType, AddressNode address, LocationIdentity location) {
        return new VectorAPIStoreMaskedNode(p, storeType, address, location, null);
    }

    private ValueNode getVector() {
        return getArgument(V_ARG_INDEX);
    }

    private ValueNode getMask() {
        return getArgument(M_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(getVector(), getMask());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        VectorAPIType newStoreType = VectorAPIType.ofConstant(getArgument(VCLASS_ARG_INDEX), tool);
        AddressNode newAddress = improveAddress(address);
        if (newStoreType != storeType || newAddress != address) {
            return new VectorAPIStoreMaskedNode(copyParams(), newStoreType, newAddress, location, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (storeType == null) {
            return false;
        }
        SimdStamp accessStamp = storeType.stamp;
        return accessStamp.getVectorLength() > 1 && vectorArch.getSupportedVectorMaskedMoveLength(accessStamp.getComponent(0), accessStamp.getVectorLength()) == accessStamp.getVectorLength();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        ValueNode vector = expanded.get(getVector());
        ValueNode mask = expanded.get(getMask());
        SimdMaskedWriteNode write = vector.graph().add(new SimdMaskedWriteNode(address, mask, vector, location, BarrierType.NONE, MemoryOrderMode.PLAIN));
        write.setStateAfter(stateAfter());
        return write;
    }
}
