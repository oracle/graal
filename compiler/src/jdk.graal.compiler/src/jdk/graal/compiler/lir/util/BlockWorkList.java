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
package jdk.graal.compiler.lir.util;

import java.util.ArrayDeque;
import java.util.BitSet;

import jdk.graal.compiler.core.common.cfg.BasicBlock;

/** A FIFO worklist that keeps each basic block queued at most once. */
public final class BlockWorkList {
    private final ArrayDeque<BasicBlock<?>> blocks = new ArrayDeque<>();
    private final BitSet queued;

    /** Creates a worklist for blocks whose IDs are in {@code [0, blockCount)}. */
    public BlockWorkList(int blockCount) {
        queued = new BitSet(blockCount);
    }

    /** Adds {@code block} unless it is already queued. */
    public void add(BasicBlock<?> block) {
        int id = block.getId();
        if (!queued.get(id)) {
            queued.set(id);
            blocks.addLast(block);
        }
    }

    /** Removes and returns the next block, or {@code null} if the worklist is empty. */
    public BasicBlock<?> poll() {
        BasicBlock<?> block = blocks.pollFirst();
        if (block != null) {
            queued.clear(block.getId());
        }
        return block;
    }

    /** Returns whether no blocks are queued. */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
