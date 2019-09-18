/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectReadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

abstract class LLVMManagedAccessDefaults {

    @ExportLibrary(value = LLVMManagedReadLibrary.class, receiverType = Object.class)
    @ImportStatic(LLVMObjectAccessFactory.class)
    static class FallbackRead {

        @ExportMessage
        static boolean isReadable(Object obj,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return read.canAccess(obj);
        }

        @ExportMessage
        static byte readI8(Object obj, long offset,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return LLVMTypesGen.asByte(read.executeRead(obj, offset, ForeignToLLVMType.I8));
        }

        @ExportMessage
        static short readI16(Object obj, long offset,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return LLVMTypesGen.asShort(read.executeRead(obj, offset, ForeignToLLVMType.I16));
        }

        @ExportMessage
        static int readI32(Object obj, long offset,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return LLVMTypesGen.asInteger(read.executeRead(obj, offset, ForeignToLLVMType.I32));
        }

        @ExportMessage
        static Object readGenericI64(Object obj, long offset,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return read.executeRead(obj, offset, ForeignToLLVMType.I64);
        }

        @ExportMessage
        static float readFloat(Object obj, long offset,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return LLVMTypesGen.asFloat(read.executeRead(obj, offset, ForeignToLLVMType.FLOAT));
        }

        @ExportMessage
        static double readDouble(Object obj, long offset,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return LLVMTypesGen.asDouble(read.executeRead(obj, offset, ForeignToLLVMType.DOUBLE));
        }

        @ExportMessage
        static LLVMPointer readPointer(Object obj, long offset,
                        @Cached LLVMToPointerNode toPointer,
                        @Shared("read") @Cached(value = "createRead()", allowUncached = true) LLVMObjectReadNode read) {
            return toPointer.executeWithTarget(read.executeRead(obj, offset, ForeignToLLVMType.POINTER));
        }
    }

    @ExportLibrary(value = LLVMManagedWriteLibrary.class, receiverType = Object.class)
    @ImportStatic(LLVMObjectAccessFactory.class)
    static class FallbackWrite {

        @ExportMessage
        static boolean isWritable(Object obj,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            return write.canAccess(obj);
        }

        @ExportMessage
        static void writeI8(Object obj, long offset, byte value,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I8);
        }

        @ExportMessage
        static void writeI16(Object obj, long offset, short value,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I16);
        }

        @ExportMessage
        static void writeI32(Object obj, long offset, int value,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I32);
        }

        @ExportMessage
        static void writeGenericI64(Object obj, long offset, Object value,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I64);
        }

        @ExportMessage
        static void writeFloat(Object obj, long offset, float value,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.FLOAT);
        }

        @ExportMessage
        static void writeDouble(Object obj, long offset, double value,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.DOUBLE);
        }

        @ExportMessage
        static void writePointer(Object obj, long offset, LLVMPointer value,
                        @Shared("write") @Cached(value = "createWrite()", allowUncached = true) LLVMObjectWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.POINTER);
        }
    }

    @ExportLibrary(value = LLVMManagedReadLibrary.class, receiverType = int[].class)
    @ExportLibrary(value = LLVMManagedWriteLibrary.class, receiverType = int[].class)
    static class VirtualAlloc {

        @ExportMessage(name = "isReadable")
        @ExportMessage(name = "isWritable")
        static boolean isAccessible(@SuppressWarnings("unused") int[] obj) {
            return true;
        }

        @ExportMessage
        static byte readI8(int[] obj, long offset,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(UnsafeArrayAccess.class).getI8(obj, offset);
        }

        @ExportMessage
        static short readI16(int[] obj, long offset,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(UnsafeArrayAccess.class).getI16(obj, offset);
        }

        @ExportMessage
        static int readI32(int[] obj, long offset,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(UnsafeArrayAccess.class).getI32(obj, offset);
        }

        // @ExportMessage(name = "readI64") for boxing elimination (blocked by GR-17850)
        @ExportMessage(name = "readGenericI64")
        static long readI64(int[] obj, long offset,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(UnsafeArrayAccess.class).getI64(obj, offset);
        }

        @ExportMessage
        static float readFloat(int[] obj, long offset,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(UnsafeArrayAccess.class).getFloat(obj, offset);
        }

        @ExportMessage
        static double readDouble(int[] obj, long offset,
                        @CachedLanguage LLVMLanguage language) {
            return language.getCapability(UnsafeArrayAccess.class).getDouble(obj, offset);
        }

        @ExportMessage
        static LLVMPointer readPointer(int[] obj, long offset,
                        @CachedLanguage LLVMLanguage language) {
            return LLVMNativePointer.create(readI64(obj, offset, language));
        }

        @ExportMessage
        static void writeI8(int[] obj, long offset, byte value,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(UnsafeArrayAccess.class).writeI8(obj, offset, value);
        }

        @ExportMessage
        static void writeI16(int[] obj, long offset, short value,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(UnsafeArrayAccess.class).writeI16(obj, offset, value);
        }

        @ExportMessage
        static void writeI32(int[] obj, long offset, int value,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(UnsafeArrayAccess.class).writeI32(obj, offset, value);
        }

        @ExportMessage
        static void writeI64(int[] obj, long offset, long value,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(UnsafeArrayAccess.class).writeI64(obj, offset, value);
        }

        @ExportMessage
        static void writeFloat(int[] obj, long offset, float value,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(UnsafeArrayAccess.class).writeFloat(obj, offset, value);
        }

        @ExportMessage
        static void writeDouble(int[] obj, long offset, double value,
                        @CachedLanguage LLVMLanguage language) {
            language.getCapability(UnsafeArrayAccess.class).writeDouble(obj, offset, value);
        }

        @ExportMessage
        static class WriteGenericI64 {

            @Specialization
            static void writeI64(int[] obj, long offset, long value,
                            @CachedLanguage LLVMLanguage language) {
                language.getCapability(UnsafeArrayAccess.class).writeI64(obj, offset, value);
            }

            @Specialization(limit = "3")
            static void writePointer(int[] obj, long offset, LLVMPointer value,
                            @CachedLibrary("value") LLVMNativeLibrary nativeLib,
                            @CachedLanguage LLVMLanguage language) {
                writeI64(obj, offset, nativeLib.toNativePointer(value).asNative(), language);
            }
        }
    }
}
