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
package com.oracle.truffle.llvm.parser.listeners;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.parser.records.TypesRecord;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.OpaqueType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class Types implements ParserListener, Iterable<Type> {

    private final ModelModule module;

    private Type[] table = new Type[0];

    private String structName = null;

    private int size = 0;

    Types(ModelModule module) {
        this.module = module;
    }

    @Override
    public Iterator<Type> iterator() {
        return Collections.unmodifiableList(Arrays.asList(table)).iterator();
    }

    @Override
    public void record(long id, long[] args) {
        TypesRecord record = TypesRecord.decode(id);
        Type type;

        switch (record) {
            case NUMBER_OF_ENTRIES:
                table = new Type[(int) args[0]];
                return;

            case VOID:
                type = VoidType.INSTANCE;
                break;

            case FLOAT:
                type = PrimitiveType.FLOAT;
                break;

            case DOUBLE:
                type = PrimitiveType.DOUBLE;
                break;

            case LABEL:
                type = MetaType.LABEL;
                break;

            case OPAQUE:
                if (structName != null) {
                    type = new OpaqueType(LLVMIdentifier.toLocalIdentifier(structName));
                    structName = null;
                    module.addGlobalType(type);
                } else {
                    type = new OpaqueType();
                }
                break;

            case INTEGER:
                type = Type.getIntegerType((int) args[0]);
                break;

            case POINTER: {
                final PointerType pointerType = new PointerType(null);
                setType((int) args[0], pointerType::setPointeeType);
                type = pointerType;
                break;
            }
            case FUNCTION_OLD: {
                final FunctionType functionType = new FunctionType(null, toTypes(args, 3, args.length), args[0] != 0);
                setType((int) args[2], functionType::setReturnType);
                type = functionType;
                break;
            }
            case HALF:
                type = PrimitiveType.HALF;
                break;

            case ARRAY: {
                final ArrayType arrayType = new ArrayType(null, (int) args[0]);
                setType((int) args[1], arrayType::setElementType);
                type = arrayType;
                break;
            }

            case VECTOR: {
                final VectorType vectorType = new VectorType(null, (int) args[0]);
                setType((int) args[1], vectorType::setElementType);
                type = vectorType;
                break;
            }

            case X86_FP80:
                type = PrimitiveType.X86_FP80;
                break;

            case FP128:
                type = PrimitiveType.F128;
                break;

            case PPC_FP128:
                type = PrimitiveType.PPC_FP128;
                break;

            case METADATA:
                type = MetaType.METADATA;
                break;

            case X86_MMX:
                type = MetaType.X86MMX;
                break;

            case STRUCT_NAME: {
                structName = Records.toString(args);
                return;
            }

            case STRUCT_ANON:
            case STRUCT_NAMED: {
                final boolean isPacked = args[0] != 0;
                final Type[] members = toTypes(args, 1, args.length);
                if (structName != null) {
                    type = new StructureType(LLVMIdentifier.toLocalIdentifier(structName), isPacked, members);
                    structName = null;
                    module.addGlobalType(type);
                } else {
                    type = new StructureType(isPacked, members);
                }
                break;
            }
            case FUNCTION: {
                final FunctionType functionType = new FunctionType(null, toTypes(args, 2, args.length), args[0] != 0);
                setType((int) args[1], functionType::setReturnType);
                type = functionType;
                break;
            }

            case TOKEN:
                type = MetaType.TOKEN;
                break;

            default:
                type = MetaType.UNKNOWN;
                break;
        }

        if (table[size] != null) {
            ((UnresolvedType) table[size]).dependent.accept(type);
        }
        table[size++] = type;

    }

    private void setType(int typeIndex, Consumer<Type> typeFieldSetter) {
        if (typeIndex < size) {
            typeFieldSetter.accept(table[typeIndex]);

        } else if (table[typeIndex] == null) {
            table[typeIndex] = new UnresolvedType(typeFieldSetter);

        } else {
            ((UnresolvedType) table[typeIndex]).addDependent(typeFieldSetter);
        }
    }

    private Type[] toTypes(long[] args, int from, int to) {
        final Type[] types = new Type[to - from];

        for (int i = 0; i < types.length; i++) {
            final int typeIndex = (int) args[from + i];
            if (typeIndex < size) {
                types[i] = table[typeIndex];

            } else {
                final Consumer<Type> setter = new MemberDependent(i, types);
                if (table[typeIndex] == null) {
                    table[typeIndex] = new UnresolvedType(setter);

                } else {
                    ((UnresolvedType) table[typeIndex]).addDependent(setter);
                }
            }
        }

        return types;
    }

    private static final class MemberDependent implements Consumer<Type> {

        private final int index;
        private final Type[] target;

        private MemberDependent(int index, Type[] target) {
            this.index = index;
            this.target = target;
        }

        @Override
        public void accept(Type type) {
            target[index] = type;
        }
    }

    public Type get(long index) {
        return table[(int) index];
    }

    private static final class UnresolvedType extends Type {

        private Consumer<Type> dependent;

        UnresolvedType(Consumer<Type> dependent) {
            this.dependent = dependent;
        }

        private void addDependent(Consumer<Type> newDependent) {
            dependent = dependent.andThen(newDependent);
        }

        @Override
        public void accept(TypeVisitor visitor) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Unresolved Forward-Referenced Type!");
        }

        @Override
        public int getBitSize() {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Unresolved Forward-Referenced Type!");
        }

        @Override
        public int getAlignment(DataSpecConverter targetDataLayout) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Unresolved Forward-Referenced Type!");
        }

        @Override
        public int getSize(DataSpecConverter targetDataLayout) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Unresolved Forward-Referenced Type!");
        }

        @Override
        public Type shallowCopy() {
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public LLVMSourceType getSourceType() {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Unresolved Forward-Referenced Type!");
        }

        @Override
        public void setSourceType(LLVMSourceType sourceType) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Unresolved Forward-Referenced Type!");
        }
    }

    @Override
    public String toString() {
        CompilerDirectives.transferToInterpreter();
        return "Typetable (size: " + table.length + ", currentIndex: " + size + ")";
    }
}
