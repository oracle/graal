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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMFrameNullerUtil;

public final class LLVMFrameNullerExpression extends LLVMExpressionNode {

    @Child private LLVMExpressionNode afterExpression;
    @CompilationFinal(dimensions = 1) private final FrameSlot[] frameSlots;

    public LLVMFrameNullerExpression(LLVMExpressionNode afterExpression, FrameSlot[] frameSlots) {
        this.afterExpression = afterExpression;
        this.frameSlots = frameSlots;
    }

    @Override
    public NodeCost getCost() {
        // this node reduces the compile code size
        return NodeCost.NONE;
    }

    @Override
    public String toString() {
        return getShortString("frameSlots");
    }

    @ExplodeLoop
    private void nullSlots(VirtualFrame frame) {
        for (int i = 0; i < frameSlots.length; i++) {
            LLVMFrameNullerUtil.nullFrameSlot(frame, frameSlots[i]);
        }
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        try {
            return afterExpression.executeGeneric(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeDouble(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public float executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeFloat(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public short executeI16(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeI16(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public boolean executeI1(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeI1(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public int executeI32(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeI32(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public long executeI64(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeI64(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public byte executeI8(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeI8(frame);
        } finally {
            nullSlots(frame);
        }
    }
}
