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
package com.sun.max.asm.gen.cisc.amd64;

import static com.sun.max.asm.amd64.AMD64GeneralRegister16.*;
import static com.sun.max.asm.amd64.AMD64GeneralRegister32.*;
import static com.sun.max.asm.amd64.AMD64GeneralRegister64.*;
import static com.sun.max.asm.amd64.AMD64GeneralRegister8.*;
import static com.sun.max.asm.gen.cisc.x86.AddressingMethodCode.*;
import static com.sun.max.asm.gen.cisc.x86.FloatingPointOperandCode.*;
import static com.sun.max.asm.gen.cisc.x86.ModRMGroup.Opcode.*;
import static com.sun.max.asm.gen.cisc.x86.OperandCode.*;
import static com.sun.max.asm.gen.cisc.x86.OperandTypeCode.*;

import java.util.*;

import com.sun.max.asm.amd64.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.x86.*;

/**
 * See A-7 in the book.
 *
 * @see com.sun.max.asm.x86
 */
public enum AMD64ModRMGroup implements ModRMGroup {

    GROUP_1(
        modRM(ModRMGroup.Opcode.OPCODE_0, "ADD"),
        modRM(OPCODE_1, "OR"),
        modRM(OPCODE_2, "ADC"),
        modRM(OPCODE_3, "SBB"),
        modRM(OPCODE_4, "AND"),
        modRM(OPCODE_5, "SUB"),
        modRM(OPCODE_6, "XOR"),
        modRM(OPCODE_7, "CMP")
    ),
    GROUP_2(
        modRM(OPCODE_0, "ROL"),
        modRM(OPCODE_1, "ROR"),
        modRM(OPCODE_2, "RCL"),
        modRM(OPCODE_3, "RCR"),
        modRM(OPCODE_4, "SHL"),
        modRM(OPCODE_5, "SHR"),
        modRM(OPCODE_6, "SHL"),
        modRM(OPCODE_7, "SAR")
    ),
    GROUP_3b(
        modRM(OPCODE_0, "TEST", Eb.excludeExternalTestArguments(AL), Ib),
        modRM(OPCODE_1, "TEST", Eb.excludeExternalTestArguments(AL), Ib),
        modRM(OPCODE_2, "NOT", Eb),
        modRM(OPCODE_3, "NEG", Eb),
        modRM(OPCODE_4, "MUL", Eb, new ExternalOmission(AL)),
        modRM(OPCODE_5, "IMUL", Eb, new ExternalOmission(AL)),
        modRM(OPCODE_6, "DIV", Eb, new ExternalOmission(AL)),
        modRM(OPCODE_7, "IDIV", Eb, new ExternalOmission(AL))
    ),
    GROUP_3v(
        modRM(OPCODE_0, "TEST", Ev.excludeExternalTestArguments(AX, EAX, RAX), Iz.externalRange(0, Integer.MAX_VALUE)),
        modRM(OPCODE_1, "TEST", Ev.excludeExternalTestArguments(AX, EAX, RAX), Iz.externalRange(0, Integer.MAX_VALUE)),
        modRM(OPCODE_2, "NOT", Ev),
        modRM(OPCODE_3, "NEG", Ev),
        modRM(OPCODE_4, "MUL", Ev),
        modRM(OPCODE_5, "IMUL", Ev),
        modRM(OPCODE_6, "DIV", Ev),
        modRM(OPCODE_7, "IDIV", Ev)
    ),
    GROUP_4(
        modRM(OPCODE_0, "INC"),
        modRM(OPCODE_1, "DEC")
    ),
    GROUP_5a(
        modRM(OPCODE_0, "INC", v, Ev),
        modRM(OPCODE_1, "DEC", v, Ev)
        // modRM(_3, "CALL", Mp), // legacy mode instruction
        // modRM(_5, "JMP", Mp) // legacy mode instruction
    ),
    GROUP_5b(
        modRM(OPCODE_2, "CALL", Ev),
        modRM(OPCODE_4, "JMP", Ev),
        modRM(OPCODE_6, "PUSH", Ev.excludeExternalTestArguments(AMD64GeneralRegister16.ENUMERATOR, AMD64GeneralRegister32.ENUMERATOR, AMD64GeneralRegister64.ENUMERATOR))
    ),
    GROUP_6a(
        modRM(OPCODE_0, "SLDT", Mw),
        modRM(OPCODE_1, "STR", Mw),
        modRM(OPCODE_2, "LLDT", Ew),
        modRM(OPCODE_3, "LTR", Ew),
        modRM(OPCODE_4, "VERR", Ew),
        modRM(OPCODE_5, "VERW", Ew)
    ),
    GROUP_6b(
        modRM(OPCODE_0, "SLDT", Rv),
        modRM(OPCODE_1, "STR", Rv)
    ),
    GROUP_7a(
        modRM(OPCODE_0, "SGDT", Ms),
        modRM(OPCODE_1, "SIDT", Ms),
        modRM(OPCODE_2, "LGDT", Ms),
        modRM(OPCODE_3, "LIDT", Ms),
        modRM(OPCODE_4, "SMSW", Mw),
        modRM(OPCODE_6, "LMSW", Ew),
        modRM(OPCODE_7, "INVLPG", M)
    ),
    GROUP_7b(
        modRM(OPCODE_4, "SMSW", Rv),
        modRM(OPCODE_7, "SWAPGS", X86TemplateContext.ModCase.MOD_3)    // r/m field == 0
        // modRM(_7, "RDTSCP", X86TemplateContext.ModCase.MOD_3) // r/m field == 1
    ),
    GROUP_8(
        modRM(OPCODE_4, "BT"),
        modRM(OPCODE_5, "BTS"),
        modRM(OPCODE_6, "BTR"),
        modRM(OPCODE_7, "BTC")
    ),
    GROUP_9a(
        modRM(OPCODE_1, "CMPXCHG8B", Mq)
    ),
    GROUP_9b(
        modRM(OPCODE_1, "CMPXCHG16B", Mdq)
    ),
    GROUP_10(
    ),
    GROUP_11(
        modRM(OPCODE_0, "MOV", Eb, Ib),
        modRM(OPCODE_1, "MOV", Ev, Iz)
    ),
    GROUP_12a(
        modRM(OPCODE_2, "PSRLW", PRq, Ib),
        modRM(OPCODE_4, "PSRAW", PRq, Ib),
        modRM(OPCODE_6, "PSLLW", PRq, Ib)
    ),
    GROUP_12b(
        modRM(OPCODE_2, "PSRLW", VRdq, Ib),
        modRM(OPCODE_4, "PSRAW", VRdq, Ib),
        modRM(OPCODE_6, "PSLLW", VRdq, Ib)
    ),
    GROUP_13a(
        modRM(OPCODE_2, "PSRLD", PRq, Ib),
        modRM(OPCODE_4, "PSRAD", PRq, Ib),
        modRM(OPCODE_6, "PSLLD", PRq, Ib)
    ),
    GROUP_13b(
        modRM(OPCODE_2, "PSRLD", VRdq, Ib),
        modRM(OPCODE_4, "PSRAD", VRdq, Ib),
        modRM(OPCODE_6, "PSLLD", VRdq, Ib)
    ),
    GROUP_14a(
        modRM(OPCODE_2, "PSRLQ", PRq, Ib),
        modRM(OPCODE_6, "PSLLQ", PRq, Ib)
    ),
    GROUP_14b(
        modRM(OPCODE_2, "PSRLQ", VRdq, Ib),
        modRM(OPCODE_3, "PSRLDQ", VRdq, Ib),
        modRM(OPCODE_6, "PSLLQ", VRdq, Ib),
        modRM(OPCODE_7, "PSLLDQ", VRdq, Ib)
    ),
    GROUP_15a(
        modRM(OPCODE_0, "FXSAVE", M),
        modRM(OPCODE_1, "FXRSTOR", M),
        modRM(OPCODE_2, "LDMXCSR", Md),
        modRM(OPCODE_3, "STMXCSR", Md),
        modRM(OPCODE_7, "CLFLUSH", Mb)
    ),
    GROUP_15b(
        modRM(OPCODE_5, "LFENCE"),
        modRM(OPCODE_6, "MFENCE"),
        modRM(OPCODE_7, "SFENCE")
    ),
    GROUP_16(
        modRM(OPCODE_0, "PREFETCHNTA"),
        modRM(OPCODE_1, "PREFETCHT0"),
        modRM(OPCODE_2, "PREFETCHT1"),
        modRM(OPCODE_3, "PREFETCHT2")
    ),
    GROUP_P(
        modRM(OPCODE_0, "PREFETCH"),
        modRM(OPCODE_1, "PREFETCHW"),
        modRM(OPCODE_3, "PREFETCH")
    ),
    FP_D8(
        modRM(OPCODE_0, "FADD", single_real),
        modRM(OPCODE_1, "FMUL", single_real),
        modRM(OPCODE_2, "FCOM", single_real),
        modRM(OPCODE_3, "FCOMP", single_real),
        modRM(OPCODE_4, "FSUB", single_real),
        modRM(OPCODE_5, "FSUBR", single_real),
        modRM(OPCODE_6, "FDIV", single_real),
        modRM(OPCODE_7, "FDIVR", single_real)
    ),
    FP_D9(
        modRM(OPCODE_0, "FLD", single_real),
        modRM(OPCODE_2, "FST", single_real),
        modRM(OPCODE_3, "FSTP", single_real),
        modRM(OPCODE_4, "FLDENV", bytes_14_28),
        modRM(OPCODE_5, "FLDCW", bytes_2),
        modRM(OPCODE_6, "FSTENV", bytes_14_28),
        modRM(OPCODE_7, "FSTCW", bytes_2)
    ),
    FP_DA(
        modRM(OPCODE_0, "FIADD", short_integer),
        modRM(OPCODE_1, "FIMUL", short_integer),
        modRM(OPCODE_2, "FICOM", short_integer),
        modRM(OPCODE_3, "FICOMP", short_integer),
        modRM(OPCODE_4, "FISUB", short_integer),
        modRM(OPCODE_5, "FISUBR", short_integer),
        modRM(OPCODE_6, "FIDIV", short_integer),
        modRM(OPCODE_7, "FIDIVR", short_integer)
    ),
    FP_DB(
        modRM(OPCODE_0, "FILD", short_integer),
        modRM(OPCODE_2, "FIST", short_integer),
        modRM(OPCODE_3, "FISTP", short_integer),
        modRM(OPCODE_5, "FLD", extended_real),
        modRM(OPCODE_7, "FSTP", extended_real)
    ),
    FP_DC(
        modRM(OPCODE_0, "FADD", double_real),
        modRM(OPCODE_1, "FMUL", double_real),
        modRM(OPCODE_2, "FCOM", double_real),
        modRM(OPCODE_3, "FCOMP", double_real),
        modRM(OPCODE_4, "FSUB", double_real),
        modRM(OPCODE_5, "FSUBR", double_real),
        modRM(OPCODE_6, "FDIV", double_real),
        modRM(OPCODE_7, "FDIVR", double_real)
    ),
    FP_DD(
        modRM(OPCODE_0, "FLD", double_real),
        modRM(OPCODE_2, "FST", double_real),
        modRM(OPCODE_3, "FSTP", double_real),
        modRM(OPCODE_4, "FRSTOR", bytes_98_108),
        modRM(OPCODE_6, "FSAVE", bytes_98_108),
        modRM(OPCODE_7, "FSTSW", bytes_2)
    ),
    FP_DE(
        modRM(OPCODE_0, "FIADD", word_integer),
        modRM(OPCODE_1, "FIMUL", word_integer),
        modRM(OPCODE_2, "FICOM", word_integer),
        modRM(OPCODE_3, "FICOMP", word_integer),
        modRM(OPCODE_4, "FISUB", word_integer),
        modRM(OPCODE_5, "FISUBR", word_integer),
        modRM(OPCODE_6, "FIDIV", word_integer),
        modRM(OPCODE_7, "FIDIVR", word_integer)
    ),
    FP_DF(
        modRM(OPCODE_0, "FILD", word_integer),
        modRM(OPCODE_2, "FIST", word_integer),
        modRM(OPCODE_3, "FISTP", word_integer),
        modRM(OPCODE_4, "FBLD", packed_bcd),
        modRM(OPCODE_5, "FILD", long_integer),
        modRM(OPCODE_6, "FBSTP", packed_bcd),
        modRM(OPCODE_7, "FISTP", long_integer)
    );

    private static ModRMDescription modRM(ModRMGroup.Opcode opcode, String name, Object... specifications) {
        return new ModRMDescription(opcode, name, Arrays.asList(specifications));
    }

    private final ModRMDescription[] instructionDescriptions;

    private AMD64ModRMGroup(ModRMDescription... instructionDescriptions) {
        this.instructionDescriptions = instructionDescriptions;
    }

    public ModRMDescription getInstructionDescription(ModRMGroup.Opcode opcode) {
        for (ModRMDescription instructionDescription : instructionDescriptions) {
            if (instructionDescription.opcode() == opcode) {
                return instructionDescription;
            }
        }
        return null;
    }
}
