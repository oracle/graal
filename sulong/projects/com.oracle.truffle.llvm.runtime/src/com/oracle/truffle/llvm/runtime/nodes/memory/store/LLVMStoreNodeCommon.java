/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory.store;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.factories.LLVMObjectAccessFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMStoreNodeCommon extends LLVMStoreNode {

    @CompilationFinal private LanguageReference<LLVMLanguage> languageRef;

    @Child private LLVMDerefHandleGetReceiverNode derefHandleGetReceiverNode;

    protected LLVMDerefHandleGetReceiverNode getDerefHandleGetReceiverNode() {
        if (derefHandleGetReceiverNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            derefHandleGetReceiverNode = insert(LLVMDerefHandleGetReceiverNode.create());
        }
        return derefHandleGetReceiverNode;
    }

    protected static LLVMObjectWriteNode createForeignWrite() {
        return LLVMObjectAccessFactory.createWrite();
    }

    protected boolean isAutoDerefHandle(LLVMNativePointer addr) {
        return isAutoDerefHandle(addr.asNative());
    }

    protected boolean isAutoDerefHandle(long addr) {
        if (languageRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            languageRef = lookupLanguageReference(LLVMLanguage.class);
        }
        // checking the bit is cheaper than getting the assumption in interpreted mode
        if (CompilerDirectives.inCompiledCode() && languageRef.get().getNoDerefHandleAssumption().isValid()) {
            return false;
        }
        return LLVMNativeMemory.isDerefHandleMemory(addr);
    }
}
