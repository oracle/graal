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
package com.sun.max.asm.gen.cisc.ia32;

import static com.sun.max.asm.gen.cisc.ia32.IA32ModRMGroup.*;
import static com.sun.max.asm.gen.cisc.x86.AddressingMethodCode.*;
import static com.sun.max.asm.gen.cisc.x86.HexByte.*;
import static com.sun.max.asm.gen.cisc.x86.OperandCode.*;
import static com.sun.max.asm.gen.cisc.x86.OperandTypeCode.*;
import static com.sun.max.asm.gen.cisc.x86.RegisterOperandCode.*;
import static com.sun.max.asm.gen.cisc.x86.X86Opcode.*;
import static com.sun.max.asm.ia32.IA32GeneralRegister16.*;
import static com.sun.max.asm.ia32.IA32GeneralRegister32.*;
import static com.sun.max.asm.ia32.IA32GeneralRegister8.*;
import static com.sun.max.asm.x86.SegmentRegister.*;

import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.lang.*;

/**
 * Representation of the ...
 */
class OneByteOpcodeMap extends X86InstructionDescriptionCreator {

    private void create_low() {
        define(_00, "ADD", Eb, Gb);
        define(_01, "ADD", Ev, Gv);
        define(_02, "ADD", Gb, Eb);
        define(_03, "ADD", Gv, Ev);
        define(_04, "ADD", AL, Ib);
        define(_05, "ADD", eAX, Iv);
        define(_06, "PUSH", ES);
        define(_07, "POP", ES);

        define(_10, "ADC", Eb, Gb);
        define(_11, "ADC", Ev, Gv);
        define(_12, "ADC", Gb, Eb);
        define(_13, "ADC", Gv, Ev);
        define(_14, "ADC", AL, Ib);
        define(_15, "ADC", eAX, Iv);
        define(_16, "PUSH", SS);
        define(_17, "POP", SS);

        define(_20, "AND", Eb, Gb);
        define(_21, "AND", Ev, Gv);
        define(_22, "AND", Gb, Eb);
        define(_23, "AND", Gv, Ev);
        define(_24, "AND", AL, Ib);
        define(_25, "AND", eAX, Iv);
        define(SEG_ES, "SEG_ES").beNotExternallyTestable(); // prefix
        define(_27, "DAA");

        define(_30, "XOR", Eb, Gb);
        define(_31, "XOR", Ev, Gv);
        define(_32, "XOR", Gb, Eb);
        define(_33, "XOR", Gv, Ev);
        define(_34, "XOR", AL, Ib);
        define(_35, "XOR", eAX, Iv);
        define(SEG_SS, "SEG_SS").beNotExternallyTestable(); // prefix
        define(_37, "AAA");

        define(_40, "INC", Nv);

        define(_50, "PUSH", Nv);

        define(_60, "PUSHA").requireOperandSize(WordWidth.BITS_16).beNotExternallyTestable(); // gas does not emit the operand size prefix
        define(_60, "PUSHAD").requireOperandSize(WordWidth.BITS_32).setExternalName("pusha");
        define(_61, "POPA").requireOperandSize(WordWidth.BITS_16).beNotExternallyTestable(); // gas does not emit the operand size prefix
        define(_61, "POPAD").requireOperandSize(WordWidth.BITS_32).setExternalName("popa");
        define(_62, "BOUND", Gv, Ma).revertExternalOperandOrdering();
        define(_63, "ARPL", Ew, Gw);
        define(SEG_FS, "SEG_FS").beNotExternallyTestable(); // prefix
        define(SEG_GS, "SEG_GS").beNotExternallyTestable(); // prefix
        define(OPERAND_SIZE, "OPERAND_SIZE").beNotDisassemblable().beNotExternallyTestable(); // prefix
        define(ADDRESS_SIZE, "ADDRESS_SIZE").beNotDisassemblable().beNotExternallyTestable(); // prefix

        define(_70, "JO", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_71, "JNO", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_72, "JB", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_73, "JNB", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_74, "JZ", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_75, "JNZ", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_76, "JBE", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_77, "JNBE", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets

        define(_80, GROUP_1, b, Eb.excludeExternalTestArguments(AL), Ib);
        define(_81, GROUP_1, v, Ev.excludeExternalTestArguments(AX, EAX), Iv);
        define(_82, GROUP_1, b, Eb.excludeExternalTestArguments(AL), Ib);
        define(_83, GROUP_1, v, Ev, Ib).beNotExternallyTestable();
        define(_84, "TEST", Eb, Gb).revertExternalOperandOrdering();
        define(_85, "TEST", Ev, Gv).revertExternalOperandOrdering();
        define(_86, "XCHG", Eb, Gb);
        define(_87, "XCHG", Ev.excludeExternalTestArguments(AX, EAX), Gv.excludeExternalTestArguments(AX, EAX));

        define(_90, "NOP");
        define(_90, "XCHG", Nv.excludeDisassemblerTestArguments(AX, EAX), eAX);

        define(_A0, "MOV", AL, Ob);
        define(_A1, "MOV", eAX, Ov);
        define(_A2, "MOV", Ob, AL);
        define(_A3, "MOV", Ov, eAX);
        define(_A4, "MOVS", Yb, Xb);
        define(_A5, "MOVS", Yv, Xv);
        define(_A6, "CMPS", Yb, Xb);
        define(_A7, "CMPS", Yv, Xv);

        define(_B0, "MOV", Nb, Ib);

        define(_C0, GROUP_2, b, Eb, Ib);
        define(_C1, GROUP_2, v, Ev, Ib);
        define(_C2, "RET", Iw);
        define(_C3, "RET");
        define(_C4, "LES", Gv, Mp);
        define(_C5, "LDS", Gv, Mp);
        define(_C6, "MOV", b, Eb.excludeExternalTestArguments(AL, CL, DL, BL, AH, CH, DH, BH), Ib);
        define(_C7, "MOV", v, Ev.excludeExternalTestArguments(AX, CX, DX, BX, SP, BP, SI, DI, EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI), Iv);

        define(_D0, GROUP_2, b, Eb, 1);
        define(_D1, GROUP_2, v, Ev, 1);
        define(_D2, GROUP_2, b, Eb, CL);
        define(_D3, GROUP_2, v, Ev, CL);
        define(_D4, "AAM", Ib.range(0, 0x7f));
        define(_D5, "AAD", Ib.range(0, 0x7f));
        define(_D6, "SALC").beNotExternallyTestable(); // missing in gas (undocumented by Intel)
        define(_D7, "XLAT");

        define(_E0, "LOOPNE", Jb);
        define(_E1, "LOOPE", Jb);
        define(_E2, "LOOP", Jb);
        define(_E3, "JCXZ", Jb).requireAddressSize(WordWidth.BITS_16);
        define(_E3, "JECXZ", Jb).requireAddressSize(WordWidth.BITS_32);
        define(_E4, "IN", AL, Ib);
        define(_E5, "IN", eAX, Ib);
        define(_E6, "OUT", Ib, AL);
        define(_E7, "OUT", Ib, eAX);

        define(LOCK, "LOCK").beNotExternallyTestable(); // prefix
        define(REPNE, "REPNE").beNotExternallyTestable(); // prefix
        define(REPE, "REPE").beNotExternallyTestable(); // prefix
        define(_F4, "HLT");
        define(_F5, "CMC");
        define(_F6, GROUP_3b, b);
        define(_F7, GROUP_3v, v);
    }

    private void create_high() {
        define(_08, "OR", Eb, Gb);
        define(_09, "OR", Ev, Gv);
        define(_0A, "OR", Gb, Eb);
        define(_0B, "OR", Gv, Ev);
        define(_0C, "OR", AL, Ib);
        define(_0D, "OR", eAX, Iv);
        define(_0E, "PUSH", CS);

        define(_18, "SBB", Eb, Gb);
        define(_19, "SBB", Ev, Gv);
        define(_1A, "SBB", Gb, Eb);
        define(_1B, "SBB", Gv, Ev);
        define(_1C, "SBB", AL, Ib);
        define(_1D, "SBB", eAX, Iv);
        define(_1E, "PUSH", DS);
        define(_1F, "POP", DS);

        define(_28, "SUB", Eb, Gb);
        define(_29, "SUB", Ev, Gv);
        define(_2A, "SUB", Gb, Eb);
        define(_2B, "SUB", Gv, Ev);
        define(_2C, "SUB", AL, Ib);
        define(_2D, "SUB", eAX, Iv);
        define(SEG_CS, "SEG_CS").beNotExternallyTestable(); // prefix
        define(_2F, "DAS");

        define(_38, "CMP", Eb, Gb);
        define(_39, "CMP", Ev, Gv);
        define(_3A, "CMP", Gb, Eb);
        define(_3B, "CMP", Gv, Ev);
        define(_3C, "CMP", AL, Ib);
        define(_3D, "CMP", eAX, Iv);
        define(SEG_DS, "SEG_DS").beNotExternallyTestable(); // prefix
        define(_3F, "AAS");

        define(_48, "DEC", Nv);

        define(_58, "POP", Nv);

        define(_68, "PUSH", Iv);
        define(_69, "IMUL", Gv, Ev, Iv);
        define(_6A, "PUSH", Ib.externalRange(0, 0x7f));
        define(_6B, "IMUL", Gv, Ev, Ib.externalRange(0, 0x7f));
        define(_6C, "INS", Yb);
        define(_6D, "INS", Yv);
        define(_6E, "OUTS", Xb);
        define(_6F, "OUTS", Xv);

        define(_78, "JS", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_79, "JNS", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_7A, "JP", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_7B, "JNP", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_7C, "JL", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_7D, "JNL", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_7E, "JLE", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets
        define(_7F, "JNLE", Jb).beNotExternallyTestable(); // Darwin's gas always uses 16 bit offsets

        define(_88, "MOV", Eb, Gb.excludeExternalTestArguments(AL));
        define(_89, "MOV", Ev, Gv.excludeExternalTestArguments(AX, EAX));
        define(_8A, "MOV", Gb.excludeExternalTestArguments(AL), Eb);
        define(_8B, "MOV", Gv.excludeExternalTestArguments(AX, EAX), Ev);
        define(_8C, "MOV", Ew.excludeExternalTestArguments(AX, CX, DX, BX, SP, BP, SI, DI), Sw); // gas may needlessly insert OPERAND_SIZE prefix
        define(_8D, "LEA", Gv, M);
        define(_8E, "MOV", Sw, Ew.excludeExternalTestArguments(AX, CX, DX, BX, SP, BP, SI, DI)); // gas may needlessly insert OPERAND_SIZE prefix
        define(_8F, "POP", Ev.excludeExternalTestArguments(AX, CX, DX, BX, SP, BP, SI, DI, EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI));

        define(_98, "CBW").requireOperandSize(WordWidth.BITS_16);
        define(_98, "CWDE").requireOperandSize(WordWidth.BITS_32);
        define(_99, "CWD").requireOperandSize(WordWidth.BITS_16);
        define(_99, "CDQ").requireOperandSize(WordWidth.BITS_32);
        define(_9A, "CALL", Ap);
        define(FWAIT, "FWAIT"); // 'wait' is a Java keyword, so we use the alternate mnemonic, which is more accurately named anyhow
        define(_9C, "PUSHF", v, Fv);
        define(_9D, "POPF", v, Fv);
        define(_9E, "SAHF");
        define(_9F, "LAHF");

        define(_A8, "TEST", AL, Ib);
        define(_A9, "TEST", eAX, Iv);
        define(_AA, "STOS", Yb);
        define(_AB, "STOS", Yv);
        define(_AC, "LODS", Xb);
        define(_AD, "LODS", Xv);
        define(_AE, "SCAS", Yb);
        define(_AF, "SCAS", Yv);

        define(_B8, "MOV", Nv, Iv);

        define(_C8, "ENTER", Iw, Ib).revertExternalOperandOrdering();
        define(_C9, "LEAVE");
        define(_CA, "RETF", Iw).beNotExternallyTestable(); // gas does not support segments
        define(_CB, "RETF").beNotExternallyTestable(); // gas does not support segments
        define(_CC, "INT", 3);
        define(_CD, "INTb", Ib).setExternalName("int"); // suffix "b" to avoid clashing with Java keyword "int"
        define(_CE, "INTO");
        define(_CF, "IRET");

        define(_D8, FP_D8);
        define(_D9, FP_D9);
        define(_DA, FP_DA);
        define(_DB, FP_DB);
        define(_DC, FP_DC);
        define(_DD, FP_DD);
        define(_DE, FP_DE);
        define(_DF, FP_DF);

        define(_E8, "CALL", Jv);
        define(_E9, "JMP", Jv);
        define(_EA, "JMP", Ap);
        define(_EB, "JMP", Jb).beNotExternallyTestable(); // Darwin's gas uses always uses a 16-bit offset
        define(_EC, "IN", AL, DX);
        define(_ED, "IN", eAX, DX);
        define(_EE, "OUT", DX, AL);
        define(_EF, "OUT", DX, eAX);

        define(_F8, "CLC");
        define(_F9, "STC");
        define(_FA, "CLI");
        define(_FB, "STI");
        define(_FC, "CLD");
        define(_FD, "STD");
        define(_FE, GROUP_4, b, Eb);
        define(_FF, GROUP_5);
    }

    OneByteOpcodeMap() {
        super(IA32Assembly.ASSEMBLY);
        create_low();
        create_high();
    }
}
