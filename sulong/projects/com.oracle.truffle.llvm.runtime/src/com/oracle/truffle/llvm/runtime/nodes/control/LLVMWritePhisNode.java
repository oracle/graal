/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.control;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public abstract class LLVMWritePhisNode extends LLVMStatementNode {

    @Children private final LLVMExpressionNode[] cycleFrom;
    @Children private final LLVMWriteNode[] cycleWrites;
    @Children private final LLVMWriteNode[] ordinaryWrites;

    public LLVMWritePhisNode(LLVMExpressionNode[] cycleFrom, LLVMWriteNode[] cycleWrites, LLVMWriteNode[] ordinaryWrites) {
        this.ordinaryWrites = ordinaryWrites;
        this.cycleFrom = cycleFrom;
        this.cycleWrites = cycleWrites;
        assert cycleFrom.length == cycleWrites.length;
    }

    @Specialization
    void doWritePhis(VirtualFrame frame) {
        if (cycleFrom.length == 0) {
            writeOrdinaryValues(frame);
        } else {
            // some values need to be kept in temporary storage
            Object[] values = readCycleValues(frame);
            writeOrdinaryValues(frame);
            writeCycleValues(frame, values);
        }
    }

    @ExplodeLoop
    private Object[] readCycleValues(VirtualFrame frame) {
        Object[] result = new Object[cycleFrom.length];
        for (int i = 0; i < cycleFrom.length; i++) {
            result[i] = cycleFrom[i].executeGeneric(frame);
        }
        return result;
    }

    @ExplodeLoop
    private void writeCycleValues(VirtualFrame frame, Object[] values) {
        for (int i = 0; i < cycleWrites.length; i++) {
            cycleWrites[i].executeWithTarget(frame, values[i]);
        }
    }

    @ExplodeLoop
    private void writeOrdinaryValues(VirtualFrame frame) {
        for (int i = 0; i < ordinaryWrites.length; i++) {
            ordinaryWrites[i].execute(frame);
        }
    }
}
