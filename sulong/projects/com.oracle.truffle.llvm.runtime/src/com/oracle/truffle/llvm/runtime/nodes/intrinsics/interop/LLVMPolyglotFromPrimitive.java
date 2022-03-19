/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromPrimitiveFactory.FromBooleanNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromPrimitiveFactory.FromI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromPrimitiveFactory.FromI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromPrimitiveFactory.FromI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromPrimitiveFactory.FromI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromPrimitiveFactory.FromFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromPrimitiveFactory.FromDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMPolyglotFromPrimitive extends LLVMIntrinsic {

    @Fallback
    Object onFallback(@SuppressWarnings("unused") Object receiver) {
        throw new LLVMPolyglotException(this, "Unexpected argument");
    }

    public abstract static class FromBoolean extends LLVMPolyglotFromPrimitive {
        @Specialization
        public Object asBoolean(boolean bool) {
            return LLVMManagedPointer.create(bool);
        }

        public static FromBoolean create(LLVMExpressionNode arg) {
            return FromBooleanNodeGen.create(arg);
        }
    }

    public abstract static class FromI8 extends LLVMPolyglotFromPrimitive {
        @Specialization
        public LLVMManagedPointer asByte(byte value) {
            return LLVMManagedPointer.create(value);
        }

        public static FromI8 create(LLVMExpressionNode arg) {
            return FromI8NodeGen.create(arg);
        }
    }

    public abstract static class FromI16 extends LLVMPolyglotFromPrimitive {
        @Specialization
        public LLVMManagedPointer asShort(short value) {
            return LLVMManagedPointer.create(value);
        }

        public static FromI16 create(LLVMExpressionNode arg) {
            return FromI16NodeGen.create(arg);
        }
    }

    public abstract static class FromI32 extends LLVMPolyglotFromPrimitive {
        @Specialization
        public LLVMManagedPointer asInt(int value) {
            return LLVMManagedPointer.create(value);
        }

        public static FromI32 create(LLVMExpressionNode arg) {
            return FromI32NodeGen.create(arg);
        }
    }

    public abstract static class FromI64 extends LLVMPolyglotFromPrimitive {
        @Specialization
        public LLVMManagedPointer asLong(long value) {
            return LLVMManagedPointer.create(value);
        }

        public static FromI64 create(LLVMExpressionNode arg) {
            return FromI64NodeGen.create(arg);
        }
    }

    public abstract static class FromFloat extends LLVMPolyglotFromPrimitive {
        @Specialization
        public LLVMManagedPointer asFloat(float value) {
            return LLVMManagedPointer.create(value);
        }

        public static FromFloat create(LLVMExpressionNode arg) {
            return FromFloatNodeGen.create(arg);
        }
    }

    public abstract static class FromDouble extends LLVMPolyglotFromPrimitive {
        @Specialization
        public LLVMManagedPointer asDouble(double value) {
            return LLVMManagedPointer.create(value);
        }

        public static FromDouble create(LLVMExpressionNode arg) {
            return FromDoubleNodeGen.create(arg);
        }
    }

}
