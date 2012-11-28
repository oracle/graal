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
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.Snippet.Varargs;
import com.oracle.graal.snippets.Snippet.VarargsParameter;
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
     * and the arguments used to specialize the snippet.
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
            assert !map.containsKey(name);
            map.put(name, value);
            hash = hash ^ name.hashCode();
            if (value != null) {
                hash *= (value.hashCode() + 1);
            }
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
            return MetaUtil.format("%h.%n", method) + map.toString();
        }

        public Set<String> names() {
            return map.keySet();
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
        private final MetaAccessProvider runtime;
        private final TargetDescription target;


        public Cache(MetaAccessProvider runtime, TargetDescription target) {
            this.runtime = runtime;
            this.target = target;
        }

        /**
         * Gets a template for a given key, creating it first if necessary.
         */
        public SnippetTemplate get(final SnippetTemplate.Key key, final Assumptions assumptions) {
            SnippetTemplate template = templates.get(key);
            if (template == null) {
                template = Debug.scope("SnippetSpecialization", key.method, new Callable<SnippetTemplate>() {
                    @Override
                    public SnippetTemplate call() throws Exception {
                        return new SnippetTemplate(runtime, assumptions, target, key);
                    }
                });
                //System.out.println(key + " -> " + template);
                templates.put(key, template);
            }
            return template;
        }
    }

    public abstract static class AbstractTemplates<T extends SnippetsInterface> {
        protected final Cache cache;
        protected final MetaAccessProvider runtime;
        protected final Assumptions assumptions;
        protected Class<T> snippetsClass;

        public AbstractTemplates(MetaAccessProvider runtime, Assumptions assumptions, TargetDescription target, Class<T> snippetsClass) {
            this.runtime = runtime;
            this.assumptions = assumptions;
            this.snippetsClass = snippetsClass;
            this.cache = new Cache(runtime, target);
        }

        protected ResolvedJavaMethod snippet(String name, Class<?>... parameterTypes) {
            try {
                ResolvedJavaMethod snippet = runtime.lookupJavaMethod(snippetsClass.getDeclaredMethod(name, parameterTypes));
                assert snippet.getAnnotation(Snippet.class) != null : "snippet is not annotated with @" + Snippet.class.getSimpleName();
                return snippet;
            } catch (NoSuchMethodException e) {
                throw new GraalInternalError(e);
            }
        }
}

    /**
     * Determines if any parameter of a given method is annotated with {@link ConstantParameter}.
     */
    public static boolean hasConstantParameter(ResolvedJavaMethod method) {
        for (ConstantParameter p : MetaUtil.getParameterAnnotations(ConstantParameter.class, method)) {
            if (p != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a snippet template.
     */
    public SnippetTemplate(MetaAccessProvider runtime, Assumptions assumptions, TargetDescription target, SnippetTemplate.Key key) {
        ResolvedJavaMethod method = key.method;
        assert Modifier.isStatic(method.getModifiers()) : "snippet method must be static: " + method;
        Signature signature = method.getSignature();

        // Copy snippet graph, replacing constant parameters with given arguments
        StructuredGraph snippetGraph = (StructuredGraph) method.getCompilerStorage().get(Graph.class);
        StructuredGraph snippetCopy = new StructuredGraph(snippetGraph.name, snippetGraph.method());
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        replacements.put(snippetGraph.start(), snippetCopy.start());

        int parameterCount = signature.getParameterCount(false);
        assert checkTemplate(key, parameterCount, method, signature);

        Parameter[] parameterAnnotations = new Parameter[parameterCount];
        VarargsParameter[] varargsParameterAnnotations = new VarargsParameter[parameterCount];
        ConstantNode[] placeholders = new ConstantNode[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            ConstantParameter c = MetaUtil.getParameterAnnotation(ConstantParameter.class, i, method);
            if (c != null) {
                String name = c.value();
                Object arg = key.get(name);
                Kind kind = signature.getParameterKind(i);
                replacements.put(snippetGraph.getLocal(i), ConstantNode.forConstant(Constant.forBoxed(kind, arg), runtime, snippetCopy));
            } else {
                VarargsParameter vp = MetaUtil.getParameterAnnotation(VarargsParameter.class, i, method);
                if (vp != null) {
                    String name = vp.value();
                    Varargs varargs = (Varargs) key.get(name);
                    Object array = varargs.getArray();
                    ConstantNode placeholder = ConstantNode.forObject(array, runtime, snippetCopy);
                    replacements.put(snippetGraph.getLocal(i), placeholder);
                    placeholders[i] = placeholder;
                    varargsParameterAnnotations[i] = vp;
                } else {
                    parameterAnnotations[i] = MetaUtil.getParameterAnnotation(Parameter.class, i, method);
                }
            }
        }
        snippetCopy.addDuplicates(snippetGraph.getNodes(), replacements);

        Debug.dump(snippetCopy, "Before specialization");
        if (!replacements.isEmpty()) {
            // Do deferred intrinsification of node intrinsics
            new SnippetIntrinsificationPhase(runtime, new BoxingMethodPool(runtime), false).apply(snippetCopy);
            new WordTypeRewriterPhase(target.wordKind).apply(snippetCopy);

            new CanonicalizerPhase(null, runtime, assumptions, 0, null).apply(snippetCopy);
        }

        // Gather the template parameters
        parameters = new HashMap<>();
        for (int i = 0; i < parameterCount; i++) {
            VarargsParameter vp = varargsParameterAnnotations[i];
            if (vp != null) {
                assert snippetCopy.getLocal(i) == null;
                Varargs varargs = (Varargs) key.get(vp.value());
                Object array = varargs.getArray();
                int length = Array.getLength(array);
                LocalNode[] locals = new LocalNode[length];
                Stamp stamp = varargs.getArgStamp();
                for (int j = 0; j < length; j++) {
                    assert (parameterCount & 0xFFFF) == parameterCount;
                    int idx = i << 16 | j;
                    LocalNode local = snippetCopy.unique(new LocalNode(idx, stamp));
                    locals[j] = local;
                }
                parameters.put(vp.value(), locals);

                ConstantNode placeholder = placeholders[i];
                assert placeholder != null;
                for (Node usage : placeholder.usages().snapshot()) {
                    if (usage instanceof LoadIndexedNode) {
                        LoadIndexedNode loadIndexed = (LoadIndexedNode) usage;
                        Debug.dump(snippetCopy, "Before replacing %s", loadIndexed);
                        LoadSnippetVarargParameterNode loadSnippetParameter = snippetCopy.add(new LoadSnippetVarargParameterNode(locals, loadIndexed.index(), loadIndexed.stamp()));
                        snippetCopy.replaceFixedWithFixed(loadIndexed, loadSnippetParameter);
                        Debug.dump(snippetCopy, "After replacing %s", loadIndexed);
                    }
                }
            } else {
                Parameter p = parameterAnnotations[i];
                if (p != null) {
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
                    LoopTransformations.fullUnroll(loop, runtime, null);
                    new CanonicalizerPhase(null, runtime, assumptions, mark, null).apply(snippetCopy);
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
        List<Node> curSideEffectNodes = new ArrayList<>();
        Node curStampNode = null;
        for (Node node : snippetCopy.getNodes()) {
            if (node instanceof ValueNode && ((ValueNode) node).stamp() == StampFactory.forNodeIntrinsic()) {
                assert curStampNode == null : "Currently limited to one stamp node (but this can be converted to a List if necessary)";
                curStampNode = node;
            }
            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState frameState = stateSplit.stateAfter();
                if (stateSplit.hasSideEffect()) {
                    curSideEffectNodes.add(node);
                }
                if (frameState != null) {
                    stateSplit.setStateAfter(null);
                }
            }
        }

        new DeadCodeEliminationPhase().apply(snippetCopy);

        assert checkAllVarargPlaceholdersAreDeleted(parameterCount, placeholders);

        this.snippet = snippetCopy;
        ReturnNode retNode = null;
        StartNode entryPointNode = snippet.start();

        new DeadCodeEliminationPhase().apply(snippetCopy);

        nodes = new ArrayList<>(snippet.getNodeCount());
        for (Node node : snippet.getNodes()) {
            if (node == entryPointNode || node == entryPointNode.stateAfter()) {
                // Do nothing.
            } else {
                nodes.add(node);
                if (node instanceof ReturnNode) {
                    retNode = (ReturnNode) node;
                }
            }
        }

        this.sideEffectNodes = curSideEffectNodes;
        this.stampNode = curStampNode;
        this.returnNode = retNode;
    }

    private static boolean checkAllVarargPlaceholdersAreDeleted(int parameterCount, ConstantNode[] placeholders) {
        for (int i = 0; i < parameterCount; i++) {
            if (placeholders[i] != null) {
                assert placeholders[i].isDeleted() : placeholders[i];
            }
        }
        return true;
    }

    private static boolean checkConstantArgument(final ResolvedJavaMethod method, Signature signature, int i, String name, Object arg, Kind kind) {
        if (kind.isObject()) {
            ResolvedJavaType type = signature.getParameterType(i, method.getDeclaringClass()).resolve(method.getDeclaringClass());
            assert arg == null || type.isInstance(Constant.forObject(arg)) :
                method + ": wrong value type for " + name + ": expected " + type.getName() + ", got " + arg.getClass().getName();
        } else {
            assert arg != null && kind.toBoxedJavaClass() == arg.getClass() :
                method + ": wrong value kind for " + name + ": expected " + kind + ", got " + (arg == null ? "null" : arg.getClass().getSimpleName());
        }
        return true;
    }

    private static boolean checkVarargs(final ResolvedJavaMethod method, Signature signature, int i, String name, Varargs varargs) {
        Object arg = varargs.getArray();
        ResolvedJavaType type = (ResolvedJavaType) signature.getParameterType(i, method.getDeclaringClass());
        assert type.isArray() : "varargs parameter must be an array type";
        assert type.isInstance(Constant.forObject(arg)) : "value for " + name + " is not a " + MetaUtil.toJavaName(type) + " instance: " + arg;
        return true;
    }

    /**
     * The graph built from the snippet method.
     */
    private final StructuredGraph snippet;

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
     * Nodes that inherit the {@link StateSplit#stateAfter()} from the replacee during instantiation.
     */
    private final List<Node> sideEffectNodes;

    /**
     * Node that inherits the {@link ValueNode#stamp()} from the replacee during instantiation.
     */
    private final Node stampNode;

    /**
     * The nodes to be inlined when this specialization is instantiated.
     */
    private final ArrayList<Node> nodes;

    /**
     * Gets the instantiation-time bindings to this template's parameters.
     *
     * @return the map that will be used to bind arguments to parameters when inlining this template
     */
    private IdentityHashMap<Node, Node> bind(StructuredGraph replaceeGraph, MetaAccessProvider runtime, SnippetTemplate.Arguments args) {
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        assert args.length() == parameters.size() : "number of args (" + args.length() + ") != number of parameters (" + parameters.size() + ")";
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
                    assert argument != null || kind.isObject() : this + " cannot accept null for non-object parameter named " + name;
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
                    Object value = Array.get(array, j);
                    if (value instanceof ValueNode) {
                        replacements.put(local, (ValueNode) value);
                    } else {
                        Constant constant = Constant.forBoxed(local.kind(), value);
                        ConstantNode element = ConstantNode.forConstant(constant, runtime, replaceeGraph);
                        replacements.put(local, element);
                    }
                }
            }
        }
        return replacements;
    }

    /**
     * Logic for replacing a snippet-lowered node at its usages with the return value
     * of the snippet. An alternative to the
     * {@linkplain SnippetTemplate#DEFAULT_REPLACER default} replacement logic can be used to
     * handle mismatches between the stamp of the node being lowered and the
     * stamp of the snippet's return value.
     */
    public interface UsageReplacer {
        /**
         * Replaces all usages of {@code oldNode} with direct or indirect usages of {@code newNode}.
         */
        void replace(ValueNode oldNode, ValueNode newNode);
    }

    /**
     * Represents the default {@link UsageReplacer usage replacer} logic which
     * simply delegates to {@link Node#replaceAtUsages(Node)}.
     */
    public static final UsageReplacer DEFAULT_REPLACER = new UsageReplacer() {
        @Override
        public void replace(ValueNode oldNode, ValueNode newNode) {
            oldNode.replaceAtUsages(newNode);
        }
    };

    /**
     * Replaces a given fixed node with this specialized snippet.
     *
     * @param runtime
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     * @return the map of duplicated nodes (original -> duplicate)
     */
    public Map<Node, Node> instantiate(MetaAccessProvider runtime,
                    FixedWithNextNode replacee,
                    UsageReplacer replacer,
                    SnippetTemplate.Arguments args) {

        // Inline the snippet nodes, replacing parameters with the given args in the process
        String name = snippet.name == null ? "{copy}" : snippet.name + "{copy}";
        StructuredGraph snippetCopy = new StructuredGraph(name, snippet.method());
        StartNode entryPointNode = snippet.start();
        FixedNode firstCFGNode = entryPointNode.next();
        StructuredGraph replaceeGraph = (StructuredGraph) replacee.graph();
        IdentityHashMap<Node, Node> replacements = bind(replaceeGraph, runtime, args);
        Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, replacements);
        Debug.dump(replaceeGraph, "After inlining snippet %s", snippetCopy.method());

        // Re-wire the control flow graph around the replacee
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        replacee.replaceAtPredecessor(firstCFGNodeDuplicate);
        FixedNode next = replacee.next();
        replacee.setNext(null);

        if (replacee instanceof StateSplit) {
            for (Node sideEffectNode : sideEffectNodes) {
                assert ((StateSplit) replacee).hasSideEffect();
                Node sideEffectDup = duplicates.get(sideEffectNode);
                ((StateSplit) sideEffectDup).setStateAfter(((StateSplit) replacee).stateAfter());
            }
        }
        if (stampNode != null) {
            Node stampDup = duplicates.get(stampNode);
            ((ValueNode) stampDup).setStamp(((ValueNode) replacee).stamp());
        }

        // Replace all usages of the replacee with the value returned by the snippet
        ValueNode returnValue = null;
        if (returnNode != null) {
            if (returnNode.result() instanceof LocalNode) {
                returnValue = (ValueNode) replacements.get(returnNode.result());
            } else {
                returnValue = (ValueNode) duplicates.get(returnNode.result());
            }
            assert returnValue != null || replacee.usages().isEmpty();
            replacer.replace(replacee, returnValue);

            Node returnDuplicate = duplicates.get(returnNode);
            if (returnDuplicate.isAlive()) {
                returnDuplicate.clearInputs();
                returnDuplicate.replaceAndDelete(next);
            }
        }

        // Remove the replacee from its graph
        replacee.clearInputs();
        replacee.replaceAtUsages(null);
        GraphUtil.killCFG(replacee);

        Debug.dump(replaceeGraph, "After lowering %s with %s", replacee, this);
        return duplicates;
    }

    /**
     * Replaces a given floating node with this specialized snippet.
     *
     * @param runtime
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param lastFixedNode the CFG of the snippet is inserted after this node
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    public void instantiate(MetaAccessProvider runtime,
                    FloatingNode replacee,
                    UsageReplacer replacer,
                    FixedWithNextNode lastFixedNode, SnippetTemplate.Arguments args) {

        // Inline the snippet nodes, replacing parameters with the given args in the process
        String name = snippet.name == null ? "{copy}" : snippet.name + "{copy}";
        StructuredGraph snippetCopy = new StructuredGraph(name, snippet.method());
        StartNode entryPointNode = snippet.start();
        FixedNode firstCFGNode = entryPointNode.next();
        StructuredGraph replaceeGraph = (StructuredGraph) replacee.graph();
        IdentityHashMap<Node, Node> replacements = bind(replaceeGraph, runtime, args);
        Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, replacements);
        Debug.dump(replaceeGraph, "After inlining snippet %s", snippetCopy.method());

        assert lastFixedNode != null : replaceeGraph;
        FixedNode next = lastFixedNode.next();
        lastFixedNode.setNext(null);
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        replaceeGraph.addAfterFixed(lastFixedNode, firstCFGNodeDuplicate);

        if (replacee instanceof StateSplit) {
            for (Node sideEffectNode : sideEffectNodes) {
                assert ((StateSplit) replacee).hasSideEffect();
                Node sideEffectDup = duplicates.get(sideEffectNode);
                ((StateSplit) sideEffectDup).setStateAfter(((StateSplit) replacee).stateAfter());
            }
        }
        if (stampNode != null) {
            Node stampDup = duplicates.get(stampNode);
            ((ValueNode) stampDup).setStamp(((ValueNode) replacee).stamp());
        }

        // Replace all usages of the replacee with the value returned by the snippet
        assert returnNode != null : replaceeGraph;
        ValueNode returnValue = null;
        if (returnNode.result() instanceof LocalNode) {
            returnValue = (ValueNode) replacements.get(returnNode.result());
        } else {
            returnValue = (ValueNode) duplicates.get(returnNode.result());
        }
        assert returnValue != null || replacee.usages().isEmpty();
        replacer.replace(replacee, returnValue);

        Node returnDuplicate = duplicates.get(returnNode);
        if (returnDuplicate.isAlive()) {
            returnDuplicate.clearInputs();
            returnDuplicate.replaceAndDelete(next);
        }

        Debug.dump(replaceeGraph, "After lowering %s with %s", replacee, this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(snippet.toString()).append('(');
        String sep = "";
        for (Map.Entry<String, Object> e : parameters.entrySet()) {
            String name = e.getKey();
            Object value = e.getValue();
            buf.append(sep);
            sep = ", ";
            if (value instanceof LocalNode) {
                LocalNode local = (LocalNode) value;
                buf.append(local.kind().getJavaName()).append(' ').append(name);
            } else {
                LocalNode[] locals = (LocalNode[]) value;
                String kind = locals.length == 0 ? "?" : locals[0].kind().getJavaName();
                buf.append(kind).append('[').append(locals.length).append("] ").append(name);
            }
        }
        return buf.append(')').toString();
    }

    private static boolean checkTemplate(SnippetTemplate.Key key, int parameterCount, ResolvedJavaMethod method, Signature signature) {
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < parameterCount; i++) {
            ConstantParameter c = MetaUtil.getParameterAnnotation(ConstantParameter.class, i, method);
            VarargsParameter vp = MetaUtil.getParameterAnnotation(VarargsParameter.class, i, method);
            Parameter p = MetaUtil.getParameterAnnotation(Parameter.class, i, method);
            if (c != null) {
                assert vp == null && p == null;
                String name = c.value();
                expected.add(name);
                Kind kind = signature.getParameterKind(i);
                assert key.names().contains(name) : "key for " + method + " is missing \"" + name + "\": " + key;
                assert checkConstantArgument(method, signature, i, c.value(), key.get(name), kind);
            } else if (vp != null) {
                assert p == null;
                String name = vp.value();
                expected.add(name);
                assert key.names().contains(name) : "key for " + method + " is missing \"" + name + "\": " + key;
                assert key.get(name) instanceof Varargs;
                Varargs varargs = (Varargs) key.get(name);
                assert checkVarargs(method, signature, i, name, varargs);
            } else {
                assert p != null : method + ": parameter " + i + " must be annotated with exactly one of " +
                    "@" + ConstantParameter.class.getSimpleName() + " or " +
                    "@" + VarargsParameter.class.getSimpleName() + " or " +
                    "@" + Parameter.class.getSimpleName();
            }
        }
        if (!key.names().containsAll(expected)) {
            expected.removeAll(key.names());
            assert false : expected + " missing from key " + key;
        }
        if (!expected.containsAll(key.names())) {
            Set<String> namesCopy = new HashSet<>(key.names());
            namesCopy.removeAll(expected);
            assert false : "parameter(s) " + namesCopy + " should be annotated with @" + ConstantParameter.class.getSimpleName() +
                " or @" + VarargsParameter.class.getSimpleName() + " in " + MetaUtil.format("%H.%n(%p)", method);
        }
        return true;
    }
}
