/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMDoubleDataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMFloatDataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMI16DataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMI1DataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMI32DataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMI64DataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMI8DataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMPointerDataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMDataEscapeNodeFactory.LLVMVoidDataEscapeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

/**
 * Values that escape Sulong and flow to other languages must be primitive or TruffleObject. This
 * node ensures that.
 */
public abstract class LLVMDataEscapeNode extends LLVMNode {

    public static LLVMDataEscapeNode create(Type type) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1DataEscapeNodeGen.create();
                case I8:
                    return LLVMI8DataEscapeNodeGen.create();
                case I16:
                    return LLVMI16DataEscapeNodeGen.create();
                case I32:
                    return LLVMI32DataEscapeNodeGen.create();
                case I64:
                    return LLVMI64DataEscapeNodeGen.create();
                case FLOAT:
                    return LLVMFloatDataEscapeNodeGen.create();
                case DOUBLE:
                    return LLVMDoubleDataEscapeNodeGen.create();
                default:
                    throw new AssertionError("unexpected type in LLVMDataEscapeNode: " + type);
            }
        } else if (type instanceof VoidType) {
            return LLVMVoidDataEscapeNodeGen.create();
        } else {
            assert type instanceof PointerType || type instanceof FunctionType : "unexpected type in LLVMDataEscapeNode: " + type;
            return LLVMPointerDataEscapeNodeGen.create();
        }
    }

    public static LLVMDataEscapeNode create(ForeignToLLVMType type) {
        switch (type) {
            case I1:
                return LLVMI1DataEscapeNodeGen.create();
            case I8:
                return LLVMI8DataEscapeNodeGen.create();
            case I16:
                return LLVMI16DataEscapeNodeGen.create();
            case I32:
                return LLVMI32DataEscapeNodeGen.create();
            case I64:
                return LLVMI64DataEscapeNodeGen.create();
            case FLOAT:
                return LLVMFloatDataEscapeNodeGen.create();
            case DOUBLE:
                return LLVMDoubleDataEscapeNodeGen.create();
            case POINTER:
                return LLVMPointerDataEscapeNodeGen.create();
            case VOID:
                return LLVMVoidDataEscapeNodeGen.create();
            default:
                throw new AssertionError("unexpected type in LLVMDataEscapeNode: " + type);
        }
    }

    public static LLVMDataEscapeNode getUncached(ForeignToLLVMType type) {
        switch (type) {
            case I1:
                return LLVMI1DataEscapeNodeGen.getUncached();
            case I8:
                return LLVMI8DataEscapeNodeGen.getUncached();
            case I16:
                return LLVMI16DataEscapeNodeGen.getUncached();
            case I32:
                return LLVMI32DataEscapeNodeGen.getUncached();
            case I64:
                return LLVMI64DataEscapeNodeGen.getUncached();
            case FLOAT:
                return LLVMFloatDataEscapeNodeGen.getUncached();
            case DOUBLE:
                return LLVMDoubleDataEscapeNodeGen.getUncached();
            case POINTER:
                return LLVMPointerDataEscapeNodeGen.getUncached();
            case VOID:
                return LLVMVoidDataEscapeNodeGen.getUncached();
            default:
                throw new AssertionError("unexpected type in LLVMDataEscapeNode: " + type);
        }
    }

    public final Object executeWithTarget(Object escapingValue) {
        return executeWithType(escapingValue, null);
    }

    public abstract Object executeWithType(Object escapingValue, LLVMInteropType.Structured type);

    @GenerateUncached
    public abstract static class LLVMI1DataEscapeNode extends LLVMDataEscapeNode {

        @Specialization
        static boolean escapingPrimitive(boolean escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }
    }

    @GenerateUncached
    public abstract static class LLVMI8DataEscapeNode extends LLVMDataEscapeNode {

        @Specialization
        static byte escapingPrimitive(byte escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }
    }

    @GenerateUncached
    public abstract static class LLVMI16DataEscapeNode extends LLVMDataEscapeNode {

        public final short executeWithTargetI16(Object escapingValue) {
            return executeWithTypeI16(escapingValue, null);
        }

        public abstract short executeWithTypeI16(Object escapingValue, LLVMInteropType.Structured type);

        @Specialization
        static short escapingPrimitive(short escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }
    }

    @GenerateUncached
    public abstract static class LLVMI32DataEscapeNode extends LLVMDataEscapeNode {

        public final int executeWithTargetI32(Object escapingValue) {
            return executeWithTypeI32(escapingValue, null);
        }

        public abstract int executeWithTypeI32(Object escapingValue, LLVMInteropType.Structured type);

        @Specialization
        static int escapingPrimitive(int escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }

        @Specialization
        static int escapingPrimitive(float escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return Float.floatToRawIntBits(escapingValue);
        }
    }

    @GenerateUncached
    public abstract static class LLVMI64DataEscapeNode extends LLVMDataEscapeNode {

        public final long executeWithTargetI64(Object escapingValue) {
            return executeWithTypeI64(escapingValue, null);
        }

        public abstract long executeWithTypeI64(Object escapingValue, LLVMInteropType.Structured type);

        @Specialization
        static long escapingPrimitive(long escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }

        @Specialization
        static long escapingPrimitive(double escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return Double.doubleToRawLongBits(escapingValue);
        }

        @Specialization
        static long escapingNativePointer(LLVMNativePointer escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue.asNative();
        }

        @Specialization
        static Object escapingPointer(LLVMManagedPointer escapingValue, LLVMInteropType.Structured type,
                        @Cached LLVMPointerDataEscapeNode pointerDataEscapeNode) {
            return pointerDataEscapeNode.executeWithType(escapingValue, type);
        }
    }

    @GenerateUncached
    public abstract static class LLVMFloatDataEscapeNode extends LLVMDataEscapeNode {

        @Specialization
        static float escapingPrimitive(float escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }

        @Specialization
        static float escapingPrimitive(int escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return Float.intBitsToFloat(escapingValue);
        }
    }

    @GenerateUncached
    public abstract static class LLVMDoubleDataEscapeNode extends LLVMDataEscapeNode {

        @Specialization
        static double escapingPrimitive(double escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }

        @Specialization
        static double escapingLong(long escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return Double.longBitsToDouble(escapingValue);
        }

        @Specialization(limit = "3", replaces = "escapingLong")
        static double escapingPointer(Object escapingValue, LLVMInteropType.Structured type,
                        @CachedLibrary("escapingValue") LLVMNativeLibrary library) {
            return escapingLong(library.toNativePointer(escapingValue).asNative(), type);
        }
    }

    @GenerateUncached
    public abstract static class LLVMPointerDataEscapeNode extends LLVMDataEscapeNode {

        @Specialization
        static String escapingString(String escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }

        @Specialization
        static TruffleObject escapingType(LLVMInteropType escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return escapingValue;
        }

        static boolean isPrimitiveValue(Object object) {
            return object instanceof Long || object instanceof Double;
        }

        @Specialization(guards = {"!isPrimitiveValue(object)", "foreigns.isForeign(object)"})
        static Object escapingForeignNonPointer(Object object, @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns) {
            return foreigns.asForeign(object);
        }

        @Specialization(guards = "!foreigns.isForeign(address)")
        static Object escapingManaged(LLVMPointer address, @SuppressWarnings("unused") LLVMInteropType.Structured type,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns,
                        @Cached ConditionProfile typedProfile) {
            // fallthrough: the value escapes as LLVM pointer object

            if (typedProfile.profile(address.getExportType() != null)) {
                // the pointer has manually attached type information -- use it
                return address;
            }

            // attach known type information
            return address.export(type);
        }

        @Specialization
        static LLVMPointer escapingPrimitive(long escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return LLVMNativePointer.create(escapingValue).export(type);
        }

        @Specialization
        static LLVMPointer escapingPrimitive(double escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            return LLVMNativePointer.create(Double.doubleToRawLongBits(escapingValue)).export(type);
        }
    }

    @GenerateUncached
    public abstract static class LLVMVoidDataEscapeNode extends LLVMDataEscapeNode {

        @Specialization
        public Object doVoid(LLVMPointer escapingValue, @SuppressWarnings("unused") LLVMInteropType.Structured type) {
            assert escapingValue.isNull();
            return escapingValue;
        }
    }
}
