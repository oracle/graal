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

package jdk.graal.compiler.phases.common.writesinking.writesink;

import java.util.ArrayDeque;
import java.util.List;

import org.graalvm.collections.EconomicSet;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.phases.common.writesinking.LoopSinkAnalyser;
import jdk.graal.compiler.phases.common.writesinking.LoopSinkAnalyser.LoopAnalysisResult;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.CandidateRejection;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.LoopOutcome;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.MethodMetrics;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteClosure;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteMergeProcessor;
import jdk.graal.compiler.phases.common.writesinking.data.WritesOrdering;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.NeverWriteSinkNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator;

/**
 * Performs low-tier write sinking on writes that can be moved out of loops.
 * <p>
 * The graph transformation depends on the results of {@link LoopSinkAnalyser} as it specifies
 * exactly which writes should be moved and the location to where they should be moved.
 * <p>
 * The phase runs after fixed reads, so read dependencies are represented by ordinary fixed nodes
 * rather than proxy nodes. It still runs before write barrier insertion, so the transformation moves
 * and duplicates the original {@code WriteNode}s while preserving their barrier metadata instead of
 * moving expanded barrier code.
 * <p>
 * This means that we know in advance where they all will sink to, simply by looking at the result
 * of the analysis:
 * <ul>
 * <li>For any given ({@code CFGLoop loop}, {@linkplain LoopAnalysisResult state}) pair, all writes in
 * the {@link LoopAnalysisResult#getSinkableWrites()} set will be moved to the loop's exits, unless the
 * parent loop has a write to the same address that is sinkable, then it will be moved to that
 * parent's exits.</li>
 * </ul>
 * <p>
 * Note that the entire loop body needs to be traversed if a loop exit is dominated by a sinkable
 * write, in which case there is no need for creating a {@link ConditionalWriteNode},
 * but a regular write will do.
 * <p>
 * Here is a simple example:
 *
 * <pre>
 * class A {
 *     int f1;
 *     int f2;
 * }
 *
 * public void m(A a) {
 *     for (int i = 0; i < 128; i++) {
 *         a.f1 = i;
 *     }
 * }
 *
 * </pre>
 *
 * The goal of this optimization is to transform this method into:
 *
 * <pre>
 * public void m(A a) {
 *     int v0 = 0;
 *     boolean taken = false
 *     for (int i = 0; i < 128; i++) {
 *         v0 = i;
 *         taken = true;
 *     }
 *     if (taken) {
 *         a.f1 = v0;
 *     }
 * }
 * </pre>
 * <p>
 * To see how the algorithm works, here is a more complex example with nested loops:
 *
 * <pre>
 * public void nested(A a) {
 *     outer: for (int j = 0; j < 128; j++) {
 *         a.f1 = j;
 *         inner: for (int i = 0; i < 128; i++) {
 *             a.f1 = i;
 *             a.f2 = i + j;
 *         }
 *     }
 * }
 * </pre>
 *
 * The {@linkplain LoopSinkAnalyser loop analysis} will tell us that in {@code inner}, both writes
 * to {@code a.f1} and {@code a.f2} are sinkable, and that for {@code outer}, the write to
 * {@code a.f1} also sinks out.
 * <p>
 * As for the optimization itself, it works out by traversing the graph:
 * <ul>
 * <li>On entering {@code outer}, the {@linkplain LoopAnalysisResult loop analysis result} pre-recorded from the
 * analysis is extracted and used as the entry state for processing. This state contains the
 * conditional write {@code a.f1 = j}</li>
 * <li>In the loop itself, the write to {@code a.f1} is detached from the graph (as it was recorded
 * as "sinkable", and information that this write happened is recorded in the
 * {@linkplain WriteSinkingBlockState block state} used for the graph iteration.</li>
 * <li>On entering {@code inner}, much like for {@code outer}, the loop analysis result is inherited from the
 * analysis. In this case, the loop analysis result contains the conditional writes {@code a.f1 = i} and
 * {@code a.f2 = i + j}. However, due to the initial block state on loop entry already containing a
 * write to {@code a.f1}, upon inheriting the analysis state, the conditional write is "upgraded" to
 * a regular write {@code a.f1 = phi(j, i)} .</li>
 * <li>On entering the inner loop, much like for {@code outer}, the writes {@code a.f1 = i} and
 * {@code a.f2 = i + j} are detached from the graph, and their existence is recorded.</li>
 * <li>Once the processing of {@code inner} is done, the states at loop exits is inspected and
 * corresponding writes are inserted there. In our case, the state at the exit of {@code inner} is a
 * conditional write to {@code a.f2} and a regular write to {@code a.f1}. The conditional write is
 * committed there, but the regular write is not, as we know it will flow out of {@code outer}.</li>
 * <li>Now, {@code outer} finishes processing, and a conditional write to {@code a.f1} is the entire
 * state at the loop exit. This conditional write is committed there</li>
 * </ul>
 * <p>
 * Thus, the final shape of the method after the optimization is:
 *
 * <pre>
 * public void nestedSunk(A a) {
 *     int v1 = 0;
 *     boolean loop1Taken = false;
 *     for (int j = 0; i < 128; j++) {
 *         v1 = j;
 *         int v2 = 0;
 *         boolean loop2Taken = false;
 *         for (int i = 0; i < 128; i++) {
 *             v1 = i;
 *             v2 = i + j;
 *             loop2Taken = true;
 *         }
 *         if (loop2Taken) {
 *             a.f2 = v2;
 *         }
 *         loop1Taken = true;
 *     }
 *     if (loop1Taken) {
 *         a.f1 = v1;
 *     }
 * }
 * </pre>
 */
public class LoopWriteSinker extends MovableWriteClosure<WriteSinkingBlockState> {
    private final CoreProviders context;

    private final LoopSinkAnalyser analysis;
    private final StructuredGraph graph;
    private final ControlFlowGraph cfg;
    private final MethodMetrics diagnostics;

    final LoopsData loopsData;

    private boolean graphChanged;
    private final EconomicSet<CFGLoop<HIRBlock>> changedLoops = EconomicSet.create();

    private enum LoopProcessingPolicy {
        NORMAL,
        EXPLICITLY_DISABLED
    }

    /**
     * Result of applying low-tier write sinking to a graph.
     */
    public static final class Result {
        private final boolean changed;

        private Result(boolean changed) {
            this.changed = changed;
        }

        /**
         * Returns {@code true} if write sinking changed the graph.
         */
        public boolean changed() {
            return changed;
        }
    }

    /**
     * Creates the graph-rewrite pass for the low-tier graph represented by {@code cfg}.
     */
    public LoopWriteSinker(CoreProviders context, ControlFlowGraph cfg, MethodFilter excludeFieldsFilter, LoopsData loopsData, String detailsCSV) {
        this(context, cfg, excludeFieldsFilter, loopsData, WriteSinkingDiagnostics.createMethodMetrics(cfg, detailsCSV));
    }

    private LoopWriteSinker(CoreProviders context, ControlFlowGraph cfg, MethodFilter excludeFieldsFilter, LoopsData loopsData, MethodMetrics diagnostics) {
        super(context, excludeFieldsFilter, TraversalMode.APPLICATION, diagnostics);
        this.context = context;
        this.diagnostics = diagnostics;
        this.analysis = new LoopSinkAnalyser(context, cfg, loopsData, true, diagnostics);
        this.graph = cfg.graph;
        this.cfg = cfg;
        this.loopsData = loopsData;
    }

    private CFGLoop<HIRBlock> currentLoop;

    /**
     * Nesting depth of loops currently being traversed without write sinking. A positive value
     * means that writes must still be visited for state reconstruction, but must not be registered
     * for sinking from the current loop or any nested loop.
     */
    private int writeSinkingDisabledDepth;

    /**
     * Returns whether analysis proved {@code write} can be removed while processing the current
     * loop. Disabled-loop traversal deliberately treats all writes as not sinkable.
     */
    private boolean isSinkableWrite(WriteNode write) {
        if (currentLoop == null || writeSinkingDisabledDepth > 0) {
            return false;
        }
        return analysis.analyze(currentLoop, excludeFieldsFilter).getSinkableWrites().contains(write);
    }

    /**
     * Applies write sinking to the whole graph. Loops are analyzed first, and only writes proven
     * safe by the analysis are removed from loop bodies and committed at loop exits.
     */
    public static Result apply(CoreProviders context, ControlFlowGraph cfg, LoopsData loopsData, MethodFilter excludeFieldsFilter, String detailsCSV) {
        LoopWriteSinker writeSinker = new LoopWriteSinker(context, cfg, excludeFieldsFilter, loopsData, detailsCSV);
        writeSinker.apply();
        return new Result(writeSinker.graphChanged);
    }

    /**
     * Processes all outermost loops in the graph and emits method-level diagnostics once all loop
     * rewrites are complete.
     */
    private void apply() {
        var toAnalyze = new ArrayDeque<CFGLoop<HIRBlock>>();
        for (CFGLoop<HIRBlock> loop : cfg.getLoops()) {
            if (loop.getParent() == null) {
                // Start from outermost loops.
                toAnalyze.addLast(loop);
            }
        }

        for (CFGLoop<HIRBlock> loop : toAnalyze) {
            currentLoop = null;
            processLoop(loop, getInitialState());
        }
        if (graphChanged) {
            WriteSinkingDiagnostics.recordMethodChanged(graph.getDebug());
        }
        diagnostics.emit(graph.getDebug());
    }

    @Override
    protected int killWritesUntil(WriteSinkingBlockState state, LocationIdentity identity, MovableWriteState.CacheEntry identifier, FixedNode commitPoint, CandidateRejection reason) {
        if (identity.isAny()) {
            return commitMovableWrites(state, commitPoint);
        }
        if (state.containsLocation(identity)) {
            return commitMovableWritesUntil(state, identity, identifier, commitPoint);
        }
        return 0;
    }

    @Override
    protected void processOther(WriteSinkingBlockState state, FixedNode node) {
    }

    @Override
    protected boolean registerWrite(WriteSinkingBlockState state, WriteNode write, MovableWriteState.CacheEntry identifier) {
        if (isSinkableWrite(write)) {
            // Previous analysis says this can be taken out of the loop.
            HIRBlock writeBlock = cfg.blockFor(write);
            state.registerWrite(identifier, write, write.value());
            WriteSinkingDiagnostics.recordWriteRemoved(graph.getDebug(), writeBlock);
            markCurrentLoopChanged();
            return true;
        }
        // Ignore writes that will not get out of the loop.
        return false;
    }

    @Override
    protected WriteSinkingBlockState getInitialState() {
        // WS is always run with a CFG
        return new WriteSinkingBlockState(graph, cfg);
    }

    @Override
    protected WriteSinkingBlockState merge(HIRBlock merge, List<WriteSinkingBlockState> states) {
        MergeProcessor processor = new MergeProcessor(getInitialState(), (AbstractMergeNode) merge.getBeginNode());
        processor.mergeForState(states);
        return processor.state();
    }

    @Override
    protected WriteSinkingBlockState cloneState(WriteSinkingBlockState oldState) {
        return oldState.cloneState();
    }

    @Override
    @SuppressWarnings("try")
    protected List<WriteSinkingBlockState> processLoop(CFGLoop<HIRBlock> loop, WriteSinkingBlockState initialState) {
        DebugContext debug = this.graph.getDebug();
        LoopProcessingPolicy policy = selectLoopPolicy(loop);
        if (policy == LoopProcessingPolicy.EXPLICITLY_DISABLED) {
            // Noop, simply prepare list of exit states
            diagnostics.recordLoopOutcome(loop, LoopOutcome.EXPLICITLY_DISABLED);
            return processLoopWithoutWriteSinking(loop, initialState);
        }

        try (DebugContext.Scope s = debug.scope("LoopWriteSinker")) {
            debug.log(DebugContext.INFO_LEVEL, "Start write sinking application for loop \"%s\"", loop);
            debug.dump(DebugContext.DETAILED_LEVEL, this.graph, "before write sinking of loop \"%s\"", loop);

            CFGLoop<HIRBlock> parent = currentLoop;
            assert parent == loop.getParent() : parent + " vs " + loop.getParent();
            currentLoop = loop;

            // Obtain state for this loop. Re-use analysis.
            WriteSinkingBlockState loopBeginState = getInitialState();
            LoopAnalysisResult preProcessing = analysis.analyze(loop, excludeFieldsFilter);
            preProcessing.inheritInto(initialState.cloneState(), loopBeginState, graph);
            // Start processing the loop.
            ReentrantBlockIterator.LoopInfo<WriteSinkingBlockState> loopInfo = ReentrantBlockIterator.processLoop(this, loop, loopBeginState);

            debug.log(DebugContext.INFO_LEVEL, "Finished loop body processing for loop \"%s\" ", loop);
            debug.dump(DebugContext.DETAILED_LEVEL, this.graph, "finished loop body processing for loop \"%s\"", loop);

            /*
             * Writes that are taken out and do not sink out of the outer loop must be committed at
             * exits.
             */
            List<WriteSinkingBlockState> exitStates = loopInfo.exitStates;
            commitAtExits(exitStates, loop.getLoopExits(), initialState);

            currentLoop = parent;
            recordUnchangedLoopOutcome(loop, preProcessing, policy);
            debug.log(DebugContext.INFO_LEVEL, "Finished write sinking application for loop \"%s\"", loop);
            debug.dump(DebugContext.DETAILED_LEVEL, this.graph, "after write sinking of loop \"%s\"", loop);
            return exitStates;
        }
    }

    /**
     * Selects whether a loop should be rewritten normally or traversed only to rebuild outgoing
     * state.
     */
    private LoopProcessingPolicy selectLoopPolicy(CFGLoop<HIRBlock> loop) {
        if (writeSinkingDisabledDepth > 0 || containsNeverWriteSinkNode(loop)) {
            return LoopProcessingPolicy.EXPLICITLY_DISABLED;
        }
        return LoopProcessingPolicy.NORMAL;
    }

    /**
     * Traverses a loop without registering new sunk writes. Movable writes flowing into the loop are
     * committed before the header so the no-sinking traversal cannot hide them.
     */
    private List<WriteSinkingBlockState> processLoopWithoutWriteSinking(CFGLoop<HIRBlock> loop, WriteSinkingBlockState initialState) {
        commitMovableWrites(initialState, loop.getHeader().getBeginNode());
        CFGLoop<HIRBlock> parent = currentLoop;
        currentLoop = loop;
        writeSinkingDisabledDepth++;
        try {
            ReentrantBlockIterator.LoopInfo<WriteSinkingBlockState> loopInfo = ReentrantBlockIterator.processLoop(this, loop, getInitialState());
            return loopInfo.exitStates;
        } finally {
            writeSinkingDisabledDepth--;
            currentLoop = parent;
        }
    }

    /**
     * Returns whether the loop body contains a lowered {@code GraalDirectives.neverWriteSink()}
     * marker.
     */
    private static boolean containsNeverWriteSinkNode(CFGLoop<HIRBlock> loop) {
        for (HIRBlock block : loop.getBlocks()) {
            for (FixedNode node : block.getNodes()) {
                if (node instanceof NeverWriteSinkNode) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Commits every movable write in {@code state} at {@code commitPoint}.
     */
    private static int commitMovableWrites(WriteSinkingBlockState state, FixedNode commitPoint) {
        MovableWriteState.CommitOrder order = state.spawnCommitOrder();
        state.getMovableWrites().forAllData((key, data) -> order.register(key));
        int committedWrites = order.size();
        state.commitOrder(order, commitPoint);
        return committedWrites;
    }

    /**
     * Commits movable writes for {@code identity} through {@code identifier}, or through the latest
     * movable write for the identity when no matching identifier is available.
     */
    private static int commitMovableWritesUntil(WriteSinkingBlockState state, LocationIdentity identity, MovableWriteState.CacheEntry identifier, FixedNode commitPoint) {
        WritesOrdering ordering = state.getMovableWrites().getOrdering(identity);
        if (ordering == null || ordering.isEmpty()) {
            return 0;
        }
        MovableWriteState.CacheEntry until = identifier == null || !state.containsEntry(identifier) ? ordering.get(ordering.size() - 1) : identifier;
        MovableWriteState.CommitOrder order = state.spawnCommitOrder();
        order.register(until);
        int committedWrites = order.size();
        state.commitOrder(order, commitPoint);
        return committedWrites;
    }

    /**
     * Materializes writes that were removed from this loop but cannot continue sinking through the
     * surrounding state.
     */
    @SuppressWarnings("try")
    private void commitAtExits(List<WriteSinkingBlockState> exitStates, List<HIRBlock> exits, WriteSinkingBlockState initialState) {
        assert exitStates.size() == exits.size() : exitStates + " " + exits;
        boolean writesCommitted = false;
        for (int i = 0; i < exitStates.size(); i++) {
            WriteSinkingBlockState state = exitStates.get(i);
            MovableWriteState.CommitOrder order = state.spawnCommitOrder();
            state.getMovableWrites().forAllData((key, data) -> {
                /*
                 * One way to know if a given write should be committed at the exit:
                 *
                 * - The outer loop does not let it sink through.
                 *
                 * By construction, we know that this write will sink out of the loop iff the state
                 * at the loop entry has it present.
                 */
                if (!initialState.containsEntry(key)) {
                    order.register(key);
                }
            });
            assert verifyCommits(order, initialState);
            AbstractBeginNode exit = exits.get(i).getBeginNode();
            FixedNode commitPoint = exit.next();

            DebugContext debug = this.graph.getDebug();
            debug.log(DebugContext.INFO_LEVEL, "Committing sunk write for exit node \"%s\" (exit state \"%s\") with order \"%s\"", exits.get(i), state, order);

            if (state.commitOrder(order, commitPoint)) {
                graph.getOptimizationLog().report(getClass(), "LoopWriteSinking", exit);
                writesCommitted = true;
                graphChanged = true;
            } else {
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, this.graph, "after write commit for loop exit \"%s\"", exits.get(i));
            }
            debug.dump(DebugContext.DETAILED_LEVEL, this.graph, "after loop exit \"%s\"", exits.get(i));
        }
        if (writesCommitted) {
            // Sunk writes may change loop induction, added value phi may become an iv
            Loop lex = loopsData.loop(currentLoop);
            lex.invalidateFragmentsAndIVs();
        }
    }

    /**
     * Records that the current loop had a write removed.
     */
    private void markCurrentLoopChanged() {
        graphChanged = true;
        assert currentLoop != null : "sinkable writes must be registered while processing a loop";
        if (changedLoops.add(currentLoop)) {
            diagnostics.recordLoopChanged(currentLoop);
            WriteSinkingDiagnostics.recordLoopChanged(graph.getDebug());
        }
    }

    /**
     * Records the mutually exclusive diagnostic outcome for a loop that did not remove a write.
     */
    private void recordUnchangedLoopOutcome(CFGLoop<HIRBlock> loop, LoopAnalysisResult loopState, LoopProcessingPolicy policy) {
        if (policy == LoopProcessingPolicy.EXPLICITLY_DISABLED) {
            diagnostics.recordLoopOutcome(loop, LoopOutcome.EXPLICITLY_DISABLED);
        } else if (!diagnostics.hasWrites(loop)) {
            diagnostics.recordLoopOutcome(loop, LoopOutcome.NO_WRITES);
        } else if (loopState.killsAny()) {
            diagnostics.recordLoopOutcome(loop, LoopOutcome.GLOBAL_KILL_OR_FORCED_COMMIT);
        } else if (!diagnostics.hasEligibleWrites(loop)) {
            diagnostics.recordLoopOutcome(loop, LoopOutcome.NO_ELIGIBLE_WRITES);
        } else if (!diagnostics.hasSinkableWrites(loop)) {
            diagnostics.recordLoopOutcome(loop, LoopOutcome.NO_SINKABLE_WRITES);
        } else {
            diagnostics.recordLoopOutcome(loop, LoopOutcome.SINKABLE_NOT_CHANGED);
        }
    }

    /**
     * Verifies that exit commits do not re-materialize writes that already existed in the incoming
     * block state.
     */
    private static boolean verifyCommits(MovableWriteState.CommitOrder order, WriteSinkingBlockState initialState) {
        // Make sure that ordering side effects do not commit writes other than specified.
        order.forAll((key) -> {
            assert !initialState.containsEntry(key) : "State " + initialState + " must not contain " + key;
        });
        return true;
    }

    private class MergeProcessor extends MovableWriteMergeProcessor<WriteSinkingBlockState> {
        private final AbstractMergeNode merge;

        MergeProcessor(WriteSinkingBlockState newState, AbstractMergeNode merge) {
            super(newState, context, merge.graph());
            this.merge = merge;
        }

        /**
         * Builds the write-sinking state that flows out of this merge.
         */
        @Override
        public void forgeNewState(List<WriteSinkingBlockState> states) {
            assert toCommit.isEmpty() : "ToCommit must be empty at merge " + merge + " =" + toCommit;
            states.get(0).getMovableWrites().forAllInOrder((key) -> {
                MovableWriteState.CacheData data = getMergedData(states, key, merge);
                newState.getMovableWrites().addEntry(merge.graph(), key, data);
            });
        }

        @Override
        protected boolean checkConditionalWrite(@SuppressWarnings("unused") LocationIdentity id) {
            return false;
        }
    }
}
