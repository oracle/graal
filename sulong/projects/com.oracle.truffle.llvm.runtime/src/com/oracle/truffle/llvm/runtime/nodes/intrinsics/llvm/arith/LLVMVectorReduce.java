/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public abstract class LLVMVectorReduce {

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceAddNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            byte result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result += value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            short result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result += value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            int result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result += value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result += value.getValue(i);
            }
            return result;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceMulNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            byte result = 1;
            for (int i = 0; i < getVectorLength(); i++) {
                result *= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            short result = 1;
            for (int i = 0; i < getVectorLength(); i++) {
                result *= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            int result = 1;
            for (int i = 0; i < getVectorLength(); i++) {
                result *= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long result = 1;
            for (int i = 0; i < getVectorLength(); i++) {
                result *= value.getValue(i);
            }
            return result;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceAndNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected boolean doVector(LLVMI1Vector value) {
            assert value.getLength() == getVectorLength();
            boolean result = true;
            for (int i = 0; i < getVectorLength(); i++) {
                result &= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            byte result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result &= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            short result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result &= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            int result = -1;
            for (int i = 0; i < getVectorLength(); i++) {
                result &= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long result = -1;
            for (int i = 0; i < getVectorLength(); i++) {
                result &= value.getValue(i);
            }
            return result;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceOrNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected boolean doVector(LLVMI1Vector value) {
            assert value.getLength() == getVectorLength();
            boolean result = false;
            for (int i = 0; i < getVectorLength(); i++) {
                result |= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            byte result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result |= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            short result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result |= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            int result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result |= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result |= value.getValue(i);
            }
            return result;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceXorNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected boolean doVector(LLVMI1Vector value) {
            assert value.getLength() == getVectorLength();
            boolean result = false;
            for (int i = 0; i < getVectorLength(); i++) {
                result ^= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            byte result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result ^= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            short result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result ^= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            int result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result ^= value.getValue(i);
            }
            return result;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long result = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                result ^= value.getValue(i);
            }
            return result;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceUnsignedMaxNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            int max = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                final int elem = (value.getValue(i) & LLVMExpressionNode.I8_MASK);
                if (elem > max) {
                    max = elem;
                }
            }
            return (byte) max;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            int max = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                final int elem = (value.getValue(i) & LLVMExpressionNode.I16_MASK);
                if (elem > max) {
                    max = elem;
                }
            }
            return (short) max;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            long max = 0;
            for (int i = 0; i < getVectorLength(); i++) {
                final long elem = (value.getValue(i) & LLVMExpressionNode.I32_MASK);
                if (elem > max) {
                    max = elem;
                }
            }
            return (int) max;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long min = Long.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final long elem = value.getValue(i);
                if (Long.compareUnsigned(elem, min) > 0) {
                    min = elem;
                }
            }
            return (int) min;
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceUnsignedMinNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final int elem = (value.getValue(i) & LLVMExpressionNode.I8_MASK);
                if (elem < min) {
                    min = elem;
                }
            }
            return (byte) min;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final int elem = (value.getValue(i) & LLVMExpressionNode.I16_MASK);
                if (elem < min) {
                    min = elem;
                }
            }
            return (short) min;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            long min = Long.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final long elem = (value.getValue(i) & LLVMExpressionNode.I32_MASK);
                if (elem < min) {
                    min = elem;
                }
            }
            return (int) min;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long min = Long.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final long elem = value.getValue(i);
                if (Long.compareUnsigned(elem, min) < 0) {
                    min = elem;
                }
            }
            return (int) min;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceSignedMaxNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            byte max = Byte.MIN_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final byte elem = value.getValue(i);
                if (elem > max) {
                    max = elem;
                }
            }
            return max;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            short max = Short.MIN_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final short elem = value.getValue(i);
                if (elem > max) {
                    max = elem;
                }
            }
            return max;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            int max = Integer.MIN_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final int elem = value.getValue(i);
                if (elem > max) {
                    max = elem;
                }
            }
            return max;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long max = Long.MIN_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final long elem = value.getValue(i);
                if (elem > max) {
                    max = elem;
                }
            }
            return max;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "vectorLength", type = int.class)
    public abstract static class LLVMVectorReduceSignedMinNode extends LLVMBuiltin {
        protected abstract int getVectorLength();

        @Specialization
        @ExplodeLoop
        protected byte doVector(LLVMI8Vector value) {
            assert value.getLength() == getVectorLength();
            byte min = Byte.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final byte elem = value.getValue(i);
                if (elem < min) {
                    min = elem;
                }
            }
            return min;
        }

        @Specialization
        @ExplodeLoop
        protected short doVector(LLVMI16Vector value) {
            assert value.getLength() == getVectorLength();
            short min = Short.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final short elem = value.getValue(i);
                if (elem < min) {
                    min = elem;
                }
            }
            return min;
        }

        @Specialization
        @ExplodeLoop
        protected int doVector(LLVMI32Vector value) {
            assert value.getLength() == getVectorLength();
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final int elem = value.getValue(i);
                if (elem < min) {
                    min = elem;
                }
            }
            return min;
        }

        @Specialization
        @ExplodeLoop
        protected long doVector(LLVMI64Vector value) {
            assert value.getLength() == getVectorLength();
            long min = Long.MAX_VALUE;
            for (int i = 0; i < getVectorLength(); i++) {
                final long elem = value.getValue(i);
                if (elem < min) {
                    min = elem;
                }
            }
            return min;
        }
    }

}
