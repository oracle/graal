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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;

/**
 * Interface for objects that want to simulate the behavior of native memory. If an object or the
 * {@link ObjectType} of a {@link DynamicObject} implements this interface, raw memory access to
 * this object will be implemented using custom nodes. If an object implements both
 * {@link LLVMObjectAccess} and {@link TruffleObject}, the nodes from {@link LLVMObjectAccess} have
 * precedence over regular interop.
 */
public interface LLVMObjectAccess {

    LLVMObjectReadNode createReadNode();

    LLVMObjectWriteNode createWriteNode();

    interface LLVMObjectAccessNode extends NodeInterface {

        boolean canAccess(Object obj);
    }

    interface LLVMObjectReadNode extends LLVMObjectAccessNode {

        /**
         * Do a native memory read on an object.
         *
         * @param obj the object that is the base of the read pointer
         * @param offset the byte offset into the object
         * @return the read value
         */
        Object executeRead(Object obj, long offset, ForeignToLLVMType type);
    }

    interface LLVMObjectWriteNode extends LLVMObjectAccessNode {

        /**
         * Do a native memory write on an object.
         *
         * @param obj the object that is the base of the written pointer
         * @param offset the byte offset into the object
         * @param value the written value
         */
        void executeWrite(Object obj, long offset, Object value, ForeignToLLVMType type);
    }
}
