/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.value;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceMemberType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStructLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.OpaqueType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class LLVMSourceTypeFactory {

    public static LLVMSourceType resolveType(Type type, DataLayout dataLayout) {
        CompilerAsserts.neverPartOfCompilation();
        final ConversionVisitor visitor = new ConversionVisitor(dataLayout);
        return visitor.resolveType(type);
    }

    private LLVMSourceTypeFactory() {
    }

    private static final class ConversionVisitor implements TypeVisitor {

        private final DataLayout dataLayout;

        private final Map<Type, LLVMSourceType> resolved;

        private ConversionVisitor(DataLayout dataLayout) {
            this.dataLayout = dataLayout;
            this.resolved = new IdentityHashMap<>();
        }

        LLVMSourceType resolveType(Type type) {
            if (type == null) {
                return null;
            }

            final LLVMSourceType previouslyResolved = resolved.get(type);
            if (previouslyResolved != null) {
                return previouslyResolved;
            }

            type.accept(this);
            return resolved.get(type);
        }

        @Override
        public void visit(FunctionType type) {
            final List<LLVMSourceType> types = new ArrayList<>();
            final LLVMSourceFunctionType resolvedType = new LLVMSourceFunctionType(types);
            resolved.put(type, resolvedType);

            final LLVMSourceType resolvedReturnType = resolveType(type.getReturnType());
            types.add(resolvedReturnType);

            for (int i = 0; i < type.getNumberOfArguments(); i++) {
                final LLVMSourceType resolvedArgType = resolveType(type.getArgumentType(i));
                types.add(resolvedArgType);
            }

            if (type.isVarargs()) {
                types.add(LLVMSourceType.VOID);
            }
        }

        @Override
        public void visit(PrimitiveType type) {
            try {
                final String name = type.getPrimitiveKind().name().toLowerCase();

                final LLVMSourceType resolvedType;
                switch (type.getPrimitiveKind()) {
                    case I1:
                        resolvedType = new LLVMSourceBasicType(name, getBitSize(type), getAlignment(type), 0L, LLVMSourceBasicType.Kind.BOOLEAN, null);
                        break;
                    case I8:
                    case I16:
                    case I32:
                    case I64:
                        resolvedType = new LLVMSourceBasicType(name, getBitSize(type), getAlignment(type), 0L, LLVMSourceBasicType.Kind.SIGNED, null);
                        break;
                    case FLOAT:
                    case DOUBLE:
                    case X86_FP80:
                        resolvedType = new LLVMSourceBasicType(name, getBitSize(type), getAlignment(type), 0L, LLVMSourceBasicType.Kind.FLOATING, null);
                        break;
                    default:
                        resolvedType = LLVMSourceType.UNSUPPORTED;
                }

                resolved.put(type, resolvedType);
            } catch (TypeOverflowException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void visit(MetaType type) {
            throw new UnsupportedOperationException("Cannot convert type: " + type);
        }

        @Override
        public void visit(PointerType type) {
            try {
                final LLVMSourcePointerType resolvedType = new LLVMSourcePointerType(getBitSize(type), getAlignment(type), 0L, false, false, null);
                resolved.put(type, resolvedType);

                final Type baseType = type.getPointeeType();
                final LLVMSourceType resolvedBaseType = baseType != null ? resolveType(baseType) : LLVMSourceType.VOID;
                resolvedType.setBaseType(resolvedBaseType);
                resolvedType.setName(() -> String.format("%s*", resolvedBaseType.getName()));
            } catch (TypeOverflowException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void visit(StructureType type) {
            try {
                final LLVMSourceStructLikeType resolvedType = new LLVMSourceStructLikeType(type.getName(), getBitSize(type), getAlignment(type), 0L, null);
                resolved.put(type, resolvedType);

                final int numberOfMembers = type.getNumberOfElementsInt();
                for (int i = 0; i < numberOfMembers; i++) {
                    final Type memberType = type.getElementType(i);
                    final long memberBitOffset = Type.multiplyUnsignedExact(type.getOffsetOf(i, dataLayout), Byte.SIZE);

                    final String memberName = String.format("[%d]", i);
                    final LLVMSourceType resolvedMemberType = resolveType(memberType);
                    final LLVMSourceMemberType member = new LLVMSourceMemberType(memberName, getBitSize(memberType), getAlignment(memberType), memberBitOffset, null);
                    member.setElementType(resolvedMemberType);

                    resolvedType.addDynamicMember(member);
                }
            } catch (TypeOverflowException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void visit(ArrayType arrayType) {
            resolveArrayOrVectorType(arrayType);
        }

        @Override
        public void visit(VectorType vectorType) {
            resolveArrayOrVectorType(vectorType);
        }

        private void resolveArrayOrVectorType(AggregateType type) {
            try {
                final LLVMSourceArrayLikeType resolvedType = new LLVMSourceArrayLikeType(getBitSize(type), getAlignment(type), 0L, null);
                resolved.put(type, resolvedType);

                final LLVMSourceType resolvedBaseType = resolveType(type.getElementType(0L));
                resolvedType.setBaseType(resolvedBaseType);
                resolvedType.setLength(type.getNumberOfElements());

                resolvedType.setName(() -> {
                    final String content = String.format("%d x %s", type.getNumberOfElements(), resolvedBaseType.getName());
                    final String aggregateFormat = type instanceof ArrayType ? "[ %s ]" : "< %s >";
                    return String.format(aggregateFormat, content);
                });
            } catch (TypeOverflowException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void visit(VariableBitWidthType type) {
            final String name = String.format("i%d", type.getBitSize());

            final LLVMSourceType resolvedType = new LLVMSourceBasicType(name, type.getBitSize(), getAlignment(type), 0L, LLVMSourceBasicType.Kind.SIGNED, null);
            resolved.put(type, resolvedType);
        }

        @Override
        public void visit(VoidType type) {
            resolved.put(type, LLVMSourceType.VOID);
        }

        @Override
        public void visit(OpaqueType type) {
            try {
                final LLVMSourceStructLikeType resolvedType = new LLVMSourceStructLikeType(type.getName(), getBitSize(type), getAlignment(type), 0L, null);
                resolved.put(type, resolvedType);
            } catch (TypeOverflowException e) {
                throw new AssertionError(e);
            }
        }

        private long getBitSize(Type type) throws TypeOverflowException {
            final long byteSize = type.getSize(dataLayout);
            return Type.multiplyUnsignedExact(byteSize, Byte.SIZE);
        }

        private long getAlignment(Type type) {
            return type.getAlignment(dataLayout);
        }
    }
}
