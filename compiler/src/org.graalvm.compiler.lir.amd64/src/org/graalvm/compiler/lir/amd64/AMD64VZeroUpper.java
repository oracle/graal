/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.BitSet;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64Call.ForeignCallOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 * vzeroupper is essential to avoid performance penalty during SSE-AVX transition. Specifically,
 * once we have executed instructions that modify the upper bits (i.e., 128+) of the YMM registers,
 * we need to perform vzeroupper to transit the state to 128bits before executing any SSE
 * instructions. We don't need to place vzeroupper between VEX-encoded SSE instructions and legacy
 * SSE instructions, nor between AVX instructions and VEX-encoded SSE instructions.
 *
 * When running Graal on HotSpot, we emit a vzeroupper LIR operation (i.e. an instance of this
 * class) before a foreign call to the runtime function where Graal has no knowledge. The underlying
 * reason is that HotSpot is SSE-compiled so as to support older CPUs. We also emit a vzeroupper
 * instruction (see {@code AMD64HotSpotReturnOp.emitCode}) upon returning, if the current LIR graph
 * contains LIR operations that touch the upper bits of the YMM registers, including but not limited
 * to {@link AMD64VectorInstruction}, {@link AMD64ArrayCompareToOp}, {@link AMD64ArrayEqualsOp},
 * {@link AMD64ArrayIndexOfOp}, and {@link ForeignCallOp} that invokes to Graal-compiled stubs. For
 * the last case, since Graal-compiled stubs is under our control, we don't emit vzeroupper upon
 * returning of the stub, but rather do that upon returning of the current method.
 *
 * On JDK8, C2 does not emit many vzeroupper instructions, potentially because that YMM registers
 * are not heavily employed (C2 vectorization starts using YMM registers in 9, source
 * https://cr.openjdk.java.net/~vlivanov/talks/2017_Vectorization_in_HotSpot_JVM.pdf) and thus less
 * care has been taken to place these instructions. One example is that many intrinsics use AVX2
 * instructions starting from https://bugs.openjdk.java.net/browse/JDK-8005419, but does not
 * properly place vzeroupper upon returning of the intrinsic stub or the caller (C2-compiled) of the
 * stubs.
 *
 * On JDK11 however, C2 is likely overdoing the vzeroupper: a) before any foreign call, even if the
 * call links to a compiled stub where C2 has knowledge about; b) upon returning of intrinsic stubs
 * (search for clear_upper_avx() in opto/library_call.cpp) or C2-compiled Java method, if the method
 * contains invocation to the aforementioned intrinsic stubs or vectorization code that employs YMM
 * registers. This means, if a Java method only performs System.arraycopy, which will be compiled
 * into a call to an intrinsic stub, the C2 compiled code will vzeroupper before the call, upon the
 * returning of the sub, and upon the returning of the current method; while Graal will only do the
 * last.
 *
 * In SubstrateVM, since the whole image is compiled consistently with or without VEX encoding (the
 * later is the default behavior, see {@code NativeImageGenerator.createTarget}), there is no need
 * for vzeroupper. For dynamic compilation on a SubstrateVM image, if the image is SSE-compiled, we
 * then need vzeroupper when returning from the dynamic compiled code to the pre-built image code.
 */
public class AMD64VZeroUpper extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64VZeroUpper> TYPE = LIRInstructionClass.create(AMD64VZeroUpper.class);

    @Temp protected final RegisterValue[] xmmRegisters;

    public AMD64VZeroUpper(Value[] exclude, RegisterConfig registerConfig) {
        super(TYPE);
        xmmRegisters = initRegisterValues(exclude, registerConfig);
    }

    private static RegisterValue[] initRegisterValues(Value[] exclude, RegisterConfig registerConfig) {
        BitSet skippedRegs = new BitSet();
        if (exclude != null) {
            for (Value value : exclude) {
                if (isRegister(value) && asRegister(value).getRegisterCategory().equals(AMD64.XMM)) {
                    skippedRegs.set(asRegister(value).number);
                }
            }
        }
        ArrayList<RegisterValue> regs = new ArrayList<>();
        for (Register r : registerConfig.getCallerSaveRegisters()) {
            if (r.getRegisterCategory().equals(AMD64.XMM) && !skippedRegs.get(r.number)) {
                regs.add(r.asValue());
            }
        }
        return regs.toArray(new RegisterValue[regs.size()]);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        asm.vzeroupper();
    }
}
