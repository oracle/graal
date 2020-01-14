/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Arm Limited. All rights reserved.
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
package com.oracle.svm.graal.meta.aarch64;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Map.Entry;

import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.CodeSynchronizationOperations;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.meta.RuntimeCodeInstaller.RuntimeCodeInstallerPlatformHelper;

import jdk.vm.ci.aarch64.AArch64;

@AutomaticFeature
@Platforms(Platform.AARCH64.class)
class AArch64RuntimeCodeInstallerPlatformHelperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeCodeInstallerPlatformHelper.class, new AArch64RuntimeCodeInstallerPlatformHelper());
    }
}

public class AArch64RuntimeCodeInstallerPlatformHelper implements RuntimeCodeInstallerPlatformHelper {
    /**
     * The size for trampoline jumps. The sequence of instructions is:
     * <ul>
     * <li>adrp scratch, #offset:hi_21</li>
     * <li>ldr scratch, [scratch{, #offset:lo_12}]</li>
     * <li>br [scratch]</li>
     * </ul>
     *
     * <p>
     * Trampoline jumps are added immediately after the method code, where each trampoline needs 12
     * bytes. The trampoline jumps reference the 8-byte destination addresses, which are allocated
     * after the trampolines.
     */
    @Override
    public int getTrampolineCallSize() {
        return 12;
    }

    /**
     * Checking if the pc displacement is within a signed 28 bit range.
     */
    @Override
    public boolean targetWithinPCDisplacement(long pcDisplacement) {
        assert (pcDisplacement & 0x3) == 0 : "Immediate has to be half word aligned";
        VMError.guarantee((pcDisplacement & 0x3) == 0, "Immediate has to be half word aligned");
        return NumUtil.isSignedNbit(28, pcDisplacement);
    }

    static int getAdrpEncoding(int regEncoding, int immHi21) {
        // Write the adrp scratch, PC + (#imm_hi_21 << 12)
        // See Arm Architecture Reference Manual C6.2.11
        int instruction = 0;
        int imm = immHi21 & NumUtil.getNbitNumberInt(21);
        instruction |= AArch64Assembler.Instruction.ADRP.encoding; // adrp opcode
        instruction |= 0x10000000; // PcRelImmOp
        instruction |= (imm & 0x3) << 29; // imm:lo bits operand
        instruction |= ((imm >> 2) & 0x7FFFF) << 5; // imm:hi bits operand
        instruction |= regEncoding; // destination register
        return instruction;
    }

    static int getBrEncoding(int regEncoding) {
        // Write br [scratch]
        // See Arm Architecture Reference Manual C6.2.36
        // AArch64Assembler.java unconditionalBranchRegInstruction also provides insights
        int instruction = 0;
        instruction |= AArch64Assembler.Instruction.BR.encoding; // br opcode
        instruction |= 0xD6000000L; // unconditionalBranchRegOp
        instruction |= regEncoding << 5; // target address register (shifted by Rs1 Offset)
        return instruction;
    }

    static int getLdrEncoding(int destRegEncoding, int srcRegEncoding, int immLo12) {
        // Write the ldr scratch, [scratch{, #imm_lo_12}]
        // See Arm Architecture Reference Manual C6.2.130
        assert (immLo12 & 0x7) == 0 : "Immediate must be word aligned";
        VMError.guarantee((immLo12 & 0x7) == 0, "Immediate must be word aligned");
        int instruction = 0;
        instruction |= 0b11_111_0_01_01 << 22; // constant instruction values
        instruction |= ((immLo12 >> 3) & 0x1FF) << 10; // immediate operand
        instruction |= srcRegEncoding << 5; // base reg encoding opcode
        instruction |= destRegEncoding; // destination reg encoding opcode
        return instruction;
    }

    @Override
    public int insertTrampolineCalls(byte[] compiledBytes, int initialPos, Map<Long, Integer> directTargets) {
        int currentPos = NumUtil.roundUp(initialPos, 8);
        ByteOrder byteOrder = ConfigurationValues.getTarget().arch.getByteOrder();
        ByteBuffer codeBuffer = ByteBuffer.wrap(compiledBytes).order(byteOrder);
        int scratchRegEncoding = AArch64.rscratch1.encoding;
        for (Entry<Long, Integer> entry : directTargets.entrySet()) {
            long targetAddress = entry.getKey();
            int trampolineOffset = entry.getValue();

            int relativePageDifference = AArch64MacroAssembler.PatcherUtil.computeRelativePageDifference(currentPos, trampolineOffset, 1 << 12);
            int instruction = getAdrpEncoding(scratchRegEncoding, relativePageDifference);
            codeBuffer.putInt(trampolineOffset + 0, instruction);
            instruction = getLdrEncoding(scratchRegEncoding, scratchRegEncoding, (currentPos & 0xFFF));
            codeBuffer.putInt(trampolineOffset + 4, instruction);
            instruction = getBrEncoding(scratchRegEncoding);
            codeBuffer.putInt(trampolineOffset + 8, instruction);

            // Write the target address
            codeBuffer.putLong(currentPos, targetAddress);
            currentPos += 8;
        }
        return currentPos;
    }

    @Override
    public void performCodeSynchronization(CodeInfo codeInfo) {
        CodeSynchronizationOperations.clearCache(CodeInfoAccess.getCodeStart(codeInfo).rawValue(), CodeInfoAccess.getCodeSize(codeInfo).rawValue());
        VMThreads.ActionOnTransitionToJavaSupport.requestAllThreadsSynchronizeCode();
    }
}
