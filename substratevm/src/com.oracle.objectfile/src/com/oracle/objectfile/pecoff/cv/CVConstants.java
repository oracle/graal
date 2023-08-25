/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

public abstract class CVConstants {

    /* The names of relevant CodeView sections. */
    static final String CV_SECTION_NAME_PREFIX = ".debug$";
    static final String CV_SYMBOL_SECTION_NAME = CV_SECTION_NAME_PREFIX + "S";
    static final String CV_TYPE_SECTION_NAME = CV_SECTION_NAME_PREFIX + "T";

    /* CodeView section header signature */
    static final int CV_SIGNATURE_C13 = 4;

    /* Register definitions */

    /* 8 bit registers. */
    static final short CV_AMD64_R8B = 344;
    static final short CV_AMD64_R9B = 345;
    static final short CV_AMD64_R10B = 346;
    static final short CV_AMD64_R11B = 347;
    static final short CV_AMD64_R12B = 348;
    static final short CV_AMD64_R13B = 349;
    static final short CV_AMD64_R14B = 350;
    static final short CV_AMD64_R15B = 351;

    static final short CV_AMD64_AL = 1;
    static final short CV_AMD64_CL = 2;
    static final short CV_AMD64_DL = 3;
    static final short CV_AMD64_BL = 4;

    static final short CV_AMD64_SIL = 324;
    static final short CV_AMD64_DIL = 325;
    static final short CV_AMD64_BPL = 326;
    static final short CV_AMD64_SPL = 327;

    /* 16 bit registers. */
    static final short CV_AMD64_R8W = 352;
    static final short CV_AMD64_R9W = 353;
    static final short CV_AMD64_R10W = 354;
    static final short CV_AMD64_R11W = 355;
    static final short CV_AMD64_R12W = 356;
    static final short CV_AMD64_R13W = 357;
    static final short CV_AMD64_R14W = 358;
    static final short CV_AMD64_R15W = 359;

    static final short CV_AMD64_AX = 9;
    static final short CV_AMD64_CX = 10;
    static final short CV_AMD64_DX = 11;
    static final short CV_AMD64_BX = 12;
    static final short CV_AMD64_SP = 13;
    static final short CV_AMD64_BP = 14;
    static final short CV_AMD64_SI = 15;
    static final short CV_AMD64_DI = 16;

    /* 32 bit registers. */
    static final short CV_AMD64_R8D = 360;
    static final short CV_AMD64_R9D = 361;
    static final short CV_AMD64_R10D = 362;
    static final short CV_AMD64_R11D = 363;
    static final short CV_AMD64_R12D = 364;
    static final short CV_AMD64_R13D = 365;
    static final short CV_AMD64_R14D = 366;
    static final short CV_AMD64_R15D = 367;

    static final short CV_AMD64_EAX = 17;
    static final short CV_AMD64_ECX = 18;
    static final short CV_AMD64_EDX = 19;
    static final short CV_AMD64_EBX = 20;
    static final short CV_AMD64_ESP = 21;
    static final short CV_AMD64_EBP = 22;
    static final short CV_AMD64_ESI = 23;
    static final short CV_AMD64_EDI = 24;

    /* 64 bit registers. */
    static final short CV_AMD64_RAX = 328;
    static final short CV_AMD64_RBX = 329;
    static final short CV_AMD64_RCX = 330;
    static final short CV_AMD64_RDX = 331;
    static final short CV_AMD64_RSI = 332;
    static final short CV_AMD64_RDI = 333;
    static final short CV_AMD64_RBP = 334;
    static final short CV_AMD64_RSP = 335;

    static final short CV_AMD64_R8 = 336;
    static final short CV_AMD64_R9 = 337;
    static final short CV_AMD64_R10 = 338;
    static final short CV_AMD64_R11 = 339;
    static final short CV_AMD64_R12 = 340;
    static final short CV_AMD64_R13 = 341;
    static final short CV_AMD64_R14 = 342;
    static final short CV_AMD64_R15 = 343;

    /* FP registers. */
    static final short CV_AMD64_XMM0 = 154;
    static final short CV_AMD64_XMM1 = 155;
    static final short CV_AMD64_XMM2 = 156;
    static final short CV_AMD64_XMM3 = 157;
    static final short CV_AMD64_XMM4 = 158;
    static final short CV_AMD64_XMM5 = 159;
    static final short CV_AMD64_XMM6 = 160;
    static final short CV_AMD64_XMM7 = 161;

    static final short CV_AMD64_XMM0_0 = 162;
    static final short CV_AMD64_XMM1_0 = 166;
    static final short CV_AMD64_XMM2_0 = 170;
    static final short CV_AMD64_XMM3_0 = 174;
    static final short CV_AMD64_XMM4_0 = 178;
    static final short CV_AMD64_XMM5_0 = 182;
    static final short CV_AMD64_XMM6_0 = 186;
    static final short CV_AMD64_XMM7_0 = 190;

    static final short CV_AMD64_XMM0L = 194;
    static final short CV_AMD64_XMM1L = 195;
    static final short CV_AMD64_XMM2L = 196;
    static final short CV_AMD64_XMM3L = 197;
    static final short CV_AMD64_XMM4L = 198;
    static final short CV_AMD64_XMM5L = 199;
    static final short CV_AMD64_XMM6L = 200;
    static final short CV_AMD64_XMM7L = 201;
}
