/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.nodes.cast.LLVMToAddressNode;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64Node.LLVMBitcastToI64Node;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalContainer;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess.LLVMObjectReadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;

public final class LLVMGlobalContainerReadNode extends LLVMNode implements LLVMObjectReadNode {

    @Child private LLVMToNativeNode toNative;
    @Child private LLVMToAddressNode toPointer;
    @Child private LLVMBitcastToI64Node toI64;
    @CompilationFinal private LLVMMemory memory;

    @Override
    public boolean canAccess(Object obj) {
        return obj instanceof LLVMGlobalContainer;
    }

    @Override
    public Object executeRead(Object obj, long offset, ForeignToLLVMType type) {
        LLVMGlobalContainer container = (LLVMGlobalContainer) obj;
        if (container.getAddress() == 0) {
            if (offset == 0 && (type == ForeignToLLVMType.POINTER || type == ForeignToLLVMType.I64)) {
                Object result = ((LLVMGlobalContainer) obj).get();
                if (type == ForeignToLLVMType.I64) {
                    return convertToI64(result);
                } else {
                    assert type == ForeignToLLVMType.POINTER;
                    return convertToPointer(result);
                }
            }
            transformToNative(container);
        }
        return readFromNative(container, offset, type);
    }

    public Object convertToPointer(Object result) {
        if (toPointer == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toPointer = (LLVMToAddressNode) insert(getNodeFactory().createBitcast(null, PointerType.VOID, null));
        }
        return toPointer.executeWithTarget(result);
    }

    public Object convertToI64(Object result) {
        if (toI64 == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toI64 = (LLVMBitcastToI64Node) insert(getNodeFactory().createBitcast(null, PrimitiveKind.I64));
        }
        return toI64.executeWithTarget(result);
    }

    private void transformToNative(LLVMGlobalContainer container) {
        if (toNative == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toNative = insert(LLVMToNativeNode.createToNativeWithTarget());
        }
        container.toNative(toNative);
    }

    private Object readFromNative(LLVMGlobalContainer container, long offset, ForeignToLLVMType type) {
        if (memory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memory = getLLVMMemory();
        }

        long address = container.getAddress() + offset;
        switch (type) {
            case DOUBLE:
                return memory.getDouble(address);
            case FLOAT:
                return memory.getFloat(address);
            case I1:
                return memory.getI1(address);
            case I16:
                return memory.getI16(address);
            case I32:
                return memory.getI32(address);
            case I64:
                return memory.getI64(address);
            case I8:
                return memory.getI8(address);
            case POINTER:
                return memory.getPointer(address);
            default:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("unexpected type " + type);
        }
    }
}
