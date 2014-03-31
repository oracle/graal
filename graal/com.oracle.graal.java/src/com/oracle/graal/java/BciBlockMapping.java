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
package com.oracle.graal.java;

import static com.oracle.graal.bytecode.Bytecodes.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;

/**
 * Builds a mapping between bytecodes and basic blocks and builds a conservative control flow graph
 * (CFG). It makes one linear passes over the bytecodes to build the CFG where it detects block
 * headers and connects them.
 * <p>
 * It also creates exception dispatch blocks for exception handling. These blocks are between a
 * bytecode that might throw an exception, and the actual exception handler entries, and are later
 * used to create the type checks with the exception handler catch types. If a bytecode is covered
 * by an exception handler, this bytecode ends the basic block. This guarantees that a) control flow
 * cannot be transferred to an exception dispatch block in the middle of a block, and b) that every
 * block has at most one exception dispatch block (which is always the last entry in the successor
 * list).
 * <p>
 * If a bytecode is covered by multiple exception handlers, a chain of exception dispatch blocks is
 * created so that multiple exception handler types can be checked. The chains are re-used if
 * multiple bytecodes are covered by the same exception handlers.
 * <p>
 * Note that exception unwinds, i.e., bytecodes that can throw an exception but the exception is not
 * handled in this method, do not end a basic block. Not modeling the exception unwind block reduces
 * the complexity of the CFG, and there is no algorithm yet where the exception unwind block would
 * matter.
 * <p>
 * The class also handles subroutines (jsr and ret bytecodes): subroutines are inlined by
 * duplicating the subroutine blocks. This is limited to simple, structured subroutines with a
 * maximum subroutine nesting of 4. Otherwise, a bailout is thrown.
 * <p>
 * Loops in the methods are detected. If a method contains an irreducible loop (a loop with more
 * than one entry), a bailout is thrown. This simplifies the compiler later on since only structured
 * loops need to be supported.
 * <p>
 * A data flow analysis computes the live local variables from the point of view of the interpreter.
 * The result is used later to prune frame states, i.e., remove local variable entries that are
 * guaranteed to be never used again (even in the case of deoptimization).
 * <p>
 * The algorithms and analysis in this class are conservative and do not use any assumptions or
 * profiling information.
 */
public final class BciBlockMapping {

    public static class BciBlock extends AbstractBlockBase<BciBlock> implements Cloneable {

        public int startBci;
        public int endBci;
        public boolean isExceptionEntry;
        public boolean isLoopHeader;
        public int loopId;

        public FixedWithNextNode firstInstruction;
        public HIRFrameStateBuilder entryState;

        // public ArrayList<BciBlock> successors = new ArrayList<>(2);
        // public ArrayList<BciBlock> predecessors = new ArrayList<>(2); // only used in the
        // baseline

        public long exits;

        private boolean visited;
        private boolean active;
        public long loops;

        public HashMap<JsrScope, BciBlock> jsrAlternatives;
        public JsrScope jsrScope = JsrScope.EMPTY_SCOPE;
        public BciBlock jsrSuccessor;
        public int jsrReturnBci;
        public BciBlock retSuccessor;
        public boolean endsWithRet = false;

        public BciBlock() {
            this.successors = new ArrayList<>();
            this.predecessors = new ArrayList<>();
        }

        public BciBlock exceptionDispatchBlock() {
            if (successors.size() > 0 && successors.get(successors.size() - 1) instanceof ExceptionDispatchBlock) {
                return successors.get(successors.size() - 1);
            }
            return null;
        }

        public int numNormalSuccessors() {
            if (exceptionDispatchBlock() != null) {
                return successors.size() - 1;
            }
            return successors.size();
        }

        public BciBlock copy() {
            try {
                BciBlock block = (BciBlock) super.clone();
                block.successors = new ArrayList<>(successors);
                return block;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("B").append(getId());
            sb.append('[').append(startBci).append("->").append(endBci);
            if (isLoopHeader || isExceptionEntry) {
                sb.append(' ');
                if (isLoopHeader) {
                    sb.append('L');
                }
                if (isExceptionEntry) {
                    sb.append('!');
                }
            }
            sb.append(']');
            return sb.toString();
        }

        public Loop getLoop() {
            // TODO Auto-generated method stub
            return null;
        }

        public int getLoopDepth() {
            // TODO Auto-generated method stub
            return 0;
        }

        public boolean isLoopHeader() {
            return isLoopHeader;
        }

        public boolean isLoopEnd() {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean isExceptionEntry() {
            // TODO Auto-generated method stub
            return false;
        }

        public BciBlock getSuccessor(int index) {
            return successors.get(index);
        }

        public BciBlock getPredecessor(int index) {
            return predecessors.get(index);
        }
    }

    public static class ExceptionDispatchBlock extends BciBlock {

        private HashMap<ExceptionHandler, ExceptionDispatchBlock> exceptionDispatch = new HashMap<>();

        public ExceptionHandler handler;
        public int deoptBci;
    }

    /**
     * The blocks found in this method, in reverse postorder.
     */
    public final List<BciBlock> blocks;
    public final ResolvedJavaMethod method;
    public boolean hasJsrBytecodes;
    public BciBlock startBlock;

    private final BytecodeStream stream;
    private final ExceptionHandler[] exceptionHandlers;
    private BciBlock[] blockMap;
    public BciBlock[] loopHeaders;

    public LocalLiveness liveness;

    /**
     * Creates a new BlockMap instance from bytecode of the given method .
     *
     * @param method the compiler interface method containing the code
     */
    private BciBlockMapping(ResolvedJavaMethod method) {
        this.method = method;
        exceptionHandlers = method.getExceptionHandlers();
        stream = new BytecodeStream(method.getCode());
        this.blockMap = new BciBlock[method.getCodeSize()];
        this.blocks = new ArrayList<>();
        this.loopHeaders = new BciBlock[64];
    }

    /**
     * Builds the block map and conservative CFG and numbers blocks.
     */
    public void build() {
        makeExceptionEntries();
        iterateOverBytecodes();
        if (hasJsrBytecodes) {
            if (!SupportJsrBytecodes.getValue()) {
                throw new JsrNotSupportedBailout("jsr/ret parsing disabled");
            }
            createJsrAlternatives(blockMap[0]);
        }
        if (Debug.isLogEnabled()) {
            this.log("Before BlockOrder");
        }
        computeBlockOrder();
        fixLoopBits();

        initializeBlockIds();

        startBlock = blockMap[0];

        assert verify();

        // Discard big arrays so that they can be GCed
        blockMap = null;
        if (Debug.isLogEnabled()) {
            this.log("Before LivenessAnalysis");
        }
        if (OptLivenessAnalysis.getValue()) {
            try (Scope s = Debug.scope("LivenessAnalysis")) {
                liveness = method.getMaxLocals() <= 64 ? new SmallLocalLiveness() : new LargeLocalLiveness();
                liveness.computeLiveness();
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
    }

    private boolean verify() {
        for (BciBlock block : blocks) {
            assert blocks.get(block.getId()) == block;

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BciBlock sux = block.getSuccessor(i);
                if (sux instanceof ExceptionDispatchBlock) {
                    assert i == block.getSuccessorCount() - 1 : "Only one exception handler allowed, and it must be last in successors list";
                }
            }
        }

        return true;
    }

    private void initializeBlockIds() {
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).setId(i);
        }
    }

    private void makeExceptionEntries() {
        // start basic blocks at all exception handler blocks and mark them as exception entries
        for (ExceptionHandler h : this.exceptionHandlers) {
            BciBlock xhandler = makeBlock(h.getHandlerBCI());
            xhandler.isExceptionEntry = true;
        }
    }

    private void iterateOverBytecodes() {
        // iterate over the bytecodes top to bottom.
        // mark the entrypoints of basic blocks and build lists of successors for
        // all bytecodes that end basic blocks (i.e. goto, ifs, switches, throw, jsr, returns, ret)
        BciBlock current = null;
        stream.setBCI(0);
        while (stream.currentBC() != Bytecodes.END) {
            int bci = stream.currentBCI();

            if (current == null || blockMap[bci] != null) {
                BciBlock b = makeBlock(bci);
                if (current != null) {
                    addSuccessor(current.endBci, b);
                }
                current = b;
            }
            blockMap[bci] = current;
            current.endBci = bci;

            switch (stream.currentBC()) {
                case IRETURN: // fall through
                case LRETURN: // fall through
                case FRETURN: // fall through
                case DRETURN: // fall through
                case ARETURN: // fall through
                case RETURN: {
                    current = null;
                    break;
                }
                case ATHROW: {
                    current = null;
                    ExceptionDispatchBlock handler = handleExceptions(bci);
                    if (handler != null) {
                        addSuccessor(bci, handler);
                    }
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
                    addSuccessor(bci, makeBlock(stream.readBranchDest()));
                    addSuccessor(bci, makeBlock(stream.nextBCI()));
                    break;
                }
                case GOTO:
                case GOTO_W: {
                    current = null;
                    addSuccessor(bci, makeBlock(stream.readBranchDest()));
                    break;
                }
                case TABLESWITCH: {
                    current = null;
                    addSwitchSuccessors(bci, new BytecodeTableSwitch(stream, bci));
                    break;
                }
                case LOOKUPSWITCH: {
                    current = null;
                    addSwitchSuccessors(bci, new BytecodeLookupSwitch(stream, bci));
                    break;
                }
                case JSR:
                case JSR_W: {
                    hasJsrBytecodes = true;
                    int target = stream.readBranchDest();
                    if (target == 0) {
                        throw new JsrNotSupportedBailout("jsr target bci 0 not allowed");
                    }
                    BciBlock b1 = makeBlock(target);
                    current.jsrSuccessor = b1;
                    current.jsrReturnBci = stream.nextBCI();
                    current = null;
                    addSuccessor(bci, b1);
                    break;
                }
                case RET: {
                    current.endsWithRet = true;
                    current = null;
                    break;
                }
                case INVOKEINTERFACE:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEVIRTUAL:
                case INVOKEDYNAMIC: {
                    current = null;
                    addSuccessor(bci, makeBlock(stream.nextBCI()));
                    ExceptionDispatchBlock handler = handleExceptions(bci);
                    if (handler != null) {
                        addSuccessor(bci, handler);
                    }
                    break;
                }
                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                case IALOAD:
                case LALOAD:
                case FALOAD:
                case DALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                case PUTFIELD:
                case GETFIELD: {
                    ExceptionDispatchBlock handler = handleExceptions(bci);
                    if (handler != null) {
                        current = null;
                        addSuccessor(bci, makeBlock(stream.nextBCI()));
                        addSuccessor(bci, handler);
                    }
                }
            }
            stream.next();
        }
    }

    private BciBlock makeBlock(int startBci) {
        BciBlock oldBlock = blockMap[startBci];
        if (oldBlock == null) {
            BciBlock newBlock = new BciBlock();
            newBlock.startBci = startBci;
            blockMap[startBci] = newBlock;
            return newBlock;

        } else if (oldBlock.startBci != startBci) {
            // Backward branch into the middle of an already processed block.
            // Add the correct fall-through successor.
            BciBlock newBlock = new BciBlock();
            newBlock.startBci = startBci;
            newBlock.endBci = oldBlock.endBci;
            newBlock.getSuccessors().addAll(oldBlock.getSuccessors());

            oldBlock.endBci = startBci - 1;
            oldBlock.getSuccessors().clear();
            oldBlock.getSuccessors().add(newBlock);

            for (int i = startBci; i <= newBlock.endBci; i++) {
                blockMap[i] = newBlock;
            }
            return newBlock;

        } else {
            return oldBlock;
        }
    }

    private void addSwitchSuccessors(int predBci, BytecodeSwitch bswitch) {
        // adds distinct targets to the successor list
        Collection<Integer> targets = new TreeSet<>();
        for (int i = 0; i < bswitch.numberOfCases(); i++) {
            targets.add(bswitch.targetAt(i));
        }
        targets.add(bswitch.defaultTarget());
        for (int targetBci : targets) {
            addSuccessor(predBci, makeBlock(targetBci));
        }
    }

    private void addSuccessor(int predBci, BciBlock sux) {
        BciBlock predecessor = blockMap[predBci];
        if (sux.isExceptionEntry) {
            throw new BailoutException("Exception handler can be reached by both normal and exceptional control flow");
        }
        predecessor.getSuccessors().add(sux);
    }

    private final ArrayList<BciBlock> jsrVisited = new ArrayList<>();

    private void createJsrAlternatives(BciBlock block) {
        jsrVisited.add(block);
        JsrScope scope = block.jsrScope;

        if (block.endsWithRet) {
            block.retSuccessor = blockMap[scope.nextReturnAddress()];
            block.getSuccessors().add(block.retSuccessor);
            assert block.retSuccessor != block.jsrSuccessor;
        }
        Debug.log("JSR alternatives block %s  sux %s  jsrSux %s  retSux %s  jsrScope %s", block, block.getSuccessors(), block.jsrSuccessor, block.retSuccessor, block.jsrScope);

        if (block.jsrSuccessor != null || !scope.isEmpty()) {
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BciBlock successor = block.getSuccessor(i);
                JsrScope nextScope = scope;
                if (successor == block.jsrSuccessor) {
                    nextScope = scope.push(block.jsrReturnBci);
                }
                if (successor == block.retSuccessor) {
                    nextScope = scope.pop();
                }
                if (!successor.jsrScope.isPrefixOf(nextScope)) {
                    throw new JsrNotSupportedBailout("unstructured control flow  (" + successor.jsrScope + " " + nextScope + ")");
                }
                if (!nextScope.isEmpty()) {
                    BciBlock clone;
                    if (successor.jsrAlternatives != null && successor.jsrAlternatives.containsKey(nextScope)) {
                        clone = successor.jsrAlternatives.get(nextScope);
                    } else {
                        if (successor.jsrAlternatives == null) {
                            successor.jsrAlternatives = new HashMap<>();
                        }
                        clone = successor.copy();
                        clone.jsrScope = nextScope;
                        successor.jsrAlternatives.put(nextScope, clone);
                    }
                    block.getSuccessors().set(i, clone);
                    if (successor == block.jsrSuccessor) {
                        block.jsrSuccessor = clone;
                    }
                    if (successor == block.retSuccessor) {
                        block.retSuccessor = clone;
                    }
                }
            }
        }
        for (BciBlock successor : block.getSuccessors()) {
            if (!jsrVisited.contains(successor)) {
                createJsrAlternatives(successor);
            }
        }
    }

    private HashMap<ExceptionHandler, ExceptionDispatchBlock> initialExceptionDispatch = new HashMap<>();

    private ExceptionDispatchBlock handleExceptions(int bci) {
        ExceptionDispatchBlock lastHandler = null;

        for (int i = exceptionHandlers.length - 1; i >= 0; i--) {
            ExceptionHandler h = exceptionHandlers[i];
            if (h.getStartBCI() <= bci && bci < h.getEndBCI()) {
                if (h.isCatchAll()) {
                    // Discard all information about succeeding exception handlers, since they can
                    // never be reached.
                    lastHandler = null;
                }

                HashMap<ExceptionHandler, ExceptionDispatchBlock> exceptionDispatch = lastHandler != null ? lastHandler.exceptionDispatch : initialExceptionDispatch;
                ExceptionDispatchBlock curHandler = exceptionDispatch.get(h);
                if (curHandler == null) {
                    curHandler = new ExceptionDispatchBlock();
                    curHandler.startBci = -1;
                    curHandler.endBci = -1;
                    curHandler.deoptBci = bci;
                    curHandler.handler = h;
                    curHandler.getSuccessors().add(blockMap[h.getHandlerBCI()]);
                    if (lastHandler != null) {
                        curHandler.getSuccessors().add(lastHandler);
                    }
                    exceptionDispatch.put(h, curHandler);
                }
                lastHandler = curHandler;
            }
        }
        return lastHandler;
    }

    private boolean loopChanges;

    private void fixLoopBits() {
        do {
            loopChanges = false;
            for (BciBlock b : blocks) {
                b.visited = false;
            }

            long loop = fixLoopBits(blockMap[0]);

            if (loop != 0) {
                // There is a path from a loop end to the method entry that does not pass the loop
                // header.
                // Therefore, the loop is non reducible (has more than one entry).
                // We don't want to compile such methods because the IR only supports structured
                // loops.
                throw new BailoutException("Non-reducible loop: %016x", loop);
            }
        } while (loopChanges);
    }

    private void computeBlockOrder() {
        long loop = computeBlockOrder(blockMap[0]);

        if (loop != 0) {
            // There is a path from a loop end to the method entry that does not pass the loop
            // header.
            // Therefore, the loop is non reducible (has more than one entry).
            // We don't want to compile such methods because the IR only supports structured loops.
            throw new BailoutException("Non-reducible loop");
        }

        // Convert postorder to the desired reverse postorder.
        Collections.reverse(blocks);
    }

    public void log(String name) {
        if (Debug.isLogEnabled()) {
            String n = System.lineSeparator();
            StringBuilder sb = new StringBuilder(Debug.currentScope()).append("BlockMap ").append(name).append(" :");
            sb.append(n);
            Iterable<BciBlock> it;
            if (blocks.isEmpty()) {
                it = new HashSet<>(Arrays.asList(blockMap));
            } else {
                it = blocks;
            }
            for (BciBlock b : it) {
                if (b == null) {
                    continue;
                }
                sb.append("B").append(b.getId()).append(" (").append(b.startBci).append(" -> ").append(b.endBci).append(")");
                if (b.isLoopHeader) {
                    sb.append(" LoopHeader");
                }
                if (b.isExceptionEntry) {
                    sb.append(" ExceptionEntry");
                }
                sb.append(n).append("  Sux : ");
                for (BciBlock s : b.getSuccessors()) {
                    sb.append("B").append(s.getId()).append(" (").append(s.startBci).append(" -> ").append(s.endBci).append(")");
                    if (s.isExceptionEntry) {
                        sb.append("!");
                    }
                    sb.append(" ");
                }
                sb.append(n).append("  Loop : ");
                long l = b.loops;
                int pos = 0;
                while (l != 0) {
                    int lMask = 1 << pos;
                    if ((l & lMask) != 0) {
                        sb.append("B").append(loopHeaders[pos].getId()).append(" ");
                        l &= ~lMask;
                    }
                    pos++;
                }
                sb.append(n).append("  Exits : ");
                l = b.exits;
                pos = 0;
                while (l != 0) {
                    int lMask = 1 << pos;
                    if ((l & lMask) != 0) {
                        sb.append("B").append(loopHeaders[pos].getId()).append(" ");
                        l &= ~lMask;
                    }
                    pos++;
                }
                sb.append(n);
            }
            Debug.log("%s", sb);
        }
    }

    /**
     * The next available loop number.
     */
    private int nextLoop;

    /**
     * Mark the block as a loop header, using the next available loop number. Also checks for corner
     * cases that we don't want to compile.
     */
    private void makeLoopHeader(BciBlock block) {
        if (!block.isLoopHeader) {
            block.isLoopHeader = true;

            if (block.isExceptionEntry) {
                // Loops that are implicitly formed by an exception handler lead to all sorts of
                // corner cases.
                // Don't compile such methods for now, until we see a concrete case that allows
                // checking for correctness.
                throw new BailoutException("Loop formed by an exception handler");
            }
            if (nextLoop >= Long.SIZE) {
                // This restriction can be removed by using a fall-back to a BitSet in case we have
                // more than 64 loops
                // Don't compile such methods for now, until we see a concrete case that allows
                // checking for correctness.
                throw new BailoutException("Too many loops in method");
            }

            assert block.loops == 0;
            block.loops = 1L << nextLoop;
            Debug.log("makeLoopHeader(%s) -> %x", block, block.loops);
            loopHeaders[nextLoop] = block;
            block.loopId = nextLoop;
            nextLoop++;
        }
        assert Long.bitCount(block.loops) == 1;
    }

    /**
     * Depth-first traversal of the control flow graph. The flag {@linkplain BciBlock#visited} is
     * used to visit every block only once. The flag {@linkplain BciBlock#active} is used to detect
     * cycles (backward edges).
     */
    private long computeBlockOrder(BciBlock block) {
        if (block.visited) {
            if (block.active) {
                // Reached block via backward branch.
                makeLoopHeader(block);
                // Return cached loop information for this block.
                return block.loops;
            } else if (block.isLoopHeader) {
                return block.loops & ~(1L << block.loopId);
            } else {
                return block.loops;
            }
        }

        block.visited = true;
        block.active = true;

        long loops = 0;
        for (BciBlock successor : block.getSuccessors()) {
            // Recursively process successors.
            loops |= computeBlockOrder(successor);
        }

        block.loops = loops;
        Debug.log("computeBlockOrder(%s) -> %x", block, block.loops);

        if (block.isLoopHeader) {
            loops &= ~(1L << block.loopId);
        }

        block.active = false;
        blocks.add(block);

        return loops;
    }

    private long fixLoopBits(BciBlock block) {
        if (block.visited) {
            // Return cached loop information for this block.
            if (block.isLoopHeader) {
                return block.loops & ~(1L << block.loopId);
            } else {
                return block.loops;
            }
        }

        block.visited = true;
        long loops = block.loops;
        for (BciBlock successor : block.getSuccessors()) {
            // Recursively process successors.
            loops |= fixLoopBits(successor);
        }
        for (BciBlock successor : block.getSuccessors()) {
            successor.exits = loops & ~successor.loops;
        }
        if (block.loops != loops) {
            loopChanges = true;
            block.loops = loops;
            Debug.log("fixLoopBits0(%s) -> %x", block, block.loops);
        }

        if (block.isLoopHeader) {
            loops &= ~(1L << block.loopId);
        }

        return loops;
    }

    /**
     * Encapsulates the liveness calculation, so that subclasses for locals &le; 64 and locals &gt;
     * 64 can be implemented.
     */
    public abstract class LocalLiveness {

        private void computeLiveness() {
            for (BciBlock block : blocks) {
                computeLocalLiveness(block);
            }

            boolean changed;
            int iteration = 0;
            do {
                Debug.log("Iteration %d", iteration);
                changed = false;
                for (int i = blocks.size() - 1; i >= 0; i--) {
                    BciBlock block = blocks.get(i);
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

        private void computeLocalLiveness(BciBlock block) {
            if (block.startBci < 0 || block.endBci < 0) {
                return;
            }
            int blockID = block.getId();
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
                    case ILOAD:
                    case IINC:
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

    public static BciBlockMapping create(ResolvedJavaMethod method) {
        BciBlockMapping map = new BciBlockMapping(method);
        map.build();
        if (Debug.isDumpEnabled()) {
            Debug.dump(map, MetaUtil.format("After block building %f %R %H.%n(%P)", method));
        }

        return map;
    }

    public final class SmallLocalLiveness extends LocalLiveness {
        /*
         * local n is represented by the bit accessible as (1 << n)
         */

        private final long[] localsLiveIn;
        private final long[] localsLiveOut;
        private final long[] localsLiveGen;
        private final long[] localsLiveKill;

        public SmallLocalLiveness() {
            localsLiveIn = new long[blocks.size()];
            localsLiveOut = new long[blocks.size()];
            localsLiveGen = new long[blocks.size()];
            localsLiveKill = new long[blocks.size()];
        }

        private String debugString(long value) {
            StringBuilder str = new StringBuilder("{");
            long current = value;
            for (int i = 0; i < method.getMaxLocals(); i++) {
                if ((current & 1L) == 1L) {
                    if (str.length() > 1) {
                        str.append(", ");
                    }
                    str.append(i);
                }
                current >>= 1;
            }
            return str.append('}').toString();
        }

        @Override
        protected String debugLiveIn(int blockID) {
            return debugString(localsLiveIn[blockID]);
        }

        @Override
        protected String debugLiveOut(int blockID) {
            return debugString(localsLiveOut[blockID]);
        }

        @Override
        protected String debugLiveGen(int blockID) {
            return debugString(localsLiveGen[blockID]);
        }

        @Override
        protected String debugLiveKill(int blockID) {
            return debugString(localsLiveKill[blockID]);
        }

        @Override
        protected int liveOutCardinality(int blockID) {
            return Long.bitCount(localsLiveOut[blockID]);
        }

        @Override
        protected void propagateLiveness(int blockID, int successorID) {
            localsLiveOut[blockID] |= localsLiveIn[successorID];
        }

        @Override
        protected void updateLiveness(int blockID) {
            localsLiveIn[blockID] = (localsLiveOut[blockID] & ~localsLiveKill[blockID]) | localsLiveGen[blockID];
        }

        @Override
        protected void loadOne(int blockID, int local) {
            long bit = 1L << local;
            if ((localsLiveKill[blockID] & bit) == 0L) {
                localsLiveGen[blockID] |= bit;
            }
        }

        @Override
        protected void storeOne(int blockID, int local) {
            long bit = 1L << local;
            if ((localsLiveGen[blockID] & bit) == 0L) {
                localsLiveKill[blockID] |= bit;
            }
        }

        @Override
        public boolean localIsLiveIn(BciBlock block, int local) {
            int blockID = block.getId();
            return blockID >= Integer.MAX_VALUE ? false : (localsLiveIn[blockID] & (1L << local)) != 0L;
        }

        @Override
        public boolean localIsLiveOut(BciBlock block, int local) {
            int blockID = block.getId();
            return blockID >= Integer.MAX_VALUE ? false : (localsLiveOut[blockID] & (1L << local)) != 0L;
        }
    }

    public final class LargeLocalLiveness extends LocalLiveness {
        private BitSet[] localsLiveIn;
        private BitSet[] localsLiveOut;
        private BitSet[] localsLiveGen;
        private BitSet[] localsLiveKill;

        public LargeLocalLiveness() {
            localsLiveIn = new BitSet[blocks.size()];
            localsLiveOut = new BitSet[blocks.size()];
            localsLiveGen = new BitSet[blocks.size()];
            localsLiveKill = new BitSet[blocks.size()];
            for (int i = 0; i < blocks.size(); i++) {
                localsLiveIn[i] = new BitSet(method.getMaxLocals());
                localsLiveOut[i] = new BitSet(method.getMaxLocals());
                localsLiveGen[i] = new BitSet(method.getMaxLocals());
                localsLiveKill[i] = new BitSet(method.getMaxLocals());
            }
        }

        @Override
        protected String debugLiveIn(int blockID) {
            return localsLiveIn[blockID].toString();
        }

        @Override
        protected String debugLiveOut(int blockID) {
            return localsLiveOut[blockID].toString();
        }

        @Override
        protected String debugLiveGen(int blockID) {
            return localsLiveGen[blockID].toString();
        }

        @Override
        protected String debugLiveKill(int blockID) {
            return localsLiveKill[blockID].toString();
        }

        @Override
        protected int liveOutCardinality(int blockID) {
            return localsLiveOut[blockID].cardinality();
        }

        @Override
        protected void propagateLiveness(int blockID, int successorID) {
            localsLiveOut[blockID].or(localsLiveIn[successorID]);
        }

        @Override
        protected void updateLiveness(int blockID) {
            BitSet liveIn = localsLiveIn[blockID];
            liveIn.clear();
            liveIn.or(localsLiveOut[blockID]);
            liveIn.andNot(localsLiveKill[blockID]);
            liveIn.or(localsLiveGen[blockID]);
        }

        @Override
        protected void loadOne(int blockID, int local) {
            if (!localsLiveKill[blockID].get(local)) {
                localsLiveGen[blockID].set(local);
            }
        }

        @Override
        protected void storeOne(int blockID, int local) {
            if (!localsLiveGen[blockID].get(local)) {
                localsLiveKill[blockID].set(local);
            }
        }

        @Override
        public boolean localIsLiveIn(BciBlock block, int local) {
            return block.getId() >= Integer.MAX_VALUE ? true : localsLiveIn[block.getId()].get(local);
        }

        @Override
        public boolean localIsLiveOut(BciBlock block, int local) {
            return block.getId() >= Integer.MAX_VALUE ? true : localsLiveOut[block.getId()].get(local);
        }
    }
}
