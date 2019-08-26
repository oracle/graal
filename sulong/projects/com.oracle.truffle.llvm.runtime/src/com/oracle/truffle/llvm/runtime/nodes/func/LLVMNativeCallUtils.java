/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.func;

import java.util.StringJoiner;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;

public final class LLVMNativeCallUtils {

    static Object bindNativeSymbol(InteropLibrary interop, TruffleObject symbol, String signature) {
        try {
            return interop.invokeMember(symbol, "bind", signature);
        } catch (InteropException ex) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Could not bind " + symbol + " " + signature, ex);
        }
    }

    static Object callNativeFunction(boolean enabled, ContextReference<LLVMContext> context, InteropLibrary nativeCall, Object function, Object[] nativeArgs, LLVMFunctionDescriptor descriptor) {
        CompilerAsserts.partialEvaluationConstant(enabled);
        if (enabled) {
            if (descriptor != null) {
                traceNativeCall(context.get(), descriptor);
            }
        }
        try {
            return nativeCall.execute(function, nativeArgs);
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Exception thrown by a callback during the native call " + function + argsToString(nativeArgs), e);
        }
    }

    @TruffleBoundary
    private static String argsToString(Object[] nativeArgs) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (Object arg : nativeArgs) {
            joiner.add(arg.toString());
        }
        return joiner.toString();
    }

    @TruffleBoundary
    private static void traceNativeCall(LLVMContext context, LLVMFunctionDescriptor descriptor) {
        context.registerNativeCall(descriptor);
    }
}
