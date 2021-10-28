/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.pointer;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;

@ExportLibrary(value = InteropLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = LLVMAsForeignLibrary.class, receiverType = LLVMPointerImpl.class, useForAOT = true, useForAOTPriority = 1)
abstract class NativePointerLibraries extends CommonPointerLibraries {

    @ExportMessage
    static boolean isNull(LLVMPointerImpl receiver) {
        return receiver.isNull();
    }

    @ExportMessage
    static boolean isForeign(@SuppressWarnings("unused") LLVMPointerImpl receiver) {
        return false;
    }

    @ExportMessage
    @ImportStatic(LLVMLanguage.class)
    static class IsExecutable {

        @Specialization
        static boolean doNative(LLVMPointerImpl receiver, @CachedLibrary("receiver") InteropLibrary library) {
            return LLVMContext.get(library).getFunctionDescriptor(receiver) != null;
        }
    }

    @ExportMessage
    @ImportStatic(LLVMLanguage.class)
    static class Execute {

        static Assumption singleContextAssumption() {
            return LLVMLanguage.get(null).singleContextAssumption;
        }

        @Specialization(limit = "5", guards = {"value.asNative() == cachedAddress", "cachedDescriptor != null"}, assumptions = "singleContextAssumption()")
        static Object doNativeCached(@SuppressWarnings("unused") LLVMPointerImpl value, Object[] args,
                        @Cached("value.asNative()") @SuppressWarnings("unused") long cachedAddress,
                        @Cached("getDescriptor(value)") LLVMFunctionDescriptor cachedDescriptor,
                        @CachedLibrary("cachedDescriptor") InteropLibrary interop) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return interop.execute(cachedDescriptor, args);
        }

        @Specialization(replaces = "doNativeCached")
        static Object doNative(LLVMPointerImpl value, Object[] args,
                        @CachedLibrary(limit = "5") InteropLibrary interop) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            LLVMFunctionDescriptor descriptor = LLVMContext.get(interop).getFunctionDescriptor(value);
            if (descriptor != null) {
                return interop.execute(descriptor, args);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        static LLVMFunctionDescriptor getDescriptor(LLVMNativePointer value) {
            return LLVMContext.get(null).getFunctionDescriptor(value);
        }
    }

    /**
     * @param receiver
     * @see InteropLibrary#isPointer(Object)
     */
    @ExportMessage(library = InteropLibrary.class)
    static boolean isPointer(LLVMPointerImpl receiver) {
        return true;
    }

    @ExportMessage(library = InteropLibrary.class)
    static long asPointer(LLVMPointerImpl receiver) {
        return receiver.asNative();
    }

    @ExportMessage
    static int identityHashCode(LLVMPointerImpl receiver) {
        return Long.hashCode(receiver.asNative());
    }
}
