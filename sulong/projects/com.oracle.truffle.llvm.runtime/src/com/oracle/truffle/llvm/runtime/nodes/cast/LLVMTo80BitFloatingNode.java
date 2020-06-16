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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMIVarBitLarge;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo80BitFloatingNodeGen.LLVMBitcastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo80BitFloatingNodeGen.LLVMSignedCastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo80BitFloatingNodeGen.LLVMUnsignedCastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMTo80BitFloatingNode extends LLVMExpressionNode {

    protected abstract LLVM80BitFloat executeWith(long value);

    protected LLVMTo80BitFloatingNode createRecursive() {
        throw new IllegalStateException("abstract node LLVMTo80BitFloatingNode used");
    }

    @Specialization
    protected LLVM80BitFloat doPointer(LLVMPointer from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                    @Cached("createRecursive()") LLVMTo80BitFloatingNode recursive) {
        long ptr = toNative.executeWithTarget(from).asNative();
        return recursive.executeWith(ptr);
    }

    public abstract static class LLVMSignedCastToLLVM80BitFloatNode extends LLVMTo80BitFloatingNode {

        @Override
        protected LLVMTo80BitFloatingNode createRecursive() {
            return LLVMSignedCastToLLVM80BitFloatNodeGen.create(null);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(boolean from) {
            if (from) {
                return LLVM80BitFloat.fromShort((short) 1);
            } else {
                return LLVM80BitFloat.fromShort((short) 0);
            }
        }

        @Specialization
        protected LLVM80BitFloat do80BitFloat(byte from) {
            return LLVM80BitFloat.fromByte(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(short from) {
            return LLVM80BitFloat.fromShort(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(int from) {
            return LLVM80BitFloat.fromInt(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(long from) {
            return LLVM80BitFloat.fromLong(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(float from) {
            return LLVM80BitFloat.fromFloat(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(double from) {
            return LLVM80BitFloat.fromDouble(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(LLVM80BitFloat from) {
            return from;
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(LLVMIVarBitLarge from) {
            return LLVM80BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMUnsignedCastToLLVM80BitFloatNode extends LLVMTo80BitFloatingNode {

        @Override
        protected LLVMTo80BitFloatingNode createRecursive() {
            return LLVMUnsignedCastToLLVM80BitFloatNodeGen.create(null);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(boolean from) {
            if (from) {
                return LLVM80BitFloat.fromUnsignedShort((short) 1);
            } else {
                return LLVM80BitFloat.fromUnsignedShort((short) 0);
            }
        }

        @Specialization
        protected LLVM80BitFloat do80BitFloat(byte from) {
            return LLVM80BitFloat.fromUnsignedByte(from);
        }

        @Specialization
        protected LLVM80BitFloat do80BitFloat(short from) {
            return LLVM80BitFloat.fromUnsignedShort(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(int from) {
            return LLVM80BitFloat.fromUnsignedInt(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(long from) {
            return LLVM80BitFloat.fromUnsignedLong(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(float from) {
            return LLVM80BitFloat.fromFloat(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(double from) {
            return LLVM80BitFloat.fromDouble(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(LLVM80BitFloat from) {
            return from;
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(LLVMIVarBitLarge from) {
            return LLVM80BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMBitcastToLLVM80BitFloatNode extends LLVMTo80BitFloatingNode {

        @Override
        protected LLVMTo80BitFloatingNode createRecursive() {
            return LLVMBitcastToLLVM80BitFloatNodeGen.create(null);
        }

        @Specialization
        protected LLVM80BitFloat doDouble(double from) {
            return LLVM80BitFloat.fromDouble(from);
        }

        @Specialization
        protected LLVM80BitFloat doLLVM80BitFloatNode(LLVM80BitFloat from) {
            return from;
        }

        @Specialization
        protected LLVM80BitFloat doIVarBit(LLVMIVarBitLarge from) {
            return LLVM80BitFloat.fromBytesBigEndian(from.getBytes());
        }

        @Specialization
        @ExplodeLoop
        protected LLVM80BitFloat doI1Vector(LLVMI1Vector from) {
            assert from.getLength() == LLVM80BitFloat.BIT_WIDTH : "invalid vector size";
            byte[] result = new byte[LLVM80BitFloat.BYTE_WIDTH];
            for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
                byte value = 0;
                for (int j = 0; j < Byte.SIZE; j++) {
                    value |= (from.getValue(i * Byte.SIZE + j) ? 1L : 0L) << j;
                }
                result[i] = value;
            }
            return LLVM80BitFloat.fromBytes(result);
        }

        @Specialization
        @ExplodeLoop
        protected LLVM80BitFloat doI8Vector(LLVMI8Vector from) {
            assert from.getLength() == LLVM80BitFloat.BIT_WIDTH / Byte.SIZE : "invalid vector size";
            byte[] values = new byte[LLVM80BitFloat.BYTE_WIDTH];
            for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
                values[i] = from.getValue(i);
            }
            return LLVM80BitFloat.fromBytes(values);
        }

        @Specialization
        @ExplodeLoop
        protected LLVM80BitFloat doI16Vector(LLVMI16Vector from) {
            assert from.getLength() == LLVM80BitFloat.BIT_WIDTH / Short.SIZE : "invalid vector size";
            byte[] values = new byte[LLVM80BitFloat.BYTE_WIDTH];
            for (int i = 0; i < LLVM80BitFloat.BIT_WIDTH / Short.SIZE; i++) {
                values[i * 2] = (byte) (from.getValue(i) & 0xFF);
                values[i * 2 + 1] = (byte) ((from.getValue(i) >>> 8) & 0xFF);
            }
            return LLVM80BitFloat.fromBytes(values);
        }
    }
}
