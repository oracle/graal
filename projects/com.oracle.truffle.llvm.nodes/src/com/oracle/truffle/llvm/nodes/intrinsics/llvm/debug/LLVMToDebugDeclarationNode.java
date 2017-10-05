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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMSharedGlobalVariable;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public abstract class LLVMToDebugDeclarationNode extends LLVMNode implements LLVMDebugValueProvider.Builder {

    @Child protected Node isPointer = Message.IS_POINTER.createNode();
    @Child protected Node asPointer = Message.AS_POINTER.createNode();

    protected static boolean notLLVM(TruffleObject object) {
        return LLVMExpressionNode.notLLVM(object);
    }

    private static LLVMDebugValueProvider unavailable() {
        // @llvm.dbg.declare is supposed to tell us the location of the variable in memory, there
        // should never be a case where this cannot be resolved to a pointer. If it happens anyhow
        // this is a safe default.
        return LLVMUnavailableDebugValueProvider.INSTANCE;
    }

    public abstract LLVMDebugValueProvider executeWithTarget(Object value);

    @Override
    public LLVMDebugValueProvider build(Object irValue) {
        return executeWithTarget(irValue);
    }

    @Specialization
    public LLVMDebugValueProvider fromAddress(LLVMAddress address) {
        return new LLVMAllocationValueProvider(address);
    }

    @Specialization
    public LLVMDebugValueProvider fromTruffleAddress(LLVMTruffleAddress truffleAddress) {
        return new LLVMAllocationValueProvider(truffleAddress.getAddress());
    }

    @Specialization
    public LLVMDebugValueProvider fromBoxedPrimitive(LLVMBoxedPrimitive boxedPrimitive) {
        if (boxedPrimitive.getValue() instanceof Long) {
            return fromAddress(LLVMAddress.fromLong((long) boxedPrimitive.getValue()));
        } else {
            return unavailable();
        }
    }

    @Specialization
    public LLVMDebugValueProvider fromGlobal(LLVMGlobalVariable value) {
        return new LLVMConstantGlobalValueProvider(value, LLVMToDebugValueNodeGen.create());
    }

    @Specialization
    public LLVMDebugValueProvider fromSharedGlobal(LLVMSharedGlobalVariable value) {
        return fromGlobal(value.getDescriptor());
    }

    @Specialization(guards = "notLLVM(obj)")
    public LLVMDebugValueProvider fromTruffleObject(TruffleObject obj) {
        try {
            if (ForeignAccess.sendIsPointer(isPointer, obj)) {
                final long rawAddress = ForeignAccess.sendAsPointer(asPointer, obj);
                return fromAddress(LLVMAddress.fromLong(rawAddress));
            }
        } catch (UnsupportedMessageException ignored) {
        }
        return unavailable();
    }

    @Specialization(guards = {"obj.getOffset() == 0", "notLLVM(obj.getObject())"})
    public LLVMDebugValueProvider fromLLVMTruffleObject(LLVMTruffleObject obj) {
        return fromTruffleObject(obj.getObject());
    }

    @Specialization
    public LLVMDebugValueProvider fromGenericObject(@SuppressWarnings("unused") Object object) {
        return unavailable();
    }
}
