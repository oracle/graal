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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.NodeFields;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeFields({@NodeField(name = "index", type = int.class), @NodeField(name = "fallback", type = Object.class)})
public abstract class LLVMOptionalArgNode extends LLVMExpressionNode {
    private Converter converter;

    public abstract int getIndex();

    public abstract Object getFallback();

    public LLVMOptionalArgNode() {
        converter = new Converter() {
            @Override
            public Object convert(Object o) {
                return o;
            }
        };
    }

    public LLVMOptionalArgNode(Converter converter) {
        this.converter = converter;
    }

    @Specialization(guards = "isAddress(frame)")
    public Object executePointee(VirtualFrame frame) {
        return converter.convert(((LLVMAddress) get(frame)).copy());
    }

    public boolean isAddress(VirtualFrame frame) {
        return get(frame) instanceof LLVMAddress;
    }

    @Specialization(guards = "!isAddress(frame)")
    public Object executeObject(VirtualFrame frame) {
        return converter.convert(get(frame));
    }

    private Object get(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        if (args.length > getIndex()) {
            return args[getIndex()];
        } else {
            return getFallback();
        }
    }

    public interface Converter {
        Object convert(Object o);
    }
}
