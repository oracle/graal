/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding.EVEX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVGATHERDPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVGATHERDPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVGATHERQPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVGATHERQPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVPGATHERDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVPGATHERDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVPGATHERQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp.EVPGATHERQQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.EVCMPPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.EVCMPPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.EVCMPSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.EVCMPSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexIntegerCompareOp.EVPCMPB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexIntegerCompareOp.EVPCMPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexIntegerCompareOp.EVPCMPQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexIntegerCompareOp.EVPCMPW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMROp.EVPMOVDB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMROp.EVPMOVDW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMROp.EVPMOVQB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMROp.EVPMOVQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMROp.EVPMOVQW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMROp.EVPMOVWB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU16;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU64;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVDQU8;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVUPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.EVMOVUPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVBROADCASTSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVBROADCASTSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTDQ2PD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTDQ2PS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTPD2PS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTPS2PD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTQQ2PD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTQQ2PS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTPD2DQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTPD2QQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTPS2DQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTPS2QQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSD2SI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSD2SQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSD2USI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSD2USQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSS2SI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSS2SQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSS2USI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVCVTTSS2USQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVMOVDDUP;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPABSB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPABSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPABSQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPABSW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPBROADCASTW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVB2M;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVD2M;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVQ2M;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVSXBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVSXBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVSXBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVSXDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVSXWD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVSXWQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVW2M;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXWD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVPMOVZXWQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVSQRTPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVSQRTPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVUCOMISD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.EVUCOMISS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTB_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTD_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTQ_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp.EVPBROADCASTW_GPR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTSD2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTSI2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTSI2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTSQ2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTSQ2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTSS2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTUSQ2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.EVCVTUSQ2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPCMPUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPCMPUD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPCMPUQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp.EVPCMPUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVADDPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVADDPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVANDPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVANDPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVBLENDMPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVBLENDMPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVDIVPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVDIVPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVMULPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVMULPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVORPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVORPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPADDW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPANDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPANDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPBLENDMB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPBLENDMD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPBLENDMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPBLENDMW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPEQB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPEQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPEQQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPEQW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPGTB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPGTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPGTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPCMPGTW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMILPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMILPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPERMW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXSB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXSQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXSW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXUD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXUQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMAXUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINSB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINSQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINSW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINUD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINUQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMINUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULHUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULHW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPMULLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSHUFB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPSUBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVPXORQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVSQRTSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVSQRTSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVSUBPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVSUBPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVXORPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.EVXORPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSLLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSLLQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSLLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRAD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRAQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRAW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRLQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.EVPSRLW;
import static jdk.graal.compiler.vector.lir.amd64.AMD64VectorNodeMatchRules.getRegisterSize;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiFunction;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.EvexGatherOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVROp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Binary;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorBinary;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorBlend;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorClearOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorCompareOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorFloatCompareOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorGather;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorShuffle;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary;
import jdk.graal.compiler.lir.amd64.vector.AVX512CompressExpand;
import jdk.graal.compiler.lir.amd64.vector.AVX512MaskedOp;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.SqrtNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.vector.nodes.simd.MaskedOpMetaData;
import jdk.graal.compiler.vector.nodes.simd.SimdPermuteWithVectorIndicesNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This class implements special LIR generation for AVX512 instructions. If AVX512 is not fully
 * supported, {@link AMD64SSEAVXArithmeticLIRGenerator} is used instead.
 */
public class AMD64AVX512ArithmeticLIRGenerator extends AMD64VectorArithmeticLIRGenerator {

    public AMD64AVX512ArithmeticLIRGenerator(AllocatableValue nullRegisterValue) {
        super(nullRegisterValue);
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> emitVectorBinary(resultKind, EVPADDB, a, b);
                case WORD -> emitVectorBinary(resultKind, EVPADDW, a, b);
                case DWORD -> emitVectorBinary(resultKind, EVPADDD, a, b);
                case QWORD -> emitVectorBinary(resultKind, EVPADDQ, a, b);
                case SINGLE -> emitVectorBinary(resultKind, EVADDPS, a, b);
                case DOUBLE -> emitVectorBinary(resultKind, EVADDPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitAdd(resultKind, a, b, setFlags);
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> emitVectorBinary(resultKind, EVPSUBB, a, b);
                case WORD -> emitVectorBinary(resultKind, EVPSUBW, a, b);
                case DWORD -> emitVectorBinary(resultKind, EVPSUBD, a, b);
                case QWORD -> emitVectorBinary(resultKind, EVPSUBQ, a, b);
                case SINGLE -> emitVectorBinary(resultKind, EVSUBPS, a, b);
                case DOUBLE -> emitVectorBinary(resultKind, EVSUBPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitSub(resultKind, a, b, setFlags);
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> throw GraalError.shouldNotReachHere("AVX/AVX2/AVX512 does not support VPMULLB"); // ExcludeFromJacocoGeneratedReport
                case WORD -> emitVectorBinary(EVPMULLW, a, b);
                case DWORD -> emitVectorBinary(EVPMULLD, a, b);
                case QWORD -> emitVectorBinary(EVPMULLQ, a, b);
                case SINGLE -> emitVectorBinary(EVMULPS, a, b);
                case DOUBLE -> emitVectorBinary(EVMULPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitMul(a, b, setFlags);
        }
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> throw GraalError.shouldNotReachHere("AVX/AVX2/AVX512 does not support VPMULHB"); // ExcludeFromJacocoGeneratedReport
                case WORD -> emitVectorBinary(EVPMULHW, a, b);
                case DWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2/AVX512 does not support VPMULHD"); // ExcludeFromJacocoGeneratedReport
                case QWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2/AVX512 does not support VPMULHQ"); // ExcludeFromJacocoGeneratedReport
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitMulHigh(a, b);
        }
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> throw GraalError.shouldNotReachHere("AVX/AVX2/AVX512 does not support VPMULHB"); // ExcludeFromJacocoGeneratedReport
                case WORD -> emitVectorBinary(EVPMULHUW, a, b);
                case DWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2/AVX512 does not support VPMULHD"); // ExcludeFromJacocoGeneratedReport
                case QWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2/AVX512 does not support VPMULHQ"); // ExcludeFromJacocoGeneratedReport
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitUMulHigh(a, b);
        }
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case SINGLE -> emitVectorBinary(EVDIVPS, a, b);
                case DOUBLE -> emitVectorBinary(EVDIVPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitDiv(a, b, state);
        }
    }

    /**
     * Array of kinds suitable as single AVX-512 mask values. Must be sorted for easy indexing based
     * on the log of the size in bytes.
     */
    private static final AMD64Kind[] MASK_REGISTER_KINDS = new AMD64Kind[]{AMD64Kind.MASK8, AMD64Kind.MASK16, AMD64Kind.MASK32, AMD64Kind.MASK64};

    /**
     * Returns the index into {@link #MASK_REGISTER_KINDS} for vectors of the given length. Also
     * valid for use with {@link #MASK_JAVA_KINDS}.
     */
    private static int maskKindIndex(int vectorLength) {
        if (vectorLength <= 8) {
            // there are no platform kinds for vector lengths 1, 2 and 4
            return 0;
        }
        return CodeUtil.log2(vectorLength / 8);
    }

    private static AMD64Kind maskForVector(AMD64Kind vectorKind) {
        return MASK_REGISTER_KINDS[maskKindIndex(vectorKind.getVectorLength())];
    }

    private Variable emitBinaryHelperWithMasks(Value a, Value b, VexRVROp[] maskOps) {
        AMD64Kind aKind = (AMD64Kind) a.getPlatformKind();
        AMD64Kind bKind = (AMD64Kind) b.getPlatformKind();
        GraalError.guarantee(aKind.isMask() && aKind == bKind, "Mask kinds a(%s) and b(%s) for binary opmask instruction must be matching!", aKind, bKind);
        Variable result = getLIRGen().newVariable(a.getValueKind());
        getLIRGen().append(new AMD64VectorBinary.AVXOpMaskBinaryOp(maskOps[CodeUtil.log2(aKind.getSizeInBytes())], result, asAllocatable(a), asAllocatable(b)));
        return result;
    }

    private static final VexRVROp[] KAND_OPS = new VexRVROp[]{VexRVROp.KANDB, VexRVROp.KANDW, VexRVROp.KANDD, VexRVROp.KANDQ};

    @Override
    public Variable emitAnd(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isMask()) {
            return emitBinaryHelperWithMasks(a, b, KAND_OPS);
        } else if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE, WORD, DWORD, QWORD -> emitVectorBinary(EVPANDD, a, b);
                case SINGLE -> emitVectorBinary(EVANDPS, a, b);
                case DOUBLE -> emitVectorBinary(EVANDPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }
        return super.emitAnd(a, b);
    }

    private static final VexRVROp[] KOR_OPS = new VexRVROp[]{VexRVROp.KORB, VexRVROp.KORW, VexRVROp.KORD, VexRVROp.KORQ};

    @Override
    public Variable emitOr(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isMask()) {
            return emitBinaryHelperWithMasks(a, b, KOR_OPS);
        } else if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE, WORD, DWORD, QWORD -> emitVectorBinary(EVPORD, a, b);
                case SINGLE -> emitVectorBinary(EVORPS, a, b);
                case DOUBLE -> emitVectorBinary(EVORPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }
        return super.emitOr(a, b);
    }

    private static final VexRVROp[] KXOR_OPS = new VexRVROp[]{VexRVROp.KXORB, VexRVROp.KXORW, VexRVROp.KXORD, VexRVROp.KXORQ};

    @Override
    public Variable emitXor(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isMask()) {
            return emitBinaryHelperWithMasks(a, b, KXOR_OPS);
        } else if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE, WORD, DWORD, QWORD -> emitVectorBinary(EVPXORD, a, b);
                case SINGLE -> emitVectorBinary(EVXORPS, a, b);
                case DOUBLE -> emitVectorBinary(EVXORPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }
        return super.emitXor(a, b);
    }

    private static final VexRVROp[] KANDN_OPS = new VexRVROp[]{VexRVROp.KANDNB, VexRVROp.KANDNW, VexRVROp.KANDND, VexRVROp.KANDNQ};

    @Override
    public Value emitLogicalAndNot(Value a, Value b) {
        if (((AMD64Kind) a.getPlatformKind()).isMask()) {
            return emitBinaryHelperWithMasks(a, b, KANDN_OPS);
        }
        return super.emitLogicalAndNot(a, b);
    }

    private static final VexRROp[] KNOT_OPS = new VexRROp[]{VexRROp.KNOTB, VexRROp.KNOTW, VexRROp.KNOTD, VexRROp.KNOTQ};

    @Override
    public Variable emitNot(Value mask) {
        AMD64Kind maskKind = (AMD64Kind) mask.getPlatformKind();
        if (maskKind.isMask()) {
            Variable result = getLIRGen().newVariable(mask.getValueKind());
            VexRROp op = KNOT_OPS[CodeUtil.log2(maskKind.getSizeInBytes())];
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryRROp(op, AVXKind.getRegisterSize(mask), asAllocatable(result), asAllocatable(mask)));
            return result;
        } else {
            return super.emitNot(mask);
        }
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        AMD64Kind aKind = (AMD64Kind) a.getPlatformKind();
        AMD64Kind bKind = (AMD64Kind) b.getPlatformKind();

        if (!aKind.isXMM()) {
            return super.emitShl(a, b);
        }

        if (bKind.isXMM()) {
            GraalError.guarantee(aKind.getScalar() == bKind.getScalar(), "Must be the same kind");
            return switch (aKind.getScalar()) {
                case WORD -> emitVectorBinary(VexRVMOp.EVPSLLVW, a, b);
                case DWORD -> emitVectorBinary(VexRVMOp.EVPSLLVD, a, b);
                case QWORD -> emitVectorBinary(VexRVMOp.EVPSLLVQ, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }

        return switch (aKind.getScalar()) {
            case WORD -> emitShift(EVPSLLW, a, b);
            case DWORD -> emitShift(EVPSLLD, a, b);
            case QWORD -> emitShift(EVPSLLQ, a, b);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        AMD64Kind aKind = (AMD64Kind) a.getPlatformKind();
        AMD64Kind bKind = (AMD64Kind) b.getPlatformKind();

        if (!aKind.isXMM()) {
            return super.emitShr(a, b);
        }

        if (bKind.isXMM()) {
            GraalError.guarantee(aKind.getScalar() == bKind.getScalar(), "Must be the same kind");
            return switch (aKind.getScalar()) {
                case WORD -> emitVectorBinary(VexRVMOp.EVPSRAVW, a, b);
                case DWORD -> emitVectorBinary(VexRVMOp.EVPSRAVD, a, b);
                case QWORD -> emitVectorBinary(VexRVMOp.EVPSRAVQ, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }

        return switch (aKind.getScalar()) {
            case WORD -> emitShift(EVPSRAW, a, b);
            case DWORD -> emitShift(EVPSRAD, a, b);
            case QWORD -> emitShift(EVPSRAQ, a, b);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        AMD64Kind aKind = (AMD64Kind) a.getPlatformKind();
        AMD64Kind bKind = (AMD64Kind) b.getPlatformKind();

        if (!aKind.isXMM()) {
            return super.emitUShr(a, b);
        }

        if (bKind.isXMM()) {
            GraalError.guarantee(aKind.getScalar() == bKind.getScalar(), "Must be the same kind");
            return switch (aKind.getScalar()) {
                case WORD -> emitVectorBinary(VexRVMOp.EVPSRLVW, a, b);
                case DWORD -> emitVectorBinary(VexRVMOp.EVPSRLVD, a, b);
                case QWORD -> emitVectorBinary(VexRVMOp.EVPSRLVQ, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }

        return switch (aKind.getScalar()) {
            case WORD -> emitShift(EVPSRLW, a, b);
            case DWORD -> emitShift(EVPSRLD, a, b);
            case QWORD -> emitShift(EVPSRLQ, a, b);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Value emitMathAbs(Value input) {
        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> emitUnary(EVPABSB, input);
                case WORD -> emitUnary(EVPABSW, input);
                case DWORD -> emitUnary(EVPABSD, input);
                case QWORD -> emitUnary(EVPABSQ, input);
                default ->
                    // abs on SINGLE and DOUBLE vectors should have been lowered already
                    throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return switch (kind) {
                case SINGLE -> {
                    Value floatMask = getLIRGen().emitJavaConstant(JavaConstant.forFloat(Float.intBitsToFloat(0x7FFFFFFF)));
                    yield emitVectorBinary(EVANDPS, input, floatMask);
                }
                case DOUBLE -> {
                    Value doubleMask = getLIRGen().emitJavaConstant(JavaConstant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)));
                    yield emitVectorBinary(EVANDPD, input, doubleMask);
                }
                default -> super.emitMathAbs(input);
            };
        }
    }

    @Override
    public Value emitMathSqrt(Value input) {
        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case SINGLE -> emitUnary(EVSQRTPS, input);
                case DOUBLE -> emitUnary(EVSQRTPD, input);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return switch (kind) {
                case SINGLE -> emitUnary(EVSQRTSS, input);
                case DOUBLE -> emitUnary(EVSQRTSD, input);
                default -> super.emitMathSqrt(input);
            };
        }
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        AMD64Kind from = (AMD64Kind) inputVal.getPlatformKind();
        if (from.isMask()) {
            GraalError.guarantee(from == to.getPlatformKind(), "Mismatching vector masks!");
            return inputVal;
        } else if (from.getVectorLength() > 1) {
            return new CastValue(to, getLIRGen().asAllocatable(inputVal));
        } else {
            switch (from) {
                case DWORD -> {
                    if (to.getPlatformKind() == AMD64Kind.SINGLE) {
                        return emitConvertOp(to, EVMOVD, inputVal, false);
                    }
                }
                case QWORD -> {
                    if (to.getPlatformKind() == AMD64Kind.DOUBLE) {
                        return emitConvertOp(to, EVMOVQ, inputVal, false);
                    }
                }
                case SINGLE -> {
                    if (to.getPlatformKind() == AMD64Kind.DWORD) {
                        return emitConvertToIntOp(to, EVMOVD, inputVal);
                    } else if (to.getPlatformKind().getVectorLength() > 1 && to.getPlatformKind().getSizeInBytes() == from.getSizeInBytes()) {
                        return new CastValue(to, getLIRGen().asAllocatable(inputVal));
                    }
                }
                case DOUBLE -> {
                    if (to.getPlatformKind() == AMD64Kind.QWORD) {
                        return emitConvertToIntOp(to, EVMOVQ, inputVal);
                    } else if (to.getPlatformKind().getVectorLength() > 1 && to.getPlatformKind().getSizeInBytes() == from.getSizeInBytes()) {
                        return new CastValue(to, getLIRGen().asAllocatable(inputVal));
                    }
                }
            }
        }
        return super.emitReinterpret(to, inputVal);
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal, boolean canBeNaN, boolean canOverflow) {
        AMD64Kind kind = (AMD64Kind) inputVal.getPlatformKind();
        int length = kind.getVectorLength();
        if (length > 1) {
            AMD64Kind baseKind = kind.getScalar();

            switch (op) {
                case D2F:
                    assert baseKind == AMD64Kind.DOUBLE : baseKind;
                    // when input length is 4 doubles or less we store the result in a 128
                    // bit/XMM register otherwise we use a YMM register
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.SINGLE, Math.max(length, 4)), EVCVTPD2PS, inputVal, true);
                case D2I:
                    assert baseKind == AMD64Kind.DOUBLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), EVCVTTPD2DQ, inputVal, true);
                case D2L:
                    assert baseKind == AMD64Kind.DOUBLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVCVTTPD2QQ, inputVal);
                case F2D:
                    assert baseKind == AMD64Kind.SINGLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DOUBLE, length), EVCVTPS2PD, inputVal);
                case F2I:
                    assert baseKind == AMD64Kind.SINGLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), EVCVTTPS2DQ, inputVal);
                case F2L:
                    assert baseKind == AMD64Kind.SINGLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVCVTTPS2QQ, inputVal);
                case I2D:
                    assert baseKind == AMD64Kind.DWORD : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DOUBLE, length), EVCVTDQ2PD, inputVal);
                case I2F:
                    assert baseKind == AMD64Kind.DWORD : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.SINGLE, length), EVCVTDQ2PS, inputVal);
                case L2F:
                    assert baseKind == AMD64Kind.QWORD : baseKind;
                    // when input length is 4 QWORDs or less we store the result in a 128
                    // bit/XMM register otherwise we use a YMM register
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.SINGLE, Math.max(length, 4)), EVCVTQQ2PS, inputVal, true);
                case L2D:
                    assert baseKind == AMD64Kind.QWORD : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DOUBLE, length), EVCVTQQ2PD, inputVal);
                default:
                    throw GraalError.unimplemented("unsupported vectorized convert " + op); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            boolean narrow = false;
            switch (op) {
                case D2F:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.SINGLE, EVCVTSD2SS, inputVal, op.signedness());
                case D2I:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.DWORD, EVCVTTSD2SI, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case D2UI:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.DWORD, EVCVTTSD2USI, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case D2L:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.QWORD, EVCVTTSD2SQ, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case D2UL:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.QWORD, EVCVTTSD2USQ, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case F2D:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.DOUBLE, EVCVTSS2SD, inputVal, op.signedness());
                case F2I:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.DWORD, EVCVTTSS2SI, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case F2UI:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.DWORD, EVCVTTSS2USI, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case F2L:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.QWORD, EVCVTTSS2SQ, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case F2UL:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.QWORD, EVCVTTSS2USQ, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case I2D:
                    assert kind == AMD64Kind.DWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.DOUBLE, EVCVTSI2SD, inputVal, op.signedness());
                case I2F:
                    assert kind == AMD64Kind.DWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.SINGLE, EVCVTSI2SS, inputVal, op.signedness());
                case L2D:
                    assert kind == AMD64Kind.QWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.DOUBLE, EVCVTSQ2SD, inputVal, op.signedness());
                case L2F:
                    assert kind == AMD64Kind.QWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.SINGLE, EVCVTSQ2SS, inputVal, op.signedness());
                case UL2D:
                    assert kind == AMD64Kind.QWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.DOUBLE, EVCVTUSQ2SD, inputVal, op.signedness());
                case UL2F:
                    assert kind == AMD64Kind.QWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.SINGLE, EVCVTUSQ2SS, inputVal, op.signedness());
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    @Override
    public Value emitSignExtend(Value input, int fromBits, int toBits) {
        if (fromBits == toBits) {
            return input;
        }

        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        int length = kind.getVectorLength();
        if (length > 1) {
            assert kind.getScalar().getSizeInBytes() * Byte.SIZE == fromBits : kind + " " + kind.getScalar() + " " + fromBits;
            assert toBits > fromBits : "unsupported vector sign extend (" + fromBits + " bit -> " + toBits + " bit)";

            return switch (((fromBits >> 3) << 4) | (toBits >> 3)) {
                // 0xfromBytes_toBytes
                case 0x1_2 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.WORD, length), EVPMOVSXBW, input);
                case 0x1_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), EVPMOVSXBD, input);
                case 0x1_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVPMOVSXBQ, input);
                case 0x2_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), EVPMOVSXWD, input);
                case 0x2_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVPMOVSXWQ, input);
                case 0x4_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVPMOVSXDQ, input);
                default -> throw GraalError.shouldNotReachHere("unsupported vector sign extend (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
            };
        }
        return super.emitSignExtend(input, fromBits, toBits);
    }

    @Override
    public Value emitZeroExtend(Value input, int fromBits, int toBits, boolean requiresExplicitZeroExtend, boolean requiresLIRKindChange) {
        if (fromBits == toBits) {
            return input;
        }

        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        int length = kind.getVectorLength();
        if (length > 1) {
            assert kind.getScalar().getSizeInBytes() * Byte.SIZE == fromBits : kind + " " + kind.getScalar() + " " + fromBits;
            assert toBits > fromBits : "unsupported vector zero extend (" + fromBits + " bit -> " + toBits + " bit)";

            return switch (((fromBits >> 3) << 4) | (toBits >> 3)) {
                // 0xfromBytes_toBytes
                case 0x1_2 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.WORD, length), EVPMOVZXBW, input);
                case 0x1_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), EVPMOVZXBD, input);
                case 0x1_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVPMOVZXBQ, input);
                case 0x2_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), EVPMOVZXWD, input);
                case 0x2_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVPMOVZXWQ, input);
                case 0x4_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), EVPMOVZXDQ, input);
                default -> throw GraalError.shouldNotReachHere("unsupported vector zero extend (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
            };
        }
        return super.emitZeroExtend(input, fromBits, toBits, requiresExplicitZeroExtend, requiresLIRKindChange);
    }

    @Override
    public Value emitNarrow(Value inputVal, int toBits) {
        AMD64Kind inputKind = (AMD64Kind) inputVal.getPlatformKind();
        int length = inputKind.getVectorLength();
        if (length > 1) {
            int fromBits = inputKind.getScalar().getSizeInBytes() * Byte.SIZE;
            if (fromBits == toBits) {
                return inputVal;
            }

            GraalError.guarantee(fromBits > toBits, "trying to emit inverse narrow");
            GraalError.guarantee(CodeUtil.isPowerOf2(fromBits) && CodeUtil.isPowerOf2(toBits), "cannot emit narrow for %sbit to %sbit", fromBits, toBits);

            AMD64Kind elementKind = (AMD64Kind) getLIRGen().getLIRKindTool().getIntegerKind(toBits).getPlatformKind();
            AMD64Kind resultPlatformKind = AVXKind.getAVXKind(elementKind, length);
            LIRKind resultKind = LIRKind.combine(inputVal).changeType(resultPlatformKind);
            AllocatableValue input = asAllocatable(inputVal);
            AVXSize inputSize = AVXKind.getRegisterSize(inputKind);

            AMD64Assembler.VexMROp op = switch ((fromBits | toBits) >> 3) {
                // left most set bit = from, right most set bit = to
                // ... QDWB
                case 0b0011 -> EVPMOVWB;
                case 0b0101 -> EVPMOVDB;
                case 0b0110 -> EVPMOVDW;
                case 0b1001 -> EVPMOVQB;
                case 0b1010 -> EVPMOVQW;
                case 0b1100 -> EVPMOVQD;
                default -> throw GraalError.shouldNotReachHere(String.format("cannot emit narrow for %sbit to %sbit", fromBits, toBits));
            };

            Variable result = getLIRGen().newVariable(resultKind);
            getLIRGen().append(new AMD64VectorUnary.AVXUnaryMROp(op, inputSize, result, input));
            return result;
        }
        return super.emitNarrow(inputVal, toBits);
    }

    @Override
    protected VexMoveOp getScalarFloatLoad(AMD64Kind kind) {
        return switch (kind) {
            case SINGLE -> EVMOVSS;
            case DOUBLE -> EVMOVSD;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Variable emitMaskedLoad(LIRKind lirKind, Value address, Value mask, LIRFrameState state, MemoryOrderMode memoryOrder) {
        GraalError.guarantee(!MemoryOrderMode.ordersMemoryAccesses(memoryOrder), "Ordered access currently not supported");
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);
        VexMoveOp op = switch (kind.getScalar()) {
            case BYTE -> EVMOVDQU8;
            case WORD -> EVMOVDQU16;
            case DWORD -> EVMOVDQU32;
            case QWORD -> EVMOVDQU64;
            case SINGLE -> EVMOVUPS;
            case DOUBLE -> EVMOVUPD;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };

        AVXSize size = switch (kind.getSizeInBytes()) {
            // We may want to support smaller-than-XMM masked load by chopping off the mask
            case 16 -> AVXSize.XMM;
            case 32 -> AVXSize.YMM;
            case 64 -> AVXSize.ZMM;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };

        Variable result = getLIRGen().newVariable(lirKind);
        getLIRGen().append(new AMD64VectorMove.VectorMaskedLoadOp(size, op, result, loadAddress, asAllocatable(mask), state));
        return result;
    }

    @Override
    protected void emitStore(AMD64Kind kind, AMD64AddressValue address, AllocatableValue input, LIRFrameState state) {
        if (kind.isXMM()) {
            VexMoveOp op;
            if (kind.getVectorLength() > 1) {
                op = switch (kind.getScalar()) {
                    case SINGLE -> EVMOVUPS;
                    case DOUBLE -> EVMOVUPD;
                    default -> switch (AVXKind.getDataSize(kind)) {
                        // handle vector with a size of DWORD - e.g. AMD64Kind.V32_BYTE
                        case DWORD -> EVMOVD;
                        // handle vector with a size of QWORD - e.g. AMD64Kind.V64_WORD
                        case QWORD -> EVMOVQ;
                        default -> EVMOVDQU32;
                    };
                };
            } else {
                op = switch (kind) {
                    case SINGLE -> EVMOVSS;
                    case DOUBLE -> EVMOVSD;
                    default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind); // ExcludeFromJacocoGeneratedReport
                };
            }
            getLIRGen().append(new AMD64VectorMove.VectorStoreOp(AVXKind.getRegisterSize(kind), op, address, getLIRGen().asAllocatable(input), state));
        } else {
            super.emitStore(kind, address, input, state);
        }
    }

    @Override
    public void emitMaskedStore(LIRKind lirKind, Value address, Value mask, Value value, LIRFrameState state, MemoryOrderMode memoryOrder) {
        GraalError.guarantee(!MemoryOrderMode.ordersMemoryAccesses(memoryOrder), "Ordered access currently not supported");
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);
        VexMoveOp op = switch (kind.getScalar()) {
            case BYTE -> EVMOVDQU8;
            case WORD -> EVMOVDQU16;
            case DWORD -> EVMOVDQU32;
            case QWORD -> EVMOVDQU64;
            case SINGLE -> EVMOVUPS;
            case DOUBLE -> EVMOVUPD;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };

        AVXSize size = switch (kind.getSizeInBytes()) {
            // We may want to support smaller-than-XMM masked load by chopping off the mask
            case 16 -> AVXSize.XMM;
            case 32 -> AVXSize.YMM;
            case 64 -> AVXSize.ZMM;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };

        getLIRGen().append(new AMD64VectorMove.VectorMaskedStoreOp(size, op, loadAddress, asAllocatable(mask), asAllocatable(value), state));
    }

    @Override
    public void emitCompareOp(AMD64Kind kind, AllocatableValue left, Value right) {
        if (kind == AMD64Kind.SINGLE) {
            getLIRGen().append(new AMD64VectorCompareOp(EVUCOMISS, left, getLIRGen().asAllocatable(right)));
        } else if (kind == AMD64Kind.DOUBLE) {
            getLIRGen().append(new AMD64VectorCompareOp(EVUCOMISD, left, getLIRGen().asAllocatable(right)));
        } else {
            super.emitCompareOp(kind, left, right);
        }
    }

    @Override
    protected void emitVectorBroadcast(AMD64Kind elementKind, Variable result, Value input) {
        ValueKind<?> singleValueKind = input.getValueKind();
        switch (elementKind) {
            case BYTE -> {
                Variable temp = getLIRGen().newVariable(singleValueKind.changeType(AMD64Kind.V128_BYTE));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(temp, getLIRGen().asAllocatable(input), EVEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(EVPBROADCASTB, getRegisterSize(result), result, temp));
            }
            case WORD -> {
                Variable temp = getLIRGen().newVariable(singleValueKind.changeType(AMD64Kind.V128_WORD));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(temp, getLIRGen().asAllocatable(input), EVEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(EVPBROADCASTW, getRegisterSize(result), result, temp));
            }
            case DWORD -> {
                Variable temp = getLIRGen().newVariable(singleValueKind.changeType(AMD64Kind.V128_DWORD));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(temp, getLIRGen().asAllocatable(input), EVEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(EVPBROADCASTD, getRegisterSize(result), result, temp));
            }
            case QWORD -> {
                Variable temp = getLIRGen().newVariable(singleValueKind.changeType(AMD64Kind.V128_QWORD));
                getLIRGen().append(new AMD64VectorShuffle.LongToVectorOp(temp, getLIRGen().asAllocatable(input), EVEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(EVPBROADCASTQ, getRegisterSize(result), result, temp));
            }
            case SINGLE -> getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(EVBROADCASTSS, getRegisterSize(result), result, input));

            case DOUBLE -> {
                AVXSize registerSize = getRegisterSize(result);
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(registerSize == AVXSize.XMM ? EVMOVDDUP : EVBROADCASTSD, registerSize, result, input));
            }
            default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + elementKind); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Value emitVectorToBitMask(LIRKind resultKind, Value vector) {
        throw GraalError.shouldNotReachHere("AVX512 should use opmask");
    }

    /**
     * Convert a vector register to a mask register.
     * <p>
     * Sets each bit in the mask register to 1 or 0 based on the value of the most significant bit
     * of the corresponding element in the vector.
     */
    public Variable emitVectorToOpmask(AMD64Kind vectorKind, AllocatableValue inputVector) {
        AVXSize inputSize = AVXKind.getRegisterSize(vectorKind);
        AMD64Kind maskKind = maskForVector(vectorKind);
        LIRKind resultKind = LIRKind.value(maskKind);
        Variable resultMask = getLIRGen().newVariable(resultKind);
        VexRMOp op = switch (vectorKind.getScalar()) {
            case BYTE -> EVPMOVB2M;
            case WORD -> EVPMOVW2M;
            case DWORD, SINGLE -> EVPMOVD2M;
            case QWORD, DOUBLE -> EVPMOVQ2M;
            default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + vectorKind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
        getLIRGen().append(new AMD64VectorUnary.AVXUnaryRROp(op, inputSize, resultMask, inputVector));
        return resultMask;
    }

    private Value emitConstOpmaskInK1(AMD64Kind maskKind, JavaConstant maskValue) {
        Value mask = emitConstOpmask(maskKind, maskValue);
        Value k1 = AMD64.k1.asValue(LIRKind.value(maskKind));
        getLIRGen().append(new AMD64Move.MoveToRegOp((AMD64Kind) mask.getPlatformKind(), asAllocatable(k1), asAllocatable(mask)));
        return k1;
    }

    public Value emitConstOpmask(AMD64Kind maskKind, JavaConstant maskValue) {
        if (maskValue.asLong() == 0) {
            Variable mask = getLIRGen().newVariable(LIRKind.value(maskKind));
            getLIRGen().append(new AMD64VectorClearOp(mask, EVEX));
            return mask;
        }
        long allOnes = maskKind == AMD64Kind.MASK64 ? -1L : (1L << (maskKind.getSizeInBytes() * Byte.SIZE)) - 1;
        if (maskValue.asLong() == allOnes) {
            Variable mask = getLIRGen().newVariable(LIRKind.value(maskKind));
            getLIRGen().append(new AMD64VectorClearOp(mask, EVEX));
            return emitNot(mask);
        }
        Variable constant = getLIRGen().newVariable(LIRKind.fromJavaKind(getArchitecture(), maskValue.getJavaKind()));
        getLIRGen().append(new AMD64Move.MoveFromConstOp(constant, maskValue));
        return emitMoveIntegerToOpMask(LIRKind.value(maskKind), constant);
    }

    @Override
    public Value emitVectorGather(LIRKind resultKind, Value base, Value offsets) {
        Variable result = getLIRGen().newVariable(resultKind);
        AllocatableValue b = getLIRGen().asAllocatable(base);
        AllocatableValue offs = getLIRGen().asAllocatable(offsets);

        AMD64Kind offsetPlatformKind = (AMD64Kind) offs.getPlatformKind();
        int vectorLength = offsetPlatformKind.getVectorLength();
        AMD64Kind offsetKind = offsetPlatformKind.getScalar();
        AMD64Kind resultElementKind = ((AMD64Kind) resultKind.getPlatformKind()).getScalar();

        AVXSize size = getRegisterSize(result).getBytes() > getRegisterSize(offs).getBytes()
                        ? getRegisterSize(result)
                        : getRegisterSize(offs);

        GraalError.guarantee(offsetKind == AMD64Kind.DWORD || offsetKind == AMD64Kind.QWORD, "vector gather offsets must be DWORD or QWORD, got: %s", offsetKind); // ExcludeFromJacocoGeneratedReport
        GraalError.guarantee(vectorLength <= 16, "cannot gather more than 16 values at once");

        /*
         * Make an all-ones mask, meaning that we want to gather all elements. More general masked
         * gathers are not supported yet. We must construct this mask immediately before the gather
         * instruction because it clobbers it. It must also be a fixed register for the Use+Temp
         * trick to work. Note that k0 is not allowed as the mask register for the AVX512 gather
         * instructions.
         */
        int maskValue = (1 << vectorLength) - 1;
        Value maskInK1 = maskValue > 0xFF
                        ? emitConstOpmaskInK1(AMD64Kind.MASK16, JavaConstant.forShort((short) maskValue))
                        : emitConstOpmaskInK1(AMD64Kind.MASK8, JavaConstant.forByte((byte) maskValue));

        EvexGatherOp op = offsetKind == AMD64Kind.DWORD
                        ? switch (resultElementKind) {
                            case DWORD -> EVPGATHERDD;
                            case QWORD -> EVPGATHERDQ;
                            case DOUBLE -> EVGATHERDPD;
                            case SINGLE -> EVGATHERDPS;
                            default -> throw GraalError.shouldNotReachHere("unsupported vector gather: offset kind " + offsetKind + ", result element kind " + resultElementKind); // ExcludeFromJacocoGeneratedReport
                        }
                        : switch (resultElementKind) {
                            case DWORD -> EVPGATHERQD;
                            case QWORD -> EVPGATHERQQ;
                            case DOUBLE -> EVGATHERQPD;
                            case SINGLE -> EVGATHERQPS;
                            default -> throw GraalError.shouldNotReachHere("unsupported vector gather: offset kind " + offsetKind + ", result element kind " + resultElementKind); // ExcludeFromJacocoGeneratedReport
                        };

        getLIRGen().append(new AMD64VectorGather.EvexVectorGatherOp(op, size, result, b, offs, asAllocatable(maskInK1)));
        return result;
    }

    @Override
    public Value emitVectorFill(LIRKind lirKind, Value input) {
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        AMD64Kind scalarKind = kind.getScalar();
        Variable result = getLIRGen().newVariable(lirKind);

        if (input instanceof ConstantValue) {
            Constant constant = ((ConstantValue) input).getConstant();
            if (constant instanceof JavaConstant && constant.isDefaultForKind()) {
                getLIRGen().append(new AMD64VectorClearOp(result, EVEX));
                return result;
            }
        }

        switch (scalarKind) {
            case BYTE -> getLIRGen().append(new AMD64VectorUnary.AVXUnaryRROp(EVPBROADCASTB_GPR, getRegisterSize(result), result, asAllocatable(input)));
            case WORD -> getLIRGen().append(new AMD64VectorUnary.AVXUnaryRROp(EVPBROADCASTW_GPR, getRegisterSize(result), result, asAllocatable(input)));
            case DWORD -> getLIRGen().append(new AMD64VectorUnary.AVXUnaryRROp(EVPBROADCASTD_GPR, getRegisterSize(result), result, asAllocatable(input)));
            case QWORD -> getLIRGen().append(new AMD64VectorUnary.AVXUnaryRROp(EVPBROADCASTQ_GPR, getRegisterSize(result), result, asAllocatable(input)));
            case SINGLE, DOUBLE -> emitVectorBroadcast(scalarKind, result, input);
        }

        return result;
    }

    @Override
    public Value emitVectorPackedEquals(Value vectorA, Value vectorB) {
        AMD64Kind kind = (AMD64Kind) vectorA.getPlatformKind();
        AllocatableValue result = getLIRGen().newVariable(LIRKind.value(maskForVector(kind)));

        AllocatableValue a = asAllocatable(vectorA);
        AllocatableValue b = asAllocatable(vectorB);
        AVXSize size = getRegisterSize(a);

        VexRVMOp op = switch (((AMD64Kind) vectorB.getPlatformKind()).getScalar()) {
            case BYTE -> EVPCMPEQB;
            case WORD -> EVPCMPEQW;
            case DWORD -> EVPCMPEQD;
            case QWORD -> EVPCMPEQQ;
            default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + vectorB.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        };
        getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(op, size, result, a, b));

        return result;
    }

    @Override
    public Value emitVectorPackedComparison(CanonicalCondition condition, Value vectorA, Value vectorB, boolean unorderedIsTrue) {
        if (condition == CanonicalCondition.EQ && ((AMD64Kind) vectorB.getPlatformKind()).getScalar().isInteger()) {
            return emitVectorPackedEquals(vectorA, vectorB);
        }

        AllocatableValue a = getLIRGen().asAllocatable(vectorA);
        AllocatableValue b = getLIRGen().asAllocatable(vectorB);

        AVXSize size = getRegisterSize(vectorA);

        if (condition == CanonicalCondition.BT) {
            AMD64Kind elementKind = ((AMD64Kind) vectorB.getPlatformKind()).getScalar();
            assert elementKind.isInteger() : "Unsigned comparison not supported for floating point vector";

            AMD64Kind kind = (AMD64Kind) vectorA.getPlatformKind();
            AllocatableValue result = getLIRGen().newVariable(LIRKind.value(maskForVector(kind)));

            VexRVMIOp op = switch (elementKind) {
                case BYTE -> EVPCMPUB;
                case WORD -> EVPCMPUW;
                case DWORD -> EVPCMPUD;
                case QWORD -> EVPCMPUQ;
                default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + vectorB.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
            };
            getLIRGen().append(new AVXVectorCompareOp(op, size, result, a, b, 0b001));
            return result;
        } else {
            assert condition == CanonicalCondition.LT || !((AMD64Kind) vectorB.getPlatformKind()).getScalar().isInteger() : condition + " " + vectorA + " " + vectorB;

            AMD64Kind kind = (AMD64Kind) vectorA.getPlatformKind();
            AllocatableValue result = getLIRGen().newVariable(LIRKind.value(maskForVector(kind)));

            switch (((AMD64Kind) vectorB.getPlatformKind()).getScalar()) {
                /*
                 * In the integer cases, note the flipped arguments a and b to implement LT
                 * comparison with GT instruction. This could instead be done using the VPCMPB/W/D/Q
                 * instruction and the LT predicate, but this instruction is currently not
                 * implemented in the AMD64Assembler.
                 */
                case BYTE -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(EVPCMPGTB, size, result, b, a));
                case WORD -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(EVPCMPGTW, size, result, b, a));
                case DWORD -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(EVPCMPGTD, size, result, b, a));
                case QWORD -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(EVPCMPGTQ, size, result, b, a));

                // Floating-point comparison instructions take a predicate, so arguments are not
                // flipped.
                case SINGLE -> getLIRGen().append(new AMD64VectorFloatCompareOp(EVCMPPS, size, result, a, b,
                                VexFloatCompareOp.Predicate.getPredicate(condition.asCondition(), unorderedIsTrue)));
                case DOUBLE -> getLIRGen().append(new AMD64VectorFloatCompareOp(EVCMPPD, size, result, a, b,
                                VexFloatCompareOp.Predicate.getPredicate(condition.asCondition(), unorderedIsTrue)));
                default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + vectorB.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
            }
            return result;
        }
    }

    @Override
    public Variable emitVectorBlend(Value zeroValue, Value oneValue, Value mask) {
        GraalError.guarantee(((AMD64Kind) mask.getPlatformKind()).isMask(), "Expected mask kind for VectorBlend!");
        Variable result = getLIRGen().newVariable(zeroValue.getValueKind());

        VexRVMOp blend = switch (((AMD64Kind) zeroValue.getPlatformKind()).getScalar()) {
            case BYTE -> EVPBLENDMB;
            case WORD -> EVPBLENDMW;
            case DWORD -> EVPBLENDMD;
            case QWORD -> EVPBLENDMQ;
            case SINGLE -> EVBLENDMPS;
            case DOUBLE -> EVBLENDMPD;
            default -> throw GraalError.shouldNotReachHere("Invalid input kind: " + ((AMD64Kind) zeroValue.getPlatformKind()).getScalar()); // ExcludeFromJacocoGeneratedReport
        };

        getLIRGen().append(new AMD64VectorBlend.EvexBlendOp(blend, getRegisterSize(zeroValue), getLIRGen().asAllocatable(result), getLIRGen().asAllocatable(zeroValue),
                        getLIRGen().asAllocatable(oneValue), getLIRGen().asAllocatable(mask)));
        return result;
    }

    /**
     * Emits a vector blend with a constant selector. This must massage the selector into a constant
     * of the appropriate kind (single bitmask or vector of bitmasks) and emit that, then it falls
     * back to the general {@link #emitVectorBlend(Value, Value, Value)}.
     */
    @Override
    public Variable emitVectorBlend(Value zeroValue, Value oneValue, boolean[] selector) {
        int kindIndex = maskKindIndex(selector.length);
        JavaKind maskJavaKind = MASK_JAVA_KINDS[kindIndex];
        AMD64Kind maskRegisterKind = MASK_REGISTER_KINDS[kindIndex];
        long mask = 0;
        for (int i = selector.length - 1; i >= 0; i--) {
            mask = (mask << 1) | (selector[i] ? 1 : 0);
        }
        Value maskValue = emitConstOpmask(maskRegisterKind, JavaConstant.forIntegerKind(maskJavaKind, mask));
        return emitVectorBlend(zeroValue, oneValue, maskValue);
    }

    @Override
    public Variable emitVectorIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        AMD64Kind resultKind = (AMD64Kind) trueValue.getPlatformKind();
        if (resultKind == AMD64Kind.SINGLE || resultKind == AMD64Kind.DOUBLE) {
            return emitFloatingPointConditionalMove(
                            (scalarTrue, scalarFalse) -> getLIRGen().emitIntegerTestMove(left, right, scalarTrue, scalarFalse),
                            trueValue, falseValue);
        }

        if (trueValue.getPlatformKind().getVectorLength() == 1) {
            return null;
        }

        // Compute an element-wise mask by computing ((left & right) == 0) for each component.
        // This could be replaced with VPTESTM_ followed by a blend
        Variable andResult = emitAnd(left, right);
        Variable zeroValue = getLIRGen().newVariable(left.getValueKind());
        getLIRGen().append(new AMD64VectorClearOp(zeroValue, EVEX));
        Value comparisonMask = emitVectorPackedComparison(CanonicalCondition.EQ, andResult, zeroValue, false);
        return emitVectorBlend(falseValue, trueValue, comparisonMask);
    }

    @Override
    public Variable emitVectorConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        AMD64Kind resultKind = (AMD64Kind) trueValue.getPlatformKind();
        if (resultKind == AMD64Kind.SINGLE || resultKind == AMD64Kind.DOUBLE) {
            return emitFloatingPointConditionalMove(
                            (scalarTrue, scalarFalse) -> getLIRGen().emitConditionalMove(cmpKind, left, right, cond, unorderedIsTrue, scalarTrue, scalarFalse),
                            trueValue, falseValue);
        }

        if (cmpKind.getVectorLength() == 1) {
            return null;
        }

        Condition.CanonicalizedCondition canonicalizedCond = cond.canonicalize();
        Value leftOperand = left;
        Value rightOperand = right;
        if (canonicalizedCond.mustMirror()) {
            leftOperand = right;
            rightOperand = left;
        }

        Value trueOperand = trueValue;
        Value falseOperand = falseValue;
        if (canonicalizedCond.mustNegate()) {
            trueOperand = falseValue;
            falseOperand = trueValue;
        }

        Value comparisonMask = emitVectorPackedComparison(canonicalizedCond.getCanonicalCondition(), leftOperand, rightOperand, unorderedIsTrue);
        return emitVectorBlend(falseOperand, trueOperand, comparisonMask);
    }

    /**
     * Emits code for a branchless floating-point Math.min/Math.max operation. Requires AVX.
     * <p>
     * Supports (scalarReg,scalarReg), (scalarReg,scalarConst), and (vectorReg,vectorReg) operands.
     *
     * @see Math#max(double, double)
     * @see Math#max(float, float)
     * @see Math#min(double, double)
     * @see Math#min(float, float)
     */
    @Override
    protected Value emitMathMinMax(Value a, Value b, AMD64MathMinMaxFloatOp minmaxop) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getScalar().isInteger()) {
            return emitIntegerMinMax(a, b, minmaxop, NumUtil.Signedness.SIGNED);
        }

        // vmin*/vmax*: if the values being compared are both 0.0 (of either sign), dst = src2.
        // hence, if one argument is +0.0 and the other is -0.0, to get the correct result, we
        // have to reorder the source registers such that -0.0 (min) / +0.0 (max) is in src2.
        // Therefore, if a (min) / b (max) is negative (most significant bit is 1), swap a and b,
        // so that if one is -0.0 and the other is +0.0, the correct result is in b', i.e.:
        // min: a' = +0.0, b' = -0.0 (negative values in a are moved to b').
        // max: a' = -0.0, b' = +0.0 (negative values in b are moved to a').
        AllocatableValue signVector = asAllocatable(minmaxop == AMD64MathMinMaxFloatOp.Min ? a : b);
        // convert vector register to a mask register (requires AVX512DQ).
        AllocatableValue selectMask = emitVectorToOpmask(kind, signVector);
        AllocatableValue atmp = emitVectorBlend(a, b, selectMask);
        AllocatableValue btmp = emitVectorBlend(b, a, selectMask);

        // vmaxps/vmaxpd/vminps/vminpd result, a', b'
        LIRKind resultKind = LIRKind.combine(atmp, btmp);
        AllocatableValue result = emitBinary(resultKind, minmaxop.getAVXOp(kind, EVEX), atmp, btmp);
        // move NaN elements in a to result
        // maskNaN = vcmpunordps/vcmpunordpd a', a'
        AllocatableValue maskNaN = emitVectorFloatCompare(atmp, atmp, VexFloatCompareOp.Predicate.UNORD_Q);
        assert ((AMD64Kind) maskNaN.getPlatformKind()).isMask() : maskNaN;
        // vblendmps/vblendmpd result' {maskNaN}, result, atmp
        // Note: Consider using movups/movupd result {maskNaN}, atmp on AVX-512 instead.
        result = emitVectorBlend(result, atmp, maskNaN);
        return result;
    }

    @Override
    public Variable emitFloatingPointConditionalMove(BiFunction<Value, Value, Variable> scalarSelectOperation, Value trueValue, Value falseValue) {
        AMD64Kind resultKind = (AMD64Kind) trueValue.getPlatformKind();
        assert resultKind == AMD64Kind.SINGLE || resultKind == AMD64Kind.DOUBLE : "expected scalar float, not " + resultKind;
        boolean singleSize = (resultKind == AMD64Kind.SINGLE);

        // Emit an integer conditional move to get 0 or 1 into an integer register...
        LIRKind longKind = getLIRGen().getValueKind(singleSize ? JavaKind.Int : JavaKind.Long);
        Value zero = getLIRGen().emitConstant(longKind, singleSize ? JavaConstant.INT_0 : JavaConstant.LONG_0);
        Value one = getLIRGen().emitConstant(longKind, singleSize ? JavaConstant.INT_1 : JavaConstant.LONG_1);

        Variable zeroOrOne = LIRValueUtil.asVariable(scalarSelectOperation.apply(one, zero));

        Value mask = emitMoveIntegerToOpMask(LIRKind.value(AMD64Kind.MASK8), zeroOrOne);
        return emitVectorBlend(falseValue, trueValue, mask);
    }

    @Override
    protected AllocatableValue emitVectorFloatCompare(AllocatableValue a, AllocatableValue b, VexFloatCompareOp.Predicate predicate) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        AVXSize size = AVXKind.getRegisterSize(kind);
        VexFloatCompareOp vcmpp;
        AMD64Kind maskKind;
        switch (kind.getScalar()) {
            case SINGLE -> {
                vcmpp = kind.getVectorLength() > 1 ? EVCMPPS : EVCMPSS;
                maskKind = (size == AVXSize.ZMM ? AMD64Kind.MASK16 : AMD64Kind.MASK8);
            }
            case DOUBLE -> {
                vcmpp = kind.getVectorLength() > 1 ? EVCMPPD : EVCMPSD;
                maskKind = AMD64Kind.MASK8;
            }
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
        }
        AllocatableValue result = getLIRGen().newVariable(LIRKind.value(maskKind));
        getLIRGen().append(new AMD64VectorFloatCompareOp(vcmpp, size, result, a, b, predicate));
        return result;
    }

    @Override
    protected Value emitIntegerMinMax(Value a, Value b, AMD64MathMinMaxFloatOp minmaxop, NumUtil.Signedness signedness) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        GraalError.guarantee(kind.getVectorLength() > 1, "scalar integer min/max not supported");
        GraalError.guarantee(kind.getScalar().isInteger(), "expected integer vector for integer min/max");
        LIRKind resultKind = LIRKind.combine(a, b);
        boolean min = (minmaxop == AMD64MathMinMaxFloatOp.Min);
        boolean signed = (signedness == NumUtil.Signedness.SIGNED);
        return switch (kind.getScalar()) {
            case BYTE -> emitVectorBinary(resultKind, min ? (signed ? EVPMINSB : EVPMINUB) : (signed ? EVPMAXSB : EVPMAXUB), a, b);
            case WORD -> emitVectorBinary(resultKind, min ? (signed ? EVPMINSW : EVPMINUW) : (signed ? EVPMAXSW : EVPMAXUW), a, b);
            case DWORD -> emitVectorBinary(resultKind, min ? (signed ? EVPMINSD : EVPMINUD) : (signed ? EVPMAXSD : EVPMAXUD), a, b);
            case QWORD -> emitVectorBinary(resultKind, min ? (signed ? EVPMINSQ : EVPMINUQ) : (signed ? EVPMAXSQ : EVPMAXUQ), a, b);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Value emitMoveOpMaskToInteger(LIRKind resultKind, Value mask, int maskLen) {
        Variable result = getLIRGen().newVariable(resultKind);
        AMD64Kind maskKind = MASK_REGISTER_KINDS[CodeUtil.log2(resultKind.getPlatformKind().getSizeInBytes())];
        getLIRGen().append(new AMD64Move.MoveToRegOp(maskKind, result, asAllocatable(mask)));

        if (maskLen < Byte.SIZE) {
            Variable fix = getLIRGen().newVariable(resultKind);
            getLIRGen().append(new AMD64Binary.ConstOp(AMD64Assembler.AMD64BinaryArithmetic.AND, AMD64BaseAssembler.OperandSize.DWORD, fix, result, (int) CodeUtil.mask(maskLen)));
            result = fix;
        }
        return result;
    }

    @Override
    public Value emitMoveIntegerToOpMask(LIRKind resultKind, Value mask) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AMD64Move.MoveToRegOp((AMD64Kind) result.getPlatformKind(), result, asAllocatable(mask)));
        return result;
    }

    @Override
    public Variable emitVectorOpMaskTestMove(Value left, boolean negateLeft, Value right, Value trueValue, Value falseValue) {
        AMD64Kind resultKind = (AMD64Kind) trueValue.getPlatformKind();
        if (resultKind == AMD64Kind.SINGLE || resultKind == AMD64Kind.DOUBLE) {
            return emitFloatingPointConditionalMove(
                            (scalarTrue, scalarFalse) -> getLIRGen().emitOpMaskTestMove(left, negateLeft, right, scalarTrue, scalarFalse),
                            trueValue, falseValue);
        }

        if (trueValue.getPlatformKind().getVectorLength() == 1) {
            return null;
        }

        Value mask = left;
        if (negateLeft) {
            mask = emitNot(mask);
        }
        if (mask.equals(right)) {
            mask = emitAnd(mask, right);
        }
        return emitVectorBlend(falseValue, trueValue, mask);
    }

    @Override
    public Variable emitVectorOpMaskOrTestMove(Value left, Value right, boolean allZeros, Value trueValue, Value falseValue) {
        AMD64Kind resultKind = (AMD64Kind) trueValue.getPlatformKind();
        if (resultKind == AMD64Kind.SINGLE || resultKind == AMD64Kind.DOUBLE) {
            return emitFloatingPointConditionalMove(
                            (scalarTrue, scalarFalse) -> getLIRGen().emitOpMaskOrTestMove(left, right, allZeros, scalarTrue, scalarFalse),
                            trueValue, falseValue);
        }

        if (trueValue.getPlatformKind().getVectorLength() == 1) {
            return null;
        }

        Value mask = emitOr(left, right);
        Variable result;
        if (allZeros) {
            result = emitVectorBlend(falseValue, trueValue, mask);
        } else {
            result = emitVectorBlend(trueValue, falseValue, mask);
        }
        return result;
    }

    @Override
    public Value emitConstShuffleBytes(LIRKind resultKind, Value vector, byte[] selector) {
        if (selector.length <= AVXSize.XMM.getBytes()) {
            // for an XMM sized shuffle we can simply use VPSHUFB
            Variable result = getLIRGen().newVariable(resultKind);
            byte[] instSelector = selector;
            if (instSelector.length < 16) {
                instSelector = Arrays.copyOf(selector, 16);
                Arrays.fill(instSelector, selector.length, 16, (byte) -1);
            }
            AllocatableValue allocatedVector = vector.getPlatformKind().getSizeInBytes() <= AVXSize.XMM.getBytes()
                            ? asAllocatable(vector)
                            : new CastValue(vector.getValueKind().changeType(AMD64Kind.V128_BYTE), getLIRGen().asAllocatable(vector));
            getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(asAllocatable(result), allocatedVector, EVEX, instSelector));
            return result;
        }

        if (supports(AMD64.CPUFeature.AVX512_VBMI)) {
            // if AVX512 VBMI is supported, we can use VPERMT2B for shuffles lager than 128 bit
            Variable result = getLIRGen().newVariable(resultKind);
            byte tableMask = (byte) Integer.highestOneBit(vector.getPlatformKind().getSizeInBytes());
            long maskValue = 0;
            for (int i = selector.length - 1; i > -1; --i) {
                if (selector[i] == -1) {
                    maskValue = (maskValue << 1) | 1;
                    selector[i] = 0;
                } else {
                    maskValue <<= 1;
                    selector[i] |= tableMask;
                }
            }
            JavaConstant constMask;
            AMD64Kind maskKind;
            switch (vector.getPlatformKind().getSizeInBytes()) {
                case 1, 2, 4, 8 -> {
                    constMask = JavaConstant.forByte((byte) (~maskValue & 0xff));
                    maskKind = AMD64Kind.MASK8;
                }
                case 16 -> {
                    constMask = JavaConstant.forShort((short) (~maskValue & 0xffff));
                    maskKind = AMD64Kind.MASK16;
                }
                case 32 -> {
                    constMask = JavaConstant.forInt((int) ~maskValue);
                    maskKind = AMD64Kind.MASK32;
                }
                case 64 -> {
                    constMask = JavaConstant.forLong(~maskValue);
                    maskKind = AMD64Kind.MASK64;
                }
                default -> throw GraalError.shouldNotReachHere("Unsupported platform kind for permute - size " + vector.getPlatformKind().getSizeInBytes()); // ExcludeFromJacocoGeneratedReport
            }

            Value mask = emitConstOpmask(maskKind, constMask);
            getLIRGen().append(new AMD64VectorShuffle.ConstPermuteBytesUsingTableOp(getLIRGen(), asAllocatable(result), asAllocatable(vector), selector, asAllocatable(mask)));
            return result;
        }

        if (selector.length == AVXSize.YMM.getBytes()) {
            /*
             * The general idea is to shuffle [L0,L1] and [L1,L0] individually using the same
             * selector. The results of the byte shuffles are masked to only contain the values that
             * are actually shuffled to the right place with the given shuffle and zeros everywhere
             * else. Finally, the two shuffle results are blended together to realize cross-lane
             * shuffles.
             */

            // widen source vector to YMM
            AllocatableValue v = asAllocatable(vector);
            if (v.getPlatformKind().getSizeInBytes() <= 16) {
                AllocatableValue widenedV = getLIRGen().newVariable(v.getValueKind().changeType(AMD64Kind.V256_BYTE));
                getLIRGen().append(new AMD64VectorShuffle.Insert128Op(widenedV, v, v, 0, EVEX));
                v = widenedV;
            }

            // Create shuffle constants
            // Masks all values obtained via an in-lane shuffle
            int maskValuesFromL0L1 = 0;
            // Masks all values obtained via a cross-lane shuffle
            int maskValuesFromL1L0 = 0;
            boolean crossLaneShuffle = false;
            boolean inLaneShuffle = false;
            for (int i = 0; i < selector.length; i++) {
                if (selector[i] == -1) {
                    continue;
                }
                int fromLane = selector[i] / 16;
                int toLane = i / 16;
                if (fromLane == toLane) {
                    inLaneShuffle |= true;
                    maskValuesFromL0L1 |= 1 << i;
                } else {
                    maskValuesFromL1L0 |= 1 << i;
                    crossLaneShuffle |= true;
                }
            }

            // If no shuffle crosses a lane, we can do a quick and simple shuffle.
            if (!crossLaneShuffle) {
                Variable result = getLIRGen().newVariable(resultKind);
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(asAllocatable(result), asAllocatable(v), EVEX, selector));
                return result;
            }

            /*
             * This variable holds the result. If a shuffle is generated for the original data and
             * the lane swapped data, those two results are blended together.
             */
            Variable result = null;

            /*
             * Shuffle the original vector if there are actually values that are shuffled within
             * their respective lanes using the L0L1 mask obtained earlier.
             */
            if (inLaneShuffle) {
                Value maskL0L1Shuffle = emitConstOpmask(AMD64Kind.MASK32, JavaConstant.forInt(maskValuesFromL0L1));
                AllocatableValue vL0L1 = v;
                Variable vL0L1shuffled = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE));
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOpWithMask(vL0L1shuffled, vL0L1, asAllocatable(maskL0L1Shuffle), selector));
                result = vL0L1shuffled;
            }

            /*
             * Swap the two 128 bit lanes of the original vector and shuffle the resulting L1L0
             * vector using the L1L0 mask obtained earlier.
             */
            Value maskL1L0Shuffle = emitConstOpmask(AMD64Kind.MASK32, JavaConstant.forInt(maskValuesFromL1L0));
            Variable vL1L0 = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE));
            getLIRGen().append(new AMD64VectorShuffle.ShuffleIntegerLanesOp(asAllocatable(vL1L0), asAllocatable(v), 0b0_1));
            Variable vL1L0shuffled = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE));
            getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOpWithMask(vL1L0shuffled, vL1L0, asAllocatable(maskL1L0Shuffle), selector));

            /*
             * If a shuffled L0L1 has been generated, we blend the shuffled L0L1 with the shuffled
             * L1L0 reusing the L1L0 mask where for every element originating from shuffling vL1L0
             * the according bit is set.
             */
            if (result == null) {
                result = vL1L0shuffled;
            } else {
                result = emitVectorBlend(result, vL1L0shuffled, maskL1L0Shuffle);
            }

            // If the resultKind is not V256_BYTE we need to cast the result to that kind.
            return resultKind.getPlatformKind() == AMD64Kind.V256_BYTE ? result : new CastValue(resultKind, result);
        }

        /*
         * This is a ZMM byte shuffle. The general idea is similar to the YMM case but to
         * potentially reduce the numbers of byte and lane shuffles, a table ('exportsTo') is
         * constructed to analyze which lanes export elements to other lanes.
         */
        GraalError.guarantee(selector.length == AVXSize.ZMM.getBytes(), "unexpected selector size %s", selector.length);

        // widen source vector to ZMM
        AllocatableValue originalDataVector = asAllocatable(vector);
        if (originalDataVector.getPlatformKind().getSizeInBytes() <= 16) {
            AllocatableValue widenedV = getLIRGen().newVariable(originalDataVector.getValueKind().changeType(AMD64Kind.V256_BYTE));
            getLIRGen().append(new AMD64VectorShuffle.Insert128Op(widenedV, originalDataVector, originalDataVector, 0, EVEX));
            originalDataVector = widenedV;
        }
        if (originalDataVector.getPlatformKind().getSizeInBytes() <= 32) {
            AllocatableValue widenedV = getLIRGen().newVariable(originalDataVector.getValueKind().changeType(AMD64Kind.V512_BYTE));
            getLIRGen().append(new AMD64VectorShuffle.Insert256Op(widenedV, originalDataVector, originalDataVector, 0));
            originalDataVector = widenedV;
        }

        /*
         * 'exportsTo' is a table that describes, which lanes each lane of the original data export
         * its values to. The first index denotes the lane in the source data, the second index
         * denotes the lane, data from the source lane is exported to.
         */
        boolean[][] exportsTo = new boolean[4][4];
        /*
         * 'shuffleMaskParts' is a table of the same shape as 'exportsTo' and has all bits set where
         * a final value will end up after shuffling the data. Everywhere shuffleMaskParts != 0,
         * 'exportsTo' is true. 'exportsTo' is used to maintain clarity later in the code.
         */
        short[][] shuffleMaskParts = new short[4][4];
        // The check if exportsTo is a diagonal matrix is done for comfort of later use
        boolean isDiagonalMatrix = true;

        for (int i = 0; i < selector.length; i++) {
            if (selector[i] == -1) {
                continue;
            }
            int fromLane = selector[i] / 16;
            int toLane = i / 16;
            exportsTo[fromLane][toLane] = true;
            shuffleMaskParts[fromLane][toLane] |= (short) (1 << (i % 16));
            isDiagonalMatrix &= fromLane == toLane;
        }

        if (isDiagonalMatrix) {
            // No shuffle traverses a lane boundary, therefore we can do a quick and simple shuffle
            Variable result = getLIRGen().newVariable(resultKind);
            getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(asAllocatable(result), asAllocatable(originalDataVector), EVEX, selector));
            return result;
        }

        /*
         * This array represents a list of up to 3 arrays with length 4 of nullable integers.
         *
         * Each element of the outer array represents a vector with permutated lanes. The index of
         * the inner array represents the lanes of the given vector while the value represents which
         * lane of the original vector this lane should get its data from. If a value is 'null', it
         * means that this lane does not get any data from the original vector and will be masked
         * out.
         */
        Integer[][] laneSelectors = new Integer[3][];
        /*
         * Mask parts laid out according to the shape of laneSelectors. The values of an inner
         * arrays will later be combined into a single long value to use as the blend mask.
         */
        short[][] rearrangedMaskParts = new short[3][];
        // Counts how many vectors with shuffled lanes are needed
        int extraShuffleCount = 0;

        // Now we iterate over the entire exportsTo table
        for (int toLane = 0; toLane < 4; toLane++) {
            for (int fromLane = 0; fromLane < 4; fromLane++) {
                /*
                 * All shuffles where fromLane == toLane are done by shuffling the original data. We
                 * do not need to take care of them here.
                 */
                if (exportsTo[fromLane][toLane] && fromLane != toLane) {
                    /*
                     * This is a cross-lane shuffle. Therefore, we need to find a free spot in a
                     * vector with permutated lanes to put the source lane (fromLane) into.
                     */
                    for (int i = 0; i < laneSelectors.length; i++) {
                        if (laneSelectors[i] == null) {
                            /*
                             * No free spot was found in the existing permutated lane vectors,
                             * therefore a new permutated lane vector is created.
                             */
                            laneSelectors[i] = new Integer[4];
                            rearrangedMaskParts[i] = new short[4];
                            extraShuffleCount++;

                            laneSelectors[i][toLane] = fromLane;
                            rearrangedMaskParts[i][toLane] = shuffleMaskParts[fromLane][toLane];
                            break;
                        } else {
                            // Check if the permutated lane vector has a free spot at toLane
                            if (laneSelectors[i][toLane] == null) {
                                laneSelectors[i][toLane] = fromLane;
                                rearrangedMaskParts[i][toLane] = shuffleMaskParts[fromLane][toLane];
                                break;
                            }
                        }
                    }
                }
            }
        }

        /*
         * This variable holds the result that is later returned. This variable is used to blend all
         * sub results together. If this variable is null at the end, this means that no element was
         * shuffled, therefore the entire selector has to be -1. This case should be filtered out
         * earlier because if there is no shuffle at all, there also is no cross lane shuffle, so
         * the 'isDiagonalMatrix' condition should trigger. This variable is only ever supposed to
         * hold values of type AMD64Kind.V512_BYTE.
         */
        Variable resultInBytes = null;

        /*
         * Shuffle the original data if there are any in-lane shuffles. The mask is used to zero out
         * all final elements that are obtained via cross-lane shuffles.
         */
        if (exportsTo[0][0] || exportsTo[1][1] || exportsTo[2][2] || exportsTo[3][3]) {
            long inLaneShuffleMaskConstant = 0;
            for (int i = shuffleMaskParts.length - 1; i >= 0; i--) {
                inLaneShuffleMaskConstant <<= 16;
                inLaneShuffleMaskConstant |= (long) shuffleMaskParts[i][i] & 0xFF_FF;
            }
            Value originalShuffleMask = emitConstOpmask(AMD64Kind.MASK64, JavaConstant.forLong(inLaneShuffleMaskConstant));
            Variable shuffledOriginalData = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V512_BYTE));
            getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOpWithMask(shuffledOriginalData, originalDataVector, asAllocatable(originalShuffleMask),
                            selector));
            resultInBytes = shuffledOriginalData;
        }

        /*
         * Now for each vector of permutated lanes in laneSelectors we first permute the lanes of
         * the original data according to the given lane selector.
         *
         * Then we shuffle the lane permutated data using the selector and a mask, selecting only
         * the final values obtained by this shuffle and zeroing the rest.
         *
         * Finally, the newly shuffled data is blended to the previous result reusing the shuffle
         * mask.
         *
         * The final result is the result of the shuffled original data blended with all generated
         * lane permutated shuffles.
         */
        for (int i = 0; i < extraShuffleCount; i++) {
            /*
             * Obtain the according lane selector and the corresponding mask where every bit for an
             * element originating from the shuffle of the lane permutated data is set.
             */
            int laneSelector = 0;
            long maskConstant = 0;
            for (int j = laneSelectors[i].length - 1; j >= 0; j--) {
                maskConstant <<= 16;  // the mask is build up from 16 bit chunks
                laneSelector <<= 2;  // the lane selector is build up from 2 bit chunks
                if (laneSelectors[i][j] != null) {
                    laneSelector |= laneSelectors[i][j].intValue();
                    maskConstant = maskConstant | ((long) rearrangedMaskParts[i][j] & 0xFF_FF);
                }
            }

            // Generate the lane permutations from the original data
            Variable laneShuffledData = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V512_BYTE));
            getLIRGen().append(new AMD64VectorShuffle.ShuffleIntegerLanesOp(laneShuffledData, asAllocatable(originalDataVector), laneSelector));

            if (resultInBytes == null && extraShuffleCount == 1) {
                /*
                 * If there is no shuffle within the original lanes (resultInBytes == null) and only
                 * one shuffle with permutated lanes, we do not need a mask because we do not need
                 * to blend the result and the selector is -1 everywhere where the mask would be -1.
                 * Using this, we can simply emit a ConstShuffleBytes without a mask and later
                 * blending.
                 *
                 * A common use case for this condition is reversing the elements of a vector.
                 */
                assert checkSelectorAgainstMask(selector, maskConstant) : "selector has to be -1 where maskConstant is 0";
                Variable shuffledData = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V512_BYTE));
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(shuffledData, laneShuffledData, EVEX, selector));
                resultInBytes = shuffledData;
                break;
            }

            // Generate byte shuffle using a lane permutated selector
            Value finalShuffleMask = emitConstOpmask(AMD64Kind.MASK64, JavaConstant.forLong(maskConstant));
            Variable shuffledData = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V512_BYTE));
            getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOpWithMask(shuffledData, laneShuffledData, asAllocatable(finalShuffleMask), selector));

            // Blend the lane and byte shuffled data to the original data
            if (resultInBytes == null) {
                /*
                 * Shuffling of the original data was skipped because no elements were shuffled
                 * within their respective lanes. Therefore, the first lane permutated shuffle
                 * result is set as the result.
                 */
                resultInBytes = shuffledData;
            } else {
                resultInBytes = emitVectorBlend(resultInBytes, shuffledData, finalShuffleMask);
            }
        }

        GraalError.guarantee(resultInBytes != null, "If no element in the result is ever set via a byte shuffle, execution should not reach here!");

        // If the result type is not a ZMM sized byte vector, we cast the blended result to the
        // correct type.
        return resultKind.getPlatformKind() == AMD64Kind.V512_BYTE ? resultInBytes : new CastValue(resultKind, resultInBytes);
    }

    /**
     * Quite expensive check if the mask is 0 if and only if the selector is -1.
     */
    private static boolean checkSelectorAgainstMask(byte[] selector, long mask) {
        long shiftedMask = mask;
        for (int i = 0; i < selector.length; i++) {
            if ((shiftedMask & 1) == 1) {
                if (selector[i] == -1) {
                    return false;
                }
            } else {
                if (selector[i] != -1) {
                    return false;
                }
            }
            shiftedMask >>= 1;
        }
        return true;
    }

    record NormalizedMaskedOp(AMD64Assembler.VexOp opcode, AVXSize avxSize, Value src1, Value src2) {
        static NormalizedMaskedOp make(AMD64Assembler.VexOp opcode, AVXSize avxSize, Value src1, Value src2) {
            AMD64Assembler.VexOp op = Objects.requireNonNull(opcode);
            Value s1 = src1;
            Value s2 = src2;
            if (op == EVPERMB && avxSize == AVXSize.XMM) {
                op = EVPSHUFB;
            } else if ((op == EVPERMD || op == EVPERMPS) && avxSize == AVXSize.XMM) {
                op = EVPERMILPS;
            } else if ((op == EVPERMQ || op == EVPERMPD) && avxSize == AVXSize.XMM) {
                op = EVPERMILPD;
            }
            if (op == EVPERMB || op == EVPERMW || op == EVPERMD || op == EVPERMQ || op == EVPERMPS || op == EVPERMPD) {
                s1 = src2;
                s2 = src1;
            }
            return new NormalizedMaskedOp(op, avxSize, s1, s2);
        }
    }

    public Value emitMaskedMergeOp(LIRKind resultKind, MaskedOpMetaData meta, Value background, Value mask, Value src1, Value src2) {
        AMD64Kind kind = (AMD64Kind) resultKind.getPlatformKind();
        AMD64Kind eKind = kind.getScalar();
        AMD64Kind srcKind = (AMD64Kind) src1.getPlatformKind();
        AMD64Kind srcEKind = srcKind.getScalar();
        AVXSize avxSize;
        if (kind.isXMM() && kind.getSizeInBytes() >= srcKind.getSizeInBytes()) {
            avxSize = AVXKind.getRegisterSize(kind);
        } else {
            avxSize = AVXKind.getRegisterSize(srcKind);
        }
        Variable result = getLIRGen().newVariable(resultKind);

        NormalizedMaskedOp normalizedOp = NormalizedMaskedOp.make(getMaskedOpcode(getArchitecture(), meta, eKind, srcEKind), avxSize, src1, src2);
        Value normalizedSrc2 = normalizedOp.src2;
        if (normalizedOp.opcode == EVPERMILPD) {
            /*
             * EVPERMILPD uses the SECOND bit in each element as the index. See also
             * AMD64VectorShuffle.
             */
            Variable tmp = getLIRGen().newVariable(normalizedOp.src2.getValueKind());
            getLIRGen().append(new AMD64VectorBinary.AVXBinaryConstOp(EVPSLLQ, getRegisterSize(tmp), tmp, asAllocatable(src2), 1));
            normalizedSrc2 = tmp;
        }
        AVX512MaskedOp.AVX512MaskedMergeOp lirOp;
        if (normalizedSrc2 == null) {
            lirOp = new AVX512MaskedOp.AVX512MaskedMergeOp(normalizedOp.opcode(), avxSize, result, asAllocatable(background), asAllocatable(mask), Value.ILLEGAL, asAllocatable(normalizedOp.src1()));
        } else {
            lirOp = new AVX512MaskedOp.AVX512MaskedMergeOp(normalizedOp.opcode(), avxSize, result, asAllocatable(background), asAllocatable(mask), asAllocatable(normalizedOp.src1()),
                            asAllocatable(normalizedSrc2));
        }
        getLIRGen().append(lirOp);
        return result;
    }

    public Value emitMaskedZeroOp(LIRKind resultKind, MaskedOpMetaData meta, Value mask, Value src1, Value src2) {
        AMD64Kind kind = (AMD64Kind) resultKind.getPlatformKind();
        AMD64Kind eKind = kind.getScalar();
        AMD64Kind srcKind = (AMD64Kind) src1.getPlatformKind();
        AMD64Kind srcEKind = srcKind.getScalar();
        AVXSize avxSize;
        if (kind.isXMM() && kind.getSizeInBytes() >= srcKind.getSizeInBytes()) {
            avxSize = AVXKind.getRegisterSize(kind);
        } else {
            avxSize = AVXKind.getRegisterSize(srcKind);
        }
        Variable result = getLIRGen().newVariable(resultKind);

        NormalizedMaskedOp normalizedOp = NormalizedMaskedOp.make(getMaskedOpcode(getArchitecture(), meta, eKind, srcEKind), avxSize, src1, src2);
        Object predicate = null;
        if (meta.op() == SimdPrimitiveCompareNode.class) {
            if (srcEKind.isInteger()) {
                predicate = AMD64Assembler.VexIntegerCompareOp.Predicate.getPredicate(meta.comparisonCondition().asCondition());
            } else {
                predicate = AMD64Assembler.VexFloatCompareOp.Predicate.getPredicate(meta.comparisonCondition().asCondition(), meta.comparisonUnordered());
            }
            GraalError.guarantee(predicate != null, "%s", meta);
        }

        Value normalizedSrc2 = normalizedOp.src2;
        if (normalizedOp.opcode == EVPERMILPD) {
            /*
             * EVPERMILPD uses the SECOND bit in each element as the index. See also
             * AMD64VectorShuffle.
             */
            Variable tmp = getLIRGen().newVariable(normalizedOp.src2.getValueKind());
            getLIRGen().append(new AMD64VectorBinary.AVXBinaryConstOp(EVPSLLQ, getRegisterSize(tmp), tmp, asAllocatable(src2), 1));
            normalizedSrc2 = tmp;
        }
        AVX512MaskedOp.AVX512MaskedZeroOp lirOp;
        if (normalizedSrc2 == null) {
            lirOp = new AVX512MaskedOp.AVX512MaskedZeroOp(normalizedOp.opcode(), predicate, avxSize, result, asAllocatable(mask), Value.ILLEGAL, asAllocatable(normalizedOp.src1()));
        } else {
            lirOp = new AVX512MaskedOp.AVX512MaskedZeroOp(normalizedOp.opcode(), predicate, avxSize, result, asAllocatable(mask), asAllocatable(normalizedOp.src1()),
                            asAllocatable(normalizedSrc2));
        }
        getLIRGen().append(lirOp);
        return result;
    }

    public static AMD64Assembler.VexOp getMaskedOpcode(AMD64 arch, MaskedOpMetaData meta, AMD64Kind dstEKind, AMD64Kind srcEKind) {
        EnumSet<AMD64.CPUFeature> features = arch.getFeatures();
        if (!AMD64BaseAssembler.supportsFullAVX512(features)) {
            return null;
        }

        Class<? extends ValueNode> op = meta.op();
        if (op == AbsNode.class) {
            return switch (dstEKind) {
                case BYTE -> EVPABSB;
                case WORD -> EVPABSW;
                case DWORD -> EVPABSD;
                case QWORD -> EVPABSQ;
                default -> null;
            };
        } else if (op == SqrtNode.class) {
            return switch (dstEKind) {
                case SINGLE -> EVSQRTPS;
                case DOUBLE -> EVSQRTPD;
                default -> null;
            };
        } else if (op == AddNode.class) {
            return switch (dstEKind) {
                case BYTE -> EVPADDB;
                case WORD -> EVPADDW;
                case DWORD -> EVPADDD;
                case QWORD -> EVPADDQ;
                case SINGLE -> EVADDPS;
                case DOUBLE -> EVADDPD;
                default -> null;
            };
        } else if (op == SubNode.class) {
            return switch (dstEKind) {
                case BYTE -> EVPSUBB;
                case WORD -> EVPSUBW;
                case DWORD -> EVPSUBD;
                case QWORD -> EVPSUBQ;
                case SINGLE -> EVSUBPS;
                case DOUBLE -> EVSUBPD;
                default -> null;
            };
        } else if (op == MulNode.class) {
            return switch (dstEKind) {
                case WORD -> EVPMULLW;
                case DWORD -> EVPMULLD;
                case QWORD -> EVPMULLQ;
                case SINGLE -> EVMULPS;
                case DOUBLE -> EVMULPD;
                default -> null;
            };
        } else if (op == FloatDivNode.class) {
            return switch (dstEKind) {
                case SINGLE -> EVDIVPS;
                case DOUBLE -> EVDIVPD;
                default -> null;
            };
        } else if (op == AndNode.class) {
            return switch (dstEKind) {
                case DWORD -> EVPANDD;
                case QWORD -> EVPANDQ;
                case SINGLE -> EVANDPS;
                case DOUBLE -> EVANDPD;
                default -> null;
            };
        } else if (op == OrNode.class) {
            return switch (dstEKind) {
                case DWORD -> EVPORD;
                case QWORD -> EVPORQ;
                case SINGLE -> EVORPS;
                case DOUBLE -> EVORPD;
                default -> null;
            };
        } else if (op == XorNode.class) {
            return switch (dstEKind) {
                case DWORD -> EVPXORD;
                case QWORD -> EVPXORQ;
                case SINGLE -> EVXORPS;
                case DOUBLE -> EVXORPD;
                default -> null;
            };
        } else if (op == SimdPermuteWithVectorIndicesNode.class) {
            return switch (dstEKind) {
                case BYTE -> features.contains(AMD64.CPUFeature.AVX512_VBMI) ? EVPERMB : null;
                case WORD -> EVPERMW;
                case DWORD -> EVPERMD;
                case QWORD -> EVPERMQ;
                case SINGLE -> EVPERMPS;
                case DOUBLE -> EVPERMPD;
                default -> null;
            };
        } else if (op == SimdPrimitiveCompareNode.class) {
            boolean isUnsigned = meta.comparisonCondition().isUnsigned();
            return switch (srcEKind) {
                case BYTE -> isUnsigned ? AMD64Assembler.VexIntegerCompareOp.EVPCMPUB : EVPCMPB;
                case WORD -> isUnsigned ? AMD64Assembler.VexIntegerCompareOp.EVPCMPUW : EVPCMPW;
                case DWORD -> isUnsigned ? AMD64Assembler.VexIntegerCompareOp.EVPCMPUD : EVPCMPD;
                case QWORD -> isUnsigned ? AMD64Assembler.VexIntegerCompareOp.EVPCMPUQ : EVPCMPQ;
                case SINGLE -> EVCMPPS;
                case DOUBLE -> EVCMPPD;
                default -> null;
            };
        } else {
            return null;
        }
    }

    @Override
    public Variable emitVectorCompress(LIRKind resultKind, Value source, Value mask) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AVX512CompressExpand.CompressOp(result, asAllocatable(source), asAllocatable(mask)));
        return result;
    }

    @Override
    public Variable emitVectorExpand(LIRKind resultKind, Value source, Value mask) {
        Variable result = getLIRGen().newVariable(resultKind);
        getLIRGen().append(new AVX512CompressExpand.ExpandOp(result, asAllocatable(source), asAllocatable(mask)));
        return result;
    }
}
