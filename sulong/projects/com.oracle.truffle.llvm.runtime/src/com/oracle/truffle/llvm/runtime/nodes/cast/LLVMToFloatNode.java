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
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToFloatNodeGen.LLVMBitcastToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToFloatNodeGen.LLVMSignedCastToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToFloatNodeGen.LLVMUnsignedCastToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToFloatNode extends LLVMExpressionNode {

    protected abstract float executeWith(long value);

    protected LLVMToFloatNode createRecursive() {
        throw new IllegalStateException("abstract node LLVMToFloatNode used");
    }

    @Specialization(guards = {"isForeignNumber(from, foreigns, interop)"})
    protected float doManagedPointer(LLVMManagedPointer from,
                    @Cached("createForeignToLLVM()") ForeignToLLVM toLLVM,
                    @Cached("createRecursive()") LLVMToFloatNode recursive,
                    @CachedLibrary(limit = "1") LLVMAsForeignLibrary foreigns,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary interop) {
        long ptr = (long) toLLVM.executeWithTarget(foreigns.asForeign(from.getObject()));
        return recursive.executeWith(ptr);
    }

    @Specialization
    protected float doPointer(LLVMPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("createRecursive()") LLVMToFloatNode recursive) {
        long ptr = toNative.executeWithTarget(from).asNative();
        return recursive.executeWith(ptr);
    }

    protected ForeignToLLVM createForeignToLLVM() {
        return CommonNodeFactory.createForeignToLLVM(ForeignToLLVMType.FLOAT);
    }

    protected static boolean isForeignNumber(LLVMManagedPointer pointer, LLVMAsForeignLibrary foreigns, InteropLibrary interop) {
        return foreigns.isForeign(pointer) && interop.isNumber(pointer.getObject());
    }

    public abstract static class LLVMSignedCastToFloatNode extends LLVMToFloatNode {

        @Override
        protected LLVMToFloatNode createRecursive() {
            return LLVMSignedCastToFloatNodeGen.create(null);
        }

        @Specialization
        protected float doFloat(boolean from) {
            return from ? 1.0f : 0.0f;
        }

        @Specialization
        protected float doFloat(byte from) {
            return from;
        }

        @Specialization
        protected float doFloat(short from) {
            return from;
        }

        @Specialization
        protected float doFloat(int from) {
            return from;
        }

        @Specialization
        protected float doFloat(long from) {
            return from;
        }

        @Specialization
        protected float doFloat(float from) {
            return from;
        }

        @Specialization
        protected float doFloat(double from) {
            return (float) from;
        }

        @Specialization
        protected float doFloat(LLVM80BitFloat from) {
            return from.getFloatValue();
        }
    }

    public abstract static class LLVMUnsignedCastToFloatNode extends LLVMToFloatNode {

        private static final float LEADING_BIT = 0x1.0p63f;

        @Override
        protected LLVMToFloatNode createRecursive() {
            return LLVMUnsignedCastToFloatNodeGen.create(null);
        }

        @Specialization
        protected float doFloat(boolean from) {
            return from ? 1 : 0;
        }

        @Specialization
        protected float doFloat(byte from) {
            return from & LLVMExpressionNode.I8_MASK;
        }

        @Specialization
        protected float doFloat(short from) {
            return from & LLVMExpressionNode.I16_MASK;
        }

        @Specialization
        protected float doFloat(int from) {
            return from & LLVMExpressionNode.I32_MASK;
        }

        @Specialization
        protected float doFloat(long from) {
            return doI64(from);
        }

        public static float doI64(long from) {
            float val = from & Long.MAX_VALUE;
            if (from < 0) {
                val += LEADING_BIT;
            }
            return val;
        }

        @Specialization
        protected float doFloat(float from) {
            return from;
        }
    }

    public abstract static class LLVMBitcastToFloatNode extends LLVMToFloatNode {

        @Override
        protected LLVMToFloatNode createRecursive() {
            return LLVMBitcastToFloatNodeGen.create(null);
        }

        @Specialization
        protected float doFloat(int from) {
            return Float.intBitsToFloat(from);
        }

        @Specialization
        protected float doFloat(float from) {
            return from;
        }

        @Specialization
        protected float doI1Vector(LLVMI1Vector from) {
            int res = (int) LLVMBitcastToI64Node.castI1Vector(from, Integer.SIZE);
            return Float.intBitsToFloat(res);
        }

        @Specialization
        protected float doI8Vector(LLVMI8Vector from) {
            int res = (int) LLVMBitcastToI64Node.castI8Vector(from, Integer.SIZE / Byte.SIZE);
            return Float.intBitsToFloat(res);
        }

        @Specialization
        protected float doI16Vector(LLVMI16Vector from) {
            int res = (int) LLVMBitcastToI64Node.castI16Vector(from, Integer.SIZE / Short.SIZE);
            return Float.intBitsToFloat(res);
        }

        @Specialization
        protected float doI32Vector(LLVMI32Vector from) {
            assert from.getLength() == 1 : "invalid vector size";
            return Float.intBitsToFloat(from.getValue(0));
        }

        @Specialization
        protected float doFloatVector(LLVMFloatVector from) {
            assert from.getLength() == 1 : "invalid vector size";
            return from.getValue(0);
        }
    }
}
