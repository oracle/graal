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

import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtract80BitFloatValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractAddressValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractDoubleValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractFloatValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI16ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI1ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI32ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI64ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI8ValueNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;

public final class LLVMAggregateFactory {

    private LLVMAggregateFactory() {
    }

    public static LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMAddressNode targetAddress) {
        switch (type) {
            case I1:
                return LLVMExtractI1ValueNodeGen.create(targetAddress);
            case I8:
                return LLVMExtractI8ValueNodeGen.create(targetAddress);
            case I16:
                return LLVMExtractI16ValueNodeGen.create(targetAddress);
            case I32:
                return LLVMExtractI32ValueNodeGen.create(targetAddress);
            case I64:
                return LLVMExtractI64ValueNodeGen.create(targetAddress);
            case FLOAT:
                return LLVMExtractFloatValueNodeGen.create(targetAddress);
            case DOUBLE:
                return LLVMExtractDoubleValueNodeGen.create(targetAddress);
            case X86_FP80:
                return LLVMExtract80BitFloatValueNodeGen.create(targetAddress);
            case ADDRESS:
            case STRUCT:
                return LLVMExtractAddressValueNodeGen.create(targetAddress);
            default:
                throw new AssertionError(type);
        }
    }

}
