/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.InputType.State;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_2;

import java.util.List;
import java.util.Map;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node.NodeIntrinsicFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.memory.AbstractMemoryCheckpoint;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call.
 */
// @formatter:off
@NodeInfo(nameTemplate = "ForeignCall#{p#descriptorName/s}",
          allowedUsageTypes = Memory,
          cycles = CYCLES_2,
          cyclesRationale = "Rough estimation of the call operation itself.",
          size = SIZE_2,
          sizeRationale = "Rough estimation of the call operation itself.")
// @formatter:on
@NodeIntrinsicFactory
public class ForeignCallNode extends AbstractMemoryCheckpoint implements ForeignCall {
    public static final NodeClass<ForeignCallNode> TYPE = NodeClass.create(ForeignCallNode.class);

    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(State) protected FrameState stateDuring;

    protected final ForeignCallDescriptor descriptor;
    protected int bci = BytecodeFrame.UNKNOWN_BCI;
    private boolean validateDeoptFrameStates = true;

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter Stamp returnStamp, @InjectedNodeParameter ForeignCallsProvider foreignCalls,
                    ForeignCallSignature signature, ValueNode... arguments) {
        ForeignCallDescriptor descriptor = foreignCalls.getDescriptor(signature);
        return finishIntrinsification(b, returnStamp, new ForeignCallNode(descriptor, arguments));
    }

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter Stamp returnStamp, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        return finishIntrinsification(b, returnStamp, new ForeignCallNode(descriptor, arguments));
    }

    public static boolean finishIntrinsification(GraphBuilderContext b, Stamp returnStamp, ForeignCall node) {
        node.asNode().setStamp(returnStamp);

        /*
         * Need to update the BCI of a ForeignCallNode so that it gets the stateDuring in the case
         * that the foreign call can deoptimize. As with all deoptimization, we need a state in a
         * non-intrinsic method.
         */
        GraphBuilderContext nonIntrinsicAncestor = b.getNonIntrinsicAncestor();
        if (nonIntrinsicAncestor != null) {
            node.setBci(nonIntrinsicAncestor.bci());
        }

        JavaKind returnKind = returnStamp.getStackKind();
        if (returnKind == JavaKind.Void) {
            b.add(node.asNode());
        } else {
            b.addPush(returnKind, node.asNode());
        }

        return true;
    }

    public ForeignCallNode(ForeignCallsProvider foreignCalls, ForeignCallSignature signature, ValueNode... arguments) {
        this(TYPE, foreignCalls.getDescriptor(signature), arguments);
    }

    public ForeignCallNode(ForeignCallDescriptor descriptor, ValueNode... arguments) {
        this(TYPE, descriptor, arguments);
    }

    @SuppressWarnings("this-escape")
    public ForeignCallNode(ForeignCallDescriptor descriptor, Stamp stamp, List<ValueNode> arguments) {
        super(TYPE, stamp);
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    @SuppressWarnings("this-escape")
    protected ForeignCallNode(NodeClass<? extends ForeignCallNode> c, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(c, StampFactory.forKind(JavaKind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    @Override
    public boolean hasSideEffect() {
        return descriptor.getSideEffect() == ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
    }

    @Override
    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
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

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        assert this.bci == BytecodeFrame.UNKNOWN_BCI || this.bci == bci : Assertions.errorMessageContext("this", this, "bci", bci);
        this.bci = bci;
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor.getName();
        }
        return super.toString(verbosity);
    }

    @Override
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Map<Object, Object> debugProperties = super.getDebugProperties(map);
        debugProperties.put("descriptorName", descriptor.getName());
        return debugProperties;
    }

    @Override
    public boolean validateDeoptFrameStates() {
        return validateDeoptFrameStates;
    }

    public void setValidateDeoptFrameStates(boolean value) {
        validateDeoptFrameStates = value;
    }
}
