/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;
import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This duplicates a {@link MacroNode} using {@link WithExceptionNode} as a base class.
 *
 * See the documentation of {@link MacroInvokable} for more information.
 *
 * @see MacroInvokable
 * @see MacroNode
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate",
          size = SIZE_UNKNOWN,
          sizeRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate")
//@formatter:on
public abstract class MacroWithExceptionNode extends WithExceptionNode implements MacroInvokable {

    public static final NodeClass<MacroWithExceptionNode> TYPE = NodeClass.create(MacroWithExceptionNode.class);
    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    protected final int bci;
    protected final ResolvedJavaMethod callerMethod;
    protected final ResolvedJavaMethod targetMethod;
    protected final InvokeKind invokeKind;
    protected final StampPair returnStamp;

    protected MacroWithExceptionNode(NodeClass<? extends MacroWithExceptionNode> c, MacroParams p) {
        super(c, p.returnStamp != null ? p.returnStamp.getTrustedStamp() : null);
        this.arguments = new NodeInputList<>(this, p.arguments);
        this.bci = p.bci;
        this.callerMethod = p.callerMethod;
        this.targetMethod = p.targetMethod;
        this.returnStamp = p.returnStamp;
        this.invokeKind = p.invokeKind;
        assert !isPlaceholderBci(p.bci);
        assert MacroInvokable.assertArgumentCount(this);
    }

    @Override
    public ResolvedJavaMethod getContextMethod() {
        return callerMethod;
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    @Override
    public InvokeKind getInvokeKind() {
        return invokeKind;
    }

    @Override
    protected void afterClone(Node other) {
        updateInliningLogAfterClone(other);
    }

    @Override
    @SuppressWarnings("try")
    public Invoke replaceWithInvoke() {
        try (DebugCloseable context = withNodeSourcePosition()) {
            InvokeWithExceptionNode invoke = createInvoke();
            graph().replaceWithExceptionSplit(this, invoke);
            return invoke;
        }
    }

    protected InvokeWithExceptionNode createInvoke() {
        return createInvoke(this);
    }

    /**
     * Creates an invoke for the {@link #getTargetMethod()} associated with this node. The exception
     * edge of the result is not set by this function. This node is not modified.
     *
     * @param oldResult represents the result of this node in the {@link #stateAfter()}. Usually, it
     *            is {@code this}, but if this node has already been replaced it might be a
     *            different one.
     */
    public InvokeWithExceptionNode createInvoke(Node oldResult) {
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(invokeKind, targetMethod, getArguments().toArray(new ValueNode[arguments.size()]), returnStamp, null));
        InvokeWithExceptionNode invoke = graph().add(new InvokeWithExceptionNode(callTarget, null, bci));
        if (stateAfter() != null) {
            invoke.setStateAfter(stateAfter().duplicate());
            if (getStackKind() != JavaKind.Void) {
                invoke.stateAfter().replaceFirstInput(oldResult, invoke);
            }
        }
        return invoke;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        assert x == null || x.isAlive() : "frame state must be in a graph";
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public final boolean hasSideEffect() {
        return true;
    }

    /**
     * @see MacroNode#getKilledLocationIdentity()
     *
     *      FIXME: make this final once [GR-32638] is fixed
     */
    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

}
