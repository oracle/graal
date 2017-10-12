/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeField(type = int.class, name = "structSize")
public abstract class LLVMStructStoreNode extends LLVMStoreNode {

    public abstract int getStructSize();

    @Child private LLVMMemMoveNode memMove;

    protected LLVMStructStoreNode(LLVMMemMoveNode memMove, Type type) {
        super(type, 0);
        this.memMove = memMove;
    }

    @Specialization
    public Object execute(VirtualFrame frame, LLVMGlobalVariable address, LLVMGlobalVariable value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1,
                    @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
        memMove.executeWithTarget(frame, globalAccess1.getNativeLocation(address), globalAccess2.getNativeLocation(value), getStructSize());
        return null;
    }

    @Specialization
    public Object execute(VirtualFrame frame, LLVMAddress address, LLVMGlobalVariable value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        memMove.executeWithTarget(frame, address, globalAccess.getNativeLocation(value), getStructSize());
        return null;
    }

    @Specialization
    public Object execute(VirtualFrame frame, LLVMGlobalVariable address, LLVMAddress value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        memMove.executeWithTarget(frame, globalAccess.getNativeLocation(address), value, getStructSize());
        return null;
    }

    @Specialization
    public Object execute(VirtualFrame frame, LLVMAddress address, LLVMAddress value) {
        memMove.executeWithTarget(frame, address, value, getStructSize());
        return null;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "getStructSize() == 0")
    public Object noCopy(LLVMAddress address, Object value) {
        return null;
    }

    @Specialization
    public Object doTruffleObject(VirtualFrame frame, LLVMTruffleObject address, LLVMTruffleObject value) {
        memMove.executeWithTarget(frame, address, value, getStructSize());
        return null;
    }

}
