/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode.LLVMPrimitiveOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen.LLVMPointerOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMPointerStoreNode extends LLVMStoreNode {

    public static LLVMPointerStoreNode create() {
        return LLVMPointerStoreNodeGen.create(null, null);
    }

    public abstract void executeWithTarget(LLVMPointer address, Object value);

    @GenerateUncached
    public abstract static class LLVMPointerOffsetStoreNode extends LLVMPrimitiveOffsetStoreNode {

        public static LLVMPointerOffsetStoreNode create() {
            return LLVMPointerOffsetStoreNodeGen.create(null, null, null);
        }

        public static LLVMPointerOffsetStoreNode create(LLVMExpressionNode value) {
            return LLVMPointerOffsetStoreNodeGen.create(null, null, value);
        }

        public abstract void executeWithTarget(LLVMPointer receiver, long offset, Object value);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected void doAddress(LLVMNativePointer addr, long offset, Object value,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
            getLanguage().getLLVMMemory().putPointer(this, addr.asNative() + offset, toNative.executeWithTarget(value));
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected static void doOpDerefHandle(LLVMNativePointer addr, long offset, Object value,
                        @Cached LLVMToPointerNode toPointer,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
            doManaged(getReceiver.execute(addr), offset, value, toPointer, nativeWrite);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected static void doDerefAddress(long addr, long offset, Object value,
                        @Cached LLVMToPointerNode toPointer,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
            doManaged(getReceiver.execute(addr), offset, value, toPointer, nativeWrite);
        }

        @Specialization(limit = "3")
        protected static void doManaged(LLVMManagedPointer addr, long offset, Object value,
                        @Cached LLVMToPointerNode toPointer,
                        @CachedLibrary("addr.getObject()") LLVMManagedWriteLibrary nativeWrite) {
            nativeWrite.writePointer(addr.getObject(), addr.getOffset() + offset, toPointer.executeWithTarget(value));
        }

        @Specialization(replaces = "doManaged")
        protected static void doManagedAOT(LLVMManagedPointer addr, long offset, Object value,
                        @Cached LLVMToPointerNode toPointer,
                        @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
            doManaged(addr, offset, value, toPointer, nativeWrite);
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doAddress(LLVMNativePointer addr, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        getLanguage().getLLVMMemory().putPointer(this, addr, toNative.executeWithTarget(value));
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doAddress(long addr, Object value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        getLanguage().getLLVMMemory().putPointer(this, addr, toNative.executeWithTarget(value));
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected static void doOpDerefHandle(LLVMNativePointer addr, Object value,
                    @Cached LLVMToPointerNode toPointer,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doManaged(getReceiver.execute(addr), value, toPointer, nativeWrite);
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected static void doDerefAddress(long addr, Object value,
                    @Cached LLVMToPointerNode toPointer,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doManaged(getReceiver.execute(addr), value, toPointer, nativeWrite);
    }

    @Specialization(limit = "3")
    protected static void doManaged(LLVMManagedPointer address, Object value,
                    @Cached LLVMToPointerNode toPointer,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        nativeWrite.writePointer(address.getObject(), address.getOffset(), toPointer.executeWithTarget(value));
    }

    @Specialization(replaces = "doManaged")
    protected static void doManagedAOT(LLVMManagedPointer address, Object value,
                    @Cached LLVMToPointerNode toPointer,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        doManaged(address, value, toPointer, nativeWrite);
    }
}
