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
package com.oracle.max.graal.compiler.types;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.lir.cfg.*;

public abstract class PostOrderBlockIterator {

    private final BitMap visitedEndBlocks;
    private final Deque<Block> blockQueue;

    public PostOrderBlockIterator(Block start, int blockCount) {
        visitedEndBlocks = new BitMap(blockCount);
        blockQueue = new ArrayDeque<>();
        blockQueue.add(start);
    }

    public void apply() {
        while (!blockQueue.isEmpty()) {
            Block current = blockQueue.removeLast();
            block(current);

            for (int i = 0; i < current.getSuccessors().size(); i++) {
                Block successor = current.getSuccessors().get(i);
                if (successor.getPredecessors().size() > 1) {
                    queueMerge(current, successor);
                } else {
                    blockQueue.addLast(successor);
                }
            }
        }
    }

    protected abstract void block(Block block);

    private void queueMerge(Block end, Block merge) {
        visitedEndBlocks.set(end.getId());
        for (Block pred : merge.getPredecessors()) {
            if (!visitedEndBlocks.get(pred.getId())) {
                return;
            }
        }
        blockQueue.addFirst(merge);
    }
}
