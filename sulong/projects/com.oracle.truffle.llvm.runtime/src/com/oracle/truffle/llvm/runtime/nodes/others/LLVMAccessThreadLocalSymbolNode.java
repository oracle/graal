/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.others;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMThreadLocalPointer;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMAccessThreadLocalSymbolNode extends LLVMAccessSymbolNode {

    LLVMAccessThreadLocalSymbolNode(LLVMSymbol symbol) {
        super(symbol);
    }

    /*
     * CachedContext is very efficient in single-context mode, otherwise we should get the context
     * from the frame.
     */
    @Specialization(assumptions = "singleContextAssumption()")
    @GenerateAOT.Exclude
    public LLVMPointer accessSingleContext(@Cached BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        LLVMPointer pointer = checkNull(getContext().getSymbol(symbol, exception), exception);
        LLVMThreadLocalPointer threadLocalPointer = (LLVMThreadLocalPointer) LLVMManagedPointer.cast(pointer).getObject();
        int offset = threadLocalPointer.getOffset();
        BitcodeID bitcodeID = threadLocalPointer.getSymbol().getBitcodeID(exception);
        if (offset < 0) {
            LLVMGlobalContainer container = LLVMLanguage.get(this).contextThreadLocal.get().getGlobalContainer(Math.abs(offset), bitcodeID);
            return LLVMManagedPointer.create(container);
        } else {
            LLVMPointer base = LLVMLanguage.get(this).contextThreadLocal.get().getSection(bitcodeID);
            if (base == null) {
                throw new LLVMIllegalSymbolIndexException("Section base for thread local global is null");
            }
            return checkNull(base.increment(offset), exception);
        }
    }

    protected LLVMStack.LLVMStackAccessHolder createStackAccessHolder() {
        return new LLVMStack.LLVMStackAccessHolder(((LLVMRootNode) getRootNode()).getStackAccess());
    }

    @Specialization
    public LLVMPointer accessMultiContext(VirtualFrame frame,
                    @Cached("createStackAccessHolder()") LLVMStack.LLVMStackAccessHolder stackAccessHolder,
                    @Cached BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        LLVMPointer pointer = checkNull(stackAccessHolder.stackAccess.executeGetStack(frame).getContext().getSymbol(symbol, exception), exception);
        LLVMThreadLocalPointer threadLocalPointer = (LLVMThreadLocalPointer) LLVMManagedPointer.cast(pointer).getObject();
        int offset = threadLocalPointer.getOffset();
        BitcodeID bitcodeID = threadLocalPointer.getSymbol().getBitcodeID(exception);
        if (offset < 0) {
            LLVMGlobalContainer container = LLVMLanguage.get(this).contextThreadLocal.get().getGlobalContainer(Math.abs(offset), bitcodeID);
            return LLVMManagedPointer.create(container);
        } else {
            LLVMPointer base = LLVMLanguage.get(this).contextThreadLocal.get().getSection(bitcodeID);
            if (base == null) {
                throw new LLVMIllegalSymbolIndexException("Section base for thread local global is null");
            }
            return checkNull(base.increment(offset), exception);
        }
    }
}
