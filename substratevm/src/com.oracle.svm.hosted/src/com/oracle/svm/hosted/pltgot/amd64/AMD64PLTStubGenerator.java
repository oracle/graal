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
package com.oracle.svm.hosted.pltgot.amd64;

import java.util.ArrayList;
import java.util.List;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.graal.amd64.SubstrateAMD64Backend;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.pltgot.GOTAccess;
import com.oracle.svm.core.pltgot.amd64.AMD64MethodAddressResolutionDispatcher;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.pltgot.HostedPLTGOTConfiguration;
import com.oracle.svm.hosted.pltgot.PLTSectionSupport;
import com.oracle.svm.hosted.pltgot.PLTStubGenerator;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Generates the contents of the PLT section for the PLT/GOT mechanism.
 *
 * Every method that can be dynamically resolved through the PLT/GOT mechanism has its unique PLT
 * stub. We handle method resolution for virtual calls and direct calls differently.
 *
 *
 * 
 * When resolving a virtual method the corresponding PLT stub loads the GOT entry associated with
 * the called method via r14 - (GOT_ENTRY + 1) * 8 into the return value register (ie. rax). If the
 * method is already resolved then the second instruction in the PLT stub will jump directly to the
 * resolved method. Otherwise, because the GOT entries are initialized to point to the third
 * instruction in the corresponding PLT stub, the second instruction will jump to the instruction
 * below that loads the gotEntry id into the return value register and then the fourth instruction
 * jumps to the {@link AMD64MethodAddressResolutionDispatcher#resolveMethodAddress(long)} that
 * resolves the method with a given got entry.
 * 
 * When resolving direct calls we jump directly to the second part of the PLT stub (third
 * instruction) from the callsite because we rewrite the direct calls to the first two instructions
 * of the corresponding PLT stub. That way, for direct calls, we only jump to the PLT stub if the
 * method wasn't already resolved.
 *
 * The {@link AMD64MethodAddressResolutionDispatcher#resolveMethodAddress(long)} uses the
 * {@link SubstrateCallingConventionKind#ForwardReturnValue} which expects its one and only argument
 * to be in the return value register. This calling convention for the method resolver enables us to
 * avoid having to spill the callers first argument to the stack. Also, we can reuse the same
 * register as scratch for the second instruction in the PLT stub because the return value register
 * is caller saved.
 *
 * An example of the `.svm_plt` section that contains 2 methods that are called via PLT/GOT (on
 * Linux AMD64):
 *
 * <pre>
 * {@code
 *0000000000355000 <svm_plt__ZN45java.util.concurrent.locks.ReentrantLock$Sync4lockEJvv>:
 *   355000:  mov      rax,QWORD PTR [r14-0x8] # <- virtual call jumps here.
 *   355004:  jmp      rax                     # <- if the method is resolved jump to it, else jump to the instruction below.
 *   355006:  mov      rax,0x0                 # <- direct call jumps here only once if the method hasn't already been resolved
 *   35500b:  jmp      19e700 <AMD64MethodAddressResolutionDispatcher.resolveMethodAddressEJvl>
 *
 * 0000000000355010 <svm_plt__ZN69java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject20awaitUninterruptiblyEJvv>:
 *   355010:  mov      rax,QWORD PTR [r14-0x10] # load the address from the GOT table. (`35501b` if the method wasn't resolved already, else method's address)
 *   355014:  jmp      rax
 *   355016:  mov      rax,0x1
 *   35501b:  jmp      19e700 <AMD64MethodAddressResolutionDispatcher.resolveMethodAddressEJvl>
 * }
 * </pre>
 */
public class AMD64PLTStubGenerator implements PLTStubGenerator {
    private List<Integer> resolverPatchOffsets = new ArrayList<>();
    private int resolverKindAddend;
    private ObjectFile.RelocationKind resolverPatchRelocationKind = null;

    @Override
    public byte[] generatePLT(SharedMethod[] got, SubstrateBackend substrateBackend) {
        HostedPLTGOTConfiguration configuration = HostedPLTGOTConfiguration.singleton();
        VMError.guarantee(configuration.getArchSpecificResolverAsHostedMethod().getCallingConventionKind().equals(SubstrateCallingConventionKind.ForwardReturnValue),
                        "AMD64PLTStubGenerator assumes that %s is using %s ",
                        configuration.getArchSpecificResolverAsHostedMethod().format("%H.%n(%p)"), SubstrateCallingConventionKind.ForwardReturnValue.name());

        SubstrateAMD64Backend amd64Backend = (SubstrateAMD64Backend) substrateBackend;
        RegisterConfig registerConfig = amd64Backend.getCodeCache().getRegisterConfig();
        Register register = configuration.getGOTPassingRegister(registerConfig);

        AMD64MacroAssembler asm = amd64Backend.createAssemblerNoOptions();
        PLTSectionSupport support = HostedPLTGOTConfiguration.singleton().getPLTSectionSupport();

        asm.setCodePatchingAnnotationConsumer(this::recordResolverCallForPatching);
        for (int gotEntryNo = 0; gotEntryNo < got.length; ++gotEntryNo) {
            HostedMethod method = (HostedMethod) got[gotEntryNo];

            /*
             * This is the method's call target. The GOT will be read to determine the method's
             * actual address.
             */
            int pltStubStart = asm.position();
            support.recordMethodPLTStubStart(method, pltStubStart);

            int gotEntryOffset = GOTAccess.getGotEntryOffsetFromHeapRegister(gotEntryNo);
            asm.maybeEmitIndirectTargetMarker();
            asm.movq(register, new AMD64Address(ReservedRegisters.singleton().getHeapBaseRegister(), gotEntryOffset));
            asm.jmp(register);

            /*
             * This is the initial target of the jmp directly above. Calls the resolver stub with
             * the key for this method.
             */
            support.recordMethodPLTStubResolverOffset(method, asm.position() - pltStubStart);
            asm.maybeEmitIndirectTargetMarker();
            asm.movl(register, gotEntryNo);
            /*
             * We patch this later to the
             * AMD64MethodAddressResolutionDispatcher#resolveMethodAddress(long).
             */
            asm.jmp();
        }

        return asm.close(true);
    }

    @Override
    public void markResolverMethodPatch(ObjectFile.ProgbitsSectionImpl pltBuffer, ResolvedJavaMethod resolverMethod) {
        for (int resolverPatchOffset : resolverPatchOffsets) {
            pltBuffer.markRelocationSite(resolverPatchOffset, resolverPatchRelocationKind, NativeImage.localSymbolNameForMethod(resolverMethod), resolverKindAddend);
        }
    }

    private void recordResolverCallForPatching(Assembler.CodeAnnotation a) {
        assert a instanceof AMD64BaseAssembler.OperandDataAnnotation;
        AMD64BaseAssembler.OperandDataAnnotation annotation = (AMD64BaseAssembler.OperandDataAnnotation) a;
        resolverPatchOffsets.add(annotation.operandPosition);
        resolverKindAddend = -annotation.operandSize;
        resolverPatchRelocationKind = ObjectFile.RelocationKind.getPCRelative(annotation.operandSize);
    }
}
