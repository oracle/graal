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
package com.oracle.truffle.llvm.parser.bc.impl.parser.listeners;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import com.oracle.truffle.llvm.parser.base.model.generators.ModuleGenerator;
import com.oracle.truffle.llvm.parser.base.model.types.ArrayType;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;
import com.oracle.truffle.llvm.parser.base.model.types.FunctionType;
import com.oracle.truffle.llvm.parser.base.model.types.IntegerType;
import com.oracle.truffle.llvm.parser.base.model.types.MetaType;
import com.oracle.truffle.llvm.parser.base.model.types.PointerType;
import com.oracle.truffle.llvm.parser.base.model.types.StructureType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;
import com.oracle.truffle.llvm.parser.bc.impl.parser.bc.records.Records;
import com.oracle.truffle.llvm.parser.bc.impl.parser.ir.module.records.TypesRecord;

public final class Types implements ParserListener, Iterable<Type> {

    public static Type[] toTypes(Types types, long[] args, long from, long to) {
        Type[] t = new Type[(int) (to - from)];

        for (int i = 0; i < t.length; i++) {
            t[i] = types.get(args[(int) from + i]);
        }

        return t;
    }

    private final ModuleGenerator generator;

    private Type[] table = new Type[0];

    private int size = 0;

    public Types(ModuleGenerator generator) {
        this.generator = generator;
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
                type = MetaType.VOID;
                break;

            case FLOAT:
                type = FloatingPointType.FLOAT;
                break;

            case DOUBLE:
                type = FloatingPointType.DOUBLE;
                break;

            case LABEL:
                type = MetaType.LABEL;
                break;

            case OPAQUE:
                type = MetaType.OPAQUE;
                break;

            case INTEGER:
                type = new IntegerType((int) args[0]);
                break;

            case POINTER: {
                int idx = (int) args[0];

                if (idx > size) {
                    table[size] = new PointerType(null);
                    table[idx] = new UnresolvedPointeeType(size);
                    size++;
                    return;
                } else {
                    type = new PointerType(get(idx));
                }
                break;
            }
            case FUNCTION_OLD: {
                int i = 2;
                Type returnType = get(args[i++]);
                type = new FunctionType(returnType, toTypes(this, args, i, args.length), args[0] != 0);
                break;
            }
            case HALF:
                type = FloatingPointType.HALF;
                break;

            case ARRAY:
                type = new ArrayType(get(args[1]), (int) args[0]);
                break;

            case VECTOR:
                type = new VectorType(get(args[1]), (int) args[0]);
                break;

            case X86_FP80:
                type = FloatingPointType.X86_FP80;
                break;

            case FP128:
                type = FloatingPointType.FP128;
                break;

            case PPC_FP128:
                type = FloatingPointType.PPC_FP128;
                break;

            case METADATA:
                type = MetaType.METADATA;
                break;

            case X86_MMX:
                type = MetaType.X86_MMX;
                break;

            case STRUCT_ANON:
                type = new StructureType(args[0] != 0, toTypes(this, args, 1, args.length));
                break;

            case STRUCT_NAME: {
                String name = Records.toString(args);
                if (table[size] instanceof UnresolvedPointeeType) {
                    table[size] = new UnresolvedNamedPointeeType(name, ((UnresolvedPointeeType) table[size]).getIndex());
                } else {
                    table[size] = new UnresolvedNamedType(name);
                }
                return;
            }
            case STRUCT_NAMED: {
                StructureType structure = new StructureType(args[0] != 0, toTypes(this, args, 1, args.length));
                if (table[size] != null) {
                    if (table[size] instanceof UnresolvedNamedPointeeType) {
                        structure.setName(((UnresolvedNamedPointeeType) table[size]).getName());
                    } else {
                        structure.setName(((UnresolvedNamedType) table[size]).getName());
                    }
                }
                type = structure;
                break;
            }
            case FUNCTION:
                type = new FunctionType(get(args[1]), toTypes(this, args, 2, args.length), args[0] != 0);
                break;

            case TOKEN:
                type = MetaType.TOKEN;
                break;

            default:
                type = MetaType.UNKNOWN;
                break;
        }

        if (table[size] instanceof UnresolvedPointeeType) {
            PointerType pointer = (PointerType) table[((UnresolvedPointeeType) table[size]).getIndex()];
            pointer.setPointeeType(type);
            generator.createType(pointer);
        }
        table[size++] = type;
        generator.createType(type);
    }

    public int size() {
        return table.length;
    }

    public Type get(long index) {
        return table[(int) index];
    }

    private static class UnresolvedPointeeType implements Type {

        private final int idx;

        UnresolvedPointeeType(int idx) {
            this.idx = idx;
        }

        public int getIndex() {
            return idx;
        }
    }

    private static final class UnresolvedNamedPointeeType extends UnresolvedPointeeType {

        private final String name;

        UnresolvedNamedPointeeType(String name, int idx) {
            super(idx);
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static final class UnresolvedNamedType implements Type {

        private final String name;

        UnresolvedNamedType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Override
    public String toString() {
        return "Types " + Arrays.toString(table);
    }
}
