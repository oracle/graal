/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChildren({@NodeChild("leftNode"), @NodeChild("rightNode")})
public abstract class LLVMLogicNode extends LLVMExpressionNode {

    public abstract static class LLVMAndNode extends LLVMLogicNode {
        @Specialization
        protected short and(short left, short right) {
            return (short) (left & right);
        }

        @Specialization
        protected boolean and(boolean left, boolean right) {
            return left & right;
        }

        @Specialization
        protected int and(int left, int right) {
            return left & right;
        }

        @Specialization
        protected long and(long left, long right) {
            return left & right;
        }

        @Specialization
        protected byte and(byte left, byte right) {
            return (byte) (left & right);
        }

        @Specialization
        protected LLVMIVarBit and(LLVMIVarBit left, LLVMIVarBit right) {
            return left.and(right);
        }

        @Specialization
        protected LLVMI16Vector executeI16Vector(LLVMI16Vector left, LLVMI16Vector right) {
            return left.and(right);
        }

        @Specialization
        protected LLVMI1Vector executeI1Vector(LLVMI1Vector left, LLVMI1Vector right) {
            return left.and(right);
        }

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMI32Vector left, LLVMI32Vector right) {
            return left.and(right);
        }

        @Specialization
        protected LLVMI64Vector executeI64Vector(LLVMI64Vector left, LLVMI64Vector right) {
            return left.and(right);
        }

        @Specialization
        protected LLVMI8Vector executeI8Vector(LLVMI8Vector left, LLVMI8Vector right) {
            return left.and(right);
        }
    }

    public abstract static class LLVMOrNode extends LLVMLogicNode {
        @Specialization
        protected short or(short left, short right) {
            return (short) (left | right);
        }

        @Specialization
        protected boolean or(boolean left, boolean right) {
            return left | right;
        }

        @Specialization
        protected int or(int left, int right) {
            return left | right;
        }

        @Specialization
        protected long or(long left, long right) {
            return left | right;
        }

        @Specialization
        protected byte or(byte left, byte right) {
            return (byte) (left | right);
        }

        @Specialization
        protected LLVMIVarBit or(LLVMIVarBit left, LLVMIVarBit right) {
            return left.or(right);
        }

        @Specialization
        protected LLVMI16Vector executeI16Vector(LLVMI16Vector left, LLVMI16Vector right) {
            return left.or(right);
        }

        @Specialization
        protected LLVMI1Vector executeI1Vector(LLVMI1Vector left, LLVMI1Vector right) {
            return left.or(right);
        }

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMI32Vector left, LLVMI32Vector right) {
            return left.or(right);
        }

        @Specialization
        protected LLVMI64Vector executeI64Vector(LLVMI64Vector left, LLVMI64Vector right) {
            return left.or(right);
        }

        @Specialization
        protected LLVMI8Vector executeI8Vector(LLVMI8Vector left, LLVMI8Vector right) {
            return left.or(right);
        }
    }

    public abstract static class LLVMXorNode extends LLVMLogicNode {
        @Specialization
        protected short xor(short left, short right) {
            return (short) (left ^ right);
        }

        @Specialization
        protected boolean xor(boolean left, boolean right) {
            return left ^ right;
        }

        @Specialization
        protected int xor(int left, int right) {
            return left ^ right;
        }

        @Specialization
        protected long xor(long left, long right) {
            return left ^ right;
        }

        @Specialization
        protected byte xor(byte left, byte right) {
            return (byte) (left ^ right);
        }

        @Specialization
        protected LLVMIVarBit xor(LLVMIVarBit left, LLVMIVarBit right) {
            return left.xor(right);
        }

        @Specialization
        protected LLVMI16Vector xor(LLVMI16Vector left, LLVMI16Vector right) {
            return left.xor(right);
        }

        @Specialization
        protected LLVMI1Vector xor(LLVMI1Vector left, LLVMI1Vector right) {
            return left.xor(right);
        }

        @Specialization
        protected LLVMI32Vector xor(LLVMI32Vector left, LLVMI32Vector right) {
            return left.xor(right);
        }

        @Specialization
        protected LLVMI64Vector xor(LLVMI64Vector left, LLVMI64Vector right) {
            return left.xor(right);
        }

        @Specialization
        protected LLVMI8Vector executeI8Vector(LLVMI8Vector left, LLVMI8Vector right) {
            return left.xor(right);
        }
    }

    public abstract static class LLVMShlNode extends LLVMLogicNode {
        @Specialization
        protected short shl(short left, short right) {
            return (short) (left << right);
        }

        @Specialization
        protected int shl(int left, int right) {
            return left << right;
        }

        @Specialization
        protected long shl(long left, long right) {
            return left << right;
        }

        @Specialization
        protected byte shl(byte left, byte right) {
            return (byte) (left << right);
        }

        @Specialization
        protected LLVMIVarBit shl(LLVMIVarBit left, LLVMIVarBit right) {
            return left.leftShift(right);
        }

        @Specialization
        protected LLVMI16Vector executeI16Vector(LLVMI16Vector left, LLVMI16Vector right) {
            return left.leftShift(right);
        }

        @Specialization
        protected LLVMI1Vector executeI1Vector(LLVMI1Vector left, LLVMI1Vector right) {
            return left.leftShift(right);
        }

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMI32Vector left, LLVMI32Vector right) {
            return left.leftShift(right);
        }

        @Specialization
        protected LLVMI64Vector executeI64Vector(LLVMI64Vector left, LLVMI64Vector right) {
            return left.leftShift(right);
        }

        @Specialization
        protected LLVMI8Vector executeI8Vector(LLVMI8Vector left, LLVMI8Vector right) {
            return left.leftShift(right);
        }
    }

    public abstract static class LLVMLshrNode extends LLVMLogicNode {
        @Specialization
        protected short ashr(short left, short right) {
            return (short) ((left & LLVMExpressionNode.I16_MASK) >>> right);
        }

        @Specialization
        protected int ashr(int left, int right) {
            return left >>> right;
        }

        @Specialization
        protected long ashr(long left, long right) {
            return left >>> right;
        }

        @Specialization
        protected byte ashr(byte left, byte right) {
            return (byte) ((left & LLVMExpressionNode.I8_MASK) >>> right);
        }

        @Specialization
        protected LLVMIVarBit ashr(LLVMIVarBit left, LLVMIVarBit right) {
            return left.logicalRightShift(right);
        }

        @Specialization
        protected LLVMI16Vector executeI16Vector(LLVMI16Vector left, LLVMI16Vector right) {
            return left.logicalRightShift(right);
        }

        @Specialization
        protected LLVMI1Vector executeI1Vector(LLVMI1Vector left, LLVMI1Vector right) {
            return left.logicalRightShift(right);
        }

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMI32Vector left, LLVMI32Vector right) {
            return left.logicalRightShift(right);
        }

        @Specialization
        protected LLVMI64Vector executeI64Vector(LLVMI64Vector left, LLVMI64Vector right) {
            return left.logicalRightShift(right);
        }

        @Specialization
        protected LLVMI8Vector executeI8Vector(LLVMI8Vector left, LLVMI8Vector right) {
            return left.logicalRightShift(right);
        }
    }

    public abstract static class LLVMAshrNode extends LLVMLogicNode {
        @Specialization
        protected short ashr(short left, short right) {
            return (short) (left >> right);
        }

        @Specialization
        protected int ashr(int left, int right) {
            return left >> right;
        }

        @Specialization
        protected long ashr(long left, long right) {
            return left >> right;
        }

        @Specialization
        protected byte ashr(byte left, byte right) {
            return (byte) (left >> right);
        }

        @Specialization
        protected LLVMIVarBit ashr(LLVMIVarBit left, LLVMIVarBit right) {
            return left.arithmeticRightShift(right);
        }

        @Specialization
        protected LLVMI16Vector executeI16Vector(LLVMI16Vector left, LLVMI16Vector right) {
            return left.arithmeticRightShift(right);
        }

        @Specialization
        protected LLVMI1Vector executeI1Vector(LLVMI1Vector left, LLVMI1Vector right) {
            return left.arithmeticRightShift(right);
        }

        @Specialization
        protected LLVMI32Vector executeI32Vector(LLVMI32Vector left, LLVMI32Vector right) {
            return left.arithmeticRightShift(right);
        }

        @Specialization
        protected LLVMI64Vector executeI64Vector(LLVMI64Vector left, LLVMI64Vector right) {
            return left.arithmeticRightShift(right);
        }

        @Specialization
        protected LLVMI8Vector executeI8Vector(LLVMI8Vector left, LLVMI8Vector right) {
            return left.arithmeticRightShift(right);
        }
    }

}
