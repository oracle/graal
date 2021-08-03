/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMForeignExceptionAccessNode extends LLVMNode {

    public static final long TYPEINFO_OFFSET = -0x50;
    public static final long THROWNOBJECT_OFFSET = 0x20;

    public abstract LLVMPointer execute(LLVMPointer unwindHeader);

    public static LLVMForeignExceptionAccessNode create() {
        return LLVMForeignExceptionAccessNodeGen.create();
    }

    @Specialization
    public LLVMPointer doResolve(LLVMPointer unwindHeader,
                    @Cached LLVMForeignReadNode read,
                    @CachedContext(value = LLVMLanguage.class) LLVMContext context) {
        final LLVMPointer typeInfo = LLVMPointer.cast(read.execute(unwindHeader.increment(TYPEINFO_OFFSET), LLVMInteropType.ValueKind.POINTER.type));
        final LLVMGlobal typeInfoSymbol = context.findGlobal(typeInfo);
        final LLVMInteropType arrayType = typeInfoSymbol.getInteropType(context);
        LLVMInteropType thrownObjectType = null;
        if (arrayType instanceof LLVMInteropType.Array) {
            thrownObjectType = ((LLVMInteropType.Array) arrayType).elementType;
        }

        final Object untypedObjectPtr = read.execute(unwindHeader.increment(THROWNOBJECT_OFFSET), LLVMInteropType.ValueKind.POINTER.type);

        return LLVMPointer.cast(untypedObjectPtr).export(thrownObjectType);
    }

}
