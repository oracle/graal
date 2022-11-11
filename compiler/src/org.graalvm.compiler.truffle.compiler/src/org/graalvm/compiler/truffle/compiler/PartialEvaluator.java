/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.EncodedGraphCache;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ExcludeAssertions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumGraalGraphSize;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.NodeSourcePositions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ParsePEGraphsWithAssumptions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PrintExpansionHistogram;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TracePerformanceWarnings;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceTransferToInterpreter;

import java.net.URI;
import java.nio.Buffer;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.SourceLanguagePosition;
import org.graalvm.compiler.graph.SourceLanguagePositionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.phases.contract.NodeCostUtil;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.CachingPEGraphDecoder;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.serviceprovider.SpeculationReasonGroup;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime.InlineKind;
import org.graalvm.compiler.truffle.common.TruffleInliningData;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.compiler.truffle.compiler.phases.DeoptimizeOnExceptionPhase;
import org.graalvm.compiler.truffle.compiler.phases.InstrumentPhase;
import org.graalvm.compiler.truffle.compiler.substitutions.GraphBuilderInvocationPluginProvider;
import org.graalvm.compiler.truffle.compiler.substitutions.KnownTruffleTypes;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.TruffleOptions;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public abstract class PartialEvaluator {

    // Configs
    protected final TruffleCompilerConfiguration config;
    // TODO GR-37097 Move to TruffleCompilerImpl
    volatile GraphBuilderConfiguration configForParsing;
    // Methods
    final ResolvedJavaMethod callDirectMethod;
    protected final ResolvedJavaMethod callInlined;
    final ResolvedJavaMethod callIndirectMethod;
    private final ResolvedJavaMethod profiledPERoot;
    final ResolvedJavaMethod callBoundary;
    // Plugins
    private final GraphBuilderConfiguration configPrototype;
    private final InvocationPlugins firstTierDecodingPlugins;
    private final InvocationPlugins lastTierDecodingPlugins;
    protected final PELoopExplosionPlugin loopExplosionPlugin = new PELoopExplosionPlugin();
    private final NodePlugin[] nodePlugins;
    // Misc
    final KnownTruffleTypes knownTruffleTypes;
    /**
     * Holds instrumentation options initialized in {@link #initialize(OptionValues)} method before
     * the first compilation. These options are not engine aware.
     */
    // TODO GR-37097 Move to TruffleCompilerImpl
    public volatile InstrumentPhase.InstrumentationConfiguration instrumentationCfg;
    /**
     * The instrumentation object is used by the Truffle instrumentation to count executions. The
     * value is lazily initialized the first time it is requested because it depends on the Truffle
     * options, and tests that need the instrumentation table need to override these options after
     * the TruffleRuntime object is created.
     */
    // TODO GR-37097 Move to TruffleCompilerImpl
    protected volatile InstrumentPhase.Instrumentation instrumentation;
    protected final TruffleConstantFieldProvider compilationLocalConstantProvider;
    protected boolean allowAssumptionsDuringParsing;
    protected boolean persistentEncodedGraphCache;

    public PartialEvaluator(TruffleCompilerConfiguration config, GraphBuilderConfiguration configForRoot, KnownTruffleTypes knownFields) {
        this.config = config;
        this.knownTruffleTypes = knownFields;

        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        final MetaAccessProvider metaAccess = this.config.lastTier().providers().getMetaAccess();
        ResolvedJavaType type = runtime.resolveType(metaAccess, "org.graalvm.compiler.truffle.runtime.OptimizedCallTarget");
        ResolvedJavaMethod[] methods = type.getDeclaredMethods();
        this.callDirectMethod = findRequiredMethod(type, methods, "callDirect", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.callInlined = findRequiredMethod(type, methods, "callInlined", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.callIndirectMethod = findRequiredMethod(type, methods, "callIndirect", "(Lcom/oracle/truffle/api/nodes/Node;[Ljava/lang/Object;)Ljava/lang/Object;");
        this.profiledPERoot = findRequiredMethod(type, methods, "profiledPERoot", "([Ljava/lang/Object;)Ljava/lang/Object;");
        this.callBoundary = findRequiredMethod(type, methods, "callBoundary", "([Ljava/lang/Object;)Ljava/lang/Object;");

        this.configPrototype = createGraphBuilderConfig(configForRoot, true);
        this.firstTierDecodingPlugins = createDecodingInvocationPlugins(config.firstTier().partialEvaluator(), configForRoot.getPlugins(), config.firstTier().providers());
        this.lastTierDecodingPlugins = createDecodingInvocationPlugins(config.lastTier().partialEvaluator(), configForRoot.getPlugins(), config.lastTier().providers());
        this.nodePlugins = createNodePlugins(configForRoot.getPlugins());
        this.compilationLocalConstantProvider = new TruffleConstantFieldProvider(
                        this.config.lastTier().providers().getConstantFieldProvider(),
                        this.config.lastTier().providers().getMetaAccess(),
                        knownFields);
    }

    protected void initialize(OptionValues options) {
        instrumentationCfg = new InstrumentPhase.InstrumentationConfiguration(options);
        boolean needSourcePositions = options.get(NodeSourcePositions) ||
                        instrumentationCfg.instrumentBranches ||
                        instrumentationCfg.instrumentBoundaries ||
                        !options.get(TracePerformanceWarnings).isEmpty() ||
                        (TruffleOptions.AOT && options.get(TraceTransferToInterpreter));
        configForParsing = configPrototype.withNodeSourcePosition(configPrototype.trackNodeSourcePosition() || needSourcePositions).withOmitAssertions(
                        options.get(ExcludeAssertions));

        this.allowAssumptionsDuringParsing = options.get(ParsePEGraphsWithAssumptions);
        // Graphs with assumptions cannot be cached across compilations, so the persistent cache is
        // disabled if assumptions are allowed.
        this.persistentEncodedGraphCache = options.get(EncodedGraphCache) && !options.get(ParsePEGraphsWithAssumptions);
    }

    public EconomicMap<ResolvedJavaMethod, EncodedGraph> getOrCreateEncodedGraphCache() {
        return EconomicMap.create();
    }

    /**
     * Gets the instrumentation manager associated with this compiler, creating it first if
     * necessary. Each compiler instance has its own instrumentation manager.
     */
    public InstrumentPhase.Instrumentation getInstrumentation() {
        if (instrumentation == null) {
            synchronized (this) {
                if (instrumentation == null) {
                    if (instrumentationCfg == null) {
                        throw new IllegalStateException("PartialEvaluator is not yet initialized");
                    }
                    long[] accessTable = new long[instrumentationCfg.instrumentationTableSize];
                    instrumentation = new InstrumentPhase.Instrumentation(accessTable);
                }
            }
        }
        return instrumentation;
    }

    static ResolvedJavaMethod findRequiredMethod(ResolvedJavaType declaringClass, ResolvedJavaMethod[] methods, String name, String descriptor) {
        for (ResolvedJavaMethod method : methods) {
            if (method.getName().equals(name) && method.getSignature().toMethodDescriptor().equals(descriptor)) {
                return method;
            }
        }
        throw new NoSuchMethodError(declaringClass.toJavaName() + "." + name + descriptor);
    }

    public static InlineInvokePlugin.InlineInfo asInlineInfo(ResolvedJavaMethod method) {
        final TruffleCompilerRuntime.InlineKind inlineKind = TruffleCompilerRuntime.getRuntime().getInlineKind(method, true);
        switch (inlineKind) {
            case DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION:
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION;
            case DO_NOT_INLINE_NO_EXCEPTION:
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            case DO_NOT_INLINE_WITH_EXCEPTION:
            case DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION:
                return InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            case INLINE:
                return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
            default:
                throw new IllegalArgumentException(String.valueOf(inlineKind));
        }
    }

    public Providers getProviders() {
        return config.lastTier().providers();
    }

    /**
     * Returns the root {@link GraphBuilderConfiguration}. The root configuration provides plugins
     * used by this {@link PartialEvaluator} but it's not configured with engine options. The root
     * configuration should be used in image generation time where the {@link PartialEvaluator} is
     * not yet initialized with engine options. At runtime the {@link #getConfig} should be used.
     */
    public GraphBuilderConfiguration getConfigPrototype() {
        return configPrototype;
    }

    /**
     * Returns the {@link GraphBuilderConfiguration} used by parsing. The returned configuration is
     * configured with engine options. In the image generation time the {@link PartialEvaluator} is
     * not yet initialized and the {@link #getConfigPrototype} should be used instead.
     *
     * @throws IllegalStateException when called on non initialized {@link PartialEvaluator}
     */
    public GraphBuilderConfiguration getConfig() {
        if (configForParsing == null) {
            throw new IllegalStateException("PartialEvaluator is not yet initialized");
        }
        return configForParsing;
    }

    public KnownTruffleTypes getKnownTruffleTypes() {
        return knownTruffleTypes;
    }

    public ResolvedJavaMethod[] getCompilationRootMethods() {
        return new ResolvedJavaMethod[]{profiledPERoot, callInlined, callDirectMethod};
    }

    public ResolvedJavaMethod[] getNeverInlineMethods() {
        return new ResolvedJavaMethod[]{callDirectMethod, callIndirectMethod};
    }

    public ResolvedJavaMethod getCallDirect() {
        return callDirectMethod;
    }

    public ResolvedJavaMethod getCallInlined() {
        return callInlined;
    }

    /**
     * Keeps track of the approximate graph size when nodes are added and removed and if this size
     * goes over the allowed limit (MaximumGraalGraphSize) checks the actual size and bails out if
     * the graph is actually too big.
     */
    private static final class GraphSizeListener extends Graph.NodeEventListener {

        private final int graphSizeLimit;
        private final StructuredGraph graph;
        private int graphSize;

        private GraphSizeListener(OptionValues options, StructuredGraph graph) {
            this.graphSizeLimit = options.get(MaximumGraalGraphSize);
            this.graph = graph;
            this.graphSize = NodeCostUtil.computeGraphSize(graph);
        }

        @Override
        public void nodeAdded(Node node) {
            increaseSizeAndCheckLimit(node);
        }

        private void increaseSizeAndCheckLimit(Node node) {
            graphSize += node.estimatedNodeSize().value;
            checkLimit();
        }

        private void checkLimit() {
            if (graphSize > graphSizeLimit) {
                throw new GraphTooBigBailoutException(
                                "Graph too big to safely compile. Node count: " + graph.getNodeCount() + ". Graph Size: " + graphSize + ". Limit: " + graphSizeLimit + ".");
            }
        }

        @Override
        public void beforeDecodingFields(Node node) {
            graphSize -= node.estimatedNodeSize().value;
        }

        @Override
        public void afterDecodingFields(Node node) {
            increaseSizeAndCheckLimit(node);
        }

        @Override
        public void nodeRemoved(Node node) {
            graphSize -= node.estimatedNodeSize().value;
        }
    }

    /**
     * Hook for subclasses: customize the StructuredGraph.
     */
    protected StructuredGraph.Builder customizeStructuredGraphBuilder(StructuredGraph.Builder builder) {
        return builder;
    }

    /**
     * Hook for subclasses: return a customized compilation root for a specific call target.
     *
     * @param compilable the Truffle AST being compiled.
     */
    public ResolvedJavaMethod rootForCallTarget(CompilableTruffleAST compilable) {
        return profiledPERoot;
    }

    /**
     * Hook for subclasses: return a customized compilation root when inlining a specific call
     * target.
     *
     * @param compilable the Truffle AST being compiled.
     */
    public ResolvedJavaMethod inlineRootForCallTarget(CompilableTruffleAST compilable) {
        return callInlined;
    }

    private class InterceptReceiverPlugin implements ParameterPlugin {

        private final CompilableTruffleAST compilable;

        InterceptReceiverPlugin(CompilableTruffleAST compilable) {
            this.compilable = compilable;
        }

        @Override
        public FloatingNode interceptParameter(GraphBuilderTool b, int index, StampPair stamp) {
            if (index == 0) {
                JavaConstant c = compilable.asJavaConstant();
                return ConstantNode.forConstant(c, config.lastTier().providers().getMetaAccess());
            }
            return null;
        }
    }

    private static final class TruffleSourceLanguagePositionProvider implements SourceLanguagePositionProvider {

        private TruffleInliningData inliningPlan;

        private TruffleSourceLanguagePositionProvider(TruffleInliningData inliningPlan) {
            this.inliningPlan = inliningPlan;
        }

        @Override
        public SourceLanguagePosition getPosition(JavaConstant node) {
            final TruffleSourceLanguagePosition position = inliningPlan.getPosition(node);
            return position == null ? null : new SourceLanguagePositionImpl(position);
        }
    }

    private static final class PELoopExplosionPlugin implements LoopExplosionPlugin {

        @Override
        public LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method) {
            TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntime();
            TruffleCompilerRuntime.LoopExplosionKind explosionKind = rt.getLoopExplosionKind(method);
            switch (explosionKind) {
                case NONE:
                    return LoopExplosionKind.NONE;
                case FULL_EXPLODE:
                    return LoopExplosionKind.FULL_EXPLODE;
                case FULL_EXPLODE_UNTIL_RETURN:
                    return LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN;
                case FULL_UNROLL:
                    return LoopExplosionKind.FULL_UNROLL;
                case MERGE_EXPLODE:
                    return LoopExplosionKind.MERGE_EXPLODE;
                case FULL_UNROLL_UNTIL_RETURN:
                    return LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN;
                default:
                    throw new IllegalStateException("Unsupported TruffleCompilerRuntime.LoopExplosionKind: " + String.valueOf(explosionKind));
            }
        }
    }

    @SuppressWarnings("unused")
    protected PEGraphDecoder createGraphDecoder(TruffleTierContext context, InvocationPlugins invocationPlugins, InlineInvokePlugin[] inlineInvokePlugins, ParameterPlugin parameterPlugin,
                    NodePlugin[] nodePluginList, SourceLanguagePositionProvider sourceLanguagePositionProvider, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache,
                    Supplier<AutoCloseable> createCachedGraphScope) {
        final GraphBuilderConfiguration newConfig = configForParsing.copy();
        InvocationPlugins parsingInvocationPlugins = newConfig.getPlugins().getInvocationPlugins();

        Plugins plugins = newConfig.getPlugins();
        ReplacementsImpl replacements = (ReplacementsImpl) config.lastTier().providers().getReplacements();
        plugins.clearInlineInvokePlugins();
        plugins.appendInlineInvokePlugin(replacements);
        plugins.appendInlineInvokePlugin(new ParsingInlineInvokePlugin(this, replacements, parsingInvocationPlugins, loopExplosionPlugin));
        if (!context.options.get(PrintExpansionHistogram)) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }
        InvocationPlugins decodingPlugins = context.isFirstTier() ? firstTierDecodingPlugins : lastTierDecodingPlugins;

        DeoptimizeOnExceptionPhase postParsingPhase = new DeoptimizeOnExceptionPhase(
                        method -> TruffleCompilerRuntime.getRuntime().getInlineKind(method, true) == InlineKind.DO_NOT_INLINE_WITH_SPECULATIVE_EXCEPTION);

        Providers compilationUnitProviders = config.lastTier().providers().copyWith(compilationLocalConstantProvider);

        assert !allowAssumptionsDuringParsing || !persistentEncodedGraphCache;
        return new CachingPEGraphDecoder(config.architecture(), context.graph, compilationUnitProviders, newConfig, TruffleCompilerImpl.Optimizations,
                        loopExplosionPlugin, decodingPlugins, inlineInvokePlugins, parameterPlugin, nodePluginList, callInlined,
                        sourceLanguagePositionProvider, postParsingPhase, graphCache, createCachedGraphScope, allowAssumptionsDuringParsing, false, true);
    }

    @SuppressWarnings("try")
    public void doGraphPE(TruffleTierContext context, InlineInvokePlugin inlineInvokePlugin, EconomicMap<ResolvedJavaMethod, EncodedGraph> graphCache) {
        InlineInvokePlugin[] inlineInvokePlugins = new InlineInvokePlugin[]{
                        inlineInvokePlugin
        };
        PEGraphDecoder decoder = createGraphDecoder(context,
                        context.isFirstTier() ? firstTierDecodingPlugins : lastTierDecodingPlugins,
                        inlineInvokePlugins,
                        new InterceptReceiverPlugin(context.compilable),
                        nodePlugins,
                        new TruffleSourceLanguagePositionProvider(context.task.inliningData()),
                        graphCache, getCreateCachedGraphScope());
        GraphSizeListener listener = new GraphSizeListener(context.options, context.graph);
        try (Graph.NodeEventScope ignored = context.graph.trackNodeEvents(listener)) {
            assert !context.graph.isSubstitution();
            decoder.decode(context.graph.method());
        }
        assert listener.graphSize == NodeCostUtil.computeGraphSize(listener.graph);
    }

    /**
     * Gets a closeable producer that will be used in a try-with-resources statement surrounding the
     * creation of encoded graphs. This can be used to ensure that the encoded graphs and its
     * dependencies can be persisted across compilations.
     *
     * The default supplier produces null, a no-op try-with-resources/scope.
     */
    protected Supplier<AutoCloseable> getCreateCachedGraphScope() {
        return () -> null;
    }

    /**
     * Graph-builder configuration is shared between the first- and the second-tier compilations,
     * because the encoded graphs for the partial evaluator are shared between these compilation
     * tiers.
     */
    private GraphBuilderConfiguration createGraphBuilderConfig(GraphBuilderConfiguration graphBuilderConfig, boolean canDelayIntrinsification) {
        GraphBuilderConfiguration newConfig = graphBuilderConfig.copy();
        InvocationPlugins invocationPlugins = newConfig.getPlugins().getInvocationPlugins();
        registerGraphBuilderInvocationPlugins(invocationPlugins, canDelayIntrinsification);
        appendParsingNodePlugins(newConfig.getPlugins());
        return newConfig;
    }

    protected void appendParsingNodePlugins(Plugins plugins) {
        if (JavaVersionUtil.JAVA_SPEC >= 16 && JavaVersionUtil.JAVA_SPEC < 19) {
            ResolvedJavaType memorySegmentProxyType = TruffleCompilerRuntime.getRuntime().resolveType(config.lastTier().providers().getMetaAccess(), "jdk.internal.access.foreign.MemorySegmentProxy");
            for (ResolvedJavaMethod m : memorySegmentProxyType.getDeclaredMethods()) {
                if (m.getName().equals("scope")) {
                    appendMemorySegmentScopePlugin(plugins, m);
                }
            }
        } else if (JavaVersionUtil.JAVA_SPEC >= 19) {
            ResolvedJavaType memorySegmentType = TruffleCompilerRuntime.getRuntime().resolveType(config.lastTier().providers().getMetaAccess(), "jdk.internal.foreign.Scoped");
            for (ResolvedJavaMethod m : memorySegmentType.getDeclaredMethods()) {
                if (m.getName().equals("sessionImpl")) {
                    appendMemorySegmentScopePlugin(plugins, m);
                }
            }
        }
    }

    /**
     * The calls to MemorySegmentProxy.scope() (JDK 17) or Scoped.sessionImpl() (JDK 19) from
     * {@link Buffer} are problematic for Truffle because they would remain as non-inlineable
     * invokes after PE. Because these are virtual calls, we can also not use a
     * {@link InvocationPlugin} during PE to intrinsify the invoke to a deoptimization. Therefore,
     * we already do the intrinsification during bytecode parsing using a {@link NodePlugin},
     * because that is also invoked for virtual calls.
     */
    private static void appendMemorySegmentScopePlugin(Plugins plugins, ResolvedJavaMethod scopeMethod) {
        plugins.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (scopeMethod.equals(method) && !b.needsExplicitException()) {
                    b.add(new DeoptimizeNode(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint));
                    return true;
                }
                return false;
            }
        });
    }

    protected NodePlugin[] createNodePlugins(Plugins plugins) {
        return plugins.getNodePlugins();
    }

    protected void registerGraphBuilderInvocationPlugins(InvocationPlugins invocationPlugins, boolean canDelayIntrinsification) {
        TruffleGraphBuilderPlugins.registerInvocationPlugins(invocationPlugins, canDelayIntrinsification, config.lastTier().providers(), knownTruffleTypes);
        for (GraphBuilderInvocationPluginProvider p : GraalServices.load(GraphBuilderInvocationPluginProvider.class)) {
            p.registerInvocationPlugins(config.lastTier().providers(), config.architecture(), invocationPlugins, canDelayIntrinsification);
        }
    }

    /**
     * Graph-decoding plugins are not shared between the first- and the second-tier compilations,
     * because the different tiers intrinsify the respective methods differently.
     */
    protected InvocationPlugins createDecodingInvocationPlugins(PartialEvaluatorConfiguration peConfig, Plugins parent, Providers tierProviders) {
        @SuppressWarnings("hiding")
        InvocationPlugins decodingInvocationPlugins = new InvocationPlugins(null, parent.getInvocationPlugins());
        registerGraphBuilderInvocationPlugins(decodingInvocationPlugins, false);
        peConfig.registerDecodingInvocationPlugins(decodingInvocationPlugins, false, tierProviders, config.architecture());
        decodingInvocationPlugins.closeRegistration();
        return decodingInvocationPlugins;
    }

    private static final SpeculationReasonGroup TRUFFLE_BOUNDARY_EXCEPTION_SPECULATIONS = new SpeculationReasonGroup("TruffleBoundaryWithoutException", ResolvedJavaMethod.class);

    public static SpeculationReason createTruffleBoundaryExceptionSpeculation(ResolvedJavaMethod targetMethod) {
        return TRUFFLE_BOUNDARY_EXCEPTION_SPECULATIONS.createSpeculationReason(targetMethod);
    }

    private static final class SourceLanguagePositionImpl implements SourceLanguagePosition {
        private final TruffleSourceLanguagePosition delegate;

        SourceLanguagePositionImpl(final TruffleSourceLanguagePosition delegate) {
            this.delegate = delegate;
        }

        @Override
        public String toShortString() {
            return delegate.getDescription();
        }

        @Override
        public int getOffsetEnd() {
            return delegate.getOffsetEnd();
        }

        @Override
        public int getOffsetStart() {
            return delegate.getOffsetStart();
        }

        @Override
        public int getLineNumber() {
            return delegate.getLineNumber();
        }

        @Override
        public URI getURI() {
            return delegate.getURI();
        }

        @Override
        public String getLanguage() {
            return delegate.getLanguage();
        }

        @Override
        public int getNodeId() {
            return delegate.getNodeId();
        }

        @Override
        public String getNodeClassName() {
            return delegate.getNodeClassName();
        }

    }
}
