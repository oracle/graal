/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.lir.ssa;

import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.EnumSet;
import java.util.Map;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BasicBlockSet;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.lir.InstructionValueConsumer;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.meta.Value;

final class SSAVerifier {
    private static class Entry {
        private final LIRInstruction inst;
        private final BasicBlock<?> block;

        Entry(LIRInstruction inst, BasicBlock<?> block) {
            this.inst = inst;
            this.block = block;
        }
    }

    private final LIR lir;
    private final BasicBlockSet visited;
    private final Map<Value, Entry> defined;
    private BasicBlock<?> currentBlock;

    SSAVerifier(LIR lir) {
        this.lir = lir;
        this.visited = lir.getControlFlowGraph().createBasicBlockSet();
        this.defined = new EconomicHashMap<>();
    }

    @SuppressWarnings("try")
    public boolean verify() {
        DebugContext debug = lir.getDebug();
        try (DebugContext.Scope s = debug.scope("SSAVerifier", lir)) {
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                doBlock(block);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return true;
    }

    @SuppressWarnings("try")
    private void doBlock(BasicBlock<?> b) {
        if (visited.get(b)) {
            return;
        }
        for (int i = 0; i < b.getPredecessorCount(); i++) {
            BasicBlock<?> pred = b.getPredecessorAt(i);
            if (!b.isLoopHeader() || !pred.isLoopEnd()) {
                doBlock(pred);
            }
        }
        try (Indent indent = lir.getDebug().logAndIndent(DebugContext.INFO_LEVEL, "handle block %s", b)) {
            assert verifyBlock(b);
        }
    }

    private boolean verifyBlock(BasicBlock<?> block) {
        currentBlock = block;
        assert !visited.get(block) : "Block already visited: " + block;
        visited.set(block);
        for (LIRInstruction op : lir.getLIRforBlock(block)) {
            op.visitEachAlive(this::useConsumer);
            op.visitEachState(this::useConsumer);
            op.visitEachInput(this::useConsumer);

            op.visitEachTemp(this::defConsumer);
            op.visitEachOutput(this::defConsumer);

        }
        currentBlock = null;
        return true;
    }

    /**
     * @see InstructionValueConsumer
     * @param mode
     * @param flags
     */
    private void useConsumer(LIRInstruction inst, Value val, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
        Value value = LIRValueUtil.stripCast(val);
        if (shouldProcess(value)) {
            assert defined.containsKey(value) || flags.contains(LIRInstruction.OperandFlag.UNINITIALIZED) : String.format("Value %s used at instruction %s in block %s but never defined", value, inst,
                            currentBlock);
        }
    }

    /**
     * @see InstructionValueConsumer
     * @param mode
     * @param flags
     */
    private void defConsumer(LIRInstruction inst, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
        assert !LIRValueUtil.isCast(value);
        if (shouldProcess(value)) {
            assert !defined.containsKey(value) : String.format("Value %s redefined at %s (previous definition %s in block %s)", value, inst, defined.get(value).inst,
                            defined.get(value).block);
            defined.put(value, new Entry(inst, currentBlock));
        }
    }

    private static boolean shouldProcess(Value value) {
        return !value.equals(Value.ILLEGAL) && !LIRValueUtil.isConstantValue(value) && !isRegister(value) && !LIRValueUtil.isStackSlotValue(value);
    }

}
