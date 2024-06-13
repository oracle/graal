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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDerefHandleGetReceiverNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNodeGen.LLVM80BitFloatOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode.LLVMPrimitiveOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVM80BitFloatStoreNode extends LLVMStoreNode {

    protected final boolean isRecursive;

    protected LLVM80BitFloatStoreNode() {
        this(false);
    }

    protected LLVM80BitFloatStoreNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    public static LLVM80BitFloatStoreNode create() {
        return LLVM80BitFloatStoreNodeGen.create(false, null, null);
    }

    public static LLVM80BitFloatStoreNode createRecursive() {
        return LLVM80BitFloatStoreNodeGen.create(true, null, null);
    }

    public abstract void executeWithTarget(LLVMPointer address, LLVM80BitFloat value);

    @GenerateUncached
    public abstract static class LLVM80BitFloatOffsetStoreNode extends LLVMPrimitiveOffsetStoreNode {

        public static LLVM80BitFloatOffsetStoreNode create() {
            return LLVM80BitFloatOffsetStoreNodeGen.create(null, null, null);
        }

        public static LLVM80BitFloatOffsetStoreNode create(LLVMExpressionNode value) {
            return LLVM80BitFloatOffsetStoreNodeGen.create(null, null, value);
        }

        public abstract void executeWithTarget(LLVMPointer receiver, long offset, LLVM80BitFloat value);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected void doOp(LLVMNativePointer addr, long offset, LLVM80BitFloat value) {
            getLanguage().getLLVMMemory().put80BitFloat(this, addr.asNative() + offset, value);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected static void doOpDerefHandle(LLVMNativePointer addr, long offset, LLVM80BitFloat value,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
            doOpManaged(getReceiver.execute(addr), offset, value, nativeWrite);
        }

        @Specialization
        @GenerateAOT.Exclude
        protected static void doOpManaged(LLVMManagedPointer address, long offset, LLVM80BitFloat value,
                        @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
            /*
             * Since we write multiple values, we need to use a dispatching library here.
             *
             * @CachedLibrary("address.getObject()") does not work because of possible state
             * transitions on earlier writes.
             */
            byte[] bytes = value.getBytes();
            assert bytes.length == LLVM80BitFloat.BYTE_WIDTH;
            long curOffset = address.getOffset() + offset;
            for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
                nativeWrite.writeI8(address.getObject(), curOffset, bytes[i]);
                curOffset += I8_SIZE_IN_BYTES;
            }
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected void doOp(LLVMNativePointer addr, LLVM80BitFloat value) {
        getLanguage().getLLVMMemory().put80BitFloat(this, addr, value);
    }

    @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
    protected static void doOpDerefHandle(LLVMNativePointer addr, LLVM80BitFloat value,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached("createRecursive()") LLVM80BitFloatStoreNode store) {
        store.executeWithTarget(getReceiver.execute(addr), value);
    }

    // TODO (chaeubl): we could store this in a more efficient way (short + long)
    // TODO (fredmorcos) When GR-26485 is fixed, use limit = "3" here.
    @Specialization
    @ExplodeLoop
    @GenerateAOT.Exclude
    protected static void doForeign(LLVMManagedPointer address, LLVM80BitFloat value,
                    // TODO (fredmorcos) When GR-26485 is fixed, use
                    // @CachedLibrary("address.getObject()") here.
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        byte[] bytes = value.getBytes();
        assert bytes.length == LLVM80BitFloat.BYTE_WIDTH;
        long curOffset = address.getOffset();
        for (int i = 0; i < LLVM80BitFloat.BYTE_WIDTH; i++) {
            nativeWrite.writeI8(address.getObject(), curOffset, bytes[i]);
            curOffset += I8_SIZE_IN_BYTES;
        }
    }
}
