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
public class CallClobberSpillTest extends GraalCompilerTest {
    private Consumer<Fixture> check;
    private boolean checked;
    private boolean markFastPaths = true;
    private boolean callClobberAwareSpilling = true;
    private int fastPathBlockLimit = Integer.MAX_VALUE;
    private int callPosition = 26;

    public static int snippet(int value) {
        if (value == 0) {
            GraalDirectives.sideEffect(1);
        } else {
            GraalDirectives.sideEffect(2);
        }
        return value;
    }

    @Test
    public void testBoundarySplitRanksAtCall() {
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            f.splitAtCall(interval);
            Assert.assertEquals("retain boundary placement", 20, interval.to());
            Assert.assertEquals("rank at the clobber, not the move boundary", 26, f.spillRank(interval));
        });
    }

    @Test
    public void testDisabledCallClobberAwareSpillingUsesBoundary() {
        callClobberAwareSpilling = false;
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            f.splitAtCall(interval);
            Assert.assertEquals(20, interval.to());
            Assert.assertEquals("disabled optimization retains endpoint ranking", 20, f.spillRank(interval));
        });
    }

    @Test
    public void testFastPathIntervalWithUnmarkedCallBlock() {
        fastPathBlockLimit = 1;
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            Assert.assertTrue(f.allocator.blockForId(2).isFastPathBlock());
            Assert.assertFalse(f.allocator.blockForId(26).isFastPathBlock());
            f.splitAtCall(interval);
            Assert.assertEquals(20, interval.to());
            Assert.assertEquals(26, f.spillRank(interval));
        });
    }

    @Test
    public void testUseBeforeCallIsPreserved() {
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            interval.addUsePos(22, RegisterPriority.ShouldHaveRegister, true);
            f.splitAtCall(interval);
            Assert.assertEquals(25, interval.to());
            Assert.assertEquals("the register use before the call must win", 22, f.spillRank(interval));
        });
    }

    @Test
    public void testUnmarkedCallSplitUsesBoundary() {
        markFastPaths = false;
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            f.splitAtCall(interval);
            Assert.assertEquals("unmarked blocks retain boundary splitting", 20, interval.to());
            Assert.assertEquals("unmarked blocks retain endpoint ranking", 20, f.spillRank(interval));
        });
    }

    @Test
    public void testFurtherSplitUsesNewEndpoint() {
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            f.splitAtCall(interval);
            invoke(interval, "split", 12, f.allocator);
            Assert.assertEquals("spill ranking must follow the shortened child", 12, f.spillRank(interval));
        });
    }

    @Test
    public void testNoClobberRetainsEndpointRank() {
        callPosition = -1;
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            invoke(f.walker, "splitWhenPartialRegisterAvailable", interval, 28);
            Assert.assertEquals(20, interval.to());
            Assert.assertEquals("no clobber to extend the ranking", 20, f.spillRank(interval));
        });
    }

    @Test
    public void testFollowingChildUseCapsRank() {
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            f.splitAtCall(interval);
            Interval child = (Interval) invoke(interval, "getSplitChildAtOpId", 22, LIRInstruction.OperandMode.USE, f.allocator);
            child.addUsePos(22, RegisterPriority.ShouldHaveRegister, true);
            Assert.assertNull("following child has not been allocated", child.location());
            Assert.assertEquals(20, interval.to());
            Assert.assertEquals("do not rank past a use in the following child", 22, f.spillRank(interval));
        });
    }

    @Test
    public void testFollowingChildSplitBeforeCall() {
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            f.splitAtCall(interval);
            Interval child = (Interval) invoke(interval, "getSplitChildAtOpId", 22, LIRInstruction.OperandMode.USE, f.allocator);
            invoke(child, "split", 24, f.allocator);
            Assert.assertEquals("do not look past another split", 20, f.spillRank(interval));
        });
    }

    @Test
    public void testHoleBeforeClobberRetainsEndpointRank() {
        checkAllocation(f -> {
            Interval interval = f.allocator.getOrCreateInterval(new Variable(f.kind, 0));
            interval.setKind(f.kind);
            invoke(interval, "addRange", 24, 38);
            invoke(interval, "addRange", 2, 22);
            interval.addUsePos(36, RegisterPriority.ShouldHaveRegister, true);
            f.splitAtCall(interval);
            Assert.assertEquals(20, interval.to());
            Assert.assertEquals("do not look through a lifetime hole", 20, f.spillRank(interval));
        });
    }

    @Test
    public void testClobberInAnotherBlockRetainsEndpointRank() {
        callPosition = 46;
        checkAllocation(f -> {
            Interval interval = f.interval(2);
            invoke(interval, "addRange", 2, 58);
            invoke(f.walker, "splitWhenPartialRegisterAvailable", interval, 26);
            Assert.assertEquals(20, interval.to());
            Assert.assertEquals("only consider clobbers in the split block", 20, f.spillRank(interval));
        });
    }

    @Test
    public void testCallSpillPreservesUnusedRegisterChild() {
        checkAllocation(f -> f.checkCallSpill(false));
    }

    @Test
    public void testCallSpillPreservesUsedRegisterChild() {
        checkAllocation(f -> f.checkCallSpill(true));
    }

    private void checkAllocation(Consumer<Fixture> action) {
        check = action;
        checked = false;
        compile(getResolvedJavaMethod("snippet"), null, getInitialOptions());
        Assert.assertTrue("allocator checks must actually run", checked);
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        suites.getPreAllocationOptimizationStage().appendPhase(new LIRPhase<PreAllocationOptimizationContext>() {
            @Override
            protected void run(TargetDescription target, LIRGenerationResult result, PreAllocationOptimizationContext context) {
                check.accept(new Fixture(target, result, context, markFastPaths, fastPathBlockLimit, callPosition, callClobberAwareSpilling));
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

        Fixture(TargetDescription target, LIRGenerationResult original, PreAllocationOptimizationContext context, boolean markFastPaths, int fastPathBlockLimit, int callPosition,
                        boolean callClobberAwareSpilling) {
            LIR source = original.getLIR();
            Assert.assertTrue("fixture requires at least two blocks", source.linearScanOrder().length >= 2);
            OptionValues options = new OptionValues(source.getOptions(), LinearScan.Options.LIROptLSRAMaxFastPathRecoverySplits, 0,
                            LinearScan.Options.LIROptLSRACallClobberAwareSpilling, callClobberAwareSpilling);
            LIR lir = new LIR(source.getControlFlowGraph(), source.linearScanOrder(), options, source.getDebug());
            kind = LIRKind.value(target.arch.getPlatformKind(JavaKind.Long));
            setField(lir, "numVariables", 1);
            for (int i = 0; i < lir.linearScanOrder().length; i++) {
                HIRBlock block = (HIRBlock) lir.getBlockById(lir.linearScanOrder()[i]);
                if (markFastPaths && i < fastPathBlockLimit) {
                    block.markFastPathBlock();
                }
                ArrayList<LIRInstruction> instructions = new ArrayList<>();
                for (int position = i * 20; position < (i + 1) * 20; position += 2) {
                    instructions.add(new PositionOp(position, callPosition));
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
            interval.addUsePos(36, RegisterPriority.ShouldHaveRegister, true);
            interval.setSpillState(SpillState.NoOptimization);
            return interval;
        }

        void splitAtCall(Interval interval) {
            invoke(walker, "splitWhenPartialRegisterAvailable", interval, 26);
        }

        int spillRank(Interval interval) {
            invoke(interval, "assignLocation", register.asValue(kind));
            invoke(walker, "initVarsForAlloc", interval);
            invoke(walker, "initUseLists", false);
            setField(walker, "currentPosition", 4);
            setField(getField(walker, "activeLists"), "any", interval);
            invoke(walker, "spillCollectActiveAny", RegisterPriority.LiveAtLoopEnd);
            return ((int[]) getField(walker, "usePos"))[register.number];
        }

        void checkCallSpill(boolean used) {
            Interval root = interval(2);
            root.addUsePos(2, RegisterPriority.MustHaveRegister, true);
            invoke(root, "assignLocation", register.asValue(kind));
            Interval parent = (Interval) invoke(root, "split", 22, allocator);
            if (used) {
                parent.addUsePos(24, RegisterPriority.ShouldHaveRegister, true);
            }
            invoke(parent, "assignLocation", register.asValue(kind));
            splitAtCall(parent);
            Assert.assertEquals(25, parent.to());
            Interval spilled = (Interval) invoke(root, "getSplitChildAtOpId", 26, LIRInstruction.OperandMode.USE, allocator);
            invoke(spilled, "split", 28, allocator);
            setField(walker, "currentPosition", 26);
            Field state = field(Interval.class, "state");
            Object active = java.util.Arrays.stream(state.getType().getEnumConstants()).filter(v -> v.toString().equals("Active")).findFirst().orElseThrow();
            setField(spilled, "state", active);
            invoke(walker, "splitForSpilling", spilled);
            Assert.assertTrue("the call child must spill", LIRValueUtil.isStackSlotValue(spilled.location()));
            Assert.assertEquals("retain the existing fast-path register child", register.asValue(kind), parent.location());
            Assert.assertEquals("the earlier defining child must retain its register", register.asValue(kind), root.location());
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
