/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdMaskedReadNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIType;

/**
 * Intrinsic node for the {@code VectorSupport.loadMasked} method. This operation performs a read
 * from a given memory address but only at the selected elements, producing a result vector. The
 * non-selected elements are set to zeroes.
 */
@NodeInfo
public class VectorAPILoadMaskedNode extends VectorAPIMacroNode implements Canonicalizable {
    public static final NodeClass<VectorAPILoadMaskedNode> TYPE = NodeClass.create(VectorAPILoadMaskedNode.class);

    @Node.Input(Association) AddressNode address;

    /** The stamp of the loaded value. */
    private final SimdStamp loadStamp;
    private final VectorAPIType loadType;
    private final LocationIdentity location;

    /* Indices into the macro argument list for relevant input values. */
    private static final int VCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 2;
    private static final int LENGTH_ARG_INDEX = 3;
    private static final int M_ARG_INDEX = 7;

    protected VectorAPILoadMaskedNode(MacroParams p, SimdStamp loadStamp, VectorAPIType loadType, AddressNode address, LocationIdentity location, FrameState stateAfter) {
        super(TYPE, p, null /* can't constant fold loads */);
        this.loadStamp = loadStamp;
        this.loadType = loadType;
        this.address = address;
        this.location = location;
        this.stateAfter = stateAfter;
    }

    public static VectorAPILoadMaskedNode create(MacroParams params, VectorAPIType loadType, AddressNode address, LocationIdentity location, CoreProviders providers) {
        SimdStamp loadStamp = improveVectorStamp(null, params.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        return new VectorAPILoadMaskedNode(params, loadStamp, loadType, address, location, null);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(getArgument(M_ARG_INDEX));
    }

    @Override
    public SimdStamp vectorStamp() {
        return loadStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (speciesStamp.isExactType() && loadStamp != null) {
            /* Nothing to improve. */
            return this;
        }

        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newLoadStamp = improveVectorStamp(loadStamp, toArgumentArray(), VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        AddressNode newAddress = improveAddress(address);
        if (newSpeciesStamp != speciesStamp || newLoadStamp != loadStamp || newAddress != address) {
            ValueNode vClass = getArgument(VCLASS_ARG_INDEX);
            VectorAPIType newLoadType = VectorAPIType.ofConstant(vClass, tool);
            return new VectorAPILoadMaskedNode(copyParamsWithImprovedStamp(newSpeciesStamp), newLoadStamp, newLoadType, newAddress, location, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (!((ObjectStamp) stamp).isExactType() || loadStamp == null || loadType == null) {
            return false;
        }

        GraalError.guarantee(loadType.payloadStamp.isCompatible(loadStamp), "%s - %s", loadType.payloadStamp, loadStamp);
        return vectorArch.getSupportedVectorMaskedMoveLength(loadStamp.getComponent(0), loadStamp.getVectorLength()) == loadStamp.getVectorLength();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        /*
         * The Vector API performs null and bounds checks before the load, but the load is not
         * connected to the checks by guard edges or Pi nodes. Therefore, this read must not float.
         */
        StructuredGraph graph = address.graph();
        ValueNode mask = expanded.get(getArgument(M_ARG_INDEX));
        SimdMaskedReadNode fixedRead = graph.add(new SimdMaskedReadNode(mask, address, location, loadStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
        graph.addBeforeFixed(this, fixedRead);
        return fixedRead;
    }
}
