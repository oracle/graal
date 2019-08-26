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
package com.oracle.truffle.llvm.asm.amd64;

import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.runtime.types.Type;

class Argument {
    private final boolean input;
    private final boolean output;
    private final boolean memory;
    private final Type type;
    private final int index;
    private final int inIndex;
    private final int outIndex;
    private final String source;
    private final String register;
    private final boolean anonymous;

    Argument(boolean input, boolean output, boolean memory, boolean anonymous, Type type, int index, int inIndex, int outIndex, String source, String register) {
        this.input = input;
        this.output = output;
        this.memory = memory;
        this.anonymous = anonymous;
        this.type = type;
        this.index = index;
        this.inIndex = inIndex;
        this.outIndex = outIndex;
        this.source = source;
        this.register = register;
    }

    public boolean isInput() {
        return input;
    }

    public boolean isOutput() {
        return output;
    }

    public boolean isMemory() {
        return memory;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public Type getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public int getInIndex() {
        assert isInput();
        return inIndex;
    }

    public int getOutIndex() {
        assert isOutput();
        return outIndex;
    }

    public String getRegister() {
        assert isRegister();
        return register;
    }

    public boolean isRegister() {
        return register != null;
    }

    public LLVMExpressionNode getAddress() {
        assert isMemory();
        if (output) {
            return LLVMArgNodeGen.create(outIndex);
        } else {
            return LLVMArgNodeGen.create(inIndex);
        }
    }

    @Override
    public String toString() {
        return String.format("Argument[IDX=%d,I=%s(%s),O=%s(%s),M=%s,T=%s,R=%s,S=%s]", index, input, inIndex, output, outIndex, memory, type, register, source);
    }
}
