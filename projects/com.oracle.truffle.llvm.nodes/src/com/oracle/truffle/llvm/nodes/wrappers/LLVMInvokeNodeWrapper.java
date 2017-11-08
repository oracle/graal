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
package com.oracle.truffle.llvm.nodes.wrappers;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMInvokeNodeWrapper implements InstrumentableFactory<LLVMInvokeNode> {
    @Override
    public WrapperNode createWrapper(LLVMInvokeNode node, ProbeNode probe) {
        return new LLVMInvokeNodeWrapper0(probe, node);
    }

    private static final class LLVMInvokeNodeWrapper0 extends LLVMInvokeNode implements WrapperNode {

        @Child private ProbeNode probeNode;

        @Child private LLVMInvokeNode delegateNode;

        private LLVMInvokeNodeWrapper0(ProbeNode probeNode, LLVMInvokeNode delegateNode) {
            super(delegateNode.getSourceSection());
            this.probeNode = probeNode;
            this.delegateNode = delegateNode;
        }

        @Override
        public ProbeNode getProbeNode() {
            return probeNode;
        }

        @Override
        public LLVMInvokeNode getDelegateNode() {
            return delegateNode;
        }

        @Override
        public int getSuccessorCount() {
            return delegateNode.getSuccessorCount();
        }

        @Override
        public LLVMExpressionNode getPhiNode(int successorIndex) {
            return delegateNode.getPhiNode(successorIndex);
        }

        @Override
        public int getNormalSuccessor() {
            return delegateNode.getNormalSuccessor();
        }

        @Override
        public int getUnwindSuccessor() {
            return delegateNode.getUnwindSuccessor();
        }

        @Override
        public void execute(VirtualFrame frame) {
            try {
                probeNode.onEnter(frame);
                delegateNode.execute(frame);
                probeNode.onReturnValue(frame, null);
            } catch (Throwable t) {
                probeNode.onReturnExceptional(frame, t);
                throw t;
            }
        }

        @Override
        public void writeResult(VirtualFrame frame, Object value) {
            delegateNode.writeResult(frame, value);
        }

        @Override
        public void writePhis(VirtualFrame frame, int successorIndex) {
            delegateNode.writePhis(frame, successorIndex);
        }
    }
}
