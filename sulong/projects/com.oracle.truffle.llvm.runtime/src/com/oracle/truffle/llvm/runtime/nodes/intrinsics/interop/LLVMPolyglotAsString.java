/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadCharsetNode.LLVMCharset;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotAsStringNodeGen.EncodeStringNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMPolyglotAsStringNodeGen.WriteStringNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.nio.ByteBuffer;

@NodeChild(value = "object", type = LLVMExpressionNode.class)
@NodeChild(value = "buffer", type = LLVMExpressionNode.class)
@NodeChild(value = "buflen", type = LLVMExpressionNode.class)
@NodeChild(value = "charset", type = LLVMReadCharsetNode.class)
public abstract class LLVMPolyglotAsString extends LLVMIntrinsic {

    public static LLVMPolyglotAsString create(LLVMExpressionNode object, LLVMExpressionNode buffer, LLVMExpressionNode buflen, LLVMExpressionNode charset) {
        return LLVMPolyglotAsStringNodeGen.create(object, buffer, buflen, LLVMReadCharsetNodeGen.create(charset));
    }

    @Child EncodeStringNode encodeString = EncodeStringNodeGen.create();
    @Child WriteStringNode writeString = WriteStringNodeGen.create();

    @Specialization
    long doAsString(VirtualFrame frame, Object object, Object buffer, long buflen, LLVMCharset charset) {
        ByteBuffer result = encodeString.execute(object, charset);
        return writeString.execute(frame, result, buffer, buflen, charset.zeroTerminatorLen);
    }

    abstract static class EncodeStringNode extends LLVMNode {

        protected abstract ByteBuffer execute(Object str, LLVMCharset charset);

        @Specialization
        ByteBuffer doString(String str, LLVMCharset charset) {
            return charset.encode(str);
        }

        @Specialization
        ByteBuffer doForeign(LLVMManagedPointer obj, LLVMCharset charset,
                        @Cached LLVMAsForeignNode asForeign,
                        @Cached BoxedEncodeStringNode encode) {
            return encode.execute(asForeign.execute(obj), charset);
        }
    }

    abstract static class BoxedEncodeStringNode extends LLVMNode {

        abstract ByteBuffer execute(Object object, LLVMCharset charset);

        @Specialization(limit = "3")
        ByteBuffer doBoxed(Object object, LLVMCharset charset,
                        @CachedLibrary("object") InteropLibrary interop,
                        @Cached BranchProfile exception) {
            try {
                String unboxed = interop.asString(object);
                return charset.encode(unboxed);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Polyglot value is not a string.");
            }
        }
    }

    abstract static class WriteStringNode extends LLVMNode {

        @Child private LLVMStoreNode write = LLVMI8StoreNodeGen.create(null, null);

        protected abstract long execute(VirtualFrame frame, ByteBuffer source, Object target, long targetLen, int zeroTerminatorLen);

        @Specialization(guards = "srcBuffer.getClass() == srcBufferClass")
        long doWrite(ByteBuffer srcBuffer, LLVMPointer target, long targetLen, int zeroTerminatorLen,
                        @Cached("srcBuffer.getClass()") Class<? extends ByteBuffer> srcBufferClass) {
            ByteBuffer source = CompilerDirectives.castExact(srcBuffer, srcBufferClass);

            long bytesWritten = 0;
            LLVMPointer ptr = target;
            while (source.hasRemaining() && bytesWritten < targetLen) {
                write.executeWithTarget(ptr, source.get());
                ptr = ptr.increment(Byte.BYTES);
                bytesWritten++;
            }

            long ret = bytesWritten;

            for (int i = 0; i < zeroTerminatorLen && bytesWritten < targetLen; i++) {
                write.executeWithTarget(ptr, (byte) 0);
                ptr = ptr.increment(Byte.BYTES);
                bytesWritten++;
            }

            return ret;
        }
    }
}
