/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionValues;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.getPolyglotOptionValue;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TracePerformanceWarnings;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceStackTraceLimit;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TreatPerformanceWarningsAsErrors;

public final class PerformanceInformationHandler implements Closeable {

    private static final ThreadLocal<PerformanceInformationHandler> instance = new ThreadLocal<>();
    private final OptionValues options;
    private final Set<PolyglotCompilerOptions.PerformanceWarningKind> warningKinds = EnumSet.noneOf(PolyglotCompilerOptions.PerformanceWarningKind.class);

    private PerformanceInformationHandler(OptionValues options) {
        this.options = options;
    }

    private void addWarning(PolyglotCompilerOptions.PerformanceWarningKind warningKind) {
        warningKinds.add(warningKind);
    }

    private Set<PolyglotCompilerOptions.PerformanceWarningKind> getWarnings() {
        return warningKinds;
    }

    @Override
    public void close() {
        assert instance.get() != null : "No PerformanceInformationHandler installed";
        instance.remove();
    }

    static PerformanceInformationHandler install(OptionValues options) {
        assert instance.get() == null : "PerformanceInformationHandler already installed";
        PerformanceInformationHandler handler = new PerformanceInformationHandler(options);
        instance.set(handler);
        return handler;
    }

    public static boolean isWarningEnabled(PolyglotCompilerOptions.PerformanceWarningKind warningKind) {
        PerformanceInformationHandler handler = instance.get();
        return getPolyglotOptionValue(handler.options, TracePerformanceWarnings).contains(warningKind) ||
                        getPolyglotOptionValue(handler.options, PerformanceWarningsAreFatal).contains(warningKind) ||
                        getPolyglotOptionValue(handler.options, TreatPerformanceWarningsAsErrors).contains(warningKind);
    }

    public static void logPerformanceWarning(PolyglotCompilerOptions.PerformanceWarningKind warningKind, CompilableTruffleAST compilable, List<? extends Node> locations, String details,
                    Map<String, Object> properties) {
        PerformanceInformationHandler handler = instance.get();
        handler.addWarning(warningKind);
        logPerformanceWarningImpl(compilable, "perf warn", details, properties, handler.getPerformanceStackTrace(locations));
    }

    private static void logInliningWarning(CompilableTruffleAST compilable, String details, Map<String, Object> properties) {
        logPerformanceWarningImpl(compilable, "inlining warn", details, properties, null);
    }

    private static void logPerformanceInfo(CompilableTruffleAST compilable, List<? extends Node> locations, String details, Map<String, Object> properties) {
        logPerformanceWarningImpl(compilable, "perf info", details, properties, instance.get().getPerformanceStackTrace(locations));
    }

    private static void logPerformanceWarningImpl(CompilableTruffleAST compilable, String event, String details, Map<String, Object> properties, String message) {
        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        runtime.logEvent(compilable, 0, event, String.format("%-60s|%s", compilable.getName(), details), properties, message);
    }

    private String getPerformanceStackTrace(List<? extends Node> locations) {
        if (locations == null || locations.isEmpty()) {
            return null;
        }
        int limit = getPolyglotOptionValue(options, TraceStackTraceLimit); // TODO
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
                builder.append(String.format("  Approximated stack trace for %s:", locationGroup));
                builder.append(stackTrace);
            }
        }
        return builder.toString();
    }

    @SuppressWarnings("try")
    void reportPerformanceWarnings(CompilableTruffleAST target, StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        ArrayList<ValueNode> warnings = new ArrayList<>();
        if (isWarningEnabled(PolyglotCompilerOptions.PerformanceWarningKind.VIRTUAL_RUNTIME_CALL)) {
            for (MethodCallTargetNode call : graph.getNodes(MethodCallTargetNode.TYPE)) {
                if (call.targetMethod().isNative()) {
                    continue; // native methods cannot be inlined
                }
                TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
                if (runtime.getInlineKind(call.targetMethod(), true).allowsInlining()) {
                    logPerformanceWarning(PolyglotCompilerOptions.PerformanceWarningKind.VIRTUAL_RUNTIME_CALL, target, Arrays.asList(call),
                                    String.format("Partial evaluation could not inline the virtual runtime call %s to %s (%s).",
                                                    call.invokeKind(),
                                                    call.targetMethod(),
                                                    call),
                                    null);
                    warnings.add(call);
                }
            }
        }
        if (isWarningEnabled(PolyglotCompilerOptions.PerformanceWarningKind.VIRTUAL_INSTANCEOF)) {
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
                logPerformanceInfo(target, entry.getValue(), reason, Collections.singletonMap("Nodes", entry.getValue()));
            }
        }

        if (debug.areScopesEnabled() && !warnings.isEmpty()) {
            try (DebugContext.Scope s = debug.scope("TrufflePerformanceWarnings", graph)) {
                debug.dump(DebugContext.BASIC_LEVEL, graph, "performance warnings %s", warnings);
            } catch (Throwable t) {
                debug.handle(t);
            }
        }

        if (!Collections.disjoint(getWarnings(), getPolyglotOptionValue(options, PerformanceWarningsAreFatal))) { // TODO
            throw new AssertionError("Performance warning detected and is fatal.");
        }
        if (!Collections.disjoint(getWarnings(), getPolyglotOptionValue(options, TreatPerformanceWarningsAsErrors))) {
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

    static void reportDecisionIsNull(CompilableTruffleAST compilable, JavaConstant callNode) {
        if (TruffleCompilerOptions.getPolyglotOptionValue(instance.get().options, TraceInlining)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("callNode", callNode.toValueString());
            logInliningWarning(compilable, "A direct call within the Truffle AST is not reachable anymore. Call node could not be inlined.", properties);
        }
    }

    static void reportCallTargetChanged(CompilableTruffleAST compilable, JavaConstant callNode, TruffleInliningPlan.Decision decision) {
        if (TruffleCompilerOptions.getPolyglotOptionValue(instance.get().options, TraceInlining)) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("originalTarget", decision.getTargetName());
            properties.put("callNode", callNode.toValueString());
            logInliningWarning(compilable, "CallTarget changed during compilation. Call node could not be inlined.", properties);
        }
    }
}
