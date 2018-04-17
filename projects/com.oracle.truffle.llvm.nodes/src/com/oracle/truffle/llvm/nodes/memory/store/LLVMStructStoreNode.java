/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

@NodeField(type = long.class, name = "structSize")
public abstract class LLVMStructStoreNode extends LLVMStoreNodeCommon {

    public abstract long getStructSize();

    @Child private LLVMMemMoveNode memMove;

    protected LLVMStructStoreNode(LLVMMemMoveNode memMove) {
        this(null, memMove);
    }

    protected LLVMStructStoreNode(LLVMSourceLocation sourceLocation, LLVMMemMoveNode memMove) {
        super(sourceLocation);
        this.memMove = memMove;
    }

    @Specialization
    protected Object doOp(LLVMGlobal address, LLVMGlobal value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess1,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess2) {
        memMove.executeWithTarget(globalAccess1.executeWithTarget(address), globalAccess2.executeWithTarget(value), getStructSize());
        return null;
    }

    @Specialization
    protected Object doOp(LLVMAddress addr, LLVMGlobal value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        memMove.executeWithTarget(addr, globalAccess.executeWithTarget(value), getStructSize());
        return null;
    }

    @Specialization
    protected Object doOp(LLVMGlobal address, LLVMAddress value,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        memMove.executeWithTarget(globalAccess.executeWithTarget(address), value, getStructSize());
        return null;
    }

    @Specialization(guards = {"!isAutoDerefHandle(address)", "!isAutoDerefHandle(value)"})
    protected Object doOp(LLVMAddress address, LLVMAddress value) {
        memMove.executeWithTarget(address, value, getStructSize());
        return null;
    }

    @Specialization(guards = {"isAutoDerefHandle(addr)", "isAutoDerefHandle(value)"})
    protected Object doOpDerefHandle(LLVMAddress addr, LLVMAddress value) {
        return doTruffleObject(getDerefHandleGetReceiverNode().execute(addr), getDerefHandleGetReceiverNode().execute(value));
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "getStructSize() == 0")
    protected Object noCopy(LLVMAddress address, Object value) {
        return null;
    }

    @Specialization
    protected Object doTruffleObject(LLVMTruffleObject address, LLVMTruffleObject value) {
        memMove.executeWithTarget(address, value, getStructSize());
        return null;
    }
}
