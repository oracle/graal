/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nativemode.runtime;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.nativemode.runtime.NFIContextExtension.WellKnownFunction;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NativeContextExtension.WellKnownNativeFunctionNode;

abstract class WellKnownNFIFunctionNode extends WellKnownNativeFunctionNode {

    private final WellKnownFunction function;
    private final ContextExtension.Key<NativeContextExtension> ctxExtKey;

    WellKnownNFIFunctionNode(WellKnownFunction function) {
        this.function = function;
        this.ctxExtKey = LLVMLanguage.get(null).lookupContextExtension(NativeContextExtension.class);
    }

    Object getFunction() {
        NFIContextExtension ctxExt = (NFIContextExtension) ctxExtKey.get(getContext());
        return ctxExt.getCachedWellKnownFunction(function);
    }

    @Specialization(assumptions = "singleContextAssumption()")
    @GenerateAOT.Exclude
    Object doCached(Object[] args,
                    @Cached("getFunction()") Object cachedFunction,
                    @CachedLibrary("cachedFunction") InteropLibrary interop) throws ArityException, UnsupportedMessageException, UnsupportedTypeException {
        return interop.execute(cachedFunction, args);
    }

    @Specialization(replaces = "doCached")
    @GenerateAOT.Exclude
    Object doGeneric(Object[] args,
                    @CachedLibrary(limit = "3") InteropLibrary interop) throws ArityException, UnsupportedMessageException, UnsupportedTypeException {
        Object fn = getFunction();
        return interop.execute(fn, args);
    }
}
