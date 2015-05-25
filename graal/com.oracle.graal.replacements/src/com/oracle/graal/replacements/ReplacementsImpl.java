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
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.graphbuilderconf.IntrinsicContext.CompilationContext.*;
import static com.oracle.graal.java.GraphBuilderPhase.Options.*;
import static com.oracle.graal.phases.common.DeadCodeEliminationPhase.Optionality.*;
import static java.lang.String.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.java.*;
import com.oracle.graal.java.GraphBuilderPhase.Instance;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.word.*;
import com.oracle.jvmci.common.*;

public class ReplacementsImpl implements Replacements, InlineInvokePlugin {

    public final Providers providers;
    public final SnippetReflectionProvider snippetReflection;
    public final TargetDescription target;
    private GraphBuilderConfiguration.Plugins graphBuilderPlugins;

    /**
     * The preprocessed replacement graphs.
     */
    protected final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphs;

    public void setGraphBuilderPlugins(GraphBuilderConfiguration.Plugins plugins) {
        assert this.graphBuilderPlugins == null;
        this.graphBuilderPlugins = plugins;
    }

    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    protected boolean hasGenericInvocationPluginAnnotation(ResolvedJavaMethod method) {
        return method.getAnnotation(Node.NodeIntrinsic.class) != null || method.getAnnotation(Word.Operation.class) != null || method.getAnnotation(Fold.class) != null;
    }

    private static final int MAX_GRAPH_INLINING_DEPTH = 100; // more than enough

    /**
     * Determines whether a given method should be inlined based on whether it has a substitution or
     * whether the inlining context is already within a substitution.
     *
     * @return an object specifying how {@code method} is to be inlined or null if it should not be
     *         inlined based on substitution related criteria
     */
    public InlineInfo getInlineInfo(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, JavaType returnType) {
        ResolvedJavaMethod subst = getSubstitutionMethod(method);
        if (subst != null) {
            if (b.parsingIntrinsic() || InlineDuringParsing.getValue() || InlineIntrinsicsDuringParsing.getValue()) {
                // Forced inlining of intrinsics
                return new InlineInfo(subst, true);
            }
            return null;
        }
        if (b.parsingIntrinsic()) {
            assert !hasGenericInvocationPluginAnnotation(method) : format("%s should have been handled by %s", method.format("%H.%n(%p)"), DefaultGenericInvocationPlugin.class.getName());

            assert b.getDepth() < MAX_GRAPH_INLINING_DEPTH : "inlining limit exceeded";

            if (method.getName().startsWith("$jacoco")) {
                throw new JVMCIError("Found call to JaCoCo instrumentation method " + method.format("%H.%n(%p)") + ". Placing \"//JaCoCo Exclude\" anywhere in " +
                                b.getMethod().getDeclaringClass().getSourceFileName() + " should fix this.");
            }

            // Force inlining when parsing replacements
            return new InlineInfo(method, true);
        } else {
            assert method.getAnnotation(NodeIntrinsic.class) == null : String.format("@%s method %s must only be called from within a replacement%n%s", NodeIntrinsic.class.getSimpleName(),
                            method.format("%h.%n"), b);
        }
        return null;
    }

    public void notifyOfNoninlinedInvoke(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (b.parsingIntrinsic()) {
            IntrinsicContext intrinsic = b.getIntrinsic();
            assert intrinsic.isCallToOriginal(method) : format("All non-recursive calls in the intrinsic %s must be inlined or intrinsified: found call to %s",
                            intrinsic.getIntrinsicMethod().format("%H.%n(%p)"), method.format("%h.%n(%p)"));
        }
    }

    /**
     * Encapsulates method and macro substitutions for a single class.
     */
    protected class ClassReplacements {
        public final Map<ResolvedJavaMethod, ResolvedJavaMethod> methodSubstitutions = CollectionsFactory.newMap();

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
                    if (methodSubstitution == null) {
                        continue;
                    }

                    int modifiers = substituteMethod.getModifiers();
                    if (!Modifier.isStatic(modifiers)) {
                        throw new JVMCIError("Substitution methods must be static: " + substituteMethod);
                    }

                    if (methodSubstitution != null) {
                        SubstitutionGuard guard = getGuard(methodSubstitution.guard());
                        if (guard == null) {
                            guard = defaultGuard;
                        }

                        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                            throw new JVMCIError("Substitution method must not be abstract or native: " + substituteMethod);
                        }
                        String originalName = originalName(substituteMethod, methodSubstitution.value());
                        JavaSignature originalSignature = originalSignature(substituteMethod, methodSubstitution.signature(), methodSubstitution.isStatic());
                        Executable[] originalMethods = originalMethods(classSubstitution, classSubstitution.optional(), originalName, originalSignature);
                        if (originalMethods != null) {
                            for (Executable originalMethod : originalMethods) {
                                if (originalMethod != null && (guard == null || guard.execute())) {
                                    registerMethodSubstitution(this, originalMethod, substituteMethod);
                                }
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

        private Executable[] originalMethods(ClassSubstitution classSubstitution, boolean optional, String name, JavaSignature signature) {
            Class<?> originalClass = classSubstitution.value();
            if (originalClass == ClassSubstitution.class) {
                ArrayList<Executable> result = new ArrayList<>();
                for (String className : classSubstitution.className()) {
                    originalClass = resolveClass(className, classSubstitution.optional());
                    if (originalClass != null) {
                        result.add(lookupOriginalMethod(originalClass, name, signature, optional));
                    }
                }
                if (result.size() == 0) {
                    // optional class was not found
                    return null;
                }
                return result.toArray(new Executable[result.size()]);
            }
            Executable original = lookupOriginalMethod(originalClass, name, signature, optional);
            if (original != null) {
                return new Executable[]{original};
            }
            return null;
        }

        private Executable lookupOriginalMethod(Class<?> originalClass, String name, JavaSignature signature, boolean optional) throws JVMCIError {
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
                throw new JVMCIError(e);
            }
        }
    }

    /**
     * Per-class replacements. The entries in these maps are all fully initialized during
     * single-threaded compiler startup and so do not need to be concurrent.
     */
    private final Map<String, AtomicReference<ClassReplacements>> classReplacements;
    private final Map<String, Class<?>[]> internalNameToSubstitutionClasses;

    // This map is key'ed by a class name instead of a Class object so that
    // it is stable across VM executions (in support of replay compilation).
    private final Map<String, SnippetTemplateCache> snippetTemplateCache;

    public ReplacementsImpl(Providers providers, SnippetReflectionProvider snippetReflection, TargetDescription target) {
        this.providers = providers.copyWith(this);
        this.classReplacements = CollectionsFactory.newMap();
        this.internalNameToSubstitutionClasses = CollectionsFactory.newMap();
        this.snippetReflection = snippetReflection;
        this.target = target;
        this.graphs = new ConcurrentHashMap<>();
        this.snippetTemplateCache = CollectionsFactory.newMap();
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

    public StructuredGraph getSnippet(ResolvedJavaMethod method, Object[] args) {
        return getSnippet(method, null, args);
    }

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args) {
        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        assert method.hasBytecodes() : "Snippet must not be abstract or native";

        StructuredGraph graph = UseSnippetGraphCache ? graphs.get(method) : null;
        if (graph == null) {
            try (DebugCloseable a = SnippetPreparationTime.start()) {
                StructuredGraph newGraph = makeGraph(method, args, recursiveEntry);
                Debug.metric("SnippetNodeCount[%#s]", method).add(newGraph.getNodeCount());
                if (!UseSnippetGraphCache || args != null) {
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
    public StructuredGraph getSubstitution(ResolvedJavaMethod original, boolean fromBytecodeOnly, int invokeBci) {
        ResolvedJavaMethod substitute = null;
        if (!fromBytecodeOnly) {
            InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(original);
            if (plugin != null) {
                if (!plugin.inlineOnly() || invokeBci >= 0) {
                    if (plugin instanceof MethodSubstitutionPlugin) {
                        MethodSubstitutionPlugin msplugin = (MethodSubstitutionPlugin) plugin;
                        substitute = msplugin.getSubstitute(providers.getMetaAccess());
                    } else {
                        StructuredGraph graph = new IntrinsicGraphBuilder(providers.getMetaAccess(), providers.getConstantReflection(), providers.getStampProvider(), original, invokeBci).buildGraph(plugin);
                        if (graph != null) {
                            return graph;
                        }
                    }
                }
            }
        }
        if (substitute == null) {
            ClassReplacements cr = getClassReplacements(original.getDeclaringClass().getName());
            substitute = cr == null ? null : cr.methodSubstitutions.get(original);
        }
        if (substitute == null) {
            return null;
        }
        StructuredGraph graph = graphs.get(substitute);
        if (graph == null) {
            graph = makeGraph(substitute, null, original);
            graph.freeze();
            graphs.putIfAbsent(substitute, graph);
            graph = graphs.get(substitute);
        }
        assert graph.isFrozen();
        return graph;

    }

    private SubstitutionGuard getGuard(Class<? extends SubstitutionGuard> guardClass) {
        if (guardClass != SubstitutionGuard.class) {
            Constructor<?>[] constructors = guardClass.getConstructors();
            if (constructors.length != 1) {
                throw new JVMCIError("Substitution guard " + guardClass.getSimpleName() + " must have a single public constructor");
            }
            Constructor<?> constructor = constructors[0];
            Class<?>[] paramTypes = constructor.getParameterTypes();
            // Check for supported constructor signatures
            try {
                Object[] args = new Object[constructor.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    Object arg = snippetReflection.getSubstitutionGuardParameter(paramTypes[i]);
                    if (arg != null) {
                        args[i] = arg;
                    } else if (paramTypes[i].isInstance(target.arch)) {
                        args[i] = target.arch;
                    } else {
                        throw new JVMCIError("Unsupported type %s in substitution guard constructor: %s", paramTypes[i].getName(), constructor);
                    }
                }

                return (SubstitutionGuard) constructor.newInstance(args);
            } catch (Exception e) {
                throw new JVMCIError(e);
            }
        }
        return null;
    }

    private static boolean checkSubstitutionInternalName(Class<?> substitutions, String internalName) {
        ClassSubstitution cs = substitutions.getAnnotation(ClassSubstitution.class);
        assert cs != null : substitutions + " must be annotated by " + ClassSubstitution.class.getSimpleName();
        if (cs.value() == ClassSubstitution.class) {
            for (String className : cs.className()) {
                if (toInternalName(className).equals(internalName)) {
                    return true;
                }
            }
            assert false : internalName + " not found in " + Arrays.toString(cs.className());
        } else {
            String originalInternalName = toInternalName(cs.value().getName());
            assert originalInternalName.equals(internalName) : originalInternalName + " != " + internalName;
        }
        return true;
    }

    public void registerSubstitutions(Type original, Class<?> substitutionClass) {
        String internalName = toInternalName(original.getTypeName());
        assert checkSubstitutionInternalName(substitutionClass, internalName);
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
    protected ResolvedJavaMethod registerMethodSubstitution(ClassReplacements cr, Executable originalMember, Method substituteMethod) {
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        ResolvedJavaMethod substitute = metaAccess.lookupJavaMethod(substituteMethod);
        ResolvedJavaMethod original = metaAccess.lookupJavaMethod(originalMember);
        if (Debug.isLogEnabled()) {
            Debug.log("substitution: %s --> %s", original.format("%H.%n(%p) %r"), substitute.format("%H.%n(%p) %r"));
        }

        cr.methodSubstitutions.put(original, substitute);
        return original;
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution.
     *
     * @param method the snippet or method substitution for which a graph will be created
     * @param args
     * @param original the original method if {@code method} is a {@linkplain MethodSubstitution
     *            substitution} otherwise null
     */
    public StructuredGraph makeGraph(ResolvedJavaMethod method, Object[] args, ResolvedJavaMethod original) {
        try (OverrideScope s = OptionValue.override(DeoptALot, false)) {
            return createGraphMaker(method, original).makeGraph(args);
        }
    }

    /**
     * Can be overridden to return an object that specializes various parts of graph preprocessing.
     */
    protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
        return new GraphMaker(this, substitute, original);
    }

    /**
     * Creates and preprocesses a graph for a replacement.
     */
    public static class GraphMaker {
        /** The replacements object that the graphs are created for. */
        protected final ReplacementsImpl replacements;

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

        protected GraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            this.replacements = replacements;
            this.method = substitute;
            this.substitutedMethod = substitutedMethod;
        }

        public StructuredGraph makeGraph(Object[] args) {
            try (Scope s = Debug.scope("BuildSnippetGraph", method)) {
                assert method.hasBytecodes() : method;
                StructuredGraph graph = buildInitialGraph(method, args);

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
            int sideEffectCount = 0;
            assert (sideEffectCount = graph.getNodes().filter(e -> hasSideEffect(e)).count()) >= 0;
            new ConvertDeoptimizeToGuardPhase().apply(graph, null);
            assert sideEffectCount == graph.getNodes().filter(e -> hasSideEffect(e)).count() : "deleted side effecting node";

            new DeadCodeEliminationPhase(Required).apply(graph);
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
                                ResolvedJavaType throwableType = replacements.providers.getMetaAccess().lookupJavaType(Throwable.class);
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

        /**
         * Builds the initial graph for a snippet.
         */
        protected StructuredGraph buildInitialGraph(final ResolvedJavaMethod methodToParse, Object[] args) {
            // Replacements cannot have optimistic assumptions since they have
            // to be valid for the entire run of the VM.
            final StructuredGraph graph = new StructuredGraph(methodToParse, AllowAssumptions.NO);

            // They will also never evolve or have breakpoints set in them
            graph.disableInlinedMethodRecording();

            try (Scope s = Debug.scope("buildInitialGraph", graph)) {
                MetaAccessProvider metaAccess = replacements.providers.getMetaAccess();

                Plugins plugins = new Plugins(replacements.graphBuilderPlugins);
                GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                if (args != null) {
                    plugins.setParameterPlugin(new ConstantBindingParameterPlugin(args, plugins.getParameterPlugin(), metaAccess, replacements.snippetReflection));
                }

                IntrinsicContext initialIntrinsicContext = null;
                if (method.getAnnotation(Snippet.class) == null) {
                    // Post-parse inlined intrinsic
                    initialIntrinsicContext = new IntrinsicContext(substitutedMethod, method, INLINE_AFTER_PARSING);
                } else {
                    // Snippet
                    ResolvedJavaMethod original = substitutedMethod != null ? substitutedMethod : method;
                    initialIntrinsicContext = new IntrinsicContext(original, method, INLINE_AFTER_PARSING);
                }

                createGraphBuilder(metaAccess, replacements.providers.getStampProvider(), replacements.providers.getConstantReflection(), config, OptimisticOptimizations.NONE, initialIntrinsicContext).apply(
                                graph);

                if (OptCanonicalizer.getValue()) {
                    new CanonicalizerPhase().apply(graph, new PhaseContext(replacements.providers));
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            return graph;
        }

        protected Instance createGraphBuilder(MetaAccessProvider metaAccess, StampProvider stampProvider, ConstantReflectionProvider constantReflection, GraphBuilderConfiguration graphBuilderConfig,
                        OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            return new GraphBuilderPhase.Instance(metaAccess, stampProvider, constantReflection, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
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
    public static Class<?> resolveClass(String className, boolean optional) {
        try {
            // Need to use launcher class path to handle classes
            // that are not on the boot class path
            ClassLoader cl = Launcher.getLauncher().getClassLoader();
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            if (optional) {
                return null;
            }
            throw new JVMCIError("Could not resolve type " + className);
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
        }
        return result;
    }

    public boolean hasSubstitution(ResolvedJavaMethod method, boolean fromBytecodeOnly, int callerBci) {
        if (!fromBytecodeOnly) {
            InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method);
            if (plugin != null) {
                if (!plugin.inlineOnly() || callerBci >= 0) {
                    return true;
                }
            }
        }
        return getSubstitutionMethod(method) != null;
    }

    public ResolvedJavaMethod getSubstitutionMethod(ResolvedJavaMethod original) {
        ClassReplacements cr = getClassReplacements(original.getDeclaringClass().getName());
        return cr == null ? null : cr.methodSubstitutions.get(original);
    }

    @Override
    public void registerSnippetTemplateCache(SnippetTemplateCache templates) {
        assert snippetTemplateCache.get(templates.getClass().getName()) == null;
        snippetTemplateCache.put(templates.getClass().getName(), templates);
    }

    @Override
    public <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass) {
        SnippetTemplateCache ret = snippetTemplateCache.get(templatesClass.getName());
        return templatesClass.cast(ret);
    }
}
