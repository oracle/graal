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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNative.LLVMObjectAsPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNative.LLVMObjectIsPointerNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNative.LLVMObjectToNativeNode;

public abstract class LLVMForceLLVMAddressNode extends Node {

    public abstract LLVMAddress executeWithTarget(VirtualFrame frame, Object object);

    @Specialization
    public LLVMAddress doAddressCase(LLVMAddress a) {
        return a;
    }

    @Specialization
    public LLVMAddress doAddressCase(LLVMGlobalVariable a, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        return globalAccess.getNativeLocation(a);
    }

    @Specialization
    public LLVMAddress executeLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        if (from.getValue() instanceof Long) {
            return LLVMAddress.fromLong((long) from.getValue());
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError(String.format("Cannot convert a primitive value (type: %s, value: %s) to an LLVMAddress).", String.valueOf(from.getValue().getClass()),
                            String.valueOf(from.getValue())));
        }
    }

    @Child private Node isNull = Message.IS_NULL.createNode();

    protected LLVMObjectToNativeNode createLLVMObjectToNativeNode() {
        return LLVMObjectNativeFactory.createToNative();
    }

    protected LLVMObjectIsPointerNode createLLVMObjectIsPointerNode() {
        return LLVMObjectNativeFactory.createIsPointer();
    }

    protected LLVMObjectAsPointerNode createLLVMObjectAsPointerNode() {
        return LLVMObjectNativeFactory.createAsPointer();
    }

    @Specialization(guards = "isNull(pointer.getObject())")
    LLVMAddress handleIsNull(LLVMTruffleObject pointer) {
        LLVMAddress base = LLVMAddress.nullPointer();
        return base.increment(pointer.getOffset());
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isPointer(frame, isPointer, pointer.getObject())")
    LLVMAddress handlePointer(VirtualFrame frame, LLVMTruffleObject pointer,
                    @Cached("createLLVMObjectIsPointerNode()") LLVMObjectIsPointerNode isPointer, @Cached("createLLVMObjectAsPointerNode()") LLVMObjectAsPointerNode asPointer) {
        try {
            TruffleObject object = pointer.getObject();
            LLVMAddress base = LLVMAddress.fromLong(asPointer.executeAsPointer(frame, object));
            return base.increment(pointer.getOffset());
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot convert " + pointer + " to LLVMAddress", e);
        }
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isNull(pointer.getObject())", "!isPointer(frame, isPointer, pointer.getObject())"})
    LLVMAddress generic(VirtualFrame frame, LLVMTruffleObject pointer, @Cached("createLLVMObjectToNativeNode()") LLVMObjectToNativeNode toNative,
                    @Cached("createLLVMObjectIsPointerNode()") LLVMObjectIsPointerNode isPointer, @Cached("createLLVMObjectAsPointerNode()") LLVMObjectAsPointerNode asPointer) {
        TruffleObject object = pointer.getObject();
        try {
            Object o = toNative.executeToNative(frame, object);
            LLVMAddress base = LLVMAddress.fromLong(asPointer.executeAsPointer(frame, o));
            return base.increment(pointer.getOffset());
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot convert " + pointer + " to LLVMAddress", e);
        }
    }

    protected boolean isPointer(VirtualFrame frame, LLVMObjectIsPointerNode isPointer, TruffleObject object) {
        return isPointer.executeIsPointer(frame, object);
    }

    protected boolean isNull(TruffleObject object) {
        return ForeignAccess.sendIsNull(isNull, object);
    }

    protected static final LLVMGlobalVariableAccess createGlobalAccess() {
        return new LLVMGlobalVariableAccess();
    }

}
