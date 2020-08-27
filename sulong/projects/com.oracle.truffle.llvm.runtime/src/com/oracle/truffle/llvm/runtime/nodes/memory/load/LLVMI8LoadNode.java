/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory.load;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ByteValueProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@GenerateUncached
public abstract class LLVMI8LoadNode extends LLVMLoadNode {

    public static LLVMI8LoadNode create() {
        return LLVMI8LoadNodeGen.create((LLVMExpressionNode) null);
    }

    @Specialization(guards = "!isAutoDerefHandle(language, addr)")
    protected byte doI8Native(LLVMNativePointer addr,
                    @Cached("createIdentityProfile()") ByteValueProfile profile,
                    @CachedLanguage LLVMLanguage language) {
        return profile.profile(language.getLLVMMemory().getI8(this, addr));
    }

    @Specialization(guards = "isAutoDerefHandle(language, addr)")
    protected byte doI8DerefHandle(LLVMNativePointer addr,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @CachedLanguage @SuppressWarnings("unused") LLVMLanguage language,
                    @CachedLibrary(limit = "3") LLVMManagedReadLibrary nativeRead) {
        return doI8Managed(getReceiver.execute(addr), nativeRead);
    }

    @Specialization(limit = "3")
    protected byte doI8Managed(LLVMManagedPointer addr,
                    @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
        return nativeRead.readI8(addr.getObject(), addr.getOffset());
    }
}
