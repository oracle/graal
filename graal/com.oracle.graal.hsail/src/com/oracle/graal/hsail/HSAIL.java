/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hsail;

import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.api.code.ValueUtil.*;

import java.nio.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Register.RegisterCategory;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;

/**
 * Represents the HSAIL architecture.
 */
public class HSAIL extends Architecture {

    // @formatter:off
    public static final RegisterCategory CPU = new RegisterCategory("CPU");
    public static final RegisterCategory FPU = new RegisterCategory("FPU");

    // Control registers.
    public static final Register c0 = new Register(0, 0, "c0", CPU);
    public static final Register c1 = new Register(1, 1, "c1", CPU);
    public static final Register c2 = new Register(2, 2, "c2", CPU);
    public static final Register c3 = new Register(3, 3, "c3", CPU);
    public static final Register c4 = new Register(4, 4, "c4", CPU);
    public static final Register c5 = new Register(5, 5, "c5", CPU);
    public static final Register c6 = new Register(6, 6, "c6", CPU);
    public static final Register c7 = new Register(7, 7, "c7", CPU);

    //32 bit registers.
    public static final Register s0  = new Register(8,  0,  "s0", CPU);
    public static final Register s1  = new Register(9,  1,  "s1", CPU);
    public static final Register s2 = new Register(10, 2, "s2", CPU);
    public static final Register s3 = new Register(11, 3, "s3", CPU);
    public static final Register s4 = new Register(12, 4, "s4", CPU);
    public static final Register s5 = new Register(13, 5, "s5", CPU);
    public static final Register s6 = new Register(14, 6, "s6", CPU);
    public static final Register s7 = new Register(15, 7, "s7", CPU);
    public static final Register s8  = new Register(16,  8,  "s8", CPU);
    public static final Register s9  = new Register(17,  9,  "s9", CPU);
    public static final Register s10 = new Register(18, 10, "s10", CPU);
    public static final Register s11 = new Register(19, 11, "s11", CPU);
    public static final Register s12 = new Register(20, 12, "s12", CPU);
    public static final Register s13 = new Register(21, 13, "s13", CPU);
    public static final Register s14 = new Register(22, 14, "s14", CPU);
    public static final Register s15 = new Register(23, 15, "s15", CPU);
    public static final Register s16  = new Register(24, 16,  "s16", CPU);
    public static final Register s17  = new Register(25,  17,  "s17", CPU);
    public static final Register s18 = new Register(26, 18, "s18", CPU);
    public static final Register s19 = new Register(27, 19, "s19", CPU);
    public static final Register s20 = new Register(28, 20, "s20", CPU);
    public static final Register s21 = new Register(29, 21, "s21", CPU);
    public static final Register s22 = new Register(30, 22, "s22", CPU);
    public static final Register s23 = new Register(31, 23, "s23", CPU);
    public static final Register s24  = new Register(32, 24,  "s24", CPU);
    public static final Register s25  = new Register(33, 25,  "s25", CPU);
    public static final Register s26 = new Register(34, 26, "s26", CPU);
    public static final Register s27 = new Register(35, 27, "s27", CPU);
    public static final Register s28 = new Register(36, 28, "s28", CPU);
    public static final Register s29 = new Register(37, 29, "s29", CPU);
    public static final Register s30 = new Register(38, 30, "s30", CPU);
    public static final Register s31 = new Register(39, 31, "s31", CPU);
    public static final Register s32  = new Register(40, 32,  "s32", CPU);
    public static final Register s33  = new Register(41,  33,  "s33", CPU);
    public static final Register s34 = new Register(42, 34, "s34", CPU);
    public static final Register s35 = new Register(43, 35, "s35", CPU);
    public static final Register s36 = new Register(44, 36, "s36", CPU);
    public static final Register s37 = new Register(45, 37, "s37", CPU);
    public static final Register s38 = new Register(45, 38, "s38", CPU);
    public static final Register s39 = new Register(46, 39, "s39", CPU);
    public static final Register s40  = new Register(47, 40,  "s40", CPU);
    public static final Register s41  = new Register(48,  41,  "s41", CPU);
    public static final Register s42 = new Register(49, 42, "s42", CPU);
    public static final Register s43 = new Register(50, 43, "s43", CPU);
    public static final Register s44 = new Register(51, 44, "s44", CPU);
    public static final Register s45 = new Register(52, 45, "s45", CPU);
    public static final Register s46 = new Register(53, 46, "s46", CPU);
    public static final Register s47 = new Register(54, 47, "s47", CPU);
    public static final Register s48  = new Register(55, 48,  "s48", CPU);
    public static final Register s49  = new Register(56,  49,  "s49", CPU);
    public static final Register s50 = new Register(57, 50, "s50", CPU);
    public static final Register s51 = new Register(58, 51, "s51", CPU);
    public static final Register s52 = new Register(59, 52, "s52", CPU);
    public static final Register s53 = new Register(60, 53, "s53", CPU);
    public static final Register s54 = new Register(61, 54, "s54", CPU);
    public static final Register s55 = new Register(62, 55, "s55", CPU);
    public static final Register s56  = new Register(64, 56,  "s56", CPU);
    public static final Register s57  = new Register(64, 57,  "s57", CPU);
    public static final Register s58 = new Register(65, 58, "s58", CPU);
    public static final Register s59 = new Register(66, 59, "s59", CPU);
    public static final Register s60 = new Register(67, 60, "s60", CPU);
    public static final Register s61 = new Register(68, 61, "s61", CPU);
    public static final Register s62 = new Register(69, 62, "s62", CPU);
    public static final Register s63 = new Register(70, 63, "s63", CPU);
    public static final Register s64  = new Register(71, 64,  "s64", CPU);
    public static final Register s65  = new Register(72, 65,  "s65", CPU);
    public static final Register s66 = new Register(73, 66, "s66", CPU);
    public static final Register s67 = new Register(74, 67, "s67", CPU);
    public static final Register s68 = new Register(75, 68, "s68", CPU);
    public static final Register s69 = new Register(76, 69, "s69", CPU);
    public static final Register s70 = new Register(77, 70, "s70", CPU);
    public static final Register s71 = new Register(78, 71, "s71", CPU);
    public static final Register s72  = new Register(79, 72,  "s72", CPU);
    public static final Register s73  = new Register(80, 73,  "s73", CPU);
    public static final Register s74 = new Register(81, 74, "s74", CPU);
    public static final Register s75 = new Register(82, 75, "s75", CPU);
    public static final Register s76 = new Register(83, 76, "s76", CPU);
    public static final Register s77 = new Register(84, 77, "s77", CPU);
    public static final Register s78 = new Register(85, 78, "s78", CPU);
    public static final Register s79 = new Register(86, 79, "s79", CPU);
    public static final Register s80  = new Register(87, 80,  "s80", CPU);
    public static final Register s81  = new Register(88, 81,  "s81", CPU);
    public static final Register s82 = new Register(89, 82, "s82", CPU);
    public static final Register s83 = new Register(90, 83, "s83", CPU);
    public static final Register s84 = new Register(91, 84, "s84", CPU);
    public static final Register s85 = new Register(92, 85, "s85", CPU);
    public static final Register s86 = new Register(93, 86, "s86", CPU);
    public static final Register s87 = new Register(94, 87, "s87", CPU);
    public static final Register s88  = new Register(95,  88,  "s88", CPU);
    public static final Register s89  = new Register(96,  89,  "s89", CPU);
    public static final Register s90 = new Register(97, 90, "s90", CPU);
    public static final Register s91 = new Register(98, 91, "s91", CPU);
    public static final Register s92 = new Register(99, 92, "s92", CPU);
    public static final Register s93 = new Register(100, 93, "s93", CPU);
    public static final Register s94 = new Register(101, 94, "s94", CPU);
    public static final Register s95 = new Register(102, 95, "s95", CPU);
    public static final Register s96  = new Register(103, 96,  "s96", CPU);
    public static final Register s97  = new Register(104, 97,  "s97", CPU);
    public static final Register s98 = new Register(105, 98, "s98", CPU);
    public static final Register s99 = new Register(106, 99, "s99", CPU);
    public static final Register s100 = new Register(107, 100, "s100", CPU);
    public static final Register s101 = new Register(108, 101, "s101", CPU);
    public static final Register s102 = new Register(109, 102, "s102", CPU);
    public static final Register s103 = new Register(110, 103, "s103", CPU);
    public static final Register s104  = new Register(111, 104,  "s104", CPU);
    public static final Register s105  = new Register(112,  105,  "s105", CPU);
    public static final Register s106 = new Register(113, 106, "s106", CPU);
    public static final Register s107 = new Register(114, 107, "s107", CPU);
    public static final Register s108 = new Register(115, 108, "s108", CPU);
    public static final Register s109 = new Register(116, 109, "s109", CPU);
    public static final Register s110 = new Register(117, 110, "s110", CPU);
    public static final Register s111 = new Register(118, 111, "s111", CPU);
    public static final Register s112  = new Register(119, 112,  "s112", CPU);
    public static final Register s113  = new Register(120, 113,  "s113", CPU);
    public static final Register s114 = new Register(121, 114, "s114", CPU);
    public static final Register s115 = new Register(122, 115, "s115", CPU);
    public static final Register s116 = new Register(123, 116, "s116", CPU);
    public static final Register s117 = new Register(124, 117, "s117", CPU);
    public static final Register s118 = new Register(125, 118, "s118", CPU);
    public static final Register s119 = new Register(126, 119, "s119", CPU);
    public static final Register s120  = new Register(127, 120,  "s120", CPU);
    public static final Register s121  = new Register(128, 121,  "s121", CPU);
    public static final Register s122 = new Register(129, 122, "s122", CPU);
    public static final Register s123 = new Register(130, 123, "s123", CPU);
    public static final Register s124 = new Register(131, 124, "s124", CPU);
    public static final Register s125 = new Register(132, 125, "s125", CPU);
    public static final Register s126 = new Register(133, 126, "s126", CPU);
    public static final Register s127 = new Register(134, 127, "s127", CPU);

    //64 bit registers.
    public static final Register d0  = new Register(135,  0,  "d0", CPU);
    public static final Register d1  = new Register(136,  1,  "d1", CPU);
    public static final Register d2 = new Register(137, 2, "d2", CPU);
    public static final Register d3 = new Register(138, 3, "d3", CPU);
    public static final Register d4 = new Register(139, 4, "d4", CPU);
    public static final Register d5 = new Register(140, 5, "d5", CPU);
    public static final Register d6 = new Register(141, 6, "d6", CPU);
    public static final Register d7 = new Register(142, 7, "d7", CPU);
    public static final Register d8  = new Register(143,  8,  "d8", CPU);
    public static final Register d9  = new Register(144,  9,  "d9", CPU);
    public static final Register d10 = new Register(145, 10, "d10", CPU);
    public static final Register d11 = new Register(146, 11, "d11", CPU);
    public static final Register d12 = new Register(147, 12, "d12", CPU);
    public static final Register d13 = new Register(148, 13, "d13", CPU);
    public static final Register d14 = new Register(149, 14, "d14", CPU);
    public static final Register d15 = new Register(150, 15, "d15", CPU);
    public static final Register d16  = new Register(151, 16,  "d16", CPU);
    public static final Register d17  = new Register(152,  17,  "d17", CPU);
    public static final Register d18 = new Register(153, 18, "d18", CPU);
    public static final Register d19 = new Register(154, 19, "d19", CPU);
    public static final Register d20 = new Register(155, 20, "d20", CPU);
    public static final Register d21 = new Register(156, 21, "d21", CPU);
    public static final Register d22 = new Register(157, 22, "d22", CPU);
    public static final Register d23 = new Register(158, 23, "d23", CPU);
    public static final Register d24  = new Register(159, 24,  "d24", CPU);
    public static final Register d25  = new Register(160, 25,  "d25", CPU);
    public static final Register d26 = new Register(161, 26, "d26", CPU);
    public static final Register d27 = new Register(162, 27, "d27", CPU);
    public static final Register d28 = new Register(163, 28, "d28", CPU);
    public static final Register d29 = new Register(164, 29, "d29", CPU);
    public static final Register d30 = new Register(165, 30, "d30", CPU);
    public static final Register d31 = new Register(166, 31, "d31", CPU);
    public static final Register d32  = new Register(167, 32,  "d32", CPU);
    public static final Register d33  = new Register(168,  33,  "d33", CPU);
    public static final Register d34 = new Register(169, 34, "d34", CPU);
    public static final Register d35 = new Register(170, 35, "d35", CPU);
    public static final Register d36 = new Register(171, 36, "d36", CPU);
    public static final Register d37 = new Register(172, 37, "d37", CPU);
    public static final Register d38 = new Register(173, 38, "d38", CPU);
    public static final Register d39 = new Register(174, 39, "d39", CPU);
    public static final Register d40  = new Register(175, 40,  "d40", CPU);
    public static final Register d41  = new Register(176,  41,  "d41", CPU);
    public static final Register d42 = new Register(177, 42, "d42", CPU);
    public static final Register d43 = new Register(178, 43, "d43", CPU);
    public static final Register d44 = new Register(179, 44, "d44", CPU);
    public static final Register d45 = new Register(180, 45, "d45", CPU);
    public static final Register d46 = new Register(181, 46, "d46", CPU);
    public static final Register d47 = new Register(182, 47, "d47", CPU);
    public static final Register d48  = new Register(183, 48,  "d48", CPU);
    public static final Register d49  = new Register(184,  49,  "d49", CPU);
    public static final Register d50 = new Register(185, 50, "d50", CPU);
    public static final Register d51 = new Register(186, 51, "d51", CPU);
    public static final Register d52 = new Register(187, 52, "d52", CPU);
    public static final Register d53 = new Register(188, 53, "d53", CPU);
    public static final Register d54 = new Register(189, 54, "d54", CPU);
    public static final Register d55 = new Register(190, 55, "d55", CPU);
    public static final Register d56  = new Register(191, 56,  "d56", CPU);
    public static final Register d57  = new Register(192, 57,  "d57", CPU);
    public static final Register d58 = new Register(193, 58, "d58", CPU);
    public static final Register d59 = new Register(194, 59, "d59", CPU);
    public static final Register d60 = new Register(195, 60, "d60", CPU);
    public static final Register d61 = new Register(196, 61, "d61", CPU);
    public static final Register d62 = new Register(197, 62, "d62", CPU);
    public static final Register d63 = new Register(198, 63, "d63", CPU);

    //128 bit registers.
    public static final Register q0 = new Register(199, 0, "q0", CPU);
    public static final Register q1 = new Register(200, 1, "q1", CPU);
    public static final Register q2 = new Register(201, 2, "q2", CPU);
    public static final Register q3 = new Register(202, 3, "q3", CPU);
    public static final Register q4 = new Register(203, 4, "q4", CPU);
    public static final Register q5 = new Register(204, 5, "q5", CPU);
    public static final Register q6 = new Register(205, 6, "q6", CPU);
    public static final Register q7 = new Register(206, 7, "q7", CPU);
    public static final Register q8 = new Register(207, 8, "q8", CPU);
    public static final Register q9 = new Register(208, 9, "q9", CPU);
    public static final Register q10 = new Register(209, 10, "q10", CPU);
    public static final Register q11 = new Register(210, 11, "q11", CPU);
    public static final Register q12 = new Register(211, 12, "q12", CPU);
    public static final Register q13 = new Register(212, 13, "q13", CPU);
    public static final Register q14 = new Register(213, 14, "q14", CPU);
    public static final Register q15 = new Register(214, 15, "q15", CPU);
    public static final Register q16 = new Register(215, 16, "q16", CPU);
    public static final Register q17 = new Register(216, 17, "q17", CPU);
    public static final Register q18 = new Register(217, 18, "q18", CPU);
    public static final Register q19 = new Register(218, 19, "q19", CPU);
    public static final Register q20 = new Register(219, 20, "q20", CPU);
    public static final Register q21 = new Register(220, 21, "q21", CPU);
    public static final Register q22 = new Register(221, 22, "q22", CPU);
    public static final Register q23 = new Register(222, 23, "q23", CPU);
    public static final Register q24 = new Register(223, 24, "q24", CPU);
    public static final Register q25 = new Register(224, 25, "q25", CPU);
    public static final Register q26 = new Register(225, 26, "q26", CPU);
    public static final Register q27 = new Register(226, 27, "q27", CPU);
    public static final Register q28 = new Register(227, 28, "q28", CPU);
    public static final Register q29 = new Register(228, 29, "q29", CPU);
    public static final Register q30 = new Register(229, 30, "q30", CPU);
    public static final Register q31 = new Register(230, 31, "q31", CPU);

    public static final Register[] cRegisters = {
        c0, c1, c2, c3, c4, c5, c6, c7
    };

    public static final Register[] sRegisters = {
        s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10,
        s11, s12, s13, s14, s15, s16, s17, s18, s19,
        s20, s21, s22, s23, s24, s25, s26, s27, s28,
        s29, s30, s31, s32, s33, s34, s35, s36, s37,
        s38, s39, s40, s41, s42, s43, s44, s45, s46,
        s47, s48, s49, s50, s51, s52, s53, s54, s55,
        s56, s57, s58, s59, s60, s61, s62, s63, s64,
        s65, s66, s67, s68, s69, s70, s71, s72, s73,
        s74, s75, s76, s77, s78, s79, s80, s81, s82,
        s83, s84, s85, s86, s87, s88, s89, s90, s91,
        s92, s93, s94, s95, s96, s97, s98, s99, s100,
        s101, s102, s103, s104, s105, s106, s107, s108,
        s109, s110, s111, s112, s113, s114, s115, s116,
        s117, s118, s119, s120, s121, s122, s123, s124,
        s125, s126, s127
    };

    public static final Register[] dRegisters = {
        d0, d1, d2, d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13, d14, d15, d16, d17, d18, d19, d20, d21, d22, d23, d24, d25, d26, d27, d28,
        d29, d30, d31, d32, d33, d34, d35, d36, d37, d38, d39, d40, d41, d42, d43, d44, d45, d46, d47, d48, d49, d50, d51, d52, d53, d54, d55,
        d56, d57, d58, d59, d60, d61, d62, d63
    };

    public static final Register[] qRegisters = {
        q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11, q12, q13, q14, q15, q16, q17, q18, q19, q20, q21, q22, q23, q24, q25, q26, q27, q28, q29, q30, q31
    };

    public static final Register[] allRegisters = {
        c0, c1, c2, c3, c4, c5, c6, c7, s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18, s19,
        s20, s21, s22, s23, s24, s25, s26, s27, s28, s29, s30, s31, s32, s33, s34, s35,
        s36, s37, s38, s39, s40, s41, s42, s43, s44, s45, s46, s47, s48, s49, s50, s51, s52,
        s53, s54, s55, s56, s57, s58, s59, s60, s61,
        s62, s63, s64, s65, s66, s67, s68, s69, s70,
        s71, s72, s73, s74, s75, s76, s77, s78, s79,
        s80, s81, s82, s83, s84, s85, s86, s87, s88,
        s89, s90, s91, s92, s93, s94, s95, s96, s97,
        s98, s99, s100, s101, s102, s103, s104, s105,
        s106, s107, s108, s109, s110, s111, s112, s113,
        s114, s115, s116, s117, s118, s119, s120, s121,
        s122, s123, s124, s125, s126, s127,  d0, d1, d2,
        d3, d4, d5, d6, d7, d8, d9, d10, d11, d12, d13,
        d14, d15, d16, d17, d18, d19, d20, d21, d22, d23,
        d24, d25, d26, d27, d28, d29, d30, d31, d32, d33,
        d34, d35, d36, d37, d38, d39, d40, d41, d42, d43,
        d44, d45, d46, d47, d48, d49, d50, d51, d52, d53,
        d54, d55, d56, d57, d58, d59, d60, d61, d62, d63,
        q0, q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11,
        q12, q13, q14, q15, q16, q17, q18, q19, q20, q21,
        q22, q23, q24, q25, q26, q27, q28, q29, q30, q31
    };

    public HSAIL() {
        super("HSAIL",
                        8,
                        ByteOrder.LITTLE_ENDIAN,
                        allRegisters,
                        LOAD_STORE | STORE_STORE,
                        1,
                        q31.encoding + 1,
                        8);
    }


    public static int getStackOffset(Value reg) {
        return -(((StackSlot) reg).getRawOffset());
    }

    public static String mapStackSlot(Value reg) {
        StackSlot s = (StackSlot) reg;
        long offset = -s.getRawOffset();
        return "[%spillseg]" + "[" + offset + "]";
    }

    // @formatter:on
    public static String mapRegister(Value arg) {
        Register reg;
        int encoding = 0;
        String regPrefix = null;
        String argType = arg.getKind().getJavaName();
        if (argType.equals("double") || argType.equals("long")) {
            regPrefix = "$d";
        } else if (argType.equals("int") || argType.equals("float")) {
            regPrefix = "$s";
        } else {
            regPrefix = "$d";
        }
        switch (argType) {
            case "float":
                reg = asFloatReg(arg);
                encoding = reg.encoding() + 16;
                break;
            case "int":
                reg = asIntReg(arg);
                encoding = reg.encoding();
                break;
            case "long":
                reg = asLongReg(arg);
                encoding = reg.encoding();
                break;
            case "double":
                reg = asDoubleReg(arg);
                encoding = reg.encoding() + 16;
                break;
            case "Object":
                reg = asObjectReg(arg);
                encoding = reg.encoding();
                break;
            default:
                GraalInternalError.shouldNotReachHere();
                break;
        }
        return new String(regPrefix + encoding);
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {
        if (!(platformKind instanceof Kind)) {
            return false;
        }
        Kind kind = (Kind) platformKind;
        if (category == CPU) {
            switch (kind) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Long:
                case Object:
                    return true;
            }
        } else if (category == FPU) {
            switch (kind) {
                case Float:
                case Double:
                    return true;
            }
        }
        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        if (category == CPU) {
            return Kind.Long;
        } else if (category == FPU) {
            return Kind.Double;
        } else {
            return Kind.Illegal;
        }
    }
}
