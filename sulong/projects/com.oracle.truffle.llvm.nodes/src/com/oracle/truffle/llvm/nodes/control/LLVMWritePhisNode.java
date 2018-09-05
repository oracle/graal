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
package com.oracle.truffle.llvm.nodes.control;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public final class LLVMWritePhisNode extends LLVMStatementNode {

    @Children private final LLVMExpressionNode[] from;
    @Children private final LLVMWriteNode[] writes;

    public LLVMWritePhisNode(LLVMExpressionNode[] from, LLVMWriteNode[] writes) {
        assert from.length > 0 && writes.length > 0;
        this.from = from;
        this.writes = writes;
        assert from.length == writes.length;
    }

    @Override
    public void execute(VirtualFrame frame) {
        // because of dependencies between the values, we need to read all the values before writing
        // any new value
        if (from.length == 1) {
            writes[0].executeWithTarget(frame, from[0].executeGeneric(frame));
        } else {
            Object[] values = readValues(frame);
            writeValues(frame, values);
        }
    }

    @ExplodeLoop
    private Object[] readValues(VirtualFrame frame) {
        Object[] result = new Object[from.length];
        for (int i = 0; i < from.length; i++) {
            result[i] = from[i].executeGeneric(frame);
        }
        return result;
    }

    @ExplodeLoop
    private void writeValues(VirtualFrame frame, Object[] values) {
        for (int i = 0; i < writes.length; i++) {
            writes[i].executeWithTarget(frame, values[i]);
        }
    }
}
