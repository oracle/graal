/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.Invokable;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.HighTierLoweringPhase;
import org.graalvm.compiler.phases.common.LowTierLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.MidTierLoweringPhase;
import org.graalvm.compiler.phases.common.RemoveValueProxyPhase;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Macro invokable nodes can be used to temporarily replace an invoke. They can, for example, be
 * used to implement constant folding for known JDK functions like {@link Class#isInterface()}.<br/>
 * <br/>
 * During lowering subclasses may lower the node as appropriate. Otherwise, the macro node is
 * replaced with an {@link Invoke}.
 */
public interface MacroInvokable extends Invokable, Lowerable, StateSplit, SingleMemoryKill {

    CallTargetNode.InvokeKind getInvokeKind();

    /**
     * Gets the arguments for this macro node.
     */
    NodeInputList<ValueNode> getArguments();

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
        assert method.getSignature().getParameterCount(!method.isStatic()) == macro.getArgumentCount();
        return true;
    }

    @Override
    default void setBci(int bci) {
        // nothing to do here, macro nodes get bci during construction
        GraalError.shouldNotReachHere("macro nodes get bci during construction");
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
                    GraalError.shouldNotReachHere("Unexpected lowering stage.");
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
}
