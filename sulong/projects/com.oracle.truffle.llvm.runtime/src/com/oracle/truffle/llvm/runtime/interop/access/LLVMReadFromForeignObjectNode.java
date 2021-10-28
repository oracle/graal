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
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.ForeignDummy;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.GetForeignTypeNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.OffsetDummy;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMAccessForeignObjectNode.ResolveNativePointerNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToDouble;
import com.oracle.truffle.llvm.runtime.interop.convert.ToFloat;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI16;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI32;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI64;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI8;
import com.oracle.truffle.llvm.runtime.interop.convert.ToPointer;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "foreign", type = ForeignDummy.class, implicit = true)
@NodeChild(value = "offset", type = OffsetDummy.class, implicit = true)
@NodeChild(value = "resolve", type = ResolveNativePointerNode.class, executeWith = {"foreign", "offset"}, implicit = true, allowUncached = true)
@NodeChild(value = "type", type = GetForeignTypeNode.class, executeWith = {"resolve"}, implicit = true, allowUncached = true)
public abstract class LLVMReadFromForeignObjectNode extends LLVMAccessForeignObjectNode {

    @GenerateUncached
    public abstract static class ForeignReadI8Node extends LLVMReadFromForeignObjectNode {

        public abstract byte execute(Object foreign, long offset);

        @Specialization
        byte doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            return getLanguage().getLLVMMemory().getI8(this, resolved);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        byte doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                return interop.readBufferByte(resolved.getObject(), resolved.getOffset());
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        byte doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, LLVMInteropType.Structured type,
                        @Cached LLVMInteropReadNode interopRead) {
            Object ret = interopRead.execute(type, resolved.getObject(), resolved.getOffset(), ForeignToLLVMType.I8);
            return LLVMTypesGen.asByte(ret);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        byte doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached ToI8 toLLVM) {
            // unknown type, assume to "array of primitives"
            try {
                Object ret = interop.readArrayElement(resolved.getObject(), resolved.getOffset() / Byte.BYTES);
                return LLVMTypesGen.asByte(toLLVM.executeWithTarget(ret));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignReadI16Node extends LLVMReadFromForeignObjectNode {

        public abstract short execute(Object foreign, long offset);

        @Specialization
        short doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            return getLanguage().getLLVMMemory().getI16(this, resolved);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        short doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                return interop.readBufferShort(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset());
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        @GenerateAOT.Exclude
        short doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, LLVMInteropType.Structured type,
                        @Cached LLVMInteropReadNode interopRead) {
            Object ret = interopRead.execute(type, resolved.getObject(), resolved.getOffset(), ForeignToLLVMType.I16);
            return LLVMTypesGen.asShort(ret);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        short doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached ToI16 toLLVM) {
            // unknown type, assume to "array of primitives"
            try {
                Object ret = interop.readArrayElement(resolved.getObject(), resolved.getOffset() / Short.BYTES);
                return LLVMTypesGen.asShort(toLLVM.executeWithTarget(ret));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignReadI32Node extends LLVMReadFromForeignObjectNode {

        public abstract int execute(Object foreign, long offset);

        @Specialization
        int doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            return getLanguage().getLLVMMemory().getI32(this, resolved);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        int doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                return interop.readBufferInt(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset());
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        int doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, LLVMInteropType.Structured type,
                        @Cached LLVMInteropReadNode interopRead) {
            Object ret = interopRead.execute(type, resolved.getObject(), resolved.getOffset(), ForeignToLLVMType.I32);
            return LLVMTypesGen.asInteger(ret);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        int doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached ToI32 toLLVM) {
            // unknown type, assume to "array of primitives"
            try {
                Object ret = interop.readArrayElement(resolved.getObject(), resolved.getOffset() / Integer.BYTES);
                return LLVMTypesGen.asInteger(toLLVM.executeWithTarget(ret));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignReadFloatNode extends LLVMReadFromForeignObjectNode {

        public abstract float execute(Object foreign, long offset);

        @Specialization
        float doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            return getLanguage().getLLVMMemory().getFloat(this, resolved);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        float doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                return interop.readBufferFloat(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset());
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        float doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, LLVMInteropType.Structured type,
                        @Cached LLVMInteropReadNode interopRead) {
            Object ret = interopRead.execute(type, resolved.getObject(), resolved.getOffset(), ForeignToLLVMType.FLOAT);
            return LLVMTypesGen.asFloat(ret);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        float doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached ToFloat toLLVM) {
            // unknown type, assume to "array of primitives"
            try {
                Object ret = interop.readArrayElement(resolved.getObject(), resolved.getOffset() / Float.BYTES);
                return LLVMTypesGen.asFloat(toLLVM.executeWithTarget(ret));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignReadDoubleNode extends LLVMReadFromForeignObjectNode {

        public abstract double execute(Object foreign, long offset);

        @Specialization
        double doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            return getLanguage().getLLVMMemory().getDouble(this, resolved);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        double doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                return interop.readBufferDouble(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset());
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        double doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, LLVMInteropType.Structured type,
                        @Cached LLVMInteropReadNode interopRead) {
            Object ret = interopRead.execute(type, resolved.getObject(), resolved.getOffset(), ForeignToLLVMType.DOUBLE);
            return LLVMTypesGen.asDouble(ret);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        double doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached ToDouble toLLVM) {
            // unknown type, assume to "array of primitives"
            try {
                Object ret = interop.readArrayElement(resolved.getObject(), resolved.getOffset() / Double.BYTES);
                return LLVMTypesGen.asDouble(toLLVM.executeWithTarget(ret));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignReadI64Node extends LLVMReadFromForeignObjectNode {

        public abstract Object execute(Object foreign, long offset);

        public abstract long executeLong(Object foreign, long offset) throws UnexpectedResultException;

        @Specialization
        @GenerateAOT.Exclude
        long doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            return getLanguage().getLLVMMemory().getI64(this, resolved);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        long doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                return interop.readBufferLong(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset());
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        @GenerateAOT.Exclude
        Object doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, LLVMInteropType.Structured type,
                        @Cached LLVMInteropReadNode interopRead) {
            return interopRead.execute(type, resolved.getObject(), resolved.getOffset(), ForeignToLLVMType.I64);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        Object doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached ToI64 toLLVM) {
            // unknown type, assume to "array of primitives"
            try {
                Object ret = interop.readArrayElement(resolved.getObject(), resolved.getOffset() / Long.BYTES);
                return toLLVM.executeWithTarget(ret);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }

    @GenerateUncached
    public abstract static class ForeignReadPointerNode extends LLVMReadFromForeignObjectNode {

        public abstract LLVMPointer execute(Object foreign, long offset);

        @Specialization
        @GenerateAOT.Exclude
        LLVMNativePointer doNative(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMNativePointer resolved, @SuppressWarnings("unused") Object type) {
            return getLanguage().getLLVMMemory().getPointer(this, resolved);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        LLVMNativePointer doBuffer(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved,
                        @SuppressWarnings("unused") LLVMInteropType.Buffer type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached BranchProfile oobProfile) {
            try {
                long ret = interop.readBufferLong(resolved.getObject(), ByteOrder.nativeOrder(), resolved.getOffset());
                return LLVMNativePointer.create(ret);
            } catch (InvalidBufferOffsetException ex) {
                throw oob(oobProfile, ex);
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }

        @Specialization
        @GenerateAOT.Exclude
        LLVMPointer doStructured(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, LLVMInteropType.Structured type,
                        @Cached LLVMInteropReadNode interopRead) {
            Object ret = interopRead.execute(type, resolved.getObject(), resolved.getOffset(), ForeignToLLVMType.POINTER);
            return LLVMTypesGen.asPointer(ret);
        }

        @Specialization(limit = "3", guards = "type == null")
        @GenerateAOT.Exclude
        LLVMPointer doFallback(@SuppressWarnings("unused") Object foreign, @SuppressWarnings("unused") long offset, LLVMManagedPointer resolved, @SuppressWarnings("unused") Object type,
                        @CachedLibrary("resolved.getObject()") InteropLibrary interop,
                        @Cached ToPointer toLLVM) {
            // unknown type, assume to "array of pointers"
            try {
                Object ret = interop.readArrayElement(resolved.getObject(), resolved.getOffset() / Long.BYTES);
                return LLVMTypesGen.asPointer(toLLVM.executeWithType(ret, null));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new LLVMPolyglotException(this, "Error reading from foreign array.");
            }
        }
    }
}
