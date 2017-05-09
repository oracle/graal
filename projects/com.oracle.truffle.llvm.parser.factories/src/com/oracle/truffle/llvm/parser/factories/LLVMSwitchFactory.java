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
package com.oracle.truffle.llvm.parser.factories;

import java.util.Arrays;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI16SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI32SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI64SwitchNode;
import com.oracle.truffle.llvm.nodes.control.LLVMSwitchNode.LLVMI8SwitchNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

final class LLVMSwitchFactory {

    static LLVMControlFlowNode createSwitch(LLVMExpressionNode cond, int[] successors, LLVMExpressionNode[] cases, PrimitiveType llvmType, LLVMExpressionNode[][] phiWriteNodes, SourceSection source) {
        switch (llvmType.getPrimitiveKind()) {
            case I8:
                LLVMExpressionNode[] i8Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI8SwitchNode(cond, i8Cases, successors, phiWriteNodes, source);
            case I16:
                LLVMExpressionNode[] i16Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI16SwitchNode(cond, i16Cases, successors, phiWriteNodes, source);
            case I32:
                LLVMExpressionNode[] i32Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI32SwitchNode(cond, i32Cases, successors, phiWriteNodes, source);
            case I64:
                LLVMExpressionNode[] i64Cases = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
                return new LLVMI64SwitchNode(cond, i64Cases, successors, phiWriteNodes, source);
            default:
                throw new AssertionError(llvmType);
        }
    }

}
