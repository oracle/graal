/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.library.internal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

abstract class LLVMNativeLibraryDefaults {

    @ExportLibrary(value = LLVMNativeLibrary.class, receiverType = Object.class)
    static class DefaultLibrary {

        @ExportMessage
        @SuppressWarnings("unused")
        static class IsPointer {

            @Specialization(guards = "interop.isPointer(receiver)")
            static boolean doPointer(Object receiver,
                            @CachedLibrary("receiver") InteropLibrary interop) {
                return true;
            }

            @Specialization(guards = "!interop.isPointer(receiver)")
            static boolean doOther(Object receiver,
                            @CachedLibrary("receiver") InteropLibrary interop) {
                return interop.isNull(receiver);
            }
        }

        @ExportMessage
        static class AsPointer {

            @Specialization(guards = "interop.isPointer(receiver)")
            static long doPointer(Object receiver,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Shared("exception") @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException {
                try {
                    return interop.asPointer(receiver);
                } catch (UnsupportedMessageException ex) {
                    exceptionProfile.enter();
                    if (interop.isNull(receiver)) {
                        return 0;
                    } else {
                        throw ex;
                    }
                }
            }

            @Specialization(guards = "!interop.isPointer(receiver)")
            static long doNullCheck(Object receiver,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Shared("exception") @Cached BranchProfile exceptionProfile) throws UnsupportedMessageException {
                if (interop.isNull(receiver)) {
                    return 0;
                } else {
                    exceptionProfile.enter();
                    throw UnsupportedMessageException.create();
                }
            }
        }

        @ExportMessage
        @SuppressWarnings("unused")
        static class ToNativePointer {

            @Specialization(guards = "interop.isNull(receiver)")
            static LLVMNativePointer doNull(Object receiver,
                            @CachedLibrary("receiver") InteropLibrary interop) {
                return LLVMNativePointer.createNull();
            }

            @Specialization(guards = "!interop.isNull(receiver)")
            static LLVMNativePointer doNotNull(Object receiver,
                            @CachedLibrary("receiver") InteropLibrary interop,
                            @Shared("exception") @Cached BranchProfile exceptionProfile) {
                try {
                    interop.toNative(receiver);
                    return LLVMNativePointer.create(interop.asPointer(receiver));
                } catch (UnsupportedMessageException ex) {
                    exceptionProfile.enter();
                    throw new LLVMPolyglotException(interop, "Cannot convert %s to native pointer.", receiver);
                }
            }
        }

    }

    @ExportLibrary(value = LLVMNativeLibrary.class, receiverType = Long.class)
    static class LongLibrary {

        @ExportMessage
        @SuppressWarnings("unused")
        static boolean isPointer(Long receiver) {
            return true;
        }

        @ExportMessage
        static long asPointer(Long receiver) {
            return receiver;
        }

        @ExportMessage
        static LLVMNativePointer toNativePointer(Long receiver) {
            return LLVMNativePointer.create(receiver);
        }
    }

    @ExportLibrary(value = LLVMNativeLibrary.class, receiverType = int[].class)
    @SuppressWarnings("unused")
    static class ArrayLibrary {

        @ExportMessage
        static boolean isPointer(int[] receiver) {
            return false;
        }

        @ExportMessage
        static long asPointer(int[] receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        static LLVMNativePointer toNativePointer(int[] receiver,
                        @CachedLibrary("receiver") LLVMNativeLibrary self) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMPolyglotException(self, "Cannot convert virtual allocation object to native pointer.", receiver);
        }
    }
}
