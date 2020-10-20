/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.analysis.liveness;

import java.util.ArrayList;
import java.util.BitSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.analysis.BlockIterator;
import com.oracle.truffle.espresso.analysis.DepthFirstBlockIterator;
import com.oracle.truffle.espresso.analysis.GraphBuilder;
import com.oracle.truffle.espresso.analysis.Util;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.analysis.liveness.actions.MultiAction;
import com.oracle.truffle.espresso.analysis.liveness.actions.NullOutAction;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.perf.AutoTimer;
import com.oracle.truffle.espresso.perf.DebugTimer;

public class LivenessAnalysis {

    public static final DebugTimer LIVENESS_TIMER = DebugTimer.create("liveness");
    public static final DebugTimer BUILDER_TIMER = DebugTimer.create("builder");
    public static final DebugTimer LOADSTORE_TIMER = DebugTimer.create("loadStore");
    public static final DebugTimer STATE_TIMER = DebugTimer.create("state");
    public static final DebugTimer PROPAGATE_TIMER = DebugTimer.create("propagation");
    public static final DebugTimer ACTION_TIMER = DebugTimer.create("action");

    public static final LivenessAnalysis NO_ANALYSIS = new LivenessAnalysis() {
        @Override
        public void performPreBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        }

        @Override
        public void performPostBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        }
    };

    /**
     * Contains 2 entries per BCI: the action to perform on entering the BCI (for nulling out locals
     * when jumping into a block), and one for the action to perform after executing the bytecode
     * (/ex: Nulling out a local once it has been loaded and no other load requires it).
     */
    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final LocalVariableAction[] result;
    private final boolean compiledCodeOnly;

    public void performPreBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        doAction(frame, bci, node, true);
    }

    public void performPostBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        doAction(frame, bci, node, false);
    }

    private void doAction(VirtualFrame frame, int bci, BytecodeNode node, boolean preAction) {
        if (!compiledCodeOnly || CompilerDirectives.inCompiledCode()) {
            int index = getIndex(bci, preAction);
            if (result != null && result[index] != null) {
                result[index].execute(frame, node);
            }
        }
    }

    public static LivenessAnalysis analyze(Method method) {
        if (method.getContext().livenessAnalysisMode == EspressoOptions.LivenessAnalysisMode.DISABLED) {
            return NO_ANALYSIS;
        }
        try (AutoTimer liveness = AutoTimer.time(LIVENESS_TIMER)) {
            Graph<? extends LinkedBlock> graph;
            try (AutoTimer builder = AutoTimer.time(BUILDER_TIMER)) {
                graph = GraphBuilder.build(method);
            }

            // Transform the graph into a more manageable graph consisting of only the history of
            // load/stores.
            LoadStoreFinder loadStoreClosure;
            try (AutoTimer loadStore = AutoTimer.time(LOADSTORE_TIMER)) {
                loadStoreClosure = new LoadStoreFinder(graph);
                BlockIterator.analyze(method, graph, loadStoreClosure);
            }

            // Computes the entry/end live sets for each variable for each block.
            BlockBoundaryFinder blockBoundaryFinder;
            try (AutoTimer boundary = AutoTimer.time(STATE_TIMER)) {
                blockBoundaryFinder = new BlockBoundaryFinder(method, loadStoreClosure.result());
                DepthFirstBlockIterator.analyze(method, graph, blockBoundaryFinder);
            }

            try (AutoTimer propagation = AutoTimer.time(PROPAGATE_TIMER)) {
                // Forces loop ends to inherit the loop entry state, and propagates the changes.
                LoopPropagatorClosure loopPropagation = new LoopPropagatorClosure(graph, blockBoundaryFinder.result());
                while (loopPropagation.process(graph)) {
                    /*
                     * This loop should iterate at MOST exactly the maximum number of nested loops
                     * in the method.
                     *
                     * The reasoning is the following:
                     *
                     * - The only reason a new iteration is required is when a loop entry's state
                     * gets modified by the previous iteration.
                     *
                     * - This can happen only if a new live variable gets propagated from an outer
                     * loop.
                     *
                     * - Which means that we do not need to re-propagate the state of the outermost
                     * loop.
                     */
                }
            }

            // Using the live sets and history, build a set of action for each bci, such that it
            // frees as early as possible each dead local.
            try (AutoTimer actionFinder = AutoTimer.time(ACTION_TIMER)) {
                LocalVariableAction[] actions = buildResultFrom(blockBoundaryFinder.result(), graph, method);
                boolean compiledCodeOnly = method.getContext().livenessAnalysisMode == EspressoOptions.LivenessAnalysisMode.COMPILED;
                return new LivenessAnalysis(actions, compiledCodeOnly);
            }
        }
    }

    private LivenessAnalysis(LocalVariableAction[] result, boolean compiledCodeOnly) {
        this.result = result;
        this.compiledCodeOnly = compiledCodeOnly;
    }

    private LivenessAnalysis() {
        this(null, false);
    }

    private static LocalVariableAction[] buildResultFrom(BlockBoundaryResult result, Graph<? extends LinkedBlock> graph, Method method) {
        LocalVariableAction[] actions = new LocalVariableAction[method.getCode().length * 2];
        for (int id = 0; id < graph.totalBlocks(); id++) {
            processBlock(actions, result, id, graph, method);
        }
        return actions;
    }

    private static void processBlock(LocalVariableAction[] actions, BlockBoundaryResult helper, int blockID, Graph<? extends LinkedBlock> graph, Method m) {
        LinkedBlock current = graph.get(blockID);

        // merge the state from all predecessors
        BitSet mergedEntryState = mergePredecessors(helper, graph, m, current);

        // Locals inherited from merging predecessors, but are not needed down the line can be
        // killed on block entry.
        killLocalsOnBlockEntry(actions, helper, blockID, current, mergedEntryState);

        // Replay history in reverse to seek the last load for each variable.
        replayHistory(actions, helper, blockID);
    }

    private static BitSet mergePredecessors(BlockBoundaryResult helper, Graph<? extends LinkedBlock> graph, Method m, LinkedBlock current) {
        BitSet mergedEntryState = new BitSet(m.getMaxLocals());
        if (current == graph.entryBlock()) {
            // The entry block has the arguments as live variables.
            int pos = 0;
            if (!m.isStatic()) {
                mergedEntryState.set(0);
                pos++;
            }
            for (int i = 0; i < m.getParameterCount(); i++) {
                mergedEntryState.set(pos++);
                JavaKind kind = Types.getJavaKind(Signatures.parameterType(m.getParsedSignature(), i));
                if (kind.needsTwoSlots()) {
                    mergedEntryState.set(pos++);
                }
            }
        } else {
            for (int pred : current.predecessorsID()) {
                BitSet predState = helper.endFor(pred);
                for (int i : Util.bitSetIterator(predState)) {
                    mergedEntryState.set(i);
                }
            }
        }
        return mergedEntryState;
    }

    private static void killLocalsOnBlockEntry(LocalVariableAction[] actions, BlockBoundaryResult helper, int blockID, LinkedBlock current, BitSet mergedEntryState) {
        ArrayList<Integer> startActions = new ArrayList<>();
        BitSet entryState = helper.entryFor(blockID);
        for (int i : Util.bitSetIterator(mergedEntryState)) {
            if (!entryState.get(i)) {
                startActions.add(i);
            }
        }
        if (!startActions.isEmpty()) {
            LocalVariableAction startAction;
            if (startActions.size() == 1) {
                startAction = NullOutAction.get(startActions.get(0));
            } else {
                startAction = new MultiAction(Util.toIntArray(startActions));
            }
            recordAction(actions, current.start(), startAction, true);
        }
    }

    private static void replayHistory(LocalVariableAction[] actions, BlockBoundaryResult helper, int blockID) {
        BitSet endState = helper.endFor(blockID);
        if (endState == null) {
            // unreachable
            return;
        }
        endState = (BitSet) endState.clone();
        for (Record r : helper.historyFor(blockID).reverse()) {
            switch (r.type) {
                case LOAD: // Fallthrough
                case IINC:
                    if (!endState.get(r.local)) {
                        // last load for this value
                        recordAction(actions, r.bci, NullOutAction.get(r.local), false);
                        endState.set(r.local);
                    }
                    break;
                case STORE:
                    if (!endState.get(r.local)) {
                        // Store is not used: can be killed immediately.
                        recordAction(actions, r.bci, NullOutAction.get(r.local), false);
                    } else {
                        // Store for this variable kills the local between here and the previous
                        // usage
                        endState.clear(r.local);
                    }
                    break;
            }
        }
    }

    private static void recordAction(LocalVariableAction[] actions, int bci, LocalVariableAction action, boolean preAction) {
        int index = getIndex(bci, preAction);
        LocalVariableAction toInsert = action;
        if (actions[index] != null) {
            // 2 actions for a single BCI: access to a 2 slot local (long/double).
            toInsert = actions[index].merge(toInsert);
        }
        actions[index] = toInsert;
    }

    private static int getIndex(int bci, boolean preAction) {
        return 2 * bci + (preAction ? 0 : 1);
    }
}
