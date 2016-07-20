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
package com.oracle.truffle.llvm.types;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class LLVMGlobalVariableStorage {

    /*
     * Global variable storage forms a lattice. A global variable storage location is UNINITIALIZED
     * when it is first created. When it is first assigned a native or managed value it moves to the
     * NATIVE or MANAGED states. However, some programs always set a native value on initialization.
     * We handle this with an extra INITIALIZED_NATIVE state that says that the global variable
     * storage location has an initial native value, but this will be ignored if you assign a
     * managed value. NATIVE and MANAGED are final states. When you have assigned explicitly a
     * native or managed value that will be the state of the global variable storage for the rest of
     * the program.
     *
     * UNINITIALIZED -> INITIALIZED_NATIVE -> (NATIVE | MANAGED)
     */

    private enum State {
        UNINITIALIZED,
        INITIALIZED_NATIVE,
        NATIVE,
        MANAGED
    }

    private final String name;

    /*
     * The state is compilation final. If you ever encounter a state that is not final (so a state
     * that is UNINITIALIZED or INITIALIZED_NATIVE) you must not compile that assumption into your
     * code. We do this by always transferring to interpreter and invalidating when those states are
     * matched.
     */

    @CompilationFinal private State state;

    private final LLVMAddress nativeStorage;
    private Object managedStorage;

    public LLVMGlobalVariableStorage(String name, LLVMAddress nativeStorage) {
        this.name = name;
        state = State.UNINITIALIZED;
        this.nativeStorage = nativeStorage;
    }

    public String getName() {
        return name;
    }

    public boolean isUninitialized() {
        return state == State.UNINITIALIZED;
    }

    public boolean isInitializedNative() {
        return state == State.INITIALIZED_NATIVE;
    }

    public boolean isNative() {
        return state == State.NATIVE;
    }

    public boolean isManaged() {
        return state == State.MANAGED;
    }

    public void initialize() {
        assert state == State.UNINITIALIZED;
        state = State.INITIALIZED_NATIVE;
    }

    public void initializeNative() {
        assert state == State.INITIALIZED_NATIVE;
        state = State.NATIVE;
    }

    public void initializeManaged() {
        assert state == State.INITIALIZED_NATIVE;
        state = State.MANAGED;
    }

    public LLVMAddress getNativeStorage() {
        assert state == State.UNINITIALIZED || state == State.INITIALIZED_NATIVE || state == State.NATIVE;
        return nativeStorage;
    }

    public Object getManagedStorage() {
        assert state == State.MANAGED;
        return managedStorage;
    }

    public void setManagedStorage(Object object) {
        assert state == State.MANAGED;
        assert !(object instanceof LLVMAddress);
        managedStorage = object;
    }

    @Override
    public String toString() {
        return "LLVMGlobalVariableStorage [name=" + name + ", state=" + state + ", nativeStorage=" + nativeStorage + ", managedStorage=" + managedStorage + "]";
    }

}
