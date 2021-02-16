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
package com.oracle.truffle.llvm.runtime.nodes.op;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.UnaryOperation;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild("operandNode")
public abstract class LLVMUnaryNode extends LLVMExpressionNode {

    public abstract Object executeWithTarget(Object operand);

    private abstract static class LLVMUnaryOp {

        abstract boolean doBoolean(boolean operand);

        abstract byte doByte(byte operand);

        abstract short doShort(short operand);

        abstract int doInt(int operand);

        abstract long doLong(long operand);

        abstract LLVMIVarBit doVarBit(LLVMIVarBit operand);
    }

    private abstract static class LLVMFPUnaryOp extends LLVMUnaryOp {

        abstract float doFloat(float operand);

        abstract double doDouble(double operand);

        abstract LLVM80BitFloat doFP80(LLVM80BitFloat operand);
    }

    final LLVMUnaryOp op;

    protected LLVMUnaryNode(UnaryOperation op) {
        switch (op) {
            case NEG:
                this.op = NEG;
                break;
            default:
                throw new AssertionError(op.name());
        }
    }

    public abstract static class LLVMFloatingUnaryNode extends LLVMUnaryNode {

        LLVMFloatingUnaryNode(UnaryOperation op) {
            super(op);
            assert this.op instanceof LLVMFPUnaryOp;
        }

        LLVMFPUnaryOp fpOp() {
            return (LLVMFPUnaryOp) op;
        }
    }

    public abstract static class LLVMFloatUnaryNode extends LLVMFloatingUnaryNode {

        LLVMFloatUnaryNode(UnaryOperation op) {
            super(op);
        }

        @Specialization
        float doFloat(float operand) {
            return fpOp().doFloat(operand);
        }
    }

    public abstract static class LLVMDoubleUnaryNode extends LLVMFloatingUnaryNode {

        LLVMDoubleUnaryNode(UnaryOperation op) {
            super(op);
        }

        @Specialization
        double doDouble(double operand) {
            return fpOp().doDouble(operand);
        }
    }

    public abstract static class LLVMFP80UnaryNode extends LLVMFloatingUnaryNode {

        LLVMFP80UnaryNode(UnaryOperation op) {
            super(op);
        }

        @Specialization
        LLVM80BitFloat do80BitFloat(LLVM80BitFloat operand) {
            return fpOp().doFP80(operand);
        }
    }

    private static final LLVMFPUnaryOp NEG = new LLVMFPUnaryOp() {

        @Override
        float doFloat(float operand) {
            return -operand;
        }

        @Override
        double doDouble(double operand) {
            return -operand;
        }

        @Override
        LLVM80BitFloat doFP80(LLVM80BitFloat operand) {
            return operand.negate();
        }

        @Override
        boolean doBoolean(boolean operand) {
            return !operand;
        }

        @Override
        byte doByte(byte operand) {
            return (byte) -operand;
        }

        @Override
        short doShort(short operand) {
            return (short) -operand;
        }

        @Override
        int doInt(int operand) {
            return -operand;
        }

        @Override
        long doLong(long operand) {
            return -operand;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit operand) {
            return LLVMIVarBit.fromInt(operand.getBitSize(), 0).sub(operand);
        }
    };
}
