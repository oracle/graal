package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMAArch64_NeonNodes {
    public LLVMAArch64_NeonNodes() {
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMAArch64_Ld1x2 extends LLVMBuiltin {
        final int vectorSize;

        public LLVMAArch64_Ld1x2(int vectorSize) {
            assert (vectorSize % I64_SIZE_IN_BYTES) == 0;
            this.vectorSize = vectorSize;
        }

        @Specialization
        protected LLVMPointer doOp(LLVMNativePointer retStackSpace, LLVMNativePointer address) {
            LLVMMemory memory = getLanguage().getLLVMMemory();

            long currentPtr = address.asNative();
            long targetPtr = retStackSpace.asNative();
            /* load two vectors from memory and store them into return struct */
            for (int i = 0; i < 2 * vectorSize; i += I64_SIZE_IN_BYTES) {
                memory.putI64(this, targetPtr, memory.getI64(this, currentPtr));
                currentPtr += I64_SIZE_IN_BYTES;
                targetPtr += I64_SIZE_IN_BYTES;
            }
            return retStackSpace;
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMAArch64_Ld2 extends LLVMBuiltin {
        final int vectorSize;
        final int elementSize;

        public LLVMAArch64_Ld2(int vectorSize, int elementSize) {
            assert (vectorSize % I64_SIZE_IN_BYTES) == 0;
            this.vectorSize = vectorSize;
            this.elementSize = elementSize;
        }

        @Specialization
        @ExplodeLoop
        protected LLVMPointer doOp(LLVMNativePointer retStackSpace, LLVMNativePointer address) {
            LLVMMemory memory = getLanguage().getLLVMMemory();

            long currentPtr = address.asNative();
            long targetPtr = retStackSpace.asNative();

            if (elementSize != I8_SIZE_IN_BYTES) {
                CompilerDirectives.transferToInterpreter();
                throw CompilerDirectives.shouldNotReachHere("Not implemented yet. elementSize=" + elementSize);
            }

            /* Unpack two vectors from memory and store them into return struct */
            for (int i = 0; i < 2 * vectorSize; i += 2 * elementSize) {
                byte v1 = memory.getI8(this, currentPtr + i);
                memory.putI8(this, targetPtr, v1);
                byte v2 = memory.getI8(this, currentPtr + i + elementSize);
                memory.putI8(this, targetPtr + vectorSize, v2);

                targetPtr += I8_SIZE_IN_BYTES;
            }

            return retStackSpace;
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMAArch64_Tbl1 extends LLVMBuiltin {
        final int vectorSize;

        public LLVMAArch64_Tbl1(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        protected LLVMI8Vector doOp(LLVMI8Vector source, LLVMI8Vector selector) {
            assert source.getLength() == selector.getLength();
            assert source.getLength() == vectorSize;

            byte[] result = new byte[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int index = selector.getValue(i);
                if (index < 0 || index >= vectorSize) {
                    result[i] = 0;
                } else {
                    result[i] = source.getValue(index);
                }
            }
            return LLVMI8Vector.create(result);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMAArch64_Tbl2 extends LLVMBuiltin {
        final int vectorSize;

        public LLVMAArch64_Tbl2(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        protected LLVMI8Vector doOp(LLVMI8Vector source1, LLVMI8Vector source2, LLVMI8Vector selector) {
            assert source1.getLength() == selector.getLength();
            assert source1.getLength() == source2.getLength();
            assert source1.getLength() == vectorSize;

            byte[] result = new byte[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                int index = selector.getValue(i);
                if (index < 0 || index >= 2 * vectorSize) {
                    result[i] = 0;
                } else if (index < vectorSize) {
                    result[i] = source1.getValue(index);
                } else {
                    result[i] = source2.getValue(index - vectorSize);
                }
            }
            return LLVMI8Vector.create(result);
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMAArch64_Umaxv extends LLVMBuiltin {
        final int vectorSize;

        public LLVMAArch64_Umaxv(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        protected int doOp(LLVMI8Vector v) {
            assert v.getLength() == vectorSize;

            int max = 0;
            for (int i = 0; i < vectorSize; i++) {
                int val = Byte.toUnsignedInt(v.getValue(i));
                max = Math.max(max, val);
            }
            return max;
        }
    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMAArch64_Uqsub extends LLVMBuiltin {
        final int vectorSize;

        public LLVMAArch64_Uqsub(int vectorSize) {
            this.vectorSize = vectorSize;
        }

        @Specialization
        protected LLVMI8Vector doOp(LLVMI8Vector v1, LLVMI8Vector v2) {
            assert v1.getLength() == v2.getLength();
            assert v1.getLength() == vectorSize;

            byte[] result = new byte[vectorSize];
            for (int i = 0; i < vectorSize; i++) {
                result[i] = clampUnsigned(Byte.toUnsignedInt(v1.getValue(i)) - Byte.toUnsignedInt(v2.getValue(i)));
            }
            return LLVMI8Vector.create(result);
        }

        private static byte clampUnsigned(int value) {
            if (value > 0xff) {
                return (byte) 0xff;
            } else if (value < 0) {
                return (byte) 0;
            }
            return (byte) value;
        }
    }
}
