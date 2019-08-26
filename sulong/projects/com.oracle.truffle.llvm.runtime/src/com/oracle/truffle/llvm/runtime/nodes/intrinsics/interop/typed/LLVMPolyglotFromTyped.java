/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.typed;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "ptr", type = LLVMExpressionNode.class)
@NodeChild(value = "typeid", type = LLVMExpressionNode.class)
public abstract class LLVMPolyglotFromTyped extends LLVMIntrinsic {

    /**
     * For binary compatibility with bitcode files compiled with polyglot.h from 1.0-RC2 or earlier.
     *
     * @deprecated
     */
    @Deprecated
    public static LLVMPolyglotFromTyped createStruct(LLVMExpressionNode ptr, LLVMExpressionNode typeid) {
        return LLVMPolyglotFromTypedNodeGen.create(ptr, LLVMTypeIDNode.create(typeid));
    }

    /**
     * For binary compatibility with bitcode files compiled with polyglot.h from 1.0-RC2 or earlier.
     *
     * @deprecated
     */
    @Deprecated
    public static LLVMPolyglotFromTyped createArray(LLVMExpressionNode ptr, LLVMExpressionNode len, LLVMExpressionNode typeid) {
        LLVMTypeIDNode elementType = LLVMTypeIDNode.create(typeid);
        LLVMArrayTypeIDNode arrayType = LLVMArrayTypeIDNode.create(elementType, len);
        return LLVMPolyglotFromTypedNodeGen.create(ptr, arrayType);
    }

    public static LLVMPolyglotFromTyped create(LLVMExpressionNode ptr, LLVMExpressionNode typeid) {
        return LLVMPolyglotFromTypedNodeGen.create(ptr, typeid);
    }

    @Specialization
    LLVMPointer doPointer(LLVMPointer address, LLVMInteropType.Structured type) {
        return address.export(type);
    }

    @Specialization
    LLVMPointer doError(@SuppressWarnings("unused") LLVMPointer address, LLVMInteropType.Value type) {
        CompilerDirectives.transferToInterpreter();
        throw new LLVMPolyglotException(this, "polyglot_from_typed can not be used with primitive type (%s).", type.getKind());
    }
}
