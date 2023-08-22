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

import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TracePerformanceWarnings;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TraceStackTraceLimit;
import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.TreatPerformanceWarningsAsErrors;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.ProfileData.ProfileSource;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.cfg.HIRBlock;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.PerformanceWarningKind;

import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class handles reporting of performance warning.
 *
 * One instance is installed ({@link #install(TruffleCompilerRuntime,OptionValues)} for each
 * compilation before the {@link org.graalvm.compiler.truffle.compiler.phases.TruffleTier} and
 * closed after.
 */
public final class PerformanceInformationHandler implements Closeable {

    private static final ThreadLocal<PerformanceInformationHandler> instance = new ThreadLocal<>();
    private final TruffleCompilerRuntime runtime;
    private final OptionValues options;
    private final Set<PerformanceWarningKind> warningKinds = EnumSet.noneOf(PerformanceWarningKind.class);

    private PerformanceInformationHandler(TruffleCompilerRuntime runtime, OptionValues options) {
        this.options = options;
        this.runtime = runtime;
    }

    private void addWarning(PerformanceWarningKind warningKind) {
        warningKinds.add(warningKind);
    }

    private Set<PerformanceWarningKind> getWarnings() {
        return warningKinds;
    }

    @Override
    public void close() {
        assert instance.get() != null : "No PerformanceInformationHandler installed";
        instance.remove();
    }

    public static PerformanceInformationHandler install(TruffleCompilerRuntime runtime, OptionValues options) {
        assert instance.get() == null : "PerformanceInformationHandler already installed";
        PerformanceInformationHandler handler = new PerformanceInformationHandler(runtime, options);
        instance.set(handler);
        return handler;
    }

    public static boolean isWarningEnabled(PerformanceWarningKind warningKind) {
        PerformanceInformationHandler handler = instance.get();
        return TracePerformanceWarnings.getValue(handler.options).kinds().contains(warningKind) ||
                        TreatPerformanceWarningsAsErrors.getValue(handler.options).kinds().contains(warningKind);
    }

    public static void logPerformanceWarning(PerformanceWarningKind warningKind, TruffleCompilable compilable, List<? extends Node> locations, String details,
                    Map<String, Object> properties) {
        PerformanceInformationHandler handler = instance.get();
        handler.addWarning(warningKind);
        handler.logPerformanceWarningImpl(compilable, "perf warn", details, properties, handler.getPerformanceStackTrace(locations));
    }

    private void logPerformanceInfo(TruffleCompilable compilable, List<? extends Node> locations, String details, Map<String, Object> properties) {
        logPerformanceWarningImpl(compilable, "perf info", details, properties, instance.get().getPerformanceStackTrace(locations));
    }

    private void logPerformanceWarningImpl(TruffleCompilable compilable, String event, String details, Map<String, Object> properties, String message) {
        runtime.logEvent(compilable, 0, event, String.format("%-60s|%s", compilable.getName(), details), properties, message);
    }

    private String getPerformanceStackTrace(List<? extends Node> locations) {
        if (locations == null || locations.isEmpty()) {
            return null;
        }
        int limit = TraceStackTraceLimit.getValue(options);
        if (limit <= 0) {
            return null;
        }

        EconomicMap<String, List<Node>> groupedByStackTrace = EconomicMap.create(Equivalence.DEFAULT);
        for (Node location : locations) {
            StackTraceElement[] stackTrace = GraphUtil.approxSourceStackTraceElement(location);
            StringBuilder sb = new StringBuilder();
            String indent = "    ";
            for (int i = 0; i < stackTrace.length && i < limit; i++) {
                if (i != 0) {
                    sb.append('\n');
                }
                sb.append(indent).append("at ").append(stackTrace[i]);
            }
            if (stackTrace.length > limit) {
                sb.append('\n').append(indent).append("...");
            }
            String stackTraceAsString = sb.toString();
            if (!groupedByStackTrace.containsKey(stackTraceAsString)) {
                groupedByStackTrace.put(stackTraceAsString, new ArrayList<>());
            }
            groupedByStackTrace.get(stackTraceAsString).add(location);
        }
        StringBuilder builder = new StringBuilder();
        MapCursor<String, List<Node>> entry = groupedByStackTrace.getEntries();
        while (entry.advance()) {
            String stackTrace = entry.getKey();
            List<Node> locationGroup = entry.getValue();
            if (builder.length() > 0) {
                builder.append(String.format("%n"));
            }
            if (stackTrace.isEmpty()) {
                builder.append(String.format("  No stack trace available for %s.", locationGroup));
            } else {
                builder.append(String.format("  Approximated stack trace for %s (append --vm.XX:+UnlockDiagnosticVMOptions --vm.XX:+DebugNonSafepoints for more precise approximation):\n",
                                locationGroup));
                builder.append(stackTrace);
            }
        }
        return builder.toString();
    }

    @SuppressWarnings("try")
    public void reportPerformanceWarnings(TruffleTierContext context) {
        StructuredGraph graph = context.graph;
        DebugContext debug = context.debug;
        ArrayList<ValueNode> warnings = new ArrayList<>();
        if (isWarningEnabled(PerformanceWarningKind.VIRTUAL_RUNTIME_CALL)) {
            for (MethodCallTargetNode call : context.graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod targetMethod = call.targetMethod();
                if (targetMethod.isNative()) {
                    continue; // native methods cannot be inlined
                }

                if (targetMethod.canBeInlined() && context.getPartialEvaluator().getMethodInfo(targetMethod).inlineForPartialEvaluation().allowsInlining()) {
                    logPerformanceWarning(PerformanceWarningKind.VIRTUAL_RUNTIME_CALL, context.compilable, Arrays.asList(call),
                                    String.format("Partial evaluation could not inline the virtual runtime call %s to %s (%s).", call.invokeKind(), targetMethod, call),
                                    null);
                    warnings.add(call);
                }
            }
        }
        if (isWarningEnabled(PerformanceWarningKind.VIRTUAL_INSTANCEOF)) {
            EconomicMap<ResolvedJavaType, ArrayList<ValueNode>> groupedByType = EconomicMap.create(Equivalence.DEFAULT);
            for (InstanceOfNode instanceOf : graph.getNodes().filter(InstanceOfNode.class)) {
                if (!instanceOf.type().isExact()) {
                    ResolvedJavaType type = instanceOf.type().getType();
                    if (isSecondaryType(type)) {
                        warnings.add(instanceOf);
                        if (!groupedByType.containsKey(type)) {
                            groupedByType.put(type, new ArrayList<>());
                        }
                        groupedByType.get(type).add(instanceOf);
                    }
                }
            }
            MapCursor<ResolvedJavaType, ArrayList<ValueNode>> entry = groupedByType.getEntries();
            while (entry.advance()) {
                ResolvedJavaType type = entry.getKey();
                String reason = "Partial evaluation could not resolve virtual instanceof to an exact type due to: " +
                                String.format(type.isInterface() ? "interface type check: %s" : "too deep in class hierarchy: %s", type);
                logPerformanceInfo(context.compilable, entry.getValue(), reason, Collections.singletonMap("Nodes", entry.getValue()));
            }
        }
        if (isWarningEnabled(PerformanceWarningKind.MISSING_LOOP_FREQUENCY_INFO)) {
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, false);
            for (Loop<HIRBlock> loop : cfg.getLoops()) {
                // check if all loop exit contain trusted profiles
                List<HIRBlock> loopBlocks = loop.getBlocks();
                for (HIRBlock exit : loop.getLoopExits()) {
                    HIRBlock exitDom = exit.getDominator();
                    while (!(exitDom.getEndNode() instanceof ControlSplitNode) && !loopBlocks.contains(exitDom)) {
                        // potential computation before loop exit
                        exitDom = exitDom.getDominator();
                    }
                    if (loopBlocks.contains(exitDom) && exitDom.getEndNode() instanceof ControlSplitNode) {
                        ControlSplitNode split = (ControlSplitNode) exitDom.getEndNode();
                        if (!ProfileSource.isTrusted(split.getProfileData().getProfileSource())) {
                            logPerformanceWarning(PerformanceWarningKind.MISSING_LOOP_FREQUENCY_INFO, context.compilable, Arrays.asList(loop.getHeader().getBeginNode()),
                                            String.format("Missing loop profile for %s at loop %s.", split, loop.getHeader().getBeginNode()), null);
                        }
                    }
                }
            }
        }

        if (debug.areScopesEnabled() && !warnings.isEmpty()) {
            try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                debug.dump(DebugContext.BASIC_LEVEL, graph, "performance warnings %s", warnings);
            } catch (Throwable t) {
                debug.handle(t);
            }
        }

        if (!Collections.disjoint(getWarnings(), TreatPerformanceWarningsAsErrors.getValue(options).kinds())) {
            throw new AssertionError("Performance warning detected and is treated as a compilation error.");
        }
    }

    /**
     * On HotSpot, a type check against a class that is at a depth <= 8 in the class hierarchy
     * (including Object) is just one extra memory load.
     */
    private static boolean isPrimarySupertype(ResolvedJavaType type) {
        if (type.isInterface()) {
            return false;
        }
        ResolvedJavaType supr = type;
        int depth = 0;
        while (supr != null) {
            depth++;
            supr = supr.getSuperclass();
        }
        return depth <= 8;
    }

    private static boolean isSecondaryType(ResolvedJavaType type) {
        return !isPrimarySupertype(type);
    }

}
