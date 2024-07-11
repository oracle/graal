/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.CancellationBailoutException;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.JavaMethodContext;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.loop.LoopSafepointVerification;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.nodes.spi.ResolvedJavaMethodProfileProvider;
import jdk.graal.compiler.nodes.spi.TrackedUnsafeAccess;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * A graph that contains at least one distinguished node : the {@link #start() start} node. This
 * node is the start of the control flow of the graph.
 */
public final class StructuredGraph extends Graph implements JavaMethodContext {
    /**
     * Constants denoting whether or not {@link Assumption}s can be made while processing a graph.
     */
    public enum AllowAssumptions {
        YES,
        NO;

        public static AllowAssumptions ifTrue(boolean flag) {
            return flag ? YES : NO;
        }

        public static AllowAssumptions ifNonNull(Assumptions assumptions) {
            return assumptions != null ? YES : NO;
        }
    }

    public static class ScheduleResult {
        private final ControlFlowGraph cfg;
        private final NodeMap<HIRBlock> nodeToBlockMap;
        private final BlockMap<List<Node>> blockToNodesMap;
        public final SchedulingStrategy strategy;

        public ScheduleResult(ControlFlowGraph cfg, NodeMap<HIRBlock> nodeToBlockMap, BlockMap<List<Node>> blockToNodesMap, SchedulingStrategy strategy) {
            this.cfg = cfg;
            this.nodeToBlockMap = nodeToBlockMap;
            this.blockToNodesMap = blockToNodesMap;
            this.strategy = strategy;
        }

        public ControlFlowGraph getCFG() {
            return cfg;
        }

        public NodeMap<HIRBlock> getNodeToBlockMap() {
            return nodeToBlockMap;
        }

        public BlockMap<List<Node>> getBlockToNodesMap() {
            return blockToNodesMap;
        }

        public List<Node> nodesFor(HIRBlock block) {
            return blockToNodesMap.get(block);
        }

        public HIRBlock blockFor(Node n) {
            if (n instanceof PhiNode) {
                return blockFor(((PhiNode) n).merge());
            } else if (n instanceof ProxyNode) {
                return blockFor(((ProxyNode) n).proxyPoint());
            } else {
                return nodeToBlockMap.get(n);
            }
        }

        public String print() {
            StringBuilder sb = new StringBuilder();
            sb.append("Schedule for graph ").append(cfg.graph).append(System.lineSeparator());
            for (HIRBlock b : cfg.reversePostOrder()) {
                sb.append("Block=").append(b).append(" with beginNode=").append(b.getBeginNode());
                sb.append(" endNode=").append(b.getEndNode()).append(System.lineSeparator());
                for (var node : blockToNodesMap.get(b)) {
                    sb.append("\t").append(node).append(System.lineSeparator());
                }
            }
            return sb.toString();
        }
    }

    /**
     * Object used to create a {@link StructuredGraph}.
     */
    public static class Builder {
        private String name;
        private final Assumptions assumptions;
        private SpeculationLog speculationLog;
        private ResolvedJavaMethod rootMethod;
        private CompilationIdentifier compilationId = CompilationIdentifier.INVALID_COMPILATION_ID;
        private int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
        private ProfileProvider profileProvider = new ResolvedJavaMethodProfileProvider();
        private boolean recordInlinedMethods = true;
        private boolean trackNodeSourcePosition;
        private final OptionValues options;
        private Cancellable cancellable = null;
        private final DebugContext debug;
        private NodeSourcePosition callerContext;
        private boolean isSubstitution;

        /**
         * Creates a builder for a graph.
         */
        public Builder(OptionValues options, DebugContext debug, AllowAssumptions allowAssumptions) {
            this.options = options;
            this.debug = debug;
            this.assumptions = allowAssumptions == AllowAssumptions.YES ? new Assumptions() : null;
            this.trackNodeSourcePosition = Graph.trackNodeSourcePositionDefault(options, debug);
        }

        /**
         * Creates a builder for a graph that does not support {@link Assumptions}.
         */
        public Builder(OptionValues options, DebugContext debug) {
            this.options = options;
            this.debug = debug;
            this.assumptions = null;
            this.trackNodeSourcePosition = Graph.trackNodeSourcePositionDefault(options, debug);
        }

        public Builder name(String s) {
            this.name = s;
            return this;
        }

        /**
         * @see StructuredGraph#isSubstitution()
         */
        public Builder setIsSubstitution(boolean flag) {
            this.isSubstitution = flag;
            if (isSubstitution) {
                this.profileProvider = null;
            }
            return this;
        }

        public ResolvedJavaMethod getMethod() {
            return rootMethod;
        }

        public Builder method(ResolvedJavaMethod method) {
            this.rootMethod = method;
            return this;
        }

        public Builder speculationLog(SpeculationLog log) {
            this.speculationLog = log;
            return this;
        }

        public CompilationIdentifier getCompilationId() {
            return compilationId;
        }

        public Builder compilationId(CompilationIdentifier id) {
            this.compilationId = id;
            return this;
        }

        public Cancellable getCancellable() {
            return cancellable;
        }

        public Builder cancellable(Cancellable cancel) {
            this.cancellable = cancel;
            return this;
        }

        public Builder entryBCI(int bci) {
            this.entryBCI = bci;
            return this;
        }

        public Builder profileProvider(ProfileProvider provider) {
            this.profileProvider = provider;
            return this;
        }

        public Builder recordInlinedMethods(boolean flag) {
            this.recordInlinedMethods = flag;
            return this;
        }

        public Builder trackNodeSourcePosition(boolean flag) {
            if (flag) {
                this.trackNodeSourcePosition = true;
            }
            return this;
        }

        public Builder callerContext(NodeSourcePosition context) {
            this.callerContext = context;
            return this;
        }

        public StructuredGraph build() {
            GraphState newGraphState = GraphState.defaultGraphState();
            List<ResolvedJavaMethod> inlinedMethods = recordInlinedMethods ? new ArrayList<>() : null;
            // @formatter:off
            return new StructuredGraph(name,
                            rootMethod,
                            entryBCI,
                            assumptions,
                            profileProvider,
                            newGraphState.copyWith(isSubstitution, speculationLog),
                            isSubstitution,
                            inlinedMethods,
                            trackNodeSourcePosition,
                            compilationId,
                            options,
                            debug,
                            cancellable,
                            callerContext);
            // @formatter:on
        }
    }

    private static final AtomicLong uniqueGraphIds = new AtomicLong();

    private StartNode start;
    private final ResolvedJavaMethod rootMethod;
    private final long graphId;
    private final CompilationIdentifier compilationId;
    private final int entryBCI;
    private final ProfileProvider profileProvider;
    private GraphState graphState;
    private final Cancellable cancellable;
    private final boolean isSubstitution;

    /**
     * The assumptions made while constructing and transforming this graph.
     */
    private final Assumptions assumptions;

    /**
     * The last schedule which was computed for this graph. Only re-use if
     * {@link #isLastScheduleValid()} is {@code true}.
     */
    private ScheduleResult lastSchedule;

    /**
     * The last control flow graph which was computed for this graph. Only re-use if
     * {@link #isLastCFGValid()} is {@code true}.
     */
    private ControlFlowGraph lastCFG;

    private final CacheInvalidationListener cacheInvalidationListener;
    private NodeEventScope cacheInvalidationNES;

    /**
     * Invalidates cached values (e.g., schedule or CFG) if the graph changes. Afterwards, removes
     * itself from the graph's list of listeners. Needs to be added to the graph again after one of
     * the caches is set to ensure proper invalidation.
     */
    private final class CacheInvalidationListener extends NodeEventListener {

        private boolean lastCFGValid;
        private boolean lastScheduleValid;

        @Override
        public void changed(NodeEvent e, Node node) {
            lastCFGValid = false;
            lastScheduleValid = false;
            disableCacheInvalidationListener();
        }
    }

    /**
     * Returns {@code true} if the graph has not changed since calculating the last schedule. Use
     * {@link #getLastSchedule()} for obtaining the cached schedule.
     */
    public boolean isLastScheduleValid() {
        return cacheInvalidationListener.lastScheduleValid;
    }

    /**
     * Returns {@code true} if the graph has not changed since calculating the last control flow
     * graph. Use {@link #getLastCFG()} for obtaining the cached cfg.
     */
    public boolean isLastCFGValid() {
        return cacheInvalidationListener.lastCFGValid;
    }

    private void enableCacheInvalidationListener() {
        if (cacheInvalidationNES == null) {
            cacheInvalidationNES = this.trackNodeEvents(cacheInvalidationListener);
        }
    }

    private void disableCacheInvalidationListener() {
        if (cacheInvalidationNES != null) {
            cacheInvalidationNES.close();
            cacheInvalidationNES = null;
        }
    }

    private InliningLog inliningLog;

    /**
     * Call stack (context) leading to construction of this graph.
     */
    private final NodeSourcePosition callerContext;

    /**
     * Records the methods that were used while constructing this graph, one entry for each time a
     * specific method is used. This will be {@code null} if recording of inlined methods is
     * disabled for the graph.
     */
    private final List<ResolvedJavaMethod> methods;

    /**
     * See {@link #markUnsafeAccess(Class)} for explanation.
     */
    private enum UnsafeAccessState {
        /**
         * A {@link TrackedUnsafeAccess} node has never been added to this graph.
         */
        NO_ACCESS,

        /**
         * A {@link TrackedUnsafeAccess} node was added to this graph at a prior point.
         */
        HAS_ACCESS,

        /**
         * In synthetic methods we disable unsafe access tracking.
         */
        DISABLED
    }

    private UnsafeAccessState hasUnsafeAccess = UnsafeAccessState.NO_ACCESS;

    public static final boolean USE_PROFILING_INFO = true;

    public static final boolean NO_PROFILING_INFO = false;

    private OptimizationLog optimizationLog;

    private StructuredGraph(String name,
                    ResolvedJavaMethod method,
                    int entryBCI,
                    Assumptions assumptions,
                    ProfileProvider profileProvider,
                    GraphState graphState,
                    boolean isSubstitution,
                    List<ResolvedJavaMethod> methods,
                    boolean trackNodeSourcePosition,
                    CompilationIdentifier compilationId,
                    OptionValues options,
                    DebugContext debug,
                    Cancellable cancellable,
                    NodeSourcePosition context) {
        super(name, options, debug, trackNodeSourcePosition);
        this.graphState = graphState;
        this.setStart(add(new StartNode()));
        this.rootMethod = method;
        this.graphId = uniqueGraphIds.incrementAndGet();
        this.compilationId = compilationId;
        this.entryBCI = entryBCI;
        this.assumptions = assumptions;
        this.methods = methods;
        assert !isSubstitution || profileProvider == null;
        this.profileProvider = profileProvider;
        this.isSubstitution = isSubstitution;
        this.cancellable = cancellable;
        this.inliningLog = GraalOptions.TraceInlining.getValue(options) || OptimizationLog.isStructuredOptimizationLogEnabled(options) ? new InliningLog(rootMethod) : null;
        this.callerContext = context;
        this.optimizationLog = OptimizationLog.getInstance(this);
        this.cacheInvalidationListener = new CacheInvalidationListener();
    }

    public void setLastSchedule(ScheduleResult result) {
        GraalError.guarantee(result == null || result.cfg.getStartBlock().isModifiable(), "Schedule must use blocks that can be modified");
        lastSchedule = result;
        cacheInvalidationListener.lastScheduleValid = result != null;
        if (result != null) {
            enableCacheInvalidationListener();
        }
    }

    /**
     * Returns the last schedule which has been computed for this graph. Use
     * {@link #isLastScheduleValid()} to verify that the graph has not changed since the last
     * schedule has been computed.
     */
    public ScheduleResult getLastSchedule() {
        return lastSchedule;
    }

    public void clearLastSchedule() {
        setLastSchedule(null);
        clearLastCFG();
    }

    /**
     * Returns the last control flow graph which has been computed for this graph. Use
     * {@link #isLastCFGValid()} to verify that the graph has not changed since the last cfg has
     * been computed. Creating a {@link ControlFlowGraph} via
     * {@link ControlFlowGraphBuilder#build()} will implicitly return and/or update the cached cfg.
     */
    public ControlFlowGraph getLastCFG() {
        return lastCFG;
    }

    public void setLastCFG(ControlFlowGraph cfg) {
        lastCFG = cfg;
        cacheInvalidationListener.lastCFGValid = cfg != null;
        if (cfg != null) {
            enableCacheInvalidationListener();
        }
    }

    public void clearLastCFG() {
        setLastCFG(null);
    }

    @Override
    public void getDebugProperties(Map<Object, Object> properties) {
        super.getDebugProperties(properties);
        properties.put("compilationIdentifier", compilationId());
        properties.put("edgeModificationCount", getEdgeModificationCount());
        properties.put("assumptions", String.valueOf(getAssumptions()));
    }

    @Override
    public void beforeNodeDuplication(Graph sourceGraph) {
        super.beforeNodeDuplication(sourceGraph);
        recordAssumptions((StructuredGraph) sourceGraph);
    }

    @Override
    protected Object beforeNodeIdChange(Node node) {
        if (inliningLog != null && node instanceof Invokable) {
            return inliningLog.unregisterLeafCallsite((Invokable) node);
        }
        return null;
    }

    @Override
    protected void afterNodeIdChange(Node node, Object value) {
        if (inliningLog != null && node instanceof Invokable) {
            inliningLog.registerLeafCallsite((Invokable) node, (InliningLog.Callsite) value);
        }
    }

    @Override
    protected boolean compress(boolean minimizeSize) {
        if (super.compress(minimizeSize)) {
            /*
             * The schedule contains a NodeMap which is unusable after compression.
             */
            clearLastSchedule();
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(getClass().getSimpleName() + ":" + graphId);
        String sep = "{";
        if (name != null) {
            buf.append(sep);
            buf.append(name);
            sep = ", ";
        }
        if (method() != null) {
            buf.append(sep);
            buf.append(method());
            sep = ", ";
        }

        if (!sep.equals("{")) {
            buf.append("}");
        }
        return buf.toString();
    }

    public StartNode start() {
        return start;
    }

    /**
     * Gets the root method from which this graph was built.
     *
     * @return null if this method was not built from a method or the method is not available
     */
    public ResolvedJavaMethod method() {
        return rootMethod;
    }

    public int getEntryBCI() {
        return entryBCI;
    }

    public GraphState getGraphState() {
        return graphState;
    }

    /**
     * Returns the guards stage of this graph. See {@link GraphState#getGuardsStage()}.
     */
    public GraphState.GuardsStage getGuardsStage() {
        return getGraphState().getGuardsStage();
    }

    /**
     * Returns the {@link SpeculationLog} for this graph. See
     * {@link GraphState#getSpeculationLog()}.
     */
    public SpeculationLog getSpeculationLog() {
        return getGraphState().getSpeculationLog();
    }

    /**
     * See {@link GraphState#isBeforeStage}.
     */
    public boolean isBeforeStage(GraphState.StageFlag stage) {
        return getGraphState().isBeforeStage(stage);
    }

    /**
     * See {@link GraphState#isAfterStage}.
     */
    public boolean isAfterStage(GraphState.StageFlag stage) {
        return getGraphState().isAfterStage(stage);
    }

    public Cancellable getCancellable() {
        return cancellable;
    }

    public void checkCancellation() {
        if (cancellable != null && cancellable.isCancelled()) {
            CancellationBailoutException.cancelCompilation();
        }
    }

    public boolean isOSR() {
        return entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
    }

    public long graphId() {
        return graphId;
    }

    /**
     * @see CompilationIdentifier
     */
    public CompilationIdentifier compilationId() {
        return compilationId;
    }

    public void setStart(StartNode start) {
        this.start = start;
    }

    /**
     * Gets the inlining log associated with this graph. This will return {@code null} iff
     * {@link GraalOptions#TraceInlining} is {@code false} in {@link #getOptions()}.
     */
    public InliningLog getInliningLog() {
        return inliningLog;
    }

    /**
     * Notifies this graph of an inlining decision for {@code invoke}.
     *
     * An inlining decision can be either positive or negative. A positive inlining decision must be
     * logged after replacing an {@link Invoke} with a graph. In this case, the node replacement map
     * and the {@link InliningLog} of the inlined graph must be provided.
     *
     * @param invoke the invocation to which the inlining decision pertains
     * @param positive {@code true} if the invocation was inlined, {@code false} otherwise
     * @param phase name of the phase doing the inlining
     * @param replacements the node replacement map used by inlining, ignored if
     *            {@code positive == false}
     * @param calleeInliningLog the inlining log of the inlined graph, ignored if
     *            {@code positive == false}
     * @param calleeOptimizationLog the optimization log of the inlined graph, ignored if
     *            {@code positive == false}
     * @param inlineeMethod the actual method considered for inlining
     * @param reason format string that along with {@code args} provides the reason for decision
     */
    public void notifyInliningDecision(Invokable invoke, boolean positive, String phase, EconomicMap<Node, Node> replacements,
                    InliningLog calleeInliningLog, OptimizationLog calleeOptimizationLog, ResolvedJavaMethod inlineeMethod,
                    String reason, Object... args) {
        if (inliningLog != null) {
            inliningLog.addDecision(invoke, positive, phase, replacements, calleeInliningLog, inlineeMethod, reason, args);
        }
        if (positive && calleeOptimizationLog != null && optimizationLog.isStructuredOptimizationLogEnabled()) {
            FixedNode invokeNode = invoke.asFixedNodeOrNull();
            optimizationLog.inline(calleeOptimizationLog, true, invokeNode == null ? null : invokeNode.getNodeSourcePosition());
        }
        if (getDebug().hasCompilationListener()) {
            String message = String.format(reason, args);
            getDebug().notifyInlining(invoke.getContextMethod(), inlineeMethod, positive, message, invoke.bci());
        }
    }

    public void logInliningTree() {
        if (GraalOptions.TraceInlining.getValue(getOptions())) {
            String formattedTree = inliningLog.formatAsTree(true);
            if (formattedTree != null) {
                TTY.println(formattedTree);
            }
        }
    }

    /**
     * Creates a copy of this graph.
     *
     * If a node contains an array of objects, only shallow copy of the field is applied.
     *
     * @param newName the name of the copy, used for debugging purposes (can be null)
     * @param duplicationMapCallback consumer of the duplication map created during the copying
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    @Override
    protected Graph copy(String newName, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback, DebugContext debugForCopy) {
        return copy(newName, rootMethod, getOptions(), duplicationMapCallback, compilationId, debugForCopy, trackNodeSourcePosition);
    }

    /**
     * Creates a copy of this graph with the new option values.
     *
     * If a node contains an array of objects, only shallow copy of the field is applied.
     *
     * @param newName the name of the copy, used for debugging purposes (can be null)
     * @param duplicationMapCallback consumer of the duplication map created during the copying
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     * @param options the option values for the graph copy
     */
    public StructuredGraph copy(String newName, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback, DebugContext debugForCopy, OptionValues options) {
        return copy(newName, rootMethod, options, duplicationMapCallback, compilationId, debugForCopy, trackNodeSourcePosition);
    }

    private StructuredGraph copy(String newName, ResolvedJavaMethod rootMethodForCopy, OptionValues optionsForCopy, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback,
                    CompilationIdentifier newCompilationId, DebugContext debugForCopy, boolean trackNodeSourcePositionForCopy) {
        List<ResolvedJavaMethod> inlinedMethodsForCopy = methods != null ? new ArrayList<>(methods) : null;
        return copy(newName, rootMethodForCopy, inlinedMethodsForCopy, optionsForCopy, duplicationMapCallback, newCompilationId, debugForCopy, trackNodeSourcePositionForCopy);
    }

    private StructuredGraph copy(String newName, ResolvedJavaMethod rootMethodForCopy, List<ResolvedJavaMethod> inlinedMethodsForCopy, OptionValues optionsForCopy,
                    Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback,
                    CompilationIdentifier newCompilationId, DebugContext debugForCopy, boolean trackNodeSourcePositionForCopy) {
        return copy(newName, rootMethodForCopy, inlinedMethodsForCopy, optionsForCopy, duplicationMapCallback, newCompilationId, debugForCopy, trackNodeSourcePositionForCopy, assumptions);
    }

    @SuppressWarnings("try")
    private StructuredGraph copy(String newName, ResolvedJavaMethod rootMethodForCopy, List<ResolvedJavaMethod> inlinedMethodsForCopy, OptionValues optionsForCopy,
                    Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback,
                    CompilationIdentifier newCompilationId, DebugContext debugForCopy, boolean trackNodeSourcePositionForCopy, Assumptions assumptionsForCopy) {
        AllowAssumptions allowAssumptions = AllowAssumptions.ifNonNull(assumptionsForCopy);

        StructuredGraph copy = new StructuredGraph(newName,
                        rootMethodForCopy,
                        entryBCI,
                        assumptionsForCopy == null ? null : new Assumptions(),
                        profileProvider,
                        graphState.copy(),
                        isSubstitution,
                        inlinedMethodsForCopy,
                        trackNodeSourcePositionForCopy,
                        newCompilationId,
                        optionsForCopy, debugForCopy, null, callerContext);
        if (allowAssumptions == AllowAssumptions.YES && assumptionsForCopy != null) {
            copy.assumptions.record(assumptionsForCopy);
        }
        copy.hasUnsafeAccess = hasUnsafeAccess;
        EconomicMap<Node, Node> replacements = EconomicMap.create(Equivalence.IDENTITY);
        replacements.put(start, copy.start);
        UnmodifiableEconomicMap<Node, Node> duplicates;
        InliningLog copyInliningLog = copy.getInliningLog();
        try (InliningLog.UpdateScope scope = InliningLog.openDefaultUpdateScope(copyInliningLog)) {
            duplicates = copy.addDuplicates(getNodes(), this, this.getNodeCount(), replacements);
            if (scope != null) {
                copyInliningLog.replaceLog(duplicates, this.getInliningLog());
            }
        }
        copy.getOptimizationLog().replaceLog(optimizationLog);
        if (duplicationMapCallback != null) {
            duplicationMapCallback.accept(duplicates);
        }
        return copy;
    }

    /**
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    public StructuredGraph copyWithIdentifier(CompilationIdentifier newCompilationId, DebugContext debugForCopy) {
        return copy(name, rootMethod, getOptions(), null, newCompilationId, debugForCopy, trackNodeSourcePosition);
    }

    public StructuredGraph copy(ResolvedJavaMethod rootMethodForCopy, OptionValues optionsForCopy, DebugContext debugForCopy, boolean trackNodeSourcePositionForCopy) {
        return copy(name, rootMethodForCopy, optionsForCopy, null, compilationId, debugForCopy, trackNodeSourcePositionForCopy);
    }

    public StructuredGraph copy(ResolvedJavaMethod rootMethodForCopy, List<ResolvedJavaMethod> inlinedMethodsForCopy, OptionValues optionsForCopy, DebugContext debugForCopy,
                    boolean trackNodeSourcePositionForCopy) {
        return copy(name, rootMethodForCopy, inlinedMethodsForCopy, optionsForCopy, null, compilationId, debugForCopy, trackNodeSourcePositionForCopy);
    }

    /**
     * Makes a copy of this graph, recording both this graph's assumptions (if any) and the
     * additional {@code newAssumptions} in it.
     *
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    public StructuredGraph copyWithAssumptions(Assumptions newAssumptions, DebugContext debugForCopy) {
        List<ResolvedJavaMethod> inlinedMethodsForCopy = methods != null ? new ArrayList<>(methods) : null;
        Assumptions assumptionsForCopy = new Assumptions();
        if (assumptions != null) {
            assumptionsForCopy.record(assumptions);
        }
        assumptionsForCopy.record(newAssumptions);
        return copy(name, rootMethod, inlinedMethodsForCopy, getOptions(), null, compilationId, debugForCopy, trackNodeSourcePosition, assumptionsForCopy);
    }

    public ParameterNode getParameter(int index) {
        for (ParameterNode param : getNodes(ParameterNode.TYPE)) {
            if (param.index() == index) {
                return param;
            }
        }
        return null;
    }

    public Iterable<Invoke> getInvokes() {
        final Iterator<MethodCallTargetNode> callTargets = getNodes(MethodCallTargetNode.TYPE).iterator();
        return new Iterable<>() {

            private Invoke next;

            @Override
            public Iterator<Invoke> iterator() {
                return new Iterator<>() {

                    @Override
                    public boolean hasNext() {
                        if (next == null) {
                            while (callTargets.hasNext()) {
                                Invoke i = callTargets.next().invoke();
                                if (i != null) {
                                    next = i;
                                    return true;
                                }
                            }
                            return false;
                        } else {
                            return true;
                        }
                    }

                    @Override
                    public Invoke next() {
                        try {
                            return next;
                        } finally {
                            next = null;
                        }
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public boolean hasLoops() {
        return hasNode(LoopBeginNode.TYPE);
    }

    /**
     * Unlinks a node from all its control flow neighbors and then removes it from its graph. The
     * node must have no {@linkplain Node#usages() usages}.
     *
     * @param node the node to be unlinked and removed
     */
    @SuppressWarnings("static-method")
    public void removeFixed(FixedWithNextNode node) {
        assert node != null;
        if (node instanceof AbstractBeginNode) {
            ((AbstractBeginNode) node).prepareDelete();
        }
        assert node.hasNoUsages() : node + " " + node.getUsageCount() + ", " + node.usages().first();
        GraphUtil.unlinkFixedNode(node);
        node.safeDelete();
    }

    public void replaceFixed(FixedWithNextNode node, Node replacement) {
        if (replacement instanceof FixedWithNextNode) {
            replaceFixedWithFixed(node, (FixedWithNextNode) replacement);
        } else {
            assert replacement != null : "cannot replace " + node + " with null";
            assert replacement instanceof FloatingNode : "cannot replace " + node + " with " + replacement;
            replaceFixedWithFloating(node, (FloatingNode) replacement);
        }
    }

    public void replaceFixedWithFixed(FixedWithNextNode node, FixedWithNextNode replacement) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        FixedNode next = node.next();
        node.setNext(null);
        replacement.setNext(next);
        node.replaceAndDelete(replacement);
        if (node == start) {
            setStart((StartNode) replacement);
        }
    }

    @SuppressWarnings("static-method")
    public void replaceFixedWithFloating(FixedWithNextNode node, ValueNode replacement) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        GraphUtil.unlinkFixedNode(node);
        node.replaceAtUsagesAndDelete(replacement);
    }

    @SuppressWarnings("static-method")
    public void removeSplit(ControlSplitNode node, AbstractBeginNode survivingSuccessor) {
        assert node != null;
        assert node.hasNoUsages();
        assert survivingSuccessor != null;
        node.clearSuccessors();
        node.replaceAtPredecessor(survivingSuccessor);
        node.safeDelete();
    }

    @SuppressWarnings("static-method")
    public void removeSplitPropagate(ControlSplitNode node, AbstractBeginNode survivingSuccessor) {
        assert node != null;
        assert node.hasNoUsages();
        assert survivingSuccessor != null;
        List<Node> snapshot = node.successors().snapshot();
        node.clearSuccessors();
        node.replaceAtPredecessor(survivingSuccessor);
        node.safeDelete();
        for (Node successor : snapshot) {
            if (successor != null && successor.isAlive()) {
                if (successor != survivingSuccessor) {
                    GraphUtil.killCFG((FixedNode) successor);
                }
            }
        }
    }

    public void replaceSplit(ControlSplitNode node, Node replacement, AbstractBeginNode survivingSuccessor) {
        if (replacement instanceof FixedWithNextNode) {
            replaceSplitWithFixed(node, (FixedWithNextNode) replacement, survivingSuccessor);
        } else {
            assert replacement != null : "cannot replace " + node + " with null";
            assert replacement instanceof FloatingNode : "cannot replace " + node + " with " + replacement;
            replaceSplitWithFloating(node, (FloatingNode) replacement, survivingSuccessor);
        }
    }

    @SuppressWarnings("static-method")
    public void replaceSplitWithFixed(ControlSplitNode node, FixedWithNextNode replacement, AbstractBeginNode survivingSuccessor) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        assert survivingSuccessor != null;
        node.clearSuccessors();
        replacement.setNext(survivingSuccessor);
        node.replaceAndDelete(replacement);
    }

    @SuppressWarnings("static-method")
    public void replaceSplitWithFloating(ControlSplitNode node, FloatingNode replacement, AbstractBeginNode survivingSuccessor) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        assert survivingSuccessor != null;
        node.clearSuccessors();
        node.replaceAtPredecessor(survivingSuccessor);
        node.replaceAtUsagesAndDelete(replacement);
    }

    @SuppressWarnings("static-method")
    public void replaceWithExceptionSplit(WithExceptionNode node, WithExceptionNode replacement) {
        assert node != null && replacement != null && node.isAlive() && replacement.isAlive() : "cannot replace " + node + " with " + replacement;
        node.replaceAtPredecessor(replacement);
        AbstractBeginNode next = node.next();
        AbstractBeginNode exceptionEdge = node.exceptionEdge();
        node.replaceAtUsagesAndDelete(replacement);

        if (next instanceof LoopExitNode) {
            // see LoopExitNode for special case with exception nodes
            BeginNode newNextBegin = add(new BeginNode());
            newNextBegin.setNext(next);
            next = newNextBegin;
        }
        if (exceptionEdge instanceof LoopExitNode) {
            // see LoopExitNode for special case with exception nodes
            BeginNode newExceptionEdgeBegin = add(new BeginNode());
            newExceptionEdgeBegin.setNext(exceptionEdge);
            exceptionEdge = newExceptionEdgeBegin;
        }

        replacement.setNext(next);
        replacement.setExceptionEdge(exceptionEdge);
    }

    @SuppressWarnings("static-method")
    public void addAfterFixed(FixedWithNextNode node, FixedNode newNode) {
        assert node != null && newNode != null && node.isAlive() && newNode.isAlive() : "cannot add " + newNode + " after " + node;
        FixedNode next = node.next();
        node.setNext(newNode);
        if (next != null) {
            assert newNode instanceof FixedWithNextNode : Assertions.errorMessage(node, newNode);
            FixedWithNextNode newFixedWithNext = (FixedWithNextNode) newNode;
            assert newFixedWithNext.next() == null;
            newFixedWithNext.setNext(next);
        }
    }

    @SuppressWarnings("static-method")
    public void addBeforeFixed(FixedNode node, FixedWithNextNode newNode) {
        assert node != null && newNode != null && node.isAlive() && newNode.isAlive() : "cannot add " + newNode + " before " + node;
        assert node.predecessor() != null && node.predecessor() instanceof FixedWithNextNode : "cannot add " + newNode + " before " + node;
        assert newNode.next() == null : newNode;
        assert !(node instanceof AbstractMergeNode) : Assertions.errorMessageContext("node", node);
        FixedWithNextNode pred = (FixedWithNextNode) node.predecessor();
        pred.setNext(newNode);
        newNode.setNext(node);
    }

    public void reduceDegenerateLoopBegin(LoopBeginNode begin) {
        reduceDegenerateLoopBegin(begin, false);
    }

    public void reduceDegenerateLoopBegin(LoopBeginNode begin, boolean forKillCFG) {
        assert begin.loopEnds().isEmpty() : "Loop begin still has backedges";
        if (begin.forwardEndCount() == 1) { // bypass merge and remove
            reduceTrivialMerge(begin, forKillCFG);
        } else { // convert to merge
            AbstractMergeNode merge = this.add(new MergeNode());
            for (EndNode end : begin.forwardEnds()) {
                merge.addForwardEnd(end);
            }
            this.replaceFixedWithFixed(begin, merge);
        }
    }

    public void reduceTrivialMerge(AbstractMergeNode merge) {
        reduceTrivialMerge(merge, false);
    }

    @SuppressWarnings("static-method")
    public void reduceTrivialMerge(AbstractMergeNode merge, boolean forKillCFG) {
        assert merge.forwardEndCount() == 1 : Assertions.errorMessageContext("merge", merge);
        assert !(merge instanceof LoopBeginNode) || ((LoopBeginNode) merge).loopEnds().isEmpty();
        for (PhiNode phi : merge.phis().snapshot()) {
            assert phi.valueCount() == 1 : Assertions.errorMessage(merge, phi);
            ValueNode singleValue = phi.valueAt(0);
            if (phi.hasUsages()) {
                phi.replaceAtUsagesAndDelete(singleValue);
            } else {
                phi.safeDelete();
                if (singleValue != null) {
                    GraphUtil.tryKillUnused(singleValue);
                }
            }
        }
        // remove loop exits
        if (merge instanceof LoopBeginNode) {
            ((LoopBeginNode) merge).removeExits(forKillCFG);
        }
        AbstractEndNode singleEnd = merge.forwardEndAt(0);
        FixedNode sux = merge.next();
        FrameState stateAfter = merge.stateAfter();
        // evacuateGuards
        merge.prepareDelete((FixedNode) singleEnd.predecessor());
        merge.safeDelete();
        if (stateAfter != null) {
            GraphUtil.tryKillUnused(stateAfter);
        }
        if (sux == null) {
            singleEnd.replaceAtPredecessor(null);
            singleEnd.safeDelete();
        } else {
            singleEnd.replaceAndDelete(sux);
        }
    }

    /**
     * Return the {@link ProfileProvider} in use for the graph.
     */
    public ProfileProvider getProfileProvider() {
        return profileProvider;
    }

    /**
     * Returns true if this graph is built without parsing the {@linkplain #method() root method} or
     * if the root method is annotated by {@link Snippet}. This is preferred over querying
     * annotations directly as querying annotations can cause class loading.
     */
    public boolean isSubstitution() {
        return isSubstitution;
    }

    /**
     * Gets the profiling info for the {@linkplain #method() root method} of this graph.
     */
    public ProfilingInfo getProfilingInfo() {
        return getProfilingInfo(method());
    }

    /**
     * Gets the profiling info for a given method that is or will be part of this graph, taking into
     * account the {@link #getProfileProvider()}.
     */
    public ProfilingInfo getProfilingInfo(ResolvedJavaMethod m) {
        if (profileProvider != null && m != null) {
            return profileProvider.getProfilingInfo(m);
        } else {
            return null;
        }
    }

    /**
     * Gets the object for recording assumptions while constructing of this graph.
     *
     * @return {@code null} if assumptions cannot be made for this graph
     */
    public Assumptions getAssumptions() {
        return assumptions;
    }

    /**
     * Returns the AllowAssumptions status for this graph.
     *
     * @return {@code AllowAssumptions.YES} if this graph allows recording assumptions,
     *         {@code AllowAssumptions.NO} otherwise
     */
    public AllowAssumptions allowAssumptions() {
        return AllowAssumptions.ifNonNull(assumptions);
    }

    public void recordAssumptions(StructuredGraph inlineGraph) {
        if (getAssumptions() != null) {
            if (this != inlineGraph && inlineGraph.getAssumptions() != null) {
                getAssumptions().record(inlineGraph.getAssumptions());
            }
        } else {
            assert inlineGraph.getAssumptions() == null : String.format("cannot inline graph (%s) which makes assumptions into a graph (%s) that doesn't", inlineGraph, this);
        }
    }

    /**
     * Checks that any method referenced from a {@link FrameState} is also in the set of methods
     * parsed while building this graph.
     */
    private boolean checkFrameStatesAgainstInlinedMethods() {
        for (FrameState fs : getNodes(FrameState.TYPE)) {
            if (!BytecodeFrame.isPlaceholderBci(fs.bci)) {
                ResolvedJavaMethod m = fs.getCode().getMethod();
                if (!m.equals(rootMethod) && !methods.contains(m)) {
                    SortedSet<String> haystack = new TreeSet<>();
                    if (!methods.contains(rootMethod)) {
                        haystack.add(rootMethod.format("%H.%n(%p)"));
                    }
                    for (ResolvedJavaMethod e : methods) {
                        haystack.add(e.format("%H.%n(%p)"));
                    }
                    throw new AssertionError(String.format("Could not find %s from %s in set(%s)", m.format("%H.%n(%p)"), fs, haystack.stream().collect(Collectors.joining(System.lineSeparator()))));
                }
            }
        }
        return true;
    }

    public boolean isRecordingInlinedMethods() {
        return methods != null;
    }

    /**
     * Gets an unmodifiable view of the methods that were inlined while constructing this graph.
     */
    public List<ResolvedJavaMethod> getMethods() {
        if (methods != null) {
            assert isSubstitution || checkFrameStatesAgainstInlinedMethods();
            return Collections.unmodifiableList(methods);
        }
        return Collections.emptyList();
    }

    /**
     * Records that {@code method} was used to build this graph.
     */
    public void recordMethod(ResolvedJavaMethod method) {
        if (methods != null) {
            methods.add(method);
        }
    }

    /**
     * Updates the {@linkplain #getMethods() methods} used to build this graph with the methods used
     * to build another graph.
     */
    public void updateMethods(StructuredGraph other) {
        if (methods != null) {
            if (other.rootMethod != null) {
                methods.add(other.rootMethod);
            }
            for (ResolvedJavaMethod m : other.methods) {
                methods.add(m);
            }
        }
    }

    /**
     * Gets the input bytecode {@linkplain ResolvedJavaMethod#getCodeSize() size} from which this
     * graph is constructed. This ignores how many bytecodes in each constituent method are actually
     * parsed (which may be none for methods whose IR is retrieved from a cache or less than the
     * full amount for any given method due to profile guided branch pruning).
     */
    public int getBytecodeSize() {
        int res = 0;
        if (rootMethod != null) {
            res += rootMethod.getCodeSize();
        }
        if (methods != null) {
            for (ResolvedJavaMethod e : methods) {
                res += e.getCodeSize();
            }
        }
        return res;
    }

    @Override
    public JavaMethod asJavaMethod() {
        return method();
    }

    public boolean hasUnsafeAccess() {
        return hasUnsafeAccess == UnsafeAccessState.HAS_ACCESS;
    }

    /**
     * Records that this graph encodes a memory access via the {@code Unsafe} class.
     *
     * HotSpot requires this information to modify the behavior of its signal handling for compiled
     * code that contains an unsafe memory access.
     *
     * @param nodeClass the type of the node encoding the unsafe access
     */
    public void markUnsafeAccess(Class<? extends TrackedUnsafeAccess> nodeClass) {
        markUnsafeAccess();
    }

    public void maybeMarkUnsafeAccess(EncodedGraph graph) {
        if (graph.hasUnsafeAccess()) {
            markUnsafeAccess();
        }
    }

    public void maybeMarkUnsafeAccess(StructuredGraph graph) {
        if (graph.hasUnsafeAccess()) {
            markUnsafeAccess();
        }
    }

    private void markUnsafeAccess() {
        if (hasUnsafeAccess == UnsafeAccessState.DISABLED) {
            return;
        }
        hasUnsafeAccess = UnsafeAccessState.HAS_ACCESS;
    }

    public void disableUnsafeAccessTracking() {
        hasUnsafeAccess = UnsafeAccessState.DISABLED;
    }

    public boolean isUnsafeAccessTrackingEnabled() {
        return hasUnsafeAccess != UnsafeAccessState.DISABLED;
    }

    /**
     * For use in tests to clear all stateAfter frame states.
     */
    public void clearAllStateAfterForTestingOnly() {
        graphState.weakenFrameStateVerification(GraphState.FrameStateVerification.NONE);
        for (Node node : getNodes()) {
            if (node instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) node).stateAfter();
                if (stateAfter != null) {
                    assert !(node instanceof ExceptionObjectNode) : "ExceptionObjects cannot have a null FrameState";
                    ((StateSplit) node).setStateAfter(null);
                    // 2 nodes referencing the same frame state
                    if (stateAfter.isAlive()) {
                        GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                    }
                }
            }
        }
        graphState.forceDisableFrameStateVerification();
    }

    public boolean hasVirtualizableAllocation() {
        for (Node n : getNodes()) {
            if (n instanceof VirtualizableAllocation) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void afterRegister(Node node) {
        assert !graphState.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) || !(node instanceof ValueProxyNode) : Assertions.errorMessage(graphState, node);
        if (inliningLog != null && node instanceof Invokable) {
            ((Invokable) node).updateInliningLogAfterRegister(this);
        }
    }

    public OptimizationLog getOptimizationLog() {
        return optimizationLog;
    }

    /**
     * Implementers of this interface should provide <empf>global</empf> profile-related information
     * about the {@link StructuredGraph} i.e. global profiling information about the
     * <empf>compilation unit</empf>.
     *
     * Unlike the {@link ProfileProvider} which represents the profile in isolation and relating to
     * a single method, implementers of this interface provide data in the context of a global
     * system and relating to a compilation unit (including e.g. inlined methods).
     *
     * This source of this information could, for example, be gathered through profiling the
     * application with a sampling based profiler, or just a heuristic-based estimation.
     *
     * This data can be used for aggressive optimizations, applying more or less optimization budget
     * to individual compilation units based on estimates of how much run time is spent in that
     * particular compilation unit.
     */
    public interface GlobalProfileProvider {

        GlobalProfileProvider DEFAULT = new GlobalProfileProvider() {

            public static final int DEFAULT_TIME = -1;

            /**
             * The default time provider always returns -1, i.e. the self time is unknown by
             * default.
             */
            @Override
            public double getGlobalSelfTimePercent() {
                return DEFAULT_TIME;
            }

            /**
             * The default time provider always returns false, i.e. no methods are hot callers by
             * default.
             */
            @Override
            public boolean hotCaller() {
                return false;
            }
        };

        /**
         * This method provides an approximation of what percentage of run time is spent executing
         * this compilation unit (a.k.a. self time - the time spent executing the compilation unit).
         * Since the value is meant to represent a percentage, this method should return a value
         * between 0 and 1 (inclusive) for {@link StructuredGraph graphs} for which the data is
         * available. If no data is available, this method should return -1 as a way to disambiguate
         * compilation units that are known to have a self time of 0 and compilation units for which
         * the data is not present.
         *
         * @return A value between 0 and 1 If self time data is available, -1 otherwise.
         */
        double getGlobalSelfTimePercent();

        /**
         * We define a "hot caller" as any method that is frequently contained in the call stack
         * during execution. Note that "contained in the call stack" means that it is not
         * necessarily on top of the stack.
         *
         * @return Is this {@link StructuredGraph graph} considered a "hot caller".
         */
        boolean hotCaller();

    }

    private GlobalProfileProvider globalProfileProvider = GlobalProfileProvider.DEFAULT;

    /**
     * Set a {@link GlobalProfileProvider global profile provider} for this {@link StructuredGraph
     * graph}.
     */
    public void setGlobalProfileProvider(GlobalProfileProvider globalProfileProvider) {
        Objects.requireNonNull(globalProfileProvider);
        this.globalProfileProvider = globalProfileProvider;
    }

    /**
     * @return The current {@link GlobalProfileProvider} for this graph.
     */
    public GlobalProfileProvider globalProfileProvider() {
        return globalProfileProvider;
    }

    /**
     * Sets the optimization log associated with this graph. The new instance should be bound to
     * this graph and be set up according to the {@link #getOptions() options}.
     *
     * @param newOptimizationLog the optimization log instance
     */
    public void setOptimizationLog(OptimizationLog newOptimizationLog) {
        assert newOptimizationLog != null : "the optimization log must not be null";
        optimizationLog = newOptimizationLog;
    }

    /**
     * Sets the inlining log associated with this graph. The new instance should be {@code null} iff
     * it is expected to be {@code null} according to the {@link #getOptions() options}.
     *
     * @param newInliningLog the new inlining log instance
     */
    public void setInliningLog(InliningLog newInliningLog) {
        assert (inliningLog == null) == (newInliningLog == null) : "the new inlining log must be null iff the previous is null";
        inliningLog = newInliningLog;
    }

    private LoopSafepointVerification safepointVerification;

    @Override
    public boolean verify(boolean verifyInputs) {
        if (verifyGraphEdges) {
            if (safepointVerification == null) {
                safepointVerification = new LoopSafepointVerification();
            }
            assert safepointVerification.verifyLoopSafepoints(this);
        }
        return super.verify(verifyInputs);
    }

}
