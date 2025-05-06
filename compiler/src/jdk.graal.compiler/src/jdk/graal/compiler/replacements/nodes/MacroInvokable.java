/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.vm.ci.code.BytecodeFrame.isPlaceholderBci;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FixedNodeInterface;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invokable;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;
import jdk.graal.compiler.phases.common.LoweringPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Macro invokable nodes can be used to temporarily replace an invoke. They can, for example, be
 * used to implement constant folding for known JDK functions like {@link Class#isInterface()}.<br/>
 * <br/>
 * During lowering subclasses may lower the node as appropriate. Otherwise, the macro node is
 * replaced with an {@link Invoke}.
 */
public interface MacroInvokable extends Invokable, Lowerable, StateSplit, SingleMemoryKill, FixedNodeInterface {

    CallTargetNode.InvokeKind getInvokeKind();

    StampPair getReturnStamp();

    /**
     * Access to the original arguments for a MethodHandle invoke call site. See
     * {@link ResolvedMethodHandleCallTargetNode}.
     */
    NodeInputList<ValueNode> getOriginalArguments();

    /**
     * Access to the original target methods for a MethodHandle invoke call site. See
     * {@link ResolvedMethodHandleCallTargetNode}.
     */
    ResolvedJavaMethod getOriginalTargetMethod();

    /**
     * Access to the original return stamp for a MethodHandle invoke call site. See
     * {@link ResolvedMethodHandleCallTargetNode}.
     */
    StampPair getOriginalReturnStamp();

    /**
     * Gets the arguments for this macro node.
     */
    NodeInputList<ValueNode> getArguments();

    /**
     * Gets {@linkplain #getArguments() the arguments} for this macro node as an array.
     */
    default ValueNode[] toArgumentArray() {
        return getArguments().toArray(ValueNode.EMPTY_ARRAY);
    }

    /**
     * @see #getArguments()
     */
    default ValueNode getArgument(int index) {
        return getArguments().get(index);
    }

    /**
     * @see #getArguments()
     */
    default int getArgumentCount() {
        return getArguments().size();
    }

    static boolean assertArgumentCount(MacroInvokable macro) {
        ResolvedJavaMethod method = macro.getTargetMethod();
        assert method.getSignature().getParameterCount(!method.isStatic()) == macro.getArgumentCount() : "Method and macro must match:" +
                        Assertions.errorMessageContext("method", method, "macro", macro);
        return true;
    }

    @Override
    default void setBci(int bci) {
        // nothing to do here, macro nodes get bci during construction
        GraalError.shouldNotReachHere("macro nodes get bci during construction"); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Replaces this node with an invoke of the {@linkplain #getTargetMethod() target method}.
     *
     * @return the newly create {@link Invoke}
     */
    Invoke replaceWithInvoke();

    /**
     * Applies {@linkplain LoweringPhase lowering} to a replacement graph.
     *
     * @param replacementGraph a replacement (i.e., snippet or method substitution) graph
     */
    @SuppressWarnings("try")
    static StructuredGraph lowerReplacement(StructuredGraph graph, StructuredGraph replacementGraph, LoweringTool tool) {
        if (graph.isAfterStage(GraphState.StageFlag.VALUE_PROXY_REMOVAL)) {
            new RemoveValueProxyPhase(CanonicalizerPhase.create()).apply(replacementGraph, null);
        }
        GraphState.GuardsStage guardsStage = graph.getGuardsStage();
        if (!guardsStage.allowsFloatingGuards()) {
            new GuardLoweringPhase().apply(replacementGraph, null);
            if (guardsStage.areFrameStatesAtDeopts()) {
                new FrameStateAssignmentPhase().apply(replacementGraph);
            }
        }
        DebugContext debug = replacementGraph.getDebug();
        try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate", replacementGraph)) {
            switch ((LoweringTool.StandardLoweringStage) tool.getLoweringStage()) {
                case HIGH_TIER:
                    new HighTierLoweringPhase(CanonicalizerPhase.create()).apply(replacementGraph, tool);
                    break;
                case MID_TIER:
                    new MidTierLoweringPhase(CanonicalizerPhase.create()).apply(replacementGraph, tool);
                    break;
                case LOW_TIER:
                    new LowTierLoweringPhase(CanonicalizerPhase.create()).apply(replacementGraph, tool);
                    break;
                default:
                    GraalError.shouldNotReachHere("Unexpected lowering stage."); // ExcludeFromJacocoGeneratedReport
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return replacementGraph;
    }

    @Override
    default void lower(LoweringTool tool) {
        Invoke invoke = replaceWithInvoke();
        assert invoke.asNode().verify();

        if (isPlaceholderBci(invoke.bci())) {
            throw new GraalError("%s: cannot lower to invoke with placeholder BCI: %s", asNode().graph(), this);
        }

        if (invoke.stateAfter() == null) {
            throw new GraalError("%s: cannot lower to invoke without state: %s", asNode().graph(), this);
        }
        invoke.lower(tool);
    }

    /**
     * Create the call target when converting this node back into a normal {@link Invoke}. For a
     * method handle invoke site this will be a {@link ResolvedMethodHandleCallTargetNode}.
     */
    default MethodCallTargetNode createCallTarget() {
        ValueNode[] arguments = getArguments().toArray(new ValueNode[getArguments().size()]);
        if (getOriginalTargetMethod() != null) {
            ValueNode[] originalArguments = getOriginalArguments().toArray(new ValueNode[getOriginalArguments().size()]);
            return asNode().graph().add(ResolvedMethodHandleCallTargetNode.create(getInvokeKind(), getTargetMethod(), arguments, getReturnStamp(), getOriginalTargetMethod(), originalArguments,
                            getOriginalReturnStamp()));

        } else {
            return asNode().graph().add(new MethodCallTargetNode(getInvokeKind(), getTargetMethod(), arguments, getReturnStamp(), null));
        }
    }

    /**
     * Captures the method handle information so that it can be properly lowered back to an
     * {@link Invoke} later.
     */
    void addMethodHandleInfo(ResolvedMethodHandleCallTargetNode methodHandle);

    /**
     * Build a new copy of the {@link MacroNode.MacroParams} stored in this node.
     */
    default MacroNode.MacroParams copyParams() {
        return new MacroNode.MacroParams(getInvokeKind(), getContextMethod(), getTargetMethod(), bci(), getReturnStamp(), toArgumentArray());
    }

    /**
     * Builds a new copy of this node's macro parameters, but with the return stamp replaced by the
     * trusted {@code newStamp}.
     */
    default MacroNode.MacroParams copyParamsWithImprovedStamp(ObjectStamp newStamp) {
        GraalError.guarantee(newStamp.join(getReturnStamp().getTrustedStamp()).equals(newStamp), "stamp should improve from %s to %s", getReturnStamp(), newStamp);
        StampPair improvedReturnStamp = StampPair.createSingle(newStamp);
        return new MacroNode.MacroParams(getInvokeKind(), getContextMethod(), getTargetMethod(), bci(), improvedReturnStamp, toArgumentArray());
    }
}
