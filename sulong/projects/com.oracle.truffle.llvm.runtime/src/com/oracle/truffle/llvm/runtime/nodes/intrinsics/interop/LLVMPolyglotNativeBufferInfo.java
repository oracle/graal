/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Buffer;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMPolyglotNativeBufferInfo extends LLVMNode {
    protected boolean isNativeBuffer(LLVMPointer pointer) {
        return pointer.getExportType() instanceof Buffer;
    }

    protected Buffer asBufferType(LLVMPointer pointer) {
        return (Buffer) pointer.getExportType();
    }

    @GenerateUncached
    public abstract static class HasBufferElements extends LLVMPolyglotNativeBufferInfo {

        public abstract boolean execute(LLVMPointer pointer);

        @Specialization
        public boolean executeNative(LLVMNativePointer pointer) {
            return isNativeBuffer(pointer);
        }

        @Specialization(guards = "!foreignsLib.isForeign(pointer)")
        public boolean executeNonForeign(LLVMManagedPointer pointer,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreignsLib) {
            return isNativeBuffer(pointer);
        }

        @Fallback
        public boolean unsupported(@SuppressWarnings("unused") LLVMPointer receiver) {
            return false;
        }
    }

    @GenerateUncached
    public abstract static class IsBufferWritable extends LLVMPolyglotNativeBufferInfo {

        public abstract boolean execute(LLVMPointer receiver) throws UnsupportedMessageException;

        @Specialization(guards = "isNativeBuffer(impl)")
        public boolean isNativeBufferWritable(LLVMNativePointer impl) {
            return asBufferType(impl).isWritable();
        }

        @Specialization(guards = {"!foreignsLib.isForeign(pointer)", "isNativeBuffer(pointer)"})
        public boolean executeNonForeign(LLVMManagedPointer pointer,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreignsLib) {
            return asBufferType(pointer).isWritable();
        }

        @Fallback
        public boolean unsupported(@SuppressWarnings("unused") LLVMPointer receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

    @GenerateUncached
    public abstract static class GetBufferSize extends LLVMPolyglotNativeBufferInfo {

        public abstract long execute(LLVMPointer receiver) throws UnsupportedMessageException;

        @Specialization(guards = "isNativeBuffer(impl)")
        public long getNativeBufferSize(LLVMNativePointer impl) {
            return asBufferType(impl).getSize();
        }

        @Specialization(guards = {"!foreignsLib.isForeign(pointer)", "isNativeBuffer(pointer)"})
        public long executeNonForeign(LLVMManagedPointer pointer,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreignsLib) {
            return asBufferType(pointer).getSize();
        }

        @Fallback
        public long unsupported(@SuppressWarnings("unused") LLVMPointer receiver) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }
    }

}
