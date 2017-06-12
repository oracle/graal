/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.hotspot.aarch64;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.meta.JavaKind.Object;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.BarrierKind.LOAD_LOAD;
import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.BarrierKind.LOAD_STORE;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.hotspot.OptimizedCallTargetInstrumentation;
import org.graalvm.compiler.truffle.hotspot.OptimizedCallTargetInstrumentationFactory;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;

@ServiceProvider(OptimizedCallTargetInstrumentationFactory.class)
public class AArch64OptimizedCallTargetInstumentationFactory extends OptimizedCallTargetInstrumentationFactory {

    @Override
    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, CompilationResult compilationResult) {
        return new OptimizedCallTargetInstrumentation(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, compilationResult, config, registers) {
            @Override
            protected void injectTailCallCode() {
                AArch64MacroAssembler masm = (AArch64MacroAssembler) this.asm;
                try (ScratchRegister scratch = masm.getScratchRegister()) {
                    Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, Object).get(0);
                    Register spillRegister = scratch.getRegister();
                    Label doProlog = new Label();
                    AArch64Address entryPointAddress = AArch64Address.createPairUnscaledImmediateAddress(thisRegister, getFieldOffset("entryPoint", InstalledCode.class));

                    masm.ldr(64, spillRegister, entryPointAddress);
                    masm.dmb(LOAD_LOAD);
                    masm.dmb(LOAD_STORE);
                    masm.cbz(64, spillRegister, doProlog);
                    // TODO(alexpro): Implement and test.
                    // masm.tbz(64, spillRegister, 0, doProlog);
                    masm.jmp(spillRegister);
                    masm.nop();
                    masm.bind(doProlog);
                }
            }
        };
    }

    @Override
    public String getArchitecture() {
        return "aarch64";
    }
}
