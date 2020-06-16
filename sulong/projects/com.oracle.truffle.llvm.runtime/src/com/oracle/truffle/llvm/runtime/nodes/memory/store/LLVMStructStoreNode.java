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
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeField(type = long.class, name = "structSize")
public abstract class LLVMStructStoreNode extends LLVMStoreNodeCommon {

    public abstract long getStructSize();

    @Child private LLVMMemMoveNode memMove;

    protected LLVMStructStoreNode(LLVMMemMoveNode memMove) {
        this.memMove = memMove;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "getStructSize() == 0")
    protected void noCopy(Object address, Object value) {
        // nothing to do
    }

    @Specialization(guards = {"getStructSize() > 0", "!isAutoDerefHandle(language, address)", "!isAutoDerefHandle(language, value)"})
    protected void doOp(LLVMNativePointer address, LLVMNativePointer value,
                    @CachedLanguage @SuppressWarnings("unused") LLVMLanguage language) {
        memMove.executeWithTarget(address, value, getStructSize());
    }

    @Specialization(guards = {"getStructSize() > 0", "isAutoDerefHandle(language, addr)", "isAutoDerefHandle(language, value)"})
    protected void doOpDerefHandle(LLVMNativePointer addr, LLVMNativePointer value,
                    @CachedLanguage @SuppressWarnings("unused") LLVMLanguage language,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver) {
        doManaged(getReceiver.execute(addr), getReceiver.execute(value));
    }

    @Specialization(guards = "getStructSize() > 0")
    protected void doManaged(LLVMManagedPointer address, LLVMManagedPointer value) {
        memMove.executeWithTarget(address, value, getStructSize());
    }

    @Specialization(guards = {"getStructSize() > 0", "!isAutoDerefHandle(language, address)"}, replaces = "doOp")
    protected void doConvert(LLVMNativePointer address, LLVMPointer value,
                    @CachedLanguage @SuppressWarnings("unused") LLVMLanguage language,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        memMove.executeWithTarget(address, toNative.executeWithTarget(value), getStructSize());
    }
}
