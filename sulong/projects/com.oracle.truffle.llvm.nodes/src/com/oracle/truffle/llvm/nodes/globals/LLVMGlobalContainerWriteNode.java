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
package com.oracle.truffle.llvm.nodes.globals;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

public final class LLVMGlobalContainerWriteNode extends LLVMNode implements LLVMObjectWriteNode {
    @Child private LLVMToNativeNode toNative;
    @CompilationFinal private LLVMMemory memory;

    @Override
    public boolean canAccess(Object obj) {
        return obj instanceof LLVMGlobalContainer;
    }

    @Override
    public void executeWrite(Object obj, long offset, Object value, ForeignToLLVMType type) {
        LLVMGlobalContainer container = (LLVMGlobalContainer) obj;
        if (container.getAddress() == 0) {
            if (offset == 0 && (type == ForeignToLLVMType.POINTER || type == ForeignToLLVMType.I64)) {
                ((LLVMGlobalContainer) obj).set(value);
                return;
            }
            transformToNative(container);
        }
        writeToNative(container, offset, value, type);
    }

    private void transformToNative(LLVMGlobalContainer container) {
        if (toNative == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toNative = insert(LLVMToNativeNode.createToNativeWithTarget());
        }
        container.toNative(toNative);
    }

    private void writeToNative(LLVMGlobalContainer container, long offset, Object value, ForeignToLLVMType type) {
        if (memory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memory = getLLVMMemory();
        }

        long address = container.getAddress() + offset;
        switch (type) {
            case DOUBLE:
                memory.putDouble(address, (double) value);
                break;
            case FLOAT:
                memory.putFloat(address, (float) value);
                break;
            case I1:
                memory.putI1(address, (boolean) value);
                break;
            case I16:
                memory.putI16(address, (short) value);
                break;
            case I32:
                memory.putI32(address, (int) value);
                break;
            case I8:
                memory.putI8(address, (byte) value);
                break;
            case I64:
            case POINTER:
                if (value instanceof Long) {
                    memory.putI64(address, (long) value);
                } else {
                    if (toNative == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        toNative = insert(LLVMToNativeNode.createToNativeWithTarget());
                    }
                    memory.putPointer(address, toNative.executeWithTarget(value));
                }
                break;
            default:
                throw new IllegalStateException("unexpected type " + type);
        }
    }
}
