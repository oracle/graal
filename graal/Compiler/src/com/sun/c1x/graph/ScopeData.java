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

import static com.sun.c1x.graph.ScopeData.Flag.*;
import static com.sun.c1x.graph.ScopeData.ReturnBlock.*;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.value.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ri.*;

/**
 * The {@code ScopeData} class represents inlining context when parsing the bytecodes
 * of an inlined method.
 *
 * @author Ben L. Titzer
*/
public class ScopeData {
    // XXX: refactor and split this class into ScopeData, JsrScopeData, and InlineScopeData

    /**
     * An enumeration of flags describing scope attributes.
     */
    public enum Flag {
        /**
         * Scope is protected by an exception handler.
         * This attribute is inherited by nested scopes.
         */
        HasHandler,

        /**
         * Code in scope cannot contain safepoints.
         * This attribute is inherited by nested scopes.
         */
        NoSafepoints;

        public final int mask = 1 << ordinal();
    }


    final ScopeData parent;
    // the IR scope
    final IRScope scope;
    // bci-to-block mapping
    final BlockMap blockMap;
    // the bytecode stream
    final BytecodeStream stream;
    // the constant pool
    final RiConstantPool constantPool;
    // the worklist of blocks, managed like a sorted list
    BlockBegin[] workList;
    // the current position in the worklist
    int workListIndex;
    // maximum inline size for this scope
    int maxInlineSize;

    /**
     * Mask of {@link Flag} values.
     */
    int flags;

    // Exception handler list
    List<ExceptionHandler> exceptionHandlers;

    // The continuation point for the inline. Currently only used in
    // multi-block inlines, but eventually would like to use this for
    // all inlines for uniformity and simplicity; in this case would
    // get the continuation point from the BlockList instead of
    // fabricating it anew because Invokes would be considered to be
    // BlockEnds.
    BlockBegin continuation;

    // Without return value of inlined method on stack
    FrameState continuationState;

    /**
     * Field used to generate fewer blocks when inlining. If this value is {@code null},
     * then no {@code return}s have been encountered during inlining. If it is an instance
     * of {@link ReturnBlock}, then it is the block info for the single {@code return}
     * encountered. Otherwise, it will be {@link ReturnBlock#MULTIPLE_RETURNS}.
     */
    ReturnBlock inlinedReturnBlock;

    /**
     * Tracks the destination bci of the jsr. This is (currently) only used to determine
     * bailout conditions, since only a subset of all of the possible jsr-ret control
     * structures can (currently) be compiled.
     *
     * A value > 0 for this field indicates parsing of a jsr.
     */
    final int jsrEntryBci;

    // We need to track the local variable in which the return address
    // was stored to ensure we can handle inlining the jsr, because we
    // don't handle arbitrary jsr/ret constructs.
    int jsrRetAddrLocal;

    // If we are parsing a jsr, the continuation point for rets
    BlockBegin jsrContinuation;

    final BlockBegin[] jsrDuplicatedBlocks; // blocks that have been duplicated for JSR inlining

    /**
     * Constructs a new ScopeData instance with the specified parent ScopeData.
     * @param parent the parent scope data
     * @param scope the IR scope
     * @param blockMap the block map for this scope
     * @param stream the bytecode stream
     * @param constantPool the constant pool
     */
    public ScopeData(ScopeData parent, IRScope scope, BlockMap blockMap, BytecodeStream stream, RiConstantPool constantPool) {
        this.parent = parent;
        this.scope = scope;
        this.blockMap = blockMap;
        this.stream = stream;
        this.constantPool = constantPool;
        this.jsrEntryBci = -1;
        this.jsrDuplicatedBlocks = null;
        if (parent != null) {
            maxInlineSize = (int) (C1XOptions.MaximumInlineRatio * parent.maxInlineSize());
            if (maxInlineSize < C1XOptions.MaximumTrivialSize) {
                maxInlineSize = C1XOptions.MaximumTrivialSize;
            }
            if (parent.hasHandler()) {
                flags |= HasHandler.mask;
            }
            if (parent.noSafepoints() || scope.method.noSafepoints()) {
                flags |= NoSafepoints.mask;
            }
        } else {
            maxInlineSize = C1XOptions.MaximumInlineSize;
            if (scope.method.noSafepoints()) {
                flags |= NoSafepoints.mask;
            }
        }
        RiExceptionHandler[] handlers = scope.method.exceptionHandlers();
        if (handlers != null && handlers.length > 0) {
            exceptionHandlers = new ArrayList<ExceptionHandler>(handlers.length);
            for (RiExceptionHandler ch : handlers) {
                ExceptionHandler h = new ExceptionHandler(ch);
                h.setEntryBlock(blockAt(h.handler.handlerBCI()));
                exceptionHandlers.add(h);
            }
            flags |= HasHandler.mask;
        }
    }

    /**
     * Constructs a new ScopeData instance with the specified parent ScopeData. This constructor variant creates
     * a scope data for parsing a JSR.
     * @param parent the parent scope data
     * @param scope the IR scope
     * @param blockMap the block map for this scope
     * @param stream the bytecode stream
     * @param constantPool the constant pool
     * @param jsrEntryBci the bytecode index of the entrypoint of the JSR
     */
    public ScopeData(ScopeData parent, IRScope scope, BlockMap blockMap, BytecodeStream stream, RiConstantPool constantPool, int jsrEntryBci) {
        this.parent = parent;
        this.scope = scope;
        this.blockMap = blockMap;
        this.stream = stream;
        this.constantPool = constantPool;
        assert jsrEntryBci > 0 : "jsr cannot jump to BCI 0";
        assert parent != null : "jsr must have parent scope";
        this.jsrEntryBci = jsrEntryBci;
        this.jsrDuplicatedBlocks = new BlockBegin[scope.method.code().length];
        this.jsrRetAddrLocal = -1;

        maxInlineSize = (int) (C1XOptions.MaximumInlineRatio * parent.maxInlineSize());
        if (maxInlineSize < C1XOptions.MaximumTrivialSize) {
            maxInlineSize = C1XOptions.MaximumTrivialSize;
        }
        flags = parent.flags;

        // duplicate the parent scope's exception handlers, if any
        List<ExceptionHandler> handlers = parent.exceptionHandlers();
        if (handlers != null && handlers.size() > 0) {
            exceptionHandlers = new ArrayList<ExceptionHandler>(handlers.size());
            for (ExceptionHandler ph : handlers) {
                ExceptionHandler h = new ExceptionHandler(ph);
                int handlerBci = h.handler.handlerBCI();
                if (handlerBci >= 0) {
                    // need to duplicate the handler block because it is a "normal" handler
                    h.setEntryBlock(blockAt(handlerBci));
                } else {
                    // don't duplicate the handler block because it is a synchronization handler
                    // that was added by parsing/inlining a synchronized method
                    assert ph.entryBlock().checkBlockFlag(BlockBegin.BlockFlag.DefaultExceptionHandler);
                    h.setEntryBlock(ph.entryBlock());
                }
                exceptionHandlers.add(h);
            }
            assert hasHandler();
        }
    }

    /**
     * Gets the block beginning at the specified bytecode index. Note that this method
     * will clone the block if it the scope data is currently parsing a JSR.
     * @param bci the bytecode index of the start of the block
     * @return the block starting at the specified bytecode index
     */
    public BlockBegin blockAt(int bci) {
        if (jsrDuplicatedBlocks != null) {
            // all blocks in a JSR are duplicated on demand using an internal array,
            // including those for exception handlers in the scope of the method
            // containing the jsr (because those exception handlers may contain ret
            // instructions in some cases).
            BlockBegin block = jsrDuplicatedBlocks[bci];
            if (block == null) {
                BlockBegin p = this.parent.blockAt(bci);
                if (p != null) {
                    BlockBegin newBlock = new BlockBegin(p.bci(), C1XCompilation.compilation().hir().nextBlockNumber());
                    newBlock.setDepthFirstNumber(p.depthFirstNumber());
                    newBlock.copyBlockFlags(p);
                    jsrDuplicatedBlocks[bci] = newBlock;
                    block = newBlock;
                }
            }
            return block;
        }
        return blockMap.get(bci);
    }

    /**
     * Checks whether this scope has any handlers.
     * @return {@code true} if there are any exception handlers
     */
    public boolean hasHandler() {
        return (flags & Flag.HasHandler.mask) != 0;
    }

    /**
     * Checks whether this scope can contain safepoints.
     */
    public boolean noSafepoints() {
        return (flags & Flag.NoSafepoints.mask) != 0;
    }

    /**
     * Gets the maximum inline size.
     * @return the maximum inline size
     */
    public int maxInlineSize() {
        return maxInlineSize;
    }

    /**
     * Gets the size of the stack at the caller.
     * @return the size of the stack
     */
    public int callerStackSize() {
        FrameState state = scope.callerState;
        return state == null ? 0 : state.stackSize();
    }

    /**
     * Gets the block continuation for this ScopeData.
     * @return the continuation
     */
    public BlockBegin continuation() {
        return continuation;
    }

    /**
     * Sets the continuation for this ScopeData.
     * @param continuation the continuation
     */
    public void setContinuation(BlockBegin continuation) {
        this.continuation = continuation;
    }

    /**
     * Gets the state at the continuation point.
     * @return the state at the continuation point
     */
    public FrameState continuationState() {
        return continuationState;
    }

    /**
     * Sets the state at the continuation point.
     * @param state the state at the continuation
     */
    public void setContinuationState(FrameState state) {
        continuationState = state;
    }

    /**
     * Checks whether this ScopeData is parsing a JSR.
     * @return {@code true} if this scope data is parsing a JSR
     */
    public boolean parsingJsr() {
        return jsrEntryBci > 0;
    }

    /**
     * Gets the bytecode index for the JSR entry.
     * @return the jsr entry bci
     */
    public int jsrEntryBCI() {
        return jsrEntryBci;
    }

    /**
     * Gets the index of the local variable containing the JSR return address.
     * @return the index of the local with the JSR return address
     */
    public int jsrEntryReturnAddressLocal() {
        return jsrRetAddrLocal;
    }

    /**
     * Sets the index of the local variable containing the JSR return address.
     * @param local the local
     */
    public void setJsrEntryReturnAddressLocal(int local) {
        jsrRetAddrLocal = local;
    }

    /**
     * Gets the continuation for parsing a JSR.
     * @return the jsr continuation
     */
    public BlockBegin jsrContinuation() {
        return jsrContinuation;
    }

    /**
     * Sets the continuation for parsing a JSR.
     * @param block the block that is the continuation
     */
    public void setJsrContinuation(BlockBegin block) {
        jsrContinuation = block;
    }

    /**
     * A block delimited by a return instruction in an inlined method.
     */
    public static class ReturnBlock {
        /**
         * The inlined block.
         */
        final BlockBegin block;

        /**
         * The second last instruction in the block. That is, the one before the return instruction.
         */
        final Instruction returnPredecessor;

        /**
         * The frame state at the end of the block.
         */
        final FrameState returnState;

        ReturnBlock(BlockBegin block, Instruction returnPredecessor, FrameState returnState) {
            super();
            this.block = block;
            this.returnPredecessor = returnPredecessor;
            this.returnState = returnState;
        }

        public static final ReturnBlock MULTIPLE_RETURNS = new ReturnBlock(null, null, null);
    }

    /**
     * Updates the info about blocks in this scope delimited by a return instruction.
     *
     * @param block a block delimited by a {@code return} instruction
     * @param returnPredecessor the second last instruction in the block. That is, the one before the return instruction.
     * @param returnState the frame state after the return instruction
     */
    public void updateSimpleInlineInfo(BlockBegin block, Instruction returnPredecessor, FrameState returnState) {
        if (inlinedReturnBlock == null) {
            inlinedReturnBlock = new ReturnBlock(block, returnPredecessor, returnState);
        } else {
            inlinedReturnBlock = MULTIPLE_RETURNS;
        }
    }

    /**
     * Gets the return block info for a simple inline scope. That is, a scope that contains only a
     * single block delimited by a {@code return} instruction.
     *
     * @return the return block info for a simple inline scope or {@code null} if this is not a simple inline scope
     */
    public ReturnBlock simpleInlineInfo() {
        if (inlinedReturnBlock == MULTIPLE_RETURNS) {
            return null;
        }
        return inlinedReturnBlock;
    }

    /**
     * Gets the list of exception handlers for this scope data.
     * @return the list of exception handlers
     */
    public List<ExceptionHandler> exceptionHandlers() {
        return exceptionHandlers;
    }

    /**
     * Adds an exception handler to this scope data.
     * @param handler the handler to add
     */
    public void addExceptionHandler(ExceptionHandler handler) {
        if (exceptionHandlers == null) {
            exceptionHandlers = new ArrayList<ExceptionHandler>();
        }
        assert !parsingJsr() : "jsr scope should already have all the handlers it needs";
        exceptionHandlers.add(handler);
        flags |= HasHandler.mask;
    }

    /**
     * Adds a block to the worklist, if it is not already in the worklist.
     * This method will keep the worklist topologically stored (i.e. the lower
     * DFNs are earlier in the list).
     * @param block the block to add to the work list
     */
    public void addToWorkList(BlockBegin block) {
        if (!block.isOnWorkList()) {
            if (block == continuation || block == jsrContinuation) {
                return;
            }
            block.setOnWorkList(true);
            sortIntoWorkList(block);
        }
    }

    private void sortIntoWorkList(BlockBegin top) {
        // XXX: this is O(n), since the whole list is sorted; a heap could achieve O(nlogn), but
        //      would only pay off for large worklists
        if (workList == null) {
            // need to allocate the worklist
            workList = new BlockBegin[5];
        } else if (workListIndex == workList.length) {
            // need to grow the worklist
            BlockBegin[] nworkList = new BlockBegin[workList.length * 3];
            System.arraycopy(workList, 0, nworkList, 0, workList.length);
            workList = nworkList;
        }
        // put the block at the end of the array
        workList[workListIndex++] = top;
        int dfn = top.depthFirstNumber();
        assert dfn >= 0 : top + " does not have a depth first number";
        int i = workListIndex - 2;
        // push top towards the beginning of the array
        for (; i >= 0; i--) {
            BlockBegin b = workList[i];
            if (b.depthFirstNumber() >= dfn) {
                break; // already in the right position
            }
            workList[i + 1] = b; // bubble b down by one
            workList[i] = top;   // and overwrite it with top
        }
    }

    /**
     * Removes the next block from the worklist. The list is sorted topologically, so the
     * block with the lowest depth first number in the list will be removed and returned.
     * @return the next block from the worklist; {@code null} if there are no blocks
     * in the worklist
     */
    public BlockBegin removeFromWorkList() {
        if (workListIndex == 0) {
            return null;
        }
        // pop the last item off the end
        return workList[--workListIndex];
    }

    /**
     * Converts this scope data to a string for debugging purposes.
     * @return a string representation of this scope data
     */
    @Override
    public String toString() {
        if (parsingJsr()) {
            return "jsr@" + jsrEntryBci + " data for " + scope.toString();
        } else {
            return "data for " + scope.toString();
        }

    }
}
