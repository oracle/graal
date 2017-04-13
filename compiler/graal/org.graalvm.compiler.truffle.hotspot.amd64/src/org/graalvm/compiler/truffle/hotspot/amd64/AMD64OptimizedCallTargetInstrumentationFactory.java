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
package org.graalvm.compiler.truffle.hotspot.amd64;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.hotspot.amd64.AMD64HotSpotBackend;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.hotspot.OptimizedCallTargetInstrumentation;
import org.graalvm.compiler.truffle.hotspot.OptimizedCallTargetInstrumentationFactory;

@ServiceProvider(OptimizedCallTargetInstrumentationFactory.class)
public class AMD64OptimizedCallTargetInstrumentationFactory extends OptimizedCallTargetInstrumentationFactory {

    @Override
    public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, CompilationResult compilationResult) {
        return new OptimizedCallTargetInstrumentation(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, compilationResult, config, registers) {
            @Override
            protected void injectTailCallCode() {
                @SuppressWarnings("hiding")
                AMD64MacroAssembler asm = (AMD64MacroAssembler) this.asm;
                Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, JavaKind.Object).get(0);
                Register spillRegister = AMD64.r10; // TODO(mg): fix me
                Label doProlog = new Label();
                int pos = asm.position();

                AMD64Address codeBlobAddress = new AMD64Address(thisRegister, getFieldOffset("entryPoint", InstalledCode.class));
                /*
                 * The first instruction must be at least 5 bytes long to be safe for not entrant
                 * patching, so force a wider encoding of the movq instruction.
                 */
                asm.movq(spillRegister, codeBlobAddress, true);
                assert asm.position() - pos >= AMD64HotSpotBackend.PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
                asm.testq(spillRegister, spillRegister);
                asm.jcc(ConditionFlag.Equal, doProlog);
                asm.jmp(spillRegister);

                asm.bind(doProlog);
            }
        };
    }

    @Override
    public String getArchitecture() {
        return "AMD64";
    }
}
