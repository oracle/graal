/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.sparc;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.meta.JavaKind.Object;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Equal;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.sparc.SPARCHotSpotMove;
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
public class SPARCTruffleCallBoundaryInstumentationFactory extends TruffleCallBoundaryInstrumentationFactory {

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
                        SPARCMacroAssembler masm = (SPARCMacroAssembler) this.asm;
                        try (ScratchRegister scratch = masm.getScratchRegister()) {
                            Register thisRegister = codeCache.getRegisterConfig().getCallingConventionRegisters(JavaCall, Object).get(0);
                            Register spillRegister = scratch.getRegister();
                            Label doProlog = new Label();

                            if (config.useCompressedOops) {
                                CompressEncoding encoding = config.getOopEncoding();
                                masm.ld(new SPARCAddress(thisRegister, installedCodeOffset), spillRegister, 4, false);
                                Register baseReg = encoding.hasBase() ? registers.getHeapBaseRegister() : null;
                                SPARCHotSpotMove.UncompressPointer.emitUncompressCode(masm, spillRegister, spillRegister, baseReg, encoding.getShift(), true);
                            } else {
                                masm.ldx(new SPARCAddress(thisRegister, installedCodeOffset), spillRegister);
                            }
                            masm.ldx(new SPARCAddress(spillRegister, entryPointOffset), spillRegister);
                            masm.compareBranch(spillRegister, 0, Equal, Xcc, doProlog, PREDICT_NOT_TAKEN, null);
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
        return "SPARC";
    }
}
