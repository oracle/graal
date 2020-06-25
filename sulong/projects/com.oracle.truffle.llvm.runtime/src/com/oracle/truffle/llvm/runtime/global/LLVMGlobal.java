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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
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
    @CompilationFinal private PointerType type;
    @CompilationFinal private boolean interopTypeCached;
    @CompilationFinal private LLVMInteropType interopType;

    public static LLVMGlobal create(String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly, int index, int id, boolean exported) {
        if (index < 0) {
            throw new AssertionError("Invalid index for LLVM global: " + index);
        }
        if (id < 0) {
            throw new AssertionError("Invalid index for LLVM global: " + id);
        }
        return new LLVMGlobal(name, type, sourceSymbol, readOnly, index, id, exported);
    }

    public static LLVMGlobal createUnavailable(String name) {
        return new LLVMGlobal(name + " (unavailable)", PointerType.VOID, null, true, -1, -1, false);
    }

    private LLVMGlobal(String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly, int globalIndex, int moduleId, boolean exported) {
        super(name, null, moduleId, globalIndex, exported);
        this.name = name;
        this.type = type;
        this.sourceSymbol = sourceSymbol;
        this.readOnly = readOnly;

        this.interopTypeCached = false;
        this.interopType = null;
    }

    @Override
    public String toString() {
        return "(" + type + ")" + (getLibrary() == null ? "" : getLibrary().getName() + "::") + name;
    }

    public LLVMInteropType getInteropType(LLVMContext context) {
        if (!interopTypeCached) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMSourceType sourceType = sourceSymbol != null ? sourceSymbol.getType() : null;
            interopType = context.getInteropType(sourceType);
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

    @Override
    public boolean isDefined() {
        return getLibrary() != null;
    }

    public void define(ExternalLibrary newLibrary) {
        define(type, newLibrary);
    }

    // TODO (chaeubl): overwriting the type is a workaround to avoid type mismatches that occur for
    // C++ code
    public void define(PointerType newType, ExternalLibrary newLibrary) {
        assert newType != null && newLibrary != null;
        if (!isDefined()) {
            this.type = newType;
            setLibrary(newLibrary);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Found multiple definitions of global " + getName() + ".");
        }
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
}
