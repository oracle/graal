/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.profiling;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInsertionBuffer;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.gen.BenchmarkCounterFactory;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase;
import com.oracle.graal.lir.profiling.MoveProfiler.MoveStatistics;
import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

/**
 * Inserts counters into the {@link LIR} code to the number of move instruction dynamically
 * executed.
 */
public class MoveProfilingPhase extends PostAllocationOptimizationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable dynamic move profiling per method.", type = OptionType.Debug)
        public static final OptionValue<Boolean> LIRDynMoveProfilMethod = new OptionValue<>(false);
        // @formatter:on
    }

    private static final String MOVE_OPERATIONS = "MoveOperations";

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    PostAllocationOptimizationContext context) {
        BenchmarkCounterFactory counterFactory = context.counterFactory;
        new Analyzer(target, lirGenRes, counterFactory).run();
    }

    static class Analyzer {
        private final TargetDescription target;
        private final LIRGenerationResult lirGenRes;
        private final BenchmarkCounterFactory counterFactory;
        private final LIRInsertionBuffer buffer;
        private String cachedGroupName;
        private final List<String> names;
        private final List<String> groups;
        private final List<Value> increments;

        Analyzer(TargetDescription target, LIRGenerationResult lirGenRes, BenchmarkCounterFactory counterFactory) {
            this.target = target;
            this.lirGenRes = lirGenRes;
            this.counterFactory = counterFactory;
            this.buffer = new LIRInsertionBuffer();
            this.names = new ArrayList<>();
            this.groups = new ArrayList<>();
            this.increments = new ArrayList<>();
        }

        public void run() {
            LIR lir = lirGenRes.getLIR();
            BlockMap<MoveStatistics> collected = MoveProfiler.profile(lir);
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                MoveStatistics moveStatistics = collected.get(block);
                if (moveStatistics != null) {
                    names.clear();
                    groups.clear();
                    increments.clear();
                    doBlock(block, moveStatistics);
                }
            }
        }

        public void doBlock(AbstractBlockBase<?> block, MoveStatistics moveStatistics) {
            // counter insertion phase
            for (MoveType type : MoveType.values()) {
                String name = type.toString();
                // current run
                addEntry(name, getGroupName(), moveStatistics.get(type));
            }
            insertBenchmarkCounter(block);
        }

        protected final void addEntry(String name, String groupName, int count) {
            if (count > 0) {
                names.add(name);
                groups.add(groupName);
                increments.add(new ConstantValue(LIRKind.fromJavaKind(target.arch, JavaKind.Int), JavaConstant.forInt(count)));
            }
        }

        protected final void insertBenchmarkCounter(AbstractBlockBase<?> block) {
            int size = names.size();
            if (size > 0) { // Don't pollute LIR when nothing has to be done
                assert size > 0 && size == groups.size() && size == increments.size();
                List<LIRInstruction> instructions = lirGenRes.getLIR().getLIRforBlock(block);
                LIRInstruction inst = counterFactory.createMultiBenchmarkCounter(names.toArray(new String[size]), groups.toArray(new String[size]),
                                increments.toArray(new Value[size]));
                assert inst != null;
                buffer.init(instructions);
                buffer.append(1, inst);
                buffer.finish();
            }
        }

        protected final String getGroupName() {
            if (cachedGroupName == null) {
                cachedGroupName = createGroupName();
            }
            return cachedGroupName;
        }

        protected String createGroupName() {
            if (Options.LIRDynMoveProfilMethod.getValue()) {
                return new StringBuilder('"').append(MOVE_OPERATIONS).append(':').append(lirGenRes.getCompilationUnitName()).append('"').toString();
            }
            return MOVE_OPERATIONS;
        }
    }

}
