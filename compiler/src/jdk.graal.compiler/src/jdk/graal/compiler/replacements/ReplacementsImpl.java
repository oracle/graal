/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.core.common.GraalOptions.UseSnippetGraphCache;
import static jdk.graal.compiler.debug.DebugOptions.DebugStubsAndSnippets;
import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;
import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;
import static jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.java.GraphBuilderPhase.Instance;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.nodes.spi.SnippetParameterInfo;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordOperationPlugin;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class ReplacementsImpl implements Replacements, InlineInvokePlugin {

    @Override
    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers.copyWith(this);
    }

    protected Providers providers;
    public final TargetDescription target;
    protected GraphBuilderConfiguration.Plugins graphBuilderPlugins;
    private final DebugHandlersFactory debugHandlersFactory;

    /**
     * The preprocessed replacement graphs. This is keyed by a pair of a method and options because
     * options influence both parsing and later lowering of the replacement graph. We must not
     * inline a snippet graph built with some set of options into a graph that is being compiled
     * with a different set of options.
     */
    protected final ConcurrentMap<Pair<ResolvedJavaMethod, OptionValues>, StructuredGraph> graphs;

    /**
     * The default {@link BytecodeProvider} to use for accessing the bytecode of a replacement if
     * the replacement doesn't provide another {@link BytecodeProvider}.
     */
    protected final BytecodeProvider defaultBytecodeProvider;

    public void setGraphBuilderPlugins(GraphBuilderConfiguration.Plugins plugins) {
        assert this.graphBuilderPlugins == null;
        this.graphBuilderPlugins = plugins;
    }

    @Override
    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getInjectedArgument(Class<T> capability) {
        if (capability.equals(TargetDescription.class)) {
            return (T) target;
        }
        if (capability.equals(ForeignCallsProvider.class)) {
            return (T) getProviders().getForeignCalls();
        }
        if (capability.equals(ArrayCopyForeignCalls.class) && getProviders().getForeignCalls() instanceof ArrayCopyForeignCalls) {
            return (T) getProviders().getForeignCalls();
        }
        if (capability.equals(SnippetReflectionProvider.class)) {
            return (T) getProviders().getSnippetReflection();
        }
        if (capability.isAssignableFrom(WordTypes.class)) {
            return (T) getProviders().getWordTypes();
        }
        throw GraalError.shouldNotReachHere(capability.toString()); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public Stamp getInjectedStamp(Class<?> type, boolean nonNull) {
        JavaKind kind = JavaKind.fromJavaClass(type);
        if (kind == JavaKind.Object) {
            WordTypes wordTypes = getProviders().getWordTypes();
            if (wordTypes.isWord(type)) {
                return wordTypes.getWordStamp(type);
            } else {
                ResolvedJavaType returnType = providers.getMetaAccess().lookupJavaType(type);
                return StampFactory.object(TypeReference.createWithoutAssumptions(returnType), nonNull);
            }
        } else {
            return StampFactory.forKind(kind);
        }
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (method.getAnnotation(Node.NodeIntrinsic.class) != null || method.getAnnotation(Fold.class) != null) {
                return GeneratedInvocationPlugin.class;
            }
            if (method.getAnnotation(Word.Operation.class) != null) {
                return WordOperationPlugin.class;
            }
        }
        return null;
    }

    private static final int MAX_GRAPH_INLINING_DEPTH = 100; // more than enough

    /**
     * Determines whether a given method should be inlined based on whether it has a substitution or
     * whether the inlining context is already within a substitution.
     *
     * @return an object specifying how {@code method} is to be inlined or null if it should not be
     *         inlined based on substitution related criteria
     */
    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (b.parsingIntrinsic()) {
            assert b.getDepth() < MAX_GRAPH_INLINING_DEPTH : "inlining limit exceeded";

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, defaultBytecodeProvider);
        } else {
            assert IS_BUILDING_NATIVE_IMAGE || method.getAnnotation(NodeIntrinsic.class) == null : String.format("@%s method %s must only be called from within a replacement%n%s",
                            NodeIntrinsic.class.getSimpleName(),
                            method.format("%h.%n"), b);
        }
        return null;
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (b.parsingIntrinsic()) {
            IntrinsicContext intrinsic = b.getIntrinsic();
            if (!intrinsic.isCallToOriginal(method)) {
                Class<? extends GraphBuilderPlugin> pluginClass = getIntrinsifyingPlugin(method);
                if (pluginClass != null) {
                    String methodDesc = method.format("%H.%n(%p)");
                    throw new GraalError("Call to %s should have been intrinsified by a %s. " +
                                    "This is typically caused by Eclipse failing to run an annotation " +
                                    "processor. This can usually be fixed by forcing Eclipse to rebuild " +
                                    "the source file in which %s is declared",
                                    methodDesc, pluginClass.getSimpleName(), methodDesc);
                }
                throw new GraalError("All non-recursive calls in the intrinsic %s must be inlined or intrinsified: found call to %s",
                                intrinsic.getIntrinsicMethod().format("%H.%n(%p)"), method.format("%h.%n(%p)"));
            }
        }
    }

    // This map is key'ed by a class name instead of a Class object so that
    // it is stable across VM executions (in support of replay compilation).
    private final EconomicMap<String, SnippetTemplateCache> snippetTemplateCache;

    @SuppressWarnings("this-escape")
    public ReplacementsImpl(DebugHandlersFactory debugHandlersFactory, Providers providers, BytecodeProvider bytecodeProvider,
                    TargetDescription target) {
        this.providers = providers.copyWith(this);
        this.target = target;
        this.graphs = new ConcurrentHashMap<>();
        this.snippetTemplateCache = EconomicMap.create(Equivalence.DEFAULT);
        this.defaultBytecodeProvider = bytecodeProvider;
        this.debugHandlersFactory = debugHandlersFactory;

    }

    private static final TimerKey SnippetPreparationTime = DebugContext.timer("SnippetPreparationTime");

    @Override
    public DebugContext openSnippetDebugContext(String idPrefix, ResolvedJavaMethod method, DebugContext outer, OptionValues options) {
        if (DebugStubsAndSnippets.getValue(options)) {
            return openDebugContext(idPrefix, method, options, outer, false);
        } else if (DumpOnError.getValue(options)) {
            // If DumpOnError is enabled create a DebugContext with only scopes enabled and the
            // debugHandlersFactory configured. This ensures that the snippet graphs can be found
            // from the scope context and that the dump handlers are available to write out
            // the graph dumps.
            return openDebugContext(idPrefix, method, options, outer, true);
        }
        return DebugContext.disabled(options);
    }

    public DebugContext openSnippetDebugContext(String idPrefix, ResolvedJavaMethod method, OptionValues options) {
        return openSnippetDebugContext(idPrefix, method, DebugContext.forCurrentThread(), options);
    }

    public DebugContext openDebugContext(String idPrefix, ResolvedJavaMethod method, OptionValues options) {
        return openDebugContext(idPrefix, method, options, DebugContext.forCurrentThread(), false);
    }

    private static final AtomicInteger nextDebugContextId = new AtomicInteger();

    private DebugContext openDebugContext(String idPrefix, ResolvedJavaMethod method, OptionValues options, DebugContext outer, boolean disabled) {
        Description description = new Description(method, idPrefix + nextDebugContextId.incrementAndGet());
        return new Builder(options, debugHandlersFactory).globalMetrics(outer.getGlobalMetrics()).description(description).disabled(disabled).build();
    }

    @Override
    @SuppressWarnings("try")
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, OptionValues options) {
        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        assert method.hasBytecodes() : "Snippet must not be abstract or native";

        Pair<ResolvedJavaMethod, OptionValues> cacheKey = Pair.create(method, options);
        StructuredGraph graph = UseSnippetGraphCache.getValue(options) ? graphs.get(cacheKey) : null;
        if (graph == null || (trackNodeSourcePosition && !graph.trackNodeSourcePosition())) {
            try (DebugContext debug = openSnippetDebugContext("Snippet_", method, options);
                            DebugCloseable a = SnippetPreparationTime.start(debug)) {
                StructuredGraph newGraph = makeGraph(debug, defaultBytecodeProvider, method, args, nonNullParameters, recursiveEntry, trackNodeSourcePosition, replaceePosition, INLINE_AFTER_PARSING);
                DebugContext.counter("SnippetNodeCount[%#s]", method).add(newGraph.getDebug(), newGraph.getNodeCount());
                if (!UseSnippetGraphCache.getValue(options) || args != null) {
                    return newGraph;
                }
                newGraph.freeze();
                if (graph != null) {
                    graphs.replace(cacheKey, graph, newGraph);
                } else {
                    graphs.putIfAbsent(cacheKey, newGraph);
                }
                graph = graphs.get(cacheKey);
            }
        }
        assert !trackNodeSourcePosition || graph.trackNodeSourcePosition();
        return graph;
    }

    @Override
    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        return new SnippetParameterInfo(method);
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        return method.getAnnotation(Snippet.class) != null;
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        // No initialization needed as snippet graphs are created on demand in getSnippet
    }

    @Override
    public void registerConditionalPlugin(InvocationPlugin plugin) {
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, OptionValues options) {
        return false;
    }

    @Override
    public BytecodeProvider getDefaultReplacementBytecodeProvider() {
        return defaultBytecodeProvider;
    }

    @Override
    public StructuredGraph getInlineSubstitution(ResolvedJavaMethod method, int invokeBci, Invoke.InlineControl inlineControl, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    AllowAssumptions allowAssumptions, OptionValues options) {
        return null;
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution.
     *
     * @param bytecodeProvider how to access the bytecode of {@code method}
     * @param method the snippet or method substitution for which a graph will be created
     * @param args
     * @param nonNullParameters
     * @param original XXX always null?
     * @param trackNodeSourcePosition record source information
     * @param context {@link CompilationContext compilation context} for the graph
     */
    public StructuredGraph makeGraph(DebugContext debug, BytecodeProvider bytecodeProvider, ResolvedJavaMethod method, Object[] args, BitSet nonNullParameters, ResolvedJavaMethod original,
                    boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition, CompilationContext context) {
        return createGraphMaker(method, original).makeGraph(debug, bytecodeProvider, args, nonNullParameters, trackNodeSourcePosition, replaceePosition, context);
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution with a context of .
     * {@link CompilationContext#INLINE_AFTER_PARSING} .
     *
     * @param bytecodeProvider how to access the bytecode of {@code method}
     * @param method the snippet or method substitution for which a graph will be created
     * @param args
     * @param nonNullParameters
     * @param original XXX always null?
     * @param trackNodeSourcePosition record source information
     */
    public final StructuredGraph makeGraph(DebugContext debug, BytecodeProvider bytecodeProvider, ResolvedJavaMethod method, Object[] args, BitSet nonNullParameters, ResolvedJavaMethod original,
                    boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition) {
        return makeGraph(debug, bytecodeProvider, method, args, nonNullParameters, original, trackNodeSourcePosition, replaceePosition, INLINE_AFTER_PARSING);
    }

    /**
     * Can be overridden to return an object that specializes various parts of graph preprocessing.
     */
    protected abstract GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original);

    /**
     * Creates and preprocesses a graph for a replacement.
     */
    public abstract static class GraphMaker {

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

        public GraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            this.replacements = replacements;
            this.method = substitute;
            this.substitutedMethod = substitutedMethod;
        }

        @SuppressWarnings("try")
        public StructuredGraph makeGraph(DebugContext debug, BytecodeProvider bytecodeProvider, Object[] args, BitSet nonNullParameters, boolean trackNodeSourcePosition,
                        NodeSourcePosition replaceePosition, CompilationContext context) {
            try (DebugContext.Scope s = debug.scope("BuildSnippetGraph", method)) {
                assert method.hasBytecodes() : method;
                StructuredGraph graph = buildInitialGraph(debug, bytecodeProvider, method, args, nonNullParameters, trackNodeSourcePosition, replaceePosition, context);

                finalizeGraph(graph);

                debug.dump(DebugContext.INFO_LEVEL, graph, "%s: Final", method.getName());

                return graph;
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

        /**
         * Does final processing of a snippet graph.
         */
        protected void finalizeGraph(StructuredGraph graph) {
            if (!GraalOptions.SnippetCounters.getValue(graph.getOptions()) || graph.getNodes().filter(SnippetCounterNode.class).isEmpty()) {
                int sideEffectCount = 0;
                int countSE = graph.getNodes().filter(e -> hasSideEffect(e)).count();
                assert (sideEffectCount = countSE) >= 0 : "Side effect counts must match " + sideEffectCount + " " + countSE;
                new ConvertDeoptimizeToGuardPhase(CanonicalizerPhase.create()).apply(graph, replacements.getProviders());
                assert sideEffectCount == graph.getNodes().filter(e -> hasSideEffect(e)).count() : "deleted side effecting node";

                new DeadCodeEliminationPhase(Required).apply(graph);
            } else {
                // ConvertDeoptimizeToGuardPhase will eliminate snippet counters on paths
                // that terminate in a deopt so we disable it if the graph contains
                // snippet counters. The trade off is that we miss out on guard
                // coalescing opportunities.
            }
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

        static class EncodedIntrinsicContext extends IntrinsicContext {
            EncodedIntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod intrinsic, BytecodeProvider bytecodeProvider, CompilationContext compilationContext,
                            boolean allowPartialIntrinsicArgumentMismatch) {
                super(method, intrinsic, bytecodeProvider, compilationContext, allowPartialIntrinsicArgumentMismatch);
            }

            @Override
            public boolean isDeferredInvoke(StateSplit stateSplit) {
                if (IS_IN_NATIVE_IMAGE) {
                    throw GraalError.shouldNotReachHere("unused in libgraal"); // ExcludeFromJacocoGeneratedReport
                }
                if (stateSplit instanceof Invoke) {
                    Invoke invoke = (Invoke) stateSplit;
                    ResolvedJavaMethod method = invoke.callTarget().targetMethod();
                    if (method == null) {
                        return false;
                    }
                    if (method.getAnnotation(Fold.class) != null) {
                        /*
                         * In SVM, @Fold methods cannot be handled eagerly, e.g.,
                         * because @ConstantParameter arguments are not yet constant. Thus, the
                         * handling of these invokes is deferred (see
                         * SubstrateReplacements.SnippetInlineInvokePlugin).
                         */
                        return true;
                    }
                    Node.NodeIntrinsic annotation = method.getAnnotation(Node.NodeIntrinsic.class);
                    if (annotation != null && !annotation.hasSideEffect()) {
                        return true;
                    }
                }
                return false;
            }
        }

        /**
         * Builds the initial graph for a replacement.
         */
        @SuppressWarnings("try")
        protected StructuredGraph buildInitialGraph(DebugContext debug, BytecodeProvider bytecodeProvider, final ResolvedJavaMethod methodToParse, Object[] args, BitSet nonNullParameters,
                        boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition, CompilationContext context) {
            // @formatter:off
            // Replacements cannot have optimistic assumptions since they have
            // to be valid for the entire run of the VM.
            final StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug, AllowAssumptions.NO).
                            method(methodToParse).
                            trackNodeSourcePosition(trackNodeSourcePosition).
                            callerContext(replaceePosition).
                            setIsSubstitution(true).
                            build();
            // @formatter:on

            // Replacements are not user code so they do not participate in unsafe access
            // tracking
            graph.disableUnsafeAccessTracking();

            try (DebugContext.Scope s = debug.scope("buildInitialGraph", graph)) {
                MetaAccessProvider metaAccess = replacements.getProviders().getMetaAccess();

                Plugins plugins = new Plugins(replacements.graphBuilderPlugins);
                GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                if (args != null) {
                    plugins.prependParameterPlugin(new ConstantBindingParameterPlugin(args, metaAccess, replacements.getProviders().getSnippetReflection()));
                }
                if (nonNullParameters != null && !nonNullParameters.isEmpty()) {
                    plugins.appendParameterPlugin(new NonNullParameterPlugin(nonNullParameters));
                }

                IntrinsicContext initialIntrinsicContext = null;
                Snippet snippetAnnotation = null;
                if (!IS_IN_NATIVE_IMAGE) {
                    snippetAnnotation = method.getAnnotation(Snippet.class);
                }
                if (snippetAnnotation == null) {
                    // Post-parse inlined intrinsic
                    initialIntrinsicContext = new EncodedIntrinsicContext(substitutedMethod, method, bytecodeProvider, context, false);
                } else {
                    // Snippet
                    ResolvedJavaMethod original = substitutedMethod != null ? substitutedMethod : method;
                    initialIntrinsicContext = new EncodedIntrinsicContext(original, method, bytecodeProvider, context,
                                    snippetAnnotation.allowPartialIntrinsicArgumentMismatch());
                }

                createGraphBuilder(replacements.providers, config, OptimisticOptimizations.NONE, initialIntrinsicContext).apply(graph);

                CanonicalizerPhase.create().apply(graph, replacements.providers);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            return graph;
        }

        protected abstract Instance createGraphBuilder(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                        IntrinsicContext initialIntrinsicContext);
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

    @Override
    public JavaKind getWordKind() {
        return getProviders().getWordTypes().getWordKind();
    }
}
