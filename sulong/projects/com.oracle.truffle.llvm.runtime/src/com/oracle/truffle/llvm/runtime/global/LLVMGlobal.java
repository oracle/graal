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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.IDGenerater;
import com.oracle.truffle.llvm.runtime.IDGenerater.BitcodeID;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMElemPtrSymbol;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMThreadLocalSymbol;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMGlobal extends LLVMSymbol {

    private final LLVMSourceSymbol sourceSymbol;
    private final boolean readOnly;
    public static final LLVMGlobal[] EMPTY = {};

    private final String name;
    private final PointerType type;
    @CompilationFinal private boolean interopTypeCached;
    @CompilationFinal private LLVMInteropType interopType;

    public static LLVMGlobal create(String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly, int index, BitcodeID id, boolean exported, boolean externalWeak) {
        if (index < 0) {
            throw new AssertionError("Invalid index for LLVM global: " + index);
        }
        if (id == null) {
            throw new AssertionError("Invalid index for LLVM global: " + id);
        }
        return new LLVMGlobal(name, type, sourceSymbol, readOnly, index, id, exported, externalWeak);
    }

    public static LLVMGlobal createUnavailable(String name) {
        return new LLVMGlobal(name + " (unavailable)", PointerType.VOID, null, true, LLVMSymbol.INVALID_INDEX, IDGenerater.INVALID_ID, false, false);
    }

    private LLVMGlobal(String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly, int globalIndex, BitcodeID id, boolean exported, boolean externalWeak) {
        super(name, id, globalIndex, exported, externalWeak);
        this.name = name;
        this.type = type;
        this.sourceSymbol = sourceSymbol;
        this.readOnly = readOnly;

        this.interopTypeCached = false;
        this.interopType = null;
    }

    @Override
    public String toString() {
        return "(" + type + ")" + name;
    }

    public LLVMInteropType getInteropType(LLVMContext context) {
        if (!interopTypeCached) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMSourceType sourceType = sourceSymbol != null ? sourceSymbol.getType() : null;
            interopType = context.getLanguage().getInteropType(sourceType);
            interopTypeCached = true;
        }
        return interopType;
    }

    public String getSourceName() {
        return sourceSymbol != null ? sourceSymbol.getName() : name;
    }

    public Type getPointeeType() {
        return type.getPointeeType();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public boolean isFunction() {
        return false;
    }

    @Override
    public boolean isGlobalVariable() {
        return true;
    }

    @Override
    public boolean isAlias() {
        return false;
    }

    @Override
    public LLVMFunction asFunction() {
        throw new IllegalStateException("Global " + name + " is not a function.");
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        return this;
    }

    @Override
    public boolean isElemPtrExpression() {
        return false;
    }

    @Override
    public LLVMElemPtrSymbol asElemPtrExpression() {
        throw new IllegalStateException("Global " + name + " is not a GetElementPointer symbol.");
    }

    @Override
    public boolean isThreadLocalSymbol() {
        return false;
    }

    @Override
    public LLVMThreadLocalSymbol asThreadLocalSymbol() {
        throw new IllegalStateException("GetElementPointerConstant " + getName() + " has to be resolved and might not be a thread local global variable.");
    }
}
