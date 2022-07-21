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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.CompilationListener;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeSuccessorList;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.util.json.JSONFormatter;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Unifies counting, logging and dumping in optimization phases. If enabled, collects info about
 * optimizations performed in a single compilation and dumps them to a JSON file.
 */
public class OptimizationLog implements CompilationListener {
    /**
     * A special bci value meaning that no byte code index was found.
     */
    public static final int NO_BCI = -1;

    /**
     * Describes the kind and location of one performed optimization in an optimization log.
     */
    public interface OptimizationEntry {
        /**
         * Sets an additional property of the performed optimization to be used in the optimization
         * log.
         *
         * @param key the name of the property
         * @param valueSupplier the supplier of the value
         * @return this
         * @param <V> the value type of the property
         */
        <V> OptimizationEntry setLazyProperty(String key, Supplier<V> valueSupplier);

        /**
         * Sets an additional property of the performed optimization to be used in the optimization
         * log.
         *
         * @param key the name of the property
         * @param value the value of the property
         * @return this
         */
        OptimizationEntry setProperty(String key, Object value);
    }

    /**
     * Represents one performed optimization stored in the optimization log. Additional properties
     * are stored and immediately evaluated. This is a leaf node in the optimization tree.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED, shortName = "Optimization", nameTemplate = "{p#eventName}")
    public static class OptimizationEntryImpl extends OptimizationTreeNode implements OptimizationEntry {
        public static final NodeClass<OptimizationEntryImpl> TYPE = NodeClass.create(OptimizationEntryImpl.class);
        private final EconomicMap<String, Object> map;
        protected final String eventName;

        protected OptimizationEntryImpl(String optimizationName, String eventName, int bci) {
            super(TYPE);
            this.eventName = eventName;
            map = EconomicMap.create();
            map.put("optimizationName", optimizationName);
            map.put("eventName", eventName);
            map.put("bci", bci);
        }

        @Override
        public EconomicMap<String, Object> asJsonMap() {
            return map;
        }

        @Override
        public <V> OptimizationEntry setLazyProperty(String key, Supplier<V> valueSupplier) {
            map.put(key, valueSupplier.get());
            return this;
        }

        @Override
        public OptimizationEntry setProperty(String key, Object value) {
            map.put(key, value);
            return this;
        }

        /**
         * Gets the name of the event that occurred, which describes this optimization entry.
         *
         * @return the name of the event that occurred
         */
        public String getEventName() {
            return eventName;
        }

        /**
         * Gets the map representation of this optimization entry.
         *
         * @return the map representation of this optimization entry
         */
        public EconomicMap<String, Object> getMap() {
            return map;
        }
    }

    /**
     * A dummy optimization entry that does not store nor evaluate its properties. Used in case the
     * optimization log is disabled. The rationale is that it should not do any work if the log is
     * disabled.
     */
    private static final class OptimizationEntryEmpty implements OptimizationEntry {
        private OptimizationEntryEmpty() {
        }

        @Override
        public <V> OptimizationEntry setLazyProperty(String key, Supplier<V> valueSupplier) {
            return this;
        }

        @Override
        public OptimizationEntry setProperty(String key, Object value) {
            return this;
        }
    }

    /**
     * Represents a node in the tree of optimizations. The tree of optimizations consists of
     * optimization phases and individual optimizations.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED)
    public abstract static class OptimizationTreeNode extends Node {
        public static final NodeClass<OptimizationTreeNode> TYPE = NodeClass.create(OptimizationTreeNode.class);

        protected OptimizationTreeNode(NodeClass<? extends OptimizationTreeNode> c) {
            super(c);
        }

        /**
         * Converts the optimization subtree to an object that can be formatted as JSON.
         *
         * @return a representation of the optimization subtree that can be formatted as JSON
         */
        abstract EconomicMap<String, Object> asJsonMap();
    }

    /**
     * Represents an optimization phase, which can trigger its own subphases and/or individual
     * optimizations. It is a node in the tree of optimizations that holds its subphases and
     * individual optimizations in the order they were performed.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED, shortName = "Phase", nameTemplate = "{p#phaseName/s}")
    public static class OptimizationPhaseScope extends OptimizationTreeNode implements DebugContext.CompilerPhaseScope {
        public static final NodeClass<OptimizationPhaseScope> TYPE = NodeClass.create(OptimizationPhaseScope.class);
        private final OptimizationLog optimizationLog;

        private final CharSequence phaseName;
        @Successor private NodeSuccessorList<OptimizationTreeNode> children = null;

        protected OptimizationPhaseScope(OptimizationLog optimizationLog, CharSequence phaseName) {
            super(TYPE);
            this.optimizationLog = optimizationLog;
            this.phaseName = phaseName;
        }

        /**
         * Gets the name of the phase described by this scope.
         *
         * @return the name of the phase described by this scope
         */
        public CharSequence getPhaseName() {
            return phaseName;
        }

        /**
         * Creates a subphase of this phase, sets it as the current phase and returns its instance.
         *
         * @param subphaseName the name of the newly created phase
         * @return the newly created phase
         */
        private OptimizationPhaseScope enterSubphase(CharSequence subphaseName) {
            OptimizationPhaseScope subphase = new OptimizationPhaseScope(optimizationLog, subphaseName);
            addChild(subphase);
            optimizationLog.currentPhase = subphase;
            return subphase;
        }

        /**
         * Adds a node to the graph as a child of this phase.
         *
         * @param node the node to be added as a child
         */
        private void addChild(OptimizationTreeNode node) {
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
            optimizationLog.currentPhase = (OptimizationPhaseScope) predecessor();
        }

        @Override
        public EconomicMap<String, Object> asJsonMap() {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put("phaseName", phaseName);
            List<EconomicMap<String, Object>> optimizations = null;
            if (children != null) {
                optimizations = children.stream().map(OptimizationTreeNode::asJsonMap).collect(Collectors.toList());
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
        public NodeSuccessorList<OptimizationTreeNode> getChildren() {
            return children;
        }
    }

    /**
     * Keeps track of virtualized allocations and materializations during partial escape analysis.
     */
    public static class PartialEscapeLog {
        private final EconomicMap<VirtualObjectNode, Integer> virtualNodes = EconomicMap.create(Equivalence.IDENTITY);

        /**
         * Notifies the log that an allocation was virtualized.
         *
         * @param virtualObjectNode the virtualized node
         */
        public void allocationRemoved(VirtualObjectNode virtualObjectNode) {
            virtualNodes.put(virtualObjectNode, 0);
        }

        /**
         * Notifies the log that an object was materialized.
         *
         * @param virtualObjectNode the object that was materialized
         */
        public void objectMaterialized(VirtualObjectNode virtualObjectNode) {
            Integer count = virtualNodes.get(virtualObjectNode);
            if (count != null) {
                virtualNodes.put(virtualObjectNode, count + 1);
            }
        }
    }

    private static final OptimizationEntryEmpty OPTIMIZATION_ENTRY_EMPTY = new OptimizationEntryEmpty();
    private final StructuredGraph graph;
    private static final AtomicBoolean nodeSourcePositionWarningEmitted = new AtomicBoolean();
    private final String compilationId;
    private final boolean optimizationLogEnabled;
    private PartialEscapeLog partialEscapeLog = null;

    /**
     * The most recently opened phase that was not closed. Initially, this is the root phase. If
     * {@link #optimizationLogEnabled} is {@code false}, the field stays null.
     */
    private OptimizationPhaseScope currentPhase;
    private final Graph optimizationTree;

    /**
     * Constructs an optimization log bound with a given graph. Optimization
     * {@link OptimizationEntry entries} are stored iff {@link GraalOptions#OptimizationLog} is
     * enabled.
     *
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
                                GraalOptions.TrackNodeSourcePosition.getName());
            }
            compilationId = parseCompilationId();
            currentPhase = new OptimizationPhaseScope(this, "RootPhase");
            optimizationTree = new Graph("OptimizationTree", graph.getOptions(), graph.getDebug(), false);
            optimizationTree.add(currentPhase);
        } else {
            compilationId = null;
            currentPhase = null;
            optimizationTree = null;
        }
    }

    /**
     * Returns {@code true} iff {@link GraalOptions#OptimizationLog optimization log} is enabled.
     * This option concerns only the detailed JSON optimization log;
     * {@link DebugContext#counter(CharSequence) counters},
     * {@link DebugContext#dump(int, Object, String) dumping} and the textual
     * {@link DebugContext#log(String) log} are controlled by their respective options.
     *
     * @return whether {@link GraalOptions#OptimizationLog optimization log} is enabled
     */
    public boolean isOptimizationLogEnabled() {
        return optimizationLogEnabled;
    }

    @Override
    public DebugContext.CompilerPhaseScope enterPhase(CharSequence name, int nesting) {
        if (currentPhase == null) {
            return null;
        }
        return currentPhase.enterSubphase(name);
    }

    @Override
    public void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, CharSequence message, int bci) {

    }

    /**
     * Increments a {@link org.graalvm.compiler.debug.CounterKey counter},
     * {@link DebugContext#log(String) logs}, {@link DebugContext#dump(int, Object, String) dumps}
     * and appends to the optimization log if each respective feature is enabled.
     *
     * @param optimizationClass the class that performed the optimization
     * @param eventName the name of the event that occurred
     * @param node the most relevant node
     * @return an optimization entry in the optimization log that can take more properties
     */
    public OptimizationEntry report(Class<?> optimizationClass, String eventName, Node node) {
        boolean isCountEnabled = graph.getDebug().isCountEnabled();
        boolean isLogEnabled = graph.getDebug().isLogEnabledForMethod();
        boolean isDumpEnabled = graph.getDebug().isDumpEnabled(DebugContext.DETAILED_LEVEL);

        if (!isCountEnabled && !isLogEnabled && !isDumpEnabled && !optimizationLogEnabled) {
            return OPTIMIZATION_ENTRY_EMPTY;
        }

        int bci = findBCI(node);
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
            currentPhase.addChild(optimizationEntry);
            return optimizationEntry;
        }
        return OPTIMIZATION_ENTRY_EMPTY;
    }

    /**
     * Returns the bci of a node. First, it tries to get from the {@link FrameState} after the
     * execution of this node, then it tries to use the node's
     * {@link org.graalvm.compiler.graph.NodeSourcePosition}. If everything fails, it returns
     * {@code OptimizationLog#NO_BCI}.
     *
     * @param node the node whose bci we want to find
     * @return the bci of the node ({@code OptimizationLog#NO_BCI} if no fitting bci found)
     */
    private static int findBCI(Node node) {
        if (node instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) node;
            if (stateSplit.stateAfter() != null) {
                return stateSplit.stateAfter().bci;
            }
        }
        if (node.getNodeSourcePosition() != null) {
            return node.getNodeSourcePosition().getBCI();
        }
        return OptimizationLog.NO_BCI;
    }

    /**
     * Returns the optimization name based on the name of the class that performed an optimization.
     *
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
     * Notifies the log that virtual escape analysis will be entered. Prepares an object that keeps
     * track of virtualized allocations.
     */
    public void enterPartialEscapeAnalysis() {
        assert partialEscapeLog == null;
        if (optimizationLogEnabled) {
            partialEscapeLog = new PartialEscapeLog();
        }
    }

    /**
     * Notifies the log that virtual escape analysis has ended. Reports all virtualized allocations.
     */
    public void exitPartialEscapeAnalysis() {
        if (optimizationLogEnabled) {
            assert partialEscapeLog != null;
            MapCursor<VirtualObjectNode, Integer> cursor = partialEscapeLog.virtualNodes.getEntries();
            while (cursor.advance()) {
                report(PartialEscapeLog.class, "AllocationVirtualized", cursor.getKey()).setProperty("materializations", cursor.getValue());
            }
            partialEscapeLog = null;
        }
    }

    /**
     * Gets the log that keeps track of virtualized allocations during partial escape analysis. Must
     * be called between {@link #enterPartialEscapeAnalysis()} and
     * {@link #exitPartialEscapeAnalysis()}.
     *
     * @return the log that keeps track of virtualized allocations during partial escape analysis
     */
    public PartialEscapeLog getPartialEscapeLog() {
        assert partialEscapeLog != null;
        return partialEscapeLog;
    }

    /**
     * Gets the tree of optimizations.
     *
     * @return the tree of optimizations
     */
    public Graph getOptimizationTree() {
        return optimizationTree;
    }

    /**
     * Gets the scope of the most recently opened phase (from unclosed phases) or null if the
     * optimization log is not enabled.
     *
     * @return the scope of the most recently opened phase (from unclosed phases) or null
     */
    public OptimizationPhaseScope getCurrentPhase() {
        return currentPhase;
    }

    /**
     * If the optimization log is enabled, prints the optimization log of this compilation to
     * {@code optimization_log/compilation-id.json} in the {@link DebugOptions#getDumpDirectoryName
     * dump directory}. Directories are created if they do not exist.
     *
     * @throws IOException failed to create a directory or the file
     */
    public void printToFileIfEnabled() throws IOException {
        if (!optimizationLogEnabled) {
            return;
        }
        String optimizationLogPath = PathUtilities.getPath(DebugOptions.getDumpDirectoryName(graph.getOptions()), "optimization_log");
        PathUtilities.createDirectories(optimizationLogPath);
        String filePath = PathUtilities.getPath(optimizationLogPath, compilationId + ".json");
        String json = JSONFormatter.formatJSON(asJsonMap());
        PrintStream stream = new PrintStream(PathUtilities.openOutputStream(filePath));
        stream.print(json);
    }

    /**
     * Dumps the tree of optimizations if the optimization log is enabled.
     */
    public void dumpOptimizationTreeIfEnabled() {
        if (optimizationLogEnabled) {
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, optimizationTree, "Optimization tree");
        }
    }

    private EconomicMap<String, Object> asJsonMap() {
        EconomicMap<String, Object> map = EconomicMap.create();
        String compilationMethodName = graph.method().format("%H.%n(%p)");
        map.put("compilationMethodName", compilationMethodName);
        map.put("compilationId", compilationId);
        map.put("rootPhase", currentPhase.asJsonMap());
        return map;
    }

    private String parseCompilationId() {
        String fullCompilationId = graph.compilationId().toString(CompilationIdentifier.Verbosity.ID);
        int dash = fullCompilationId.indexOf('-');
        if (dash == -1) {
            return fullCompilationId;
        }
        return fullCompilationId.substring(dash + 1);
    }
}
