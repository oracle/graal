/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToPointer.Dummy;
import com.oracle.truffle.llvm.runtime.interop.convert.ToPointer.InteropTypeNode;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@NodeChild(type = Dummy.class)
@NodeChild(type = InteropTypeNode.class)
@GenerateUncached
public abstract class ToPointer extends ForeignToLLVM {

    public static ToPointer create() {
        return ToPointerNodeGen.create(null, new InteropTypeNode(null));
    }

    public static ToPointer create(LLVMInteropType.Structured type) {
        return ToPointerNodeGen.create(null, new InteropTypeNode(type));
    }

    abstract static class Dummy extends LLVMNode {

        protected abstract Object execute();
    }

    static final class InteropTypeNode extends LLVMNode {

        private final LLVMInteropType.Structured type;

        private InteropTypeNode(LLVMInteropType.Structured type) {
            this.type = type;
        }

        public LLVMInteropType.Structured execute() {
            return type;
        }
    }

    @Specialization
    protected LLVMManagedPointer fromInt(int value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected LLVMManagedPointer fromChar(char value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected LLVMManagedPointer fromLong(long value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected LLVMManagedPointer fromByte(byte value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected LLVMManagedPointer fromShort(short value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected LLVMManagedPointer fromFloat(float value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected LLVMManagedPointer fromDouble(double value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected LLVMManagedPointer fromBoolean(boolean value, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(value);
    }

    @Specialization
    protected String fromString(String obj, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return obj;
    }

    @Specialization
    protected LLVMPointer fromPointer(LLVMPointer pointer, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return pointer;
    }

    @Specialization(guards = {"foreigns.isForeign(obj)", "nativeTypes.hasNativeType(obj) || typeFromMethodSignature == null"})
    @GenerateAOT.Exclude
    protected LLVMManagedPointer fromTypedTruffleObjectNoAttachedType(Object obj, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns) {
        return LLVMManagedPointer.create(obj);
    }

    @Specialization(guards = {"foreigns.isForeign(obj)", "!nativeTypes.hasNativeType(obj)", "typeFromMethodSignature != null"})
    @GenerateAOT.Exclude
    protected LLVMManagedPointer fromNonTypedTruffleObject(Object obj, LLVMInteropType.Structured typeFromMethodSignature,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns) {
        return LLVMManagedPointer.create(LLVMTypedForeignObject.create(obj, typeFromMethodSignature));
    }

    @Specialization
    protected LLVMManagedPointer fromInternal(LLVMInternalTruffleObject object, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return LLVMManagedPointer.create(object);
    }

    @Specialization
    protected LLVMInteropType fromInteropType(LLVMInteropType object, @SuppressWarnings("unused") LLVMInteropType.Structured typeFromMethodSignature) {
        return object;
    }

    @TruffleBoundary
    static Object slowPathPrimitiveConvert(Object value, LLVMInteropType.Structured typeFromMethodSignature) throws UnsupportedTypeException {
        if (value instanceof Number) {
            return LLVMManagedPointer.create(value);
        } else if (value instanceof Boolean) {
            return LLVMManagedPointer.create(value);
        } else if (value instanceof Character) {
            return LLVMManagedPointer.create(value);
        } else if (value instanceof String) {
            return value;
        } else if (LLVMPointer.isInstance(value)) {
            return value;
        } else if (value instanceof LLVMInternalTruffleObject) {
            return LLVMManagedPointer.create(value);
        } else if (LLVMAsForeignLibrary.getFactory().getUncached().isForeign(value)) {
            if (typeFromMethodSignature == null || NativeTypeLibrary.getFactory().getUncached().hasNativeType(value)) {
                return LLVMManagedPointer.create(value);
            } else {
                return LLVMManagedPointer.create(LLVMTypedForeignObject.create(value, typeFromMethodSignature));
            }
        } else if (value instanceof LLVMInteropType) {
            return value;
        } else {
            throw UnsupportedTypeException.create(new Object[]{value});
        }
    }
}
