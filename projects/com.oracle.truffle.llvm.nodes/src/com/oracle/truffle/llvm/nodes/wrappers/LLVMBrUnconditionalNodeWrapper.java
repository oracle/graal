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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.control.LLVMBrUnconditionalNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMBrUnconditionalNodeWrapper implements InstrumentableFactory<LLVMBrUnconditionalNode> {

    @Override
    public WrapperNode createWrapper(LLVMBrUnconditionalNode node, ProbeNode probe) {
        return new LLVMBrUnconditionalNodeWrapper0(probe, node);
    }

    private static final class LLVMBrUnconditionalNodeWrapper0 extends LLVMBrUnconditionalNode implements WrapperNode {

        @Child private ProbeNode probe;

        @Child private LLVMBrUnconditionalNode delegate;

        LLVMBrUnconditionalNodeWrapper0(ProbeNode probe, LLVMBrUnconditionalNode delegate) {
            super(delegate.getSourceSection());
            this.probe = probe;
            this.delegate = delegate;
        }

        @Override
        public void execute(VirtualFrame frame) {
            try {
                probe.onEnter(frame);
                delegate.execute(frame);
                probe.onReturnValue(frame, null);
            } catch (Throwable t) {
                probe.onReturnExceptional(frame, t);
                throw t;
            }
        }

        @Override
        public int getSuccessorCount() {
            return delegate.getSuccessorCount();
        }

        @Override
        public LLVMExpressionNode getPhiNode(int successorIndex) {
            return delegate.getPhiNode(successorIndex);
        }

        @Override
        public int getSuccessor() {
            return delegate.getSuccessor();
        }

        @Override
        public Node getDelegateNode() {
            return delegate;
        }

        @Override
        public ProbeNode getProbeNode() {
            return probe;
        }
    }
}
