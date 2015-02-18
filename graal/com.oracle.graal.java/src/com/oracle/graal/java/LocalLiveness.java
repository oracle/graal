/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.java;

import static com.oracle.graal.bytecode.Bytecodes.*;

import com.oracle.graal.bytecode.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.BciBlockMapping.*;

/**
 * Encapsulates the liveness calculation, so that subclasses for locals &le; 64 and locals &gt; 64
 * can be implemented.
 */
public abstract class LocalLiveness {
    protected final BciBlock[] blocks;

    public static LocalLiveness compute(BytecodeStream stream, BciBlock[] blocks, int maxLocals, int loopCount) {
        LocalLiveness liveness = maxLocals <= 64 ? new SmallLocalLiveness(blocks, maxLocals, loopCount) : new LargeLocalLiveness(blocks, maxLocals, loopCount);
        liveness.computeLiveness(stream);
        return liveness;
    }

    protected LocalLiveness(BciBlock[] blocks) {
        this.blocks = blocks;
    }

    void computeLiveness(BytecodeStream stream) {
        for (BciBlock block : blocks) {
            computeLocalLiveness(stream, block);
        }

        boolean changed;
        int iteration = 0;
        do {
            Debug.log("Iteration %d", iteration);
            changed = false;
            for (int i = blocks.length - 1; i >= 0; i--) {
                BciBlock block = blocks[i];
                int blockID = block.getId();
                // log statements in IFs because debugLiveX creates a new String
                if (Debug.isLogEnabled()) {
                    Debug.logv("  start B%d  [%d, %d]  in: %s  out: %s  gen: %s  kill: %s", block.getId(), block.startBci, block.endBci, debugLiveIn(blockID), debugLiveOut(blockID),
                                    debugLiveGen(blockID), debugLiveKill(blockID));
                }

                boolean blockChanged = (iteration == 0);
                if (block.getSuccessorCount() > 0) {
                    int oldCardinality = liveOutCardinality(blockID);
                    for (BciBlock sux : block.getSuccessors()) {
                        if (Debug.isLogEnabled()) {
                            Debug.log("    Successor B%d: %s", sux.getId(), debugLiveIn(sux.getId()));
                        }
                        propagateLiveness(blockID, sux.getId());
                    }
                    blockChanged |= (oldCardinality != liveOutCardinality(blockID));
                }

                if (blockChanged) {
                    updateLiveness(blockID);
                    if (Debug.isLogEnabled()) {
                        Debug.logv("  end   B%d  [%d, %d]  in: %s  out: %s  gen: %s  kill: %s", block.getId(), block.startBci, block.endBci, debugLiveIn(blockID), debugLiveOut(blockID),
                                        debugLiveGen(blockID), debugLiveKill(blockID));
                    }
                }
                changed |= blockChanged;
            }
            iteration++;
        } while (changed);
    }

    /**
     * Returns whether the local is live at the beginning of the given block.
     */
    public abstract boolean localIsLiveIn(BciBlock block, int local);

    /**
     * Returns whether the local is set in the given loop.
     */
    public abstract boolean localIsChangedInLoop(int loopId, int local);

    /**
     * Returns whether the local is live at the end of the given block.
     */
    public abstract boolean localIsLiveOut(BciBlock block, int local);

    /**
     * Returns a string representation of the liveIn values of the given block.
     */
    protected abstract String debugLiveIn(int blockID);

    /**
     * Returns a string representation of the liveOut values of the given block.
     */
    protected abstract String debugLiveOut(int blockID);

    /**
     * Returns a string representation of the liveGen values of the given block.
     */
    protected abstract String debugLiveGen(int blockID);

    /**
     * Returns a string representation of the liveKill values of the given block.
     */
    protected abstract String debugLiveKill(int blockID);

    /**
     * Returns the number of live locals at the end of the given block.
     */
    protected abstract int liveOutCardinality(int blockID);

    /**
     * Adds all locals the are in the liveIn of the successor to the liveOut of the block.
     */
    protected abstract void propagateLiveness(int blockID, int successorID);

    /**
     * Calculates a new liveIn for the given block from liveOut, liveKill and liveGen.
     */
    protected abstract void updateLiveness(int blockID);

    /**
     * Adds the local to liveGen if it wasn't already killed in this block.
     */
    protected abstract void loadOne(int blockID, int local);

    /**
     * Add this local to liveKill if it wasn't already generated in this block.
     */
    protected abstract void storeOne(int blockID, int local);

    private void computeLocalLiveness(BytecodeStream stream, BciBlock block) {
        if (block.startBci < 0 || block.endBci < 0) {
            return;
        }
        int blockID = block.getId();
        int localIndex;
        stream.setBCI(block.startBci);
        while (stream.currentBCI() <= block.endBci) {
            switch (stream.currentBC()) {
                case LLOAD:
                case DLOAD:
                    loadTwo(blockID, stream.readLocalIndex());
                    break;
                case LLOAD_0:
                case DLOAD_0:
                    loadTwo(blockID, 0);
                    break;
                case LLOAD_1:
                case DLOAD_1:
                    loadTwo(blockID, 1);
                    break;
                case LLOAD_2:
                case DLOAD_2:
                    loadTwo(blockID, 2);
                    break;
                case LLOAD_3:
                case DLOAD_3:
                    loadTwo(blockID, 3);
                    break;
                case IINC:
                    localIndex = stream.readLocalIndex();
                    loadOne(blockID, localIndex);
                    storeOne(blockID, localIndex);
                    break;
                case ILOAD:
                case FLOAD:
                case ALOAD:
                case RET:
                    loadOne(blockID, stream.readLocalIndex());
                    break;
                case ILOAD_0:
                case FLOAD_0:
                case ALOAD_0:
                    loadOne(blockID, 0);
                    break;
                case ILOAD_1:
                case FLOAD_1:
                case ALOAD_1:
                    loadOne(blockID, 1);
                    break;
                case ILOAD_2:
                case FLOAD_2:
                case ALOAD_2:
                    loadOne(blockID, 2);
                    break;
                case ILOAD_3:
                case FLOAD_3:
                case ALOAD_3:
                    loadOne(blockID, 3);
                    break;

                case LSTORE:
                case DSTORE:
                    storeTwo(blockID, stream.readLocalIndex());
                    break;
                case LSTORE_0:
                case DSTORE_0:
                    storeTwo(blockID, 0);
                    break;
                case LSTORE_1:
                case DSTORE_1:
                    storeTwo(blockID, 1);
                    break;
                case LSTORE_2:
                case DSTORE_2:
                    storeTwo(blockID, 2);
                    break;
                case LSTORE_3:
                case DSTORE_3:
                    storeTwo(blockID, 3);
                    break;
                case ISTORE:
                case FSTORE:
                case ASTORE:
                    storeOne(blockID, stream.readLocalIndex());
                    break;
                case ISTORE_0:
                case FSTORE_0:
                case ASTORE_0:
                    storeOne(blockID, 0);
                    break;
                case ISTORE_1:
                case FSTORE_1:
                case ASTORE_1:
                    storeOne(blockID, 1);
                    break;
                case ISTORE_2:
                case FSTORE_2:
                case ASTORE_2:
                    storeOne(blockID, 2);
                    break;
                case ISTORE_3:
                case FSTORE_3:
                case ASTORE_3:
                    storeOne(blockID, 3);
                    break;
            }
            stream.next();
        }
    }

    private void loadTwo(int blockID, int local) {
        loadOne(blockID, local);
        loadOne(blockID, local + 1);
    }

    private void storeTwo(int blockID, int local) {
        storeOne(blockID, local);
        storeOne(blockID, local + 1);
    }
}
