/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.PathUtilities;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.NodeSuccessorList;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.IsolateUtil;
import jdk.graal.compiler.util.json.JSONFormatter;

import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
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
     * The key of the property which is {@code true} iff the call is known to be indirect.
     */
    public static final String INDIRECT_PROPERTY = "indirect";

    /**
     * The key of the property which is {@code true} iff the invoke of a callsite is alive.
     */
    public static final String ALIVE_PROPERTY = "alive";

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
     * The key of the maturity flag in profiling info.
     */
    public static final String MATURE_PROPERTY = "mature";

    /**
     * The line separator, which separates compilation units in optimization log files. Profdiff
     * makes strong assumptions about the structure of the files to speed up parsing. Therefore, a
     * common line separator for all platform simplifies the logic.
     */
    public static final char LINE_SEPARATOR = '\n';

    /**
     * The name of the property holding the name of a receiver type.
     */
    public static final String TYPE_NAME_PROPERTY = "typeName";

    /**
     * The name of the property holding a probability of a receiver type.
     */
    public static final String PROBABILITY_PROPERTY = "probability";

    /**
     * The name of the property holding a list of receiver types with probabilities.
     */
    public static final String PROFILED_TYPES_PROPERTY = "profiledTypes";

    /**
     * The name of the property holding a receiver-type profile for a polymorphic callsite.
     */
    public static final String RECEIVER_TYPE_PROFILE_PROPERTY = "receiverTypeProfile";

    /**
     * The name of the property holding the name of the concrete method called for a given receiver
     * type.
     */
    public static final String CONCRETE_METHOD_NAME_PROPERTY = "concreteMethodName";

    /**
     * Represents an applied optimization phase as a node in the optimization tree. The children are
     * the phases applied and the optimizations performed in the phase.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED, shortName = "Phase", nameTemplate = "{p#phaseName/s}")
    public static class OptimizationPhaseNode extends OptimizationTreeNode {
        public static final NodeClass<OptimizationPhaseNode> TYPE = NodeClass.create(OptimizationPhaseNode.class);

        private final CharSequence phaseName;

        @Successor private NodeSuccessorList<OptimizationTreeNode> children;

        @SuppressWarnings("this-escape")
        protected OptimizationPhaseNode(CharSequence phaseName) {
            super(TYPE);
            this.phaseName = phaseName;
            this.children = new NodeSuccessorList<>(this, 0);
        }

        /**
         * Gets the subphases and the optimizations performed in this phase.
         */
        public CharSequence getPhaseName() {
            return phaseName;
        }

        /**
         * Gets the subphases and the optimizations performed in this phase.
         */
        public NodeSuccessorList<OptimizationTreeNode> getChildren() {
            return children;
        }

        /**
         * Adds a node to the graph as a child of this phase.
         *
         * @param node the node to be added as a child
         */
        public void addChild(OptimizationTreeNode node) {
            graph().add(node);
            children.add(node);
        }

        @Override
        public EconomicMap<String, Object> asJSONMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
            EconomicMap<String, Object> map = EconomicMap.create();
            map.put(PHASE_NAME_PROPERTY, phaseName);
            List<EconomicMap<String, Object>> optimizations = null;
            if (children != null) {
                optimizations = new ArrayList<>();
                for (OptimizationTreeNode entry : children) {
                    optimizations.add(entry.asJSONMap(methodNameFormatter));
                }
            }
            map.put(OPTIMIZATIONS_PROPERTY, optimizations);
            return map;
        }
    }

    /**
     * Represents a performed optimization in the optimization tree. The parent (predecessor) is the
     * phase that performed the optimization. An optimization does not have any children of its own;
     * it is always a leaf node in the optimization tree.
     */
    @NodeInfo(cycles = NodeCycles.CYCLES_IGNORED, size = NodeSize.SIZE_IGNORED, shortName = "Optimization", nameTemplate = "{p#event}")
    public static class OptimizationNode extends OptimizationTreeNode {
        public static final NodeClass<OptimizationNode> TYPE = NodeClass.create(OptimizationNode.class);

        /**
         * A map of additional named properties or {@code null} if no properties were set.
         */
        private final EconomicMap<String, Object> properties;

        /**
         * A position of a significant node related to this optimization.
         */
        private NodeSourcePosition position;

        /**
         * The name of this optimization. Corresponds to the name of the compiler phase or another
         * class which performed this optimization.
         */
        private final String optimizationName;

        /**
         * The name of the event that occurred, which describes this optimization entry.
         */
        private final String eventName;

        public OptimizationNode(EconomicMap<String, Object> properties, NodeSourcePosition position, String optimizationName, String eventName) {
            super(TYPE);
            this.properties = properties;
            this.position = position;
            this.optimizationName = optimizationName;
            this.eventName = eventName;
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

        @Override
        public EconomicMap<String, Object> asJSONMap(Function<ResolvedJavaMethod, String> methodNameFormatter) {
            EconomicMap<String, Object> jsonMap = EconomicMap.create();
            jsonMap.put(OPTIMIZATION_NAME_PROPERTY, optimizationName);
            jsonMap.put(EVENT_NAME_PROPERTY, eventName);
            jsonMap.put(POSITION_PROPERTY, createPositionProperty(methodNameFormatter, position));
            if (properties != null) {
                jsonMap.putAll(properties);
            }
            return jsonMap;
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
            return eventName;
        }

        /**
         * Gets the position of the optimization, i.e., the position of a significant node.
         *
         * @return the position of the optimization
         */
        public NodeSourcePosition getPosition() {
            return position;
        }
    }

    /**
     * Describes a performed optimization. Allows to incrementally add properties and then report
     * the performed optimization. When the optimization is reported and the optimization log is
     * enabled, an {@link OptimizationNode} is added to the {@link #getCurrentPhase() current
     * phase}.
     */
    public static class OptimizationEntryImpl implements OptimizationEntry {
        /**
         * A map of additional named properties or {@code null} if no properties were set.
         */
        private EconomicMap<String, Object> properties;

        /**
         * The associated optimization log, which is extended with this node when it is
         * {@link #report reported}.
         */
        private final OptimizationLogImpl optimizationLog;

        public OptimizationEntryImpl(OptimizationLogImpl optimizationLog) {
            this.optimizationLog = optimizationLog;
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
            assert logLevel >= OptimizationLog.MINIMUM_LOG_LEVEL : logLevel;
            String optimizationName = createOptimizationName(optimizationClass);
            NodeSourcePosition position = node.getNodeSourcePosition();
            DebugContext debug = optimizationLog.graph.getDebug();
            if (debug.isCountEnabled() || debug.hasUnscopedCounters()) {
                DebugContext.counter("Optimization_" + optimizationName + "_" + eventName).increment(debug);
            }
            if (debug.isLogEnabled(logLevel)) {
                debug.log(logLevel, "Performed %s %s for node %s at bci %s %s", optimizationName, eventName, node,
                                position == null ? "unknown" : position.getBCI(),
                                properties == null ? "" : JSONFormatter.formatJSON(properties));
            }
            if (debug.isDumpEnabled(logLevel)) {
                debug.dump(logLevel, optimizationLog.graph, "%s %s for %s", optimizationName, eventName, node);
            }
            if (optimizationLog.structuredOptimizationLogEnabled) {
                optimizationLog.currentPhase.addChild(new OptimizationNode(properties, position, optimizationName, eventName));
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
     * {@code true} iff the structured optimization log is
     * {@link OptimizationLog#isStructuredOptimizationLogEnabled(OptionValues) enabled}.
     */
    private final boolean structuredOptimizationLogEnabled;

    /**
     * A data structure that holds the state of virtualized allocations during partial escape
     * analysis.
     */
    private PartialEscapeLog partialEscapeLog = null;

    /**
     * The most recently entered phase which has not been exited yet. Initially, this is the root
     * phase. If {@link #structuredOptimizationLogEnabled} is {@code false}, the field stays
     * {@code null}.
     */
    private OptimizationPhaseNode currentPhase;

    /**
     * The graph that holds the optimization tree if it is enabled.
     */
    private Graph optimizationTree;

    /**
     * Constructs an optimization log bound with a given graph. Optimization
     * {@link OptimizationLog.OptimizationEntry entries} are stored iff
     * {@link DebugOptions#OptimizationLog} is enabled.
     *
     * @param graph the bound graph
     */
    public OptimizationLogImpl(StructuredGraph graph) {
        this.graph = graph;
        structuredOptimizationLogEnabled = OptimizationLog.isStructuredOptimizationLogEnabled(graph.getOptions());
        if (structuredOptimizationLogEnabled) {
            compilationId = parseCompilationID();
            currentPhase = new OptimizationPhaseNode(ROOT_PHASE_NAME);
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
    public boolean isStructuredOptimizationLogEnabled() {
        return structuredOptimizationLogEnabled;
    }

    @Override
    public boolean isAnyLoggingEnabled() {
        return true;
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
     * Gets the optimization tree.
     *
     * @return the optimization tree
     * @see OptimizationLog.OptimizationTreeNode
     */
    public Graph getOptimizationTree() {
        return optimizationTree;
    }

    /**
     * Notifies the log that an optimization phase is entered.
     *
     * @param name the name of the phase
     * @return a scope that represents the open phase
     */
    @Override
    public DebugCloseable enterPhase(CharSequence name) {
        if (structuredOptimizationLogEnabled) {
            OptimizationPhaseNode previousPhase = currentPhase;
            OptimizationPhaseNode subphase = new OptimizationPhaseNode(name);
            currentPhase.addChild(subphase);
            currentPhase = subphase;
            return () -> currentPhase = previousPhase;
        }
        return null;
    }

    @Override
    public void inline(OptimizationLog calleeOptimizationLog, boolean updatePosition, NodeSourcePosition invokePosition) {
        if (!structuredOptimizationLogEnabled || !calleeOptimizationLog.isStructuredOptimizationLogEnabled()) {
            return;
        }
        assert calleeOptimizationLog instanceof OptimizationLogImpl : "an enabled log is an instance of OptimizationLogImpl";
        OptimizationLogImpl calleeLogImpl = (OptimizationLogImpl) calleeOptimizationLog;
        Graph calleeTree = calleeLogImpl.optimizationTree;
        UnmodifiableEconomicMap<Node, Node> duplicates = optimizationTree.addDuplicates(calleeTree.getNodes(), calleeTree, calleeTree.getNodeCount(), (EconomicMap<Node, Node>) null);
        if (updatePosition) {
            for (Node duplicate : duplicates.getValues()) {
                if (!(duplicate instanceof OptimizationNode)) {
                    continue;
                }
                OptimizationNode optimization = (OptimizationNode) duplicate;
                if (invokePosition != null && optimization.position != null) {
                    optimization.position = optimization.position.addCaller(invokePosition);
                } else {
                    optimization.position = invokePosition;
                }
            }
        }
        currentPhase.children.add(duplicates.get(calleeLogImpl.findRootPhase()));
    }

    @Override
    public void replaceLog(OptimizationLog replacementLog) {
        if (!structuredOptimizationLogEnabled) {
            return;
        }
        optimizationTree = new Graph(optimizationTree.name, optimizationTree.getOptions(), optimizationTree.getDebug(), optimizationTree.trackNodeSourcePosition());
        if (!replacementLog.isStructuredOptimizationLogEnabled()) {
            currentPhase = new OptimizationPhaseNode(ROOT_PHASE_NAME);
            optimizationTree.add(currentPhase);
            return;
        }
        assert replacementLog instanceof OptimizationLogImpl : "an enabled log is an instance of OptimizationLogImpl";
        OptimizationLogImpl replacementLogImpl = (OptimizationLogImpl) replacementLog;
        Graph replacementTree = replacementLogImpl.optimizationTree;
        UnmodifiableEconomicMap<Node, Node> duplicates = optimizationTree.addDuplicates(replacementTree.getNodes(), replacementTree, replacementTree.getNodeCount(), (EconomicMap<Node, Node>) null);
        currentPhase = (OptimizationPhaseNode) duplicates.get(replacementLogImpl.findRootPhase());
    }

    @Override
    public DebugCloseable enterPartialEscapeAnalysis() {
        assert partialEscapeLog == null : "recursive entry to PEA is disallowed";
        partialEscapeLog = new PartialEscapeLog();
        return () -> {
            assert partialEscapeLog != null : "the partial escape log is available during PEA";
            MapCursor<VirtualObjectNode, Integer> cursor = partialEscapeLog.getVirtualNodes().getEntries();
            while (cursor.advance()) {
                withProperty("materializations", cursor.getValue()).report(PartialEscapeLog.class, "AllocationVirtualization", cursor.getKey());
            }
            partialEscapeLog = null;
        };
    }

    @Override
    public PartialEscapeLog getPartialEscapeLog() {
        assert partialEscapeLog != null : "accessing the partial escape log outside PEA";
        return partialEscapeLog;
    }

    /**
     * Gets the current phase or {@code null} if the optimization log is not enabled. The current
     * phase is the most recently entered phase which has not been exited yet.
     *
     * @return the current phase or {@code null}
     */
    public OptimizationPhaseNode getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Depending on the {@link DebugOptions#OptimizationLog OptimizationLog} option, prints the
     * optimization log to the standard output, JSON files and/or dumps it. When the log is printed
     * to a file, the directory is either specified by the {@link DebugOptions#OptimizationLogPath
     * OptimizationLogPath} option or it is printed to {@link DebugOptions#DumpPath
     * DumpPath}/optimization_log (the former having precedence). Directories are created if they do
     * not exist.
     *
     * When the log are printed to a file, the filename is set to the current thread ID. The
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
     */
    @Override
    public void emit(Function<ResolvedJavaMethod, String> methodNameFormatter) {
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
            try {
                String pathOptionValue = DebugOptions.OptimizationLogPath.getValue(graph.getOptions());
                if (pathOptionValue == null) {
                    pathOptionValue = PathUtilities.getPath(DebugOptions.getDumpDirectory(graph.getOptions()), OPTIMIZATION_LOG_DIRECTORY);
                }
                PathUtilities.createDirectories(pathOptionValue);
                @SuppressWarnings("deprecation")
                String fileName = IsolateUtil.getIsolateID() + "_" + Thread.currentThread().getId();
                String filePath = PathUtilities.getPath(pathOptionValue, fileName);
                try (OutputStream outputStream = PathUtilities.openOutputStream(filePath, true);
                                PrintStream printStream = new PrintStream(outputStream)) {
                    printStream.print(json);
                    printStream.print(LINE_SEPARATOR);
                    printStream.flush();
                }
            } catch (IOException exception) {
                throw new GraalError("Failed to print the optimization log to a file: %s", exception.getMessage());
            }
        }
    }

    /**
     * Finds and returns the root of the optimization tree.
     *
     * @return the root of the optimization tree
     */
    public OptimizationPhaseNode findRootPhase() {
        OptimizationPhaseNode root = currentPhase;
        while (root.predecessor() != null) {
            root = (OptimizationPhaseNode) root.predecessor();
        }
        assert ROOT_PHASE_NAME.contentEquals(root.getPhaseName()) : "the found phase must be the root phase";
        return root;
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
        assert graph.getInliningLog() != null : "the graph must have an inlining log";
        EconomicMap<InliningLog.Callsite, EconomicMap<String, Object>> replacements = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        return callsiteAsJSONMap(graph.getInliningLog().getRootCallsite(), true, null, methodNameFormatter, replacements);
    }

    /**
     * Converts an inlining subtree to a JSON map starting from a callsite.
     *
     * The tree built by this method respects {@link InliningLog.Callsite#getOverriddenParent() the
     * overriden parents}. As a result of this, the tree is slightly different from what is printed
     * by {@link GraalOptions#TraceInlining}. This is achieved by remembering the mapping from
     * callsites to their JSON representations in the {@code replacements} parameter. Each callsite
     * then attaches itself to the correct {@link InliningLog.Callsite#getOverriddenParent() parent}
     * by querying {@code replacements}.
     *
     * @param callsite the root of the inlining subtree
     * @param isInlined {@code true} if the callsite was inlined
     * @param reason the list of reasons for the inlining decisions made about the callsite
     * @param methodNameFormatter a function that formats method names
     * @param replacements a mapping of callsites to their respective JSON maps
     * @return inlining subtree as a JSON map
     */
    @SuppressWarnings("unchecked")
    private EconomicMap<String, Object> callsiteAsJSONMap(InliningLog.Callsite callsite, boolean isInlined, List<String> reason,
                    Function<ResolvedJavaMethod, String> methodNameFormatter, EconomicMap<InliningLog.Callsite, EconomicMap<String, Object>> replacements) {
        EconomicMap<String, Object> map = EconomicMap.create();
        replacements.put(callsite, map);
        map.put(METHOD_NAME_PROPERTY, callsite.getTarget() == null ? null : methodNameFormatter.apply(callsite.getTarget()));
        map.put(CALLSITE_BCI_PROPERTY, callsite.getBci());
        map.put(INLINED_PROPERTY, isInlined);
        map.put(REASON_PROPERTY, reason);
        map.put(INDIRECT_PROPERTY, callsite.isIndirect());
        map.put(ALIVE_PROPERTY, callsite.getInvoke() instanceof Node && ((Node) callsite.getInvoke()).isAlive());
        if (callsite.isIndirect()) {
            EconomicMap<String, Object> receiverTypeProfile = receiverTypeProfileAsJSONMap(callsite, methodNameFormatter);
            if (receiverTypeProfile != null) {
                map.put(RECEIVER_TYPE_PROFILE_PROPERTY, receiverTypeProfile);
            }
        }
        for (InliningLog.Callsite child : callsite.getChildren()) {
            boolean childIsInlined = false;
            List<String> childReason = null;
            for (InliningLog.Decision childDecision : child.getDecisions()) {
                childIsInlined = childIsInlined || childDecision.isPositive();
                if (childDecision.getReason() != null) {
                    if (childReason == null) {
                        childReason = new ArrayList<>();
                    }
                    childReason.add(childDecision.getReason());
                }
            }
            callsiteAsJSONMap(child, childIsInlined, childReason, methodNameFormatter, replacements);
        }
        if (callsite.getOverriddenParent() != null) {
            EconomicMap<String, Object> parentMap = replacements.get(callsite.getOverriddenParent());
            assert parentMap != null : "there must already exist a JSON map for the overriden parent";
            List<Object> parentInvokesProperty = (List<Object>) parentMap.get(INVOKES_PROPERTY);
            if (parentInvokesProperty == null) {
                parentInvokesProperty = new ArrayList<>();
                parentMap.put(INVOKES_PROPERTY, parentInvokesProperty);
            }
            parentInvokesProperty.add(map);
        }
        return map;
    }

    /**
     * Converts the type profile of the receiver to a JSON map. Returns {@code null} if there is no
     * profiling info available.
     *
     * @param callsite the callsite whose profiling info is returned
     * @return the type profile as a map or {@code null}
     */
    private EconomicMap<String, Object> receiverTypeProfileAsJSONMap(InliningLog.Callsite callsite, Function<ResolvedJavaMethod, String> methodNameFormatter) {
        if (callsite.getParent() == null || callsite.getParent().getTarget() == null) {
            return null;
        }
        ProfilingInfo profilingInfo = graph.getProfileProvider().getProfilingInfo(callsite.getParent().getTarget());
        if (profilingInfo == null) {
            return null;
        }
        EconomicMap<String, Object> typeProfileMap = EconomicMap.create();
        typeProfileMap.put(MATURE_PROPERTY, profilingInfo.isMature());
        if (callsite.getTargetTypeProfile() != null) {
            List<EconomicMap<String, Object>> profiledTypes = new ArrayList<>();
            for (JavaTypeProfile.ProfiledType profiledType : callsite.getTargetTypeProfile().getTypes()) {
                EconomicMap<String, Object> profiledTypeMap = EconomicMap.create();
                profiledTypeMap.put(TYPE_NAME_PROPERTY, profiledType.getType().toJavaName(true));
                profiledTypeMap.put(PROBABILITY_PROPERTY, profiledType.getProbability());
                if (callsite.getTarget() != null) {
                    ResolvedJavaMethod concreteMethod = profiledType.getType().resolveConcreteMethod(callsite.getTarget(), callsite.getParent().getTarget().getDeclaringClass());
                    if (concreteMethod != null) {
                        profiledTypeMap.put(CONCRETE_METHOD_NAME_PROPERTY, methodNameFormatter.apply(concreteMethod));
                    }
                }
                profiledTypes.add(profiledTypeMap);
            }
            typeProfileMap.put(PROFILED_TYPES_PROPERTY, profiledTypes);
        }
        return typeProfileMap;
    }
}
