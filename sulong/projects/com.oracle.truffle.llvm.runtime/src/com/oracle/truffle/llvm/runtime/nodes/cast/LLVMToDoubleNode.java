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
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToDoubleNodeGen.LLVMBitcastToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToDoubleNodeGen.LLVMSignedCastToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToDoubleNodeGen.LLVMUnsignedCastToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToDoubleNode extends LLVMExpressionNode {

    protected abstract double executeWith(long value);

    protected LLVMToDoubleNode createRecursive() {
        throw new IllegalStateException("abstract node LLVMToDoubleNode used");
    }

    @Specialization(guards = {"isForeignNumber(from, foreigns, interop)"})
    protected double doManagedPointer(LLVMManagedPointer from,
                    @Cached("createForeignToLLVM()") ForeignToLLVM toLLVM,
                    @Cached("createRecursive()") LLVMToDoubleNode recursive,
                    @CachedLibrary(limit = "1") LLVMAsForeignLibrary foreigns,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary interop) {
        long ptr = (long) toLLVM.executeWithTarget(foreigns.asForeign(from.getObject()));
        return recursive.executeWith(ptr);
    }

    @Specialization
    protected double doPointer(LLVMPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("createRecursive()") LLVMToDoubleNode recursive) {
        long ptr = toNative.executeWithTarget(from).asNative();
        return recursive.executeWith(ptr);
    }

    protected ForeignToLLVM createForeignToLLVM() {
        return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.DOUBLE);
    }

    protected static boolean isForeignNumber(LLVMManagedPointer pointer, LLVMAsForeignLibrary foreigns, InteropLibrary interop) {
        return foreigns.isForeign(pointer) && interop.isNumber(pointer.getObject());
    }

    public abstract static class LLVMSignedCastToDoubleNode extends LLVMToDoubleNode {

        @Override
        protected LLVMToDoubleNode createRecursive() {
            return LLVMSignedCastToDoubleNodeGen.create(null);
        }

        @Specialization
        protected double doDouble(boolean from) {
            return from ? 1.0 : 0.0;
        }

        @Specialization
        protected double doDouble(byte from) {
            return from;
        }

        @Specialization
        protected double doDouble(short from) {
            return from;
        }

        @Specialization
        protected double doDouble(int from) {
            return from;
        }

        @Specialization
        protected double doDouble(long from) {
            return from;
        }

        @Specialization
        protected double doDouble(float from) {
            return from;
        }

        @Specialization
        protected double doDouble(double from) {
            return from;
        }

        @Specialization
        protected double doDouble(LLVM80BitFloat from) {
            return from.getDoubleValue();
        }
    }

    public abstract static class LLVMUnsignedCastToDoubleNode extends LLVMToDoubleNode {

        private static final double LEADING_BIT = 0x1.0p63;

        @Override
        protected LLVMToDoubleNode createRecursive() {
            return LLVMUnsignedCastToDoubleNodeGen.create(null);
        }

        @Specialization
        protected double doDouble(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        protected double doDouble(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        protected double doDouble(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        protected double doDouble(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        protected double doDouble(long from) {
            return doI64(from);
        }

        public static double doI64(long from) {
            double val = from & Long.MAX_VALUE;
            if (from < 0) {
                val += LEADING_BIT;
            }
            return val;
        }

        @Specialization
        protected double doDouble(double from) {
            return from;
        }
    }

    public abstract static class LLVMBitcastToDoubleNode extends LLVMToDoubleNode {

        @Override
        protected LLVMToDoubleNode createRecursive() {
            return LLVMBitcastToDoubleNodeGen.create(null);
        }

        @Specialization
        protected double doDouble(long from) {
            return Double.longBitsToDouble(from);
        }

        @Specialization
        protected double doDouble(double from) {
            return from;
        }

        @Specialization
        protected double doFloat(float from) {
            return from;
        }

        @Specialization
        protected double doI1Vector(LLVMI1Vector from) {
            long raw = LLVMBitcastToI64Node.castI1Vector(from, Long.SIZE);
            return Double.longBitsToDouble(raw);
        }

        @Specialization
        protected double doI8Vector(LLVMI8Vector from) {
            long raw = LLVMBitcastToI64Node.castI8Vector(from, Long.SIZE / Byte.SIZE);
            return Double.longBitsToDouble(raw);
        }

        @Specialization
        protected double doI16Vector(LLVMI16Vector from) {
            long raw = LLVMBitcastToI64Node.castI16Vector(from, Long.SIZE / Short.SIZE);
            return Double.longBitsToDouble(raw);
        }

        @Specialization
        protected double doI32Vector(LLVMI32Vector from) {
            long raw = LLVMBitcastToI64Node.castI32Vector(from, Long.SIZE / Integer.SIZE);
            return Double.longBitsToDouble(raw);
        }

        @Specialization
        protected double doFloatVector(LLVMFloatVector from) {
            long raw = LLVMBitcastToI64Node.castFloatVector(from, Long.SIZE / Float.SIZE);
            return Double.longBitsToDouble(raw);
        }

        @Specialization
        protected double doI64Vector(LLVMI64Vector from) {
            assert from.getLength() == 1 : "invalid vector size";
            return Double.longBitsToDouble(from.getValue(0));
        }

        @Specialization
        protected double doDoubleVector(LLVMDoubleVector from) {
            assert from.getLength() == 1 : "invalid vector size";
            return from.getValue(0);
        }
    }
}
