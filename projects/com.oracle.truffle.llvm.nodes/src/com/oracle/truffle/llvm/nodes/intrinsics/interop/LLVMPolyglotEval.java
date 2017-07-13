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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMPolyglotEval extends LLVMIntrinsic {

    @Specialization
    @TruffleBoundary
    public Object executeIntrinsicString(String mimeType, String code,
                    @Cached("getContext()") LLVMContext context) {

        try {
            Source sourceObject = Source.newBuilder(code).mimeType(mimeType).build();

            CallTarget callTarget = context.getEnv().parse(sourceObject);
            return callTarget.call();
        } catch (Exception e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException(e);
        }

    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = {"constantPointer(mimePointer, cachedMimePtr)", "constantPointer(srcPointer, cachedSrcPtr)"})
    public Object executeIntrinsicCached(LLVMAddress mimePointer, LLVMAddress srcPointer,
                    @Cached("pointerOf(mimePointer)") long cachedMimePtr,
                    @Cached("pointerOf(srcPointer)") long cachedSrcPtr,
                    @Cached("readString(mimePointer)") String mime,
                    @Cached("readString(srcPointer)") String src,
                    @Cached("getContext()") LLVMContext context) {
        return executeIntrinsicString(mime, src, context);
    }

    @Specialization
    public Object executeIntrinsicUncached(LLVMAddress mimePointer, LLVMAddress srcPointer,
                    @Cached("getContext()") LLVMContext context) {
        return executeIntrinsicString(LLVMTruffleIntrinsicUtil.readString(mimePointer), LLVMTruffleIntrinsicUtil.readString(srcPointer), context);
    }

    @Specialization
    public Object executeIntrinsicUncached(String mime, LLVMAddress srcPointer,
                    @Cached("getContext()") LLVMContext context) {
        return executeIntrinsicString(mime, LLVMTruffleIntrinsicUtil.readString(srcPointer), context);
    }

    @Specialization
    public Object executeIntrinsicUncached(LLVMAddress mimePointer, String src,
                    @Cached("getContext()") LLVMContext context) {
        return executeIntrinsicString(LLVMTruffleIntrinsicUtil.readString(mimePointer), src, context);
    }

}
