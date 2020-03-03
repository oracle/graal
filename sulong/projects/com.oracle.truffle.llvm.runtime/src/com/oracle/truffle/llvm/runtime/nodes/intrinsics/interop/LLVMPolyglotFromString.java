/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromString.ReadBytesNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromStringNodeGen.PutCharNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromStringNodeGen.ReadBytesWithLengthNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromStringNodeGen.ReadZeroTerminatedBytesNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadCharsetNode.LLVMCharset;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "charset", type = LLVMReadCharsetNode.class)
@NodeChild(value = "rawString", type = ReadBytesNode.class, executeWith = "charset")
public abstract class LLVMPolyglotFromString extends LLVMIntrinsic {

    public static LLVMPolyglotFromString create(LLVMExpressionNode string, LLVMExpressionNode charset) {
        LLVMReadCharsetNode charsetNode = LLVMReadCharsetNodeGen.create(charset);
        ReadBytesNode rawString = ReadZeroTerminatedBytesNodeGen.create(null, string);
        return LLVMPolyglotFromStringNodeGen.create(charsetNode, rawString);
    }

    public static LLVMPolyglotFromString createN(LLVMExpressionNode string, LLVMExpressionNode n, LLVMExpressionNode charset) {
        LLVMReadCharsetNode charsetNode = LLVMReadCharsetNodeGen.create(charset);
        ReadBytesNode rawString = ReadBytesWithLengthNodeGen.create(null, string, n);
        return LLVMPolyglotFromStringNodeGen.create(charsetNode, rawString);
    }

    @Specialization
    LLVMManagedPointer doFromString(LLVMCharset charset, ByteBuffer rawString) {
        return LLVMManagedPointer.create(charset.decode(rawString));
    }

    abstract static class ReadBytesNode extends LLVMNode {

        protected abstract ByteBuffer execute(VirtualFrame frame, LLVMCharset charset);
    }

    @NodeChild(type = LLVMReadCharsetNode.class)
    @NodeChild(value = "string", type = LLVMExpressionNode.class)
    @NodeChild(value = "len", type = LLVMExpressionNode.class)
    abstract static class ReadBytesWithLengthNode extends ReadBytesNode {

        @Child private LLVMLoadNode load = LLVMI8LoadNode.create();

        @Specialization
        ByteBuffer doRead(@SuppressWarnings("unused") LLVMCharset charset, LLVMPointer string, long len) {
            ByteBuffer buffer = ByteBuffer.allocate((int) len);

            LLVMPointer ptr = string;
            for (int i = 0; i < len; i++) {
                byte value = (byte) load.executeWithTarget(ptr);
                ptr = ptr.increment(Byte.BYTES);
                buffer.put(value);
            }

            buffer.flip();
            return buffer;
        }
    }

    @NodeChild(type = LLVMReadCharsetNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    abstract static class ReadZeroTerminatedBytesNode extends ReadBytesNode {

        @CompilationFinal int bufferSize = 8;

        @Specialization(limit = "4", guards = "charset.zeroTerminatorLen == increment")
        ByteBuffer doRead(@SuppressWarnings("unused") LLVMCharset charset, LLVMPointer string,
                        @Cached("charset.zeroTerminatorLen") int increment,
                        @Cached("createLoad(increment)") LLVMLoadNode load,
                        @Cached("create()") PutCharNode put) {
            int currentBufferSize = bufferSize;
            ByteBuffer result = ByteBuffer.allocate(currentBufferSize).order(ByteOrder.nativeOrder());

            LLVMPointer ptr = string;
            Object value;
            do {
                value = load.executeWithTarget(ptr);
                ptr = ptr.increment(increment);

                if (result.remaining() < increment) {
                    // buffer overflow, allocate a bigger buffer
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    currentBufferSize *= 2;
                    ByteBuffer prev = result;
                    result = ByteBuffer.allocate(currentBufferSize).order(ByteOrder.nativeOrder());
                    prev.flip();
                    result.put(prev);
                }

            } while (put.execute(result, value));

            if (currentBufferSize > bufferSize) {
                // next time, start with a bigger buffer
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferSize = currentBufferSize;
            }

            result.flip();
            return result;
        }

        protected static LLVMLoadNode createLoad(int increment) {
            switch (increment) {
                case 1:
                    return LLVMI8LoadNode.create();
                case 2:
                    return LLVMI16LoadNode.create();
                case 4:
                    return LLVMI32LoadNode.create();
                case 8:
                    return LLVMI64LoadNode.create();
                default:
                    throw new AssertionError("should not reach here");
            }
        }
    }

    abstract static class PutCharNode extends LLVMNode {

        protected abstract boolean execute(ByteBuffer target, Object value);

        @Specialization
        boolean doByte(ByteBuffer target, byte value) {
            if (value == 0) {
                return false;
            } else {
                target.put(value);
                return true;
            }
        }

        @Specialization
        boolean doShort(ByteBuffer target, short value) {
            if (value == 0) {
                return false;
            } else {
                target.putShort(value);
                return true;
            }
        }

        @Specialization
        boolean doInt(ByteBuffer target, int value) {
            if (value == 0) {
                return false;
            } else {
                target.putInt(value);
                return true;
            }
        }

        @Specialization
        boolean doLong(ByteBuffer target, long value) {
            if (value == 0) {
                return false;
            } else {
                target.putLong(value);
                return true;
            }
        }

        public static PutCharNode create() {
            return PutCharNodeGen.create();
        }
    }
}
