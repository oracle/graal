/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.jtt;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.junit.Assume.assumeTrue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp.ValueMoveOp;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Verifies that register pressure on one side of a diamond does not introduce spill moves on a
 * sibling low-pressure path.
 */
public class ForceSpillSplitAtSlowPathTest extends LIRTest {
    private static final int HIGH_PRESSURE_TEMP_COUNT = 12;
    private static final HighPressureSpec HIGH_PRESSURE = new HighPressureSpec();
    private static final LowPressurePathMarkerSpec LOW_PRESSURE_PATH_MARKER = new LowPressurePathMarkerSpec();
    private static final FinalUseSpec FINAL_USE = new FinalUseSpec();

    private boolean sawLowPressurePathMarker;
    private boolean sawHighPressure;
    private int lowPressurePathSpillStores;
    private int lowPressurePathSpillReloads;
    private int highPressurePathSpillStores;
    private int highPressurePathSpillReloads;
    private int entryBlockSpillStores;
    private int entryBlockSpillReloads;
    private int totalSpillStores;
    private int totalSpillReloads;
    private String lowPressurePathBlock;
    private String entryBlock;
    private boolean markBytecodeHandlerStubBlocks;
    private boolean sawBytecodeHandlerStubBlock;

    private static final class HighPressureSpec extends LIRTestSpecification {
        @Override
        public void generate(LIRGeneratorTool gen, Value a, Value b, Value c, Value d, Value e) {
            Value[] temps = new Value[HIGH_PRESSURE_TEMP_COUNT];
            for (int i = 0; i < temps.length; i++) {
                temps[i] = gen.newVariable(a.getValueKind());
            }
            gen.append(new HighPressureOp(new Value[]{a, b, c, d, e}, temps));
        }
    }

    private static final class LowPressurePathMarkerSpec extends LIRTestSpecification {
        @Override
        public void generate(LIRGeneratorTool gen) {
            gen.append(new LowPressurePathMarkerOp());
        }
    }

    private static final class FinalUseSpec extends LIRTestSpecification {
        @Override
        public void generate(LIRGeneratorTool gen, Value a, Value b, Value c, Value d, Value e) {
            gen.append(new FinalUseOp(new Value[]{a, b, c, d, e}));
        }
    }

    private static final class HighPressureOp extends LIRInstruction {
        private static final LIRInstructionClass<HighPressureOp> TYPE = LIRInstructionClass.create(HighPressureOp.class);

        @Alive({REG, STACK, CONST}) private Value[] carriedValues;
        @Temp({REG}) private Value[] temps;

        private HighPressureOp(Value[] carriedValues, Value[] temps) {
            super(TYPE);
            this.carriedValues = carriedValues;
            this.temps = temps;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    private static final class LowPressurePathMarkerOp extends LIRInstruction {
        private static final LIRInstructionClass<LowPressurePathMarkerOp> TYPE = LIRInstructionClass.create(LowPressurePathMarkerOp.class);

        private LowPressurePathMarkerOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    private static final class FinalUseOp extends LIRInstruction {
        private static final LIRInstructionClass<FinalUseOp> TYPE = LIRInstructionClass.create(FinalUseOp.class);

        @Use({REG, CONST}) private Value[] values;

        private FinalUseOp(Value[] values) {
            super(TYPE);
            this.values = values;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static void highPressure(LIRTestSpecification spec, long a, long b, long c, long d, long e) {
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static void lowPressurePathMarker(LIRTestSpecification spec) {
    }

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static void finalUse(LIRTestSpecification spec, long a, long b, long c, long d, long e) {
    }

    public static int testControlFlow(long seed, boolean takeHighPressurePath) {
        long a = GraalDirectives.opaque(seed + 1);
        long b = GraalDirectives.opaque(seed + 2);
        long c = GraalDirectives.opaque(seed + 3);
        long d = GraalDirectives.opaque(seed + 4);
        long e = GraalDirectives.opaque(seed + 5);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.LIKELY_PROBABILITY, takeHighPressurePath)) {
            highPressure(HIGH_PRESSURE, a, b, c, d, e);
        } else {
            lowPressurePathMarker(LOW_PRESSURE_PATH_MARKER);
        }
        finalUse(FINAL_USE, a, b, c, d, e);
        return 0;
    }

    public static int testLowPressurePathFirst(long seed, boolean takeLowPressurePath) {
        long a = GraalDirectives.opaque(seed + 1);
        long b = GraalDirectives.opaque(seed + 2);
        long c = GraalDirectives.opaque(seed + 3);
        long d = GraalDirectives.opaque(seed + 4);
        long e = GraalDirectives.opaque(seed + 5);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.LIKELY_PROBABILITY, takeLowPressurePath)) {
            lowPressurePathMarker(LOW_PRESSURE_PATH_MARKER);
        } else {
            highPressure(HIGH_PRESSURE, a, b, c, d, e);
        }
        if (GraalDirectives.injectBranchProbability(GraalDirectives.UNLIKELY_PROBABILITY, GraalDirectives.opaque(seed) == 0)) {
            GraalDirectives.controlFlowAnchor();
        }
        finalUse(FINAL_USE, a, b, c, d, e);
        return 0;
    }

    public static int testCommonPredecessorSpill(long seed, boolean takeMarkerPath) {
        long a = GraalDirectives.opaque(seed + 1);
        long b = GraalDirectives.opaque(seed + 2);
        long c = GraalDirectives.opaque(seed + 3);
        long d = GraalDirectives.opaque(seed + 4);
        long e = GraalDirectives.opaque(seed + 5);
        highPressure(HIGH_PRESSURE, a, b, c, d, e);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.UNLIKELY_PROBABILITY, takeMarkerPath)) {
            lowPressurePathMarker(LOW_PRESSURE_PATH_MARKER);
        }
        finalUse(FINAL_USE, a, b, c, d, e);
        return 0;
    }

    public static int testRepeatedControlFlow(long seed, boolean takeFirstHighPressurePath, boolean takeSecondHighPressurePath) {
        long a = GraalDirectives.opaque(seed + 1);
        long b = GraalDirectives.opaque(seed + 2);
        long c = GraalDirectives.opaque(seed + 3);
        long d = GraalDirectives.opaque(seed + 4);
        long e = GraalDirectives.opaque(seed + 5);
        if (GraalDirectives.injectBranchProbability(GraalDirectives.LIKELY_PROBABILITY, takeFirstHighPressurePath)) {
            highPressure(HIGH_PRESSURE, a, b, c, d, e);
        } else {
            lowPressurePathMarker(LOW_PRESSURE_PATH_MARKER);
        }
        if (GraalDirectives.injectBranchProbability(GraalDirectives.LIKELY_PROBABILITY, takeSecondHighPressurePath)) {
            highPressure(HIGH_PRESSURE, a, b, c, d, e);
        } else {
            lowPressurePathMarker(LOW_PRESSURE_PATH_MARKER);
        }
        finalUse(FINAL_USE, a, b, c, d, e);
        return 0;
    }

    @Before
    public void resetInspection() {
        assumeTrue("skipping AMD64-specific register allocation test", getTarget().arch instanceof AMD64);
        sawLowPressurePathMarker = false;
        sawHighPressure = false;
        lowPressurePathSpillStores = 0;
        lowPressurePathSpillReloads = 0;
        highPressurePathSpillStores = 0;
        highPressurePathSpillReloads = 0;
        entryBlockSpillStores = 0;
        entryBlockSpillReloads = 0;
        totalSpillStores = 0;
        totalSpillReloads = 0;
        lowPressurePathBlock = null;
        entryBlock = null;
        markBytecodeHandlerStubBlocks = true;
        sawBytecodeHandlerStubBlock = false;
    }

    private void compileAndInspect(String methodName) {
        compile(getResolvedJavaMethod(methodName), null, getInitialOptions());
    }

    @Test
    public void testSpillsStayOnHighPressurePath() {
        compileAndInspect("testControlFlow");
        Assert.assertTrue("expected a bytecode-handler stub block", sawBytecodeHandlerStubBlock);
        Assert.assertTrue("expected a low-pressure path marker", sawLowPressurePathMarker);
        Assert.assertTrue("expected a high-pressure operation", sawHighPressure);
        Assert.assertTrue("expected the high-pressure path to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
        Assert.assertEquals("unexpected spill stores on the low-pressure path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillStores);
        Assert.assertEquals("unexpected spill reloads on the low-pressure path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillReloads);
    }

    @Test
    public void testMixedLocationMergeRecoversToRegister() {
        compileAndInspect("testLowPressurePathFirst");
        Assert.assertTrue("expected a low-pressure path marker", sawLowPressurePathMarker);
        Assert.assertTrue("expected a high-pressure operation", sawHighPressure);
        Assert.assertTrue("expected stack traffic to remain on the high-pressure path", highPressurePathSpillStores > 0 || highPressurePathSpillReloads > 0);
        Assert.assertEquals("unexpected spill stores on the low-pressure path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillStores);
        Assert.assertEquals("unexpected spill reloads on the low-pressure path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillReloads);
    }

    @Test
    public void testSpillFromCommonPredecessorIsNotRecovered() {
        compileAndInspect("testCommonPredecessorSpill");
        Assert.assertTrue("expected a low-pressure path marker", sawLowPressurePathMarker);
        Assert.assertTrue("expected the common predecessor to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
    }

    @Test
    public void testRepeatedSpillsStayOnHighPressurePaths() {
        compileAndInspect("testRepeatedControlFlow");
        Assert.assertTrue("expected the high-pressure paths to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
        Assert.assertTrue("expected stack traffic to remain on a high-pressure path", highPressurePathSpillStores > 0 || highPressurePathSpillReloads > 0);
        Assert.assertEquals("unexpected spill stores in the common entry block:\n" + entryBlock, 0, entryBlockSpillStores);
        Assert.assertEquals("unexpected spill reloads in the common entry block:\n" + entryBlock, 0, entryBlockSpillReloads);
    }

    @Test
    public void testUnmarkedCompilationKeepsOriginalPlacement() {
        markBytecodeHandlerStubBlocks = false;
        compileAndInspect("testControlFlow");
        Assert.assertFalse("ordinary compilation was marked as a bytecode-handler stub", sawBytecodeHandlerStubBlock);
    }

    @Test
    public void testExecution() {
        runTest("testControlFlow", 7L, false);
        runTest("testControlFlow", 7L, true);
        runTest("testLowPressurePathFirst", 0L, false);
        runTest("testLowPressurePathFirst", 0L, true);
        runTest("testLowPressurePathFirst", 7L, false);
        runTest("testLowPressurePathFirst", 7L, true);
        runTest("testCommonPredecessorSpill", 7L, false);
        runTest("testCommonPredecessorSpill", 7L, true);
        runTest("testRepeatedControlFlow", 7L, false, false);
        runTest("testRepeatedControlFlow", 7L, false, true);
        runTest("testRepeatedControlFlow", 7L, true, false);
        runTest("testRepeatedControlFlow", 7L, true, true);
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        suites.getPreAllocationOptimizationStage().prependPhase(new MarkBytecodeHandlerStubBlocksPhase());
        suites.getPostAllocationOptimizationStage().prependPhase(new CheckAllocationPhase());
        return suites;
    }

    private final class MarkBytecodeHandlerStubBlocksPhase extends LIRPhase<PreAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
            if (markBytecodeHandlerStubBlocks) {
                for (var block : lirGenRes.getLIR().getControlFlowGraph().getBlocks()) {
                    block.markBytecodeHandlerStubBlock();
                }
            }
        }
    }

    private final class CheckAllocationPhase extends LIRPhase<PostAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
            for (var block : lirGenRes.getLIR().getControlFlowGraph().getBlocks()) {
                sawBytecodeHandlerStubBlock |= block.isBytecodeHandlerStubBlock();
                boolean entry = block.getPredecessorCount() == 0;
                boolean lowPressurePathBlockMarker = false;
                boolean highPressurePathBlockMarker = false;
                StringBuilder description = new StringBuilder();
                for (LIRInstruction instruction : lirGenRes.getLIR().getLIRforBlock(block)) {
                    if (instruction instanceof LowPressurePathMarkerOp) {
                        sawLowPressurePathMarker = true;
                        lowPressurePathBlockMarker = true;
                    } else if (instruction instanceof HighPressureOp) {
                        sawHighPressure = true;
                        highPressurePathBlockMarker = true;
                    }
                    if (description.length() != 0) {
                        description.append(System.lineSeparator());
                    }
                    description.append(instruction.toString(lirGenRes));
                }
                if (lowPressurePathBlockMarker) {
                    lowPressurePathBlock = description.toString();
                }
                if (entry) {
                    entryBlock = description.toString();
                }
                for (LIRInstruction instruction : lirGenRes.getLIR().getLIRforBlock(block)) {
                    if (ValueMoveOp.isValueMoveOp(instruction)) {
                        ValueMoveOp move = ValueMoveOp.asValueMoveOp(instruction);
                        boolean spillStore = LIRValueUtil.isStackSlotValue(move.getResult());
                        boolean spillReload = LIRValueUtil.isStackSlotValue(move.getInput());
                        if (spillStore) {
                            totalSpillStores++;
                        }
                        if (spillReload) {
                            totalSpillReloads++;
                        }
                        if (lowPressurePathBlockMarker) {
                            if (spillStore) {
                                lowPressurePathSpillStores++;
                            }
                            if (spillReload) {
                                lowPressurePathSpillReloads++;
                            }
                        }
                        if (highPressurePathBlockMarker) {
                            if (spillStore) {
                                highPressurePathSpillStores++;
                            }
                            if (spillReload) {
                                highPressurePathSpillReloads++;
                            }
                        }
                        if (entry) {
                            if (spillStore) {
                                entryBlockSpillStores++;
                            }
                            if (spillReload) {
                                entryBlockSpillReloads++;
                            }
                        }
                    }
                }
            }
        }
    }
}
