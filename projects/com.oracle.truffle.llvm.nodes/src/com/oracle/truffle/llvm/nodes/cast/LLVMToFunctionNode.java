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
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMTruffleNull;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToFunctionNode extends LLVMExpressionNode {

    @Specialization
    public LLVMFunction executeI64(long from) {
        return new LLVMFunctionHandle((int) from);
    }

    @Specialization
    public LLVMFunction executeI64(LLVMAddress from) {
        return new LLVMFunctionHandle((int) from.getVal());
    }

    @Specialization
    public LLVMFunction executeI32(@SuppressWarnings("unused") LLVMTruffleNull from) {
        return new LLVMFunctionHandle(0);
    }

    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();

    @Specialization(guards = "notLLVM(from)")
    public TruffleObject executeTruffleObject(TruffleObject from) {
        if (ForeignAccess.sendIsExecutable(isExecutable, from)) {
            return from;
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("Not a function");
    }

}
