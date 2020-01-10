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
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMI64StoreNode extends LLVMStoreNodeCommon {

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected void doOp(LLVMNativePointer address, long value,
                    @CachedLanguage LLVMLanguage language) {
        language.getLLVMMemory().putI64(address, value);
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected void doOpDerefHandleI64(LLVMNativePointer addr, long value,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doOpManagedI64(getDerefHandleGetReceiverNode().execute(addr), value, nativeWrite);
    }

    @Specialization(guards = "isAutoDerefHandle(addr)", replaces = "doOpDerefHandleI64")
    protected void doOpDerefHandle(LLVMNativePointer addr, Object value,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doOpManaged(getDerefHandleGetReceiverNode().execute(addr), value, nativeWrite);
    }

    @Specialization(guards = "!isAutoDerefHandle(address)")
    protected void doOpNative(LLVMNativePointer address, LLVMNativePointer value,
                    @CachedLanguage LLVMLanguage language) {
        language.getLLVMMemory().putI64(address, value.asNative());
    }

    @Specialization(replaces = "doOpNative", guards = "!isAutoDerefHandle(addr)")
    protected void doOp(LLVMNativePointer addr, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toAddress,
                    @CachedLanguage LLVMLanguage language) {
        language.getLLVMMemory().putI64(addr, toAddress.executeWithTarget(value).asNative());
    }

    @Specialization(limit = "3")
    protected void doOpManagedI64(LLVMManagedPointer address, long value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        nativeWrite.writeI64(address.getObject(), address.getOffset(), value);
    }

    @Specialization(limit = "3", replaces = "doOpManagedI64")
    protected void doOpManaged(LLVMManagedPointer address, Object value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        nativeWrite.writeGenericI64(address.getObject(), address.getOffset(), value);
    }
}
