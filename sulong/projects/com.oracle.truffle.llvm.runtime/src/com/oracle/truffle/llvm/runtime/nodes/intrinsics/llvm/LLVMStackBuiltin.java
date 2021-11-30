/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.LLVMStackAccess;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;

abstract class LLVMStackBuiltin extends LLVMBuiltin {

    @CompilationFinal private LLVMStackAccess stackAccess;

    /**
     * Eager initialization of stackAccess during AOT preparation.
     */
    @Child private AOTInitHelper aotInitHelper = new AOTInitHelper(new GenerateAOT.Provider() {
        @Override
        public void prepareForAOT(TruffleLanguage<?> language, RootNode root) {
            stackAccess = ((LLVMRootNode) root).getStackAccess();
        }
    });

    protected LLVMStackAccess ensureStackAccess() {
        if (stackAccess == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackAccess = ((LLVMRootNode) getRootNode()).getStackAccess();
        }
        return stackAccess;
    }
}
