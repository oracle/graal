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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMGlobal implements LLVMSymbol {

    private final LLVMContext context;
    private final LLVMSourceSymbol sourceSymbol;
    private final boolean readOnly;

    /**
     * Globals currently store the value they are bound to directly in here. If Sulong moves to a
     * model that supports sharing ASTs between contexts, this needs to be adapted to an indirect
     * lookup via the context.
     */
    private final AssumedValue<Object> target = new AssumedValue<>("llvm global", null);

    @CompilationFinal private String name;
    @CompilationFinal private PointerType type;
    @CompilationFinal private ExternalLibrary library;
    @CompilationFinal private boolean interopTypeCached;
    @CompilationFinal private LLVMInteropType interopType;

    public static LLVMGlobal create(LLVMContext context, String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly) {
        return new LLVMGlobal(context, name, type, sourceSymbol, readOnly);
    }

    private LLVMGlobal(LLVMContext context, String name, PointerType type, LLVMSourceSymbol sourceSymbol, boolean readOnly) {
        this.context = context;
        this.name = name;
        this.type = type;
        this.sourceSymbol = sourceSymbol;
        this.readOnly = readOnly;

        this.library = null;
        this.interopTypeCached = false;
        this.interopType = null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public ExternalLibrary getLibrary() {
        return library;
    }

    public LLVMInteropType getInteropType() {
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
        return library != null;
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
            this.library = newLibrary;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Found multiple definitions of global " + getName() + ".");
        }
    }

    public Object getTarget() {
        assert target.get() != null;
        return target.get();
    }

    public boolean isInitialized() {
        return target.get() != null;
    }

    public void setTarget(Object target) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.target.set(target);
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
    public LLVMFunctionDescriptor asFunction() {
        throw new IllegalStateException("Global " + name + " is not a function.");
    }

    @Override
    public LLVMGlobal asGlobalVariable() {
        return this;
    }
}
