/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * This node creates an instance of a platform specific managed va_list object. The instantiation is
 * delegated to
 * {@link PlatformCapability#createVAListStorage(RootNode, com.oracle.truffle.llvm.runtime.pointer.LLVMPointer)})
 * so that this class remains platform independent. This node is appended to the AST when
 * {@link NodeFactory#createAlloca(Type, int)} is called. That method is the place where the request
 * to allocate a <code>va_list</code> variable on the stack is intercepted by comparing the type
 * argument with the predefined platform specific <code>va_list</code> type (obtained via
 * {@link PlatformCapability#getVAListType()}).
 */
public abstract class LLVMVAListNode extends LLVMExpressionNode {

    protected LLVMVAListNode() {
    }

    LLVMExpressionNode createAllocaNode() {
        DataLayout dataLayout = getDataLayout();
        LLVMLanguage language = LLVMLanguage.get(null);
        PlatformCapability<?> capability = language.getCapability(PlatformCapability.class);
        return language.getActiveConfiguration().createNodeFactory(language, dataLayout).createAlloca(capability.getVAListType(), capability.getVAListAlignment());
    }

    @Specialization
    public LLVMManagedPointer createVAList(VirtualFrame frame,
                    @Cached("createAllocaNode()") LLVMExpressionNode allocaNode) {
        // allocaNode == null indicates that no native stack is supported
        LLVMNativePointer vaListNativeStackPtr = allocaNode == null ? LLVMNativePointer.createNull() : LLVMNativePointer.cast(allocaNode.executeGeneric(frame));
        Object vaListStorage = LLVMLanguage.get(this).getCapability(PlatformCapability.class).createVAListStorage(getRootNode(), vaListNativeStackPtr);
        return LLVMManagedPointer.create(vaListStorage);
    }

}
