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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class LLVMGlobalVariableDescriptor {

    /*
     * Global variables can be either declared in the managed source code, or they can be external.
     * We determine this lazily, as we don't know if a global variable will be declared in managed
     * code or not until we have loaded all the managed code. Therefore global variables all start
     * in an UNKNOWN state.
     *
     * If a declaration is seen in managed code, the global variable moves to the DECLARED state.
     *
     * A global variable declared in managed code can either store managed or native values. Code
     * will often assign a native value as part of initialization, before wanting to use managed
     * values, so an initial write of a native value puts the global variable into the
     * INITIAL_NATIVE state. A global variable in either the DECLARED or INITIAL_NATIVE state can
     * then store a native or managed value, and on the first store it will transition to the NATIVE
     * or MANAGED state, and will stay in that state permanently.
     *
     * Reading a global variable in the DECLARED state is an error. Writing a managed value to a
     * global variable in the NATIVE state is an error.
     *
     * If a declaration is never seen, it transitions to the NATIVE state the first time it is read
     * or written, and the address is looked up in the native symbol table.
     *
     * The state of global variables is permanently fixed the first time they read or the first time
     * they are written with a managed value. They are permanently fixed the second time that they
     * written with a native value, due to our handling of initialization writes of native values
     * which may be followed by an actual write with a managed value or another native value.
     */

    private enum State {
        UNKNOWN,
        DECLARED,
        INITIAL_NATIVE,
        NATIVE,
        MANAGED
    }

    private final String name;
    private final NativeResolver nativeResolver;

    /*
     * The state is compilation final. If the global variable state is not final (so a state that is
     * UNKNOWN, DECLARED or INITIAL_NATIVE) then you must not compile it into your code.
     * Transferring to interpreter and invalidate when a non-final state is matched.
     */

    @CompilationFinal private State state;

    /*
     * The native storage address is compilation final. If the global variable state is not final,
     * you must not compile the native storage into your code. Transferring to interpreter and
     * invalidate when a non-final state is matched.
     */

    @CompilationFinal private LLVMAddress nativeStorage;

    private Object managedStorage;

    public LLVMGlobalVariableDescriptor(String name, NativeResolver nativeResolver) {
        this.name = name;
        this.nativeResolver = nativeResolver;
        state = State.UNKNOWN;
    }

    public String getName() {
        return name;
    }

    public boolean needsTransition() {
        return state == State.UNKNOWN || state == State.DECLARED || state == State.INITIAL_NATIVE;
    }

    public boolean isNative() {
        return state == State.NATIVE;
    }

    public boolean isManaged() {
        return state == State.MANAGED;
    }

    public void declare(LLVMAddress setNativeStorage) {
        assert state == State.UNKNOWN : this;
        state = State.DECLARED;
        nativeStorage = setNativeStorage;
    }

    public void transition(boolean write, boolean managed) {
        CompilerAsserts.neverPartOfCompilation();

        if (state == State.UNKNOWN) {
            /*
             * If we are about to read or write the global variable and it is still in the UNKNOWN
             * state, then it was never declared in managed code and we should treat it as NATIVE
             * and extern. If we're about to write a managed value then that's an error.
             */

            if (managed) {
                throw new UnsupportedOperationException("Sulong can't store a Truffle object in a global variable " + name + " that hasn't been declared in managed code");
            }

            state = State.NATIVE;
            nativeStorage = nativeResolver.resolve();
        } else if (state == State.DECLARED || state == State.INITIAL_NATIVE) {
            if (write && managed) {
                /*
                 * If we're writing a managed value then the state just goes straight to managed.
                 */

                state = State.MANAGED;
            } else {
                /*
                 * If we're writing and the global variable has been declared in managed code, then
                 * we treat the global variable as initially native, meaning that the next write
                 * could be a managed value and we'd forget about this initial value. Otherwise
                 * treat it as native.
                 */

                if (write && state == State.DECLARED) {
                    state = State.INITIAL_NATIVE;
                } else {
                    state = State.NATIVE;
                }
            }
        } else {
            throw new IllegalStateException();
        }

        // TODO we may now be wasting the native storage we already allocated
    }

    public LLVMAddress getNativeStorage() {
        assert state == State.INITIAL_NATIVE || state == State.NATIVE || state == State.DECLARED : this;
        return nativeStorage;
    }

    public Object getManagedStorage() {
        assert state == State.MANAGED : this;
        return managedStorage;
    }

    public void setManagedStorage(Object object) {
        assert state == State.MANAGED : this;
        assert !(object instanceof LLVMAddress);
        managedStorage = object;
    }

    @Override
    public String toString() {
        return "LLVMGlobalVariableDescriptor [name=" + name + ", state=" + state + ", nativeStorage=" + nativeStorage + ", managedStorage=" + managedStorage + "]";
    }

    public boolean isDeclared() {
        return state == State.DECLARED;
    }

}
