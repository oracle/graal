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
package com.oracle.truffle.llvm.runtime.nodes.func;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMBitcodeLibraryFunctions.SulongEHUnwindWindowsNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.LLVMThreadLocalValue;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException.LLVMUserExceptionWindows;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Overrides the _CxxThrowException function (see vcruntime/throw.cpp).
 */
@NodeChild(value = "exceptionObject", type = LLVMExpressionNode.class)
@NodeChild(value = "throwInfo", type = LLVMExpressionNode.class)
public abstract class LLVMRaiseExceptionWindows extends LLVMExpressionNode {

    @Child private SulongEHUnwindWindowsNode unwindNode;

    public SulongEHUnwindWindowsNode getUnwind() {
        if (unwindNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            unwindNode = insert(new SulongEHUnwindWindowsNode(getContext()));
        }
        return unwindNode;
    }

    @Specialization(guards = {"throwInfo.isNull()", "exceptionObject.isNull()"})
    public Object doNull(@SuppressWarnings("unused") LLVMPointer exceptionObject, @SuppressWarnings("unused") LLVMPointer throwInfo) {
        // rethrow the last exception
        throw getLanguage().contextThreadLocal.get().popException();
    }

    protected void unwindCurrentException(LLVMStack stack) {
        LLVMThreadLocalValue threadLocal = getLanguage().contextThreadLocal.get();
        if (threadLocal.hasException()) {
            LLVMUserExceptionWindows exception = (LLVMUserExceptionWindows) threadLocal.popException();
            getUnwind().unwind(stack, exception.getExceptionObject(), exception.getThrowInfo(), exception.getImageBase());
        }
    }

    @Specialization(guards = {"!throwInfo.isNull()", "throwInfo.isSame(cachedThrowInfo)"}, limit = "3")
    public Object doRaise(LLVMPointer exceptionObject, @SuppressWarnings("unused") LLVMPointer throwInfo,
                    @Cached("throwInfo") LLVMPointer cachedThrowInfo,
                    @Cached("getImageBase(cachedThrowInfo)") LLVMPointer imageBase) {
        LLVMStack stack = getContext().getThreadingStack().getStack(this);
        unwindCurrentException(stack);
        throw new LLVMUserExceptionWindows(this, imageBase, exceptionObject, cachedThrowInfo, stack.getStackPointer());
    }

    @Specialization(guards = "!throwInfo.isNull()", replaces = "doRaise")
    public Object doFallback(LLVMPointer exceptionObject, LLVMPointer throwInfo) {
        LLVMStack stack = getContext().getThreadingStack().getStack(this);
        unwindCurrentException(stack);
        throw new LLVMUserExceptionWindows(this, getImageBase(throwInfo), exceptionObject, throwInfo, stack.getStackPointer());
    }

    @TruffleBoundary
    protected LLVMPointer getImageBase(LLVMPointer throwInfo) {
        List<LLVMSymbol> symbols = getContext().findSymbols(throwInfo);
        // this list sometimes contains identical duplicate symbols
        assert symbols.stream().allMatch((symb) -> symb == symbols.get(0));
        if (symbols.isEmpty()) {
            throw new LLVMParserException(this, "Could not find exception throw info symbol.");
        }
        return getContext().getGlobalsBase(symbols.get(0).getBitcodeIDUncached());
    }
}
