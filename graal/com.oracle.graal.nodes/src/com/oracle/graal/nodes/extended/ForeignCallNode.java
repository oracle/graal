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
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call.
 */
@NodeInfo(nameTemplate = "ForeignCall#{p#descriptor/s}", allowedUsageTypes = {InputType.Memory})
public class ForeignCallNode extends AbstractMemoryCheckpoint implements LIRLowerable, DeoptimizingNode.DeoptDuring, MemoryCheckpoint.Multi {
    public static final NodeClass<ForeignCallNode> TYPE = NodeClass.create(ForeignCallNode.class);

    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(InputType.State) protected FrameState stateDuring;
    protected final ForeignCallsProvider foreignCalls;

    protected final ForeignCallDescriptor descriptor;
    protected int bci = BytecodeFrame.UNKNOWN_BCI;

    public ForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        this(TYPE, foreignCalls, descriptor, arguments);
    }

    public ForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp, List<ValueNode> arguments) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    public ForeignCallNode(@InjectedNodeParameter ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, Stamp stamp) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this);
        this.descriptor = descriptor;
        this.foreignCalls = foreignCalls;
    }

    protected ForeignCallNode(NodeClass<? extends ForeignCallNode> c, ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(c, StampFactory.forKind(Kind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
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

    protected Value[] operands(NodeLIRBuilderTool gen) {
        Value[] operands = new Value[arguments.size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = gen.operand(arguments.get(i));
        }
        return operands;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        ForeignCallLinkage linkage = gen.getLIRGeneratorTool().getForeignCalls().lookupForeignCall(descriptor);
        Value[] operands = operands(gen);
        Value result = gen.getLIRGeneratorTool().emitForeignCall(linkage, gen.state(this), operands);
        if (result != null) {
            gen.setResult(this, result);
        }
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert hasSideEffect() || x == null;
        super.setStateAfter(x);
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    /**
     * Set the {@code bci} of the invoke bytecode for use when converting a stateAfter into a
     * stateDuring.
     */
    public void setBci(int bci) {
        this.bci = bci;
    }

    @Override
    public void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring;
        if ((currentStateAfter.stackSize() > 0 && currentStateAfter.stackAt(currentStateAfter.stackSize() - 1) == this) ||
                        (currentStateAfter.stackSize() > 1 && currentStateAfter.stackAt(currentStateAfter.stackSize() - 2) == this)) {
            // The result of this call is on the top of stack, so roll back to the previous bci.
            assert bci != BytecodeFrame.UNKNOWN_BCI : this;
            newStateDuring = currentStateAfter.duplicateModifiedDuringCall(bci, this.getKind());
        } else {
            newStateDuring = currentStateAfter;
        }
        setStateDuring(newStateDuring);
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
}
