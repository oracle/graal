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

import java.util.BitSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.analysis.BlockBuilder;
import com.oracle.truffle.espresso.analysis.BlockIterator;
import com.oracle.truffle.espresso.analysis.DepthFirstBlockIterator;
import com.oracle.truffle.espresso.analysis.WorkingQueue;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;

public class LivenessAnalysis {
    @CompilerDirectives.CompilationFinal(dimensions = 1) LocalVariableAction[] result;

    public void performLivenessAnalysisAction(VirtualFrame frame, int bci, BytecodeNode node) {
        result[bci].execute(frame, node);
    }

    public static LivenessAnalysis analyze(Method method) {
        Graph<? extends LinkedBlock> graph = BlockBuilder.build(method);
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
        WorkingQueue<LinkedBlock> queue = new WorkingQueue<>();
        BitSet enqueued = new BitSet(graph.totalBlocks());

        queue.push(graph.entryBlock());
        enqueued.set(graph.entryBlock().id());

        while (!queue.isEmpty()) {
            LinkedBlock current = queue.pop();

            processBlock(actions, result, current);

            for (int succ : current.successorsID()) {
                if (!enqueued.get(succ)) {
                    queue.push(graph.get(succ));
                }
            }
        }
        return actions;
    }

    private static void processBlock(LocalVariableAction[] actions, BlockBoundaryResult helper, LinkedBlock current) {

    }
}
