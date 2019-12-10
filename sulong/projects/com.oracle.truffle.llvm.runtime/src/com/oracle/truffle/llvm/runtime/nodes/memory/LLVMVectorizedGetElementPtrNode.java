/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMVectorizedGetElementPtrNode extends LLVMExpressionNode {
    final long typeWidth;
    final Type targetType;

    public LLVMVectorizedGetElementPtrNode(long typeWidth, Type targetType) {
        this.typeWidth = typeWidth;
        this.targetType = targetType;
    }

    @Specialization
    protected LLVMPointerVector doLong(LLVMPointerVector addrs, LLVMI64Vector vals) {
        assert addrs.getLength() == vals.getLength();
        LLVMPointer[] results = new LLVMPointer[addrs.getLength()];
        for (int i = 0; i < results.length; i++) {
            results[i] = addrs.getElement(i).increment(typeWidth * vals.getValue(i));
        }
        return LLVMPointerVector.create(results);
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class ResultVectorBroadcastNode extends LLVMExpressionNode {
        final int vectorLength;

        public ResultVectorBroadcastNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        LLVMPointerVector doPointer(LLVMPointer basePointer) {
            LLVMPointer[] pointers = new LLVMPointer[vectorLength];
            for (int i = 0; i < pointers.length; i++) {
                pointers[i] = basePointer;
            }
            return LLVMPointerVector.create(pointers);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class IndexVectorBroadcastNode extends LLVMExpressionNode {
        final int vectorLength;

        public IndexVectorBroadcastNode(int vectorLength) {
            this.vectorLength = vectorLength;
        }

        @Specialization
        LLVMI64Vector doInt(int index) {
            long[] indexes = new long[vectorLength];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = index;
            }
            return LLVMI64Vector.create(indexes);
        }

        @Specialization
        LLVMI64Vector doLong(long index) {
            long[] indexes = new long[vectorLength];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = index;
            }
            return LLVMI64Vector.create(indexes);
        }

        @Specialization
        LLVMI64Vector doShort(short index) {
            long[] indexes = new long[vectorLength];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = index;
            }
            return LLVMI64Vector.create(indexes);
        }

        @Specialization
        LLVMI64Vector doByte(byte index) {
            long[] indexes = new long[vectorLength];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = index;
            }
            return LLVMI64Vector.create(indexes);
        }

        @Specialization
        LLVMI64Vector doBoolean(boolean index) {
            long[] indexes = new long[vectorLength];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = index ? 1 : 0;
            }
            return LLVMI64Vector.create(indexes);
        }

        @Specialization
        LLVMI64Vector doPointer(LLVMPointer basePointer,
                        @Cached LLVMToNativeNode toNative) {
            long[] pointers = new long[vectorLength];
            long baseNativePointer = toNative.executeWithTarget(basePointer).asNative();
            for (int i = 0; i < pointers.length; i++) {
                pointers[i] = baseNativePointer;
            }
            return LLVMI64Vector.create(pointers);
        }
    }
}
