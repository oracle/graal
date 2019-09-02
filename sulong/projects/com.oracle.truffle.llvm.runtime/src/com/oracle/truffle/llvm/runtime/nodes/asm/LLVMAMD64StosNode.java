/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "rax", type = LLVMExpressionNode.class)
@NodeChild(value = "rdi", type = LLVMExpressionNode.class)
@NodeChild(value = "df", type = LLVMExpressionNode.class)
public abstract class LLVMAMD64StosNode extends LLVMStatementNode {
    @Child protected LLVMStoreNode store;
    @Child protected LLVMAMD64WriteValueNode writeRDI;

    public LLVMAMD64StosNode(LLVMAMD64WriteValueNode writeRDI) {
        this.writeRDI = writeRDI;
    }

    public abstract static class LLVMAMD64StosbNode extends LLVMAMD64StosNode {
        public LLVMAMD64StosbNode(LLVMAMD64WriteValueNode writeRDI) {
            super(writeRDI);
            store = LLVMI8StoreNodeGen.create(null, null);
        }

        @Specialization
        protected void opI8(VirtualFrame frame, byte al, long rdi, boolean df) {
            store.executeWithTarget(LLVMNativePointer.create(rdi), al);
            writeRDI.execute(frame, rdi + (df ? -1 : 1));
        }

        @Specialization
        protected void opI8(VirtualFrame frame, byte al, LLVMPointer rdi, boolean df) {
            store.executeWithTarget(rdi, al);
            writeRDI.execute(frame, rdi.increment(df ? -1 : 1));
        }
    }

    public abstract static class LLVMAMD64StoswNode extends LLVMAMD64StosNode {
        public LLVMAMD64StoswNode(LLVMAMD64WriteValueNode writeRDI) {
            super(writeRDI);
            store = LLVMI16StoreNodeGen.create(null, null);
        }

        @Specialization
        protected void opI8(VirtualFrame frame, short al, long rdi, boolean df) {
            store.executeWithTarget(LLVMNativePointer.create(rdi), al);
            writeRDI.execute(frame, rdi + (df ? -2 : 2));
        }

        @Specialization
        protected void opI8(VirtualFrame frame, short al, LLVMPointer rdi, boolean df) {
            store.executeWithTarget(rdi, al);
            writeRDI.execute(frame, rdi.increment(df ? -2 : 2));
        }
    }

    public abstract static class LLVMAMD64StosdNode extends LLVMAMD64StosNode {
        public LLVMAMD64StosdNode(LLVMAMD64WriteValueNode writeRDI) {
            super(writeRDI);
            store = LLVMI32StoreNodeGen.create(null, null);
        }

        @Specialization
        protected void opI8(VirtualFrame frame, int al, long rdi, boolean df) {
            store.executeWithTarget(LLVMNativePointer.create(rdi), al);
            writeRDI.execute(frame, rdi + (df ? -4 : 4));
        }

        @Specialization
        protected void opI8(VirtualFrame frame, int al, LLVMPointer rdi, boolean df) {
            store.executeWithTarget(rdi, al);
            writeRDI.execute(frame, rdi.increment(df ? -4 : 4));
        }
    }

    public abstract static class LLVMAMD64StosqNode extends LLVMAMD64StosNode {
        public LLVMAMD64StosqNode(LLVMAMD64WriteValueNode writeRDI) {
            super(writeRDI);
            store = LLVMI64StoreNodeGen.create(null, null);
        }

        @Specialization
        protected void opI8(VirtualFrame frame, long al, long rdi, boolean df) {
            store.executeWithTarget(LLVMNativePointer.create(rdi), al);
            writeRDI.execute(frame, rdi + (df ? -8 : 8));
        }

        @Specialization
        protected void opI8(VirtualFrame frame, long al, LLVMPointer rdi, boolean df) {
            store.executeWithTarget(rdi, al);
            writeRDI.execute(frame, rdi.increment(df ? -8 : 8));
        }
    }
}
