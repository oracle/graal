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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.loop.*;
import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Multiple;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.nodes.*;

/**
 * A snippet template is a graph created by parsing a snippet method and then
 * specialized by binding constants to the snippet's {@link ConstantParameter} parameters.
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
        public final ResolvedJavaMethod method;
        private final HashMap<String, Object> map = new HashMap<>();
        private int hash;

        public Key(ResolvedJavaMethod method) {
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
            return CodeUtil.format("%h.%n", method) + map.toString();
        }
    }

    /**
     * Arguments used to instantiate a template.
     */
    public static class Arguments implements Iterable<Map.Entry<String, Object>> {
        private final HashMap<String, Object> map = new HashMap<>();

        public static Arguments arguments(String name, Object value) {
            return new Arguments().add(name, value);
        }

        public Arguments add(String name, Object value) {
            assert !map.containsKey(name);
            map.put(name, value);
            return this;
        }

        public int length() {
            return map.size();
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            return map.entrySet().iterator();
        }

        @Override
        public String toString() {
            return map.toString();
        }
    }

    /**
     * A collection of snippet templates accessed by a {@link Key} instance.
     */
    public static class Cache {

        private final ConcurrentHashMap<SnippetTemplate.Key, SnippetTemplate> templates = new ConcurrentHashMap<>();
        private final CodeCacheProvider runtime;


        public Cache(CodeCacheProvider runtime) {
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
    public SnippetTemplate(CodeCacheProvider runtime, SnippetTemplate.Key key) {
        ResolvedJavaMethod method = key.method;
        assert Modifier.isStatic(method.accessFlags()) : "snippet method must be static: " + method;
        Signature signature = method.signature();

        // Copy snippet graph, replacing @Constant parameters with given arguments
        StructuredGraph snippetGraph = (StructuredGraph) method.compilerStorage().get(Graph.class);
        StructuredGraph snippetCopy = new StructuredGraph(snippetGraph.name, snippetGraph.method());
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        replacements.put(snippetGraph.start(), snippetCopy.start());

        int parameterCount = signature.argumentCount(false);
        Parameter[] parameterAnnotations = new Parameter[parameterCount];
        ConstantNode[] placeholders = new ConstantNode[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            ConstantParameter c = CodeUtil.getParameterAnnotation(ConstantParameter.class, i, method);
            if (c != null) {
                String name = c.value();
                Object arg = key.get(name);
                assert arg != null : method + ": requires a constant named " + name;
                Kind kind = signature.argumentKindAt(i);
                assert checkConstantArgument(method, signature, i, name, arg, kind);
                replacements.put(snippetGraph.getLocal(i), ConstantNode.forConstant(Constant.forBoxed(kind, arg), runtime, snippetCopy));
            } else {
                Parameter p = CodeUtil.getParameterAnnotation(Parameter.class, i, method);
                assert p != null : method + ": parameter " + i + " must be annotated with either @Constant or @Parameter";
                String name = p.value();
                if (p.multiple()) {
                    Object multiple = key.get(name);
                    assert multiple != null : method + ": requires a Multiple named " + name;
                    assert checkMultipleArgument(method, signature, i, name, multiple);
                    Object array = ((Multiple) multiple).array;
                    ConstantNode placeholder = ConstantNode.forObject(array, runtime, snippetCopy);
                    replacements.put(snippetGraph.getLocal(i), placeholder);
                    placeholders[i] = placeholder;
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
                if (p.multiple()) {
                    assert snippetCopy.getLocal(i) == null;
                    Object array = ((Multiple) key.get(p.value())).array;
                    int length = Array.getLength(array);
                    LocalNode[] locals = new LocalNode[length];
                    Stamp stamp = StampFactory.forKind(runtime.getResolvedJavaType(array.getClass().getComponentType()).kind());
                    for (int j = 0; j < length; j++) {
                        assert (parameterCount & 0xFFFF) == parameterCount;
                        int idx = i << 16 | j;
                        LocalNode local = snippetCopy.unique(new LocalNode(idx, stamp));
                        locals[j] = local;
                    }
                    parameters.put(p.value(), locals);

                    ConstantNode placeholder = placeholders[i];
                    assert placeholder != null;
                    for (Node usage : placeholder.usages().snapshot()) {
                        if (usage instanceof LoadIndexedNode) {
                            LoadIndexedNode loadIndexed = (LoadIndexedNode) usage;
                            Debug.dump(snippetCopy, "Before replacing %s", loadIndexed);
                            LoadSnippetParameterNode loadSnippetParameter = snippetCopy.add(new LoadSnippetParameterNode(locals, loadIndexed.index(), loadIndexed.stamp()));
                            snippetCopy.replaceFixedWithFixed(loadIndexed, loadSnippetParameter);
                            Debug.dump(snippetCopy, "After replacing %s", loadIndexed);
                        }
                    }
                } else {
                    LocalNode local = snippetCopy.getLocal(i);
                    assert local != null;
                    parameters.put(p.value(), local);
                }
            }
        }

        // Do any required loop explosion
        boolean exploded = false;
        do {
            exploded = false;
            ExplodeLoopNode explodeLoop = snippetCopy.getNodes().filter(ExplodeLoopNode.class).first();
            if (explodeLoop != null) { // Earlier canonicalization may have removed the loop altogether
                LoopBeginNode loopBegin = explodeLoop.findLoopBegin();
                if (loopBegin != null) {
                    LoopEx loop = new LoopsData(snippetCopy).loop(loopBegin);
                    int mark = snippetCopy.getMark();
                    LoopTransformations.fullUnroll(loop);
                    new CanonicalizerPhase(null, runtime, null, mark, null).apply(snippetCopy);
                }
                FixedNode explodeLoopNext = explodeLoop.next();
                explodeLoop.clearSuccessors();
                explodeLoop.replaceAtPredecessor(explodeLoopNext);
                explodeLoop.replaceAtUsages(null);
                GraphUtil.killCFG(explodeLoop);
                exploded = true;
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

        assert checkAllMultipleParameterPlaceholdersAreDeleted(parameterCount, placeholders);

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

    private static boolean checkAllMultipleParameterPlaceholdersAreDeleted(int parameterCount, ConstantNode[] placeholders) {
        for (int i = 0; i < parameterCount; i++) {
            if (placeholders[i] != null) {
                assert placeholders[i].isDeleted() : placeholders[i];
            }
        }
        return true;
    }

    private static boolean checkConstantArgument(final ResolvedJavaMethod method, Signature signature, int i, String name, Object arg, Kind kind) {
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

    private static boolean checkMultipleArgument(final ResolvedJavaMethod method, Signature signature, int i, String name, Object multiple) {
        assert multiple instanceof Multiple;
        Object arg = ((Multiple) multiple).array;
        ResolvedJavaType type = (ResolvedJavaType) signature.argumentTypeAt(i, method.holder());
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
     * Each value in this map is either a {@link LocalNode} instance or a {@link LocalNode} array.
     */
    private final Map<String, Object> parameters;

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
    private IdentityHashMap<Node, Node> bind(StructuredGraph replaceeGraph, CodeCacheProvider runtime, SnippetTemplate.Arguments args) {
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();

        for (Map.Entry<String, Object> e : args) {
            String name = e.getKey();
            Object parameter = parameters.get(name);
            assert parameter != null : this + " has no parameter named " + name;
            Object argument = e.getValue();
            if (parameter instanceof LocalNode) {
                if (argument instanceof ValueNode) {
                    replacements.put((LocalNode) parameter, (ValueNode) argument);
                } else {
                    Kind kind = ((LocalNode) parameter).kind();
                    Constant constant = Constant.forBoxed(kind, argument);
                    replacements.put((LocalNode) parameter, ConstantNode.forConstant(constant, runtime, replaceeGraph));
                }
            } else {
                assert parameter instanceof LocalNode[];
                LocalNode[] locals = (LocalNode[]) parameter;
                Object array = argument;
                assert array != null && array.getClass().isArray();
                int length = locals.length;
                assert Array.getLength(array) == length : length + " != " + Array.getLength(array);
                for (int j = 0; j < length; j++) {
                    LocalNode local = locals[j];
                    assert local != null;
                    Constant constant = Constant.forBoxed(local.kind(), Array.get(array, j));
                    ConstantNode element = ConstantNode.forConstant(constant, runtime, replaceeGraph);
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
    public void instantiate(CodeCacheProvider runtime,
                    Node replacee,
                    FixedWithNextNode anchor, SnippetTemplate.Arguments args) {

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
        anchor.replaceAtPredecessor(firstCFGNodeDuplicate);
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
        for (Map.Entry<String, Object> e : parameters.entrySet()) {
            String name = e.getKey();
            Object value = e.getValue();
            buf.append(sep);
            sep = ", ";
            if (value instanceof LocalNode) {
                LocalNode local = (LocalNode) value;
                buf.append(local.kind().name()).append(' ').append(name);
            } else {
                LocalNode[] locals = (LocalNode[]) value;
                String kind = locals.length == 0 ? "?" : locals[0].kind().name();
                buf.append(kind).append('[').append(locals.length).append("] ").append(name);
            }
        }
        return buf.append(')').toString();
    }
}

