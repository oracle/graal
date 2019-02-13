/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMToDebugDeclarationNode extends LLVMNode implements LLVMDebugValue.Builder {

    @Child protected Node isPointer = Message.IS_POINTER.createNode();
    @Child protected Node asPointer = Message.AS_POINTER.createNode();

    protected static boolean isPointer(Object object) {
        return LLVMPointer.isInstance(object);
    }

    public abstract LLVMDebugValue executeWithTarget(Object value);

    @Override
    public LLVMDebugValue build(Object irValue) {
        return executeWithTarget(irValue);
    }

    @Specialization
    protected LLVMDebugValue fromPointer(LLVMPointer address) {
        return new LLDBMemoryValue(address);
    }

    @Specialization
    protected LLVMDebugValue fromBoxedPrimitive(LLVMBoxedPrimitive boxedPrimitive) {
        if (boxedPrimitive.getValue() instanceof Long) {
            return fromPointer(LLVMNativePointer.create((long) boxedPrimitive.getValue()));
        } else {
            return fromGenericObject(boxedPrimitive);
        }
    }

    @Specialization(guards = "!isPointer(obj)")
    protected LLVMDebugValue fromTruffleObject(TruffleObject obj) {
        try {
            if (ForeignAccess.sendIsPointer(isPointer, obj)) {
                final long rawAddress = ForeignAccess.sendAsPointer(asPointer, obj);
                return fromPointer(LLVMNativePointer.create(rawAddress));
            }
        } catch (UnsupportedMessageException ignored) {
            CompilerDirectives.transferToInterpreter();
        }
        return fromGenericObject(obj);
    }

    @Fallback
    protected LLVMDebugValue fromGenericObject(@SuppressWarnings("unused") Object object) {
        // @llvm.dbg.declare is supposed to tell us the location of the variable in memory, there
        // should never be a case where this cannot be resolved to a pointer. If it happens anyhow
        // this is a safe default.
        return LLVMDebugValue.UNAVAILABLE;
    }
}
