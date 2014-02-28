/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.ReplacementsImpl.FrameStateProcessing;
import com.oracle.graal.word.phases.*;

/**
 * A utility for manually creating a graph. This will be expanded as necessary to support all
 * subsystems that employ manual graph creation (as opposed to {@linkplain GraphBuilderPhase
 * bytecode parsing} based graph creation).
 */
public class GraphKit {

    private final Providers providers;
    private final StructuredGraph graph;
    private FixedWithNextNode lastFixedNode;

    public GraphKit(StructuredGraph graph, Providers providers) {
        this.providers = providers;
        this.graph = graph;
        this.lastFixedNode = graph.start();
    }

    public StructuredGraph getGraph() {
        return graph;
    }

    /**
     * Ensures a floating node is added to or already present in the graph via {@link Graph#unique}.
     * 
     * @return a node similar to {@code node} if one exists, otherwise {@code node}
     */
    public <T extends FloatingNode> T unique(T node) {
        return graph.unique(node);
    }

    /**
     * Appends a fixed node to the graph.
     */
    public <T extends FixedNode> T append(T node) {
        T result = graph.add(node);
        assert lastFixedNode != null;
        assert result.predecessor() == null;
        graph.addAfterFixed(lastFixedNode, result);
        if (result instanceof FixedWithNextNode) {
            lastFixedNode = (FixedWithNextNode) result;
        } else {
            lastFixedNode = null;
        }
        return result;
    }

    /**
     * Creates and appends an {@link InvokeNode} for a call to a given method with a given set of
     * arguments.
     * 
     * @param declaringClass the class declaring the invoked method
     * @param name the name of the invoked method
     * @param args the arguments to the invocation
     */
    public InvokeNode createInvoke(Class<?> declaringClass, String name, ValueNode... args) {
        ResolvedJavaMethod method = null;
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(name)) {
                assert method == null : "found more than one method in " + declaringClass + " named " + name;
                method = providers.getMetaAccess().lookupJavaMethod(m);
            }
        }
        assert method != null : "did not find method in " + declaringClass + " named " + name;
        Signature signature = method.getSignature();
        JavaType returnType = signature.getReturnType(null);
        assert checkArgs(method, args);
        MethodCallTargetNode callTarget = graph.add(new MethodCallTargetNode(InvokeKind.Static, method, args, returnType));
        InvokeNode invoke = append(new InvokeNode(callTarget, FrameState.UNKNOWN_BCI));
        return invoke;
    }

    /**
     * Determines if a given set of arguments is compatible with the signature of a given method.
     * 
     * @return true if {@code args} are compatible with the signature if {@code method}
     * @throws AssertionError if {@code args} are not compatible with the signature if
     *             {@code method}
     */
    public boolean checkArgs(ResolvedJavaMethod method, ValueNode... args) {
        Signature signature = method.getSignature();
        if (signature.getParameterCount(false) != args.length) {
            throw new AssertionError(graph + ": wrong number of arguments to " + method);
        }
        for (int i = 0; i != args.length; i++) {
            Kind expected = signature.getParameterKind(i).getStackKind();
            Kind actual = args[i].stamp().getStackKind();
            if (expected != actual) {
                throw new AssertionError(graph + ": wrong kind of value for argument " + i + " of calls to " + method + " [" + actual + " != " + expected + "]");
            }
        }
        return true;
    }

    /**
     * Rewrite all word types in the graph.
     */
    public void rewriteWordTypes() {
        new WordTypeRewriterPhase(providers.getMetaAccess(), providers.getCodeCache().getTarget().wordKind).apply(graph);
    }

    /**
     * {@linkplain #inline(InvokeNode) Inlines} all invocations currently in the graph.
     */
    public void inlineInvokes() {
        for (InvokeNode invoke : graph.getNodes().filter(InvokeNode.class).snapshot()) {
            inline(invoke);
        }

        // Clean up all code that is now dead after inlining.
        new DeadCodeEliminationPhase().apply(graph);
        assert graph.getNodes().filter(InvokeNode.class).isEmpty();
    }

    /**
     * Inlines a given invocation to a method. The graph of the inlined method is
     * {@linkplain ReplacementsImpl#makeGraph processed} in the same manner as for snippets and
     * method substitutions.
     */
    public void inline(InvokeNode invoke) {
        ResolvedJavaMethod method = ((MethodCallTargetNode) invoke.callTarget()).targetMethod();
        ReplacementsImpl repl = new ReplacementsImpl(providers, new Assumptions(false), providers.getCodeCache().getTarget());
        StructuredGraph calleeGraph = repl.makeGraph(method, null, method, null, FrameStateProcessing.CollapseFrameForSingleSideEffect);
        InliningUtil.inline(invoke, calleeGraph, false);
    }
}
