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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.ToLLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
@NodeField(name = "type", type = Type.class)
public abstract class LLVMToAddressNode extends LLVMExpressionNode {

    public abstract Type getType();

    @Specialization
    public LLVMAddress executeI1(boolean from) {
        return LLVMAddress.fromLong(from ? 1 : 0);
    }

    @Specialization
    public LLVMAddress executeI8(byte from) {
        return LLVMAddress.fromLong(from);
    }

    @Specialization
    public LLVMAddress executeI64(long from) {
        return LLVMAddress.fromLong(from);
    }

    @Specialization
    public LLVMAddress executeI64(LLVMFunctionDescriptor from) {
        return LLVMAddress.fromLong(from.getFunctionIndex());
    }

    @Specialization
    public LLVMAddress executeI64(LLVMFunctionHandle from) {
        return LLVMAddress.fromLong(from.getFunctionIndex());
    }

    @Specialization
    public LLVMAddress executeLLVMAddress(LLVMAddress from) {
        return from;
    }

    @Child private ToLLVMNode toLong = ToLLVMNode.createNode(long.class);

    @Specialization
    public LLVMAddress executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return LLVMAddress.fromLong((long) toLong.executeWithTarget(from.getValue()));
    }

    protected static boolean checkIsPointer(Node isPointer, TruffleObject object) {
        return ForeignAccess.sendIsPointer(isPointer, object);
    }

    protected static Node createIsPointer() {
        return Message.IS_POINTER.createNode();
    }

    protected static Node createAsPointer() {
        return Message.AS_POINTER.createNode();
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"checkIsPointer(isPointer, obj)", "notLLVM(obj)"})
    public LLVMAddress fromNativePointer(TruffleObject obj, @Cached("createIsPointer()") Node isPointer, @Cached("createAsPointer()") Node asPointer) {
        try {
            long raw = ForeignAccess.sendAsPointer(asPointer, obj);
            return LLVMAddress.fromLong(raw);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!checkIsPointer(isPointer, from)", "notLLVM(from)"})
    public LLVMTruffleObject executeTruffleObject(TruffleObject from, @Cached("createIsPointer()") Node isPointer) {
        return new LLVMTruffleObject(from, getType());
    }
}
