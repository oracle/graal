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

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.json.JSONFormatter;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Unifies counting, logging and dumping in optimization phases. If enabled, collects info about
 * optimizations performed in a single compilation and dumps them to the standard output, a JSON
 * file and/or IGV.
 *
 * This is the "slow" implementation of the interface that performs the actual logging. If none of
 * the logging features is enabled, the dummy implementation should be used to reduce runtime
 * overhead.
 */
public class OptimizationLogImpl implements OptimizationLog {

    /**
     * Represents an optimization phase, which can trigger its own subphases and/or individual
     * optimizations. It is a node in the tree of optimizations that holds its subphases and
     * individual optimizations in the order they were performed.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED, shortName = "Phase", nameTemplate = "{p#phaseName/s}")
    public static class OptimizationPhaseScopeImpl extends OptimizationTreeNode implements OptimizationLog.OptimizationPhaseScope {
        public static final NodeClass<OptimizationPhaseScopeImpl> TYPE = NodeClass.create(OptimizationPhaseScopeImpl.class);

        private final OptimizationLogImpl optimizationLog;

        private final CharSequence phaseName;

        @Successor private NodeSuccessorList<OptimizationTreeNode> children = null;

        protected OptimizationPhaseScopeImpl(OptimizationLogImpl optimizationLog, CharSequence phaseName) {
            super(TYPE);
            this.optimizationLog = optimizationLog;
            this.phaseName = phaseName;
        }

        /**
         * Gets the name of the phase described by this scope.
         *
         * @return the name of the phase described by this scope
         */
        @Override
        public CharSequence getPhaseName() {
            return phaseName;
        }

        /**
         * Creates a subphase of this phase, sets it as the current phase and returns its instance.
         *
         * @param subphaseName the name of the newly created phase
         * @return the newly created phase
         */
        private OptimizationPhaseScopeImpl enterSubphase(CharSequence subphaseName) {
            OptimizationPhaseScopeImpl subphase = new OptimizationPhaseScopeImpl(optimizationLog, subphaseName);
            addChild(subphase);
            optimizationLog.currentPhase = subphase;
            return subphase;
        }

        /**
         * Adds a node to the graph as a child of this phase.
         *
         * @param node the node to be added as a child
         */
        private void addChild(OptimizationLogImpl.OptimizationTreeNode node) {
            graph().add(node);
            if (children == null) {
                children = new NodeSuccessorList<>(this, 1);
                children.set(0, node);
            } else {
                children.add(node);
            }
        }

        /**
         * Notifies the phase that it has ended. Sets the current phase to the parent phase of this
         * phase.
         */
        @Override
        public void close() {
            optimizationLog.currentPhase = (OptimizationPhaseScopeImpl) predecessor();
        }

        @Override
        public EconomicMap<String, Object> asJsonMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put("phaseName", phaseName);
            List<EconomicMap<String, Object>> optimizations = null;
            if (children != null) {
                optimizations = new ArrayList<>();
                for (OptimizationLogImpl.OptimizationTreeNode entry : children) {
                    optimizations.add(entry.asJsonMap(methodNameFormatter));
                }
            }
            map.put("optimizations", optimizations);
            return map;
        }

        /**
         * Gets child nodes (successors) in the graph, i.e., optimizations and phases triggered
         * inside this phase.
         *
         * @return child nodes (successors) in the graph
         */
        @Override
        public NodeSuccessorList<OptimizationTreeNode> getChildren() {
            return children;
        }
    }

    /**
     * Represents one performed optimization stored in the optimization log. Additional properties
     * are stored and immediately evaluated. This is a leaf node in the optimization tree.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED, shortName = "Optimization", nameTemplate = "{p#event}")
    public static class OptimizationEntryImpl extends OptimizationTreeNode implements OptimizationEntry {
        public static final NodeClass<OptimizationEntryImpl> TYPE = NodeClass.create(OptimizationEntryImpl.class);

        public static final String OPTIMIZATION_NAME_PROPERTY = "optimizationName";

        public static final String EVENT_NAME_PROPERTY = "eventName";

        public static final String POSITION_PROPERTY = "position";

        /**
         * A map of additional named properties or {@code null} if no properties were set.
         */
        private EconomicMap<String, Object> properties = null;

        /**
         * A position of a significant node related to this optimization.
         */
        private NodeSourcePosition position;

        /**
         * The associated optimization log, which is extended with this node when it is
         * {@link #report reported}.
         */
        private final OptimizationLogImpl optimizationLog;

        /**
         * The name of this optimization. Corresponds to the name of the compiler phase or another
         * class which performed this optimization.
         */
        private String optimizationName;

        /**
         * The name of the event that occurred, which describes this optimization entry.
         */
        private String event;

        protected OptimizationEntryImpl(OptimizationLogImpl optimizationLog) {
            super(TYPE);
            this.optimizationLog = optimizationLog;
        }

        /**
         * Creates an ordered map that represents the position of a significant node related to an
         * optimization. It maps stable method names to byte code indices, starting with the method
         * containing the significant node and its bci. If the node does not belong the root method
         * in the compilation unit, the map also contains the method names of the method's callsites
         * mapped to the byte code indices of their invokes.
         *
         * @param methodNameFormatter a function that formats method names
         * @param position the position of a significant node related to an optimization
         * @return an ordered map that represents the position of a significant node
         */
        private static EconomicMap<String, Integer> createPositionProperty(Function<ResolvedJavaMethod, String> methodNameFormatter,
                        NodeSourcePosition position) {
            if (position == null) {
                return null;
            }
            EconomicMap<String, Integer> map = EconomicMap.create();
            NodeSourcePosition current = position;
            while (current != null) {
                ResolvedJavaMethod method = current.getMethod();
                map.put(methodNameFormatter.apply(method), current.getBCI());
                current = current.getCaller();
            }
            return map;
        }

        private static String createOptimizationName(Class<?> optimizationClass) {
            String className = optimizationClass.getSimpleName();
            String phaseSuffix = "Phase";
            if (className.endsWith(phaseSuffix)) {
                return className.substring(0, className.length() - phaseSuffix.length());
            }
            return className;
        }

        @Override
        public EconomicMap<String, Object> asJsonMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
            EconomicMap<String, Object> jsonMap = EconomicMap.create();
            jsonMap.put(OPTIMIZATION_NAME_PROPERTY, optimizationName);
            jsonMap.put(EVENT_NAME_PROPERTY, event);
            jsonMap.put(POSITION_PROPERTY, createPositionProperty(methodNameFormatter, position));
            if (properties != null) {
                jsonMap.putAll(properties);
            }
            return jsonMap;
        }

        @Override
        public <V> OptimizationEntry withLazyProperty(String key, Supplier<V> valueSupplier) {
            if (properties == null) {
                properties = EconomicMap.create(1);
            }
            properties.put(key, valueSupplier.get());
            return this;
        }

        @Override
        public OptimizationEntry withProperty(String key, Object value) {
            if (properties == null) {
                properties = EconomicMap.create(1);
            }
            properties.put(key, value);
            return this;
        }

        @Override
        public void report(int logLevel, Class<?> optimizationClass, String eventName, Node node) {
            assert logLevel >= OptimizationLog.MINIMUM_LOG_LEVEL;
            event = eventName;
            optimizationName = createOptimizationName(optimizationClass);
            position = node.getNodeSourcePosition();
            DebugContext debug = optimizationLog.graph.getDebug();
            if (debug.isCountEnabled() || debug.hasUnscopedCounters()) {
                DebugContext.counter(optimizationName + "_" + eventName).increment(debug);
            }
            if (debug.isLogEnabled(logLevel)) {
                debug.log(logLevel, "Performed %s %s for node %s at bci %s %s", optimizationName, eventName, node,
                                position == null ? "unknown" : position.getBCI(),
                                properties == null ? "" : JSONFormatter.formatJSON(properties));
            }
            if (debug.isDumpEnabled(logLevel)) {
                debug.dump(logLevel, optimizationLog.graph, "%s %s for %s", optimizationName, eventName, node);
            }
            if (optimizationLog.optimizationLogEnabled) {
                optimizationLog.currentPhase.addChild(this);
            }
        }

        /**
         * Gets the map representation of this optimization entry.
         */
        public EconomicMap<String, Object> getProperties() {
            return properties;
        }

        /**
         * Gets the name of this optimization. Corresponds to the name of the compiler phase or
         * another class which performed this optimization.
         *
         * @return the name of this optimization.
         */
        public String getOptimizationName() {
            return optimizationName;
        }

        /**
         * Gets the name of the event that occurred, which describes this optimization entry.
         *
         * @return the name of the event that occurred
         */
        public String getEventName() {
            return event;
        }
    }

    public static final class PartialEscapeLogImpl implements OptimizationLog.PartialEscapeLog {
        private final EconomicMap<VirtualObjectNode, Integer> virtualNodes = EconomicMap.create(Equivalence.IDENTITY);

        @Override
        public void allocationRemoved(VirtualObjectNode virtualObjectNode) {
            virtualNodes.put(virtualObjectNode, 0);
        }

        @Override
        public void objectMaterialized(VirtualObjectNode virtualObjectNode) {
            Integer count = virtualNodes.get(virtualObjectNode);
            if (count != null) {
                virtualNodes.put(virtualObjectNode, count + 1);
            }
        }
    }

    /**
     * The graph that is bound with this optimization log.
     */
    private final StructuredGraph graph;

    /**
     * {@code true} iff the options have been verified and warnings emitted if needed.
     */
    private static volatile boolean optionsVerified = false;

    /**
     * A unique number (compilation request ID) that identifies this compilation.
     */
    private final String compilationId;

    /**
     * {@code true} iff the structured optimization log is enabled according to
     * {@link OptimizationLog#isOptimizationLogEnabled(OptionValues)}.
     */
    private final boolean optimizationLogEnabled;

    /**
     * A data structure that holds the state of virtualized allocations during partial escape
     * analysis.
     */
    private PartialEscapeLogImpl partialEscapeLog = null;

    /**
     * The most recently opened phase that was not closed. Initially, this is the root phase. If
     * {@link #optimizationLogEnabled} is {@code false}, the field stays {@code null}.
     */
    private OptimizationPhaseScopeImpl currentPhase;

    /**
     * The graph that holds the optimization tree if it is enabled.
     */
    private final Graph optimizationTree;

    /**
     * Constructs an optimization log bound with a given graph. Optimization
     * {@link org.graalvm.compiler.nodes.OptimizationLog.OptimizationEntry entries} are stored iff
     * {@link DebugOptions#OptimizationLog} is enabled.
     *
     * @param graph the bound graph
     */
    public OptimizationLogImpl(StructuredGraph graph) {
        this.graph = graph;
        optimizationLogEnabled = OptimizationLog.isOptimizationLogEnabled(graph.getOptions());
        if (optimizationLogEnabled) {
            compilationId = parseCompilationId();
            currentPhase = new OptimizationPhaseScopeImpl(this, "RootPhase");
            optimizationTree = new Graph("OptimizationTree", graph.getOptions(), graph.getDebug(), false);
            optimizationTree.add(currentPhase);
            verifyOptions(graph.getOptions());
        } else {
            compilationId = null;
            currentPhase = null;
            optimizationTree = null;
        }
    }

    /**
     * Verifies that node source position tracking is enabled. If not, a warning is emitted once per
     * execution.
     *
     * @param optionValues the option values
     */
    private static void verifyOptions(OptionValues optionValues) {
        if (optionsVerified) {
            return;
        }
        boolean trackNodeSourcePosition = GraalOptions.TrackNodeSourcePosition.getValue(optionValues);
        if (!trackNodeSourcePosition) {
            synchronized (OptimizationLogImpl.class) {
                if (!optionsVerified) {
                    TTY.println("Warning: %s without %s cannot assign bci to performed optimizations",
                                    DebugOptions.OptimizationLog.getName(),
                                    GraalOptions.TrackNodeSourcePosition.getName());
                    optionsVerified = true;
                }
            }
        } else {
            optionsVerified = true;
        }
    }

    @Override
    public boolean isOptimizationLogEnabled() {
        return optimizationLogEnabled;
    }

    @Override
    public <V> OptimizationEntry withLazyProperty(String key, Supplier<V> valueSupplier) {
        return new OptimizationEntryImpl(this).withLazyProperty(key, valueSupplier);
    }

    @Override
    public OptimizationEntry withProperty(String key, Object value) {
        return new OptimizationEntryImpl(this).withProperty(key, value);
    }

    @Override
    public void report(int logLevel, Class<?> optimizationClass, String eventName, Node node) {
        new OptimizationEntryImpl(this).report(logLevel, optimizationClass, eventName, node);
    }

    /**
     * Notifies the log that an optimization phase is entered.
     *
     * @param name the name of the phase
     * @param nesting the level of nesting (ignored)
     * @return a scope that represents the open phase
     */
    @Override
    public OptimizationPhaseScope enterPhase(CharSequence name, int nesting) {
        if (currentPhase == null) {
            return null;
        }
        return currentPhase.enterSubphase(name);
    }

    @Override
    public void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, CharSequence message, int bci) {

    }

    @Override
    public DebugCloseable enterPartialEscapeAnalysis() {
        assert partialEscapeLog == null;
        if (!optimizationLogEnabled) {
            return DebugCloseable.VOID_CLOSEABLE;
        }
        partialEscapeLog = new PartialEscapeLogImpl();
        return () -> {
            assert partialEscapeLog != null;
            MapCursor<VirtualObjectNode, Integer> cursor = partialEscapeLog.virtualNodes.getEntries();
            while (cursor.advance()) {
                withProperty("materializations", cursor.getValue()).report(PartialEscapeLog.class, "AllocationVirtualization", cursor.getKey());
            }
            partialEscapeLog = null;
        };
    }

    @Override
    public DebugCloseable listen(Function<ResolvedJavaMethod, String> methodNameFormatter) {
        if (!optimizationLogEnabled) {
            return DebugCloseable.VOID_CLOSEABLE;
        }
        graph.getDebug().setCompilationListener(this);
        return () -> {
            graph.getDebug().setCompilationListener(null);
            try {
                printOptimizationTree(methodNameFormatter);
            } catch (IOException exception) {
                throw new GraalError("Failed to print the optimization tree: %s", exception.getMessage());
            }
        };
    }

    @Override
    public PartialEscapeLog getPartialEscapeLog() {
        assert partialEscapeLog != null;
        return partialEscapeLog;
    }

    @Override
    public Graph getOptimizationTree() {
        return optimizationTree;
    }

    @Override
    public OptimizationPhaseScope getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Depending on the {@link DebugOptions#OptimizationLog OptimizationLog} option, prints the
     * optimization tree to the standard output, a JSON file and/or dumps it. If the optimization
     * tree is printed to a file, the directory is either specified by the
     * {@link DebugOptions#OptimizationLogPath OptimizationLogPath} option or it is printed to
     * {@link DebugOptions#DumpPath DumpPath}/optimization_log (the former having precedence).
     * Directories are created if they do not exist.
     *
     * @param methodNameFormatter a function that formats method names
     * @throws IOException failed to create a directory or the file
     */
    private void printOptimizationTree(Function<ResolvedJavaMethod, String> methodNameFormatter) throws IOException {
        EconomicSet<DebugOptions.OptimizationLogTarget> targets = DebugOptions.OptimizationLog.getValue(graph.getOptions());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        if (targets.contains(DebugOptions.OptimizationLogTarget.Dump)) {
            graph.getDebug().dump(DebugContext.ENABLED_LEVEL, optimizationTree, "Optimization tree");
        }
        List<PrintStream> streams = new ArrayList<>();
        if (targets.contains(DebugOptions.OptimizationLogTarget.Stdout)) {
            streams.add(TTY.out);
        }
        if (targets.contains(DebugOptions.OptimizationLogTarget.Directory)) {
            String pathOptionValue = DebugOptions.OptimizationLogPath.getValue(graph.getOptions());
            if (pathOptionValue == null) {
                pathOptionValue = PathUtilities.getPath(DebugOptions.getDumpDirectory(graph.getOptions()), "optimization_log");
            }
            PathUtilities.createDirectories(pathOptionValue);
            String filePath = PathUtilities.getPath(pathOptionValue, compilationId + ".json");
            streams.add(new PrintStream(PathUtilities.openOutputStream(filePath)));
        }
        if (!streams.isEmpty()) {
            String json = JSONFormatter.formatJSON(asJsonMap(methodNameFormatter));
            for (PrintStream stream : streams) {
                stream.println(json);
                stream.close();
            }
        }
    }

    private EconomicMap<String, Object> asJsonMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
        EconomicMap<String, Object> map = EconomicMap.create();
        String compilationMethodName = methodNameFormatter.apply(graph.method());
        map.put("compilationMethodName", compilationMethodName);
        map.put("compilationId", compilationId);
        map.put("rootPhase", currentPhase.asJsonMap(methodNameFormatter));
        return map;
    }

    /**
     * Parses and returns a unique identifier for the compilation. The string before the first dash
     * is skipped, so that only the ID of the compilation request is returned.
     *
     * @return a unique compilation identifier
     */
    private String parseCompilationId() {
        String fullCompilationId = graph.compilationId().toString(CompilationIdentifier.Verbosity.ID);
        int dash = fullCompilationId.indexOf('-');
        if (dash == -1) {
            return fullCompilationId;
        }
        return fullCompilationId.substring(dash + 1);
    }
}
