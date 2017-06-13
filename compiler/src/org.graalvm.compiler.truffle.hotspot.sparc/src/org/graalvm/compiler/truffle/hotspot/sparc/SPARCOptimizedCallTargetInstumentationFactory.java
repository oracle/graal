/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.hotspot.sparc;

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.meta.JavaKind.Object;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;

import jdk.vm.ci.sparc.SPARC;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
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

@ServiceProvider(OptimizedCallTargetInstrumentationFactory.class)
public class SPARCOptimizedCallTargetInstumentationFactory extends OptimizedCallTargetInstrumentationFactory {

    @Override
    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, CompilationResult compilationResult) {
        return new OptimizedCallTargetInstrumentation(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, compilationResult, config, registers) {
            @Override
            protected void injectTailCallCode() {
                @SuppressWarnings("hiding")
                SPARCMacroAssembler asm = (SPARCMacroAssembler) this.asm;
                try (ScratchRegister scratch = asm.getScratchRegister()) {
                    Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, Object).get(0);
                    Register spillRegister = scratch.getRegister();
                    Label doProlog = new Label();
                    SPARCAddress entryPointAddress = new SPARCAddress(thisRegister, getFieldOffset("entryPoint", InstalledCode.class));

                    asm.ldx(entryPointAddress, spillRegister);
                    asm.membar(SPARCAssembler.MEMBAR_LOAD_LOAD | SPARCAssembler.MEMBAR_LOAD_STORE);
                    asm.compareBranch(spillRegister, 0, Equal, Xcc, doProlog, PREDICT_NOT_TAKEN, null);
                    asm.and(spillRegister, 1, SPARC.g0);
                    asm.bz(doProlog);
                    asm.xor(spillRegister, 1, spillRegister);
                    asm.jmp(spillRegister);
                    asm.nop();
                    asm.bind(doProlog);
                }
            }
        };
    }

    @Override
    public String getArchitecture() {
        return "SPARC";
    }
}
