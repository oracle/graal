/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

public final class LLVMGlobalVariableDescriptor {

    public enum MemoryState {
        UNKNOWN,
        EXTERNAL_NATIVE,
        SULONG_NATIVE,
        MANAGED_UNINIT,
        MANAGED_CACHED,
        MANAGED_GENERIC
    }

    private final String name;
    private final NativeResolver resolver;

    @CompilationFinal private MemoryState memoryState;
    @CompilationFinal private Assumption stateAssumption;
    @CompilationFinal private LLVMAddress globalVariabelAddress;

    @CompilationFinal private Object cachedManagedValue;
    private Object managedValue;

    private LLVMGlobalVariableDescriptor(String name, NativeResolver resolver) {
        this.name = name;
        this.memoryState = MemoryState.UNKNOWN;
        this.resolver = resolver;
        stateAssumption = Truffle.getRuntime().createAssumption();
    }

    public static LLVMGlobalVariableDescriptor create(String name, NativeResolver resolver) {
        return new LLVMGlobalVariableDescriptor(name, resolver);
    }

    public void declareInSulong(LLVMAddress address) {
        assert memoryState == MemoryState.UNKNOWN;

        stateAssumption.invalidate();
        CompilerDirectives.transferToInterpreterAndInvalidate();

        this.globalVariabelAddress = address;
        this.memoryState = MemoryState.MANAGED_UNINIT;

        stateAssumption = Truffle.getRuntime().createAssumption();
    }

    public MemoryState getState() {
        if (!stateAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return memoryState;
    }

    private void resolveExternal() {
        assert globalVariabelAddress == null;
        assert memoryState == MemoryState.UNKNOWN : memoryState;
        this.globalVariabelAddress = resolver.resolve();
        this.memoryState = MemoryState.EXTERNAL_NATIVE;
    }

    public String getName() {
        return name;
    }

    public LLVMAddress getNativeAddress() {
        switch (getState()) {
            case EXTERNAL_NATIVE:
                return globalVariabelAddress;
            case MANAGED_UNINIT:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.SULONG_NATIVE;
                stateAssumption = Truffle.getRuntime().createAssumption();

                return globalVariabelAddress;
            case MANAGED_CACHED:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.SULONG_NATIVE;
                stateAssumption = Truffle.getRuntime().createAssumption();

                return convertToNative(cachedManagedValue);
            case MANAGED_GENERIC:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.SULONG_NATIVE;
                stateAssumption = Truffle.getRuntime().createAssumption();

                return convertToNative(managedValue);
            case SULONG_NATIVE:
                return globalVariabelAddress;
            case UNKNOWN:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveExternal();
                stateAssumption = Truffle.getRuntime().createAssumption();
                return globalVariabelAddress;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }
    }

    private LLVMAddress convertToNative(Object value) throws AssertionError {
        if (value instanceof LLVMFunction) {
            storeFunction((LLVMFunction) value);
            return globalVariabelAddress;
        } else if (value instanceof LLVMTruffleNull) {
            storeNull((LLVMTruffleNull) value);
            return globalVariabelAddress;
        } else if (value instanceof LLVMAddress) {
            storeLLVMAddress((LLVMAddress) value);
            return globalVariabelAddress;
        } else if (value instanceof LLVMGlobalVariableDescriptor) {
            storeGlobalVariableDescriptor((LLVMGlobalVariableDescriptor) value);
            return globalVariabelAddress;
        } else if (value instanceof TruffleObject || value instanceof LLVMTruffleObject) {
            throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject!");
        } else if (value == null) {
            return globalVariabelAddress;
        }
        throw new AssertionError("Unknown type: " + value.getClass());
    }

    public void storeFunction(LLVMFunction function) {
        switch (getState()) {
            case UNKNOWN:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveExternal();
                stateAssumption = Truffle.getRuntime().createAssumption();
                LLVMMemory.putAddress(globalVariabelAddress, LLVMAddress.fromLong(function.getFunctionIndex()));
                break;
            case EXTERNAL_NATIVE:
            case SULONG_NATIVE:
                LLVMMemory.putAddress(globalVariabelAddress, LLVMAddress.fromLong(function.getFunctionIndex()));
                break;
            case MANAGED_UNINIT:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_CACHED;
                stateAssumption = Truffle.getRuntime().createAssumption();
                cachedManagedValue = function;
                break;
            case MANAGED_CACHED:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_GENERIC;
                stateAssumption = Truffle.getRuntime().createAssumption();
                managedValue = function;
                break;
            case MANAGED_GENERIC:
                managedValue = function;
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }

    }

    public void storeNull(LLVMTruffleNull n) {
        switch (getState()) {
            case UNKNOWN:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveExternal();
                stateAssumption = Truffle.getRuntime().createAssumption();
                LLVMMemory.putAddress(globalVariabelAddress, LLVMAddress.fromLong(0));
                break;
            case EXTERNAL_NATIVE:
            case SULONG_NATIVE:
                LLVMMemory.putAddress(globalVariabelAddress, LLVMAddress.fromLong(0));
                break;
            case MANAGED_UNINIT:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_CACHED;
                stateAssumption = Truffle.getRuntime().createAssumption();
                cachedManagedValue = n;
                break;
            case MANAGED_CACHED:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_GENERIC;
                stateAssumption = Truffle.getRuntime().createAssumption();
                managedValue = n;
                break;
            case MANAGED_GENERIC:
                managedValue = n;
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }
    }

    public void storeLLVMAddress(LLVMAddress a) {
        switch (getState()) {
            case UNKNOWN:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveExternal();
                stateAssumption = Truffle.getRuntime().createAssumption();
                LLVMMemory.putAddress(globalVariabelAddress, a);
                break;
            case EXTERNAL_NATIVE:
            case SULONG_NATIVE:
                LLVMMemory.putAddress(globalVariabelAddress, a);
                break;
            case MANAGED_UNINIT:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_CACHED;
                stateAssumption = Truffle.getRuntime().createAssumption();
                cachedManagedValue = a;
                break;
            case MANAGED_CACHED:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_GENERIC;
                stateAssumption = Truffle.getRuntime().createAssumption();
                managedValue = a;
                break;
            case MANAGED_GENERIC:
                managedValue = a;
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }
    }

    public void storeGlobalVariableDescriptor(LLVMGlobalVariableDescriptor d) {
        switch (getState()) {
            case UNKNOWN:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveExternal();
                stateAssumption = Truffle.getRuntime().createAssumption();
                LLVMMemory.putAddress(globalVariabelAddress, d.getNativeAddress());
                break;
            case EXTERNAL_NATIVE:
            case SULONG_NATIVE:
                LLVMMemory.putAddress(globalVariabelAddress, d.getNativeAddress());
                break;
            case MANAGED_UNINIT:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_CACHED;
                stateAssumption = Truffle.getRuntime().createAssumption();
                cachedManagedValue = d;
                break;
            case MANAGED_CACHED:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_GENERIC;
                stateAssumption = Truffle.getRuntime().createAssumption();
                managedValue = d;
                break;
            case MANAGED_GENERIC:
                managedValue = d;
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }

    }

    public void storeTruffleObject(TruffleObject foreign) {
        switch (getState()) {
            case UNKNOWN:
            case EXTERNAL_NATIVE:
            case SULONG_NATIVE:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Cannot store foreign value into global variable " + name + " - already transitioned to NATIVE");
            case MANAGED_UNINIT:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_CACHED;
                stateAssumption = Truffle.getRuntime().createAssumption();
                cachedManagedValue = foreign;
                break;
            case MANAGED_CACHED:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_GENERIC;
                stateAssumption = Truffle.getRuntime().createAssumption();
                managedValue = foreign;
                break;
            case MANAGED_GENERIC:
                managedValue = foreign;
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }
    }

    public void storeLLVMTruffleObject(LLVMTruffleObject value) {
        switch (getState()) {
            case UNKNOWN:
            case EXTERNAL_NATIVE:
            case SULONG_NATIVE:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Cannot store foreign value into global variable " + name + " - already transitioned to NATIVE");
            case MANAGED_UNINIT:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_CACHED;
                stateAssumption = Truffle.getRuntime().createAssumption();
                cachedManagedValue = value;
                break;
            case MANAGED_CACHED:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryState = MemoryState.MANAGED_GENERIC;
                stateAssumption = Truffle.getRuntime().createAssumption();
                managedValue = value;
                break;
            case MANAGED_GENERIC:
                managedValue = value;
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }
    }

    public Object load() {
        switch (getState()) {
            case UNKNOWN:
                stateAssumption.invalidate();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resolveExternal();
                stateAssumption = Truffle.getRuntime().createAssumption();
                return LLVMMemory.getAddress(globalVariabelAddress);
            case EXTERNAL_NATIVE:
            case SULONG_NATIVE:
                return LLVMMemory.getAddress(globalVariabelAddress);
            case MANAGED_UNINIT:
                return managedValue;
            case MANAGED_CACHED:
                return cachedManagedValue;
            case MANAGED_GENERIC:
                return managedValue;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();
        }
    }

    @Override
    public String toString() {
        return "LLVMGlobalVariableDescriptor [name=" + name + ", state=" + memoryState;
    }

}
