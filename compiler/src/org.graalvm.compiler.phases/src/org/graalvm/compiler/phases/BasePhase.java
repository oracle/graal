/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.CompilerPhaseScope;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.MemUseTrackerKey;
import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.Mark;
import org.graalvm.compiler.graph.Graph.NodeEvent;
import org.graalvm.compiler.graph.Graph.NodeEventListener;
import org.graalvm.compiler.graph.Graph.NodeEventScope;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.contract.NodeCostUtil;
import org.graalvm.compiler.phases.contract.PhaseSizeContract;

import jdk.vm.ci.meta.JavaMethod;

/**
 * Base class for all compiler phases. Subclasses should be stateless. There will be one global
 * instance for each compiler phase that is shared for all compilations. VM-, target- and
 * compilation-specific data can be passed with a context object.
 */
public abstract class BasePhase<C> implements PhaseSizeContract {

    public static class PhaseOptions {
        // @formatter:off
        @Option(help = "Verify before - after relation of the relative, computed, code size of a graph", type = OptionType.Debug)
        public static final OptionKey<Boolean> VerifyGraalPhasesSize = new OptionKey<>(false);
        @Option(help = "Minimal size in NodeSize to check the graph size increases of phases.", type = OptionType.Debug)
        public static final OptionKey<Integer> MinimalGraphNodeSizeCheckSize = new OptionKey<>(1000);
        @Option(help = "Exclude certain phases from compilation, either unconditionally or with a method filter", type = OptionType.Debug)
        public static final OptionKey<String> CompilationExcludePhases = new OptionKey<>(null);
        // @formatter:on
    }

    /**
     * Records time spent in {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final TimerKey timer;

    /**
     * Counts calls to {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final CounterKey executionCount;

    /**
     * Accumulates the {@linkplain Graph#getNodeCount() live node count} of all graphs sent to
     * {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final CounterKey inputNodesCount;

    /**
     * Records memory usage within {@link #apply(StructuredGraph, Object, boolean)}.
     */
    private final MemUseTrackerKey memUseTracker;

    /** Lazy initialization to create pattern only when assertions are enabled. */
    static class NamePatternHolder {
        static final Pattern NAME_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]+");
    }

    public static class BasePhaseStatistics {
        /**
         * Records time spent in {@link BasePhase#apply(StructuredGraph, Object, boolean)}.
         */
        private final TimerKey timer;

        /**
         * Counts calls to {@link BasePhase#apply(StructuredGraph, Object, boolean)}.
         */
        private final CounterKey executionCount;

        /**
         * Accumulates the {@linkplain Graph#getNodeCount() live node count} of all graphs sent to
         * {@link BasePhase#apply(StructuredGraph, Object, boolean)}.
         */
        private final CounterKey inputNodesCount;

        /**
         * Records memory usage within {@link BasePhase#apply(StructuredGraph, Object, boolean)}.
         */
        private final MemUseTrackerKey memUseTracker;

        public BasePhaseStatistics(Class<?> clazz) {
            timer = DebugContext.timer("PhaseTime_%s", clazz).doc("Time spent in phase.");
            executionCount = DebugContext.counter("PhaseCount_%s", clazz).doc("Number of phase executions.");
            memUseTracker = DebugContext.memUseTracker("PhaseMemUse_%s", clazz).doc("Memory allocated in phase.");
            inputNodesCount = DebugContext.counter("PhaseNodes_%s", clazz).doc("Number of nodes input to phase.");
        }
    }

    private static final ClassValue<BasePhaseStatistics> statisticsClassValue = new ClassValue<BasePhaseStatistics>() {
        @Override
        protected BasePhaseStatistics computeValue(Class<?> c) {
            return new BasePhaseStatistics(c);
        }
    };

    private static BasePhaseStatistics getBasePhaseStatistics(Class<?> c) {
        return statisticsClassValue.get(c);
    }

    protected BasePhase() {
        BasePhaseStatistics statistics = getBasePhaseStatistics(getClass());
        timer = statistics.timer;
        executionCount = statistics.executionCount;
        memUseTracker = statistics.memUseTracker;
        inputNodesCount = statistics.inputNodesCount;
    }

    public final void apply(final StructuredGraph graph, final C context) {
        apply(graph, context, true);
    }

    private BasePhase<?> getEnclosingPhase(DebugContext debug) {
        for (Object c : debug.context()) {
            if (c != this && c instanceof BasePhase) {
                if (!(c instanceof PhaseSuite)) {
                    return (BasePhase<?>) c;
                }
            }
        }
        return null;
    }

    private boolean dumpBefore(final StructuredGraph graph, final C context, boolean isTopLevel) {
        DebugContext debug = graph.getDebug();
        if (isTopLevel && (debug.isDumpEnabled(DebugContext.VERBOSE_LEVEL) || shouldDumpBeforeAtBasicLevel() && debug.isDumpEnabled(DebugContext.BASIC_LEVEL))) {
            if (shouldDumpBeforeAtBasicLevel()) {
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Before phase %s", getName());
            } else {
                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "Before phase %s", getName());
            }
        } else if (!isTopLevel && debug.isDumpEnabled(DebugContext.VERBOSE_LEVEL + 1)) {
            debug.dump(DebugContext.VERBOSE_LEVEL + 1, graph, "Before subphase %s", getName());
        } else if (debug.isDumpEnabled(DebugContext.ENABLED_LEVEL) && shouldDump(graph, context)) {
            debug.dump(DebugContext.ENABLED_LEVEL, graph, "Before %s %s", isTopLevel ? "phase" : "subphase", getName());
            return true;
        }
        return false;
    }

    protected boolean shouldDumpBeforeAtBasicLevel() {
        return false;
    }

    protected boolean shouldDumpAfterAtBasicLevel() {
        return false;
    }

    @SuppressWarnings("try")
    public final void apply(final StructuredGraph graph, final C context, final boolean dumpGraph) {
        if (ExcludePhaseFilter.exclude(graph.getOptions(), this, graph.asJavaMethod())) {
            return;
        }

        DebugContext debug = graph.getDebug();
        try (CompilerPhaseScope cps = getClass() != PhaseSuite.class ? debug.enterCompilerPhase(getName()) : null;
                        DebugCloseable a = timer.start(debug);
                        DebugContext.Scope s = debug.scope(getClass(), this);
                        DebugCloseable c = memUseTracker.start(debug);) {

            int sizeBefore = 0;
            Mark before = null;
            OptionValues options = graph.getOptions();
            boolean verifySizeContract = PhaseOptions.VerifyGraalPhasesSize.getValue(options) && checkContract();
            if (verifySizeContract) {
                sizeBefore = NodeCostUtil.computeGraphSize(graph);
                before = graph.getMark();
            }
            boolean isTopLevel = getEnclosingPhase(graph.getDebug()) == null;
            boolean dumpedBefore = false;
            if (dumpGraph && debug.areScopesEnabled()) {
                dumpedBefore = dumpBefore(graph, context, isTopLevel);
            }
            inputNodesCount.add(debug, graph.getNodeCount());
            this.run(graph, context);
            executionCount.increment(debug);
            if (verifySizeContract) {
                if (!before.isCurrent()) {
                    int sizeAfter = NodeCostUtil.computeGraphSize(graph);
                    NodeCostUtil.phaseFulfillsSizeContract(graph, sizeBefore, sizeAfter, this);
                }
            }

            if (dumpGraph && debug.areScopesEnabled()) {
                dumpAfter(graph, isTopLevel, dumpedBefore);
            }
            if (debug.isVerifyEnabled()) {
                debug.verify(graph, "%s", getName());
            }
            assert graph.verify();
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    private void dumpAfter(final StructuredGraph graph, boolean isTopLevel, boolean dumpedBefore) {
        boolean dumped = false;
        DebugContext debug = graph.getDebug();
        if (isTopLevel) {
            if (shouldDumpAfterAtBasicLevel()) {
                if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
                    debug.dump(DebugContext.BASIC_LEVEL, graph, "After phase %s", getName());
                    dumped = true;
                }
            } else {
                if (debug.isDumpEnabled(DebugContext.INFO_LEVEL)) {
                    debug.dump(DebugContext.INFO_LEVEL, graph, "After phase %s", getName());
                    dumped = true;
                }
            }
        } else {
            if (debug.isDumpEnabled(DebugContext.INFO_LEVEL + 1)) {
                debug.dump(DebugContext.INFO_LEVEL + 1, graph, "After subphase %s", getName());
                dumped = true;
            }
        }
        if (!dumped && debug.isDumpEnabled(DebugContext.ENABLED_LEVEL) && dumpedBefore) {
            debug.dump(DebugContext.ENABLED_LEVEL, graph, "After %s %s", isTopLevel ? "phase" : "subphase", getName());
        }
    }

    @SuppressWarnings("try")
    private boolean shouldDump(StructuredGraph graph, C context) {
        DebugContext debug = graph.getDebug();
        String phaseChange = DebugOptions.DumpOnPhaseChange.getValue(graph.getOptions());
        if (phaseChange != null && Pattern.matches(phaseChange, getClass().getSimpleName())) {
            StructuredGraph graphCopy = (StructuredGraph) graph.copy(graph.getDebug());
            GraphChangeListener listener = new GraphChangeListener(graphCopy);
            try (NodeEventScope s = graphCopy.trackNodeEvents(listener)) {
                try (DebugContext.Scope s2 = debug.sandbox("GraphChangeListener", null)) {
                    run(graphCopy, context);
                } catch (Throwable t) {
                    debug.handle(t);
                }
            }
            return listener.changed;
        }
        return false;
    }

    private static final class GraphChangeListener extends NodeEventListener {
        boolean changed;
        private StructuredGraph graph;
        private Mark mark;

        GraphChangeListener(StructuredGraph graphCopy) {
            this.graph = graphCopy;
            this.mark = graph.getMark();
        }

        @Override
        public void changed(NodeEvent e, Node node) {
            if (!graph.isNew(mark, node) && node.isAlive()) {
                if (e == NodeEvent.INPUT_CHANGED || e == NodeEvent.ZERO_USAGES) {
                    changed = true;
                }
            }
        }
    }

    protected CharSequence getName() {
        return new ClassTypeSequence(BasePhase.this.getClass());
    }

    protected abstract void run(StructuredGraph graph, C context);

    @Override
    public String contractorName() {
        return getName().toString();
    }

    @Override
    public float codeSizeIncrease() {
        return 1.25f;
    }

    private static final class ExcludePhaseFilter {

        /**
         * Contains the excluded phases and the corresponding methods to exclude.
         */
        private EconomicMap<Pattern, MethodFilter> filters;

        /**
         * Cache instances of this class to avoid parsing the same option string more than once.
         */
        private static ConcurrentHashMap<String, ExcludePhaseFilter> instances;

        static {
            instances = new ConcurrentHashMap<>();
        }

        /**
         * Determines whether the phase should be excluded from running on the given method based on
         * the given option values.
         */
        protected static boolean exclude(OptionValues options, BasePhase<?> phase, JavaMethod method) {
            String compilationExcludePhases = PhaseOptions.CompilationExcludePhases.getValue(options);
            if (compilationExcludePhases == null) {
                return false;
            } else {
                return getInstance(compilationExcludePhases).exclude(phase, method);
            }
        }

        /**
         * Gets an instance of this class for the given option values. This will typically be a
         * cached instance.
         */
        private static ExcludePhaseFilter getInstance(String compilationExcludePhases) {
            return instances.computeIfAbsent(compilationExcludePhases, excludePhases -> ExcludePhaseFilter.parse(excludePhases));
        }

        /**
         * Determines whether the given phase should be excluded from running on the given method.
         */
        protected boolean exclude(BasePhase<?> phase, JavaMethod method) {
            if (method == null) {
                return false;
            }
            String phaseName = phase.getClass().getSimpleName();
            for (Pattern excludedPhase : filters.getKeys()) {
                if (excludedPhase.matcher(phaseName).matches()) {
                    return filters.get(excludedPhase).matches(method);
                }
            }
            return false;
        }

        /**
         * Creates a phase filter based on a specification string. The string is a colon-separated
         * list of phase names or {@code phase_name=filter} pairs. Phase names match any phase of
         * which they are a substring. Filters follow {@link MethodFilter} syntax.
         */
        private static ExcludePhaseFilter parse(String compilationExcludePhases) {
            EconomicMap<Pattern, MethodFilter> filters = EconomicMap.create();
            String[] parts = compilationExcludePhases.trim().split(":");
            for (String part : parts) {
                String phaseName;
                MethodFilter methodFilter;
                if (part.contains("=")) {
                    String[] pair = part.split("=");
                    if (pair.length != 2) {
                        throw new IllegalArgumentException("expected phase_name=filter pair in: " + part);
                    }
                    phaseName = pair[0];
                    methodFilter = MethodFilter.parse(pair[1]);
                } else {
                    phaseName = part;
                    methodFilter = MethodFilter.matchAll();
                }
                Pattern phasePattern = Pattern.compile(".*" + MethodFilter.createGlobString(phaseName) + ".*");
                filters.put(phasePattern, methodFilter);
            }
            return new ExcludePhaseFilter(filters);
        }

        private ExcludePhaseFilter(EconomicMap<Pattern, MethodFilter> filters) {
            this.filters = filters;
        }
    }
}
