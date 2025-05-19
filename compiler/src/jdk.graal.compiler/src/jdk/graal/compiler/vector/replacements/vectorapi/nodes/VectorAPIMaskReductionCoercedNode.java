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

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import java.util.List;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.replacements.nodes.BitCountNode;
import jdk.graal.compiler.replacements.nodes.CountLeadingZerosNode;
import jdk.graal.compiler.replacements.nodes.CountTrailingZerosNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.amd64.OpMaskToIntegerNode;
import jdk.graal.compiler.vector.nodes.simd.LogicValueStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.nodes.simd.SimdToBitMaskNode;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsic node for the {@code VectorSupport.maskReductionCoerced} method. This operation performs
 * reduction operations on masks such as converting to bitmask integers, finding the indices of the
 * first/last true element.
 */
@NodeInfo
public class VectorAPIMaskReductionCoercedNode extends VectorAPISinkNode implements Canonicalizable {

    public static final NodeClass<VectorAPIMaskReductionCoercedNode> TYPE = NodeClass.create(VectorAPIMaskReductionCoercedNode.class);

    public enum Op {
        TO_LONG,
        TRUE_COUNT,
        FIRST_TRUE,
        LAST_TRUE
    }

    /* Indices into the macro argument list for relevant input values. */
    private static final int OPRID_ARG_INDEX = 0;
    private static final int MASK_ARG_INDEX = 4;

    private final Op op;

    protected VectorAPIMaskReductionCoercedNode(MacroParams macroParams, Op op, FrameState stateAfter) {
        super(TYPE, macroParams);
        this.op = op;
        this.stateAfter = stateAfter;
    }

    public static VectorAPIMaskReductionCoercedNode create(MacroParams macroParams) {
        Op op = computeOp(null, macroParams.arguments[OPRID_ARG_INDEX]);
        return new VectorAPIMaskReductionCoercedNode(macroParams, op, null);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(inputMask());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Op newOp = computeOp(op, getArgument(OPRID_ARG_INDEX));
        if (newOp != op) {
            return new VectorAPIMaskReductionCoercedNode(copyParams(), newOp, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        return op != null;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        ValueNode mask = expanded.get(inputMask());
        SimdStamp maskStamp = (SimdStamp) mask.stamp(NodeView.DEFAULT);
        ValueNode toLong;
        if (maskStamp.getComponent(0) instanceof LogicValueStamp) {
            toLong = OpMaskToIntegerNode.create(mask);
        } else {
            toLong = new SimdToBitMaskNode(mask);
        }
        if (op == Op.TO_LONG) {
            return toLong;
        }

        ValueNode res = switch (op) {
            case TRUE_COUNT -> new BitCountNode(toLong);
            case FIRST_TRUE -> {
                if (maskStamp.getVectorLength() == Integer.SIZE) {
                    toLong = NarrowNode.create(toLong, Integer.SIZE, NodeView.DEFAULT);
                } else if (maskStamp.getVectorLength() < Integer.SIZE) {
                    toLong = OrNode.create(toLong, ConstantNode.forLong(1L << maskStamp.getVectorLength()), NodeView.DEFAULT);
                }
                yield CountTrailingZerosNode.create(toLong);
            }
            case LAST_TRUE -> SubNode.create(ConstantNode.forInt(63), CountLeadingZerosNode.create(toLong), NodeView.DEFAULT);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(op);
        };
        return reinterpretAsLong(res);
    }

    private ValueNode inputMask() {
        return getArgument(MASK_ARG_INDEX);
    }

    private static Op computeOp(Op oldOp, ValueNode opId) {
        if (oldOp != null) {
            return oldOp;
        }

        if (opId.isJavaConstant() && opId.asJavaConstant().getJavaKind() == JavaKind.Int) {
            return VectorAPIOperations.lookupMaskReduction(opId.asJavaConstant().asInt());
        }
        return null;
    }
}
