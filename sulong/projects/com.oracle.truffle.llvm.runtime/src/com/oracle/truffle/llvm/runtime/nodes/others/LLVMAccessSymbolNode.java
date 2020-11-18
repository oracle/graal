/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.LLVMStackAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public final class LLVMAccessSymbolNode extends LLVMExpressionNode {

    private boolean statement;
    private LLVMSourceLocation sourceLocation;

    protected final LLVMSymbol symbol;

    @CompilationFinal private Assumption singleContextAssumption;
    @CompilationFinal private LLVMStackAccess stackAccess;
    @CompilationFinal private ContextReference<LLVMContext> contextRef;

    public LLVMAccessSymbolNode(LLVMSymbol symbol) {
        this.symbol = resolveAlias(symbol);
    }

    public static LLVMSymbol resolveAlias(LLVMSymbol symbol) {
        LLVMSymbol tmp = symbol;
        while (tmp.isAlias()) {
            tmp = ((LLVMAlias) tmp).getTarget();
        }
        return tmp;
    }

    @Override
    public String toString() {
        return getShortString("symbol");
    }

    public LLVMSymbol getSymbol() {
        return symbol;
    }

    public LLVMPointer execute() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = lookupContextReference(LLVMLanguage.class);
        }
        return contextRef.get().getSymbol(symbol);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        if (singleContextAssumption == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            singleContextAssumption = singleContextAssumption();
        }
        if (singleContextAssumption.isValid()) {
            return execute();
        }
        if (stackAccess == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackAccess = ((LLVMRootNode) getRootNode()).getStackAccess();
        }
        return stackAccess.executeGetStack(frame).getContext().getSymbol(symbol);
    }

    @Override
    public LLVMSourceLocation getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public void setSourceLocation(LLVMSourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @Override
    protected boolean isStatement() {
        return statement;
    }

    @Override
    protected void setStatement(boolean statementTag) {
        this.statement = statementTag;
    }
}
