/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InliningLog;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * @see MacroInvokable
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
          cyclesRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate",
          size = SIZE_UNKNOWN,
          sizeRationale = "If this node is not optimized away it will be lowered to a call, which we cannot estimate")
//@formatter:on
public abstract class MacroNode extends FixedWithNextNode implements MacroInvokable {

    public static final NodeClass<MacroNode> TYPE = NodeClass.create(MacroNode.class);
    @Input protected NodeInputList<ValueNode> arguments;
    @OptionalInput(InputType.State) protected FrameState stateAfter;

    protected final int bci;
    protected final ResolvedJavaMethod callerMethod;
    protected final ResolvedJavaMethod targetMethod;
    protected final InvokeKind invokeKind;
    protected final StampPair returnStamp;

    /**
     * The original target method for a MethodHandle invoke call site. See
     * {@link ResolvedMethodHandleCallTargetNode}.
     */
    protected ResolvedJavaMethod originalTargetMethod;

    /**
     * The original return stamp for a MethodHandle invoke call site. See
     * {@link ResolvedMethodHandleCallTargetNode}.
     */
    protected StampPair originalReturnStamp;

    /**
     * The original arguments for a MethodHandle invoke call site. See
     * {@link ResolvedMethodHandleCallTargetNode}.
     */
    @Input NodeInputList<ValueNode> originalArguments;

    /**
     * Encapsulates the parameters for constructing a {@link MacroNode} that are the same for all
     * leaf constructor call sites. Collecting the parameters in an object simplifies passing the
     * parameters through the many chained constructor calls.
     */
    public static class MacroParams {
        public final InvokeKind invokeKind;
        public final ResolvedJavaMethod callerMethod;
        public final ResolvedJavaMethod targetMethod;
        public final int bci;
        public final StampPair returnStamp;
        public final ValueNode[] arguments;

        public MacroParams(InvokeKind invokeKind,
                        ResolvedJavaMethod callerMethod,
                        ResolvedJavaMethod targetMethod,
                        int bci,
                        StampPair returnStamp,
                        ValueNode... arguments) {
            this.invokeKind = invokeKind;
            this.callerMethod = callerMethod;
            this.targetMethod = targetMethod;
            this.bci = bci;
            this.returnStamp = returnStamp;
            this.arguments = arguments;
        }

        public static MacroParams of(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
            return new MacroParams(b.getInvokeKind(), b.getMethod(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), arguments);
        }

        public static MacroParams of(GraphBuilderContext b, ResolvedJavaMethod targetMethod, StampPair returnStamp, ValueNode... arguments) {
            return new MacroParams(b.getInvokeKind(), b.getMethod(), targetMethod, b.bci(), returnStamp, arguments);
        }

        public static MacroParams of(GraphBuilderContext b, InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode... arguments) {
            return new MacroParams(invokeKind, b.getMethod(), targetMethod, b.bci(), b.getInvokeReturnStamp(b.getAssumptions()), arguments);
        }

        public static MacroParams of(InvokeKind invokeKind,
                        ResolvedJavaMethod callerMethod,
                        ResolvedJavaMethod targetMethod,
                        int bci,
                        StampPair returnStamp,
                        ValueNode... arguments) {
            return new MacroParams(invokeKind, callerMethod, targetMethod, bci, returnStamp, arguments);
        }
    }

    @SuppressWarnings("this-escape")
    protected MacroNode(NodeClass<? extends MacroNode> c, MacroParams p) {
        super(c, p.returnStamp != null ? p.returnStamp.getTrustedStamp() : null);
        this.arguments = new NodeInputList<>(this, p.arguments);
        this.bci = p.bci;
        this.callerMethod = p.callerMethod;
        this.targetMethod = p.targetMethod;
        this.returnStamp = p.returnStamp;
        this.invokeKind = p.invokeKind;
        assert !isPlaceholderBci(p.bci);
        assert MacroInvokable.assertArgumentCount(this);
        this.originalArguments = new NodeInputList<>(this);
    }

    @Override
    public boolean inferStamp() {
        verifyStamp();
        return false;
    }

    protected void verifyStamp() {
        GraalError.guarantee(returnStamp.getTrustedStamp().equals(stamp(NodeView.DEFAULT)), "Stamp of replaced node %s must be the same as the original Invoke %s, but is %s ",
                        this, returnStamp.getTrustedStamp(), stamp(NodeView.DEFAULT));
    }

    @Override
    public ResolvedJavaMethod getContextMethod() {
        return callerMethod;
    }

    @Override
    public NodeInputList<ValueNode> getArguments() {
        return arguments;
    }

    public ValueNode[] toArgumentArray() {
        return arguments.toArray(ValueNode.EMPTY_ARRAY);
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
    public StampPair getReturnStamp() {
        return returnStamp;
    }

    @Override
    public NodeInputList<ValueNode> getOriginalArguments() {
        return originalArguments;
    }

    @Override
    public ResolvedJavaMethod getOriginalTargetMethod() {
        return originalTargetMethod;
    }

    @Override
    public StampPair getOriginalReturnStamp() {
        return originalReturnStamp;
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
     * Returns {@link LocationIdentity#any()}. This node needs to kill any location because it might
     * get {@linkplain MacroInvokable#replaceWithInvoke() replaced with an invoke} and
     * {@link InvokeNode#getKilledLocationIdentity()} kills {@link LocationIdentity#any()} and the
     * kill location must not get broader.
     */
    @Override
    public final LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    protected void afterClone(Node other) {
        updateInliningLogAfterClone(other);
    }

    @Override
    @SuppressWarnings("try")
    public Invoke replaceWithInvoke() {
        try (DebugCloseable context = withNodeSourcePosition(); InliningLog.UpdateScope updateScope = InliningLog.openUpdateScopeTrackingReplacement(graph().getInliningLog(), this)) {
            InvokeNode invoke = createInvoke(true);
            graph().replaceFixedWithFixed(this, invoke);
            assert invoke.verify();
            return invoke;
        }
    }

    public LocationIdentity getLocationIdentity() {
        return LocationIdentity.any();
    }

    /**
     * Replace this node with the equivalent invoke. If we are falling back to the original invoke
     * then the stamp of the current node isn't permitted to be different than the actual invoke
     * because this would leave the graph in an inconsistent state.
     */
    protected InvokeNode createInvoke(boolean verifyStamp) {
        MethodCallTargetNode callTarget = createCallTarget();
        InvokeNode invoke = graph().add(new InvokeNode(callTarget, bci, getLocationIdentity()));
        if (stateAfter() != null) {
            invoke.setStateAfter(stateAfter().duplicate());
            if (getStackKind() != JavaKind.Void) {
                invoke.stateAfter().replaceFirstInput(this, invoke);
            }
        }
        if (verifyStamp) {
            verifyStamp();
        }
        return invoke;
    }

    @Override
    public void addMethodHandleInfo(ResolvedMethodHandleCallTargetNode methodHandle) {
        assert originalArguments.size() == 0 && originalReturnStamp == null & originalTargetMethod == null : this;
        originalReturnStamp = methodHandle.originalReturnStamp;
        originalTargetMethod = methodHandle.originalTargetMethod;
        originalArguments.addAll(methodHandle.originalArguments);
    }

    /**
     * Build a new copy of the {@link MacroParams} stored in this node.
     */
    public MacroParams copyParams() {
        return new MacroParams(invokeKind, callerMethod, targetMethod, bci, returnStamp, toArgumentArray());
    }
}
