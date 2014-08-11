/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.UnsafeAccess.*;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.HotSpotCodeCacheProvider.MarkId;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nfi.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.nfi.api.*;

/**
 * HotSpot AMD64 specific backend.
 */
public class AMD64HotSpotBackend extends HotSpotHostBackend {

    public AMD64HotSpotBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(runtime, providers);
    }

    @Override
    public boolean shouldAllocateRegisters() {
        return true;
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new AMD64FrameMap(getCodeCache(), registerConfig);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(CallingConvention cc, LIRGenerationResult lirGenRes) {
        return new AMD64HotSpotLIRGenerator(getProviders(), getRuntime().getConfig(), cc, lirGenRes);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(LIR lir, FrameMap frameMap, ResolvedJavaMethod method, Object stub) {
        return new AMD64HotSpotLIRGenerationResult(lir, frameMap, stub);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new AMD64HotSpotNodeLIRBuilder(graph, lirGen);
    }

    @Override
    public BytecodeLIRBuilder newBytecodeLIRBuilder(LIRGeneratorTool gen, BytecodeParserTool parser) {
        return new AMD64HotSpotBytecodeLIRBuilder(gen, parser);

    }

    /**
     * Emits code to do stack overflow checking.
     *
     * @param afterFrameInit specifies if the stack pointer has already been adjusted to allocate
     *            the current frame
     * @param isVerifiedEntryPoint specifies if the code buffer is currently at the verified entry
     *            point
     */
    protected static void emitStackOverflowCheck(CompilationResultBuilder crb, int pagesToBang, boolean afterFrameInit, boolean isVerifiedEntryPoint) {
        if (pagesToBang > 0) {

            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            int frameSize = crb.frameMap.frameSize();
            if (frameSize > 0) {
                int lastFramePage = frameSize / unsafe.pageSize();
                // emit multiple stack bangs for methods with frames larger than a page
                for (int i = 0; i <= lastFramePage; i++) {
                    int disp = (i + pagesToBang) * unsafe.pageSize();
                    if (afterFrameInit) {
                        disp -= frameSize;
                    }
                    crb.blockComment("[stack overflow check]");
                    int pos = asm.position();
                    asm.movq(new AMD64Address(rsp, -disp), AMD64.rax);
                    assert i > 0 || !isVerifiedEntryPoint || asm.position() - pos >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
                }
            }
        }
    }

    /**
     * The size of the instruction used to patch the verified entry point of an nmethod when the
     * nmethod is made non-entrant or a zombie (e.g. during deopt or class unloading). The first
     * instruction emitted at an nmethod's verified entry point must be at least this length to
     * ensure mt-safe patching.
     */
    public static final int PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE = 5;

    /**
     * Emits code at the verified entry point and return point(s) of a method.
     */
    class HotSpotFrameContext implements FrameContext {

        final boolean isStub;
        final boolean omitFrame;

        HotSpotFrameContext(boolean isStub, boolean omitFrame) {
            this.isStub = isStub;
            this.omitFrame = omitFrame;
        }

        public boolean hasFrame() {
            return !omitFrame;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            int frameSize = frameMap.frameSize();
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            if (omitFrame) {
                if (!isStub) {
                    asm.nop(PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE);
                }
            } else {
                int verifiedEntryPointOffset = asm.position();
                if (!isStub && pagesToBang > 0) {
                    emitStackOverflowCheck(crb, pagesToBang, false, true);
                    assert asm.position() - verifiedEntryPointOffset >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
                }
                if (!isStub && asm.position() == verifiedEntryPointOffset) {
                    asm.subqWide(rsp, frameSize);
                    assert asm.position() - verifiedEntryPointOffset >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
                } else {
                    asm.decrementq(rsp, frameSize);
                }
                if (ZapStackOnMethodEntry.getValue()) {
                    final int intSize = 4;
                    for (int i = 0; i < frameSize / intSize; ++i) {
                        asm.movl(new AMD64Address(rsp, i * intSize), 0xC1C1C1C1);
                    }
                }
                CalleeSaveLayout csl = frameMap.registerConfig.getCalleeSaveLayout();
                if (csl != null && csl.size != 0) {
                    int frameToCSA = frameMap.offsetToCalleeSaveArea();
                    assert frameToCSA >= 0;
                    asm.save(csl, frameToCSA);
                }
            }
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            if (!omitFrame) {
                AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
                CalleeSaveLayout csl = crb.frameMap.registerConfig.getCalleeSaveLayout();

                if (csl != null && csl.size != 0) {
                    // saved all registers, restore all registers
                    int frameToCSA = crb.frameMap.offsetToCalleeSaveArea();
                    asm.restore(csl, frameToCSA);
                }

                int frameSize = crb.frameMap.frameSize();
                asm.incrementq(rsp, frameSize);
            }
        }
    }

    @Override
    protected Assembler createAssembler(FrameMap frameMap) {
        return new AMD64MacroAssembler(getTarget(), frameMap.registerConfig);
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRen, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        // Omit the frame if the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no deoptimization points
        // - makes no foreign calls (which require an aligned stack)
        AMD64HotSpotLIRGenerationResult gen = (AMD64HotSpotLIRGenerationResult) lirGenRen;
        FrameMap frameMap = gen.getFrameMap();
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";
        boolean omitFrame = CanOmitFrame.getValue() && !frameMap.frameNeedsAllocating() && !lir.hasArgInCallerFrame() && !gen.hasForeignCall();

        Stub stub = gen.getStub();
        Assembler masm = createAssembler(frameMap);
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null, omitFrame);
        CompilationResultBuilder crb = factory.createBuilder(getCodeCache(), getForeignCalls(), frameMap, masm, frameContext, compilationResult);
        crb.setTotalFrameSize(frameMap.totalFrameSize());
        StackSlot deoptimizationRescueSlot = gen.getDeoptimizationRescueSlot();
        if (deoptimizationRescueSlot != null && stub == null) {
            crb.compilationResult.setCustomStackAreaOffset(frameMap.offsetForStackSlot(deoptimizationRescueSlot));
        }

        if (stub != null) {
            Set<Register> definedRegisters = gatherDefinedRegisters(lir);
            updateStub(stub, definedRegisters, gen.getCalleeSaveInfo(), frameMap);
        }

        return crb;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.registerConfig;
        HotSpotVMConfig config = getRuntime().getConfig();
        Label verifiedEntry = new Label();

        // Emit the prefix
        emitCodePrefix(installedCodeOwner, crb, asm, regConfig, config, verifiedEntry);

        // Emit code for the LIR
        emitCodeBody(installedCodeOwner, crb, lir);

        // Emit the suffix
        emitCodeSuffix(installedCodeOwner, crb, asm, frameMap);
    }

    /**
     * Emits the code prior to the verified entry point.
     *
     * @param installedCodeOwner see {@link Backend#emitCode}
     */
    public void emitCodePrefix(ResolvedJavaMethod installedCodeOwner, CompilationResultBuilder crb, AMD64MacroAssembler asm, RegisterConfig regConfig, HotSpotVMConfig config, Label verifiedEntry) {
        HotSpotProviders providers = getProviders();
        if (installedCodeOwner != null && !installedCodeOwner.isStatic()) {
            MarkId.recordMark(crb, MarkId.UNVERIFIED_ENTRY);
            CallingConvention cc = regConfig.getCallingConvention(JavaCallee, null, new JavaType[]{providers.getMetaAccess().lookupJavaType(Object.class)}, getTarget(), false);
            Register inlineCacheKlass = rax; // see definition of IC_Klass in
                                             // c1_LIRAssembler_x86.cpp
            Register receiver = asRegister(cc.getArgument(0));
            AMD64Address src = new AMD64Address(receiver, config.hubOffset);

            if (config.useCompressedClassPointers) {
                Register register = r10;
                AMD64HotSpotMove.decodeKlassPointer(asm, register, providers.getRegisters().getHeapBaseRegister(), src, config.getKlassEncoding());
                if (config.narrowKlassBase != 0) {
                    // The heap base register was destroyed above, so restore it
                    asm.movq(providers.getRegisters().getHeapBaseRegister(), config.narrowOopBase);
                }
                asm.cmpq(inlineCacheKlass, register);
            } else {
                asm.cmpq(inlineCacheKlass, src);
            }
            AMD64Call.directConditionalJmp(crb, asm, getForeignCalls().lookupForeignCall(IC_MISS_HANDLER), ConditionFlag.NotEqual);
        }

        asm.align(config.codeEntryAlignment);
        MarkId.recordMark(crb, MarkId.OSR_ENTRY);
        asm.bind(verifiedEntry);
        MarkId.recordMark(crb, MarkId.VERIFIED_ENTRY);
    }

    /**
     * Emits the code which starts at the verified entry point.
     *
     * @param installedCodeOwner see {@link Backend#emitCode}
     */
    public void emitCodeBody(ResolvedJavaMethod installedCodeOwner, CompilationResultBuilder crb, LIR lir) {
        crb.emit(lir);
    }

    /**
     * @param installedCodeOwner see {@link Backend#emitCode}
     */
    public void emitCodeSuffix(ResolvedJavaMethod installedCodeOwner, CompilationResultBuilder crb, AMD64MacroAssembler asm, FrameMap frameMap) {
        HotSpotProviders providers = getProviders();
        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        if (!frameContext.isStub) {
            HotSpotForeignCallsProvider foreignCalls = providers.getForeignCalls();
            MarkId.recordMark(crb, MarkId.EXCEPTION_HANDLER_ENTRY);
            AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(EXCEPTION_HANDLER), null, false, null);
            MarkId.recordMark(crb, MarkId.DEOPT_HANDLER_ENTRY);
            AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(DEOPTIMIZATION_HANDLER), null, false, null);
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries

            if (frameContext.omitFrame) {
                // Cannot access slots in caller's frame if my frame is omitted
                assert !frameMap.accessesCallerFrame();
            }
        }
    }

    /**
     * Called from the VM.
     */
    public static NativeFunctionInterface createNativeFunctionInterface() {
        HotSpotVMConfig config = HotSpotGraalRuntime.runtime().getConfig();
        RawNativeCallNodeFactory factory = new RawNativeCallNodeFactory() {
            public FixedWithNextNode createRawCallNode(Kind returnType, Constant functionPointer, ValueNode... args) {
                return new AMD64RawNativeCallNode(returnType, functionPointer, args);
            }
        };
        Backend backend = HotSpotGraalRuntime.runtime().getBackend(AMD64.class);
        return new HotSpotNativeFunctionInterface(HotSpotGraalRuntime.runtime().getHostProviders(), factory, backend, config.dllLoad, config.dllLookup, config.rtldDefault);
    }

}
