/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp.VEXTRACTI128;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMoveOp.VMOVDQU32;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VBROADCASTSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp.VPBROADCASTD;
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
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPADDW;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPHADDD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VPMULLD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.amd64.AMD64LIRHelper.recordExternalAddress;
import static jdk.vm.ci.amd64.AMD64Kind.QWORD;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.Arrays;
import java.util.EnumSet;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Emits the AMD64 implementation of the vectorized array hash-code intrinsic.
 * <p>
 * This implementation is based on the corresponding C2 intrinsic, with the following intentional
 * differences:
 * <ul>
 * <li>The four result vectors are reduced pairwise before their lanes are reduced.</li>
 * <li>Lane reduction uses shuffles and {@code VPADDD} instead of {@code VPHADDD}.</li>
 * <li>The scalar tail uses a three-operand immediate {@code IMUL}, and the vector-loop recurrence
 * multiplier is materialized as an immediate rather than loaded from the coefficient table.</li>
 * <li>The data temporaries are reused for coefficients, reducing the number of vector temporary
 * registers from 13 to 9.</li>
 * <li>The vector loop and powers-of-31 coefficient data are aligned.</li>
 * <li>XMM/AVX1 and scalar-only x86-64-v1 targets are supported in addition to YMM/AVX2.</li>
 * </ul>
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L1896-L2005",
          sha1 = "1cc5a10b19e7746105493d8f430f628cc7f89c51")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L2194-L2240",
          sha1 = "9cbba8bd6c4037427fa46f067abb722b15aca90c")
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/x86/c2_MacroAssembler_x86.cpp#L3551-L3738",
          sha1 = "a3fe941a49e0e3f8443b2e16e550d6c94b012b12")
// @formatter:on
@Opcode("VECTORIZED_HASHCODE")
public final class AMD64VectorizedHashCodeOp extends AMD64ComplexVectorOp {
    public static final LIRInstructionClass<AMD64VectorizedHashCodeOp> TYPE = LIRInstructionClass.create(AMD64VectorizedHashCodeOp.class);

    @Def({OperandFlag.REG}) private Value resultValue;
    @Alive({OperandFlag.REG}) private Value arrayStart;
    @Alive({OperandFlag.REG}) private Value length;
    @Alive({OperandFlag.REG}) private Value initialValue;

    private final JavaKind arrayKind;

    @Temp({OperandFlag.REG}) Value[] temp;
    @Temp({OperandFlag.REG}) Value[] vectorTemp;

    public AMD64VectorizedHashCodeOp(LIRGeneratorTool tool, EnumSet<CPUFeature> runtimeCheckedCPUFeatures,
                    AllocatableValue result, AllocatableValue arrayStart, AllocatableValue length, AllocatableValue initialValue, JavaKind arrayKind) {
        super(TYPE, tool, runtimeCheckedCPUFeatures, YMM);
        this.resultValue = result;
        this.arrayStart = arrayStart;
        this.length = length;
        this.initialValue = initialValue;
        this.arrayKind = arrayKind;

        this.temp = allocateTempRegisters(tool, QWORD, 5);
        this.vectorTemp = allocateVectorRegisters(tool, JavaKind.Byte, 9);
    }

    private static void arraysHashcodeElload(AMD64MacroAssembler masm, Register dst, AMD64Address src, JavaKind eltype) {
        switch (eltype) {
            case Boolean -> masm.movzbl(dst, src);
            case Byte -> masm.movsbl(dst, src);
            case Short -> masm.movswl(dst, src);
            case Char -> masm.movzwl(dst, src);
            case Int -> masm.movl(dst, src);
            default -> throw GraalError.shouldNotReachHere("Unsupported JavaKind " + eltype);
        }
    }

    private static void vectorUnsignedCast(AMD64MacroAssembler masm, Register dst, Register src, AVXKind.AVXSize avxSize, JavaKind fromElemBt, JavaKind toElemBt) {
        switch (fromElemBt) {
            case Byte -> {
                switch (toElemBt) {
                    case Short -> masm.emit(VPMOVZXBW, dst, src, avxSize);
                    case Int -> masm.emit(VPMOVZXBD, dst, src, avxSize);
                    case Long -> masm.emit(VPMOVZXBQ, dst, src, avxSize);
                    default -> throw GraalError.shouldNotReachHere("Unsupported unsigned vector cast from " + fromElemBt + " to " + toElemBt);
                }
            }
            case Short -> {
                switch (toElemBt) {
                    case Int -> masm.emit(VPMOVZXWD, dst, src, avxSize);
                    case Long -> masm.emit(VPMOVZXWQ, dst, src, avxSize);
                    default -> throw GraalError.shouldNotReachHere("Unsupported unsigned vector cast from " + fromElemBt + " to " + toElemBt);
                }
            }
            case Int -> {
                GraalError.guarantee(toElemBt == JavaKind.Long, "Unsupported unsigned vector cast from %s to %s", fromElemBt, toElemBt);
                masm.emit(VPMOVZXDQ, dst, src, avxSize);
            }
            default -> throw GraalError.shouldNotReachHere("Unsupported unsigned vector cast from " + fromElemBt + " to " + toElemBt);
        }
    }

    private static void vectorSignedCast(AMD64MacroAssembler masm, Register dst, Register src, AVXKind.AVXSize avxSize, JavaKind fromElemBt, JavaKind toElemBt) {
        switch (fromElemBt) {
            case Byte -> {
                switch (toElemBt) {
                    case Short -> masm.emit(VPMOVSXBW, dst, src, avxSize);
                    case Int -> masm.emit(VPMOVSXBD, dst, src, avxSize);
                    case Long -> masm.emit(VPMOVSXBQ, dst, src, avxSize);
                    default -> throw GraalError.shouldNotReachHere("Unsupported signed vector cast from " + fromElemBt + " to " + toElemBt);
                }
            }
            case Short -> {
                switch (toElemBt) {
                    case Int -> masm.emit(VPMOVSXWD, dst, src, avxSize);
                    case Long -> masm.emit(VPMOVSXWQ, dst, src, avxSize);
                    default -> throw GraalError.shouldNotReachHere("Unsupported signed vector cast from " + fromElemBt + " to " + toElemBt);
                }
            }
            case Int -> {
                GraalError.guarantee(toElemBt == JavaKind.Long, "Unsupported signed vector cast from %s to %s", fromElemBt, toElemBt);
                masm.emit(VPMOVSXDQ, dst, src, avxSize);
            }
            default -> throw GraalError.shouldNotReachHere("Unsupported signed vector cast from " + fromElemBt + " to " + toElemBt);
        }
    }

    private static void arraysHashcodeElvcast(AMD64MacroAssembler masm, Register dst, JavaKind eltype, AVXKind.AVXSize avxSize) {
        switch (eltype) {
            case Boolean -> vectorUnsignedCast(masm, dst, dst, avxSize, JavaKind.Byte, JavaKind.Int);
            case Byte -> vectorSignedCast(masm, dst, dst, avxSize, JavaKind.Byte, JavaKind.Int);
            case Short -> vectorSignedCast(masm, dst, dst, avxSize, JavaKind.Short, JavaKind.Int);
            case Char -> vectorUnsignedCast(masm, dst, dst, avxSize, JavaKind.Short, JavaKind.Int);
            case Int -> {
                // do nothing
            }
            default -> throw GraalError.shouldNotReachHere("Unsupported vector cast from " + eltype);
        }
    }

    private static void loadVector(AMD64MacroAssembler masm, Register dst, AMD64Address src, int vlenInBytes) {
        switch (vlenInBytes) {
            case 4 -> masm.movdl(dst, src);
            case 8 -> masm.movq(dst, src);
            case 16 -> masm.movdqu(dst, src);
            case 32 -> masm.vmovdqu(dst, src);
            case 64 -> masm.emit(VMOVDQU32, dst, src, ZMM);
            default -> throw GraalError.shouldNotReachHere("Unsupported vector load of size " + vlenInBytes);
        }
    }

    // we only port Op_AddReductionVI-related code
    private static void reduce(AMD64MacroAssembler masm, AVXKind.AVXSize avxSize, JavaKind eleType, Register dst, Register src1, Register src2) {
        switch (eleType) {
            case Byte -> masm.emit(VPADDB, dst, src1, src2, avxSize);
            case Short -> masm.emit(VPADDW, dst, src1, src2, avxSize);
            case Int -> masm.emit(VPADDD, dst, src1, src2, avxSize);
            default -> throw GraalError.shouldNotReachHere("Unsupported reduce type " + eleType);
        }
    }

    private static void reduce2I(AMD64MacroAssembler masm, Register dst, Register src1, Register src2, Register vtmp1, Register vtmp2, boolean useHorizontalAdd) {
        if (useHorizontalAdd) {
            if (!vtmp1.equals(src2)) {
                masm.movdqu(vtmp1, src2);
            }
            masm.emit(VPHADDD, vtmp1, vtmp1, vtmp1, XMM);
        } else {
            masm.vpshufd(vtmp1, src2, 0x1, XMM);
            reduce(masm, XMM, JavaKind.Int, vtmp1, vtmp1, src2);
        }
        masm.movdl(vtmp2, src1);
        reduce(masm, XMM, JavaKind.Int, vtmp1, vtmp1, vtmp2);
        masm.movdl(dst, vtmp1);
    }

    private static void reduce4I(AMD64MacroAssembler masm, Register dst, Register src1, Register src2, Register vtmp1, Register vtmp2, boolean useHorizontalAdd) {
        if (useHorizontalAdd) {
            if (!vtmp1.equals(src2)) {
                masm.movdqu(vtmp1, src2);
            }
            masm.emit(VPHADDD, vtmp1, vtmp1, src2, XMM);
            reduce2I(masm, dst, src1, vtmp1, vtmp1, vtmp2, useHorizontalAdd);
        } else {
            masm.vpshufd(vtmp2, src2, 0xe, XMM);
            reduce(masm, XMM, JavaKind.Int, vtmp2, vtmp2, src2);
            reduce2I(masm, dst, src1, vtmp2, vtmp1, vtmp2, useHorizontalAdd);
        }
    }

    private static void reduce8I(AMD64MacroAssembler masm, Register dst, Register src1, Register src2, Register vtmp1, Register vtmp2, boolean useHorizontalAdd) {
        if (useHorizontalAdd) {
            masm.emit(VPHADDD, vtmp1, src2, src2, YMM);
            masm.emit(VEXTRACTI128, vtmp2, vtmp1, 1, YMM);
            masm.emit(VPADDD, vtmp1, vtmp1, vtmp2, XMM);
            reduce2I(masm, dst, src1, vtmp1, vtmp1, vtmp2, useHorizontalAdd);
        } else {
            masm.emit(VEXTRACTI128, vtmp1, src2, 1, YMM);
            reduce(masm, XMM, JavaKind.Int, vtmp1, vtmp1, src2);
            reduce4I(masm, dst, src1, vtmp1, vtmp1, vtmp2, useHorizontalAdd);
        }
    }

    private static void reduceI(AMD64MacroAssembler masm, int vlen, Register dst, Register src1, Register src2, Register vtmp1, Register vtmp2, boolean useHorizontalAdd) {
        switch (vlen) {
            case 2 -> reduce2I(masm, dst, src1, src2, vtmp1, vtmp2, useHorizontalAdd);
            case 4 -> reduce4I(masm, dst, src1, src2, vtmp1, vtmp2, useHorizontalAdd);
            case 8 -> reduce8I(masm, dst, src1, src2, vtmp1, vtmp2, useHorizontalAdd);
            default -> throw GraalError.shouldNotReachHere("Unsupported vector length " + vlen);
        }
    }

    private static final int[] POWERS_OF_31_BACKWARDS = {
                    2111290369,
                    -2010103841,
                    350799937,
                    11316127,
                    693101697,
                    -254736545,
                    961614017,
                    31019807,
                    -2077209343,
                    -67006753,
                    1244764481,
                    -2038056289,
                    211350913,
                    -408824225,
                    -844471871,
                    -997072353,
                    1353309697,
                    -510534177,
                    1507551809,
                    -505558625,
                    -293403007,
                    129082719,
                    -1796951359,
                    -196513505,
                    -1807454463,
                    1742810335,
                    887503681,
                    28629151,
                    923521,
                    29791,
                    961,
                    31,
                    1,
    };
    // The first coefficient (31^32) is used as a scalar and is not loaded into a vector.
    private static final ArrayDataPointerConstant powersOf31 = pointerConstant(64,
                    Arrays.copyOfRange(POWERS_OF_31_BACKWARDS, 1, POWERS_OF_31_BACKWARDS.length));

    private static int powerOf31(int exponent) {
        return POWERS_OF_31_BACKWARDS[POWERS_OF_31_BACKWARDS.length - 1 - exponent];
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Label labelShortUnrolledBegin = new Label();
        Label labelShortUnrolledLoopBegin = new Label();
        Label labelShortUnrolledLoopExit = new Label();
        Label labelUnrolledVectorLoopBegin = new Label();
        Label labelEnd = new Label();

        Register result = asRegister(resultValue);
        Register ary1 = asRegister(temp[0]);
        Register cnt1 = asRegister(temp[1]);
        Register tmp2 = asRegister(temp[2]);
        Register tmp3 = asRegister(temp[3]);
        Register index = asRegister(temp[4]);

        masm.movq(ary1, asRegister(arrayStart));
        masm.movl(cnt1, asRegister(length));
        masm.movl(result, asRegister(initialValue));

        // For "renaming" for readability of the code
        Register vnext = asRegister(vectorTemp[0]);
        Register[] vtmp = {asRegister(vectorTemp[1]), asRegister(vectorTemp[2]), asRegister(vectorTemp[3]), asRegister(vectorTemp[4])};
        Register[] vresult = {asRegister(vectorTemp[5]), asRegister(vectorTemp[6]), asRegister(vectorTemp[7]), asRegister(vectorTemp[8])};

        Stride stride = Stride.fromJavaKind(arrayKind);
        int elsize = arrayKind.getByteCount();

        // @formatter:off
        // if (cnt1 >= 2) {
        //   if (cnt1 >= elementsPerLoop) {
        //     UNROLLED VECTOR LOOP
        //   }
        //   UNROLLED SCALAR LOOP
        // }
        // SINGLE SCALAR
        // @formatter:on
        if (supports(CPUFeature.AVX)) {
            AVXKind.AVXSize avxSize = supportsAVX2AndYMM() ? YMM : XMM;
            int elementsPerVector = avxSize.getBytes() / JavaKind.Int.getByteCount();
            int elementsPerLoop = elementsPerVector * vresult.length;
            int powersOf31Offset = (POWERS_OF_31_BACKWARDS.length - 1 - elementsPerLoop) * JavaKind.Int.getByteCount();

            masm.cmplAndJcc(cnt1, elementsPerLoop, ConditionFlag.Less, labelShortUnrolledBegin, false);
            // cnt1 >= elementsPerLoop
            masm.xorl(index, index);
            // vresult = IntVector.zero(species);
            for (int idx = 0; idx < 4; idx++) {
                masm.vpxor(vresult[idx], vresult[idx], vresult[idx], avxSize);
            }
            // vnext = IntVector.broadcast(species, power_of_31_backwards[0]);

            Register bound = tmp2;
            Register next = tmp3;
            masm.movl(next, powerOf31(elementsPerLoop));
            masm.movdl(vnext, next);
            masm.emit(supports(CPUFeature.AVX2) ? VPBROADCASTD : VBROADCASTSS, vnext, vnext, avxSize);

            // index = 0;
            // bound = cnt1 & ~(elementsPerLoop - 1);
            masm.movl(bound, cnt1);
            masm.andl(bound, ~(elementsPerLoop - 1));
            // for (; index < bound; index += elementsPerLoop) {
            masm.align(preferredLoopAlignment(crb));
            masm.bind(labelUnrolledVectorLoopBegin);
            // result *= next;
            masm.imull(result, next);
            /*
             * Load the next 32 data elements into 4 vector registers. By grouping the loads and
             * fetching from memory up front (loop fission), out-of-order execution can hopefully do
             * a better job of prefetching, while also allowing subsequent instructions to be
             * executed while data are still being fetched.
             */
            int bytesPerVector = elementsPerVector * elsize;
            for (int idx = 0; idx < 4; idx++) {
                loadVector(masm, vtmp[idx], new AMD64Address(ary1, index, stride, idx * bytesPerVector), bytesPerVector);
            }
            // vresult = vresult * vnext + ary1[index + idx * elementsPerVector];
            for (int idx = 0; idx < 4; idx++) {
                masm.emit(VPMULLD, vresult[idx], vresult[idx], vnext, avxSize);
                arraysHashcodeElvcast(masm, vtmp[idx], arrayKind, avxSize);
                masm.emit(VPADDD, vresult[idx], vresult[idx], vtmp[idx], avxSize);
            }
            // index += elementsPerLoop;
            masm.addl(index, elementsPerLoop);
            // index < bound;
            masm.cmplAndJcc(index, bound, ConditionFlag.Less, labelUnrolledVectorLoopBegin, false);
            // }

            masm.leaq(ary1, new AMD64Address(ary1, bound, stride));
            masm.subl(cnt1, bound);
            // release bound

            // vresult *= IntVector.fromArray(species, power_of_31_backwards, 1);
            masm.leaq(tmp2, recordExternalAddress(crb, powersOf31));
            for (int idx = 0; idx < 4; idx++) {
                loadVector(masm, vtmp[idx], new AMD64Address(tmp2, powersOf31Offset + idx * avxSize.getBytes()), avxSize.getBytes());
            }
            for (int idx = 0; idx < 4; idx++) {
                masm.emit(VPMULLD, vresult[idx], vresult[idx], vtmp[idx], avxSize);
            }
            // result += vresult.reduceLanes(ADD);
            reduce(masm, avxSize, JavaKind.Int, vtmp[0], vresult[0], vresult[1]);
            reduce(masm, avxSize, JavaKind.Int, vtmp[1], vresult[2], vresult[3]);
            reduce(masm, avxSize, JavaKind.Int, vresult[0], vtmp[0], vtmp[1]);
            reduceI(masm, elementsPerVector, result, result, vresult[0], vtmp[2], vtmp[3], false);
        }
        // } else if (cnt1 < elementsPerLoop) {

        masm.bind(labelShortUnrolledBegin);
        // int i = 1;
        masm.movl(index, 1);
        masm.cmplAndJcc(index, cnt1, ConditionFlag.GreaterEqual, labelShortUnrolledLoopExit, false);

        // for (; i < cnt1 ; i += 2) {
        masm.bind(labelShortUnrolledLoopBegin);
        masm.imull(result, result, 961);
        arraysHashcodeElload(masm, tmp2, new AMD64Address(ary1, index, stride, -elsize), arrayKind);
        masm.movl(tmp3, tmp2);
        masm.shll(tmp3, 5);
        masm.subl(tmp3, tmp2);
        masm.addl(result, tmp3);
        arraysHashcodeElload(masm, tmp3, new AMD64Address(ary1, index, stride), arrayKind);
        masm.addl(result, tmp3);
        masm.addl(index, 2);
        masm.cmplAndJcc(index, cnt1, ConditionFlag.Less, labelShortUnrolledLoopBegin, false);

        // }
        // if (i >= cnt1) {
        masm.bind(labelShortUnrolledLoopExit);
        masm.jccb(ConditionFlag.Greater, labelEnd);
        masm.movl(tmp2, result);
        masm.shll(result, 5);
        masm.subl(result, tmp2);
        arraysHashcodeElload(masm, tmp3, new AMD64Address(ary1, index, stride, -elsize), arrayKind);
        masm.addl(result, tmp3);
        // }
        masm.bind(labelEnd);
    }
}
