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
package com.oracle.truffle.llvm.nodes.asm;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMAMD64RepNode extends LLVMExpressionNode {
    @Child private LLVMAMD64WriteValueNode writeRCX;
    @Child private LLVMExpressionNode rcx;
    @Child private LoopNode loop;

    public LLVMAMD64RepNode(LLVMAMD64WriteValueNode writeRCX, LLVMExpressionNode rcx, LLVMExpressionNode body) {
        this.writeRCX = writeRCX;
        this.rcx = rcx;
        this.loop = Truffle.getRuntime().createLoopNode(new LLVMAMD64RepLoopNode(body));
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        loop.executeLoop(frame);
        return null;
    }

    private class LLVMAMD64RepLoopNode extends Node implements RepeatingNode {
        @Child private LLVMExpressionNode body;

        LLVMAMD64RepLoopNode(LLVMExpressionNode body) {
            this.body = body;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            try {
                long rcxValue = rcx.executeI64(frame);
                if (rcxValue == 0) {
                    return false;
                } else {
                    body.executeGeneric(frame);
                    writeRCX.execute(frame, rcxValue - 1);
                    return true;
                }
            } catch (UnexpectedResultException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
