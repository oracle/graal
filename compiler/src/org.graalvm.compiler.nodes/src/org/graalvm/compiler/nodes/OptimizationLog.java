/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.json.JSONFormatter;
import org.graalvm.compiler.serviceprovider.GraalServices;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Unifies counting, logging and dumping in optimization phases. If enabled, collects info about optimizations performed
 * in a single compilation and dumps them to a JSON file.
 */
public class OptimizationLog {
    /**
     * A special bci value meaning that no byte code index was found.
     */
    public static final int NO_BCI = -1;

    /**
     * Describes the kind and location of one performed optimization in an optimization log.
     */
    public interface OptimizationEntry {
        /**
         * Sets an additional property of the performed optimization to be used in the optimization log.
         * @param key the name of the property
         * @param valueSupplier the supplier of the value
         * @return this
         */
        OptimizationEntry setProperty(String key, Supplier<Object> valueSupplier);

        /**
         * Sets an additional property of the performed optimization to be used in the optimization log.
         * @param key the name of the property
         * @param value the value of the property
         * @return this
         */
        OptimizationEntry setProperty(String key, Object value);
    }

    /**
     * Represents one performed optimization stored in the optimization log. Additional properties are stored and
     * immediately evaluated.
     */
    private static final class OptimizationEntryImpl implements OptimizationEntry {
        private final EconomicMap<String, Object> map;

        private OptimizationEntryImpl(String optimizationName, String eventName, int bci) {
            map = EconomicMap.create();
            map.put("optimizationName", optimizationName);
            map.put("eventName", eventName);
            map.put("bci", bci);
        }

        private EconomicMap<String, Object> asJsonMap() {
            return map;
        }

        @Override
        public OptimizationEntry setProperty(String key, Supplier<Object> valueSupplier) {
            return setProperty(key, valueSupplier.get());
        }

        @Override
        public OptimizationEntry setProperty(String key, Object value) {
            map.put(key, value);
            return this;
        }
    }

    /**
     * A dummy optimization entry that does not store nor evaluate its properties. Used in case the optimization log is
     * disabled. The rationale is that it should not do any work if the log is disabled.
     */
    private static final class OptimizationEntryEmpty implements OptimizationEntry {
        private OptimizationEntryEmpty() { }

        @Override
        public OptimizationEntry setProperty(String key, Supplier<Object> valueSupplier) {
            return this;
        }

        @Override
        public OptimizationEntry setProperty(String key, Object value) {
            return this;
        }
    }

    private static final OptimizationEntryEmpty OPTIMIZATION_ENTRY_EMPTY = new OptimizationEntryEmpty();
    private final StructuredGraph graph;
    private static final AtomicBoolean nodeSourcePositionWarningEmitted = new AtomicBoolean();
    private final ArrayList<OptimizationEntryImpl> optimizationEntries;
    private final String compilationId;
    private final boolean optimizationLogEnabled;

    /**
     * Constructs an optimization log bound with a given graph. Optimization {@link OptimizationEntry entries} are stored
     * iff {@link GraalOptions#OptimizationLog} is enabled.
     * @param graph the bound graph
     */
    public OptimizationLog(StructuredGraph graph) {
        this.graph = graph;
        optimizationLogEnabled = GraalOptions.OptimizationLog.getValue(graph.getOptions());
        if (optimizationLogEnabled) {
            if (!GraalOptions.TrackNodeSourcePosition.getValue(graph.getOptions()) &&
                !nodeSourcePositionWarningEmitted.getAndSet(true)) {
                TTY.println(
                        "Warning: Optimization log without node source position tracking (-Dgraal.%s) yields inferior results",
                        GraalOptions.TrackNodeSourcePosition.getName()
                );
            }
            optimizationEntries = new ArrayList<>();
            compilationId = parseCompilationId();
        } else {
            optimizationEntries = null;
            compilationId = null;
        }
    }

    /**
     * Increments a {@link org.graalvm.compiler.debug.CounterKey counter}, {@link DebugContext#log(String) logs},
     * {@link DebugContext#dump(int, Object, String) dumps} and appends to the optimization log if each respective
     * feature is enabled.
     * @param optimizationClass the class that performed the optimization
     * @param eventName the name of the event that occurred
     * @param bci the bci of the most relevant node (use {@link #NO_BCI} if no bci is appropriate)
     * @return an optimization entry in the optimization log that can take more properties
     */
    public OptimizationEntry report(Class<?> optimizationClass, String eventName, int bci) {
        boolean isCountEnabled = graph.getDebug().isCountEnabled();
        boolean isLogEnabled = graph.getDebug().isLogEnabledForMethod();
        boolean isDumpEnabled = graph.getDebug().isDumpEnabled(DebugContext.DETAILED_LEVEL);

        if (!isCountEnabled && !isLogEnabled && !isDumpEnabled && !optimizationLogEnabled) {
            return OPTIMIZATION_ENTRY_EMPTY;
        }

        String optimizationName = getOptimizationName(optimizationClass);
        if (isCountEnabled) {
            DebugContext.counter(optimizationName + "_" + eventName).increment(graph.getDebug());
        }
        if (isLogEnabled) {
            graph.getDebug().log("Performed %s %s at bci %i", optimizationName, eventName, bci);
        }
        if (isDumpEnabled) {
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "After %s %s", optimizationName, eventName);
        }
        if (optimizationLogEnabled) {
            OptimizationEntryImpl optimizationEntry = new OptimizationEntryImpl(optimizationName, eventName, bci);
            optimizationEntries.add(optimizationEntry);
            return optimizationEntry;
        }
        return OPTIMIZATION_ENTRY_EMPTY;
    }

    /**
     * Increments a {@link org.graalvm.compiler.debug.CounterKey counter}, {@link DebugContext#log(String) logs},
     * {@link DebugContext#dump(int, Object, String) dumps} and appends to the optimization log if each respective
     * feature is enabled.
     * @param optimizationClass the class that performed the optimization
     * @param eventName the name of the event that occurred
     * @param node the most relevant node
     * @return an optimization entry in the optimization log that can take more properties
     */
    public OptimizationEntry report(Class<?> optimizationClass, String eventName, Node node) {
        return report(optimizationClass, eventName, OptimizationLogUtil.findBCI(node));
    }

    /**
     * Returns the optimization name based on the name of the class that performed an optimization.
     * @param optimizationClass the class that performed an optimization
     * @return the name of the optimization
     */
    private static String getOptimizationName(Class<?> optimizationClass) {
        String className = optimizationClass.getSimpleName();
        String phaseSuffix = "Phase";
        if (className.endsWith(phaseSuffix)) {
            return className.substring(0, className.length() - phaseSuffix.length());
        }
        return className;
    }

    /**
     * If the optimization log is enabled, prints the optimization log of this compilation to
     * {@code optimization_log/compilation-id.json} in the
     * {@link DebugOptions#getDumpDirectoryName dump directory}. Directories are created if they do not exist.
     * @throws IOException failed to create a directory or the file
     */
    public void printToFileIfEnabled() throws IOException {
        if (!optimizationLogEnabled) {
            return;
        }
        String filename = compilationId + ".json";
        Path path = Path.of(
                DebugOptions.getDumpDirectoryName(graph.getOptions()),
                "optimization_log",
                filename
        );
        Files.createDirectories(path.getParent());
        String json = JSONFormatter.formatJSON(asJsonMap());
        PrintStream stream = new PrintStream(Files.newOutputStream(path));
        stream.print(json);
    }

    private EconomicMap<String, Object> asJsonMap() {
        EconomicMap<String, Object> map = EconomicMap.create();
        map.put("executionId", GraalServices.getExecutionID());
        String compilationMethodName = graph.compilationId().toString(CompilationIdentifier.Verbosity.NAME);
        map.put("compilationMethodName", compilationMethodName);
        map.put("compilationId", compilationId);
        map.put("optimizations", optimizationEntries.stream().map(OptimizationEntryImpl::asJsonMap).collect(Collectors.toList()));
        return map;
    }

    private String parseCompilationId() {
        String compilationId = graph.compilationId().toString(CompilationIdentifier.Verbosity.ID);
        int dash = compilationId.indexOf('-');
        if (dash == -1) {
            return compilationId;
        }
        return compilationId.substring(dash + 1);
    }
}
