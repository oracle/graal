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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding.VEX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp.VCMPPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVUPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVUPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPERMQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFHW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMIOp.VPSHUFLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VBROADCASTSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VBROADCASTSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTDQ2PD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTDQ2PS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTPD2PS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTPS2PD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTPD2DQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTPS2DQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSD2SI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSD2SQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSS2SI;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VCVTTSS2SQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VMOVDDUP;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VMOVMSKPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VMOVMSKPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPABSB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPABSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPABSW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVMSKB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVSXBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVSXBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVSXBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVSXDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVSXWD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVSXWQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVZXBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVZXBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVZXBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVZXDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVZXWD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPMOVZXWQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VSQRTPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VSQRTPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VUCOMISD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VUCOMISS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSD2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSI2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSI2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSQ2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSQ2SS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMConvertOp.VCVTSS2SD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VANDPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VANDPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VDIVPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VDIVPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VORPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VORPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPAND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPEQB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPEQD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPEQQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPEQW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPGTB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPGTD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPGTQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPCMPGTW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMAXSB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMAXSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMAXSW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMAXUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMAXUD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMAXUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMINSB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMINSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMINSW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMINUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMINUD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMINUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULHUW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULHW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPSUBW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPXOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSQRTSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSQRTSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VXORPS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSLLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSLLQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSLLW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSRAD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSRAW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSRLD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSRLQ;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexShiftOp.VPSRLW;
import static jdk.graal.compiler.lir.LIRValueUtil.asJavaConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.graal.compiler.vector.lir.amd64.AMD64VectorNodeMatchRules.getRegisterSize;
import static jdk.vm.ci.amd64.AMD64.xmm0;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.BiFunction;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexFloatCompareOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexGatherOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMaskedMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.calc.Condition.CanonicalizedCondition;
import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.DataPointerConstant;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Binary;
import jdk.graal.compiler.lir.amd64.AMD64Unary;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorBinary;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorBlend;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorClearOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorCompareOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorFloatCompareOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorGather;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorShuffle;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorUnary;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This class handles specific cases for SSE/AVX/AVX2 that differ from AVX512. If full AVX512 is
 * supported, {@link AMD64AVX512ArithmeticLIRGenerator} is used instead of this class.
 */
public class AMD64SSEAVXArithmeticLIRGenerator extends AMD64VectorArithmeticLIRGenerator {

    public AMD64SSEAVXArithmeticLIRGenerator(AllocatableValue nullRegisterValue) {
        super(nullRegisterValue);
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> emitVectorBinary(resultKind, VPADDB, a, b);
                case WORD -> emitVectorBinary(resultKind, VPADDW, a, b);
                case DWORD -> emitVectorBinary(resultKind, VPADDD, a, b);
                case QWORD -> emitVectorBinary(resultKind, VPADDQ, a, b);
                case SINGLE -> emitVectorBinary(resultKind, VADDPS, a, b);
                case DOUBLE -> emitVectorBinary(resultKind, VADDPD, a, b);
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
                case BYTE -> emitVectorBinary(resultKind, VPSUBB, a, b);
                case WORD -> emitVectorBinary(resultKind, VPSUBW, a, b);
                case DWORD -> emitVectorBinary(resultKind, VPSUBD, a, b);
                case QWORD -> emitVectorBinary(resultKind, VPSUBQ, a, b);
                case SINGLE -> emitVectorBinary(resultKind, VSUBPS, a, b);
                case DOUBLE -> emitVectorBinary(resultKind, VSUBPD, a, b);
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
                case BYTE -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULLB"); // ExcludeFromJacocoGeneratedReport
                case WORD -> emitVectorBinary(VPMULLW, a, b);
                case DWORD -> emitVectorBinary(VPMULLD, a, b);
                case QWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULLQ"); // ExcludeFromJacocoGeneratedReport
                case SINGLE -> emitVectorBinary(VMULPS, a, b);
                case DOUBLE -> emitVectorBinary(VMULPD, a, b);
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
                case BYTE -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULHB"); // ExcludeFromJacocoGeneratedReport
                case WORD -> emitVectorBinary(VPMULHW, a, b);
                case DWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULHD"); // ExcludeFromJacocoGeneratedReport
                case QWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULHQ"); // ExcludeFromJacocoGeneratedReport
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
                case BYTE -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULHUB"); // ExcludeFromJacocoGeneratedReport
                case WORD -> emitVectorBinary(VPMULHUW, a, b);
                case DWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULHUD"); // ExcludeFromJacocoGeneratedReport
                case QWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPMULHUQ"); // ExcludeFromJacocoGeneratedReport
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
                case SINGLE -> emitVectorBinary(VDIVPS, a, b);
                case DOUBLE -> emitVectorBinary(VDIVPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitDiv(a, b, state);
        }
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE, WORD, DWORD, QWORD -> emitVectorBinary(VPAND, a, b);
                case SINGLE -> emitVectorBinary(VANDPS, a, b);
                case DOUBLE -> emitVectorBinary(VANDPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitAnd(a, b);
        }
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE, WORD, DWORD, QWORD -> emitVectorBinary(VPOR, a, b);
                case SINGLE -> emitVectorBinary(VORPS, a, b);
                case DOUBLE -> emitVectorBinary(VORPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitOr(a, b);
        }
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE, WORD, DWORD, QWORD -> emitVectorBinary(VPXOR, a, b);
                case SINGLE -> emitVectorBinary(VXORPS, a, b);
                case DOUBLE -> emitVectorBinary(VXORPD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitXor(a, b);
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
                case DWORD -> emitVectorBinary(VexRVMOp.VPSLLVD, a, b);
                case QWORD -> emitVectorBinary(VexRVMOp.VPSLLVQ, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }

        return switch (aKind.getScalar()) {
            case WORD -> emitShift(VPSLLW, a, b);
            case DWORD -> emitShift(VPSLLD, a, b);
            case QWORD -> emitShift(VPSLLQ, a, b);
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
                case DWORD -> emitVectorBinary(VexRVMOp.VPSRAVD, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }

        return switch (aKind.getScalar()) {
            case WORD -> emitShift(VPSRAW, a, b);
            case DWORD -> emitShift(VPSRAD, a, b);
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
                case DWORD -> emitVectorBinary(VexRVMOp.VPSRLVD, a, b);
                case QWORD -> emitVectorBinary(VexRVMOp.VPSRLVQ, a, b);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        }

        return switch (aKind.getScalar()) {
            case WORD -> emitShift(VPSRLW, a, b);
            case DWORD -> emitShift(VPSRLD, a, b);
            case QWORD -> emitShift(VPSRLQ, a, b);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(aKind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Value emitMathAbs(Value input) {
        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        if (kind.getVectorLength() > 1) {
            return switch (kind.getScalar()) {
                case BYTE -> emitUnary(VPABSB, input);
                case WORD -> emitUnary(VPABSW, input);
                case DWORD -> emitUnary(VPABSD, input);
                case QWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VPABSQ"); // ExcludeFromJacocoGeneratedReport
                default ->
                    // abs on SINGLE and DOUBLE vectors should have been lowered already
                    throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return switch (kind) {
                case SINGLE -> {
                    Value floatMask = getLIRGen().emitJavaConstant(JavaConstant.forFloat(Float.intBitsToFloat(0x7FFFFFFF)));
                    yield emitVectorBinary(VANDPS, input, floatMask);
                }
                case DOUBLE -> {
                    Value doubleMask = getLIRGen().emitJavaConstant(JavaConstant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)));
                    yield emitVectorBinary(VANDPD, input, doubleMask);
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
                case SINGLE -> emitUnary(VSQRTPS, input);
                case DOUBLE -> emitUnary(VSQRTPD, input);
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return switch (kind) {
                case SINGLE -> emitUnary(VSQRTSS, input);
                case DOUBLE -> emitUnary(VSQRTSD, input);
                default -> super.emitMathSqrt(input);
            };
        }
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        AMD64Kind from = (AMD64Kind) inputVal.getPlatformKind();
        if (from.getVectorLength() > 1) {
            return new CastValue(to, getLIRGen().asAllocatable(inputVal));
        } else {
            switch (from) {
                case DWORD -> {
                    if (to.getPlatformKind() == AMD64Kind.SINGLE) {
                        return emitConvertOp(to, VMOVD, inputVal, false);
                    }
                }
                case QWORD -> {
                    if (to.getPlatformKind() == AMD64Kind.DOUBLE) {
                        return emitConvertOp(to, VMOVQ, inputVal, false);
                    }
                }
                case SINGLE -> {
                    if (to.getPlatformKind() == AMD64Kind.DWORD) {
                        return emitConvertToIntOp(to, VMOVD, inputVal);
                    } else if (to.getPlatformKind().getVectorLength() > 1 && to.getPlatformKind().getSizeInBytes() == from.getSizeInBytes()) {
                        return new CastValue(to, getLIRGen().asAllocatable(inputVal));
                    }
                }
                case DOUBLE -> {
                    if (to.getPlatformKind() == AMD64Kind.QWORD) {
                        return emitConvertToIntOp(to, VMOVQ, inputVal);
                    } else if (to.getPlatformKind().getVectorLength() > 1 && to.getPlatformKind().getSizeInBytes() == from.getSizeInBytes()) {
                        return new CastValue(to, getLIRGen().asAllocatable(inputVal));
                    }
                }
            }
            return super.emitReinterpret(to, inputVal);
        }
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
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.SINGLE, Math.max(length, 4)), VCVTPD2PS, inputVal, true);
                case D2I:
                    assert baseKind == AMD64Kind.DOUBLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), VCVTTPD2DQ, inputVal, true);
                case D2L:
                    throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VCVTTPD2QQ");
                case F2D:
                    assert baseKind == AMD64Kind.SINGLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DOUBLE, length), VCVTPS2PD, inputVal);
                case F2I:
                    assert baseKind == AMD64Kind.SINGLE : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), VCVTTPS2DQ, inputVal);
                case F2L:
                    throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VCVTTPS2QQ");
                case I2D:
                    assert baseKind == AMD64Kind.DWORD : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DOUBLE, length), VCVTDQ2PD, inputVal);
                case I2F:
                    assert baseKind == AMD64Kind.DWORD : baseKind;
                    return emitConvertOp(AVXKind.getAVXKind(AMD64Kind.SINGLE, length), VCVTDQ2PS, inputVal);
                case L2F:
                    throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VCVTQQ2PS");
                case L2D:
                    throw GraalError.shouldNotReachHere("AVX/AVX2 does not support VCVTQQ2PD");
                default:
                    throw GraalError.unimplemented("unsupported vectorized convert " + op); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            boolean narrow = false;
            switch (op) {
                case D2F:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.SINGLE, VCVTSD2SS, inputVal, op.signedness());
                case D2I:
                case D2UI:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // extract into normal register
                    narrow = true;
                    return emitFloatConvertWithFixup(AMD64Kind.DWORD, VCVTTSD2SI, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case D2L:
                case D2UL:
                    assert kind == AMD64Kind.DOUBLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.QWORD, VCVTTSD2SQ, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case F2D:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.DOUBLE, VCVTSS2SD, inputVal, op.signedness());
                case F2I:
                case F2UI:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.DWORD, VCVTTSS2SI, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case F2L:
                case F2UL:
                    assert kind == AMD64Kind.SINGLE : kind;
                    // extract into normal register
                    return emitFloatConvertWithFixup(AMD64Kind.QWORD, VCVTTSS2SQ, inputVal, canBeNaN, canOverflow, narrow, op.signedness());
                case I2D:
                    assert kind == AMD64Kind.DWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.DOUBLE, VCVTSI2SD, inputVal, op.signedness());
                case I2F:
                    assert kind == AMD64Kind.DWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.SINGLE, VCVTSI2SS, inputVal, op.signedness());
                case L2D:
                    assert kind == AMD64Kind.QWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.DOUBLE, VCVTSQ2SD, inputVal, op.signedness());
                case L2F:
                    assert kind == AMD64Kind.QWORD : kind;
                    // stores into vector register
                    return emitConvertOp(AMD64Kind.SINGLE, VCVTSQ2SS, inputVal, op.signedness());
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
                case 0x1_2 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.WORD, length), VPMOVSXBW, input);
                case 0x1_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), VPMOVSXBD, input);
                case 0x1_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), VPMOVSXBQ, input);
                case 0x2_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), VPMOVSXWD, input);
                case 0x2_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), VPMOVSXWQ, input);
                case 0x4_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), VPMOVSXDQ, input);
                default -> throw GraalError.shouldNotReachHere("unsupported vector sign extend (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitSignExtend(input, fromBits, toBits);
        }
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
                case 0x1_2 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.WORD, length), VPMOVZXBW, input);
                case 0x1_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), VPMOVZXBD, input);
                case 0x1_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), VPMOVZXBQ, input);
                case 0x2_4 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.DWORD, length), VPMOVZXWD, input);
                case 0x2_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), VPMOVZXWQ, input);
                case 0x4_8 -> emitConvertOp(AVXKind.getAVXKind(AMD64Kind.QWORD, length), VPMOVZXDQ, input);
                default -> throw GraalError.shouldNotReachHere("unsupported vector zero extend (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            return super.emitZeroExtend(input, fromBits, toBits, requiresExplicitZeroExtend, requiresLIRKindChange);
        }
    }

    private AllocatableValue emitNarrowingHelper(ValueKind<?> resultKind, int fromBits, int toBits, AllocatableValue input) {
        Variable result = getLIRGen().newVariable(resultKind);

        // optimized cases using WORD and DWORD shuffles
        assert fromBits > toBits : fromBits + " " + toBits;
        assert CodeUtil.isPowerOf2(fromBits) && CodeUtil.isPowerOf2(toBits) : fromBits + " " + toBits;
        switch ((fromBits | toBits) >> 4) {
            // ... QDW
            case 0b011 -> {
                // input == WORDS[0, 1, 2, 3, 4, 5, 6, 7]
                // tmp1 := WORDS[0, 2, ?, ?, 4, 5, 6, 7]
                Variable tmp1 = getLIRGen().newVariable(resultKind);
                getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFLW, tmp1, input, 0x08));
                // tmp2 := WORDS[0, 2, ?, ?, 4, 6, ?, ?]
                Variable tmp2 = getLIRGen().newVariable(resultKind);
                getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFHW, tmp2, tmp1, 0x08));
                // output := WORDS[0, 2, 4, 6]
                getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFD, result, tmp2, 0x08));
                return result;
            }
            case 0b101 -> {
                // input == WORDS[0, 1, 2, 3, 4, 5, 6, 7]
                // tmp3 := WORDS[0, 1, 4, 5]
                Variable tmp3 = getLIRGen().newVariable(resultKind);
                getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFD, tmp3, input, 0x08));
                // output := WORDS[0, 4]
                getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFLW, result, tmp3, 0x08));
                return result;
            }
            case 0b110 -> {
                // input == QWORDS[0, 1] == DWORDS[0, 1, 2, 3]
                // output := DWORDS[0, 2]
                getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPSHUFD, result, input, 0x08));
                return result;
            }
        }

        // general case, using BYTE shuffle
        assert fromBits % 8 == 0 && toBits % 8 == 0 : fromBits + " " + toBits;
        int fromBytes = fromBits / 8;
        int toBytes = toBits / 8;

        AVXSize selectorSize = AVXKind.getRegisterSize((AMD64Kind) resultKind.getPlatformKind());
        byte[] selector = switch (selectorSize) {
            case XMM -> new byte[16];
            case YMM -> new byte[32];
            default -> throw GraalError.shouldNotReachHere("Invalid selector register size: " + selectorSize); // ExcludeFromJacocoGeneratedReport
        };

        int dstIdx = 0;
        for (int i = 0; i < 16 / fromBytes; i++) {
            int byteIdx = i * fromBytes;
            for (int j = 0; j < toBytes; j++) {
                selector[dstIdx++] = (byte) byteIdx++;
            }
        }
        for (; dstIdx < 16; dstIdx++) {
            selector[dstIdx] = (byte) 0x80;
        }
        for (; dstIdx < selector.length; dstIdx++) {
            selector[dstIdx] = selector[dstIdx - 16];
        }

        getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(result, input, VEX, selector));
        return result;
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

            assert fromBits > toBits : fromBits + " " + toBits;
            AMD64Kind elementKind = (AMD64Kind) getLIRGen().getLIRKindTool().getIntegerKind(toBits).getPlatformKind();
            AMD64Kind resultPlatformKind = AVXKind.getAVXKind(elementKind, length);
            LIRKind resultKind = LIRKind.combine(inputVal).changeType(resultPlatformKind);
            AllocatableValue input = asAllocatable(inputVal);
            AVXSize inputSize = AVXKind.getRegisterSize(inputKind);

            switch (inputSize) {
                case XMM:
                    return emitNarrowingHelper(resultKind, fromBits, toBits, input);
                case YMM:
                    assert supports(CPUFeature.AVX2) : "YMM register size requires AVX2 support";
                    AllocatableValue tmp1 = emitNarrowingHelper(inputVal.getValueKind(), fromBits, toBits, input);
                    Variable tmp2 = getLIRGen().newVariable(resultKind);
                    getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPERMQ, tmp2, tmp1, 0x08));

                    return switch (AVXKind.getDataSize(resultPlatformKind)) {
                        case XMM -> tmp2;
                        case QWORD -> emitNarrowingHelper(resultKind, 64, 32, tmp2);
                        case DWORD -> emitNarrowingHelper(resultKind, 64, 16, tmp2);
                        default -> throw GraalError.shouldNotReachHereUnexpectedValue(AVXKind.getDataSize(resultPlatformKind)); // ExcludeFromJacocoGeneratedReport
                    };
                default:
                    throw GraalError.shouldNotReachHere("Invalid AVX register size for narrowing instruction: " + inputSize); // ExcludeFromJacocoGeneratedReport
            }
        } else {
            return super.emitNarrow(inputVal, toBits);
        }
    }

    @Override
    protected VexMoveOp getScalarFloatLoad(AMD64Kind kind) {
        return switch (kind) {
            case SINGLE -> VMOVSS;
            case DOUBLE -> VMOVSD;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind); // ExcludeFromJacocoGeneratedReport
        };
    }

    @Override
    public Variable emitMaskedLoad(LIRKind lirKind, Value address, Value mask, LIRFrameState state, MemoryOrderMode memoryOrder) {
        GraalError.guarantee(!MemoryOrderMode.ordersMemoryAccesses(memoryOrder), "Ordered access currently not supported");
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);
        VexMaskedMoveOp op = switch (kind.getScalar()) {
            case SINGLE -> VexMaskedMoveOp.VMASKMOVPS;
            case DOUBLE -> VexMaskedMoveOp.VMASKMOVPD;
            case DWORD -> supports(CPUFeature.AVX2) ? VexMaskedMoveOp.VPMASKMOVD : VexMaskedMoveOp.VMASKMOVPS;
            case QWORD -> supports(CPUFeature.AVX2) ? VexMaskedMoveOp.VPMASKMOVQ : VexMaskedMoveOp.VMASKMOVPD;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };

        AVXSize size = switch (kind.getSizeInBytes()) {
            // We may want to support smaller-than-XMM masked load by chopping off the mask
            case 16 -> AVXSize.XMM;
            case 32 -> AVXSize.YMM;
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
                    case SINGLE -> VMOVUPS;
                    case DOUBLE -> VMOVUPD;
                    default -> switch (AVXKind.getDataSize(kind)) {
                        // handle vector with a size of DWORD - e.g. AMD64Kind.V32_BYTE
                        case DWORD -> VMOVD;
                        // handle vector with a size of QWORD - e.g. AMD64Kind.V64_WORD
                        case QWORD -> VMOVQ;
                        default -> VMOVDQU32;
                    };
                };
            } else {
                op = switch (kind) {
                    case SINGLE -> VMOVSS;
                    case DOUBLE -> VMOVSD;
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
        VexMaskedMoveOp op = switch (kind.getScalar()) {
            case SINGLE -> VexMaskedMoveOp.VMASKMOVPS;
            case DOUBLE -> VexMaskedMoveOp.VMASKMOVPD;
            case DWORD -> supports(CPUFeature.AVX2) ? VexMaskedMoveOp.VPMASKMOVD : VexMaskedMoveOp.VMASKMOVPS;
            case QWORD -> supports(CPUFeature.AVX2) ? VexMaskedMoveOp.VPMASKMOVQ : VexMaskedMoveOp.VMASKMOVPD;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };

        AVXSize size = switch (kind.getSizeInBytes()) {
            // We may want to support smaller-than-XMM masked load by chopping off the mask
            case 16 -> AVXSize.XMM;
            case 32 -> AVXSize.YMM;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind);
        };

        getLIRGen().append(new AMD64VectorMove.VectorMaskedStoreOp(size, op, loadAddress, asAllocatable(mask), asAllocatable(value), state));
    }

    private boolean canBroadcastValue(AMD64Kind resultKind, Value input) {
        EnumSet<CPUFeature> cpuFeatures = getArchitecture().getFeatures();
        if (cpuFeatures.contains(CPUFeature.AVX2)) {
            return true;
        } else if (cpuFeatures.contains(CPUFeature.AVX) && isJavaConstant(input)) {
            if (asJavaConstant(input).getJavaKind() == JavaKind.Double && AVXKind.getRegisterSize(resultKind) == AVXSize.XMM) {
                // VBROADCASTSD does not support 128-bit/XMM0 registers, and the workaround with
                // VPBROADCASTQ is only available on AVX2
                return false;
            }
            return asJavaConstant(input).getJavaKind().isNumericFloat();
        } else {
            return false;
        }
    }

    @Override
    public Value emitVectorFill(LIRKind lirKind, Value input) {
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();

        if (input instanceof ConstantValue) {
            Constant constant = ((ConstantValue) input).getConstant();
            if (constant instanceof JavaConstant && constant.isDefaultForKind()) {
                Variable result = getLIRGen().newVariable(lirKind);
                getLIRGen().append(new AMD64VectorClearOp(result, VEX));
                return result;
            }
        }

        if (canBroadcastValue(kind, input)) {
            Variable result = getLIRGen().newVariable(lirKind);
            emitVectorBroadcast(kind.getScalar(), result, input);
            return result;
        }

        Variable result = getLIRGen().newVariable(lirKind);
        AllocatableValue value = asAllocatable(input);
        ValueKind<?> singleValueKind = input.getValueKind();

        // move scalar input to vector register
        AllocatableValue scalar;
        scalar = switch (kind.getScalar()) {
            case BYTE, WORD, DWORD, QWORD -> moveIntegerToVectorRegister(kind, singleValueKind, value, true);
            case SINGLE, DOUBLE -> value;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };

        // expand scalar value to 128 bit vector
        Variable result128;
        if (AVXKind.getRegisterSize(kind).getBytes() >= AVXSize.YMM.getBytes()) {
            AMD64Kind kind128 = AVXKind.changeSize(kind, AVXSize.XMM);
            result128 = getLIRGen().newVariable(lirKind.changeType(kind128));
        } else {
            result128 = result;
        }
        switch (kind.getScalar()) {
            case BYTE -> {
                Variable byteSelector = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V128_BYTE));
                getLIRGen().append(new AMD64VectorClearOp(byteSelector, VEX));
                getLIRGen().append(new AMD64VectorShuffle.ShuffleBytesOp(result128, scalar, byteSelector, VEX));
            }
            case WORD -> {
                byte[] shortSelector = new byte[]{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1};
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(result128, scalar, VEX, shortSelector));
            }
            case DWORD -> {
                byte[] intSelector = new byte[]{0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3};
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(result128, scalar, VEX, intSelector));
            }
            case QWORD -> {
                byte[] longSelector = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7};
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(result128, scalar, VEX, longSelector));
            }
            case SINGLE, DOUBLE -> getLIRGen().append(new AMD64VectorShuffle.ShuffleFloatOp(result128, scalar, scalar, 0, VEX));
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar());
        }

        AVXSize resultSize = AVXKind.getRegisterSize(kind);
        if (resultSize == AVXSize.YMM) {
            getLIRGen().append(new AMD64VectorShuffle.Insert128Op(result, result128, result128, 1, VEX));
        }

        return result;
    }

    @Override
    public void emitCompareOp(AMD64Kind kind, AllocatableValue left, Value right) {
        if (kind == AMD64Kind.SINGLE) {
            getLIRGen().append(new AMD64VectorCompareOp(VUCOMISS, left, getLIRGen().asAllocatable(right)));
        } else if (kind == AMD64Kind.DOUBLE) {
            getLIRGen().append(new AMD64VectorCompareOp(VUCOMISD, left, getLIRGen().asAllocatable(right)));
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
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(temp, getLIRGen().asAllocatable(input), VEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(VPBROADCASTB, getRegisterSize(result), result, temp));
            }

            case WORD -> {
                Variable temp = getLIRGen().newVariable(singleValueKind.changeType(AMD64Kind.V128_WORD));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(temp, getLIRGen().asAllocatable(input), VEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(VPBROADCASTW, getRegisterSize(result), result, temp));
            }

            case DWORD -> {
                Variable temp = getLIRGen().newVariable(singleValueKind.changeType(AMD64Kind.V128_DWORD));
                getLIRGen().append(new AMD64VectorShuffle.IntToVectorOp(temp, getLIRGen().asAllocatable(input), VEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(VPBROADCASTD, getRegisterSize(result), result, temp));
            }

            case QWORD -> {
                Variable temp = getLIRGen().newVariable(singleValueKind.changeType(AMD64Kind.V128_QWORD));
                getLIRGen().append(new AMD64VectorShuffle.LongToVectorOp(temp, getLIRGen().asAllocatable(input), VEX));
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(VPBROADCASTQ, getRegisterSize(result), result, temp));
            }

            case SINGLE -> getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(VBROADCASTSS, getRegisterSize(result), result, input));

            case DOUBLE -> {
                AVXSize registerSize = getRegisterSize(result);
                getLIRGen().append(new AMD64VectorUnary.AVXBroadcastOp(registerSize == AVXSize.XMM ? VMOVDDUP : VBROADCASTSD, registerSize, result, input));
            }

            default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + elementKind); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public Value emitVectorToBitMask(LIRKind resultKind, Value vector) {
        Variable result = getLIRGen().newVariable(resultKind);
        AMD64Kind vKind = (AMD64Kind) vector.getPlatformKind();
        AMD64Kind eKind = vKind.getScalar();

        AllocatableValue input = asAllocatable(vector);
        AVXSize size = getRegisterSize(input);
        if (eKind == AMD64Kind.WORD) {
            GraalError.guarantee(!supports(CPUFeature.BMI2), "should be lowered");
            Variable packed = getLIRGen().newVariable(vector.getValueKind());
            getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VexRVMOp.VPACKSSWB, AVXKind.getRegisterSize(input), packed, input, input));
            if (vKind.getSizeInBytes() == 32) {
                Variable shuffle = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_WORD));
                getLIRGen().append(new AMD64VectorShuffle.ShuffleWordOp(VPERMQ, shuffle, packed, 0b1000));
                packed = shuffle;
            }
            size = AVXSize.XMM;
            input = packed;
            eKind = AMD64Kind.BYTE;
        }

        AMD64Assembler.VexRMOp op = switch (eKind) {
            case BYTE -> VPMOVMSKB;
            case DWORD -> VMOVMSKPS;
            case QWORD -> VMOVMSKPD;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(eKind);
        };
        getLIRGen().append(new AMD64VectorUnary.AVXUnaryRROp(op, size, result, input));

        if (eKind.getSizeInBytes() * vKind.getVectorLength() < 16) {
            Variable fix = getLIRGen().newVariable(resultKind);
            getLIRGen().append(new AMD64Binary.ConstOp(AMD64Assembler.AMD64BinaryArithmetic.AND, AMD64BaseAssembler.OperandSize.DWORD, fix, result, (int) CodeUtil.mask(vKind.getVectorLength())));
            result = fix;
        }

        return result;
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

        // Make an all-ones mask, meaning that we want to gather all elements. More general
        // masked gathers are not supported yet. We must construct this mask immediately
        // before the gather instruction because it clobbers it. It must also be a fixed
        // register for the Use+Temp trick to work.
        RegisterValue mask = xmm0.asValue(offsets.getValueKind());
        PrimitiveConstant allBits = (offsetKind == AMD64Kind.DWORD
                        ? JavaConstant.forInt(-1)
                        : JavaConstant.forLong(-1));
        SimdConstant allOnesConstant = SimdConstant.broadcast(allBits, vectorLength);
        getLIRGen().append(new AVXAllOnesOp(mask, allOnesConstant));
        VexGatherOp op;
        if (offsetKind == AMD64Kind.DWORD) {
            op = switch (resultElementKind) {
                case DWORD -> VexGatherOp.VPGATHERDD;
                case QWORD -> VexGatherOp.VPGATHERDQ;
                case DOUBLE -> VexGatherOp.VGATHERDPD;
                case SINGLE -> VexGatherOp.VGATHERDPS;
                default -> throw GraalError.shouldNotReachHere("unsupported vector gather: offset kind " + offsetKind + ", result element kind " + resultElementKind); // ExcludeFromJacocoGeneratedReport
            };
        } else if (offsetKind == AMD64Kind.QWORD) {
            op = switch (resultElementKind) {
                case DWORD -> VexGatherOp.VPGATHERQD;
                case QWORD -> VexGatherOp.VPGATHERQQ;
                case DOUBLE -> VexGatherOp.VGATHERQPD;
                case SINGLE -> VexGatherOp.VGATHERQPS;
                default -> throw GraalError.shouldNotReachHere("unsupported vector gather: offset kind " + offsetKind + ", result element kind " + resultElementKind); // ExcludeFromJacocoGeneratedReport
            };
        } else {
            throw GraalError.shouldNotReachHere("vector gather offsets must be DWORD or QWORD, got: " + offsetKind); // ExcludeFromJacocoGeneratedReport
        }

        getLIRGen().append(new AMD64VectorGather.VexVectorGatherOp(op, size, result, b, offs, mask));

        return result;
    }

    @Override
    public Value emitVectorPackedEquals(Value vectorA, Value vectorB) {
        Variable result = getLIRGen().newVariable(vectorB.getValueKind());
        AllocatableValue a = getLIRGen().asAllocatable(vectorA);
        AllocatableValue b = getLIRGen().asAllocatable(vectorB);

        VexRVMOp op = switch (((AMD64Kind) vectorB.getPlatformKind()).getScalar()) {
            case BYTE -> VPCMPEQB;
            case WORD -> VPCMPEQW;
            case DWORD -> VPCMPEQD;
            case QWORD -> VPCMPEQQ;
            default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + vectorB.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        };
        getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(op, getRegisterSize(result), result, a, b));
        return result;
    }

    @Override
    public Value emitVectorPackedComparison(CanonicalCondition condition, Value vectorA, Value vectorB, boolean unorderedIsTrue) {
        AMD64Kind platformKind = (AMD64Kind) vectorB.getPlatformKind();
        GraalError.guarantee(condition != CanonicalCondition.BT, "AVX/AVX2 does not support unsigned packed comparison");

        AMD64Kind maskKind = AVXKind.getMaskKind(platformKind);
        Variable result = getLIRGen().newVariable(vectorB.getValueKind().changeType(maskKind));
        if (condition == CanonicalCondition.EQ && platformKind.getScalar().isInteger()) {
            return emitVectorPackedEquals(vectorA, vectorB);
        }

        AllocatableValue a = getLIRGen().asAllocatable(vectorA);
        AllocatableValue b = getLIRGen().asAllocatable(vectorB);

        assert condition == CanonicalCondition.LT || !platformKind.getScalar().isInteger() : condition + " " + platformKind;

        switch (platformKind.getScalar()) {
            // In the integer cases, note the flipped arguments a and b to implement LT
            // comparison with GT instruction.
            case BYTE -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VPCMPGTB, getRegisterSize(result), result, b, a));
            case WORD -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VPCMPGTW, getRegisterSize(result), result, b, a));
            case DWORD -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VPCMPGTD, getRegisterSize(result), result, b, a));
            case QWORD -> getLIRGen().append(new AMD64VectorBinary.AVXBinaryOp(VPCMPGTQ, getRegisterSize(result), result, b, a));

            // Floating-point comparison instructions take a predicate, so arguments are not
            // flipped.
            case SINGLE -> getLIRGen().append(new AMD64VectorFloatCompareOp(VCMPPS, getRegisterSize(result), result, a, b,
                            VexFloatCompareOp.Predicate.getPredicate(condition.asCondition(), unorderedIsTrue)));
            case DOUBLE -> getLIRGen().append(new AMD64VectorFloatCompareOp(VCMPPD, getRegisterSize(result), result, a, b,
                            VexFloatCompareOp.Predicate.getPredicate(condition.asCondition(), unorderedIsTrue)));

            default -> throw GraalError.shouldNotReachHere("unexpected element kind: " + vectorB.getPlatformKind()); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    /**
     * Emits a vector blend with a constant selector. This must massage the selector into a constant
     * of the appropriate kind (single bitmask or vector of bitmasks) and emit that, then it falls
     * back to the general {@link #emitVectorBlend(Value, Value, Value)}.
     */
    @Override
    public Variable emitVectorBlend(Value zeroValue, Value oneValue, boolean[] selector) {
        AMD64Kind baseKind = ((AMD64Kind) zeroValue.getPlatformKind()).getScalar();
        int kindIndex = CodeUtil.log2(baseKind.getSizeInBytes());
        JavaKind maskJavaKind = MASK_JAVA_KINDS[kindIndex];
        SimdStamp simdMaskStamp = SimdStamp.broadcast(IntegerStamp.create(maskJavaKind.getBitCount(), -1, 0), selector.length);
        LIRKind simdMaskKind = getLIRGen().getLIRKind(simdMaskStamp);
        Constant selectorConstant = SimdConstant.forBitmaskBlendSelector(selector, maskJavaKind);
        Value selectorValue = getLIRGen().emitConstant(simdMaskKind, selectorConstant);
        return emitVectorBlend(zeroValue, oneValue, selectorValue);
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

        CanonicalizedCondition canonicalizedCond = cond.canonicalize();
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
        comparisonMask = adjustMaskForBlend(comparisonMask, (AMD64Kind) trueValue.getPlatformKind());

        return emitVectorBlend(falseOperand, trueOperand, comparisonMask);
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
        Variable andResult = emitAnd(left, right);
        Variable zeroValue = getLIRGen().newVariable(left.getValueKind());
        getLIRGen().append(new AMD64VectorClearOp(zeroValue, VEX));
        GraalError.guarantee(AVXKind.getRegisterSize(left) != AVXSize.ZMM, "SSE/AVX arithmetic LIR generator can not generate instructions of size ZMM!");
        Value comparisonMask = emitVectorPackedComparison(CanonicalCondition.EQ, andResult, zeroValue, false);
        comparisonMask = adjustMaskForBlend(comparisonMask, (AMD64Kind) trueValue.getPlatformKind());

        // true and false values inverted because blend picks the first operand if the mask is
        // 0, i.e., the condition is false.
        return emitVectorBlend(falseValue, trueValue, comparisonMask);
    }

    private Value adjustMaskForBlend(Value mask, AMD64Kind blendResultKind) {
        // Extend or narrow the mask's elements to match the size of the result kind.
        AMD64Kind maskKind = (AMD64Kind) mask.getPlatformKind();
        assert maskKind.getVectorLength() == blendResultKind.getVectorLength() : "expected equal lengths, got " + maskKind.getVectorLength() + ", " + blendResultKind.getVectorLength();
        int maskScalarBytes = maskKind.getScalar().getSizeInBytes();
        int resultScalarBytes = blendResultKind.getScalar().getSizeInBytes();
        if (maskScalarBytes < resultScalarBytes) {
            return emitSignExtend(mask, maskScalarBytes * Byte.SIZE, resultScalarBytes * Byte.SIZE);
        } else if (maskScalarBytes > resultScalarBytes) {
            return emitNarrow(mask, resultScalarBytes * Byte.SIZE);
        }
        return mask;
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

        // ... then negate it to get a 0 or -1 mask.
        Variable integerMask = getLIRGen().newVariable(longKind);
        getLIRGen().append(new AMD64Unary.MOp(AMD64MOp.NEG, AMD64BaseAssembler.OperandSize.QWORD, integerMask, zeroOrOne));

        // Move this mask to an XMM register and use it as the mask for a blend.
        LIRKind resultLirKind = getLIRGen().getLIRKindTool().getFloatingKind(resultKind.getSizeInBytes() * Byte.SIZE);
        Value selectorMask = emitReinterpret(resultLirKind, integerMask);
        return emitVectorBlend(falseValue, trueValue, selectorMask);
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
        AVXSize size = AVXKind.getRegisterSize(kind);
        GraalError.guarantee(size != AVXSize.ZMM, "SSE/AVX arithmetic LIR generator can not generate instructions of size ZMM!");
        if (kind.getScalar().isInteger()) {
            return emitIntegerMinMax(a, b, minmaxop, NumUtil.Signedness.SIGNED);
        }
        return super.emitMathMinMax(a, b, minmaxop);
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
            case BYTE -> emitVectorBinary(resultKind, min ? (signed ? VPMINSB : VPMINUB) : (signed ? VPMAXSB : VPMAXUB), a, b);
            case WORD -> emitVectorBinary(resultKind, min ? (signed ? VPMINSW : VPMINUW) : (signed ? VPMAXSW : VPMAXUW), a, b);
            case DWORD -> emitVectorBinary(resultKind, min ? (signed ? VPMINSD : VPMINUD) : (signed ? VPMAXSD : VPMAXUD), a, b);
            case QWORD -> throw GraalError.shouldNotReachHere("AVX/AVX2 does not support " + (min ? (signed ? "VPMINSQ" : "VPMINUQ") : (signed ? "VPMAXSQ" : "VPMAXUQ"))); // ExcludeFromJacocoGeneratedReport
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(kind.getScalar()); // ExcludeFromJacocoGeneratedReport
        };
    }

    private static final byte[] LOWER_IDENTITY_256_SHUFFLE_BYTES = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4, 5, 6, 7};
    private static final byte[] UPPER_IDENTITY_256_SHUFFLE_BYTES = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, -1, -1, -1, -1, -1, -1, -1, -1};

    @Override
    public Value emitConstShuffleBytes(LIRKind resultKind, Value vector, byte[] selector) {
        Variable result = getLIRGen().newVariable(resultKind);
        if (selector.length <= 16) {
            byte[] instSelector = selector;
            if (instSelector.length < 16) {
                instSelector = Arrays.copyOf(selector, 16);
                Arrays.fill(instSelector, selector.length, 16, (byte) -1);
            }
            AllocatableValue allocatedVector;
            if (vector.getPlatformKind().getSizeInBytes() > 16) {
                allocatedVector = new CastValue(vector.getValueKind().changeType(AMD64Kind.V128_BYTE), asAllocatable(vector));
            } else {
                allocatedVector = asAllocatable(vector);
            }
            getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(asAllocatable(result), allocatedVector, VEX, instSelector));
        } else {
            assert selector.length <= 32 : selector.length;
            AllocatableValue v = asAllocatable(vector);

            if (v.getPlatformKind().getSizeInBytes() <= 16) {
                AllocatableValue widenedV = getLIRGen().newVariable(v.getValueKind().changeType(AMD64Kind.V256_BYTE));
                getLIRGen().append(new AMD64VectorShuffle.Insert128Op(widenedV, v, v, 0, VEX));
                v = widenedV;
            }

            // the first step is to build two selectors that can be used to select elements across
            // the two lanes that will all go to the lower 128 bits or the upper 128 bits.
            byte[] lowerSelector = new byte[32];
            byte[] upperSelector = new byte[32];
            Arrays.fill(lowerSelector, (byte) -1);
            Arrays.fill(upperSelector, (byte) -1);

            boolean l1set = false;
            boolean l2set = false;
            boolean u1set = false;
            boolean u2set = false;
            for (int i = 0; i < 16; ++i) {
                if (selector[i] < 16) {
                    lowerSelector[i] = selector[i];
                    l1set = true;
                } else {
                    lowerSelector[i + 16] = (byte) (selector[i] - 16);
                    l2set = true;
                }
                if (i + 16 < selector.length) {
                    if (selector[i + 16] < 16) {
                        upperSelector[i] = selector[i + 16];
                        u1set = true;
                    } else {
                        upperSelector[i + 16] = (byte) (selector[i + 16] - 16);
                        u2set = true;
                    }
                }
            }

            // if permutation is only within the lane of origin we can do a quick and simple shuffle
            if (l1set && !l2set && !u1set && u2set) {
                byte[] mergedSelector = new byte[32];
                System.arraycopy(lowerSelector, 0, mergedSelector, 0, 16);
                System.arraycopy(upperSelector, 16, mergedSelector, 16, 16);
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(asAllocatable(result), v, VEX, mergedSelector));
                return result;
            }

            AllocatableValue upper = asAllocatable(getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE)));
            AllocatableValue lower = asAllocatable(getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE)));

            if (!Arrays.equals(upperSelector, UPPER_IDENTITY_256_SHUFFLE_BYTES)) {
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(upper, v, VEX, upperSelector));
            } else {
                upper = v;
            }
            if (!Arrays.equals(upperSelector, LOWER_IDENTITY_256_SHUFFLE_BYTES)) {
                getLIRGen().append(new AMD64VectorShuffle.ConstShuffleBytesOp(lower, v, VEX, lowerSelector));
            } else {
                lower = v;
            }

            // we now have two registers lower (elements for the lower half of the result)
            // and upper (elements for the upper half of the result)
            // lower = [ L1, L2 ]
            // upper = [ U1, U2 ]
            // and we want to produce
            // a = [ L1, U1 ]
            // b = [ L2, U2 ]
            // so we can do a blend within a lane - enter extract128
            AllocatableValue a;
            AllocatableValue b;
            byte[] mask = new byte[32];
            for (int i = 0; i < 16; ++i) {
                mask[i] = (byte) (lowerSelector[i] == -1 ? -1 : 0);
            }
            for (int i = 16; i < 32; ++i) {
                mask[i] = (byte) (upperSelector[i - 16] == -1 ? -1 : 0);
            }

            // only the actual extract and swap if we have to - eg there are values in L2 or U1
            if (l2set || u1set) {
                AllocatableValue l2 = asAllocatable(getLIRGen().newVariable(LIRKind.value(AMD64Kind.V128_BYTE)));
                AllocatableValue u1 = asAllocatable(getLIRGen().newVariable(LIRKind.value(AMD64Kind.V128_BYTE)));

                getLIRGen().append(new AMD64VectorShuffle.Extract128Op(l2, lower, 1, VEX));
                getLIRGen().append(new AMD64VectorShuffle.Extract128Op(u1, upper, 0, VEX));

                a = asAllocatable(getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE)));
                b = asAllocatable(getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE)));

                getLIRGen().append(new AMD64VectorShuffle.Insert128Op(a, lower, u1, 1, VEX));
                getLIRGen().append(new AMD64VectorShuffle.Insert128Op(b, upper, l2, 0, VEX));

            } else if (l1set || u2set) {
                a = lower;
                b = upper;
                Arrays.fill(mask, 16, 32, (byte) -1);
            } else {
                a = lower;
                b = upper;

                Arrays.fill(mask, 0, 32, (byte) -1);
            }

            DataPointerConstant maskArray = new ArrayDataPointerConstant(mask, mask.length);
            Variable maskReg = getLIRGen().newVariable(LIRKind.value(AMD64Kind.V256_BYTE));
            getLIRGen().append(new AMD64VectorMove.MoveFromArrayConstOp(maskReg, maskArray, VEX));
            getLIRGen().append(new AMD64VectorBlend.VexBlendOp(AMD64Assembler.VexRVMROp.VPBLENDVB, AVXKind.AVXSize.YMM, asAllocatable(result), a, b, asAllocatable(maskReg)));
        }
        return result;
    }

    @Override
    public Value emitMoveOpMaskToInteger(LIRKind resultKind, Value mask, int maskLen) {
        throw GraalError.shouldNotReachHere("AVX/AVX2 does not support opmasks");
    }

    @Override
    public Value emitMoveIntegerToOpMask(LIRKind resultKind, Value mask) {
        throw GraalError.shouldNotReachHere("AVX/AVX2 does not support opmasks");
    }

    @Override
    public Variable emitVectorOpMaskTestMove(Value left, boolean negateLeft, Value right, Value trueValue, Value falseValue) {
        throw GraalError.shouldNotReachHere("AVX/AVX2 does not support opmasks");
    }

    @Override
    public Variable emitVectorOpMaskOrTestMove(Value left, Value right, boolean allZeros, Value trueValue, Value falseValue) {
        throw GraalError.shouldNotReachHere("AVX/AVX2 does not support opmasks");
    }

    @Override
    public Variable emitVectorCompress(LIRKind resultKind, Value source, Value mask) {
        throw GraalError.shouldNotReachHere("AVX/AVX2 does not support compress/expand");
    }

    @Override
    public Variable emitVectorExpand(LIRKind resultKind, Value source, Value mask) {
        throw GraalError.shouldNotReachHere("AVX/AVX2 does not support compress/expand");
    }
}
