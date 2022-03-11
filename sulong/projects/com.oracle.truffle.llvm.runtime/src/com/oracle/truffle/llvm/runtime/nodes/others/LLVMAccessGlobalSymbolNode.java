/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Returns the value of a given symbol for the current context. This node behaves differently for
 * single context and multi context mode: In single context mode, the cached context will resolve to
 * a constant, which is very efficient, but in multi context mode, it's more efficient to get the
 * context from the {@link LLVMStack} stored in the frame.
 */
public abstract class LLVMAccessGlobalSymbolNode extends LLVMAccessSymbolNode {

    LLVMAccessGlobalSymbolNode(LLVMSymbol symbol) {
        super(symbol);
    }

    /*
     * CachedContext is very efficient in single-context mode, otherwise we should get the context
     * from the frame.
     */
    @Specialization(assumptions = "singleContextAssumption()")
    @GenerateAOT.Exclude
    public LLVMPointer accessSingleContext(@Cached BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        return checkNull(getContext().getSymbol(symbol, exception), exception);
    }

    protected LLVMStack.LLVMStackAccessHolder createStackAccessHolder() {
        return new LLVMStack.LLVMStackAccessHolder(((LLVMRootNode) getRootNode()).getStackAccess());
    }

    @Specialization
    public LLVMPointer accessMultiContext(VirtualFrame frame,
                    @Cached("createStackAccessHolder()") LLVMStack.LLVMStackAccessHolder stackAccessHolder,
                    @Cached BranchProfile exception) throws LLVMIllegalSymbolIndexException {
        return checkNull(stackAccessHolder.stackAccess.executeGetStack(frame).getContext().getSymbol(symbol, exception), exception);
    }
}
