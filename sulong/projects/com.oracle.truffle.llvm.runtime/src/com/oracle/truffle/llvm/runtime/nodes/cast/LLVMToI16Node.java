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
package com.oracle.truffle.llvm.runtime.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI64Node.LLVMBitcastToI64Node;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToI16Node extends LLVMExpressionNode {

    @Specialization(guards = {"isForeignNumber(from, foreigns, interop)"})
    protected short doManagedPointer(LLVMManagedPointer from,
                    @Cached("createForeignToLLVM()") ForeignToLLVM toLLVM,
                    @CachedLibrary(limit = "1") LLVMAsForeignLibrary foreigns,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary interop) {
        return (short) toLLVM.executeWithTarget(foreigns.asForeign(from.getObject()));
    }

    @Specialization
    protected short doPointer(LLVMPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        return (short) toNative.executeWithTarget(from).asNative();
    }

    protected ForeignToLLVM createForeignToLLVM() {
        return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.I16);
    }

    protected static boolean isForeignNumber(LLVMManagedPointer pointer, LLVMAsForeignLibrary foreigns, InteropLibrary interop) {
        return foreigns.isForeign(pointer) && interop.isNumber(pointer.getObject());
    }

    public abstract static class LLVMSignedCastToI16Node extends LLVMToI16Node {

        @Specialization
        protected short doI16(boolean from) {
            return (short) (from ? -1 : 0);
        }

        @Specialization
        protected short doI16(byte from) {
            return from;
        }

        @Specialization
        protected short doLLVMFunction(short from) {
            return from;
        }

        @Specialization
        protected short doI16(int from) {
            return (short) from;
        }

        @Specialization
        protected short doI16(long from) {
            return (short) from;
        }

        @Specialization
        protected short doI16(float from) {
            return (short) from;
        }

        @Specialization
        protected short doI16(double from) {
            return (short) from;
        }

        @Specialization
        protected short doLLVM80BitFloat(LLVM80BitFloat from) {
            return from.getShortValue();
        }

        @Specialization
        protected short doI16(LLVMIVarBit from) {
            return from.getShortValue();
        }
    }

    public abstract static class LLVMUnsignedCastToI16Node extends LLVMToI16Node {

        @Specialization
        protected short doI1(boolean from) {
            return (short) (from ? 1 : 0);
        }

        @Specialization
        protected short doI8(byte from) {
            return (short) (from & LLVMExpressionNode.I8_MASK);
        }

        @Specialization
        protected short doI16(short from) {
            return from;
        }

        @Specialization
        protected short doIVarBit(LLVMIVarBit from) {
            return from.getZeroExtendedShortValue();
        }

        @Specialization
        protected short doFloat(float from) {
            return (short) from;
        }

        @Specialization
        protected short doDouble(double from) {
            return (short) from;
        }

        @Specialization
        protected short doLLVM80BitFloat(LLVM80BitFloat from) {
            return from.getShortValue();
        }
    }

    public abstract static class LLVMBitcastToI16Node extends LLVMToI16Node {

        @Specialization
        protected short doI16(short from) {
            return from;
        }

        @Specialization
        protected short doI1Vector(LLVMI1Vector from) {
            return (short) LLVMBitcastToI64Node.castI1Vector(from, Short.SIZE);
        }

        @Specialization
        protected short doI8Vector(LLVMI8Vector from) {
            return (short) LLVMBitcastToI64Node.castI8Vector(from, Short.SIZE / Byte.SIZE);
        }

        @Specialization
        protected short doI16Vector(LLVMI16Vector from) {
            assert from.getLength() == 1 : "invalid vector size";
            return from.getValue(0);
        }
    }
}
