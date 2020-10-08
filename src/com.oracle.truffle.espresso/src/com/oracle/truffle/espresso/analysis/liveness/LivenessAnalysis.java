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
import com.oracle.truffle.espresso.analysis.BlockIterator;
import com.oracle.truffle.espresso.analysis.DepthFirstBlockIterator;
import com.oracle.truffle.espresso.analysis.GraphBuilder;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.analysis.liveness.actions.MultiAction;
import com.oracle.truffle.espresso.analysis.liveness.actions.NullOutAction;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;

public class LivenessAnalysis {
    @CompilerDirectives.CompilationFinal(dimensions = 1) BCILocalActionRecord[] result;

    public void performPreBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        if (result[bci] == null) {
            return;
        }
        result[bci].pre(frame, node);
    }

    public void performPostBCI(VirtualFrame frame, int bci, BytecodeNode node) {
        if (result[bci] == null) {
            return;
        }
        result[bci].post(frame, node);
    }

    public static LivenessAnalysis analyze(Method method) {
        Graph<? extends LinkedBlock> graph = GraphBuilder.build(method);

        // Transform the graph into a more manageable graph consisting of only the history of
        // load/stores.
        LoadStoreFinder loadStoreClosure = new LoadStoreFinder(graph);
        BlockIterator.analyze(method, graph, loadStoreClosure);

        // Computes the entry/end live sets for each variable for each block.
        BlockBoundaryFinder blockBoundaryFinder = new BlockBoundaryFinder(method, loadStoreClosure.result());
        DepthFirstBlockIterator.analyze(method, graph, blockBoundaryFinder);

        // Using the live sets and history, build a set of action for each bci, such that it frees
        // as early as possible each dead local.
        return new LivenessAnalysis(blockBoundaryFinder.result(), graph, method);
    }

    private LivenessAnalysis(BlockBoundaryResult result, Graph<? extends LinkedBlock> graph, Method method) {
        // this.result = new BCILocalActionRecord[method.getCode().length];
        this.result = buildResultFrom(result, graph, method);
    }

    private static BCILocalActionRecord[] buildResultFrom(BlockBoundaryResult result, Graph<? extends LinkedBlock> graph, Method method) {
        BCILocalActionRecord[] actions = new BCILocalActionRecord[method.getCode().length];
        for (int id = 0; id < graph.totalBlocks(); id++) {
            processBlock(actions, result, id, graph, method);
        }
        for (int i = 0; i < actions.length; i++) {
            BCILocalActionRecord action = actions[i];
            if (action != null && action.hasMulti()) {
                action.freeze();
            }
        }
        return actions;
    }

    private static void processBlock(BCILocalActionRecord[] actions, BlockBoundaryResult helper, int blockID, Graph<? extends LinkedBlock> graph, Method m) {
        LinkedBlock current = graph.get(blockID);

        // merge the state from all predecessors
        BitSet mergedEntryState = mergePredecessors(helper, graph, m, current);

        // Locals inherited from merging predecessors, but are not needed down the line can be
        // killed on block entry.
        killLocalsOnBlockEntry(actions, helper, blockID, m, current, mergedEntryState);

        // Replay history in reverse to seek the last load for each variable.
        replayHistory(actions, helper, blockID);
    }

    private static BitSet mergePredecessors(BlockBoundaryResult helper, Graph<? extends LinkedBlock> graph, Method m, LinkedBlock current) {
        BitSet mergedEntryState = new BitSet(m.getMaxLocals());
        if (current == graph.entryBlock()) {
            // The entry block has the arguments as live variables.
            for (int i = 0; i < m.getParameterCount() + (m.isStatic() ? 0 : 1); i++) {
                mergedEntryState.set(i);
            }
        } else {
            for (int pred : current.predecessorsID()) {
                BitSet predState = helper.endFor(pred);
                for (int i = 0; i < m.getMaxLocals(); i++) {
                    if (predState.get(i)) {
                        mergedEntryState.set(i);
                    }
                }
            }
        }
        return mergedEntryState;
    }

    private static void killLocalsOnBlockEntry(BCILocalActionRecord[] actions, BlockBoundaryResult helper, int blockID, Method m, LinkedBlock current, BitSet mergedEntryState) {
        ArrayList<LocalVariableAction> startActions = new ArrayList<>();
        BitSet entryState = helper.entryFor(blockID);
        for (int i = 0; i < m.getMaxLocals(); i++) {
            if (mergedEntryState.get(i) && !entryState.get(i)) {
                startActions.add(new NullOutAction(i));
            }
        }
        if (!startActions.isEmpty()) {
            LocalVariableAction startAction;
            if (startActions.size() == 1) {
                startAction = startActions.get(0);
            } else {
                startAction = new MultiAction.TempMultiAction(startActions);
            }
            recordAction(actions, current.start(), startAction, true);
        }
    }

    private static void replayHistory(BCILocalActionRecord[] actions, BlockBoundaryResult helper, int blockID) {
        BitSet endState = (BitSet) helper.endFor(blockID).clone();
        for (Record r : helper.historyFor(blockID).reverse()) {
            switch (r.type) {
                case LOAD: // Fallthrough
                case IINC:
                    if (!endState.get(r.local)) {
                        // last load for this value
                        recordAction(actions, r.bci, new NullOutAction(r.local), false);
                        endState.set(r.local);
                    }
                    break;
                case STORE:
                    if (!endState.get(r.local)) {
                        // Store is not used: can be killed immediately.
                        recordAction(actions, r.bci, new NullOutAction(r.local), false);
                    }
                    // Store for this variable kills the local between here and the previous usage
                    endState.clear(r.local);
                    break;
            }
        }
    }

    private static void recordAction(BCILocalActionRecord[] actions, int bci, LocalVariableAction action, boolean preAction) {
        BCILocalActionRecord current = actions[bci];
        if (current == null) {
            current = new BCILocalActionRecord();
            actions[bci] = current;
        }
        current.register(action, preAction);
    }
}
