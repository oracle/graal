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
package jdk.graal.compiler.truffle.host;

import java.util.Optional;
import java.util.function.Function;

import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.TruffleBytecodeHandlerTypes;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This phase is responsible for identifying and processing Truffle bytecode handler invocations
 * within a given graph. It provides a framework for outlining these handlers into separate stubs,
 * which can be called from the enclosing method.
 */
public abstract class OutlineBytecodeHandlerPhase extends BasePhase<HighTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.unlessRunBefore(this, GraphState.StageFlag.LOOP_OVERFLOWS_CHECKED, graphState);
    }

    protected TruffleBytecodeHandlerTypes getTruffleBytecodeHandlerTypes(TruffleKnownHostTypes truffleKnownHostTypes) {
        return new TruffleBytecodeHandlerTypes(truffleKnownHostTypes.BytecodeInterpreterSwitch,
                        truffleKnownHostTypes.BytecodeInterpreterHandlerConfig,
                        truffleKnownHostTypes.BytecodeInterpreterHandler,
                        truffleKnownHostTypes.BytecodeInterpreterFetchOpcode);
    }

    protected TruffleBytecodeHandlerCallsite getTruffleBytecodeHandlerCallsite(ResolvedJavaMethod enclosingMethod, int bci, ResolvedJavaMethod targetMethod, TruffleBytecodeHandlerTypes truffleTypes) {
        return new TruffleBytecodeHandlerCallsite(enclosingMethod, bci, targetMethod, truffleTypes);
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
    protected abstract FixedNode replaceInvoke(HighTierContext context, TruffleBytecodeHandlerCallsite callsite, Invoke invoke, ValueNode[] arguments);

    @Override
    protected final void run(StructuredGraph graph, HighTierContext context) {
        if (graph.isOSR()) {
            return;
        }
        ResolvedJavaMethod enclosingMethod = graph.method();
        if (enclosingMethod == null) {
            return;
        }
        TruffleHostEnvironment env = TruffleHostEnvironment.get(enclosingMethod);
        if (env == null) {
            return;
        }

        TruffleBytecodeHandlerTypes truffleTypes = getTruffleBytecodeHandlerTypes(env.types());
        // Outlining only takes place at @BytecodeInterpreterSwitch-annotated method
        if (!AnnotationValueSupport.isAnnotationPresent(truffleTypes.typeBytecodeInterpreterSwitch(), enclosingMethod)) {
            return;
        }

        if (!applicableTo(enclosingMethod)) {
            return;
        }

        for (Node node : graph.getNodes()) {
            if (node instanceof Invoke invoke) {
                ResolvedJavaMethod targetMethod = invoke.callTarget().targetMethod();
                if (!AnnotationValueSupport.isAnnotationPresent(truffleTypes.typeBytecodeInterpreterHandler(), targetMethod)) {
                    continue;
                }

                // targetMethod is annotated with @BytecodeInterpreterHandler, replace the invoke
                // with stub call
                TruffleBytecodeHandlerCallsite callsite = getTruffleBytecodeHandlerCallsite(enclosingMethod, invoke.bci(), targetMethod, truffleTypes);
                ValueNode[] oldArguments = invoke.callTarget().arguments().toArray(ValueNode.EMPTY_ARRAY);
                ValueNode[] newArguments = callsite.createCallerArguments(oldArguments, invoke.asFixedNode(), getFieldMap(context.getMetaAccess()));
                FixedNode next = invoke instanceof InvokeNode invokeNode ? invokeNode.next() : ((InvokeWithExceptionNode) invoke).next().next();
                FixedNode newInvoke = replaceInvoke(context, callsite, invoke, newArguments);
                callsite.updateCallerReturns(newInvoke, oldArguments, next, getFieldMap(context.getMetaAccess()));
            }
        }
    }

    protected boolean applicableTo(@SuppressWarnings("unused") ResolvedJavaMethod enclosingMethod) {
        return true;
    }
}
