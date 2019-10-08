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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.typed;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMTypeIDNode extends LLVMExpressionNode {

    public static LLVMTypeIDNode create(LLVMExpressionNode child) {
        return LLVMTypeIDNodeGen.create(child);
    }

    @CompilationFinal private LLVMInteropType cachedType;

    protected final LLVMInteropType getType(ContextReference<LLVMContext> ctxRef, LLVMPointer pointer) {
        LLVMContext context = ctxRef.get();
        if (cachedType == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMGlobal global = ctxRef.get().findGlobal(pointer);
            if (global == null) {
                return null;
            }
            cachedType = global.getInteropType(context);
        } else {
            // the type this resolved to needs to stay the same
            assert context.findGlobal(pointer).getInteropType(context) == cachedType;
        }
        return cachedType;
    }

    @Specialization
    LLVMInteropType doGlobal(LLVMPointer pointer,
                    @CachedContext(LLVMLanguage.class) ContextReference<LLVMContext> ctxRef) {
        LLVMInteropType type = getType(ctxRef, pointer);
        if (type instanceof LLVMInteropType.Array) {
            return ((LLVMInteropType.Array) type).getElementType();
        }

        CompilerDirectives.transferToInterpreter();
        return fallback(pointer);
    }

    @Fallback
    LLVMInteropType.Structured fallback(@SuppressWarnings("unused") Object typeid) {
        CompilerDirectives.transferToInterpreter();
        throw new LLVMPolyglotException(this, "Couldn't find runtime type information. Make sure the LLVM bitcode is compiled with debug information (-g).");
    }
}
