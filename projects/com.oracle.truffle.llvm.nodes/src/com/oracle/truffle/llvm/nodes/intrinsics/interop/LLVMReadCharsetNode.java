/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMReadCharsetNode extends Node {

    @Child LLVMReadStringNode readString = LLVMReadStringNodeGen.create();

    public abstract Object execute(VirtualFrame frame);

    @Specialization(guards = "cachedAddress.equals(address)")
    @SuppressWarnings("unused")
    protected LLVMCharset doCachedAddress(LLVMAddress address,
                    @Cached("address") LLVMAddress cachedAddress,
                    @Cached("doGeneric(cachedAddress)") LLVMCharset cachedCharset) {
        return cachedCharset;
    }

    @Specialization(guards = {"foreign.getObject() == cachedForeign.getObject()", "foreign.getOffset() == cachedForeign.getOffset()"})
    @SuppressWarnings("unused")
    protected LLVMCharset doCachedForeign(LLVMTruffleObject foreign,
                    @Cached("foreign") LLVMTruffleObject cachedForeign,
                    @Cached("doGeneric(cachedForeign)") LLVMCharset cachedCharset) {
        return cachedCharset;
    }

    @Specialization(guards = "address == cachedAddress")
    @SuppressWarnings("unused")
    protected LLVMCharset doCachedOther(Object address,
                    @Cached("address") Object cachedAddress,
                    @Cached("doGeneric(cachedAddress)") LLVMCharset cachedCharset) {
        return cachedCharset;
    }

    @Specialization(replaces = {"doCachedAddress", "doCachedForeign", "doCachedOther"})
    protected LLVMCharset doGeneric(Object strPtr) {
        String string = readString.executeWithTarget(strPtr);
        return lookup(string);
    }

    @TruffleBoundary
    private static LLVMCharset lookup(String str) {
        return new LLVMCharset(Charset.forName(str));
    }

    public static final class LLVMCharset {

        private final Charset charset;
        public final int zeroTerminatorLen;

        private LLVMCharset(Charset charset) {
            this.charset = charset;
            this.zeroTerminatorLen = charset.encode("\0").limit();
        }

        @TruffleBoundary
        public ByteBuffer encode(String str) {
            return charset.encode(str);
        }

        @TruffleBoundary
        public String decode(ByteBuffer b) {
            return charset.decode(b).toString();
        }
    }
}
