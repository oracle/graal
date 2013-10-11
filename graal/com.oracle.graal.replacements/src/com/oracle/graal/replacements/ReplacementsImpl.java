/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.compiler.GraalCompiler.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.Snippet.DefaultSnippetInliningPolicy;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.word.phases.*;

public class ReplacementsImpl implements Replacements {

    protected final Providers providers;
    protected final TargetDescription target;
    protected final Assumptions assumptions;

    /**
     * The preprocessed replacement graphs.
     */
    private final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphs;

    // These data structures are all fully initialized during single-threaded
    // compiler startup and so do not need to be concurrent.
    private final Map<ResolvedJavaMethod, ResolvedJavaMethod> registeredMethodSubstitutions;
    private final Map<ResolvedJavaMethod, Class<? extends FixedWithNextNode>> registeredMacroSubstitutions;
    private final Set<ResolvedJavaMethod> forcedSubstitutions;
    private final Map<Class<? extends SnippetTemplateCache>, SnippetTemplateCache> snippetTemplateCache;

    public ReplacementsImpl(MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection, CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, LoweringProvider lowerer,
                    Assumptions assumptions, TargetDescription target) {
        this.providers = new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, this);
        this.target = target;
        this.assumptions = assumptions;
        this.graphs = new ConcurrentHashMap<>();
        this.registeredMethodSubstitutions = new HashMap<>();
        this.registeredMacroSubstitutions = new HashMap<>();
        this.forcedSubstitutions = new HashSet<>();
        this.snippetTemplateCache = new HashMap<>();
    }

    public StructuredGraph getSnippet(ResolvedJavaMethod method) {
        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        assert !Modifier.isAbstract(method.getModifiers()) && !Modifier.isNative(method.getModifiers()) : "Snippet must not be abstract or native";

        StructuredGraph graph = graphs.get(method);
        if (graph == null) {
            graphs.putIfAbsent(method, makeGraph(method, null, inliningPolicy(method), method.getAnnotation(Snippet.class).removeAllFrameStates()));
            graph = graphs.get(method);
        }
        return graph;
    }

    public StructuredGraph getMethodSubstitution(ResolvedJavaMethod original) {
        ResolvedJavaMethod substitute = registeredMethodSubstitutions.get(original);
        if (substitute == null) {
            return null;
        }
        StructuredGraph graph = graphs.get(substitute);
        if (graph == null) {
            graphs.putIfAbsent(substitute, makeGraph(substitute, original, inliningPolicy(substitute), false));
            graph = graphs.get(substitute);
        }
        return graph;

    }

    public Class<? extends FixedWithNextNode> getMacroSubstitution(ResolvedJavaMethod method) {
        return registeredMacroSubstitutions.get(method);
    }

    public Assumptions getAssumptions() {
        return assumptions;
    }

    public void registerSubstitutions(Class<?> substitutions) {
        ClassSubstitution classSubstitution = substitutions.getAnnotation(ClassSubstitution.class);
        assert classSubstitution != null;
        assert !Snippets.class.isAssignableFrom(substitutions);
        for (Method substituteMethod : substitutions.getDeclaredMethods()) {
            MethodSubstitution methodSubstitution = substituteMethod.getAnnotation(MethodSubstitution.class);
            MacroSubstitution macroSubstitution = substituteMethod.getAnnotation(MacroSubstitution.class);
            if (methodSubstitution == null && macroSubstitution == null) {
                continue;
            }

            int modifiers = substituteMethod.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                throw new GraalInternalError("Substitution methods must be static: " + substituteMethod);
            }

            if (methodSubstitution != null) {
                if (macroSubstitution != null && macroSubstitution.isStatic() != methodSubstitution.isStatic()) {
                    throw new GraalInternalError("Macro and method substitution must agree on isStatic attribute: " + substituteMethod);
                }
                if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                    throw new GraalInternalError("Substitution method must not be abstract or native: " + substituteMethod);
                }
                String originalName = originalName(substituteMethod, methodSubstitution.value());
                Class[] originalParameters = originalParameters(substituteMethod, methodSubstitution.signature(), methodSubstitution.isStatic());
                Member originalMethod = originalMethod(classSubstitution, methodSubstitution.optional(), originalName, originalParameters);
                if (originalMethod != null) {
                    ResolvedJavaMethod original = registerMethodSubstitution(originalMethod, substituteMethod);
                    if (original != null && methodSubstitution.forced() && shouldIntrinsify(original)) {
                        forcedSubstitutions.add(original);
                    }
                }
            }
            if (macroSubstitution != null) {
                String originalName = originalName(substituteMethod, macroSubstitution.value());
                Class[] originalParameters = originalParameters(substituteMethod, macroSubstitution.signature(), macroSubstitution.isStatic());
                Member originalMethod = originalMethod(classSubstitution, macroSubstitution.optional(), originalName, originalParameters);
                if (originalMethod != null) {
                    ResolvedJavaMethod original = registerMacroSubstitution(originalMethod, macroSubstitution.macro());
                    if (original != null && macroSubstitution.forced() && shouldIntrinsify(original)) {
                        forcedSubstitutions.add(original);
                    }
                }
            }
        }
    }

    /**
     * Registers a method substitution.
     * 
     * @param originalMember a method or constructor being substituted
     * @param substituteMethod the substitute method
     * @return the original method
     */
    protected ResolvedJavaMethod registerMethodSubstitution(Member originalMember, Method substituteMethod) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod substitute = metaAccess.lookupJavaMethod(substituteMethod);
        ResolvedJavaMethod original;
        if (originalMember instanceof Method) {
            original = metaAccess.lookupJavaMethod((Method) originalMember);
        } else {
            original = metaAccess.lookupJavaConstructor((Constructor) originalMember);
        }
        Debug.log("substitution: " + MetaUtil.format("%H.%n(%p)", original) + " --> " + MetaUtil.format("%H.%n(%p)", substitute));

        registeredMethodSubstitutions.put(original, substitute);
        return original;
    }

    /**
     * Registers a macro substitution.
     * 
     * @param originalMethod a method or constructor being substituted
     * @param macro the substitute macro node class
     * @return the original method
     */
    protected ResolvedJavaMethod registerMacroSubstitution(Member originalMethod, Class<? extends FixedWithNextNode> macro) {
        ResolvedJavaMethod originalJavaMethod;
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        if (originalMethod instanceof Method) {
            originalJavaMethod = metaAccess.lookupJavaMethod((Method) originalMethod);
        } else {
            originalJavaMethod = metaAccess.lookupJavaConstructor((Constructor) originalMethod);
        }
        registeredMacroSubstitutions.put(originalJavaMethod, macro);
        return originalJavaMethod;
    }

    private SnippetInliningPolicy inliningPolicy(ResolvedJavaMethod method) {
        Class<? extends SnippetInliningPolicy> policyClass = SnippetInliningPolicy.class;
        Snippet snippet = method.getAnnotation(Snippet.class);
        if (snippet != null) {
            policyClass = snippet.inlining();
        }
        if (policyClass == SnippetInliningPolicy.class) {
            return new DefaultSnippetInliningPolicy(providers.getMetaAccess());
        }
        try {
            return policyClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution.
     * 
     * @param method the snippet or method substitution for which a graph will be created
     * @param original the original method if {@code method} is a {@linkplain MethodSubstitution
     *            substitution} otherwise null
     * @param policy the inlining policy to use during preprocessing
     * @param removeAllFrameStates removes all frame states from side effecting instructions
     */
    public StructuredGraph makeGraph(ResolvedJavaMethod method, ResolvedJavaMethod original, SnippetInliningPolicy policy, boolean removeAllFrameStates) {
        return createGraphMaker(method, original).makeGraph(policy, removeAllFrameStates);
    }

    /**
     * Can be overridden to return an object that specializes various parts of graph preprocessing.
     */
    protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
        return new GraphMaker(substitute, original);
    }

    /**
     * Cache to speed up preprocessing of replacement graphs.
     */
    final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphCache = new ConcurrentHashMap<>();

    /**
     * Creates and preprocesses a graph for a replacement.
     */
    protected class GraphMaker {

        /**
         * The method for which a graph is being created.
         */
        protected final ResolvedJavaMethod method;

        /**
         * The original method if {@link #method} is a {@linkplain MethodSubstitution substitution}
         * otherwise null.
         */
        protected final ResolvedJavaMethod original;

        boolean substituteCallsOriginal;

        protected GraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
            this.method = substitute;
            this.original = original;
        }

        public StructuredGraph makeGraph(final SnippetInliningPolicy policy, final boolean removeAllFrameStates) {
            return Debug.scope("BuildSnippetGraph", new Object[]{method}, new Callable<StructuredGraph>() {

                @Override
                public StructuredGraph call() throws Exception {
                    StructuredGraph graph = parseGraph(method, policy);

                    // Cannot have a finalized version of a graph in the cache
                    graph = graph.copy();

                    finalizeGraph(graph, removeAllFrameStates);

                    Debug.dump(graph, "%s: Final", method.getName());

                    return graph;
                }
            });
        }

        /**
         * Does final processing of a snippet graph.
         */
        protected void finalizeGraph(StructuredGraph graph, boolean removeAllFrameStates) {
            new NodeIntrinsificationPhase(providers).apply(graph);
            if (!SnippetTemplate.hasConstantParameter(method)) {
                NodeIntrinsificationVerificationPhase.verify(graph);
            }
            new ConvertDeoptimizeToGuardPhase().apply(graph);

            if (original == null) {
                if (removeAllFrameStates) {
                    for (Node node : graph.getNodes()) {
                        if (node instanceof StateSplit) {
                            ((StateSplit) node).setStateAfter(null);
                        }
                    }
                } else {
                    new SnippetFrameStateCleanupPhase().apply(graph);
                }
            }
            new DeadCodeEliminationPhase().apply(graph);
        }

        private StructuredGraph parseGraph(final ResolvedJavaMethod methodToParse, final SnippetInliningPolicy policy) {
            StructuredGraph graph = graphCache.get(methodToParse);
            if (graph == null) {
                StructuredGraph newGraph = Debug.scope("ParseGraph", new Object[]{methodToParse}, new Callable<StructuredGraph>() {

                    public StructuredGraph call() throws Exception {
                        return buildGraph(methodToParse, policy == null ? inliningPolicy(methodToParse) : policy);
                    }
                });

                graphCache.putIfAbsent(methodToParse, newGraph);
                graph = graphCache.get(methodToParse);
                assert graph != null;
            }
            return graph;
        }

        /**
         * Builds the initial graph for a snippet.
         */
        protected StructuredGraph buildInitialGraph(final ResolvedJavaMethod methodToParse) {
            final StructuredGraph graph = new StructuredGraph(methodToParse);
            Debug.scope("buildInitialGraph", graph, new Runnable() {

                @Override
                public void run() {
                    MetaAccessProvider metaAccess = providers.getMetaAccess();
                    ForeignCallsProvider foreignCalls = providers.getForeignCalls();
                    new GraphBuilderPhase(metaAccess, foreignCalls, GraphBuilderConfiguration.getSnippetDefault(), OptimisticOptimizations.NONE).apply(graph);
                    new WordTypeVerificationPhase(metaAccess, target.wordKind).apply(graph);
                    new WordTypeRewriterPhase(metaAccess, target.wordKind).apply(graph);

                    if (OptCanonicalizer.getValue()) {
                        new CanonicalizerPhase(true).apply(graph, new PhaseContext(providers, assumptions));
                    }
                }
            });
            return graph;
        }

        protected Object beforeInline(@SuppressWarnings("unused") MethodCallTargetNode callTarget, @SuppressWarnings("unused") StructuredGraph callee) {
            return null;
        }

        /**
         * Called after a graph is inlined.
         * 
         * @param caller the graph into which {@code callee} was inlined
         * @param callee the graph that was inlined into {@code caller}
         * @param beforeInlineData value returned by {@link #beforeInline}.
         */
        protected void afterInline(StructuredGraph caller, StructuredGraph callee, Object beforeInlineData) {
            if (OptCanonicalizer.getValue()) {
                new CanonicalizerPhase(true).apply(caller, new PhaseContext(providers, assumptions));
            }
        }

        /**
         * Called after all inlining for a given graph is complete.
         */
        protected void afterInlining(StructuredGraph graph) {
            new NodeIntrinsificationPhase(providers).apply(graph);
            new DeadCodeEliminationPhase().apply(graph);
            if (OptCanonicalizer.getValue()) {
                new CanonicalizerPhase(true).apply(graph, new PhaseContext(providers, assumptions));
            }
        }

        private StructuredGraph buildGraph(final ResolvedJavaMethod methodToParse, final SnippetInliningPolicy policy) {
            assert !Modifier.isAbstract(methodToParse.getModifiers()) && !Modifier.isNative(methodToParse.getModifiers()) : methodToParse;
            final StructuredGraph graph = buildInitialGraph(methodToParse);
            Debug.scope("buildGraph", graph, new Runnable() {

                @Override
                public void run() {
                    for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.class)) {
                        ResolvedJavaMethod callee = callTarget.targetMethod();
                        if (callee == method) {
                            final StructuredGraph originalGraph = new StructuredGraph(original);
                            MetaAccessProvider metaAccess = providers.getMetaAccess();
                            ForeignCallsProvider foreignCalls = providers.getForeignCalls();
                            new GraphBuilderPhase(metaAccess, foreignCalls, GraphBuilderConfiguration.getSnippetDefault(), OptimisticOptimizations.NONE).apply(originalGraph);
                            new WordTypeVerificationPhase(metaAccess, target.wordKind).apply(graph);
                            new WordTypeRewriterPhase(metaAccess, target.wordKind).apply(graph);

                            InliningUtil.inline(callTarget.invoke(), originalGraph, true);

                            Debug.dump(graph, "after inlining %s", callee);
                            afterInline(graph, originalGraph, null);
                            substituteCallsOriginal = true;
                        } else {
                            StructuredGraph intrinsicGraph = InliningUtil.getIntrinsicGraph(ReplacementsImpl.this, callee);
                            if ((callTarget.invokeKind() == InvokeKind.Static || callTarget.invokeKind() == InvokeKind.Special) &&
                                            (policy.shouldInline(callee, methodToParse) || (intrinsicGraph != null && policy.shouldUseReplacement(callee, methodToParse)))) {
                                StructuredGraph targetGraph;
                                if (intrinsicGraph != null && policy.shouldUseReplacement(callee, methodToParse)) {
                                    targetGraph = intrinsicGraph;
                                } else {
                                    if (callee.getName().startsWith("$jacoco")) {
                                        throw new GraalInternalError("Parsing call to JaCoCo instrumentation method " + format("%H.%n(%p)", callee) + " from " + format("%H.%n(%p)", methodToParse) +
                                                        " while preparing replacement " + format("%H.%n(%p)", method) + ". Placing \"//JaCoCo Exclude\" anywhere in " +
                                                        methodToParse.getDeclaringClass().getSourceFileName() + " should fix this.");
                                    }
                                    targetGraph = parseGraph(callee, policy);
                                }
                                Object beforeInlineData = beforeInline(callTarget, targetGraph);
                                InliningUtil.inline(callTarget.invoke(), targetGraph, true);
                                Debug.dump(graph, "after inlining %s", callee);
                                afterInline(graph, targetGraph, beforeInlineData);
                            }
                        }
                    }

                    afterInlining(graph);

                    for (LoopEndNode end : graph.getNodes(LoopEndNode.class)) {
                        end.disableSafepoint();
                    }

                    new DeadCodeEliminationPhase().apply(graph);
                }
            });
            return graph;
        }
    }

    private static String originalName(Method substituteMethod, String methodSubstitution) {
        if (methodSubstitution.isEmpty()) {
            return substituteMethod.getName();
        } else {
            return methodSubstitution;
        }
    }

    /**
     * Resolves a name to a class.
     * 
     * @param className the name of the class to resolve
     * @param optional if true, resolution failure returns null
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    static Class resolveType(String className, boolean optional) {
        try {
            // Need to use launcher class path to handle classes
            // that are not on the boot class path
            ClassLoader cl = Launcher.getLauncher().getClassLoader();
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            if (optional) {
                return null;
            }
            throw new GraalInternalError("Could not resolve type " + className);
        }
    }

    private static Class resolveType(JavaType type) {
        JavaType base = type;
        int dimensions = 0;
        while (base.getComponentType() != null) {
            base = base.getComponentType();
            dimensions++;
        }

        Class baseClass = base.getKind() != Kind.Object ? base.getKind().toJavaClass() : resolveType(toJavaName(base), false);
        return dimensions == 0 ? baseClass : Array.newInstance(baseClass, new int[dimensions]).getClass();
    }

    private Class[] originalParameters(Method substituteMethod, String methodSubstitution, boolean isStatic) {
        Class[] parameters;
        if (methodSubstitution.isEmpty()) {
            parameters = substituteMethod.getParameterTypes();
            if (!isStatic) {
                assert parameters.length > 0 : "must be a static method with the 'this' object as its first parameter";
                parameters = Arrays.copyOfRange(parameters, 1, parameters.length);
            }
        } else {
            Signature signature = providers.getMetaAccess().parseMethodDescriptor(methodSubstitution);
            parameters = new Class[signature.getParameterCount(false)];
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = resolveType(signature.getParameterType(i, null));
            }
        }
        return parameters;
    }

    private static Member originalMethod(ClassSubstitution classSubstitution, boolean optional, String name, Class[] parameters) {
        Class<?> originalClass = classSubstitution.value();
        if (originalClass == ClassSubstitution.class) {
            originalClass = resolveType(classSubstitution.className(), classSubstitution.optional());
            if (originalClass == null) {
                // optional class was not found
                return null;
            }
        }
        try {
            if (name.equals("<init>")) {
                return originalClass.getDeclaredConstructor(parameters);
            } else {
                return originalClass.getDeclaredMethod(name, parameters);
            }
        } catch (NoSuchMethodException | SecurityException e) {
            if (optional) {
                return null;
            }
            throw new GraalInternalError(e);
        }
    }

    @Override
    public Collection<ResolvedJavaMethod> getAllReplacements() {
        HashSet<ResolvedJavaMethod> result = new HashSet<>();
        result.addAll(registeredMethodSubstitutions.keySet());
        result.addAll(registeredMacroSubstitutions.keySet());
        return result;
    }

    @Override
    public boolean isForcedSubstitution(ResolvedJavaMethod method) {
        return forcedSubstitutions.contains(method);
    }

    @Override
    public void registerSnippetTemplateCache(SnippetTemplateCache templates) {
        assert snippetTemplateCache.get(templates.getClass()) == null;
        snippetTemplateCache.put(templates.getClass(), templates);
    }

    @Override
    public <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass) {
        SnippetTemplateCache ret = snippetTemplateCache.get(templatesClass);
        return templatesClass.cast(ret);
    }
}
