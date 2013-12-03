/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.hotspot.amd64;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.amd64.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.hotspot.amd64.util.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;

/**
 * Backend that decorates an existing {@link AMD64HotSpotBackend}, injecting special code into
 * {@link OptimizedCallTarget#call(PackedFrame, Arguments)} for making a tail-call to the entry
 * point of the target callee.
 */
class AMD64HotSpotTruffleBackend extends Backend {

    private final AMD64HotSpotBackend original;

    private HotSpotResolvedJavaMethod optimizedCallTargetCall;

    public AMD64HotSpotTruffleBackend(AMD64HotSpotBackend original) {
        super(original.getProviders());
        this.original = original;
    }

    private ResolvedJavaMethod getInstrumentedMethod() throws GraalInternalError {
        if (optimizedCallTargetCall == null) {
            try {
                optimizedCallTargetCall = (HotSpotResolvedJavaMethod) getProviders().getMetaAccess().lookupJavaMethod(
                                OptimizedCallTarget.class.getDeclaredMethod("call", PackedFrame.class, Arguments.class));
                optimizedCallTargetCall.setDontInline();
            } catch (NoSuchMethodException | SecurityException e) {
                throw new GraalInternalError(e);
            }
        }
        return optimizedCallTargetCall;
    }

    @Override
    public SuitesProvider getSuites() {
        return original.getSuites();
    }

    @Override
    public DisassemblerProvider getDisassembler() {
        return original.getDisassembler();
    }

    @Override
    public FrameMap newFrameMap() {
        return original.newFrameMap();
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, CallingConvention cc, LIR lir) {
        return original.newLIRGenerator(graph, frameMap, cc, lir);
    }

    @Override
    protected AbstractAssembler createAssembler(FrameMap frameMap) {
        return null;
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerator lirGen, CompilationResult compilationResult) {
        return original.newCompilationResultBuilder(lirGen, compilationResult);
    }

    @Override
    public boolean shouldAllocateRegisters() {
        return original.shouldAllocateRegisters();
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIRGenerator lirGen, ResolvedJavaMethod installedCodeOwner) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.registerConfig;
        HotSpotVMConfig config = original.getRuntime().getConfig();
        Label verifiedStub = new Label();

        // Emit the prefix
        original.emitCodePrefix(installedCodeOwner, crb, asm, regConfig, config, verifiedStub);

        if (getInstrumentedMethod().equals(installedCodeOwner)) {
            // Inject code for {@link OptimizedCallTarget#call(PackedFrame, Arguments)}
            injectCode(asm, config);
        }

        // Emit code for the LIR
        original.emitCodeBody(installedCodeOwner, crb, lirGen);

        // Emit the suffix
        original.emitCodeSuffix(installedCodeOwner, crb, lirGen, asm, frameMap);
    }

    private void injectCode(AMD64MacroAssembler asm, HotSpotVMConfig config) {
        HotSpotProviders providers = original.getRuntime().getHostProviders();
        Register thisRegister = providers.getCodeCache().getRegisterConfig().getCallingConventionRegisters(Type.JavaCall, Kind.Object)[0];
        Register spillRegister = AMD64.r10; // TODO(mg): fix me
        AMD64Address nMethodAddress = new AMD64Address(thisRegister, OptimizedCallTargetFieldInfo.getCompiledMethodFieldOffset());
        if (config.useCompressedOops) {
            asm.movl(spillRegister, nMethodAddress);
            AMD64HotSpotMove.decodePointer(asm, spillRegister, providers.getRegisters().getHeapBaseRegister(), config.narrowOopBase, config.narrowOopShift, config.logMinObjAlignment());
        } else {
            asm.movq(spillRegister, nMethodAddress);
        }
        Label doProlog = new Label();

        asm.cmpq(spillRegister, 0);
        asm.jcc(ConditionFlag.Equal, doProlog);

        AMD64Address codeBlobAddress = new AMD64Address(spillRegister, OptimizedCallTargetFieldInfo.getCodeBlobFieldOffset());
        asm.movq(spillRegister, codeBlobAddress);
        asm.cmpq(spillRegister, 0);
        asm.jcc(ConditionFlag.Equal, doProlog);

        AMD64Address verifiedEntryPointAddress = new AMD64Address(spillRegister, config.nmethodEntryOffset);
        asm.movq(spillRegister, verifiedEntryPointAddress);
        asm.jmp(spillRegister);

        asm.bind(doProlog);
    }
}
