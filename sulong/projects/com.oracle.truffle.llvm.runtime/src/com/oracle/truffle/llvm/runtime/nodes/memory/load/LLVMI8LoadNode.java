/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen.LLVMI8OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMI8LoadNode extends LLVMLoadNode {

    public static LLVMI8LoadNode create() {
        return LLVMI8LoadNodeGen.create((LLVMExpressionNode) null);
    }

    public abstract byte executeWithTarget(Object address);

    @GenerateUncached
    public abstract static class LLVMI8OffsetLoadNode extends LLVMOffsetLoadNode {

        public static LLVMI8OffsetLoadNode create() {
            return LLVMI8OffsetLoadNodeGen.create();
        }

        public abstract byte executeWithTarget(LLVMPointer receiver, long offset);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected byte doI8Native(LLVMNativePointer addr, long offset) {
            return getLanguage().getLLVMMemory().getI8(this, addr.asNative() + offset);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected byte doI8DerefHandle(LLVMNativePointer addr, long offset,
                        @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                        @CachedLibrary(limit = "3") LLVMManagedReadLibrary nativeRead) {
            return doI8Managed(getReceiver.execute(addr), offset, nativeRead);
        }

        @Specialization(limit = "3")
        @GenerateAOT.Exclude
        protected byte doI8Managed(LLVMManagedPointer addr, long offset,
                        @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
            return nativeRead.readI8(addr.getObject(), addr.getOffset() + offset);
        }
    }

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected byte doI8Native(LLVMNativePointer addr) {
        return getLanguage().getLLVMMemory().getI8(this, addr);
    }

    @Specialization(guards = "isAutoDerefHandle(addr)")
    protected byte doI8DerefHandle(LLVMNativePointer addr,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @CachedLibrary(limit = "3") LLVMManagedReadLibrary nativeRead) {
        return doI8Managed(getReceiver.execute(addr), nativeRead);
    }

    @Specialization(limit = "3")
    @GenerateAOT.Exclude
    protected byte doI8Managed(LLVMManagedPointer addr,
                    @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
        return nativeRead.readI8(addr.getObject(), addr.getOffset());
    }
}
