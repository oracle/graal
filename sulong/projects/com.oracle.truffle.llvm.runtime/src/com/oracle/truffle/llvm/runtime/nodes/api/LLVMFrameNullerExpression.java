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
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;

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
    public LLVM80BitFloat executeLLVM80BitFloat(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVM80BitFloat(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMPointer executeLLVMPointer(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMPointer(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMNativePointer executeLLVMNativePointer(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMNativePointer(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMManagedPointer executeLLVMManagedPointer(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMManagedPointer(frame);
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
    public LLVMIVarBit executeLLVMIVarBit(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMIVarBit(frame);
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

    @Override
    public LLVMI8Vector executeLLVMI8Vector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMI8Vector(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMI64Vector executeLLVMI64Vector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMI64Vector(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMI32Vector executeLLVMI32Vector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMI32Vector(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMI1Vector executeLLVMI1Vector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMI1Vector(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMI16Vector executeLLVMI16Vector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMI16Vector(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMFloatVector executeLLVMFloatVector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMFloatVector(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMDoubleVector executeLLVMDoubleVector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMDoubleVector(frame);
        } finally {
            nullSlots(frame);
        }
    }

    @Override
    public LLVMPointerVector executeLLVMPointerVector(VirtualFrame frame) throws UnexpectedResultException {
        try {
            return afterExpression.executeLLVMPointerVector(frame);
        } finally {
            nullSlots(frame);
        }
    }
}
