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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

@ExportLibrary(value = LLVMNativeLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = InteropLibrary.class, receiverType = LLVMPointerImpl.class)
@ExportLibrary(value = LLVMAsForeignLibrary.class, receiverType = LLVMPointerImpl.class)
@SuppressWarnings("deprecation") // needed because the superclass implements ReferenceLibrary
abstract class ManagedPointerLibraries extends CommonPointerLibraries {

    @ExportMessage
    static boolean isNull(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.object") InteropLibrary interop) {
        if (receiver.getOffset() == 0) {
            return interop.isNull(receiver.object);
        } else {
            return false;
        }
    }

    @ExportMessage
    static boolean isExecutable(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.object") InteropLibrary interop) {
        if (receiver.getOffset() == 0) {
            return interop.isExecutable(receiver.object);
        } else {
            return false;
        }
    }

    @ExportMessage
    static Object execute(LLVMPointerImpl receiver, Object[] args,
                    @CachedLibrary("receiver.object") InteropLibrary interop) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        if (receiver.getOffset() == 0) {
            return interop.execute(receiver.object, args);
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage(library = LLVMNativeLibrary.class)
    @ExportMessage(library = InteropLibrary.class)
    static boolean isPointer(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.object") LLVMNativeLibrary natives) {
        return natives.isPointer(receiver.object);
    }

    @ExportMessage(library = LLVMNativeLibrary.class)
    @ExportMessage(library = InteropLibrary.class)
    static long asPointer(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.object") LLVMNativeLibrary natives) throws UnsupportedMessageException {
        return natives.asPointer(receiver.object) + receiver.getOffset();
    }

    @ExportMessage
    static void toNative(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.object") InteropLibrary interop) {
        interop.toNative(receiver.object);
    }

    @ExportMessage
    static LLVMNativePointer toNativePointer(LLVMPointerImpl receiver,
                    @CachedLibrary("receiver.object") LLVMNativeLibrary natives) {
        return natives.toNativePointer(receiver.object).increment(receiver.getOffset());
    }

    @ExportMessage
    static boolean isForeign(LLVMPointerImpl receiver,
                    @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns) {
        return isForeignTest(receiver, foreigns);
    }

    @ExportMessage
    static Object asForeign(LLVMPointerImpl receiver,
                    @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns) {
        return foreigns.asForeign(receiver.object);
    }

    static boolean isForeignTest(LLVMPointerImpl receiver, LLVMAsForeignLibrary foreigns) {
        return receiver.getOffset() == 0 && foreigns.isForeign(receiver.object);
    }

    @ExportMessage
    static class IdentityHashCode {

        @Specialization(guards = "!foreigns.isForeign(receiver.object)")
        @TruffleBoundary
        static int doInternal(LLVMPointerImpl receiver,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.object") LLVMAsForeignLibrary foreigns) {
            return hash(System.identityHashCode(receiver.getObject()), receiver.getOffset());
        }

        @Specialization(guards = "foreigns.isForeign(receiver.object)")
        static int doForeign(LLVMPointerImpl receiver,
                        @CachedLibrary("receiver.object") LLVMAsForeignLibrary foreigns,
                        @Cached ForeignIdentityHashNode hashForeign) {
            Object foreign = foreigns.asForeign(receiver.getObject());
            return hash(hashForeign.execute(foreign), receiver.getOffset());
        }

        private static int hash(int objHash, long offset) {
            int ret = 0;
            ret = ret * 31 + objHash;
            ret = ret * 31 + Long.hashCode(offset);
            return ret;
        }
    }

    @GenerateUncached
    abstract static class ForeignIdentityHashNode extends LLVMNode {

        abstract int execute(Object obj);

        @Specialization(limit = "3", rewriteOn = UnsupportedMessageException.class)
        int doUnchecked(Object obj,
                        @CachedLibrary("obj") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.identityHashCode(obj);
        }

        @Specialization(limit = "3", guards = "!interop.hasIdentity(obj)", replaces = "doUnchecked")
        @TruffleBoundary
        int doNoIdentity(Object obj,
                        @SuppressWarnings("unused") @CachedLibrary("obj") InteropLibrary interop) {
            return System.identityHashCode(obj);
        }

        @Specialization(limit = "3", replaces = "doNoIdentity")
        int doChecked(Object obj,
                        @CachedLibrary("obj") InteropLibrary interop) {
            try {
                return interop.identityHashCode(obj);
            } catch (UnsupportedMessageException ex) {
                return doNoIdentity(obj, interop);
            }
        }
    }
}
