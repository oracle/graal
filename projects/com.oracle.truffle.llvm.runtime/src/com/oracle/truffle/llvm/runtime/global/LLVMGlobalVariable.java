/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.NativeAllocator;
import com.oracle.truffle.llvm.runtime.NativeResolver;
import com.oracle.truffle.llvm.runtime.global.Container.NativeContainer;
import com.oracle.truffle.llvm.runtime.global.Container.UninitializedContainer;
import com.oracle.truffle.llvm.runtime.global.Container.UninitializedManagedContainer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMGlobalVariable {

    private final String name;
    @CompilationFinal private Assumption assumption;
    // only access via getter and setter!
    @CompilationFinal private Container container;

    private LLVMGlobalVariable(String name, NativeResolver resolver, Type type) {
        CompilerAsserts.neverPartOfCompilation();
        this.name = name;
        this.assumption = Truffle.getRuntime().createAssumption();
        this.container = new UninitializedContainer(type, resolver);
    }

    Container getContainer() {
        if (!assumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return container;
    }

    void setContainer(Container value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.assumption.invalidate();
        this.assumption = Truffle.getRuntime().createAssumption();
        this.container = value;
    }

    public static LLVMGlobalVariable create(String name, NativeResolver resolver, Type type) {
        return new LLVMGlobalVariable(name, resolver, type);
    }

    public void declareInSulong(NativeAllocator allocator) {
        assert getContainer() instanceof UninitializedContainer;
        if (getContainer().getType() instanceof PointerType) {
            setContainer(new UninitializedManagedContainer(getContainer().getType(), allocator));
        } else {
            setContainer(new NativeContainer(getContainer().getType(), allocator.allocate()));
        }
    }

    public void destroy() {
        getContainer().destroy();
    }

    public LLVMAddress getNativeLocation() {
        return getContainer().getNativeLocation(this);
    }

    public Type getType() {
        return getContainer().getType();
    }

    public void putI1(boolean value) {
        getContainer().putI1(this, value);
    }

    public void putI8(byte value) {
        getContainer().putI8(this, value);
    }

    public void putI16(short value) {
        getContainer().putI16(this, value);
    }

    public void putAddress(LLVMAddress value) {
        getContainer().putAddress(this, value);
    }

    public void putDouble(double value) {
        getContainer().putDouble(this, value);
    }

    public void putFloat(float value) {
        getContainer().putFloat(this, value);
    }

    public void putI64(long value) {
        getContainer().putI64(this, value);
    }

    public void putI32(int value) {
        getContainer().putI32(this, value);
    }

    public Object get() {
        return getContainer().get(this);
    }

    public double getDouble() {
        return getContainer().getDouble(this);
    }

    public float getFloat() {
        return getContainer().getFloat(this);
    }

    public short getI16() {
        return getContainer().getI16(this);
    }

    public boolean getI1() {
        return getContainer().getI1(this);
    }

    public int getI32() {
        return getContainer().getI32(this);
    }

    public long getI64() {
        return getContainer().getI64(this);
    }

    public byte getI8() {
        return getContainer().getI8(this);
    }

    public void putTruffleObject(TruffleObject value) {
        getContainer().putTruffleObject(this, value);
    }

    public void putLLVMTruffleObject(LLVMTruffleObject value) {
        getContainer().putLLVMTruffleObject(this, value);
    }

    public void putFunction(LLVMFunction value) {
        getContainer().putFunction(this, value);
    }

    public void putBoxedPrimitive(LLVMBoxedPrimitive value) {
        getContainer().putBoxedPrimitive(this, value);
    }

    public boolean isUninitialized() {
        return getContainer() instanceof UninitializedContainer;
    }

    public void putGlobal(LLVMGlobalVariable value) {
        getContainer().putGlobal(this, value);
    }

    @Override
    public String toString() {
        return "GlobalVariable " + name;
    }

}
