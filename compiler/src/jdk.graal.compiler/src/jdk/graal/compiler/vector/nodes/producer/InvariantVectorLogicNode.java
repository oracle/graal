/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.util.Collections;
import java.util.List;

import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorProducer;
import jdk.graal.compiler.vector.nodes.VectorLogicNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorTransformation;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.VoidStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Placeholder node representing a logic vector whose elements are all true or all false according
 * to the scalar input condition. We need this to behave like a vectorized logic node for use by
 * vector guards and other vectorized control flow. However, we never generate SIMD code for this
 * node: The branch that uses this as its condition is expanded to a scalar {@link IfNode} during
 * either mid tier or low tier vector simplification.
 */
// @formatter:off
@NodeInfo(allowedUsageTypes = {Condition},
      cycles = CYCLES_0,
      cyclesRationale = "This placeholder disappears before code generation.",
      size = SIZE_0,
      sizeRationale = "This placeholder disappears before code generation.")
// @formatter:on
public class InvariantVectorLogicNode extends VectorLogicNode implements ShiftableVectorNode, SimdifyableVectorProducer {
    public static final NodeClass<InvariantVectorLogicNode> TYPE = NodeClass.create(InvariantVectorLogicNode.class);

    @Input(Condition) LogicNode condition;

    public InvariantVectorLogicNode(LogicNode condition) {
        this(condition, StampFactory.forVoid());
    }

    public InvariantVectorLogicNode(LogicNode condition, Stamp stamp) {
        super(TYPE, checkStamp(stamp));
        this.condition = condition;
    }

    private static Stamp checkStamp(Stamp stamp) {
        GraalError.guarantee(stamp instanceof VoidStamp || stamp instanceof VectorStamp || stamp instanceof SimdStamp,
                        "unexpected invariant vector logic stamp: %s", stamp);
        return stamp;
    }

    @Override
    public VectorTransformation createCopy(FixedNode insertBefore, ValueNode... vectorInputs) {
        assert vectorInputs.length == 0 : vectorInputs;
        /*
         * Most instances are scalar-condition placeholders and have a void stamp. An invariant
         * vector logic operation can also replace an existing vector logic producer, in which case
         * it must keep the producer's vector or SIMD stamp until vector simplification consumes it.
         */
        return graph().unique(new InvariantVectorLogicNode(condition, stamp(NodeView.DEFAULT)));
    }

    @Override
    public VectorNode simplify(VectorSimplifier simplifier) {
        return this;
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        return Collections.emptyList();
    }

    @Override
    public VectorNode shift(ValueNode index, GuardingNode guard, FixedNode insertBefore, ConstantReflectionProvider constantReflection) {
        return this;
    }

    @Override
    public LogicNode asScalar() {
        return condition;
    }

    @Override
    public LogicNode cutToScalar(int offset) {
        return condition;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch, int upperBound) {
        return upperBound;
    }

    @Override
    public LogicNode invariantCondition() {
        return condition;
    }

    @Override
    public ValueNode simdify(int length, Direction consumerDirection) {
        throw GraalError.shouldNotReachHere("should never try to simdify " + this + " (length " + length + ", consumer direction " + consumerDirection + ")"); // ExcludeFromJacocoGeneratedReport
    }
}
