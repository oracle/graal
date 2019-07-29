/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.types;

import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class FunctionType extends Type {

    @CompilationFinal private Assumption returnTypeAssumption;
    @CompilationFinal private Type returnType;

    private final Type[] argumentTypes;
    private final boolean isVarargs;

    public FunctionType(Type returnType, Type[] argumentTypes, boolean isVarargs) {
        this.returnTypeAssumption = Truffle.getRuntime().createAssumption("FunctionType.returnType");
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
        this.isVarargs = isVarargs;
    }

    public Type[] getArgumentTypes() {
        return argumentTypes;
    }

    public Type getReturnType() {
        if (!returnTypeAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return returnType;
    }

    public void setReturnType(Type returnType) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.returnTypeAssumption.invalidate();
        this.returnType = returnType;
        this.returnTypeAssumption = Truffle.getRuntime().createAssumption("FunctionType.returnType");
    }

    public boolean isVarargs() {
        return isVarargs;
    }

    @Override
    public int getBitSize() {
        return 0;
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getAlignment(DataLayout targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(this) / Byte.SIZE;
        } else {
            return Long.BYTES;
        }
    }

    @Override
    public int getSize(DataLayout targetDataLayout) {
        return LLVMNode.ADDRESS_SIZE_IN_BYTES;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getReturnType()).append(" (");

        for (int i = 0; i < argumentTypes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(argumentTypes[i]);
        }

        if (isVarargs) {
            if (argumentTypes.length > 0) {
                sb.append(", ");
            }
            sb.append("...");
        }
        sb.append(")");

        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(argumentTypes);
        result = prime * result + (isVarargs ? 1231 : 1237);
        result = prime * result + ((getReturnType() == null) ? 0 : getReturnType().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FunctionType other = (FunctionType) obj;
        if (!Arrays.equals(argumentTypes, other.argumentTypes)) {
            return false;
        }
        if (isVarargs != other.isVarargs) {
            return false;
        }
        if (getReturnType() == null) {
            if (other.getReturnType() != null) {
                return false;
            }
        } else if (!getReturnType().equals(other.getReturnType())) {
            return false;
        }
        return true;
    }
}
