/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.java;

import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ALOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ASTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.DSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.FSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.IINC;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ILOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.ISTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LLOAD_3;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_0;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_1;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_2;
import static org.graalvm.compiler.bytecode.Bytecodes.LSTORE_3;
import static org.graalvm.compiler.bytecode.Bytecodes.RET;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.BciBlockMapping.BciBlock;

/**
 * Encapsulates the liveness calculation, so that subclasses for locals &le; 64 and locals &gt; 64
 * can be implemented.
 */
public abstract class LocalLiveness {
    protected final BciBlock[] blocks;
    private final ArrayList<ArrayList<Integer>> asyncSuccessors;

    public static LocalLiveness compute(DebugContext debug, BytecodeStream stream, BciBlockMapping mapping, int maxLocals, int loopCount, boolean asyncLiveness) {
        LocalLiveness liveness = maxLocals <= 64 ? new SmallLocalLiveness(mapping, maxLocals, loopCount, asyncLiveness) : new LargeLocalLiveness(mapping, maxLocals, loopCount, asyncLiveness);
        liveness.computeLiveness(debug, stream);
        return liveness;
    }

    protected LocalLiveness(BciBlockMapping mapping, boolean asyncLiveness) {
        if (asyncLiveness && mapping.exceptionHandlers.length > 0) {
            Pair<ArrayList<BciBlock>, ArrayList<ArrayList<Integer>>> info = generateAsyncLivenessInfo(mapping);
            this.blocks = info.getLeft().toArray(new BciBlock[0]);
            this.asyncSuccessors = info.getRight();
        } else {
            this.blocks = mapping.getBlocks();
            this.asyncSuccessors = null;
        }
    }

    /**
     * Asynchronous exceptions can occur from any bci within the block. Accordingly, all exception
     * handlers reachable from a block must be considered live for the entire block. In our
     * implementation, this is done by recording the {@link #asyncSuccessors} and always propagating
     * their liveness information into a block's liveIn.
     *
     * Asynchronous exceptions may also make blocks reachable that are not otherwise reached. If so,
     * then these newly reachable blocks must also be included as part of the liveness analysis.
     */
    private static Pair<ArrayList<BciBlock>, ArrayList<ArrayList<Integer>>> generateAsyncLivenessInfo(BciBlockMapping mapping) {
        ArrayList<BciBlock> blocks = new ArrayList<>(Arrays.asList(mapping.getBlocks()));
        ArrayList<ArrayList<Integer>> asyncSuccessors = new ArrayList<>();

        for (int id = 0; id < blocks.size(); id++) {
            BciBlock block = blocks.get(id);
            assert block.getId() == id;
            assert asyncSuccessors.size() == id;

            if (block.isInstructionBlock()) {
                /*
                 * Finding exceptions handlers which are reachable from an instruction block.
                 */
                BitSet handlerIDs = new BitSet();
                for (int bci = block.getStartBci(); bci <= block.getEndBci(); bci++) {
                    BitSet bciHandlerIDs = mapping.getBciExceptionHandlerIDs(bci);
                    if (bciHandlerIDs != null) {
                        handlerIDs.or(bciHandlerIDs);
                    }
                }

                /*
                 * Collecting handler blocks reachable from this block.
                 */
                ArrayList<Integer> handlerBlockIDs = new ArrayList<>();
                for (int handlerID = handlerIDs.nextSetBit(0); handlerID >= 0; handlerID = handlerIDs.nextSetBit(handlerID + 1)) {
                    BciBlock handlerBlock = mapping.getHandlerBlock(handlerID);

                    /* If handler isn't already reachable, then add to the end of list. */
                    if (handlerBlock.getId() == BciBlockMapping.UNASSIGNED_ID) {
                        int newID = blocks.size();
                        handlerBlock.setId(newID);
                        blocks.add(handlerBlock);
                    }

                    handlerBlockIDs.add(handlerBlock.getId());

                    if (handlerID == Integer.MAX_VALUE) {
                        break; // or (i+1) would overflow
                    }
                }

                asyncSuccessors.add(handlerBlockIDs.isEmpty() ? null : handlerBlockIDs);
            } else {
                /* Only consider async successors from instruction blocks. */
                asyncSuccessors.add(null);
            }

            /* Making sure all successors are reachable. If not, then add to the end of list. */
            for (BciBlock sux : block.getSuccessors()) {
                if (sux.getId() == BciBlockMapping.UNASSIGNED_ID) {
                    int newID = blocks.size();
                    sux.setId(newID);
                    blocks.add(sux);
                }
            }
        }

        return Pair.create(blocks, asyncSuccessors);
    }

    void computeLiveness(DebugContext debug, BytecodeStream stream) {
        for (BciBlock block : blocks) {
            computeLocalLiveness(stream, block);
        }

        boolean changed;
        int iteration = 0;
        do {
            assert traceIteration(debug, iteration);
            changed = false;
            for (int i = blocks.length - 1; i >= 0; i--) {
                BciBlock block = blocks[i];
                int blockID = block.getId();
                assert traceStart(debug, block, blockID);

                boolean blockChanged = (iteration == 0);
                if (block.getSuccessorCount() > 0) {
                    int oldCardinality = liveOutCardinality(blockID);
                    for (BciBlock sux : block.getSuccessors()) {
                        assert traceSuccessor(debug, sux);
                        propagateLiveness(blockID, sux.getId());
                    }
                    blockChanged |= (oldCardinality != liveOutCardinality(blockID));
                }

                if (asyncSuccessors != null && asyncSuccessors.get(i) != null) {
                    int oldCardinality = liveAsyncCardinality(blockID);
                    for (Integer suxId : asyncSuccessors.get(i)) {
                        propagateAsyncLiveness(blockID, suxId);
                    }
                    blockChanged |= (oldCardinality != liveAsyncCardinality(blockID));
                }

                if (blockChanged) {
                    updateLiveness(blockID);
                    assert traceEnd(debug, block, blockID);
                }
                changed |= blockChanged;
            }
            iteration++;
        } while (changed);
    }

    private static boolean traceIteration(DebugContext debug, int iteration) {
        debug.log("Iteration %d", iteration);
        return true;
    }

    private boolean traceEnd(DebugContext debug, BciBlock block, int blockID) {
        if (debug.isLogEnabled()) {
            debug.logv("  end   B%d  [%d, %d]  in: %s  out: %s  gen: %s  kill: %s", block.getId(), block.startBci, block.getEndBci(), debugLiveIn(blockID), debugLiveOut(blockID),
                            debugLiveGen(blockID),
                            debugLiveKill(blockID));
        }
        return true;
    }

    private boolean traceSuccessor(DebugContext debug, BciBlock sux) {
        if (debug.isLogEnabled()) {
            debug.log("    Successor B%d: %s", sux.getId(), debugLiveIn(sux.getId()));
        }
        return true;
    }

    private boolean traceStart(DebugContext debug, BciBlock block, int blockID) {
        if (debug.isLogEnabled()) {
            debug.logv("  start B%d  [%d, %d]  in: %s  out: %s  gen: %s  kill: %s", block.getId(), block.startBci, block.getEndBci(), debugLiveIn(blockID), debugLiveOut(blockID),
                            debugLiveGen(blockID),
                            debugLiveKill(blockID));
        }
        return true;
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
     * Returns the number of live async locals for the given block.
     */
    protected abstract int liveAsyncCardinality(int blockID);

    /**
     * Adds all locals that are in the liveIn of the successor to the liveOut of the block.
     */
    protected abstract void propagateLiveness(int blockID, int successorID);

    /**
     * Adds all locals that are in the liveIn of the successor to the liveAsync of the block.
     */
    protected abstract void propagateAsyncLiveness(int blockID, int successorID);

    /**
     * Calculates a new liveIn for the given block from liveOut, liveKill, liveGen and liveAsync.
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
        if (!block.isInstructionBlock()) {
            return;
        }
        int blockID = block.getId();
        int localIndex;
        stream.setBCI(block.startBci);
        while (stream.currentBCI() <= block.getEndBci()) {
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
