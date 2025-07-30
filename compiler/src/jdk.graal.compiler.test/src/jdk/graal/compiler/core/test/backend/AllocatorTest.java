/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.backend;

import static jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph.INVALID_BLOCK_ID;

import java.util.Set;

import jdk.graal.compiler.util.EconomicHashSet;
import org.junit.Assert;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp.ValueMoveOp;
import jdk.graal.compiler.lir.ValueProcedure;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;

public class AllocatorTest extends BackendTest {

    protected void testAllocation(String snippet, final int expectedRegisters, final int expectedRegRegMoves, final int expectedSpillMoves) {
        final StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope _ = debug.scope("AllocatorTest", graph, graph.method(), getCodeCache())) {
            final RegisterStats stats = new RegisterStats(getLIRGenerationResult(graph).getLIR());
            try (DebugContext.Scope _ = debug.scope("Assertions", stats.lir)) {
                Assert.assertEquals("register count", expectedRegisters, stats.registers.size());
                Assert.assertEquals("reg-reg moves", expectedRegRegMoves, stats.regRegMoves);
                Assert.assertEquals("spill moves", expectedSpillMoves, stats.spillMoves);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private class RegisterStats {

        public final LIR lir;
        public Set<Register> registers = new EconomicHashSet<>();
        public int regRegMoves;
        public int spillMoves;

        RegisterStats(LIR lir) {
            this.lir = lir;

            for (int blockId : lir.codeEmittingOrder()) {
                if (blockId == INVALID_BLOCK_ID) {
                    continue;
                }
                BasicBlock<?> block = lir.getBlockById(blockId);
                for (LIRInstruction instr : lir.getLIRforBlock(block)) {
                    collectStats(instr);
                }
            }
        }

        private ValueProcedure collectStatsProc = (value, _, _) -> {
            if (ValueUtil.isRegister(value)) {
                final Register reg = ValueUtil.asRegister(value);
                registers.add(reg);
            }
            return value;
        };

        private void collectStats(final LIRInstruction instr) {
            instr.forEachOutput(collectStatsProc);

            if (ValueMoveOp.isValueMoveOp(instr)) {
                ValueMoveOp move = ValueMoveOp.asValueMoveOp(instr);
                Value def = move.getResult();
                Value use = move.getInput();
                if (ValueUtil.isRegister(def)) {
                    if (ValueUtil.isRegister(use)) {
                        regRegMoves++;
                    }
                } else if (LIRValueUtil.isStackSlotValue(def)) {
                    spillMoves++;
                }
            }
        }
    }
}
