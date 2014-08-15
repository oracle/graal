/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.*;

/**
 * Macro nodes can be used to temporarily replace an invoke (usually by using the
 * {@link MacroSubstitution} annotation). They can, for example, be used to implement constant
 * folding for known JDK functions like {@link Class#isInterface()}.<br/>
 * <br/>
 * During lowering, multiple sources are queried in order to look for a replacement:
 * <ul>
 * <li>If {@link #getLoweredSnippetGraph(LoweringTool)} returns a non-null result, this graph is
 * used as a replacement.</li>
 * <li>If a {@link MethodSubstitution} for the target method is found, this substitution is used as
 * a replacement.</li>
 * <li>Otherwise, the macro node is replaced with an {@link InvokeNode}. Note that this is only
 * possible if the macro node is a {@link MacroStateSplitNode}.</li>
 * </ul>
 */
@NodeInfo
public class MacroNode extends FixedWithNextNode implements Lowerable {

    @Input protected final NodeInputList<ValueNode> arguments;

    private final int bci;
    private final ResolvedJavaMethod targetMethod;
    private final JavaType returnType;
    private final InvokeKind invokeKind;

    protected MacroNode(Invoke invoke) {
        super(StampFactory.forKind(((MethodCallTargetNode) invoke.callTarget()).targetMethod().getSignature().getReturnKind()));
        MethodCallTargetNode methodCallTarget = (MethodCallTargetNode) invoke.callTarget();
        this.arguments = new NodeInputList<>(this, methodCallTarget.arguments());
        this.bci = invoke.bci();
        this.targetMethod = methodCallTarget.targetMethod();
        this.returnType = methodCallTarget.returnType();
        this.invokeKind = methodCallTarget.invokeKind();
    }

    public int getBci() {
        return bci;
    }

    public ResolvedJavaMethod getTargetMethod() {
        return targetMethod;
    }

    public JavaType getReturnType() {
        return returnType;
    }

    protected FrameState stateAfter() {
        return null;
    }

    /**
     * Gets a snippet to be used for lowering this macro node. The returned graph (if non-null) must
     * have been {@linkplain #lowerReplacement(StructuredGraph, LoweringTool) lowered}.
     */
    @SuppressWarnings("unused")
    protected StructuredGraph getLoweredSnippetGraph(LoweringTool tool) {
        return null;
    }

    /**
     * Gets a normal method substitution to be used for lowering this macro node. This is only
     * called if {@link #getLoweredSnippetGraph(LoweringTool)} returns null. The returned graph (if
     * non-null) must have been {@linkplain #lowerReplacement(StructuredGraph, LoweringTool)
     * lowered}.
     */
    protected StructuredGraph getLoweredSubstitutionGraph(LoweringTool tool) {
        StructuredGraph methodSubstitution = tool.getReplacements().getMethodSubstitution(getTargetMethod());
        if (methodSubstitution != null) {
            methodSubstitution = methodSubstitution.copy();
            if (stateAfter() == null || stateAfter().bci == BytecodeFrame.AFTER_BCI) {
                /*
                 * handles the case of a MacroNode inside a snippet used for another MacroNode
                 * lowering
                 */
                new CollapseFrameForSingleSideEffectPhase().apply(methodSubstitution);
            }
            return lowerReplacement(methodSubstitution, tool);
        }
        return null;
    }

    /**
     * Applies {@linkplain LoweringPhase lowering} to a replacement graph.
     *
     * @param replacementGraph a replacement (i.e., snippet or method substitution) graph
     */
    protected StructuredGraph lowerReplacement(final StructuredGraph replacementGraph, LoweringTool tool) {
        final PhaseContext c = new PhaseContext(tool.getMetaAccess(), tool.getConstantReflection(), tool.getLowerer(), tool.getReplacements(), tool.assumptions());
        if (!graph().hasValueProxies()) {
            new RemoveValueProxyPhase().apply(replacementGraph);
        }
        GuardsStage guardsStage = graph().getGuardsStage();
        if (guardsStage.ordinal() >= GuardsStage.FIXED_DEOPTS.ordinal()) {
            new GuardLoweringPhase().apply(replacementGraph, null);
            if (guardsStage.ordinal() >= GuardsStage.AFTER_FSA.ordinal()) {
                new FrameStateAssignmentPhase().apply(replacementGraph);
            }
        }
        try (Scope s = Debug.scope("LoweringSnippetTemplate", replacementGraph)) {
            new LoweringPhase(new CanonicalizerPhase(true), tool.getLoweringStage()).apply(replacementGraph, c);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
        return replacementGraph;
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph replacementGraph = getLoweredSnippetGraph(tool);
        if (replacementGraph == null) {
            replacementGraph = getLoweredSubstitutionGraph(tool);
        }

        InvokeNode invoke = replaceWithInvoke();
        assert invoke.verify();

        if (replacementGraph != null) {
            // Pull out the receiver null check so that a replaced
            // receiver can be lowered if necessary
            if (!targetMethod.isStatic()) {
                ValueNode nonNullReceiver = InliningUtil.nonNullReceiver(invoke);
                if (nonNullReceiver instanceof Lowerable) {
                    ((Lowerable) nonNullReceiver).lower(tool);
                }
            }
            InliningUtil.inline(invoke, replacementGraph, false, null);
            Debug.dump(graph(), "After inlining replacement %s", replacementGraph);
        } else {
            if (stateAfter() == null) {
                throw new GraalInternalError("cannot lower to invoke without state: %s", this);
            }
            invoke.lower(tool);
        }
    }

    protected InvokeNode replaceWithInvoke() {
        InvokeNode invoke = createInvoke();
        graph().replaceFixedWithFixed(this, invoke);
        return invoke;
    }

    protected InvokeNode createInvoke() {
        MethodCallTargetNode callTarget = graph().add(new MethodCallTargetNode(invokeKind, targetMethod, arguments.toArray(new ValueNode[arguments.size()]), returnType));
        InvokeNode invoke = graph().add(new InvokeNode(callTarget, bci));
        if (stateAfter() != null) {
            invoke.setStateAfter(stateAfter().duplicate());
            if (getKind() != Kind.Void) {
                invoke.stateAfter().replaceFirstInput(this, invoke);
            }
        }
        return invoke;
    }
}
