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
package com.oracle.graal.hotspot.sparc;

import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.compiler.gen.LIRGenerator;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.nodes.*;

import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static java.lang.reflect.Modifier.*;

/**
 * HotSpot SPARC specific backend.
 */
public class SPARCHotSpotBackend extends HotSpotHostBackend {

    private static final Unsafe unsafe = Unsafe.getUnsafe();

    public SPARCHotSpotBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(runtime, providers);
    }

    @Override
    public boolean shouldAllocateRegisters() {
        return true;
    }

    @Override
    public FrameMap newFrameMap() {
        return new SPARCFrameMap(getProviders().getCodeCache());
    }

    @Override
    public LIRGenerator newLIRGenerator(StructuredGraph graph, FrameMap frameMap, CallingConvention cc, LIR lir) {
        return new SPARCHotSpotLIRGenerator(graph, getProviders(), getRuntime().getConfig(), frameMap, cc, lir);
    }

    /**
     * Emits code to do stack overflow checking.
     * 
     * @param afterFrameInit specifies if the stack pointer has already been adjusted to allocate
     *            the current frame
     */
    protected static void emitStackOverflowCheck(TargetMethodAssembler tasm, boolean afterFrameInit) {
        if (StackShadowPages.getValue() > 0) {
            SPARCMacroAssembler masm = (SPARCMacroAssembler) tasm.asm;
            final int frameSize = tasm.frameMap.totalFrameSize();
            if (frameSize > 0) {
                int lastFramePage = frameSize / unsafe.pageSize();
                // emit multiple stack bangs for methods with frames larger than a page
                for (int i = 0; i <= lastFramePage; i++) {
                    int disp = (i + StackShadowPages.getValue()) * unsafe.pageSize();
                    if (afterFrameInit) {
                        disp -= frameSize;
                    }
                    tasm.blockComment("[stack overflow check]");
                    // Use SPARCAddress to get the final displacement including the stack bias.
                    SPARCAddress address = new SPARCAddress(sp, -disp);
                    if (SPARCAssembler.isSimm13(address.getDisplacement())) {
                        new Stx(g0, address).emit(masm);
                    } else {
                        new Setx(address.getDisplacement(), g3).emit(masm);
                        new Stx(g0, new SPARCAddress(sp, g3)).emit(masm);
                    }
                }
            }
        }
    }

    public class HotSpotFrameContext implements FrameContext {

        final boolean isStub;

        HotSpotFrameContext(boolean isStub) {
            this.isStub = isStub;
        }

        @Override
        public void enter(TargetMethodAssembler tasm) {
            final int frameSize = tasm.frameMap.totalFrameSize();

            SPARCMacroAssembler masm = (SPARCMacroAssembler) tasm.asm;
            if (!isStub) {
                emitStackOverflowCheck(tasm, false);
            }
            new Save(sp, -frameSize, sp).emit(masm);

            if (ZapStackOnMethodEntry.getValue()) {
                final int slotSize = 8;
                for (int i = 0; i < frameSize / slotSize; ++i) {
                    // 0xC1C1C1C1
                    new Stx(g0, new SPARCAddress(sp, i * slotSize)).emit(masm);
                }
            }
        }

        @Override
        public void leave(TargetMethodAssembler tasm) {
            SPARCMacroAssembler masm = (SPARCMacroAssembler) tasm.asm;
            new RestoreWindow().emit(masm);
        }
    }

    @Override
    protected AbstractAssembler createAssembler(FrameMap frameMap) {
        return new SPARCMacroAssembler(getTarget(), frameMap.registerConfig);
    }

    @Override
    public TargetMethodAssembler newAssembler(LIRGenerator lirGen, CompilationResult compilationResult) {
        SPARCHotSpotLIRGenerator gen = (SPARCHotSpotLIRGenerator) lirGen;
        FrameMap frameMap = gen.frameMap;
        assert gen.deoptimizationRescueSlot == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";

        Stub stub = gen.getStub();
        AbstractAssembler masm = createAssembler(frameMap);
        // On SPARC we always use stack frames.
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null);
        TargetMethodAssembler tasm = new TargetMethodAssembler(getProviders().getCodeCache(), getProviders().getForeignCalls(), frameMap, masm, frameContext, compilationResult);
        tasm.setFrameSize(frameMap.frameSize());
        StackSlot deoptimizationRescueSlot = gen.deoptimizationRescueSlot;
        if (deoptimizationRescueSlot != null && stub == null) {
            tasm.compilationResult.setCustomStackAreaOffset(frameMap.offsetForStackSlot(deoptimizationRescueSlot));
        }

        if (stub != null) {
            // SPARC stubs always enter a frame which saves the registers.
            final Set<Register> definedRegisters = new HashSet<>();
            stub.initDestroyedRegisters(definedRegisters);
        }

        return tasm;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, LIRGenerator lirGen, ResolvedJavaMethod installedCodeOwner) {
        SPARCMacroAssembler masm = (SPARCMacroAssembler) tasm.asm;
        FrameMap frameMap = tasm.frameMap;
        RegisterConfig regConfig = frameMap.registerConfig;
        HotSpotVMConfig config = getRuntime().getConfig();
        Label unverifiedStub = installedCodeOwner == null || isStatic(installedCodeOwner.getModifiers()) ? null : new Label();

        // Emit the prefix

        if (unverifiedStub != null) {
            tasm.recordMark(Marks.MARK_UNVERIFIED_ENTRY);
            // We need to use JavaCall here because we haven't entered the frame yet.
            CallingConvention cc = regConfig.getCallingConvention(JavaCall, null, new JavaType[]{getProviders().getMetaAccess().lookupJavaType(Object.class)}, getTarget(), false);
            Register inlineCacheKlass = g5; // see MacroAssembler::ic_call
            Register scratch = g3;
            Register receiver = asRegister(cc.getArgument(0));
            SPARCAddress src = new SPARCAddress(receiver, config.hubOffset);

            new Ldx(src, scratch).emit(masm);
            new Cmp(scratch, inlineCacheKlass).emit(masm);
            new Bpne(CC.Xcc, unverifiedStub).emit(masm);
            new Nop().emit(masm);  // delay slot
        }

        masm.align(config.codeEntryAlignment);
        tasm.recordMark(Marks.MARK_OSR_ENTRY);
        tasm.recordMark(Marks.MARK_VERIFIED_ENTRY);

        // Emit code for the LIR
        lirGen.lir.emitCode(tasm);

        HotSpotFrameContext frameContext = (HotSpotFrameContext) tasm.frameContext;
        HotSpotForeignCallsProvider foreignCalls = getProviders().getForeignCalls();
        if (frameContext != null && !frameContext.isStub) {
            tasm.recordMark(Marks.MARK_EXCEPTION_HANDLER_ENTRY);
            SPARCCall.directCall(tasm, masm, foreignCalls.lookupForeignCall(EXCEPTION_HANDLER), null, false, null);
            tasm.recordMark(Marks.MARK_DEOPT_HANDLER_ENTRY);
            SPARCCall.directCall(tasm, masm, foreignCalls.lookupForeignCall(DEOPT_HANDLER), null, false, null);
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries
            assert !frameMap.accessesCallerFrame() : lirGen.getGraph();
        }

        if (unverifiedStub != null) {
            masm.bind(unverifiedStub);
            Register scratch = g3;
            SPARCCall.indirectJmp(tasm, masm, scratch, foreignCalls.lookupForeignCall(IC_MISS_HANDLER));
        }
    }
}
