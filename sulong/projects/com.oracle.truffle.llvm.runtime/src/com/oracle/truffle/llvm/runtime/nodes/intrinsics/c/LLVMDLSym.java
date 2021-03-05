/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.c;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMDLOpen.LLVMDLHandler;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMDLSym extends LLVMIntrinsic {

    @Specialization(guards = "isLLVMLibrary(pointer)", limit = "1")
    protected Object doOp(LLVMManagedPointer pointer,
                    LLVMPointer flag,
                    @Cached() LLVMReadStringNode readStr,
                    @CachedLibrary("getLibrary(pointer)") InteropLibrary interop) {
        try {
            String flagName = readStr.executeWithTarget(flag);
            return LLVMManagedPointer.create(interop.readMember(getLibrary(pointer), flagName));
        } catch (InteropException e) {
            return LLVMNativePointer.createNull();
        }
    }

    @Specialization(guards = "!(isLLVMLibrary(pointer))")
    protected Object doOp(@SuppressWarnings("unused") LLVMManagedPointer pointer,
                    @SuppressWarnings("unused") LLVMPointer flag,
                    @SuppressWarnings("unused") @Cached() LLVMReadStringNode readStr) {
        return LLVMNativePointer.createNull();
    }

    protected Object getLibrary(LLVMManagedPointer pointer){
        return ((LLVMDLHandler) pointer.getObject()).getLibrary();
    }

    protected boolean isLLVMLibrary(LLVMManagedPointer library) {
        return library.getObject() instanceof LLVMDLHandler;
    }

}
