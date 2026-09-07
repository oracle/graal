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

package jdk.graal.compiler.asm.amd64.test;

import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;

import java.util.EnumSet;
import java.util.HexFormat;
import java.util.function.Consumer;

import org.junit.Test;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexMRIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.test.GraalTest;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * {@code Float.float16ToFloat} / {@code Float.floatToFloat16} lower to
 * {@link jdk.graal.compiler.lir.amd64.AMD64HalfFloatToFloatOp} /
 * {@link jdk.graal.compiler.lir.amd64.AMD64FloatToHalfFloatOp}, which emit {@code vcvtph2ps} /
 * {@code vcvtps2ph}. When the register allocator hands them an AVX-512 high register
 * ({@code xmm16-31}) the conversion must be EVEX-encoded: the VEX form cannot address those
 * registers and failed with "instruction VCVTPH2PS illegal operand xmm22".
 *
 * Host-independent: builds a synthetic AVX-512 target and only encodes the instructions.
 */
public class AMD64Float16HighRegisterEncodingTest extends GraalTest {

    /** A representative spread of high registers, including xmm22 from the reported failure. */
    private static final Register[] HIGH_REGISTERS = {AMD64.xmm16, AMD64.xmm22, AMD64.xmm31};

    /** Synthetic full-AVX-512 (+F16C) target: only encoding is exercised, so it need not match the host. */
    private static final TargetDescription TARGET = new TargetDescription(new AMD64(EnumSet.of(
                    CPUFeature.SSE, CPUFeature.SSE2, CPUFeature.AVX, CPUFeature.AVX2, CPUFeature.F16C,
                    CPUFeature.AVX512F, CPUFeature.AVX512BW, CPUFeature.AVX512VL, CPUFeature.AVX512DQ)), true, 16, 4096, true);

    private static String hexOf(Consumer<AMD64MacroAssembler> emit) {
        AMD64MacroAssembler masm = new AMD64MacroAssembler(TARGET);
        emit.accept(masm);
        return HexFormat.ofDelimiter(" ").withUpperCase().formatHex(masm.copy(0, masm.position()));
    }

    /**
     * {@code vcvtph2ps(dst, dst)} (as emitted by HalfFloatToFloat) must select the EVEX variant for
     * a high register instead of throwing on the VEX encoding.
     */
    @Test
    public void vcvtph2psHighRegisterUsesEvex() {
        for (Register xmm : HIGH_REGISTERS) {
            String macro = hexOf(m -> m.vcvtph2ps(xmm, xmm));
            String evex = hexOf(m -> VexRMOp.EVCVTPH2PS.emit(m, XMM, xmm, xmm));
            assertDeepEquals("vcvtph2ps " + xmm + " must encode as EVCVTPH2PS", evex, macro);
        }
    }

    /**
     * {@code vcvtps2ph(dst, src, imm8)} (as emitted by FloatToHalfFloat) must select the EVEX variant
     * for a high register.
     */
    @Test
    public void vcvtps2phHighRegisterUsesEvex() {
        for (Register xmm : HIGH_REGISTERS) {
            String macro = hexOf(m -> m.vcvtps2ph(xmm, xmm, 0b100));
            String evex = hexOf(m -> VexMRIOp.EVCVTPS2PH.emit(m, XMM, xmm, xmm, 0b100));
            assertDeepEquals("vcvtps2ph " + xmm + " must encode as EVCVTPS2PH", evex, macro);
        }
    }
}
