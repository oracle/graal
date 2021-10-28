/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen.LLVMI1OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMI1StoreNode extends LLVMStoreNode {

    public abstract void executeWithTarget(LLVMPointer address, boolean value);

    @GenerateUncached
    public abstract static class LLVMI1OffsetStoreNode extends LLVMOffsetStoreNode {

        public static LLVMI1OffsetStoreNode create() {
            return LLVMI1OffsetStoreNodeGen.create(null, null, null);
        }

        public static LLVMI1OffsetStoreNode create(LLVMExpressionNode value) {
            return LLVMI1OffsetStoreNodeGen.create(null, null, value);
        }

        public abstract void executeWithTarget(LLVMPointer receiver, long offset, boolean value);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected void doOp(LLVMNativePointer addr, long offset, boolean value) {
            getLanguage().getLLVMMemory().putI1(this, addr.asNative() + offset, value);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected static void doOpDerefHandle(LLVMNativePointer addr, long offset, boolean value,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
            doOpManaged(getReceiver.execute(addr), offset, value, nativeWrite);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        protected static void doOpManaged(LLVMManagedPointer address, long offset, boolean value,
                        @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
            nativeWrite.writeI8(address.getObject(), address.getOffset() + offset, value ? (byte) 1 : (byte) 0);
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doOp(LLVMNativePointer addr, boolean value) {
        getLanguage().getLLVMMemory().putI1(this, addr, value);
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected static void doOpDerefHandle(LLVMNativePointer addr, boolean value,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doOpManaged(getReceiver.execute(addr), value, nativeWrite);
    }

    @Specialization(limit = "3")
    @GenerateAOT.Exclude
    protected static void doOpManaged(LLVMManagedPointer address, boolean value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        nativeWrite.writeI8(address.getObject(), address.getOffset(), value ? (byte) 1 : (byte) 0);
    }

    public static LLVMI1StoreNode create() {
        return LLVMI1StoreNodeGen.create(null, null);
    }
}
