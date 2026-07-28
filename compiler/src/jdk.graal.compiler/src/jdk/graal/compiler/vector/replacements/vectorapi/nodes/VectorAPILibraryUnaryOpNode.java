/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import java.util.List;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

/**
 * Macro node for VectorSupport.libraryUnaryOp calls with a native math library address.
 */
@NodeInfo(nameTemplate = "VectorAPILibraryUnaryOp")
public class VectorAPILibraryUnaryOpNode extends VectorAPIMacroNode implements Canonicalizable {

    public static final NodeClass<VectorAPILibraryUnaryOpNode> TYPE = NodeClass.create(VectorAPILibraryUnaryOpNode.class);

    private final SimdStamp vectorStamp;
    private final ForeignCallDescriptor callDescriptor;

    /* Indices into the macro argument list for relevant input values. */
    private static final int ADDRESS_ARG_INDEX = 0;
    private static final int VCLASS_ARG_INDEX = 1;
    private static final int ECLASS_ARG_INDEX = 2;
    private static final int LENGTH_ARG_INDEX = 3;
    private static final int NAME_ARG_INDEX = 4;
    private static final int VALUE_ARG_INDEX = 5;

    protected VectorAPILibraryUnaryOpNode(MacroParams macroParams, SimdStamp vectorStamp) {
        this(macroParams, vectorStamp, null, null);
    }

    protected VectorAPILibraryUnaryOpNode(MacroParams macroParams, SimdStamp vectorStamp, ForeignCallDescriptor callDescriptor, FrameState stateAfter) {
        super(TYPE, macroParams, null);
        this.vectorStamp = vectorStamp;
        this.callDescriptor = callDescriptor;
        this.stateAfter = stateAfter;
    }

    public static VectorAPILibraryUnaryOpNode create(MacroParams macroParams, CoreProviders providers) {
        SimdStamp vectorStamp = improveVectorStamp(null, macroParams.arguments, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, providers);
        ForeignCallDescriptor callDescriptor = VectorAPILibraryOpSupport.improveCallDescriptor(null, macroParams.arguments, ADDRESS_ARG_INDEX, NAME_ARG_INDEX, vectorStamp, 1, providers);
        return new VectorAPILibraryUnaryOpNode(macroParams, vectorStamp, callDescriptor, null);
    }

    private ValueNode vector() {
        return getArgument(VALUE_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vector());
    }

    @Override
    public SimdStamp vectorStamp() {
        return vectorStamp;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ObjectStamp speciesStamp = (ObjectStamp) stamp;
        if (speciesStamp.isExactType() && vectorStamp != null) {
            return this;
        }

        ValueNode[] args = toArgumentArray();
        ObjectStamp newSpeciesStamp = improveSpeciesStamp(tool, VCLASS_ARG_INDEX);
        SimdStamp newVectorStamp = improveVectorStamp(vectorStamp, args, VCLASS_ARG_INDEX, ECLASS_ARG_INDEX, LENGTH_ARG_INDEX, tool);
        ForeignCallDescriptor newCallDescriptor = VectorAPILibraryOpSupport.improveCallDescriptor(callDescriptor, args, ADDRESS_ARG_INDEX, NAME_ARG_INDEX, newVectorStamp, 1, tool);
        if (newSpeciesStamp != speciesStamp || newVectorStamp != vectorStamp || newCallDescriptor != callDescriptor) {
            return new VectorAPILibraryUnaryOpNode(copyParamsWithImprovedStamp(newSpeciesStamp), newVectorStamp, newCallDescriptor, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        return callDescriptor != null && VectorAPILibraryOpSupport.canExpand((ObjectStamp) stamp, vectorStamp, vectorArch, simdStamps, vector());
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        return graph().add(new ForeignCallNode(callDescriptor, vectorStamp, List.of(expanded.get(vector()))));
    }
}
