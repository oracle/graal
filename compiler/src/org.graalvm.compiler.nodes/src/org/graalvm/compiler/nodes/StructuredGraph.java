/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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
import org.graalvm.compiler.api.replacements.MethodSubstitution;
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
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.VirtualizableAllocation;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * A graph that contains at least one distinguished node : the {@link #start() start} node. This
 * node is the start of the control flow of the graph.
 */
public final class StructuredGraph extends Graph implements JavaMethodContext {

    /**
     * The different stages of the compilation of a {@link Graph} regarding the status of
     * {@link GuardNode}s, {@link DeoptimizingNode}s and {@link FrameState}s. The stage of a graph
     * progresses monotonously.
     */
    public enum GuardsStage {
        /**
         * During this stage, there can be {@link FloatingNode floating} {@link DeoptimizingNode}s
         * such as {@link GuardNode}s. New {@link DeoptimizingNode}s can be introduced without
         * constraints. {@link FrameState}s are associated with {@link StateSplit} nodes.
         */
        FLOATING_GUARDS,
        /**
         * During this stage, all {@link DeoptimizingNode}s must be {@link FixedNode fixed} but new
         * {@link DeoptimizingNode}s can still be introduced. {@link FrameState}s are still
         * associated with {@link StateSplit} nodes.
         */
        FIXED_DEOPTS,
        /**
         * During this stage, all {@link DeoptimizingNode}s must be {@link FixedNode fixed}. New
         * {@link DeoptimizingNode}s cannot be introduced. {@link FrameState}s are now associated
         * with {@link DeoptimizingNode}s.
         */
        AFTER_FSA;

        public boolean allowsFloatingGuards() {
            return this == FLOATING_GUARDS;
        }

        public boolean allowsGuardInsertion() {
            return this.ordinal() <= FIXED_DEOPTS.ordinal();
        }

        public boolean areFrameStatesAtDeopts() {
            return this == AFTER_FSA;
        }

        public boolean areFrameStatesAtSideEffects() {
            return !this.areFrameStatesAtDeopts();
        }

        public boolean areDeoptsFixed() {
            return this.ordinal() >= FIXED_DEOPTS.ordinal();
        }

        public boolean requiresValueProxies() {
            return this != AFTER_FSA;
        }

        public boolean reachedGuardsStage(GuardsStage stage) {
            return this.ordinal() >= stage.ordinal();
        }
    }

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

    /**
     * Different stages of the compilation regarding the status of various graph properties.
     */
    public enum StageFlag {
        PARTIAL_ESCAPE,
        HIGH_TIER_LOWERING,
        FLOATING_READS,
        GUARD_MOVEMENT,
        VALUE_PROXY_REMOVAL,
        MID_TIER_LOWERING,
        LOW_TIER_LOWERING,
        EXPAND_LOGIC,
        FIXED_READS,
        FINAL_CANONICALIZATION
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
        private SpeculationLog speculationLog;
        private ResolvedJavaMethod rootMethod;
        private CompilationIdentifier compilationId = CompilationIdentifier.INVALID_COMPILATION_ID;
        private int entryBCI = JVMCICompiler.INVOCATION_ENTRY_BCI;
        private boolean useProfilingInfo = true;
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

        public boolean getUseProfilingInfo() {
            return useProfilingInfo;
        }

        public Builder useProfilingInfo(boolean flag) {
            this.useProfilingInfo = flag;
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
            List<ResolvedJavaMethod> inlinedMethods = recordInlinedMethods ? new ArrayList<>() : null;
            // @formatter:off
            return new StructuredGraph(name,
                            rootMethod,
                            entryBCI,
                            assumptions,
                            speculationLog,
                            useProfilingInfo,
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
    private ResolvedJavaMethod rootMethod;
    private final long graphId;
    private final CompilationIdentifier compilationId;
    private final int entryBCI;
    private GuardsStage guardsStage = GuardsStage.FLOATING_GUARDS;
    private EnumSet<StageFlag> stageFlags = EnumSet.noneOf(StageFlag.class);
    private FrameStateVerification frameStateVerification;
    /** Flag to indicate {@link #clearAllStateAfterForTestingOnly()} was called. */
    private boolean stateAfterClearedForTesting = false;

    /**
     * Different node types verified during {@linkplain FrameStateVerification}. See
     * {@linkplain FrameStateVerification} for details.
     */
    public enum FrameStateVerificationFeature {
        STATE_SPLITS,
        MERGES,
        LOOP_BEGINS,
        LOOP_EXITS
    }

    /**
     * The different stages of the compilation of a {@link Graph} regarding the status of
     * {@linkplain FrameState} verification of {@linkplain AbstractStateSplit} state after.
     * Verification starts with the mode {@linkplain FrameStateVerification#ALL}, i.e., all state
     * splits with side-effects, merges and loop exits need a proper state after. The verification
     * mode progresses monotonously until the {@linkplain FrameStateVerification#NONE} mode is
     * reached. From there on, no further {@linkplain AbstractStateSplit#stateAfter} verification
     * happens.
     */
    public enum FrameStateVerification {
        /**
         * Verify all {@linkplain AbstractStateSplit} nodes that return {@code true} for
         * {@linkplain AbstractStateSplit#hasSideEffect()} have a
         * {@linkplain AbstractStateSplit#stateAfter} assigned. Additionally, verify
         * {@linkplain LoopExitNode} and {@linkplain AbstractMergeNode} have a valid
         * {@linkplain AbstractStateSplit#stateAfter}. This is necessary to avoid missing
         * {@linkplain FrameState} after optimizations. See {@link GraphUtil#mayRemoveSplit} for
         * more details.
         *
         * This stage is the initial verification stage for every graph.
         */
        ALL(EnumSet.allOf(FrameStateVerificationFeature.class)),
        /**
         * Same as {@linkplain #ALL} except that {@linkplain LoopExitNode} nodes are no longer
         * verified.
         */
        ALL_EXCEPT_LOOP_EXIT(EnumSet.complementOf(EnumSet.of(FrameStateVerificationFeature.LOOP_EXITS))),
        /**
         * Same as {@linkplain #ALL_EXCEPT_LOOP_EXIT} except that {@linkplain LoopBeginNode} are no
         * longer verified.
         */
        ALL_EXCEPT_LOOPS(EnumSet.complementOf(EnumSet.of(FrameStateVerificationFeature.LOOP_BEGINS, FrameStateVerificationFeature.LOOP_EXITS))),
        /**
         * Verification is disabled. Typically used after assigning {@linkplain FrameState} to
         * {@linkplain DeoptimizeNode} or for {@linkplain Snippet} compilations.
         */
        NONE(EnumSet.noneOf(FrameStateVerificationFeature.class));

        private EnumSet<FrameStateVerificationFeature> features;

        FrameStateVerification(EnumSet<FrameStateVerificationFeature> features) {
            this.features = features;
        }

        /**
         * Determines if the current verification mode implies this feature.
         *
         * @param feature the other verification feature to check
         * @return {@code true} if this verification mode implies the feature, {@code false}
         *         otherwise
         */
        boolean implies(FrameStateVerificationFeature feature) {
            return this.features.contains(feature);
        }

    }

    private final boolean useProfilingInfo;
    private final Cancellable cancellable;
    private final boolean isSubstitution;

    /**
     * The assumptions made while constructing and transforming this graph.
     */
    private final Assumptions assumptions;

    private SpeculationLog speculationLog;

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

    private StructuredGraph(String name,
                    ResolvedJavaMethod method,
                    int entryBCI,
                    Assumptions assumptions,
                    SpeculationLog speculationLog,
                    boolean useProfilingInfo,
                    boolean isSubstitution,
                    List<ResolvedJavaMethod> methods,
                    boolean trackNodeSourcePosition,
                    CompilationIdentifier compilationId,
                    OptionValues options,
                    DebugContext debug,
                    Cancellable cancellable,
                    NodeSourcePosition context) {
        super(name, options, debug, trackNodeSourcePosition);
        this.setStart(add(new StartNode()));
        this.rootMethod = method;
        this.graphId = uniqueGraphIds.incrementAndGet();
        this.compilationId = compilationId;
        this.entryBCI = entryBCI;
        this.assumptions = assumptions;
        this.methods = methods;
        this.speculationLog = speculationLog;
        this.useProfilingInfo = useProfilingInfo;
        this.isSubstitution = isSubstitution;
        assert checkIsSubstitutionInvariants(method, isSubstitution);
        this.cancellable = cancellable;
        this.inliningLog = new InliningLog(rootMethod, GraalOptions.TraceInlining.getValue(options), debug);
        this.callerContext = context;
        this.frameStateVerification = isSubstitution ? FrameStateVerification.NONE : FrameStateVerification.ALL;
    }

    private static boolean checkIsSubstitutionInvariants(ResolvedJavaMethod method, boolean isSubstitution) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (method != null) {
                if (method.getAnnotation(Snippet.class) != null || method.getAnnotation(MethodSubstitution.class) != null) {
                    assert isSubstitution : "Graph for method " + method.format("%H.%n(%p)") +
                                    " annotated by " + Snippet.class.getName() + " or " +
                                    MethodSubstitution.class.getName() +
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
        properties.put("modificationCount", getModificationCount());
        properties.put("assumptions", String.valueOf(getAssumptions()));
    }

    @Override
    public void beforeNodeDuplication(Graph sourceGraph) {
        super.beforeNodeDuplication(sourceGraph);
        recordAssumptions((StructuredGraph) sourceGraph);
    }

    @Override
    protected Object beforeNodeIdChange(Node node) {
        if (node instanceof Invokable) {
            return inliningLog.removeLeafCallsite((Invokable) node);
        }
        return null;
    }

    @Override
    protected void afterNodeIdChange(Node node, Object value) {
        if (node instanceof Invokable) {
            inliningLog.addLeafCallsite((Invokable) node, (InliningLog.Callsite) value);
        }
    }

    @Override
    public boolean maybeCompress() {
        if (super.maybeCompress()) {
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

    public InliningLog getInliningLog() {
        return inliningLog;
    }

    public void logInliningTree() {
        if (GraalOptions.TraceInlining.getValue(getOptions())) {
            String formattedTree = getInliningLog().formatAsTree(true);
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
                        speculationLog,
                        useProfilingInfo,
                        isSubstitution,
                        methods != null ? new ArrayList<>(methods) : null,
                        trackNodeSourcePositionForCopy,
                        newCompilationId,
                        optionsForCopy, debugForCopy, null, callerContext);
        if (allowAssumptions == AllowAssumptions.YES && assumptions != null) {
            copy.assumptions.record(assumptions);
        }
        copy.hasUnsafeAccess = hasUnsafeAccess;
        copy.setGuardsStage(getGuardsStage());
        copy.stageFlags = EnumSet.copyOf(stageFlags);
        copy.trackNodeSourcePosition = trackNodeSourcePositionForCopy;
        EconomicMap<Node, Node> replacements = EconomicMap.create(Equivalence.IDENTITY);
        replacements.put(start, copy.start);
        UnmodifiableEconomicMap<Node, Node> duplicates;
        try (InliningLog.UpdateScope scope = copy.getInliningLog().openDefaultUpdateScope()) {
            duplicates = copy.addDuplicates(getNodes(), this, this.getNodeCount(), replacements);
            if (scope != null) {
                copy.getInliningLog().replaceLog(duplicates, this.getInliningLog());
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
        return new Iterable<Invoke>() {

            private Invoke next;

            @Override
            public Iterator<Invoke> iterator() {
                return new Iterator<Invoke>() {

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
        assert begin.loopEnds().isEmpty() : "Loop begin still has backedges";
        if (begin.forwardEndCount() == 1) { // bypass merge and remove
            reduceTrivialMerge(begin);
        } else { // convert to merge
            AbstractMergeNode merge = this.add(new MergeNode());
            for (EndNode end : begin.forwardEnds()) {
                merge.addForwardEnd(end);
            }
            this.replaceFixedWithFixed(begin, merge);
        }
    }

    @SuppressWarnings("static-method")
    public void reduceTrivialMerge(AbstractMergeNode merge) {
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
            ((LoopBeginNode) merge).removeExits();
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

    public GuardsStage getGuardsStage() {
        return guardsStage;
    }

    public void setGuardsStage(GuardsStage guardsStage) {
        assert guardsStage.ordinal() >= this.guardsStage.ordinal();
        this.guardsStage = guardsStage;
    }

    public boolean isAfterStage(StageFlag state) {
        return stageFlags.contains(state);
    }

    public boolean isBeforeStage(StageFlag state) {
        return !isAfterStage(state);
    }

    public void setAfterStage(StageFlag state) {
        assert isBeforeStage(state) : "Cannot set after state " + state + " since the graph is already in that state";
        stageFlags.add(state);
    }

    public EnumSet<StageFlag> getStageFlags() {
        return stageFlags;
    }

    /**
     * Determines if {@link ProfilingInfo} is used during construction of this graph.
     */
    public boolean useProfilingInfo() {
        return useProfilingInfo;
    }

    /**
     * Returns true if this graph is built without parsing the {@linkplain #method() root method} or
     * if the root method is annotated by {@link Snippet} or {@link MethodSubstitution}. This is
     * preferred over querying annotations directly as querying annotations can cause class loading.
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
     * account {@link #useProfilingInfo()}.
     */
    public ProfilingInfo getProfilingInfo(ResolvedJavaMethod m) {
        if (useProfilingInfo && m != null) {
            return m.getProfilingInfo();
        } else {
            return DefaultProfilingInfo.get(TriState.UNKNOWN);
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
                ResolvedJavaMethod m = fs.code.getMethod();
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

    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    public FrameStateVerification getFrameStateVerification() {
        return frameStateVerification;
    }

    public boolean isStateAfterClearedForTesting() {
        return stateAfterClearedForTesting;
    }

    public void weakenFrameStateVerification(FrameStateVerification newFrameStateVerification) {
        if (frameStateVerification == FrameStateVerification.NONE) {
            return;
        }
        if (newFrameStateVerification == FrameStateVerification.NONE) {
            /*
             * Unit tests and substitution graphs can disable verification early on in the
             * compilation pipeline.
             */
            frameStateVerification = FrameStateVerification.NONE;
            return;
        }
        assert frameStateVerification.ordinal() < newFrameStateVerification.ordinal() : "Old verification " + frameStateVerification + " must imply new verification " + newFrameStateVerification +
                        ", i.e., verification can only be relaxed over the course of compilation";
        frameStateVerification = newFrameStateVerification;
    }

    public void disableFrameStateVerification() {
        weakenFrameStateVerification(FrameStateVerification.NONE);
    }

    /**
     * For use in tests to clear all stateAfter frame states.
     */
    public void clearAllStateAfterForTestingOnly() {
        stateAfterClearedForTesting = true;
        weakenFrameStateVerification(FrameStateVerification.NONE);
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
        assert !isAfterStage(StageFlag.VALUE_PROXY_REMOVAL) || !(node instanceof ValueProxyNode);
        if (GraalOptions.TraceInlining.getValue(getOptions())) {
            if (node instanceof Invokable) {
                ((Invokable) node).updateInliningLogAfterRegister(this);
            }
        }
    }

    public NodeSourcePosition getCallerContext() {
        return callerContext;
    }
}
