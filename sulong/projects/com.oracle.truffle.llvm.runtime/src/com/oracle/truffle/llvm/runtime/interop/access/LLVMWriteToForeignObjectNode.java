/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.access;

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNode.LLVMPointerDataEscapeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.ForeignDummy;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.GetForeignTypeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.OffsetDummy;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.ResolveNativePointerNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativePointerSupport;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "foreign", type = ForeignDummy.class, implicit = true)
@NodeChild(value = "offset", type = OffsetDummy.class, implicit = true)
@NodeChild(value = "value", type = ForeignDummy.class, implicit = true)
@NodeChild(value = "resolve", type = ResolveNativePointerNode.class, executeWith = {"foreign", "offset"}, implicit = true, allowUncached = true)
@NodeChild(value = "type", type = GetForeignTypeNode.class, executeWith = {"resolve"}, implicit = true, allowUncached = true)
public abstract class LLVMWriteToForeignObjectNode extends LLVMAccessForeignObjectNode {

    @GenerateUncached
    public abstract static class ForeignWriteI8Node extends LLVMWriteToForeignObjectNode {

        public abstract void execute(Object foreign, long offset, byte value);

        @Specialization
        void doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, byte value, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            getLanguage().getLLVMMemory().putI8(this, resolved, value);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        void doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, byte value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                interop.writeBufferByte(resolved.getObject(), resolved.getOffset(), value);
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        void doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, byte value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @Cached LLVMInteropWriteNode interopWrite) {
            interopWrite.execute(type, resolved.getObject(), resolved.getOffset(), value, ForeignToLLVMType.I8);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        void doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, byte value, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop) {
            // unknown type, assume to "array of primitives"
            try {
                interop.writeArrayElement(resolved.getObject(), resolved.getOffset() / Byte.BYTES, value);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element.");
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignWriteI16Node extends LLVMWriteToForeignObjectNode {

        public abstract void execute(Object foreign, long offset, short value);

        @Specialization
        void doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, short value, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            getLanguage().getLLVMMemory().putI16(this, resolved, value);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        void doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, short value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                interop.writeBufferShort(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset(), value);
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        void doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, short value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @Cached LLVMInteropWriteNode interopWrite) {
            interopWrite.execute(type, resolved.getObject(), resolved.getOffset(), value, ForeignToLLVMType.I16);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        void doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, short value, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop) {
            // unknown type, assume to "array of primitives"
            try {
                interop.writeArrayElement(resolved.getObject(), resolved.getOffset() / Short.BYTES, value);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element.");
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignWriteI32Node extends LLVMWriteToForeignObjectNode {

        public abstract void execute(Object foreign, long offset, int value);

        @Specialization
        void doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, int value, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            getLanguage().getLLVMMemory().putI32(this, resolved, value);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        void doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, int value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                interop.writeBufferInt(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset(), value);
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        void doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, int value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @Cached LLVMInteropWriteNode interopWrite) {
            interopWrite.execute(type, resolved.getObject(), resolved.getOffset(), value, ForeignToLLVMType.I32);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        void doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, int value, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop) {
            // unknown type, assume to "array of primitives"
            try {
                interop.writeArrayElement(resolved.getObject(), resolved.getOffset() / Integer.BYTES, value);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element.");
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignWriteFloatNode extends LLVMWriteToForeignObjectNode {

        public abstract void execute(Object foreign, long offset, float value);

        @Specialization
        void doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, float value, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            getLanguage().getLLVMMemory().putFloat(this, resolved, value);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        void doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, float value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                interop.writeBufferFloat(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset(), value);
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        void doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, float value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @Cached LLVMInteropWriteNode interopWrite) {
            interopWrite.execute(type, resolved.getObject(), resolved.getOffset(), value, ForeignToLLVMType.FLOAT);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        void doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, float value, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop) {
            // unknown type, assume to "array of primitives"
            try {
                interop.writeArrayElement(resolved.getObject(), resolved.getOffset() / Float.BYTES, value);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element.");
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignWriteDoubleNode extends LLVMWriteToForeignObjectNode {

        public abstract void execute(Object foreign, long offset, double value);

        @Specialization
        void doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, double value, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            getLanguage().getLLVMMemory().putDouble(this, resolved, value);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        void doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, double value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                interop.writeBufferDouble(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset(), value);
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        void doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, double value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @Cached LLVMInteropWriteNode interopWrite) {
            interopWrite.execute(type, resolved.getObject(), resolved.getOffset(), value, ForeignToLLVMType.DOUBLE);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        void doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, double value, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop) {
            // unknown type, assume to "array of primitives"
            try {
                interop.writeArrayElement(resolved.getObject(), resolved.getOffset() / Double.BYTES, value);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element.");
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignWriteI64Node extends LLVMWriteToForeignObjectNode {

        public abstract void execute(Object foreign, long offset, Object value);

        public abstract void executeLong(Object foreign, long offset, long value);

        public abstract void executePointer(Object foreign, long offset, LLVMPointer value);

        @Specialization
        void doNativeLong(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, long value, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            getLanguage().getLLVMMemory().putI64(this, resolved, value);
        }

        @Specialization
        void doNativePointer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, Object value, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type,
                        @Cached LLVMNativePointerSupport.ToNativePointerNode toNativePointer) {
            getLanguage().getLLVMMemory().putPointer(this, resolved, toNativePointer.execute(value));
        }

        @Specialization
        @GenerateAOT.Exclude
        void doBufferLong(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, long value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary(limit = "3") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                interop.writeBufferLong(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset(), value);
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        @GenerateAOT.Exclude
        void doBufferPointer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, Object value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary(limit = "3") InteropLibrary interop,
                        @Cached LLVMNativePointerSupport.ToNativePointerNode toNativePointer,
                        @Cached BranchProfile oobProfile) {
            LLVMNativePointer n = toNativePointer.execute(value);
            try {
                interop.writeBufferLong(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset(), n.asNative());
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        void doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, Object value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @Cached LLVMInteropWriteNode interopWrite) {
            interopWrite.execute(type, resolved.getObject(), resolved.getOffset(), value, ForeignToLLVMType.I64);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        void doFallbackLong(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, long value, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop) {
            // unknown type, assume "array of primitives"
            try {
                interop.writeArrayElement(resolved.getObject(), resolved.getOffset() / Long.BYTES, value);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element.");
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        void doFallbackPointer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMPointer value, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") Object type,
                        @Cached LLVMPointerDataEscapeNode dataEscape,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop) {
            // unknown type, assume "array of pointers"
            try {
                Object escapedValue = dataEscape.executeWithTarget(value);
                interop.writeArrayElement(resolved.getObject(), resolved.getOffset() / Long.BYTES, escapedValue);
            } catch (UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Wrong type writing to array element.");
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error writing to foreign array.");
            }
        }
    }
}
