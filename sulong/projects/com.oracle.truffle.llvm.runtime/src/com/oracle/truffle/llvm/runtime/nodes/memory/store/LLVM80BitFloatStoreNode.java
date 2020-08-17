/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVM80BitFloatStoreNode extends LLVMStoreNodeCommon {

    static LLVM80BitFloatStoreNode create() {
        return LLVM80BitFloatStoreNodeGen.create(null, null);
    }

    protected abstract void executeManaged(LLVMManagedPointer address, LLVM80BitFloat value);

    @Specialization(guards = "!isAutoDerefHandle(language, addr)")
    protected void doOp(LLVMNativePointer addr, LLVM80BitFloat value,
                    @CachedLanguage LLVMLanguage language) {
        language.getLLVMMemory().put80BitFloat(this, addr, value);
    }

    @Specialization(guards = "isAutoDerefHandle(language, addr)")
    protected void doOpDerefHandle(LLVMNativePointer addr, LLVM80BitFloat value,
                    @CachedLanguage @SuppressWarnings("unused") LLVMLanguage language,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached LLVM80BitFloatStoreNode store) {
        store.executeManaged(getReceiver.execute(addr), value);
    }

    // TODO (chaeubl): we could store this in a more efficient way (short + long)
    @Specialization(limit = "3")
    @ExplodeLoop
    protected void doForeign(LLVMManagedPointer address, LLVM80BitFloat value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        byte[] bytes = value.getBytes();
        assert bytes.length == LLVM80BitFloat.BYTE_WIDTH;
        long curOffset = address.getOffset();
        for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
            nativeWrite.writeI8(address.getObject(), curOffset, bytes[i]);
            curOffset += I8_SIZE_IN_BYTES;
        }
    }
}
