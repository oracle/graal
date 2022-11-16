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
import java.io.OutputStream;
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
 * optimizations performed in a single compilation and dumps them to the standard output, JSON
 * files, and/or IGV.
 *
 * This is the "slow" implementation of the interface that performs the actual logging. If none of
 * the logging features is enabled, the dummy implementation should be used to reduce runtime
 * overhead.
 */
public class OptimizationLogImpl implements OptimizationLog {

    /**
     * The key of the method name property.
     */
    public static final String METHOD_NAME_PROPERTY = "methodName";

    /**
     * The key of the optimization name property. The optimization name is obtained from the name of
     * the phase that performed an optimization.
     */
    public static final String OPTIMIZATION_NAME_PROPERTY = "optimizationName";

    /**
     * The key of the event name property. The event name describes an optimization in more detail
     * than the {@link #OPTIMIZATION_NAME_PROPERTY}. Event names are in {@code PascalCase} by
     * convention.
     */
    public static final String EVENT_NAME_PROPERTY = "eventName";

    /**
     * The key of the position property. The property holds a path in an inlining tree, which
     * represents the position of an optimization in a compilation unit.
     */
    public static final String POSITION_PROPERTY = "position";

    /**
     * The key of the phase name property.
     */
    public static final String PHASE_NAME_PROPERTY = "phaseName";

    /**
     * The key of the optimizations property. The property holds a list of optimizations and
     * subphases triggered in an optimization phase.
     */
    public static final String OPTIMIZATIONS_PROPERTY = "optimizations";

    /**
     * The key of the callsite bci property. The property holds the bci of the callsite of an
     * inlining candidate method.
     */
    public static final String CALLSITE_BCI_PROPERTY = "callsiteBci";

    /**
     * The key of the inlined property. The value is {@code true} iff an inlining candidate was
     * inlined.
     */
    public static final String INLINED_PROPERTY = "inlined";

    /**
     * The key of the reason property. The property holds a list of reasons for inlining decisions
     * in their original order.
     */
    public static final String REASON_PROPERTY = "reason";

    /**
     * The key of the invokes property. The property holds the methods invoked (and considered for
     * inlining) in an inlined method.
     */
    public static final String INVOKES_PROPERTY = "invokes";

    /**
     * The name of the artificial root phase, which holds all root phases.
     */
    public static final String ROOT_PHASE_NAME = "RootPhase";

    /**
     * The key of the compilation ID property.
     */
    public static final String COMPILATION_ID_PROPERTY = "compilationId";

    /**
     * The key of the inlining property. The property holds the inlining tree, starting with the
     * root-compiled method.
     */
    public static final String INLINING_TREE_PROPERTY = "inliningTree";

    /**
     * The key of the optimization tree property. The property holds the optimization tree starting
     * with the root phase.
     */
    public static final String OPTIMIZATION_TREE_PROPERTY = "optimizationTree";

    /**
     * The name of the directory holding optimization log files.
     */
    public static final String OPTIMIZATION_LOG_DIRECTORY = "optimization_log";

    /**
     * The line separator, which separates compilation units in optimization log files. Profdiff
     * makes strong assumptions about the structure of the files to speed up parsing. Therefore, a
     * common line separator for all platform simplifies the logic.
     */
    public static final char LINE_SEPARATOR = '\n';

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
        public EconomicMap<String, Object> asJSONMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put(PHASE_NAME_PROPERTY, phaseName);
            List<EconomicMap<String, Object>> optimizations = null;
            if (children != null) {
                optimizations = new ArrayList<>();
                for (OptimizationLogImpl.OptimizationTreeNode entry : children) {
                    optimizations.add(entry.asJSONMap(methodNameFormatter));
                }
            }
            map.put(OPTIMIZATIONS_PROPERTY, optimizations);
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
        public EconomicMap<String, Object> asJSONMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
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
            compilationId = parseCompilationID();
            currentPhase = new OptimizationPhaseScopeImpl(this, ROOT_PHASE_NAME);
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
                printCompilationUnit(methodNameFormatter);
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
     * compilation unit to the standard output, JSON files and/or dumps it. If the optimization tree
     * is printed to a file, the directory is either specified by the
     * {@link DebugOptions#OptimizationLogPath OptimizationLogPath} option or it is printed to
     * {@link DebugOptions#DumpPath DumpPath}/optimization_log (the former having precedence).
     * Directories are created if they do not exist.
     *
     * When the logs are printed to files, the filename is set to the current thread ID. The
     * optimization log in the form of a single-line JSON is appended to the file. This way, the
     * number of files is reduced, which significantly speeds up parsing later. It is also not
     * necessary to coordinate with other compilation threads, because they write to a different
     * file.
     *
     * Profdiff makes strong assumptions about the format of the optimization log files in order to
     * speed up parsing. In particular, it expects one compilation per line with {@code '\n'} line
     * separators. The JSON must start with the method name and compilation ID properties. There
     * must be no extra whitespace other than what is generated by {@link JSONFormatter}.
     *
     * @param methodNameFormatter a function that formats method names
     * @throws IOException failed to create a directory or the file
     */
    private void printCompilationUnit(Function<ResolvedJavaMethod, String> methodNameFormatter) throws IOException {
        EconomicSet<DebugOptions.OptimizationLogTarget> targets = DebugOptions.OptimizationLog.getValue(graph.getOptions());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        if (targets.contains(DebugOptions.OptimizationLogTarget.Dump)) {
            graph.getDebug().dump(DebugContext.ENABLED_LEVEL, optimizationTree, "Optimization tree");
        }
        boolean printToStdout = targets.contains(DebugOptions.OptimizationLogTarget.Stdout);
        boolean printToFile = targets.contains(DebugOptions.OptimizationLogTarget.Directory);
        if (!printToStdout && !printToFile) {
            return;
        }
        String json = JSONFormatter.formatJSON(asJSONMap(methodNameFormatter));
        if (printToStdout) {
            TTY.out().println(json);
        }
        if (printToFile) {
            String pathOptionValue = DebugOptions.OptimizationLogPath.getValue(graph.getOptions());
            if (pathOptionValue == null) {
                pathOptionValue = PathUtilities.getPath(DebugOptions.getDumpDirectory(graph.getOptions()), OPTIMIZATION_LOG_DIRECTORY);
            }
            PathUtilities.createDirectories(pathOptionValue);
            @SuppressWarnings("deprecation")
            String fileName = String.valueOf(Thread.currentThread().getId());
            String filePath = PathUtilities.getPath(pathOptionValue, fileName);
            try (OutputStream outputStream = PathUtilities.openOutputStream(filePath, true);
                            PrintStream printStream = new PrintStream(outputStream)) {
                printStream.print(json);
                printStream.print(LINE_SEPARATOR);
                printStream.flush();
            }
        }
    }

    private EconomicMap<String, Object> asJSONMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
        EconomicMap<String, Object> map = EconomicMap.create();
        String compilationMethodName = methodNameFormatter.apply(graph.method());
        map.put(METHOD_NAME_PROPERTY, compilationMethodName);
        map.put(COMPILATION_ID_PROPERTY, compilationId);
        map.put(INLINING_TREE_PROPERTY, inliningTreeAsJSONMap(methodNameFormatter));
        map.put(OPTIMIZATION_TREE_PROPERTY, currentPhase.asJSONMap(methodNameFormatter));
        return map;
    }

    /**
     * Parses and returns a unique identifier for the compilation. The string before the first dash
     * is skipped, so that only the ID of the compilation request is returned.
     *
     * @return a unique compilation identifier
     */
    private String parseCompilationID() {
        String fullCompilationId = graph.compilationId().toString(CompilationIdentifier.Verbosity.ID);
        int dash = fullCompilationId.indexOf('-');
        if (dash == -1) {
            return fullCompilationId;
        }
        return fullCompilationId.substring(dash + 1);
    }

    private EconomicMap<String, Object> inliningTreeAsJSONMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
        if (graph.getInliningLog() == null) {
            return null;
        }
        return callsiteAsJSONMap(graph.getInliningLog().getRootCallsite(), true, null, methodNameFormatter);
    }

    /**
     * Converts an inlining subtree to a JSON map starting from a callsite.
     *
     * @param callsite the root of the inlining subtree
     * @param isInlined {@code true} if the callsite was inlined
     * @param reason the list of reasons for the inlining decisions made about the callsite
     * @param methodNameFormatter a function that formats method names
     * @return inlining subtree as a JSON map
     */
    private EconomicMap<String, Object> callsiteAsJSONMap(InliningLog.Callsite callsite, boolean isInlined, List<String> reason,
                    Function<ResolvedJavaMethod, String> methodNameFormatter) {
        EconomicMap<String, Object> map = EconomicMap.create();
        map.put(METHOD_NAME_PROPERTY, callsite.target == null ? null : methodNameFormatter.apply(callsite.target));
        map.put(CALLSITE_BCI_PROPERTY, callsite.getBci());
        map.put(INLINED_PROPERTY, isInlined);
        map.put(REASON_PROPERTY, reason);
        if (!isInlined) {
            return map;
        }
        List<Object> invokes = null;
        for (InliningLog.Callsite child : callsite.children) {
            boolean childIsInlined = false;
            List<String> childReason = null;
            for (InliningLog.Decision childDecision : child.decisions) {
                childIsInlined = childIsInlined || childDecision.isPositive();
                if (childDecision.getReason() != null) {
                    if (childReason == null) {
                        childReason = new ArrayList<>();
                    }
                    childReason.add(childDecision.getReason());
                }
            }
            if (invokes == null) {
                invokes = new ArrayList<>();
            }
            invokes.add(callsiteAsJSONMap(child, childIsInlined, childReason, methodNameFormatter));
        }
        map.put(INVOKES_PROPERTY, invokes);
        return map;
    }
}
