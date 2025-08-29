/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import static com.oracle.svm.hosted.code.SubstrateCompilationDirectives.DEOPT_TARGET_METHOD;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.CompletionExecutor.DebugContextRunnable;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptTest;
import com.oracle.svm.core.deopt.Specialize;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.OptimizeExceptionPathsPhase;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.core.meta.SubstrateMethodOffsetConstant;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.diagnostic.HostedHeapDumpFeature;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.LayeredDispatchTableFeature;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerLoader;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.ImageBuildStatisticsCounterPhase;
import com.oracle.svm.hosted.phases.ImplicitAssertionsPhase;
import com.oracle.svm.util.ImageBuildStatistics;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.DataBuilder;
import jdk.graal.compiler.lir.asm.FrameContext;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedFoldInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.PEGraphDecoder;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.VMConstant;

public class CompileQueue {

    public interface ParseFunction {
        void parse(DebugContext debug, HostedMethod method, CompileReason reason, RuntimeConfiguration config);
    }

    public interface CompileFunction {
        CompilationResult compile(DebugContext debug, HostedMethod method, CompilationIdentifier identifier, CompileReason reason, RuntimeConfiguration config);
    }

    public static class ParseHooks {
        private final CompileQueue compileQueue;

        protected ParseHooks(CompileQueue compileQueue) {
            this.compileQueue = compileQueue;
        }

        protected PhaseSuite<HighTierContext> getAfterParseSuite() {
            return compileQueue.afterParseCanonicalization();
        }
    }

    protected final HostedUniverse universe;
    private final Boolean deoptimizeAll;
    protected final List<Policy> policies;
    protected CompletionExecutor executor;
    protected final ConcurrentMap<HostedMethod, CompileTask> compilations;
    protected final RuntimeConfiguration runtimeConfig;
    protected final MetaAccessProvider metaAccess;
    private Suites regularSuites = null;
    private Suites deoptTargetSuites = null;
    private LIRSuites regularLIRSuites = null;
    private LIRSuites deoptTargetLIRSuites = null;

    protected final FeatureHandler featureHandler;
    protected final GlobalMetrics metricValues = new GlobalMetrics();
    private final AnalysisToHostedGraphTransplanter graphTransplanter;
    private final ParseHooks defaultParseHooks;

    private volatile boolean inliningProgress;

    private final boolean printMethodHistogram = NativeImageOptions.PrintMethodHistogram.getValue();
    private final boolean optionAOTTrivialInline = SubstrateOptions.AOTTrivialInline.getValue();
    private final boolean allowFoldMethods = NativeImageOptions.AllowFoldMethods.getValue();

    private final ResolvedJavaType generatedFoldInvocationPluginType;

    public record UnpublishedTrivialMethods(CompilationGraph unpublishedGraph, boolean newlyTrivial) {
    }

    private final ConcurrentMap<HostedMethod, UnpublishedTrivialMethods> unpublishedTrivialMethods = new ConcurrentHashMap<>();

    private final LayeredDispatchTableFeature layeredDispatchTableSupport = ImageLayerBuildingSupport.buildingSharedLayer() ? LayeredDispatchTableFeature.singleton() : null;

    public abstract static class CompileReason {
        /**
         * For debugging only: chaining of the compile reason, so that you can track the compilation
         * of a method back to an entry point.
         */
        @SuppressWarnings("unused") private final CompileReason prevReason;

        public CompileReason(CompileReason prevReason) {
            this.prevReason = prevReason;
        }
    }

    public static class EntryPointReason extends CompileReason {

        public EntryPointReason() {
            super(null);
        }

        @Override
        public String toString() {
            return "entry point";
        }
    }

    public static class DirectCallReason extends CompileReason {

        private final HostedMethod caller;

        public DirectCallReason(HostedMethod caller, CompileReason prevReason) {
            super(prevReason);
            this.caller = caller;
        }

        @Override
        public String toString() {
            return "Direct call from " + caller.format("%r %h.%n(%p)");
        }
    }

    public static class VirtualCallReason extends CompileReason {

        private final HostedMethod caller;
        private final HostedMethod callTarget;

        public VirtualCallReason(HostedMethod caller, HostedMethod callTarget, CompileReason prevReason) {
            super(prevReason);
            this.caller = caller;
            this.callTarget = callTarget;
        }

        @Override
        public String toString() {
            return "Virtual call from " + caller.format("%r %h.%n(%p)") + ", callTarget " + callTarget.format("%r %h.%n(%p)");
        }
    }

    public static class MethodRefConstantReason extends CompileReason {

        private final HostedMethod owner;
        private final HostedMethod callTarget;

        public MethodRefConstantReason(HostedMethod owner, HostedMethod callTarget, CompileReason prevReason) {
            super(prevReason);
            this.owner = owner;
            this.callTarget = callTarget;
        }

        @Override
        public String toString() {
            return "Method " + callTarget.format("%r %h.%n(%p)") + " is reachable via MethodRef from " + owner.format("%r %h.%n(%p)");
        }
    }

    public abstract static class Policy {

        protected final HostedUniverse universe;

        public Policy(HostedUniverse universe) {
            this.universe = universe;
        }

        @SuppressWarnings("unused")
        public void beforeEncode(HostedMethod method, StructuredGraph graph, HighTierContext context) {
        }

        @SuppressWarnings("unused")
        public void afterMethodCompile(HostedMethod method, StructuredGraph graph) {
        }

        public void afterCompile() {
        }
    }

    protected interface Task extends DebugContextRunnable {
        @Override
        default DebugContext getDebug(OptionValues options, List<DebugDumpHandlersFactory> factories) {
            return new DebugContext.Builder(options, factories).description(getDescription()).build();
        }
    }

    public class CompileTask implements Task {

        public final HostedMethod method;
        protected final CompileReason reason;
        public CompilationResult result;
        public final CompilationIdentifier compilationIdentifier;

        public CompileTask(HostedMethod method, CompileReason reason) {
            this.method = method;
            this.reason = reason;
            compilationIdentifier = new SubstrateHostedCompilationIdentifier(method);
        }

        @Override
        public DebugContext getDebug(OptionValues options, List<DebugDumpHandlersFactory> factories) {
            return new DebugContext.Builder(options, factories).description(getDescription()).globalMetrics(metricValues).build();
        }

        @Override
        public void run(DebugContext debug) {
            result = doCompile(debug, method, compilationIdentifier, reason);
        }

        @Override
        public Description getDescription() {
            return new Description(method, compilationIdentifier.toString(Verbosity.ID));
        }

        public CompileReason getReason() {
            return reason;
        }
    }

    protected class TrivialInlineTask implements Task {

        private final HostedMethod method;
        private final Description description;

        TrivialInlineTask(HostedMethod method) {
            this.method = method;
            this.description = new Description(method, method.getName());
        }

        @Override
        public void run(DebugContext debug) {
            doInlineTrivial(debug, method);
        }

        @Override
        public Description getDescription() {
            return description;
        }
    }

    public class ParseTask implements Task {

        protected final CompileReason reason;
        private final HostedMethod method;
        private final Description description;

        public ParseTask(HostedMethod method, CompileReason reason) {
            this.method = method;
            this.reason = reason;
            this.description = new Description(method, method.getName());
        }

        @Override
        public void run(DebugContext debug) {
            doParse(debug, this);
        }

        @Override
        public Description getDescription() {
            return description;
        }

        @Override
        public DebugContext getDebug(OptionValues options, List<DebugDumpHandlersFactory> factories) {
            return new DebugContext.Builder(options, factories).description(getDescription()).globalMetrics(metricValues).build();
        }
    }

    @SuppressWarnings("this-escape")
    public CompileQueue(DebugContext debug, FeatureHandler featureHandler, HostedUniverse universe, RuntimeConfiguration runtimeConfiguration, Boolean deoptimizeAll,
                    List<Policy> policies) {
        this.universe = universe;
        this.compilations = new ConcurrentHashMap<>();
        this.runtimeConfig = runtimeConfiguration;
        this.metaAccess = runtimeConfiguration.getProviders().getMetaAccess();
        this.deoptimizeAll = deoptimizeAll;
        this.policies = policies;
        this.executor = new CompletionExecutor(debug, universe.getBigBang());
        this.featureHandler = featureHandler;
        this.graphTransplanter = createGraphTransplanter();
        this.defaultParseHooks = new ParseHooks(this);

        callForReplacements(debug, runtimeConfig);
        generatedFoldInvocationPluginType = GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(GeneratedFoldInvocationPlugin.class);
    }

    protected AnalysisToHostedGraphTransplanter createGraphTransplanter() {
        return new AnalysisToHostedGraphTransplanter(universe, this);
    }

    public static OptimisticOptimizations getOptimisticOpts() {
        return OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseLoopLimitChecks);
    }

    protected void callForReplacements(DebugContext debug, @SuppressWarnings("hiding") RuntimeConfiguration runtimeConfig) {
        NativeImageGenerator.registerReplacements(debug, featureHandler, runtimeConfig, runtimeConfig.getProviders(), true, true, new GraphEncoder(ConfigurationValues.getTarget().arch));
    }

    @SuppressWarnings("try")
    public void finish(DebugContext debug) {
        ProgressReporter reporter = ProgressReporter.singleton();
        try {
            try (ProgressReporter.ReporterClosable ac = reporter.printParsing()) {
                parseAll();
            }

            if (!PointstoOptions.UseExperimentalReachabilityAnalysis.getValue(universe.hostVM().options())) {
                /*
                 * Reachability Analysis creates call graphs with more edges compared to the
                 * Points-to Analysis, therefore the annotations would have to be added to a lot
                 * more methods if these checks are supposed to pass, see GR-39002
                 */
                checkUninterruptibleAnnotations();
                checkRestrictHeapAnnotations(debug);
            }

            /*
             * The graph in the analysis universe is no longer necessary. This clears the graph for
             * methods that were not "parsed", i.e., method that were reached by the static analysis
             * but are no longer reachable now.
             */
            for (HostedMethod method : universe.getMethods()) {
                method.wrapped.clearAnalyzedGraph();
            }

            if (ImageSingletons.contains(HostedHeapDumpFeature.class)) {
                ImageSingletons.lookup(HostedHeapDumpFeature.class).beforeInlining();
            }
            try (ProgressReporter.ReporterClosable ac = reporter.printInlining()) {
                inlineTrivialMethods(debug);
            }
            if (ImageSingletons.contains(HostedHeapDumpFeature.class)) {
                ImageSingletons.lookup(HostedHeapDumpFeature.class).afterInlining();
            }

            assert suitesNotCreated();
            createSuites();
            try (ProgressReporter.ReporterClosable ac = reporter.printCompiling()) {
                compileAll();
                notifyAfterCompile();
            }

            metricValues.print(universe.getBigBang().getOptions());
        } catch (InterruptedException ie) {
            throw new InterruptImageBuilding();
        }
        if (printMethodHistogram) {
            printMethodHistogram();
        }
        if (ImageSingletons.contains(HostedHeapDumpFeature.class)) {
            ImageSingletons.lookup(HostedHeapDumpFeature.class).compileQueueAfterCompilation();
        }
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            HostedImageLayerBuildingSupport.singleton().getLoader().cleanupAfterCompilation();
        }
    }

    protected void checkUninterruptibleAnnotations() {
        UninterruptibleAnnotationChecker.checkBeforeCompilation(universe.getMethods());
    }

    protected void checkRestrictHeapAnnotations(DebugContext debug) {
        RestrictHeapAccessAnnotationChecker.check(debug, universe, universe.getMethods());
    }

    private boolean suitesNotCreated() {
        return regularSuites == null && deoptTargetLIRSuites == null && regularLIRSuites == null && deoptTargetSuites == null;
    }

    protected void createSuites() {
        regularSuites = createRegularSuites();
        modifyRegularSuites(regularSuites);
        deoptTargetSuites = createDeoptTargetSuites();
        removeDeoptTargetOptimizations(deoptTargetSuites);
        regularLIRSuites = createLIRSuites();
        deoptTargetLIRSuites = createDeoptTargetLIRSuites();
        removeDeoptTargetOptimizations(deoptTargetLIRSuites);
    }

    protected Suites createRegularSuites() {
        return NativeImageGenerator.createSuites(featureHandler, runtimeConfig, true);
    }

    protected Suites createDeoptTargetSuites() {
        return NativeImageGenerator.createSuites(featureHandler, runtimeConfig, true);
    }

    protected LIRSuites createLIRSuites() {
        return NativeImageGenerator.createLIRSuites(featureHandler, runtimeConfig.getProviders(), true);
    }

    protected LIRSuites createDeoptTargetLIRSuites() {
        return NativeImageGenerator.createLIRSuites(featureHandler, runtimeConfig.getProviders(), true);
    }

    protected void modifyRegularSuites(@SuppressWarnings("unused") Suites suites) {
    }

    /**
     * Get suites for the compilation of {@code graph}. Parameter {@code suites} can be used to
     * create new suites, they must not be modified directly as this code is called concurrently by
     * multiple threads. Rather implementors can copy suites and modify them.
     */
    protected Suites createSuitesForRegularCompile(@SuppressWarnings("unused") StructuredGraph graph, Suites originalSuites) {
        return originalSuites;
    }

    protected PhaseSuite<HighTierContext> afterParseCanonicalization() {
        PhaseSuite<HighTierContext> phaseSuite = new PhaseSuite<>();
        phaseSuite.appendPhase(new ImplicitAssertionsPhase());
        phaseSuite.appendPhase(new DeadStoreRemovalPhase());
        phaseSuite.appendPhase(CanonicalizerPhase.create());
        phaseSuite.appendPhase(CanonicalizerPhase.create());
        phaseSuite.appendPhase(new OptimizeExceptionPathsPhase());
        if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(universe.hostVM().options())) {
            phaseSuite.appendPhase(CanonicalizerPhase.create());
            phaseSuite.appendPhase(new ImageBuildStatisticsCounterPhase(ImageBuildStatistics.CheckCountLocation.AFTER_PARSE_CANONICALIZATION));
        }
        phaseSuite.appendPhase(CanonicalizerPhase.create());
        return phaseSuite;
    }

    public Map<HostedMethod, CompileTask> getCompilations() {
        return compilations;
    }

    public void purge() {
        compilations.clear();
    }

    public Collection<CompileTask> getCompilationTasks() {
        return compilations.values();
    }

    private void printMethodHistogram() {
        long sizeAllMethods = 0;
        long sizeDeoptMethods = 0;
        long sizeDeoptMethodsInNonDeopt = 0;
        long sizeNonDeoptMethods = 0;
        int numberOfMethods = 0;
        int numberOfNonDeopt = 0;
        int numberOfDeopt = 0;
        long totalNumDeoptEntryPoints = 0;
        long totalNumDuringCallEntryPoints = 0;

        System.out.format("Code Size; Nodes Parsing; Nodes Before; Nodes After; Is Trivial;" +
                        " Deopt Target; Code Size; Nodes Parsing; Nodes Before; Nodes After; Deopt Entries; Deopt During Call;" +
                        " Entry Points; Direct Calls; Virtual Calls; Method%n");

        List<CompileTask> tasks = new ArrayList<>(compilations.values());
        tasks.sort(Comparator.comparing(t2 -> t2.method.format("%H.%n(%p) %r")));

        for (CompileTask task : tasks) {
            HostedMethod method = task.method;
            CompilationResult result = task.result;

            CompilationInfo ci = method.compilationInfo;
            if (!method.isDeoptTarget()) {
                numberOfMethods += 1;
                sizeAllMethods += result.getTargetCodeSize();
                System.out.format("%8d; %5d; %5d; %5d; %s;", result.getTargetCodeSize(), ci.numNodesAfterParsing, ci.numNodesBeforeCompilation, ci.numNodesAfterCompilation,
                                ci.isTrivialMethod ? "T" : " ");

                int deoptMethodSize = 0;
                HostedMethod deoptTargetMethod = method.getMultiMethod(DEOPT_TARGET_METHOD);
                if (deoptTargetMethod != null && isRegisteredDeoptTarget(deoptTargetMethod)) {
                    CompilationInfo dci = deoptTargetMethod.compilationInfo;

                    numberOfDeopt += 1;
                    deoptMethodSize = compilations.get(deoptTargetMethod).result.getTargetCodeSize();
                    sizeDeoptMethods += deoptMethodSize;
                    sizeDeoptMethodsInNonDeopt += result.getTargetCodeSize();
                    totalNumDeoptEntryPoints += dci.numDeoptEntryPoints;
                    totalNumDuringCallEntryPoints += dci.numDuringCallEntryPoints;

                    System.out.format(" D; %6d; %5d; %5d; %5d; %4d; %4d;", deoptMethodSize, dci.numNodesAfterParsing, dci.numNodesBeforeCompilation, dci.numNodesAfterCompilation,
                                    dci.numDeoptEntryPoints,
                                    dci.numDuringCallEntryPoints);

                } else {
                    sizeNonDeoptMethods += result.getTargetCodeSize();
                    numberOfNonDeopt += 1;
                    System.out.format("  ; %6d; %5d; %5d; %5d; %4d; %4d;", 0, 0, 0, 0, 0, 0);
                }

                System.out.format(" %4d; %4d; %4d; %s%n", ci.numEntryPointCalls.get(), ci.numDirectCalls.get(), ci.numVirtualCalls.get(), method.format("%H.%n(%p) %r"));
            }
        }
        System.out.println();
        System.out.println("Size all methods                           ; " + sizeAllMethods);
        System.out.println("Size deopt methods                         ; " + sizeDeoptMethods);
        System.out.println("Size deopt methods in non-deopt mode       ; " + sizeDeoptMethodsInNonDeopt);
        System.out.println("Size non-deopt method                      ; " + sizeNonDeoptMethods);
        System.out.println("Number of methods                          ; " + numberOfMethods);
        System.out.println("Number of non-deopt methods                ; " + numberOfNonDeopt);
        System.out.println("Number of deopt methods                    ; " + numberOfDeopt);
        System.out.println("Number of deopt entry points               ; " + totalNumDeoptEntryPoints);
        System.out.println("Number of deopt during calls entries       ; " + totalNumDuringCallEntryPoints);
    }

    public CompletionExecutor getExecutor() {
        return executor;
    }

    public final void runOnExecutor(Runnable runnable) throws InterruptedException {
        executor.init();
        runnable.run();
        executor.start();
        executor.complete();
        executor.shutdown();
    }

    protected void parseAll() throws InterruptedException {
        /*
         * We parse ahead of time compiled methods before deoptimization targets so that we remove
         * deoptimization entrypoints which are determined to be unneeded. This both helps the
         * performance of deoptimization target methods and also reduces their code size.
         */
        runOnExecutor(this::parseAheadOfTimeCompiledMethods);
        runOnExecutor(this::parseDeoptimizationTargetMethods);
    }

    /**
     * Regular compiled methods. Only entry points and manually marked methods are compiled, all
     * transitively reachable methods are then identified by looking at the callees of already
     * parsed methods.
     */
    private void parseAheadOfTimeCompiledMethods() {

        for (HostedMethod method : universe.getMethods()) {
            if (SubstrateCompilationDirectives.singleton().isRegisteredForDeoptTesting(method)) {
                method.compilationInfo.canDeoptForTesting = true;
                assert SubstrateCompilationDirectives.singleton().isRegisteredDeoptTarget(method.getMultiMethod(DEOPT_TARGET_METHOD));
            }

            for (MultiMethod multiMethod : method.getAllMultiMethods()) {
                HostedMethod hMethod = (HostedMethod) multiMethod;
                if (hMethod.isDeoptTarget() || SubstrateCompilationDirectives.isRuntimeCompiledMethod(hMethod)) {
                    /*
                     * Deoptimization targets are parsed in a later phase.
                     *
                     * Runtime compiled methods are compiled and encoded in a separate process.
                     */
                    continue;
                }
                if (layeredForceCompilation(hMethod)) {
                    // when layered force compilation is triggered we try to parse all graphs
                    if (method.wrapped.getAnalyzedGraph() != null) {
                        ensureParsed(method, null, new EntryPointReason());
                    }
                    continue;
                }
                if (hMethod.isEntryPoint() || SubstrateCompilationDirectives.singleton().isForcedCompilation(hMethod) ||
                                hMethod.wrapped.isDirectRootMethod() && hMethod.wrapped.isSimplyImplementationInvoked()) {
                    ensureParsed(hMethod, null, new EntryPointReason());
                }
                if (hMethod.wrapped.isVirtualRootMethod()) {
                    for (HostedMethod impl : hMethod.getImplementations()) {
                        VMError.guarantee(impl.wrapped.isImplementationInvoked());
                        ensureParsed(impl, null, new EntryPointReason());
                    }
                }
            }
        }

        SubstrateForeignCallsProvider foreignCallsProvider = (SubstrateForeignCallsProvider) runtimeConfig.getProviders().getForeignCalls();
        for (SubstrateForeignCallLinkage linkage : foreignCallsProvider.getForeignCalls().values()) {
            HostedMethod method = (HostedMethod) linkage.getDescriptor().findMethod(runtimeConfig.getProviders().getMetaAccess());
            if (method.wrapped.isDirectRootMethod() && method.wrapped.isSimplyImplementationInvoked()) {
                ensureParsed(method, null, new EntryPointReason());
            }
            if (method.wrapped.isVirtualRootMethod()) {
                for (HostedMethod impl : method.getImplementations()) {
                    VMError.guarantee(impl.wrapped.isImplementationInvoked());
                    ensureParsed(impl, null, new EntryPointReason());
                }
            }
        }
    }

    public boolean isRegisteredDeoptTarget(HostedMethod method) {
        return SubstrateCompilationDirectives.singleton().isRegisteredDeoptTarget(method);
    }

    private void parseDeoptimizationTargetMethods() {
        /*
         * Deoptimization target code for all methods that were manually marked as deoptimization
         * targets.
         */
        universe.getMethods().stream().map(method -> method.getMultiMethod(DEOPT_TARGET_METHOD)).filter(deoptMethod -> {
            if (deoptMethod != null) {
                return isRegisteredDeoptTarget(deoptMethod);
            }
            return false;
        }).forEach(deoptMethod -> {
            ensureParsed(deoptMethod, null, new EntryPointReason());
        });
    }

    private static boolean checkNewlyTrivial(HostedMethod method, StructuredGraph graph) {
        return !method.compilationInfo.isTrivialMethod() && method.canBeInlined() && InliningUtilities.isTrivialMethod(graph);
    }

    @SuppressWarnings("try")
    protected void inlineTrivialMethods(DebugContext debug) throws InterruptedException {
        int round = 0;
        do {
            ProgressReporter.singleton().reportStageProgress();
            inliningProgress = false;
            round++;
            try (Indent ignored = debug.logAndIndent("==== Trivial Inlining  round %d%n", round)) {
                runOnExecutor(() -> {
                    universe.getMethods().forEach(method -> {
                        assert method.isOriginalMethod();
                        for (MultiMethod multiMethod : method.getAllMultiMethods()) {
                            HostedMethod hMethod = (HostedMethod) multiMethod;
                            if (hMethod.compilationInfo.getCompilationGraph() != null) {
                                executor.execute(new TrivialInlineTask(hMethod));
                            }
                        }
                    });
                });
            }
            for (Map.Entry<HostedMethod, UnpublishedTrivialMethods> entry : unpublishedTrivialMethods.entrySet()) {
                entry.getKey().compilationInfo.setCompilationGraph(entry.getValue().unpublishedGraph);
                if (entry.getValue().newlyTrivial) {
                    inliningProgress = true;
                    entry.getKey().compilationInfo.setTrivialMethod();
                }
            }
            unpublishedTrivialMethods.clear();
        } while (inliningProgress);
    }

    class TrivialInliningPlugin implements InlineInvokePlugin {

        boolean inlinedDuringDecoding;

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            if (makeInlineDecision((HostedMethod) b.getMethod(), (HostedMethod) method) && b.recursiveInliningDepth(method) == 0) {
                return InlineInfo.createStandardInlineInfo(method);
            } else {
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
        }

        @Override
        public void notifyAfterInline(ResolvedJavaMethod methodToInline) {
            inlinedDuringDecoding = true;
        }
    }

    class InliningGraphDecoder extends PEGraphDecoder {

        InliningGraphDecoder(StructuredGraph graph, Providers providers, TrivialInliningPlugin inliningPlugin) {
            super(AnalysisParsedGraph.HOST_ARCHITECTURE, graph, providers, null,
                            null,
                            new InlineInvokePlugin[]{inliningPlugin},
                            null, null, null, null,
                            new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), true, false);
        }

        @Override
        protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod method, BytecodeProvider intrinsicBytecodeProvider) {
            return ((HostedMethod) method).compilationInfo.getCompilationGraph().getEncodedGraph();
        }

        @Override
        protected LoopScope trySimplifyInvoke(PEMethodScope methodScope, LoopScope loopScope, InvokeData invokeData, MethodCallTargetNode callTarget) {
            return super.trySimplifyInvoke(methodScope, loopScope, invokeData, callTarget);
        }
    }

    // Wrapper to clearly identify phase
    class TrivialInlinePhase extends Phase {
        final InliningGraphDecoder decoder;
        final HostedMethod method;

        TrivialInlinePhase(InliningGraphDecoder decoder, HostedMethod method) {
            this.decoder = decoder;
            this.method = method;
        }

        @Override
        protected void run(StructuredGraph graph) {
            decoder.decode(method);
        }

        @Override
        public CharSequence getName() {
            return "TrivialInline";
        }
    }

    @SuppressWarnings("try")
    private void doInlineTrivial(DebugContext debug, HostedMethod method) {
        /*
         * Before doing any work, check if there is any potential for inlining.
         *
         * Note that we do not have information about the recursive inlining depth, but that is OK
         * because in that case we just over-estimate the inlining potential, i.e., we do the
         * decoding just to find out that nothing could be inlined.
         */
        boolean inliningPotential = false;
        for (var invokeInfo : method.compilationInfo.getCompilationGraph().getInvokeInfos()) {
            if (invokeInfo.getInvokeKind().isDirect() && makeInlineDecision(method, invokeInfo.getTargetMethod())) {
                inliningPotential = true;
                break;
            }
        }
        if (!inliningPotential) {
            return;
        }
        var providers = runtimeConfig.lookupBackend(method).getProviders();
        var graph = method.compilationInfo.createGraph(debug, getCustomizedOptions(method, debug), CompilationIdentifier.INVALID_COMPILATION_ID, false);
        try (var s = debug.scope("InlineTrivial", graph, method, this)) {
            var inliningPlugin = new TrivialInliningPlugin();
            var decoder = new InliningGraphDecoder(graph, providers, inliningPlugin);
            new TrivialInlinePhase(decoder, method).apply(graph);

            if (inliningPlugin.inlinedDuringDecoding) {
                CanonicalizerPhase.create().apply(graph, providers);

                if (!method.compilationInfo.isTrivialInliningDisabled() && graph.getNodeCount() > SubstrateOptions.MaxNodesAfterTrivialInlining.getValue()) {
                    /*
                     * The method is too larger after inlining. There is no good way of just
                     * inlining some but not all trivial callees, because inlining is done during
                     * graph decoding so the total graph size is not known until the whole graph is
                     * decoded. We therefore disable all trivial inlining for the method. Except
                     * callees that are annotated as "always inline" - therefore we need to pretend
                     * that there was inlining progress, which triggers another round of inlining
                     * where only "always inline" methods are inlined.
                     */
                    method.compilationInfo.setTrivialInliningDisabled();
                    inliningProgress = true;

                } else {
                    /*
                     * If we publish the new graph immediately, it can be picked up by other threads
                     * trying to inline this method, and that would make the inlining
                     * non-deterministic. This is why we are saving graphs to be published at the
                     * end of each round.
                     */
                    unpublishedTrivialMethods.put(method, new UnpublishedTrivialMethods(CompilationGraph.encode(graph), checkNewlyTrivial(method, graph)));
                }
            }
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    private boolean makeInlineDecision(HostedMethod method, HostedMethod callee) {
        if (!SubstrateOptions.UseSharedLayerStrengthenedGraphs.getValue() && callee.compilationInfo.getCompilationGraph() == null) {
            /*
             * We have compiled this method in a prior layer or this method's compilation is delayed
             * to the application layer, but don't have the graph available here.
             */
            assert callee.isCompiledInPriorLayer() || callee.wrapped.isDelayed() : method;
            return false;
        }
        if (universe.hostVM().neverInlineTrivial(method.getWrapped(), callee.getWrapped())) {
            return false;
        }
        if (callee.shouldBeInlined()) {
            return true;
        }
        if (optionAOTTrivialInline && callee.compilationInfo.isTrivialMethod() && !method.compilationInfo.isTrivialInliningDisabled()) {
            return true;
        }
        return false;
    }

    private static boolean mustNotAllocateCallee(HostedMethod method) {
        return ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(method);
    }

    private static boolean mustNotAllocate(HostedMethod method) {
        /*
         * GR-15580: This check is suspicious. The no-allocation restriction is propagated through
         * the call graph, so checking explicitly for annotated methods means that either not enough
         * methods are excluded from inlining, or the inlining restriction is not necessary at all.
         * We should elevate all methods that really need an inlining restriction
         * to @Uninterruptible or mark them as @NeverInline, so that no-allocation does not need any
         * more inlining restrictions and this code can be removed.
         */
        RestrictHeapAccess annotation = method.getAnnotation(RestrictHeapAccess.class);
        return annotation != null && annotation.access() == RestrictHeapAccess.Access.NO_ALLOCATION;
    }

    public static boolean callerAnnotatedWith(Invoke invoke, Class<? extends Annotation> annotationClass) {
        return getCallerAnnotation(invoke, annotationClass) != null;
    }

    private static <T extends Annotation> T getCallerAnnotation(Invoke invoke, Class<T> annotationClass) {
        for (FrameState state = invoke.stateAfter(); state != null; state = state.outerFrameState()) {
            assert state.getMethod() != null : state;
            T annotation = state.getMethod().getAnnotation(annotationClass);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    protected CompileTask createCompileTask(HostedMethod method, CompileReason reason) {
        return new CompileTask(method, reason);
    }

    protected void compileAll() throws InterruptedException {
        /*
         * We parse ahead of time compiled methods before deoptimization targets so that we remove
         * deoptimization entrypoints which are determined to be unneeded. This both helps the
         * performance of deoptimization target methods and also reduces their code size.
         */
        runOnExecutor(this::scheduleEntryPoints);

        runOnExecutor(this::scheduleDeoptTargets);
    }

    private void notifyAfterCompile() {
        for (Policy policy : this.policies) {
            policy.afterCompile();
        }
    }

    public void scheduleEntryPoints() {
        for (HostedMethod method : universe.getMethods()) {
            for (MultiMethod multiMethod : method.getAllMultiMethods()) {
                HostedMethod hMethod = (HostedMethod) multiMethod;
                if (hMethod.isDeoptTarget() || SubstrateCompilationDirectives.isRuntimeCompiledMethod(hMethod)) {
                    /*
                     * Deoptimization targets are parsed in a later phase.
                     *
                     * Runtime compiled methods are compiled and encoded in a separate process.
                     */
                    continue;
                }

                if (layeredForceCompilation(hMethod)) {
                    /*
                     * when layeredForceCompilation is triggered we try to parse all graphs.
                     */
                    if (method.compilationInfo.getCompilationGraph() != null) {
                        ensureCompiled(hMethod, new EntryPointReason());
                    }
                    continue;
                }

                if (hMethod.isEntryPoint() || SubstrateCompilationDirectives.singleton().isForcedCompilation(hMethod) ||
                                hMethod.wrapped.isDirectRootMethod() && hMethod.wrapped.isSimplyImplementationInvoked()) {
                    ensureCompiled(hMethod, new EntryPointReason());
                }
                if (hMethod.wrapped.isVirtualRootMethod()) {
                    MultiMethod.MultiMethodKey key = hMethod.getMultiMethodKey();
                    assert key != DEOPT_TARGET_METHOD && key != SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD : "unexpected method as virtual root " + hMethod;
                    for (HostedMethod impl : hMethod.getImplementations()) {
                        VMError.guarantee(impl.wrapped.isImplementationInvoked());
                        ensureCompiled(impl, new EntryPointReason());
                    }
                }
            }
        }
    }

    private static boolean layeredForceCompilation(@SuppressWarnings("unused") HostedMethod hMethod) {
        // GR-57021 force some methods to be compiled in initial layer
        return false;
    }

    public void scheduleDeoptTargets() {
        for (HostedMethod method : universe.getMethods()) {
            HostedMethod deoptTarget = method.getMultiMethod(DEOPT_TARGET_METHOD);
            if (deoptTarget != null) {
                /*
                 * Not all methods will be deopt targets since the optimization of runtime compiled
                 * methods may eliminate FrameStates.
                 */
                if (isRegisteredDeoptTarget(deoptTarget)) {
                    ensureCompiled(deoptTarget, new EntryPointReason());
                }
            }
        }
    }

    private static boolean parseInCurrentLayer(HostedMethod method) {
        var hasAnalyzedGraph = method.wrapped.getAnalyzedGraph() != null;
        if (!hasAnalyzedGraph && method.wrapped.reachableInCurrentLayer()) {
            SVMImageLayerLoader imageLayerLoader = HostedImageLayerBuildingSupport.singleton().getLoader();
            hasAnalyzedGraph = imageLayerLoader.hasStrengthenedGraph(method.wrapped);
        }
        assert hasAnalyzedGraph || !method.wrapped.reachableInCurrentLayer() || method.isCompiledInPriorLayer() || method.compilationInfo.inParseQueue.get() : method;
        return hasAnalyzedGraph;
    }

    protected void ensureParsed(HostedMethod method, HostedMethod callerMethod, CompileReason reason) {
        if (!parseInCurrentLayer(method)) {
            return;
        }

        if (!allowFoldMethods && method.getAnnotation(Fold.class) != null && !isFoldInvocationPluginMethod(callerMethod)) {
            throw VMError.shouldNotReachHere("Parsing method annotated with @%s: %s. " +
                            "This could happen if either: the Graal annotation processor was not executed on the parent-project of the method's declaring class, " +
                            "the arguments passed to the method were not compile-time constants, or the plugin was disabled by the corresponding %s.",
                            Fold.class.getSimpleName(), method.format("%H.%n(%p)"), GraphBuilderContext.class.getSimpleName());
        }
        if (!method.compilationInfo.inParseQueue.getAndSet(true)) {
            executor.execute(new ParseTask(method, reason));
        }
    }

    private boolean isFoldInvocationPluginMethod(HostedMethod method) {
        return method != null && generatedFoldInvocationPluginType.isAssignableFrom(OriginalClassProvider.getOriginalType(method.getDeclaringClass()));
    }

    protected final void doParse(DebugContext debug, ParseTask task) {
        HostedMethod method = task.method;
        if (HostedImageLayerBuildingSupport.buildingExtensionLayer()) {
            loadPriorStrengthenedGraph(method);
        }

        ParseFunction customFunction = task.method.compilationInfo.getCustomParseFunction();
        if (customFunction != null) {
            customFunction.parse(debug, task.method, task.reason, runtimeConfig);
        } else {

            ParseHooks hooks = task.method.compilationInfo.getCustomParseHooks();
            if (hooks == null) {
                hooks = defaultParseHooks;
            }
            defaultParseFunction(debug, task.method, task.reason, runtimeConfig, hooks);
        }
    }

    private static void loadPriorStrengthenedGraph(HostedMethod method) {
        if (method.wrapped.getAnalyzedGraph() == null && method.wrapped.reachableInCurrentLayer()) {
            /*
             * Only the strengthened graphs of methods that need to be analyzed in the current layer
             * are loaded.
             */
            SVMImageLayerLoader imageLayerLoader = HostedImageLayerBuildingSupport.singleton().getLoader();
            boolean hasStrengthenedGraph = imageLayerLoader.hasStrengthenedGraph(method.wrapped);
            assert method.isCompiledInPriorLayer() || method.compilationInfo.inParseQueue.get() || hasStrengthenedGraph : method;
            if (hasStrengthenedGraph) {
                /*
                 * GR-59679: The loading of graphs could be even more lazy than this. It could be
                 * loaded only when the inlining will be performed
                 */
                method.wrapped.setAnalyzedGraph(imageLayerLoader.getStrengthenedGraph(method.wrapped));
            }
        }
    }

    @SuppressWarnings("try")
    private void defaultParseFunction(DebugContext debug, HostedMethod method, CompileReason reason, RuntimeConfiguration config, ParseHooks hooks) {
        if (method.getAnnotation(NodeIntrinsic.class) != null) {
            throw VMError.shouldNotReachHere("Parsing method annotated with @" + NodeIntrinsic.class.getSimpleName() + ": " +
                            method.format("%H.%n(%p)") +
                            ". Make sure you have used Graal annotation processors on the parent-project of the method's declaring class.");
        }

        HostedProviders providers = (HostedProviders) config.lookupBackend(method).getProviders();

        StructuredGraph graph = graphTransplanter.transplantGraph(debug, method, reason);
        try (DebugContext.Scope s = debug.scope("Parsing", graph, method, this)) {

            try {
                graph.getGraphState().configureExplicitExceptionsNoDeoptIfNecessary();

                PhaseSuite<HighTierContext> afterParseSuite = hooks.getAfterParseSuite();
                afterParseSuite.apply(graph, new HighTierContext(providers, afterParseSuite, getOptimisticOpts()));

                method.compilationInfo.numNodesAfterParsing = graph.getNodeCount();

                for (Invoke invoke : graph.getInvokes()) {
                    if (!canBeUsedForInlining(invoke)) {
                        invoke.setUseForInlining(false);
                    }
                    CallTargetNode targetNode = invoke.callTarget();
                    ensureParsed(method, reason, targetNode, (HostedMethod) targetNode.targetMethod(), targetNode.invokeKind().isIndirect() || targetNode instanceof IndirectCallTargetNode);
                }
                for (Node n : graph.getNodes()) {
                    if (n instanceof MacroInvokable) {
                        /*
                         * A MacroInvokable might be lowered back to a regular invoke. At this point
                         * we do not know if that happens, but we need to prepared and have the
                         * graph of the potential callee parsed as if the MacroNode was an Invoke.
                         */
                        MacroInvokable macroNode = (MacroInvokable) n;
                        ensureParsed(method, reason, null, (HostedMethod) macroNode.getTargetMethod(), macroNode.getInvokeKind().isIndirect());
                    }
                }

                GraalError.guarantee(graph.isAfterStage(StageFlag.GUARD_LOWERING), "Hosted compilations must have explicit exceptions %s %s", graph, graph.getGraphState().getStageFlags());

                notifyBeforeEncode(method, graph);
                assert GraphOrder.assertSchedulableGraph(graph);
                method.compilationInfo.encodeGraph(graph);
                if (checkNewlyTrivial(method, graph)) {
                    method.compilationInfo.setTrivialMethod();
                }
            } catch (Throwable ex) {
                GraalError error = ex instanceof GraalError ? (GraalError) ex : new GraalError(ex);
                error.addContext("method: " + method.format("%r %H.%n(%p)"));
                throw error;
            }

        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private void ensureParsed(HostedMethod method, CompileReason reason, CallTargetNode targetNode, HostedMethod invokeTarget, boolean isIndirect) {
        if (isIndirect) {
            for (HostedMethod invokeImplementation : invokeTarget.getImplementations()) {
                handleSpecialization(method, targetNode, invokeTarget, invokeImplementation);
                ensureParsed(invokeImplementation, method, new VirtualCallReason(method, invokeImplementation, reason));
            }
        } else {
            /*
             * Direct calls to instance methods (invokespecial bytecode or devirtualized calls) can
             * go to methods that are unreachable if the receiver is always null. At this time, we
             * do not know the receiver types, so we filter such invokes by looking at the
             * reachability status from the point of view of the static analysis. Note that we
             * cannot use "isImplementationInvoked" because (for historic reasons) it also returns
             * true if a method has a graph builder plugin registered. All graph builder plugins are
             * already applied during parsing before we reach this point, so we look at the "simple"
             * implementation invoked status.
             */
            if (invokeTarget.wrapped.isSimplyImplementationInvoked()) {
                handleSpecialization(method, targetNode, invokeTarget, invokeTarget);
                ensureParsed(invokeTarget, method, new DirectCallReason(method, reason));
            }
        }
    }

    @SuppressWarnings("unused")
    protected void notifyBeforeEncode(HostedMethod method, StructuredGraph graph) {
        HostedProviders providers = (HostedProviders) runtimeConfig.lookupBackend(method).getProviders();
        HighTierContext highTierContext = new HighTierContext(providers, null, CompileQueue.getOptimisticOpts());

        for (Policy policy : this.policies) {
            policy.beforeEncode(method, graph, highTierContext);
        }
    }

    protected OptionValues getCustomizedOptions(@SuppressWarnings("unused") HostedMethod method, DebugContext debug) {
        return debug.getOptions();
    }

    protected boolean canBeUsedForInlining(Invoke invoke) {
        HostedMethod caller = (HostedMethod) invoke.asNode().graph().method();
        HostedMethod callee = (HostedMethod) invoke.callTarget().targetMethod();

        if (!DeoptimizationUtils.canBeUsedForInlining(universe, caller, callee, invoke.bci())) {
            /*
             * Inlining will violate deoptimization requirements.
             */
            return false;
        }

        if (callee.getAnnotation(Specialize.class) != null) {
            return false;
        }
        if (callerAnnotatedWith(invoke, Specialize.class) && callee.getAnnotation(DeoptTest.class) != null) {
            return false;
        }

        if (!Uninterruptible.Utils.inliningAllowed(caller, callee)) {
            return false;
        }
        if (!mustNotAllocateCallee(caller) && mustNotAllocate(callee)) {
            return false;
        }
        /*
         * Note that we do not check callee.canBeInlined() yet. Otherwise a @NeverInline annotation
         * on a virtual target method would also prevent inlining of a concrete implementation
         * method (after a later de-virtualization of the invoke) that is not annotated
         * with @NeverInline. It is the responsibility of every inlining phase to check
         * canBeInlined().
         */
        return invoke.useForInlining();
    }

    private static void handleSpecialization(final HostedMethod method, CallTargetNode targetNode, HostedMethod invokeTarget, HostedMethod invokeImplementation) {
        if (method.getAnnotation(Specialize.class) != null && !method.isDeoptTarget() && invokeTarget.getAnnotation(DeoptTest.class) != null) {
            /*
             * Collect the constant arguments to a method which should be specialized.
             */
            if (invokeImplementation.compilationInfo.specializedArguments != null) {
                VMError.shouldNotReachHere("Specialized method " + invokeImplementation.format("%H.%n(%p)") + " can only be called from one place");
            }
            invokeImplementation.compilationInfo.specializedArguments = new ConstantNode[targetNode.arguments().size()];
            int idx = 0;
            for (ValueNode argument : targetNode.arguments()) {
                if (!(argument instanceof ConstantNode)) {
                    VMError.shouldNotReachHere("Argument " + idx + " of specialized method " + invokeImplementation.format("%H.%n(%p)") + " is not constant");
                }
                invokeImplementation.compilationInfo.specializedArguments[idx++] = (ConstantNode) argument;
            }
        }
    }

    protected void ensureCompiled(HostedMethod method, CompileReason reason) {
        if (method.isCompiledInPriorLayer() || method.wrapped.isDelayed()) {
            /*
             * Since this method was compiled in a prior layer or its compilation is delayed to the
             * application layer we should not compile it.
             */
            return;
        }
        if (ImageLayerBuildingSupport.buildingExtensionLayer() && !method.wrapped.reachableInCurrentLayer()) {
            assert method.wrapped.isInBaseLayer();
            /*
             * This method was reached and analyzed in the base layer, but it was not compiled in
             * that layer, e.g., because it was always inlined. It is referenced in the app layer,
             * but it was not reached during this layer's analysis, so its base layer graph was not
             * loaded. However, it is considered as a potential compilation target because it is the
             * implementation of a method invoked in this layer. Since we don't have an analysis
             * graph we cannot compile it, however it should not be called at run time since it was
             * not reached during analysis. (GR-64200)
             */
            return;
        }

        CompilationInfo compilationInfo = method.compilationInfo;
        assert method.getMultiMethodKey() != SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;

        if (printMethodHistogram) {
            if (reason instanceof DirectCallReason) {
                compilationInfo.numDirectCalls.incrementAndGet();
            } else if (reason instanceof VirtualCallReason) {
                compilationInfo.numVirtualCalls.incrementAndGet();
            } else if (reason instanceof EntryPointReason) {
                compilationInfo.numEntryPointCalls.incrementAndGet();
            }
        }

        /*
         * Fast non-atomic check if method is already scheduled for compilation, to avoid frequent
         * access of the ConcurrentHashMap.
         */
        if (compilationInfo.inCompileQueue) {
            return;
        }

        CompileTask task = createCompileTask(method, reason);
        CompileTask oldTask = compilations.putIfAbsent(method, task);
        if (oldTask != null) {
            return;
        }
        compilationInfo.inCompileQueue = true;

        executor.execute(task);
        method.setCompiled();
    }

    class HostedCompilationResultBuilderFactory implements CompilationResultBuilderFactory {
        @Override
        public CompilationResultBuilder createBuilder(CoreProviders providers,
                        FrameMap frameMap,
                        Assembler<?> asm,
                        DataBuilder dataBuilder,
                        FrameContext frameContext,
                        OptionValues options,
                        DebugContext debug,
                        CompilationResult compilationResult,
                        Register uncompressedNullRegister,
                        LIR lir) {
            return new CompilationResultBuilder(providers,
                            frameMap,
                            asm,
                            dataBuilder,
                            frameContext,
                            options,
                            debug,
                            compilationResult,
                            uncompressedNullRegister,
                            CompilationResultBuilder.NO_VERIFIERS,
                            lir);
        }
    }

    protected final CompilationResult doCompile(DebugContext debug, final HostedMethod method, CompilationIdentifier compilationIdentifier, CompileReason reason) {
        CompileFunction fun = method.compilationInfo.getCustomCompileFunction();
        if (fun == null) {
            fun = this::defaultCompileFunction;
        }
        return fun.compile(debug, method, compilationIdentifier, reason, runtimeConfig);
    }

    @SuppressWarnings("try")
    private CompilationResult defaultCompileFunction(DebugContext debug, HostedMethod method, CompilationIdentifier compilationIdentifier, CompileReason reason, RuntimeConfiguration config) {

        if (NativeImageOptions.PrintAOTCompilation.getValue()) {
            TTY.println(String.format("[CompileQueue] Compiling [idHash=%10d] %s Reason: %s", System.identityHashCode(method), method.format("%r %H.%n(%p)"), reason));
        }

        try {
            SubstrateBackend backend = config.lookupBackend(method);

            VMError.guarantee(method.compilationInfo.getCompilationGraph() != null, "The following method is reachable during compilation, but was not seen during Bytecode parsing: %s", method);
            StructuredGraph graph = method.compilationInfo.createGraph(debug, getCustomizedOptions(method, debug), compilationIdentifier, true);
            customizeGraph(graph, reason);

            GraalError.guarantee(graph.getGraphState().getGuardsStage().areDeoptsFixed(),
                            "Hosted compilations must have explicit exceptions [guard stage] %s=%s", graph, graph.getGraphState().getGuardsStage());
            GraalError.guarantee(graph.isAfterStage(StageFlag.GUARD_LOWERING),
                            "Hosted compilations must have explicit exceptions [guard lowering] %s=%s -> %s", graph, graph.getGraphState().getStageFlags(), graph.getGuardsStage());

            if (method.compilationInfo.specializedArguments != null) {
                // Do the specialization: replace the argument locals with the constant arguments.
                int idx = 0;
                for (ConstantNode argument : method.compilationInfo.specializedArguments) {
                    ParameterNode local = graph.getParameter(idx++);
                    if (local != null) {
                        local.replaceAndDelete(ConstantNode.forConstant(argument.asJavaConstant(), runtimeConfig.getProviders().getMetaAccess(), graph));
                    }
                }
            }

            /* Check that graph is in good shape before compilation. */
            assert GraphOrder.assertSchedulableGraph(graph);

            try (DebugContext.Scope s = debug.scope("Compiling", graph, method, this);
                            DebugCloseable b = GraalServices.GCTimerScope.create(debug)) {

                if (deoptimizeAll && method.compilationInfo.canDeoptForTesting) {
                    DeoptimizationUtils.insertDeoptTests(method, graph);
                }
                method.compilationInfo.numNodesBeforeCompilation = graph.getNodeCount();
                method.compilationInfo.numDeoptEntryPoints = graph.getNodes().filter(DeoptEntryNode.class).count();
                method.compilationInfo.numDuringCallEntryPoints = graph.getNodes(MethodCallTargetNode.TYPE).snapshot().stream().map(MethodCallTargetNode::invoke).filter(
                                invoke -> method.compilationInfo.isDeoptEntry(invoke.bci(), FrameState.StackState.AfterPop)).count();

                Suites suites = method.isDeoptTarget() ? deoptTargetSuites : createSuitesForRegularCompile(graph, regularSuites);
                LIRSuites lirSuites = method.isDeoptTarget() ? deoptTargetLIRSuites : regularLIRSuites;

                CompilationResult result = backend.newCompilationResult(compilationIdentifier, method.getQualifiedName());

                try (Indent indent = debug.logAndIndent("compile %s", method)) {
                    Providers providers = backend.getProviders();
                    OptimisticOptimizations optimisticOpts = getOptimisticOpts();
                    GraalCompiler.compile(new GraalCompiler.Request<>(graph,
                                    method,
                                    providers,
                                    backend,
                                    null,
                                    optimisticOpts,
                                    null,
                                    suites,
                                    lirSuites,
                                    result,
                                    new HostedCompilationResultBuilderFactory(),
                                    false));
                }
                graph.getOptimizationLog().emit();
                method.compilationInfo.numNodesAfterCompilation = graph.getNodeCount();

                if (method.isDeoptTarget()) {
                    assert DeoptimizationUtils.verifyDeoptTarget(method, graph, result);
                }
                ensureCalleesCompiled(method, reason, result);

                /* Shrink resulting code array to minimum size, to reduce memory footprint. */
                if (result.getTargetCode().length > result.getTargetCodeSize()) {
                    result.setTargetCode(Arrays.copyOf(result.getTargetCode(), result.getTargetCodeSize()), result.getTargetCodeSize());
                }

                notifyAfterMethodCompile(method, graph);

                return result;
            }
        } catch (Throwable ex) {
            GraalError error = ex instanceof GraalError ? (GraalError) ex : new GraalError(ex);
            error.addContext("method: " + method.format("%r %H.%n(%p)") + "  [" + reason + "]");
            throw error;
        }
    }

    private void notifyAfterMethodCompile(HostedMethod method, StructuredGraph graph) {
        for (Policy policy : this.policies) {
            policy.afterMethodCompile(method, graph);
        }
    }

    /**
     * Allows subclasses to customize the {@link StructuredGraph graph} after its creation.
     *
     * @param graph A newly created {@link StructuredGraph graph} for one particular compilation
     *            unit.
     * @param reason The reason for compiling this compilation unit.
     */
    protected void customizeGraph(StructuredGraph graph, CompileReason reason) {
        // Hook for subclasses
    }

    protected boolean isDynamicallyResolvedCall(@SuppressWarnings("unused") CompilationResult result, @SuppressWarnings("unused") Call call) {
        return false;
    }

    protected void ensureCalleesCompiled(HostedMethod method, CompileReason reason, CompilationResult result) {
        for (Infopoint infopoint : result.getInfopoints()) {
            if (infopoint instanceof Call call) {
                HostedMethod callTarget = (HostedMethod) call.target;
                if (call.direct || isDynamicallyResolvedCall(result, call)) {
                    ensureCompiled(callTarget, new DirectCallReason(method, reason));
                } else if (callTarget != null && callTarget.getImplementations() != null) {
                    if (layeredDispatchTableSupport != null) {
                        layeredDispatchTableSupport.recordVirtualCallTarget(method, callTarget);
                    }
                    for (HostedMethod impl : callTarget.getImplementations()) {
                        ensureCompiled(impl, new VirtualCallReason(method, callTarget, reason));
                    }
                }
            }
        }
        ensureCompiledForMethodRefConstants(method, reason, result);
    }

    protected void removeDeoptTargetOptimizations(Suites suites) {
        DeoptimizationUtils.removeDeoptTargetOptimizations(suites);
    }

    protected void removeDeoptTargetOptimizations(LIRSuites lirSuites) {
        DeoptimizationUtils.removeDeoptTargetOptimizations(lirSuites);
    }

    protected final void ensureCompiledForMethodRefConstants(HostedMethod method, CompileReason reason, CompilationResult result) {
        for (DataPatch dataPatch : result.getDataPatches()) {
            if (dataPatch.reference instanceof ConstantReference constantRef) {
                VMConstant constant = constantRef.getConstant();
                MethodRef ref = null;
                if (constant instanceof SubstrateMethodPointerConstant pointerConstant) {
                    ref = pointerConstant.pointer();
                } else if (constant instanceof SubstrateMethodOffsetConstant offsetConstant) {
                    ref = offsetConstant.offset();
                }
                if (ref != null) {
                    HostedMethod referencedMethod = (HostedMethod) ref.getMethod();
                    ensureCompiled(referencedMethod, new MethodRefConstantReason(method, referencedMethod, reason));
                }
            }
        }
    }

    public Map<HostedMethod, CompilationResult> getCompilationResults() {
        Map<HostedMethod, CompilationResult> result = new TreeMap<>(HostedUniverse.METHOD_COMPARATOR);
        for (Entry<HostedMethod, CompileTask> entry : compilations.entrySet()) {
            result.put(entry.getKey(), entry.getValue().result);
        }
        return result;
    }

    public Suites getRegularSuites() {
        return regularSuites;
    }
}
