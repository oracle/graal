/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize.FullReg;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.loadExternalAddress;
import static jdk.graal.compiler.lir.aarch64.AArch64LIRHelper.pointerConstant;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.asm.ArrayDataPointerConstant;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk25u/blob/c59e44a7aa2aeff0823830b698d524523b996650/src/hotspot/cpu/aarch64/stubGenerator_aarch64.cpp#L6488-L6694",
          sha1 = "07a854e9c4c95410e68c367d339d5655f6fdc80b")
// @formatter:on
public final class AArch64DilithiumAlmostNttOp extends AArch64LIRInstruction {

    public static final LIRInstructionClass<AArch64DilithiumAlmostNttOp> TYPE = LIRInstructionClass.create(AArch64DilithiumAlmostNttOp.class);

    private static final int DILITHIUM_Q = 8380417;
    private static final int DILITHIUM_Q_INV_MOD_R = 58728449;

    private static final ArrayDataPointerConstant DILITHIUM_CONSTS = pointerConstant(16, new int[]{
                    DILITHIUM_Q_INV_MOD_R, DILITHIUM_Q_INV_MOD_R, DILITHIUM_Q_INV_MOD_R, DILITHIUM_Q_INV_MOD_R,
                    DILITHIUM_Q, DILITHIUM_Q, DILITHIUM_Q, DILITHIUM_Q,
    });

    private static final int[] LEVEL_0_4_OFFSETS = {0, 32, 64, 96};
    private static final int[] LEVEL_5_OFFSETS_1 = {16, 48, 80, 112, 144, 176, 208, 240};
    private static final int[] LEVEL_5_OFFSETS_2 = {0, 32, 64, 96, 128, 160, 192, 224};

    private static final Register[] VS1 = {AArch64.v0, AArch64.v1, AArch64.v2, AArch64.v3, AArch64.v4, AArch64.v5, AArch64.v6, AArch64.v7};
    private static final Register[] VS2 = {AArch64.v16, AArch64.v17, AArch64.v18, AArch64.v19, AArch64.v20, AArch64.v21, AArch64.v22, AArch64.v23};
    private static final Register[] VS3 = {AArch64.v24, AArch64.v25, AArch64.v26, AArch64.v27, AArch64.v28, AArch64.v29, AArch64.v30, AArch64.v31};
    private static final Register[] VS1_EVEN = {AArch64.v0, AArch64.v2, AArch64.v4, AArch64.v6};
    private static final Register[] VS1_ODD = {AArch64.v1, AArch64.v3, AArch64.v5, AArch64.v7};
    private static final Register[] VS2_FRONT = {AArch64.v16, AArch64.v17, AArch64.v18, AArch64.v19};

    @Def({REG}) private Value resultValue;

    @Use({REG}) private Value coeffsValue;
    @Use({REG}) private Value zetasValue;

    @Temp({REG}) private Value[] temps;

    public AArch64DilithiumAlmostNttOp(AllocatableValue resultValue, AllocatableValue coeffsValue, AllocatableValue zetasValue) {
        super(TYPE);
        GraalError.guarantee(asRegister(resultValue).equals(AArch64.r0), "expect resultValue at r0, but was %s", resultValue);
        GraalError.guarantee(asRegister(coeffsValue).equals(AArch64.r0), "expect coeffsValue at r0, but was %s", coeffsValue);
        GraalError.guarantee(asRegister(zetasValue).equals(AArch64.r1), "expect zetasValue at r1, but was %s", zetasValue);
        this.resultValue = resultValue;
        this.coeffsValue = coeffsValue;
        this.zetasValue = zetasValue;

        this.temps = new Value[]{
                        AArch64.r1.asValue(), // zetas is clobbered by post-indexed loads
                        AArch64.r14.asValue(),
                        AArch64.v0.asValue(),
                        AArch64.v1.asValue(),
                        AArch64.v2.asValue(),
                        AArch64.v3.asValue(),
                        AArch64.v4.asValue(),
                        AArch64.v5.asValue(),
                        AArch64.v6.asValue(),
                        AArch64.v7.asValue(),
                        AArch64.v16.asValue(),
                        AArch64.v17.asValue(),
                        AArch64.v18.asValue(),
                        AArch64.v19.asValue(),
                        AArch64.v20.asValue(),
                        AArch64.v21.asValue(),
                        AArch64.v22.asValue(),
                        AArch64.v23.asValue(),
                        AArch64.v24.asValue(),
                        AArch64.v25.asValue(),
                        AArch64.v26.asValue(),
                        AArch64.v27.asValue(),
                        AArch64.v28.asValue(),
                        AArch64.v29.asValue(),
                        AArch64.v30.asValue(),
                        AArch64.v31.asValue(),
        };
    }

    private static void loadVSeq8Post(AArch64MacroAssembler masm, Register[] vseq, Register base) {
        for (int i = 0; i < 8; i += 2) {
            masm.fldp(128, vseq[i], vseq[i + 1], AArch64Address.createImmediateAddress(128, IMMEDIATE_PAIR_POST_INDEXED, base, 32));
        }
    }

    private static void loadVSeq4Post(AArch64MacroAssembler masm, Register[] vseq, Register base) {
        for (int i = 0; i < 4; i += 2) {
            masm.fldp(128, vseq[i], vseq[i + 1], AArch64Address.createImmediateAddress(128, IMMEDIATE_PAIR_POST_INDEXED, base, 32));
        }
    }

    private static void loadVSeq8Indexed(AArch64MacroAssembler masm, Register[] vseq, Register base, int start, int[] offsets) {
        for (int i = 0; i < 4; i++) {
            masm.fldp(128, vseq[2 * i], vseq[2 * i + 1], AArch64Address.createImmediateAddress(128, IMMEDIATE_PAIR_SIGNED_SCALED, base, start + offsets[i]));
        }
    }

    private static void storeVSeq8Indexed(AArch64MacroAssembler masm, Register[] vseq, Register base, int start, int[] offsets) {
        for (int i = 0; i < 4; i++) {
            masm.fstp(128, vseq[2 * i], vseq[2 * i + 1], AArch64Address.createImmediateAddress(128, IMMEDIATE_PAIR_SIGNED_SCALED, base, start + offsets[i]));
        }
    }

    private static void loadVSeq8LdrIndexed(AArch64MacroAssembler masm, Register[] vseq, Register base, int start, int[] offsets) {
        for (int i = 0; i < 8; i++) {
            masm.fldr(128, vseq[i], AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, base, start + offsets[i]));
        }
    }

    private static void storeVSeq8StrIndexed(AArch64MacroAssembler masm, Register[] vseq, Register base, int start, int[] offsets) {
        for (int i = 0; i < 8; i++) {
            masm.fstr(128, vseq[i], AArch64Address.createImmediateAddress(128, IMMEDIATE_UNSIGNED_SCALED, base, start + offsets[i]));
        }
    }

    private static void loadInterleavedVSeq8(AArch64MacroAssembler masm, Register[] vseq, Register base, Register tmpAddr, int start, ElementSize eSize, int[] offsets) {
        for (int i = 0; i < 4; i++) {
            masm.add(64, tmpAddr, base, start + offsets[i]);
            masm.neon.ld2MultipleVV(FullReg, eSize, vseq[2 * i], vseq[2 * i + 1], AArch64Address.createStructureNoOffsetAddress(tmpAddr));
        }
    }

    private static void storeInterleavedVSeq8(AArch64MacroAssembler masm, Register[] vseq, Register base, Register tmpAddr, int start, ElementSize eSize, int[] offsets) {
        for (int i = 0; i < 4; i++) {
            masm.add(64, tmpAddr, base, start + offsets[i]);
            masm.neon.st2MultipleVV(FullReg, eSize, vseq[2 * i], vseq[2 * i + 1], AArch64Address.createStructureNoOffsetAddress(tmpAddr));
        }
    }

    private static void addVSeq8(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register[] right) {
        for (int i = 0; i < 8; i++) {
            masm.neon.addVVV(FullReg, ElementSize.Word, dst[i], left[i], right[i]);
        }
    }

    private static void subVSeq8(AArch64MacroAssembler masm, Register[] dst, Register[] left, Register[] right) {
        for (int i = 0; i < 8; i++) {
            masm.neon.subVVV(FullReg, ElementSize.Word, dst[i], left[i], right[i]);
        }
    }

    private static void emitNttLevel0To4(AArch64MacroAssembler masm,
                    Register coeffs,
                    Register zetas,
                    Register dilithiumConsts) {
        int c1 = 0;
        int c2 = 512;
        int startIncr;
        int[] offsets = LEVEL_0_4_OFFSETS.clone();

        for (int level = 0; level < 5; level++) {
            int c1Start = c1;
            int c2Start = c2;
            if (level == 3) {
                offsets[1] = 32;
                offsets[2] = 128;
                offsets[3] = 160;
            } else if (level == 4) {
                offsets[1] = 64;
                offsets[2] = 128;
                offsets[3] = 192;
            }

            // For levels 1 - 4 we simply load 2 x 4 adjacent values at a
            // time at 4 different offsets and multiply them in order by the
            // next set of input values. So we employ indexed load and store
            // pair instructions with arrangement 4S.
            for (int i = 0; i < 4; i++) {
                // reload q and qinv
                AArch64DilithiumSupport.loadQInvQ(masm, dilithiumConsts);
                // load 8x4S coefficients via second start pos == c2
                loadVSeq8Indexed(masm, VS1, coeffs, c2Start, offsets);
                // load next 8x4S inputs == b
                loadVSeq8Post(masm, VS2, zetas);
                // compute a == c2 * b mod MONT_Q
                AArch64DilithiumSupport.emitMontMul32(masm, VS2, VS1, VS2, AArch64DilithiumSupport.VTMP, AArch64DilithiumSupport.VQ);
                // load 8x4s coefficients via first start pos == c1
                loadVSeq8Indexed(masm, VS1, coeffs, c1Start, offsets);
                // compute a1 = c1 + a
                addVSeq8(masm, VS3, VS1, VS2);
                // compute a2 = c1 - a
                subVSeq8(masm, VS1, VS1, VS2);
                // output a1 and a2
                storeVSeq8Indexed(masm, VS3, coeffs, c1Start, offsets);
                storeVSeq8Indexed(masm, VS1, coeffs, c2Start, offsets);

                int k = 4 * level + i;

                if (k > 7) {
                    startIncr = 256;
                } else if (k == 5) {
                    startIncr = 384;
                } else {
                    startIncr = 128;
                }

                c1Start += startIncr;
                c2Start += startIncr;
            }

            c2 /= 2;
        }
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        GraalError.guarantee(resultValue.getPlatformKind().equals(AArch64Kind.DWORD), "Invalid resultValue kind: %s", resultValue);
        GraalError.guarantee(coeffsValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid coeffsValue kind: %s", coeffsValue);
        GraalError.guarantee(zetasValue.getPlatformKind().equals(AArch64Kind.QWORD), "Invalid zetasValue kind: %s", zetasValue);

        Register coeffs = asRegister(coeffsValue);
        Register zetas = asRegister(zetasValue);
        Register dilithiumConsts = AArch64.r14;

        try (AArch64MacroAssembler.ScratchRegister scratch1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister scratch2 = masm.getScratchRegister()) {
            Register tmpAddr = scratch2.getRegister();
            GraalError.guarantee(!scratch1.getRegister().equals(tmpAddr), "scratch registers must be distinct");

            loadExternalAddress(crb, masm, dilithiumConsts, DILITHIUM_CONSTS);

            // Each level represents one iteration of the outer for loop of the Java version.

            // level 0-4
            emitNttLevel0To4(masm, coeffs, zetas, dilithiumConsts);

            // level 5

            // At level 5 the coefficients we need to combine with the zetas
            // are grouped in memory in blocks of size 4. So, for both sets of
            // coefficients we load 4 adjacent values at 8 different offsets
            // using an indexed ldr with register variant Q and multiply them
            // in sequence order by the next set of inputs. Likewise we store
            // the resuls using an indexed str with register variant Q.
            for (int i = 0; i < 1024; i += 256) {
                // reload constants q, qinv each iteration as they get clobbered later
                AArch64DilithiumSupport.loadQInvQ(masm, dilithiumConsts);
                // load 32 (8x4S) coefficients via first offsets = c1
                loadVSeq8LdrIndexed(masm, VS1, coeffs, i, LEVEL_5_OFFSETS_1);
                // load next 32 (8x4S) inputs = b
                loadVSeq8Post(masm, VS2, zetas);
                // a = b montul c1
                AArch64DilithiumSupport.emitMontMul32(masm, VS2, VS1, VS2, AArch64DilithiumSupport.VTMP, AArch64DilithiumSupport.VQ);
                // load 32 (8x4S) coefficients via second offsets = c2
                loadVSeq8LdrIndexed(masm, VS1, coeffs, i, LEVEL_5_OFFSETS_2);
                // add/sub with result of multiply
                addVSeq8(masm, VS3, VS1, VS2);     // a1 = a - c2
                subVSeq8(masm, VS1, VS1, VS2);     // a0 = a + c1
                // write back new coefficients using same offsets
                storeVSeq8StrIndexed(masm, VS3, coeffs, i, LEVEL_5_OFFSETS_2);
                storeVSeq8StrIndexed(masm, VS1, coeffs, i, LEVEL_5_OFFSETS_1);
            }

            // level 6
            // At level 6 the coefficients we need to combine with the zetas
            // are grouped in memory in pairs, the first two being montmul
            // inputs and the second add/sub inputs. We can still implement
            // the montmul+sub+add using 4-way parallelism but only if we
            // combine the coefficients with the zetas 16 at a time. We load 8
            // adjacent values at 4 different offsets using an ld2 load with
            // arrangement 2D. That interleaves the lower and upper halves of
            // each pair of quadwords into successive vector registers. We
            // then need to montmul the 4 even elements of the coefficients
            // register sequence by the zetas in order and then add/sub the 4
            // odd elements of the coefficients register sequence. We use an
            // equivalent st2 operation to store the results back into memory
            // de-interleaved.
            for (int i = 0; i < 1024; i += 128) {
                // reload constants q, qinv each iteration as they get clobbered later
                AArch64DilithiumSupport.loadQInvQ(masm, dilithiumConsts);
                // load interleaved 16 (4x2D) coefficients via offsets
                loadInterleavedVSeq8(masm, VS1, coeffs, tmpAddr, i, ElementSize.DoubleWord, LEVEL_0_4_OFFSETS);
                // load next 16 (4x4S) inputs
                loadVSeq4Post(masm, VS2_FRONT, zetas);
                // mont multiply odd elements of vs1 by vs2 and add/sub into odds/evens
                AArch64DilithiumSupport.emitMontMul16SubAdd(masm, VS1_EVEN, VS1_ODD, VS2_FRONT, AArch64DilithiumSupport.VTMP, AArch64DilithiumSupport.VQ);
                // store interleaved 16 (4x2D) coefficients via offsets
                storeInterleavedVSeq8(masm, VS1, coeffs, tmpAddr, i, ElementSize.DoubleWord, LEVEL_0_4_OFFSETS);
            }

            // level 7
            // At level 7 the coefficients we need to combine with the zetas
            // occur singly with montmul inputs alterating with add/sub
            // inputs. Once again we can use 4-way parallelism to combine 16
            // zetas at a time. However, we have to load 8 adjacent values at
            // 4 different offsets using an ld2 load with arrangement 4S. That
            // interleaves the the odd words of each pair into one
            // coefficients vector register and the even words of the pair
            // into the next register. We then need to montmul the 4 even
            // elements of the coefficients register sequence by the zetas in
            // order and then add/sub the 4 odd elements of the coefficients
            // register sequence. We use an equivalent st2 operation to store
            // the results back into memory de-interleaved.
            for (int i = 0; i < 1024; i += 128) {
                // reload constants q, qinv each iteration as they get clobbered later
                AArch64DilithiumSupport.loadQInvQ(masm, dilithiumConsts);
                // load interleaved 16 (4x4S) coefficients via offsets
                loadInterleavedVSeq8(masm, VS1, coeffs, tmpAddr, i, ElementSize.Word, LEVEL_0_4_OFFSETS);
                // load next 16 (4x4S) inputs
                loadVSeq4Post(masm, VS2_FRONT, zetas);
                // mont multiply odd elements of vs1 by vs2 and add/sub into odds/evens
                AArch64DilithiumSupport.emitMontMul16SubAdd(masm, VS1_EVEN, VS1_ODD, VS2_FRONT, AArch64DilithiumSupport.VTMP, AArch64DilithiumSupport.VQ);
                // store interleaved 16 (4x4S) coefficients via offsets
                storeInterleavedVSeq8(masm, VS1, coeffs, tmpAddr, i, ElementSize.Word, LEVEL_0_4_OFFSETS);
            }

            masm.mov(32, asRegister(resultValue), zr);
        }
    }
}
