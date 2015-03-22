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
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.java.AbstractBytecodeParser.IntrinsicContext;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.word.*;

/**
 * A utility for manually creating a graph. This will be expanded as necessary to support all
 * subsystems that employ manual graph creation (as opposed to {@linkplain GraphBuilderPhase
 * bytecode parsing} based graph creation).
 */
public class GraphKit {

    protected final Providers providers;
    protected final StructuredGraph graph;
    protected final WordTypes wordTypes;
    protected final GraphBuilderConfiguration.Plugins graphBuilderPlugins;
    protected FixedWithNextNode lastFixedNode;

    private final List<Structure> structures;

    abstract static class Structure {
    }

    public GraphKit(StructuredGraph graph, Providers providers, WordTypes wordTypes, GraphBuilderConfiguration.Plugins graphBuilderPlugins) {
        this.providers = providers;
        this.graph = graph;
        this.wordTypes = wordTypes;
        this.graphBuilderPlugins = graphBuilderPlugins;
        this.lastFixedNode = graph.start();

        structures = new ArrayList<>();
        /* Add a dummy element, so that the access of the last element never leads to an exception. */
        structures.add(new Structure() {
        });
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
        return graph.unique(changeToWord(node));
    }

    public <T extends ValueNode> T changeToWord(T node) {
        if (wordTypes.isWord(node)) {
            node.setStamp(wordTypes.getWordStamp(StampTool.typeOrNull(node)));
        }
        return node;
    }

    /**
     * Appends a fixed node to the graph.
     */
    public <T extends FixedNode> T append(T node) {
        T result = graph.add(changeToWord(node));
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

    public InvokeNode createInvoke(Class<?> declaringClass, String name, ValueNode... args) {
        return createInvoke(declaringClass, name, InvokeKind.Static, null, BytecodeFrame.UNKNOWN_BCI, args);
    }

    /**
     * Creates and appends an {@link InvokeNode} for a call to a given method with a given set of
     * arguments. The method is looked up via reflection based on the declaring class and name.
     *
     * @param declaringClass the class declaring the invoked method
     * @param name the name of the invoked method
     * @param args the arguments to the invocation
     */
    public InvokeNode createInvoke(Class<?> declaringClass, String name, InvokeKind invokeKind, HIRFrameStateBuilder frameStateBuilder, int bci, ValueNode... args) {
        boolean isStatic = invokeKind == InvokeKind.Static;
        ResolvedJavaMethod method = null;
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) == isStatic && m.getName().equals(name)) {
                assert method == null : "found more than one method in " + declaringClass + " named " + name;
                method = providers.getMetaAccess().lookupJavaMethod(m);
            }
        }
        assert method != null : "did not find method in " + declaringClass + " named " + name;
        return createInvoke(method, invokeKind, frameStateBuilder, bci, args);
    }

    /**
     * Creates and appends an {@link InvokeNode} for a call to a given method with a given set of
     * arguments.
     */
    public InvokeNode createInvoke(ResolvedJavaMethod method, InvokeKind invokeKind, HIRFrameStateBuilder frameStateBuilder, int bci, ValueNode... args) {
        assert method.isStatic() == (invokeKind == InvokeKind.Static);
        Signature signature = method.getSignature();
        JavaType returnType = signature.getReturnType(null);
        assert checkArgs(method, args);
        MethodCallTargetNode callTarget = graph.add(createMethodCallTarget(invokeKind, method, args, returnType, bci));
        InvokeNode invoke = append(new InvokeNode(callTarget, bci));

        if (frameStateBuilder != null) {
            if (invoke.getKind() != Kind.Void) {
                frameStateBuilder.push(returnType.getKind(), invoke);
            }
            invoke.setStateAfter(frameStateBuilder.create(bci));
            if (invoke.getKind() != Kind.Void) {
                frameStateBuilder.pop(returnType.getKind());
            }
        }
        return invoke;
    }

    protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, JavaType returnType, @SuppressWarnings("unused") int bci) {
        return new MethodCallTargetNode(invokeKind, targetMethod, args, returnType);
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
        boolean isStatic = method.isStatic();
        if (signature.getParameterCount(!isStatic) != args.length) {
            throw new AssertionError(graph + ": wrong number of arguments to " + method);
        }
        int argIndex = 0;
        if (!isStatic) {
            Kind expected = wordTypes.asKind(method.getDeclaringClass());
            Kind actual = args[argIndex++].stamp().getStackKind();
            assert expected == actual : graph + ": wrong kind of value for receiver argument of call to " + method + " [" + actual + " != " + expected + "]";
        }
        for (int i = 0; i != signature.getParameterCount(false); i++) {
            Kind expected = wordTypes.asKind(signature.getParameterType(i, method.getDeclaringClass())).getStackKind();
            Kind actual = args[argIndex++].stamp().getStackKind();
            if (expected != actual) {
                throw new AssertionError(graph + ": wrong kind of value for argument " + i + " of call to " + method + " [" + actual + " != " + expected + "]");
            }
        }
        return true;
    }

    /**
     * Recursively {@linkplain #inline inlines} all invocations currently in the graph.
     */
    public void inlineInvokes() {
        while (!graph.getNodes().filter(InvokeNode.class).isEmpty()) {
            for (InvokeNode invoke : graph.getNodes().filter(InvokeNode.class).snapshot()) {
                inline(invoke);
            }
        }

        // Clean up all code that is now dead after inlining.
        new DeadCodeEliminationPhase().apply(graph);
    }

    /**
     * Inlines a given invocation to a method. The graph of the inlined method is processed in the
     * same manner as for snippets and method substitutions.
     */
    public void inline(InvokeNode invoke) {
        ResolvedJavaMethod method = ((MethodCallTargetNode) invoke.callTarget()).targetMethod();

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        Plugins plugins = new Plugins(graphBuilderPlugins);
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);

        StructuredGraph calleeGraph = new StructuredGraph(method, AllowAssumptions.NO);
        IntrinsicContext initialReplacementContext = new IntrinsicContext(method, method, null, IntrinsicContext.POST_PARSE_INLINE_BCI);
        new GraphBuilderPhase.Instance(metaAccess, providers.getStampProvider(), providers.getConstantReflection(), config, OptimisticOptimizations.NONE, initialReplacementContext).apply(calleeGraph);

        // Remove all frame states from inlinee
        for (Node node : calleeGraph.getNodes()) {
            if (node instanceof StateSplit) {
                ((StateSplit) node).setStateAfter(null);
            }
        }
        new DeadCodeEliminationPhase(Optionality.Required).apply(calleeGraph);

        InliningUtil.inline(invoke, calleeGraph, false, null);
    }

    protected void pushStructure(Structure structure) {
        structures.add(structure);
    }

    protected <T extends Structure> T getTopStructure(Class<T> expectedClass) {
        return expectedClass.cast(structures.get(structures.size() - 1));
    }

    protected void popStructure() {
        structures.remove(structures.size() - 1);
    }

    protected enum IfState {
        CONDITION,
        THEN_PART,
        ELSE_PART,
        FINISHED
    }

    static class IfStructure extends Structure {
        protected IfState state;
        protected FixedNode thenPart;
        protected FixedNode elsePart;
    }

    /**
     * Starts an if-block. This call can be followed by a call to {@link #thenPart} to start
     * emitting the code executed when the condition hold; and a call to {@link #elsePart} to start
     * emititng the code when the condition does not hold. It must be followed by a call to
     * {@link #endIf} to close the if-block.
     *
     * @param condition The condition for the if-block
     * @param trueProbability The estimated probability the the condition is true
     */
    public void startIf(LogicNode condition, double trueProbability) {
        AbstractBeginNode thenSuccessor = graph.add(new BeginNode());
        AbstractBeginNode elseSuccessor = graph.add(new BeginNode());
        append(new IfNode(condition, thenSuccessor, elseSuccessor, trueProbability));
        lastFixedNode = null;

        IfStructure s = new IfStructure();
        s.state = IfState.CONDITION;
        s.thenPart = thenSuccessor;
        s.elsePart = elseSuccessor;
        pushStructure(s);
    }

    private IfStructure saveLastNode() {
        IfStructure s = getTopStructure(IfStructure.class);
        switch (s.state) {
            case CONDITION:
                assert lastFixedNode == null;
                break;
            case THEN_PART:
                s.thenPart = lastFixedNode;
                break;
            case ELSE_PART:
                s.elsePart = lastFixedNode;
                break;
            case FINISHED:
                assert false;
                break;
        }
        lastFixedNode = null;
        return s;
    }

    public void thenPart() {
        IfStructure s = saveLastNode();
        lastFixedNode = (FixedWithNextNode) s.thenPart;
        s.state = IfState.THEN_PART;
    }

    public void elsePart() {
        IfStructure s = saveLastNode();
        lastFixedNode = (FixedWithNextNode) s.elsePart;
        s.state = IfState.ELSE_PART;
    }

    public void endIf() {
        IfStructure s = saveLastNode();

        FixedWithNextNode thenPart = s.thenPart instanceof FixedWithNextNode ? (FixedWithNextNode) s.thenPart : null;
        FixedWithNextNode elsePart = s.elsePart instanceof FixedWithNextNode ? (FixedWithNextNode) s.elsePart : null;

        if (thenPart != null && elsePart != null) {
            /* Both parts are alive, we need a real merge. */
            EndNode thenEnd = graph.add(new EndNode());
            graph.addAfterFixed(thenPart, thenEnd);
            EndNode elseEnd = graph.add(new EndNode());
            graph.addAfterFixed(elsePart, elseEnd);

            AbstractMergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(thenEnd);
            merge.addForwardEnd(elseEnd);

            lastFixedNode = merge;

        } else if (thenPart != null) {
            /* elsePart ended with a control sink, so we can continue with thenPart. */
            lastFixedNode = thenPart;

        } else if (elsePart != null) {
            /* thenPart ended with a control sink, so we can continue with elsePart. */
            lastFixedNode = elsePart;

        } else {
            /* Both parts ended with a control sink, so no nodes can be added after the if. */
            assert lastFixedNode == null;
        }
        s.state = IfState.FINISHED;
        popStructure();
    }
}
