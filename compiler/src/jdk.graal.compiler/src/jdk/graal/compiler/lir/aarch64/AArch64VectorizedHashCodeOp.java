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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDInstruction;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
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

    @Temp({OperandFlag.REG}) Value[] temp;
    @Temp({OperandFlag.REG}) Value[] vectorTemp;

    public AArch64VectorizedHashCodeOp(LIRGeneratorTool tool,
                    AllocatableValue result, AllocatableValue arrayStart, AllocatableValue length, AllocatableValue initialValue, JavaKind arrayKind) {
        super(TYPE);
        this.resultValue = result;
        this.arrayStart = arrayStart;
        this.length = length;
        this.initialValue = initialValue;
        this.arrayKind = arrayKind;

        this.temp = allocateTempRegisters(tool, 5);
        this.vectorTemp = allocateVectorRegisters(tool, 13);
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
    private static final ArrayDataPointerConstant powersOf31 = new ArrayDataPointerConstant(POWERS_OF_31_BACKWARDS, 16);

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
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

        masm.mov(64, ary1, asRegister(arrayStart));
        masm.mov(32, cnt1, asRegister(length));
        masm.mov(32, result, asRegister(initialValue));

        // For "renaming" for readability of the code
        Register vnext = asRegister(vectorTemp[0]);
        Register[] vcoef = {asRegister(vectorTemp[1]), asRegister(vectorTemp[2]), asRegister(vectorTemp[3]), asRegister(vectorTemp[4])};
        Register[] vresult = {asRegister(vectorTemp[5]), asRegister(vectorTemp[6]), asRegister(vectorTemp[7]), asRegister(vectorTemp[8])};
        Register[] vtmp = {asRegister(vectorTemp[9]), asRegister(vectorTemp[10]), asRegister(vectorTemp[11]), asRegister(vectorTemp[12])};

        final boolean unsigned = arrayKind == JavaKind.Boolean || arrayKind == JavaKind.Char;
        final ElementSize elSize = switch (arrayKind) {
            case Boolean, Byte -> ElementSize.Byte;
            case Char, Short -> ElementSize.HalfWord;
            case Int -> ElementSize.Word;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(arrayKind);
        };

        final int nRegs = vresult.length;
        assert nRegs >= 2 && CodeUtil.isPowerOf2(nRegs) : "number of vectors must be >= 2 and a power of 2";
        final int elementsPerVector = ASIMDSize.FullReg.bytes() / ElementSize.Word.bytes();
        final int elementsPerIteration = elementsPerVector * nRegs;

        // @formatter:off
        // elementsPerIteration = 16;
        // if (cnt1 >= elementsPerIteration) {
        //   UNROLLED VECTOR LOOP
        // }
        // if (cnt1 >= 2) {
        //   UNROLLED SCALAR LOOP
        // }
        // SINGLE SCALAR
        // @formatter:on

        // if (cnt1 >= elementsPerIteration) && generate_vectorized_loop
        masm.compare(32, cnt1, elementsPerIteration);
        masm.branchConditionally(ConditionFlag.LT, labelShortUnrolledBegin);

        // index = 0;
        masm.mov(index, 0);
        // vresult[i] = IntVector.zero(I128);
        for (int idx = 0; idx < nRegs; idx++) {
            masm.neon.moviVI(ASIMDSize.FullReg, vresult[idx], 0);
        }

        Register bound = tmp2;
        Register next = tmp3;

        // vnext = IntVector.broadcast(I128, power_of_31_backwards[0]);
        boolean useConstant = true;
        if (useConstant) {
            int nextValue = POWERS_OF_31_BACKWARDS[POWERS_OF_31_BACKWARDS.length - elementsPerIteration - 1];
            masm.mov(next, nextValue);
        } else {
            crb.recordDataReferenceInCode(powersOf31);
            masm.adrpAdd(next);
            int nextOffset = (POWERS_OF_31_BACKWARDS.length - elementsPerIteration - 1) * JavaKind.Int.getByteCount();
            if (nextOffset != 0) {
                masm.add(64, next, next, nextOffset);
            }
            masm.ldr(32, next, AArch64Address.createBaseRegisterOnlyAddress(32, next));
        }
        masm.neon.dupVG(ASIMDSize.FullReg, ElementSize.Word, vnext, next);

        // bound = cnt1 & ~(elementsPerIteration - 1);
        masm.and(32, bound, cnt1, ~(elementsPerIteration - 1));

        // for (; index < bound; index += elementsPerIteration) {
        masm.bind(labelUnrolledVectorLoopBegin);
        // result *= next;
        masm.mul(32, result, result, next);

        // loop fission to upfront the cost of fetching from memory,
        // OOO execution can then hopefully do a better job of prefetching
        // load next 16 elements into 4 data vector registers
        // vtmp = ary1[index:index+elementsPerIteration];
        try (var scratch1 = masm.getScratchRegister()) {
            var rscratch1 = scratch1.getRegister();
            // load next chunk address into scratch reg
            AArch64Address dataChunkStart = AArch64Address.createRegisterOffsetAddress(elSize.bits(), ary1, index, true);
            masm.loadAddress(rscratch1, dataChunkStart);

            ASIMDSize loadVecSize = elSize == ElementSize.Byte || elSize == ElementSize.HalfWord ? ASIMDSize.HalfReg : ASIMDSize.FullReg;
            int consecutiveRegs = elSize == ElementSize.Byte ? 2 : 4;
            boolean useMultiRegisterLoad = elSize == ElementSize.Byte && allConsecutiveSIMDRegisters(vtmp) && nRegs >= 4;
            if (useMultiRegisterLoad) {
                int regsFilledPerLoad = 4;
                for (int ldVi = 0; ldVi < nRegs; ldVi += regsFilledPerLoad) {
                    // load vector size chunk of memory into vtmp, and optionally increment address
                    if (consecutiveRegs == 2) {
                        assert elSize == ElementSize.Byte : elSize;
                        AArch64Address indexedAddr = nRegs == regsFilledPerLoad
                                        ? AArch64Address.createStructureNoOffsetAddress(rscratch1)
                                        : AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_2R,
                                                        loadVecSize, elSize, rscratch1, loadVecSize.bytes() * consecutiveRegs);
                        masm.neon.ld1MultipleVV(loadVecSize, elSize, vtmp[ldVi], vtmp[ldVi + 1], indexedAddr);
                        // extend byte to halfword
                        xtlVV(masm, unsigned, elSize, vtmp[ldVi + 1], vtmp[ldVi + 1]);
                        xtlVV(masm, unsigned, elSize, vtmp[ldVi], vtmp[ldVi]);
                        // extend halfword to word
                        xtl2VV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi + 3], vtmp[ldVi + 1]);
                        xtlVV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi + 2], vtmp[ldVi + 1]);
                        xtl2VV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi + 1], vtmp[ldVi]);
                        xtlVV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi], vtmp[ldVi]);
                    } else {
                        assert consecutiveRegs == 4 : consecutiveRegs;
                        assert elSize == ElementSize.HalfWord || elSize == ElementSize.Word : elSize;
                        AArch64Address indexedAddr = nRegs == regsFilledPerLoad
                                        ? AArch64Address.createStructureNoOffsetAddress(rscratch1)
                                        : AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_4R,
                                                        loadVecSize, elSize, rscratch1, loadVecSize.bytes() * consecutiveRegs);
                        masm.neon.ld1MultipleVVVV(loadVecSize, elSize, vtmp[ldVi], vtmp[ldVi + 1], vtmp[ldVi + 2], vtmp[ldVi + 3], indexedAddr);
                        if (elSize == ElementSize.HalfWord) {
                            for (int i = 0; i < consecutiveRegs; i++) {
                                // extend halfword to word
                                xtlVV(masm, unsigned, elSize, vtmp[ldVi + i], vtmp[ldVi + i]);
                            }
                        }
                    }
                }
            } else {
                int regsFilledPerLoad = loadVecSize.bytes() / ElementSize.Word.bytes() / elSize.bytes();
                AArch64Address indexedAddr = nRegs == regsFilledPerLoad
                                ? AArch64Address.createStructureNoOffsetAddress(rscratch1)
                                : AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_1R,
                                                loadVecSize, elSize, rscratch1, loadVecSize.bytes());
                // load vector size chunk of memory into vtmp, and optionally increment address
                for (int ldVi = 0; ldVi < nRegs; ldVi += regsFilledPerLoad) {
                    masm.neon.ld1MultipleV(loadVecSize, elSize, vtmp[ldVi], indexedAddr);
                }
                // expand (i.e. zero- or sign-extend) vector elements to int size
                if (elSize == ElementSize.Byte || elSize == ElementSize.HalfWord) {
                    for (int ldVi = 0; ldVi < nRegs; ldVi += regsFilledPerLoad) {
                        if (loadVecSize == ASIMDSize.HalfReg) {
                            xtlVV(masm, unsigned, elSize, vtmp[ldVi], vtmp[ldVi]);
                        } else {
                            xtl2VV(masm, unsigned, elSize, vtmp[ldVi + 1], vtmp[ldVi]);
                            xtlVV(masm, unsigned, elSize, vtmp[ldVi], vtmp[ldVi]);
                        }
                    }
                    if (elSize == ElementSize.Byte) {
                        for (int ldVi = 0; ldVi < nRegs; ldVi += regsFilledPerLoad) {
                            if (loadVecSize == ASIMDSize.HalfReg) {
                                assert regsFilledPerLoad == 2 : regsFilledPerLoad;
                                xtl2VV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi + 1], vtmp[ldVi]);
                                xtlVV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi], vtmp[ldVi]);
                            } else {
                                assert regsFilledPerLoad == 4 : regsFilledPerLoad;
                                xtl2VV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi + 3], vtmp[ldVi + 1]);
                                xtlVV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi + 2], vtmp[ldVi + 1]);
                                xtl2VV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi + 1], vtmp[ldVi]);
                                xtlVV(masm, unsigned, ElementSize.HalfWord, vtmp[ldVi], vtmp[ldVi]);
                            }
                        }
                    }
                }
            }
        }

        // vresult[i] = vresult[i] * vnext + vtmp[i];
        for (int idx = 0; idx < nRegs; idx++) {
            // masm.neon.mulVVV(FullReg, ElementSize.Word, vresult[idx], vresult[idx], vnext);
            // masm.neon.addVVV(FullReg, ElementSize.Word, vresult[idx], vresult[idx], vtmp[idx]);
            masm.neon.mlaVVV(ASIMDSize.FullReg, ElementSize.Word, vtmp[idx], vresult[idx], vnext);
            masm.neon.moveVV(ASIMDSize.FullReg, vresult[idx], vtmp[idx]);
        }

        // increment index by the number of elements read in this iteration
        masm.add(32, index, index, elementsPerIteration);

        // index < bound;
        masm.cmp(32, index, bound);
        masm.branchConditionally(ConditionFlag.LT, labelUnrolledVectorLoopBegin);
        // }

        // start = ary[bound];
        // cnt1 -= bound;
        masm.loadAddress(ary1, AArch64Address.createRegisterOffsetAddress(elSize.bits(), ary1, bound, true));
        masm.sub(32, cnt1, cnt1, bound);
        // release bound

        // vcoef = IntVector.fromArray(I128, power_of_31_backwards, 1);
        crb.recordDataReferenceInCode(powersOf31);
        Register coefAddrReg = tmp2;
        masm.adrpAdd(coefAddrReg);
        int coefOffset = (POWERS_OF_31_BACKWARDS.length - elementsPerIteration) * JavaKind.Int.getByteCount();
        assert coefOffset >= 0 : coefOffset;
        if (coefOffset != 0) {
            masm.add(64, coefAddrReg, coefAddrReg, coefOffset);
        }
        if (allConsecutiveSIMDRegisters(vcoef) && vcoef.length == 4) {
            AArch64Address coefAddr = AArch64Address.createStructureNoOffsetAddress(tmp2);
            masm.neon.ld1MultipleVVVV(ASIMDSize.FullReg, ElementSize.Word, vcoef[0], vcoef[1], vcoef[2], vcoef[3], coefAddr);
        } else {
            AArch64Address coefAddrPostIndex = AArch64Address.createStructureImmediatePostIndexAddress(ASIMDInstruction.LD1_MULTIPLE_1R,
                            ASIMDSize.FullReg, ElementSize.Word, coefAddrReg, ASIMDSize.FullReg.bytes());
            for (int idx = 0; idx < nRegs; idx++) {
                masm.neon.ld1MultipleV(ASIMDSize.FullReg, ElementSize.Word, vcoef[idx], coefAddrPostIndex);
            }
        }

        // vresult *= vcoef;
        for (int idx = 0; idx < nRegs; idx++) {
            masm.neon.mulVVV(ASIMDSize.FullReg, ElementSize.Word, vresult[idx], vresult[idx], vcoef[idx]);
        }

        reduceVectorLanes(masm, ASIMDSize.FullReg, vresult);

        // accumulate horizontal vector sum in result
        masm.neon.umovGX(ElementSize.Word, tmp2, vresult[0], 0);
        masm.add(32, result, result, tmp2);

        // } else if (cnt1 < elementsPerIteration) {

        masm.bind(labelShortUnrolledBegin);
        // int i = 1;
        masm.mov(index, 1);

        AArch64Address postIndexAddr = AArch64Address.createImmediateAddress(elSize.bits(), AddressingMode.IMMEDIATE_POST_INDEXED, ary1, elSize.bytes());

        masm.cmp(32, index, cnt1);
        masm.branchConditionally(ConditionFlag.GE, labelShortUnrolledLoopExit);

        // for (; i < cnt1 ; i += 2) {
        masm.bind(labelShortUnrolledLoopBegin);
        // result *= 31**2;
        masm.mov(tmp3, 961);
        masm.mul(32, result, result, tmp3);

        // result += ary1[index-1] * 31
        arraysHashcodeElload(masm, tmp2, postIndexAddr, arrayKind);
        masm.mov(32, tmp3, tmp2);
        masm.lsl(32, tmp3, tmp3, 5);
        masm.sub(32, tmp3, tmp3, tmp2);
        masm.add(32, result, result, tmp3);

        // result += ary1[index]
        arraysHashcodeElload(masm, tmp3, postIndexAddr, arrayKind);
        masm.add(32, result, result, tmp3);
        // i += 2
        masm.add(32, index, index, 2);

        masm.cmp(32, index, cnt1);
        masm.branchConditionally(ConditionFlag.LT, labelShortUnrolledLoopBegin);

        // }
        // if (i >= cnt1) {
        masm.bind(labelShortUnrolledLoopExit);
        // masm.cmp(32, index, cnt1); // already compared above
        masm.branchConditionally(ConditionFlag.GT, labelEnd);
        // result *= 31;
        masm.mov(32, tmp2, result);
        masm.lsl(32, result, result, 5);
        masm.sub(32, result, result, tmp2);

        // result += ary1[index - 1]
        arraysHashcodeElload(masm, tmp3, postIndexAddr, arrayKind);
        masm.add(32, result, result, tmp3);
        // }
        masm.bind(labelEnd);
    }

    /**
     * Reduces elements from multiple vectors to a single vector and then reduces that vector's
     * lanes to a single scalar value in {@code vresult[0]}.
     */
    private static void reduceVectorLanes(AArch64MacroAssembler masm, ASIMDSize vsize, Register[] vresult) {
        // reduce vectors pairwise until there's only a single vector left
        // e.g. vresult = vresult[0].add(vresult[1]).add(vresult[2]).add(vresult[3]);
        for (int nRegs = vresult.length, stride = 1; nRegs >= 2; nRegs /= 2, stride *= 2) {
            for (int i = 0; i < vresult.length - stride; i += 2 * stride) {
                masm.neon.addVVV(vsize, ElementSize.Word, vresult[i], vresult[i], vresult[i + stride]);
            }
            if (nRegs % 2 != 0) {
                masm.neon.addVVV(vsize, ElementSize.Word, vresult[0], vresult[0], vresult[(nRegs - 1) * stride]);
            }
        }
        // result = vresult.reduceLanes(ADD);
        masm.neon.addvSV(vsize, ElementSize.Word, vresult[0], vresult[0]);
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
