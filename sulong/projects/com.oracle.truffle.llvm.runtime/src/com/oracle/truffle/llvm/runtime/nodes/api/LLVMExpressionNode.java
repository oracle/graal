/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * An expression node is a node that returns a result, e.g., a local variable read, or an addition
 * operation.
 */
@GenerateWrapper
public abstract class LLVMExpressionNode extends LLVMInstrumentableNode {

    @SuppressWarnings("static-method")
    @GenerateWrapper.OutgoingConverter
    final Object convertOutgoing(@SuppressWarnings("unused") Object object) {
        return null;
    }

    public static final LLVMExpressionNode[] NO_EXPRESSIONS = {};

    public abstract Object executeGeneric(VirtualFrame frame);

    public final LLVMPointer executeLLVMPointer(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectLLVMPointer(executeGeneric(frame));
    }

    public final LLVMNativePointer executeLLVMNativePointer(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectLLVMNativePointer(executeGeneric(frame));
    }

    public final LLVMManagedPointer executeLLVMManagedPointer(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectLLVMManagedPointer(executeGeneric(frame));
    }

    public double executeDouble(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectDouble(executeGeneric(frame));
    }

    public float executeFloat(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectFloat(executeGeneric(frame));
    }

    public short executeI16(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectShort(executeGeneric(frame));
    }

    public boolean executeI1(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectBoolean(executeGeneric(frame));
    }

    public int executeI32(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectInteger(executeGeneric(frame));
    }

    public long executeI64(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectLong(executeGeneric(frame));
    }

    public byte executeI8(VirtualFrame frame) throws UnexpectedResultException {
        return LLVMTypesGen.expectByte(executeGeneric(frame));
    }

    public final String getSourceDescription() {
        return getRootNode().getName();
    }

    @Override
    public final WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMExpressionNodeWrapper(this, probe);
    }
}
