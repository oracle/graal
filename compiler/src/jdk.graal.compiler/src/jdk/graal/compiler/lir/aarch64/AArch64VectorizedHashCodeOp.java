/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import java.util.Arrays;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * @see jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode
 * @see jdk.graal.compiler.lir.amd64.AMD64VectorizedHashCodeOp
 */
@Opcode("VECTORIZED_HASHCODE")
public final class AArch64VectorizedHashCodeOp extends AArch64ComplexVectorOp {

    public static final LIRInstructionClass<AArch64VectorizedHashCodeOp> TYPE = LIRInstructionClass.create(AArch64VectorizedHashCodeOp.class);

    @Def({OperandFlag.REG}) private Value resultValue;
    @Alive({OperandFlag.REG}) private Value arrayStart;
    @Alive({OperandFlag.REG}) private Value length;
    @Alive({OperandFlag.REG}) private Value initialValue;

    private final JavaKind arrayKind;
    private final int nRegs;

    @Temp({OperandFlag.REG}) Value[] temp;
    @Temp({OperandFlag.REG}) Value[] vectorTemp;

    /** Number of vector registers to use per loop iteration (2, 4, or 8). */
    private static final int VECTOR_COUNT = 4;

    public AArch64VectorizedHashCodeOp(LIRGeneratorTool tool,
                    AllocatableValue result, AllocatableValue arrayStart, AllocatableValue length, AllocatableValue initialValue, JavaKind arrayKind) {
        super(TYPE);
        this.resultValue = result;
        this.arrayStart = arrayStart;
        this.length = length;
        this.initialValue = initialValue;
        this.arrayKind = arrayKind;

        this.nRegs = VECTOR_COUNT;
        this.temp = allocateTempRegisters(tool, 5);
        // (1 * vnext + n * vresult + n * vtmp/vcoef)
        this.vectorTemp = allocateVectorRegisters(tool, 1 + 2 * nRegs);
    }

    private static Register[] asRegisterSlice(Value[] registerValues, int start, int end) {
        Register[] regs = new Register[end - start];
        for (int fromIndex = start, toIndex = 0; fromIndex < end; fromIndex++, toIndex++) {
            regs[toIndex] = asRegister(registerValues[fromIndex]);
        }
        return regs;
    }

    private static void arraysHashcodeElload(AArch64MacroAssembler masm, Register dst, AArch64Address src, JavaKind eltype) {
        switch (eltype) {
            case Boolean -> masm.ldr(8, dst, src);
            case Byte -> masm.ldrs(32, 8, dst, src);
            case Short -> masm.ldrs(32, 16, dst, src);
            case Char -> masm.ldr(16, dst, src);
            case Int -> masm.ldr(32, dst, src);
            default -> throw GraalError.shouldNotReachHere("Unsupported JavaKind " + eltype);
        }
    }

    private static final int[] POWERS_OF_31_BACKWARDS = new int[]{
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

    /**
     * Emits an unrolled x16 vector loop + an unrolled x2 scalar loop + a final single scalar case
     * to compute the array or string hash code.
     *
     * Roughly equivalent to the following vector pseudo code:
     *
     * <pre>
     * final var I128 = VectorSpecies.of(int.class, VectorShape.S_128_BIT);
     * final int vlen = vspecies.length();
     * final int N = 4 * vlen;
     * final int[] POW31BW = new int[N]{31**(N-1), ..., 31**1, 31**0};
     * assert N == 16;
     *
     * int result = initialValue;
     * int bound = data.length &amp; ~(N - 1);
     * // UNROLLED (x16) VECTOR LOOP
     * if (data.length >= N) {
     *     var vresult1 = IntVector.zero(I128);
     *     var vresult2 = IntVector.zero(I128);
     *     var vresult3 = IntVector.zero(I128);
     *     var vresult4 = IntVector.zero(I128);
     *     int inext = 31 ** N;
     *     var vnext = IntVector.broadcast(I128, inext);
     *     for (int i = 0; i &lt; bound; i += N) {
     *         result *= inext;
     *         // load 4x4 zero-extended-to-32-bit int values
     *         var vtmp1 = IntVector.fromArray(I128, data, i + 0 * vlen);
     *         var vtmp2 = IntVector.fromArray(I128, data, i + 1 * vlen);
     *         var vtmp3 = IntVector.fromArray(I128, data, i + 2 * vlen);
     *         var vtmp4 = IntVector.fromArray(I128, data, i + 3 * vlen);
     *         vresult1 = vresult1.mul(vnext).add(vtmp1);
     *         vresult2 = vresult2.mul(vnext).add(vtmp2);
     *         vresult3 = vresult3.mul(vnext).add(vtmp3);
     *         vresult4 = vresult4.mul(vnext).add(vtmp4);
     *     }
     *     vresult1 = vresult1.mul(IntVector.fromArray(I128, POW31BW, 0 * vlen));
     *     vresult2 = vresult2.mul(IntVector.fromArray(I128, POW31BW, 1 * vlen));
     *     vresult3 = vresult3.mul(IntVector.fromArray(I128, POW31BW, 2 * vlen));
     *     vresult4 = vresult4.mul(IntVector.fromArray(I128, POW31BW, 3 * vlen));
     *     var vresult = vresult1.add(vresult2).add(vresult3).add(vresult4);
     *     result += vresult.reduceLanes(ADD);
     * }
     * // UNROLLED (x2) SCALAR LOOP
     * int i = 1;
     * for (; i &lt; data.length - bound; i += 2) {
     *     result *= 31 * 31;
     *     result += data[i - 1] * 31 + data[i];
     * }
     * // LAST REMAINING SCALAR (if length is odd)
     * if (i == data.length - bound) {
     *     result *= 31;
     *     result += data[i - 1];
     * }
     * return result;
     * </pre>
     */
    @SuppressWarnings("unused") // unused block labels
    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Label labelShortUnrolledBegin = new Label();
        Label labelEnd = new Label();

        // Parameters (immutable, alive)
        Register lengthParam = asRegister(length);
        Register arrayStartParam = asRegister(arrayStart);
        Register initialValueParam = asRegister(initialValue);
        // Result (def) and temp registers
        Register result = asRegister(resultValue);
        Register ary1 = asRegister(temp[0]);
        Register cnt1 = asRegister(temp[1]);
        Register tmp2 = asRegister(temp[2]);
        Register tmp3 = asRegister(temp[3]);
        Register index = asRegister(temp[4]);

        masm.mov(64, ary1, arrayStartParam);
        masm.mov(32, cnt1, lengthParam);
        masm.mov(32, result, initialValueParam);

        Register vnext = asRegister(vectorTemp[0]);
        Register[] vcoef = asRegisterSlice(vectorTemp, 1, 1 + nRegs);
        Register[] vresult = asRegisterSlice(vectorTemp, 1 + nRegs, 1 + 2 * nRegs);
        Register[] vtmp = vcoef;

        final boolean unsigned = arrayKind == JavaKind.Boolean || arrayKind == JavaKind.Char;
        final ElementSize elSize = switch (arrayKind) {
            case Boolean, Byte -> ElementSize.Byte;
            case Char, Short -> ElementSize.HalfWord;
            case Int -> ElementSize.Word;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(arrayKind);
        };
        final Stride stride = Stride.fromJavaKind(arrayKind);

        GraalError.guarantee(nRegs == 2 || nRegs == 4 || nRegs == 8, "number of vectors must be either 2, 4, or 8");
        final int elementsPerVector = ASIMDSize.FullReg.bytes() / ElementSize.Word.bytes();
        final int elementsPerIteration = elementsPerVector * nRegs;
        // We can load up to 4 registers at once.
        final int maxConsecutiveRegs = Math.min(nRegs, 4);
        final int minBits = elSize.bits() * elementsPerVector;
        final int maxBits = Math.min(elSize.bits() * elementsPerIteration, ASIMDSize.FullReg.bits());
        // Sub-word element loads can be spread out to more vector registers.
        // i.e. byte: 4x32, 2x64, or 1x128, halfword: 4x64 or 2x128, word: 4x128.
        final ASIMDSize loadVecSize = nRegs * minBits <= ASIMDSize.HalfReg.bits() ? ASIMDSize.HalfReg : ASIMDSize.FullReg;
        final int loadVecBits = loadVecSize.bits();
        GraalError.guarantee(CodeUtil.isPowerOf2(loadVecBits) && loadVecBits % minBits == 0 && loadVecBits <= maxBits,
                        "loaded bit width must be (2^n)*%d <= %d for %s", minBits, maxBits, elSize);

        // to how many eventual registers do we expand each loaded register?
        int extensionFactor = Math.max((ElementSize.Word.bits() / elSize.bits()) / (maxBits / loadVecBits), 1);
        // number of <loadVecSize> registers needed to load to fill 4 full vectors.
        // i.e. byte: 1 full or 2 half, halfword: 2 full or 4 half, word: 4 full.
        int consecutiveRegs = Math.max(maxConsecutiveRegs / extensionFactor, 1);
        int regsFilledPerLoad = consecutiveRegs * extensionFactor;

        Register bound = tmp2;
        // bound = length & ~(N - 1);
        masm.ands(32, bound, cnt1, ~(elementsPerIteration - 1));
        // if (bound != 0) { // (EQ = Z flag set)
        masm.branchConditionally(ConditionFlag.EQ, labelShortUnrolledBegin);
        vectorBranch: {
            // vresult[i] = IntVector.zero(I128);
            for (int idx = 0; idx < nRegs; idx++) {
                masm.neon.moviVI(ASIMDSize.FullReg, vresult[idx], 0);
            }

            // vnext = IntVector.broadcast(I128, power_of_31_backwards[0]);
            // Throws AIOOBE if there are not enough elements in the array but allows more.
            int powersOf31Start = POWERS_OF_31_BACKWARDS.length - elementsPerIteration;
            int nextPow31 = POWERS_OF_31_BACKWARDS[powersOf31Start - 1];
            Register next = tmp3;
            masm.mov(next, nextPow31);
            masm.neon.dupVG(ASIMDSize.FullReg, ElementSize.Word, vnext, next);

            // cnt1 -= bound;
            masm.sub(32, cnt1, cnt1, bound);
            // bound = arrayStart + bound * elSize.bytes();
            masm.add(64, bound, ary1, bound, ShiftType.LSL, stride.log2);

            vectorLoop: { // for (; index |<| (length & ~(N - 1)); index += N) {
                Label labelUnrolledVectorLoopBegin = new Label();
                masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
                masm.bind(labelUnrolledVectorLoopBegin);

                // result *= next;
                masm.mul(32, result, result, next);

                /*
                 * Load the next 16 data elements, zero-extended to 32-bit, into 4 vector registers.
                 * By grouping the loads and fetching from memory up front (loop fission), we can
                 * combine multiple loads into one instruction, and out-of-order execution can
                 * hopefully do a better job of prefetching, while also allowing subsequent
                 * instructions to be executed while data are still being fetched.
                 */
                // vtmp = ary1[index:index+elementsPerIteration];
                for (int ldVi = 0; ldVi < nRegs; ldVi += regsFilledPerLoad) {
                    boolean postIndex = true;
                    loadConsecutiveVectors(masm, loadVecSize, loadVecBits, elSize, ary1, vtmp, ldVi, consecutiveRegs, postIndex, false);
                    extendVectorsToWord(masm, unsigned, loadVecBits, elSize, vtmp, ldVi, consecutiveRegs);
                }

                // vresult[i] = vresult[i] * vnext + vtmp[i];
                // (desugared to: vtmp[i] += vresult[i] * vnext, vresult[i] = vtmp[i])
                for (int idx = 0; idx < nRegs; idx++) {
                    masm.neon.mlaVVV(ASIMDSize.FullReg, ElementSize.Word, vtmp[idx], vresult[idx], vnext);
                }
                for (int idx = 0; idx < nRegs; idx++) {
                    masm.neon.moveVV(ASIMDSize.FullReg, vresult[idx], vtmp[idx]);
                }

                // if (ary1 |<| bound) continue; else break;
                masm.cmp(64, ary1, bound);
                masm.branchConditionally(ConditionFlag.LO, labelUnrolledVectorLoopBegin);
            }
            // release bound/tmp2

            // vcoef = IntVector.fromArray(I128, power_of_31_backwards, 1);
            var powersOf31 = new ArrayDataPointerConstant(Arrays.copyOfRange(POWERS_OF_31_BACKWARDS, powersOf31Start, POWERS_OF_31_BACKWARDS.length), 16);
            crb.recordDataReferenceInCode(powersOf31);
            Register coefAddrReg = tmp2;
            masm.adrpAdd(coefAddrReg);
            for (int i = 0; i < nRegs; i += maxConsecutiveRegs) {
                boolean postIndex = maxConsecutiveRegs < nRegs;
                loadConsecutiveVectors(masm, ASIMDSize.FullReg, ASIMDSize.FullReg.bits(), ElementSize.Word, coefAddrReg, vcoef, i, maxConsecutiveRegs, postIndex, false);
            }

            // fused multiply (vresult[i] * vcoef[i]) and vertical add reduction
            // vresult[0] = vresult[0] * vcoef[0];
            // vresult[0] += vresult[i] * vcoef[i] for i > 0;
            for (int i = 0; i < nRegs; i++) {
                if (i == 0) {
                    masm.neon.mulVVV(ASIMDSize.FullReg, ElementSize.Word, vresult[0], vresult[i], vcoef[i]);
                } else {
                    masm.neon.mlaVVV(ASIMDSize.FullReg, ElementSize.Word, vresult[0], vresult[i], vcoef[i]);
                }
            }

            // result += vresult[0].reduceLanes(ADD); (horizontal lane-wise add reduction)
            masm.neon.addvSV(ASIMDSize.FullReg, ElementSize.Word, vresult[0], vresult[0]);
            masm.fmov(32, tmp2, vresult[0]); // umovGX(Word, tmp2, vresult[0], 0);
            masm.add(32, result, result, tmp2);
        }

        masm.align(AArch64MacroAssembler.PREFERRED_BRANCH_TARGET_ALIGNMENT);
        masm.bind(labelShortUnrolledBegin);
        shortUnrolledBranch: {
            Label labelShortUnrolledLoopExit = new Label();
            AArch64Address postIndexAddr = AArch64Address.createImmediateAddress(elSize.bits(), AddressingMode.IMMEDIATE_POST_INDEXED, ary1, elSize.bytes());

            // int index = 1;
            masm.mov(index, 1);
            masm.cmp(32, index, cnt1);
            masm.branchConditionally(ConditionFlag.HS, labelShortUnrolledLoopExit);
            try (var scratch1 = masm.getScratchRegister(); var scratch2 = masm.getScratchRegister()) {
                var tmp31 = scratch1.getRegister();
                var tmp961 = scratch2.getRegister();
                masm.mov(tmp31, 31);
                masm.mov(tmp961, 961);

                shortUnrolledLoop: { // for (; index < cnt1; index += 2) {
                    Label labelShortUnrolledLoopBegin = new Label();
                    masm.align(AArch64MacroAssembler.PREFERRED_LOOP_ALIGNMENT);
                    masm.bind(labelShortUnrolledLoopBegin);

                    // result *= 31**2;
                    // result += ary1[index-1] * 31 + ary1[index]
                    arraysHashcodeElload(masm, tmp2, postIndexAddr, arrayKind); // index-1
                    arraysHashcodeElload(masm, tmp3, postIndexAddr, arrayKind); // index
                    masm.madd(32, tmp3, tmp2, tmp31, tmp3);
                    masm.madd(32, result, result, tmp961, tmp3);
                    // i += 2
                    masm.add(32, index, index, 2);

                    masm.cmp(32, index, cnt1);
                    masm.branchConditionally(ConditionFlag.LO, labelShortUnrolledLoopBegin);
                }

                // if (index >= cnt1) {
                masm.bind(labelShortUnrolledLoopExit);
                // already compared right above and before the jump here, no need to repeat:
                // masm.cmp(32, index, cnt1);
                masm.branchConditionally(ConditionFlag.HI, labelEnd);
                lastElementBranch: { // if (index == cnt1) {
                    // result *= 31;
                    // result += ary1[index - 1]
                    masm.mov(tmp31, 31);
                    arraysHashcodeElload(masm, tmp3, postIndexAddr, arrayKind); // index-1
                    masm.madd(32, result, result, tmp31, tmp3);
                }
            }
        }
        masm.bind(labelEnd);
    }

    /**
     * Load consecutive vector registers from a register-based memory address, and optionally
     * increment the address register by the loaded bytes.
     */
    private static void loadConsecutiveVectors(AArch64MacroAssembler masm,
                    ASIMDSize vecSize, int vecBits, ElementSize elSize, Register indexedAddrReg, Register[] vreg,
                    int startReg, int consecutiveRegs, boolean postIndex, boolean preferLd1) {
        assert consecutiveRegs <= 4 : consecutiveRegs;
        switch (consecutiveRegs) {
            case 2 -> {
                if (preferLd1 && allConsecutiveSIMDRegisters(vreg) && vecBits == vecSize.bits()) {
                    masm.neon.ld1MultipleVV(vecSize, elSize, vreg[startReg], vreg[startReg + 1],
                                    addressForLd1M(2, vecSize, elSize, indexedAddrReg, postIndex));
                } else {
                    masm.fldp(vecBits, vreg[startReg], vreg[startReg + 1],
                                    addressForLdp(vecSize, indexedAddrReg, postIndex, 0));
                }
            }
            case 4 -> {
                if (preferLd1 && allConsecutiveSIMDRegisters(vreg) && vecBits == vecSize.bits()) {
                    masm.neon.ld1MultipleVVVV(vecSize, elSize,
                                    vreg[startReg], vreg[startReg + 1], vreg[startReg + 2], vreg[startReg + 3],
                                    addressForLd1M(4, vecSize, elSize, indexedAddrReg, postIndex));
                } else {
                    masm.fldp(vecBits, vreg[startReg], vreg[startReg + 1],
                                    addressForLdp(vecSize, indexedAddrReg, postIndex, 0));
                    masm.fldp(vecBits, vreg[startReg + 2], vreg[startReg + 3],
                                    addressForLdp(vecSize, indexedAddrReg, postIndex, 2));
                }
            }
            default -> {
                boolean useLd1 = preferLd1 && (consecutiveRegs == 1 || postIndex) && vecBits == vecSize.bits();
                for (int i = 0; i < consecutiveRegs; i++) {
                    if (useLd1) {
                        masm.neon.ld1MultipleV(vecSize, elSize, vreg[startReg + i],
                                        addressForLd1M(1, vecSize, elSize, indexedAddrReg, postIndex));
                    } else {
                        masm.fldr(vecBits, vreg[startReg + i],
                                        addressForLdr(vecSize, indexedAddrReg, postIndex, i));
                    }
                }
            }
        }
    }

    private static AArch64Address addressForLdr(ASIMDSize vecSize, Register indexedAddrReg, boolean postIndex, int offsetScaled) {
        int scale = vecSize.bytes();
        if (postIndex) {
            return AArch64Address.createImmediateAddress(vecSize.bits(), AddressingMode.IMMEDIATE_POST_INDEXED, indexedAddrReg, scale);
        } else {
            return AArch64Address.createImmediateAddress(vecSize.bits(), AddressingMode.IMMEDIATE_UNSIGNED_SCALED, indexedAddrReg, offsetScaled * scale);
        }
    }

    private static AArch64Address addressForLdp(ASIMDSize vecSize, Register indexedAddrReg, boolean postIndex, int offsetScaled) {
        int scale = vecSize.bytes();
        if (postIndex) {
            return AArch64Address.createImmediateAddress(vecSize.bits(), AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, indexedAddrReg, 2 * scale);
        } else {
            return AArch64Address.createImmediateAddress(vecSize.bits(), AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED, indexedAddrReg, offsetScaled * scale);
        }
    }

    private static AArch64Address addressForLd1M(int loadedVectors, ASIMDSize vecSize, ElementSize elSize, Register indexedAddrReg, boolean postIndex) {
        if (postIndex) {
            var ld1Instruction = switch (loadedVectors) {
                case 1 -> ASIMDInstruction.LD1_MULTIPLE_1R;
                case 2 -> ASIMDInstruction.LD1_MULTIPLE_2R;
                case 3 -> ASIMDInstruction.LD1_MULTIPLE_3R;
                case 4 -> ASIMDInstruction.LD1_MULTIPLE_4R;
                default -> throw GraalError.shouldNotReachHereUnexpectedValue(loadedVectors);
            };
            int incrementUnscaled = vecSize.bytes() * loadedVectors;
            return AArch64Address.createStructureImmediatePostIndexAddress(ld1Instruction, vecSize, elSize, indexedAddrReg, incrementUnscaled);
        } else {
            return AArch64Address.createStructureNoOffsetAddress(indexedAddrReg);
        }
    }

    /**
     * Zero-or-sign-extend byte-or-halfword vector registers to word-size vectors.
     */
    private static void extendVectorsToWord(AArch64MacroAssembler masm, boolean unsigned, int startVecBits, ElementSize startElSize, Register[] vtmp, int start, int srcRegsInitial) {
        int vecBits = startVecBits;
        ElementSize srcElSize = startElSize;
        ElementSize endElSize = ElementSize.Word;
        int srcRegs = srcRegsInitial;
        while (srcElSize.bits() < endElSize.bits()) {
            if (vecBits < ASIMDSize.FullReg.bits()) {
                extendSameRegs(masm, unsigned, srcElSize, vtmp, start, srcRegs);
                vecBits *= 2;
            } else {
                extendPairwise(masm, unsigned, srcElSize, vtmp, start, srcRegs);
                srcRegs *= 2;
            }
            srcElSize = srcElSize.expand();
        }
    }

    /**
     * Zero-or-sign-extend byte-to-halfword or halfword-to-word within the same register.
     */
    private static void extendSameRegs(AArch64MacroAssembler masm, boolean unsigned, ElementSize srcElSize, Register[] vtmp, int start, int srcRegs) {
        for (int i = start; i < start + srcRegs; i++) {
            xtlVV(masm, unsigned, srcElSize, vtmp[i], vtmp[i]);
        }
    }

    /**
     * Zero-or-sign-extend 1 to 2 or 2 to 4 vectors (byte-to-halfword or halfword-to-word).
     *
     * Note that the registers need to be written in the right order to not overwrite any source
     * register before we've extended all its elements.
     */
    private static void extendPairwise(AArch64MacroAssembler masm, boolean unsigned, ElementSize srcElSize, Register[] vtmp, int start, int srcRegs) {
        int dstRegs = srcRegs * 2;
        for (int srci = start + srcRegs - 1, dsti = start + dstRegs - 1; srci >= start; srci -= 1, dsti -= 2) {
            xtl2VV(masm, unsigned, srcElSize, vtmp[dsti], vtmp[srci]);
            xtlVV(masm, unsigned, srcElSize, vtmp[dsti - 1], vtmp[srci]);
        }
    }

    private static void xtlVV(AArch64MacroAssembler masm, boolean unsigned, ElementSize elementSize, Register dst, Register src) {
        if (unsigned) {
            masm.neon.uxtlVV(elementSize, dst, src);
        } else {
            masm.neon.sxtlVV(elementSize, dst, src);
        }
    }

    private static void xtl2VV(AArch64MacroAssembler masm, boolean unsigned, ElementSize elementSize, Register dst, Register src) {
        if (unsigned) {
            masm.neon.uxtl2VV(elementSize, dst, src);
        } else {
            masm.neon.sxtl2VV(elementSize, dst, src);
        }
    }

    /**
     * Checks whether all registers follow one another (modulo the number of SIMD registers).
     */
    private static boolean allConsecutiveSIMDRegisters(Register[] regs) {
        int numRegs = AArch64.simdRegisters.size();
        for (int i = 1; i < regs.length; i++) {
            assert regs[i].getRegisterCategory().equals(AArch64.SIMD) : regs[i];
            if (!((regs[i - 1].encoding + 1) % numRegs == regs[i].encoding)) {
                return false;
            }
        }
        return true;
    }
}
