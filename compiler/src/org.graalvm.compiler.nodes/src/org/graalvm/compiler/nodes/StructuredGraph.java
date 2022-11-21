/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.CancellationBailoutException;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.ProfileProvider;
import org.graalvm.compiler.nodes.spi.ResolvedJavaMethodProfileProvider;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

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
        private final NodeMap<Block> nodeToBlockMap;
        private final BlockMap<List<Node>> blockToNodesMap;

        public ScheduleResult(ControlFlowGraph cfg, NodeMap<Block> nodeToBlockMap, BlockMap<List<Node>> blockToNodesMap) {
            this.cfg = cfg;
            this.nodeToBlockMap = nodeToBlockMap;
            this.blockToNodesMap = blockToNodesMap;
        }

        public ControlFlowGraph getCFG() {
            return cfg;
        }

        public NodeMap<Block> getNodeToBlockMap() {
            return nodeToBlockMap;
        }

        public BlockMap<List<Node>> getBlockToNodesMap() {
            return blockToNodesMap;
        }

        public List<Node> nodesFor(Block block) {
            return blockToNodesMap.get(block);
        }
    }

    /**
     * Object used to create a {@link StructuredGraph}.
     */
    public static class Builder {
        private String name;
        private final Assumptions assumptions;
        private GraphState graphState;
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

        public String getName() {
            return name;
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

        public DebugContext getDebug() {
            return debug;
        }

        public GraphState getGraphState() {
            return graphState;
        }

        public Builder graphState(GraphState state) {
            this.graphState = state;
            return this;
        }

        public SpeculationLog getSpeculationLog() {
            return speculationLog;
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

        public int getEntryBCI() {
            return entryBCI;
        }

        public Builder entryBCI(int bci) {
            this.entryBCI = bci;
            return this;
        }

        public Builder profileProvider(ProfileProvider provider) {
            this.profileProvider = provider;
            return this;
        }

        public boolean getRecordInlinedMethods() {
            return recordInlinedMethods;
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
            GraphState newGraphState = graphState == null ? GraphState.defaultGraphState() : graphState;
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

    public static final long INVALID_GRAPH_ID = -1;
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

    private ScheduleResult lastSchedule;

    private final InliningLog inliningLog;

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

    private enum UnsafeAccessState {
        NO_ACCESS,
        HAS_ACCESS,
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
        assert checkIsSubstitutionInvariants(method, isSubstitution);
        this.cancellable = cancellable;
        this.inliningLog = GraalOptions.TraceInlining.getValue(options) || OptimizationLog.isOptimizationLogEnabled(options) ? new InliningLog(rootMethod) : null;
        this.callerContext = context;
        this.optimizationLog = OptimizationLog.getInstance(this);
    }

    private static boolean checkIsSubstitutionInvariants(ResolvedJavaMethod method, boolean isSubstitution) {
        if (!IS_IN_NATIVE_IMAGE && !IS_BUILDING_NATIVE_IMAGE) {
            if (method != null) {
                if (method.getAnnotation(Snippet.class) != null) {
                    assert isSubstitution : "Graph for method " + method.format("%H.%n(%p)") +
                                    " annotated by " + Snippet.class.getName() +
                                    " must have its `isSubstitution` field set to true";
                }
            }
        }
        return true;
    }

    public void setLastSchedule(ScheduleResult result) {
        lastSchedule = result;
    }

    public ScheduleResult getLastSchedule() {
        return lastSchedule;
    }

    public void clearLastSchedule() {
        setLastSchedule(null);
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
            return inliningLog.removeLeafCallsite((Invokable) node);
        }
        return null;
    }

    @Override
    protected void afterNodeIdChange(Node node, Object value) {
        if (inliningLog != null && node instanceof Invokable) {
            inliningLog.addLeafCallsite((Invokable) node, (InliningLog.Callsite) value);
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

    public Stamp getReturnStamp() {
        Stamp returnStamp = null;
        for (ReturnNode returnNode : getNodes(ReturnNode.TYPE)) {
            ValueNode result = returnNode.result();
            if (result != null) {
                if (returnStamp == null) {
                    returnStamp = result.stamp(NodeView.DEFAULT);
                } else {
                    returnStamp = returnStamp.meet(result.stamp(NodeView.DEFAULT));
                }
            }
        }
        return returnStamp;
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
     * @param replacements the node replacement map used by inlining. Must be non-null if
     *            {@code positive == true}, ignored if {@code positive == false}.
     * @param calleeLog the inlining log of the inlined graph. Must be non-null if
     *            {@code positive == true}, ignored if {@code positive == false}.
     * @param reason format string that along with {@code args} provides the reason for decision
     */
    public void notifyInliningDecision(Invokable invoke, boolean positive, String phase, EconomicMap<Node, Node> replacements, InliningLog calleeLog, String reason, Object... args) {
        if (inliningLog != null) {
            inliningLog.addDecision(invoke, positive, phase, replacements, calleeLog, reason, args);
        }
        if (getDebug().hasCompilationListener()) {
            String message = String.format(reason, args);
            getDebug().notifyInlining(invoke.getContextMethod(), invoke.getTargetMethod(), positive, message, invoke.bci());
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

    @SuppressWarnings("try")
    private StructuredGraph copy(String newName, ResolvedJavaMethod rootMethodForCopy, OptionValues optionsForCopy, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback,
                    CompilationIdentifier newCompilationId, DebugContext debugForCopy, boolean trackNodeSourcePositionForCopy) {
        AllowAssumptions allowAssumptions = allowAssumptions();
        StructuredGraph copy = new StructuredGraph(newName,
                        rootMethodForCopy,
                        entryBCI,
                        assumptions == null ? null : new Assumptions(),
                        profileProvider,
                        graphState.copy(),
                        isSubstitution,
                        methods != null ? new ArrayList<>(methods) : null,
                        trackNodeSourcePositionForCopy,
                        newCompilationId,
                        optionsForCopy, debugForCopy, null, callerContext);
        if (allowAssumptions == AllowAssumptions.YES && assumptions != null) {
            copy.assumptions.record(assumptions);
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
            assert newNode instanceof FixedWithNextNode;
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
        assert !(node instanceof AbstractMergeNode);
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
        assert merge.forwardEndCount() == 1;
        assert !(merge instanceof LoopBeginNode) || ((LoopBeginNode) merge).loopEnds().isEmpty();
        for (PhiNode phi : merge.phis().snapshot()) {
            assert phi.valueCount() == 1;
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

    public void markUnsafeAccess() {
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
        assert !graphState.isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) || !(node instanceof ValueProxyNode);
        if (inliningLog != null && node instanceof Invokable) {
            ((Invokable) node).updateInliningLogAfterRegister(this);
        }
    }

    public NodeSourcePosition getCallerContext() {
        return callerContext;
    }

    public OptimizationLog getOptimizationLog() {
        return optimizationLog;
    }
}
