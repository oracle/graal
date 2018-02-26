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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMToNativeNode extends LLVMNode {

    public abstract LLVMAddress execute(VirtualFrame frame);

    public abstract LLVMAddress executeWithTarget(VirtualFrame frame, Object object);

    public static LLVMToNativeNode createToNativeWithTarget() {
        return LLVMToNativeNodeGen.create(null);
    }

    @Specialization
    protected LLVMAddress doLongCase(long a) {
        return LLVMAddress.fromLong(a);
    }

    @Specialization
    protected LLVMAddress doAddressCase(LLVMAddress a) {
        return a;
    }

    @Specialization
    protected LLVMAddress doLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        if (from.getValue() instanceof Long) {
            return LLVMAddress.fromLong((long) from.getValue());
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError(String.format("Cannot convert a primitive value (type: %s, value: %s) to an LLVMAddress).", String.valueOf(from.getValue().getClass()),
                            String.valueOf(from.getValue())));
        }
    }

    @Specialization(guards = {"lib.guard(pointer)", "lib.isPointer(frame, pointer)"})
    protected LLVMAddress handlePointerCached(VirtualFrame frame, Object pointer,
                    @Cached("createCached(pointer)") LLVMObjectNativeLibrary lib) {
        return handlePointer(frame, pointer, lib);
    }

    @Specialization(replaces = "handlePointerCached", guards = {"lib.guard(pointer)", "lib.isPointer(frame, pointer)"})
    protected LLVMAddress handlePointer(VirtualFrame frame, Object pointer,
                    @Cached("createGeneric()") LLVMObjectNativeLibrary lib) {
        try {
            return LLVMAddress.fromLong(lib.asPointer(frame, pointer));
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot convert " + pointer + " to LLVMAddress", e);
        }
    }

    @Specialization(replaces = {"handlePointer", "handlePointerCached"}, guards = {"lib.guard(pointer)"})
    protected LLVMAddress transitionToNative(VirtualFrame frame, Object pointer,
                    @Cached("createGeneric()") LLVMObjectNativeLibrary lib) {
        try {
            Object n = lib.toNative(frame, pointer);
            return LLVMAddress.fromLong(lib.asPointer(frame, n));
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot convert " + pointer + " to LLVMAddress", e);
        }
    }
}
