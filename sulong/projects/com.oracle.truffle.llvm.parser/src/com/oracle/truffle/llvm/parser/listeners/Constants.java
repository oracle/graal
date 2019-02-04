/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import java.math.BigInteger;

import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class Constants implements ParserListener {

    private static final int CONSTANT_SETTYPE = 1;
    private static final int CONSTANT_NULL = 2;
    private static final int CONSTANT_UNDEF = 3;
    private static final int CONSTANT_INTEGER = 4;
    private static final int CONSTANT_WIDE_INTEGER = 5;
    private static final int CONSTANT_FLOAT = 6;
    private static final int CONSTANT_AGGREGATE = 7;
    private static final int CONSTANT_STRING = 8;
    private static final int CONSTANT_CSTRING = 9;
    private static final int CONSTANT_CE_BINOP = 10;
    private static final int CONSTANT_CE_CAST = 11;
    private static final int CONSTANT_CE_GEP = 12;
    // private static final int CONSTANT_CE_SELECT = 13;
    // private static final int CONSTANT_CE_EXTRACTELT = 14;
    // private static final int CONSTANT_CE_INSERTELT = 15;
    // private static final int CONSTANT_CE_SHUFFLEVEC = 16;
    private static final int CONSTANT_CE_CMP = 17;
    // private static final int CONSTANT_INLINEASM_OLD = 18;
    // private static final int CONSTANT_CE_SHUFVEC_EX = 19;
    private static final int CONSTANT_CE_INBOUNDS_GEP = 20;
    private static final int CONSTANT_BLOCKADDRESS = 21;
    private static final int CONSTANT_DATA = 22;
    private static final int CONSTANT_INLINEASM = 23;
    private static final int CONSTANT_CE_GEP_WITH_INRANGE_INDEX = 24;

    private static final BigInteger WIDE_INTEGER_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);

    private final Types types;

    private final IRScope scope;

    private Type type;

    Constants(Types types, IRScope scope) {
        this.types = types;
        this.scope = scope;
    }

    @Override
    public void record(long id, long[] args) {
        final int opCode = (int) id;

        switch (opCode) {
            case CONSTANT_SETTYPE:
                type = types.get(args[0]);
                return;

            case CONSTANT_NULL:
                if (Type.isIntegerType(type)) {
                    scope.addSymbol(new NullConstant(type), Type.createConstantForType(type, 0));
                } else {
                    scope.addSymbol(new NullConstant(type), type);
                }
                return;

            case CONSTANT_UNDEF:
                scope.addSymbol(new UndefinedConstant(type), type);
                return;

            case CONSTANT_INTEGER: {
                long value = Records.toSignedValue(args[0]);
                scope.addSymbol(new IntegerConstant(type, value), Type.createConstantForType(type, value));
                return;
            }
            case CONSTANT_WIDE_INTEGER: {
                BigInteger value = BigInteger.ZERO;

                for (int i = 0; i < args.length; i++) {
                    BigInteger temp = BigInteger.valueOf(Records.toSignedValue(args[i]));
                    temp = temp.and(WIDE_INTEGER_MASK);
                    temp = temp.shiftLeft(i * Long.SIZE);
                    value = value.add(temp);
                }
                scope.addSymbol(new BigIntegerConstant(type, value), Type.createConstantForType(type, value));
                return;
            }
            case CONSTANT_FLOAT:
                scope.addSymbol(FloatingPointConstant.create(type, args), type);
                return;

            case CONSTANT_AGGREGATE: {
                scope.addSymbol(Constant.createFromValues(type, scope.getSymbols(), Records.toIntegers(args)), type);
                return;
            }
            case CONSTANT_STRING:
                scope.addSymbol(new StringConstant((ArrayType) type, Records.toString(args), false), type);
                return;

            case CONSTANT_CSTRING:
                scope.addSymbol(new StringConstant((ArrayType) type, Records.toString(args), true), type);
                return;

            case CONSTANT_CE_BINOP: {
                int op = (int) args[0];
                int lhs = (int) args[1];
                int rhs = (int) args[2];
                scope.addSymbol(BinaryOperationConstant.fromSymbols(scope.getSymbols(), type, op, lhs, rhs), type);
                return;
            }

            case CONSTANT_CE_CAST: {
                int op = (int) args[0];
                int value = (int) args[2];
                scope.addSymbol(CastConstant.fromSymbols(scope.getSymbols(), type, op, value), type);
                return;
            }

            case CONSTANT_CE_CMP: {
                int i = 1;
                int lhs = (int) args[i++];
                int rhs = (int) args[i++];
                int opcode = (int) args[i];

                scope.addSymbol(CompareConstant.fromSymbols(scope.getSymbols(), type, opcode, lhs, rhs), type);
                return;
            }

            case CONSTANT_BLOCKADDRESS: {
                int function = (int) args[1];
                int block = (int) args[2];
                scope.addSymbol(BlockAddressConstant.fromSymbols(scope.getSymbols(), type, function, block), type);
                return;
            }

            case CONSTANT_DATA:
                scope.addSymbol(Constant.createFromData(type, args), type);
                return;

            case CONSTANT_INLINEASM:
                scope.addSymbol(InlineAsmConstant.generate(type, args), type);
                return;

            case CONSTANT_CE_GEP:
            case CONSTANT_CE_INBOUNDS_GEP:
            case CONSTANT_CE_GEP_WITH_INRANGE_INDEX:
                createGetElementPointerExpression(args, opCode);
                return;

            default:
                throw new LLVMParserException("Unsupported opCode in constant block: " + opCode);
        }
    }

    private void createGetElementPointerExpression(long[] args, int opCode) {
        int i = 0;
        if (opCode == CONSTANT_CE_GEP_WITH_INRANGE_INDEX || args.length % 2 != 0) {
            i++; // type of pointee
        }

        boolean isInbounds;
        if (opCode == CONSTANT_CE_GEP_WITH_INRANGE_INDEX) {
            final long op = args[i++];
            isInbounds = (op & 0x1) != 0;
        } else {
            isInbounds = opCode == CONSTANT_CE_INBOUNDS_GEP;
        }

        i++; // type of pointer
        int pointer = (int) args[i++];

        final int[] indices = new int[(args.length - i) >> 1];
        for (int j = 0; j < indices.length; j++) {
            i++; // index type
            indices[j] = (int) args[i++];
        }

        scope.addSymbol(GetElementPointerConstant.fromSymbols(scope.getSymbols(), type, pointer, indices, isInbounds), type);
    }
}
