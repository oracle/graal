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
import com.oracle.truffle.llvm.parser.records.ConstantsRecord;
import com.oracle.truffle.llvm.parser.records.Records;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class Constants implements ParserListener {

    private static final BigInteger WIDE_INTEGER_MASK = BigInteger.ONE.shiftLeft(Long.SIZE).subtract(BigInteger.ONE);

    private final Types types;

    private final IRScope container;

    private Type type;

    Constants(Types types, IRScope container) {
        this.types = types;
        this.container = container;
    }

    @Override
    public void record(long id, long[] args) {
        ConstantsRecord record = ConstantsRecord.decode(id);

        switch (record) {
            case SETTYPE:
                type = types.get(args[0]);
                return;

            case NULL:
                if (Type.isIntegerType(type)) {
                    container.addSymbol(new NullConstant(type), Type.createConstantForType(type, 0));
                } else {
                    container.addSymbol(new NullConstant(type), type);
                }
                return;

            case UNDEF:
                container.addSymbol(new UndefinedConstant(type), type);
                return;

            case INTEGER: {
                long value = Records.toSignedValue(args[0]);
                container.addSymbol(new IntegerConstant(type, value), Type.createConstantForType(type, value));
                return;
            }
            case WIDE_INTEGER: {
                BigInteger value = BigInteger.ZERO;

                for (int i = 0; i < args.length; i++) {
                    BigInteger temp = BigInteger.valueOf(Records.toSignedValue(args[i]));
                    temp = temp.and(WIDE_INTEGER_MASK);
                    temp = temp.shiftLeft(i * Long.SIZE);
                    value = value.add(temp);
                }
                container.addSymbol(new BigIntegerConstant(type, value), Type.createConstantForType(type, value));
                return;
            }
            case FLOAT:
                container.addSymbol(FloatingPointConstant.create(type, args), type);
                return;

            case AGGREGATE: {
                container.addSymbol(Constant.createFromValues(type, container.getSymbols(), Records.toIntegers(args)), type);
                return;
            }
            case STRING:
                container.addSymbol(new StringConstant(type, Records.toString(args), false), type);
                return;

            case CSTRING:
                container.addSymbol(new StringConstant(type, Records.toString(args), true), type);
                return;

            case CE_BINOP: {
                int opCode = (int) args[0];
                int lhs = (int) args[1];
                int rhs = (int) args[2];
                container.addSymbol(BinaryOperationConstant.fromSymbols(container.getSymbols(), type, opCode, lhs, rhs), type);
                return;
            }

            case CE_CAST: {
                int opCode = (int) args[0];
                int value = (int) args[2];
                container.addSymbol(CastConstant.fromSymbols(container.getSymbols(), type, opCode, value), type);
                return;
            }

            case CE_CMP: {
                int i = 1;
                int lhs = (int) args[i++];
                int rhs = (int) args[i++];
                int opcode = (int) args[i];

                container.addSymbol(CompareConstant.fromSymbols(container.getSymbols(), type, opcode, lhs, rhs), type);
                return;
            }

            case BLOCKADDRESS: {
                int function = (int) args[1];
                int block = (int) args[2];
                container.addSymbol(BlockAddressConstant.fromSymbols(container.getSymbols(), type, function, block), type);
                return;
            }

            case DATA:
                container.addSymbol(Constant.createFromData(type, args), type);
                return;

            case INLINEASM:
                container.addSymbol(InlineAsmConstant.generate(type, args), type);
                return;

            case CE_GEP:
            case CE_INBOUNDS_GEP:
            case CE_GEP_WITH_INRANGE_INDEX:
                createGetElementPointerExpression(args, record);
                return;

            default:
                throw new UnsupportedOperationException("Unsupported Constant Record: " + record);
        }
    }

    private void createGetElementPointerExpression(long[] args, ConstantsRecord record) {
        int i = 0;
        if (record == ConstantsRecord.CE_GEP_WITH_INRANGE_INDEX || args.length % 2 != 0) {
            i++; // type of pointee
        }

        boolean isInbounds;
        if (record == ConstantsRecord.CE_GEP_WITH_INRANGE_INDEX) {
            final long op = args[i++];
            isInbounds = (op & 0x1) != 0;
        } else {
            isInbounds = record == ConstantsRecord.CE_INBOUNDS_GEP;
        }

        i++; // type of pointer
        int pointer = (int) args[i++];

        final int[] indices = new int[(args.length - i) >> 1];
        for (int j = 0; j < indices.length; j++) {
            i++; // index type
            indices[j] = (int) args[i++];
        }

        container.addSymbol(GetElementPointerConstant.fromSymbols(container.getSymbols(), type, pointer, indices, isInbounds), type);
    }
}
