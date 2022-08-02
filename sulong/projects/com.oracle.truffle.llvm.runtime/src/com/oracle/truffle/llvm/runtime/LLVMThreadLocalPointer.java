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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.LLVMThreadLocalValue;
import com.oracle.truffle.llvm.runtime.except.LLVMIllegalSymbolIndexException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public class LLVMThreadLocalPointer {

    private final LLVMSymbol symbol;

    // if the offset is -1, then the global contains an object.
    private final int offset;

    public LLVMThreadLocalPointer(LLVMSymbol symbol, int offset) {
        this.symbol = symbol;
        this.offset = offset;
    }

    public LLVMSymbol getSymbol() {
        return symbol;
    }

    public boolean isManaged() {
        return offset < 0;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return symbol.toString();
    }

    public LLVMPointer resolve(LLVMLanguage language, BranchProfile exception) {
        return resolveWithThreadContext(language.contextThreadLocal.get(), exception);
    }

    public LLVMPointer resolveWithThreadContext(LLVMThreadLocalValue contextThreadLocal, BranchProfile exception) {
        BitcodeID bitcodeID = getSymbol().getBitcodeID(exception);
        if (isManaged()) {
            LLVMGlobalContainer container = contextThreadLocal.getGlobalContainer(Math.abs(offset), bitcodeID);
            return LLVMManagedPointer.create(container);
        } else {
            LLVMPointer base = contextThreadLocal.getSectionBase(bitcodeID);
            if (base == null) {
                throw new LLVMIllegalSymbolIndexException("Section base for thread local global is null");
            }
            return base.increment(getOffset());
        }
    }
}
