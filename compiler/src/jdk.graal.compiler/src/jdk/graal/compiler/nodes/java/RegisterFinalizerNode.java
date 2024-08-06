/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.java;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_8;
import static jdk.graal.compiler.nodes.java.ForeignCallDescriptors.REGISTER_FINALIZER;

import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.nodes.AbstractStateSplit;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This node is used to perform the finalizer registration at the end of the java.lang.Object
 * constructor.
 *
 * Can be optimized away if the class may not have a finalizer (if we know the exact type or can
 * record an assumption that it does not have a finalizable subclass). On HotSpot, it is lowered to
 * a conditional runtime call (see {@code RegisterFinalizerSnippets.registerFinalizerSnippet}).
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot estimate the time of a runtime call.",
          size = SIZE_8,
          sizeRationale = "Rough estimation for register handling & calling")
// @formatter:on
public class RegisterFinalizerNode extends AbstractStateSplit implements Canonicalizable.Unary<ValueNode>, LIRLowerable, Lowerable, Virtualizable, DeoptimizingNode.DeoptAfter {

    public static final NodeClass<RegisterFinalizerNode> TYPE = NodeClass.create(RegisterFinalizerNode.class);
    @Input ValueNode value;

    public RegisterFinalizerNode(ValueNode value) {
        this(TYPE, value);
    }

    protected RegisterFinalizerNode(NodeClass<? extends RegisterFinalizerNode> c, ValueNode value) {
        super(c, StampFactory.forVoid());
        this.value = value;
    }

    @Override
    public ValueNode getValue() {
        return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        // Note that an unconditional call to the runtime routine is made without
        // checking that the object actually has a finalizer. This requires the
        // runtime routine to do the check.
        ForeignCallLinkage linkage = gen.getLIRGeneratorTool().getForeignCalls().lookupForeignCall(REGISTER_FINALIZER);
        gen.getLIRGeneratorTool().emitForeignCall(linkage, gen.state(this), gen.operand(getValue()));
    }

    /**
     * Determines if the compiler should emit code to test whether a given object has a finalizer
     * that must be registered with the runtime upon object initialization.
     */
    public static boolean mayHaveFinalizer(ValueNode object, MetaAccessProvider metaAccess, Assumptions assumptions) {
        ObjectStamp objectStamp = (ObjectStamp) object.stamp(NodeView.DEFAULT);
        ResolvedJavaType objectType = StampTool.typeOrNull(objectStamp, metaAccess);
        if (objectStamp.isExactType()) {
            return objectType.hasFinalizer();
        }
        AssumptionResult<Boolean> result = objectType.hasFinalizableSubclass();
        if (result.canRecordTo(assumptions)) {
            result.recordTo(assumptions);
            return result.getResult();
        }
        return true;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        NodeView view = NodeView.from(tool);
        if (!(forValue.stamp(view) instanceof ObjectStamp)) {
            return this;
        }
        if (!mayHaveFinalizer(forValue, tool.getMetaAccess(), graph().getAssumptions())) {
            return null;
        }

        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(getValue());
        if (alias instanceof VirtualObjectNode && !((VirtualObjectNode) alias).type().hasFinalizer()) {
            tool.delete();
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
