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

import static jdk.graal.compiler.replacements.nodes.MacroNode.MacroParams;

import java.util.List;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdMaskLogicNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIOperations;
import jdk.graal.compiler.vector.replacements.vectorapi.VectorAPIUtils;
import jdk.vm.ci.meta.JavaKind;

/**
 * Intrinsic node for the {@code VectorSupport.test} method. This operation computes a property of
 * the input vector masks {@code x} and {@code y}, producing a scalar boolean result:
 * <p/>
 *
 * {@code
 * result = x.0 | x.1 | ... | x.n  // "any true" operation
 * }
 * <p/>
 * or
 * <p/>
 * {@code
 * result = x.0 & x.1 & ... & x.n  // "all true" operation
 * }
 *
 * <p/>
 * In the current version of the Vector API, the {@code y} mask is always -1 (all bits set) and not
 * used otherwise. The operation is identified by an integer condition code which we map to the
 * corresponding operation.
 */
@NodeInfo
public class VectorAPITestNode extends VectorAPISinkNode implements Canonicalizable {

    public static final NodeClass<VectorAPITestNode> TYPE = NodeClass.create(VectorAPITestNode.class);

    private final SimdMaskLogicNode.Condition condition;

    /* Indices into the macro argument list for relevant input values. */
    private static final int COND_ARG_INDEX = 0;
    private static final int X_ARG_INDEX = 4;
    private static final int Y_ARG_INDEX = 5;

    protected VectorAPITestNode(MacroParams macroParams, SimdMaskLogicNode.Condition condition, FrameState stateAfter) {
        super(TYPE, macroParams);
        this.condition = condition;
        this.stateAfter = stateAfter;
    }

    public static VectorAPITestNode create(MacroParams macroParams) {
        SimdMaskLogicNode.Condition condition = improveCondition(null, macroParams.arguments);
        return new VectorAPITestNode(macroParams, condition, null);
    }

    private static SimdMaskLogicNode.Condition improveCondition(SimdMaskLogicNode.Condition oldCondition, ValueNode[] arguments) {
        if (oldCondition != null) {
            return oldCondition;
        }
        ValueNode cond = arguments[COND_ARG_INDEX];
        ValueNode y = arguments[Y_ARG_INDEX];
        /*
         * For now, only support the "any true" and "all true" tests. These are represented with the
         * second operand being -1L coerced to a logic stamp. The condition code is the one encoding
         * the standard NE condition for anyTrue and 2 (overflow, not one of our standard
         * conditions) for allTrue.
         */
        if (!(cond.isJavaConstant() && cond.asJavaConstant().getJavaKind() == JavaKind.Int)) {
            return null;
        }
        int conditionCode = cond.asJavaConstant().asInt();
        Condition conditionValue = VectorAPIOperations.lookupCondition(conditionCode);
        final int overflowCode = 2; /* VectorSupport.BT_overflow */
        if (!(conditionValue == Condition.NE || conditionCode == overflowCode)) {
            return null;
        }
        Long constantOperand = null;
        if (y instanceof VectorAPIFromBitsCoercedNode fromBitsCoerced) {
            ValueNode maybeConst = fromBitsCoerced.getValue();
            if (maybeConst.isJavaConstant() && maybeConst.asJavaConstant().getJavaKind() == JavaKind.Long) {
                constantOperand = maybeConst.asJavaConstant().asLong();
            }
        }
        if (constantOperand == null || constantOperand != -1) {
            return null;
        }
        SimdMaskLogicNode.Condition maskCondition = null;
        if (conditionValue == Condition.NE && constantOperand == -1) {
            // anyTrue, i.e., not all zeros
            maskCondition = SimdMaskLogicNode.Condition.ALL_ZEROS;
        } else if (conditionCode == overflowCode && constantOperand == -1) {
            // allTrue
            maskCondition = SimdMaskLogicNode.Condition.ALL_ONES;
        }
        return maskCondition;
    }

    public ValueNode vectorX() {
        return getArgument(X_ARG_INDEX);
    }

    public ValueNode vectorY() {
        return getArgument(Y_ARG_INDEX);
    }

    @Override
    public Iterable<ValueNode> vectorInputs() {
        return List.of(vectorX(), vectorY());
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (condition != null) {
            /* Nothing to improve. */
            return this;
        }

        SimdMaskLogicNode.Condition newCondition = improveCondition(condition, toArgumentArray());
        if (newCondition != condition) {
            return new VectorAPITestNode(copyParams(), newCondition, stateAfter());
        }
        return this;
    }

    @Override
    public boolean canExpand(VectorArchitecture vectorArch, EconomicMap<ValueNode, Stamp> simdStamps) {
        if (condition == null) {
            return false;
        }
        if (vectorArch.logicVectorsAreBitmasks()) {
            SimdStamp simdStamp = (SimdStamp) simdStamps.get(vectorX());
            Stamp logicElement = simdStamp.getComponent(0);
            return vectorArch.getSupportedSimdMaskLogicLength(logicElement, simdStamp.getVectorLength()) == simdStamp.getVectorLength();
        }
        return true;
    }

    @Override
    public ValueNode expand(VectorArchitecture vectorArch, NodeMap<ValueNode> expanded) {
        ValueNode x = expanded.get(vectorX());
        /*
         * We don't want to look at the second argument, it only encodes our operation. All the
         * relevant information about it should have been computed when this node was built or
         * canonicalized (see the computeCondition() method). When this node was originally created,
         * its input was a VectorAPIFromBitsCoercedNode, but we cannot guarantee that here because
         * the graph may have changed in the meantime.
         */

        ValueNode logicVector;
        if (vectorArch.logicVectorsAreBitmasks()) {
            logicVector = VectorAPIUtils.isNonzero(x, vectorArch);
        } else {
            GraalError.guarantee(SimdStamp.isOpmask(x.stamp(NodeView.DEFAULT)), "We only expect op masks as inputs for VectorAPITest");
            logicVector = x;
        }
        LogicNode logicValue = new SimdMaskLogicNode(logicVector, condition);
        /*
         * We use ALL_ZEROS for the anyTrue, i.e., "not all zeros" check. So negate if this is our
         * condition.
         */
        boolean negate = (condition == SimdMaskLogicNode.Condition.ALL_ZEROS);
        if (negate) {
            logicValue = LogicNegationNode.create(logicValue);
        }
        return ConditionalNode.create(logicValue, ConstantNode.forInt(1), ConstantNode.forInt(0), NodeView.DEFAULT);
    }
}
