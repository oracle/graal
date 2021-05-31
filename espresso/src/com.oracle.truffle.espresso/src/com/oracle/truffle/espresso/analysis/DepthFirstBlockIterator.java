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

import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.impl.Method;

/**
 * Depth-first iteration over a graph's blocks.
 */
public final class DepthFirstBlockIterator extends BlockIterator {
    private final IntArrayIterator[] successors;

    public static void analyze(Method m, Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        new DepthFirstBlockIterator(m, graph, closure).analyze();
    }

    public static void analyze(Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        new DepthFirstBlockIterator(graph, closure).analyze();
    }

    private DepthFirstBlockIterator(Method m, Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        super(m, graph, closure);
        this.successors = new IntArrayIterator[graph.totalBlocks()];
    }

    private DepthFirstBlockIterator(Graph<? extends LinkedBlock> graph, BlockIteratorClosure closure) {
        super(graph, closure);
        this.successors = new IntArrayIterator[graph.totalBlocks()];
    }

    @Override
    protected void processResult(LinkedBlock b, BlockProcessResult result) {
        switch (result) {
            case SKIP: {
                pushNextSuccessor(b);
                break;
            }
            case DONE:
                done.set(b.id());
                pop();
                break;
        }
    }

    private void pushNextSuccessor(LinkedBlock b) {
        IntArrayIterator iterator = successors[b.id()];
        if (iterator == null) {
            iterator = new IntArrayIterator(closure.getSuccessors(b));
            successors[b.id()] = iterator;
        }
        assert iterator.hasNext();
        while (iterator.hasNext() && !push(graph.get(iterator.next()))) {
            // nothing
        }

    }

    private static final class IntArrayIterator {
        final int[] array;
        int pos = 0;

        private IntArrayIterator(int[] array) {
            this.array = array;
        }

        public boolean hasNext() {
            return pos < array.length;
        }

        public int next() {
            return array[pos++];
        }
    }
}
