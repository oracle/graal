/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.ValueKind;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMTruffleManagedMalloc extends LLVMIntrinsic {

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(LLVMManagedReadLibrary.class)
    @ExportLibrary(LLVMManagedWriteLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public static class ManagedMallocObject extends LLVMInternalTruffleObject {

        private final LLVMPointer[] contents;

        // no need to specify length in the type, since we implement the `getArraySize` message
        private static final LLVMInteropType NATIVE_TYPE = ValueKind.POINTER.type.toArray(0);

        public ManagedMallocObject(int entries) {
            contents = new LLVMPointer[entries];
        }

        public LLVMPointer get(int index) {
            return contents[index];
        }

        public void set(int index, LLVMPointer value) {
            contents[index] = value;
        }

        @ExportMessage
        boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        Object getNativeType() {
            return NATIVE_TYPE;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return contents.length;
        }

        @ExportMessage(name = "isArrayElementReadable")
        @ExportMessage(name = "isArrayElementModifiable")
        @ExportMessage(name = "isArrayElementInsertable")
        boolean isArrayElementValid(long index) {
            return 0 <= index && index < getArraySize();
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Shared("exception") @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (isArrayElementValid(index)) {
                return get((int) index);
            } else {
                exception.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        void writeArrayElement(long index, Object value,
                        @Cached LLVMToPointerNode toPointer,
                        @Shared("exception") @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (isArrayElementValid(index)) {
                set((int) index, toPointer.executeWithTarget(value));
            } else {
                exception.enter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage(name = "isReadable")
        @ExportMessage(name = "isWritable")
        boolean isAccessible() {
            return true;
        }

        @ExportMessage
        byte readI8(long offset,
                        @CachedLibrary("this") LLVMManagedReadLibrary read) {
            throw new LLVMPolyglotException(read, "Can't read I8 from managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        short readI16(long offset,
                        @CachedLibrary("this") LLVMManagedReadLibrary read) {
            throw new LLVMPolyglotException(read, "Can't read I16 from managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        int readI32(long offset,
                        @CachedLibrary("this") LLVMManagedReadLibrary read) {
            throw new LLVMPolyglotException(read, "Can't read I32 from managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        double readDouble(long offset,
                        @CachedLibrary("this") LLVMManagedReadLibrary read) {
            throw new LLVMPolyglotException(read, "Can't read double from managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        LLVMPointer readPointer(long offset,
                        @Cached BranchProfile exception,
                        @CachedLibrary("this") LLVMManagedReadLibrary read) {
            if (offset % LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES == 0) {
                long idx = offset / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES;
                if (idx == (int) idx) {
                    return get((int) idx);
                }
            }

            exception.enter();
            throw new LLVMPolyglotException(read, "Can't read pointer from managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        LLVMPointer readGenericI64(long offset,
                        @CachedLibrary("this") LLVMManagedReadLibrary read) {
            return read.readPointer(this, offset);
        }

        @ExportMessage
        void writeI8(long offset, @SuppressWarnings("unused") byte value,
                        @CachedLibrary("this") LLVMManagedWriteLibrary write) {
            throw new LLVMPolyglotException(write, "Can't write I8 to managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        void writeI16(long offset, @SuppressWarnings("unused") short value,
                        @CachedLibrary("this") LLVMManagedWriteLibrary write) {
            throw new LLVMPolyglotException(write, "Can't write I16 to managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        void writeI32(long offset, @SuppressWarnings("unused") int value,
                        @CachedLibrary("this") LLVMManagedWriteLibrary write) {
            throw new LLVMPolyglotException(write, "Can't write I32 to managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        void writePointer(long offset, LLVMPointer value,
                        @Cached BranchProfile exception,
                        @CachedLibrary("this") LLVMManagedWriteLibrary write) {
            if (offset % LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES == 0) {
                long idx = offset / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES;
                if (idx == (int) idx) {
                    set((int) idx, value);
                    return;
                }
            }

            exception.enter();
            throw new LLVMPolyglotException(write, "Can't write pointer to managed malloc object at offset %d.", offset);
        }

        @ExportMessage
        void writeI64(long offset, long value,
                        @CachedLibrary("this") LLVMManagedWriteLibrary write) {
            write.writePointer(this, offset, LLVMNativePointer.create(value));
        }

        @ExportMessage
        void writeGenericI64(long offset, Object value,
                        @Cached LLVMToPointerNode toPointer,
                        @CachedLibrary("this") LLVMManagedWriteLibrary write) {
            write.writePointer(this, offset, toPointer.executeWithTarget(value));
        }

    }

    @Specialization
    protected Object doIntrinsic(long size) {
        if (size < 0) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Can't truffle_managed_malloc less than zero bytes");
        }

        long sizeInWords = (size + LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES - 1) / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES;
        if (sizeInWords > Integer.MAX_VALUE) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Can't truffle_managed_malloc for more than 2^31 objects");
        }

        return LLVMManagedPointer.create(new ManagedMallocObject((int) sizeInWords));
    }
}
