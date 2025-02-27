/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pltgot.aarch64;

import static com.oracle.objectfile.ObjectFile.RelocationKind.AARCH64_R_AARCH64_ADD_ABS_LO12_NC;
import static com.oracle.objectfile.ObjectFile.RelocationKind.AARCH64_R_AARCH64_ADR_PREL_PG_HI21;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.createImmediateAddress;
import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.vm.ci.aarch64.AArch64.sp;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptimizationSlotPacking;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.GOTAccess;
import com.oracle.svm.core.pltgot.aarch64.AArch64MethodAddressResolutionDispatcher;
import com.oracle.svm.core.aarch64.SubstrateAArch64MacroAssembler;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.pltgot.HostedPLTGOTConfiguration;
import com.oracle.svm.hosted.pltgot.PLTSectionSupport;
import com.oracle.svm.hosted.pltgot.PLTStubGenerator;
import com.oracle.svm.hosted.pltgot.amd64.AMD64PLTStubGenerator;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Generates the contents of the PLT section for the PLT/GOT mechanism.
 *
 * Please first see {@link AMD64PLTStubGenerator} for more details on how PLT/GOT mechanism works in
 * general.
 *
 * AArch64 and AMD64 .svm_plt sections are different because on AArch64 we can't use the
 * {@code ForwardReturnValue} calling convention for the
 * {@link AArch64MethodAddressResolutionDispatcher#resolveMethodAddress()} without spilling the
 * register x0, because x0 is both the first parameter register and the return value register.
 * That's why we pass the method's GOT entry index via the (unused) deopt frame handle slot on the
 * stack. The .svm_plt stub at the beginning of the section stores the got entry just after the
 * return address, which is the deopt frame handle slot, and then we jump to the
 * {@link AArch64MethodAddressResolutionDispatcher#resolveMethodAddress()}. As there are other users
 * of the deopt slot, an encoding is used (see{@link DeoptimizationSlotPacking}).
 *
 * The AArch64 variant has a common svm_plt stub to avoid repeating the instruction that stores the
 * got entry at the deopt frame handle slot and the instructions that load the address of the
 * resolver method and jump to it.
 *
 * An example of an `.svm_plt` section that contains 2 methods that are called via PLT/GOT on
 * darwin-aarch64:
 * 
 * <pre>
 * 0x7f0000 <.svm_plt>:
 * 0x7f0000:  ldur x8, [sp]
 * 0x7f0004:  and x8, x8, #0xff00000000000000
 * 0x7f0008:  orr x9, x9, x8
 * 0x7f000c:  adrp x8, 0x5df000
 * 0x7f0010:  add x8, x8, #0xdd0
 * 0x7f0014:  stur x9, [sp]        @ store gotEntry at the deopt frame handle slot
 * 0x7f0018:  br x8                @ Jumps to <AArch64MethodAddressResolutionDispatcher.resolveMethodAddress()void>
 * 0x7f001c <svm_plt__ZN45java.util.concurrent.locks.ReentrantLock$Sync4lockEJvv>:
 * 0x7f001c:  ldr x9, [x27,#-8]    @ <--- We jump here from the virtual method call site
 * 0x7f0020:  br x9                @ Jumps to the resolved method, or to the line below if the method wasn't resolved.
 * 0x7f0024:  mov w9, wzr          @ <---- We jump here from the direct method call site. This line loads the gotEntry id
 * 0x7f0028:  b 0x7f0000 <.svm_plt>
 * 0x7f002c <svm_plt__ZN69java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject20awaitUninterruptiblyEJvv>:
 * 0x7f002c:  ldr x9, [x27,#-16]
 * 0x7f0030:  br x9
 * 0x7f0034:  orr w9, wzr, #0x1
 * 0x7f0038:  b 0x7f0000 <.svm_plt>
 * 0x7f003c <svm_plt__ZN53java.util.concurrent.locks.AbstractQueuedSynchronizer7releaseEJ7booleani>:
 * 0x7f003c:  ldr x9, [x27,#-24]
 * 0x7f0040:  br x9
 * 0x7f0044:  orr w9, wzr, #0x2
 * 0x7f0048:  b 0x7f0000 <.svm_plt>
 * </pre>
 */
public class AArch64PLTStubGenerator implements PLTStubGenerator {
    private int resolverAddressLoadOffset = -1;

    @Override
    public byte[] generatePLT(SharedMethod[] got, SubstrateBackend substrateBackend) {
        AArch64MacroAssembler masm = new SubstrateAArch64MacroAssembler(ConfigurationValues.getTarget());
        Label pltStart = new Label();
        masm.bind(pltStart);
        try (AArch64MacroAssembler.ScratchRegister scratchRegister1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
            Register resolverJmpRegister = scratchRegister1.getRegister();
            Register gotEntryPassingRegister = scratchRegister2.getRegister();

            generateResolverCallStub(masm, gotEntryPassingRegister, resolverJmpRegister);

            PLTSectionSupport support = HostedPLTGOTConfiguration.singleton().getPLTSectionSupport();
            for (int gotEntryNo = 0; gotEntryNo < got.length; ++gotEntryNo) {
                HostedMethod method = (HostedMethod) got[gotEntryNo];
                int pltStubStart = masm.position();

                /* Start of PLT stub for this GOT entry. */
                support.recordMethodPLTStubStart(method, pltStubStart);

                int gotEntryOffset = GOTAccess.getGotEntryOffsetFromHeapRegister(gotEntryNo);
                Register heapReg = ReservedRegisters.singleton().getHeapBaseRegister();
                AArch64Address addr = masm.makeAddress(64, heapReg, gotEntryOffset, gotEntryPassingRegister);

                masm.maybeEmitIndirectTargetMarker();
                masm.ldr(64, gotEntryPassingRegister, addr);
                masm.jmp(gotEntryPassingRegister);

                /*
                 * This is used as initial entry in the GOT, so that on first access this entry is
                 * going to be resolved.
                 */
                support.recordMethodPLTStubResolverOffset(method, masm.position() - pltStubStart);
                masm.maybeEmitIndirectTargetMarker();
                masm.mov(gotEntryPassingRegister, gotEntryNo);
                masm.jmp(pltStart);
            }
        }
        return masm.close(true);
    }

    @Override
    public void markResolverMethodPatch(ObjectFile.ProgbitsSectionImpl pltBuffer, ResolvedJavaMethod resolverMethod) {
        pltBuffer.markRelocationSite(resolverAddressLoadOffset, AARCH64_R_AARCH64_ADR_PREL_PG_HI21, NativeImage.localSymbolNameForMethod(resolverMethod), 0);
        pltBuffer.markRelocationSite(resolverAddressLoadOffset + 4, AARCH64_R_AARCH64_ADD_ABS_LO12_NC, NativeImage.localSymbolNameForMethod(resolverMethod), 0);
    }

    public void generateResolverCallStub(AArch64MacroAssembler masm, Register gotEntryPassingRegister, Register jmpTarget) {
        masm.setCodePatchingAnnotationConsumer(this::recordResolverCallForPatching);

        /*
         * GR-54839: Upper byte of deoptSlot may be used by leaveInterpreterStub, therefore an
         * encoding is used for this word.
         */
        Register scratch = jmpTarget;
        masm.ldr(64, scratch, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, sp, 0));
        masm.and(64, scratch, scratch, DeoptimizationSlotPacking.MASK_VARIABLE_FRAMESIZE);
        masm.orr(64, gotEntryPassingRegister, gotEntryPassingRegister, scratch);

        /*
         * use indirect jump to avoid problems around branch islands (displacement larger than
         * +/-128MB)
         */
        masm.adrpAdd(jmpTarget);
        masm.str(64, gotEntryPassingRegister, createImmediateAddress(64, IMMEDIATE_SIGNED_UNSCALED, sp, 0));
        masm.jmp(jmpTarget);
    }

    private void recordResolverCallForPatching(Assembler.CodeAnnotation a) {
        if (resolverAddressLoadOffset != -1) {
            return;
        }
        assert a instanceof AArch64MacroAssembler.AdrpAddMacroInstruction;
        AArch64MacroAssembler.AdrpAddMacroInstruction annotation = (AArch64MacroAssembler.AdrpAddMacroInstruction) a;
        resolverAddressLoadOffset = annotation.instructionPosition;
    }
}
