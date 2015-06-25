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
package com.oracle.graal.compiler.test.backend;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.debug.Debug.*;
import jdk.internal.jvmci.meta.*;

import org.junit.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;

public class AllocatorTest extends BackendTest {

    protected void testAllocation(String snippet, final int expectedRegisters, final int expectedRegRegMoves, final int expectedSpillMoves) {
        final StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        try (Scope s = Debug.scope("AllocatorTest", graph, graph.method(), getCodeCache())) {
            final RegisterStats stats = new RegisterStats(getLIRGenerationResult(graph).getLIR());
            try (Scope s2 = Debug.scope("Assertions", stats.lir)) {
                Assert.assertEquals("register count", expectedRegisters, stats.registers.size());
                Assert.assertEquals("reg-reg moves", expectedRegRegMoves, stats.regRegMoves);
                Assert.assertEquals("spill moves", expectedSpillMoves, stats.spillMoves);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private class RegisterStats {

        public final LIR lir;
        public HashSet<Register> registers = new HashSet<>();
        public int regRegMoves;
        public int spillMoves;

        public RegisterStats(LIR lir) {
            this.lir = lir;

            for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
                for (LIRInstruction instr : lir.getLIRforBlock(block)) {
                    collectStats(instr);
                }
            }
        }

        private ValueProcedure collectStatsProc = (value, mode, flags) -> {
            if (ValueUtil.isRegister(value)) {
                final Register reg = ValueUtil.asRegister(value);
                registers.add(reg);
            }
            return value;
        };

        private void collectStats(final LIRInstruction instr) {
            instr.forEachOutput(collectStatsProc);

            if (instr instanceof MoveOp) {
                MoveOp move = (MoveOp) instr;
                Value def = move.getResult();
                Value use = move.getInput();
                if (ValueUtil.isRegister(def)) {
                    if (ValueUtil.isRegister(use)) {
                        regRegMoves++;
                    }
                } else if (ValueUtil.isStackSlotValue(def)) {
                    spillMoves++;
                }
            }
        }
    }
}
