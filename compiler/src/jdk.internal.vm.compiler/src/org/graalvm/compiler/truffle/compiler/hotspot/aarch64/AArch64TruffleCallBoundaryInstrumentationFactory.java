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
import static org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_FIELD_BARRIER;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.hotspot.aarch64.AArch64HotSpotBackend;
import org.graalvm.compiler.hotspot.aarch64.AArch64HotSpotMove;
import org.graalvm.compiler.hotspot.aarch64.AArch64HotSpotZBarrierSetLIRGenerator;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMap;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.EntryPointDecorator;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerConfiguration;
import org.graalvm.compiler.truffle.compiler.hotspot.TruffleCallBoundaryInstrumentationFactory;
import org.graalvm.compiler.truffle.compiler.hotspot.TruffleEntryPointDecorator;

import jdk.vm.ci.code.Register;

@ServiceProvider(TruffleCallBoundaryInstrumentationFactory.class)
public class AArch64TruffleCallBoundaryInstrumentationFactory extends TruffleCallBoundaryInstrumentationFactory {

    @Override
    public EntryPointDecorator create(TruffleCompilerConfiguration compilerConfig, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        return new TruffleEntryPointDecorator(compilerConfig, config, registers) {
            @Override
            public void emitEntryPoint(CompilationResultBuilder crb) {
                AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
                AArch64HotSpotBackend.emitInvalidatePlaceholder(crb, masm);

                try (ScratchRegister scratch = masm.getScratchRegister()) {
                    Register thisRegister = crb.codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, Object).get(0);
                    Register spillRegister = scratch.getRegister();
                    Label doProlog = new Label();
                    if (config.useCompressedOops) {
                        CompressEncoding encoding = config.getOopEncoding();
                        masm.ldr(32, spillRegister, AArch64Address.createImmediateAddress(32, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, thisRegister, installedCodeOffset));
                        Register base = encoding.hasBase() ? registers.getHeapBaseRegister() : null;
                        AArch64HotSpotMove.UncompressPointer.emitUncompressCode(masm, spillRegister, spillRegister, base, encoding.getShift(), true);
                    } else {
                        AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, thisRegister, installedCodeOffset);
                        masm.ldr(64, spillRegister, address);
                        if (config.gc == HotSpotGraalRuntime.HotSpotGC.Z) {
                            ForeignCallLinkage callTarget = crb.providers.getForeignCalls().lookupForeignCall(Z_FIELD_BARRIER);
                            AArch64FrameMap frameMap = (AArch64FrameMap) crb.frameMap;
                            AArch64HotSpotZBarrierSetLIRGenerator.emitBarrier(crb, masm, null, spillRegister, config, callTarget, address, null, frameMap);
                        }
                    }
                    masm.ldr(64, spillRegister, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, spillRegister, entryPointOffset));
                    masm.cbz(64, spillRegister, doProlog);
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
