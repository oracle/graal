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
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMIVarBitStoreNodeGen.LLVMIVarBitOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode.LLVMPrimitiveOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMIVarBitStoreNode extends LLVMStoreNode {

    protected final boolean isRecursive;

    protected LLVMIVarBitStoreNode() {
        this(false);
    }

    protected LLVMIVarBitStoreNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    protected abstract void executeWithTarget(LLVMManagedPointer address, LLVMIVarBit value);

    public abstract static class LLVMIVarBitOffsetStoreNode extends LLVMPrimitiveOffsetStoreNode {

        protected final boolean isRecursive;

        protected LLVMIVarBitOffsetStoreNode() {
            this(false);
        }

        protected LLVMIVarBitOffsetStoreNode(boolean isRecursive) {
            this.isRecursive = isRecursive;
        }

        public static LLVMIVarBitOffsetStoreNode create() {
            return LLVMIVarBitOffsetStoreNodeGen.create(false, null, null, null);
        }

        public static LLVMIVarBitOffsetStoreNode createRecursive() {
            return LLVMIVarBitOffsetStoreNodeGen.create(true, null, null, null);
        }

        public static LLVMIVarBitOffsetStoreNode create(LLVMExpressionNode value) {
            return LLVMIVarBitOffsetStoreNodeGen.create(false, null, null, value);
        }

        public abstract void executeWithTarget(LLVMPointer receiver, long offset, LLVMIVarBit value);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected void doOp(LLVMNativePointer addr, long offset, LLVMIVarBit value) {
            getLanguage().getLLVMMemory().putIVarBit(this, addr.asNative() + offset, value);
        }

        @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
        protected static void doOpDerefHandle(LLVMNativePointer addr, long offset, LLVMIVarBit value,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @Cached("createRecursive()") LLVMIVarBitOffsetStoreNode store) {
            store.executeWithTarget(getReceiver.execute(addr), offset, value);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        protected static void doOpManaged(LLVMManagedPointer address, long offset, LLVMIVarBit value,
                        @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
            byte[] bytes = value.getBytes();
            long curOffset = address.getOffset() + offset;
            for (int i = bytes.length - 1; i >= 0; i--) {
                nativeWrite.writeI8(address.getObject(), curOffset, bytes[i]);
                curOffset += I8_SIZE_IN_BYTES;
            }
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doOp(LLVMNativePointer addr, LLVMIVarBit value) {
        getLanguage().getLLVMMemory().putIVarBit(this, addr, value);
    }

    @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
    protected static void doOpDerefHandle(LLVMNativePointer addr, LLVMIVarBit value,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached("createRecursive()") LLVMIVarBitStoreNode store) {
        store.executeWithTarget(getReceiver.execute(addr), value);
    }

    @Specialization(limit = "3")
    @GenerateAOT.Exclude
    protected static void doOpManaged(LLVMManagedPointer address, LLVMIVarBit value,
                    @CachedLibrary("address.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        byte[] bytes = value.getBytes();
        long curOffset = address.getOffset();
        for (int i = bytes.length - 1; i >= 0; i--) {
            nativeWrite.writeI8(address.getObject(), curOffset, bytes[i]);
            curOffset += I8_SIZE_IN_BYTES;
        }
    }

    public static LLVMIVarBitStoreNode create() {
        return LLVMIVarBitStoreNodeGen.create(false, null, null);
    }

    public static LLVMIVarBitStoreNode createRecursive() {
        return LLVMIVarBitStoreNodeGen.create(true, null, null);
    }

}
