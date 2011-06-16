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
package com.oracle.max.graal.compiler.graph;

import static com.sun.cri.bytecode.Bytecodes.*;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.value.*;
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
 */
public final class BlockMap {
    public static class Block {
        public int startBci;
        public int endBci;
        public boolean isExceptionEntry;
        public boolean isLoopHeader;
        public int blockID;

        public Instruction firstInstruction;

        final HashSet<Block> successors = new HashSet<Block>();
        private boolean visited;
        private boolean active;
        private int loops;
    }

    public static class ExceptionBlock  extends Block {
        public RiExceptionHandler handler;
        public Block next;
    }

    public static class DeoptBlock  extends Block {
    }

    public static class BranchOverride {
        public DeoptBlock block;
        public boolean taken;
    }

    private static final Block[] NO_SUCCESSORS = new Block[0];

    /**
     * The blocks found in this method, in reverse postorder.
     */
    public final List<Block> blocks;

    /**
     * A bit map covering the locals with a bit set for each local that might be stored to within a
     * loop. If the bit is cleared, it is guaranteed that the local is never stored in a loop.
     */
    public final BitSet storesInLoops;

    private final RiMethod method;

    public final HashMap<Integer, BranchOverride> branchOverride;

    private Block[] blockMap;

    private BitSet canTrap;

    /**
     * Creates a new BlockMap instance from bytecode of the given method .
     * @param method the compiler interface method containing the code
     */
    public BlockMap(RiMethod method) {
        this.method = method;
        this.blockMap = new Block[method.code().length];
        if (method.exceptionHandlers().length != 0) {
            this.canTrap = new BitSet(blockMap.length);
        }
        this.blocks = new ArrayList<Block>();
        this.storesInLoops = new BitSet(method.maxLocals());
        branchOverride = new HashMap<Integer, BranchOverride>();
    }

    /**
     * Builds the block map and conservative CFG and numbers blocks.
     */
    public void build() {
        makeExceptionEntries();
        iterateOverBytecodes();
        addExceptionEdges();
        computeBlockOrder();

        initializeBlockIds();

        // Discard big arrays so that they can be GCed
        blockMap = null;
        canTrap = null;
    }

    private void initializeBlockIds() {
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).blockID = i;
        }
    }

    private void makeExceptionEntries() {
        // start basic blocks at all exception handler blocks and mark them as exception entries
        for (RiExceptionHandler h : method.exceptionHandlers()) {
            Block xhandler = makeBlock(h.handlerBCI());
            xhandler.isExceptionEntry = true;
        }
    }

    private void iterateOverBytecodes() {
        // iterate over the bytecodes top to bottom.
        // mark the entrypoints of basic blocks and build lists of successors for
        // all bytecodes that end basic blocks (i.e. goto, ifs, switches, throw, jsr, returns, ret)
        byte[] code = method.code();
        Block current = null;
        int bci = 0;
        while (bci < code.length) {
            if (current == null || blockMap[bci] != null) {
                Block b = makeBlock(bci);
                if (current != null) {
                    setSuccessors(current.endBci, b);
                }
                current = b;
            }
            blockMap[bci] = current;
            current.endBci = bci;

            int opcode = Bytes.beU1(code, bci);
            switch (opcode) {
                case IRETURN: // fall through
                case LRETURN: // fall through
                case FRETURN: // fall through
                case DRETURN: // fall through
                case ARETURN: // fall through
                case WRETURN: // fall through
                case RETURN: {
                    current = null;

                    assert lengthOf(code, bci) == 1;
                    bci += 1;
                    break;
                }

                case ATHROW: {
                    current = null;
                    if (canTrap != null) {
                        canTrap.set(bci);
                    }

                    assert lengthOf(code, bci) == 1;
                    bci += 1;
                    break;
                }

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
                    current = null;

                    int probability = GraalOptions.UseBranchPrediction ? method.branchProbability(bci) : -1;

                    Block b1 = probability == 100 ? makeBranchOverrideBlock(bci, bci + 3, false) : makeBlock(bci + 3);
                    Block b2 = probability == 0 ? makeBranchOverrideBlock(bci, bci + Bytes.beS2(code, bci + 1), true) : makeBlock(bci + Bytes.beS2(code, bci + 1));
                    setSuccessors(bci, b1, b2);

                    assert lengthOf(code, bci) == 3;
                    bci += 3;
                    break;
                }

                case GOTO: {
                    current = null;
                    Block b1 = makeBlock(bci + Bytes.beS2(code, bci + 1));
                    setSuccessors(bci, b1);

                    assert lengthOf(code, bci) == 3;
                    bci += 3;
                    break;
                }

                case GOTO_W: {
                    current = null;
                    Block b1 = makeBlock(bci + Bytes.beS4(code, bci + 1));
                    setSuccessors(bci, b1);

                    assert lengthOf(code, bci) == 5;
                    bci += 5;
                    break;
                }

                case TABLESWITCH: {
                    BytecodeTableSwitch sw = new BytecodeTableSwitch(code, bci);
                    setSuccessors(bci, makeSwitchSuccessors(sw));
                    current = null;

                    assert lengthOf(code, bci) == sw.size();
                    bci += sw.size();
                    break;
                }

                case LOOKUPSWITCH: {
                    current = null;
                    BytecodeLookupSwitch sw = new BytecodeLookupSwitch(code, bci);
                    setSuccessors(bci, makeSwitchSuccessors(sw));

                    assert lengthOf(code, bci) == sw.size();
                    bci += sw.size();
                    break;
                }

                case JSR: {
                    throw new JSRNotSupportedBailout();
                }
                case JSR_W: {
                    throw new JSRNotSupportedBailout();
                }
                case RET: {
                    throw new JSRNotSupportedBailout();
                }

                case WIDE: {
                    bci += lengthOf(code, bci);
                    break;
                }

                default: {
                    if (canTrap != null && canTrap(opcode)) {
                        canTrap.set(bci);
                    }

                    assert lengthOf(code, bci) == lengthOf(opcode);
                    bci += lengthOf(opcode);
                }
            }
        }
    }

    public static boolean canTrap(int opcode) {
        switch (opcode) {
            case INVOKESTATIC:
            case INVOKESPECIAL:
            case INVOKEVIRTUAL:
            case INVOKEINTERFACE: {
                return true;
            }
        }
        return false;
    }

    private Block makeBlock(int startBci) {
        Block oldBlock = blockMap[startBci];
        if (oldBlock == null) {
            Block newBlock = new Block();
            newBlock.startBci = startBci;
            blockMap[startBci] = newBlock;
            return newBlock;

        } else if (oldBlock.startBci != startBci) {
            // Backward branch into the middle of an already processed block.
            // Add the correct fall-through successor.
            Block newBlock = new Block();
            newBlock.startBci = startBci;
            newBlock.endBci = oldBlock.endBci;
            newBlock.successors.addAll(oldBlock.successors);

            oldBlock.endBci = startBci - 1;
            oldBlock.successors.clear();
            oldBlock.successors.add(newBlock);

            for (int i = startBci; i <= newBlock.endBci; i++) {
                blockMap[i] = newBlock;
            }
            return newBlock;

        } else {
            return oldBlock;
        }
    }

    private Block makeBranchOverrideBlock(int branchBci, int startBci, boolean taken) {
        DeoptBlock newBlock = new DeoptBlock();
        newBlock.startBci = startBci;
        BranchOverride override = new BranchOverride();
        override.block = newBlock;
        override.taken = taken;
        assert branchOverride.get(branchBci) == null;
        branchOverride.put(branchBci, override);
        return newBlock;
    }

    private Block[] makeSwitchSuccessors(BytecodeSwitch tswitch) {
        int max = tswitch.numberOfCases();
        Block[] successors = new Block[max + 1];
        for (int i = 0; i < max; i++) {
            successors[i] = makeBlock(tswitch.targetAt(i));
        }
        successors[max] = makeBlock(tswitch.defaultTarget());
        return successors;
    }

    private void setSuccessors(int predBci, Block... successors) {
        for (Block sux : successors) {
            if (sux.isExceptionEntry) {
                throw new CiBailout("Exception handler can be reached by both normal and exceptional control flow");
            }
        }
        Block predecessor = blockMap[predBci];
        assert predecessor.successors.size() == 0;
        predecessor.successors.addAll(Arrays.asList(successors));
    }

    private HashMap<RiExceptionHandler, ExceptionBlock> exceptionDispatch = new HashMap<RiExceptionHandler, ExceptionBlock>();

    private ExceptionBlock unwindBlock;

    private Block makeExceptionDispatch(List<RiExceptionHandler> handlers, int index) {
        RiExceptionHandler handler = handlers.get(index);
        if (handler.isCatchAll()) {
            return blockMap[handler.handlerBCI()];
        }
        ExceptionBlock block = exceptionDispatch.get(handler);
        if (block == null) {
            block = new ExceptionBlock();
            block.startBci = -1;
            block.endBci = -1;
            block.handler = handler;
            block.successors.add(blockMap[handler.handlerBCI()]);
            if (index < handlers.size() - 1) {
                block.next = makeExceptionDispatch(handlers, index + 1);
                block.successors.add(block.next);
            }
            exceptionDispatch.put(handler, block);
        }
        return block;
    }

    private void addExceptionEdges() {
        if (canTrap == null) {
            return;
        }

        for (int bci = canTrap.nextSetBit(0); bci >= 0; bci = canTrap.nextSetBit(bci + 1)) {
            Block block = blockMap[bci];

            ArrayList<RiExceptionHandler> handlers = null;
            for (RiExceptionHandler h : method.exceptionHandlers()) {
                if (h.startBCI() <= bci && bci < h.endBCI()) {
                    if (handlers == null) {
                        handlers = new ArrayList<RiExceptionHandler>();
                    }
                    handlers.add(h);
                    if (h.isCatchAll()) {
                        break;
                    }
                }
            }
            if (handlers != null) {
                Block dispatch = makeExceptionDispatch(handlers, 0);
                block.successors.add(dispatch);
            }
        }
    }

    private void computeBlockOrder() {
        int loop = computeBlockOrder(blockMap[0]);

        if (loop != 0) {
            // There is a path from a loop end to the method entry that does not pass the loop header.
            // Therefore, the loop is non reducible (has more than one entry).
            // We don't want to compile such methods because the IR only supports structured loops.
            throw new CiBailout("Non-reducible loop");
        }

        // Convert postorder to the desired reverse postorder.
        Collections.reverse(blocks);
    }

    /**
     * The next available loop number.
     */
    private int nextLoop = 0;

    /**
     * Mark the block as a loop header, using the next available loop number.
     * Also checks for corner cases that we don't want to compile.
     */
    private void makeLoopHeader(Block block) {
        if (!block.isLoopHeader) {
            block.isLoopHeader = true;

            if (block.isExceptionEntry) {
                // Loops that are implicitly formed by an exception handler lead to all sorts of corner cases.
                // Don't compile such methods for now, until we see a concrete case that allows checking for correctness.
                throw new CiBailout("Loop formed by an exception handler");
            }
            if (nextLoop >= Integer.SIZE) {
                // This restriction can be removed by using a fall-back to a BitSet in case we have more than 32 loops
                // Don't compile such methods for now, until we see a concrete case that allows checking for correctness.
                throw new CiBailout("Too many loops in method");
            }

            assert block.loops == 0;
            block.loops = 1 << nextLoop;
            nextLoop++;
        }
        assert Integer.bitCount(block.loops) == 1;
    }

    /**
     * Depth-first traversal of the control flow graph. The flag {@linkplain Block#visited} is used to
     * visit every block only once. The flag {@linkplain Block#active} is used to detect cycles (backward
     * edges).
     */
    private int computeBlockOrder(Block block) {
        if (block.visited) {
            if (block.active) {
                // Reached block via backward branch.
                makeLoopHeader(block);
            }
            // Return cached loop information for this block.
            return block.loops;
        }

        block.visited = true;
        block.active = true;

        int loops = 0;
        for (Block successor : block.successors) {
            // Recursively process successors.
            loops |= computeBlockOrder(successor);
        }

        if (loops != 0) {
            processLoopBlock(block);
        }
        if (block.isLoopHeader) {
            assert Integer.bitCount(block.loops) == 1;
            loops &= ~block.loops;
        }

        block.loops = loops;
        block.active = false;
        blocks.add(block);

        return loops;
    }

    private void processLoopBlock(Block block) {
        // process all the stores in this block
        byte[] code = method.code();
        int bci = block.startBci;
        if (bci >= 0) {
            while (bci <= block.endBci) {
                int opcode = Bytes.beU1(code, bci);
                if (isStore(opcode)) {
                    processStore(opcode, Bytes.beU1(code, bci + 1));

                } else if (opcode == WIDE) {
                    opcode = Bytes.beU1(code, bci + 1);
                    if (isStore(opcode)) {
                        processStore(opcode, Bytes.beU2(code, bci + 2));
                    }
                }
                bci += lengthOf(code, bci);
            }
        }
    }

    private void processStore(int opcode, int local) {
        switch (opcode) {
            case IINC:
            case ISTORE:
            case FSTORE:
            case WSTORE:
            case ASTORE:
                storesInLoops.set(local);
                break;

            case LSTORE:
            case DSTORE:
                storesInLoops.set(local);
                storesInLoops.set(local + 1);
                break;

            case ISTORE_0:
            case FSTORE_0:
            case ASTORE_0:
            case WSTORE_0:
                storesInLoops.set(0);
                break;
            case ISTORE_1:
            case FSTORE_1:
            case ASTORE_1:
            case WSTORE_1:
                storesInLoops.set(1);
                break;
            case ISTORE_2:
            case FSTORE_2:
            case ASTORE_2:
            case WSTORE_2:
                storesInLoops.set(2);
                break;
            case ISTORE_3:
            case FSTORE_3:
            case ASTORE_3:
            case WSTORE_3:
                storesInLoops.set(3);
                break;

            case LSTORE_0:
            case DSTORE_0:
                storesInLoops.set(0);
                storesInLoops.set(1);
                break;
            case LSTORE_1:
            case DSTORE_1:
                storesInLoops.set(1);
                storesInLoops.set(2);
                break;
            case LSTORE_2:
            case DSTORE_2:
                storesInLoops.set(2);
                storesInLoops.set(3);
                break;
            case LSTORE_3:
            case DSTORE_3:
                storesInLoops.set(3);
                storesInLoops.set(4);
                break;

            default:
                throw new InternalError("undefined store bytecode");
        }
    }



    /**
     * Print block information in the format required by {@linkplain CFGPrinter}. The method must
     * be here because it accesses private state of a block.
     */
    public void printBlock(Block block, LogStream out) {
        out.print("name \"B").print(block.startBci).println('"');
        out.print("from_bci ").println(block.startBci);
        out.print("to_bci ").println(block.endBci);

        out.println("predecessors ");

        out.print("successors ");
        for (Block succ : block.successors) {
            if (!succ.isExceptionEntry) {
                out.print("\"B").print(succ.startBci).print("\" ");
            }
        }
        out.println();

        out.print("xhandlers");
        for (Block succ : block.successors) {
            if (succ.isExceptionEntry) {
                out.print("\"B").print(succ.startBci).print("\" ");
            }
        }
        out.println();

        out.print("flags ");
        if (block.isExceptionEntry) {
            out.print("\"ex\" ");
        }
        if (block.isLoopHeader) {
            out.print("\"plh\" ");
        }
        out.println();

        out.print("loop_depth ").println(Integer.bitCount(block.loops));
    }
}
