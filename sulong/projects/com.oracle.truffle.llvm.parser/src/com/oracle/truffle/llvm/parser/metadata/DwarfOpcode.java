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
package com.oracle.truffle.llvm.parser.metadata;

import java.math.BigInteger;
import java.util.ArrayDeque;

public final class DwarfOpcode {

    public static final long ADDR = 0x3;
    public static final long DEREF = 0x6;
    public static final long CONST1U = 0x8;
    public static final long CONST1S = 0x9;
    public static final long CONST2U = 0xa;
    public static final long CONST2S = 0xb;
    public static final long CONST4U = 0xc;
    public static final long CONST4S = 0xd;
    public static final long CONST8U = 0xe;
    public static final long CONST8S = 0xf;
    public static final long CONSTU = 0x10;
    public static final long CONSTS = 0x11;
    public static final long DUP = 0x12;
    public static final long DROP = 0x13;
    public static final long OVER = 0x14;
    public static final long PICK = 0x15;
    public static final long SWAP = 0x16;
    public static final long ROT = 0x17;
    public static final long XDEREF = 0x18;
    public static final long ABS = 0x19;
    public static final long AND = 0x1a;
    public static final long DIV = 0x1b;
    public static final long MINUS = 0x1c;
    public static final long MOD = 0x1d;
    public static final long MUL = 0x1e;
    public static final long NEG = 0x1f;
    public static final long NOT = 0x20;
    public static final long OR = 0x21;
    public static final long PLUS = 0x22;
    public static final long PLUS_UCONST = 0x23;
    public static final long SHL = 0x24;
    public static final long SHR = 0x25;
    public static final long SHRA = 0x26;
    public static final long XOR = 0x27;
    public static final long BRA = 0x28;
    public static final long EQ = 0x29;
    public static final long GE = 0x2a;
    public static final long GT = 0x2b;
    public static final long LE = 0x2c;
    public static final long LT = 0x2d;
    public static final long NE = 0x2e;
    public static final long SKIP = 0x2f;
    public static final long LIT0 = 0x30;
    public static final long LIT1 = 0x31;
    public static final long LIT2 = 0x32;
    public static final long LIT3 = 0x33;
    public static final long LIT4 = 0x34;
    public static final long LIT5 = 0x35;
    public static final long LIT6 = 0x36;
    public static final long LIT7 = 0x37;
    public static final long LIT8 = 0x38;
    public static final long LIT9 = 0x39;
    public static final long LIT10 = 0x3a;
    public static final long LIT11 = 0x3b;
    public static final long LIT12 = 0x3c;
    public static final long LIT13 = 0x3d;
    public static final long LIT14 = 0x3e;
    public static final long LIT15 = 0x3f;
    public static final long LIT16 = 0x40;
    public static final long LIT17 = 0x41;
    public static final long LIT18 = 0x42;
    public static final long LIT19 = 0x43;
    public static final long LIT20 = 0x44;
    public static final long LIT21 = 0x45;
    public static final long LIT22 = 0x46;
    public static final long LIT23 = 0x47;
    public static final long LIT24 = 0x48;
    public static final long LIT25 = 0x49;
    public static final long LIT26 = 0x4a;
    public static final long LIT27 = 0x4b;
    public static final long LIT28 = 0x4c;
    public static final long LIT29 = 0x4d;
    public static final long LIT30 = 0x4e;
    public static final long LIT31 = 0x4f;
    public static final long REG0 = 0x50;
    public static final long REG1 = 0x51;
    public static final long REG2 = 0x52;
    public static final long REG3 = 0x53;
    public static final long REG4 = 0x54;
    public static final long REG5 = 0x55;
    public static final long REG6 = 0x56;
    public static final long REG7 = 0x57;
    public static final long REG8 = 0x58;
    public static final long REG9 = 0x59;
    public static final long REG10 = 0x5a;
    public static final long REG11 = 0x5b;
    public static final long REG12 = 0x5c;
    public static final long REG13 = 0x5d;
    public static final long REG14 = 0x5e;
    public static final long REG15 = 0x5f;
    public static final long REG16 = 0x60;
    public static final long REG17 = 0x61;
    public static final long REG18 = 0x62;
    public static final long REG19 = 0x63;
    public static final long REG20 = 0x64;
    public static final long REG21 = 0x65;
    public static final long REG22 = 0x66;
    public static final long REG23 = 0x67;
    public static final long REG24 = 0x68;
    public static final long REG25 = 0x69;
    public static final long REG26 = 0x6a;
    public static final long REG27 = 0x6b;
    public static final long REG28 = 0x6c;
    public static final long REG29 = 0x6d;
    public static final long REG30 = 0x6e;
    public static final long REG31 = 0x6f;
    public static final long BREG0 = 0x70;
    public static final long BREG1 = 0x71;
    public static final long BREG2 = 0x72;
    public static final long BREG3 = 0x73;
    public static final long BREG4 = 0x74;
    public static final long BREG5 = 0x75;
    public static final long BREG6 = 0x76;
    public static final long BREG7 = 0x77;
    public static final long BREG8 = 0x78;
    public static final long BREG9 = 0x79;
    public static final long BREG10 = 0x7a;
    public static final long BREG11 = 0x7b;
    public static final long BREG12 = 0x7c;
    public static final long BREG13 = 0x7d;
    public static final long BREG14 = 0x7e;
    public static final long BREG15 = 0x7f;
    public static final long BREG16 = 0x80;
    public static final long BREG17 = 0x81;
    public static final long BREG18 = 0x82;
    public static final long BREG19 = 0x83;
    public static final long BREG20 = 0x84;
    public static final long BREG21 = 0x85;
    public static final long BREG22 = 0x86;
    public static final long BREG23 = 0x87;
    public static final long BREG24 = 0x88;
    public static final long BREG25 = 0x89;
    public static final long BREG26 = 0x8a;
    public static final long BREG27 = 0x8b;
    public static final long BREG28 = 0x8c;
    public static final long BREG29 = 0x8d;
    public static final long BREG30 = 0x8e;
    public static final long BREG31 = 0x8f;
    public static final long REGX = 0x90;
    public static final long FBREG = 0x91;
    public static final long BREGX = 0x92;
    public static final long PIECE = 0x93;
    public static final long DEREF_SIZE = 0x94;
    public static final long XDEREF_SIZE = 0x95;
    public static final long NOP = 0x96;
    public static final long PUSH_OBJECT_ADDRESS = 0x97;
    public static final long CALL2 = 0x98;
    public static final long CALL4 = 0x99;
    public static final long CALL_REF = 0x9a;
    public static final long FORM_TLS_ADDRESS = 0x9b;
    public static final long CALL_FRAME_CFA = 0x9c;
    public static final long BIT_PIECE = 0x9d;
    public static final long IMPLICIT_VALUE = 0x9e;
    public static final long STACK_VALUE = 0x9f;
    public static final long IMPLICIT_POlongER = 0xa0;
    public static final long ADDRX = 0xa1;
    public static final long CONSTX = 0xa2;
    public static final long ENTRY_VALUE = 0xa3;
    public static final long CONST_TYPE = 0xa4;
    public static final long REGVAL_TYPE = 0xa5;
    public static final long DEREF_TYPE = 0xa6;
    public static final long XDEREF_TYPE = 0xa7;
    public static final long CONVERT = 0xa8;
    public static final long REINTERPRET = 0xa9;
    public static final long GNU_PUSH_TLS_ADDRESS = 0xe0;
    public static final long GNU_ADDR_INDEX = 0xfb;
    public static final long GNU_CONST_INDEX = 0xfc;
    public static final long LLVM_FRAGMENT = 0x1000;

    public static int numElements(long op) {
        if (op == PLUS_UCONST || op == PLUS || op == MINUS || (op >= CONST1U && op <= CONSTS)) {
            return 2;
        } else if (op == LLVM_FRAGMENT || op == BIT_PIECE) {
            return 3;
        }
        return 1;
    }

    public static boolean hasOp(MDExpression expression, long operand) {
        final int elementCount = expression.getElementCount();
        int i = 0;
        while (i < elementCount) {
            final long op = expression.getOperand(i);
            if (op == operand) {
                return true;
            }
            i += numElements(op);
        }
        return false;
    }

    public static boolean isDeref(MDExpression expression) {
        return hasOp(expression, DEREF);
    }

    public static BigInteger toIntegerSymbol(MDExpression exp) {
        final ArrayDeque<BigInteger> dwStack = new ArrayDeque<>(4);

        int i = 0;
        while (i < exp.getElementCount()) {
            final long op = exp.getOperand(i++);

            if (op >= LIT0 && op <= LIT31) {
                dwStack.push(BigInteger.valueOf(op - LIT0));

            } else if (op >= CONST1U && op <= CONSTS) {
                if (i >= exp.getElementCount()) {
                    return null;
                }

                final long arg = exp.getOperand(i++);
                BigInteger res;
                switch ((int) op) {
                    case (int) CONST1S:
                        res = BigInteger.valueOf((byte) arg);
                        break;

                    case (int) CONST1U:
                        res = BigInteger.valueOf(arg & 0xff);
                        break;

                    case (int) CONST2S:
                        res = BigInteger.valueOf((short) arg);
                        break;

                    case (int) CONST2U:
                        res = BigInteger.valueOf(arg & 0xffff);
                        break;

                    case (int) CONST4S:
                        res = BigInteger.valueOf((int) arg);
                        break;

                    case (int) CONST4U:
                        res = BigInteger.valueOf(arg & 0xffffffffL);
                        break;

                    case (int) CONST8S:
                        res = BigInteger.valueOf(arg);
                        break;

                    case (int) CONST8U:
                        res = new BigInteger(Long.toUnsignedString(arg));
                        break;

                    case (int) CONSTS:
                    case (int) CONSTU: {
                        // in practice, CONSTU is used also for signed value and of max 64bit
                        res = BigInteger.valueOf(arg);
                        break;
                    }

                    default:
                        return null;
                }

                dwStack.push(res);

            } else if (op == STACK_VALUE) {
                return !dwStack.isEmpty() ? dwStack.getFirst() : null;

            } else {
                // currently unsupported operation like PLUS_CONSTU which may expect the current
                // value of the source-level symbol to be on the expression stack
                return null;
            }
        }

        return null;

    }

    private DwarfOpcode() {
    }
}
