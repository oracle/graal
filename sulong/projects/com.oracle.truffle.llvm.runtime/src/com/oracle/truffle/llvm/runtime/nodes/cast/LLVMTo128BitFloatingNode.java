package com.oracle.truffle.llvm.runtime.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMIVarBitLarge;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMTo128BitFloatingNode extends LLVMExpressionNode {

    protected final boolean isRecursive;
    protected LLVMTo128BitFloatingNode() {
        this(false);
    }

    protected LLVMTo128BitFloatingNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    protected abstract LLVM128BitFloat executeWith(long value);

    protected LLVMTo128BitFloatingNode createRecursive() {
        throw new IllegalStateException("abstract node LLVMTo128BitFloatingNode used");
    }

    @Specialization(guards = "!isRecursive")
    protected LLVM128BitFloat doPointer(LLVMPointer from,
                                       @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative,
                                       @Cached("createRecursive()") LLVMTo128BitFloatingNode recursive) {
        long ptr = toNative.executeWithTarget(from).asNative();
        return recursive.executeWith(ptr);
    }


    public abstract static class LLVMSignedCastToLLVM128BitFloatNode extends LLVMTo128BitFloatingNode {

        protected LLVMSignedCastToLLVM128BitFloatNode() {
        }

        protected LLVMSignedCastToLLVM128BitFloatNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMTo128BitFloatingNode createRecursive() {
            return LLVMTo128BitFloatingNodeGen.LLVMSignedCastToLLVM128BitFloatNodeGen.create(true, null);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(long from) {
            return LLVM128BitFloat.fromLong(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(double from) {
            return LLVM128BitFloat.fromDouble(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(int from) {
            return LLVM128BitFloat.fromInt(from);
        }
        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(float from) {
            return LLVM128BitFloat.fromFloat(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVM128BitFloat from) {
            return from;
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVMIVarBitLarge from) {
            return LLVM128BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }

    @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMUnsignedCastToLLVM128BitFloatNode extends LLVMTo128BitFloatingNode {

        protected LLVMUnsignedCastToLLVM128BitFloatNode() {
        }

        protected LLVMUnsignedCastToLLVM128BitFloatNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMTo128BitFloatingNode createRecursive() {
            return LLVMTo128BitFloatingNodeGen.LLVMUnsignedCastToLLVM128BitFloatNodeGen.create(true, null);
        }
        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(float from) {
            return LLVM128BitFloat.fromFloat(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(double from) {
            return LLVM128BitFloat.fromDouble(from);
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVM128BitFloat from) {
            return from;
        }

        @Specialization
        protected LLVM128BitFloat doLLVM128BitFloatNode(LLVMIVarBitLarge from) {
            return LLVM128BitFloat.fromBytesBigEndian(from.getBytes());
        }
    }

   /* @NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
    public abstract static class LLVMBitcastToLLVM80BitFloatNode extends LLVMTo80BitFloatingNode {

        protected LLVMBitcastToLLVM80BitFloatNode() {
        }

        protected LLVMBitcastToLLVM80BitFloatNode(boolean isRecursive) {
            super(isRecursive);
        }

        @Override
        protected LLVMTo80BitFloatingNode createRecursive() {
            return LLVMTo80BitFloatingNodeGen.LLVMBitcastToLLVM80BitFloatNodeGen.create(true, null);
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
    */
}
