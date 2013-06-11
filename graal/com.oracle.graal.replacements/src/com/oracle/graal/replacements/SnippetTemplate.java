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
package com.oracle.graal.replacements;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.VarargsParameter;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;
import com.oracle.graal.word.phases.*;

/**
 * A snippet template is a graph created by parsing a snippet method and then specialized by binding
 * constants to the snippet's {@link ConstantParameter} parameters.
 * 
 * Snippet templates can be managed in a cache maintained by {@link AbstractTemplates}.
 */
public class SnippetTemplate {

    /**
     * Holds the {@link ResolvedJavaMethod} of the snippet, together with some information about the
     * method that needs to be computed only once. The {@link SnippetInfo} should be created once
     * per snippet an then cached.
     */
    public static class SnippetInfo {

        protected final ResolvedJavaMethod method;
        protected final boolean[] constantParameters;
        protected final boolean[] varargsParameters;

        /**
         * The parameter names, taken from the local variables table. Only used for assertion
         * checking, so use only within an assert statement.
         */
        protected final String[] names;

        protected SnippetInfo(ResolvedJavaMethod method) {
            this.method = method;

            assert Modifier.isStatic(method.getModifiers()) : "snippet method must be static: " + MetaUtil.format("%H.%n", method);
            int count = method.getSignature().getParameterCount(false);
            constantParameters = new boolean[count];
            varargsParameters = new boolean[count];
            for (int i = 0; i < count; i++) {
                constantParameters[i] = MetaUtil.getParameterAnnotation(ConstantParameter.class, i, method) != null;
                varargsParameters[i] = MetaUtil.getParameterAnnotation(VarargsParameter.class, i, method) != null;

                assert !isConstantParameter(i) || !isVarargsParameter(i) : "Parameter cannot be annotated with both @" + ConstantParameter.class.getSimpleName() + " and @" +
                                VarargsParameter.class.getSimpleName();
            }

            names = new String[count];
            // Retrieve the names only when assertions are turned on.
            assert initNames();
        }

        private boolean initNames() {
            int slotIdx = 0;
            for (int i = 0; i < names.length; i++) {
                names[i] = method.getLocalVariableTable().getLocal(slotIdx, 0).getName();

                Kind kind = method.getSignature().getParameterKind(i);
                slotIdx += kind == Kind.Long || kind == Kind.Double ? 2 : 1;
            }
            return true;
        }

        public ResolvedJavaMethod getMethod() {
            return method;
        }

        public int getParameterCount() {
            return constantParameters.length;
        }

        public boolean isConstantParameter(int paramIdx) {
            return constantParameters[paramIdx];
        }

        public boolean isVarargsParameter(int paramIdx) {
            return varargsParameters[paramIdx];
        }

        public String getParameterName(int paramIdx) {
            return names[paramIdx];
        }
    }

    /**
     * Values that are bound to the snippet method parameters. The methods {@link #add},
     * {@link #addConst}, and {@link #addVarargs} must be called in the same order as in the
     * signature of the snippet method. The parameter name is passed to the add methods for
     * assertion checking, i.e., to enforce that the order matches. Which method needs to be called
     * depends on the annotation of the snippet method parameter:
     * <ul>
     * <li>Use {@link #add} for a parameter without an annotation. The value is bound when the
     * {@link SnippetTemplate} is {@link SnippetTemplate#instantiate instantiated}.
     * <li>Use {@link #addConst} for a parameter annotated with {@link ConstantParameter}. The value
     * is bound when the {@link SnippetTemplate} is {@link SnippetTemplate#SnippetTemplate created}.
     * <li>Use {@link #addVarargs} for an array parameter annotated with {@link VarargsParameter}. A
     * separate {@link SnippetTemplate} is {@link SnippetTemplate#SnippetTemplate created} for every
     * distinct array length. The actual values are bound when the {@link SnippetTemplate} is
     * {@link SnippetTemplate#instantiate instantiated}
     * </ul>
     */
    public static class Arguments {

        protected final SnippetInfo info;
        protected final CacheKey cacheKey;
        protected final Object[] values;

        protected int nextParamIdx;

        public Arguments(SnippetInfo info) {
            this.info = info;
            this.cacheKey = new CacheKey(info);
            this.values = new Object[info.getParameterCount()];
        }

        public Arguments add(String name, Object value) {
            assert check(name, false, false);
            values[nextParamIdx] = value;
            nextParamIdx++;
            return this;
        }

        public Arguments addConst(String name, Object value) {
            assert check(name, true, false);
            values[nextParamIdx] = value;
            cacheKey.setParam(nextParamIdx, value);
            nextParamIdx++;
            return this;
        }

        public Arguments addVarargs(String name, Class componentType, Stamp argStamp, Object value) {
            assert check(name, false, true);
            Varargs varargs = new Varargs(componentType, argStamp, value);
            values[nextParamIdx] = varargs;
            // A separate template is necessary for every distinct array length
            cacheKey.setParam(nextParamIdx, varargs.length);
            nextParamIdx++;
            return this;
        }

        private boolean check(String name, boolean constParam, boolean varargsParam) {
            assert nextParamIdx < info.getParameterCount() : "too many parameters: " + name + "  " + this;
            assert info.names[nextParamIdx].equals(name) : "wrong parameter name: " + name + "  " + this;
            assert constParam == info.isConstantParameter(nextParamIdx) : "Parameter " + (constParam ? "not " : "") + "annotated with @" + ConstantParameter.class.getSimpleName() + ": " + name +
                            "  " + this;
            assert varargsParam == info.isVarargsParameter(nextParamIdx) : "Parameter " + (varargsParam ? "not " : "") + "annotated with @" + VarargsParameter.class.getSimpleName() + ": " + name +
                            "  " + this;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("Parameters<").append(MetaUtil.format("%h.%n", info.method)).append(" [");
            String sep = "";
            for (int i = 0; i < info.getParameterCount(); i++) {
                result.append(sep);
                if (info.isConstantParameter(i)) {
                    result.append("const ");
                } else if (info.isVarargsParameter(i)) {
                    result.append("varargs ");
                }
                result.append(info.names[i]).append(" = ").append(values[i]);
                sep = ", ";
            }
            result.append(">");
            return result.toString();
        }
    }

    /**
     * Wrapper for the prototype value of a {@linkplain VarargsParameter varargs} parameter.
     */
    static class Varargs {

        protected final Class componentType;
        protected final Stamp stamp;
        protected final Object value;
        protected final int length;

        protected Varargs(Class componentType, Stamp stamp, Object value) {
            this.componentType = componentType;
            this.stamp = stamp;
            this.value = value;
            if (value instanceof List) {
                this.length = ((List) value).size();
            } else {
                this.length = Array.getLength(value);
            }
        }

        @Override
        public String toString() {
            if (value instanceof boolean[]) {
                return Arrays.toString((boolean[]) value);
            }
            if (value instanceof byte[]) {
                return Arrays.toString((byte[]) value);
            }
            if (value instanceof char[]) {
                return Arrays.toString((char[]) value);
            }
            if (value instanceof float[]) {
                return Arrays.toString((short[]) value);
            }
            if (value instanceof float[]) {
                return Arrays.toString((int[]) value);
            }
            if (value instanceof float[]) {
                return Arrays.toString((long[]) value);
            }
            if (value instanceof float[]) {
                return Arrays.toString((float[]) value);
            }
            if (value instanceof double[]) {
                return Arrays.toString((double[]) value);
            }
            if (value instanceof Object[]) {
                return Arrays.toString((Object[]) value);
            }
            return String.valueOf(value);
        }
    }

    static class CacheKey {

        private final ResolvedJavaMethod method;
        private final Object[] values;
        private int hash;

        protected CacheKey(SnippetInfo info) {
            this.method = info.method;
            this.values = new Object[info.getParameterCount()];
            this.hash = info.method.hashCode();
        }

        protected void setParam(int paramIdx, Object value) {
            values[paramIdx] = value;
            hash = (hash * 31) ^ (value == null ? 0 : value.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            if (method != other.method) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && !values[i].equals(other.values[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Base class for snippet classes. It provides a cache for {@link SnippetTemplate}s.
     */
    public abstract static class AbstractTemplates implements SnippetTemplateCache {

        protected final MetaAccessProvider runtime;
        protected final Replacements replacements;
        protected final TargetDescription target;
        private final ConcurrentHashMap<CacheKey, SnippetTemplate> templates;

        protected AbstractTemplates(MetaAccessProvider runtime, Replacements replacements, TargetDescription target) {
            this.runtime = runtime;
            this.replacements = replacements;
            this.target = target;
            this.templates = new ConcurrentHashMap<>();
        }

        /**
         * Finds the method in {@code declaringClass} annotated with {@link Snippet} named
         * {@code methodName}. If {@code methodName} is null, then there must be exactly one snippet
         * method in {@code declaringClass}.
         */
        protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName) {
            Method found = null;
            for (Method method : declaringClass.getDeclaredMethods()) {
                if (method.getAnnotation(Snippet.class) != null && (methodName == null || method.getName().equals(methodName))) {
                    assert found == null : "found more than one @" + Snippet.class.getSimpleName() + " method in " + declaringClass + (methodName == null ? "" : " named " + methodName);
                    found = method;
                }
            }
            assert found != null : "did not find @" + Snippet.class.getSimpleName() + " method in " + declaringClass + (methodName == null ? "" : " named " + methodName);
            return new SnippetInfo(runtime.lookupJavaMethod(found));
        }

        /**
         * Gets a template for a given key, creating it first if necessary.
         */
        protected SnippetTemplate template(final Arguments args) {
            SnippetTemplate template = templates.get(args.cacheKey);
            if (template == null) {
                template = Debug.scope("SnippetSpecialization", args.info.method, new Callable<SnippetTemplate>() {

                    @Override
                    public SnippetTemplate call() throws Exception {
                        return new SnippetTemplate(runtime, replacements, target, args);
                    }
                });
                templates.put(args.cacheKey, template);
            }
            return template;
        }
    }

    private static final Object UNUSED_PARAMETER = "DEAD PARAMETER";
    private static final Object CONSTANT_PARAMETER = "CONSTANT";

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
    protected SnippetTemplate(MetaAccessProvider runtime, Replacements replacements, TargetDescription target, Arguments args) {
        StructuredGraph snippetGraph = replacements.getSnippet(args.info.method);

        ResolvedJavaMethod method = snippetGraph.method();
        Signature signature = method.getSignature();

        // Copy snippet graph, replacing constant parameters with given arguments
        StructuredGraph snippetCopy = new StructuredGraph(snippetGraph.name, snippetGraph.method());
        IdentityHashMap<Node, Node> nodeReplacements = new IdentityHashMap<>();
        nodeReplacements.put(snippetGraph.start(), snippetCopy.start());

        assert checkTemplate(runtime, args, method, signature);

        int parameterCount = args.info.getParameterCount();
        ConstantNode[] placeholders = new ConstantNode[parameterCount];

        for (int i = 0; i < parameterCount; i++) {
            if (args.info.isConstantParameter(i)) {
                Object arg = args.values[i];
                Kind kind = signature.getParameterKind(i);
                Constant constantArg;
                if (arg instanceof Constant) {
                    constantArg = (Constant) arg;
                } else {
                    constantArg = Constant.forBoxed(kind, arg);
                }
                nodeReplacements.put(snippetGraph.getLocal(i), ConstantNode.forConstant(constantArg, runtime, snippetCopy));
            } else if (args.info.isVarargsParameter(i)) {
                Varargs varargs = (Varargs) args.values[i];
                Object array = Array.newInstance(varargs.componentType, varargs.length);
                ConstantNode placeholder = ConstantNode.forObject(array, runtime, snippetCopy);
                nodeReplacements.put(snippetGraph.getLocal(i), placeholder);
                placeholders[i] = placeholder;
            }
        }
        snippetCopy.addDuplicates(snippetGraph.getNodes(), nodeReplacements);

        Debug.dump(snippetCopy, "Before specialization");
        if (!nodeReplacements.isEmpty()) {
            // Do deferred intrinsification of node intrinsics
            new NodeIntrinsificationPhase(runtime).apply(snippetCopy);
            new WordTypeRewriterPhase(runtime, target.wordKind).apply(snippetCopy);

            new CanonicalizerPhase.Instance(runtime, replacements.getAssumptions(), true, 0, null).apply(snippetCopy);
        }
        NodeIntrinsificationVerificationPhase.verify(snippetCopy);

        // Gather the template parameters
        parameters = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            if (args.info.isConstantParameter(i)) {
                parameters[i] = CONSTANT_PARAMETER;
            } else if (args.info.isVarargsParameter(i)) {
                assert snippetCopy.getLocal(i) == null;
                Varargs varargs = (Varargs) args.values[i];
                int length = varargs.length;
                LocalNode[] locals = new LocalNode[length];
                Stamp stamp = varargs.stamp;
                for (int j = 0; j < length; j++) {
                    assert (parameterCount & 0xFFFF) == parameterCount;
                    int idx = i << 16 | j;
                    LocalNode local = snippetCopy.unique(new LocalNode(idx, stamp));
                    locals[j] = local;
                }
                parameters[i] = locals;

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
                LocalNode local = snippetCopy.getLocal(i);
                if (local == null) {
                    // Parameter value was eliminated
                    parameters[i] = UNUSED_PARAMETER;
                } else {
                    parameters[i] = local;
                }
            }
        }

        // Do any required loop explosion
        boolean exploded = false;
        do {
            exploded = false;
            ExplodeLoopNode explodeLoop = snippetCopy.getNodes().filter(ExplodeLoopNode.class).first();
            if (explodeLoop != null) { // Earlier canonicalization may have removed the loop
                                       // altogether
                LoopBeginNode loopBegin = explodeLoop.findLoopBegin();
                if (loopBegin != null) {
                    LoopEx loop = new LoopsData(snippetCopy).loop(loopBegin);
                    int mark = snippetCopy.getMark();
                    LoopTransformations.fullUnroll(loop, runtime, replacements.getAssumptions(), true);
                    new CanonicalizerPhase.Instance(runtime, replacements.getAssumptions(), true, mark, null).apply(snippetCopy);
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
        ArrayList<StateSplit> curSideEffectNodes = new ArrayList<>();
        ArrayList<ValueNode> curStampNodes = new ArrayList<>();
        for (Node node : snippetCopy.getNodes()) {
            if (node instanceof ValueNode && ((ValueNode) node).stamp() == StampFactory.forNodeIntrinsic()) {
                curStampNodes.add((ValueNode) node);
            }
            if (node instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) node;
                FrameState frameState = stateSplit.stateAfter();
                if (stateSplit.hasSideEffect()) {
                    curSideEffectNodes.add((StateSplit) node);
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
        this.stampNodes = curStampNodes;
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

    private static boolean checkConstantArgument(MetaAccessProvider runtime, final ResolvedJavaMethod method, Signature signature, int i, String name, Object arg, Kind kind) {
        ResolvedJavaType type = signature.getParameterType(i, method.getDeclaringClass()).resolve(method.getDeclaringClass());
        if (runtime.lookupJavaType(WordBase.class).isAssignableFrom(type)) {
            assert arg instanceof Constant : method + ": word constant parameters must be passed boxed in a Constant value: " + arg;
            return true;
        }
        if (kind == Kind.Object) {
            assert arg == null || type.isInstance(Constant.forObject(arg)) : method + ": wrong value type for " + name + ": expected " + type.getName() + ", got " + arg.getClass().getName();
        } else {
            assert arg != null && kind.toBoxedJavaClass() == arg.getClass() : method + ": wrong value kind for " + name + ": expected " + kind + ", got " +
                            (arg == null ? "null" : arg.getClass().getSimpleName());
        }
        return true;
    }

    private static boolean checkVarargs(MetaAccessProvider runtime, final ResolvedJavaMethod method, Signature signature, int i, String name, Varargs varargs) {
        ResolvedJavaType type = (ResolvedJavaType) signature.getParameterType(i, method.getDeclaringClass());
        assert type.isArray() : "varargs parameter must be an array type";
        assert type.getComponentType().isAssignableFrom(runtime.lookupJavaType(varargs.componentType)) : "componentType for " + name + " not matching " + MetaUtil.toJavaName(type) + " instance: " +
                        varargs.componentType;
        return true;
    }

    /**
     * The graph built from the snippet method.
     */
    private final StructuredGraph snippet;

    /**
     * The named parameters of this template that must be bound to values during instantiation. For
     * a parameter that is still live after specialization, the value in this map is either a
     * {@link LocalNode} instance or a {@link LocalNode} array. For an eliminated parameter, the
     * value is identical to the key.
     */
    private final Object[] parameters;

    /**
     * The return node (if any) of the snippet.
     */
    private final ReturnNode returnNode;

    /**
     * Nodes that inherit the {@link StateSplit#stateAfter()} from the replacee during
     * instantiation.
     */
    private final ArrayList<StateSplit> sideEffectNodes;

    /**
     * The nodes that inherit the {@link ValueNode#stamp()} from the replacee during instantiation.
     */
    private final ArrayList<ValueNode> stampNodes;

    /**
     * The nodes to be inlined when this specialization is instantiated.
     */
    private final ArrayList<Node> nodes;

    /**
     * Gets the instantiation-time bindings to this template's parameters.
     * 
     * @return the map that will be used to bind arguments to parameters when inlining this template
     */
    private IdentityHashMap<Node, Node> bind(StructuredGraph replaceeGraph, MetaAccessProvider runtime, Arguments args) {
        IdentityHashMap<Node, Node> replacements = new IdentityHashMap<>();
        assert args.info.getParameterCount() == parameters.length : "number of args (" + args.info.getParameterCount() + ") != number of parameters (" + parameters.length + ")";
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            assert parameter != null : this + " has no parameter named " + args.info.names[i];
            Object argument = args.values[i];
            if (parameter instanceof LocalNode) {
                if (argument instanceof ValueNode) {
                    replacements.put((LocalNode) parameter, (ValueNode) argument);
                } else {
                    Kind kind = ((LocalNode) parameter).kind();
                    assert argument != null || kind == Kind.Object : this + " cannot accept null for non-object parameter named " + args.info.names[i];
                    Constant constant = forBoxed(argument, kind);
                    replacements.put((LocalNode) parameter, ConstantNode.forConstant(constant, runtime, replaceeGraph));
                }
            } else if (parameter instanceof LocalNode[]) {
                LocalNode[] locals = (LocalNode[]) parameter;
                Varargs varargs = (Varargs) argument;
                int length = locals.length;
                List list = null;
                Object array = null;
                if (varargs.value instanceof List) {
                    list = (List) varargs.value;
                    assert list.size() == length : length + " != " + list.size();
                } else {
                    array = varargs.value;
                    assert array != null && array.getClass().isArray();
                    assert Array.getLength(array) == length : length + " != " + Array.getLength(array);
                }

                for (int j = 0; j < length; j++) {
                    LocalNode local = locals[j];
                    assert local != null;
                    Object value = list != null ? list.get(j) : Array.get(array, j);
                    if (value instanceof ValueNode) {
                        replacements.put(local, (ValueNode) value);
                    } else {
                        Constant constant = forBoxed(value, local.kind());
                        ConstantNode element = ConstantNode.forConstant(constant, runtime, replaceeGraph);
                        replacements.put(local, element);
                    }
                }
            } else {
                assert parameter == CONSTANT_PARAMETER || parameter == UNUSED_PARAMETER : "unexpected entry for parameter: " + args.info.names[i] + " -> " + parameter;
            }
        }
        return replacements;
    }

    /**
     * Converts a Java boxed value to a {@link Constant} of the right kind. This adjusts for the
     * limitation that a {@link Local}'s kind is a {@linkplain Kind#getStackKind() stack kind} and
     * so cannot be used for re-boxing primitives smaller than an int.
     * 
     * @param argument a Java boxed value
     * @param localKind the kind of the {@link Local} to which {@code argument} will be bound
     */
    protected Constant forBoxed(Object argument, Kind localKind) {
        assert localKind == localKind.getStackKind();
        if (localKind == Kind.Int && !(argument instanceof Integer)) {
            if (argument instanceof Boolean) {
                return Constant.forBoxed(Kind.Boolean, argument);
            }
            if (argument instanceof Byte) {
                return Constant.forBoxed(Kind.Byte, argument);
            }
            if (argument instanceof Short) {
                return Constant.forBoxed(Kind.Short, argument);
            }
            assert argument instanceof Character;
            return Constant.forBoxed(Kind.Char, argument);
        }
        return Constant.forBoxed(localKind, argument);
    }

    /**
     * Logic for replacing a snippet-lowered node at its usages with the return value of the
     * snippet. An alternative to the {@linkplain SnippetTemplate#DEFAULT_REPLACER default}
     * replacement logic can be used to handle mismatches between the stamp of the node being
     * lowered and the stamp of the snippet's return value.
     */
    public interface UsageReplacer {

        /**
         * Replaces all usages of {@code oldNode} with direct or indirect usages of {@code newNode}.
         */
        void replace(ValueNode oldNode, ValueNode newNode);
    }

    /**
     * Represents the default {@link UsageReplacer usage replacer} logic which simply delegates to
     * {@link Node#replaceAtUsages(Node)}.
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
    public Map<Node, Node> instantiate(MetaAccessProvider runtime, FixedNode replacee, UsageReplacer replacer, Arguments args) {

        // Inline the snippet nodes, replacing parameters with the given args in the process
        String name = snippet.name == null ? "{copy}" : snippet.name + "{copy}";
        StructuredGraph snippetCopy = new StructuredGraph(name, snippet.method());
        StartNode entryPointNode = snippet.start();
        FixedNode firstCFGNode = entryPointNode.next();
        StructuredGraph replaceeGraph = replacee.graph();
        IdentityHashMap<Node, Node> replacements = bind(replaceeGraph, runtime, args);
        Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, replacements);
        Debug.dump(replaceeGraph, "After inlining snippet %s", snippetCopy.method());

        // Re-wire the control flow graph around the replacee
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        replacee.replaceAtPredecessor(firstCFGNodeDuplicate);
        FixedNode next = null;
        if (replacee instanceof FixedWithNextNode) {
            FixedWithNextNode fwn = (FixedWithNextNode) replacee;
            next = fwn.next();
            fwn.setNext(null);
        }

        if (replacee instanceof StateSplit) {
            for (StateSplit sideEffectNode : sideEffectNodes) {
                assert ((StateSplit) replacee).hasSideEffect();
                Node sideEffectDup = duplicates.get(sideEffectNode);
                ((StateSplit) sideEffectDup).setStateAfter(((StateSplit) replacee).stateAfter());
            }
        }
        for (ValueNode stampNode : stampNodes) {
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
            Node returnDuplicate = duplicates.get(returnNode);
            if (returnValue == null && replacee.usages().isNotEmpty() && replacee instanceof MemoryCheckpoint) {
                replacer.replace(replacee, (ValueNode) returnDuplicate.predecessor());
            } else {
                assert returnValue != null || replacee.usages().isEmpty() : this + " " + returnValue + " " + returnNode + " " + replacee.usages();
                replacer.replace(replacee, returnValue);

            }
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
     * Gets a copy of the specialized graph.
     */
    public StructuredGraph copySpecializedGraph() {
        return snippet.copy();
    }

    /**
     * Replaces a given floating node with this specialized snippet.
     * 
     * @param runtime
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    public void instantiate(MetaAccessProvider runtime, FloatingNode replacee, UsageReplacer replacer, LoweringTool tool, Arguments args) {

        // Inline the snippet nodes, replacing parameters with the given args in the process
        String name = snippet.name == null ? "{copy}" : snippet.name + "{copy}";
        StructuredGraph snippetCopy = new StructuredGraph(name, snippet.method());
        StartNode entryPointNode = snippet.start();
        FixedNode firstCFGNode = entryPointNode.next();
        StructuredGraph replaceeGraph = replacee.graph();
        IdentityHashMap<Node, Node> replacements = bind(replaceeGraph, runtime, args);
        Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, replacements);
        Debug.dump(replaceeGraph, "After inlining snippet %s", snippetCopy.method());

        FixedWithNextNode lastFixedNode = tool.lastFixedNode();
        assert lastFixedNode != null && lastFixedNode.isAlive() : replaceeGraph;
        FixedNode next = lastFixedNode.next();
        lastFixedNode.setNext(null);
        FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
        replaceeGraph.addAfterFixed(lastFixedNode, firstCFGNodeDuplicate);

        if (replacee instanceof StateSplit) {
            for (StateSplit sideEffectNode : sideEffectNodes) {
                assert ((StateSplit) replacee).hasSideEffect();
                Node sideEffectDup = duplicates.get(sideEffectNode);
                ((StateSplit) sideEffectDup).setStateAfter(((StateSplit) replacee).stateAfter());
            }
        }
        for (ValueNode stampNode : stampNodes) {
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

        tool.setLastFixedNode(null);
        Node returnDuplicate = duplicates.get(returnNode);
        if (returnDuplicate.isAlive()) {
            returnDuplicate.clearInputs();
            returnDuplicate.replaceAndDelete(next);
            if (next != null && next.predecessor() instanceof FixedWithNextNode) {
                tool.setLastFixedNode((FixedWithNextNode) next.predecessor());
            }
        }

        Debug.dump(replaceeGraph, "After lowering %s with %s", replacee, this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(snippet.toString()).append('(');
        String sep = "";
        for (int i = 0; i < parameters.length; i++) {
            String name = "[" + i + "]";
            Object value = parameters[i];
            buf.append(sep);
            sep = ", ";
            if (value == null) {
                buf.append("<null> ").append(name);
            } else if (value == UNUSED_PARAMETER) {
                buf.append("<unused> ").append(name);
            } else if (value == CONSTANT_PARAMETER) {
                buf.append("<constant> ").append(name);
            } else if (value instanceof LocalNode) {
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

    private static boolean checkTemplate(MetaAccessProvider runtime, Arguments args, ResolvedJavaMethod method, Signature signature) {
        for (int i = 0; i < args.info.getParameterCount(); i++) {
            if (args.info.isConstantParameter(i)) {
                Kind kind = signature.getParameterKind(i);
                assert checkConstantArgument(runtime, method, signature, i, args.info.names[i], args.values[i], kind);

            } else if (args.info.isVarargsParameter(i)) {
                assert args.values[i] instanceof Varargs;
                Varargs varargs = (Varargs) args.values[i];
                assert checkVarargs(runtime, method, signature, i, args.info.names[i], varargs);
            }
        }
        return true;
    }
}
