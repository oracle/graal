/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.FinalCodeAnalysisPhase;
import jdk.vm.ci.code.TargetDescription;

/**
 * This phase verifies that no LIR instructions that {@linkplain LIRInstruction#modifiesStackPointer
 * modify the stack pointer} were added after {@linkplain FramePointerPhase} to a LIR that did not
 * have them before.
 */
class VerifyFramePointerPhase extends FinalCodeAnalysisPhase {
    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, FinalCodeAnalysisContext context) {
        LIR lir = lirGenRes.getLIR();
        for (int blockId : lir.getBlocks()) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                if (op.modifiesStackPointer()) {
                    var registerAllocationConfig = (SubstrateAMD64Backend.SubstrateAMD64RegisterAllocationConfig) lirGenRes.getRegisterAllocationConfig();
                    var frameMap = (SubstrateAMD64Backend.SubstrateAMD64FrameMap) lirGenRes.getFrameMap();
                    if (registerAllocationConfig.preserveFramePointer() && frameMap.needsFramePointer()) {
                        return; /* Frame pointer is used, no need to check further. */
                    }
                    throw VMError.shouldNotReachHere("The following instruction modifies the stack pointer, but was added after " + FramePointerPhase.class.getSimpleName() + ": " + op);
                }
            }
        }
    }
}
