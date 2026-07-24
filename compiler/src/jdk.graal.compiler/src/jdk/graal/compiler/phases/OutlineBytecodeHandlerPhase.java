/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases;

import java.util.Optional;
import java.util.function.Function;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.BytecodeHandlerCallSite;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeInterpreterAnnotations;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This phase is responsible for identifying and processing bytecode handler invocations
 * within a given graph. It provides a framework for outlining these handlers into separate stubs,
 * which can be called from the enclosing method.
 */
public abstract class OutlineBytecodeHandlerPhase extends BasePhase<HighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunBefore(this, GraphState.StageFlag.LOOP_OVERFLOWS_CHECKED, graphState);
    }

    protected BytecodeHandlerCallSite getBytecodeHandlerCallSite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod) {
        return new BytecodeHandlerCallSite(enclosingMethod, bci, targetMethod, templateModeEnabled());
    }

    protected boolean templateModeEnabled() {
        return false;
    }

    protected Function<ResolvedJavaField, ResolvedJavaField> getFieldMap(@SuppressWarnings("unused") MetaAccessProvider metaAccess) {
        return f -> f;
    }

    protected Function<ResolvedJavaType, ResolvedJavaType> getTypeMap(@SuppressWarnings("unused") MetaAccessProvider metaAccess) {
        return t -> t;
    }

    /**
     * Replaces an invoke node with a call to the outlined handler stub.
     */
    protected abstract FixedNode replaceInvoke(HighTierContext context, BytecodeHandlerCallSite callsite, Invoke invoke, ValueNode[] arguments);

    @Override
    protected final void run(StructuredGraph graph, HighTierContext context) {
        if (graph.isOSR()) {
            return;
        }
        ResolvedJavaMethod enclosingMethod = graph.method();
        if (enclosingMethod == null) {
            return;
        }
        // Outlining only takes place in methods that declare bytecode-handler metadata.
        AnnotationValue handlerConfigAnnotation = BytecodeInterpreterAnnotations.getBytecodeInterpreterHandlerConfig(enclosingMethod);
        if (handlerConfigAnnotation == null) {
            return;
        }
        // Secondary switches are outlined only after they are inlined into the primary switch.
        if (handlerConfigAnnotation.getBoolean("secondarySwitch")) {
            return;
        }

        if (!applicableTo(enclosingMethod)) {
            return;
        }

        for (Node node : graph.getNodes()) {
            if (node instanceof Invoke invoke) {
                ResolvedJavaMethod targetMethod = invoke.callTarget().targetMethod();
                GraalError.guarantee(targetMethod != null, "Missing target method for handler invoke %s in %s", invoke, graph);
                if (BytecodeInterpreterAnnotations.getBytecodeInterpreterHandler(targetMethod) == null) {
                    continue;
                }
                BytecodeHandlerConfig handlerConfig = BytecodeHandlerConfig.getHandlerConfig(enclosingMethod, targetMethod, templateModeEnabled());

                // targetMethod is annotated with @BytecodeInterpreterHandler, replace the
                // invoke with stub call. Use the invoke's frame-state owner so split inlinees
                // keep their original caller method after host inlining into another
                // interpreter method.
                FrameState invokeState = invoke.stateAfter();
                if (invokeState == null) {
                    invokeState = invoke.stateDuring();
                }
                GraalError.guarantee(invokeState != null, "Missing frame state for handler invoke %s in %s", invoke, graph);
                ResolvedJavaMethod invokeEnclosingMethod = invokeState.getMethod();
                GraalError.guarantee(invokeEnclosingMethod != null, "Missing context method for handler invoke %s in %s", invoke, graph);
                guaranteeConsistentHandlerConfig(handlerConfig, enclosingMethod, invokeEnclosingMethod, targetMethod, templateModeEnabled());
                BytecodeHandlerCallSite callsite = getBytecodeHandlerCallSite(invokeEnclosingMethod, invoke.bci(), targetMethod);
                ValueNode[] oldArguments = invoke.callTarget().arguments().toArray(ValueNode.EMPTY_ARRAY);
                ValueNode[] newArguments = callsite.createCallerArguments(oldArguments, invoke.asFixedNode(), getFieldMap(context.getMetaAccess()));
                FixedNode next = invoke instanceof InvokeNode invokeNode ? invokeNode.next() : ((InvokeWithExceptionNode) invoke).next().next();
                FixedNode newInvoke = replaceInvoke(context, callsite, invoke, newArguments);
                callsite.updateCallerReturns(newInvoke, oldArguments, next, getFieldMap(context.getMetaAccess()));
            }
        }
        afterProcessGraph(context, graph);
    }

    /**
     * Verifies that a handler invoke whose frame state comes from an inlined interpreter method uses
     * the same {@code @BytecodeInterpreterHandlerConfig} as the current graph method. Outlining
     * derives the stub ABI from {@code enclosingMethod}, but uses the invoke frame-state owner as the
     * actual callsite method. A mismatch would outline the invoke with one argument layout while
     * updating caller state as if it belonged to another layout.
     */
    private static void guaranteeConsistentHandlerConfig(BytecodeHandlerConfig enclosingConfig, ResolvedJavaMethod enclosingMethod, ResolvedJavaMethod invokeEnclosingMethod,
                    ResolvedJavaMethod targetMethod, boolean templateModeEnabled) {
        if (enclosingMethod.equals(invokeEnclosingMethod)) {
            return;
        }
        BytecodeHandlerConfig invokeConfig = BytecodeHandlerConfig.getHandlerConfig(invokeEnclosingMethod, targetMethod, templateModeEnabled);
        GraalError.guarantee(enclosingConfig.equals(invokeConfig), "Inconsistent BytecodeInterpreterHandlerConfig for handler %s between switch methods %s and %s",
                        targetMethod.format("%H.%n(%p)"), enclosingMethod.format("%H.%n(%p)"), invokeEnclosingMethod.format("%H.%n(%p)"));
    }

    @SuppressWarnings("unused")
    protected boolean applicableTo(ResolvedJavaMethod enclosingMethod) {
        return true;
    }

    @SuppressWarnings("unused")
    protected void afterProcessGraph(HighTierContext context, StructuredGraph graph) {
    }
}
