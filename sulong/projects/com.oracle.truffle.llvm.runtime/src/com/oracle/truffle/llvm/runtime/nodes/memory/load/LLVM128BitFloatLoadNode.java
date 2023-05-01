/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVM128BitFloatLoadNode extends LLVMLoadNode {

    protected final boolean isRecursive;

    protected LLVM128BitFloatLoadNode() {
        this(false);
    }

    protected LLVM128BitFloatLoadNode(boolean isRecursive) {
        this.isRecursive = isRecursive;
    }

    static LLVM128BitFloatLoadNode create() {
        return LLVM128BitFloatLoadNodeGen.create((LLVMExpressionNode) null);
    }

    static LLVM128BitFloatLoadNode createRecursive() {
        return LLVM128BitFloatLoadNodeGen.create(true, (LLVMExpressionNode) null);
    }

    public abstract LLVM128BitFloat executeWithTarget(LLVMManagedPointer addr);

    @Specialization(guards = "!isAutoDerefHandle(addr)")
    protected LLVM128BitFloat do128BitFloatNative(LLVMNativePointer addr) {
        return getLanguage().getLLVMMemory().get128BitFloat(this, addr);
    }

    @Specialization(guards = {"!isRecursive", "isAutoDerefHandle(addr)"})
    protected LLVM128BitFloat do128BitFloatDerefHandle(LLVMNativePointer addr,
                    @Cached LLVMDerefHandleGetReceiverNode getReceiver,
                    @Cached("createRecursive()") LLVM128BitFloatLoadNode load) {
        return load.executeWithTarget(getReceiver.execute(addr));
    }
}
