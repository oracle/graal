/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.replacements.Snippet.DefaultSnippetInliningPolicy;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.word.phases.*;

public class ReplacementsImpl implements Replacements {

    protected final MetaAccessProvider runtime;
    protected final TargetDescription target;
    protected final Assumptions assumptions;

    private BoxingMethodPool pool;

    /**
     * A graph cache used by this installer to avoid using the compiler storage for each method
     * processed during snippet installation. Without this, all processed methods are to be
     * determined as {@linkplain InliningUtil#canIntrinsify intrinsifiable}.
     */
    private final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphCache;

    private final ConcurrentMap<ResolvedJavaMethod, ResolvedJavaMethod> originalToSubstitute;

    private final ConcurrentMap<ResolvedJavaMethod, Class<? extends FixedWithNextNode>> macroNodeClasses;

    public ReplacementsImpl(MetaAccessProvider runtime, Assumptions assumptions, TargetDescription target) {
        this.runtime = runtime;
        this.target = target;
        this.assumptions = assumptions;
        this.graphCache = new ConcurrentHashMap<>();
        this.originalToSubstitute = new ConcurrentHashMap<>();
        this.macroNodeClasses = new ConcurrentHashMap<>();
    }

    public void registerSnippets(Class<?> snippets) {
        assert Snippets.class.isAssignableFrom(snippets);
        for (Method method : snippets.getDeclaredMethods()) {
            if (method.getAnnotation(Snippet.class) != null) {
                int modifiers = method.getModifiers();
                if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                    throw new RuntimeException("Snippet must not be abstract or native");
                }
                ResolvedJavaMethod snippet = runtime.lookupJavaMethod(method);
                graphCache.putIfAbsent(snippet, placeholder);
            }
        }
    }

    private final StructuredGraph placeholder = new StructuredGraph();

    public StructuredGraph getSnippet(ResolvedJavaMethod method) {
        StructuredGraph graph = graphCache.get(method);
        if (graph == placeholder) {
            graph = createGraphMaker(null, null).makeGraph(method, inliningPolicy(method));
            assert graph == graphCache.get(method);
        }
        return graph;
    }

    public StructuredGraph getMethodSubstitution(ResolvedJavaMethod original) {
        StructuredGraph graph = graphCache.get(original);
        if (graph == placeholder) {
            ResolvedJavaMethod substitute = originalToSubstitute.get(original);
            if (substitute != null) {
                graph = createGraphMaker(substitute, original).makeGraph(substitute, inliningPolicy(substitute));
                assert graph == graphCache.get(substitute);
            }
        }
        return graph;
    }

    public Class<? extends FixedWithNextNode> getMacroSubstitution(ResolvedJavaMethod method) {
        return macroNodeClasses.get(method);
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
                throw new RuntimeException("Substitution methods must be static: " + substituteMethod);
            }

            if (methodSubstitution != null) {
                if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                    throw new RuntimeException("Substitution method must not be abstract or native: " + substituteMethod);
                }
                String originalName = originalName(substituteMethod, methodSubstitution.value());
                Class[] originalParameters = originalParameters(substituteMethod, methodSubstitution.signature(), methodSubstitution.isStatic());
                Member originalMethod = originalMethod(classSubstitution, originalName, originalParameters);
                if (originalMethod != null) {
                    installMethodSubstitution(originalMethod, substituteMethod);
                }
            }
            if (macroSubstitution != null) {
                String originalName = originalName(substituteMethod, macroSubstitution.value());
                Class[] originalParameters = originalParameters(substituteMethod, macroSubstitution.signature(), macroSubstitution.isStatic());
                Member originalMethod = originalMethod(classSubstitution, originalName, originalParameters);
                if (originalMethod != null) {
                    installMacroSubstitution(originalMethod, macroSubstitution.macro());
                }
            }
        }
    }

    /**
     * Installs a method substitution.
     * 
     * @param originalMember a method or constructor being substituted
     * @param substituteMethod the substitute method
     */
    protected void installMethodSubstitution(Member originalMember, Method substituteMethod) {
        ResolvedJavaMethod substitute = runtime.lookupJavaMethod(substituteMethod);
        ResolvedJavaMethod original;
        if (originalMember instanceof Method) {
            original = runtime.lookupJavaMethod((Method) originalMember);
        } else {
            original = runtime.lookupJavaConstructor((Constructor) originalMember);
        }
        Debug.log("substitution: " + MetaUtil.format("%H.%n(%p)", original) + " --> " + MetaUtil.format("%H.%n(%p)", substitute));

        graphCache.putIfAbsent(original, placeholder);
        originalToSubstitute.put(original, substitute);
    }

    /**
     * Installs a macro substitution.
     * 
     * @param originalMethod a method or constructor being substituted
     * @param macro the substitute macro node class
     */
    protected void installMacroSubstitution(Member originalMethod, Class<? extends FixedWithNextNode> macro) {
        ResolvedJavaMethod originalJavaMethod;
        if (originalMethod instanceof Method) {
            originalJavaMethod = runtime.lookupJavaMethod((Method) originalMethod);
        } else {
            originalJavaMethod = runtime.lookupJavaConstructor((Constructor) originalMethod);
        }
        macroNodeClasses.put(originalJavaMethod, macro);
    }

    private SnippetInliningPolicy inliningPolicy(ResolvedJavaMethod method) {
        Class<? extends SnippetInliningPolicy> policyClass = SnippetInliningPolicy.class;
        Snippet snippet = method.getAnnotation(Snippet.class);
        if (snippet != null) {
            policyClass = snippet.inlining();
        }
        if (policyClass == SnippetInliningPolicy.class) {
            return new DefaultSnippetInliningPolicy(runtime, pool());
        }
        try {
            return policyClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    public StructuredGraph makeGraph(ResolvedJavaMethod method, SnippetInliningPolicy policy) {
        return createGraphMaker(null, null).makeGraph(method, policy);
    }

    protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
        return new GraphMaker(substitute, original);
    }

    protected class GraphMaker {

        // These fields are used to detect calls from the substitute method to the original method.
        protected final ResolvedJavaMethod substitute;
        protected final ResolvedJavaMethod original;

        boolean substituteCallsOriginal;

        protected GraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
            this.substitute = substitute;
            this.original = original;
        }

        /**
         * Does final processing of a snippet graph.
         */
        protected void finalizeGraph(ResolvedJavaMethod method, StructuredGraph graph) {
            new NodeIntrinsificationPhase(runtime, pool()).apply(graph);
            assert SnippetTemplate.hasConstantParameter(method) || NodeIntrinsificationVerificationPhase.verify(graph);

            if (substitute == null) {
                new SnippetFrameStateCleanupPhase().apply(graph);
                new DeadCodeEliminationPhase().apply(graph);
                new InsertStateAfterPlaceholderPhase().apply(graph);
            } else {
                new DeadCodeEliminationPhase().apply(graph);
            }
        }

        public StructuredGraph makeGraph(final ResolvedJavaMethod method, final SnippetInliningPolicy policy) {
            return Debug.scope("BuildSnippetGraph", new Object[]{method}, new Callable<StructuredGraph>() {

                @Override
                public StructuredGraph call() throws Exception {
                    StructuredGraph graph = parseGraph(method, policy);

                    finalizeGraph(method, graph);

                    Debug.dump(graph, "%s: Final", method.getName());

                    return graph;
                }
            });
        }

        private StructuredGraph parseGraph(final ResolvedJavaMethod method, final SnippetInliningPolicy policy) {
            StructuredGraph graph = graphCache.get(method);
            if (graph == null || graph == placeholder) {
                graph = buildGraph(method, policy == null ? inliningPolicy(method) : policy);
                graphCache.put(method, graph);
            }
            return graph;
        }

        /**
         * Builds the initial graph for a snippet.
         */
        protected StructuredGraph buildInitialGraph(final ResolvedJavaMethod method) {
            final StructuredGraph graph = new StructuredGraph(method);
            GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault();
            GraphBuilderPhase graphBuilder = new GraphBuilderPhase(runtime, config, OptimisticOptimizations.NONE);
            graphBuilder.apply(graph);

            Debug.dump(graph, "%s: %s", method.getName(), GraphBuilderPhase.class.getSimpleName());

            new WordTypeVerificationPhase(runtime, target.wordKind).apply(graph);
            new NodeIntrinsificationPhase(runtime, pool()).apply(graph);

            return graph;
        }

        /**
         * Called after a graph is inlined.
         * 
         * @param caller the graph into which {@code callee} was inlined
         * @param callee the graph that was inlined into {@code caller}
         */
        protected void afterInline(StructuredGraph caller, StructuredGraph callee) {
            if (GraalOptions.OptCanonicalizer) {
                new WordTypeRewriterPhase(runtime, target.wordKind).apply(caller);
                new CanonicalizerPhase(runtime, assumptions).apply(caller);
            }
        }

        /**
         * Called after all inlining for a given graph is complete.
         */
        protected void afterInlining(StructuredGraph graph) {
            new NodeIntrinsificationPhase(runtime, pool()).apply(graph);

            new WordTypeRewriterPhase(runtime, target.wordKind).apply(graph);

            new DeadCodeEliminationPhase().apply(graph);
            if (GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase(runtime, assumptions).apply(graph);
            }
        }

        private StructuredGraph buildGraph(final ResolvedJavaMethod method, final SnippetInliningPolicy policy) {
            assert !Modifier.isAbstract(method.getModifiers()) && !Modifier.isNative(method.getModifiers()) : method;
            final StructuredGraph graph = buildInitialGraph(method);

            for (Invoke invoke : graph.getInvokes()) {
                MethodCallTargetNode callTarget = invoke.methodCallTarget();
                ResolvedJavaMethod callee = callTarget.targetMethod();
                if (callee == substitute) {
                    final StructuredGraph originalGraph = new StructuredGraph(original);
                    new GraphBuilderPhase(runtime, GraphBuilderConfiguration.getSnippetDefault(), OptimisticOptimizations.NONE).apply(originalGraph);
                    InliningUtil.inline(invoke, originalGraph, true);

                    Debug.dump(graph, "after inlining %s", callee);
                    afterInline(graph, originalGraph);
                    substituteCallsOriginal = true;
                } else {
                    if ((callTarget.invokeKind() == InvokeKind.Static || callTarget.invokeKind() == InvokeKind.Special) && policy.shouldInline(callee, method)) {
                        StructuredGraph targetGraph = parseGraph(callee, policy);
                        InliningUtil.inline(invoke, targetGraph, true);
                        Debug.dump(graph, "after inlining %s", callee);
                        afterInline(graph, targetGraph);
                    }
                }
            }

            afterInlining(graph);

            for (LoopEndNode end : graph.getNodes(LoopEndNode.class)) {
                end.disableSafepoint();
            }

            if (GraalOptions.ProbabilityAnalysis) {
                new DeadCodeEliminationPhase().apply(graph);
                new ComputeProbabilityPhase().apply(graph);
            }
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
            Signature signature = runtime.parseMethodDescriptor(methodSubstitution);
            parameters = new Class[signature.getParameterCount(false)];
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = resolveType(signature.getParameterType(i, null));
            }
        }
        return parameters;
    }

    private static Member originalMethod(ClassSubstitution classSubstitution, String name, Class[] parameters) {
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
            throw new GraalInternalError(e);
        }
    }

    protected BoxingMethodPool pool() {
        if (pool == null) {
            // A race to create the pool is ok
            pool = new BoxingMethodPool(runtime);
        }
        return pool;
    }
}
