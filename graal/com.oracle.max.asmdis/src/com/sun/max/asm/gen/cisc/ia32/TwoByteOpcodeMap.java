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
import static com.sun.max.asm.gen.cisc.x86.HexByte.*;
import static com.sun.max.asm.gen.cisc.x86.OperandCode.*;
import static com.sun.max.asm.ia32.IA32GeneralRegister8.*;
import static com.sun.max.asm.x86.SegmentRegister.*;

import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.asm.ia32.*;
import com.sun.max.lang.*;

/**
 */
class TwoByteOpcodeMap extends X86InstructionDescriptionCreator {

    private void create_low() {
        define(_0F, _00, GROUP_6a);
        define(_0F, _00, GROUP_6b);
        define(_0F, _01, GROUP_7a);
        define(_0F, _01, GROUP_7b);
        define(_0F, _02, "LAR", Gv, Ev); // bug in table, wrongly suggesting Ew
        define(_0F, _03, "LSL", Gv, Ev); // bug in table, wrongly suggesting Ew
        define(_0F, _06, "CLTS");

        define(_0F, _10, "MOVUPS", Vps, Wps);
        define(_0F, _11, "MOVUPS", Wps, Vps);
        define(_0F, _12, "MOVHLPS", Vps, VRq);
        define(_0F, _13, "MOVLPS", Mq, Vps);
        define(_0F, _14, "UNPCKLPS", Vps, Wq);
        define(_0F, _15, "UNPCKHPS", Vps, Wq);
        define(_0F, _16, "MOVLHPS", Vps, VRq);
        define(_0F, _17, "MOVHPS", Mq, Vps);

        define(_66, _0F, _10, "MOVUPD", Vpd, Wpd);
        define(_66, _0F, _11, "MOVUPD", Wpd, Vpd);
        define(_66, _0F, _12, "MOVLPD", Vsd, Mq);
        define(_66, _0F, _13, "MOVLPD", Mq, Vsd);
        define(_66, _0F, _14, "UNPCKLPD", Vpd, Wq);
        define(_66, _0F, _15, "UNPCKHPD", Vpd, Wq);
        define(_66, _0F, _16, "MOVHPD", Vsd, Mq);
        define(_66, _0F, _17, "MOVHPD", Mq, Vsd);

        define(_F2, _0F, _10, "MOVSD", Vdq, Wsd);
        define(_F2, _0F, _10, "MOVSD", Vsd, Wsd);
        define(_F2, _0F, _11, "MOVSD", Wsd, Vsd);
        define(_F2, _0F, _12, "MOVDDUP", Vpd, Wsd);

        define(_F3, _0F, _10, "MOVSS", Vdq, Wss);
        define(_F3, _0F, _10, "MOVSS", Vss, Wss);
        define(_F3, _0F, _11, "MOVSS", Wss, Vss);
        define(_F3, _0F, _12, "MOVSLDUP", Vps, Wps);
        define(_F3, _0F, _16, "MOVSHDUP", Vps, Wps);

        define(_0F, _20, "MOV", Rd, Cd);
        define(_0F, _21, "MOV", Rd, Dd);
        define(_0F, _22, "MOV", Cd, Rd);

        define(_0F, _20, "MOV", Rd, Cd);
        define(_0F, _21, "MOV", Rd, Dd);
        define(_0F, _22, "MOV", Cd, Rd);
        define(_0F, _23, "MOV", Dd, Rd);

        define(_0F, _30, "WRMSR");
        define(_0F, _31, "RDTSC");
        define(_0F, _32, "RDMSR");
        define(_0F, _33, "RDPMC");

        define(_0F, _40, "CMOVO", Gv, Ev);
        define(_0F, _41, "CMOVNO", Gv, Ev);
        define(_0F, _42, "CMOVB", Gv, Ev);
        define(_0F, _43, "CMOVAE", Gv, Ev);
        define(_0F, _44, "CMOVE", Gv, Ev);
        define(_0F, _45, "CMOVNE", Gv, Ev);
        define(_0F, _46, "CMOVBE", Gv, Ev);
        define(_0F, _47, "CMOVA", Gv, Ev);

        define(_0F, _50, "MOVMSKPS", Gd, VRps);
        define(_0F, _51, "SQRTPS", Vps, Wps);
        define(_0F, _52, "RSQRTPS", Vps, Wps);
        define(_0F, _53, "RCPPS", Vps, Wps);
        define(_0F, _54, "ANDPS", Vps, Wps);
        define(_0F, _55, "ANDNPS", Vps, Wps);
        define(_0F, _56, "ORPS", Vps, Wps);
        define(_0F, _57, "XORPS", Vps, Wps);

        define(_66, _0F, _50, "MOVMSKPD", Gd, VRpd);
        define(_66, _0F, _51, "SQRTPD", Vpd, Wpd);
        define(_66, _0F, _54, "ANDPD", Vpd, Wpd);
        define(_66, _0F, _55, "ANDNPD", Vpd, Wpd);
        define(_66, _0F, _56, "ORPD", Vpd, Wpd);
        define(_66, _0F, _57, "XORPD", Vpd, Wpd);

        define(_F2, _0F, _51, "SQRTSD", Vsd, Wsd);

        define(_F3, _0F, _51, "SQRTSS", Vss, Wss);
        define(_F3, _0F, _52, "RSQRTSS", Vss, Wss);
        define(_F3, _0F, _53, "RCPSS", Vss, Wss);

        define(_0F, _60, "PUNPCKLBW", Pq, Qd);
        define(_0F, _61, "PUNPCKLWD", Pq, Qd);
        define(_0F, _62, "PUNPCKLDQ", Pq, Qd);

        define(_0F, _60, "PUNPCKLBW", Pq, Qd);
        define(_0F, _61, "PUNPCKLWD", Pq, Qd);
        define(_0F, _62, "PUNPCKLDQ", Pq, Qd);
        define(_0F, _63, "PACKSSWB", Pq, Qd);
        define(_0F, _64, "PCMPGTB", Pq, Qd);
        define(_0F, _65, "PCMPGTW", Pq, Qd);
        define(_0F, _66, "PCMPGTD", Pq, Qd);
        define(_0F, _67, "PACKUSWB", Pq, Qd);

        define(_0F, _70, "PSHUFW", Pq, Qd, Ib);
        define(_0F, _71, GROUP_11);
        define(_0F, _72, GROUP_12);
        define(_0F, _73, GROUP_13a);
        define(_0F, _74, "PCMPEQB", Pq, Qd);
        define(_0F, _75, "PCMPEQW", Pq, Qd);
        define(_0F, _76, "PCMPEQD", Pq, Qd);
        define(_0F, _77, "EMMS");

        define(_66, _0F, _60, "PUNPCKLBW", Vdq, Wq);
        define(_66, _0F, _61, "PUNPCKLWD", Vdq, Wq);
        define(_66, _0F, _62, "PUNPCKLDQ", Vdq, Wq);
        define(_66, _0F, _63, "PACKSSWB", Vdq, Wdq);
        define(_66, _0F, _64, "PCMPGTB", Vdq, Wdq);
        define(_66, _0F, _65, "PCMPGTW", Vdq, Wdq);
        define(_66, _0F, _66, "PCMPGTD", Vdq, Wdq);
        define(_66, _0F, _67, "PACKUSWB", Vdq, Wdq);

        define(_66, _0F, _73, GROUP_13b);
        define(_66, _0F, _76, "PCMPEQD", Vdq, Wdq);

        define(_0F, _80, "JO", Jv);
        define(_0F, _81, "JNO", Jv);
        define(_0F, _82, "JB", Jv);
        define(_0F, _83, "JNB", Jv);
        define(_0F, _84, "JZ", Jv);
        define(_0F, _85, "JNZ", Jv);
        define(_0F, _86, "JBE", Jv);
        define(_0F, _87, "JNBE", Jv);

        define(_0F, _90, "SETO", Eb);
        define(_0F, _91, "SETNO", Eb);
        define(_0F, _92, "SETB", Eb);
        define(_0F, _93, "SETNB", Eb);
        define(_0F, _94, "SETZ", Eb);
        define(_0F, _95, "SETNZ", Eb);
        define(_0F, _96, "SETBE", Eb);
        define(_0F, _97, "SETNBE", Eb);

        define(_0F, _A0, "PUSH", FS);
        define(_0F, _A1, "POP", FS);
        define(_0F, _A2, "CPUID");
        define(_0F, _A3, "BT", Ev, Gv);
        define(_0F, _A4, "SHLD", Ev, Gv, Ib);
        define(_0F, _A5, "SHLD", Ev, Gv, CL);
        //define(_0F, _A6, "CMPXCHG", Xb, Ts); // invalid opcode or undocumented instruction?
        //define(_0F, _A7, "CMPXCHG", Ib, Ts); // invalid opcode or undocumented instruction?

        define(_0F, _B0, "CMPXCHG", Eb, Gb);
        define(_0F, _B1, "CMPXCHG", Ev, Gv);
        define(_0F, _B2, "LSS", Gv, Mp); // bug in table: no Gv
        define(_0F, _B3, "BTR", Ev, Gv);
        define(_0F, _B4, "LFS", Gv, Mp); // bug in table: no Gv
        define(_0F, _B5, "LGS", Gv, Mp); // bug in table: no Gv
        define(_0F, _B6, "MOVZXB", Gv, Eb).setExternalName("movzx");
        define(_0F, _B7, "MOVZXW", Gv, Ew).beNotExternallyTestable(); // gas unnecessarily prepends the operand size prefix 0x66

        define(_0F, _C0, "XADD", Eb, Gb);
        define(_0F, _C1, "XADD", Ev, Gv);
        define(_0F, _C2, "CMPPS", Vps, Wps, ICb);
        define(_0F, _C3, "MOVNTI", Md_q, Gd);
        define(_0F, _C4, "PINSRW", Pq, Ed, Ib).requireOperandSize(WordWidth.BITS_32); // Ed instead of Ew to mimic intended Gd/Mw
        define(_0F, _C5, "PEXTRW", Gd, PRq, Ib);
        define(_0F, _C6, "SHUFPS", Vps, Wps, Ib);
        define(_0F, _C7, GROUP_9);

        define(_66, _0F, _C2, "CMPPD", Vpd, Wpd, ICb);
        define(_66, _0F, _C4, "PINSRW", Vdq, Ed, Ib).requireOperandSize(WordWidth.BITS_32); // Ed instead of Ew to mimic intended Gd/Mw
        define(_66, _0F, _C5, "PEXTRW", Gd, VRdq, Ib);
        define(_66, _0F, _C6, "SHUFPD", Vpd, Wpd, Ib);

        define(_F2, _0F, _C2, "CMPSD", Vsd, Wsd, ICb);

        define(_F3, _0F, _C2, "CMPSS", Vss, Wss, ICb);

        define(_0F, _C7, GROUP_9);

        define(_0F, _D1, "PSRLW", Pq, Qd);
        define(_0F, _D2, "PSRLD", Pq, Qd);
        define(_0F, _D3, "PSRLQ", Pq, Qd);
        define(_0F, _D5, "PMULLW", Pq, Qd);

        define(_66, _0F, _D0, "ADDSUBPD", Vpd, Wpd);
        define(_66, _0F, _D1, "PSRLW", Vdq, Wdq);
        define(_66, _0F, _D2, "PSRLD", Vdq, Wdq);
        define(_66, _0F, _D3, "PSRLQ", Vdq, Wdq);
        define(_66, _0F, _D4, "PADDQ", Vdq, Wdq);
        define(_66, _0F, _D5, "PMULLW", Vdq, Wdq);
        define(_66, _0F, _D6, "MOVQ", Wq.excludeExternalTestArguments(IA32XMMRegister.ENUMERATOR), Vq); // gas uses F3 0F 7E for reg-reg
        define(_66, _0F, _D7, "PMOVMSKB", Gd, VRdq);

        define(_F2, _0F, _D6, "MOVDQ2Q", Pq, VRq);

        define(_F3, _0F, _D6, "MOVQ2DQ", Vdq, PRq);

        define(_0F, _E1, "PSRAW", Pq, Qd);
        define(_0F, _E2, "PSRAD", Pq, Qd);
        define(_0F, _E5, "PMULHW", Pq, Qd);

        define(_66, _0F, _E0, "PAVGB", Vdq, Wdq);
        define(_66, _0F, _E1, "PSRAW", Vdq, Wdq);
        define(_66, _0F, _E2, "PSRAD", Vdq, Wdq);
        define(_66, _0F, _E3, "PAVGW", Vdq, Wdq);
        define(_66, _0F, _E4, "PMULHUW", Vdq, Wdq);
        define(_66, _0F, _E5, "PMULHW", Vdq, Wdq);
        define(_66, _0F, _E6, "CVTTPD2DQ", Vq, Wpd);
        define(_66, _0F, _E7, "MVNTDQ", Mdq, Vdq).beNotExternallyTestable(); // gas does not know it

        define(_F2, _0F, _E6, "CVTPD2DQ", Vq, Wpd);

        define(_F3, _0F, _E6, "CVTDQ2PD", Vpd, Wq);

        define(_0F, _F1, "PSLLW", Pq, Qd);
        define(_0F, _F2, "PSLLD", Pq, Qd);
        define(_0F, _F3, "PSLLQ", Pq, Qd);
        define(_0F, _F5, "PMADDWD", Pq, Qd);

        define(_66, _0F, _F1, "PSLLW", Vdq, Wdq);
        define(_66, _0F, _F2, "PSLLD", Vdq, Wdq);
        define(_66, _0F, _F3, "PSLLQ", Vdq, Wdq);
        define(_66, _0F, _F4, "PMULUDQ", Vdq, Wdq);
        define(_66, _0F, _F5, "PMADDWD", Vdq, Wdq);
        define(_66, _0F, _F6, "PSADBW", Vdq, Wdq);
        define(_66, _0F, _F7, "MASKMOVDQU", Vdq, VRdq);

        define(_F2, _0F, _F0, "LDDQU", Vpd, Mdq);
    }

    private void create_high() {
        define(_0F, _08, "INVD");
        define(_0F, _09, "WBINVD");
        define(_0F, _0B, "UD2");

        define(_0F, _28, "MOVAPS", Vps, Wps);
        define(_0F, _29, "MOVAPS", Wps, Vps);
        define(_0F, _2A, "CVTPI2PS", Vps, Qq);
        define(_0F, _2B, "MOVNTPS", Mdq, Vps);
        define(_0F, _2C, "CVTTPS2PI", Pq, Wps);
        define(_0F, _2D, "CVTPS2PI", Pq, Wps);
        define(_0F, _2E, "UCOMISS", Vss, Wss);
        define(_0F, _2F, "COMISS", Vps, Wps);

        define(_66, _0F, _28, "MOVAPD", Vpd, Wpd);
        define(_66, _0F, _29, "MOVAPD", Wpd, Vpd);
        define(_66, _0F, _2A, "CVTPI2PD", Vpd, Qq);
        define(_66, _0F, _2B, "MOVNTPD", Mdq, Vpd);
        define(_66, _0F, _2C, "CVTTPD2PI", Pq, Wpd);
        define(_66, _0F, _2D, "CVTPD2PI", Pq, Wpd);
        define(_66, _0F, _2E, "UCOMISD", Vsd, Wsd);
        define(_66, _0F, _2F, "COMISD", Vpd, Wsd);

        define(_F2, _0F, _2A, "CVTSI2SD", Vsd, Ed);
        define(_F2, _0F, _2C, "CVTTSD2SI", Gd, Wsd);
        define(_F2, _0F, _2D, "CVTSD2SI", Gd, Wsd);

        define(_F3, _0F, _2A, "CVTSI2SS", Vss, Ed);
        define(_F3, _0F, _2C, "CVTTSS2SI", Gd, Wss);
        define(_F3, _0F, _2D, "CVTSS2SI", Gd, Wss);

        define(_0F, _48, "CMOVS", Gv, Ev);
        define(_0F, _49, "CMOVNS", Gv, Ev);
        define(_0F, _4A, "CMOVP", Gv, Ev);
        define(_0F, _4B, "CMOVNP", Gv, Ev);
        define(_0F, _4C, "CMOVL", Gv, Ev);
        define(_0F, _4D, "CMOVGE", Gv, Ev);
        define(_0F, _4E, "CMOVLE", Gv, Ev);
        define(_0F, _4F, "CMOVG", Gv, Ev);

        define(_0F, _58, "ADDPS", Vps, Wps);
        define(_0F, _59, "MULPS", Vps, Wps);
        define(_0F, _5A, "CVTPS2PD", Vpd, Wps);
        define(_0F, _5B, "CVTDQ2PS", Vps, Wdq);
        define(_0F, _5C, "SUBPS", Vps, Wps);
        define(_0F, _5D, "MINPS", Vps, Wps);
        define(_0F, _5E, "DIVPS", Vps, Wps);
        define(_0F, _5F, "MAXPS", Vps, Wps);

        define(_66, _0F, _58, "ADDPD", Vpd, Wpd);
        define(_66, _0F, _59, "MULPD", Vpd, Wpd);
        define(_66, _0F, _5A, "CVTPD2PS", Vps, Wpd);
        define(_66, _0F, _5B, "CVTPS2DQ", Vdq, Wps);
        define(_66, _0F, _5C, "SUBPD", Vpd, Wpd);
        define(_66, _0F, _5D, "MINPD", Vpd, Wpd);
        define(_66, _0F, _5E, "DIVPD", Vpd, Wpd);
        define(_66, _0F, _5F, "MAXPD", Vpd, Wpd);

        define(_F2, _0F, _58, "ADDSD", Vsd, Wsd);
        define(_F2, _0F, _59, "MULSD", Vsd, Wsd);
        define(_F2, _0F, _5A, "CVTSD2SS", Vss, Wsd);
        define(_F2, _0F, _5C, "SUBSD", Vsd, Wsd);
        define(_F2, _0F, _5D, "MINSD", Vsd, Wsd);
        define(_F2, _0F, _5E, "DIVSD", Vsd, Wsd);
        define(_F2, _0F, _5F, "MAXSD", Vsd, Wsd);

        define(_F3, _0F, _58, "ADDSS", Vss, Wss);
        define(_F3, _0F, _59, "MULSS", Vss, Wss);
        define(_F3, _0F, _5A, "CVTSS2SD", Vsd, Wss);
        define(_F3, _0F, _5B, "CVTTPS2DQ", Vdq, Wps);
        define(_F3, _0F, _5C, "SUBSS", Vss, Wss);
        define(_F3, _0F, _5D, "MINSS", Vss, Wss);
        define(_F3, _0F, _5E, "DIVSS", Vss, Wss);
        define(_F3, _0F, _5F, "MAXSS", Vss, Wss);

        define(_0F, _68, "PUNPCKHBW", Pq, Qd);
        define(_0F, _69, "PUNPCKHWD", Pq, Qd);
        define(_0F, _6A, "PUNPCKHDQ", Pq, Qd);
        define(_0F, _6B, "PACKSSDW", Pq, Qd);
        define(_0F, _6E, "MOVD", Pd, Ed);
        define(_0F, _6F, "MOVQ", Pq, Qq);

        define(_66, _0F, _68, "PUNPCKHBW", Vdq, Wq);
        define(_66, _0F, _69, "PUNPCKHWD", Vdq, Wq);
        define(_66, _0F, _6A, "PUNPCKHDQ", Vdq, Wq);
        define(_66, _0F, _6B, "PACKSSDW", Vdq, Wdq);
        define(_66, _0F, _6C, "PUNPCKLQDQ", Vdq, Wq);
        define(_66, _0F, _6D, "PUNPCKHQDQ", Vdq, Wq);
        define(_66, _0F, _6E, "MOVD", Vdq, Ed).beNotExternallyTestable(); // gas does not feature suffix to distinguish operand width
        define(_66, _0F, _6F, "MOVDQA", Vdq, Wdq);

        define(_F3, _0F, _6F, "MOVDQU", Vdq, Wdq);

        define(_0F, _7E, "MOVD", Ed, Pd);
        define(_0F, _7F, "MOVQ", Qq, Pq);

        define(_66, _0F, _7C, "HADDPD", Vpd, Wpd);
        define(_66, _0F, _7D, "HSUBPD", Vpd, Wpd);
        define(_66, _0F, _7E, "MOVD", Ed, Vdq).beNotExternallyTestable(); // gas does not feature suffix to distinguish operand width
        define(_66, _0F, _7F, "MOVDQA", Wdq, Vdq);

        define(_F2, _0F, _7C, "HADDPS", Vps, Wps);
        define(_F2, _0F, _7D, "HSUBPS", Vps, Wps);

        define(_F3, _0F, _7E, "MOVQ", Vq, Wq);
        define(_F3, _0F, _7F, "MOVDQU", Wdq, Vdq);

        define(_0F, _88, "JS", Jv);
        define(_0F, _89, "JNS", Jv);
        define(_0F, _8A, "JP", Jv);
        define(_0F, _8B, "JNP", Jv);
        define(_0F, _8C, "JL", Jv);
        define(_0F, _8D, "JNL", Jv);
        define(_0F, _8E, "JLE", Jv);
        define(_0F, _8F, "JNLE", Jv);

        define(_0F, _98, "SETS", Eb);
        define(_0F, _99, "SETNS", Eb);
        define(_0F, _9A, "SETP", Eb);
        define(_0F, _9B, "SETNP", Eb);
        define(_0F, _9C, "SETL", Eb);
        define(_0F, _9D, "SETNL", Eb);
        define(_0F, _9E, "SETLE", Eb);
        define(_0F, _9F, "SETNLE", Eb);

        define(_0F, _A8, "PUSH", GS);
        define(_0F, _A9, "POP", GS);
        define(_0F, _AA, "RSM");
        define(_0F, _AB, "BTS", Ev, Gv);
        define(_0F, _AC, "SHRD", Ev, Gv, Ib);
        define(_0F, _AD, "SHRD", Ev, Gv, CL);
        define(_0F, _AE, GROUP_15b, X86TemplateContext.ModCase.MOD_3);
        define(_0F, _AF, "IMUL", Gv, Ev);

        define(_0F, _B9, GROUP_10);
        define(_0F, _BA, GROUP_8, Ev, Ib);
        define(_0F, _BB, "BTC", Ev, Gv);
        define(_0F, _BC, "BSF", Gv, Ev);
        define(_0F, _BD, "BSR", Gv, Ev);
        define(_0F, _BE, "MOVSXB", Gv, Eb).setExternalName("movsx");
        define(_0F, _BF, "MOVSXW", Gv, Ew).beNotExternallyTestable(); // gas unnecessarily prepends the operand size prefix 0x66

        define(_0F, _C8, "BSWAP", Nd);

        define(_0F, _D8, "PSUBUSB", Pq, Qq);
        define(_0F, _D9, "PSUBUSW", Pq, Qq);
        define(_0F, _DB, "PAND", Pq, Qq);
        define(_0F, _DC, "PADDUSB", Pq, Qq);
        define(_0F, _DD, "PADDUSW", Pq, Qq);
        define(_0F, _DF, "PANDN", Pq, Qq);

        define(_66, _0F, _D8, "PSUBUSB", Vdq, Wdq);
        define(_66, _0F, _D9, "PSUBUSW", Vdq, Wdq);
        define(_66, _0F, _DA, "PMINUB", Vdq, Wdq);
        define(_66, _0F, _DB, "PAND", Vdq, Wdq);
        define(_66, _0F, _DC, "PADDUSB", Vdq, Wdq);
        define(_66, _0F, _DD, "PADDUSW", Vdq, Wdq);
        define(_66, _0F, _DE, "PMAXUB", Vdq, Wdq);
        define(_66, _0F, _DF, "PANDN", Vdq, Wdq);

        define(_0F, _E8, "PSUBSB", Pq, Qq);
        define(_0F, _E9, "PSUBSW", Pq, Qq);
        define(_0F, _EB, "POR", Pq, Qq);
        define(_0F, _EC, "PADDSB", Pq, Qq);
        define(_0F, _ED, "PADDSW", Pq, Qq);
        define(_0F, _EF, "PXOR", Pq, Qq);

        define(_66, _0F, _E8, "PSUBSB", Vdq, Wdq);
        define(_66, _0F, _E9, "PSUBSW", Vdq, Wdq);
        define(_66, _0F, _EA, "PMINSW", Vdq, Wdq);
        define(_66, _0F, _EB, "POR", Vdq, Wdq);
        define(_66, _0F, _EC, "PADDSB", Vdq, Wdq);
        define(_66, _0F, _ED, "PADDSW", Vdq, Wdq);
        define(_66, _0F, _EE, "PMAXSW", Vdq, Wdq);
        define(_66, _0F, _EF, "PXOR", Vdq, Wdq);

        define(_0F, _F8, "PSUBB", Pq, Qq);
        define(_0F, _F9, "PSUBW", Pq, Qq);
        define(_0F, _FA, "PSUBD", Pq, Qq);
        define(_0F, _FC, "PADDB", Pq, Qq);
        define(_0F, _FD, "PADDW", Pq, Qq);
        define(_0F, _FE, "PADDD", Pq, Qq);

        define(_66, _0F, _F8, "PSUBB", Vdq, Wdq);
        define(_66, _0F, _F9, "PSUBW", Vdq, Wdq);
        define(_66, _0F, _FA, "PSUBD", Vdq, Wdq);
        define(_66, _0F, _FB, "PSUBQ", Vdq, Wdq);
        define(_66, _0F, _FC, "PADDB", Vdq, Wdq);
        define(_66, _0F, _FD, "PADDW", Vdq, Wdq);
        define(_66, _0F, _FE, "PADDD", Vdq, Wdq);
    }

    TwoByteOpcodeMap() {
        super(IA32Assembly.ASSEMBLY);
        create_low();
        create_high();
    }
}
