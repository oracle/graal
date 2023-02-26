/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMAArch64_NeonNodes {
    public LLVMAArch64_NeonNodes() {
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAArch64_Ld1x2 extends LLVMBuiltin {
        final int vectorSize;

        public LLVMAArch64_Ld1x2(int vectorSize) {
            assert (vectorSize % I64_SIZE_IN_BYTES) == 0;
            this.vectorSize = vectorSize;
        }

        @Specialization
        protected LLVMPointer doOp(LLVMPointer retStackSpace, LLVMPointer srcAddress,
                        @Cached LLVMI64LoadNode.LLVMI64OffsetLoadNode loadNode,
                        @Cached LLVMI64StoreNode.LLVMI64OffsetStoreNode storeNode) {

            /* load two vectors from memory and store them into return struct */
            for (int i = 0; i < 2 * vectorSize; i += I64_SIZE_IN_BYTES) {
                Object value = loadNode.executeWithTargetGeneric(srcAddress, i);
                storeNode.executeWithTargetGeneric(retStackSpace, i, value);
            }
            return retStackSpace;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
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
        protected LLVMPointer doOp(LLVMPointer retStackSpace, LLVMPointer srcAddress,
                        @Cached LLVMI8LoadNode.LLVMI8OffsetLoadNode loadNodeV1,
                        @Cached LLVMI8LoadNode.LLVMI8OffsetLoadNode loadNodeV2,
                        @Cached LLVMI8StoreNode.LLVMI8OffsetStoreNode storeNodeV1,
                        @Cached LLVMI8StoreNode.LLVMI8OffsetStoreNode storeNodeV2) {

            if (elementSize != I8_SIZE_IN_BYTES) {
                CompilerDirectives.transferToInterpreter();
                throw CompilerDirectives.shouldNotReachHere("Not implemented yet. elementSize=" + elementSize);
            }

            long targetOffset = 0;
            /* Unpack two vectors from memory and store them into return struct */
            for (int i = 0; i < 2 * vectorSize; i += 2 * elementSize) {
                byte v1 = loadNodeV1.executeWithTarget(srcAddress, i);
                storeNodeV1.executeWithTarget(retStackSpace, targetOffset, v1);

                byte v2 = loadNodeV2.executeWithTarget(srcAddress, i + elementSize);
                storeNodeV2.executeWithTarget(retStackSpace, targetOffset + vectorSize, v2);

                targetOffset += I8_SIZE_IN_BYTES;
            }

            return retStackSpace;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
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

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
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

    @NodeChild(type = LLVMExpressionNode.class)
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

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
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
