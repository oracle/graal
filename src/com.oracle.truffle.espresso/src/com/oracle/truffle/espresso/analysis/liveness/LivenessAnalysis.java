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
    @CompilerDirectives.CompilationFinal(dimensions = 1) LocalVariableAction[] result;

    public void performLivenessAnalysisAction(VirtualFrame frame, int bci, BytecodeNode node) {
        result[bci].execute(frame, node);
    }

    public static LivenessAnalysis analyze(Method method) {
        Graph<? extends LinkedBlock> graph = GraphBuilder.build(method);
        LoadStoreFinder loadStoreClosure = new LoadStoreFinder(graph);
        BlockIterator.analyze(method, graph, loadStoreClosure);
        BlockBoundaryFinder blockBoundaryFinder = new BlockBoundaryFinder(method, loadStoreClosure.result());
        DepthFirstBlockIterator.analyze(method, graph, blockBoundaryFinder);
        return new LivenessAnalysis(blockBoundaryFinder.result(), graph, method);
    }

    private LivenessAnalysis(BlockBoundaryResult result, Graph<? extends LinkedBlock> graph, Method method) {
        this.result = buildResultFrom(result, graph, method);
    }

    private static LocalVariableAction[] buildResultFrom(BlockBoundaryResult result, Graph<? extends LinkedBlock> graph, Method method) {
        LocalVariableAction[] actions = new LocalVariableAction[method.getCode().length];
        for (int id = 0; id < graph.totalBlocks(); id++) {
            processBlock(actions, result, id, graph, method);
        }
        for (int i = 0; i < actions.length; i++) {
            LocalVariableAction action = actions[i];
            if (action instanceof MultiAction.TempMultiAction) {
                actions[i] = ((MultiAction.TempMultiAction) action).freeze();
            }
        }
        return actions;
    }

    private static void processBlock(LocalVariableAction[] actions, BlockBoundaryResult helper, int blockID, Graph<? extends LinkedBlock> graph, Method m) {
        LinkedBlock current = graph.get(blockID);
        BitSet mergedEntryState = new BitSet(m.getMaxLocals());
        if (current == graph.entryBlock()) {
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
            recordAction(actions, current.start(), startAction);
        }

        BitSet endState = (BitSet) helper.endFor(blockID).clone();
        for (Record r : helper.historyFor(blockID).reverse()) {
            switch (r.type) {
                case LOAD: // Fallthrough
                case IINC:
                    if (!endState.get(r.local)) {
                        // last load for this value
                        recordAction(actions, r.bci, new NullOutAction(r.local));
                        endState.set(r.local);
                    }
                    break;
                case STORE:
                    if (!endState.get(r.local)) {
                        // last load for this value
                        recordAction(actions, r.bci, new NullOutAction(r.local));
                    }
                    endState.clear(r.local);
                    break;
            }
        }
    }

    private static void recordAction(LocalVariableAction[] actions, int bci, LocalVariableAction action) {
        LocalVariableAction current = actions[bci];
        if (current == null) {
            actions[bci] = action;
        } else if (current instanceof MultiAction.TempMultiAction) {
            ((MultiAction.TempMultiAction) current).add(action);
        } else {
            MultiAction.TempMultiAction multi = new MultiAction.TempMultiAction();
            multi.add(current);
            multi.add(action);
        }
    }
}
