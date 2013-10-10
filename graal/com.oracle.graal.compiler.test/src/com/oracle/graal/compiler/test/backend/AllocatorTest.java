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

import static com.oracle.graal.api.code.CodeUtil.*;
import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;
import java.util.concurrent.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.ValueProcedure;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.*;

public class AllocatorTest extends GraalCompilerTest {

    protected void test(String snippet, final int expectedRegisters, final int expectedRegRegMoves, final int expectedSpillMoves) {
        final StructuredGraph graph = parse(snippet);
        Debug.scope("AllocatorTest", new Object[]{graph, graph.method(), getCodeCache()}, new Runnable() {

            @Override
            public void run() {
                final RegisterStats stats = getRegisterStats(graph);

                Debug.scope("Assertions", stats.lir, new Runnable() {

                    @Override
                    public void run() {
                        Assert.assertEquals("register count", expectedRegisters, stats.registers.size());
                        Assert.assertEquals("reg-reg moves", expectedRegRegMoves, stats.regRegMoves);
                        Assert.assertEquals("spill moves", expectedSpillMoves, stats.spillMoves);
                    }
                });
            }
        });
    }

    private class RegisterStats {

        public final LIR lir;
        public HashSet<Register> registers = new HashSet<>();
        public int regRegMoves;
        public int spillMoves;

        public RegisterStats(LIR lir) {
            this.lir = lir;

            for (Block block : lir.codeEmittingOrder()) {
                for (LIRInstruction instr : lir.lir(block)) {
                    collectStats(instr);
                }
            }
        }

        private void collectStats(final LIRInstruction instr) {
            instr.forEachOutput(new ValueProcedure() {

                @Override
                public Value doValue(Value value) {
                    if (ValueUtil.isRegister(value)) {
                        final Register reg = ValueUtil.asRegister(value);
                        registers.add(reg);
                    }
                    return value;
                }
            });

            if (instr instanceof MoveOp) {
                MoveOp move = (MoveOp) instr;
                Value def = move.getResult();
                Value use = move.getInput();
                if (ValueUtil.isRegister(def)) {
                    if (ValueUtil.isRegister(use)) {
                        regRegMoves++;
                    }
                } else if (ValueUtil.isStackSlot(def)) {
                    spillMoves++;
                }
            }
        }
    }

    private RegisterStats getRegisterStats(final StructuredGraph graph) {
        final PhasePlan phasePlan = getDefaultPhasePlan();
        final Assumptions assumptions = new Assumptions(OptAssumptions.getValue());

        final LIR lir = Debug.scope("FrontEnd", new Callable<LIR>() {

            @Override
            public LIR call() {
                return GraalCompiler.emitHIR(getMetaAccess(), getCodeCache(), getLowerer(), backend.target, graph, replacements, assumptions, null, phasePlan, OptimisticOptimizations.NONE,
                                new SpeculationLog(), suites);
            }
        });

        return Debug.scope("BackEnd", lir, new Callable<RegisterStats>() {

            @Override
            public RegisterStats call() {
                CallingConvention cc = getCallingConvention(getCodeCache(), Type.JavaCallee, graph.method(), false);
                GraalCompiler.emitLIR(backend, backend.target, lir, graph, cc);
                return new RegisterStats(lir);
            }
        });
    }
}
