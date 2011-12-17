/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.asm.gen.cisc.x86;

import static com.sun.max.asm.gen.cisc.x86.X86AssemblerGenerator.*;

import java.io.*;
import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.amd64.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * An assembler that creates binary instructions from templates and arguments.
 */
public class X86TemplateAssembler {

    private static final int MORE_BYTES_THAN_ANY_INSTRUCTION = 32;
    private final byte[] bytes = new byte[MORE_BYTES_THAN_ANY_INSTRUCTION];
    private int n;

    private int rexByte;

    private void emit(byte b) {
        bytes[n++] = b;
    }

    private void emit(int b) {
        bytes[n++] = (byte) (b & 0xff);
    }

    private void emit(HexByte b) {
        bytes[n++] = b.byteValue();
    }

    private int createRexData(int bitIndex, Argument argument, boolean unconditionalRexBit) {
        if (unconditionalRexBit) {
            return ((int) argument.asLong() & 8) >> (3 - bitIndex);
        }
        int rexByte = 0;
        if (argument instanceof AMD64GeneralRegister8) {
            final AMD64GeneralRegister8 reg8 = (AMD64GeneralRegister8) argument;
            if (reg8.requiresRexPrefix()) {
                rexByte |= basicRexValue(template);
                if (argument.asLong() >= 8) {
                    rexByte |= createRexData(bitIndex, argument.asLong());
                }
            }
        } else {
            if (argument.asLong() >= 8) {
                rexByte |= createRexData(bitIndex, argument.asLong()) + basicRexValue(template);
            }
        }
        return rexByte;
    }

    private int createRexData(int bitIndex, long argument) {
        final byte b = (byte) (argument & 0xffL);
        if (b == 0) {
            return 0;
        }
        return X86Field.inRexPlace(bitIndex, b);
    }

    private int createFieldData(X86Field field, long argument) {
        return field.inPlace((byte) (argument & field.mask));
    }

    private final X86Template template;
    private WordWidth addressWidth;

    public X86TemplateAssembler(X86Template template, WordWidth addressWidth) {
        this.template = template;
        this.addressWidth = addressWidth;
    }

    private int createModRMByte() {
        if (!template.hasModRMByte()) {
            return 0;
        }
        int result = template.modCase().ordinal() << X86Field.MOD.shift();
        if (template.modRMGroupOpcode() != null) {
            result |= template.modRMGroupOpcode().byteValue() << X86Field.REG.shift();
        }
        result |= template.rmCase().value() << X86Field.RM.shift();
        return result;
    }

    private int createSibByte() {
        if (template.hasSibByte() && template.sibBaseCase() == X86TemplateContext.SibBaseCase.SPECIAL) {
            return 5 << X86Field.BASE.shift();
        }
        return 0;
    }

    private boolean modRMRequiresSib(int modRMByte) {
        final byte m = (byte) modRMByte;
        return X86Field.MOD.extract(m) != 3 && X86Field.RM.extract(m) == 4;
    }

    private boolean modRMRequiresImmediate(int modRMByte) {
        final byte m = (byte) modRMByte;
        return X86Field.MOD.extract(m) == 0 && X86Field.RM.extract(m) == 5;
    }

    private boolean sibRequiresImmediate(int sibRMByte) {
        final byte s = (byte) sibRMByte;
        return X86Field.BASE.extract(s) == 5;
    }

    public byte[] assemble(List<Argument> arguments) {
        int rexByte = 0;
        final boolean unconditionalRexBit = template.operandSizeAttribute() == WordWidth.BITS_64 && template.instructionDescription().defaultOperandSize() != WordWidth.BITS_64;
        if (unconditionalRexBit) {
            rexByte = X86Opcode.REX_MIN.byteValue() | (1 << X86Field.REX_W_BIT_INDEX);
        }
        int opcode1 = template.opcode1().byteValue() & 0xff;
        int opcode2 = template.opcode2() == null ? 0 : template.opcode2().byteValue() & 0xff;
        int modRMByte = createModRMByte();
        int sibByte = createSibByte();
        final ByteArrayOutputStream appendStream = new ByteArrayOutputStream();
        for (int i = 0; i < arguments.size(); i++) {
            final X86Parameter parameter = template.parameters().get(i);
            final long argument = arguments.get(i).asLong();
            switch (parameter.place()) {
                case MOD_REG_REXR:
                    rexByte |= createRexData(X86Field.REX_R_BIT_INDEX, arguments.get(i), unconditionalRexBit);
                    // fall through...
                case MOD_REG:
                    modRMByte |= createFieldData(X86Field.REG, argument);
                    break;
                case MOD_RM_REXB:
                    rexByte |= createRexData(X86Field.REX_B_BIT_INDEX, arguments.get(i), unconditionalRexBit);
                    // fall through...
                case MOD_RM:
                    modRMByte |= createFieldData(X86Field.RM, argument);
                    break;
                case SIB_BASE_REXB:
                    rexByte |= createRexData(X86Field.REX_B_BIT_INDEX, arguments.get(i), unconditionalRexBit);
                    // fall through...
                case SIB_BASE:
                    sibByte |= createFieldData(X86Field.BASE, argument);
                    break;
                case SIB_INDEX_REXX:
                    rexByte |= createRexData(X86Field.REX_X_BIT_INDEX, arguments.get(i), unconditionalRexBit);
                    // fall through...
                case SIB_INDEX:
                    sibByte |= createFieldData(X86Field.INDEX, argument);
                    break;
                case SIB_SCALE:
                    sibByte |= createFieldData(X86Field.SCALE, argument);
                    break;
                case APPEND:
                    if (parameter instanceof X86EnumerableParameter) {
                        appendStream.write((byte) (argument & 0xffL));
                    } else {
                        try {
                            final X86NumericalParameter numericalParameter = (X86NumericalParameter) parameter;
                            switch (numericalParameter.width()) {
                                case BITS_8:
                                    appendStream.write((byte) (argument & 0xffL));
                                    break;
                                case BITS_16:
                                    Endianness.LITTLE.writeShort(appendStream, (short) (argument & 0xffffL));
                                    break;
                                case BITS_32:
                                    Endianness.LITTLE.writeInt(appendStream, (int) (argument & 0xffffffffL));
                                    break;
                                case BITS_64:
                                    Endianness.LITTLE.writeLong(appendStream, argument);
                                    break;
                            }
                        } catch (IOException ioException) {
                            throw ProgramError.unexpected();
                        }
                    }
                    break;
                case OPCODE1_REXB:
                    rexByte |= createRexData(X86Field.REX_B_BIT_INDEX, arguments.get(i), unconditionalRexBit);
                    // fall through...
                case OPCODE1:
                    opcode1 |= (int) argument & 7;
                    break;
                case OPCODE2_REXB:
                    rexByte |= createRexData(X86Field.REX_B_BIT_INDEX, arguments.get(i), unconditionalRexBit);
                    // fall through...
                case OPCODE2:
                    opcode2 |= (int) argument & 7;
                    break;
            }
        }
        if (rexByte > 0) {
            emit(rexByte);
        }
        if (template.addressSizeAttribute() != addressWidth) {
            emit(X86Opcode.ADDRESS_SIZE);
        }
        if (template.operandSizeAttribute() == WordWidth.BITS_16) {
            emit(X86Opcode.OPERAND_SIZE);
        }
        if (template.instructionSelectionPrefix() != null) {
            emit(template.instructionSelectionPrefix());
        }
        emit(opcode1);
        if (opcode2 != 0) {
            emit(opcode2);
        }
        if (template.hasModRMByte()) {
            emit(modRMByte);
            if (modRMRequiresImmediate(modRMByte) && appendStream.size() == 0) {
                return null;
            }
        }
        if (template.hasSibByte()) {
            if (sibRequiresImmediate(sibByte) && appendStream.size() == 0) {
                return null;
            }
            emit(sibByte);
        } else if (modRMRequiresSib(modRMByte)) {
            return null;
        }
        for (byte b : appendStream.toByteArray()) {
            emit(b);
        }
        return Bytes.withLength(bytes, n);
    }

}
