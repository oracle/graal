/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.nodes.op.ToComparableValue;
import com.oracle.truffle.llvm.nodes.op.ToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMBitcodeLibraryFunctions;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public final class LLVMLandingpadNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode getStack;
    @Child private LLVMExpressionNode allocateLandingPadValue;
    @Child private LLVMPointerStoreNode writePointer;
    @Child private LLVMI32StoreNode writeI32;
    @Children private final LandingpadEntryNode[] entries;
    private final FrameSlot exceptionSlot;
    private final boolean cleanup;

    public LLVMLandingpadNode(LLVMExpressionNode getStack, LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionSlot, boolean cleanup,
                    LandingpadEntryNode[] entries) {
        this.getStack = getStack;
        this.allocateLandingPadValue = allocateLandingPadValue;
        this.writePointer = LLVMPointerStoreNodeGen.create(null, null);
        this.writeI32 = LLVMI32StoreNodeGen.create(null, null);
        this.exceptionSlot = exceptionSlot;
        this.cleanup = cleanup;
        this.entries = entries;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            LLVMUserException exception = (LLVMUserException) frame.getObject(exceptionSlot);
            LLVMPointer unwindHeader = exception.getUnwindHeader();
            LLVMStack.StackPointer stack = (LLVMStack.StackPointer) getStack.executeGeneric(frame);

            int clauseId = getEntryIdentifier(frame, stack, unwindHeader);
            if (clauseId == 0 && !cleanup) {
                throw exception;
            } else {
                LLVMPointer landingPadValue = allocateLandingPadValue.executeLLVMPointer(frame);
                writePointer.executeWithTarget(landingPadValue, unwindHeader);
                writeI32.executeWithTarget(landingPadValue.increment(LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES), clauseId);
                return landingPadValue;
            }
        } catch (FrameSlotTypeException | UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @ExplodeLoop
    private int getEntryIdentifier(VirtualFrame frame, LLVMStack.StackPointer stack, LLVMPointer unwindHeader) {
        for (int i = 0; i < entries.length; i++) {
            int clauseId = entries[i].getIdentifier(frame, stack, unwindHeader);
            if (clauseId != 0) {
                return clauseId;
            }
        }
        return 0;
    }

    public abstract static class LandingpadEntryNode extends LLVMExpressionNode {

        public abstract int getIdentifier(VirtualFrame frame, LLVMStack.StackPointer stack, LLVMPointer unwindHeader);

        @Override
        public final Object executeGeneric(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException();
        }
    }

    public static final class LandingpadCatchEntryNode extends LandingpadEntryNode {

        @Child private LLVMExpressionNode catchType;
        @Child private LLVMBitcodeLibraryFunctions.SulongCanCatchNode canCatch;
        @Child private ToComparableValue toComparableValue;

        public LandingpadCatchEntryNode(LLVMExpressionNode catchType) {
            this.catchType = catchType;
            this.toComparableValue = ToComparableValueNodeGen.create();
        }

        public LLVMBitcodeLibraryFunctions.SulongCanCatchNode getCanCatch() {
            if (canCatch == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                LLVMContext context = lookupContextReference(LLVMLanguage.class).get();
                this.canCatch = insert(new LLVMBitcodeLibraryFunctions.SulongCanCatchNode(context));
            }
            return canCatch;
        }

        @Override
        public int getIdentifier(VirtualFrame frame, LLVMStack.StackPointer stack, LLVMPointer unwindHeader) {
            try {
                LLVMPointer catchAddress = catchType.executeLLVMPointer(frame);
                if (catchAddress.isNull()) {
                    /*
                     * If ExcType is null, any exception matches, so the landing pad should always
                     * be entered. catch (...)
                     */
                    return 1;
                }
                if (getCanCatch().canCatch(stack, unwindHeader, catchAddress) != 0) {
                    return (int) toComparableValue.executeWithTarget(catchAddress);
                }
                return 0;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static final class LandingpadFilterEntryNode extends LandingpadEntryNode {

        @Children private final LLVMExpressionNode[] filterTypes;
        @Child private LLVMBitcodeLibraryFunctions.SulongCanCatchNode canCatch;

        public LandingpadFilterEntryNode(LLVMExpressionNode[] filterTypes) {
            this.filterTypes = filterTypes;
        }

        public LLVMBitcodeLibraryFunctions.SulongCanCatchNode getCanCatch() {
            if (canCatch == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                LLVMContext context = lookupContextReference(LLVMLanguage.class).get();
                this.canCatch = insert(new LLVMBitcodeLibraryFunctions.SulongCanCatchNode(context));
            }
            return canCatch;
        }

        @Override
        public int getIdentifier(VirtualFrame frame, LLVMStack.StackPointer stack, LLVMPointer unwindHeader) {
            if (!filterMatches(frame, stack, unwindHeader)) {
                // when this clause is matched, the selector value has to be negative
                return -1;
            }
            return 0;
        }

        @ExplodeLoop
        private boolean filterMatches(VirtualFrame frame, LLVMStack.StackPointer stack, LLVMPointer unwindHeader) {
            /*
             * Landingpad should be entered if the exception being thrown does not match any of the
             * types in the list
             */
            try {
                for (int i = 0; i < filterTypes.length; i++) {
                    LLVMPointer filterAddress = filterTypes[i].executeLLVMPointer(frame);
                    if (filterAddress.isNull()) {
                        /*
                         * If ExcType is null, any exception matches, so the landing pad should
                         * always be entered. catch (...)
                         */
                        return true;
                    }
                    if (getCanCatch().canCatch(stack, unwindHeader, filterAddress) != 0) {
                        return true;
                    }
                }
                return false;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }
}
