/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.types;

import java.util.*;

import com.oracle.graal.lir.cfg.*;

public abstract class PostOrderBlockIterator {

    private static class MergeInfo {
        int endsVisited;
        int loopVisited;
    }

    private final Deque<Block> blockQueue = new ArrayDeque<>();
    private final Deque<Block> epochs = new ArrayDeque<>();
    private final HashMap<Block, MergeInfo> mergeInfo = new HashMap<>();

    public void apply(Block start) {
        blockQueue.add(start);
        while (true) {
            while (!blockQueue.isEmpty()) {
                Block current = blockQueue.removeLast();
                boolean queueSuccessors = true;
                if (current.isLoopHeader()) {
                    MergeInfo info = mergeInfo.get(current);
//                    System.out.println("loop header: " + info.loopVisited + " " + info.endsVisited + "-" + info.epoch + " " + epoch);
                    if (info.endsVisited == 1) {
                        loopHeaderInitial(current);
                    } else {
                        info.loopVisited++;
                        if (loopHeader(current, info.loopVisited)) {
                            epochs.addFirst(current);
                        }
                        queueSuccessors = false;
                    }
                } else {
                    block(current);
                }

                if (queueSuccessors) {
                    for (int i = 0; i < current.getSuccessors().size(); i++) {
                        Block successor = current.getSuccessors().get(i);
                        if (successor.getPredecessors().size() > 1) {
                            queueMerge(successor);
                        } else {
                            blockQueue.addLast(successor);
                        }
                    }
                }
            }
            if (epochs.isEmpty()) {
                return;
            } else {
                Block nextEpoch = epochs.removeLast();

                for (int i = 0; i < nextEpoch.getSuccessors().size(); i++) {
                    Block successor = nextEpoch.getSuccessors().get(i);
                    if (successor.getPredecessors().size() > 1) {
                        queueMerge(successor);
                    } else {
                        blockQueue.addLast(successor);
                    }
                }
            }
        }
    }

    protected abstract void loopHeaderInitial(Block block);

    protected abstract boolean loopHeader(Block block, int loopVisitedCount);

    protected abstract void block(Block block);

    private void queueMerge(Block merge) {
        if (!mergeInfo.containsKey(merge)) {
            mergeInfo.put(merge, new MergeInfo());
        }
        MergeInfo info = mergeInfo.get(merge);
        info.endsVisited++;
        if (merge.isLoopHeader() && info.endsVisited == 1) {
            blockQueue.addFirst(merge);
        } else if (info.endsVisited == merge.getPredecessors().size()) {
            blockQueue.addFirst(merge);
        }
    }
}
