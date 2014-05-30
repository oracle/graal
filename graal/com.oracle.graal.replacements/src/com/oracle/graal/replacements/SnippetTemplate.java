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

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.debug.Debug.*;
import static com.oracle.graal.graph.util.CollectionsAccess.*;
import static com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates.*;
import static java.util.FormattableFlags.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.GuardsStage;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.FloatingReadPhase.MemoryMapImpl;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.VarargsParameter;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

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
     * per snippet and then cached.
     */
    public static class SnippetInfo {

        protected final ResolvedJavaMethod method;

        /**
         * Lazily constructed parts of {@link SnippetInfo}.
         */
        static class Lazy {
            public Lazy(ResolvedJavaMethod method) {
                int count = method.getSignature().getParameterCount(false);
                constantParameters = new boolean[count];
                varargsParameters = new boolean[count];
                for (int i = 0; i < count; i++) {
                    constantParameters[i] = MetaUtil.getParameterAnnotation(ConstantParameter.class, i, method) != null;
                    varargsParameters[i] = MetaUtil.getParameterAnnotation(VarargsParameter.class, i, method) != null;

                    assert !constantParameters[i] || !varargsParameters[i] : "Parameter cannot be annotated with both @" + ConstantParameter.class.getSimpleName() + " and @" +
                                    VarargsParameter.class.getSimpleName();
                }

                // Retrieve the names only when assertions are turned on.
                assert initNames(method, count);
            }

            final boolean[] constantParameters;
            final boolean[] varargsParameters;

            /**
             * The parameter names, taken from the local variables table. Only used for assertion
             * checking, so use only within an assert statement.
             */
            String[] names;

            private boolean initNames(ResolvedJavaMethod method, int parameterCount) {
                names = new String[parameterCount];
                int slotIdx = 0;
                for (int i = 0; i < names.length; i++) {
                    names[i] = method.getLocalVariableTable().getLocal(slotIdx, 0).getName();

                    Kind kind = method.getSignature().getParameterKind(i);
                    slotIdx += kind == Kind.Long || kind == Kind.Double ? 2 : 1;
                }
                return true;
            }

        }

        protected final AtomicReference<Lazy> lazy = new AtomicReference<>(null);

        /**
         * Times instantiations of all templates derived form this snippet.
         *
         * @see SnippetTemplate#instantiationTimer
         */
        private final DebugTimer instantiationTimer;

        /**
         * Counts instantiations of all templates derived from this snippet.
         *
         * @see SnippetTemplate#instantiationCounter
         */
        private final DebugMetric instantiationCounter;

        private Lazy lazy() {
            if (lazy.get() == null) {
                lazy.compareAndSet(null, new Lazy(method));
            }
            return lazy.get();
        }

        protected SnippetInfo(ResolvedJavaMethod method) {
            this.method = method;
            instantiationCounter = Debug.metric("SnippetInstantiationCount[%s]", method);
            instantiationTimer = Debug.timer("SnippetInstantiationTime[%s]", method);
            assert method.isStatic() : "snippet method must be static: " + MetaUtil.format("%H.%n", method);
        }

        private int templateCount;

        void notifyNewTemplate() {
            templateCount++;
            if (UseSnippetTemplateCache && templateCount > MaxTemplatesPerSnippet) {
                PrintStream err = System.err;
                err.printf("WARNING: Exceeded %d templates for snippet %s%n" + "         Adjust maximum with %s system property%n", MaxTemplatesPerSnippet, format("%h.%n(%p)", method),
                                MAX_TEMPLATES_PER_SNIPPET_PROPERTY_NAME);
            }
        }

        public ResolvedJavaMethod getMethod() {
            return method;
        }

        public int getParameterCount() {
            return lazy().constantParameters.length;
        }

        public boolean isConstantParameter(int paramIdx) {
            return lazy().constantParameters[paramIdx];
        }

        public boolean isVarargsParameter(int paramIdx) {
            return lazy().varargsParameters[paramIdx];
        }

        public String getParameterName(int paramIdx) {
            String[] names = lazy().names;
            if (names != null) {
                return names[paramIdx];
            }
            return null;
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
    public static class Arguments implements Formattable {

        protected final SnippetInfo info;
        protected final CacheKey cacheKey;
        protected final Object[] values;

        protected int nextParamIdx;

        public Arguments(SnippetInfo info, GuardsStage guardsStage, LoweringTool.LoweringStage loweringStage) {
            this.info = info;
            this.cacheKey = new CacheKey(info, guardsStage, loweringStage);
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

        public Arguments addVarargs(String name, Class<?> componentType, Stamp argStamp, Object value) {
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
            assert info.getParameterName(nextParamIdx) == null || info.getParameterName(nextParamIdx).equals(name) : "wrong parameter name: " + name + "  " + this;
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
                result.append(info.getParameterName(i)).append(" = ").append(values[i]);
                sep = ", ";
            }
            result.append(">");
            return result.toString();
        }

        public void formatTo(Formatter formatter, int flags, int width, int precision) {
            if ((flags & ALTERNATE) == 0) {
                formatter.format(applyFormattingFlagsAndWidth(toString(), flags, width));
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(info.method.getName()).append('(');
                String sep = "";
                for (int i = 0; i < info.getParameterCount(); i++) {
                    if (info.isConstantParameter(i)) {
                        sb.append(sep);
                        if (info.getParameterName(i) != null) {
                            sb.append(info.getParameterName(i));
                        } else {
                            sb.append(i);
                        }
                        sb.append('=').append(values[i]);
                        sep = ", ";
                    }
                }
                sb.append(")");
                formatter.format(applyFormattingFlagsAndWidth(sb.toString(), flags & ~ALTERNATE, width));
            }
        }
    }

    /**
     * Wrapper for the prototype value of a {@linkplain VarargsParameter varargs} parameter.
     */
    static class Varargs {

        protected final Class<?> componentType;
        protected final Stamp stamp;
        protected final Object value;
        protected final int length;

        protected Varargs(Class<?> componentType, Stamp stamp, Object value) {
            this.componentType = componentType;
            this.stamp = stamp;
            this.value = value;
            if (value instanceof List) {
                this.length = ((List<?>) value).size();
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
            if (value instanceof short[]) {
                return Arrays.toString((short[]) value);
            }
            if (value instanceof int[]) {
                return Arrays.toString((int[]) value);
            }
            if (value instanceof long[]) {
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

    static class VarargsPlaceholderNode extends FloatingNode implements ArrayLengthProvider {

        final Varargs varargs;

        public VarargsPlaceholderNode(Varargs varargs, MetaAccessProvider metaAccess) {
            super(StampFactory.exactNonNull(metaAccess.lookupJavaType(varargs.componentType).getArrayClass()));
            this.varargs = varargs;
        }

        public ValueNode length() {
            return ConstantNode.forInt(varargs.length, graph());
        }
    }

    static class CacheKey {

        private final ResolvedJavaMethod method;
        private final Object[] values;
        private final GuardsStage guardsStage;
        private final LoweringTool.LoweringStage loweringStage;
        private int hash;

        protected CacheKey(SnippetInfo info, GuardsStage guardsStage, LoweringTool.LoweringStage loweringStage) {
            this.method = info.method;
            this.guardsStage = guardsStage;
            this.loweringStage = loweringStage;
            this.values = new Object[info.getParameterCount()];
            this.hash = info.method.hashCode() + 31 * guardsStage.hashCode();
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
            if (!method.equals(other.method)) {
                return false;
            }
            if (guardsStage != other.guardsStage || loweringStage != other.loweringStage) {
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

    private static final DebugTimer SnippetTemplateCreationTime = Debug.timer("SnippetTemplateCreationTime");
    private static final DebugMetric SnippetTemplates = Debug.metric("SnippetTemplateCount");

    private static final String MAX_TEMPLATES_PER_SNIPPET_PROPERTY_NAME = "graal.maxTemplatesPerSnippet";
    private static final int MaxTemplatesPerSnippet = Integer.getInteger(MAX_TEMPLATES_PER_SNIPPET_PROPERTY_NAME, 50);

    /**
     * Base class for snippet classes. It provides a cache for {@link SnippetTemplate}s.
     */
    public abstract static class AbstractTemplates implements com.oracle.graal.api.replacements.SnippetTemplateCache {

        static final boolean UseSnippetTemplateCache = Boolean.parseBoolean(System.getProperty("graal.useSnippetTemplateCache", "true"));

        protected final Providers providers;
        protected final SnippetReflectionProvider snippetReflection;
        protected final TargetDescription target;
        private final ConcurrentHashMap<CacheKey, SnippetTemplate> templates;

        protected AbstractTemplates(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
            this.providers = providers;
            this.snippetReflection = snippetReflection;
            this.target = target;
            if (UseSnippetTemplateCache) {
                this.templates = new ConcurrentHashMap<>();
            } else {
                this.templates = null;
            }
        }

        private static Method findMethod(Class<? extends Snippets> declaringClass, String methodName, Method except) {
            for (Method m : declaringClass.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && !m.equals(except)) {
                    return m;
                }
            }
            return null;
        }

        /**
         * Finds the unique method in {@code declaringClass} named {@code methodName} annotated by
         * {@link Snippet} and returns a {@link SnippetInfo} value describing it. There must be
         * exactly one snippet method in {@code declaringClass}.
         */
        protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName) {
            assert methodName != null;
            Method method = findMethod(declaringClass, methodName, null);
            assert method != null : "did not find @" + Snippet.class.getSimpleName() + " method in " + declaringClass + " named " + methodName;
            assert method.getAnnotation(Snippet.class) != null : method + " must be annotated with @" + Snippet.class.getSimpleName();
            assert findMethod(declaringClass, methodName, method) == null : "found more than one method named " + methodName + " in " + declaringClass;
            ResolvedJavaMethod javaMethod = providers.getMetaAccess().lookupJavaMethod(method);
            providers.getReplacements().registerSnippet(javaMethod);
            return new SnippetInfo(javaMethod);
        }

        /**
         * Gets a template for a given key, creating it first if necessary.
         */
        protected SnippetTemplate template(final Arguments args) {
            SnippetTemplate template = UseSnippetTemplateCache ? templates.get(args.cacheKey) : null;
            if (template == null) {
                SnippetTemplates.increment();
                try (TimerCloseable a = SnippetTemplateCreationTime.start(); Scope s = Debug.scope("SnippetSpecialization", args.info.method)) {
                    template = new SnippetTemplate(providers, snippetReflection, args);
                    if (UseSnippetTemplateCache) {
                        templates.put(args.cacheKey, template);
                    }
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
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

    private final SnippetReflectionProvider snippetReflection;

    /**
     * Creates a snippet template.
     */
    protected SnippetTemplate(final Providers providers, SnippetReflectionProvider snippetReflection, Arguments args) {
        this.snippetReflection = snippetReflection;

        StructuredGraph snippetGraph = providers.getReplacements().getSnippet(args.info.method);
        instantiationTimer = Debug.timer("SnippetTemplateInstantiationTime[%#s]", args);
        instantiationCounter = Debug.metric("SnippetTemplateInstantiationCount[%#s]", args);

        ResolvedJavaMethod method = snippetGraph.method();
        Signature signature = method.getSignature();

        PhaseContext phaseContext = new PhaseContext(providers, new Assumptions(false));

        // Copy snippet graph, replacing constant parameters with given arguments
        final StructuredGraph snippetCopy = new StructuredGraph(snippetGraph.name, snippetGraph.method());
        Map<Node, Node> nodeReplacements = newNodeIdentityMap();
        nodeReplacements.put(snippetGraph.start(), snippetCopy.start());

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        assert checkTemplate(metaAccess, args, method, signature);

        int parameterCount = args.info.getParameterCount();
        VarargsPlaceholderNode[] placeholders = new VarargsPlaceholderNode[parameterCount];

        for (int i = 0; i < parameterCount; i++) {
            if (args.info.isConstantParameter(i)) {
                Object arg = args.values[i];
                Kind kind = signature.getParameterKind(i);
                Constant constantArg;
                if (arg instanceof Constant) {
                    constantArg = (Constant) arg;
                } else {
                    constantArg = snippetReflection.forBoxed(kind, arg);
                }
                nodeReplacements.put(snippetGraph.getParameter(i), ConstantNode.forConstant(constantArg, metaAccess, snippetCopy));
            } else if (args.info.isVarargsParameter(i)) {
                Varargs varargs = (Varargs) args.values[i];
                VarargsPlaceholderNode placeholder = snippetCopy.unique(new VarargsPlaceholderNode(varargs, providers.getMetaAccess()));
                nodeReplacements.put(snippetGraph.getParameter(i), placeholder);
                placeholders[i] = placeholder;
            }
        }
        snippetCopy.addDuplicates(snippetGraph.getNodes(), snippetGraph, snippetGraph.getNodeCount(), nodeReplacements);

        Debug.dump(snippetCopy, "Before specialization");
        if (!nodeReplacements.isEmpty()) {
            providers.getReplacements().notifyAfterConstantsBound(snippetCopy);
        }

        // Gather the template parameters
        parameters = new Object[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            if (args.info.isConstantParameter(i)) {
                parameters[i] = CONSTANT_PARAMETER;
            } else if (args.info.isVarargsParameter(i)) {
                assert snippetCopy.getParameter(i) == null;
                Varargs varargs = (Varargs) args.values[i];
                int length = varargs.length;
                ParameterNode[] params = new ParameterNode[length];
                Stamp stamp = varargs.stamp;
                for (int j = 0; j < length; j++) {
                    // Use a decimal friendly numbering make it more obvious how values map
                    assert parameterCount < 10000;
                    int idx = (i + 1) * 10000 + j;
                    assert idx >= parameterCount : "collision in parameter numbering";
                    ParameterNode local = snippetCopy.unique(new ParameterNode(idx, stamp));
                    params[j] = local;
                }
                parameters[i] = params;

                VarargsPlaceholderNode placeholder = placeholders[i];
                assert placeholder != null;
                for (Node usage : placeholder.usages().snapshot()) {
                    if (usage instanceof LoadIndexedNode) {
                        LoadIndexedNode loadIndexed = (LoadIndexedNode) usage;
                        Debug.dump(snippetCopy, "Before replacing %s", loadIndexed);
                        LoadSnippetVarargParameterNode loadSnippetParameter = snippetCopy.add(new LoadSnippetVarargParameterNode(params, loadIndexed.index(), loadIndexed.stamp()));
                        snippetCopy.replaceFixedWithFixed(loadIndexed, loadSnippetParameter);
                        Debug.dump(snippetCopy, "After replacing %s", loadIndexed);
                    } else if (usage instanceof StoreIndexedNode) {
                        // The template lowering doesn't really treat this as an array so you can't
                        // store back into the varargs. Allocate your own array if you really need
                        // this and EA should eliminate it.
                        throw new GraalInternalError("Can't store into VarargsParameter array");
                    }
                }
            } else {
                ParameterNode local = snippetCopy.getParameter(i);
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
                    Mark mark = snippetCopy.getMark();
                    LoopTransformations.fullUnroll(loop, phaseContext, new CanonicalizerPhase(true));
                    new CanonicalizerPhase(true).applyIncremental(snippetCopy, phaseContext, mark);
                }
                GraphUtil.removeFixedWithUnusedInputs(explodeLoop);
                exploded = true;
            }
        } while (exploded);

        GuardsStage guardsStage = args.cacheKey.guardsStage;
        // Perform lowering on the snippet
        if (guardsStage.ordinal() >= GuardsStage.FIXED_DEOPTS.ordinal()) {
            new GuardLoweringPhase().apply(snippetCopy, null);
        }
        snippetCopy.setGuardsStage(guardsStage);
        try (Scope s = Debug.scope("LoweringSnippetTemplate", snippetCopy)) {
            new LoweringPhase(new CanonicalizerPhase(true), args.cacheKey.loweringStage).apply(snippetCopy, phaseContext);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        // Remove all frame states from snippet graph. Snippets must be atomic (i.e. free
        // of side-effects that prevent deoptimizing to a point before the snippet).
        ArrayList<StateSplit> curSideEffectNodes = new ArrayList<>();
        ArrayList<DeoptimizingNode> curDeoptNodes = new ArrayList<>();
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
            if (node instanceof DeoptimizingNode) {
                DeoptimizingNode deoptNode = (DeoptimizingNode) node;
                if (deoptNode.canDeoptimize()) {
                    curDeoptNodes.add(deoptNode);
                }
            }
        }

        new DeadCodeEliminationPhase().apply(snippetCopy);

        assert checkAllVarargPlaceholdersAreDeleted(parameterCount, placeholders);

        new FloatingReadPhase(FloatingReadPhase.ExecutionMode.ANALYSIS_ONLY).apply(snippetCopy);

        MemoryAnchorNode memoryAnchor = snippetCopy.add(new MemoryAnchorNode());
        snippetCopy.start().replaceAtUsages(InputType.Memory, memoryAnchor);

        this.snippet = snippetCopy;

        Debug.dump(snippet, "SnippetTemplate after fixing memory anchoring");

        List<ReturnNode> returnNodes = new ArrayList<>(4);
        List<MemoryMapNode> memMaps = new ArrayList<>(4);
        StartNode entryPointNode = snippet.start();
        boolean anchorUsed = false;
        for (ReturnNode retNode : snippet.getNodes(ReturnNode.class)) {
            MemoryMapNode memMap = retNode.getMemoryMap();
            anchorUsed |= memMap.replaceLastLocationAccess(snippetCopy.start(), memoryAnchor);
            memMaps.add(memMap);
            retNode.setMemoryMap(null);
            returnNodes.add(retNode);
            if (memMap.usages().isEmpty()) {
                memMap.safeDelete();
            }
        }
        if (memoryAnchor.usages().isEmpty() && !anchorUsed) {
            memoryAnchor.safeDelete();
        } else {
            snippetCopy.addAfterFixed(snippetCopy.start(), memoryAnchor);
        }
        assert snippet.getNodes().filter(MemoryMapNode.class).isEmpty();
        if (returnNodes.isEmpty()) {
            this.returnNode = null;
            this.memoryMap = null;
        } else if (returnNodes.size() == 1) {
            this.returnNode = returnNodes.get(0);
            this.memoryMap = memMaps.get(0);
        } else {
            MergeNode merge = snippet.add(new MergeNode());
            ValueNode returnValue = InliningUtil.mergeReturns(merge, returnNodes);
            this.returnNode = snippet.add(new ReturnNode(returnValue));
            this.memoryMap = FloatingReadPhase.mergeMemoryMaps(merge, memMaps);
            merge.setNext(this.returnNode);
        }

        this.sideEffectNodes = curSideEffectNodes;
        this.deoptNodes = curDeoptNodes;
        this.stampNodes = curStampNodes;

        nodes = new ArrayList<>(snippet.getNodeCount());
        for (Node node : snippet.getNodes()) {
            if (node != entryPointNode && node != entryPointNode.stateAfter()) {
                nodes.add(node);
            }
        }

        Debug.metric("SnippetTemplateNodeCount[%#s]", args).add(nodes.size());
        args.info.notifyNewTemplate();
        Debug.dump(snippet, "SnippetTemplate final state");
    }

    private static boolean checkAllVarargPlaceholdersAreDeleted(int parameterCount, VarargsPlaceholderNode[] placeholders) {
        for (int i = 0; i < parameterCount; i++) {
            if (placeholders[i] != null) {
                assert placeholders[i].isDeleted() : placeholders[i];
            }
        }
        return true;
    }

    private static boolean checkConstantArgument(MetaAccessProvider metaAccess, final ResolvedJavaMethod method, Signature signature, int i, String name, Object arg, Kind kind) {
        ResolvedJavaType type = signature.getParameterType(i, method.getDeclaringClass()).resolve(method.getDeclaringClass());
        if (metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(type)) {
            assert arg instanceof Constant : method + ": word constant parameters must be passed boxed in a Constant value: " + arg;
            return true;
        }
        if (kind != Kind.Object) {
            assert arg != null && kind.toBoxedJavaClass() == arg.getClass() : method + ": wrong value kind for " + name + ": expected " + kind + ", got " +
                            (arg == null ? "null" : arg.getClass().getSimpleName());
        }
        return true;
    }

    private static boolean checkVarargs(MetaAccessProvider metaAccess, final ResolvedJavaMethod method, Signature signature, int i, String name, Varargs varargs) {
        ResolvedJavaType type = (ResolvedJavaType) signature.getParameterType(i, method.getDeclaringClass());
        assert type.isArray() : "varargs parameter must be an array type";
        assert type.getComponentType().isAssignableFrom(metaAccess.lookupJavaType(varargs.componentType)) : "componentType for " + name + " not matching " + MetaUtil.toJavaName(type) + " instance: " +
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
     * {@link ParameterNode} instance or a {@link ParameterNode} array. For an eliminated parameter,
     * the value is identical to the key.
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
     * Nodes that inherit a deoptimization {@link FrameState} from the replacee during
     * instantiation.
     */
    private final ArrayList<DeoptimizingNode> deoptNodes;

    /**
     * The nodes that inherit the {@link ValueNode#stamp()} from the replacee during instantiation.
     */
    private final ArrayList<ValueNode> stampNodes;

    /**
     * The nodes to be inlined when this specialization is instantiated.
     */
    private final ArrayList<Node> nodes;

    /**
     * map of killing locations to memory checkpoints (nodes).
     */
    private final MemoryMapNode memoryMap;

    /**
     * Times instantiations of this template.
     *
     * @see SnippetInfo#instantiationTimer
     */
    private final DebugTimer instantiationTimer;

    /**
     * Counts instantiations of this template.
     *
     * @see SnippetInfo#instantiationCounter
     */
    private final DebugMetric instantiationCounter;

    /**
     * Gets the instantiation-time bindings to this template's parameters.
     *
     * @return the map that will be used to bind arguments to parameters when inlining this template
     */
    private Map<Node, Node> bind(StructuredGraph replaceeGraph, MetaAccessProvider metaAccess, Arguments args) {
        Map<Node, Node> replacements = newNodeIdentityMap();
        assert args.info.getParameterCount() == parameters.length : "number of args (" + args.info.getParameterCount() + ") != number of parameters (" + parameters.length + ")";
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            assert parameter != null : this + " has no parameter named " + args.info.getParameterName(i);
            Object argument = args.values[i];
            if (parameter instanceof ParameterNode) {
                if (argument instanceof ValueNode) {
                    replacements.put((ParameterNode) parameter, (ValueNode) argument);
                } else {
                    Kind kind = ((ParameterNode) parameter).getKind();
                    assert argument != null || kind == Kind.Object : this + " cannot accept null for non-object parameter named " + args.info.getParameterName(i);
                    Constant constant = forBoxed(argument, kind);
                    replacements.put((ParameterNode) parameter, ConstantNode.forConstant(constant, metaAccess, replaceeGraph));
                }
            } else if (parameter instanceof ParameterNode[]) {
                ParameterNode[] params = (ParameterNode[]) parameter;
                Varargs varargs = (Varargs) argument;
                int length = params.length;
                List<?> list = null;
                Object array = null;
                if (varargs.value instanceof List) {
                    list = (List<?>) varargs.value;
                    assert list.size() == length : length + " != " + list.size();
                } else {
                    array = varargs.value;
                    assert array != null && array.getClass().isArray();
                    assert Array.getLength(array) == length : length + " != " + Array.getLength(array);
                }

                for (int j = 0; j < length; j++) {
                    ParameterNode param = params[j];
                    assert param != null;
                    Object value = list != null ? list.get(j) : Array.get(array, j);
                    if (value instanceof ValueNode) {
                        replacements.put(param, (ValueNode) value);
                    } else {
                        Constant constant = forBoxed(value, param.getKind());
                        ConstantNode element = ConstantNode.forConstant(constant, metaAccess, replaceeGraph);
                        replacements.put(param, element);
                    }
                }
            } else {
                assert parameter == CONSTANT_PARAMETER || parameter == UNUSED_PARAMETER : "unexpected entry for parameter: " + args.info.getParameterName(i) + " -> " + parameter;
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
        if (localKind == Kind.Int) {
            return Constant.forBoxedPrimitive(argument);
        }
        return snippetReflection.forBoxed(localKind, argument);
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
        void replace(ValueNode oldNode, ValueNode newNode, MemoryMapNode mmap);
    }

    /**
     * Represents the default {@link UsageReplacer usage replacer} logic which simply delegates to
     * {@link Node#replaceAtUsages(Node)}.
     */
    public static final UsageReplacer DEFAULT_REPLACER = new UsageReplacer() {

        private LocationIdentity getLocationIdentity(Node node) {
            if (node instanceof MemoryAccess) {
                return ((MemoryAccess) node).getLocationIdentity();
            } else if (node instanceof MemoryProxy) {
                return ((MemoryProxy) node).getLocationIdentity();
            } else if (node instanceof MemoryPhiNode) {
                return ((MemoryPhiNode) node).getLocationIdentity();
            } else {
                return null;
            }
        }

        @Override
        public void replace(ValueNode oldNode, ValueNode newNode, MemoryMapNode mmap) {
            if (mmap != null) {
                for (Node usage : oldNode.usages().snapshot()) {
                    LocationIdentity identity = getLocationIdentity(usage);
                    boolean usageReplaced = false;
                    if (identity != null && identity != FINAL_LOCATION) {
                        // lastLocationAccess points into the snippet graph. find a proper
                        // MemoryCheckPoint inside the snippet graph
                        MemoryNode lastAccess = mmap.getLastLocationAccess(identity);

                        assert lastAccess != null : "no mapping found for lowerable node " + oldNode + ". (No node in the snippet kills the same location as the lowerable node?)";
                        if (usage instanceof MemoryAccess) {
                            MemoryAccess access = (MemoryAccess) usage;
                            if (access.getLastLocationAccess() == oldNode) {
                                assert oldNode.graph().isAfterFloatingReadPhase();
                                access.setLastLocationAccess(lastAccess);
                                usageReplaced = true;
                            }
                        } else {
                            assert usage instanceof MemoryProxy || usage instanceof MemoryPhiNode;
                            usage.replaceFirstInput(oldNode, lastAccess.asNode());
                            usageReplaced = true;
                        }
                    }
                    if (!usageReplaced) {
                        assert newNode != null : "this branch is only valid if we have a newNode for replacement";
                    }
                }
            }
            if (newNode == null) {
                assert oldNode.usages().isEmpty();
            } else {
                oldNode.replaceAtUsages(newNode);
            }
        }
    };

    private boolean checkSnippetKills(ScheduledNode replacee) {
        if (!replacee.graph().isAfterFloatingReadPhase()) {
            // no floating reads yet, ignore locations created while lowering
            return true;
        }
        if (memoryMap == null || ((MemoryMapImpl) memoryMap).isEmpty()) {
            // there're no kills in the snippet graph
            return true;
        }

        Set<LocationIdentity> kills = new HashSet<>(((MemoryMapImpl) memoryMap).getLocations());

        if (replacee instanceof MemoryCheckpoint.Single) {
            // check if some node in snippet graph also kills the same location
            LocationIdentity locationIdentity = ((MemoryCheckpoint.Single) replacee).getLocationIdentity();
            if (locationIdentity == ANY_LOCATION) {
                assert !(memoryMap.getLastLocationAccess(ANY_LOCATION) instanceof MemoryAnchorNode) : replacee + " kills ANY_LOCATION, but snippet does not";
            }
            assert kills.contains(locationIdentity) : replacee + " kills " + locationIdentity + ", but snippet doesn't contain a kill to this location";
            return true;
        }
        assert !(replacee instanceof MemoryCheckpoint.Multi) : replacee + " multi not supported (yet)";

        Debug.log("WARNING: %s is not a MemoryCheckpoint, but the snippet graph contains kills (%s). You might want %s to be a MemoryCheckpoint", replacee, kills, replacee);

        // remove ANY_LOCATION if it's just a kill by the start node
        if (memoryMap.getLastLocationAccess(ANY_LOCATION) instanceof MemoryAnchorNode) {
            kills.remove(ANY_LOCATION);
        }

        // node can only lower to a ANY_LOCATION kill if the replacee also kills ANY_LOCATION
        assert !kills.contains(ANY_LOCATION) : "snippet graph contains a kill to ANY_LOCATION, but replacee (" + replacee + ") doesn't kill ANY_LOCATION.  kills: " + kills;

        /*
         * kills to other locations than ANY_LOCATION can be still inserted if there aren't any
         * floating reads accessing this locations. Example: In HotSpot, InstanceOfNode is lowered
         * to a snippet containing a write to SECONDARY_SUPER_CACHE_LOCATION. This is runtime
         * specific, so the runtime independent InstanceOfNode can not kill this location. However,
         * if no FloatingReadNode is reading from this location, the kill to this location is fine.
         */
        for (FloatingReadNode frn : replacee.graph().getNodes(FloatingReadNode.class)) {
            LocationIdentity locationIdentity = frn.location().getLocationIdentity();
            if (SnippetCounters.getValue()) {
                // accesses to snippet counters are artificially introduced and violate the memory
                // semantics.
                if (locationIdentity == SnippetCounter.SNIPPET_COUNTER_LOCATION) {
                    continue;
                }
            }
            assert !kills.contains(locationIdentity) : frn + " reads from location \"" + locationIdentity + "\" but " + replacee + " does not kill this location";
        }
        return true;
    }

    private class DuplicateMapper extends MemoryMapNode {

        private final Map<Node, Node> duplicates;
        @Input private StartNode replaceeStart;

        public DuplicateMapper(Map<Node, Node> duplicates, StartNode replaceeStart) {
            this.duplicates = duplicates;
            this.replaceeStart = replaceeStart;
        }

        @Override
        public MemoryNode getLastLocationAccess(LocationIdentity locationIdentity) {
            assert memoryMap != null : "no memory map stored for this snippet graph (snippet doesn't have a ReturnNode?)";
            MemoryNode lastLocationAccess = memoryMap.getLastLocationAccess(locationIdentity);
            assert lastLocationAccess != null;
            if (lastLocationAccess instanceof StartNode) {
                return replaceeStart;
            } else {
                return (MemoryNode) duplicates.get(ValueNodeUtil.asNode(lastLocationAccess));
            }
        }

        @Override
        public Set<LocationIdentity> getLocations() {
            return memoryMap.getLocations();
        }

        @Override
        public boolean replaceLastLocationAccess(MemoryNode oldNode, MemoryNode newNode) {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    /**
     * Replaces a given fixed node with this specialized snippet.
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     * @return the map of duplicated nodes (original -&gt; duplicate)
     */
    public Map<Node, Node> instantiate(MetaAccessProvider metaAccess, FixedNode replacee, UsageReplacer replacer, Arguments args) {
        assert checkSnippetKills(replacee);
        try (TimerCloseable a = args.info.instantiationTimer.start(); TimerCloseable b = instantiationTimer.start()) {
            args.info.instantiationCounter.increment();
            instantiationCounter.increment();
            // Inline the snippet nodes, replacing parameters with the given args in the process
            StartNode entryPointNode = snippet.start();
            FixedNode firstCFGNode = entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            Map<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            replacements.put(entryPointNode, BeginNode.prevBegin(replacee));
            Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, snippet, snippet.getNodeCount(), replacements);
            Debug.dump(replaceeGraph, "After inlining snippet %s", snippet.method());

            // Re-wire the control flow graph around the replacee
            FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
            replacee.replaceAtPredecessor(firstCFGNodeDuplicate);

            if (replacee instanceof StateSplit) {
                for (StateSplit sideEffectNode : sideEffectNodes) {
                    assert ((StateSplit) replacee).hasSideEffect();
                    Node sideEffectDup = duplicates.get(sideEffectNode);
                    ((StateSplit) sideEffectDup).setStateAfter(((StateSplit) replacee).stateAfter());
                }
            }

            if (replacee instanceof DeoptimizingNode) {
                DeoptimizingNode replaceeDeopt = (DeoptimizingNode) replacee;

                FrameState stateBefore = null;
                FrameState stateDuring = null;
                FrameState stateAfter = null;
                if (replaceeDeopt.canDeoptimize()) {
                    if (replaceeDeopt instanceof DeoptimizingNode.DeoptBefore) {
                        stateBefore = ((DeoptimizingNode.DeoptBefore) replaceeDeopt).stateBefore();
                    }
                    if (replaceeDeopt instanceof DeoptimizingNode.DeoptDuring) {
                        stateDuring = ((DeoptimizingNode.DeoptDuring) replaceeDeopt).stateDuring();
                    }
                    if (replaceeDeopt instanceof DeoptimizingNode.DeoptAfter) {
                        stateAfter = ((DeoptimizingNode.DeoptAfter) replaceeDeopt).stateAfter();
                    }
                }

                for (DeoptimizingNode deoptNode : deoptNodes) {
                    DeoptimizingNode deoptDup = (DeoptimizingNode) duplicates.get(deoptNode);
                    if (deoptDup.canDeoptimize()) {
                        if (deoptDup instanceof DeoptimizingNode.DeoptBefore) {
                            ((DeoptimizingNode.DeoptBefore) deoptDup).setStateBefore(stateBefore);
                        }
                        if (deoptDup instanceof DeoptimizingNode.DeoptDuring) {
                            DeoptimizingNode.DeoptDuring deoptDupDuring = (DeoptimizingNode.DeoptDuring) deoptDup;
                            if (stateDuring != null) {
                                deoptDupDuring.setStateDuring(stateDuring);
                            } else if (stateAfter != null) {
                                deoptDupDuring.computeStateDuring(stateAfter);
                            } else if (stateBefore != null) {
                                assert !deoptDupDuring.hasSideEffect() : "can't use stateBefore as stateDuring for state split " + deoptDupDuring;
                                deoptDupDuring.setStateDuring(stateBefore);
                            }
                        }
                        if (deoptDup instanceof DeoptimizingNode.DeoptAfter) {
                            DeoptimizingNode.DeoptAfter deoptDupAfter = (DeoptimizingNode.DeoptAfter) deoptDup;
                            if (stateAfter != null) {
                                deoptDupAfter.setStateAfter(stateAfter);
                            } else {
                                assert !deoptDupAfter.hasSideEffect() : "can't use stateBefore as stateAfter for state split " + deoptDupAfter;
                                deoptDupAfter.setStateAfter(stateBefore);
                            }

                        }
                    }
                }
            }

            updateStamps(replacee, duplicates);

            // Replace all usages of the replacee with the value returned by the snippet
            ValueNode returnValue = null;
            if (returnNode != null && !(replacee instanceof ControlSinkNode)) {
                ReturnNode returnDuplicate = (ReturnNode) duplicates.get(returnNode);
                returnValue = returnDuplicate.result();
                MemoryMapNode mmap = new DuplicateMapper(duplicates, replaceeGraph.start());
                if (returnValue == null && replacee.usages().isNotEmpty() && replacee instanceof MemoryCheckpoint) {
                    replacer.replace(replacee, null, mmap);
                } else {
                    assert returnValue != null || replacee.usages().isEmpty();
                    replacer.replace(replacee, returnValue, mmap);
                }
                if (returnDuplicate.isAlive()) {
                    FixedNode next = null;
                    if (replacee instanceof FixedWithNextNode) {
                        FixedWithNextNode fwn = (FixedWithNextNode) replacee;
                        next = fwn.next();
                        fwn.setNext(null);
                    }
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
    }

    private void propagateStamp(Node node) {
        if (node instanceof PhiNode) {
            PhiNode phi = (PhiNode) node;
            if (phi.inferStamp()) {
                for (Node usage : node.usages()) {
                    propagateStamp(usage);
                }
            }
        }
    }

    private void updateStamps(ValueNode replacee, Map<Node, Node> duplicates) {
        for (ValueNode stampNode : stampNodes) {
            Node stampDup = duplicates.get(stampNode);
            ((ValueNode) stampDup).setStamp(replacee.stamp());
        }
        for (ParameterNode paramNode : snippet.getNodes(ParameterNode.class)) {
            for (Node usage : paramNode.usages()) {
                Node usageDup = duplicates.get(usage);
                propagateStamp(usageDup);
            }
        }
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
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    public void instantiate(MetaAccessProvider metaAccess, FloatingNode replacee, UsageReplacer replacer, LoweringTool tool, Arguments args) {
        assert checkSnippetKills(replacee);
        try (TimerCloseable a = args.info.instantiationTimer.start()) {
            args.info.instantiationCounter.increment();
            instantiationCounter.increment();

            // Inline the snippet nodes, replacing parameters with the given args in the process
            String name = snippet.name == null ? "{copy}" : snippet.name + "{copy}";
            StructuredGraph snippetCopy = new StructuredGraph(name, snippet.method());
            StartNode entryPointNode = snippet.start();
            FixedNode firstCFGNode = entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            Map<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            replacements.put(entryPointNode, tool.getCurrentGuardAnchor().asNode());
            Map<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, snippet, snippet.getNodeCount(), replacements);
            Debug.dump(replaceeGraph, "After inlining snippet %s", snippetCopy.method());

            FixedWithNextNode lastFixedNode = tool.lastFixedNode();
            assert lastFixedNode != null && lastFixedNode.isAlive() : replaceeGraph + " lastFixed=" + lastFixedNode;
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
            updateStamps(replacee, duplicates);

            // Replace all usages of the replacee with the value returned by the snippet
            ReturnNode returnDuplicate = (ReturnNode) duplicates.get(returnNode);
            ValueNode returnValue = returnDuplicate.result();
            assert returnValue != null || replacee.usages().isEmpty();
            replacer.replace(replacee, returnValue, new DuplicateMapper(duplicates, replaceeGraph.start()));

            if (returnDuplicate.isAlive()) {
                returnDuplicate.clearInputs();
                returnDuplicate.replaceAndDelete(next);
            }

            Debug.dump(replaceeGraph, "After lowering %s with %s", replacee, this);
        }
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
            } else if (value instanceof ParameterNode) {
                ParameterNode param = (ParameterNode) value;
                buf.append(param.getKind().getJavaName()).append(' ').append(name);
            } else {
                ParameterNode[] params = (ParameterNode[]) value;
                String kind = params.length == 0 ? "?" : params[0].getKind().getJavaName();
                buf.append(kind).append('[').append(params.length).append("] ").append(name);
            }
        }
        return buf.append(')').toString();
    }

    private static boolean checkTemplate(MetaAccessProvider metaAccess, Arguments args, ResolvedJavaMethod method, Signature signature) {
        for (int i = 0; i < args.info.getParameterCount(); i++) {
            if (args.info.isConstantParameter(i)) {
                Kind kind = signature.getParameterKind(i);
                assert checkConstantArgument(metaAccess, method, signature, i, args.info.getParameterName(i), args.values[i], kind);

            } else if (args.info.isVarargsParameter(i)) {
                assert args.values[i] instanceof Varargs;
                Varargs varargs = (Varargs) args.values[i];
                assert checkVarargs(metaAccess, method, signature, i, args.info.getParameterName(i), varargs);
            }
        }
        return true;
    }
}
