/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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

import java.nio.ByteOrder;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromString.ReadBytesNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromStringNodeGen.ReadBytesWithLengthNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotFromStringNodeGen.ReadZeroTerminatedBytesNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadCharsetNode.LLVMCharset;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode.LLVMI16OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode.LLVMI8OffsetLoadNode;
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
    LLVMManagedPointer doFromString(LLVMCharset charset, byte[] rawString) {
        return LLVMManagedPointer.create(charset.decode(rawString));
    }

    abstract static class ReadBytesNode extends LLVMNode {
        protected abstract byte[] execute(VirtualFrame frame, LLVMCharset charset);
    }

    @NodeChild(type = LLVMReadCharsetNode.class)
    @NodeChild(value = "string", type = LLVMExpressionNode.class)
    @NodeChild(value = "len", type = LLVMExpressionNode.class)
    abstract static class ReadBytesWithLengthNode extends ReadBytesNode {

        @Child private LLVMI8OffsetLoadNode load = LLVMI8OffsetLoadNode.create();

        @Specialization
        byte[] doRead(@SuppressWarnings("unused") LLVMCharset charset, LLVMPointer string, long len) {
            byte[] buffer = new byte[(int) len];

            for (int i = 0; i < len; i++) {
                buffer[i] = load.executeWithTarget(string, i * Byte.BYTES);
            }

            return buffer;
        }
    }

    @NodeChild(type = LLVMReadCharsetNode.class)
    @NodeChild(type = LLVMExpressionNode.class)
    abstract static class ReadZeroTerminatedBytesNode extends ReadBytesNode {

        @CompilationFinal private int bufferSize = 8;
        @CompilationFinal private BranchProfile trimProfile = BranchProfile.create();

        private static ByteArraySupport nativeOrder() {
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                return ByteArraySupport.bigEndian();
            } else {
                return ByteArraySupport.littleEndian();
            }
        }

        private static byte[] ensureCapacity(byte[] result, int size, int elementSize) {
            if (result.length - size < elementSize) {
                // buffer overflow, allocate a bigger buffer
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return Arrays.copyOf(result, result.length * 2);
            }
            return result;
        }

        private byte[] trimResult(byte[] blockedResult, int size) {
            byte[] result = blockedResult;

            if (result.length > size) {
                trimProfile.enter();
                // shrink the space we had allocated to the exact size
                result = Arrays.copyOf(result, size);
            }

            if (result.length > bufferSize) {
                // next time, start with a bigger buffer
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferSize = result.length;
                trimProfile = BranchProfile.create();
            }
            return result;
        }

        @Specialization(guards = "charset.zeroTerminatorLen == 1")
        byte[] doReadI8(@SuppressWarnings("unused") LLVMCharset charset, LLVMPointer string,
                        @Cached LLVMI8OffsetLoadNode load) {
            ByteArraySupport byteArraySupport = nativeOrder();
            byte[] result = new byte[bufferSize];
            int size = 0;
            while (true) {
                byte value = load.executeWithTarget(string, size);
                /*
                 * It's important to check the size before "ensureCapacity", otherwise we might
                 * encounter repeated deopts.
                 */
                if (value == 0) {
                    break;
                }
                result = ensureCapacity(result, size, 1);
                byteArraySupport.putByte(result, size, value);
                size += 1;
            }

            return trimResult(result, size);
        }

        @Specialization(guards = "charset.zeroTerminatorLen == 2")
        byte[] doReadI16(@SuppressWarnings("unused") LLVMCharset charset, LLVMPointer string,
                        @Cached LLVMI16OffsetLoadNode load) {
            ByteArraySupport byteArraySupport = nativeOrder();
            byte[] result = new byte[bufferSize];
            int size = 0;
            while (true) {
                short value = load.executeWithTarget(string, size);
                if (value == 0) {
                    break;
                }
                result = ensureCapacity(result, size, 2);
                byteArraySupport.putShort(result, size, value);
                size += 2;
            }

            return trimResult(result, size);
        }

        @Specialization(guards = "charset.zeroTerminatorLen == 4")
        byte[] doReadI32(@SuppressWarnings("unused") LLVMCharset charset, LLVMPointer string,
                        @Cached LLVMI32OffsetLoadNode load) {
            ByteArraySupport byteArraySupport = nativeOrder();
            byte[] result = new byte[bufferSize];
            int size = 0;
            while (true) {
                int value = load.executeWithTarget(string, size);
                if (value == 0) {
                    break;
                }
                result = ensureCapacity(result, size, 4);
                byteArraySupport.putInt(result, size, value);
                size += 4;
            }

            return trimResult(result, size);
        }

        @Specialization(guards = "charset.zeroTerminatorLen == 8")
        byte[] doReadI64(@SuppressWarnings("unused") LLVMCharset charset, LLVMPointer string,
                        @Cached LLVMI64OffsetLoadNode load,
                        @Cached LLVMToNativeNode toNative) {
            ByteArraySupport byteArraySupport = nativeOrder();
            byte[] result = new byte[bufferSize];
            int size = 0;
            while (true) {
                long value = toNative.executeWithTarget(load.executeWithTargetGeneric(string, size)).asNative();
                if (value == 0) {
                    break;
                }
                result = ensureCapacity(result, size, 8);
                byteArraySupport.putLong(result, size, value);
                size += 8;
            }

            return trimResult(result, size);
        }
    }
}
