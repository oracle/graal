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

import java.util.Collections;

import org.graalvm.collections.EconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIBoxingUtils;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIType;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;

/**
 * Intrinsic node for the {@code VectorSupport.load} method. This operation performs a read from a
 * given memory address, producing a result vector. This operation does not take a mask.
 */
@NodeInfo
public class VectorAPILoadNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPILoadNode> TYPE = NodeClass.create(VectorAPILoadNode.class);

    @Input(Association) AddressNode address;

    /** The stamp of the loaded value. */
    private final SimdStamp loadStamp;
    private final VectorAPIType loadType;
    private final LocationIdentity location;

    /* Indices into the macro argument list for relevant input values. */
    private static final int VMCLASS_ARG_INDEX = 0;
    private static final int ECLASS_ARG_INDEX = 1;
    private static final int LENGTH_ARG_INDEX = 2;

    protected VectorAPILoadNode(MacroParams p, SimdStamp loadStamp, VectorAPIType loadType, AddressNode address, LocationIdentity location, FrameState stateAfter) {
        super(TYPE, p, null /* can't constant fold loads */);
        this.loadStamp = loadStamp;
        this.loadType = loadType;
        this.address = address;
        this.location = location;
        this.stateAfter = stateAfter;
    }

    public static VectorAPILoadNode create(MacroParams params, VectorAPIType loadType, AddressNode address, LocationIdentity location, CoreProviders providers) {
        SimdStamp loadStamp = improveVectorStamp(null, params.arguments, VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        return new VectorAPILoadNode(params, loadStamp, loadType, address, location, null);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return Collections.emptyList();
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

        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VMCLASS_ARG_INDEX);
        SimdStamp newLoadStamp = improveVectorStamp(loadStamp, toArgumentArray(), VMCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        AddressNode newAddress = improveAddress(address);
        if (newSpeciesStamp != speciesStamp || newLoadStamp != loadStamp || newAddress != address) {
            ValueNode vmClass = arguments.get(VMCLASS_ARG_INDEX);
            VectorAPIType newLoadType = VectorAPIType.ofConstant(vmClass, tool);
            return new VectorAPILoadNode(copyParamsWithImprovedStamp(newSpeciesStamp), newLoadStamp, newLoadType, newAddress, location, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (!((ObjectStamp) stamp).isExactType() || loadStamp == null || loadType == null) {
            return false;
        }
        if (loadType.isMask) {
            if (!VectorAPIBoxingUtils.canConvertBooleansToLogic(loadType, vectorArch)) {
                return false;
            }
        }
        SimdStamp payloadStamp = loadType.payloadStamp;
        return vectorArch.getSupportedVectorMoveLength(payloadStamp.getComponent(0), payloadStamp.getVectorLength()) == payloadStamp.getVectorLength();
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        /*
         * The Vector API performs null and bounds checks before the load, but the load is not
         * connected to the checks by guard edges or Pi nodes. Therefore, this read must not float.
         */
        StructuredGraph graph = address.graph();
        ReadNode fixedRead = graph.add(new ReadNode(address, location, loadType.payloadStamp, BarrierType.NONE, MemoryOrderMode.PLAIN));
        fixedRead.setForceFixed(true);
        graph.addBeforeFixed(this, fixedRead);
        ValueNode expansion = fixedRead;
        if (loadType.isMask) {
            PrimitiveStamp elementStamp = VectorAPIUtils.primitiveStampForKind(loadType.elementKind);
            expansion = VectorAPIBoxingUtils.booleansAsLogic(fixedRead, elementStamp, vectorArch);
        }
        return expansion;
    }
}
