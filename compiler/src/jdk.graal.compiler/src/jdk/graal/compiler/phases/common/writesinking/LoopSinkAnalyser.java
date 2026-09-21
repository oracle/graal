/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common.writesinking;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteClosure;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheData;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheEntry;
import static jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.DataAction.unit;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteMergeProcessor;
import jdk.graal.compiler.phases.common.writesinking.data.WritesOrdering;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.CandidateRejection;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.MethodMetrics;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator;

/**
 * A utility class used to obtain information pertaining to write sinking capabilities of loops in
 * the graph. Each loop is traversed to obtain this data.
 * <p>
 * See the documentation for {@link LoopAnalysisResult} for more details about what data is
 * collected.
 * <p>
 * The algorithm for obtaining which writes will sink is very simple. For each write in the loop:
 * <ul>
 * <li>If all loop ends' states contains a write to the same address, this write is likely to be
 * sinkable.</li>
 * <li>But, if at any point in the loop the {@code LocationIdentity location} corresponding to this
 * write is {@code MemoryKill killed}, then this write will be unsinkable.</li>
 * </ul>
 */
public class LoopSinkAnalyser {
    final CoreProviders context;
    final ControlFlowGraph cfg;
    private final LoopsData loopsData;
    final boolean allowModifyGraph;
    private final MethodMetrics diagnostics;

    final EconomicMap<HIRBlock, LoopAnalysisResult> knownLoopAnalysisResults = EconomicMap.create();

    /**
     * Creates an analyser over {@code cfg}. When {@code allowModifyGraph} is true, analysis may
     * materialize helper values needed by later sinking.
     */
    public LoopSinkAnalyser(CoreProviders context, ControlFlowGraph cfg, boolean allowModifyGraph) {
        this(context, cfg, context.getLoopsDataProvider().getLoopsData(cfg), allowModifyGraph, WriteSinkingDiagnostics.disabledMethodMetrics());
    }

    /**
     * Creates an analyser over {@code cfg}, recording diagnostics in {@code diagnostics}.
     */
    public LoopSinkAnalyser(CoreProviders context, ControlFlowGraph cfg, LoopsData loopsData, boolean allowModifyGraph, MethodMetrics diagnostics) {
        this.context = context;
        this.cfg = cfg;
        this.loopsData = loopsData;
        this.allowModifyGraph = allowModifyGraph;
        this.diagnostics = diagnostics;
    }

    /**
     * The entry point for performing the write sinking analysis. Feed a loop to it, and it will
     * return the corresponding {@link LoopAnalysisResult}. Furthermore, this will also compute the
     * result for all inner loops, and subsequent analysis requests for said inner loops will hit the
     * result cache and complete immediately.
     * <p>
     * For any graph, this algorithm will complete in {@code O(D x N)}, where {@code D} is the
     * maximum nesting depth of the graph, and {@code N} is the number of nodes in the graph.
     */
    @SuppressWarnings("try")
    public LoopAnalysisResult analyze(CFGLoop<HIRBlock> loop, MethodFilter excludeFieldsFilter) {
        DebugContext debug = loop.getHeader().getBeginNode().graph().getDebug();
        try (DebugContext.Scope s = debug.scope("LoopSinkAnalyser")) {
            debug.log(DebugContext.VERBOSE_LEVEL, "Started loop sink analysis for loop \"%s\"", loop);

            if (knownLoopAnalysisResults.containsKey(loop.getHeader())) {
                debug.log(DebugContext.VERBOSE_LEVEL, "Finished loop sink analysis for loop \"%s\", cache hit with loop analysis result \"%s\"", loop, knownLoopAnalysisResults.get(loop.getHeader()));
                return knownLoopAnalysisResults.get(loop.getHeader());
            }
            LoopAnalysisResult result;

            if (killsAny(loop)) {
                // No need to analyze a loop that kills any: no write can sink.
                result = LoopAnalysisResult.KILLS_ANY;
            } else {
                result = new Closure(loop, context, excludeFieldsFilter, diagnostics).analyze();
            }
            knownLoopAnalysisResults.put(loop.getHeader(), result);

            debug.log(DebugContext.VERBOSE_LEVEL, "Finished loop sink analysis for loop \"%s\", computed loop analysis result \"%s\"", loop, result);

            return result;
        }
    }

    /**
     * Returns whether the loop contains a global memory kill or forced commit point that prevents
     * all movable writes from sinking through the loop.
     */
    private static boolean killsAny(CFGLoop<HIRBlock> loop) {
        for (HIRBlock b : loop.getBlocks()) {
            for (FixedNode n : b.getNodes()) {
                if (MemoryKill.isSingleMemoryKill(n)) {
                    if (((SingleMemoryKill) n).getKilledLocationIdentity().isAny()) {
                        return true;
                    }
                }
                if (MemoryKill.isMultiMemoryKill(n)) {
                    for (LocationIdentity id : ((MultiMemoryKill) n).getKilledLocationIdentities()) {
                        if (id.isAny()) {
                            return true;
                        }
                    }
                }
                if (WriteSinkingUtil.isForcedCommitPoint(n)) {
                    // We expect control sinks to be preceded by loops exits.
                    return true;
                }
            }
        }
        return false;
    }

    static class BlockState extends MovableWriteMergeProcessor.DefaultProcessableState {
        BlockState() {
        }

        BlockState(BlockState other) {
            super(other.getMovableWrites().cloneState());
        }
    }

    /**
     * Final result for the write-sinking analysis of one loop. This information is collected
     * locally for the loop, and is strictly independent of all that is outside of the loop.
     * <p>
     * The collected data stored in this class are:
     * <ul>
     * <li>The set of {@code WriteNode writes} that are able to sink through the loop.</li>
     * <li>A list of {@code ValuePhiNode} that will need to be added to the graph in order to
     * support sinking said writes.</li>
     * <li>The set of {@code LocationIdentity} that are killed in the loop, whether through actual
     * nodes in the graph, or because of state merging issues w.r.t. write sinking, and should only
     * be used in the context of write sinking.</li>
     * <li>The {@link MovableWriteState state} that was collected. This is to be re-used at a later
     * point to, once again, avoid combinatorial explosion.</li>
     * </ul>
     */
    public static final class LoopAnalysisResult {
        private static final LoopAnalysisResult EMPTY = new LoopAnalysisResult(false);
        private static final LoopAnalysisResult KILLS_ANY = new LoopAnalysisResult(true);

        final MovableWriteState data;
        final EconomicSet<WriteNode> sinkableWrites = EconomicSet.create();
        final EconomicSet<LocationIdentity> killedIdentities = EconomicSet.create();

        private LoopAnalysisResult(boolean killsAny) {
            data = new MovableWriteState();
            if (killsAny) {
                killedIdentities.add(LocationIdentity.any());
            }
        }

        LoopAnalysisResult(MovableWriteState state, Closure closure) {
            this.data = state.cloneState();
            for (LocationIdentity id : data.getLocations()) {
                EconomicSet<WriteNode> sinkable = closure.sinkableNodes.get(id);
                if (sinkable != null) {
                    sinkableWrites.addAll(sinkable);
                }
            }
            killedIdentities.addAll(closure.killedIdentities);
            assert validState();
        }

        /**
         * Builds the final immutable analysis result from the merged block state and side data
         * collected by the closure.
         */
        private static LoopAnalysisResult getLoopAnalysisResult(Closure.MergeProcessor processor, BlockState mergedState, Closure closure) {
            if (closure.kills(LocationIdentity.any())) {
                assert mergedState.getMovableWrites().isEmpty() && closure.sinkableNodes.isEmpty() : "A loops that kills any cannot have sinkable nodes.";
                return KILLS_ANY;
            }
            if (mergedState.getMovableWrites().isEmpty() &&
                            // Need to propagate killed identities for outer loops.
                            closure.killedIdentities.isEmpty()) {
                assert closure.sinkableNodes.isEmpty() : "Invariant: empty states means no write can sink.";
                assert processor.getConditionals().isEmpty() : "Conditionals must be empty " + processor.getConditionals().isEmpty();
                return EMPTY;
            }
            return new LoopAnalysisResult(mergedState.getMovableWrites(), closure);
        }

        /**
         * Verifies that no write is reported sinkable for a location killed by the same loop.
         */
        private boolean validState() {
            for (WriteNode write : sinkableWrites) {
                assert !write.getKilledLocationIdentity().isAny() : "Must not kill any " + write + " sinkable=" + sinkableWrites;
                assert !kills(write.getKilledLocationIdentity()) : "Writes associated with a location killed in the loop should not be sinkable.";
            }
            return true;
        }

        /**
         * Returns writes that can be removed from this loop and committed at loop exits.
         */
        public UnmodifiableEconomicSet<WriteNode> getSinkableWrites() {
            return sinkableWrites;
        }

        /**
         * Returns {@code true} when this loop contains a global kill or forced commit point that
         * prevents all writes from sinking through it.
         */
        public boolean killsAny() {
            return kills(LocationIdentity.any());
        }

        /**
         * Returns whether this loop-analysis result blocks writes for {@code id}.
         */
        private boolean kills(LocationIdentity id) {
            return killedIdentities.contains(id) || killedIdentities.contains(LocationIdentity.any());
        }

        /**
         * Given an initial state, this method injects the data collected during pre-processing into
         * a new state. This new state can then be used for continuing processing of a loop.
         */
        public <T extends MovableWriteMergeProcessor.DefaultProcessableState> void inheritInto(T initialState, T result, Graph graph) {

            // Start interpretation of the pre-processing.
            MovableWriteState preProcessing = data;
            for (LocationIdentity id : preProcessing.getLocations()) {
                WritesOrdering preProcessOrder = preProcessing.getOrdering(id);
                WritesOrdering thisOrder = initialState.getLocationOrder(id);
                if (thisOrder == null || !thisOrder.agreesWith(preProcessOrder)) {
                    // Prevent conflicting orders from flowing through. Prefer keeping the
                    // conditionally sinking writes.
                    initialState.getMovableWrites().killWritesUntil(id, null, unit);
                }
                // Touch up the state to inherit data from the pre-processing
                for (CacheEntry key : preProcessOrder) {
                    CacheData preProcessingData = preProcessing.getCacheData(key);
                    assert preProcessingData.isConditional() : "Must be conditional " + preProcessingData;
                    if (initialState.containsEntry(key)) {
                        CacheData thisData = initialState.getCacheData(key);
                        if (preProcessingData.value instanceof ValuePhiNode phiValue) {
                            ValueNode[] values = phiValue.values().toArray(ValueNode.EMPTY_ARRAY);
                            values[0] = thisData.value;
                            ValuePhiNode newPhi = new ValuePhiNode(phiValue.stamp(NodeView.DEFAULT), phiValue.merge(), values);
                            preProcessingData = new CacheData(newPhi, preProcessingData.barrier);
                        }
                    }
                    if (graph != null && !preProcessingData.value.isAlive()) {
                        preProcessingData = new CacheData(graph.addOrUnique(preProcessingData.value), preProcessingData.barrier, preProcessingData.conditionalMerge);
                    }
                    result.getMovableWrites().addEntry(graph, key, preProcessingData);
                }
            }

            // Add the writes that can go over the loop
            for (LocationIdentity id : initialState.getLocations()) {
                if (!preProcessing.containsLocation(id) && !kills(id)) {
                    for (CacheEntry key : initialState.getLocationOrder(id)) {
                        result.getMovableWrites().addEntry(graph, key, initialState.getCacheData(key));
                    }
                }
            }
        }

        /**
         * Returns a debug representation of the collected loop analysis result.
         */
        @Override
        public String toString() {
            return "LoopAnalysisResult{" +
                            "data=" + data +
                            ", sinkableWrites=" + sinkableWrites +
                            ", killedIdentities=" + killedIdentities +
                            '}';
        }
    }

    public final class Closure extends MovableWriteClosure<BlockState> {
        /**
         * Loop whose body this closure analyzes for sinkable writes.
         */
        private final CFGLoop<HIRBlock> loop;
        /**
         * High-level loop view used to classify values as loop-local or loop-external.
         */
        private final Loop loopEx;

        /**
         * Fixed loop-begin node for {@link #loop}.
         */
        private final LoopBeginNode start;

        /**
         * Location identities killed while processing the loop, including locations killed by failed
         * merge compatibility checks.
         */
        final EconomicSet<LocationIdentity> killedIdentities = EconomicSet.create();

        /**
         * Writes proven sinkable through this loop, grouped by killed location identity.
         */
        final EconomicMap<LocationIdentity, EconomicSet<WriteNode>> sinkableNodes = EconomicMap.create();

        private Closure(CFGLoop<HIRBlock> loop, CoreProviders context, MethodFilter excludeFieldsFilter, MethodMetrics diagnostics) {
            super(context, excludeFieldsFilter, TraversalMode.ANALYSIS, diagnostics);
            this.loop = loop;
            this.loopEx = loopsData.loop(loop);
            this.start = (LoopBeginNode) loop.getHeader().getBeginNode();
        }

        /**
         * Computes the write-sinking state for this closure's loop.
         */
        public LoopAnalysisResult analyze() {
            BlockState initialState = getInitialState();

            ReentrantBlockIterator.LoopInfo<BlockState> loopInfo = ReentrantBlockIterator.processLoop(this, loop, initialState);
            List<BlockState> states = loopInfo.endStates;
            states.add(0, getInitialState());
            MergeProcessor processor = new MergeProcessor(new BlockState(), (AbstractMergeNode) loop.getHeader().getBeginNode());
            BlockState mergedState = processor.mergeForState(states);
            return LoopAnalysisResult.getLoopAnalysisResult(processor, mergedState, this);
        }

        @Override
        protected BlockState getInitialState() {
            return new BlockState();
        }

        @Override
        protected BlockState merge(HIRBlock merge, List<BlockState> states) {
            MergeProcessor processor = new MergeProcessor(new BlockState(), (AbstractMergeNode) merge.getBeginNode());
            return processor.mergeForState(states);
        }

        @Override
        protected BlockState cloneState(BlockState oldState) {
            return new BlockState(oldState);
        }

        @Override
        protected boolean registerWrite(BlockState state, WriteNode write, CacheEntry identifier) {
            DebugContext debug = write.getDebug();
            if (kills(identifier.identity)) {
                // Prevent writes whose location is killed to sink.
                return false;
            }
            if (writeAddressDependsOnLoop(identifier)) {
                // Prevent sinking writes whose address is computed in the loop.
                debug.log(DebugContext.VERBOSE_LEVEL, "Write \"%s\" is not deferrable due to having a loop dependent address!", write);
                diagnostics.recordCandidateRejected(loop, identifier, CandidateRejection.LOOP_DEPENDENT_ADDRESS);
                return false;
            }

            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Write \"%s\" with identifier \"%s\" is deferrable", write, identifier);

            registerSinkable(write, identifier);
            diagnostics.recordSinkableWrite(loop, write);
            state.getMovableWrites().addEntry(identifier, write.value(), write);
            return true;
        }

        @Override
        protected void processOther(BlockState state, FixedNode node) {
            // Nothing to do
        }

        /**
         * Returns whether the write key's base value is produced inside the analyzed loop.
         */
        private boolean writeAddressDependsOnLoop(CacheEntry key) {
            assert GraphUtil.assertIsConstant(key.offset);
            return !key.base.isConstant() && valueDependsOnLoop(key.base);
        }

        /**
         * Returns whether {@code value} is defined inside the analyzed loop.
         */
        private boolean valueDependsOnLoop(ValueNode value) {
            return !loopEx.isOutsideLoop(value);
        }

        /**
         * Records {@code write} as sinkable under its killed location identity.
         */
        private void registerSinkable(WriteNode write, CacheEntry identifier) {
            EconomicSet<WriteNode> set = sinkableNodes.get(identifier.identity);
            if (set == null) {
                set = EconomicSet.create();
                sinkableNodes.put(identifier.identity, set);
            }
            set.add(write);
        }

        /**
         * Returns whether the current analysis closure has seen a kill that blocks {@code id}.
         */
        private boolean kills(LocationIdentity id) {
            return killedIdentities.contains(id) || killedIdentities.contains(LocationIdentity.any());
        }

        /**
         * Records a killed identity and removes any sinkable writes whose location can no longer
         * pass through the loop.
         */
        private void addKill(LocationIdentity id) {
            if (killedIdentities.contains(LocationIdentity.any())) {
                return;
            }
            if (id.isAny()) {
                // Killing any prevents any write from sinking through the loop.
                sinkableNodes.clear();
                killedIdentities.clear();
            }
            // Prevents writes associated with the location to sink through the loop.
            sinkableNodes.removeKey(id);
            killedIdentities.add(id);
        }

        /**
         * Records all killed identities from nested analysis or merge processing.
         */
        private void addKillAll(EconomicSet<LocationIdentity> ids) {
            if (kills(LocationIdentity.any())) {
                return;
            }
            if (ids.contains(LocationIdentity.any())) {
                addKill(LocationIdentity.any());
                return;
            }
            for (LocationIdentity id : ids) {
                addKill(id);
            }
        }

        /**
         * If anything happens in the loop body that would trigger the committing of a write that we
         * are trying to sink, it means that we would never be able to get the write out of the
         * loop.
         *
         * In particular, any killed location in a loop body prevents any write that corresponds to
         * that location to sink.
         */
        @Override
        protected int killWritesUntil(BlockState state, LocationIdentity id, CacheEntry key, FixedNode commitPoint, CandidateRejection reason) {
            addKill(id);
            if (id.isAny()) {
                return state.getMovableWrites().killWrites((killedKey, data) -> diagnostics.recordCandidateRejected(loop, killedKey, reason));
            } else {
                return state.getMovableWrites().killWritesUntil(id, key, (killedKey, data) -> diagnostics.recordCandidateRejected(loop, killedKey, reason));
            }
        }

        /**
         * Analyzes a nested loop once, inherits its summarized state into this closure, and then
         * continues walking the nested loop body with that inherited state.
         */
        @Override
        protected List<BlockState> processLoop(CFGLoop<HIRBlock> inner, BlockState initialState) {
            DebugContext debug = this.start.getDebug();
            debug.log(DebugContext.INFO_LEVEL, "Loop sink analysis closure for loop \"%s\" (inner loop: \"%s\", initial state: \"%s\") starts", loop, inner, initialState);

            LoopBeginNode loopBegin = (LoopBeginNode) inner.getHeader().getBeginNode();
            assert loopBegin != start : "Must be different from start, loopBegin=" + loopBegin + " start=" + start;

            // Nested loop

            // Perform recursive analysis. This is done once per loop then cached, to prevent
            // combinatorial explosion.
            LoopAnalysisResult analysis = LoopSinkAnalyser.this.analyze(inner, excludeFieldsFilter);

            // Build the state to be used for further processing.
            BlockState loopState = new BlockState();
            analysis.inheritInto(cloneState(initialState), loopState, allowModifyGraph ? loopBegin.graph() : null);

            // The initial state for the loop is complete, continue processing.
            ReentrantBlockIterator.LoopInfo<BlockState> loopInfo = ReentrantBlockIterator.processLoop(this, inner, loopState);

            debug.log(DebugContext.INFO_LEVEL, "Loop sink analysis closure for loop \"%s\" (inner loop: \"%s\", initial state: \"%s\") completed", loop, inner, initialState);
            return loopInfo.exitStates;
        }

        private class MergeProcessor extends MovableWriteMergeProcessor<BlockState> {
            private final AbstractMergeNode merge;

            MergeProcessor(BlockState newState, AbstractMergeNode merge) {
                super(newState, context, merge.graph());
                this.merge = merge;
            }

            /**
             * Builds the merged loop-analysis state and records killed locations.
             */
            @Override
            public void forgeNewState(List<BlockState> blockSinkingData) {
                addKillAll(this.killedIdentities);
                var candidates = new ArrayList<CacheEntry>();
                blockSinkingData.get(conditionalSinks.isEmpty() ? 0 : 1).getMovableWrites().forAllInOrder((key) -> {
                    if (toCommit.contains(key)) {
                        return;
                    }
                    if (!canMergeData(blockSinkingData, key)) {
                        diagnostics.recordCandidateRejected(loop, key, CandidateRejection.MERGE_VALUE_OR_BARRIER);
                        addKill(key.identity);
                        return;
                    }
                    candidates.add(key);
                });
                for (CacheEntry key : candidates) {
                    if (kills(key.identity)) {
                        continue;
                    }
                    CacheData data = getMergedData(blockSinkingData, key, merge);
                    newState.getMovableWrites().addEntry(merge.graph(), key, data);
                }
            }

            @Override
            protected boolean addPhis() {
                return allowModifyGraph;
            }

            @Override
            protected boolean checkConditionalWrite(LocationIdentity id) {
                if (merge instanceof LoopBeginNode) {
                    return !kills(id);
                }
                return false;
            }

            @Override
            protected void recordMergeOrderingConflict(CacheEntry key) {
                diagnostics.recordCandidateRejected(loop, key, CandidateRejection.MERGE_ORDERING_CONFLICT);
            }
        }

    }
}
