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
import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPhase.Instance;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.replacements.Snippet.DefaultSnippetInliningPolicy;
import com.oracle.graal.replacements.Snippet.SnippetInliningPolicy;
import com.oracle.graal.word.phases.*;

public class ReplacementsImpl implements Replacements {

    protected final Providers providers;
    protected final SnippetReflectionProvider snippetReflection;
    protected final TargetDescription target;
    protected final Assumptions assumptions;

    /**
     * The preprocessed replacement graphs.
     */
    protected final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphs;

    /**
     * Encapsulates method and macro substitutions for a single class.
     */
    protected class ClassReplacements {
        protected final Map<ResolvedJavaMethod, ResolvedJavaMethod> methodSubstitutions = new HashMap<>();
        private final Map<ResolvedJavaMethod, Class<? extends FixedWithNextNode>> macroSubstitutions = new HashMap<>();
        private final Set<ResolvedJavaMethod> forcedSubstitutions = new HashSet<>();

        public ClassReplacements(Class<?>[] substitutionClasses, AtomicReference<ClassReplacements> ref) {
            for (Class<?> substitutionClass : substitutionClasses) {
                ClassSubstitution classSubstitution = substitutionClass.getAnnotation(ClassSubstitution.class);
                assert !Snippets.class.isAssignableFrom(substitutionClass);
                SubstitutionGuard defaultGuard = getGuard(classSubstitution.defaultGuard());
                for (Method substituteMethod : substitutionClass.getDeclaredMethods()) {
                    if (ref.get() != null) {
                        // Bail if another thread beat us creating the substitutions
                        return;
                    }
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
                        SubstitutionGuard guard = getGuard(methodSubstitution.guard());
                        if (guard == null) {
                            guard = defaultGuard;
                        }

                        if (macroSubstitution != null && macroSubstitution.isStatic() != methodSubstitution.isStatic()) {
                            throw new GraalInternalError("Macro and method substitution must agree on isStatic attribute: " + substituteMethod);
                        }
                        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                            throw new GraalInternalError("Substitution method must not be abstract or native: " + substituteMethod);
                        }
                        String originalName = originalName(substituteMethod, methodSubstitution.value());
                        JavaSignature originalSignature = originalSignature(substituteMethod, methodSubstitution.signature(), methodSubstitution.isStatic());
                        Member originalMethod = originalMethod(classSubstitution, methodSubstitution.optional(), originalName, originalSignature);
                        if (originalMethod != null && (guard == null || guard.execute())) {
                            ResolvedJavaMethod original = registerMethodSubstitution(this, originalMethod, substituteMethod);
                            if (original != null && methodSubstitution.forced() && shouldIntrinsify(original)) {
                                forcedSubstitutions.add(original);
                            }
                        }
                    }
                    // We don't have per method guards for macro substitutions but at
                    // least respect the defaultGuard if there is one.
                    if (macroSubstitution != null && (defaultGuard == null || defaultGuard.execute())) {
                        String originalName = originalName(substituteMethod, macroSubstitution.value());
                        JavaSignature originalSignature = originalSignature(substituteMethod, macroSubstitution.signature(), macroSubstitution.isStatic());
                        Member originalMethod = originalMethod(classSubstitution, macroSubstitution.optional(), originalName, originalSignature);
                        if (originalMethod != null) {
                            ResolvedJavaMethod original = registerMacroSubstitution(this, originalMethod, macroSubstitution.macro());
                            if (original != null && macroSubstitution.forced() && shouldIntrinsify(original)) {
                                forcedSubstitutions.add(original);
                            }
                        }
                    }
                }
            }
        }

        private JavaSignature originalSignature(Method substituteMethod, String methodSubstitution, boolean isStatic) {
            Class<?>[] parameters;
            Class<?> returnType;
            if (methodSubstitution.isEmpty()) {
                parameters = substituteMethod.getParameterTypes();
                if (!isStatic) {
                    assert parameters.length > 0 : "must be a static method with the 'this' object as its first parameter";
                    parameters = Arrays.copyOfRange(parameters, 1, parameters.length);
                }
                returnType = substituteMethod.getReturnType();
            } else {
                Signature signature = providers.getMetaAccess().parseMethodDescriptor(methodSubstitution);
                parameters = new Class[signature.getParameterCount(false)];
                for (int i = 0; i < parameters.length; i++) {
                    parameters[i] = resolveClass(signature.getParameterType(i, null));
                }
                returnType = resolveClass(signature.getReturnType(null));
            }
            return new JavaSignature(returnType, parameters);
        }

        private Member originalMethod(ClassSubstitution classSubstitution, boolean optional, String name, JavaSignature signature) {
            Class<?> originalClass = classSubstitution.value();
            if (originalClass == ClassSubstitution.class) {
                originalClass = resolveClass(classSubstitution.className(), classSubstitution.optional());
                if (originalClass == null) {
                    // optional class was not found
                    return null;
                }
            }
            try {
                if (name.equals("<init>")) {
                    assert signature.returnType.equals(void.class) : signature;
                    Constructor<?> original = originalClass.getDeclaredConstructor(signature.parameters);
                    return original;
                } else {
                    Method original = originalClass.getDeclaredMethod(name, signature.parameters);
                    if (!original.getReturnType().equals(signature.returnType)) {
                        throw new NoSuchMethodException(originalClass.getName() + "." + name + signature);
                    }
                    return original;
                }
            } catch (NoSuchMethodException | SecurityException e) {
                if (optional) {
                    return null;
                }
                throw new GraalInternalError(e);
            }
        }
    }

    /**
     * Per-class replacements. The entries in these maps are all fully initialized during
     * single-threaded compiler startup and so do not need to be concurrent.
     */
    private final Map<String, AtomicReference<ClassReplacements>> classReplacements;
    private final Map<String, Class<?>[]> internalNameToSubstitutionClasses;

    private final Map<Class<? extends SnippetTemplateCache>, SnippetTemplateCache> snippetTemplateCache;

    public ReplacementsImpl(Providers providers, SnippetReflectionProvider snippetReflection, Assumptions assumptions, TargetDescription target) {
        this.providers = providers.copyWith(this);
        this.classReplacements = new HashMap<>();
        this.internalNameToSubstitutionClasses = new HashMap<>();
        this.snippetReflection = snippetReflection;
        this.target = target;
        this.assumptions = assumptions;
        this.graphs = new ConcurrentHashMap<>();
        this.snippetTemplateCache = new HashMap<>();
    }

    private static final boolean UseSnippetGraphCache = Boolean.parseBoolean(System.getProperty("graal.useSnippetGraphCache", "true"));
    private static final DebugTimer SnippetPreparationTime = Debug.timer("SnippetPreparationTime");

    /**
     * Gets the method and macro replacements for a given class. This method will parse the
     * replacements in the substitution classes associated with {@code internalName} the first time
     * this method is called for {@code internalName}.
     */
    protected ClassReplacements getClassReplacements(String internalName) {
        Class<?>[] substitutionClasses = internalNameToSubstitutionClasses.get(internalName);
        if (substitutionClasses != null) {
            AtomicReference<ClassReplacements> crRef = classReplacements.get(internalName);
            if (crRef.get() == null) {
                crRef.compareAndSet(null, new ClassReplacements(substitutionClasses, crRef));
            }
            return crRef.get();
        }
        return null;
    }

    public StructuredGraph getSnippet(ResolvedJavaMethod method) {
        return getSnippet(method, null);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry) {
        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        assert !method.isAbstract() && !method.isNative() : "Snippet must not be abstract or native";

        StructuredGraph graph = UseSnippetGraphCache ? graphs.get(method) : null;
        if (graph == null) {
            try (TimerCloseable a = SnippetPreparationTime.start()) {
                FrameStateProcessing frameStateProcessing = method.getAnnotation(Snippet.class).removeAllFrameStates() ? FrameStateProcessing.Removal
                                : FrameStateProcessing.CollapseFrameForSingleSideEffect;
                StructuredGraph newGraph = makeGraph(method, recursiveEntry, inliningPolicy(method), frameStateProcessing);
                Debug.metric("SnippetNodeCount[%#s]", method).add(newGraph.getNodeCount());
                if (!UseSnippetGraphCache) {
                    return newGraph;
                }
                graphs.putIfAbsent(method, newGraph);
                graph = graphs.get(method);
            }
        }
        return graph;
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method) {
        // No initialization needed as snippet graphs are created on demand in getSnippet
    }

    @Override
    public void notifyAfterConstantsBound(StructuredGraph specializedSnippet) {

        // Do deferred intrinsification of node intrinsics

        new NodeIntrinsificationPhase(providers, snippetReflection).apply(specializedSnippet);
        new CanonicalizerPhase(true).apply(specializedSnippet, new PhaseContext(providers, assumptions));
        NodeIntrinsificationVerificationPhase.verify(specializedSnippet);
    }

    @Override
    public StructuredGraph getMethodSubstitution(ResolvedJavaMethod original) {
        ClassReplacements cr = getClassReplacements(original.getDeclaringClass().getName());
        ResolvedJavaMethod substitute = cr == null ? null : cr.methodSubstitutions.get(original);
        if (substitute == null) {
            return null;
        }
        StructuredGraph graph = graphs.get(substitute);
        if (graph == null) {
            graph = makeGraph(substitute, original, inliningPolicy(substitute), FrameStateProcessing.None);
            graph.freeze();
            graphs.putIfAbsent(substitute, graph);
            graph = graphs.get(substitute);
        }
        assert graph.isFrozen();
        return graph;

    }

    public Class<? extends FixedWithNextNode> getMacroSubstitution(ResolvedJavaMethod method) {
        ClassReplacements cr = getClassReplacements(method.getDeclaringClass().getName());
        return cr == null ? null : cr.macroSubstitutions.get(method);
    }

    public Assumptions getAssumptions() {
        return assumptions;
    }

    private static SubstitutionGuard getGuard(Class<? extends SubstitutionGuard> guardClass) {
        if (guardClass != SubstitutionGuard.class) {
            try {
                return guardClass.newInstance();
            } catch (Exception e) {
                throw new GraalInternalError(e);
            }
        }
        return null;
    }

    private static String getOriginalInternalName(Class<?> substitutions) {
        ClassSubstitution cs = substitutions.getAnnotation(ClassSubstitution.class);
        assert cs != null : substitutions + " must be annotated by " + ClassSubstitution.class.getSimpleName();
        if (cs.value() == ClassSubstitution.class) {
            return toInternalName(cs.className());
        }
        return toInternalName(cs.value().getName());
    }

    public void registerSubstitutions(Type original, Class<?> substitutionClass) {
        String internalName = toInternalName(original.getTypeName());
        assert getOriginalInternalName(substitutionClass).equals(internalName) : getOriginalInternalName(substitutionClass) + " != " + (internalName);
        Class<?>[] classes = internalNameToSubstitutionClasses.get(internalName);
        if (classes == null) {
            classes = new Class<?>[]{substitutionClass};
        } else {
            assert !Arrays.asList(classes).contains(substitutionClass);
            classes = Arrays.copyOf(classes, classes.length + 1);
            classes[classes.length - 1] = substitutionClass;
        }
        internalNameToSubstitutionClasses.put(internalName, classes);
        AtomicReference<ClassReplacements> existing = classReplacements.put(internalName, new AtomicReference<>());
        assert existing == null || existing.get() == null;
    }

    /**
     * Registers a method substitution.
     *
     * @param originalMember a method or constructor being substituted
     * @param substituteMethod the substitute method
     * @return the original method
     */
    protected ResolvedJavaMethod registerMethodSubstitution(ClassReplacements cr, Member originalMember, Method substituteMethod) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod substitute = metaAccess.lookupJavaMethod(substituteMethod);
        ResolvedJavaMethod original;
        if (originalMember instanceof Method) {
            original = metaAccess.lookupJavaMethod((Method) originalMember);
        } else {
            original = metaAccess.lookupJavaConstructor((Constructor<?>) originalMember);
        }
        if (Debug.isLogEnabled()) {
            Debug.log("substitution: %s --> %s", original.format("%H.%n(%p) %r"), substitute.format("%H.%n(%p) %r"));
        }

        cr.methodSubstitutions.put(original, substitute);
        return original;
    }

    /**
     * Registers a macro substitution.
     *
     * @param originalMethod a method or constructor being substituted
     * @param macro the substitute macro node class
     * @return the original method
     */
    protected ResolvedJavaMethod registerMacroSubstitution(ClassReplacements cr, Member originalMethod, Class<? extends FixedWithNextNode> macro) {
        ResolvedJavaMethod originalJavaMethod;
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        if (originalMethod instanceof Method) {
            originalJavaMethod = metaAccess.lookupJavaMethod((Method) originalMethod);
        } else {
            originalJavaMethod = metaAccess.lookupJavaConstructor((Constructor<?>) originalMethod);
        }
        cr.macroSubstitutions.put(originalJavaMethod, macro);
        return originalJavaMethod;
    }

    private static SnippetInliningPolicy createPolicyClassInstance(Class<? extends SnippetInliningPolicy> policyClass) {
        try {
            return policyClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new GraalInternalError(e);
        }
    }

    protected SnippetInliningPolicy inliningPolicy(ResolvedJavaMethod method) {
        Class<? extends SnippetInliningPolicy> policyClass = SnippetInliningPolicy.class;
        Snippet snippet = method.getAnnotation(Snippet.class);
        if (snippet != null) {
            policyClass = snippet.inlining();
        }
        if (policyClass == SnippetInliningPolicy.class) {
            return new DefaultSnippetInliningPolicy(providers.getMetaAccess());
        }
        return createPolicyClassInstance(policyClass);
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution.
     *
     * @param method the snippet or method substitution for which a graph will be created
     * @param original the original method if {@code method} is a {@linkplain MethodSubstitution
     *            substitution} otherwise null
     * @param policy the inlining policy to use during preprocessing
     * @param frameStateProcessing controls how {@link FrameState FrameStates} should be handled.
     */
    public StructuredGraph makeGraph(ResolvedJavaMethod method, ResolvedJavaMethod original, SnippetInliningPolicy policy, FrameStateProcessing frameStateProcessing) {
        return createGraphMaker(method, original, frameStateProcessing).makeGraph(policy);
    }

    /**
     * Can be overridden to return an object that specializes various parts of graph preprocessing.
     */
    protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original, FrameStateProcessing frameStateProcessing) {
        return new GraphMaker(substitute, original, frameStateProcessing);
    }

    /**
     * Cache to speed up preprocessing of replacement graphs.
     */
    final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphCache = new ConcurrentHashMap<>();

    public enum FrameStateProcessing {
        None,
        CollapseFrameForSingleSideEffect,
        Removal
    }

    /**
     * Calls in snippets to methods matching one of these filters are elided. Only void methods are
     * considered for elision.
     */
    private static final MethodFilter[] MethodsElidedInSnippets = getMethodsElidedInSnippets();

    private static MethodFilter[] getMethodsElidedInSnippets() {
        String commaSeparatedPatterns = System.getProperty("graal.MethodsElidedInSnippets");
        if (commaSeparatedPatterns != null) {
            return MethodFilter.parse(commaSeparatedPatterns);
        }
        return null;
    }

    /**
     * Creates and preprocesses a graph for a replacement.
     */
    protected class GraphMaker {
        /**
         * The method for which a graph is being created.
         */
        protected final ResolvedJavaMethod method;

        /**
         * The original method which {@link #method} is substituting. Calls to {@link #method} or
         * {@link #substitutedMethod} will be replaced with a forced inline of
         * {@link #substitutedMethod}.
         */
        protected final ResolvedJavaMethod substitutedMethod;

        /**
         * Controls how FrameStates are processed.
         */
        private FrameStateProcessing frameStateProcessing;

        protected GraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod, FrameStateProcessing frameStateProcessing) {
            this.method = substitute;
            this.substitutedMethod = substitutedMethod;
            this.frameStateProcessing = frameStateProcessing;
        }

        public StructuredGraph makeGraph(final SnippetInliningPolicy policy) {
            try (Scope s = Debug.scope("BuildSnippetGraph", method)) {
                StructuredGraph graph = parseGraph(method, policy, 0);

                // Cannot have a finalized version of a graph in the cache
                graph = graph.copy();

                finalizeGraph(graph);

                Debug.dump(graph, "%s: Final", method.getName());

                return graph;
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }

        /**
         * Does final processing of a snippet graph.
         */
        protected void finalizeGraph(StructuredGraph graph) {
            new NodeIntrinsificationPhase(providers, snippetReflection).apply(graph);
            if (!SnippetTemplate.hasConstantParameter(method)) {
                NodeIntrinsificationVerificationPhase.verify(graph);
            }
            int sideEffectCount = 0;
            assert (sideEffectCount = graph.getNodes().filter(e -> hasSideEffect(e)).count()) >= 0;
            new ConvertDeoptimizeToGuardPhase().apply(graph);
            assert sideEffectCount == graph.getNodes().filter(e -> hasSideEffect(e)).count() : "deleted side effecting node";

            switch (frameStateProcessing) {
                case Removal:
                    for (Node node : graph.getNodes()) {
                        if (node instanceof StateSplit) {
                            ((StateSplit) node).setStateAfter(null);
                        }
                    }
                    break;
                case CollapseFrameForSingleSideEffect:
                    new CollapseFrameForSingleSideEffectPhase().apply(graph);
                    break;
            }
            new DeadCodeEliminationPhase().apply(graph);
        }

        /**
         * Filter nodes which have side effects and shouldn't be deleted from snippets when
         * converting deoptimizations to guards. Currently this only allows exception constructors
         * to be eliminated to cover the case when Java assertions are in the inlined code.
         *
         * @param node
         * @return true for nodes that have side effects and are unsafe to delete
         */
        private boolean hasSideEffect(Node node) {
            if (node instanceof StateSplit) {
                if (((StateSplit) node).hasSideEffect()) {
                    if (node instanceof Invoke) {
                        CallTargetNode callTarget = ((Invoke) node).callTarget();
                        if (callTarget instanceof MethodCallTargetNode) {
                            ResolvedJavaMethod targetMethod = ((MethodCallTargetNode) callTarget).targetMethod();
                            if (targetMethod.isConstructor()) {
                                ResolvedJavaType throwableType = providers.getMetaAccess().lookupJavaType(Throwable.class);
                                return !throwableType.isAssignableFrom(targetMethod.getDeclaringClass());
                            }
                        }
                    }
                    // Not an exception constructor call
                    return true;
                }
            }
            // Not a StateSplit
            return false;
        }

        private static final int MAX_GRAPH_INLINING_DEPTH = 100; // more than enough

        private StructuredGraph parseGraph(final ResolvedJavaMethod methodToParse, final SnippetInliningPolicy policy, int inliningDepth) {
            StructuredGraph graph = graphCache.get(methodToParse);
            if (graph == null) {
                StructuredGraph newGraph = null;
                try (Scope s = Debug.scope("ParseGraph", methodToParse)) {
                    newGraph = buildGraph(methodToParse, policy == null ? inliningPolicy(methodToParse) : policy, inliningDepth);
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }

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
            try (Scope s = Debug.scope("buildInitialGraph", graph)) {
                MetaAccessProvider metaAccess = providers.getMetaAccess();

                if (MethodsElidedInSnippets != null && methodToParse.getSignature().getReturnKind() == Kind.Void && MethodFilter.matches(MethodsElidedInSnippets, methodToParse)) {
                    graph.addAfterFixed(graph.start(), graph.add(new ReturnNode(null)));
                } else {
                    createGraphBuilder(metaAccess, GraphBuilderConfiguration.getSnippetDefault(), OptimisticOptimizations.NONE).apply(graph);
                }
                new WordTypeVerificationPhase(metaAccess, snippetReflection, target.wordKind).apply(graph);
                new WordTypeRewriterPhase(metaAccess, snippetReflection, target.wordKind).apply(graph);

                if (OptCanonicalizer.getValue()) {
                    new CanonicalizerPhase(true).apply(graph, new PhaseContext(providers, assumptions));
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            return graph;
        }

        protected Instance createGraphBuilder(MetaAccessProvider metaAccess, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {
            return new GraphBuilderPhase.Instance(metaAccess, graphBuilderConfig, optimisticOpts);
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
            new NodeIntrinsificationPhase(providers, snippetReflection).apply(graph);
            new DeadCodeEliminationPhase().apply(graph);
            if (OptCanonicalizer.getValue()) {
                new CanonicalizerPhase(true).apply(graph, new PhaseContext(providers, assumptions));
            }
        }

        private StructuredGraph buildGraph(final ResolvedJavaMethod methodToParse, final SnippetInliningPolicy policy, int inliningDepth) {
            assert inliningDepth < MAX_GRAPH_INLINING_DEPTH : "inlining limit exceeded";
            assert isInlinable(methodToParse) : methodToParse;
            final StructuredGraph graph = buildInitialGraph(methodToParse);
            try (Scope s = Debug.scope("buildGraph", graph)) {
                Set<MethodCallTargetNode> doNotInline = null;
                for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.class)) {
                    if (doNotInline != null && doNotInline.contains(callTarget)) {
                        continue;
                    }
                    ResolvedJavaMethod callee = callTarget.targetMethod();
                    if (substitutedMethod != null && (callee.equals(method) || callee.equals(substitutedMethod))) {
                        /*
                         * Ensure that calls to the original method inside of a substitution ends up
                         * calling it instead of the Graal substitution.
                         */
                        if (isInlinable(substitutedMethod)) {
                            final StructuredGraph originalGraph = buildInitialGraph(substitutedMethod);
                            Mark mark = graph.getMark();
                            InliningUtil.inline(callTarget.invoke(), originalGraph, true, null);
                            for (MethodCallTargetNode inlinedCallTarget : graph.getNewNodes(mark).filter(MethodCallTargetNode.class)) {
                                if (doNotInline == null) {
                                    doNotInline = new HashSet<>();
                                }
                                // We do not want to do further inlining (now) for calls
                                // in the original method as this can cause unlimited
                                // recursive inlining given an eager inlining policy such
                                // as DefaultSnippetInliningPolicy.
                                doNotInline.add(inlinedCallTarget);
                            }
                            Debug.dump(graph, "after inlining %s", callee);
                            afterInline(graph, originalGraph, null);
                        }
                    } else {
                        Class<? extends FixedWithNextNode> macroNodeClass = InliningUtil.getMacroNodeClass(ReplacementsImpl.this, callee);
                        if (macroNodeClass != null) {
                            InliningUtil.inlineMacroNode(callTarget.invoke(), callee, macroNodeClass);
                        } else {
                            StructuredGraph intrinsicGraph = InliningUtil.getIntrinsicGraph(ReplacementsImpl.this, callee);
                            if ((callTarget.invokeKind() == InvokeKind.Static || callTarget.invokeKind() == InvokeKind.Special) &&
                                            (policy.shouldInline(callee, methodToParse) || (intrinsicGraph != null && policy.shouldUseReplacement(callee, methodToParse)))) {
                                StructuredGraph targetGraph;
                                if (intrinsicGraph != null && policy.shouldUseReplacement(callee, methodToParse)) {
                                    targetGraph = intrinsicGraph;
                                } else {
                                    if (callee.getName().startsWith("$jacoco")) {
                                        throw new GraalInternalError("Parsing call to JaCoCo instrumentation method " + callee.format("%H.%n(%p)") + " from " + methodToParse.format("%H.%n(%p)") +
                                                        " while preparing replacement " + method.format("%H.%n(%p)") + ". Placing \"//JaCoCo Exclude\" anywhere in " +
                                                        methodToParse.getDeclaringClass().getSourceFileName() + " should fix this.");
                                    }
                                    targetGraph = parseGraph(callee, policy, inliningDepth + 1);
                                }
                                Object beforeInlineData = beforeInline(callTarget, targetGraph);
                                InliningUtil.inline(callTarget.invoke(), targetGraph, true, null);
                                Debug.dump(graph, "after inlining %s", callee);
                                afterInline(graph, targetGraph, beforeInlineData);
                            }
                        }
                    }
                }

                afterInlining(graph);

                for (LoopEndNode end : graph.getNodes(LoopEndNode.class)) {
                    end.disableSafepoint();
                }

                new DeadCodeEliminationPhase().apply(graph);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            return graph;
        }
    }

    private static boolean isInlinable(final ResolvedJavaMethod method) {
        return !method.isAbstract() && !method.isNative();
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
    static Class<?> resolveClass(String className, boolean optional) {
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

    private static Class<?> resolveClass(JavaType type) {
        JavaType base = type;
        int dimensions = 0;
        while (base.getComponentType() != null) {
            base = base.getComponentType();
            dimensions++;
        }

        Class<?> baseClass = base.getKind() != Kind.Object ? base.getKind().toJavaClass() : resolveClass(base.toJavaName(), false);
        return dimensions == 0 ? baseClass : Array.newInstance(baseClass, new int[dimensions]).getClass();
    }

    static class JavaSignature {
        final Class<?> returnType;
        final Class<?>[] parameters;

        public JavaSignature(Class<?> returnType, Class<?>[] parameters) {
            this.parameters = parameters;
            this.returnType = returnType;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < parameters.length; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(parameters[i].getName());
            }
            return sb.append(") ").append(returnType.getName()).toString();
        }
    }

    @Override
    public Collection<ResolvedJavaMethod> getAllReplacements() {
        HashSet<ResolvedJavaMethod> result = new HashSet<>();
        for (String internalName : classReplacements.keySet()) {
            ClassReplacements cr = getClassReplacements(internalName);
            result.addAll(cr.methodSubstitutions.keySet());
            result.addAll(cr.macroSubstitutions.keySet());
        }
        return result;
    }

    @Override
    public boolean isForcedSubstitution(ResolvedJavaMethod method) {
        ClassReplacements cr = getClassReplacements(method.getDeclaringClass().getName());
        return cr != null && cr.forcedSubstitutions.contains(method);
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
