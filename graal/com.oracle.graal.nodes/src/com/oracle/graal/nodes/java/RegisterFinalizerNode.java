/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.java;

import static com.oracle.graal.nodes.java.ForeignCallDescriptors.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.meta.Assumptions.AssumptionResult;

/**
 * This node is used to perform the finalizer registration at the end of the java.lang.Object
 * constructor.
 */
@NodeInfo
public final class RegisterFinalizerNode extends AbstractStateSplit implements Canonicalizable.Unary<ValueNode>, LIRLowerable, Virtualizable, DeoptimizingNode.DeoptAfter {

    public static final NodeClass<RegisterFinalizerNode> TYPE = NodeClass.create(RegisterFinalizerNode.class);
    @OptionalInput(InputType.State) FrameState deoptState;
    @Input ValueNode value;

    public RegisterFinalizerNode(ValueNode value) {
        super(TYPE, StampFactory.forVoid());
        this.value = value;
    }

    public ValueNode getValue() {
        return value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ForeignCallLinkage linkage = gen.getLIRGeneratorTool().getForeignCalls().lookupForeignCall(REGISTER_FINALIZER);
        gen.getLIRGeneratorTool().emitForeignCall(linkage, gen.state(this), gen.operand(getValue()));
    }

    /**
     * Determines if the compiler should emit code to test whether a given object has a finalizer
     * that must be registered with the runtime upon object initialization.
     */
    public static boolean mayHaveFinalizer(ValueNode object, Assumptions assumptions) {
        ObjectStamp objectStamp = (ObjectStamp) object.stamp();
        if (objectStamp.isExactType()) {
            return objectStamp.type().hasFinalizer();
        } else if (objectStamp.type() != null) {
            AssumptionResult<Boolean> result = objectStamp.type().hasFinalizableSubclass();
            if (result.isAssumptionFree()) {
                return result.getResult();
            } else if (assumptions != null) {
                assumptions.record(result);
                return result.getResult();
            }
        }
        return true;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (!(forValue.stamp() instanceof ObjectStamp)) {
            return this;
        }
        if (!mayHaveFinalizer(forValue, graph().getAssumptions())) {
            return null;
        }

        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        State state = tool.getObjectState(getValue());
        if (state != null && !state.getVirtualObject().type().hasFinalizer()) {
            tool.delete();
        }
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    @NodeIntrinsic
    public static native void register(Object thisObj);
}
