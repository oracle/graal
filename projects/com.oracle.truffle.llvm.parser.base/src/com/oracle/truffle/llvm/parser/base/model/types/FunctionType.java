/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.base.model.types;

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.base.model.symbols.ValueSymbol;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;

public class FunctionType implements Type, ValueSymbol {

    private final Type type;

    private final Type[] args;

    private final boolean isVarArg;

    private String name = ValueSymbol.UNKNOWN;

    public FunctionType(Type type, Type[] args, boolean isVarArg) {
        this.type = type;
        this.args = args;
        this.isVarArg = isVarArg;
    }

    public Type[] getArgumentTypes() {
        return args;
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        return LLVMBaseType.FUNCTION_ADDRESS;
    }

    @Override
    public Type getType() {
        return new PointerType(Type.super.getType());
    }

    public Type getReturnType() {
        return type;
    }

    @Override
    public int getAlignment(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(getLLVMBaseType()) / Byte.SIZE;
        } else {
            return Long.BYTES;
        }
    }

    @Override
    public int getSize(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return 0;
    }

    public boolean isVarArg() {
        return isVarArg;
    }

    @Override
    public int getBits() {
        return 0;
    }

    @Override
    public void setName(String name) {
        this.name = "@" + name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        return LLVMFunctionDescriptor.LLVMRuntimeType.FUNCTION_ADDRESS;
    }

    @Override
    public int hashCode() {
        int hash = 29;
        hash = 17 * hash + Arrays.hashCode(args);
        hash = 17 * hash + (isVarArg ? 1231 : 1237);
        hash = 17 * hash + ((type == null) ? 0 : type.hashCode());
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FunctionType) {
            FunctionType other = (FunctionType) obj;
            if (!Objects.equals(type, other.type)) {
                return false;
            }
            if (!Arrays.equals(args, other.args)) {
                return false;
            }
            if (isVarArg != other.isVarArg) {
                return false;
            }
            if (!Objects.equals(name, other.name)) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(type).append(" (");

        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i]);
        }

        if (isVarArg) {
            if (args.length > 0) {
                sb.append(", ");
            }
            sb.append("...");
        }
        sb.append(")");

        return sb.toString();
    }
}
