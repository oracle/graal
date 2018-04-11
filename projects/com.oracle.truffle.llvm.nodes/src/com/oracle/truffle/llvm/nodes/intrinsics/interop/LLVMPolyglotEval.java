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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMPolyglotEval extends LLVMIntrinsic {

    private final boolean legacyMimeTypeEval;

    @Child ForeignToLLVM toLLVM = ForeignToLLVM.create(ForeignToLLVMType.POINTER);

    public static LLVMPolyglotEval create(LLVMExpressionNode id, LLVMExpressionNode code) {
        return LLVMPolyglotEvalNodeGen.create(false, id, code);
    }

    public static LLVMPolyglotEval createLegacy(LLVMExpressionNode mime, LLVMExpressionNode code) {
        return LLVMPolyglotEvalNodeGen.create(true, mime, code);
    }

    protected LLVMPolyglotEval(boolean legacyMimeTypeEval) {
        this.legacyMimeTypeEval = legacyMimeTypeEval;
    }

    @TruffleBoundary
    protected CallTarget getCallTarget(String id, String code, ContextReference<LLVMContext> context) {
        Source sourceObject;
        if (legacyMimeTypeEval) {
            sourceObject = Source.newBuilder(code).name("<eval>").mimeType(id).build();
        } else {
            sourceObject = Source.newBuilder(code).name("<eval>").language(id).build();
        }
        return context.get().getEnv().parse(sourceObject);
    }

    @SuppressWarnings("unused")
    @Specialization(limit = "2", guards = {"id.equals(readId.executeWithTarget(idPointer))", "src.equals(readSrc.executeWithTarget(srcPointer))"})
    protected Object doCached(Object idPointer, Object srcPointer,
                    @Cached("createReadString()") LLVMReadStringNode readId,
                    @Cached("createReadString()") LLVMReadStringNode readSrc,
                    @Cached("readId.executeWithTarget(idPointer)") String id,
                    @Cached("readSrc.executeWithTarget(srcPointer)") String src,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context,
                    @Cached("getCallTarget(id, src, context)") CallTarget callTarget) {
        Object foreign = callTarget.call();
        return toLLVM.executeWithTarget(foreign);
    }

    @Specialization(replaces = "doCached")
    protected Object uncached(Object idPointer, Object srcPointer,
                    @Cached("createReadString()") LLVMReadStringNode readId,
                    @Cached("createReadString()") LLVMReadStringNode readSrc,
                    @Cached("getContextReference()") ContextReference<LLVMContext> context) {
        Object foreign = getCallTarget(readId.executeWithTarget(idPointer), readSrc.executeWithTarget(srcPointer), context).call();
        return toLLVM.executeWithTarget(foreign);
    }
}
