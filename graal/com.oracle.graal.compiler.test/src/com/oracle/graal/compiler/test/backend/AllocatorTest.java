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
import java.util.concurrent.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.ValueProcedure;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.*;

public class AllocatorTest extends GraalCompilerTest {

    @Test
    public void test1() {
        test("test1snippet", 2, 1, 0);
    }

    public static long test1snippet(long x) {
        return x + 5;
    }

    @Ignore
    @Test
    public void test2() {
        test("test2snippet", 2, 0, 0);
    }

    public static long test2snippet(long x) {
        return x * 5;
    }

    @Ignore
    @Test
    public void test3() {
        test("test3snippet", 4, 1, 0);
    }

    public static long test3snippet(long x) {
        return x / 3 + x % 3;
    }

    private void test(String snippet, final int expectedRegisters, final int expectedRegRegMoves, final int expectedSpillMoves) {
        final StructuredGraph graph = parse(snippet);
        Debug.scope("AllocatorTest", new Object[]{graph, graph.method(), backend.target}, new Runnable() {

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

    class RegisterStats {

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
            final boolean move = instr.name().equals("MOVE");
            instr.forEachOutput(new ValueProcedure() {

                @Override
                public Value doValue(Value defValue) {
                    if (ValueUtil.isRegister(defValue)) {
                        final Register reg = ValueUtil.asRegister(defValue);
                        registers.add(reg);
                        if (move) {
                            instr.forEachInput(new ValueProcedure() {

                                @Override
                                public Value doValue(Value useValue) {
                                    if (ValueUtil.isRegister(useValue) && ValueUtil.asRegister(useValue) != reg) {
                                        regRegMoves++;
                                    }
                                    return useValue;
                                }
                            });
                        }
                    } else if (move && ValueUtil.isStackSlot(defValue)) {
                        spillMoves++;
                    }
                    return defValue;
                }
            });
        }
    }

    private RegisterStats getRegisterStats(final StructuredGraph graph) {
        final PhasePlan phasePlan = getDefaultPhasePlan();
        final Assumptions assumptions = new Assumptions(GraalOptions.OptAssumptions);

        final LIR lir = Debug.scope("FrontEnd", new Callable<LIR>() {

            @Override
            public LIR call() {
                return GraalCompiler.emitHIR(runtime, backend.target, graph, assumptions, null, phasePlan, OptimisticOptimizations.NONE);
            }
        });

        return Debug.scope("BackEnd", lir, new Callable<RegisterStats>() {

            @Override
            public RegisterStats call() {
                GraalCompiler.emitLIR(backend, backend.target, lir, graph, graph.method());
                return new RegisterStats(lir);
            }
        });
    }
}
