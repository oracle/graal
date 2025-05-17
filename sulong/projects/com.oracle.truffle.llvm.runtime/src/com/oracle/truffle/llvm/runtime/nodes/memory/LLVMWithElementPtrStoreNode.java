/*
 * Copyright (c) 2025, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.interop.LLVMNegatedForeignObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "base", type = LLVMExpressionNode.class)
@NodeChild(value = "offset", type = LLVMExpressionNode.class)
public abstract class LLVMWithElementPtrStoreNode extends LLVMStatementNode {

    private final long typeWidth;

    @Child private LLVMOffsetStoreNode store;

    public LLVMWithElementPtrStoreNode(LLVMOffsetStoreNode store, long typeWidth) {
        this.store = store;
        this.typeWidth = typeWidth;
    }

    protected static boolean isNegated(Object obj, Object negatedObj) {
        if (negatedObj instanceof LLVMNegatedForeignObject) {
            return ((LLVMNegatedForeignObject) negatedObj).getForeign() == obj;
        } else {
            return false;
        }
    }

    @Specialization(guards = "isNegated(addr.getObject(), element.getObject())")
    protected void doPointerDiff(VirtualFrame frame, LLVMManagedPointer addr, LLVMManagedPointer element) {
        store.executeWithTarget(frame, LLVMNativePointer.create(addr.getOffset() + element.getOffset()), 0);
    }

    @Specialization(guards = "isNegated(element.getObject(), addr.getObject())")
    protected void doPointerDiffRev(VirtualFrame frame, LLVMManagedPointer addr, LLVMManagedPointer element) {
        store.executeWithTarget(frame, LLVMNativePointer.create(element.getOffset() + addr.getOffset()), 0);
    }

    @Specialization
    protected void doInt(VirtualFrame frame, LLVMPointer addr, int element) {
        store.executeWithTarget(frame, addr, typeWidth * element);
    }

    @Specialization
    protected void doLong(VirtualFrame frame, LLVMPointer addr, long element) {
        store.executeWithTarget(frame, addr, typeWidth * element);
    }

    @Specialization
    protected void doNativePointer(VirtualFrame frame, LLVMPointer addr, LLVMNativePointer element) {
        store.executeWithTarget(frame, addr, typeWidth * element.asNative());
    }
}
