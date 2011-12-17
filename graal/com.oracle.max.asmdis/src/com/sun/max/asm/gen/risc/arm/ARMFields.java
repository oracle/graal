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

package com.sun.max.asm.gen.risc.arm;

import static com.sun.max.asm.arm.GPR.*;

import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.arm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.lang.*;

/**
 * The fields used in defining ARM instruction templates.
 */

public final class ARMFields {

    private ARMFields() {
    }

    public static final SymbolicOperandField<ConditionCode> cond = SymbolicOperandField.createDescending("cond", ConditionCode.SYMBOLIZER, 31, 28);
    public static final SymbolicOperandField<SBit> s = SymbolicOperandField.createDescending(SBit.SYMBOLIZER, 20, 20);

    public static final ImmediateOperandField bits_27_26 = ImmediateOperandField.createDescending(27, 26);
    public static final ImmediateOperandField i = ImmediateOperandField.createDescending(25, 25);
    public static final ImmediateOperandField opcode = ImmediateOperandField.createDescending(24, 21);
    public static final ImmediateOperandField rotate_imm = ImmediateOperandField.createDescending(11, 8);
    public static final ImmediateOperandField immed_8 = ImmediateOperandField.createDescending(7, 0);
    public static final ImmediateOperandField shifter_operand = ImmediateOperandField.createDescending(11, 0);
    public static final ImmediateOperandField bits_11_7 = ImmediateOperandField.createDescending(11, 7);
    public static final ImmediateOperandField bits_6_4 = ImmediateOperandField.createDescending(6, 4);
    public static final ImmediateOperandField shift_imm = ImmediateOperandField.createDescending(11, 7);
    public static final ImmediateOperandField bits_7_4 = ImmediateOperandField.createDescending(7, 4);
    public static final ImmediateOperandField bits_11_4 = ImmediateOperandField.createDescending(11, 4);
    public static final ImmediateOperandField bits_4_0 = ImmediateOperandField.createDescending(4, 0);
    public static final ImmediateOperandField sbz_19_16 = ImmediateOperandField.createDescending(19, 16);
    public static final ImmediateOperandField sbz_15_12 = ImmediateOperandField.createDescending(15, 12);
    public static final ImmediateOperandField sbz_11_0 = ImmediateOperandField.createDescending(11, 0);
    public static final ImmediateOperandField sbz_11_8 = ImmediateOperandField.createDescending(11, 8);
    public static final ImmediateOperandField sbo_19_16 = ImmediateOperandField.createDescending(19, 16);
    public static final ImmediateOperandField sbo_11_8 = ImmediateOperandField.createDescending(11, 8);
    public static final ImmediateOperandField bits_5_0 = new ImmediateOperandField(new DescendingBitRange(5, 0)) {
        @Override
        public ArgumentRange argumentRange() {
            return new ArgumentRange(this, 0, 32);
        }
    };
    public static final ImmediateOperandField bits_31_0 = new ImmediateOperandField(new DescendingBitRange(31, 0)) {
        @Override
        public Iterable< ? extends Argument> getIllegalTestArguments() {
            final List<Immediate32Argument> illegalTestArguments = new ArrayList<Immediate32Argument>();
            illegalTestArguments.add(new Immediate32Argument(0x101));
            illegalTestArguments.add(new Immediate32Argument(0x102));
            illegalTestArguments.add(new Immediate32Argument(0xff1));
            illegalTestArguments.add(new Immediate32Argument(0xff04));
            illegalTestArguments.add(new Immediate32Argument(0xff003));
            illegalTestArguments.add(new Immediate32Argument(0xf000001f));
            return illegalTestArguments;
        }
        @Override
        public Iterable< ? extends Argument> getLegalTestArguments() {
            final List<Immediate32Argument> legalTestArguments = new ArrayList<Immediate32Argument>();
            int argument;
            for (int imm : new int[]{0, 1, 31, 32, 33, 63, 64, 65, 127, 128, 129, 254, 255}) {
                for (int j = 0; j < 32; j += 2) {
                    argument = Integer.rotateLeft(imm, j);
                    final Immediate32Argument immediate32Argument = new Immediate32Argument(argument);
                    if (!legalTestArguments.contains(immediate32Argument)) {
                        legalTestArguments.add(immediate32Argument);
                    }
                }
            }
            return legalTestArguments;
        }
        @Override
        public ArgumentRange argumentRange() {
            return new ArgumentRange(this, 0x80000000, 0x7fffffff);
        }
    };
    public static final ImmediateOperandField bits_27_21 = ImmediateOperandField.createDescending(27, 21);
    public static final ImmediateOperandField bits_27_20 = ImmediateOperandField.createDescending(27, 20);
    public static final ImmediateOperandField bit_27 = ImmediateOperandField.createDescending(27, 27);
    public static final ImmediateOperandField bit_26 = ImmediateOperandField.createDescending(26, 26);
    public static final ImmediateOperandField bit_25 = ImmediateOperandField.createDescending(25, 25);
    public static final ImmediateOperandField bit_24 = ImmediateOperandField.createDescending(24, 24);
    public static final ImmediateOperandField bit_23 = ImmediateOperandField.createDescending(23, 23);
    public static final ImmediateOperandField r = ImmediateOperandField.createDescending(22, 22);
    public static final ImmediateOperandField bit_21 = ImmediateOperandField.createDescending(21, 21);
    public static final ImmediateOperandField bit_20 = ImmediateOperandField.createDescending(20, 20);
    public static final ImmediateOperandField bit_4 = ImmediateOperandField.createDescending(4, 4);
    public static final ImmediateOperandField p = ImmediateOperandField.createDescending(24, 24);
    public static final ImmediateOperandField u = ImmediateOperandField.createDescending(23, 23);
    public static final ImmediateOperandField b = ImmediateOperandField.createDescending(22, 22);
    public static final ImmediateOperandField w = ImmediateOperandField.createDescending(21, 21);
    public static final ImmediateOperandField l = ImmediateOperandField.createDescending(20, 20);
    public static final ImmediateOperandField offset_12 = ImmediateOperandField.createDescending(11, 0);
    public static final ImmediateOperandField shift = ImmediateOperandField.createDescending(6, 5);
    public static final ImmediateOperandField bits_31_28 = ImmediateOperandField.createDescending(31, 28);
    public static final ImmediateOperandField immed_19_8 = ImmediateOperandField.createDescending(19, 8);
    public static final ImmediateOperandField immed_3_0 = ImmediateOperandField.createDescending(3, 0);
    public static final ImmediateOperandField immed_24 = ImmediateOperandField.createDescending(23, 0);
    public static final ImmediateOperandField bits_27_24 = ImmediateOperandField.createDescending(27, 24);

    public static final InputOperandField immediate = InputOperandField.create(bits_31_0).setVariableName("immediate");
    public static final InputOperandField rotate_amount = InputOperandField.create(bits_4_0).setVariableName("rotate_amount");
    public static final InputOperandField shift_imm2 = (InputOperandField) InputOperandField.create(bits_5_0).withExcludedExternalTestArguments(new Immediate32Argument(0)).setVariableName("shift_imm");
    public static final InputOperandField immediate2 = InputOperandField.create(new ImmediateOperandField(new DescendingBitRange(15, 0))).setVariableName("immediate");

    public static RiscConstant s(int value) {
        return s.constant(value);
    }
    public static RiscConstant cond(int value) {
        return cond.constant(value);
    }
    public static RiscConstant bits_27_26(int value) {
        return bits_27_26.constant(value);
    }
    public static RiscConstant i(int value) {
        return i.constant(value);
    }
    public static RiscConstant opcode(int value) {
        return opcode.constant(value);
    }
    public static RiscConstant bits_11_7(int value) {
        return bits_11_7.constant(value);
    }
    public static RiscConstant bits_6_4(int value) {
        return bits_6_4.constant(value);
    }
    public static RiscConstant bits_7_4(int value) {
        return bits_7_4.constant(value);
    }
    public static RiscConstant bits_11_4(int value) {
        return bits_11_4.constant(value);
    }
    public static RiscConstant sbz_19_16(int value) {
        return sbz_19_16.constant(value);
    }
    public static RiscConstant sbz_15_12(int value) {
        return sbz_15_12.constant(value);
    }
    public static RiscConstant sbz_11_0(int value) {
        return sbz_11_0.constant(value);
    }
    public static RiscConstant sbz_11_8(int value) {
        return sbz_11_8.constant(value);
    }
    public static RiscConstant sbo_19_16(int value) {
        return sbo_19_16.constant(value);
    }
    public static RiscConstant sbo_11_8(int value) {
        return sbo_11_8.constant(value);
    }
    public static RiscConstant bits_27_21(int value) {
        return bits_27_21.constant(value);
    }
    public static RiscConstant bits_27_20(int value) {
        return bits_27_20.constant(value);
    }
    public static RiscConstant bit_27(int value) {
        return bit_27.constant(value);
    }
    public static RiscConstant bit_26(int value) {
        return bit_26.constant(value);
    }
    public static RiscConstant bit_25(int value) {
        return bit_25.constant(value);
    }
    public static RiscConstant bit_24(int value) {
        return bit_24.constant(value);
    }
    public static RiscConstant bit_23(int value) {
        return bit_23.constant(value);
    }
    public static RiscConstant bit_4(int value) {
        return bit_4.constant(value);
    }
    public static RiscConstant r(int value) {
        return r.constant(value);
    }
    public static RiscConstant bit_21(int value) {
        return bit_21.constant(value);
    }
    public static RiscConstant bit_20(int value) {
        return bit_20.constant(value);
    }
    public static RiscConstant p(int value) {
        return p.constant(value);
    }
    public static RiscConstant u(int value) {
        return u.constant(value);
    }
    public static RiscConstant b(int value) {
        return b.constant(value);
    }
    public static RiscConstant w(int value) {
        return w.constant(value);
    }
    public static RiscConstant l(int value) {
        return l.constant(value);
    }
    public static RiscConstant shift(int value) {
        return shift.constant(value);
    }
    public static RiscConstant shift_imm(int value) {
        return shift_imm.constant(value);
    }
    public static RiscConstant bits_31_28(int value) {
        return bits_31_28.constant(value);
    }
    public static RiscConstant bits_27_24(int value) {
        return bits_27_24.constant(value);
    }

    public static OperandField<ImmediateArgument> rotate_imm(Expression expression) {
        return rotate_imm.bindTo(expression);
    }
    public static OperandField<ImmediateArgument> shifter_operand(Expression expression) {
        return shifter_operand.bindTo(expression);
    }
    public static OperandField<ImmediateArgument> shift_imm(Expression expression) {
        return shift_imm.bindTo(expression);
    }
    public static OperandField<ImmediateArgument> immed_19_8(Expression expression) {
        return immed_19_8.bindTo(expression);
    }
    public static OperandField<ImmediateArgument> immed_3_0(Expression expression) {
        return immed_3_0.bindTo(expression);
    }

    public static final SymbolicOperandField<GPR> Rn = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 19, 16);
    public static final SymbolicOperandField<GPR> Rn2 = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 15, 12).setVariableName("Rn");
    public static final SymbolicOperandField<GPR> Rd = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 15, 12);
    public static final SymbolicOperandField<GPR> Rd2 = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 19, 16).setVariableName("Rd");
    public static final SymbolicOperandField<GPR> Rm = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 3, 0);
    public static final SymbolicOperandField<GPR> Rs = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 11, 8);
    public static final SymbolicOperandField<GPR> RdHi = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 19, 16);
    public static final SymbolicOperandField<GPR> RdLo = SymbolicOperandField.createDescending(GPR_SYMBOLIZER, 15, 12);

    static {
        StaticFieldName.Static.initialize(ARMFields.class, new StaticFieldName.StringFunction() {
            public String function(String name) {
                if (name.startsWith("_")) {
                    return name.substring(1);
                }
                return name;
            }
        });
    }

}
