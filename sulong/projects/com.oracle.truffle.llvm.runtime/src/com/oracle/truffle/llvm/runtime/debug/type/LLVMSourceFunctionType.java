/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.type;

import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class LLVMSourceFunctionType extends LLVMSourceType {

    private final List<LLVMSourceType> types;

    @TruffleBoundary
    public LLVMSourceFunctionType(List<LLVMSourceType> types) {
        // function type do not require size or offset information since there are no concrete
        // values of them in C/C++/Fortran. they are only used as basis for function pointers
        super(0L, 0L, 0L, null);
        assert types != null;
        this.types = types;
        setName(() -> {
            CompilerDirectives.transferToInterpreter();

            StringBuilder nameBuilder = new StringBuilder(getReturnType().getName()).append("(");

            final List<LLVMSourceType> params = getParameterTypes();
            if (params.size() > 0) {
                nameBuilder.append(params.get(0).getName());
            }
            for (int i = 1; i < params.size(); i++) {
                nameBuilder.append(", ").append(params.get(i).getName());
            }

            if (!isVarArgs()) {
                nameBuilder.append(")");
            } else if (getParameterTypes().size() == 0) {
                nameBuilder.append("...)");
            } else {
                nameBuilder.append(", ...)");
            }

            return nameBuilder.toString();
        });
    }

    @TruffleBoundary
    public LLVMSourceType getReturnType() {
        if (types.size() > 0) {
            return types.get(0);
        } else {
            return LLVMSourceType.VOID;
        }
    }

    @TruffleBoundary
    public List<LLVMSourceType> getParameterTypes() {
        if (types.size() <= 1) {
            return Collections.emptyList();
        } else {
            return types.subList(1, types.size() - (isVarArgs() ? 1 : 0));
        }
    }

    @TruffleBoundary
    public boolean isVarArgs() {
        return types.size() > 1 && types.get(types.size() - 1) == LLVMSourceType.VOID;
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return this;
    }
}
