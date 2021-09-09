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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVM80BitFloatLoadNode extends LLVMLoadNode {

    protected final boolean isRecursive;

    protected LLVM80BitFloatLoadNode() {
        this(false);
    }

    protected LLVM80BitFloatLoadNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    static LLVM80BitFloatLoadNode create() {
        return LLVM80BitFloatLoadNodeGen.create((LLVMExpressionNode) null);
    }

    static LLVM80BitFloatLoadNode createRecursive() {
        return LLVM80BitFloatLoadNodeGen.create(true, (LLVMExpressionNode) null);
    }

    public abstract LLVM80BitFloat executeWithTarget(LLVMManagedPointer addr);

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected LLVM80BitFloat do80BitFloatNative(LLVMNativePointer addr) {
        return getLanguage().getLLVMMemory().get80BitFloat(this, addr);
    }

    @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
    protected LLVM80BitFloat do80BitFloatDerefHandle(LLVMNativePointer addr,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached("createRecursive()") LLVM80BitFloatLoadNode load) {
        return load.executeWithTarget(getReceiver.execute(addr));
    }

    @Specialization(limit = "3")
    @ExplodeLoop
    @GenerateAOT.Exclude
    protected LLVM80BitFloat doForeign(LLVMManagedPointer addr,
                    @CachedLibrary("addr.getObject()") LLVMManagedReadLibrary nativeRead) {
        byte[] result = new byte[LLVM80BitFloat.BYTE_WIDTH];
        long curOffset = addr.getOffset();
        for (int i = 0; i < result.length; i++) {
            result[i] = nativeRead.readI8(addr.getObject(), curOffset);
            curOffset += I8_SIZE_IN_BYTES;
        }
        return LLVM80BitFloat.fromBytes(result);
    }
}
