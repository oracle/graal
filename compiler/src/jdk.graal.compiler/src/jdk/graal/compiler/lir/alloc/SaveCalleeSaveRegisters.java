/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc;

import java.util.ArrayList;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInsertionBuffer;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.MoveFactory;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase;
import jdk.graal.compiler.lir.util.RegisterMap;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.PlatformKind;

public class SaveCalleeSaveRegisters extends PreAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
        RegisterArray calleeSaveRegisters = lirGenRes.getRegisterConfig().getCalleeSaveRegisters();
        if (calleeSaveRegisters == null || calleeSaveRegisters.size() == 0) {
            return;
        }
        LIR lir = lirGenRes.getLIR();
        RegisterMap<Variable> savedRegisters = saveAtEntry(lir, context.lirGen, lirGenRes, calleeSaveRegisters, target.arch);

        for (int blockId : lir.getBlocks()) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            if (block.getSuccessorCount() == 0) {
                restoreAtExit(lir, context.lirGen.getSpillMoveFactory(), lirGenRes, savedRegisters, block);
            }
        }
    }

    private static RegisterMap<Variable> saveAtEntry(LIR lir, LIRGeneratorTool lirGen, LIRGenerationResult lirGenRes, RegisterArray calleeSaveRegisters, Architecture arch) {
        BasicBlock<?> startBlock = lir.getControlFlowGraph().getStartBlock();
        ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(startBlock);
        int insertionIndex = lirGenRes.getFirstInsertPosition();
        LIRInsertionBuffer buffer = new LIRInsertionBuffer();
        buffer.init(instructions);
        StandardOp.LabelOp entry = (StandardOp.LabelOp) instructions.get(insertionIndex - 1);
        RegisterValue[] savedRegisterValues = new RegisterValue[calleeSaveRegisters.size()];
        int savedRegisterValueIndex = 0;
        RegisterMap<Variable> saveMap = new RegisterMap<>(arch);
        for (Register register : calleeSaveRegisters) {
            PlatformKind registerPlatformKind = arch.getLargestStorableKind(register.getRegisterCategory());
            LIRKind lirKind = LIRKind.value(registerPlatformKind);
            RegisterValue registerValue = register.asValue(lirKind);
            Variable saveVariable = lirGen.newVariable(lirKind);
            LIRInstruction save = lirGen.getSpillMoveFactory().createMove(saveVariable, registerValue);
            buffer.append(insertionIndex, save);
            save.setComment(lirGenRes, "SaveCalleeSavedRegisters: saveAtEntry");
            saveMap.put(register, saveVariable);
            savedRegisterValues[savedRegisterValueIndex++] = registerValue;
        }
        entry.addIncomingValues(savedRegisterValues);
        buffer.finish();
        return saveMap;
    }

    private static void restoreAtExit(LIR lir, MoveFactory moveFactory, LIRGenerationResult lirGenRes, RegisterMap<Variable> calleeSaveRegisters, BasicBlock<?> block) {
        ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
        int insertionIndex = instructions.size() - 1;
        LIRInsertionBuffer buffer = new LIRInsertionBuffer();
        buffer.init(instructions);
        LIRInstruction lirInstruction = instructions.get(insertionIndex);
        assert lirInstruction instanceof StandardOp.BlockEndOp : lirInstruction;
        calleeSaveRegisters.forEach((Register register, Variable saved) -> {
            LIRInstruction restore = moveFactory.createMove(register.asValue(saved.getValueKind()), saved);
            buffer.append(insertionIndex, restore);
            restore.setComment(lirGenRes, "SaveCalleeSavedRegisters: restoreAtExit");
        });
        buffer.finish();
    }
}
