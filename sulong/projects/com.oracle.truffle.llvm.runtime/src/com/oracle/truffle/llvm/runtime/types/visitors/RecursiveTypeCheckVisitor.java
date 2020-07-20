/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.types.visitors;

import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.OpaqueType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class RecursiveTypeCheckVisitor implements TypeVisitor {

    /**
     * Checks whether adding {@code addedType} to {@code root} would create a cycle. The only
     * allowed cycles are via pointers to named structs. The method assumes that {@code addedType}
     * is cycle-free.
     */
    public static void check(Type root, Type addedType) {
        if (root instanceof PointerType && isNamedStruct(addedType)) {
            // nothing to do for pointer to named struct
            return;
        }
        new RecursiveTypeCheckVisitor(root).check(addedType);
    }

    private final Type root;

    private RecursiveTypeCheckVisitor(Type root) {
        this.root = root;
    }

    private static boolean isNamedStruct(Type type) {
        return type instanceof StructureType && ((StructureType) type).isNamed();
    }

    private void check(Type type) {
        if (type == null) {
            return;
        }
        if (type == root) {
            throw new LLVMParserException("Invalid bitcode: recursive " + type.getClass().getSimpleName());
        }
        type.accept(this);
    }

    @Override
    public void visit(ArrayType type) {
        check(type.getElementType());
    }

    @Override
    public void visit(VectorType type) {
        check(type.getElementType());
    }

    @Override
    public void visit(StructureType type) {
        for (int i = 0; i < type.getNumberOfElementsInt(); i++) {
            check(type.getElementType(i));
        }
    }

    @Override
    public void visit(FunctionType type) {
        check(type.getReturnType());
        for (int i = 0; i < type.getNumberOfArguments(); i++) {
            check(type.getArgumentType(i));
        }
    }

    @Override
    public void visit(PointerType type) {
        // do nothing - pointer are allowed to create cycles
        Type pointeeType = type.getPointeeType();
        if (isNamedStruct(pointeeType)) {
            return;
        }
        check(pointeeType);
    }

    @Override
    public void visit(PrimitiveType type) {
        // do nothing
    }

    @Override
    public void visit(MetaType type) {
        // do nothing
    }

    @Override
    public void visit(VariableBitWidthType type) {
        // do nothing
    }

    @Override
    public void visit(VoidType type) {
        // do nothing
    }

    @Override
    public void visit(OpaqueType type) {
        // do nothing
    }
}
