/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMArithmetic.LLVMArithmeticOpNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild("leftNode")
@NodeChild("rightNode")
public abstract class LLVMArithmeticNode extends LLVMExpressionNode {
    public abstract Object executeWithTarget(Object left, Object right);

    protected static ToComparableValue createToComparable() {
        CompilerAsserts.neverPartOfCompilation();
        return ToComparableValueNodeGen.create();
    }

    public abstract static class LLVMAddNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean add(boolean left, boolean right) {
            return left ^ right;
        }

        @Specialization
        protected byte add(byte left, byte right) {
            return (byte) (left + right);
        }

        @Specialization
        protected short add(short left, short right) {
            return (short) (left + right);
        }

        @Specialization
        protected int add(int left, int right) {
            return left + right;
        }

        @Specialization
        protected long add(long left, long right) {
            return left + right;
        }

        @Specialization
        protected LLVMIVarBit add(LLVMIVarBit left, LLVMIVarBit right) {
            return left.add(right);
        }

        @Specialization
        protected float add(float left, float right) {
            return left + right;
        }

        @Specialization
        protected double add(double left, double right) {
            return left + right;
        }

        protected LLVMArithmeticOpNode createFP80AddNode() {
            return LLVMArithmeticFactory.createAddNode();
        }

        @Specialization
        protected LLVM80BitFloat add(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80AddNode()") LLVMArithmeticOpNode node) {
            return (LLVM80BitFloat) node.execute(left, right);
        }
    }

    public abstract static class LLVMMulNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean mul(boolean left, boolean right) {
            return left & right;
        }

        @Specialization
        protected byte mul(byte left, byte right) {
            return (byte) (left * right);
        }

        @Specialization
        protected short mul(short left, short right) {
            return (short) (left * right);
        }

        @Specialization
        protected int mul(int left, int right) {
            return left * right;
        }

        @Specialization
        protected long mul(long left, long right) {
            return left * right;
        }

        @Specialization
        protected LLVMIVarBit mul(LLVMIVarBit left, LLVMIVarBit right) {
            return left.mul(right);
        }

        @Specialization
        protected float mul(float left, float right) {
            return left * right;
        }

        @Specialization
        protected double mul(double left, double right) {
            return left * right;
        }

        protected LLVMArithmeticOpNode createFP80MulNode() {
            return LLVMArithmeticFactory.createMulNode();
        }

        @Specialization
        protected LLVM80BitFloat mul(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80MulNode()") LLVMArithmeticOpNode node) {
            return (LLVM80BitFloat) node.execute(left, right);
        }
    }

    public abstract static class LLVMSubNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean sub(boolean left, boolean right) {
            return left ^ right;
        }

        @Specialization
        protected byte sub(byte left, byte right) {
            return (byte) (left - right);
        }

        @Specialization
        protected short sub(short left, short right) {
            return (short) (left - right);
        }

        @Specialization
        protected int sub(int left, int right) {
            return left - right;
        }

        @Specialization
        protected long sub(long left, long right) {
            return left - right;
        }

        @Specialization
        protected LLVMIVarBit sub(LLVMIVarBit left, LLVMIVarBit right) {
            return left.sub(right);
        }

        @Specialization
        protected float sub(float left, float right) {
            return left - right;
        }

        @Specialization
        protected double sub(double left, double right) {
            return left - right;
        }

        protected LLVMArithmeticOpNode createFP80SubNode() {
            return LLVMArithmeticFactory.createSubNode();
        }

        @Specialization
        protected LLVM80BitFloat sub(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80SubNode()") LLVMArithmeticOpNode node) {
            return (LLVM80BitFloat) node.execute(left, right);
        }
    }

    public abstract static class LLVMDivNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean div(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return left;
        }

        @Specialization
        protected byte div(byte left, byte right) {
            return (byte) (left / right);
        }

        @Specialization
        protected short div(short left, short right) {
            return (short) (left / right);
        }

        @Specialization
        protected int div(int left, int right) {
            return left / right;
        }

        @Specialization
        protected long div(long left, long right) {
            return left / right;
        }

        @Specialization
        protected LLVMIVarBit div(LLVMIVarBit left, LLVMIVarBit right) {
            return left.div(right);
        }

        @Specialization
        protected float div(float left, float right) {
            return left / right;
        }

        @Specialization
        protected double div(double left, double right) {
            return left / right;
        }

        protected LLVMArithmeticOpNode createFP80DivNode() {
            return LLVMArithmeticFactory.createDivNode();
        }

        @Specialization
        protected LLVM80BitFloat div(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80DivNode()") LLVMArithmeticOpNode node) {
            return (LLVM80BitFloat) node.execute(left, right);
        }
    }

    public abstract static class LLVMUDivNode extends LLVMArithmeticNode {

        @Specialization
        protected boolean udiv(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return left;
        }

        @Specialization
        protected byte udiv(byte left, byte right) {
            return (byte) (Byte.toUnsignedInt(left) / Byte.toUnsignedInt(right));
        }

        @Specialization
        protected short udiv(short left, short right) {
            return (short) (Short.toUnsignedInt(left) / Short.toUnsignedInt(right));
        }

        @Specialization
        protected int udiv(int left, int right) {
            return Integer.divideUnsigned(left, right);
        }

        @Specialization
        protected long udiv(long left, long right) {
            return Long.divideUnsigned(left, right);
        }

        @Specialization
        protected LLVMIVarBit udiv(LLVMIVarBit left, LLVMIVarBit right) {
            return left.unsignedDiv(right);
        }
    }

    public abstract static class LLVMRemNode extends LLVMArithmeticNode {
        @Specialization
        protected boolean rem(@SuppressWarnings("unused") boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return false;
        }

        @Specialization
        protected byte rem(byte left, byte right) {
            return (byte) (left % right);
        }

        @Specialization
        protected short rem(short left, short right) {
            return (short) (left % right);
        }

        @Specialization
        protected int rem(int left, int right) {
            return left % right;
        }

        @Specialization
        protected long rem(long left, long right) {
            return left % right;
        }

        @Specialization
        protected LLVMIVarBit rem(LLVMIVarBit left, LLVMIVarBit right) {
            return left.rem(right);
        }

        @Specialization
        protected float rem(float left, float right) {
            return left % right;
        }

        @Specialization
        protected double rem(double left, double right) {
            return left % right;
        }

        protected LLVMArithmeticOpNode createFP80RemNode() {
            return LLVMArithmeticFactory.createRemNode();
        }

        @Specialization
        protected LLVM80BitFloat rem(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80RemNode()") LLVMArithmeticOpNode node) {
            return (LLVM80BitFloat) node.execute(left, right);
        }
    }

    public abstract static class LLVMURemNode extends LLVMArithmeticNode {

        @Specialization
        protected byte urem(byte left, byte right) {
            return (byte) (Byte.toUnsignedInt(left) % Byte.toUnsignedInt(right));
        }

        @Specialization
        protected short urem(short left, short right) {
            return (short) (Short.toUnsignedInt(left) % Short.toUnsignedInt(right));
        }

        @Specialization
        protected boolean urem(@SuppressWarnings("unused") boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return false;
        }

        @Specialization
        protected int urem(int left, int right) {
            return Integer.remainderUnsigned(left, right);
        }

        @Specialization
        protected long urem(long left, long right) {
            return Long.remainderUnsigned(left, right);
        }

        @Specialization
        protected LLVMIVarBit urem(LLVMIVarBit left, LLVMIVarBit right) {
            return left.unsignedRem(right);
        }
    }

    public abstract static class LLVMAndNode extends LLVMArithmeticNode {

        @Specialization
        protected boolean and(boolean left, boolean right) {
            return left && right;
        }

        @Specialization
        protected byte and(byte left, byte right) {
            return (byte) (left & right);
        }

        @Specialization
        protected short and(short left, short right) {
            return (short) (left & right);
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
        protected LLVMIVarBit and(LLVMIVarBit left, LLVMIVarBit right) {
            return left.and(right);
        }
    }

    public abstract static class LLVMOrNode extends LLVMArithmeticNode {

        @Specialization
        protected boolean or(boolean left, boolean right) {
            return left || right;
        }

        @Specialization
        protected byte or(byte left, byte right) {
            return (byte) (left | right);
        }

        @Specialization
        protected short or(short left, short right) {
            return (short) (left | right);
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
        protected LLVMIVarBit or(LLVMIVarBit left, LLVMIVarBit right) {
            return left.or(right);
        }
    }

    public abstract static class LLVMXorNode extends LLVMArithmeticNode {
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
    }

    public abstract static class LLVMShlNode extends LLVMArithmeticNode {
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
    }

    public abstract static class LLVMLshrNode extends LLVMArithmeticNode {
        @Specialization
        protected short lshr(short left, short right) {
            return (short) ((left & LLVMExpressionNode.I16_MASK) >>> right);
        }

        @Specialization
        protected int lshr(int left, int right) {
            return left >>> right;
        }

        @Specialization
        protected long lshr(long left, long right) {
            return left >>> right;
        }

        @Specialization
        protected byte lshr(byte left, byte right) {
            return (byte) ((left & LLVMExpressionNode.I8_MASK) >>> right);
        }

        @Specialization
        protected LLVMIVarBit lshr(LLVMIVarBit left, LLVMIVarBit right) {
            return left.logicalRightShift(right);
        }
    }

    public abstract static class LLVMAshrNode extends LLVMArithmeticNode {
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
    }
}
