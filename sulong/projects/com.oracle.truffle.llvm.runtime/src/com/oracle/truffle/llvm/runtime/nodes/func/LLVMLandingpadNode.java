/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.func;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.LLVMBitcodeLibraryFunctions;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.interop.LLVMManagedExceptionObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMManagedLandingpadValue;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMInstrumentableNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMLandingpadNodeGen.LandingpadCatchEntryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMLandingpadNodeGen.LandingpadFilterEntryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.op.ToComparableValue;
import com.oracle.truffle.llvm.runtime.nodes.op.ToComparableValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMDynAccessSymbolNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMLandingpadNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode getStack;
    @Children private final LandingpadEntryNode[] entries;

    private final FrameSlot exceptionSlot;
    private final boolean cleanup;

    public LLVMLandingpadNode(LLVMExpressionNode getStack, FrameSlot exceptionSlot, boolean cleanup,
                    LandingpadEntryNode[] entries) {
        this.getStack = getStack;
        this.exceptionSlot = exceptionSlot;
        this.cleanup = cleanup;
        this.entries = entries;
    }

    @Specialization
    public Object doLandingpad(VirtualFrame frame) {
        try {
            LLVMUserException exception = (LLVMUserException) frame.getObject(exceptionSlot);
            LLVMPointer unwindHeader = exception.getUnwindHeader();
            LLVMStack stack = (LLVMStack) getStack.executeGeneric(frame);

            int clauseId = getEntryIdentifier(frame, stack, unwindHeader);
            if (clauseId == 0 && !cleanup) {
                throw exception;
            } else {
                LLVMManagedLandingpadValue landingpadValue = new LLVMManagedLandingpadValue(unwindHeader, clauseId);
                return LLVMManagedPointer.create(landingpadValue);
            }
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @ExplodeLoop
    private int getEntryIdentifier(VirtualFrame frame, LLVMStack stack, LLVMPointer unwindHeader) {
        for (int i = 0; i < entries.length; i++) {
            int clauseId = entries[i].execute(frame, stack, unwindHeader);
            if (clauseId != 0) {
                return clauseId;
            }
        }
        return 0;
    }

    @GenerateWrapper
    public abstract static class LandingpadEntryNode extends LLVMInstrumentableNode {

        public abstract int execute(VirtualFrame frame, LLVMStack stack, LLVMPointer unwindHeader);

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new LandingpadEntryNodeWrapper(this, probe);
        }
    }

    public static LandingpadEntryNode createCatchEntry(LLVMExpressionNode catchType) {
        return LandingpadCatchEntryNodeGen.create(null, null, catchType);
    }

    @NodeChild(value = "stack", type = LLVMExpressionNode.class)
    @NodeChild(value = "unwindHeader", type = LLVMExpressionNode.class)
    @NodeChild(value = "catchType", type = LLVMExpressionNode.class)
    abstract static class LandingpadCatchEntryNode extends LandingpadEntryNode {

        @Child private LLVMBitcodeLibraryFunctions.SulongCanCatchNode canCatch;
        @Child private ToComparableValue toComparableValue;

        LandingpadCatchEntryNode() {
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

        protected static final String voidPointerType = "_ZTIPv";

        protected static final int nativePointerToI32(LLVMPointer pointer) {
            return (int) LLVMNativePointer.cast(pointer).asNative();
        }

        /**
         * @param accessSymbolNode
         * @param context
         * @param typeInfo
         */
        @Specialization
        int getIdentifier(LLVMStack stack, LLVMPointer unwindHeader, LLVMPointer catchType,
                        @Cached LLVMDynAccessSymbolNode accessSymbolNode,
                        @CachedContext(LLVMLanguage.class) LLVMContext context,
                        @Cached(value = "context.getGlobalScope().get(voidPointerType)") LLVMSymbol typeInfo,
                        @Cached(value = "accessSymbolNode.execute(typeInfo)") LLVMPointer resolvedVoidTypePtr,
                        @Cached(value = "nativePointerToI32(resolvedVoidTypePtr)") int resolvedReturnValue) {
            if (catchType.isNull()) {
                /*
                 * If ExcType is null, any exception matches, so the landing pad should always be
                 * entered. catch (...)
                 */
                return 1;
            }
            if (LLVMManagedPointer.isInstance(unwindHeader)) {
                final Object eObj = LLVMManagedPointer.cast(unwindHeader).getObject();
                if (eObj instanceof LLVMManagedExceptionObject) {
                    /*
                     * for foreign objects (thrown via polyglot interop), catching in LLVM is
                     * possible for 'void*', i.e. _ZTIPv
                     */
                    return resolvedVoidTypePtr.isSame(catchType) ? resolvedReturnValue : 0;
                }
            }
            if (getCanCatch().canCatch(stack, unwindHeader, catchType) != 0) {
                return (int) toComparableValue.executeWithTarget(catchType);
            }
            return 0;
        }
    }

    public static LandingpadEntryNode createFilterEntry(LLVMExpressionNode[] filterTypes) {
        return LandingpadFilterEntryNodeGen.create(filterTypes);
    }

    public abstract static class LandingpadFilterEntryNode extends LandingpadEntryNode {

        @Children private final LLVMExpressionNode[] filterTypes;
        @Child private LLVMBitcodeLibraryFunctions.SulongCanCatchNode canCatch;

        LandingpadFilterEntryNode(LLVMExpressionNode[] filterTypes) {
            this.filterTypes = filterTypes;
        }

        LLVMBitcodeLibraryFunctions.SulongCanCatchNode getCanCatch() {
            if (canCatch == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                LLVMContext context = lookupContextReference(LLVMLanguage.class).get();
                this.canCatch = insert(new LLVMBitcodeLibraryFunctions.SulongCanCatchNode(context));
            }
            return canCatch;
        }

        @Specialization
        int getIdentifier(VirtualFrame frame, LLVMStack stack, LLVMPointer unwindHeader) {
            if (!filterMatches(frame, stack, unwindHeader)) {
                // when this clause is matched, the selector value has to be negative
                return -1;
            }
            return 0;
        }

        @ExplodeLoop
        private boolean filterMatches(VirtualFrame frame, LLVMStack stack, LLVMPointer unwindHeader) {
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
