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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNodeGen.LLVMObjectToNativeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMToNativeNode extends LLVMNode {

    public abstract LLVMNativePointer execute(VirtualFrame frame);

    public abstract LLVMNativePointer executeWithTarget(Object object);

    public static LLVMToNativeNode createToNativeWithTarget() {
        return LLVMToNativeNodeGen.create(null);
    }

    @Specialization
    protected LLVMNativePointer doLongCase(long a) {
        return LLVMNativePointer.create(a);
    }

    @Specialization
    protected LLVMNativePointer doAddressCase(LLVMNativePointer a) {
        return a;
    }

    @Specialization
    protected LLVMNativePointer doLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        if (from.getValue() instanceof Long) {
            return LLVMNativePointer.create((long) from.getValue());
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError(String.format("Cannot convert a primitive value (type: %s, value: %s) to an LLVMNativePointer).", String.valueOf(from.getValue().getClass()),
                            String.valueOf(from.getValue())));
        }
    }

    // this is a workaround because @Fallback does not support @Cached
    @Specialization(guards = "isOther(pointer)")
    protected LLVMNativePointer doOther(Object pointer,
                    @Cached("createLLVMObjectToNative()") LLVMObjectToNativeNode toNative) {
        return toNative.executeWithTarget(pointer);
    }

    protected static boolean isOther(Object pointer) {
        return !(pointer instanceof Long || LLVMNativePointer.isInstance(pointer) || pointer instanceof LLVMBoxedPrimitive);
    }

    protected LLVMObjectToNativeNode createLLVMObjectToNative() {
        return LLVMObjectToNativeNodeGen.create();
    }

    abstract static class LLVMObjectToNativeNode extends LLVMNode {
        public abstract LLVMNativePointer executeWithTarget(Object pointer);

        @Specialization(guards = {"lib.guard(pointer)", "lib.isPointer(pointer)"})
        protected LLVMNativePointer handlePointerCached(Object pointer,
                        @Cached("createCached(pointer)") LLVMObjectNativeLibrary lib) {
            return handlePointer(pointer, lib);
        }

        @Specialization(replaces = "handlePointerCached", guards = {"lib.guard(pointer)", "lib.isPointer(pointer)"})
        protected LLVMNativePointer handlePointer(Object pointer,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib) {
            try {
                return LLVMNativePointer.create(lib.asPointer(pointer));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Cannot convert " + pointer + " to LLVMNativePointer", e);
            }
        }

        @Specialization(replaces = {"handlePointer", "handlePointerCached"}, guards = {"lib.guard(pointer)"})
        protected LLVMNativePointer transitionToNative(Object pointer,
                        @Cached("createGeneric()") LLVMObjectNativeLibrary lib) {
            try {
                Object n = lib.toNative(pointer);
                return LLVMNativePointer.create(lib.asPointer(n));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Cannot convert " + pointer + " to LLVMNativePointer", e);
            }
        }
    }
}
