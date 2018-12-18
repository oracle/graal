/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.library.LLVMNativeLibrary;

@ExportLibrary(value = LLVMNativeLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = InteropLibrary.class, receiverType = LLVMPointerImpl.class)
abstract class ManagedPointerLibraries extends CommonPointerLibraries {

    @ExportMessage(limit = "5")
    static boolean isNull(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.getObject()") InteropLibrary interop) {
        if (receiver.getOffset() == 0) {
            return interop.isNull(receiver.getObject());
        } else {
            return false;
        }
    }

    @ExportMessage(limit = "5")
    static boolean isExecutable(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.getObject()") InteropLibrary interop) {
        if (receiver.getOffset() == 0) {
            return interop.isExecutable(receiver.getObject());
        } else {
            return false;
        }
    }

    @ExportMessage(limit = "5")
    static Object execute(LLVMPointerImpl receiver, Object[] args,
                    @CachedLibrary("receiver.getObject()") InteropLibrary interop) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        if (receiver.getOffset() == 0) {
            return interop.execute(receiver.getObject(), args);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(library = LLVMNativeLibrary.class, limit = "1")
    static boolean accepts(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.getObject()") DynamicDispatchLibrary dispatch,
                    @Cached(value = "dispatch.dispatch(receiver.getObject())", allowUncached = true) Class<?> dispatchClass) {
        // TODO better solution?
        return dispatchClass == dispatch.dispatch(receiver.getObject());
    }

    @ExportMessage(library = LLVMNativeLibrary.class, limit = "1")
    @ExportMessage(library = InteropLibrary.class, limit = "5")
    static boolean isPointer(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.getObject()") LLVMNativeLibrary natives) {
        return natives.isPointer(receiver.getObject());
    }

    @ExportMessage(library = LLVMNativeLibrary.class, limit = "1")
    @ExportMessage(library = InteropLibrary.class, limit = "5")
    static long asPointer(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.getObject()") LLVMNativeLibrary natives) throws UnsupportedMessageException {
        return natives.asPointer(receiver.getObject()) + receiver.getOffset();
    }

    @ExportMessage(limit = "5")
    static void toNative(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.getObject()") InteropLibrary interop) {
        interop.toNative(receiver.getObject());
    }

    @ExportMessage(limit = "1")
    static LLVMNativePointer toNativePointer(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.getObject()") LLVMNativeLibrary natives) {
        return natives.toNativePointer(receiver.getObject()).increment(receiver.getOffset());
    }
}
