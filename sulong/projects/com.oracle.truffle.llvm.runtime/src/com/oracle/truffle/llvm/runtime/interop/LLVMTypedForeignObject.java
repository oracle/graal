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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObjectFactory.ForeignGetTypeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropReadNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropWriteNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

@ValueType
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(ReferenceLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public final class LLVMTypedForeignObject extends LLVMInternalTruffleObject implements LLVMObjectAccess {

    final Object foreign;
    private final LLVMInteropType.Structured type;

    public static LLVMTypedForeignObject create(Object foreign, LLVMInteropType.Structured type) {
        return new LLVMTypedForeignObject(foreign, type);
    }

    public static LLVMTypedForeignObject createUnknown(Object foreign) {
        return new LLVMTypedForeignObject(foreign, null);
    }

    private LLVMTypedForeignObject(Object foreign, LLVMInteropType.Structured type) {
        this.foreign = foreign;
        this.type = type;
    }

    public Object getForeign() {
        return foreign;
    }

    public LLVMInteropType.Structured getType() {
        return type;
    }

    @Override
    public LLVMObjectReadNode createReadNode() {
        return new ForeignReadNode();
    }

    @Override
    public LLVMObjectWriteNode createWriteNode() {
        return new ForeignWriteNode();
    }

    @Override
    public boolean equals(Object obj) {
        // ignores the type explicitly
        if (obj instanceof LLVMTypedForeignObject) {
            LLVMTypedForeignObject other = (LLVMTypedForeignObject) obj;
            return foreign.equals(other.foreign);
        }
        return false;
    }

    @Override
    public int hashCode() {
        // ignores the type explicitly
        return foreign.hashCode();
    }

    @GenerateUncached
    public abstract static class ForeignGetTypeNode extends LLVMNode {

        public abstract LLVMInteropType.Structured execute(Object object);

        @Specialization(limit = "3")
        public LLVMInteropType.Structured getType(Object object,
                        @CachedLibrary("object") NativeTypeLibrary typeLibrary) {
            Object type = typeLibrary.getNativeType(object);
            if (type == null || type instanceof LLVMInteropType.Structured) {
                return (LLVMInteropType.Structured) type;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(this, "Invalid type %s returned from foreign object.", type);
            }
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    static class GetNativeType {

        @Specialization(guards = "typeLibrary.hasNativeType(object.foreign)")
        static Object getType(LLVMTypedForeignObject object,
                        @CachedLibrary("object.foreign") NativeTypeLibrary typeLibrary) {
            return typeLibrary.getNativeType(object.foreign);
        }

        @Specialization(limit = "3", guards = "!typeLibrary.hasNativeType(object.getForeign())")
        static LLVMInteropType.Structured doFallback(LLVMTypedForeignObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object.getForeign()") NativeTypeLibrary typeLibrary) {
            return object.getType();
        }
    }

    static class ForeignReadNode extends LLVMNode implements LLVMObjectReadNode {

        @Child LLVMInteropReadNode read = LLVMInteropReadNode.create();
        @Child ForeignGetTypeNode getType = ForeignGetTypeNodeGen.create();

        @Override
        public Object executeRead(Object obj, long offset, ForeignToLLVMType type) {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            return read.execute(getType.execute(object), object.getForeign(), offset, type);
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }

    static class ForeignWriteNode extends LLVMNode implements LLVMObjectWriteNode {

        @Child LLVMInteropWriteNode write = LLVMInteropWriteNode.create();
        @Child ForeignGetTypeNode getType = ForeignGetTypeNodeGen.create();

        @Override
        public void executeWrite(Object obj, long offset, Object value, ForeignToLLVMType writeType) {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            write.execute(getType.execute(object), object.getForeign(), offset, value, writeType);
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    static boolean isWrappedAutoDerefHandle(LLVMLanguage language, LLVMNativeLibrary nativeLibrary, Object obj) {
        try {
            return LLVMNode.isAutoDerefHandle(language, nativeLibrary.asPointer(obj));
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(ex);
        }
    }

    @ExportMessage
    static class ReadI8 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static byte doPointer(LLVMTypedForeignObject receiver, long offset,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                return language.getLLVMMemory().getI8(nativeLibrary.asPointer(receiver.getForeign()) + offset);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static byte doHandle(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                return (byte) read.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, ForeignToLLVMType.I8);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static byte doValue(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            return (byte) read.execute(getType.execute(receiver), receiver.getForeign(), offset, ForeignToLLVMType.I8);
        }
    }

    @ExportMessage
    static class ReadI16 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static short doPointer(LLVMTypedForeignObject receiver, long offset,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                return language.getLLVMMemory().getI16(nativeLibrary.asPointer(receiver.getForeign()) + offset);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static short doHandle(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                return (short) read.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, ForeignToLLVMType.I16);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static short doValue(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            return (short) read.execute(getType.execute(receiver), receiver.getForeign(), offset, ForeignToLLVMType.I16);
        }
    }

    @ExportMessage
    static class ReadI32 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static int doPointer(LLVMTypedForeignObject receiver, long offset,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                return language.getLLVMMemory().getI32(nativeLibrary.asPointer(receiver.getForeign()) + offset);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static int doHandle(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                return (int) read.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, ForeignToLLVMType.I32);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static int doValue(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            return (int) read.execute(getType.execute(receiver), receiver.getForeign(), offset, ForeignToLLVMType.I32);
        }
    }

    @ExportMessage
    static class ReadGenericI64 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static Object doPointer(LLVMTypedForeignObject receiver, long offset,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                return language.getLLVMMemory().getI64(nativeLibrary.asPointer(receiver.getForeign()) + offset);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static Object doHandle(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                return read.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, ForeignToLLVMType.I64);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static Object doValue(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            return read.execute(getType.execute(receiver), receiver.getForeign(), offset, ForeignToLLVMType.I64);
        }
    }

    @ExportMessage
    static class ReadFloat {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static float doPointer(LLVMTypedForeignObject receiver, long offset,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                return language.getLLVMMemory().getFloat(nativeLibrary.asPointer(receiver.getForeign()) + offset);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static float doHandle(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                return (float) read.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, ForeignToLLVMType.FLOAT);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static float doValue(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            return (float) read.execute(getType.execute(receiver), receiver.getForeign(), offset, ForeignToLLVMType.FLOAT);
        }
    }

    @ExportMessage
    static class ReadDouble {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static double doPointer(LLVMTypedForeignObject receiver, long offset,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                return language.getLLVMMemory().getDouble(nativeLibrary.asPointer(receiver.getForeign()) + offset);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static double doHandle(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                return (double) read.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, ForeignToLLVMType.DOUBLE);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static double doValue(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            return (double) read.execute(getType.execute(receiver), receiver.getForeign(), offset, ForeignToLLVMType.DOUBLE);
        }
    }

    @ExportMessage
    static class ReadPointer {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static LLVMPointer doPointer(LLVMTypedForeignObject receiver, long offset,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                return language.getLLVMMemory().getPointer(nativeLibrary.asPointer(receiver.getForeign()) + offset);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static LLVMPointer doHandle(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                return LLVMPointer.cast(read.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, ForeignToLLVMType.POINTER));
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static LLVMPointer doValue(LLVMTypedForeignObject receiver, long offset,
                        @Shared("read") @Cached LLVMInteropReadNode read,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            return LLVMPointer.cast(read.execute(getType.execute(receiver), receiver.getForeign(), offset, ForeignToLLVMType.POINTER));
        }
    }

    @ExportMessage
    static class WriteI8 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doPointer(LLVMTypedForeignObject receiver, long offset, byte value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                language.getLLVMMemory().putI8(nativeLibrary.asPointer(receiver.getForeign()) + offset, value);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doHandle(LLVMTypedForeignObject receiver, long offset, byte value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                write.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, value, ForeignToLLVMType.I8);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static void doValue(LLVMTypedForeignObject receiver, long offset, byte value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            write.execute(getType.execute(receiver), receiver.getForeign(), offset, value, ForeignToLLVMType.I8);
        }
    }

    @ExportMessage
    static class WriteI16 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doPointer(LLVMTypedForeignObject receiver, long offset, short value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                language.getLLVMMemory().putI16(nativeLibrary.asPointer(receiver.getForeign()) + offset, value);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doHandle(LLVMTypedForeignObject receiver, long offset, short value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                write.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, value, ForeignToLLVMType.I16);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static void doValue(LLVMTypedForeignObject receiver, long offset, short value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            write.execute(getType.execute(receiver), receiver.getForeign(), offset, value, ForeignToLLVMType.I16);
        }
    }

    @ExportMessage
    static class WriteI32 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doPointer(LLVMTypedForeignObject receiver, long offset, int value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                language.getLLVMMemory().putI32(nativeLibrary.asPointer(receiver.getForeign()) + offset, value);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doHandle(LLVMTypedForeignObject receiver, long offset, int value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                write.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, value, ForeignToLLVMType.I32);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static void doValue(LLVMTypedForeignObject receiver, long offset, int value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            write.execute(getType.execute(receiver), receiver.getForeign(), offset, value, ForeignToLLVMType.I32);
        }
    }

    @ExportMessage
    static class WriteGenericI64 {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doPointer(LLVMTypedForeignObject receiver, long offset, Object value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @CachedLibrary(limit = "5") LLVMNativeLibrary nativePointerLibrary) {
            try {
                long valuePointer = nativePointerLibrary.toNativePointer(value).asNative();
                language.getLLVMMemory().putI64(nativeLibrary.asPointer(receiver.getForeign()) + offset, valuePointer);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doHandle(LLVMTypedForeignObject receiver, long offset, Object value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                write.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, value, ForeignToLLVMType.I64);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static void doValue(LLVMTypedForeignObject receiver, long offset, Object value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            write.execute(getType.execute(receiver), receiver.getForeign(), offset, value, ForeignToLLVMType.I64);
        }
    }

    @ExportMessage
    static class WriteFloat {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doPointer(LLVMTypedForeignObject receiver, long offset, float value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                language.getLLVMMemory().putFloat(nativeLibrary.asPointer(receiver.getForeign()) + offset, value);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doHandle(LLVMTypedForeignObject receiver, long offset, float value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                write.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, value, ForeignToLLVMType.FLOAT);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static void doValue(LLVMTypedForeignObject receiver, long offset, float value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            write.execute(getType.execute(receiver), receiver.getForeign(), offset, value, ForeignToLLVMType.FLOAT);
        }
    }

    @ExportMessage
    static class WriteDouble {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doPointer(LLVMTypedForeignObject receiver, long offset, double value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            try {
                language.getLLVMMemory().putDouble(nativeLibrary.asPointer(receiver.getForeign()) + offset, value);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doHandle(LLVMTypedForeignObject receiver, long offset, double value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                write.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, value, ForeignToLLVMType.DOUBLE);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static void doValue(LLVMTypedForeignObject receiver, long offset, double value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            write.execute(getType.execute(receiver), receiver.getForeign(), offset, value, ForeignToLLVMType.DOUBLE);
        }
    }

    @ExportMessage
    static class WritePointer {
        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "!isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doPointer(LLVMTypedForeignObject receiver, long offset, LLVMPointer value,
                        @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @CachedLibrary(limit = "5") LLVMNativeLibrary nativePointerLibrary) {
            try {
                LLVMNativePointer nativePointer = nativePointerLibrary.toNativePointer(value);
                language.getLLVMMemory().putPointer(nativeLibrary.asPointer(receiver.getForeign()) + offset, nativePointer);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = {"nativeLibrary.isPointer(receiver.foreign)", "isWrappedAutoDerefHandle(language, nativeLibrary, receiver.foreign)"})
        static void doHandle(LLVMTypedForeignObject receiver, long offset, LLVMPointer value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLanguage LLVMLanguage language,
                        @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary,
                        @Shared("receiverNode") @Cached LLVMDerefHandleGetReceiverNode receiverNode,
                        @Shared("asForeignNode") @Cached LLVMAsForeignNode asForeignNode) {
            try {
                LLVMManagedPointer recv = receiverNode.execute(nativeLibrary.asPointer(receiver.getForeign()));
                write.execute(getType.execute(recv.getObject()), asForeignNode.execute(recv), recv.getOffset() + offset, value, ForeignToLLVMType.POINTER);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }

        @Specialization(guards = "!nativeLibrary.isPointer(receiver.foreign)")
        static void doValue(LLVMTypedForeignObject receiver, long offset, LLVMPointer value,
                        @Shared("write") @Cached LLVMInteropWriteNode write,
                        @Shared("getType") @Cached ForeignGetTypeNode getType,
                        @SuppressWarnings("unused") @CachedLibrary("receiver.foreign") LLVMNativeLibrary nativeLibrary) {
            write.execute(getType.execute(receiver), receiver.getForeign(), offset, value, ForeignToLLVMType.POINTER);
        }
    }

    @ExportMessage
    boolean isNull(@CachedLibrary("this.foreign") InteropLibrary interop) {
        return interop.isNull(getForeign());
    }

    @ExportMessage
    boolean isPointer(@CachedLibrary("this.foreign") InteropLibrary interop) {
        return interop.isPointer(getForeign());
    }

    @ExportMessage
    long asPointer(@CachedLibrary("this.foreign") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asPointer(getForeign());
    }

    @ExportMessage
    void toNative(@CachedLibrary("this.foreign") InteropLibrary interop) {
        interop.toNative(getForeign());
    }

    @GenerateUncached
    abstract static class CompareForeignNode extends LLVMNode {

        protected abstract boolean execute(Object a, Object b);

        @Specialization(guards = {"ctx.getEnv().isHostObject(a)", "ctx.getEnv().isHostObject(b)"})
        static boolean doHostObjects(Object a, Object b,
                        @CachedContext(LLVMLanguage.class) LLVMContext ctx) {
            Env env = ctx.getEnv();
            return env.asHostObject(a) == env.asHostObject(b);
        }

        @Specialization(limit = "3", guards = "!ctx.getEnv().isHostObject(a) || !ctx.getEnv().isHostObject(b)")
        static boolean doOther(Object a, Object b,
                        @SuppressWarnings("unused") @CachedContext(LLVMLanguage.class) LLVMContext ctx,
                        @CachedLibrary("a") ReferenceLibrary lib) {
            return lib.isSame(a, b);
        }
    }

    @ExportMessage
    static class IsSame {

        @Specialization
        static boolean doTyped(LLVMTypedForeignObject receiver, LLVMTypedForeignObject other,
                        @Cached CompareForeignNode compare) {
            return compare.execute(receiver.getForeign(), other.foreign);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean doGeneric(LLVMTypedForeignObject receiver, Object other) {
            return false;
        }
    }
}
