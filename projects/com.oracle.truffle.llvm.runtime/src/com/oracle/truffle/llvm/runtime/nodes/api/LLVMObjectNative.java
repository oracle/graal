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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;

/**
 * Interface for objects that want to simulate the behavior of native memory. If an object or the
 * {@link ObjectType} of a {@link DynamicObject} implements this interface, raw memory access to
 * this object will be implemented using custom nodes. If an object implements both
 * {@link LLVMObjectNative} and {@link TruffleObject}, the nodes from {@link LLVMObjectNative} have
 * precedence over regular interop.
 */
public interface LLVMObjectNative {

    LLVMObjectToNativeNode createToNativeNode();

    LLVMObjectIsPointerNode createIsPointerNode();

    LLVMObjectAsPointerNode createAsPointerNode();

    abstract class LLVMObjectNativeNode extends Node {

        public abstract boolean isNative(Object obj);
    }

    abstract class LLVMObjectToNativeNode extends LLVMObjectNativeNode {

        /**
         * Transform an object to a pointer.
         *
         * @param frame the Truffle frame
         * @param obj the object that is the base of the read pointer
         */
        public abstract Object executeToNative(VirtualFrame frame, Object obj) throws InteropException;
    }

    abstract class LLVMObjectIsPointerNode extends LLVMObjectNativeNode {

        /**
         * Check if object is a pointer.
         *
         * @param frame the Truffle frame
         * @param obj the object that is queried for IS_POINTER
         */
        public abstract boolean executeIsPointer(VirtualFrame frame, Object obj);
    }

    abstract class LLVMObjectAsPointerNode extends LLVMObjectNativeNode {

        /**
         * Get raw pointer representation of this object.
         *
         * @param frame the Truffle frame
         * @param obj the object that is converted to a pointer
         */
        public abstract long executeAsPointer(VirtualFrame frame, Object obj) throws InteropException;
    }

}
