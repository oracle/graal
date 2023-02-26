/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class FunctionType extends Type {
    public static final int NOT_VARARGS = -1;

    private Type returnType;

    private final Type[] argumentTypes;
    private final int fixedArgs;

    private FunctionType(Type returnType, Type[] argumentTypes, int fixedArgs) {
        this.returnType = returnType;
        this.argumentTypes = argumentTypes;
        this.fixedArgs = fixedArgs;
    }

    /**
     * Creates a function type with a single argument type.
     */
    public static FunctionType create(Type returnType, Type arg0) {
        return new FunctionType(returnType, new Type[]{arg0}, NOT_VARARGS);
    }

    public FunctionType(Type returnType, TypeArrayBuilder argumentTypes, int fixedArgs) {
        this(returnType, getRawTypeArray(argumentTypes), fixedArgs);
    }

    public FunctionType(Type returnType, int numArguments, int fixedArgs) {
        this(returnType, new Type[numArguments], fixedArgs);
    }

    public static FunctionType copy(FunctionType type) {
        return new FunctionType(type.returnType, type.argumentTypes.clone(), type.fixedArgs);
    }

    public void setArgumentType(int idx, Type type) {
        verifyCycleFree(type);
        argumentTypes[idx] = type;
    }

    public List<Type> getArgumentTypes() {
        return Collections.unmodifiableList(Arrays.asList(argumentTypes));
    }

    public Type getArgumentType(int idx) {
        return argumentTypes[idx];
    }

    public int getNumberOfArguments() {
        return argumentTypes.length;
    }

    public Type getReturnType() {
        CompilerAsserts.neverPartOfCompilation();
        return returnType;
    }

    public void setReturnType(Type returnType) {
        CompilerAsserts.neverPartOfCompilation();
        verifyCycleFree(returnType);
        this.returnType = returnType;
    }

    public boolean isVarargs() {
        return fixedArgs != NOT_VARARGS;
    }

    public int getFixedArgs() {
        return fixedArgs;
    }

    @Override
    public long getBitSize() {
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
    public long getSize(DataLayout targetDataLayout) {
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
            if (i == fixedArgs) {
                sb.append("...");
            }
            sb.append(argumentTypes[i]);
        }

        if (fixedArgs == argumentTypes.length) {
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
        result = prime * result + Integer.hashCode(fixedArgs);
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
        if (fixedArgs != other.fixedArgs) {
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

    @Override
    public LLVMExpressionNode createNullConstant(NodeFactory nodeFactory, DataLayout dataLayout, GetStackSpaceFactory stackFactory) {
        return CommonNodeFactory.createSimpleConstantNoArray(null, this);
    }
}
