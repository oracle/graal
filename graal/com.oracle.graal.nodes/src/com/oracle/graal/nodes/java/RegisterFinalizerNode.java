/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * This node is used to perform the finalizer registration at the end of the java.lang.Object
 * constructor.
 */
@NodeInfo
public class RegisterFinalizerNode extends AbstractStateSplit implements Canonicalizable.Unary<ValueNode>, LIRLowerable, Virtualizable, DeoptimizingNode.DeoptAfter {

    @OptionalInput(InputType.State) private FrameState deoptState;
    @Input private ValueNode value;

    public static RegisterFinalizerNode create(ValueNode value) {
        return new RegisterFinalizerNodeGen(value);
    }

    RegisterFinalizerNode(ValueNode value) {
        super(StampFactory.forVoid());
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

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (!(forValue.stamp() instanceof ObjectStamp)) {
            return this;
        }

        ObjectStamp stamp = (ObjectStamp) forValue.stamp();

        boolean needsCheck = true;
        if (stamp.isExactType()) {
            needsCheck = stamp.type().hasFinalizer();
        } else if (stamp.type() != null && !stamp.type().hasFinalizableSubclass()) {
            // if either the declared type of receiver or the holder
            // can be assumed to have no finalizers
            if (tool.assumptions().useOptimisticAssumptions()) {
                tool.assumptions().recordNoFinalizableSubclassAssumption(stamp.type());
                needsCheck = false;
            }
        }

        if (!needsCheck) {
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

    @SuppressWarnings("unused")
    @NodeIntrinsic
    public static void register(Object thisObj) {
    }
}
