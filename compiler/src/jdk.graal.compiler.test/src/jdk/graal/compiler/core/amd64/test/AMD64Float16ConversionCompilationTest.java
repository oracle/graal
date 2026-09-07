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
package jdk.graal.compiler.core.amd64.test;

import static jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph.INVALID_BLOCK_ID;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.ValueProcedure;
import jdk.graal.compiler.lir.amd64.AMD64FloatToHalfFloatOp;
import jdk.graal.compiler.lir.amd64.AMD64HalfFloatToFloatOp;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.FinalCodeAnalysisPhase.FinalCodeAnalysisContext;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;

/**
 * High-level regression test: {@code Float.float16ToFloat} and
 * {@code Float.floatToFloat16} are lowered to {@link AMD64HalfFloatToFloatOp} /
 * {@link AMD64FloatToHalfFloatOp}, which emit the SIMD conversions {@code vcvtph2ps} /
 * {@code vcvtps2ph}. When the register allocator places a conversion operand in an AVX-512 high
 * register ({@code xmm16-31}) - which it does under register pressure on AVX-512 hardware - the
 * conversion must be EVEX-encoded. The VEX form cannot address those registers and previously failed
 * during code emission with e.g. "instruction VCVTPH2PS illegal operand xmm22" (originally observed
 * compiling com.llama4j.Q6_KFloatTensor.vectorDot under Native Image).
 *
 * The kernels below keep 20 conversion operands live across a loop so the allocator must use the high
 * registers; compiling them exercises the exact failing path. The test additionally checks (via a
 * final-code-analysis LIR phase) that a conversion op really did land in a high register, so it
 * cannot pass vacuously. Requires full AVX-512; skipped otherwise.
 */
public class AMD64Float16ConversionCompilationTest extends GraalCompilerTest {

    static final int N = 20;

    private boolean halfFloatToFloatHigh;
    private int halfFloatToFloatOps;
    private boolean floatToHalfFloatHigh;
    private int floatToHalfFloatOps;

    /** float16 -> float: 20 accumulators kept live force the conversion result into high registers. */
    public static void float16ToFloatKernel(short[] in, float[] out, int iters) {
        float a0 = Float.float16ToFloat(in[0]);
        float a1 = Float.float16ToFloat(in[1]);
        float a2 = Float.float16ToFloat(in[2]);
        float a3 = Float.float16ToFloat(in[3]);
        float a4 = Float.float16ToFloat(in[4]);
        float a5 = Float.float16ToFloat(in[5]);
        float a6 = Float.float16ToFloat(in[6]);
        float a7 = Float.float16ToFloat(in[7]);
        float a8 = Float.float16ToFloat(in[8]);
        float a9 = Float.float16ToFloat(in[9]);
        float a10 = Float.float16ToFloat(in[10]);
        float a11 = Float.float16ToFloat(in[11]);
        float a12 = Float.float16ToFloat(in[12]);
        float a13 = Float.float16ToFloat(in[13]);
        float a14 = Float.float16ToFloat(in[14]);
        float a15 = Float.float16ToFloat(in[15]);
        float a16 = Float.float16ToFloat(in[16]);
        float a17 = Float.float16ToFloat(in[17]);
        float a18 = Float.float16ToFloat(in[18]);
        float a19 = Float.float16ToFloat(in[19]);
        for (int i = 1; i < iters; i++) {
            int b = i * N;
            a0 += Float.float16ToFloat(in[b + 0]);
            a1 += Float.float16ToFloat(in[b + 1]);
            a2 += Float.float16ToFloat(in[b + 2]);
            a3 += Float.float16ToFloat(in[b + 3]);
            a4 += Float.float16ToFloat(in[b + 4]);
            a5 += Float.float16ToFloat(in[b + 5]);
            a6 += Float.float16ToFloat(in[b + 6]);
            a7 += Float.float16ToFloat(in[b + 7]);
            a8 += Float.float16ToFloat(in[b + 8]);
            a9 += Float.float16ToFloat(in[b + 9]);
            a10 += Float.float16ToFloat(in[b + 10]);
            a11 += Float.float16ToFloat(in[b + 11]);
            a12 += Float.float16ToFloat(in[b + 12]);
            a13 += Float.float16ToFloat(in[b + 13]);
            a14 += Float.float16ToFloat(in[b + 14]);
            a15 += Float.float16ToFloat(in[b + 15]);
            a16 += Float.float16ToFloat(in[b + 16]);
            a17 += Float.float16ToFloat(in[b + 17]);
            a18 += Float.float16ToFloat(in[b + 18]);
            a19 += Float.float16ToFloat(in[b + 19]);
        }
        out[0] = a0;
        out[1] = a1;
        out[2] = a2;
        out[3] = a3;
        out[4] = a4;
        out[5] = a5;
        out[6] = a6;
        out[7] = a7;
        out[8] = a8;
        out[9] = a9;
        out[10] = a10;
        out[11] = a11;
        out[12] = a12;
        out[13] = a13;
        out[14] = a14;
        out[15] = a15;
        out[16] = a16;
        out[17] = a17;
        out[18] = a18;
        out[19] = a19;
    }

    /** float -> float16: 20 floats kept live force the conversion source into high registers. */
    public static void floatToFloat16Kernel(float[] in, short[] out, int iters) {
        float a0 = in[0];
        float a1 = in[1];
        float a2 = in[2];
        float a3 = in[3];
        float a4 = in[4];
        float a5 = in[5];
        float a6 = in[6];
        float a7 = in[7];
        float a8 = in[8];
        float a9 = in[9];
        float a10 = in[10];
        float a11 = in[11];
        float a12 = in[12];
        float a13 = in[13];
        float a14 = in[14];
        float a15 = in[15];
        float a16 = in[16];
        float a17 = in[17];
        float a18 = in[18];
        float a19 = in[19];
        for (int i = 1; i < iters; i++) {
            int b = i * N;
            a0 += in[b + 0];
            a1 += in[b + 1];
            a2 += in[b + 2];
            a3 += in[b + 3];
            a4 += in[b + 4];
            a5 += in[b + 5];
            a6 += in[b + 6];
            a7 += in[b + 7];
            a8 += in[b + 8];
            a9 += in[b + 9];
            a10 += in[b + 10];
            a11 += in[b + 11];
            a12 += in[b + 12];
            a13 += in[b + 13];
            a14 += in[b + 14];
            a15 += in[b + 15];
            a16 += in[b + 16];
            a17 += in[b + 17];
            a18 += in[b + 18];
            a19 += in[b + 19];
        }
        out[0] = Float.floatToFloat16(a0);
        out[1] = Float.floatToFloat16(a1);
        out[2] = Float.floatToFloat16(a2);
        out[3] = Float.floatToFloat16(a3);
        out[4] = Float.floatToFloat16(a4);
        out[5] = Float.floatToFloat16(a5);
        out[6] = Float.floatToFloat16(a6);
        out[7] = Float.floatToFloat16(a7);
        out[8] = Float.floatToFloat16(a8);
        out[9] = Float.floatToFloat16(a9);
        out[10] = Float.floatToFloat16(a10);
        out[11] = Float.floatToFloat16(a11);
        out[12] = Float.floatToFloat16(a12);
        out[13] = Float.floatToFloat16(a13);
        out[14] = Float.floatToFloat16(a14);
        out[15] = Float.floatToFloat16(a15);
        out[16] = Float.floatToFloat16(a16);
        out[17] = Float.floatToFloat16(a17);
        out[18] = Float.floatToFloat16(a18);
        out[19] = Float.floatToFloat16(a19);
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues opts) {
        LIRSuites suites = super.createLIRSuites(opts);
        suites.getFinalCodeAnalysisStage().appendPhase(new LIRPhase<FinalCodeAnalysisContext>() {
            @Override
            protected CharSequence createName() {
                return "Float16HighRegisterVerification";
            }

            @Override
            protected void run(TargetDescription target, LIRGenerationResult lirGenRes, FinalCodeAnalysisContext context) {
                LIR lir = lirGenRes.getLIR();
                for (int blockId : lir.codeEmittingOrder()) {
                    if (blockId == INVALID_BLOCK_ID) {
                        continue;
                    }
                    BasicBlock<?> block = lir.getBlockById(blockId);
                    for (LIRInstruction instr : lir.getLIRforBlock(block)) {
                        if (instr instanceof AMD64HalfFloatToFloatOp) {
                            halfFloatToFloatOps++;
                            halfFloatToFloatHigh |= usesHighRegister(instr);
                        } else if (instr instanceof AMD64FloatToHalfFloatOp) {
                            floatToHalfFloatOps++;
                            floatToHalfFloatHigh |= usesHighRegister(instr);
                        }
                    }
                }
            }
        });
        return suites;
    }

    @Test
    public void float16ToFloatWithHighRegisters() {
        assumeFullAVX512();
        int iters = 4;
        short[] in = new short[iters * N];
        for (int i = 0; i < in.length; i++) {
            in[i] = (short) (0x3C00 + i); // ~1.0 and up, in IEEE-754 binary16
        }
        ArgSupplier out = () -> new float[N];
        test("float16ToFloatKernel", in, out, iters);

        assertTrue("Float.float16ToFloat did not lower to AMD64HalfFloatToFloatOp", halfFloatToFloatOps > 0);
        assertTrue("no AMD64HalfFloatToFloatOp landed in an AVX-512 high register; " + "the register-pressure scenario was not exercised", halfFloatToFloatHigh);
    }

    @Test
    public void floatToFloat16WithHighRegisters() {
        assumeFullAVX512();
        int iters = 4;
        float[] in = new float[iters * N];
        for (int i = 0; i < in.length; i++) {
            in[i] = i * 0.5f;
        }
        ArgSupplier out = () -> new short[N];
        test("floatToFloat16Kernel", in, out, iters);

        assertTrue("Float.floatToFloat16 did not lower to AMD64FloatToHalfFloatOp", floatToHalfFloatOps > 0);
        assertTrue("no AMD64FloatToHalfFloatOp used an AVX-512 high register; " + "the register-pressure scenario was not exercised", floatToHalfFloatHigh);
    }

    private void assumeFullAVX512() {
        assumeTrue("test is AMD64 specific", getTarget().arch instanceof AMD64);
        assumeTrue("requires full AVX-512 (otherwise xmm16-31 are not allocatable)",
                        AMD64BaseAssembler.supportsFullAVX512(((AMD64) getTarget().arch).getFeatures()));
    }

    private static boolean usesHighRegister(LIRInstruction instr) {
        boolean[] found = {false};
        // The high register may be in any operand role: the float16->float result is a @Def XMM,
        // while the float->float16 source is an @Alive XMM (its @Def is a GPR).
        ValueProcedure proc = (value, _, _) -> {
            if (ValueUtil.isRegister(value) && AMD64BaseAssembler.isAVX512Register(ValueUtil.asRegister(value))) {
                found[0] = true;
            }
            return value;
        };
        instr.forEachInput(proc);
        instr.forEachAlive(proc);
        instr.forEachTemp(proc);
        instr.forEachOutput(proc);
        return found[0];
    }
}
