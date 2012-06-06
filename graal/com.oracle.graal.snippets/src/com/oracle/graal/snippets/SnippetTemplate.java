/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.snippets;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import com.oracle.graal.compiler.loop.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.snippets.Snippet.Arguments;
import com.oracle.graal.snippets.Snippet.Constant;
import com.oracle.graal.snippets.Snippet.Multiple;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.nodes.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * A snippet template is a graph created by parsing a snippet method and then
 * specialized by binding constants to the snippet's {@link Constant} parameters.
 *
 * Snippet templates can be managed in a {@link Cache}.
 */
public class SnippetTemplate {

    /**
     * A snippet template key encapsulates the method from which a snippet was built
     * and the arguments used to specialized the snippet.
     *
     * @see Cache
     */
    public static class Key implements Iterable<Map.Entry<String, Object>> {
        public final RiResolvedMethod method;
        private final HashMap<String, Object> map = new HashMap<>();
        private int hash;

        public Key(RiResolvedMethod method) {
            this.method = method;
            this.hash = method.hashCode();
        }

        public Key add(String name, Object value) {
            assert value != null;
            assert !map.containsKey(name);
            map.put(name, value);
            hash = hash ^ name.hashCode() * (value.hashCode() + 1);
            return this;
        }

        public int length() {
            return map.size();
        }

        public Object get(String name) {
            return map.get(name);
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return map.entrySet().iterator();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Key) {
                Key other = (Key) obj;
                return other.method == method && other.map.equals(map);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return CiUtil.format("%h.%n", method) + map.toString();
        }
    }

    /**
     * A collection of snippet templates accessed by a {@link Key} instance.
     */
    public static class Cache {

        private final ConcurrentHashMap<SnippetTemplate.Key, SnippetTemplate> templates = new ConcurrentHashMap<>();
        private final RiRuntime runtime;


        public Cache(RiRuntime runtime) {
            this.runtime = runtime;
        }

        /**
         * Gets a template for a given key, creating it first if necessary.
         */
        public SnippetTemplate get(final SnippetTemplate.Key key) {
            SnippetTemplate template = templates.get(key);
            if (template == null) {
                template = Debug.scope("SnippetSpecialization", key.method, new Callable<SnippetTemplate>() {
                    @Override
                    public SnippetTemplate call() throws Exception {
                        return new SnippetTemplate(runtime, key);
                    }
                });
                //System.out.println(key + " -> " + template);
                templates.put(key, template);
            }
            return template;
        }

    }

    /**
     * Creates a snippet template.
     */
    public SnippetTemplate(RiRuntime runtime, SnippetTemplate.Key key) {
        RiResolvedMethod method = key.method;
        assert Modifier.isStatic(method.accessFlags()) : "snippet method must be static: " + method;
        RiSignature signature = method.signature();

        // Copy snippet graph, replacing @Constant parameters with given arguments
        StructuredGraph snippetGraph = (StructuredGraph) method.compilerStorage().get(Graph.class);
        StructuredGraph snippetCopy = new StructuredGraph(snippetGraph.name, snippetGraph.method());
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        replacements.put(snippetGraph.start(), snippetCopy.start());

        int parameterCount = signature.argumentCount(false);
        Parameter[] parameterAnnotations = new Parameter[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            Constant c = CiUtil.getParameterAnnotation(Constant.class, i, method);
            if (c != null) {
                String name = c.value();
                Object arg = key.get(name);
                assert arg != null : method + ": requires a constant named " + name;
                CiKind kind = signature.argumentKindAt(i, false);
                assert checkConstantArgument(method, signature, i, name, arg, kind);
                replacements.put(snippetGraph.getLocal(i), ConstantNode.forCiConstant(CiConstant.forBoxed(kind, arg), runtime, snippetCopy));
            } else {
                Parameter p = CiUtil.getParameterAnnotation(Parameter.class, i, method);
                assert p != null : method + ": parameter " + i + " must be annotated with either @Constant or @Parameter";
                String name = p.value();
                if (p.multiple()) {
                    Object multiple = key.get(name);
                    assert multiple != null : method + ": requires a Multiple named " + name;
                    assert checkMultipleArgument(method, signature, i, name, multiple);
                    Object array = ((Multiple) multiple).array;
                    replacements.put(snippetGraph.getLocal(i), ConstantNode.forObject(array, runtime, snippetCopy));
                }
                parameterAnnotations[i] = p;
            }
        }
        snippetCopy.addDuplicates(snippetGraph.getNodes(), replacements);

        Debug.dump(snippetCopy, "Before specialization");
        if (!replacements.isEmpty()) {
            new CanonicalizerPhase(null, runtime, null, 0, null).apply(snippetCopy);
        }

        // Gather the template parameters
        parameters = new HashMap<>();
        for (int i = 0; i < parameterCount; i++) {
            Parameter p = parameterAnnotations[i];
            if (p != null) {
                ValueNode parameter;
                if (p.multiple()) {
                    parameter = null;
                    assert snippetCopy.getLocal(i) == null;
                    ConstantNode array = (ConstantNode) replacements.get(snippetGraph.getLocal(i));
                    for (LoadIndexedNode loadIndexed : snippetCopy.getNodes(LoadIndexedNode.class)) {
                        if (loadIndexed.array() == array) {
                            Debug.dump(snippetCopy, "Before replacing %s", loadIndexed);
                            LoadMultipleParameterNode lmp = new LoadMultipleParameterNode(array, i, loadIndexed.index(), loadIndexed.stamp());
                            StructuredGraph g = (StructuredGraph) loadIndexed.graph();
                            g.add(lmp);
                            g.replaceFixedWithFixed(loadIndexed, lmp);
                            parameter = lmp;
                            Debug.dump(snippetCopy, "After replacing %s", loadIndexed);
                            break;
                        }
                    }
                } else {
                    parameter = snippetCopy.getLocal(i);
                }
                assert parameter != null;
                parameters.put(p.value(), parameter);
            }
        }

        // Do any required loop explosion
        boolean exploded = false;
        do {
            exploded = false;
            for (Node node : snippetCopy.getNodes()) {
                if (node instanceof ExplodeLoopNode) {
                    final ExplodeLoopNode explodeLoop = (ExplodeLoopNode) node;
                    LoopBeginNode loopBegin = explodeLoop.findLoopBegin();
                    if (loopBegin != null) {
                        ControlFlowGraph cfg = ControlFlowGraph.compute(snippetCopy, true, true, false, false);
                        for (Loop loop : cfg.getLoops()) {
                            if (loop.loopBegin() == loopBegin) {
                                SuperBlock wholeLoop = LoopTransformUtil.wholeLoop(loop);
                                Debug.dump(snippetCopy, "Before exploding loop %s", loopBegin);
                                int peel = 0;
                                while (!loopBegin.isDeleted()) {
                                    int mark = snippetCopy.getMark();
                                    LoopTransformUtil.peel(loop, wholeLoop);
                                    Debug.dump(snippetCopy, "After peel %d", peel);
                                    new CanonicalizerPhase(null, runtime, null, mark, null).apply(snippetCopy);
                                    peel++;
                                }
                                Debug.dump(snippetCopy, "After exploding loop %s", loopBegin);
                                exploded = true;
                                break;
                            }
                        }
                    } else {
                        // Earlier canonicalization removed the loop altogether
                    }

                    FixedNode explodeLoopNext = explodeLoop.next();
                    explodeLoop.clearSuccessors();
                    explodeLoop.replaceAtPredecessors(explodeLoopNext);
                    explodeLoop.replaceAtUsages(null);
                    GraphUtil.killCFG(explodeLoop);
                    break;
                }
            }
        } while (exploded);

        // Remove all frame states from inlined snippet graph. Snippets must be atomic (i.e. free
        // of side-effects that prevent deoptimizing to a point before the snippet).
        for (Node node : snippetCopy.getNodes()) {
            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState frameState = stateSplit.stateAfter();
                assert !stateSplit.hasSideEffect() : "snippets cannot contain side-effecting node " + node + "\n    " + frameState.toString(Verbosity.Debugger);
                if (frameState != null) {
                    stateSplit.setStateAfter(null);
                }
            }
        }

        new DeadCodeEliminationPhase().apply(snippetCopy);

        this.graph = snippetCopy;
        nodes = new ArrayList<>(graph.getNodeCount());
        ReturnNode retNode = null;
        StartNode entryPointNode = graph.start();
        for (Node node : graph.getNodes()) {
            if (node == entryPointNode || node == entryPointNode.stateAfter()) {
                // Do nothing.
            } else {
                nodes.add(node);
                if (node instanceof ReturnNode) {
                    retNode = (ReturnNode) node;
                }
            }
        }
        this.returnNode = retNode;
    }

    private static boolean checkConstantArgument(final RiResolvedMethod method, RiSignature signature, int i, String name, Object arg, CiKind kind) {
        if (kind.isObject()) {
            Class<?> type = signature.argumentTypeAt(i, method.holder()).resolve(method.holder()).toJava();
            assert type.isInstance(arg) :
                method + ": wrong value type for " + name + ": expected " + type.getName() + ", got " + arg.getClass().getName();
        } else {
            assert kind.toBoxedJavaClass() == arg.getClass() :
                method + ": wrong value kind for " + name + ": expected " + kind + ", got " + arg.getClass().getSimpleName();
        }
        return true;
    }

    private static boolean checkMultipleArgument(final RiResolvedMethod method, RiSignature signature, int i, String name, Object multiple) {
        assert multiple instanceof Multiple;
        Object arg = ((Multiple) multiple).array;
        RiResolvedType type = (RiResolvedType) signature.argumentTypeAt(i, method.holder());
        Class< ? > javaType = type.toJava();
        assert javaType.isArray() : "multiple parameter must be an array type";
        assert javaType.isInstance(arg) : "value for " + name + " is not a " + javaType.getName() + " instance: " + arg;
        return true;
    }

    /**
     * The graph built from the snippet method.
     */
    private final StructuredGraph graph;

    /**
     * The named parameters of this template that must be bound to values during instantiation.
     * Each parameter is either a {@link LocalNode} or a {@link LoadMultipleParameterNode} instance.
     */
    private final Map<String, ValueNode> parameters;

    /**
     * The return node (if any) of the snippet.
     */
    private final ReturnNode returnNode;

    /**
     * The nodes to be inlined when this specialization is instantiated.
     */
    private final ArrayList<Node> nodes;

    /**
     * Gets the instantiation-time bindings to this template's parameters.
     *
     * @return the map that will be used to bind arguments to parameters when inlining this template
     */
    private IdentityHashMap<Node, Node> bind(StructuredGraph replaceeGraph, RiRuntime runtime, Snippet.Arguments args) {
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();

        for (Map.Entry<String, Object> e : args) {
            String name = e.getKey();
            ValueNode parameter = parameters.get(name);
            assert parameter != null : this + " has no parameter named " + name;
            Object argument = e.getValue();
            if (parameter instanceof LocalNode) {
                if (argument instanceof ValueNode) {
                    replacements.put(parameter, (ValueNode) argument);
                } else {
                    CiKind kind = ((LocalNode) parameter).kind();
                    CiConstant constant = CiConstant.forBoxed(kind, argument);
                    replacements.put(parameter, ConstantNode.forCiConstant(constant, runtime, replaceeGraph));
                }
            } else {
                assert parameter instanceof LoadMultipleParameterNode;
                Object array = argument;
                assert array != null && array.getClass().isArray();
                int length = Array.getLength(array);
                LoadMultipleParameterNode lmp = (LoadMultipleParameterNode) parameter;
                assert length == lmp.getLocalCount() : length + " != " + lmp.getLocalCount();
                for (int j = 0; j < length; j++) {
                    LocalNode local = lmp.getLocal(j);
                    assert local != null;
                    CiConstant constant = CiConstant.forBoxed(lmp.kind(), Array.get(array, j));
                    ConstantNode element = ConstantNode.forCiConstant(constant, runtime, replaceeGraph);
                    replacements.put(local, element);
                }
            }
        }
        return replacements;
    }

    /**
     * Replaces a given node with this specialized snippet.
     *
     * @param runtime
     * @param replacee the node that will be replaced
     * @param anchor the control flow replacee
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    public void instantiate(RiRuntime runtime,
                    Node replacee,
                    FixedWithNextNode anchor, Arguments args) {

        // Inline the snippet nodes, replacing parameters with the given args in the process
        String name = graph.name == null ? "{copy}" : graph.name + "{copy}";
        StructuredGraph graphCopy = new StructuredGraph(name, graph.method());
        StartNode entryPointNode = graph.start();
        FixedNode firstCFGNode = entryPointNode.next();
        StructuredGraph replaceeGraph = (StructuredGraph) replacee.graph();
        IdentityHashMap<Node, Node> replacements = bind(replaceeGraph, runtime, args);
        Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, replacements);
        Debug.dump(replaceeGraph, "After inlining snippet %s", graphCopy.method());

        // Re-wire the control flow graph around the replacee
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        anchor.replaceAtPredecessors(firstCFGNodeDuplicate);
        FixedNode next = anchor.next();
        anchor.setNext(null);

        // Replace all usages of the replacee with the value returned by the snippet
        Node returnValue = null;
        if (returnNode != null) {
            if (returnNode.result() instanceof LocalNode) {
                returnValue = replacements.get(returnNode.result());
            } else {
                returnValue = duplicates.get(returnNode.result());
            }
            assert returnValue != null || replacee.usages().isEmpty();
            replacee.replaceAtUsages(returnValue);

            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.clearInputs();
            returnDuplicate.replaceAndDelete(next);
        }

        // Remove the replacee from its graph
        replacee.clearInputs();
        replacee.replaceAtUsages(null);
        if (replacee instanceof FixedNode) {
            GraphUtil.killCFG((FixedNode) replacee);
        } else {
            replacee.safeDelete();
        }

        Debug.dump(replaceeGraph, "After lowering %s with %s", replacee, this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(graph.toString()).append('(');
        String sep = "";
        for (Map.Entry<String, ValueNode> e : parameters.entrySet()) {
            String name = e.getKey();
            ValueNode value = e.getValue();
            buf.append(sep);
            sep = ", ";
            if (value instanceof LocalNode) {
                buf.append(value.kind().name()).append(' ').append(name);
            } else {
                LoadMultipleParameterNode lmp = (LoadMultipleParameterNode) value;
                buf.append(value.kind().name()).append('[').append(lmp.getLocalCount()).append("] ").append(name);
            }
        }
        return buf.append(')').toString();
    }
}

