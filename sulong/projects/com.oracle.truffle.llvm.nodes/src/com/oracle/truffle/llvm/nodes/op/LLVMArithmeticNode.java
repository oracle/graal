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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.nodes.op.arith.floating.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMArithmetic.LLVMArithmeticOpNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild("leftNode")
@NodeChild("rightNode")
public abstract class LLVMArithmeticNode extends LLVMExpressionNode {

    public abstract Object executeWithTarget(Object left, Object right);

    private abstract static class LLVMArithmeticOp {

        abstract boolean doBoolean(boolean left, boolean right);

        abstract byte doByte(byte left, byte right);

        abstract short doShort(short left, short right);

        abstract int doInt(int left, int right);

        abstract long doLong(long left, long right);

        abstract LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right);
    }

    private abstract static class LLVMFPArithmeticOp extends LLVMArithmeticOp {

        abstract float doFloat(float left, float right);

        abstract double doDouble(double left, double right);

        abstract LLVMArithmeticOpNode createFP80Node();
    }

    final LLVMArithmeticOp op;

    LLVMArithmeticNode(ArithmeticOperation op) {
        switch (op) {
            case ADD:
                this.op = ADD;
                break;
            case SUB:
                this.op = SUB;
                break;
            case MUL:
                this.op = MUL;
                break;
            case DIV:
                this.op = DIV;
                break;
            case UDIV:
                this.op = UDIV;
                break;
            case REM:
                this.op = REM;
                break;
            case UREM:
                this.op = UREM;
                break;
            case AND:
                this.op = AND;
                break;
            case OR:
                this.op = OR;
                break;
            case XOR:
                this.op = XOR;
                break;
            case SHL:
                this.op = SHL;
                break;
            case LSHR:
                this.op = LSHR;
                break;
            case ASHR:
                this.op = ASHR;
                break;
            default:
                throw new AssertionError(op.name());
        }
    }

    public abstract static class LLVMI1ArithmeticNode extends LLVMArithmeticNode {

        LLVMI1ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        boolean doBoolean(boolean left, boolean right) {
            return op.doBoolean(left, right);
        }
    }

    public abstract static class LLVMI8ArithmeticNode extends LLVMArithmeticNode {

        LLVMI8ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        byte doByte(byte left, byte right) {
            return op.doByte(left, right);
        }
    }

    public abstract static class LLVMI16ArithmeticNode extends LLVMArithmeticNode {

        LLVMI16ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        short doShort(short left, short right) {
            return op.doShort(left, right);
        }
    }

    public abstract static class LLVMI32ArithmeticNode extends LLVMArithmeticNode {

        LLVMI32ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        int doInt(int left, int right) {
            return op.doInt(left, right);
        }
    }

    public abstract static class LLVMI64ArithmeticNode extends LLVMArithmeticNode {

        LLVMI64ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        long doLong(long left, long right) {
            return op.doLong(left, right);
        }

        @Specialization(limit = "3")
        long doPointer(long left, LLVMPointer right,
                        @CachedLibrary("right") LLVMNativeLibrary rightLib) {
            return op.doLong(left, rightLib.toNativePointer(right).asNative());
        }

        @Specialization(limit = "3")
        long doPointer(LLVMPointer left, long right,
                        @CachedLibrary("left") LLVMNativeLibrary leftLib) {
            return op.doLong(leftLib.toNativePointer(left).asNative(), right);
        }

        @Specialization(limit = "3")
        long doPointer(LLVMPointer left, LLVMPointer right,
                        @CachedLibrary("left") LLVMNativeLibrary leftLib,
                        @CachedLibrary("right") LLVMNativeLibrary rightLib) {
            return op.doLong(leftLib.toNativePointer(left).asNative(), rightLib.toNativePointer(right).asNative());
        }
    }

    public abstract static class LLVMIVarBitArithmeticNode extends LLVMArithmeticNode {

        LLVMIVarBitArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return op.doVarBit(left, right);
        }
    }

    public abstract static class LLVMFloatingArithmeticNode extends LLVMArithmeticNode {

        LLVMFloatingArithmeticNode(ArithmeticOperation op) {
            super(op);
            assert this.op instanceof LLVMFPArithmeticOp;
        }

        LLVMFPArithmeticOp fpOp() {
            return (LLVMFPArithmeticOp) op;
        }
    }

    public abstract static class LLVMFloatArithmeticNode extends LLVMFloatingArithmeticNode {

        LLVMFloatArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        float doFloat(float left, float right) {
            return fpOp().doFloat(left, right);
        }
    }

    public abstract static class LLVMDoubleArithmeticNode extends LLVMFloatingArithmeticNode {

        LLVMDoubleArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization
        double doDouble(double left, double right) {
            return fpOp().doDouble(left, right);
        }
    }

    public abstract static class LLVMFP80ArithmeticNode extends LLVMFloatingArithmeticNode {

        LLVMFP80ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        LLVMArithmeticOpNode createFP80Node() {
            return fpOp().createFP80Node();
        }

        @Specialization
        LLVM80BitFloat do80BitFloat(LLVM80BitFloat left, LLVM80BitFloat right,
                        @Cached("createFP80Node()") LLVMArithmeticOpNode node) {
            return (LLVM80BitFloat) node.execute(left, right);
        }
    }

    private static final LLVMFPArithmeticOp ADD = new LLVMFPArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return left ^ right;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left + right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left + right);
        }

        @Override
        int doInt(int left, int right) {
            return left + right;
        }

        @Override
        long doLong(long left, long right) {
            return left + right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.add(right);
        }

        @Override
        float doFloat(float left, float right) {
            return left + right;
        }

        @Override
        double doDouble(double left, double right) {
            return left + right;
        }

        @Override
        LLVMArithmeticOpNode createFP80Node() {
            return LLVMArithmeticFactory.createAddNode();
        }
    };

    private static final LLVMFPArithmeticOp MUL = new LLVMFPArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return left & right;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left * right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left * right);
        }

        @Override
        int doInt(int left, int right) {
            return left * right;
        }

        @Override
        long doLong(long left, long right) {
            return left * right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.mul(right);
        }

        @Override
        float doFloat(float left, float right) {
            return left * right;
        }

        @Override
        double doDouble(double left, double right) {
            return left * right;
        }

        @Override
        LLVMArithmeticOpNode createFP80Node() {
            return LLVMArithmeticFactory.createMulNode();
        }
    };

    private static final LLVMFPArithmeticOp SUB = new LLVMFPArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return left ^ right;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left - right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left - right);
        }

        @Override
        int doInt(int left, int right) {
            return left - right;
        }

        @Override
        long doLong(long left, long right) {
            return left - right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.sub(right);
        }

        @Override
        float doFloat(float left, float right) {
            return left - right;
        }

        @Override
        double doDouble(double left, double right) {
            return left - right;
        }

        @Override
        LLVMArithmeticOpNode createFP80Node() {
            return LLVMArithmeticFactory.createSubNode();
        }
    };

    private static final LLVMFPArithmeticOp DIV = new LLVMFPArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return left;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left / right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left / right);
        }

        @Override
        int doInt(int left, int right) {
            return left / right;
        }

        @Override
        long doLong(long left, long right) {
            return left / right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.div(right);
        }

        @Override
        float doFloat(float left, float right) {
            return left / right;
        }

        @Override
        double doDouble(double left, double right) {
            return left / right;
        }

        @Override
        LLVMArithmeticOpNode createFP80Node() {
            return LLVMArithmeticFactory.createDivNode();
        }
    };

    private static final LLVMArithmeticOp UDIV = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return left;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (Byte.toUnsignedInt(left) / Byte.toUnsignedInt(right));
        }

        @Override
        short doShort(short left, short right) {
            return (short) (Short.toUnsignedInt(left) / Short.toUnsignedInt(right));
        }

        @Override
        int doInt(int left, int right) {
            return Integer.divideUnsigned(left, right);
        }

        @Override
        long doLong(long left, long right) {
            return Long.divideUnsigned(left, right);
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.unsignedDiv(right);
        }
    };

    private static final LLVMFPArithmeticOp REM = new LLVMFPArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return false;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left % right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left % right);
        }

        @Override
        int doInt(int left, int right) {
            return left % right;
        }

        @Override
        long doLong(long left, long right) {
            return left % right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.rem(right);
        }

        @Override
        float doFloat(float left, float right) {
            return left % right;
        }

        @Override
        double doDouble(double left, double right) {
            return left % right;
        }

        @Override
        LLVMArithmeticOpNode createFP80Node() {
            return LLVMArithmeticFactory.createRemNode();
        }
    };

    private static final LLVMArithmeticOp UREM = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            if (!right) {
                CompilerDirectives.transferToInterpreter();
                throw new ArithmeticException("Division by zero!");
            }
            return left;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (Byte.toUnsignedInt(left) % Byte.toUnsignedInt(right));
        }

        @Override
        short doShort(short left, short right) {
            return (short) (Short.toUnsignedInt(left) % Short.toUnsignedInt(right));
        }

        @Override
        int doInt(int left, int right) {
            return Integer.remainderUnsigned(left, right);
        }

        @Override
        long doLong(long left, long right) {
            return Long.remainderUnsigned(left, right);
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.unsignedRem(right);
        }
    };

    private static final LLVMArithmeticOp AND = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return left && right;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left & right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left & right);
        }

        @Override
        int doInt(int left, int right) {
            return left & right;
        }

        @Override
        long doLong(long left, long right) {
            return left & right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.and(right);
        }
    };

    private static final LLVMArithmeticOp OR = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return left || right;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left | right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left | right);
        }

        @Override
        int doInt(int left, int right) {
            return left | right;
        }

        @Override
        long doLong(long left, long right) {
            return left | right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.or(right);
        }
    };

    private static final LLVMArithmeticOp XOR = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return left ^ right;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left ^ right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left ^ right);
        }

        @Override
        int doInt(int left, int right) {
            return left ^ right;
        }

        @Override
        long doLong(long left, long right) {
            return left ^ right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.xor(right);
        }
    };

    private static final LLVMArithmeticOp SHL = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return right ? false : left;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left << right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left << right);
        }

        @Override
        int doInt(int left, int right) {
            return left << right;
        }

        @Override
        long doLong(long left, long right) {
            return left << right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.leftShift(right);
        }
    };

    private static final LLVMArithmeticOp LSHR = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return right ? false : left;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) ((left & LLVMExpressionNode.I8_MASK) >>> right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) ((left & LLVMExpressionNode.I16_MASK) >>> right);
        }

        @Override
        int doInt(int left, int right) {
            return left >>> right;
        }

        @Override
        long doLong(long left, long right) {
            return left >>> right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.logicalRightShift(right);
        }
    };

    private static final LLVMArithmeticOp ASHR = new LLVMArithmeticOp() {

        @Override
        boolean doBoolean(boolean left, boolean right) {
            return left;
        }

        @Override
        byte doByte(byte left, byte right) {
            return (byte) (left >> right);
        }

        @Override
        short doShort(short left, short right) {
            return (short) (left >> right);
        }

        @Override
        int doInt(int left, int right) {
            return left >> right;
        }

        @Override
        long doLong(long left, long right) {
            return left >> right;
        }

        @Override
        LLVMIVarBit doVarBit(LLVMIVarBit left, LLVMIVarBit right) {
            return left.arithmeticRightShift(right);
        }
    };
}
