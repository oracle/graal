/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.common.cfg;

import jdk.graal.compiler.nodes.cfg.CFGVerifier;

public interface AbstractControlFlowGraph<T extends BasicBlock<T>> {

    /**
     * Special value used for basic block indices into the {@link #getBlocks()} array. Using this
     * index can mean a block's id was not initialized or the block was deleted later on by control
     * flow optimizations.
     */
    int INVALID_BLOCK_ID = Integer.MAX_VALUE;

    /**
     * Last valid block index used by the compiler. A compilation unit with more basic blocks would
     * trigger bailouts or compilation exceptions.
     */
    int LAST_VALID_BLOCK_INDEX = Integer.MAX_VALUE - 1;

    /**
     * Returns the list blocks contained in this control flow graph.
     *
     * It is {@linkplain CFGVerifier guaranteed} that the blocks are numbered and ordered according
     * to a reverse post order traversal of the control flow graph. The
     * {@linkplain BasicBlock#getId() id} of each block in the graph is an index into the returned
     * array.
     *
     * @see CFGVerifier
     */
    T[] getBlocks();

    int getNumberOfLoops();

    T getStartBlock();

    /**
     * True if block {@code b} is dominated by block {@code a} or {@code a} is equal to {@code b}.
     */
    static boolean dominates(BasicBlock<?> a, BasicBlock<?> b) {
        assert a != null;
        assert b != null;
        int domNumberA = a.getDominatorNumber();
        int domNumberB = b.getDominatorNumber();
        return domNumberB >= domNumberA && domNumberB <= a.getMaxChildDominatorNumber();
    }

    /**
     * True if block {@code a} dominates block {@code b} and {@code a} is not identical block to
     * {@code b}.
     */
    static boolean strictlyDominates(BasicBlock<?> a, BasicBlock<?> b) {
        return a != b && dominates(a, b);
    }

    /**
     * Calculates the common dominator of two blocks.
     *
     * Note that this algorithm makes use of special properties regarding the numbering of blocks.
     *
     * @see #getBlocks()
     * @see CFGVerifier
     */
    static BasicBlock<?> commonDominator(BasicBlock<?> a, BasicBlock<?> b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else if (a == b) {
            return a;
        } else {
            int aDomDepth = a.getDominatorDepth();
            int bDomDepth = b.getDominatorDepth();
            BasicBlock<?> aTemp;
            BasicBlock<?> bTemp;
            if (aDomDepth > bDomDepth) {
                aTemp = a;
                bTemp = b;
            } else {
                aTemp = b;
                bTemp = a;
            }
            return commonDominatorHelper(aTemp, bTemp);
        }
    }

    static BasicBlock<?> commonDominatorHelper(BasicBlock<?> a, BasicBlock<?> b) {
        int domNumberA = a.getDominatorNumber();
        BasicBlock<?> result = b;
        while (domNumberA < result.getDominatorNumber()) {
            result = result.getDominator();
        }
        while (domNumberA > result.getMaxChildDominatorNumber()) {
            result = result.getDominator();
        }
        return result;
    }

    /**
     * @see AbstractControlFlowGraph#commonDominator(BasicBlock, BasicBlock)
     */
    @SuppressWarnings("unchecked")
    static <T extends BasicBlock<T>> T commonDominatorTyped(T a, T b) {
        return (T) commonDominator(a, b);
    }

    static boolean blockIsDeletedOrNew(int blockId) {
        if (blockId == INVALID_BLOCK_ID) {
            return true;
        }
        return false;
    }

    default BasicBlockSet createBasicBlockSet() {
        return new BasicBlockSet(this);
    }
}
