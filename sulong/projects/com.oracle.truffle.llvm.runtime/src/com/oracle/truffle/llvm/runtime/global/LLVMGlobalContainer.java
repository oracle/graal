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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
public final class LLVMGlobalContainer extends LLVMInternalTruffleObject {

    private long address;
    private Object contents;

    public LLVMGlobalContainer() {
        contents = 0L;
    }

    public Object get() {
        return contents;
    }

    public void set(Object value) {
        contents = value;
    }

    @ExportMessage
    public boolean isPointer() {
        return address != 0;
    }

    @ExportMessage
    public long asPointer() throws UnsupportedMessageException {
        if (isPointer()) {
            return address;
        } else {
            throw UnsupportedMessageException.create();
        }
    }

    public long getAddress() {
        return address;
    }

    @SuppressWarnings("static-method")
    public int getSize() {
        return 1;
    }

    @TruffleBoundary
    @ExportMessage
    public void toNative(@Cached LLVMToNativeNode toNative) {
        if (address == 0) {
            LLVMMemory memory = LLVMLanguage.getLanguage().getLLVMMemory();
            LLVMNativePointer pointer = memory.allocateMemory(toNative, 8);
            address = pointer.asNative();
            long value;
            if (contents instanceof Number) {
                value = ((Number) contents).longValue();
            } else {
                value = toNative.executeWithTarget(contents).asNative();
            }
            memory.putI64(toNative, pointer, value);
        }
    }

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    @ExportMessage
    static class ReadI8 {

        @Specialization(guards = "self.isPointer()")
        static byte readNative(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getI8(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        static byte readManaged(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedReadLibrary read) {
            interop.toNative(self);
            return read.readI8(self, offset);
        }
    }

    @ExportMessage
    static class ReadI16 {

        @Specialization(guards = "self.isPointer()")
        static short readNative(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getI16(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        static short readManaged(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedReadLibrary read) {
            interop.toNative(self);
            return read.readI16(self, offset);
        }
    }

    @ExportMessage
    static class ReadI32 {

        @Specialization(guards = "self.isPointer()")
        static int readNative(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getI32(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        static int readManaged(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedReadLibrary read) {
            interop.toNative(self);
            return read.readI32(self, offset);
        }
    }

    @ExportMessage
    static class ReadFloat {

        @Specialization(guards = "self.isPointer()")
        static float readNative(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getFloat(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        static float readManaged(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedReadLibrary read) {
            interop.toNative(self);
            return read.readFloat(self, offset);
        }
    }

    @ExportMessage
    static class ReadDouble {

        @Specialization(guards = "self.isPointer()")
        static double readNative(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getDouble(location, self.getAddress() + offset);
        }

        @Specialization(guards = "!self.isPointer()")
        static double readManaged(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedReadLibrary read) {
            interop.toNative(self);
            return read.readDouble(self, offset);
        }
    }

    @ExportMessage
    static class ReadGenericI64 {

        @Specialization(guards = "self.isPointer()")
        static long readNative(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getI64(location, self.getAddress() + offset);
        }

        @Specialization(guards = {"!self.isPointer()", "offset == 0"})
        static Object readManaged(LLVMGlobalContainer self, long offset) {
            assert offset == 0;
            return self.get();
        }

        @Specialization(guards = {"!self.isPointer()", "offset != 0"})
        static Object readFallback(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedReadLibrary read) {
            interop.toNative(self);
            return read.readGenericI64(self, offset);
        }
    }

    @ExportMessage
    static class ReadPointer {

        @Specialization(guards = "self.isPointer()")
        static LLVMPointer readNative(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") LLVMManagedReadLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            return language.getLLVMMemory().getPointer(location, self.getAddress() + offset);
        }

        @Specialization(guards = {"!self.isPointer()", "offset == 0"})
        static LLVMPointer readManaged(LLVMGlobalContainer self, long offset,
                        @Cached LLVMToPointerNode toPointer) {
            assert offset == 0;
            return toPointer.executeWithTarget(self.get());
        }

        @Specialization(guards = {"!self.isPointer()", "offset != 0"})
        static LLVMPointer readFallback(LLVMGlobalContainer self, long offset,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedReadLibrary read) {
            interop.toNative(self);
            return read.readPointer(self, offset);
        }
    }

    @ExportMessage
    static class WriteI8 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMGlobalContainer self, long offset, byte value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putI8(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        static void writeManaged(LLVMGlobalContainer self, long offset, byte value,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedWriteLibrary write) {
            interop.toNative(self);
            write.writeI8(self, offset, value);
        }
    }

    @ExportMessage
    static class WriteI16 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMGlobalContainer self, long offset, short value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putI16(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        static void writeManaged(LLVMGlobalContainer self, long offset, short value,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedWriteLibrary write) {
            interop.toNative(self);
            write.writeI16(self, offset, value);
        }
    }

    @ExportMessage
    static class WriteI32 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMGlobalContainer self, long offset, int value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putI32(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        static void writeManaged(LLVMGlobalContainer self, long offset, int value,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedWriteLibrary write) {
            interop.toNative(self);
            write.writeI32(self, offset, value);
        }
    }

    @ExportMessage
    static class WriteFloat {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMGlobalContainer self, long offset, float value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putFloat(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        static void writeManaged(LLVMGlobalContainer self, long offset, float value,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedWriteLibrary write) {
            interop.toNative(self);
            write.writeFloat(self, offset, value);
        }
    }

    @ExportMessage
    static class WriteDouble {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMGlobalContainer self, long offset, double value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putDouble(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = "!self.isPointer()")
        static void writeManaged(LLVMGlobalContainer self, long offset, double value,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedWriteLibrary write) {
            interop.toNative(self);
            write.writeDouble(self, offset, value);
        }
    }

    @ExportMessage
    static class WriteI64 {

        @Specialization(guards = "self.isPointer()")
        static void writeNative(LLVMGlobalContainer self, long offset, long value,
                        @CachedLibrary("self") LLVMManagedWriteLibrary location,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putI64(location, self.getAddress() + offset, value);
        }

        @Specialization(guards = {"!self.isPointer()", "offset == 0"})
        static void writeManaged(LLVMGlobalContainer self, long offset, long value) {
            assert offset == 0;
            self.set(value);
        }

        @Specialization(guards = {"!self.isPointer()", "offset != 0"})
        static void writeFallback(LLVMGlobalContainer self, long offset, long value,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedWriteLibrary write) {
            interop.toNative(self);
            write.writeI64(self, offset, value);
        }
    }

    @ExportMessage
    static class WriteGenericI64 {

        @Specialization(limit = "3", guards = "self.isPointer()")
        static void writeNative(LLVMGlobalContainer self, long offset, Object value,
                        @CachedLibrary("value") LLVMNativeLibrary toNative,
                        @CachedLanguage LLVMLanguage language) {
            long ptr = toNative.toNativePointer(value).asNative();
            language.getLLVMMemory().putI64(toNative, self.getAddress() + offset, ptr);
        }

        @Specialization(guards = {"!self.isPointer()", "offset == 0"})
        static void writeManaged(LLVMGlobalContainer self, long offset, Object value) {
            assert offset == 0;
            self.set(value);
        }

        @Specialization(guards = {"!self.isPointer()", "offset != 0"})
        static void writeFallback(LLVMGlobalContainer self, long offset, Object value,
                        @CachedLibrary("self") InteropLibrary interop,
                        @CachedLibrary("self") LLVMManagedWriteLibrary write) {
            interop.toNative(self);
            write.writeGenericI64(self, offset, value);
        }
    }

    public void dispose() {
        if (address != 0) {
            LLVMMemory memory = LLVMLanguage.getLanguage().getLLVMMemory();
            memory.free(null, address);
            address = 0;
        }
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("LLVMGlobalContainer (address = 0x%x, contents = %s)", address, contents);
    }

}
