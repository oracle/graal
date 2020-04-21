/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue.Builder;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public final class LLVMToDebugDeclaration implements LLVMDebugValue.Builder {

    private static final LLVMNativeLibrary NATIVE_LIBRARY = LibraryFactory.resolve(LLVMNativeLibrary.class).getUncached();

    private static final LLVMToDebugDeclaration INSTANCE = new LLVMToDebugDeclaration();

    public static Builder getInstance() {
        return INSTANCE;
    }

    private LLVMToDebugDeclaration() {
        // singleton
    }

    @Override
    public LLVMDebugValue build(Object value) {
        LLVMPointer pointer;
        if (LLVMPointer.isInstance(value)) {
            pointer = LLVMPointer.cast(value);
        } else {
            if (!NATIVE_LIBRARY.isPointer(value)) {
                // @llvm.dbg.declare is supposed to tell us the location of the variable in memory,
                // there should never be a case where this cannot be resolved to a pointer. If it
                // happens anyhow this is a safe default.
                return LLVMDebugValue.UNAVAILABLE;
            }
            pointer = NATIVE_LIBRARY.toNativePointer(value);
        }
        if (LLVMManagedPointer.isInstance(pointer)) {
            final Object target = LLVMManagedPointer.cast(pointer).getObject();
            if (target instanceof LLVMGlobalContainer) {
                return fromGlobalContainer((LLVMGlobalContainer) target);
            }
        }
        return new LLDBMemoryValue(pointer);
    }

    private static LLVMDebugValue fromGlobalContainer(LLVMGlobalContainer globalContainer) {
        if (globalContainer.isPointer()) {
            try {
                return new LLDBMemoryValue(LLVMNativePointer.create(globalContainer.asPointer()));
            } catch (UnsupportedMessageException e) {
                return LLVMDebugValue.UNAVAILABLE;
            }
        }

        final Object currentValue = globalContainer.get();
        if (LLVMPointer.isInstance(currentValue)) {
            return new LLDBMemoryValue(LLVMPointer.cast(currentValue));
        } else {
            return new LLDBMemoryValue(LLVMManagedPointer.create(currentValue));
        }
    }
}
