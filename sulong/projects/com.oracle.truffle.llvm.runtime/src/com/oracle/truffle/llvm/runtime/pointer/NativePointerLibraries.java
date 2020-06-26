/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
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
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;

@ExportLibrary(value = LLVMNativeLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = LLVMAsForeignLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = InteropLibrary.class, receiverType = LLVMPointerImpl.class)
@SuppressWarnings("deprecation") // needed because the superclass implements ReferenceLibrary
abstract class NativePointerLibraries extends CommonPointerLibraries {

    @ExportMessage
    static boolean isNull(LLVMPointerImpl receiver) {
        return receiver.isNull();
    }

    @ExportMessage
    @ImportStatic(LLVMLanguage.class)
    static class IsExecutable {

        @Specialization
        static boolean doNative(LLVMPointerImpl receiver,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            return context.getFunctionDescriptor(receiver) != null;
        }
    }

    @ExportMessage
    @ImportStatic(LLVMLanguage.class)
    static class Execute {

        @SuppressWarnings("unused")
        @Specialization(limit = "5", guards = {"value.asNative() == cachedAddress", "cachedDescriptor != null"})
        static Object doNativeCached(@SuppressWarnings("unused") LLVMPointerImpl value, Object[] args,
                        @Cached("value.asNative()") @SuppressWarnings("unused") long cachedAddress,
                        @CachedContext(LLVMLanguage.class) ContextReference<LLVMContext> ctxRef,
                        @Cached("getDescriptor(ctxRef, value)") LLVMFunctionDescriptor cachedDescriptor,
                        @CachedLibrary("cachedDescriptor") InteropLibrary interop) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            return interop.execute(cachedDescriptor, args);
        }

        @Specialization(replaces = "doNativeCached")
        static Object doNative(LLVMPointerImpl value, Object[] args,
                        @CachedContext(LLVMLanguage.class) LLVMContext context,
                        @CachedLibrary(limit = "5") InteropLibrary interop) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
            LLVMFunctionDescriptor descriptor = context.getFunctionDescriptor(value);
            if (descriptor != null) {
                return interop.execute(descriptor, args);
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        static LLVMFunctionDescriptor getDescriptor(ContextReference<LLVMContext> ctxRef, LLVMNativePointer value) {
            return ctxRef.get().getFunctionDescriptor(value);
        }
    }

    @ExportMessage(library = LLVMNativeLibrary.class)
    @ExportMessage(library = InteropLibrary.class)
    @SuppressWarnings("unused")
    static boolean isPointer(LLVMPointerImpl receiver) {
        return true;
    }

    @ExportMessage(library = LLVMNativeLibrary.class)
    @ExportMessage(library = InteropLibrary.class)
    static long asPointer(LLVMPointerImpl receiver) {
        return receiver.asNative();
    }

    @ExportMessage
    static LLVMNativePointer toNativePointer(LLVMPointerImpl receiver) {
        return receiver;
    }

    @ExportMessage
    static int identityHashCode(LLVMPointerImpl receiver) {
        return Long.hashCode(receiver.asNative());
    }
}
