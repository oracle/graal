/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadDoubleNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadFloatNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI16Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI32Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI64Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI8Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadPointerNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMWriteToForeignObjectNode.ForeignWriteDoubleNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMWriteToForeignObjectNode.ForeignWriteFloatNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMWriteToForeignObjectNode.ForeignWriteI16Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMWriteToForeignObjectNode.ForeignWriteI32Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMWriteToForeignObjectNode.ForeignWriteI64Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMWriteToForeignObjectNode.ForeignWriteI8Node;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativePointerSupport;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

abstract class LLVMManagedAccessDefaults {

    @ExportLibrary(value = LLVMManagedReadLibrary.class, receiverType = Object.class, useForAOT = false)
    static class FallbackRead {

        @ExportMessage
        static boolean isReadable(Object obj,
                        @CachedLibrary("obj") InteropLibrary interop) {
            // TODO
            return interop.accepts(obj);
        }

        @ExportMessage
        static byte readI8(Object obj, long offset,
                        @Cached ForeignReadI8Node readNode) {
            return readNode.execute(obj, offset);
        }

        @ExportMessage
        static short readI16(Object obj, long offset,
                        @Cached ForeignReadI16Node readNode) {
            return readNode.execute(obj, offset);
        }

        @ExportMessage
        static int readI32(Object obj, long offset,
                        @Cached ForeignReadI32Node readNode) {
            return readNode.execute(obj, offset);
        }

        @ExportMessage
        static long readI64(Object obj, long offset,
                        @Shared("readI64") @Cached ForeignReadI64Node readNode) throws UnexpectedResultException {
            return readNode.executeLong(obj, offset);
        }

        @ExportMessage
        static Object readGenericI64(Object obj, long offset,
                        @Shared("readI64") @Cached ForeignReadI64Node readNode) {
            return readNode.execute(obj, offset);
        }

        @ExportMessage
        static float readFloat(Object obj, long offset,
                        @Cached ForeignReadFloatNode readNode) {
            return readNode.execute(obj, offset);
        }

        @ExportMessage
        static double readDouble(Object obj, long offset,
                        @Cached ForeignReadDoubleNode readNode) {
            return readNode.execute(obj, offset);
        }

        @ExportMessage
        static LLVMPointer readPointer(Object obj, long offset,
                        @Cached ForeignReadPointerNode readNode) {
            return readNode.execute(obj, offset);
        }
    }

    @ExportLibrary(value = LLVMManagedWriteLibrary.class, receiverType = Object.class, useForAOT = false)
    static class FallbackWrite {

        @ExportMessage
        static boolean isWritable(Object obj,
                        @CachedLibrary(limit = "5") InteropLibrary interop) {
            // TODO
            return interop.accepts(obj);
        }

        @ExportMessage
        static void writeI8(Object obj, long offset, byte value,
                        @Cached ForeignWriteI8Node write) {
            write.execute(obj, offset, value);
        }

        @ExportMessage
        static void writeI16(Object obj, long offset, short value,
                        @Cached ForeignWriteI16Node write) {
            write.execute(obj, offset, value);
        }

        @ExportMessage
        static void writeI32(Object obj, long offset, int value,
                        @Cached ForeignWriteI32Node write) {
            write.execute(obj, offset, value);
        }

        @ExportMessage
        static void writeI64(Object obj, long offset, long value,
                        @Shared("writeI64") @Cached ForeignWriteI64Node write) {
            write.executeLong(obj, offset, value);
        }

        @ExportMessage
        static void writeGenericI64(Object obj, long offset, Object value,
                        @Shared("writeI64") @Cached ForeignWriteI64Node write) {
            write.execute(obj, offset, value);
        }

        @ExportMessage
        static void writeFloat(Object obj, long offset, float value,
                        @Cached ForeignWriteFloatNode write) {
            write.execute(obj, offset, value);
        }

        @ExportMessage
        static void writeDouble(Object obj, long offset, double value,
                        @Cached ForeignWriteDoubleNode write) {
            write.execute(obj, offset, value);
        }

        @ExportMessage
        static void writePointer(Object obj, long offset, LLVMPointer value,
                        @Shared("writeI64") @Cached ForeignWriteI64Node write) {
            write.executePointer(obj, offset, value);
        }
    }

    @ExportLibrary(value = LLVMManagedReadLibrary.class, receiverType = byte[].class, useForAOT = false)
    @ExportLibrary(value = LLVMManagedWriteLibrary.class, receiverType = byte[].class, useForAOT = false)
    static class VirtualAlloc {

        private static int checkOffset(long offset) throws IndexOutOfBoundsException {
            int io = (int) offset;
            if (io == offset) {
                return io;
            } else {
                throw new IndexOutOfBoundsException();
            }
        }

        @ExportMessage(name = "isReadable")
        @ExportMessage(name = "isWritable")
        static boolean isAccessible(@SuppressWarnings("unused") byte[] obj) {
            return true;
        }

        @ExportMessage
        static byte readI8(byte[] obj, long offset,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                return LLVMLanguage.get(self).getByteArraySupport().getByte(obj, checkOffset(offset));
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static short readI16(byte[] obj, long offset,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                return LLVMLanguage.get(self).getByteArraySupport().getShort(obj, checkOffset(offset));
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static int readI32(byte[] obj, long offset,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                return LLVMLanguage.get(self).getByteArraySupport().getInt(obj, checkOffset(offset));
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        // @ExportMessage(name = "readI64") for boxing elimination (blocked by GR-17850)
        @ExportMessage(name = "readGenericI64")
        static long readI64(byte[] obj, long offset,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                return LLVMLanguage.get(self).getByteArraySupport().getLong(obj, checkOffset(offset));
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static float readFloat(byte[] obj, long offset,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                return LLVMLanguage.get(self).getByteArraySupport().getFloat(obj, checkOffset(offset));
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static double readDouble(byte[] obj, long offset,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                return LLVMLanguage.get(self).getByteArraySupport().getDouble(obj, checkOffset(offset));
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static LLVMPointer readPointer(byte[] obj, long offset,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            return LLVMNativePointer.create(readI64(obj, offset, self, exception));
        }

        @ExportMessage
        static void writeI8(byte[] obj, long offset, byte value,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                LLVMLanguage.get(self).getByteArraySupport().putByte(obj, checkOffset(offset), value);
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static void writeI16(byte[] obj, long offset, short value,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                LLVMLanguage.get(self).getByteArraySupport().putShort(obj, checkOffset(offset), value);
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static void writeI32(byte[] obj, long offset, int value,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                LLVMLanguage.get(self).getByteArraySupport().putInt(obj, checkOffset(offset), value);
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static void writeI64(byte[] obj, long offset, long value,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                LLVMLanguage.get(self).getByteArraySupport().putLong(obj, checkOffset(offset), value);
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static void writeFloat(byte[] obj, long offset, float value,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                LLVMLanguage.get(self).getByteArraySupport().putFloat(obj, checkOffset(offset), value);
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static void writeDouble(byte[] obj, long offset, double value,
                        @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                        @Shared("exception") @Cached BranchProfile exception) {
            try {
                LLVMLanguage.get(self).getByteArraySupport().putDouble(obj, checkOffset(offset), value);
            } catch (IndexOutOfBoundsException ex) {
                exception.enter();
                throw new LLVMPolyglotException(self, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
            }
        }

        @ExportMessage
        static class WriteGenericI64 {

            private static void writeLong(byte[] obj, long offset, long value, Node pos, BranchProfile exception, LLVMLanguage language) {
                try {
                    language.getByteArraySupport().putLong(obj, checkOffset(offset), value);
                } catch (IndexOutOfBoundsException ex) {
                    exception.enter();
                    throw new LLVMPolyglotException(pos, "Out-of-bounds access: offset=%d, size=%d", offset, obj.length);
                }
            }

            @Specialization
            static void writeI64(byte[] obj, long offset, long value,
                            @CachedLibrary("obj") LLVMManagedWriteLibrary self,
                            @Shared("exception") @Cached BranchProfile exception) {
                writeLong(obj, offset, value, self, exception, LLVMLanguage.get(self));
            }

            @Specialization
            static void writePointer(byte[] obj, long offset, LLVMPointer value,
                            @Cached LLVMNativePointerSupport.ToNativePointerNode toNativePointer,
                            @Exclusive @Cached BranchProfile exception) {
                LLVMNativePointer nativeValue = toNativePointer.execute(value);
                writeLong(obj, offset, nativeValue.asNative(), toNativePointer, exception, LLVMLanguage.get(toNativePointer));
            }
        }
    }
}
