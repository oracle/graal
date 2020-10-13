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

package com.oracle.truffle.espresso.analysis;

import java.util.BitSet;
import java.util.List;

import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Method;

public class BlockIterator implements AnalysisProcessor {
    public enum BlockProcessResult {
        SKIP,
        DONE
    }

    private final BytecodeStream bs;
    protected final Graph<? extends LinkedBlock> graph;

    private final BitSet enqueued;
    protected final BitSet done;

    protected final WorkingQueue<LinkedBlock> queue = new WorkingQueue<>();

    protected final BlockIteratorClosure closure;

    public static void analyze(Method method, Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        new BlockIterator(method, graph, closure).analyze();
    }

    public static void analyze(Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        new BlockIterator(graph, closure);
    }

    protected BlockIterator(Method m, Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        assert m.getCodeAttribute() != null;
        this.bs = new BytecodeStream(m.getCode());
        this.graph = graph == null ? GraphBuilder.build(m) : graph;
        this.enqueued = new BitSet(graph.totalBlocks());
        this.done = new BitSet(graph.totalBlocks());
        this.closure = closure;
    }

    protected BlockIterator(Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        assert graph != null;
        this.bs = null;
        this.graph = graph;
        this.enqueued = new BitSet(graph.totalBlocks());
        this.done = new BitSet(graph.totalBlocks());
        this.closure = closure;
    }

    protected final void analyze() {
        push(closure.getEntry(graph));
        while (!queue.isEmpty()) {
            LinkedBlock b = peek();
            BlockProcessResult result = closure.processBlock(b, bs, this);
            processResult(b, result);
        }
    }

    protected void processResult(LinkedBlock b, BlockProcessResult result) {
        if (result == BlockProcessResult.DONE) {
            done.set(b.id());
            pop();
        }
        for (int id : closure.getSuccessors(b)) {
            push(graph.get(id));
        }
    }

    protected boolean push(LinkedBlock block) {
        if (isEnqueued(block) || isDone(block)) {
            return false;
        }
        enqueued.set(block.id());
        queue.push(block);
        return true;
    }

    protected LinkedBlock pop() {
        LinkedBlock res = queue.pop();
        assert isEnqueued(res);
        enqueued.clear(res.id());
        return res;
    }

    protected LinkedBlock peek() {
        return queue.peek();
    }

    private boolean isDone(LinkedBlock block) {
        return isDone(block.id());
    }

    @Override
    public boolean isDone(int blockID) {
        return done.get(blockID);
    }

    @Override
    public boolean isInProcess(int blockID) {
        return enqueued.get(blockID);
    }

    @Override
    public LinkedBlock idToBlock(int id) {
        return graph.get(id);
    }

    @Override
    public List<LinkedBlock> findLoop(int block) {
        return queue.findLoop(block);
    }

    private boolean isEnqueued(LinkedBlock block) {
        return enqueued.get(block.id());
    }

}