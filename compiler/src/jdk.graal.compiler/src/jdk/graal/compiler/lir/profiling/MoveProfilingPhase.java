/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.profiling;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInsertionBuffer;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.gen.DiagnosticLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase;
import jdk.graal.compiler.lir.profiling.MoveProfiler.MoveStatistics;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
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
        public static final OptionKey<Boolean> LIRDynMoveProfileMethod = new OptionKey<>(false);
        // @formatter:on
    }

    private static final String MOVE_OPERATIONS = "MoveOperations";

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        new Analyzer(target, lirGenRes, context.diagnosticLirGenTool).run();
    }

    static class Analyzer {
        private final TargetDescription target;
        private final LIRGenerationResult lirGenRes;
        private final DiagnosticLIRGeneratorTool diagnosticLirGenTool;
        private final LIRInsertionBuffer buffer;
        private String cachedGroupName;
        private final List<String> names;
        private final List<String> groups;
        private final List<Value> increments;

        Analyzer(TargetDescription target, LIRGenerationResult lirGenRes, DiagnosticLIRGeneratorTool diagnosticLirGenTool) {
            this.target = target;
            this.lirGenRes = lirGenRes;
            this.diagnosticLirGenTool = diagnosticLirGenTool;
            this.buffer = new LIRInsertionBuffer();
            this.names = new ArrayList<>();
            this.groups = new ArrayList<>();
            this.increments = new ArrayList<>();
        }

        public void run() {
            LIR lir = lirGenRes.getLIR();
            BlockMap<MoveStatistics> collected = MoveProfiler.profile(lir);
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                MoveStatistics moveStatistics = collected.get(block);
                if (moveStatistics != null) {
                    names.clear();
                    groups.clear();
                    increments.clear();
                    doBlock(block, moveStatistics);
                }
            }
        }

        public void doBlock(BasicBlock<?> block, MoveStatistics moveStatistics) {
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

        protected final void insertBenchmarkCounter(BasicBlock<?> block) {
            int size = names.size();
            if (size > 0) { // Don't pollute LIR when nothing has to be done
                assert NumUtil.assertPositiveInt(size);
                assert size == groups.size() : Assertions.errorMessageContext("size", size, "groups", groups);
                assert size == increments.size() : Assertions.errorMessageContext("size", size, "increments", increments);
                ArrayList<LIRInstruction> instructions = lirGenRes.getLIR().getLIRforBlock(block);
                LIRInstruction inst = diagnosticLirGenTool.createMultiBenchmarkCounter(names.toArray(new String[size]), groups.toArray(new String[size]),
                                increments.toArray(new Value[size]));
                assert inst != null;
                buffer.init(instructions);
                int insertionIndex = lirGenRes.getFirstInsertPosition();
                buffer.append(insertionIndex, inst);
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
            if (Options.LIRDynMoveProfileMethod.getValue(lirGenRes.getLIR().getOptions())) {
                return new StringBuilder("\"").append(MOVE_OPERATIONS).append(':').append(lirGenRes.getCompilationUnitName()).append('"').toString();
            }
            return MOVE_OPERATIONS;
        }
    }

}
