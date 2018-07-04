/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.types.Type;

class AsmImmediateOperand implements AsmOperand {
    private final String val;
    private final long ival;
    private final boolean label;

    AsmImmediateOperand(long value) {
        this.val = null;
        this.ival = value;
        this.label = false;
    }

    AsmImmediateOperand(String value) {
        this.val = value;
        this.ival = 0;
        this.label = true;
    }

    public String getLabel() {
        if (!isLabel()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("not a label!");
        }
        return val;
    }

    public long getValue() {
        if (isLabel()) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("is a label!");
        }
        return ival;
    }

    public boolean isLabel() {
        return label;
    }

    @Override
    public Type getType() {
        return null;
    }

    @Override
    public String toString() {
        return isLabel() ? getLabel() : Long.toUnsignedString(getValue());
    }
}
