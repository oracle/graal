/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.graph;

import static com.sun.cri.bytecode.Bytecodes.*;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.util.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Builds a mapping between bytecodes and basic blocks and builds a conservative control flow
 * graph. Note that this class serves a similar role to C1's {@code BlockListBuilder}, but makes fewer assumptions about
 * what the compiler interface provides. It builds all basic blocks for the control flow graph without requiring the
 * compiler interface to provide a bitmap of the beginning of basic blocks. It makes two linear passes; one over the
 * bytecodes to build block starts and successor lists, and one pass over the block map to build the CFG.
 *
 * Note that the CFG built by this class is <i>not</i> connected to the actual {@code BlockBegin} instances; this class
 * does, however, compute and assign the reverse postorder number of the blocks. This comment needs refinement. (MJJ)
 *
 * <H2>More Details on {@link BlockMap#build}</H2>
 *
 * If the method has any exception handlers the {@linkplain #exceptionMap exception map} will be created (TBD).
 *
 * A {@link BlockBegin} node with the {@link BlockFlag#StandardEntry} flag is created with bytecode index 0.
 * Note this is distinct from the similar {@link BlockBegin} node assigned to {@link IR#startBlock} by
 * {@link GraphBuilder}.
 *
 * The bytecodes are then scanned linearly looking for bytecodes that contain control transfers, e.g., {@code GOTO},
 * {@code RETURN}, {@code IFGE}, and creating the corresponding entries in {@link #successorMap} and {@link #blockMap}.
 * In addition, if {@link #exceptionMap} is not null, entries are made for any bytecode that can cause an exception.
 * More TBD.
 *
 * Observe that this process finds bytecodes that terminate basic blocks, so the {@link #moveSuccessorLists} method is
 * called to reassign the successors to the {@code BlockBegin} node that actually starts the block.
 *
 * <H3>Example</H3>
 *
 * Consider the following source code:
 *
 * <pre>
 * <code>
 *     public static int test(int arg1, int arg2) {
 *         int x = 0;
 *         while (arg2 > 0) {
 *             if (arg1 > 0) {
 *                 x += 1;
 *             } else if (arg1 < 0) {
 *                 x -= 1;
 *             }
 *         }
 *         return x;
 *     }
 * </code>
 * </pre>
 *
 * This is translated by javac to the following bytecode:
 *
 * <pre>
 * <code>
 *    0:   iconst_0
 *    1:   istore_2
 *    2:   goto    22
 *    5:   iload_0
 *    6:   ifle    15
 *    9:   iinc    2, 1
 *    12:  goto    22
 *    15:  iload_0
 *    16:  ifge    22
 *    19:  iinc    2, -1
 *    22:  iload_1
 *    23:  ifgt    5
 *    26:  iload_2
 *    27:  ireturn
 *    </code>
 * </pre>
 *
 * There are seven basic blocks in this method, 0..2, 5..6, 9..12, 15..16, 19..19, 22..23 and 26..27. Therefore, before
 * the call to {@code moveSuccessorLists}, the {@code blockMap} array has {@code BlockBegin} nodes at indices 0, 5, 9,
 * 15, 19, 22 and 26. The {@code successorMap} array has entries at 2, 6, 12, 16, 23, 27 corresponding to the control
 * transfer bytecodes. The entry at index 6, for example, is a length two array of {@code BlockBegin} nodes for indices
 * 9 and 15, which are the successors for the basic block 5..6. After the call to {@code moveSuccessors}, {@code
 * successorMap} has entries at 0, 5, 9, 15, 19, 22 and 26, i.e, matching {@code blockMap}.
 * <p>
 * Next the blocks are numbered using <a href="http://en.wikipedia.org/wiki/Depth-first_search#Vertex_orderings">reverse
 * post-order</a>. For the above example this results in the numbering 2, 4, 7, 5, 6, 3, 8. Also loop header blocks are
 * detected during the traversal by detecting a repeat visit to a block that is still being processed. This causes the
 * block to be flagged as a loop header and also added to the {@link #loopBlocks} list. The {@code loopBlocks} list
 * contains the blocks at 0, 5, 9, 15, 19, 22, with 22 as the loop header. (N.B. the loop header block is added multiple
 * (4) times to this list). (Should 0 be in? It's not inside the loop).
 *
 * If the {@code computeStoresInLoops} argument to {@code build} is true, the {@code loopBlocks} list is processed to
 * mark all local variables that are stored in the blocks in the list.
 *
 * @author Ben L. Titzer
 */
public final class BlockMap {

    private static final BlockBegin[] NONE = {};
    private static final List<BlockBegin> NONE_LIST = Collections.emptyList();

    /**
     * The {@code ExceptionMap} class is used internally to track exception handlers
     * while iterating over the bytecode and the control flow graph. Since methods with
     * exception handlers are much less frequent than those without, the common case
     * does not need to construct an exception map.
     */
    private class ExceptionMap {
        private final CiBitMap canTrap;
        private final boolean isObjectInit;
        private final RiExceptionHandler[] allHandlers;
        private final ArrayMap<HashSet<BlockBegin>> handlerMap;

        ExceptionMap(RiMethod method, byte[] code) {
            canTrap = new CiBitMap(code.length);
            isObjectInit = C1XIntrinsic.getIntrinsic(method) == C1XIntrinsic.java_lang_Object$init;
            allHandlers = method.exceptionHandlers();
            handlerMap = new ArrayMap<HashSet<BlockBegin>>(firstBlock, firstBlock + code.length / 5);
        }

        void setCanTrap(int bci) {
            canTrap.set(bci);
        }

        void addHandlers(BlockBegin block, int bci) {
            if (canTrap.get(bci)) {
                // XXX: replace with faster algorithm (sort exception handlers by start and end)
                for (RiExceptionHandler h : allHandlers) {
                    if (h.startBCI() <= bci && bci < h.endBCI()) {
                        addHandler(block, get(h.handlerBCI()));
                        if (h.isCatchAll()) {
                            break;
                        }
                    }
                }
            }
        }

        Collection<BlockBegin> getHandlers(BlockBegin block) {
            // lookup handlers for the basic block
            HashSet<BlockBegin> set = handlerMap.get(block.blockID);
            return set == null ? NONE_LIST : set;
        }

        void setHandlerEntrypoints() {
            // start basic blocks at all exception handler blocks and mark them as exception entries
            for (RiExceptionHandler h : allHandlers) {
                addEntrypoint(h.handlerBCI(), BlockBegin.BlockFlag.ExceptionEntry);
            }
        }

        void addHandler(BlockBegin block, BlockBegin handler) {
            // add a handler to a basic block, creating the set if necessary
            HashSet<BlockBegin> set = handlerMap.get(block.blockID);
            if (set == null) {
                set = new HashSet<BlockBegin>();
                handlerMap.put(block.blockID, set);
            }
            set.add(handler);
        }
    }

    /** The bytecodes for the associated method. */
    private final byte[] code;

    /**
     * Every {@link BlockBegin} node created by {@link BlockMap#build} has an entry in this
     * array at the corresponding bytecode index. Length is same as {@link BlockMap#code}.
     */
    private final BlockBegin[] blockMap;

    /**
     * A bit map covering the locals with a bit set for each local that is
     * stored to within a loop. This may be conservative depending on the value
     * of the {@code computeStoresInLoops} parameters of {@link #build(boolean)}.
     */
    private final CiBitMap storesInLoops;

    /**
     * Every bytecode instruction that has zero, one or more successor nodes (e.g. {@link Bytecodes#GOTO} has one) has
     * an entry in this array at the corresponding bytecode index. The value is another array of {@code BlockBegin} nodes,
     * with length equal to the number of successors, whose entries are the {@code BlockBegin} nodes for the successor
     * blocks. Length is same as {@link BlockMap#code}.
     */
    private BlockBegin[][] successorMap;

    /** List of {@code BlockBegin} nodes that are inside loops. */
    private ArrayList<BlockBegin> loopBlocks;
    private ExceptionMap exceptionMap;

    /**
     * The first block number allocated for the blocks within this block map.
     */
    private final int firstBlock;

    /**
     * Used for initial block ID (count up) and post-order number (count down).
     */
    private int blockNum;

    /**
     * Creates a new BlockMap instance from bytecode of the given method .
     * @param method the compiler interface method containing the code
     * @param firstBlockNum the first block number to use when creating {@link BlockBegin} nodes
     */
    public BlockMap(RiMethod method, int firstBlockNum) {
        byte[] code = method.code();
        this.code = code;
        firstBlock = firstBlockNum;
        blockNum = firstBlockNum;
        blockMap = new BlockBegin[code.length];
        successorMap = new BlockBegin[code.length][];
        storesInLoops = new CiBitMap(method.maxLocals());
        if (method.exceptionHandlers().length != 0) {
            exceptionMap = new ExceptionMap(method, code);
        }
    }

    /**
     * Add an entrypoint to this BlockMap. The resulting block will be marked
     * with the specified block flags.
     * @param bci the bytecode index of the start of the block
     * @param entryFlag the entry flag to mark the block with
     */
    public void addEntrypoint(int bci, BlockBegin.BlockFlag entryFlag) {
        make(bci).setBlockFlag(entryFlag);
    }

    /**
     * Gets the block that begins at the specified bytecode index.
     * @param bci the bytecode index of the start of the block
     * @return the block starting at the specified index, if it exists; {@code null} otherwise
     */
    public BlockBegin get(int bci) {
        if (bci < blockMap.length) {
            return blockMap[bci];
        }
        return null;
    }

    BlockBegin make(int bci) {
        BlockBegin block = blockMap[bci];
        if (block == null) {
            block = new BlockBegin(bci, blockNum++);
            blockMap[bci] = block;
        }
        return block;
    }

    /**
     * Gets a conservative approximation of the successors of a given block.
     * @param block the block for which to get the successors
     * @return an array of the successors of the specified block
     */
    public BlockBegin[] getSuccessors(BlockBegin block) {
        BlockBegin[] succ = successorMap[block.bci()];
        return succ == null ? NONE : succ;
    }

    /**
     * Gets the exception handlers for a specified block. Note that this
     * set of exception handlers takes into account whether the block contains
     * bytecodes that can cause traps or not.
     * @param block the block for which to get the exception handlers
     * @return an array of the blocks which represent exception handlers; a zero-length
     * array of blocks if there are no handlers that cover any potentially trapping
     * instruction in the specified block
     */
    public Collection<BlockBegin> getHandlers(BlockBegin block) {
        if (exceptionMap == null) {
            return NONE_LIST;
        }
        return exceptionMap.getHandlers(block);
    }

    /**
     * Builds the block map and conservative CFG and numbers blocks.
     * @param computeStoresInLoops {@code true} if the block map builder should
     * make a second pass over the bytecodes for blocks in loops
     * @return {@code true} if the block map was built successfully; {@code false} otherwise
     */
    public boolean build(boolean computeStoresInLoops) {
        if (exceptionMap != null) {
            exceptionMap.setHandlerEntrypoints();
        }
        iterateOverBytecodes();
        moveSuccessorLists();
        computeBlockNumbers();
        if (computeStoresInLoops) {
            // process any blocks in loops to compute their stores
            // (requires another pass, but produces fewer phi's and ultimately better code)
            processLoopBlocks();
        } else {
            // be conservative and assume all locals are potentially stored in loops
            // (does not require another pass, but produces more phi's and worse code)
            storesInLoops.setAll();
        }
        return true; // XXX: what bailout conditions should the BlockMap check?
    }

    /**
     * Cleans up any internal state not necessary after the initial pass. Note that
     * this method discards the conservative CFG edges and only retains the block mapping
     * and stores in loops.
     */
    public void cleanup() {
        // discard internal state no longer needed
        successorMap = null;
        loopBlocks = null;
        exceptionMap = null;
    }

    /**
     * Gets the number of blocks in this block map.
     * @return the number of blocks
     */
    public int numberOfBlocks() {
        return blockNum - firstBlock;
    }

    public int numberOfBytes() {
        return code.length;
    }

    /**
     * Gets the bitmap that indicates which local variables are assigned in loops.
     * @return a bitmap which indicates the locals stored in loops
     */
    public CiBitMap getStoresInLoops() {
        return storesInLoops;
    }

    void iterateOverBytecodes() {
        // iterate over the bytecodes top to bottom.
        // mark the entrypoints of basic blocks and build lists of successors for
        // all bytecodes that end basic blocks (i.e. goto, ifs, switches, throw, jsr, returns, ret)
        int bci = 0;
        ExceptionMap exceptionMap = this.exceptionMap;
        byte[] code = this.code;
        make(0).setStandardEntry();
        while (bci < code.length) {
            int opcode = Bytes.beU1(code, bci);
            switch (opcode) {
                case ATHROW:
                    if (exceptionMap != null) {
                        exceptionMap.setCanTrap(bci);
                    }
                    // fall through
                case IRETURN: // fall through
                case LRETURN: // fall through
                case FRETURN: // fall through
                case DRETURN: // fall through
                case ARETURN: // fall through
                case WRETURN: // fall through
                case RETURN:
                    if (exceptionMap != null && exceptionMap.isObjectInit) {
                        exceptionMap.setCanTrap(bci);
                    }
                    successorMap[bci] = NONE; // end of control flow
                    bci += 1; // these are all 1 byte opcodes
                    break;

                case RET:
                    successorMap[bci] = NONE; // end of control flow
                    bci += 2; // ret is 2 bytes
                    break;

                case IFEQ:      // fall through
                case IFNE:      // fall through
                case IFLT:      // fall through
                case IFGE:      // fall through
                case IFGT:      // fall through
                case IFLE:      // fall through
                case IF_ICMPEQ: // fall through
                case IF_ICMPNE: // fall through
                case IF_ICMPLT: // fall through
                case IF_ICMPGE: // fall through
                case IF_ICMPGT: // fall through
                case IF_ICMPLE: // fall through
                case IF_ACMPEQ: // fall through
                case IF_ACMPNE: // fall through
                case IFNULL:    // fall through
                case IFNONNULL: {
                    succ2(bci, bci + 3, bci + Bytes.beS2(code, bci + 1));
                    bci += 3; // these are all 3 byte opcodes
                    break;
                }

                case GOTO: {
                    succ1(bci, bci + Bytes.beS2(code, bci + 1));
                    bci += 3; // goto is 3 bytes
                    break;
                }

                case GOTO_W: {
                    succ1(bci, bci + Bytes.beS4(code, bci + 1));
                    bci += 5; // goto_w is 5 bytes
                    break;
                }

                case JSR: {
                    int target = bci + Bytes.beS2(code, bci + 1);
                    succ2(bci, bci + 3, target); // make JSR's a successor or not?
                    addEntrypoint(target, BlockBegin.BlockFlag.SubroutineEntry);
                    bci += 3; // jsr is 3 bytes
                    break;
                }

                case JSR_W: {
                    int target = bci + Bytes.beS4(code, bci + 1);
                    succ2(bci, bci + 5, target);
                    addEntrypoint(target, BlockBegin.BlockFlag.SubroutineEntry);
                    bci += 5; // jsr_w is 5 bytes
                    break;
                }

                case TABLESWITCH: {
                    BytecodeSwitch sw = new BytecodeTableSwitch(code, bci);
                    makeSwitchSuccessors(bci, sw);
                    bci += sw.size();
                    break;
                }

                case LOOKUPSWITCH: {
                    BytecodeSwitch sw = new BytecodeLookupSwitch(code, bci);
                    makeSwitchSuccessors(bci, sw);
                    bci += sw.size();
                    break;
                }
                case WIDE: {
                    bci += lengthOf(code, bci);
                    break;
                }

                default: {
                    if (exceptionMap != null && canTrap(opcode)) {
                        exceptionMap.setCanTrap(bci);
                    }
                    bci += lengthOf(opcode); // all variable length instructions are handled above
                }
            }
        }
    }

    private void makeSwitchSuccessors(int bci, BytecodeSwitch tswitch) {
        // make a list of all the successors of a switch
        int max = tswitch.numberOfCases();
        ArrayList<BlockBegin> list = new ArrayList<BlockBegin>(max + 1);
        for (int i = 0; i < max; i++) {
            list.add(make(tswitch.targetAt(i)));
        }
        list.add(make(tswitch.defaultTarget()));
        successorMap[bci] = list.toArray(new BlockBegin[list.size()]);
    }

    private void moveSuccessorLists() {
        // move successor lists from the block-ending bytecodes that created them
        // to the basic blocks which they end.
        // also handle fall-through cases from backwards branches into the middle of a block
        // add exception handlers to basic blocks
        BlockBegin current = get(0);
        ExceptionMap exceptionMap = this.exceptionMap;
        for (int bci = 0; bci < blockMap.length; bci++) {
            BlockBegin next = blockMap[bci];
            if (next != null && next != current) {
                if (current != null) {
                    // add fall through successor to current block
                    successorMap[current.bci()] = new BlockBegin[] {next};
                }
                current = next;
            }
            if (exceptionMap != null) {
                exceptionMap.addHandlers(current, bci);
            }
            BlockBegin[] succ = successorMap[bci];
            if (succ != null && current != null) {
                // move this successor list to current block
                successorMap[bci] = null;
                successorMap[current.bci()] = succ;
                current = null;
            }
        }
        assert current == null : "fell off end of code, should end with successor list";
    }

    private void computeBlockNumbers() {
        // compute the block number for all blocks
        int blockNum = this.blockNum;
        int numBlocks = blockNum - firstBlock;
        numberBlock(get(0), new CiBitMap(numBlocks), new CiBitMap(numBlocks));
        this.blockNum = blockNum; // _blockNum is used to compute the number of blocks later
    }

    private boolean numberBlock(BlockBegin block, CiBitMap visited, CiBitMap active) {
        // number a block with its reverse post-order traversal number
        int blockIndex = block.blockID - firstBlock;

        if (visited.get(blockIndex)) {
            if (active.get(blockIndex)) {
                // reached block via backward branch
                block.setParserLoopHeader(true);
                addLoopBlock(block);
                return true;
            }
            // return whether the block is already a loop header
            return block.isParserLoopHeader();
        }

        visited.set(blockIndex);
        active.set(blockIndex);

        boolean inLoop = false;
        for (BlockBegin succ : getSuccessors(block)) {
            // recursively process successors
            inLoop |= numberBlock(succ, visited, active);
        }
        if (exceptionMap != null) {
            for (BlockBegin succ : exceptionMap.getHandlers(block)) {
                // process exception handler blocks
                inLoop |= numberBlock(succ, visited, active);
            }
        }
        // clear active bit after successors are processed
        active.clear(blockIndex);
        block.setDepthFirstNumber(blockNum--);
        if (inLoop) {
            addLoopBlock(block);
        }

        return inLoop;
    }

    private void addLoopBlock(BlockBegin block) {
        if (loopBlocks == null) {
            loopBlocks = new ArrayList<BlockBegin>();
        }
        loopBlocks.add(block);
    }

    private void processLoopBlocks() {
        if (loopBlocks == null) {
            return;
        }
        for (BlockBegin block : loopBlocks) {
            // process all the stores in this block
            int bci = block.bci();
            byte[] code = this.code;
            while (true) {
                // iterate over the bytecodes in this block
                int opcode = code[bci] & 0xff;
                if (opcode == WIDE) {
                    bci += processWideStore(code[bci + 1] & 0xff, code, bci);
                } else if (isStore(opcode)) {
                    bci += processStore(opcode, code, bci);
                } else {
                    bci += lengthOf(code, bci);
                }
                if (bci >= code.length || blockMap[bci] != null) {
                    // stop when we reach the next block
                    break;
                }
            }
        }
    }

    private int processWideStore(int opcode, byte[] code, int bci) {
        switch (opcode) {
            case IINC:     storeOne(Bytes.beU2(code, bci + 2)); return 6;
            case ISTORE:   storeOne(Bytes.beU2(code, bci + 2)); return 3;
            case LSTORE:   storeTwo(Bytes.beU2(code, bci + 2)); return 3;
            case FSTORE:   storeOne(Bytes.beU2(code, bci + 2)); return 3;
            case DSTORE:   storeTwo(Bytes.beU2(code, bci + 2)); return 3;
            case ASTORE:   storeOne(Bytes.beU2(code, bci + 2)); return 3;
        }
        return lengthOf(code, bci);
    }

    private int processStore(int opcode, byte[] code, int bci) {
        switch (opcode) {
            case IINC:     storeOne(code[bci + 1] & 0xff); return 3;
            case ISTORE:   storeOne(code[bci + 1] & 0xff); return 2;
            case LSTORE:   storeTwo(code[bci + 1] & 0xff); return 2;
            case FSTORE:   storeOne(code[bci + 1] & 0xff); return 2;
            case DSTORE:   storeTwo(code[bci + 1] & 0xff); return 2;
            case WSTORE:
            case ASTORE:   storeOne(code[bci + 1] & 0xff); return 2;
            case ISTORE_0: // fall through
            case ISTORE_1: // fall through
            case ISTORE_2: // fall through
            case ISTORE_3: storeOne(opcode - ISTORE_0); return 1;
            case LSTORE_0: // fall through
            case LSTORE_1: // fall through
            case LSTORE_2: // fall through
            case LSTORE_3: storeTwo(opcode - LSTORE_0); return 1;
            case FSTORE_0: // fall through
            case FSTORE_1: // fall through
            case FSTORE_2: // fall through
            case FSTORE_3: storeOne(opcode - FSTORE_0); return 1;
            case DSTORE_0: // fall through
            case DSTORE_1: // fall through
            case DSTORE_2: // fall through
            case DSTORE_3: storeTwo(opcode - DSTORE_0); return 1;
            case ASTORE_0: // fall through
            case ASTORE_1: // fall through
            case ASTORE_2: // fall through
            case ASTORE_3: storeOne(opcode - ASTORE_0); return 1;
            case WSTORE_0: // fall through
            case WSTORE_1: // fall through
            case WSTORE_2: // fall through
            case WSTORE_3: storeOne(opcode - WSTORE_0); return 1;
        }
        throw Util.shouldNotReachHere();
    }

    private void storeOne(int local) {
        storesInLoops.set(local);
    }

    private void storeTwo(int local) {
        storesInLoops.set(local);
        storesInLoops.set(local + 1);
    }

    private void succ2(int bci, int s1, int s2) {
        successorMap[bci] = new BlockBegin[] {make(s1), make(s2)};
    }

    private void succ1(int bci, int s1) {
        successorMap[bci] = new BlockBegin[] {make(s1)};
    }

    private static StringBuilder append(StringBuilder sb, BlockBegin block) {
        return sb.append('B').append(block.blockID).append('@').append(block.bci());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int bci = 0; bci < blockMap.length; ++bci) {
            BlockBegin block = blockMap[bci];
            if (block != null) {
                append(sb, block);
                if (loopBlocks != null && loopBlocks.contains(block)) {
                    sb.append("{loop-header}");
                }
                if (successorMap != null) {
                    BlockBegin[] succs = successorMap[bci];
                    if (succs != null && succs.length > 0) {
                        sb.append(" ->");
                        for (BlockBegin succ : succs) {
                            append(sb.append(' '), succ);
                        }
                    }
                }
                Collection<BlockBegin> handlers = getHandlers(block);
                if (!handlers.isEmpty()) {
                    sb.append(" xhandlers{");
                    for (BlockBegin h : handlers) {
                        append(sb, h).append(' ');
                    }
                    sb.append('}');
                }
                sb.append(String.format("%n"));
            }
        }
        return sb.toString();
    }
}
