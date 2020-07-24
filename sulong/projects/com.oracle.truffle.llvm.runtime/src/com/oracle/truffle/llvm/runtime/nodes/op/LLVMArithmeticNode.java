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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.runtime.nodes.op.arith.floating.LLVMArithmeticFactory;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.LLVMNegatedForeignObject;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMArithmetic.LLVMArithmeticOpNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMI64ArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMI64SubNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.ManagedAndNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.ManagedMulNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.ManagedSubNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.ManagedXorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.PointerToI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.util.LLVMSameObjectNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
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

        boolean canDoManaged(@SuppressWarnings("unused") long operand) {
            return false;
        }

        ManagedArithmeticNode createManagedNode() {
            return null;
        }
    }

    private abstract static class LLVMFPArithmeticOp extends LLVMArithmeticOp {

        abstract float doFloat(float left, float right);

        abstract double doDouble(double left, double right);

        abstract LLVMArithmeticOpNode createFP80Node();
    }

    abstract static class ManagedArithmeticNode extends LLVMNode {

        abstract Object execute(LLVMManagedPointer left, long right);

        abstract Object execute(long left, LLVMManagedPointer right);

        long executeLong(LLVMManagedPointer left, long right) throws UnexpectedResultException {
            return LLVMTypesGen.expectLong(execute(left, right));
        }

        long executeLong(long left, LLVMManagedPointer right) throws UnexpectedResultException {
            return LLVMTypesGen.expectLong(execute(left, right));
        }
    }

    abstract static class ManagedCommutativeArithmeticNode extends ManagedArithmeticNode {

        @Override
        final Object execute(long left, LLVMManagedPointer right) {
            return execute(right, left);
        }

        @Override
        final long executeLong(long left, LLVMManagedPointer right) throws UnexpectedResultException {
            return executeLong(right, left);
        }
    }

    final LLVMArithmeticOp op;

    protected boolean canDoManaged(long operand) {
        return op.canDoManaged(operand);
    }

    protected LLVMArithmeticNode(ArithmeticOperation op) {
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

    @NodeChild
    public abstract static class PointerToI64Node extends LLVMExpressionNode {

        @Specialization
        long doLong(long l) {
            return l;
        }

        @Specialization(limit = "3", guards = "lib.isPointer(ptr)", rewriteOn = UnsupportedMessageException.class)
        long doPointer(Object ptr,
                        @CachedLibrary("ptr") LLVMNativeLibrary lib) throws UnsupportedMessageException {
            return lib.asPointer(ptr);
        }

        @Specialization(limit = "3", guards = "!lib.isPointer(ptr)")
        Object doManaged(Object ptr,
                        @SuppressWarnings("unused") @CachedLibrary("ptr") LLVMNativeLibrary lib) {
            return ptr;
        }

        @Specialization(limit = "5", replaces = {"doLong", "doPointer", "doManaged"})
        Object doGeneric(Object ptr,
                        @CachedLibrary("ptr") LLVMNativeLibrary lib) {
            if (lib.isPointer(ptr)) {
                try {
                    return lib.asPointer(ptr);
                } catch (UnsupportedMessageException ex) {
                    // ignore
                }
            }
            return ptr;
        }
    }

    /**
     * We try to preserve pointers as good as possible because pointers to foreign objects can
     * usually not be converted to i64. Even if the foreign object implements the pointer messages,
     * the conversion is usually one-way.
     */
    public abstract static class LLVMAbstractI64ArithmeticNode extends LLVMArithmeticNode {

        public abstract long executeLongWithTarget(long left, long right);

        public static LLVMAbstractI64ArithmeticNode create(ArithmeticOperation op, LLVMExpressionNode left, LLVMExpressionNode right) {
            if (op == ArithmeticOperation.SUB) {
                return LLVMI64SubNodeGen.create(left, right);
            } else {
                return LLVMI64ArithmeticNodeGen.create(op, left, right);
            }
        }

        protected LLVMAbstractI64ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @CreateCast({"leftNode", "rightNode"})
        PointerToI64Node createCast(LLVMExpressionNode child) {
            return PointerToI64NodeGen.create(child);
        }

        @Specialization
        protected long doLong(long left, long right) {
            return op.doLong(left, right);
        }

        ManagedArithmeticNode createManagedNode() {
            return op.createManagedNode();
        }

        @Specialization(guards = "canDoManaged(right)", rewriteOn = UnexpectedResultException.class)
        long doManagedLeftLong(LLVMManagedPointer left, long right,
                        @Cached("createManagedNode()") ManagedArithmeticNode node) throws UnexpectedResultException {
            return node.executeLong(left, right);
        }

        @Specialization(guards = "canDoManaged(right)", replaces = "doManagedLeftLong")
        Object doManagedLeft(LLVMManagedPointer left, long right,
                        @Cached("createManagedNode()") ManagedArithmeticNode node) {
            return node.execute(left, right);
        }

        @Specialization(guards = "canDoManaged(left)", rewriteOn = UnexpectedResultException.class)
        long doManagedRightLong(long left, LLVMManagedPointer right,
                        @Cached("createManagedNode()") ManagedArithmeticNode node) throws UnexpectedResultException {
            return node.executeLong(left, right);
        }

        @Specialization(guards = "canDoManaged(left)", replaces = "doManagedRightLong")
        Object doManagedRight(long left, LLVMManagedPointer right,
                        @Cached("createManagedNode()") ManagedArithmeticNode node) {
            return node.execute(left, right);
        }

        @Specialization(limit = "3", guards = "!canDoManaged(left)")
        long doPointerRight(long left, LLVMPointer right,
                        @CachedLibrary("right") LLVMNativeLibrary rightLib) {
            return op.doLong(left, rightLib.toNativePointer(right).asNative());
        }

        @Specialization(limit = "3", guards = "!canDoManaged(right)")
        long doPointerLeft(LLVMPointer left, long right,
                        @CachedLibrary("left") LLVMNativeLibrary leftLib) {
            return op.doLong(leftLib.toNativePointer(left).asNative(), right);
        }
    }

    abstract static class LLVMI64ArithmeticNode extends LLVMAbstractI64ArithmeticNode {

        protected LLVMI64ArithmeticNode(ArithmeticOperation op) {
            super(op);
        }

        @Specialization(limit = "3")
        long doPointer(LLVMPointer left, LLVMPointer right,
                        @CachedLibrary("left") LLVMNativeLibrary leftLib,
                        @CachedLibrary("right") LLVMNativeLibrary rightLib) {
            return op.doLong(leftLib.toNativePointer(left).asNative(), rightLib.toNativePointer(right).asNative());
        }
    }

    abstract static class LLVMI64SubNode extends LLVMAbstractI64ArithmeticNode {

        protected LLVMI64SubNode() {
            super(ArithmeticOperation.SUB);
        }

        @Specialization(guards = "sameObject.execute(left.getObject(), right.getObject())")
        long doSameObjectLong(LLVMManagedPointer left, LLVMManagedPointer right,
                        @SuppressWarnings("unused") @Cached LLVMSameObjectNode sameObject) {
            return left.getOffset() - right.getOffset();
        }

        @Specialization(limit = "3", guards = "!sameObject.execute(left.getObject(), right.getObject())")
        long doNotSameObject(LLVMManagedPointer left, LLVMManagedPointer right,
                        @SuppressWarnings("unused") @Cached LLVMSameObjectNode sameObject,
                        @CachedLibrary("left") LLVMNativeLibrary leftLib,
                        @CachedLibrary("right") LLVMNativeLibrary rightLib) {
            return leftLib.toNativePointer(left).asNative() - rightLib.toNativePointer(right).asNative();
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

    static final class ManagedAddNode extends ManagedCommutativeArithmeticNode {

        @Override
        Object execute(LLVMManagedPointer left, long right) {
            return left.increment(right);
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
        boolean canDoManaged(long op) {
            return true;
        }

        @Override
        ManagedArithmeticNode createManagedNode() {
            return new ManagedAddNode();
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

    abstract static class ManagedMulNode extends ManagedCommutativeArithmeticNode {

        @Specialization(guards = "right == 1")
        LLVMManagedPointer doIdentity(LLVMManagedPointer left, @SuppressWarnings("unused") long right) {
            return left;
        }

        @Specialization(guards = "right == 0")
        @SuppressWarnings("unused")
        long doZero(LLVMManagedPointer left, long right) {
            return 0;
        }

        static boolean isMinusOne(long v) {
            return v == -1L;
        }

        @Specialization(guards = "isMinusOne(right)")
        LLVMManagedPointer doNegate(LLVMManagedPointer left, @SuppressWarnings("unused") long right) {
            Object negated = LLVMNegatedForeignObject.negate(left.getObject());
            return LLVMManagedPointer.create(negated, -left.getOffset());
        }
    }

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
        boolean canDoManaged(long op) {
            return op == 1 || op == -1 || op == 0;
        }

        @Override
        ManagedArithmeticNode createManagedNode() {
            return ManagedMulNodeGen.create();
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

    abstract static class ManagedSubNode extends ManagedArithmeticNode {

        @Specialization
        LLVMManagedPointer doLeft(LLVMManagedPointer left, long right) {
            return left.increment(-right);
        }

        @Specialization
        LLVMManagedPointer doRight(long left, LLVMManagedPointer right,
                        @Cached("createClassProfile()") ValueProfile type) {
            // type profile to be able to do fast-path negate-negate
            Object foreign = type.profile(right.getObject());
            Object negated = LLVMNegatedForeignObject.negate(foreign);
            return LLVMManagedPointer.create(negated, left - right.getOffset());
        }
    }

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
        boolean canDoManaged(long op) {
            return true;
        }

        @Override
        ManagedArithmeticNode createManagedNode() {
            return ManagedSubNodeGen.create();
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

    abstract static class ManagedAndNode extends ManagedCommutativeArithmeticNode {

        /*
         * For doing certain pointer arithmetics on managed pointers, we assume that a pointer
         * consists of n offset and m pointer identity bits. As long as the pointer offset bits are
         * manipulated, the pointer still points to the same managed object. If the pointer identity
         * bits are changed, we assume that the pointer will point to a different object, i.e., that
         * the pointer is destroyed.
         */
        private static final int POINTER_OFFSET_BITS = 32;

        static boolean highBitsSet(long op) {
            // if the high bits are all set, this AND is used for alignment
            long highBits = op >> POINTER_OFFSET_BITS;
            return highBits == -1L;
        }

        @Specialization(guards = "highBitsSet(right)")
        LLVMManagedPointer doAlign(LLVMManagedPointer left, long right) {
            return LLVMManagedPointer.create(left.getObject(), left.getOffset() & right);
        }

        static boolean highBitsClear(long op) {
            // if the high bits are all clear, this AND drops the base of the pointer
            long highBits = op >> POINTER_OFFSET_BITS;
            return highBits == 0L;
        }

        @Specialization(guards = "highBitsClear(right)")
        long doMask(LLVMManagedPointer left, long right) {
            return left.getOffset() & right;
        }
    }

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
        boolean canDoManaged(long op) {
            return ManagedAndNode.highBitsSet(op) || ManagedAndNode.highBitsClear(op);
        }

        @Override
        ManagedArithmeticNode createManagedNode() {
            return ManagedAndNodeGen.create();
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

    abstract static class ManagedXorNode extends ManagedCommutativeArithmeticNode {

        @Specialization
        LLVMManagedPointer doXor(LLVMManagedPointer left, long right,
                        @Cached("createClassProfile()") ValueProfile type) {
            // type profile to be able to do fast-path negate-negate
            Object foreign = type.profile(left.getObject());

            assert right == -1L;
            // -a is the two's complement, i.e. -a == (a ^ -1) + 1
            // therefore, (a ^ -1) == -a - 1
            Object negated = LLVMNegatedForeignObject.negate(foreign);
            return LLVMManagedPointer.create(negated, -left.getOffset() - 1);
        }
    }

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
        boolean canDoManaged(long op) {
            return op == -1L;
        }

        @Override
        ManagedArithmeticNode createManagedNode() {
            return ManagedXorNodeGen.create();
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
