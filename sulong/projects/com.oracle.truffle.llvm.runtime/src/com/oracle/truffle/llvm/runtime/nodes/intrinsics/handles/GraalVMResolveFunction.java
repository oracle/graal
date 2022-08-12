/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.handles;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class GraalVMResolveFunction extends LLVMIntrinsic {

    @Specialization
    protected Object doNativeResolve(LLVMNativePointer pointer,
                    @Cached ConditionProfile isFunction) {
        LLVMFunctionDescriptor descriptor = getContext().getFunctionDescriptor(pointer);
        if (isFunction.profile(descriptor != null)) {
            return LLVMManagedPointer.create(descriptor);
        } else {
            return pointer;
        }
    }

    @Specialization(guards = "pointsToFunctionDescriptor(pointer)")
    protected Object doManagedResolve(LLVMManagedPointer pointer) {
        return pointer;
    }

    @Specialization(guards = "pointsToLong(pointer)")
    protected Object doNativePointerResolve(LLVMPointer pointer) {
        LLVMManagedPointer object = LLVMManagedPointer.cast(pointer);
        Object pointerValue = object.getObject();
        LLVMNativePointer nativePointer = LLVMNativePointer.create((long) pointerValue);
        return LLVMManagedPointer.create(getContext().getFunctionDescriptor(nativePointer));
    }

    @Fallback
    protected Object doError(Object pointer) {
        throw new LLVMPolyglotException(this, "Cannot resolve pointer %s to a function.", pointer);
    }

    protected boolean pointsToFunctionDescriptor(LLVMManagedPointer pointer) {
        return pointer.getObject() instanceof LLVMFunctionDescriptor;
    }

    protected boolean pointsToLong(LLVMPointer pointer) {
        if (LLVMManagedPointer.isInstance(pointer)) {
            return LLVMManagedPointer.cast(pointer).getObject() instanceof Long;
        }
        return false;
    }
}
