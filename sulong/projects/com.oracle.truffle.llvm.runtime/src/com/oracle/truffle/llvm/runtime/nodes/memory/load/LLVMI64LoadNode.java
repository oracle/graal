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

import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.LongValueProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMI64LoadNode extends LLVMAbstractLoadNode {

    private final LongValueProfile profile = LongValueProfile.createIdentityProfile();

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected long doI64Native(LLVMNativePointer addr,
                    @CachedLanguage LLVMLanguage language) {
        return profile.profile(language.getLLVMMemory().getI64(addr));
    }

    @Specialization(guards = "isAutoDerefHandle(addr)", rewriteOn = UnexpectedResultException.class)
    protected long doI64DerefHandle(LLVMNativePointer addr,
                    @CachedLibrary(limit = "3") LLVMManagedReadLibrary nativeRead) throws UnexpectedResultException {
        return doI64Managed(getDerefHandleGetReceiverNode().execute(addr), nativeRead);
    }

    @Specialization(guards = "isAutoDerefHandle(addr)", replaces = "doI64DerefHandle")
    protected Object doGenericI64DerefHandle(LLVMNativePointer addr,
                    @CachedLibrary(limit = "3") LLVMManagedReadLibrary nativeRead) {
        return doGenericI64Managed(getDerefHandleGetReceiverNode().execute(addr), nativeRead);
    }

    @Specialization(limit = "3", rewriteOn = UnexpectedResultException.class)
    protected long doI64Managed(LLVMManagedPointer addr,
                    @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) throws UnexpectedResultException {
        return nativeRead.readI64(addr.getObject(), addr.getOffset());
    }

    @Specialization(limit = "3", replaces = "doI64Managed")
    protected Object doGenericI64Managed(LLVMManagedPointer addr,
                    @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
        return nativeRead.readGenericI64(addr.getObject(), addr.getOffset());
    }
}
