/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

import java.util.function.Supplier;

import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMElemPtrSymbol extends LLVMSymbol {
    private final Type type;
    private final Supplier<LLVMExpressionNode> createGetElementPtrNode;

    public LLVMElemPtrSymbol(String name, BitcodeID bitcodeID, int symbolIndex, boolean exported, Type type, LLVMSymbol base, Supplier<LLVMExpressionNode> createGetElementPtrNode) {
        super(name, bitcodeID, symbolIndex, exported, base.isExternalWeak());
        this.type = type;
        this.createGetElementPtrNode = createGetElementPtrNode;
    }

    @Override
    public boolean isGlobalVariable() {
        return false;
    }

    @Override
    public boolean isFunction() {
        return false;
    }

    @Override
    public boolean isAlias() {
        return false;
    }

    @Override
    public LLVMFunction asFunction() {
        throw new IllegalStateException("GetElementPointerConstant " + getName() + " has to be resolved and might not be a function.");
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        throw new IllegalStateException("GetElementPointerConstant " + getName() + " has to be resolved and might not be a global variable.");
    }

    public Type getType() {
        return type;
    }

    public Supplier<LLVMExpressionNode> createGetElementPtrNode() {
        return createGetElementPtrNode;
    }

    @Override
    public boolean isElemPtrExpression() {
        return true;
    }

    @Override
    public LLVMElemPtrSymbol asElemPtrExpression() {
        return this;
    }

}
