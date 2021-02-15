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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropReadNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropWriteNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadDoubleNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadFloatNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI16Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI32Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI64Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadI8Node;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMReadFromForeignObjectNode.ForeignReadPointerNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedAccessDefaultsFactory.GetWriteIdentifierNodeGen;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

abstract class LLVMManagedAccessDefaults {

    @ExportLibrary(value = LLVMManagedReadLibrary.class, receiverType = Object.class)
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

    abstract static class ManagedAccessNode extends LLVMNode {

        /**
         * Annotation helper for guards to check whether {@code obj} is an auto-deref handle (e.g. a
         * wrapped pointer). This helper assumes that an isPointer call returns true for {@code obj}
         * .
         */
        static boolean isWrappedAutoDerefHandle(LLVMLanguage language, LLVMNativeLibrary nativeLibrary, Object obj) {
            try {
                return LLVMNode.isAutoDerefHandle(language, nativeLibrary.asPointer(obj));
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

    }

    @ExportLibrary(value = LLVMManagedWriteLibrary.class, receiverType = Object.class)
    static class FallbackWrite {

        @ExportMessage
        static boolean isWritable(Object obj,
                        @Shared("write") @Cached ManagedWriteNode write,
                        @CachedLibrary(limit = "5") InteropLibrary interop) {
            return write.canAccess(obj, interop);
        }

        @ExportMessage
        static void writeI8(Object obj, long offset, byte value,
                        @Shared("write") @Cached ManagedWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I8);
        }

        @ExportMessage
        static void writeI16(Object obj, long offset, short value,
                        @Shared("write") @Cached ManagedWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I16);
        }

        @ExportMessage
        static void writeI32(Object obj, long offset, int value,
                        @Shared("write") @Cached ManagedWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I32);
        }

        @ExportMessage
        static void writeGenericI64(Object obj, long offset, Object value,
                        @Shared("write") @Cached ManagedWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.I64);
        }

        @ExportMessage
        static void writeFloat(Object obj, long offset, float value,
                        @Shared("write") @Cached ManagedWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.FLOAT);
        }

        @ExportMessage
        static void writeDouble(Object obj, long offset, double value,
                        @Shared("write") @Cached ManagedWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.DOUBLE);
        }

        @ExportMessage
        static void writePointer(Object obj, long offset, LLVMPointer value,
                        @Shared("write") @Cached ManagedWriteNode write) {
            write.executeWrite(obj, offset, value, ForeignToLLVMType.POINTER);
        }
    }

    @GenerateUncached
    abstract static class ManagedWriteNode extends ManagedAccessNode {

        public boolean canAccess(Object obj, InteropLibrary interop) {
            return interop.accepts(obj);
        }

        abstract void executeWrite(Object obj, long offset, Object value, ForeignToLLVMType type);

        @Specialization(guards = {"nativeLibrary.isPointer(receiver)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver)"})
        static void doPointer(Object receiver, long offset, Object value, @SuppressWarnings("unused") ForeignToLLVMType type,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary(limit = "3") LLVMNativeLibrary nativeLibrary,
                        @Cached LLVMMemoryWriteNode memoryWriteNode) {
            try {
                memoryWriteNode.executeWrite(nativeLibrary.asPointer(receiver) + offset, value);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver)"})
        static void doTypedHandle(Object receiver, long offset, Object value, ForeignToLLVMType type,
                        @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                        @Shared("interopWrite") @Cached LLVMInteropWriteNode interopWrite,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary(limit = "3") LLVMNativeLibrary nativeLibrary,
                        @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Cached LLVMAsForeignNode asForeignNode,
                        @Shared("fallbackWrite") @Cached FallbackWriteNode fallbackWrite,
                        @Cached("createBinaryProfile()") ConditionProfile typedWriteProfile) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver));
                Object nativeType = nativeTypes.getNativeType(receiver);
                if (typedWriteProfile.profile(nativeType == null || nativeType instanceof LLVMInteropType.Structured)) {
                    interopWrite.execute((LLVMInteropType.Structured) nativeType, asForeignNode.execute(recv), recv.getOffset() + offset, value, type);
                } else {
                    fallbackWrite.executeWrite(recv.getObject(), offset, value, type);
                }
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"!natives.isPointer(receiver)"})
        static void doValue(Object receiver, long offset, Object value, ForeignToLLVMType type,
                        @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                        @Shared("interopWrite") @Cached LLVMInteropWriteNode interopWrite,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMNativeLibrary natives,
                        @Shared("fallbackWrite") @Cached FallbackWriteNode fallbackWrite,
                        @Cached("createBinaryProfile()") ConditionProfile typedWriteProfile) {
            Object nativeType = nativeTypes.getNativeType(receiver);
            if (typedWriteProfile.profile(nativeType == null || nativeType instanceof LLVMInteropType.Structured)) {
                interopWrite.execute((LLVMInteropType.Structured) nativeType, receiver, offset, value, type);
            } else {
                fallbackWrite.executeWrite(receiver, offset, value, type);
            }
        }
    }

    @GenerateUncached
    abstract static class LLVMMemoryWriteNode extends LLVMNode {

        abstract void executeWrite(long ptr, Object value);

        @Specialization
        void writeI8(long ptr, byte value,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putI8(this, ptr, value);
        }

        @Specialization
        void writeI16(long ptr, short value,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putI16(this, ptr, value);
        }

        @Specialization
        void writeI32(long ptr, int value,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putI32(this, ptr, value);
        }

        @Specialization
        void writeI64(long ptr, long value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary(limit = "3") LLVMNativeLibrary nativeLibrary) {
            long valuePointer = nativeLibrary.toNativePointer(value).asNative();
            language.getLLVMMemory().putI64(this, ptr, valuePointer);
        }

        @Specialization
        void writeFloat(long ptr, float value,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putFloat(this, ptr, value);
        }

        @Specialization
        void writeDouble(long ptr, double value,
                        @CachedLanguage LLVMLanguage language) {
            language.getLLVMMemory().putDouble(this, ptr, value);
        }

        @Specialization
        void writePointer(long ptr, Object value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary(limit = "3") LLVMNativeLibrary nativeLibrary) {
            LLVMNativePointer nativePointer = nativeLibrary.toNativePointer(value);
            language.getLLVMMemory().putPointer(this, ptr, nativePointer);
        }

    }

    @GenerateUncached
    abstract static class FallbackWriteNode extends LLVMNode {

        abstract void executeWrite(Object obj, long offset, Object value, ForeignToLLVMType type);

        /**
         * @param obj
         * @param offset
         * @param value
         * @param type
         * @see #executeWrite(Object, long, Object, ForeignToLLVMType)
         */
        @Specialization(limit = "3", guards = "type == cachedType")
        void doCachedType(Object obj, long offset, Object value, ForeignToLLVMType type,
                        @Cached("type") @SuppressWarnings("unused") ForeignToLLVMType cachedType,
                        @Cached(parameters = "cachedType") LLVMDataEscapeNode dataEscape,
                        @CachedLibrary(limit = "5") InteropLibrary interop,
                        @Cached GetWriteIdentifierNode getWriteIdentifier) {
            doWrite(obj, offset, value, dataEscape, interop, getWriteIdentifier);
        }

        @Specialization(replaces = "doCachedType")
        @TruffleBoundary
        void doUncached(Object obj, long offset, Object value, ForeignToLLVMType type) {
            doWrite(obj, offset, value, LLVMDataEscapeNode.getUncached(type), InteropLibrary.getFactory().getUncached(), GetWriteIdentifierNodeGen.getUncached());
        }

        private void doWrite(Object obj, long offset, Object value, LLVMDataEscapeNode dataEscape, InteropLibrary interop, GetWriteIdentifierNode getWriteIdentifier) {
            long identifier = getWriteIdentifier.execute(offset, value);
            Object escaped = dataEscape.executeWithTarget(value);
            try {
                interop.writeArrayElement(obj, identifier, escaped);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }

    @GenerateUncached
    abstract static class GetWriteIdentifierNode extends LLVMNode {

        abstract long execute(long offset, Object value);

        @Specialization
        long doByte(long offset, @SuppressWarnings("unused") byte value) {
            return offset;
        }

        @Specialization
        long doShort(long offset, @SuppressWarnings("unused") short value) {
            return offset / 2;
        }

        @Specialization
        long doChar(long offset, @SuppressWarnings("unused") char value) {
            return offset / 2;
        }

        @Specialization
        long doInt(long offset, @SuppressWarnings("unused") int value) {
            return offset / 4;
        }

        @Specialization
        long doFloat(long offset, @SuppressWarnings("unused") float value) {
            return offset / 4;
        }

        @Fallback // long, double or non-primitive
        long doDouble(long offset, @SuppressWarnings("unused") Object value) {
            return offset / 8;
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
