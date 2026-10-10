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
package jdk.graal.compiler.lir.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.alloc.lsra.Interval;
import jdk.graal.compiler.lir.alloc.lsra.Interval.RegisterPriority;
import jdk.graal.compiler.lir.alloc.lsra.Interval.SpillState;
import jdk.graal.compiler.lir.alloc.lsra.LinearScan;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

/**
 * Exercises call-forced interval splitting and spill decisions with explicit instruction positions.
 * Reflection keeps access to the package-private allocator internals confined to this test. The
 * fixture uses a separate LIR and interval table; its synthetic instructions are never emitted.
 */
@AddExports({"jdk.graal.compiler/jdk.graal.compiler.lir.alloc.lsra", "jdk.graal.compiler/jdk.graal.compiler.lir.gen"})
public class CallClobberReloadTest extends GraalCompilerTest {
    private Consumer<Fixture> check;
    private boolean checked;
    private boolean markFastPaths = true;
    private boolean callClobberAwareSpilling = true;
    private int callPosition = 26;

    public static int snippet(int value) {
        if (value == 0) {
            GraalDirectives.sideEffect(1);
        } else {
            GraalDirectives.sideEffect(2);
        }
        GraalDirectives.controlFlowAnchor();
        return value;
    }

    public static int postCallSnippet(int value) {
        if (value == 0) {
            GraalDirectives.sideEffect(1);
        } else {
            GraalDirectives.sideEffect(2);
        }
        GraalDirectives.controlFlowAnchor();
        if (value == 1) {
            GraalDirectives.sideEffect(3);
        } else {
            GraalDirectives.sideEffect(4);
        }
        GraalDirectives.controlFlowAnchor();
        return value;
    }

    public static int loopSnippet(int value) {
        while (value > 0) {
            GraalDirectives.sideEffect(value);
            value--;
        }
        GraalDirectives.controlFlowAnchor();
        return value;
    }

    @Test
    public void testSpilledChildStaysOnStackUntilCall() {
        checkAllocation(f -> f.checkReload(true, false, 26, true));
    }

    @Test
    public void testDisabledCallClobberAwareSpilling() {
        callClobberAwareSpilling = false;
        checkAllocation(f -> f.checkReload(true, false, 26, false));
    }

    @Test
    public void testFreeRegisterAllocationDoesNotReload() {
        checkAllocation(f -> f.checkFreeRegisterAllocation());
    }

    @Test
    public void testAlreadySplitContinuationDoesNotReload() {
        callPosition = -2;
        checkAllocation(f -> f.checkAlreadySplit(false));
    }

    @Test
    public void testAlreadySplitContinuationPreservesUse() {
        callPosition = -2;
        checkAllocation(f -> f.checkAlreadySplit(true));
    }

    @Test
    public void testAlreadySplitContinuationWithoutCall() {
        callPosition = -1;
        checkAllocation(f -> f.checkAlreadySplit(false, false));
    }

    @Test
    public void testAlreadySplitContinuationDoesNotCrossHole() {
        callPosition = -2;
        checkAllocation(f -> f.checkAlreadySplit(false, true, true));
    }

    @Test
    public void testAlreadySplitSiblingCallDoesNotSpill() {
        callPosition = 46;
        checkAllocation(f -> f.checkAlreadySplit(false, true, false, true));
    }

    @Test
    public void testPartialRegisterSiblingCallDoesNotSpill() {
        callPosition = 46;
        checkAllocation(f -> f.checkPartialRegisterPath(true));
    }

    @Test
    public void testPartialRegisterStraightLineCallKeepsSpill() {
        callPosition = -2;
        checkAllocation(f -> f.checkPartialRegisterPath(false));
    }

    @Test
    public void testAlreadySplitNonAdjacentSuccessorKeepsSpill() {
        callPosition = -2;
        checkAllocation(f -> f.checkBranchingPaths(true, false));
    }

    @Test
    public void testPartialRegisterBranchingPathsKeepSpill() {
        callPosition = -2;
        checkAllocation(f -> f.checkBranchingPaths(false, false));
    }

    @Test
    public void testBranchingPathsPreserveInterveningUse() {
        callPosition = -2;
        checkAllocation(f -> f.checkBranchingPaths(false, true));
    }

    @Test
    public void testDifferentCallsOnBranchesKeepSpill() {
        callPosition = -4;
        checkAllocation(f -> f.checkPaths(10, 26, false, -1));
    }

    @Test
    public void testPartialRegisterLaterSiblingCallDoesNotSpill() {
        callPosition = 26;
        checkAllocation(f -> {
            // The first branch calls, but the later sibling reaches the merge use directly.
            Assert.assertSame(f.allocator.blockAt(3), f.allocator.blockAt(1).getSuccessorAt(0));
            Assert.assertSame(f.allocator.blockAt(3), f.allocator.blockAt(2).getSuccessorAt(0));
            f.checkPaths(20, 26, false, -1, false);
        });
    }

    @Test
    public void testPostCallContinuationKeepsSpill() {
        callPosition = -5;
        checkAllocation("postCallSnippet", f -> {
            for (int i = 0; i < f.allocator.blockCount() - 1; i++) {
                var block = f.allocator.blockAt(i);
                if (block.getPredecessorCount() > 1) {
                    int start = f.allocator.getFirstLirInstructionId(block);
                    Assert.assertEquals("the clobber must precede a branching continuation", 2, block.getSuccessorCount());
                    f.checkPaths(start, start + 6, false, -1);
                    return;
                }
            }
            Assert.fail("fixture must retain a merge before the final block");
        });
    }

    @Test
    public void testUseAtOtherBranchCallRetainsRegister() {
        callPosition = -4;
        checkAllocation(f -> f.checkPaths(10, 26, false, 46));
    }

    @Test
    public void testLoopBeforeCallKeepsSpill() {
        callPosition = -3;
        checkAllocation("loopSnippet", f -> f.checkLoopPaths(false));
    }

    @Test
    public void testLoopBackedgeUseRetainsRegister() {
        callPosition = -3;
        checkAllocation("loopSnippet", f -> f.checkLoopPaths(true));
    }

    @Test
    public void testMergeIgnoresPreviousRegisterChild() {
        callPosition = -2;
        checkAllocation(f -> f.checkMerge(false));
    }

    @Test
    public void testMergePreservesUseBeforeCall() {
        callPosition = -2;
        checkAllocation(f -> f.checkMerge(true));
    }

    @Test
    public void testUseBeforeCallRetainsRegisterAllocation() {
        checkAllocation(f -> f.checkReload(true, true, 26, false));
    }

    @Test
    public void testRegisterPredecessorRetainsAllocation() {
        checkAllocation(f -> f.checkReload(false, false, 26, false));
    }

    @Test
    public void testNonCallConflictRetainsAllocation() {
        callPosition = -1;
        checkAllocation(f -> f.checkReload(true, false, 26, false));
    }

    @Test
    public void testUnmarkedBlocksRetainAllocation() {
        markFastPaths = false;
        checkAllocation(f -> f.checkReload(true, false, 26, false));
    }

    @Test
    public void testUseAtCallRetainsAllocation() {
        checkAllocation(f -> {
            Interval root = f.interval(2);
            root.addUsePos(26, RegisterPriority.ShouldHaveRegister, true);
            f.checkReload(true, false, 26, false);
        });
    }

    private void checkAllocation(Consumer<Fixture> action) {
        checkAllocation("snippet", action);
    }

    private void checkAllocation(String snippet, Consumer<Fixture> action) {
        check = action;
        checked = false;
        compile(getResolvedJavaMethod(snippet), null, getInitialOptions());
        Assert.assertTrue("allocator checks must actually run", checked);
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        suites.getPreAllocationOptimizationStage().appendPhase(new LIRPhase<PreAllocationOptimizationContext>() {
            @Override
            protected void run(TargetDescription target, LIRGenerationResult result, PreAllocationOptimizationContext context) {
                check.accept(new Fixture(target, result, context, markFastPaths, callPosition, callClobberAwareSpilling));
                checked = true;
            }
        });
        return suites;
    }

    private static final class PositionOp extends LIRInstruction {
        private static final LIRInstructionClass<PositionOp> TYPE = LIRInstructionClass.create(PositionOp.class);

        private final boolean clobbers;

        PositionOp(int position, int callPosition) {
            super(TYPE);
            setId(position);
            clobbers = position == callPosition;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return clobbers;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            throw new AssertionError("fixture instructions must not be emitted");
        }
    }

    private static final class Fixture {
        private final LinearScan allocator;
        private final Object walker;
        private final Register register;
        private final LIRKind kind;

        Fixture(TargetDescription target, LIRGenerationResult original, PreAllocationOptimizationContext context, boolean markFastPaths, int callPosition, boolean callClobberAwareSpilling) {
            LIR source = original.getLIR();
            Assert.assertTrue("fixture requires at least two blocks", source.linearScanOrder().length >= 2);
            OptionValues options = new OptionValues(source.getOptions(), LinearScan.Options.LIROptLSRAMaxFastPathRecoverySplits, 0,
                            LinearScan.Options.LIROptLSRACallClobberAwareSpilling, callClobberAwareSpilling);
            LIR lir = new LIR(source.getControlFlowGraph(), source.linearScanOrder(), options, source.getDebug());
            kind = LIRKind.value(target.arch.getPlatformKind(JavaKind.Long));
            setField(lir, "numVariables", 1);
            if (callPosition == -5) {
                for (int i = 0; i < lir.linearScanOrder().length; i++) {
                    if (lir.getBlockById(lir.linearScanOrder()[i]).getPredecessorCount() > 1) {
                        callPosition = i * 20 + 6;
                        break;
                    }
                }
            }
            for (int i = 0; i < lir.linearScanOrder().length; i++) {
                HIRBlock block = (HIRBlock) lir.getBlockById(lir.linearScanOrder()[i]);
                if (markFastPaths) {
                    block.markFastPathBlock();
                }
                ArrayList<LIRInstruction> instructions = new ArrayList<>();
                for (int position = i * 20; position < (i + 1) * 20; position += 2) {
                    int clobber = callPosition == -2 && block.getPredecessorCount() > 1 ? i * 20 + 6 : callPosition;
                    if (callPosition == -3 && i == lir.linearScanOrder().length - 1 || callPosition == -4 && (i == 1 || i == 2)) {
                        clobber = i * 20 + 6;
                    }
                    instructions.add(new PositionOp(position, clobber));
                }
                lir.setLIRforBlock(block, instructions);
            }
            LIRGenerationResult result = new LIRGenerationResult(CompilationIdentifier.INVALID_COMPILATION_ID, lir, original.getFrameMapBuilder(), original.getRegisterAllocationConfig(),
                            original.getCallingConvention());
            allocator = construct(LinearScan.class, target, result, context.lirGen.getSpillMoveFactory(), result.getRegisterAllocationConfig(), lir.linearScanOrder(), false);
            invoke(allocator, "initIntervals");
            invoke(allocator, "initOpIdMaps", lir.linearScanOrder().length * 10);
            for (int id : lir.linearScanOrder()) {
                var block = lir.getBlockById(id);
                for (LIRInstruction op : lir.getLIRforBlock(block)) {
                    invoke(allocator, "putOpIdMaps", op.id() / 2, op, block);
                }
            }
            try {
                Class<?> walkerClass = Class.forName("jdk.graal.compiler.lir.alloc.lsra.LinearScanWalker");
                Object end = getField(allocator, "intervalEndMarker");
                walker = construct(walkerClass, allocator, end, end);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
            register = result.getRegisterAllocationConfig().getAllocatableRegisters(kind.getPlatformKind()).allocatableRegisters.stream().filter(r -> (boolean) invoke(allocator, "isCallerSave",
                            r.asValue(kind))).findFirst().orElseThrow();
        }

        Interval interval(int from) {
            Interval interval = allocator.getOrCreateInterval(new Variable(kind, 0));
            interval.setKind(kind);
            invoke(interval, "addRange", from, 38);
            if ((int) invoke(interval, "firstUsage", RegisterPriority.ShouldHaveRegister) == Integer.MAX_VALUE) {
                interval.addUsePos(36, RegisterPriority.ShouldHaveRegister, true);
            }
            interval.setSpillState(SpillState.NoOptimization);
            return interval;
        }

        void checkReload(boolean spilled, boolean used, int clobber, boolean expected) {
            Interval root = interval(2);
            root.addUsePos(2, RegisterPriority.MustHaveRegister, true);
            // Queue the call-boundary child before deciding to spill its predecessor.
            Interval child = (Interval) invoke(root, "split", 20, allocator);
            if (spilled) {
                invoke(allocator, "assignSpillSlot", root);
            } else {
                invoke(root, "assignLocation", register.asValue(kind));
            }
            invoke(root, "makeCurrentSplitChild");
            if (used) {
                child.addUsePos(24, RegisterPriority.ShouldHaveRegister, true);
            }
            setField(walker, "currentPosition", 20);
            Field state = field(Interval.class, "state");
            Object active = java.util.Arrays.stream(state.getType().getEnumConstants()).filter(v -> v.toString().equals("Active")).findFirst().orElseThrow();
            setField(child, "state", active);
            Assert.assertEquals(expected, invoke(walker, "keepSpilledUntilCall", child, register, clobber));
            if (expected) {
                Assert.assertTrue("the unused pre-call child must never receive a register", LIRValueUtil.isStackSlotValue(child.location()));
                Assert.assertEquals(root.location(), child.location());
                Assert.assertTrue("keep the value on the stack across the clobber", child.to() > clobber);
                Interval next = (Interval) invoke(root, "getSplitChildAtOpId", 36, LIRInstruction.OperandMode.USE, allocator);
                Assert.assertNotSame(child, next);
                Assert.assertNull("leave allocation of the used child to the normal allocator", next.location());
                Assert.assertTrue("do not hoist recovery before the clobber", next.from() > clobber);
                Assert.assertEquals(36, invoke(next, "firstUsage", RegisterPriority.ShouldHaveRegister));
            } else {
                Assert.assertNull("declining must leave normal allocation unchanged", child.location());
                Assert.assertEquals(38, child.to());
            }
        }

        void checkMerge(boolean used) {
            HIRBlock merge = null;
            for (int i = 0; i < allocator.blockCount(); i++) {
                HIRBlock block = (HIRBlock) allocator.blockAt(i);
                if (block.getPredecessorCount() > 1) {
                    merge = block;
                    break;
                }
            }
            Assert.assertNotNull(merge);
            int start = allocator.getFirstLirInstructionId(merge);
            Interval root = allocator.getOrCreateInterval(new Variable(kind, 0));
            root.setKind(kind);
            invoke(root, "addRange", 2, start + 18);
            root.addUsePos(start + 16, RegisterPriority.ShouldHaveRegister, true);
            root.addUsePos(2, RegisterPriority.MustHaveRegister, true);
            root.setSpillState(SpillState.NoOptimization);
            Interval child = (Interval) invoke(root, "split", start, allocator);
            invoke(root, "assignLocation", register.asValue(kind));
            invoke(root, "makeCurrentSplitChild");
            if (used) {
                child.addUsePos(start + 4, RegisterPriority.ShouldHaveRegister, true);
            }
            setField(walker, "currentPosition", start);
            Field state = field(Interval.class, "state");
            Object active = java.util.Arrays.stream(state.getType().getEnumConstants()).filter(v -> v.toString().equals("Active")).findFirst().orElseThrow();
            setField(child, "state", active);
            Assert.assertEquals(!used, invoke(walker, "keepSpilledUntilCall", child, register, start + 6));
            if (used) {
                Assert.assertNull(child.location());
            } else {
                Assert.assertTrue(LIRValueUtil.isStackSlotValue(child.location()));
                Assert.assertTrue(child.to() > start + 6);
            }
            Assert.assertEquals("do not spill the earlier defining child", register.asValue(kind), root.location());
        }

        void checkFreeRegisterAllocation() {
            Interval root = interval(2);
            root.addUsePos(2, RegisterPriority.MustHaveRegister, true);
            Interval child = (Interval) invoke(root, "split", 20, allocator);
            invoke(allocator, "assignSpillSlot", root);
            invoke(root, "makeCurrentSplitChild");
            Interval fixed = allocator.getOrCreateInterval(register.asValue(kind));
            invoke(fixed, "addRange", 26, 27);
            setField(getField(walker, "inactiveLists"), "fixed", fixed);
            setField(walker, "currentPosition", 20);
            Field state = field(Interval.class, "state");
            Object active = java.util.Arrays.stream(state.getType().getEnumConstants()).filter(v -> v.toString().equals("Active")).findFirst().orElseThrow();
            setField(child, "state", active);
            invoke(walker, "initVarsForAlloc", child);
            setField(walker, "availableRegs", new Register[]{register});
            Assert.assertEquals(true, invoke(walker, "allocFreeRegister", child));
            Assert.assertTrue("even a free register is unnecessary before the clobber", LIRValueUtil.isStackSlotValue(child.location()));
            Assert.assertTrue(child.to() > 26);
        }

        void checkBranchingPaths(boolean alreadySplit, boolean used) {
            int boundary = allocator.getFirstLirInstructionId(allocator.blockAt(allocator.blockCount() - 1));
            var firstBranch = allocator.blockAt(1);
            Assert.assertEquals(1, firstBranch.getSuccessorCount());
            Assert.assertSame("the successor must skip the sibling in allocation order", allocator.blockForId(boundary), firstBranch.getSuccessorAt(0));
            Assert.assertNotSame(firstBranch.getSuccessorAt(0), allocator.blockAt(2));
            checkPaths(alreadySplit ? 20 : 10, boundary + 6, alreadySplit, used ? 44 : -1);
        }

        void checkLoopPaths(boolean used) {
            for (int i = 0; i < allocator.blockCount(); i++) {
                var block = allocator.blockAt(i);
                if (block.isLoopHeader()) {
                    int start = allocator.getFirstLirInstructionId(block) + 8;
                    int clobber = allocator.getFirstLirInstructionId(allocator.blockAt(allocator.blockCount() - 1)) + 6;
                    Assert.assertTrue(start < clobber);
                    // A use before the child start is reached again through the backedge.
                    checkPaths(start, clobber, false, used ? start - 4 : -1);
                    return;
                }
            }
            Assert.fail("fixture must retain the loop");
        }

        void checkPaths(int start, int clobber, boolean alreadySplit, int use) {
            checkPaths(start, clobber, alreadySplit, use, use < 0);
        }

        void checkPaths(int start, int clobber, boolean alreadySplit, int use, boolean expectSpill) {
            int end = allocator.getFirstLirInstructionId(allocator.blockAt(allocator.blockCount() - 1)) + 18;
            Interval root = allocator.getOrCreateInterval(new Variable(kind, 0));
            root.setKind(kind);
            invoke(root, "addRange", 2, end);
            root.addUsePos(end - 2, RegisterPriority.ShouldHaveRegister, true);
            if (use >= 0) {
                root.addUsePos(use, RegisterPriority.ShouldHaveRegister, true);
            }
            root.addUsePos(2, RegisterPriority.MustHaveRegister, true);
            root.setSpillState(SpillState.NoOptimization);
            if (alreadySplit) {
                invoke(root, "split", allocator.getFirstLirInstructionId(allocator.blockForId(clobber)), allocator);
            }
            Interval child = (Interval) invoke(root, "split", start, allocator);
            invoke(allocator, "assignSpillSlot", root);
            invoke(root, "makeCurrentSplitChild");
            Interval fixed = allocator.getOrCreateInterval(register.asValue(kind));
            invoke(fixed, "addRange", clobber, clobber + 1);
            setField(getField(walker, "inactiveLists"), "fixed", fixed);
            setField(walker, "currentPosition", start);
            Field state = field(Interval.class, "state");
            Object active = java.util.Arrays.stream(state.getType().getEnumConstants()).filter(v -> v.toString().equals("Active")).findFirst().orElseThrow();
            setField(child, "state", active);
            invoke(walker, "initVarsForAlloc", child);
            setField(walker, "availableRegs", new Register[]{register});
            Assert.assertEquals(true, invoke(walker, "allocFreeRegister", child));
            Assert.assertEquals("keep the spill only if no path uses the register before a clobber", expectSpill, LIRValueUtil.isStackSlotValue(child.location()));
        }

        void checkPartialRegisterPath(boolean sibling) {
            int boundary = allocator.getFirstLirInstructionId(allocator.blockAt(sibling ? 2 : allocator.blockCount() - 1));
            int start = boundary - 10;
            var currentBlock = allocator.blockForId(start);
            var callBlock = allocator.blockForId(boundary);
            Assert.assertEquals(1, currentBlock.getSuccessorCount());
            Assert.assertEquals("fixture must distinguish a successor from a sibling", !sibling, currentBlock.getSuccessorAt(0) == callBlock);
            Interval root = allocator.getOrCreateInterval(new Variable(kind, 0));
            root.setKind(kind);
            invoke(root, "addRange", 2, (sibling ? allocator.getFirstLirInstructionId(allocator.blockAt(allocator.blockCount() - 1)) : boundary) + 18);
            if (sibling) {
                // The bypass must actually need a register without executing the sibling call.
                root.addUsePos(allocator.getFirstLirInstructionId(allocator.blockAt(allocator.blockCount() - 1)) + 16, RegisterPriority.ShouldHaveRegister, true);
            }
            root.addUsePos(boundary + 16, RegisterPriority.ShouldHaveRegister, true);
            root.addUsePos(2, RegisterPriority.MustHaveRegister, true);
            root.setSpillState(SpillState.NoOptimization);
            Interval child = (Interval) invoke(root, "split", start, allocator);
            invoke(allocator, "assignSpillSlot", root);
            invoke(root, "makeCurrentSplitChild");
            Interval fixed = allocator.getOrCreateInterval(register.asValue(kind));
            invoke(fixed, "addRange", boundary + 6, boundary + 7);
            setField(getField(walker, "inactiveLists"), "fixed", fixed);
            setField(walker, "currentPosition", start);
            Field state = field(Interval.class, "state");
            Object active = java.util.Arrays.stream(state.getType().getEnumConstants()).filter(v -> v.toString().equals("Active")).findFirst().orElseThrow();
            setField(child, "state", active);
            invoke(walker, "initVarsForAlloc", child);
            setField(walker, "availableRegs", new Register[]{register});
            Assert.assertTrue("the clobber is inside this child", boundary + 6 < child.to());
            Assert.assertEquals(true, invoke(walker, "allocFreeRegister", child));
            if (sibling) {
                Assert.assertEquals("a sibling call must not spill the current path", register.asValue(kind), child.location());
            } else {
                Assert.assertTrue("an unavoidable call should still prevent a reload", LIRValueUtil.isStackSlotValue(child.location()));
                Assert.assertTrue(child.to() > boundary + 6);
            }
        }

        void checkAlreadySplit(boolean used) {
            checkAlreadySplit(used, true);
        }

        void checkAlreadySplit(boolean used, boolean hasCall) {
            checkAlreadySplit(used, hasCall, false);
        }

        void checkAlreadySplit(boolean used, boolean hasCall, boolean hole) {
            checkAlreadySplit(used, hasCall, hole, false);
        }

        void checkAlreadySplit(boolean used, boolean hasCall, boolean hole, boolean sibling) {
            int boundary = allocator.getFirstLirInstructionId(allocator.blockAt(sibling ? 2 : allocator.blockCount() - 1));
            int start = boundary - 10;
            var currentBlock = allocator.blockForId(start);
            var callBlock = allocator.blockForId(boundary);
            Assert.assertEquals(1, currentBlock.getSuccessorCount());
            Assert.assertEquals("fixture must distinguish a successor from a sibling", !sibling, currentBlock.getSuccessorAt(0) == callBlock);
            Interval root = allocator.getOrCreateInterval(new Variable(kind, 0));
            root.setKind(kind);
            invoke(root, "addRange", hole ? start + 8 : 2, (sibling ? allocator.getFirstLirInstructionId(allocator.blockAt(allocator.blockCount() - 1)) : boundary) + 18);
            if (sibling) {
                // The bypass must actually need a register without executing the sibling call.
                root.addUsePos(allocator.getFirstLirInstructionId(allocator.blockAt(allocator.blockCount() - 1)) + 16, RegisterPriority.ShouldHaveRegister, true);
            }
            root.addUsePos(boundary + 16, RegisterPriority.ShouldHaveRegister, true);
            root.setSpillState(SpillState.NoOptimization);
            if (hole) {
                invoke(root, "addRange", 2, start + 4);
            }
            if (used) {
                root.addUsePos(boundary + 4, RegisterPriority.ShouldHaveRegister, true);
            }
            root.addUsePos(2, RegisterPriority.MustHaveRegister, true);
            Interval continuation = (Interval) invoke(root, "split", boundary, allocator);
            Interval child = (Interval) invoke(root, "split", start, allocator);
            invoke(allocator, "assignSpillSlot", root);
            invoke(root, "makeCurrentSplitChild");
            Interval fixed = allocator.getOrCreateInterval(register.asValue(kind));
            invoke(fixed, "addRange", boundary + 6, boundary + 7);
            setField(getField(walker, "inactiveLists"), "fixed", fixed);
            setField(walker, "currentPosition", start);
            Field state = field(Interval.class, "state");
            Object active = java.util.Arrays.stream(state.getType().getEnumConstants()).filter(v -> v.toString().equals("Active")).findFirst().orElseThrow();
            setField(child, "state", active);
            invoke(walker, "initVarsForAlloc", child);
            setField(walker, "availableRegs", new Register[]{register});
            Assert.assertEquals(true, invoke(walker, "allocFreeRegister", child));
            Assert.assertEquals("look through the already-split child before choosing a register", hasCall && !used && !hole && !sibling, LIRValueUtil.isStackSlotValue(child.location()));
            Assert.assertEquals(boundary, child.to());
            Assert.assertNull("leave the continuation for normal allocation", continuation.location());
        }
    }

    private static Field field(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field result = current.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException e) {
                // Look in the superclass.
            }
        }
        throw new AssertionError(name);
    }

    private static Object getField(Object receiver, String name) {
        try {
            return field(receiver.getClass(), name).get(receiver);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setField(Object receiver, String name, Object value) {
        try {
            field(receiver.getClass(), name).set(receiver, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object invoke(Object receiver, String name, Object... args) {
        for (Class<?> type = receiver.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                    try {
                        method.setAccessible(true);
                        return method.invoke(receiver, args);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                }
            }
        }
        throw new AssertionError(name);
    }

    private static <T> T construct(Class<T> type, Object... args) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == args.length) {
                try {
                    constructor.setAccessible(true);
                    return type.cast(constructor.newInstance(args));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError(type);
    }
}
