/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.SelectConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UnaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.AggregateConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatingPointConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.Type;

import java.math.BigInteger;

public final class Constants implements ParserListener {

    // see llvm/include/llvm/Bitcode/LLVMBitCodes.h, enum ConstantCodes
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
    private static final int CONSTANT_CE_GEP_OLD = 12;
    private static final int CONSTANT_CE_SELECT = 13;
    // private static final int CONSTANT_CE_EXTRACTELT = 14;
    // private static final int CONSTANT_CE_INSERTELT = 15;
    // private static final int CONSTANT_CE_SHUFFLEVEC = 16;
    private static final int CONSTANT_CE_CMP = 17;
    // private static final int CONSTANT_INLINEASM_OLD = 18;
    // private static final int CONSTANT_CE_SHUFVEC_EX = 19;
    private static final int CONSTANT_CE_INBOUNDS_GEP = 20;
    private static final int CONSTANT_BLOCKADDRESS = 21;
    private static final int CONSTANT_DATA = 22;
    private static final int CONSTANT_INLINEASM_OLD2 = 23;
    private static final int CONSTANT_CE_GEP_WITH_INRANGE_INDEX_OLD = 24;
    private static final int CONSTANT_CE_UNOP = 25;
    private static final int CONSTANT_POISON = 26;
    // private static final int CONSTANT_DSO_LOCAL_EQUIVALENT = 27;
    // private static final int CONSTANT_INLINEASM_OLD3 = 28;
    // private static final int CONSTANT_NO_CFI_VALUE = 29;
    private static final int CONSTANT_INLINEASM = 30;
    private static final int CONSTANT_CE_GEP_WITH_INRANGE = 31;
    private static final int CONSTANT_CE_GEP = 32;
    // private static final int CONSTANT_PTRAUTH = 30;

    private static final BigInteger WIDE_INTEGER_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);

    private final Types types;

    private final IRScope scope;

    private Type type;

    Constants(Types types, IRScope scope) {
        this.types = types;
        this.scope = scope;
    }

    @Override
    public void record(RecordBuffer buffer) {
        final int opCode = buffer.getId();

        switch (opCode) {
            case CONSTANT_SETTYPE:
                type = types.get(buffer.read());
                return;

            case CONSTANT_NULL:
                if (Type.isIntegerType(type)) {
                    scope.addSymbol(new NullConstant(type), Type.createConstantForType(type, 0));
                } else {
                    scope.addSymbol(new NullConstant(type), type);
                }
                return;

            case CONSTANT_UNDEF:
            case CONSTANT_POISON:
                scope.addSymbol(new UndefinedConstant(type), type);
                return;

            case CONSTANT_INTEGER: {
                long value = buffer.readSignedValue();
                scope.addSymbol(new IntegerConstant(type, value), Type.createConstantForType(type, value));
                return;
            }
            case CONSTANT_WIDE_INTEGER: {
                BigInteger value = BigInteger.ZERO;

                for (int i = 0; i < buffer.size(); i++) {
                    BigInteger temp = BigInteger.valueOf(buffer.readSignedValue());
                    temp = temp.and(WIDE_INTEGER_MASK);
                    temp = temp.shiftLeft(i * Long.SIZE);
                    value = value.add(temp);
                }
                scope.addSymbol(new BigIntegerConstant(type, value), Type.createConstantForType(type, value));
                return;
            }
            case CONSTANT_FLOAT:
                scope.addSymbol(FloatingPointConstant.create(type, buffer), type);
                return;

            case CONSTANT_AGGREGATE: {
                scope.addSymbol(AggregateConstant.createFromValues(scope.getSymbols(), type, buffer), type);
                return;
            }
            case CONSTANT_STRING:
                scope.addSymbol(new StringConstant((ArrayType) type, buffer.readStringAsBytes(false)), type);
                return;

            case CONSTANT_CSTRING:
                scope.addSymbol(new StringConstant((ArrayType) type, buffer.readStringAsBytes(true)), type);
                return;

            case CONSTANT_CE_UNOP: {
                int op = buffer.readInt();
                int v = buffer.readInt();
                scope.addSymbol(UnaryOperationConstant.fromSymbols(scope.getSymbols(), type, op, v), type);
                return;
            }

            case CONSTANT_CE_BINOP: {
                int op = buffer.readInt();
                int lhs = buffer.readInt();
                int rhs = buffer.readInt();
                scope.addSymbol(BinaryOperationConstant.fromSymbols(scope.getSymbols(), type, op, lhs, rhs), type);
                return;
            }

            case CONSTANT_CE_CAST: {
                int op = buffer.readInt();
                buffer.skip(); // ignored
                int value = buffer.readInt();
                scope.addSymbol(CastConstant.fromSymbols(scope.getSymbols(), type, op, value), type);
                return;
            }

            case CONSTANT_CE_CMP: {
                buffer.skip(); // ignored
                int lhs = buffer.readInt();
                int rhs = buffer.readInt();
                int opcode = buffer.readInt();

                scope.addSymbol(CompareConstant.fromSymbols(scope.getSymbols(), type, opcode, lhs, rhs), type);
                return;
            }

            case CONSTANT_BLOCKADDRESS: {
                buffer.skip(); // ignored
                int function = buffer.readInt();
                int block = buffer.readInt();
                scope.addSymbol(BlockAddressConstant.fromSymbols(scope.getSymbols(), type, function, block), type);
                return;
            }

            case CONSTANT_DATA:
                scope.addSymbol(AggregateConstant.createFromData(type, buffer), type);
                return;

            case CONSTANT_INLINEASM_OLD2:
                scope.addSymbol(InlineAsmConstant.createFromData(type, null, buffer), type);
                return;
            case CONSTANT_INLINEASM:
                final Type fnType = types.get(buffer.read());
                scope.addSymbol(InlineAsmConstant.createFromData(type, fnType, buffer), type);
                return;

            case CONSTANT_CE_GEP_OLD:
            case CONSTANT_CE_INBOUNDS_GEP:
            case CONSTANT_CE_GEP_WITH_INRANGE_INDEX_OLD:
            case CONSTANT_CE_GEP_WITH_INRANGE:
            case CONSTANT_CE_GEP:
                createGetElementPointerExpression(buffer);
                return;

            case CONSTANT_CE_SELECT: {
                final int condition = buffer.readInt();
                final int trueValue = buffer.readInt();
                final int falseValue = buffer.readInt();
                scope.addSymbol(SelectConstant.fromSymbols(scope.getSymbols(), type, condition, trueValue, falseValue), type);
                break;
            }

            default:
                throw new LLVMParserException("Unsupported opCode in constant block: " + opCode);
        }
    }

    private void createGetElementPointerExpression(RecordBuffer buffer) {
        int opCode = buffer.getId();
        Type gepType = null;
        if (opCode == CONSTANT_CE_GEP_WITH_INRANGE_INDEX_OLD ||
                        opCode == CONSTANT_CE_GEP_WITH_INRANGE ||
                        opCode == CONSTANT_CE_GEP || buffer.size() % 2 != 0) {
            gepType = types.get(buffer.read());
        }

        boolean isInbounds;
        // structure of this if on purpose kept very similar to the code in LLVMs BitcodeReader.cpp
        if (opCode == CONSTANT_CE_GEP_WITH_INRANGE_INDEX_OLD) {
            long op = buffer.read();
            isInbounds = (op & 0x1) != 0;
        } else if (opCode == CONSTANT_CE_GEP_WITH_INRANGE) {
            long op = buffer.read();
            // ignore nuw and nusw flags
            isInbounds = (op & 0x1) != 0;
            // ignore the range
            long bitWidth = buffer.read();
            buffer.skipConstantRange(bitWidth);
        } else if (opCode == CONSTANT_CE_GEP) {
            long op = buffer.read();
            isInbounds = (op & 0x1) != 0;
        } else {
            isInbounds = opCode == CONSTANT_CE_INBOUNDS_GEP;
        }

        buffer.skip(); // type of pointer
        int pointer = buffer.readInt();

        final int[] indices = new int[buffer.remaining() >> 1];
        for (int j = 0; j < indices.length; j++) {
            buffer.skip(); // index type
            indices[j] = buffer.readInt();
        }

        scope.addSymbol(GetElementPointerConstant.fromSymbols(scope.getSymbols(), type, gepType, pointer, indices, isInbounds), type);
    }
}
