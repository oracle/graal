/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.hotspot.amd64;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.X_FIELD_BARRIER;
import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_LOAD_BARRIER;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.amd64.AMD64HotSpotBackend;
import jdk.graal.compiler.hotspot.amd64.x.AMD64HotSpotXBarrierSetLIRGenerator;
import jdk.graal.compiler.hotspot.amd64.z.AMD64HotSpotZBarrierSetLIRGenerator;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.graal.compiler.truffle.TruffleCompilerConfiguration;
import jdk.graal.compiler.truffle.hotspot.TruffleCallBoundaryInstrumentationFactory;
import jdk.graal.compiler.truffle.hotspot.TruffleEntryPointDecorator;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;

@ServiceProvider(TruffleCallBoundaryInstrumentationFactory.class)
public class AMD64TruffleCallBoundaryInstrumentationFactory extends TruffleCallBoundaryInstrumentationFactory {

    @Override
    public EntryPointDecorator create(TruffleCompilerConfiguration compilerConfig, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        return new TruffleEntryPointDecorator(compilerConfig, config, registers) {
            @Override
            public void emitEntryPoint(CompilationResultBuilder crb) {
                AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
                Register thisRegister = crb.getCodeCache().getRegisterConfig().getCallingConventionRegisters(JavaCall, JavaKind.Object).get(0);
                Register spillRegister = AMD64.r10;
                Label doProlog = new Label();
                int pos = masm.position();

                AMD64Address address = new AMD64Address(thisRegister, installedCodeOffset);
                if (config.useCompressedOops) {
                    // First instruction must be at least 5 bytes long to be safe for
                    // patching
                    masm.movl(spillRegister, address, true);
                    assert masm.position() - pos >= AMD64HotSpotBackend.PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE : masm.position() + "-" + pos;
                    CompressEncoding encoding = config.getOopEncoding();
                    Register heapBaseRegister = AMD64Move.UncompressPointerOp.hasBase(encoding) ? registers.getHeapBaseRegister() : Register.None;
                    AMD64Move.UncompressPointerOp.emitUncompressCode(masm, spillRegister, encoding.getShift(), heapBaseRegister, true);
                } else {
                    // First instruction must be at least 5 bytes long to be safe for
                    // patching
                    masm.movq(spillRegister, address, true);
                    assert masm.position() - pos >= AMD64HotSpotBackend.PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE : masm.position() + "-" + pos;
                    if (config.gc == HotSpotGraalRuntime.HotSpotGC.X) {
                        ForeignCallLinkage callTarget = crb.getForeignCalls().lookupForeignCall(X_FIELD_BARRIER);
                        AMD64HotSpotXBarrierSetLIRGenerator.emitBarrier(crb, masm, null, spillRegister, config, callTarget, address, null,
                                        (AMD64HotSpotBackend.HotSpotFrameContext) crb.frameContext);
                    }
                    if (config.gc == HotSpotGraalRuntime.HotSpotGC.Z) {
                        ForeignCallLinkage callTarget = crb.getForeignCalls().lookupForeignCall(Z_LOAD_BARRIER);
                        AMD64HotSpotZBarrierSetLIRGenerator.emitLoadBarrier(crb, masm, spillRegister, callTarget, address, null,
                                        (AMD64HotSpotBackend.HotSpotFrameContext) crb.frameContext, false);
                    }
                }
                masm.movq(spillRegister, new AMD64Address(spillRegister, entryPointOffset));
                masm.testqAndJcc(spillRegister, spillRegister, ConditionFlag.Equal, doProlog, true);
                masm.jmp(spillRegister);
                masm.bind(doProlog);
            }
        };
    }

    @Override
    public String getArchitecture() {
        return "AMD64";
    }
}
