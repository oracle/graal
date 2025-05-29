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
package jdk.graal.compiler.truffle.hotspot.aarch64;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_LOAD_BARRIER;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.meta.JavaKind.Object;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotBackend;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotMove;
import jdk.graal.compiler.hotspot.aarch64.z.AArch64HotSpotZBarrierSetLIRGenerator;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;
import jdk.graal.compiler.truffle.hotspot.TruffleCallBoundaryInstrumentationFactory;
import jdk.graal.compiler.truffle.hotspot.TruffleEntryPointDecorator;
import jdk.vm.ci.code.Register;

@ServiceProvider(TruffleCallBoundaryInstrumentationFactory.class)
public class AArch64TruffleCallBoundaryInstrumentationFactory extends TruffleCallBoundaryInstrumentationFactory {

    @Override
    public EntryPointDecorator create(TruffleCompilerConfiguration compilerConfig, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        return new TruffleEntryPointDecorator(compilerConfig, config, registers) {
            @Override
            public void emitEntryPoint(CompilationResultBuilder crb, boolean beforeFrameSetup) {
                if (beforeFrameSetup == (config.gc == HotSpotGraalRuntime.HotSpotGC.Z)) {
                    // The Z load barrier must be performed after the nmethod entry barrier which is
                    // part of the frame setup. The other GCs don't have a read barrier so it's
                    // safe to do this dispatch before the frame is set up.
                    return;
                }

                AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
                AArch64HotSpotBackend.emitInvalidatePlaceholder(crb, masm);

                try (ScratchRegister scratch = masm.getScratchRegister()) {
                    Register thisRegister = crb.getCodeCache().getRegisterConfig().getCallingConventionRegisters(JavaCall, Object).get(0);
                    Register spillRegister = scratch.getRegister();
                    Label doProlog = new Label();
                    if (config.useCompressedOops) {
                        CompressEncoding encoding = config.getOopEncoding();
                        masm.ldr(32, spillRegister, AArch64Address.createImmediateAddress(32, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, thisRegister, installedCodeOffset));
                        Register base = encoding.hasBase() ? registers.getHeapBaseRegister() : null;
                        AArch64HotSpotMove.UncompressPointer.emitUncompressCode(masm, spillRegister, spillRegister, base, encoding, true);
                    } else {
                        AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, thisRegister, installedCodeOffset);
                        masm.ldr(64, spillRegister, address);
                        if (config.gc == HotSpotGraalRuntime.HotSpotGC.Z) {
                            ForeignCallLinkage callTarget = crb.getForeignCalls().lookupForeignCall(Z_LOAD_BARRIER);
                            AArch64HotSpotZBarrierSetLIRGenerator.emitLoadBarrier(crb, masm, config, spillRegister, callTarget, address, null, false, false);
                        }
                    }
                    masm.ldr(64, spillRegister, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, spillRegister, entryPointOffset));
                    masm.cbz(64, spillRegister, doProlog);
                    if (!beforeFrameSetup) {
                        // Must tear down the frame before jumping
                        ((AArch64HotSpotBackend.HotSpotFrameContext) crb.frameContext).leave(crb);
                    }
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
