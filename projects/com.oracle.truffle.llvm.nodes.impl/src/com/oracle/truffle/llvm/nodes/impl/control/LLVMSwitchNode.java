/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.impl.control;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStatementNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;

// TODO remove code duplication
public abstract class LLVMSwitchNode extends LLVMStatementNode {

    private static final int DEFAULT_LABEL_INDEX = 0;
    private static final int CASE_LABEL_START_INDEX = 1;
    @Children final LLVMNode[] phiWriteNodes;

    public LLVMSwitchNode(int defaultLabel, int[] successors, LLVMNode[] phiWriteNodes) {
        super(getLabelArray(successors, defaultLabel));
        this.phiWriteNodes = phiWriteNodes;
    }

    protected static int[] getLabelArray(int[] successors, int defaultLabel) {
        CompilerAsserts.neverPartOfCompilation();
        int[] labels = new int[successors.length + 1];
        labels[DEFAULT_LABEL_INDEX] = defaultLabel;
        System.arraycopy(successors, 0, labels, CASE_LABEL_START_INDEX, successors.length);
        return labels;
    }

    @ExplodeLoop
    void executePhiWrites(VirtualFrame frame) {
        for (int i = 0; i < phiWriteNodes.length; i++) {
            phiWriteNodes[i].executeVoid(frame);
        }
    }

    public static class LLVMI8SwitchNode extends LLVMSwitchNode {

        @Child private LLVMI8Node cond;
        @Children private final LLVMI8Node[] cases;

        public LLVMI8SwitchNode(LLVMI8Node cond, LLVMI8Node[] cases, int[] successors, int defaultLabel, LLVMNode[] phiWriteNodes) {
            super(defaultLabel, successors, phiWriteNodes);
            this.cond = cond;
            this.cases = cases;
        }

        @Override
        @ExplodeLoop
        public int executeGetSuccessorIndex(VirtualFrame frame) {
            int val = cond.executeI8(frame);
            for (int i = 0; i < cases.length; i++) {
                int caseValue = cases[i].executeI8(frame);
                if (val == caseValue) {
                    executePhiWrites(frame);
                    return i + CASE_LABEL_START_INDEX;
                }
            }
            executePhiWrites(frame);
            return DEFAULT_LABEL_INDEX;
        }

    }

    public static class LLVMI32SwitchNode extends LLVMSwitchNode {

        @Child private LLVMI32Node cond;
        @Children private final LLVMI32Node[] cases;

        public LLVMI32SwitchNode(LLVMI32Node cond, LLVMI32Node[] cases, int[] successors, int defaultLabel, LLVMNode[] phiWriteNodes) {
            super(defaultLabel, successors, phiWriteNodes);
            this.cond = cond;
            this.cases = cases;
        }

        @Override
        @ExplodeLoop
        public int executeGetSuccessorIndex(VirtualFrame frame) {
            int val = cond.executeI32(frame);
            for (int i = 0; i < cases.length; i++) {
                int caseValue = cases[i].executeI32(frame);
                if (val == caseValue) {
                    executePhiWrites(frame);
                    return i + CASE_LABEL_START_INDEX;
                }
            }
            executePhiWrites(frame);
            return DEFAULT_LABEL_INDEX;
        }

    }

    public static class LLVMI64SwitchNode extends LLVMSwitchNode {

        @Child private LLVMI64Node cond;
        @Children private final LLVMI64Node[] cases;

        public LLVMI64SwitchNode(LLVMI64Node cond, LLVMI64Node[] cases, int[] successors, int defaultLabel, LLVMNode[] phiWriteNodes) {
            super(defaultLabel, successors, phiWriteNodes);
            this.cond = cond;
            this.cases = cases;
        }

        @Override
        @ExplodeLoop
        public int executeGetSuccessorIndex(VirtualFrame frame) {
            long val = cond.executeI64(frame);
            for (int i = 0; i < cases.length; i++) {
                long caseValue = cases[i].executeI64(frame);
                if (val == caseValue) {
                    executePhiWrites(frame);
                    return i + CASE_LABEL_START_INDEX;
                }
            }
            executePhiWrites(frame);
            return DEFAULT_LABEL_INDEX;
        }

    }

}
