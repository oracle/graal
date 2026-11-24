/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.pgo.BranchProfileRecorder;
import com.oracle.svm.core.pgo.BranchProfileThreadCounters;
import com.oracle.svm.core.pgo.CallCountProfileRecorder;
import com.oracle.svm.core.pgo.ProfileDumper;
import com.oracle.svm.core.pgo.StackSampleRecorder;
import com.oracle.svm.core.pgo.SwitchProfileRecorder;
import com.oracle.svm.core.pgo.TypeProfileRecorder;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.shared.util.LogUtils;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.APIOption;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.loop.phases.LoopSafepointEliminationPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.AbstractInliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;

/**
 * Builds the instrumented image of {@code --pgo-instrument}: inserts the
 * {@link PGOInstrumentationPhases} into the high tier, registers the recorders as thread listeners,
 * the stack sampler and the teardown hook that writes the profile, and prints how many sites were
 * instrumented.
 */
@AutomaticallyRegisteredFeature
public final class PGOInstrumentationFeature implements InternalFeature {

    public static final class Options {
        // @formatter:off
        @APIOption(name = "pgo-instrument")//
        @Option(help = "Instrument the image to record an execution profile, written as an .iprof file when the application exits. " +
                       "Branches, switches, receiver types, monitor enters and call edges are counted in each root before priority inlining " +
                       "and in each callee the priority inliner expands, and call stacks are sampled. " +
                       "Raises the default optimization level -O2 to -O3; an -O given after this option wins.")//
        public static final HostedOptionKey<Boolean> PGOInstrument = new InstrumentationOption();

        @Option(help = "Also sample call stacks periodically in the instrumented image and emit samplingProfiles. Interval: -XX:PGOSamplingIntervalMillis. " +
                       "The sampler runs at safepoint polls, so a sampling image keeps the polls of counted loops unless -H:RemoveLoopSafepoints is given. " +
                       "It uses a thread's recurring callback, so a thread is not sampled while another recurring callback is installed on it, " +
                       "such as one registered with Threading.registerRecurringCallback.")//
        public static final HostedOptionKey<Boolean> PGOSampleStacks = new HostedOptionKey<>(true);

        @Option(help = "Record branch (IfNode) frequencies in conditionalProfiles.")//
        public static final HostedOptionKey<Boolean> PGOProfileBranches = new HostedOptionKey<>(true);

        @Option(help = "Record concrete receiver frequencies at indirect calls and emit virtualInvokeProfiles.")//
        public static final HostedOptionKey<Boolean> PGOProfileReceivers = new HostedOptionKey<>(true);

        @Option(help = "Record context-sensitive method-entry counts and emit callCountProfiles.")//
        public static final HostedOptionKey<Boolean> PGOProfileCallCounts = new HostedOptionKey<>(true);

        @Option(help = "Record switch successor frequencies in conditionalProfiles.")//
        public static final HostedOptionKey<Boolean> PGOProfileSwitches = new HostedOptionKey<>(true);

        @Option(help = "Record exact runtime types at monitor-enter sites and emit monitorProfiles.")//
        public static final HostedOptionKey<Boolean> PGOProfileMonitors = new HostedOptionKey<>(true);
        // @formatter:on

        private static final class InstrumentationOption extends HostedOptionKey<Boolean> {
            private InstrumentationOption() {
                super(false);
            }

            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                super.onValueUpdate(values, oldValue, newValue);
                if (Boolean.TRUE.equals(newValue)) {
                    GraalOptions.TrackNodeSourcePosition.update(values, true);
                    /* The stack sampler runs as a recurring callback on every Java thread. */
                    RecurringCallbackSupport.ConcealedOptions.SupportRecurringCallback.update(values, true);
                    /* Instrument at the level of the optimized build. */
                    PGOUtils.raiseDefaultToO3(values);
                }
            }
        }
    }

    public static boolean enabled() {
        return Options.PGOInstrument.getValue();
    }

    /**
     * The options for compiling a method of the image: {@code options}, except that an
     * instrumented image that samples stacks keeps the safepoint polls of counted loops. The
     * sampler runs only at polls, and the compiler removes the polls of counted loops over an int
     * range. Without them, a hot counted loop gets one sample at the first poll after it, however
     * long it ran, so its time is missing from the profile. Decided from the parsed options, so
     * the order of the options on the command line does not matter, and a
     * {@code -H:RemoveLoopSafepoints} given there wins.
     */
    public static OptionValues compilationOptions(OptionValues options) {
        if (keepsLoopSafepoints(options)) {
            return new OptionValues(options, LoopSafepointEliminationPhase.Options.RemoveLoopSafepoints, false);
        }
        return options;
    }

    /** Whether {@link #compilationOptions} keeps the safepoint polls of counted loops. */
    static boolean keepsLoopSafepoints(OptionValues options) {
        return Options.PGOInstrument.getValue(options) && Options.PGOSampleStacks.getValue(options) && !LoopSafepointEliminationPhase.Options.RemoveLoopSafepoints.hasBeenSet(options);
    }

    /**
     * Number of counter nodes in {@code graph}. The priority inliner leaves them out of its size
     * estimates, so that counters do not make roots and expanded callees look larger.
     */
    public static int instrumentationNodeCount(StructuredGraph graph) {
        if (!enabled()) {
            return 0;
        }
        return graph.getNodes(PGOInstrumentationNode.TYPE).count();
    }

    /**
     * Called for every decoded priority-inliner cutoff graph under its exact caller context. Like a
     * root, an expansion inside a Native Image runtime root gets no sites: that code may exist only
     * in the instrumented image (the recorders), and counting inside it would also count while the
     * recorders run.
     */
    public static void instrumentExpandedGraph(StructuredGraph graph, NodeSourcePosition inliningContext) {
        if (enabled() && !inRuntimeRoot(inliningContext)) {
            if (Options.PGOProfileBranches.getValue() || Options.PGOProfileSwitches.getValue()) {
                BranchProfileInstrumentationPhase.instrumentGraph(graph, inliningContext);
            }
            if (Options.PGOProfileReceivers.getValue()) {
                ReceiverProfileInstrumentationPhase.instrumentGraph(graph, inliningContext);
            }
            if (Options.PGOProfileCallCounts.getValue()) {
                CallCountProfileInstrumentationPhase.instrumentGraph(graph, inliningContext);
            }
            if (Options.PGOProfileMonitors.getValue()) {
                MonitorProfileInstrumentationPhase.instrumentGraph(graph);
            }
        }
    }

    private static boolean inRuntimeRoot(NodeSourcePosition inliningContext) {
        return inliningContext != null && ProfilingUtilities.isNativeImageRuntimeCode(inliningContext.getRootMethod());
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return enabled();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!enabled()) {
            return;
        }
        if (!SubstrateOptions.isMaximumOptimizationLevel()) {
            /* Below -O3 the priority inliner explores less, so code only -O3 inlines gets no sites in its caller's context. */
            LogUtils.warning("--pgo-instrument should be used with -O3, the level of the optimized build; otherwise code that only -O3 inlines is profiled without its caller's context");
        }
        RuntimeSupport.getRuntimeSupport().addTearDownHook(ProfileDumper.getTeardownHook());
        if (Options.PGOProfileBranches.getValue()) {
            BranchProfileRecorder.enable();
            ThreadListenerSupport.get().register(BranchProfileThreadCounters.create());
        }
        if (callCountRecorderEnabled()) {
            ThreadListenerSupport.get().register(CallCountProfileRecorder.createRecorder());
        }
        if (Options.PGOProfileSwitches.getValue()) {
            SwitchProfileRecorder.enable();
        }
        if (typeProfileRecorderEnabled()) {
            if (Options.PGOProfileMonitors.getValue()) {
                TypeProfileRecorder.enableMonitorProfiles();
            }
            ThreadListenerSupport.get().register(TypeProfileRecorder.create());
        }
        if (Options.PGOSampleStacks.getValue()) {
            ThreadListenerSupport.get().register(StackSampleRecorder.create());
        }
    }

    private static boolean callCountRecorderEnabled() {
        return enabled() && (Options.PGOProfileCallCounts.getValue() || Options.PGOProfileSwitches.getValue());
    }

    private static boolean typeProfileRecorderEnabled() {
        return enabled() && (Options.PGOProfileReceivers.getValue() || Options.PGOProfileMonitors.getValue());
    }

    /** Slow paths of the instrumentation snippets run as foreign calls so that the snippets stay free of atomics. */
    private static List<SubstrateForeignCallDescriptor> foreignCalls() {
        List<SubstrateForeignCallDescriptor> descriptors = new ArrayList<>();
        if (callCountRecorderEnabled()) {
            descriptors.add(CallCountProfileRecorder.INCREMENT_SLOW_PATH);
        }
        if (typeProfileRecorderEnabled()) {
            descriptors.add(TypeProfileRecorder.RECORD_SLOW_PATH);
        }
        return descriptors;
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        for (SubstrateForeignCallDescriptor descriptor : foreignCalls()) {
            foreignCalls.register(descriptor);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl accessImpl = (BeforeAnalysisAccessImpl) access;
        for (SubstrateForeignCallDescriptor descriptor : foreignCalls()) {
            accessImpl.getBigBang().addRootMethod((AnalysisMethod) descriptor.findMethod(accessImpl.getMetaAccess()), true,
                            "PGO instrumentation foreign call, registered in " + PGOInstrumentationFeature.class);
        }
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        if (hosted && enabled()) {
            if (Options.PGOProfileBranches.getValue(options)) {
                providers.getReplacements().registerSnippetTemplateCache(new BranchProfileCounterNode.Templates(options, providers));
            }
            if (Options.PGOProfileCallCounts.getValue(options) || Options.PGOProfileSwitches.getValue(options)) {
                providers.getReplacements().registerSnippetTemplateCache(new CallCountProfileCounterNode.Templates(options, providers));
            }
            if (Options.PGOProfileReceivers.getValue(options) || Options.PGOProfileMonitors.getValue(options)) {
                providers.getReplacements().registerSnippetTemplateCache(new TypeProfileCounterNode.Templates(options, providers));
            }
        }
    }

    @Override
    public void registerGraalPhases(Providers providers, Suites suites, boolean hosted, boolean fallback) {
        if (!hosted || fallback || !enabled()) {
            return;
        }
        PhaseSuite<HighTierContext> highTier = suites.getHighTier();
        addBeforeInliner(highTier, new PGOInstrumentationPhases(Options.PGOProfileReceivers.getValue(), Options.PGOProfileMonitors.getValue(),
                        Options.PGOProfileBranches.getValue() || Options.PGOProfileSwitches.getValue(), Options.PGOProfileCallCounts.getValue()));
    }

    /**
     * Adds the instrumentation phases immediately before the inliner, so every branch and call is
     * instrumented in the shape of the graph before inlining; the inliner instruments the graphs it
     * expands (see {@link #instrumentExpandedGraph}). Phases placed after the inliner would see
     * branches that inlining and canonicalization had already split or folded, and miss every call
     * the instrumented image inlined or devirtualized.
     */
    private static void addBeforeInliner(PhaseSuite<HighTierContext> highTier, BasePhase<HighTierContext> phase) {
        ListIterator<BasePhase<? super HighTierContext>> inliner = highTier.findPhase(AbstractInliningPhase.class);
        if (inliner == null) {
            highTier.prependPhase(phase);
            return;
        }
        /* findPhase returns an iterator positioned immediately after the inliner. */
        inliner.previous();
        inliner.add(phase);
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (!enabled()) {
            return;
        }
        BranchProfileRecorder.sealRegistry();
        if (typeProfileRecorderEnabled()) {
            /* Name every image type by its hub id so dynamic receiver and monitor types decode. */
            for (HostedType type : ((FeatureImpl.AfterCompilationAccessImpl) access).getUniverse().getTypes()) {
                if (type.getWrapped().isReachable() && type.getHub() != null) {
                    TypeProfileRecorder.registerTypeDescriptor(type.getHub().getTypeID(), type.getName());
                }
            }
        }
        LogUtils.prefixInfo("PGO", "instrumentation: %d IfNodes instrumented, %d skipped; switches=%d, skipped=%d; receiver invokes=%d, skipped=%d; monitor enters=%d; call edges=%d, skipped=%d;" +
                        " in compiled code: branch sites=%d, call-count counters=%d, receiver sites=%d",
                        BranchProfileInstrumentationPhase.instrumentedBranches(), BranchProfileInstrumentationPhase.skippedBranches(),
                        BranchProfileInstrumentationPhase.instrumentedSwitches(), BranchProfileInstrumentationPhase.skippedSwitches(),
                        ReceiverProfileInstrumentationPhase.instrumentedInvokes(), ReceiverProfileInstrumentationPhase.skippedInvokes(),
                        MonitorProfileInstrumentationPhase.instrumentedMonitors(),
                        CallCountProfileInstrumentationPhase.instrumented(), CallCountProfileInstrumentationPhase.skipped(),
                        BranchProfileRecorder.numberedSiteCount(), CallCountProfileRecorder.numberedCounterCount(), TypeProfileRecorder.numberedReceiverSiteCount());
    }
}
