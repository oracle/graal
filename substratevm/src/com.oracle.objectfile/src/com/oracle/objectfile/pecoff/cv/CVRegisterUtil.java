/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2025, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.objectfile.pecoff.cv;

import com.oracle.objectfile.debugentry.TypeEntry;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;

@SuppressWarnings("unused")
public class CVRegisterUtil {

    private static final int MAX_JAVA_REGISTER_NUMBER = AMD64.xmm15.number;

    /* Register definitions */

    /* 8 bit registers. */
    private static final short CV_AMD64_R8B = 344;
    private static final short CV_AMD64_R9B = 345;
    private static final short CV_AMD64_R10B = 346;
    private static final short CV_AMD64_R11B = 347;
    private static final short CV_AMD64_R12B = 348;
    private static final short CV_AMD64_R13B = 349;
    private static final short CV_AMD64_R14B = 350;
    private static final short CV_AMD64_R15B = 351;

    private static final short CV_AMD64_AL = 1;
    private static final short CV_AMD64_CL = 2;
    private static final short CV_AMD64_DL = 3;
    private static final short CV_AMD64_BL = 4;

    private static final short CV_AMD64_SIL = 324;
    private static final short CV_AMD64_DIL = 325;
    private static final short CV_AMD64_BPL = 326;
    private static final short CV_AMD64_SPL = 327;

    /* 16 bit registers. */
    private static final short CV_AMD64_R8W = 352;
    private static final short CV_AMD64_R9W = 353;
    private static final short CV_AMD64_R10W = 354;
    private static final short CV_AMD64_R11W = 355;
    private static final short CV_AMD64_R12W = 356;
    private static final short CV_AMD64_R13W = 357;
    private static final short CV_AMD64_R14W = 358;
    private static final short CV_AMD64_R15W = 359;

    private static final short CV_AMD64_AX = 9;
    private static final short CV_AMD64_CX = 10;
    private static final short CV_AMD64_DX = 11;
    private static final short CV_AMD64_BX = 12;
    private static final short CV_AMD64_SP = 13;
    private static final short CV_AMD64_BP = 14;
    private static final short CV_AMD64_SI = 15;
    private static final short CV_AMD64_DI = 16;

    /* 32 bit registers. */
    private static final short CV_AMD64_R8D = 360;
    private static final short CV_AMD64_R9D = 361;
    private static final short CV_AMD64_R10D = 362;
    private static final short CV_AMD64_R11D = 363;
    private static final short CV_AMD64_R12D = 364;
    private static final short CV_AMD64_R13D = 365;
    private static final short CV_AMD64_R14D = 366;
    private static final short CV_AMD64_R15D = 367;

    private static final short CV_AMD64_EAX = 17;
    private static final short CV_AMD64_ECX = 18;
    private static final short CV_AMD64_EDX = 19;
    private static final short CV_AMD64_EBX = 20;
    private static final short CV_AMD64_ESP = 21;
    private static final short CV_AMD64_EBP = 22;
    private static final short CV_AMD64_ESI = 23;
    private static final short CV_AMD64_EDI = 24;

    /* 64 bit registers. */
    private static final short CV_AMD64_RAX = 328;
    private static final short CV_AMD64_RBX = 329;
    private static final short CV_AMD64_RCX = 330;
    private static final short CV_AMD64_RDX = 331;
    private static final short CV_AMD64_RSI = 332;
    private static final short CV_AMD64_RDI = 333;
    private static final short CV_AMD64_RBP = 334;
    private static final short CV_AMD64_RSP = 335;

    static final short CV_AMD64_R8 = 336;
    private static final short CV_AMD64_R9 = 337;
    private static final short CV_AMD64_R10 = 338;
    private static final short CV_AMD64_R11 = 339;
    private static final short CV_AMD64_R12 = 340;
    private static final short CV_AMD64_R13 = 341;
    private static final short CV_AMD64_R14 = 342;
    private static final short CV_AMD64_R15 = 343;

    /* FP registers. */
    private static final short CV_AMD64_XMM0 = 154;
    private static final short CV_AMD64_XMM1 = 155;
    private static final short CV_AMD64_XMM2 = 156;
    private static final short CV_AMD64_XMM3 = 157;
    private static final short CV_AMD64_XMM4 = 158;
    private static final short CV_AMD64_XMM5 = 159;
    private static final short CV_AMD64_XMM6 = 160;
    private static final short CV_AMD64_XMM7 = 161;

    private static final short CV_AMD64_XMM8 = 252;
    private static final short CV_AMD64_XMM9 = 253;
    private static final short CV_AMD64_XMM10 = 254;
    private static final short CV_AMD64_XMM11 = 255;
    private static final short CV_AMD64_XMM12 = 256;
    private static final short CV_AMD64_XMM13 = 257;
    private static final short CV_AMD64_XMM14 = 258;
    private static final short CV_AMD64_XMM15 = 259;

    private static final short CV_AMD64_XMM0_0 = 162;
    private static final short CV_AMD64_XMM1_0 = 166;
    private static final short CV_AMD64_XMM2_0 = 170;
    private static final short CV_AMD64_XMM3_0 = 174;
    private static final short CV_AMD64_XMM4_0 = 178;
    private static final short CV_AMD64_XMM5_0 = 182;
    private static final short CV_AMD64_XMM6_0 = 186;
    private static final short CV_AMD64_XMM7_0 = 190;

    private static final short CV_AMD64_XMM8_0 = 260;
    private static final short CV_AMD64_XMM9_0 = 261;
    private static final short CV_AMD64_XMM10_0 = 262;
    private static final short CV_AMD64_XMM11_0 = 263;
    private static final short CV_AMD64_XMM12_0 = 264;
    private static final short CV_AMD64_XMM13_0 = 265;
    private static final short CV_AMD64_XMM14_0 = 266;
    private static final short CV_AMD64_XMM15_0 = 267;

    private static final short CV_AMD64_XMM0L = 194;
    private static final short CV_AMD64_XMM1L = 195;
    private static final short CV_AMD64_XMM2L = 196;
    private static final short CV_AMD64_XMM3L = 197;
    private static final short CV_AMD64_XMM4L = 198;
    private static final short CV_AMD64_XMM5L = 199;
    private static final short CV_AMD64_XMM6L = 200;
    private static final short CV_AMD64_XMM7L = 201;

    private static final short CV_AMD64_XMM8L = 292;
    private static final short CV_AMD64_XMM9L = 293;
    private static final short CV_AMD64_XMM10L = 294;
    private static final short CV_AMD64_XMM11L = 295;
    private static final short CV_AMD64_XMM12L = 296;
    private static final short CV_AMD64_XMM13L = 297;
    private static final short CV_AMD64_XMM14L = 298;
    private static final short CV_AMD64_XMM15L = 299;

    private static class CvRegDef {
        final Register register;
        final short cv1;
        final short cv2;
        final short cv4;
        final short cv8;

        CvRegDef(Register r, short cv1, short cv2, short cv4, short cv8) {
            this.register = r;
            this.cv1 = cv1;
            this.cv2 = cv2;
            this.cv4 = cv4;
            this.cv8 = cv8;
        }

        CvRegDef(Register r, short cv4, short cv8) {
            this.register = r;
            this.cv1 = -1;
            this.cv2 = -1;
            this.cv4 = cv4;
            this.cv8 = cv8;
        }
    }

    /* List of Graal assignable registers and CodeView register IDs. */
    private static final CvRegDef[] compactRegDefs = {
                    /* 8, 16, 32, 64 bits */
                    new CvRegDef(AMD64.rax, CV_AMD64_AL, CV_AMD64_AX, CV_AMD64_EAX, CV_AMD64_RAX),
                    new CvRegDef(AMD64.rcx, CV_AMD64_CL, CV_AMD64_CX, CV_AMD64_ECX, CV_AMD64_RCX),
                    new CvRegDef(AMD64.rdx, CV_AMD64_DL, CV_AMD64_DX, CV_AMD64_EDX, CV_AMD64_RDX),
                    new CvRegDef(AMD64.rbx, CV_AMD64_BL, CV_AMD64_BX, CV_AMD64_EBX, CV_AMD64_RBX),
                    new CvRegDef(AMD64.rsp, CV_AMD64_SPL, CV_AMD64_SP, CV_AMD64_ESP, CV_AMD64_RSP),
                    new CvRegDef(AMD64.rbp, CV_AMD64_BPL, CV_AMD64_BP, CV_AMD64_EBP, CV_AMD64_RBP),
                    new CvRegDef(AMD64.rsi, CV_AMD64_SIL, CV_AMD64_SI, CV_AMD64_ESI, CV_AMD64_RSI),
                    new CvRegDef(AMD64.rdi, CV_AMD64_DIL, CV_AMD64_DI, CV_AMD64_EDI, CV_AMD64_RDI),
                    new CvRegDef(AMD64.r8, CV_AMD64_R8B, CV_AMD64_R8W, CV_AMD64_R8D, CV_AMD64_R8),
                    new CvRegDef(AMD64.r9, CV_AMD64_R9B, CV_AMD64_R9W, CV_AMD64_R9D, CV_AMD64_R9),
                    new CvRegDef(AMD64.r10, CV_AMD64_R10B, CV_AMD64_R10W, CV_AMD64_R10D, CV_AMD64_R10),
                    new CvRegDef(AMD64.r11, CV_AMD64_R11B, CV_AMD64_R11W, CV_AMD64_R11D, CV_AMD64_R11),
                    new CvRegDef(AMD64.r12, CV_AMD64_R12B, CV_AMD64_R12W, CV_AMD64_R12D, CV_AMD64_R12),
                    new CvRegDef(AMD64.r13, CV_AMD64_R13B, CV_AMD64_R13W, CV_AMD64_R13D, CV_AMD64_R13),
                    new CvRegDef(AMD64.r14, CV_AMD64_R14B, CV_AMD64_R14W, CV_AMD64_R14D, CV_AMD64_R14),
                    new CvRegDef(AMD64.r15, CV_AMD64_R15B, CV_AMD64_R15W, CV_AMD64_R15D, CV_AMD64_R15),

                    /* 32, 64 bits */
                    new CvRegDef(AMD64.xmm0, CV_AMD64_XMM0_0, CV_AMD64_XMM0L), /* xmm0=16 */
                    new CvRegDef(AMD64.xmm1, CV_AMD64_XMM1_0, CV_AMD64_XMM1L),
                    new CvRegDef(AMD64.xmm2, CV_AMD64_XMM2_0, CV_AMD64_XMM2L),
                    new CvRegDef(AMD64.xmm3, CV_AMD64_XMM3_0, CV_AMD64_XMM3L),
                    new CvRegDef(AMD64.xmm4, CV_AMD64_XMM4_0, CV_AMD64_XMM4L),
                    new CvRegDef(AMD64.xmm5, CV_AMD64_XMM5_0, CV_AMD64_XMM5L),
                    new CvRegDef(AMD64.xmm6, CV_AMD64_XMM6_0, CV_AMD64_XMM6L),
                    new CvRegDef(AMD64.xmm7, CV_AMD64_XMM7_0, CV_AMD64_XMM7L),
                    new CvRegDef(AMD64.xmm8, CV_AMD64_XMM8_0, CV_AMD64_XMM8L),
                    new CvRegDef(AMD64.xmm9, CV_AMD64_XMM9_0, CV_AMD64_XMM9L),
                    new CvRegDef(AMD64.xmm10, CV_AMD64_XMM10_0, CV_AMD64_XMM10L),
                    new CvRegDef(AMD64.xmm11, CV_AMD64_XMM11_0, CV_AMD64_XMM11L),
                    new CvRegDef(AMD64.xmm12, CV_AMD64_XMM12_0, CV_AMD64_XMM12L),
                    new CvRegDef(AMD64.xmm13, CV_AMD64_XMM13_0, CV_AMD64_XMM13L),
                    new CvRegDef(AMD64.xmm14, CV_AMD64_XMM14_0, CV_AMD64_XMM14L),
                    new CvRegDef(AMD64.xmm15, CV_AMD64_XMM15_0, CV_AMD64_XMM15L),
    };

    private static final CvRegDef[] javaToCvRegisters = new CvRegDef[MAX_JAVA_REGISTER_NUMBER + 1];

    static {
        for (CvRegDef def : compactRegDefs) {
            assert 0 <= def.register.number && def.register.number <= MAX_JAVA_REGISTER_NUMBER;
            javaToCvRegisters[def.register.number] = def;
        }
    }

    /* convert a Java register number to a CodeView register code */
    /* thos Codeview code depends upon the register type and size */
    static short getCVRegister(int javaReg, TypeEntry typeEntry) {
        assert 0 <= javaReg && javaReg <= MAX_JAVA_REGISTER_NUMBER;
        if (javaReg > MAX_JAVA_REGISTER_NUMBER) {
            return -1;
        }
        CvRegDef cvReg = javaToCvRegisters[javaReg];
        assert cvReg != null;
        assert cvReg.register.number == javaReg;
        if (cvReg == null) {
            return -1;
        }

        final short cvCode;
        if (typeEntry.isPrimitive()) {
            switch (typeEntry.getSize()) {
                case 1:
                    cvCode = cvReg.cv1;
                    break;
                case 2:
                    cvCode = cvReg.cv2;
                    break;
                case 4:
                    cvCode = cvReg.cv4;
                    break;
                case 8:
                    cvCode = cvReg.cv8;
                    break;
                default:
                    cvCode = -1;
                    break;
            }
        } else {
            /* Objects are represented by 8 byte pointers. */
            cvCode = cvReg.cv8;
        }
        assert cvCode != -1;
        return cvCode;
    }
}
