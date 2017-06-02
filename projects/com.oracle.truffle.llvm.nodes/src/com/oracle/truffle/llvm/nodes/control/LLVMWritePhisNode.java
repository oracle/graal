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
package com.oracle.truffle.llvm.nodes.control;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMWritePhisNode extends LLVMExpressionNode {

    @Children private final LLVMExpressionNode[] from;
    @CompilationFinal(dimensions = 1) private final FrameSlot[] to;
    @CompilationFinal(dimensions = 1) private final Type[] types;

    public LLVMWritePhisNode(LLVMExpressionNode[] from, FrameSlot[] to, Type[] types) {
        this.from = from;
        this.to = to;
        this.types = types;
        assert types.length == from.length;
        assert from.length == to.length;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object[] values = readValues(frame);
        writeValues(frame, values);
        return null;
    }

    @ExplodeLoop
    private Object[] readValues(VirtualFrame frame) {
        Object[] result = new Object[from.length];
        for (int i = 0; i < from.length; i++) {
            result[i] = from[i].executeGeneric(frame);
        }
        return result;
    }

    @ExplodeLoop
    private void writeValues(VirtualFrame frame, Object[] values) {
        for (int i = 0; i < to.length; i++) {
            CompilerAsserts.partialEvaluationConstant(types[i]);
            if (types[i] instanceof PrimitiveType) {
                PrimitiveKind primitiveKind = ((PrimitiveType) types[i]).getPrimitiveKind();
                CompilerAsserts.partialEvaluationConstant(types[i]);
                switch (primitiveKind) {
                    case I1:
                        frame.setBoolean(to[i], (boolean) values[i]);
                        break;
                    case I8:
                        frame.setByte(to[i], (byte) values[i]);
                        break;
                    case I16:
                        frame.setInt(to[i], (short) values[i]);
                        break;
                    case I32:
                        frame.setInt(to[i], (int) values[i]);
                        break;
                    case I64:
                        frame.setLong(to[i], (long) values[i]);
                        break;
                    case FLOAT:
                        frame.setFloat(to[i], (float) values[i]);
                        break;
                    case DOUBLE:
                        frame.setDouble(to[i], (double) values[i]);
                        break;
                    case X86_FP80:
                        frame.setObject(to[i], values[i]);
                        break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw new AssertionError(primitiveKind);
                }
            } else {
                frame.setObject(to[i], values[i]);
            }
        }
    }

}
