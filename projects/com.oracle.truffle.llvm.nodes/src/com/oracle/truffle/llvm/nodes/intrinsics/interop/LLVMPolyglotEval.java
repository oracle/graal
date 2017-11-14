/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMPolyglotEval extends LLVMIntrinsic {

    protected static CallTarget getCallTarget(String mimeType, String code, ContextReference<LLVMContext> context) {
        Source sourceObject = Source.newBuilder(code).name("<eval>").mimeType(mimeType).build();
        CallTarget callTarget = context.get().getEnv().parse(sourceObject);
        return callTarget;
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = {"mime.equals(readMime.executeWithTarget(frame, mimePointer))", "src.equals(readSrc.executeWithTarget(frame, srcPointer))"})
    public Object doCached(VirtualFrame frame, Object mimePointer, Object srcPointer,
                    @Cached("createReadString()") LLVMReadStringNode readMime,
                    @Cached("createReadString()") LLVMReadStringNode readSrc,
                    @Cached("readMime.executeWithTarget(frame, mimePointer)") String mime,
                    @Cached("readSrc.executeWithTarget(frame, srcPointer)") String src,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context,
                    @Cached("getCallTarget(mime, src, context)") CallTarget callTarget) {
        return callTarget.call();
    }

    @Specialization(replaces = "doCached")
    public Object uncached(VirtualFrame frame, Object mimePointer, Object srcPointer,
                    @Cached("createReadString()") LLVMReadStringNode readMime,
                    @Cached("createReadString()") LLVMReadStringNode readSrc,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context) {
        return getCallTarget(readMime.executeWithTarget(frame, mimePointer), readSrc.executeWithTarget(frame, srcPointer), context).call();
    }
}
