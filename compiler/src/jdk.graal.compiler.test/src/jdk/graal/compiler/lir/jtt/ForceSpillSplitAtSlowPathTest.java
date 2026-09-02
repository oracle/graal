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
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp.ValueMoveOp;
import jdk.graal.compiler.lir.alloc.lsra.LinearScan;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.MultiReturnNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Verifies that LSRA keeps pressure spills local to the path that caused them. The test methods
 * create control-flow diamonds carrying five live {@code long} values. One branch contains a
 * synthetic instruction that requires enough temporary registers to cause spills, while a sibling
 * branch contains a marker used to identify unexpected spill moves.
 * <p>
 * A pre-allocation phase marks the selected fast-path blocks. A post-allocation phase then counts
 * spill stores and reloads in the marked and high-pressure blocks and records the recovery counters.
 * The execution test also returns a value derived from every live value so incorrect recovery moves
 * cannot pass solely because the expected allocation shape was produced.
 */
public class ForceSpillSplitAtSlowPathTest extends LIRTest {
    /** Selects which blocks the test-only pre-allocation phase marks as fast paths. */
    private enum FastPathMarking {
        ALL,
        LOW_PRESSURE_PATH,
        NONE
    }

    private static final int HIGH_PRESSURE_TEMP_COUNT = 12;
    private static final HighPressureSpec HIGH_PRESSURE = new HighPressureSpec();
    private static final LowPressurePathMarkerSpec LOW_PRESSURE_PATH_MARKER = new LowPressurePathMarkerSpec();
    private static final FinalUseSpec FINAL_USE = new FinalUseSpec();
    private static final CounterKey FAST_PATH_RECOVERY_SPLITS = DebugContext.counter("LinearScanWalker[fastPathRecoverySplits]");
    private static final CounterKey FAST_PATH_RECOVERY_SPLIT_LIMIT_REACHED = DebugContext.counter("LinearScanWalker[fastPathRecoverySplitLimitReached]");
    private static final CounterKey FAST_PATH_PREDECESSOR_REGISTER_HINTS = DebugContext.counter("LinearScanWalker[fastPathPredecessorRegisterHints]");

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
    private FastPathMarking fastPathMarking;
    private boolean sawFastPathBlock;
    private long fastPathRecoverySplits;
    private long fastPathRecoverySplitLimitReached;
    private long fastPathPredecessorRegisterHints;

    /** Lowers {@link #highPressure} to an instruction with many register-only temporaries. */
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

    /** Lowers {@link #lowPressurePathMarker} to a test-only block marker. */
    private static final class LowPressurePathMarkerSpec extends LIRTestSpecification {
        @Override
        public void generate(LIRGeneratorTool gen) {
            gen.append(new LowPressurePathMarkerOp());
        }
    }

    /** Lowers {@link #finalUse} to one instruction that uses all values carried through the paths. */
    private static final class FinalUseSpec extends LIRTestSpecification {
        @Override
        public void generate(LIRGeneratorTool gen, Value a, Value b, Value c, Value d, Value e) {
            gen.append(new FinalUseOp(new Value[]{a, b, c, d, e}));
        }
    }

    /**
     * Keeps the carried values alive while requiring enough register-only temporaries to force
     * pressure spills on this path.
     */
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

    /** Identifies the low-pressure path during post-allocation inspection. */
    private static final class LowPressurePathMarkerOp extends LIRInstruction {
        private static final LIRInstructionClass<LowPressurePathMarkerOp> TYPE = LIRInstructionClass.create(LowPressurePathMarkerOp.class);

        private LowPressurePathMarkerOp() {
            super(TYPE);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    /** Makes all carried values live until the paths have merged. */
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
        return (int) (a ^ b ^ c ^ d ^ e);
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
        return (int) (a ^ b ^ c ^ d ^ e);
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
        return (int) (a ^ b ^ c ^ d ^ e);
    }

    public static int testFastPathMarking(boolean firstResult) {
        if (firstResult) {
            GraalDirectives.controlFlowAnchor();
            GraalDirectives.deoptimizeAndInvalidate();
        }
        GraalDirectives.controlFlowAnchor();
        return 2;
    }

    public static int testLoopFastPathMarking(int iterations) {
        int result = 0;
        for (int i = 0; i < iterations; i++) {
            GraalDirectives.controlFlowAnchor();
            result += i;
        }
        return result;
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
        return (int) (a ^ b ^ c ^ d ^ e);
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
        fastPathMarking = FastPathMarking.ALL;
        sawFastPathBlock = false;
        fastPathRecoverySplits = 0;
        fastPathRecoverySplitLimitReached = 0;
        fastPathPredecessorRegisterHints = 0;
    }

    private void compileAndInspect(String methodName) {
        compile(getResolvedJavaMethod(methodName), null, getInitialOptions());
    }

    private void compileAndInspect(String methodName, OptionValues options) {
        compile(getResolvedJavaMethod(methodName), null, options);
    }

    @Test
    public void testSpillsStayOnHighPressurePath() {
        compileAndInspect("testControlFlow");
        Assert.assertTrue("expected a fast-path block", sawFastPathBlock);
        Assert.assertTrue("expected a low-pressure path marker", sawLowPressurePathMarker);
        Assert.assertTrue("expected a high-pressure operation", sawHighPressure);
        Assert.assertTrue("expected the high-pressure path to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
        Assert.assertEquals("unexpected spill stores on the low-pressure path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillStores);
        Assert.assertEquals("unexpected spill reloads on the low-pressure path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillReloads);
    }

    @Test
    public void testFastPathRecoverySplittingDisabled() {
        OptionValues options = new OptionValues(getInitialOptions(), LinearScan.Options.LIROptLSRAMaxFastPathRecoverySplits, 0, DebugOptions.Counters, "");
        compileAndInspect("testControlFlow", options);
        Assert.assertTrue("expected register pressure to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
        Assert.assertEquals("fast-path recovery splitting was not disabled", 0, fastPathRecoverySplits);
    }

    @Test
    public void testFastPathRecoveryBlockLimit() {
        OptionValues options = new OptionValues(getInitialOptions(), LinearScan.Options.LIROptLSRAMaxBlocksForFastPathRecovery, 1, DebugOptions.Counters, "");
        compileAndInspect("testControlFlow", options);
        Assert.assertTrue("expected register pressure to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
        Assert.assertEquals("fast-path recovery splitting exceeded the block limit", 0, fastPathRecoverySplits);
    }

    @Test
    public void testFastPathRecoverySplitLimit() {
        OptionValues options = new OptionValues(getInitialOptions(), LinearScan.Options.LIROptLSRAMaxFastPathRecoverySplits, 1, DebugOptions.Counters, "");
        compileAndInspect("testRepeatedControlFlow", options);
        Assert.assertTrue("expected register pressure to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
        Assert.assertEquals("unexpected number of fast-path recovery splits", 1, fastPathRecoverySplits);
        Assert.assertEquals("expected the fast-path recovery split limit to be reached", 1, fastPathRecoverySplitLimitReached);
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
    public void testRecoveryUsesRegisteredPredecessorHint() {
        OptionValues options = new OptionValues(getInitialOptions(), DebugOptions.Counters, "");
        compileAndInspect("testRepeatedControlFlow", options);
        Assert.assertTrue("expected a fast-path recovery split", fastPathRecoverySplits > 0);
        Assert.assertTrue("expected recovery to use a registered predecessor hint", fastPathPredecessorRegisterHints > 0);
    }

    @Test
    public void testSpillFromCommonPredecessor() {
        compileAndInspect("testCommonPredecessorSpill");
        Assert.assertTrue("expected a low-pressure path marker", sawLowPressurePathMarker);
        Assert.assertTrue("expected the common predecessor to require stack traffic", highPressurePathSpillStores > 0 || highPressurePathSpillReloads > 0);
    }

    @Test
    public void testHandlerStubMarksPredecessorPaths() {
        StructuredGraph graph = parseEager("testFastPathMarking", StructuredGraph.AllowAssumptions.NO);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        MultiReturnNode multiReturn = graph.addOrUnique(new MultiReturnNode(returnNode.result(), null));
        returnNode.replaceFirstInput(returnNode.result(), multiReturn);

        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(graph);
        cfg.markFastPathBlocks();
        Assert.assertTrue("expected multiple blocks in the handler stub", cfg.getBlocks().length > 1);
        int fastPathBlockCount = 0;
        boolean sawDeoptBlock = false;
        for (HIRBlock block : cfg.getBlocks()) {
            if (block.getEndNode() instanceof DeoptimizeNode) {
                sawDeoptBlock = true;
                Assert.assertFalse("deoptimization block was marked as fast path: " + block, block.isFastPathBlock());
            }
            if (block.isFastPathBlock()) {
                fastPathBlockCount++;
            }
        }
        Assert.assertTrue("expected a deoptimization block", sawDeoptBlock);
        Assert.assertTrue("expected a marked path to the handler-stub return", fastPathBlockCount > 0);
        Assert.assertTrue("every handler-stub block was marked as fast path", fastPathBlockCount < cfg.getBlocks().length);
    }

    @Test
    public void testHandlerStubMarksLoopPaths() {
        StructuredGraph graph = parseEager("testLoopFastPathMarking", StructuredGraph.AllowAssumptions.NO);
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        MultiReturnNode multiReturn = graph.addOrUnique(new MultiReturnNode(returnNode.result(), null));
        returnNode.replaceFirstInput(returnNode.result(), multiReturn);

        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(graph);
        cfg.markFastPathBlocks();
        boolean sawLoopEnd = false;
        for (HIRBlock block : cfg.getBlocks()) {
            sawLoopEnd |= block.isLoopEnd();
            Assert.assertTrue("normal loop path was not marked as fast path: " + block, block.isFastPathBlock());
        }
        Assert.assertTrue("expected a loop backedge in the handler stub", sawLoopEnd);
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
    public void testNarrowFastPathMarking() {
        fastPathMarking = FastPathMarking.LOW_PRESSURE_PATH;
        compileAndInspect("testControlFlow");
        Assert.assertTrue("expected a fast-path block", sawFastPathBlock);
        Assert.assertEquals("unexpected spill stores on the marked path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillStores);
        Assert.assertEquals("unexpected spill reloads on the marked path:\n" + lowPressurePathBlock, 0, lowPressurePathSpillReloads);
    }

    @Test
    public void testUnmarkedCompilationIsNotTreatedAsFastPath() {
        fastPathMarking = FastPathMarking.NONE;
        compileAndInspect("testControlFlow");
        Assert.assertFalse("ordinary compilation was marked as fast path", sawFastPathBlock);
        Assert.assertTrue("expected the high-pressure path to require stack traffic", totalSpillStores > 0 || totalSpillReloads > 0);
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
        suites.getPreAllocationOptimizationStage().prependPhase(new MarkFastPathBlocksPhase());
        suites.getPostAllocationOptimizationStage().prependPhase(new CheckAllocationPhase());
        return suites;
    }

    /** Marks all or selected test blocks before register allocation. */
    private final class MarkFastPathBlocksPhase extends LIRPhase<PreAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
            for (var block : lirGenRes.getLIR().getControlFlowGraph().getBlocks()) {
                boolean mark = fastPathMarking == FastPathMarking.ALL;
                if (fastPathMarking == FastPathMarking.LOW_PRESSURE_PATH) {
                    for (LIRInstruction instruction : lirGenRes.getLIR().getLIRforBlock(block)) {
                        mark |= instruction instanceof LowPressurePathMarkerOp;
                    }
                }
                if (mark) {
                    ((HIRBlock) block).markFastPathBlock();
                }
            }
        }
    }

    /** Counts spill moves and reads recovery counters after register allocation. */
    private final class CheckAllocationPhase extends LIRPhase<PostAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
            DebugContext debug = lirGenRes.getLIR().getDebug();
            fastPathRecoverySplits = FAST_PATH_RECOVERY_SPLITS.getCurrentValue(debug);
            fastPathRecoverySplitLimitReached = FAST_PATH_RECOVERY_SPLIT_LIMIT_REACHED.getCurrentValue(debug);
            fastPathPredecessorRegisterHints = FAST_PATH_PREDECESSOR_REGISTER_HINTS.getCurrentValue(debug);
            for (var block : lirGenRes.getLIR().getControlFlowGraph().getBlocks()) {
                sawFastPathBlock |= block.isFastPathBlock();
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
