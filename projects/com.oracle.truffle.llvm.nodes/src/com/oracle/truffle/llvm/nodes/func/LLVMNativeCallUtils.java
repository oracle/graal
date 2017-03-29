/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.func;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

public final class LLVMNativeCallUtils {

    static Node getBindNode() {
        CompilerAsserts.neverPartOfCompilation();
        return Message.createInvoke(1).createNode();
    }

    static TruffleObject bindNativeSymbol(Node bindNode, TruffleObject symbol, String signature) {
        try {
            return (TruffleObject) ForeignAccess.sendInvoke(bindNode, symbol, "bind", signature);
        } catch (Throwable ex) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(symbol + " " + signature, ex);
        }
    }

    static Object callNativeFunction(LLVMContext context, Node nativeCall, TruffleObject function, Object[] nativeArgs, LLVMFunctionDescriptor descriptor) {
        if (LLVMOptions.ENGINE.traceNativeCalls()) {
            traceNativeCall(context, descriptor);
        }
        try {
            return ForeignAccess.sendExecute(nativeCall, function, nativeArgs);
        } catch (Throwable e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(function + Arrays.toString(nativeArgs), e);
        }
    }

    @TruffleBoundary
    private static void traceNativeCall(LLVMContext context, LLVMFunctionDescriptor descriptor) {
        context.registerNativeCall(descriptor);
    }

    public static TruffleObject bindNativeSymbol(TruffleObject symbol, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        return bindNativeSymbol(getBindNode(), symbol, signature);
    }
}
