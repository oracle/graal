/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationIdentifier.Verbosity;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.lir.RedundantMoveElimination;
import org.graalvm.compiler.lir.alloc.RegisterAllocationPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.FixReadsPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.GraphOrder;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.nodes.MacroNode;
import org.graalvm.compiler.virtual.phases.ea.EarlyReadEliminationPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.GraphProvider.Purpose;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.CompletionExecutor.DebugContextRunnable;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AlwaysInlineAllCallees;
import com.oracle.svm.core.annotate.DeoptTest;
import com.oracle.svm.core.annotate.NeverInlineTrivial;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Specialize;
import com.oracle.svm.core.annotate.StubCallingConvention;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptTestNode;
import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureHandler;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.phases.DevirtualizeCallsPhase;
import com.oracle.svm.hosted.phases.HostedGraphBuilderPhase;
import com.oracle.svm.hosted.phases.StrengthenStampsPhase;
import com.oracle.svm.hosted.substitute.DeletedMethod;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.Constant;

public class CompileQueue {

    public interface ParseFunction {
        void parse(DebugContext debug, HostedMethod method, CompileReason reason, RuntimeConfiguration config);
    }

    public interface CompileFunction {
        CompilationResult compile(DebugContext debug, HostedMethod method, CompilationIdentifier identifier, CompileReason reason, RuntimeConfiguration config);
    }

    protected final HostedUniverse universe;
    private final Boolean deoptimizeAll;
    protected CompletionExecutor executor;
    private final ConcurrentMap<HostedMethod, CompileTask> compilations;
    protected final RuntimeConfiguration runtimeConfig;
    private Suites regularSuites = null;
    private Suites deoptTargetSuites = null;
    private LIRSuites regularLIRSuites = null;
    private LIRSuites deoptTargetLIRSuites = null;
    private final ConcurrentMap<Constant, DataSection.Data> dataCache;

    private SnippetReflectionProvider snippetReflection;
    private final FeatureHandler featureHandler;

    private volatile boolean inliningProgress;

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

    public class CompileTask implements DebugContextRunnable {

        public final HostedMethod method;
        protected final CompileReason reason;
        protected final List<CompileReason> allReasons;
        public CompilationResult result;
        public final CompilationIdentifier compilationIdentifier;

        public CompileTask(HostedMethod method, CompileReason reason) {
            this.method = method;
            this.reason = reason;
            if (NativeImageOptions.PrintMethodHistogram.getValue()) {
                this.allReasons = Collections.synchronizedList(new ArrayList<CompileReason>());
                this.allReasons.add(reason);
            } else {
                this.allReasons = null;
            }
            compilationIdentifier = new SubstrateHostedCompilationIdentifier(method);
        }

        @Override
        public void run(DebugContext debug) {
            if (method.compilationInfo.graph != null) {
                method.compilationInfo.graph.resetDebug(debug);
            }
            result = doCompile(debug, method, compilationIdentifier, reason);
        }

        @Override
        public Description getDescription() {
            return new Description(method, compilationIdentifier.toString(Verbosity.ID));
        }
    }

    protected class TrivialInlineTask implements DebugContextRunnable {

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

    public class ParseTask implements DebugContextRunnable {

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
    }

    public CompileQueue(DebugContext debug, FeatureHandler featureHandler, HostedUniverse universe, SharedRuntimeConfigurationBuilder runtimeConfigBuilder, Boolean deoptimizeAll,
                    SnippetReflectionProvider snippetReflection, ForkJoinPool executorService) {
        this.universe = universe;
        this.compilations = new ConcurrentHashMap<>();
        this.runtimeConfig = runtimeConfigBuilder.getRuntimeConfig();
        this.deoptimizeAll = deoptimizeAll;
        this.dataCache = new ConcurrentHashMap<>();
        this.executor = new CompletionExecutor(universe.getBigBang(), executorService, universe.getBigBang().getHeartbeatCallback());
        this.featureHandler = featureHandler;
        this.snippetReflection = snippetReflection;

        // let aotjs override the replacements registration
        callForReplacements(debug, runtimeConfig);
    }

    public static OptimisticOptimizations getOptimisticOpts() {
        return OptimisticOptimizations.ALL.remove(OptimisticOptimizations.Optimization.UseLoopLimitChecks);
    }

    protected void callForReplacements(DebugContext debug, @SuppressWarnings("hiding") RuntimeConfiguration runtimeConfig) {
        NativeImageGenerator.registerReplacements(debug, featureHandler, runtimeConfig, runtimeConfig.getProviders(), snippetReflection, true, true);
    }

    @SuppressWarnings("try")
    public void finish(DebugContext debug) {
        try {
            String imageName = universe.getBigBang().getHostVM().getImageName();
            try (StopTimer t = new Timer(imageName, "(parse)").start()) {
                parseAll();
            }
            // Checking @Uninterruptible annotations does not take long enough to justify a timer.
            new UninterruptibleAnnotationChecker(universe.getMethods()).check();
            // Checking @RestrictHeapAccess annotations does not take long enough to justify a
            // timer.
            RestrictHeapAccessAnnotationChecker.check(debug, universe, universe.getMethods());
            // Checking @MustNotSynchronize annotations does not take long enough to justify a
            // timer.
            MustNotSynchronizeAnnotationChecker.check(debug, universe.getMethods());

            if (SubstrateOptions.AOTInline.getValue() && SubstrateOptions.AOTTrivialInline.getValue()) {
                try (StopTimer ignored = new Timer(imageName, "(inline)").start()) {
                    inlineTrivialMethods(debug);
                }
            }

            assert suitesNotCreated();
            createSuites();
            try (StopTimer t = new Timer(imageName, "(compile)").start()) {
                compileAll();
            }
        } catch (InterruptedException ie) {
            throw new InterruptImageBuilding();
        }
        if (NativeImageOptions.PrintMethodHistogram.getValue()) {
            printMethodHistogram();
        }
    }

    private boolean suitesNotCreated() {
        return regularSuites == null && deoptTargetLIRSuites == null && regularLIRSuites == null && deoptTargetSuites == null;
    }

    private void createSuites() {
        regularSuites = NativeImageGenerator.createSuites(featureHandler, runtimeConfig, snippetReflection, true);
        deoptTargetSuites = NativeImageGenerator.createSuites(featureHandler, runtimeConfig, snippetReflection, true);
        removeDeoptTargetOptimizations(deoptTargetSuites);
        regularLIRSuites = NativeImageGenerator.createLIRSuites(featureHandler, runtimeConfig.getProviders(), true);
        deoptTargetLIRSuites = NativeImageGenerator.createLIRSuites(featureHandler, runtimeConfig.getProviders(), true);
        removeDeoptTargetOptimizations(deoptTargetLIRSuites);
    }

    public static PhaseSuite<HighTierContext> afterParseCanonicalization() {
        PhaseSuite<HighTierContext> phaseSuite = new PhaseSuite<>();
        phaseSuite.appendPhase(new DeadStoreRemovalPhase());
        phaseSuite.appendPhase(new DevirtualizeCallsPhase());
        phaseSuite.appendPhase(CanonicalizerPhase.create());
        phaseSuite.appendPhase(new StrengthenStampsPhase());
        phaseSuite.appendPhase(CanonicalizerPhase.create());
        return phaseSuite;
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

        System.out.format("Code Size; Nodes Before; Nodes After; Is Trivial;" +
                        " Deopt Target; Code Size; Nodes Before; Nodes After; Deopt Entries; Deopt During Call;" +
                        " Entry Points; Direct Calls; Virtual Calls; Method\n");

        List<CompileTask> tasks = new ArrayList<>(compilations.values());
        tasks.sort(Comparator.comparing(t2 -> t2.method.format("%H.%n(%p) %r")));

        for (CompileTask task : tasks) {
            HostedMethod method = task.method;
            CompilationResult result = task.result;

            CompilationInfo ci = method.compilationInfo;
            if (!ci.isDeoptTarget()) {
                numberOfMethods += 1;
                sizeAllMethods += result.getTargetCodeSize();
                System.out.format("%8d; %5d; %5d; %s;", result.getTargetCodeSize(), ci.numNodesBeforeCompilation, ci.numNodesAfterCompilation, ci.isTrivialMethod ? "T" : " ");

                int deoptMethodSize = 0;
                if (ci.deoptTarget != null) {
                    CompilationInfo dci = ci.deoptTarget.compilationInfo;

                    numberOfDeopt += 1;
                    deoptMethodSize = compilations.get(ci.deoptTarget).result.getTargetCodeSize();
                    sizeDeoptMethods += deoptMethodSize;
                    sizeDeoptMethodsInNonDeopt += result.getTargetCodeSize();
                    totalNumDeoptEntryPoints += dci.numDeoptEntryPoints;
                    totalNumDuringCallEntryPoints += dci.numDuringCallEntryPoints;

                    System.out.format(" D; %6d; %5d; %5d; %4d; %4d;", deoptMethodSize, dci.numNodesBeforeCompilation, dci.numNodesAfterCompilation, dci.numDeoptEntryPoints,
                                    dci.numDuringCallEntryPoints);

                } else {
                    sizeNonDeoptMethods += result.getTargetCodeSize();
                    numberOfNonDeopt += 1;
                    System.out.format("  ; %6d; %5d; %5d; %4d; %4d;", 0, 0, 0, 0, 0);
                }

                System.out.format(" %4d; %4d; %4d; %s\n",
                                task.allReasons.stream().filter(t -> t instanceof EntryPointReason).count(),
                                task.allReasons.stream().filter(t -> t instanceof DirectCallReason).count(),
                                task.allReasons.stream().filter(t -> t instanceof VirtualCallReason).count(),
                                method.format("%H.%n(%p) %r"));
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

    private void parseAll() throws InterruptedException {
        executor.init();

        parseDeoptimizationTargetMethods();
        parseAheadOfTimeCompiledMethods();

        // calling start before marking methods for parsing summons evil daemons
        executor.start();
        executor.complete();
        executor.shutdown();
    }

    /**
     * Regular compiled methods. Only entry points and manually marked methods are compiled, all
     * transitively reachable methods are then identified by looking at the callees of already
     * parsed methods.
     */
    private void parseAheadOfTimeCompiledMethods() {
        universe.getMethods().stream()
                        .filter(method -> method.isEntryPoint() || CompilationInfoSupport.singleton().isForcedCompilation(method))
                        .forEach(method -> ensureParsed(method, new EntryPointReason()));

        SubstrateForeignCallsProvider foreignCallsProvider = (SubstrateForeignCallsProvider) runtimeConfig.getProviders().getForeignCalls();
        foreignCallsProvider.getForeignCalls().values().stream()
                        .map(linkage -> (HostedMethod) linkage.getDescriptor().findMethod(runtimeConfig.getProviders().getMetaAccess()))
                        .filter(method -> method.wrapped.isRootMethod())
                        .forEach(method -> ensureParsed(method, new EntryPointReason()));
    }

    private void parseDeoptimizationTargetMethods() {
        /*
         * Deoptimization target code for all methods that were manually marked as deoptimization
         * targets.
         */
        universe.getMethods().stream()
                        .filter(method -> CompilationInfoSupport.singleton().isDeoptTarget(method))
                        .forEach(method -> ensureParsed(universe.createDeoptTarget(method), new EntryPointReason()));

        /*
         * Deoptimization target code for deoptimization testing: all methods that are not
         * blacklisted are possible deoptimization targets. The methods are also flagged so that all
         * possible deoptimization entry points are emitted.
         */
        universe.getMethods().stream()
                        .filter(method -> method.getWrapped().isImplementationInvoked() && canDeoptForTesting(method))
                        .forEach(this::ensureParsedForDeoptTesting);

    }

    private void ensureParsedForDeoptTesting(HostedMethod method) {
        method.compilationInfo.canDeoptForTesting = true;
        ensureParsed(universe.createDeoptTarget(method), new EntryPointReason());
    }

    private void checkTrivial(HostedMethod method) {
        if (!method.compilationInfo.isTrivialMethod() && method.canBeInlined() && InliningUtilities.isTrivialMethod(method.compilationInfo.getGraph())) {
            method.compilationInfo.setTrivialMethod(true);
            inliningProgress = true;
        }
    }

    @SuppressWarnings("try")
    private void inlineTrivialMethods(DebugContext debug) throws InterruptedException {
        for (HostedMethod method : universe.getMethods()) {
            try (DebugContext.Scope s = debug.scope("InlineTrivial", method.compilationInfo.getGraph(), method, this)) {
                if (method.compilationInfo.getGraph() != null) {
                    checkTrivial(method);
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

        int round = 0;
        do {
            inliningProgress = false;
            round++;
            try (Indent ignored = debug.logAndIndent("==== Trivial Inlining  round %d\n", round)) {

                executor.init();
                universe.getMethods().stream().filter(method -> method.compilationInfo.getGraph() != null).forEach(method -> executor.execute(new TrivialInlineTask(method)));

                universe.getMethods().stream().map(method -> method.compilationInfo.getDeoptTargetMethod()).filter(Objects::nonNull).forEach(
                                deoptTargetMethod -> executor.execute(new TrivialInlineTask(deoptTargetMethod)));
                executor.start();
                executor.complete();
                executor.shutdown();
            }
        } while (inliningProgress);
    }

    @SuppressWarnings("try")
    private void doInlineTrivial(DebugContext debug, final HostedMethod method) {
        /*
         * Make a copy of the graph to avoid concurrency problems. Graph manipulations are not
         * thread safe, and another thread can concurrently inline this method.
         */
        final StructuredGraph graph = (StructuredGraph) method.compilationInfo.getGraph().copy(debug);

        try (DebugContext.Scope s = debug.scope("InlineTrivial", graph, method, this)) {

            try {

                try (Indent in = debug.logAndIndent("do inline trivial in %s", method)) {

                    boolean inlined = false;
                    for (Invoke invoke : graph.getInvokes()) {
                        if (invoke instanceof InvokeNode) {
                            throw VMError.shouldNotReachHere("Found InvokeNode without exception edge: invocation of " +
                                            invoke.callTarget().targetMethod().format("%H.%n(%p)") + " in " + (graph.method() == null ? graph.toString() : graph.method().format("%H.%n(%p)")));
                        }

                        if (invoke.useForInlining()) {
                            inlined |= tryInlineTrivial(graph, invoke, !inlined);
                        }
                    }

                    if (inlined) {
                        Providers providers = runtimeConfig.lookupBackend(method).getProviders();
                        CanonicalizerPhase.create().apply(graph, providers);

                        /*
                         * Publish the new graph, it can be picked up immediately by other threads
                         * trying to inline this method.
                         */
                        method.compilationInfo.setGraph(graph);
                        checkTrivial(method);
                        inliningProgress = true;
                    }
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

    private static boolean tryInlineTrivial(StructuredGraph graph, Invoke invoke, boolean firstInline) {
        if (invoke.getInvokeKind().isDirect()) {
            HostedMethod singleCallee = (HostedMethod) invoke.callTarget().targetMethod();
            if (makeInlineDecision(invoke, singleCallee) && InliningUtilities.recursionDepth(invoke, singleCallee) == 0) {
                if (firstInline) {
                    graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "Before inlining");
                }
                InliningUtil.inline(invoke, singleCallee.compilationInfo.getGraph(), true, singleCallee);

                graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After inlining %s with trivial callee %s", invoke, singleCallee.getQualifiedName());
                return true;
            }
        }
        return false;
    }

    private static boolean makeInlineDecision(Invoke invoke, HostedMethod callee) {
        if (!callee.canBeInlined() || callee.getAnnotation(NeverInlineTrivial.class) != null) {
            return false;
        }
        if (callee.shouldBeInlined() || callerAnnotatedWith(invoke, AlwaysInlineAllCallees.class)) {
            return true;
        }
        if (callee.compilationInfo.isTrivialMethod()) {
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
        return annotation != null && annotation.access() == RestrictHeapAccess.Access.NO_ALLOCATION && !annotation.mayBeInlined();
    }

    public static boolean callerAnnotatedWith(Invoke invoke, Class<? extends Annotation> annotationClass) {
        for (FrameState state = invoke.stateAfter(); state != null; state = state.outerFrameState()) {
            if (state.getMethod().getAnnotation(annotationClass) != null) {
                return true;
            }
        }
        return false;
    }

    protected void compileAll() throws InterruptedException {
        executor.init();
        universe.getMethods().stream()
                        .filter(method -> method.isEntryPoint() || CompilationInfoSupport.singleton().isForcedCompilation(method))
                        .forEach(method -> ensureCompiled(method, new EntryPointReason()));

        universe.getMethods().stream()
                        .map(method -> method.compilationInfo.getDeoptTargetMethod())
                        .filter(deoptTargetMethod -> deoptTargetMethod != null)
                        .forEach(deoptTargetMethod -> ensureCompiled(deoptTargetMethod, new EntryPointReason()));

        executor.start();
        executor.complete();
        executor.shutdown();
    }

    protected void ensureParsed(HostedMethod method, CompileReason reason) {
        if (!method.compilationInfo.inParseQueue.getAndSet(true)) {
            executor.execute(new ParseTask(method, reason));
        }
    }

    protected void doParse(DebugContext debug, ParseTask task) {
        ParseFunction fun = task.method.compilationInfo.getCustomParseFunction();
        if (fun == null) {
            fun = this::defaultParseFunction;
        }
        fun.parse(debug, task.method, task.reason, runtimeConfig);
    }

    @SuppressWarnings("try")
    private void defaultParseFunction(DebugContext debug, HostedMethod method, CompileReason reason, RuntimeConfiguration config) {
        if ((!NativeImageOptions.AllowFoldMethods.getValue() && method.getAnnotation(Fold.class) != null) || method.getAnnotation(NodeIntrinsic.class) != null) {
            throw VMError.shouldNotReachHere("Parsing method annotated with @" + Fold.class.getSimpleName() + " or " + NodeIntrinsic.class.getSimpleName() + ": " +
                            method.format("%H.%n(%p)") +
                            ". Make sure you have used Graal annotation processors on the parent-project of the method's declaring class.");
        }

        HostedProviders providers = (HostedProviders) config.lookupBackend(method).getProviders();
        boolean needParsing = false;
        StructuredGraph graph = method.buildGraph(debug, method, providers, Purpose.AOT_COMPILATION);
        if (graph == null) {
            InvocationPlugin plugin = providers.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method);
            if (plugin != null && !plugin.inlineOnly()) {
                Bytecode code = new ResolvedJavaMethodBytecode(method);
                // DebugContext debug = new DebugContext(options, providers.getSnippetReflection());
                graph = new SubstrateIntrinsicGraphBuilder(getCustomizedOptions(debug), debug, providers, code).buildGraph(plugin);
            }
        }
        if (graph == null && method.isNative() && NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            graph = DeletedMethod.buildGraph(debug, method, providers, DeletedMethod.NATIVE_MESSAGE);
        }
        if (graph == null) {
            needParsing = true;
            graph = new StructuredGraph.Builder(getCustomizedOptions(debug), debug).method(method).build();
        }

        try (DebugContext.Scope s = debug.scope("Parsing", graph, method, this)) {

            try {
                if (needParsing) {
                    GraphBuilderConfiguration gbConf = createHostedGraphBuilderConfiguration(providers, method);
                    new HostedGraphBuilderPhase(providers, gbConf, getOptimisticOpts(), null, providers.getWordTypes()).apply(graph);

                } else {
                    graph.setGuardsStage(GuardsStage.FIXED_DEOPTS);
                }

                method.compilationInfo.graph = graph;

                afterParse(method);
                PhaseSuite<HighTierContext> afterParseSuite = afterParseCanonicalization();
                afterParseSuite.apply(method.compilationInfo.graph, new HighTierContext(providers, afterParseSuite, getOptimisticOpts()));
                assert GraphOrder.assertSchedulableGraph(method.compilationInfo.getGraph());

                for (Invoke invoke : graph.getInvokes()) {
                    if (!canBeUsedForInlining(invoke)) {
                        invoke.setUseForInlining(false);
                    }
                    CallTargetNode targetNode = invoke.callTarget();
                    ensureParsed(method, reason, targetNode, (HostedMethod) targetNode.targetMethod(), targetNode.invokeKind().isIndirect() || targetNode instanceof IndirectCallTargetNode);
                }
                for (Node n : graph.getNodes()) {
                    if (n instanceof MacroNode) {
                        /*
                         * A MacroNode might be lowered back to a regular invoke. At this point we
                         * do not know if that happens, but we need to prepared and have the graph
                         * of the potential callee parsed as if the MacroNode was an Invoke.
                         */
                        MacroNode macroNode = (MacroNode) n;
                        ensureParsed(method, reason, null, (HostedMethod) macroNode.getTargetMethod(), macroNode.getInvokeKind().isIndirect());
                    }
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
                ensureParsed(invokeImplementation, new VirtualCallReason(method, invokeImplementation, reason));
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
                ensureParsed(invokeTarget, new DirectCallReason(method, reason));
            }
        }
    }

    @SuppressWarnings("unused")
    protected void afterParse(HostedMethod method) {
    }

    protected OptionValues getCustomizedOptions(DebugContext debug) {
        return debug.getOptions();
    }

    protected GraphBuilderConfiguration createHostedGraphBuilderConfiguration(HostedProviders providers, HostedMethod method) {
        GraphBuilderConfiguration gbConf = GraphBuilderConfiguration.getDefault(providers.getGraphBuilderPlugins()).withBytecodeExceptionMode(BytecodeExceptionMode.CheckAll);

        if (SubstrateOptions.Optimize.getValue() <= 0 && !method.isDeoptTarget()) {
            /*
             * Disabling liveness analysis preserves the values of local variables beyond the
             * bytecode-liveness. This greatly helps debugging. When local variable numbers are
             * reused by javac, local variables can still get illegal values. Since we cannot
             * "restore" such illegal values during deoptimization, we cannot disable liveness
             * analysis for deoptimization target methods.
             */
            gbConf = gbConf.withRetainLocalVariables(true);
        }

        return gbConf;
    }

    protected boolean containsStackValueNode(HostedMethod method) {
        return universe.getBigBang().getHostVM().containsStackValueNode(method.wrapped);
    }

    protected boolean canBeUsedForInlining(Invoke invoke) {
        HostedMethod caller = (HostedMethod) invoke.asNode().graph().method();
        HostedMethod callee = (HostedMethod) invoke.callTarget().targetMethod();

        if (canDeoptForTesting(caller) && Modifier.isNative(callee.getModifiers())) {
            /*
             * We must not deoptimize in the stubs for native functions, since they don't have a
             * valid bytecode state.
             */
            return false;
        }
        if (canDeoptForTesting(caller) && containsStackValueNode(callee)) {
            /*
             * We must not inline a method that has stack values and can be deoptimized.
             */
            return false;
        }

        if (caller.compilationInfo.isDeoptTarget()) {
            if (caller.compilationInfo.isDeoptEntry(invoke.bci(), true, false)) {
                /*
                 * The call can be on the stack for a deoptimization, so we need an actual
                 * non-inlined invoke to deoptimize too.
                 *
                 * We could lift this restriction by providing an explicit deopt entry point (with
                 * the correct exception handling edges) in addition to the inlined method.
                 */
                return false;
            }
            if (CompilationInfoSupport.singleton().isDeoptInliningExclude(callee)) {
                /*
                 * The graphs for runtime compilation have an intrinisic for the callee, which might
                 * alter the behavior. Be safe and do not inline, otherwise we might optimize too
                 * aggressively.
                 *
                 * For example, the Truffle method CompilerDirectives.inCompiledCode is
                 * intrinisified to return a constant with the opposite value than returned by the
                 * method we would inline here, i.e., we would constant-fold away the compiled-code
                 * only code (which is the code we need deoptimization entry points for).
                 */
                return false;
            }
        }

        if (callee.getAnnotation(Specialize.class) != null) {
            return false;
        }
        if (callerAnnotatedWith(invoke, Specialize.class) && callee.getAnnotation(DeoptTest.class) != null) {
            return false;
        }
        Uninterruptible calleeUninterruptible = callee.getAnnotation(Uninterruptible.class);
        if (calleeUninterruptible != null && !calleeUninterruptible.mayBeInlined() && caller.getAnnotation(Uninterruptible.class) == null) {
            return false;
        }
        if (!mustNotAllocateCallee(caller) && mustNotAllocate(callee)) {
            return false;
        }
        if (!callee.canBeInlined()) {
            return false;
        }
        return invoke.useForInlining();
    }

    private static void handleSpecialization(final HostedMethod method, CallTargetNode targetNode, HostedMethod invokeTarget, HostedMethod invokeImplementation) {
        if (method.getAnnotation(Specialize.class) != null && !method.compilationInfo.isDeoptTarget() && invokeTarget.getAnnotation(DeoptTest.class) != null) {
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
        CompileTask task = new CompileTask(method, reason);
        CompileTask oldTask = compilations.putIfAbsent(method, task);
        if (oldTask != null) {
            // Method is already scheduled for compilation.
            if (oldTask.allReasons != null) {
                oldTask.allReasons.add(reason);
            }
            return;
        }
        if (method.compilationInfo.specializedArguments != null) {
            // Do the specialization: replace the argument locals with the constant arguments.
            StructuredGraph graph = method.compilationInfo.graph;

            /* Check that graph is in good shape before compilation. */
            assert GraphOrder.assertSchedulableGraph(graph);

            int idx = 0;
            for (ConstantNode argument : method.compilationInfo.specializedArguments) {
                ParameterNode local = graph.getParameter(idx++);
                if (local != null) {
                    local.replaceAndDelete(ConstantNode.forConstant(argument.asJavaConstant(), runtimeConfig.getProviders().getMetaAccess(), graph));
                }
            }
        }
        executor.execute(task);
        method.setCompiled();
    }

    class HostedCompilationResultBuilderFactory implements CompilationResultBuilderFactory {
        @Override
        public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder,
                        FrameContext frameContext, OptionValues options, DebugContext debug, CompilationResult compilationResult, Register uncompressedNullRegister) {
            return new CompilationResultBuilder(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, uncompressedNullRegister,
                            EconomicMap.wrapMap(dataCache));
        }
    }

    protected CompilationResult doCompile(DebugContext debug, final HostedMethod method, CompilationIdentifier compilationIdentifier, CompileReason reason) {
        CompileFunction fun = method.compilationInfo.getCustomCompileFunction();
        if (fun == null) {
            fun = this::defaultCompileFunction;
        }
        return fun.compile(debug, method, compilationIdentifier, reason, runtimeConfig);
    }

    @SuppressWarnings("try")
    private CompilationResult defaultCompileFunction(DebugContext debug, HostedMethod method, CompilationIdentifier compilationIdentifier, CompileReason reason, RuntimeConfiguration config) {
        if (NativeImageOptions.PrintAOTCompilation.getValue()) {
            System.out.println("Compiling " + method.format("%r %H.%n(%p)") + "  [" + reason + "]");
        }

        try {
            SubstrateBackend backend = config.lookupBackend(method);

            StructuredGraph graph = method.compilationInfo.graph;
            assert graph != null : method;
            /* Operate on a copy, to keep the original graph intact for later inlining. */
            graph = graph.copyWithIdentifier(compilationIdentifier, debug);

            /* Check that graph is in good shape before compilation. */
            assert GraphOrder.assertSchedulableGraph(graph);

            try (DebugContext.Scope s = debug.scope("Compiling", graph, method, this)) {

                if (deoptimizeAll && method.compilationInfo.canDeoptForTesting) {
                    insertDeoptTests(method, graph);
                }
                method.compilationInfo.numNodesBeforeCompilation = graph.getNodeCount();
                method.compilationInfo.numDeoptEntryPoints = graph.getNodes().filter(DeoptEntryNode.class).count();
                method.compilationInfo.numDuringCallEntryPoints = graph.getNodes(MethodCallTargetNode.TYPE).snapshot().stream()
                                .map(MethodCallTargetNode::invoke)
                                .filter(invoke -> method.compilationInfo.isDeoptEntry(invoke.bci(), true, false))
                                .count();

                Suites suites = method.compilationInfo.isDeoptTarget() ? deoptTargetSuites : regularSuites;
                LIRSuites lirSuites = method.compilationInfo.isDeoptTarget() ? deoptTargetLIRSuites : regularLIRSuites;

                CompilationResult result = backend.newCompilationResult(compilationIdentifier, method.format("%H.%n(%p)"));

                try (Indent indent = debug.logAndIndent("compile %s", method)) {
                    GraalCompiler.compileGraph(graph, method, backend.getProviders(), backend, null, getOptimisticOpts(), method.getProfilingInfo(), suites, lirSuites, result,
                                    new HostedCompilationResultBuilderFactory(), false);
                }
                method.getProfilingInfo().setCompilerIRSize(StructuredGraph.class, method.compilationInfo.graph.getNodeCount());
                method.compilationInfo.numNodesAfterCompilation = graph.getNodeCount();

                if (method.compilationInfo.isDeoptTarget()) {
                    assert verifyDeoptTarget(method, result);
                }
                for (Infopoint infopoint : result.getInfopoints()) {
                    if (infopoint instanceof Call) {
                        Call call = (Call) infopoint;
                        HostedMethod callTarget = (HostedMethod) call.target;
                        if (call.direct) {
                            ensureCompiled(callTarget, new DirectCallReason(method, reason));
                        } else if (callTarget != null && callTarget.getImplementations() != null) {
                            for (HostedMethod impl : callTarget.getImplementations()) {
                                ensureCompiled(impl, new VirtualCallReason(method, callTarget, reason));
                            }
                        }
                    }
                }

                /* Shrink resulting code array to minimum size, to reduze memory footprint. */
                if (result.getTargetCode().length > result.getTargetCodeSize()) {
                    result.setTargetCode(Arrays.copyOf(result.getTargetCode(), result.getTargetCodeSize()), result.getTargetCodeSize());
                }

                return result;
            }
        } catch (Throwable ex) {
            GraalError error = ex instanceof GraalError ? (GraalError) ex : new GraalError(ex);
            error.addContext("method: " + method.format("%r %H.%n(%p)") + "  [" + reason + "]");
            throw error;
        }
    }

    protected void removeDeoptTargetOptimizations(Suites suites) {
        GraalConfiguration.instance().removeDeoptTargetOptimizations(suites);

        PhaseSuite<HighTierContext> highTier = suites.getHighTier();
        VMError.guarantee(highTier.removePhase(PartialEscapePhase.class));
        VMError.guarantee(highTier.removePhase(EarlyReadEliminationPhase.class));
        PhaseSuite<MidTierContext> midTier = suites.getMidTier();
        VMError.guarantee(midTier.removePhase(FloatingReadPhase.class));
        PhaseSuite<LowTierContext> lowTier = suites.getLowTier();
        ((FixReadsPhase) lowTier.findPhase(FixReadsPhase.class).previous()).setReplaceInputsWithConstants(false);
    }

    private static void removeDeoptTargetOptimizations(LIRSuites lirSuites) {
        lirSuites.getPostAllocationOptimizationStage().findPhase(RedundantMoveElimination.class).remove();
        lirSuites.getAllocationStage().findPhaseInstance(RegisterAllocationPhase.class).setNeverSpillConstants(true);
    }

    private static boolean verifyDeoptTarget(HostedMethod method, CompilationResult result) {
        Map<Long, BytecodeFrame> encodedBciMap = new HashMap<>();

        /*
         * All deopt targets must have a graph.
         */
        assert method.compilationInfo.graph != null : "Deopt target must have a graph.";

        /*
         * No deopt targets can have a StackValueNode in the graph.
         */
        assert method.compilationInfo.graph.getNodes(StackValueNode.TYPE).isEmpty() : "No stack value nodes must be present in deopt target.";

        for (Infopoint infopoint : result.getInfopoints()) {
            if (infopoint.debugInfo != null) {
                DebugInfo debugInfo = infopoint.debugInfo;
                if (!debugInfo.hasFrame()) {
                    continue;
                }
                BytecodeFrame topFrame = debugInfo.frame();

                BytecodeFrame rootFrame = topFrame;
                while (rootFrame.caller() != null) {
                    rootFrame = rootFrame.caller();
                }
                assert rootFrame.getMethod().equals(method);

                boolean isDeoptEntry = method.compilationInfo.isDeoptEntry(rootFrame.getBCI(), rootFrame.duringCall, rootFrame.rethrowException);
                if (infopoint instanceof DeoptEntryInfopoint) {
                    assert isDeoptEntry;
                } else if (rootFrame.duringCall && isDeoptEntry) {
                    assert infopoint instanceof Call || isSingleSteppingInfopoint(infopoint);
                } else {
                    continue;
                }

                long encodedBci = FrameInfoEncoder.encodeBci(rootFrame.getBCI(), rootFrame.duringCall, rootFrame.rethrowException);
                if (encodedBciMap.containsKey(encodedBci)) {
                    assert encodedBciMap.get(encodedBci).equals(rootFrame) : "duplicate encoded bci " + encodedBci + " in deopt target " + method + " with different debug info:\n\n" + rootFrame +
                                    "\n\n" + encodedBciMap.get(encodedBci);
                }
                encodedBciMap.put(encodedBci, rootFrame);
            }
        }

        return true;
    }

    private static boolean isSingleSteppingInfopoint(Infopoint infopoint) {
        return infopoint.reason == InfopointReason.METHOD_START ||
                        infopoint.reason == InfopointReason.METHOD_END ||
                        infopoint.reason == InfopointReason.BYTECODE_POSITION;
    }

    /**
     * Returns true if a method should be considered as deoptimization source. This is only a
     * feature for testing. Note that usually all image compiled methods cannot deoptimize.
     */
    protected boolean canDeoptForTesting(HostedMethod method) {
        if (method.getName().equals("<clinit>")) {
            /* Cannot deoptimize into static initializers. */
            return false;
        }

        if (method.getAnnotation(DeoptTest.class) != null) {
            return true;
        }

        if (method.isEntryPoint()) {
            /*
             * Entry points from C have special entry/exit nodes added, so they cannot be
             * deoptimized.
             */
            return false;
        }
        if (method.isNative()) {
            /*
             * Native methods (i.e., the stubs that actually perform the native calls) cannot be
             * deoptimized.
             */
            return false;
        }
        if (method.wrapped.isIntrinsicMethod()) {
            return false;
        }
        if (method.getAnnotation(Uninterruptible.class) != null) {
            return false;
        }
        if (method.getAnnotation(RestrictHeapAccess.class) != null) {
            return false;
        }
        if (StubCallingConvention.Utils.hasStubCallingConvention(method)) {
            /* Deoptimization runtime cannot fill the callee saved registers. */
            return false;
        }
        if (containsStackValueNode(method)) {
            return false;
        }

        if (deoptimizeAll) {
            /*
             * The DeoptimizeAll option is set. So we use all methods for deoptimization testing.
             * Exclude some "runtime" methods, like the heap code, via this blacklist. Issue GR-1706
             * tracks the bug in DebugValueMap.
             */
            String className = method.getDeclaringClass().getName();
            if (className.contains("/svm/core/code/CodeInfoEncoder") ||
                            className.contains("com/oracle/svm/core/thread/JavaThreads") ||
                            className.contains("com/oracle/svm/core/heap/") ||
                            className.contains("com/oracle/svm/core/genscavenge/") ||
                            className.contains("com/oracle/svm/core/thread/VMOperationControl") ||
                            className.contains("debug/internal/DebugValueMap") && method.getName().equals("registerTopLevel")) {
                return false;
            }
            /*
             * Method without bytecodes, e.g., methods that have a manually constructed graph, are
             * usually not deoptimizable. This needs to change as soon as we want to runtime compile
             * our synthetic annotation methods.
             */
            if (method.getCode() == null) {
                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * Inserts a call to {@link DeoptTester#deoptTest} right after FixedWithNextNode StateSplits.
     *
     * @param method method that is being augmented with deopt test calls
     * @param graph The graph of a deoptimizable method or the corresponding deopt target method.
     */
    private static void insertDeoptTests(HostedMethod method, StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof FixedWithNextNode && node instanceof StateSplit && !(node instanceof InvokeNode) && !(node instanceof ForeignCallNode) && !(node instanceof DeoptTestNode) &&
                            !(method.isSynchronized() && node instanceof StartNode)) {
                FixedWithNextNode fixedWithNext = (FixedWithNextNode) node;
                FixedNode next = fixedWithNext.next();
                DeoptTestNode testNode = graph.add(new DeoptTestNode());
                fixedWithNext.setNext(null);
                testNode.setNext(next);
                fixedWithNext.setNext(testNode);
                if (((StateSplit) node).hasSideEffect() && ((StateSplit) node).stateAfter() != null) {
                    testNode.setStateAfter(((StateSplit) node).stateAfter().duplicateWithVirtualState());
                } else {
                    testNode.setStateAfter(SnippetTemplate.findLastFrameState((FixedNode) node).duplicateWithVirtualState());
                }
            }
        }
    }

    public Map<HostedMethod, CompilationResult> getCompilations() {
        Map<HostedMethod, CompilationResult> result = new TreeMap<>();
        for (Entry<HostedMethod, CompileTask> entry : compilations.entrySet()) {
            result.put(entry.getKey(), entry.getValue().result);
        }
        return result;
    }
}
