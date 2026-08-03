/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import java.util.Collection;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.vm.ci.code.CodeUtil;

public class DerivedConvertedInductionVariable extends DerivedInductionVariable {

    protected final Stamp stamp;
    protected final ValueNode value;

    public DerivedConvertedInductionVariable(Loop loop, InductionVariable base, Stamp stamp, ValueNode value) {
        super(loop, base);
        this.stamp = stamp;
        this.value = value;
    }

    @Override
    public boolean structuralIntegrityValid() {
        return super.structuralIntegrityValid() && value.isAlive();
    }

    @Override
    public ValueNode valueNode() {
        return value;
    }

    @Override
    public Direction direction() {
        return base.direction();
    }

    @Override
    public ValueNode initNode() {
        return op(base.initNode(), true);
    }

    @Override
    public ValueNode strideNode() {
        return op(base.strideNode(), false);
    }

    @Override
    public boolean isConstantInit() {
        return base.isConstantInit();
    }

    @Override
    public boolean isConstantStride() {
        return base.isConstantStride();
    }

    @Override
    public long constantInit() {
        return base.constantInit();
    }

    @Override
    public long constantStride() {
        return base.constantStride();
    }

    @Override
    public boolean isConstantExtremum() {
        return base.isConstantExtremum();
    }

    @Override
    public long constantExtremum() {
        return base.constantExtremum();
    }

    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp s) {
        return extremumNode(assumeLoopEntered, s, null);
    }

    /**
     * Computes the base endpoint in the base IV's native stamp, reapplies this conversion, and then
     * adapts the result to {@code requestedStamp}. Computing the base endpoint directly in
     * {@code requestedStamp} can skip an intermediate conversion. For example, it can make a
     * zero extension appear redundant after a narrowing and sign extension.
     */
    @Override
    public ValueNode extremumNode(boolean assumeLoopEntered, Stamp requestedStamp, ValueNode maxTripCount) {
        Stamp baseStamp = base.valueNode().stamp(NodeView.DEFAULT);
        ValueNode baseExtremum;
        if (maxTripCount == null) {
            baseExtremum = base.extremumNode(assumeLoopEntered, baseStamp);
        } else {
            baseExtremum = base.extremumNode(assumeLoopEntered, baseStamp, maxTripCount);
        }
        ValueNode converted = IntegerConvertNode.convert(baseExtremum, stamp, value instanceof ZeroExtendNode, graph(), NodeView.DEFAULT);
        return IntegerConvertNode.convert(converted, requestedStamp, false, graph(), NodeView.DEFAULT);
    }

    /**
     * Adds the condition under which a conversion introduces a discontinuity in the endpoint
     * range. The endpoints are those of the base IV, before this conversion is applied.
     * For example:
     * <pre>
     * baseInit = -2, baseExtremum = 2; baseRange = [-2, ..., 2]
     * -> zeroExtension
     * init = 0xFFFFFFFEL, extremum = 2L; range = [0xFFFFFFFEL, 0xFFFFFFFFL] U [0L,1L,2L]
     * </pre>
     * This discontinuity makes the IV range non-monotonic. The computed base endpoints' stamps
     * are used, rather than checking if the {@link #valueNode()}'s stamp is positive or strictly
     * negative - the conversion's input might use branch-local stamp refinements that make it
     * not applicable to whole-loop endpoint proofs.
     */
    void collectRangeEndpointConditions(ValueNode baseInit, ValueNode baseExtremum, Collection<LogicNode> conditions) {
        IntegerStamp initStamp = (IntegerStamp) baseInit.stamp(NodeView.DEFAULT);
        IntegerStamp extremumStamp = (IntegerStamp) baseExtremum.stamp(NodeView.DEFAULT);
        GraalError.guarantee(initStamp.isCompatible(extremumStamp),
                        "Expected compatible conversion endpoints for %s: %s, %s", this, baseInit, baseExtremum);
        if (value instanceof ZeroExtendNode) {
            LogicNode signChange;
            if (IntegerStamp.sameSign(initStamp, extremumStamp)) {
                return;
            } else if ((initStamp.isPositive() && extremumStamp.isStrictlyNegative()) ||
                            (initStamp.isStrictlyNegative() && extremumStamp.isPositive())) {
                signChange = LogicConstantNode.tautology();
            } else {
                ValueNode endpointsXor = graph().addOrUniqueWithInputs(XorNode.create(baseInit, baseExtremum, NodeView.DEFAULT));
                ValueNode zero = ConstantNode.forIntegerStamp(initStamp, 0, graph());
                // signChange = (init ^ extremum) < 0
                signChange = graph().addOrUniqueWithInputs(IntegerLessThanNode.create(endpointsXor, zero, NodeView.DEFAULT));
            }
            if (!signChange.isContradiction()) {
                conditions.add(graph().addOrUniqueWithInputs(signChange));
            }
        }
    }

    @Override
    protected ValueNode collectLocalEndpointOverflowConditions(boolean assumeLoopEntered, Stamp endpointStamp, ValueNode effectiveMaxTripCount, ValueNode baseEndpoint,
                    Collection<LogicNode> conditions) {
        GraalError.guarantee(baseEndpoint != null, "Expected base endpoint for %s", this);
        /*
         * An integer conversion does not add new endpoint arithmetic of its own. The base IV's
         * overflow conditions already cover the computation whose result is being converted.
         * Zero extensions are handled separately in #collectRangeEndpointConditions, while
         * narrowing must check that converting the computed base endpoints is exact.
         */
        if (value instanceof NarrowNode narrow) {
            collectEndpointNarrowingConditions(narrow, baseEndpoint, conditions);
        }
        return IntegerConvertNode.convert(baseEndpoint, endpointStamp, value instanceof ZeroExtendNode, graph(), NodeView.DEFAULT);
    }

    /**
     * Adds conditions that are true when narrowing {@code baseEndpoint} to the result width of
     * {@code narrow} loses information.
     * <p>
     * This method is called separately for the initial and extremum endpoints. If either endpoint would
     * wrap when narrowed, the range is not monotonic, so it is unsafe to apply whole-loop endpoint proofs.
     * For example:
     * <pre>
     * baseInit = (long) Integer.MAX_VALUE + 10
     * baseExtremum = (long) Integer.MAX_VALUE - 5
     * -> narrow to int
     * init = Integer.MIN_VALUE + 9
     * extremum = Integer.MAX_VALUE - 5
     * </pre>
     * The initial endpoint wraps, creating a discontinuity.
     * <p>
     * Note that even if the underlying {@link NarrowNode} is lossless based on
     * its input's stamp, the stamp might use branch-local refinements, but monotonicity proofs
     * follow the underlying IV's whole range, so that alone cannot be used to prove safety.
     */
    private void collectEndpointNarrowingConditions(NarrowNode narrow, ValueNode baseEndpoint, Collection<LogicNode> conditions) {
        int resultBits = narrow.getResultBits();
        IntegerStamp sourceStamp = (IntegerStamp) baseEndpoint.stamp(NodeView.DEFAULT);
        ValueNode min = ConstantNode.forIntegerStamp(sourceStamp, CodeUtil.minValue(resultBits), graph());
        ValueNode max = ConstantNode.forIntegerStamp(sourceStamp, CodeUtil.maxValue(resultBits), graph());
        LogicNode outOfRangeLow = IntegerLessThanNode.create(baseEndpoint, min, NodeView.DEFAULT);
        LogicNode outOfRangeHigh = IntegerLessThanNode.create(max, baseEndpoint, NodeView.DEFAULT);
        if (!outOfRangeLow.isContradiction()) {
            conditions.add(graph().addOrUniqueWithInputs(outOfRangeLow));
        }
        if (!outOfRangeHigh.isContradiction()) {
            conditions.add(graph().addOrUniqueWithInputs(outOfRangeHigh));
        }
    }

    @Override
    public ValueNode exitValueNode() {
        return op(base.exitValueNode(), true);
    }

    @Override
    public void deleteUnusedNodes() {
    }

    public ValueNode op(ValueNode v, boolean allowZeroExtend) {
        return op(v, allowZeroExtend, true);
    }

    private ValueNode op(ValueNode v, boolean allowZeroExtend, boolean gvn) {
        boolean zeroExtend = allowZeroExtend && value instanceof ZeroExtendNode;
        return IntegerConvertNode.convert(v, stamp, zeroExtend, graph(), NodeView.DEFAULT, gvn);
    }

    @Override
    public String toString(IVToStringVerbosity verbosity) {
        if (verbosity == IVToStringVerbosity.FULL) {
            return String.format("DerivedConvertedInductionVariable base (%s) %s %s", base, value.getNodeClass().shortName(), stamp);
        } else {
            return String.format("(%s) %s %s", base, value.getNodeClass().shortName(), stamp);
        }
    }

    @Override
    public InductionVariable copy(InductionVariable newBase, ValueNode newValue) {
        return new DerivedConvertedInductionVariable(loop, newBase, stamp, newValue);
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase) {
        return op(newBase.valueNode(), true);
    }

    @Override
    public ValueNode copyValue(InductionVariable newBase, boolean gvn) {
        return op(newBase.valueNode(), true, gvn);
    }

    @Override
    public ValueNode entryTripValue() {
        return op(getBase().entryTripValue(), true);
    }
}
