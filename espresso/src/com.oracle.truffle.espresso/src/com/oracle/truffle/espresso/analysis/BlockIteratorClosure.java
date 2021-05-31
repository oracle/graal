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

import com.oracle.truffle.espresso.analysis.BlockIterator.BlockProcessResult;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;

public abstract class BlockIteratorClosure {
    /**
     * The return value of this method controls the behavior of when blocks are pushed to the
     * working queue of the {@link DepthFirstBlockIterator}. If a block is not ready to be
     * processed, this method should return {@link BlockProcessResult#SKIP}, else, the method should
     * return {@link BlockProcessResult#DONE}.
     * <p>
     * Otherwise, if this closure is to be used with the {@link BlockIterator}, it should always
     * return {@link BlockProcessResult#DONE}.
     */
    public abstract BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor);

    public int[] getSuccessors(LinkedBlock b) {
        return b.successorsID();
    }

    public LinkedBlock getEntry(Graph<? extends LinkedBlock> graph) {
        return graph.entryBlock();
    }
}
