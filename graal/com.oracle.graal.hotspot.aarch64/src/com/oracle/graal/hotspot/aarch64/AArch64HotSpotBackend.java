/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.aarch64;

import static com.oracle.graal.compiler.common.GraalOptions.ZapStackOnMethodEntry;
import static java.lang.reflect.Modifier.isStatic;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.CallingConvention.Type.JavaCallee;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.fp;

import java.lang.reflect.Field;
import java.util.Set;

import com.oracle.graal.asm.Assembler;
import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.aarch64.AArch64Address;
import com.oracle.graal.asm.aarch64.AArch64Assembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import com.oracle.graal.compiler.aarch64.AArch64NodeMatchRules;
import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.HotSpotHostBackend;
import com.oracle.graal.hotspot.meta.HotSpotForeignCallsProvider;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.aarch64.AArch64Call;
import com.oracle.graal.lir.aarch64.AArch64FrameMap;
import com.oracle.graal.lir.aarch64.AArch64FrameMapBuilder;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.asm.FrameContext;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

/**
 * HotSpot AArch64 specific backend.
 */
public class AArch64HotSpotBackend extends HotSpotHostBackend {

    public AArch64HotSpotBackend(HotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(config, runtime, providers);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AArch64FrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfigNonNull);
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new AArch64FrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(CallingConvention cc, LIRGenerationResult lirGenRes) {
        return new AArch64HotSpotLIRGenerator(getProviders(), config(), cc, lirGenRes);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(String compilationUnitName, LIR lir, FrameMapBuilder frameMapBuilder, ResolvedJavaMethod method, Object stub) {
        return new AArch64HotSpotLIRGenerationResult(compilationUnitName, lir, frameMapBuilder, stub);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new AArch64HotSpotNodeLIRBuilder(graph, lirGen, new AArch64NodeMatchRules(lirGen));
    }

    /**
     * Emits code to do stack overflow checking.
     *
     * @param afterFrameInit specifies if the stack pointer has already been adjusted to allocate
     *            the current frame
     */
    protected static void emitStackOverflowCheck(CompilationResultBuilder crb, int pagesToBang, boolean afterFrameInit) {
        if (pagesToBang > 0) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            int frameSize = crb.frameMap.totalFrameSize();
            if (frameSize > 0) {
                int lastFramePage = frameSize / UNSAFE.pageSize();
                // emit multiple stack bangs for methods with frames larger than a page
                for (int i = 0; i <= lastFramePage; i++) {
                    int disp = (i + pagesToBang) * UNSAFE.pageSize();
                    if (afterFrameInit) {
                        disp -= frameSize;
                    }
                    crb.blockComment("[stack overflow check]");
                    try (ScratchRegister sc = masm.getScratchRegister()) {
                        Register scratch = sc.getRegister();
                        AArch64Address address = masm.makeAddress(sp, -disp, scratch, 8, /* allowOverwrite */false);
                        masm.str(64, zr, address);
                    }
                }
            }
        }
    }

    private class HotSpotFrameContext implements FrameContext {
        final boolean isStub;

        HotSpotFrameContext(boolean isStub) {
            this.isStub = isStub;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            final int frameSize = frameMap.frameSize();
            final int totalFrameSize = frameMap.totalFrameSize();
            assert frameSize + 2 * crb.target.arch.getWordSize() == totalFrameSize : "total framesize should be framesize + 2 words";
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            if (!isStub && pagesToBang > 0) {
                emitStackOverflowCheck(crb, pagesToBang, false);
            }
            crb.blockComment("[method prologue]");

            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                // save link register and framepointer
                masm.mov(64, scratch, sp);
                AArch64Address address = AArch64Address.createPreIndexedImmediateAddress(scratch, -crb.target.arch.getWordSize());
                masm.str(64, lr, address);
                masm.str(64, fp, address);
                // Update framepointer
                masm.mov(64, fp, scratch);

                if (ZapStackOnMethodEntry.getValue()) {
                    int intSize = 4;
                    address = AArch64Address.createPreIndexedImmediateAddress(scratch, -intSize);
                    try (ScratchRegister sc2 = masm.getScratchRegister()) {
                        Register value = sc2.getRegister();
                        masm.mov(value, 0xC1C1C1C1);
                        for (int i = 0; i < frameSize; i += intSize) {
                            masm.str(32, value, address);
                        }
                    }
                    masm.mov(64, sp, scratch);
                } else {
                    if (AArch64MacroAssembler.isArithmeticImmediate(totalFrameSize)) {
                        masm.sub(64, sp, scratch, frameSize);
                    } else {
                        try (ScratchRegister sc2 = masm.getScratchRegister()) {
                            Register scratch2 = sc2.getRegister();
                            masm.mov(scratch2, frameSize);
                            masm.sub(64, sp, scratch, scratch2);
                        }
                    }
                }
            }
            crb.blockComment("[code body]");
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            crb.blockComment("[method epilogue]");
            final int frameSize = crb.frameMap.totalFrameSize();
            if (AArch64MacroAssembler.isArithmeticImmediate(frameSize)) {
                masm.add(64, sp, sp, frameSize);
            } else {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    masm.mov(scratch, frameSize);
                    masm.add(64, sp, sp, scratch);
                }
            }
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                // restore link register and framepointer
                masm.mov(64, scratch, sp);
                AArch64Address address = AArch64Address.createPostIndexedImmediateAddress(scratch, crb.target.arch.getWordSize());
                masm.ldr(64, fp, address);
                masm.ldr(64, lr, address);
                masm.mov(64, sp, scratch);
            }
        }

        @Override
        public boolean hasFrame() {
            return true;
        }
    }

    @Override
    protected Assembler createAssembler(FrameMap frameMap) {
        return new AArch64MacroAssembler(getTarget());
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRen, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        AArch64HotSpotLIRGenerationResult gen = (AArch64HotSpotLIRGenerationResult) lirGenRen;
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";

        Stub stub = gen.getStub();
        Assembler masm = createAssembler(frameMap);
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null);

        CompilationResultBuilder crb = new CompilationResultBuilder(getCodeCache(), getForeignCalls(), frameMap, masm, frameContext, compilationResult);
        crb.setTotalFrameSize(frameMap.frameSize());
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
        AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.getRegisterConfig();
        HotSpotVMConfig config = config();
        Label verifiedStub = new Label();

        emitCodePrefix(crb, installedCodeOwner, masm, regConfig, config, verifiedStub);
        emitCodeBody(crb, lir);
        emitCodeSuffix(crb, masm, config, frameMap);
    }

    private void emitCodePrefix(CompilationResultBuilder crb, ResolvedJavaMethod installedCodeOwner, AArch64MacroAssembler masm, RegisterConfig regConfig, HotSpotVMConfig config, Label verifiedStub) {
        HotSpotProviders providers = getProviders();
        if (installedCodeOwner != null && !isStatic(installedCodeOwner.getModifiers())) {
            crb.recordMark(config.MARKID_UNVERIFIED_ENTRY);
            CallingConvention cc = regConfig.getCallingConvention(JavaCallee, null, new JavaType[]{providers.getMetaAccess().lookupJavaType(Object.class)}, getTarget(), false);
            // See definition of IC_Klass in c1_LIRAssembler_aarch64.cpp
            // equal to scratch(1) careful!
            Register inlineCacheKlass = AArch64HotSpotRegisterConfig.inlineCacheRegister;
            Register receiver = asRegister(cc.getArgument(0));
            int transferSize = config.useCompressedClassPointers ? 4 : 8;
            AArch64Address klassAddress = masm.makeAddress(receiver, config.hubOffset, transferSize);

            // Are r10 and r11 available scratch registers here? One would hope so.
            Register klass = r10;
            if (config.useCompressedClassPointers) {
                masm.ldr(32, klass, klassAddress);
                AArch64HotSpotMove.decodeKlassPointer(masm, klass, klass, providers.getRegisters().getHeapBaseRegister(), config.getKlassEncoding());
            } else {
                masm.ldr(64, klass, klassAddress);
            }
            masm.cmp(64, inlineCacheKlass, klass);
            // conditional jumps have a much lower range than unconditional ones, which can be a
            // problem because
            // the miss handler could be out of range.
            masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, verifiedStub);
            AArch64Call.directJmp(crb, masm, getForeignCalls().lookupForeignCall(IC_MISS_HANDLER));
        }
        masm.align(config.codeEntryAlignment);
        crb.recordMark(config.MARKID_OSR_ENTRY);
        masm.bind(verifiedStub);
        crb.recordMark(config.MARKID_VERIFIED_ENTRY);
    }

    private static void emitCodeBody(CompilationResultBuilder crb, LIR lir) {
        crb.emit(lir);
    }

    private void emitCodeSuffix(CompilationResultBuilder crb, AArch64MacroAssembler masm, HotSpotVMConfig config, FrameMap frameMap) {
        HotSpotProviders providers = getProviders();
        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        if (!frameContext.isStub) {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                HotSpotForeignCallsProvider foreignCalls = providers.getForeignCalls();
                crb.recordMark(config.MARKID_EXCEPTION_HANDLER_ENTRY);
                ForeignCallLinkage linkage = foreignCalls.lookupForeignCall(EXCEPTION_HANDLER);
                Register helper = AArch64Call.isNearCall(linkage) ? null : scratch;
                AArch64Call.directCall(crb, masm, linkage, helper, null);

                crb.recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
                linkage = foreignCalls.lookupForeignCall(DEOPTIMIZATION_HANDLER);
                helper = AArch64Call.isNearCall(linkage) ? null : scratch;
                AArch64Call.directCall(crb, masm, linkage, helper, null);
            }
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries
            assert !frameMap.accessesCallerFrame();
        }
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AArch64HotSpotRegisterAllocationConfig(registerConfigNonNull);
    }

    private static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }
}
