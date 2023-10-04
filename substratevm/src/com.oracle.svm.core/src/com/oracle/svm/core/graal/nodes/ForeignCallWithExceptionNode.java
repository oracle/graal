/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import static jdk.compiler.graal.nodeinfo.InputType.Memory;
import static jdk.compiler.graal.nodeinfo.InputType.State;
import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_2;

import jdk.compiler.graal.core.common.spi.ForeignCallDescriptor;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node.NodeIntrinsicFactory;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.graph.NodeInputList;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodeinfo.Verbosity;
import jdk.compiler.graal.nodes.AbstractBeginNode;
import jdk.compiler.graal.nodes.FixedNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.UnreachableBeginNode;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.WithExceptionNode;
import jdk.compiler.graal.nodes.extended.ForeignCall;
import jdk.compiler.graal.nodes.extended.ForeignCallNode;
import jdk.compiler.graal.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.compiler.graal.nodes.spi.Simplifiable;
import jdk.compiler.graal.nodes.spi.SimplifierTool;
import jdk.compiler.graal.nodes.util.GraphUtil;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;

/**
 * Node for a {@linkplain ForeignCallDescriptor foreign} call with an {@linkplain WithExceptionNode
 * exception edge}.
 */
// @formatter:off
@NodeInfo(nameTemplate = "ForeignCallWithException#{p#descriptor/s}",
          allowedUsageTypes = Memory,
          cycles = CYCLES_2,
          cyclesRationale = "Rough estimation of the call operation itself.",
          size = SIZE_2,
          sizeRationale = "Rough estimation of the call operation itself.")
// @formatter:on
@NodeIntrinsicFactory
public class ForeignCallWithExceptionNode extends WithExceptionNode implements ForeignCall, Simplifiable {
    public static final NodeClass<ForeignCallWithExceptionNode> TYPE = NodeClass.create(ForeignCallWithExceptionNode.class);

    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(State) protected FrameState stateDuring;
    @OptionalInput(State) protected FrameState stateAfter;

    protected final ForeignCallDescriptor descriptor;
    protected int bci = BytecodeFrame.UNKNOWN_BCI;

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter Stamp returnStamp, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        return ForeignCallNode.finishIntrinsification(b, returnStamp, new ForeignCallWithExceptionNode(descriptor, arguments));
    }

    public ForeignCallWithExceptionNode(ForeignCallDescriptor descriptor, ValueNode... arguments) {
        this(TYPE, descriptor, arguments);
    }

    @SuppressWarnings("this-escape")
    protected ForeignCallWithExceptionNode(NodeClass<? extends ForeignCallWithExceptionNode> c, ForeignCallDescriptor descriptor, ValueNode... arguments) {
        super(c, StampFactory.forKind(JavaKind.fromJavaClass(descriptor.getResultType())));
        this.arguments = new NodeInputList<>(this, arguments);
        this.descriptor = descriptor;
        assert descriptor.getArgumentTypes().length == this.arguments.size() : "wrong number of arguments to " + this;
    }

    @Override
    public boolean hasSideEffect() {
        return !descriptor.isReexecutable();
    }

    @Override
    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState stateAfter) {
        assert hasSideEffect() || stateAfter == null;
        updateUsages(this.stateAfter(), stateAfter);
        this.stateAfter = stateAfter;
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
        assert this.bci == BytecodeFrame.UNKNOWN_BCI || this.bci == bci;
        this.bci = bci;
    }

    @Override
    public String toString(Verbosity verbosity) {
        if (verbosity == Verbosity.Name) {
            return super.toString(verbosity) + "#" + descriptor;
        }
        return super.toString(verbosity);
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (exceptionEdge instanceof UnreachableBeginNode) {
            FixedNode replacement = replaceWithNonThrowing();
            tool.addToWorkList(replacement);
        }
    }

    @Override
    public FixedNode replaceWithNonThrowing() {
        ForeignCallNode foreignCall = this.asNode().graph().add(new ForeignCallNode(descriptor, stamp, arguments));
        foreignCall.setStateAfter(stateAfter());
        foreignCall.setStateDuring(stateDuring());

        AbstractBeginNode nextBegin = this.next;
        AbstractBeginNode oldException = this.exceptionEdge;
        graph().replaceSplitWithFixed(this, foreignCall, nextBegin);
        GraphUtil.killCFG(oldException);
        return foreignCall;
    }
}
