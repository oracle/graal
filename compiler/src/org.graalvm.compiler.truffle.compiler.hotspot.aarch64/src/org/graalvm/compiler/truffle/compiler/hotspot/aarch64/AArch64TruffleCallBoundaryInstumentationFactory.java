/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.aarch64;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.meta.JavaKind.Object;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.aarch64.AArch64HotSpotBackend;
import org.graalvm.compiler.hotspot.aarch64.AArch64HotSpotMove;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentation;
import org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.MetaAccessProvider;

@ServiceProvider(TruffleCallBoundaryInstrumentationFactory.class)
public class AArch64TruffleCallBoundaryInstumentationFactory extends TruffleCallBoundaryInstrumentationFactory {

    @Override
    public CompilationResultBuilderFactory create(MetaAccessProvider metaAccess, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        return new TruffleCompilationResultBuilderFactory(metaAccess, config, registers) {
            @Override
            public CompilationResultBuilder createBuilder(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder,
                            FrameContext frameContext,
                            OptionValues options, DebugContext debug, CompilationResult compilationResult, Register nullRegister) {
                return new TruffleCallBoundaryInstrumentation(metaAccess, codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, debug, compilationResult, config, registers) {
                    @Override
                    protected void injectTailCallCode(int installedCodeOffset, int entryPointOffset) {
                        AArch64MacroAssembler masm = (AArch64MacroAssembler) this.asm;
                        AArch64HotSpotBackend.emitInvalidatePlaceholder(this, masm);

                        try (ScratchRegister scratch = masm.getScratchRegister()) {
                            Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, Object).get(0);
                            Register spillRegister = scratch.getRegister();
                            Label doProlog = new Label();
                            if (config.useCompressedOops) {
                                CompressEncoding encoding = config.getOopEncoding();
                                masm.ldr(32, spillRegister, AArch64Address.createPairUnscaledImmediateAddress(thisRegister, installedCodeOffset));
                                Register base = encoding.hasBase() ? registers.getHeapBaseRegister() : null;
                                AArch64HotSpotMove.UncompressPointer.emitUncompressCode(masm, spillRegister, spillRegister, base, encoding.getShift(), true);
                            } else {
                                masm.ldr(64, spillRegister, AArch64Address.createPairUnscaledImmediateAddress(thisRegister, installedCodeOffset));
                            }
                            masm.ldr(64, spillRegister, AArch64Address.createPairUnscaledImmediateAddress(spillRegister, entryPointOffset));
                            masm.cbz(64, spillRegister, doProlog);
                            masm.jmp(spillRegister);
                            masm.nop();
                            masm.bind(doProlog);
                        }
                    }
                };
            }
        };
    }

    @Override
    public String getArchitecture() {
        return "aarch64";
    }
}
