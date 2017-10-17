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
package com.oracle.truffle.llvm.nodes.memory.literal;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild(value = "address", type = LLVMExpressionNode.class)
public abstract class LLVMStructArrayLiteralNode extends LLVMExpressionNode {

    @Children private final LLVMExpressionNode[] values;
    private final int stride;
    @Child private LLVMMemMoveNode memMove;
    private final Type elementType;

    public LLVMStructArrayLiteralNode(LLVMExpressionNode[] values, LLVMMemMoveNode memMove, int stride, Type elementType) {
        this.values = values;
        this.stride = stride;
        this.memMove = memMove;
        this.elementType = elementType;
    }

    @Specialization
    protected LLVMAddress write(VirtualFrame frame, LLVMGlobalVariable global, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        return writeDouble(frame, globalAccess.getNativeLocation(global));
    }

    @Specialization
    @ExplodeLoop
    protected LLVMAddress writeDouble(VirtualFrame frame, LLVMAddress addr) {
        long currentPtr = addr.getVal();
        for (int i = 0; i < values.length; i++) {
            try {
                LLVMAddress currentValue = values[i].executeLLVMAddress(frame);
                memMove.executeWithTarget(frame, LLVMAddress.fromLong(currentPtr), currentValue, stride);
                currentPtr += stride;
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
        return addr;
    }

    protected boolean noOffset(LLVMTruffleObject o) {
        return o.getOffset() == 0;
    }

    @Specialization(guards = {"noOffset(addr)"})
    @ExplodeLoop
    public Object executeVoid(VirtualFrame frame, LLVMTruffleObject addr) {
        LLVMTruffleObject currentPtr = addr;
        for (int i = 0; i < values.length; i++) {
            LLVMTruffleObject currentValue = (LLVMTruffleObject) values[i].executeGeneric(frame);
            memMove.executeWithTarget(frame, currentPtr, currentValue, stride);
            currentPtr = currentPtr.increment(stride, elementType);
        }
        return addr;
    }

}
