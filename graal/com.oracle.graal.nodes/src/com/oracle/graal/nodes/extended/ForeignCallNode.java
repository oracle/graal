/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.extended;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call.
 */
@NodeInfo(nameTemplate = "ForeignCall#{p#descriptor/s}")
public class ForeignCallNode extends AbstractMemoryCheckpoint implements LIRLowerable, DeoptimizingNode, MemoryCheckpoint.Multi {

    @Input private final NodeInputList<ValueNode> arguments;
    private final ForeignCallsProvider foreignCalls;
    @Input private FrameState deoptState;

    private final ForeignCallDescriptor descriptor;

    public ForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(StampFactory.forKind(Kind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    public ForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, List<ValueNode> arguments) {
        this(foreignCalls, descriptor, StampFactory.forKind(Kind.fromJavaClass(descriptor.getResultType())), arguments);
    }

    public ForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp, List<ValueNode> arguments) {
        super(stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    protected ForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp) {
        super(stamp);
        this.arguments = new NodeInputList<>(this);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    @Override
    public boolean hasSideEffect() {
        return !foreignCalls.isReexecutable(descriptor);
    }

    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        return foreignCalls.getKilledLocations(descriptor);
    }

    protected Value[] operands(LIRGeneratorTool gen) {
        Value[] operands = new Value[arguments.size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = gen.operand(arguments.get(i));
        }
        return operands;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        ForeignCallLinkage linkage = gen.getForeignCalls().lookupForeignCall(descriptor);
        Value[] operands = operands(gen);
        Value result = gen.emitForeignCall(linkage, this, operands);
        if (result != null) {
            gen.setResult(this, result);
        }
    }

    @Override
    public FrameState getDeoptimizationState() {
        if (deoptState != null) {
            return deoptState;
        } else if (stateAfter() != null && canDeoptimize()) {
            FrameState stateDuring = stateAfter();
            if ((stateDuring.stackSize() > 0 && stateDuring.stackAt(stateDuring.stackSize() - 1) == this) || (stateDuring.stackSize() > 1 && stateDuring.stackAt(stateDuring.stackSize() - 2) == this)) {
                stateDuring = stateDuring.duplicateModified(stateDuring.bci, stateDuring.rethrowException(), this.getKind());
            }
            setDeoptimizationState(stateDuring);
            return stateDuring;
        }
        return null;
    }

    @Override
    public void setDeoptimizationState(FrameState f) {
        updateUsages(deoptState, f);
        assert deoptState == null && canDeoptimize() : "shouldn't assign deoptState to " + this;
        deoptState = f;
    }

    @Override
    public void setStateAfter(FrameState x) {
        if (hasSideEffect()) {
            super.setStateAfter(x);
        }
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }

    @Override
    public boolean canDeoptimize() {
        return foreignCalls.canDeoptimize(descriptor);
    }

    @Override
    public FrameState getState() {
        if (deoptState != null) {
            assert stateAfter() == null;
            return deoptState;
        } else {
            return super.getState();
        }
    }
}
