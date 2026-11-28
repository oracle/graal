/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.EarlyEscapeAnalysis;
import com.oracle.truffle.api.CompilerDirectives.EarlyInline;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExplosionKeyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.LoopExplosionPlugin;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectState;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tests {@link CompilerDirectives#mergeExplodeKey(int)} and
 * {@link CompilerDirectives#mergeExplodeKey(long)} for explicit merge explode keys.
 */
@SuppressWarnings("deprecation")
public class MergeExplodeKeyTest extends PartialEvaluationTest {

    static final class VirtualState {
        int key;
        int value;

        VirtualState(int key, int value) {
            this.key = key;
            this.value = value;
        }
    }

    static final class NestedVirtualState {
        VirtualState inner;

        NestedVirtualState(VirtualState inner) {
            this.inner = inner;
        }
    }

    static final class LongVirtualState {
        long key;

        @EarlyInline
        LongVirtualState(long key) {
            this.key = key;
        }
    }

    static final class NestedLongVirtualState {
        final LongVirtualState inner;

        @EarlyInline
        NestedLongVirtualState(LongVirtualState inner) {
            this.inner = inner;
        }
    }

    static final class LoopControl {
        final boolean enterAtOne;
        int remainingTransitions;

        LoopControl(boolean enterAtOne, int remainingTransitions) {
            this.enterAtOne = enterAtOne;
            this.remainingTransitions = remainingTransitions;
        }
    }

    public static class Bytecode {
        public static final byte CONST = 0;
        public static final byte ARGUMENT = 1;
        public static final byte ADD = 2;
        public static final byte SUB = 3;
        public static final byte DUP = 4;
        public static final byte POP = 5;
        public static final byte JMP = 6;
        public static final byte IFZERO = 7;
        public static final byte RETURN = 8;
    }

    public static class Program extends RootNode {
        protected final String name;
        @CompilationFinal(dimensions = 1) protected final byte[] bytecodes;
        protected final int stackOffset;
        /**
         * Temporary testing switch to ensure only one variable can be marked and marking multiple
         * results in a bailout.
         */
        protected final boolean markTopAsKey;

        static Program create(String name, byte[] bytecodes, int maxStack, boolean markTopAsKey) {
            var builder = FrameDescriptor.newBuilder();
            int stackOffset = builder.addSlots(maxStack, FrameSlotKind.Int);
            return new Program(name, bytecodes, builder.build(), stackOffset, markTopAsKey);
        }

        Program(String name, byte[] bytecodes, FrameDescriptor descriptor, int stackOffset, boolean markTopAsKey) {
            super(null, descriptor);
            this.name = name;
            this.bytecodes = bytecodes;
            this.stackOffset = stackOffset;
            this.markTopAsKey = markTopAsKey;
        }

        protected void setInt(VirtualFrame frame, int stackIndex, int value) {
            frame.setInt(stackOffset + stackIndex, value);
        }

        protected int getInt(VirtualFrame frame, int stackIndex) {
            return frame.getInt(stackOffset + stackIndex);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        public Object execute(VirtualFrame frame) {
            int top = -1;
            int bci = 0;
            bci = CompilerDirectives.mergeExplodeKey(bci);
            if (markTopAsKey) {
                // Testing error on multiple key variables.
                // TODO(GR-71753): this becomes obsolete
                top = CompilerDirectives.mergeExplodeKey(top);
            }
            while (true) {
                CompilerAsserts.partialEvaluationConstant(bci);
                switch (bytecodes[bci]) {
                    case Bytecode.CONST: {
                        byte value = bytecodes[bci + 1];
                        top++;
                        setInt(frame, top, value);
                        bci = bci + 2;
                        continue;
                    }
                    case Bytecode.ARGUMENT: {
                        int value = (int) frame.getArguments()[bytecodes[bci + 1]];
                        top++;
                        setInt(frame, top, value);
                        bci = bci + 2;
                        continue;
                    }

                    case Bytecode.ADD: {
                        int left = getInt(frame, top);
                        int right = getInt(frame, top - 1);
                        top--;
                        setInt(frame, top, left + right);
                        bci = bci + 1;
                        continue;
                    }
                    case Bytecode.SUB: {
                        int left = getInt(frame, top);
                        int right = getInt(frame, top - 1);
                        top--;
                        setInt(frame, top, left - right);
                        bci = bci + 1;
                        continue;
                    }
                    case Bytecode.DUP: {
                        int dupValue = getInt(frame, top);
                        top++;
                        setInt(frame, top, dupValue);
                        bci++;
                        continue;
                    }
                    case Bytecode.POP: {
                        top--;
                        bci++;
                        continue;
                    }

                    case Bytecode.JMP: {
                        byte newBci = bytecodes[bci + 1];
                        bci = newBci;
                        continue;
                    }
                    case Bytecode.IFZERO: {
                        int value = getInt(frame, top);
                        top--;
                        if (value == 0) {
                            bci = bytecodes[bci + 1];
                            continue;
                        } else {
                            bci = bci + 2;
                            continue;
                        }
                    }
                    case Bytecode.RETURN: {
                        int value = getInt(frame, top);
                        return value;
                    }

                    default: {
                        throw shouldNotReachHere();
                    }
                }
            }
        }
    }

    @Test
    public void constReturnProgram() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.RETURN};
        partialEval(Program.create("constReturnProgram", bytecodes, 2, false));
    }

    @Test
    public void constAddProgram() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */40,
                        /* 2: */Bytecode.CONST,
                        /* 3: */2,
                        /* 4: */Bytecode.ADD,
                        /* 5: */Bytecode.RETURN};
        partialEval(Program.create("constAddProgram", bytecodes, 2, false));
    }

    @Test
    public void keyCanBeUsedAfterLoop() {
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithKeyAfterLoop();
            }
        };
        OptimizedCallTarget target = compileHelper("keyCanBeUsedAfterLoop", root, new Object[0]);
        Assert.assertEquals(1, target.call());
    }

    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private static int executeWithKeyAfterLoop() {
        int bci = CompilerDirectives.mergeExplodeKey(0);
        while (true) {
            CompilerAsserts.partialEvaluationConstant(bci);
            if (bci == 0) {
                bci = 1;
                continue;
            }
            break;
        }
        return bci;
    }

    @Test
    public void keyInMergeExplodeMethodWithoutLoopFails() {
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return keyInMergeExplodeMethodWithoutLoop();
            }
        };
        try {
            compile((OptimizedCallTarget) root.getCallTarget(), partialEval(root));
            Assert.fail("Expected a bailout for a merge key that is not used by a merge exploded loop");
        } catch (BailoutException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("must only be used with a merge exploded loop"));
        }
    }

    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private static int keyInMergeExplodeMethodWithoutLoop() {
        return CompilerDirectives.mergeExplodeKey(42);
    }

    @Test
    public void nestedMergeKeyWithoutStableVirtualPathFails() {
        RootNode root = nestedEscapingFieldKeyProgram();
        try {
            compile((OptimizedCallTarget) root.getCallTarget(), partialEval(root));
            Assert.fail("Expected a bailout for a nested merge key whose object path escapes");
        } catch (BailoutException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("CompilerDirectives.mergeExplodeKey"));
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("EarlyEscapeAnalysis"));
        }
    }

    private static RootNode nestedEscapingFieldKeyProgram() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithEscapingNestedFieldMarker();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithEscapingNestedFieldMarker() {
                NestedVirtualState state = new NestedVirtualState(new VirtualState(0, 0));
                state.inner.key = CompilerDirectives.mergeExplodeKey(state.inner.key);

                while (true) {
                    switch (state.inner.key) {
                        case 0:
                            escape(state);
                            state.inner.key = 1;
                            continue;
                        case 1:
                            return 42;
                        default:
                            throw shouldNotReachHere();
                    }
                }
            }
        };
    }

    @CompilerDirectives.TruffleBoundary
    private static void escape(Object object) {
    }

    @Test
    public void keyOutsideMergeExplodeFails() {
        RootNode root = new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return CompilerDirectives.mergeExplodeKey(1);
            }
        };
        try {
            compile((OptimizedCallTarget) root.getCallTarget(), partialEval(root));
            Assert.fail("Expected a bailout for a key outside a merge exploded method");
        } catch (BailoutException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("must only be used with a merge exploded loop"));
        }
    }

    @Test
    public void multipleKeyVariables() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.CONST,
                        /* 1: */42,
                        /* 2: */Bytecode.RETURN};
        try {
            partialEval(Program.create("multipleKeyVariables", bytecodes, 2, true));
            Assert.fail("Expected a bailout for multiple merge key variables (until GR-71753 adds support for multiple merge keys)");
        } catch (BailoutException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("more than one specified merge key local"));
        }
    }

    @Test(expected = BailoutException.class)
    public void variableStackSize() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.ARGUMENT,
                        /* 1: */0,
                        /* 2: */Bytecode.IFZERO,
                        /* 3: */6,
                        /* 4: */Bytecode.CONST,
                        /* 5: */40,
                        /* 6: */Bytecode.CONST,
                        /* 7: */42,
                        /* 8: */Bytecode.RETURN};
        partialEval(Program.create("variableStackSize", bytecodes, 3, false), 0);
    }

    @Test(expected = BailoutException.class)
    public void explicitKeyRequiresMatchingVirtualObjectState() {
        partialEval(explicitKeyWithVirtualStateProgram());
    }

    private static RootNode explicitKeyWithVirtualStateProgram() {
        return new RootNode(null) {
            @Override
            public String toString() {
                return "explicitKeyWithVirtualStateProgram";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithExplicitKey();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithExplicitKey() {
                int bci = 0;
                VirtualState state = new VirtualState(0, 0);

                bci = CompilerDirectives.mergeExplodeKey(bci);
                while (true) {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    switch (bci) {
                        case 0:
                            if (state.value == 2) {
                                return state.value;
                            }
                            state.value++;
                            continue;
                        default:
                            throw shouldNotReachHere();
                    }
                }
            }
        };
    }

    @Test
    public void nestedFieldCanBeUsedAsMergeKey() {
        RootNode root = nestedFieldKeyProgram();
        OptimizedCallTarget target = compileHelper("nestedFieldCanBeUsedAsMergeKey", root, new Object[0]);
        Assert.assertEquals(42, target.call());
    }

    private static RootNode nestedFieldKeyProgram() {
        return new RootNode(null) {
            @Override
            public String toString() {
                return "nestedFieldKeyProgram";
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithNestedFieldMarker();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithNestedFieldMarker() {
                NestedVirtualState state = new NestedVirtualState(new VirtualState(0, 0));
                state.inner.key = CompilerDirectives.mergeExplodeKey(state.inner.key);

                while (true) {
                    switch (state.inner.key) {
                        case 0:
                            state.inner.key = 1;
                            continue;
                        case 1:
                            return 42;
                        default:
                            throw shouldNotReachHere();
                    }
                }
            }
        };
    }

    @Test
    public void aliasesOfNestedFieldAreOneMergeKey() {
        RootNode root = aliasedNestedFieldKeyProgram();
        Assert.assertEquals(42, root.getCallTarget().call());
        OptimizedCallTarget target = compileHelper("aliasesOfNestedFieldAreOneMergeKey", root, new Object[0]);
        Assert.assertEquals(42, target.call());
    }

    private static RootNode aliasedNestedFieldKeyProgram() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithAliasedKey();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithAliasedKey() {
                NestedVirtualState state = new NestedVirtualState(new VirtualState(0, 0));
                VirtualState alias = state.inner;
                state.inner.key = CompilerDirectives.mergeExplodeKey(state.inner.key);

                while (true) {
                    /* Both locals stay live at the header, but identify the same keyed field. */
                    if (state.inner.key == 0) {
                        state.inner.key = 1;
                        continue;
                    }
                    return 41 + alias.key;
                }
            }
        };
    }

    @Test
    public void distinctNestedFieldsAreDifferentMergeKeys() {
        try {
            partialEval(distinctNestedFieldKeysProgram());
            Assert.fail("Expected a bailout for distinct keyed fields with equal initial values (until GR-71753 adds support for multiple merge keys)");
        } catch (BailoutException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("more than one specified merge key local"));
        }
    }

    private static RootNode distinctNestedFieldKeysProgram() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithDistinctKeys();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithDistinctKeys() {
                VirtualState state = new VirtualState(0, 0);
                state.key = CompilerDirectives.mergeExplodeKey(state.key);
                state.value = CompilerDirectives.mergeExplodeKey(state.value);
                while (true) {
                    if (state.key == 0) {
                        state.key = 1;
                        continue;
                    }
                    return 42 + state.value;
                }
            }
        };
    }

    @Test
    public void sharedVirtualObjectPathsAreOneMergeKey() {
        RootNode root = sharedVirtualObjectKeyProgram();
        Assert.assertEquals(42, root.getCallTarget().call());
        OptimizedCallTarget target = compileHelper("sharedVirtualObjectPathsAreOneMergeKey", root, new Object[0]);
        Assert.assertEquals(42, target.call());
    }

    private static RootNode sharedVirtualObjectKeyProgram() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithSharedKey();
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithSharedKey() {
                /*
                 * Thirteen objects encode 4096 paths to one field. Keep construction straight-line
                 * to avoid adding a second top-level loop, and bound the depth so a regression does
                 * not exhaust the test VM's heap. Only root is live at the loop header.
                 */
                SharedKeyState leaf = new SharedKeyState(null);
                SharedKeyState level1 = new SharedKeyState(leaf);
                SharedKeyState level2 = new SharedKeyState(level1);
                SharedKeyState level3 = new SharedKeyState(level2);
                SharedKeyState level4 = new SharedKeyState(level3);
                SharedKeyState level5 = new SharedKeyState(level4);
                SharedKeyState level6 = new SharedKeyState(level5);
                SharedKeyState level7 = new SharedKeyState(level6);
                SharedKeyState level8 = new SharedKeyState(level7);
                SharedKeyState level9 = new SharedKeyState(level8);
                SharedKeyState level10 = new SharedKeyState(level9);
                SharedKeyState level11 = new SharedKeyState(level10);
                SharedKeyState root = new SharedKeyState(level11);
                leaf.key = CompilerDirectives.mergeExplodeKey(leaf.key);

                while (true) {
                    if (root.left.left.left.left.left.left.left.left.left.left.left.left.key == 0) {
                        root.left.left.left.left.left.left.left.left.left.left.left.left.key = 1;
                        continue;
                    }
                    return 41 + root.left.left.left.left.left.left.left.left.left.left.left.left.key;
                }
            }
        };
    }

    static final class SharedKeyState {
        final SharedKeyState left;
        final SharedKeyState right;
        int key;

        @EarlyInline
        SharedKeyState(SharedKeyState child) {
            this.left = child;
            this.right = child;
        }
    }

    @Test
    public void scalarLongKeyCanBeUsedAfterLoop() {
        assertLongKeyProgram("scalarLongKeyCanBeUsedAfterLoop", false, false);
    }

    @Test
    public void nestedLongFieldCanBeUsedAsMergeKey() {
        assertLongKeyProgram("nestedLongFieldCanBeUsedAsMergeKey", true, false);
    }

    @Test
    public void scalarLongKeyInIrreducibleLoop() {
        assertLongKeyProgram("scalarLongKeyInIrreducibleLoop", false, true);
    }

    @Test
    public void nestedLongFieldKeyInIrreducibleLoop() {
        assertLongKeyProgram("nestedLongFieldKeyInIrreducibleLoop", true, true);
    }

    @Test
    public void unmarkedLongVariableInIrreducibleLoop() {
        assertLongKeyProgram("unmarkedLongVariableInIrreducibleLoop", longKeyProgram(false, true, false), true);
    }

    @Test
    public void unmarkedLongVariablesOutsideIntRangeInIrreducibleLoops() {
        for (long thirdKey : new long[]{(long) Integer.MAX_VALUE + 1, (long) Integer.MIN_VALUE - 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertLongKeyProgram("unmarkedLongVariablesOutsideIntRangeInIrreducibleLoops", longKeyProgram(false, true, false, thirdKey), true, thirdKey);
        }
    }

    @Test
    public void unmarkedNestedLongVariableInIrreducibleLoop() {
        // All differing state is virtual: there is no differing top-level scalar to infer a key.
        assertLongKeyProgram("unmarkedNestedLongVariableInIrreducibleLoop", longKeyProgram(true, true, false), true);
    }

    @Test
    public void longKeysAboveIntRangeInIrreducibleLoopsFail() {
        assertOutOfRangeLongKeysFail((long) Integer.MAX_VALUE + 1);
    }

    @Test
    public void longKeysBelowIntRangeInIrreducibleLoopsFail() {
        assertOutOfRangeLongKeysFail((long) Integer.MIN_VALUE - 1);
    }

    private void assertOutOfRangeLongKeysFail(long key) {
        for (boolean nested : new boolean[]{false, true}) {
            assertOutOfRangeLongKeyFails(longKeyProgram(nested, true, true, key), key);
        }
    }

    private void assertOutOfRangeLongKeyFails(RootNode root, long key) {
        // Exercise every entry variant and several backedges before compilation.
        for (int entry = 0; entry < 3; entry++) {
            for (int transitions = 0; transitions <= 5; transitions++) {
                root.getCallTarget().call(entry, transitions);
            }
        }
        Assert.assertEquals(key, root.getCallTarget().call(1, 1));
        BailoutException bailout = Assert.assertThrows(BailoutException.class, () -> partialEval(root, new Object[]{0, 0}));
        Assert.assertTrue(bailout.getMessage(), bailout.getMessage().contains("a long value that is representable as int"));
    }

    private void assertLongKeyProgram(String name, boolean nested, boolean irreducible) {
        assertLongKeyProgram(name, longKeyProgram(nested, irreducible, true), irreducible);
    }

    private void assertLongKeyProgram(String name, RootNode root, boolean irreducible) {
        assertLongKeyProgram(name, root, irreducible, HIGH_LONG_KEY);
    }

    private void assertLongKeyProgram(String name, RootNode root, boolean irreducible, long thirdKey) {
        assertLongKeyResults(root.getCallTarget(), irreducible, thirdKey);
        OptimizedCallTarget target = compileHelper(name, root, new Object[]{0, 0});
        assertLongKeyResults(target, irreducible, thirdKey);
        Assert.assertTrue("Long-key dispatch must not invalidate the compiled target", target.isValid());
    }

    private static final long HIGH_LONG_KEY = 1L << 30;

    private static void assertLongKeyResults(CallTarget target, boolean irreducible, long thirdKey) {
        long[][] keysByEntry = {
                        {-0x8000_0000L, 0x7fff_ffffL, thirdKey, -0x8000_0000L, 0x7fff_ffffL, thirdKey},
                        {-0x8000_0000L, thirdKey, -0x8000_0000L, thirdKey, -0x8000_0000L, thirdKey},
                        {-0x8000_0000L, thirdKey, 0x7fff_ffffL, thirdKey, 0x7fff_ffffL, thirdKey}};
        for (int entry = 0; entry < keysByEntry.length; entry++) {
            for (int transitions = 0; transitions < keysByEntry[entry].length; transitions++) {
                long expected = irreducible ? keysByEntry[entry][transitions] : thirdKey;
                Assert.assertEquals(expected, target.call(entry, transitions));
            }
        }
    }

    private static RootNode longKeyProgram(boolean nested, boolean irreducible, boolean markKey) {
        return longKeyProgram(nested, irreducible, markKey, HIGH_LONG_KEY);
    }

    private static RootNode longKeyProgram(boolean nested, boolean irreducible, boolean markKey, long thirdKey) {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                int entry = (int) frame.getArguments()[0];
                LoopControl control = new LoopControl(false, (int) frame.getArguments()[1]);
                return nested ? executeNested(entry, control, irreducible, markKey, thirdKey) : executeScalar(entry, control, irreducible, markKey, thirdKey);
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static long executeScalar(int entry, LoopControl control, boolean irreducible, boolean markKey, long thirdKey) {
                long key = 0L;
                if (markKey) {
                    key = CompilerDirectives.mergeExplodeKey(key);
                }
                while (true) {
                    CompilerAsserts.partialEvaluationConstant(key);
                    if (key != 0L) {
                        if (irreducible ? exitIrreducibleLoop(control) : key == thirdKey) {
                            break;
                        }
                    }
                    key = nextLongKey(key, entry, thirdKey);
                }
                return key;
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static long executeNested(int entry, LoopControl control, boolean irreducible, boolean markKey, long thirdKey) {
                NestedLongVirtualState state = new NestedLongVirtualState(new LongVirtualState(0L));
                LongVirtualState alias = state.inner;
                if (markKey) {
                    state.inner.key = CompilerDirectives.mergeExplodeKey(state.inner.key);
                }
                while (true) {
                    CompilerAsserts.partialEvaluationConstant(state.inner.key);
                    if (state.inner.key != 0L) {
                        if (irreducible ? exitIrreducibleLoop(control) : state.inner.key == thirdKey) {
                            break;
                        }
                    }
                    alias.key = nextLongKey(state.inner.key, entry, thirdKey);
                }
                return alias.key;
            }
        };
    }

    @EarlyInline
    private static long nextLongKey(long key, int entry, long thirdKey) {
        if (key == 0L) {
            opaqueAdd(0, entry);
            return -0x8000_0000L;
        } else if (key == -0x8000_0000L) {
            /* Residual calls keep the two entries from folding to a non-constant key. */
            if (entry == 0) {
                opaqueAdd(1, 1);
                return 0x7fff_ffffL;
            }
            opaqueAdd(1, 2);
            return thirdKey;
        } else if (key == 0x7fff_ffffL) {
            return thirdKey;
        } else if (key == thirdKey) {
            /* Backedges to the inner and outer cycles exercise dispatcher reconstruction. */
            if (entry == 2) {
                opaqueAdd(2, 1);
                return 0x7fff_ffffL;
            }
            opaqueAdd(2, 2);
            return -0x8000_0000L;
        } else {
            throw shouldNotReachHere();
        }
    }

    @Test
    public void nonConstantLongKeyFails() {
        RootNode root = new RootNode(null) {
            @Override
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            public Object execute(VirtualFrame frame) {
                long key = CompilerDirectives.mergeExplodeKey(-0x8000_0000L);
                while (key != 0x7fff_ffffL) {
                    key = opaqueLong(0x7fff_ffffL);
                }
                return key;
            }
        };
        Assert.assertEquals(0x7fff_ffffL, root.getCallTarget().call());
        try {
            partialEval(root);
            Assert.fail("Expected a bailout for a non-constant long merge key");
        } catch (BailoutException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("merge keys must partial evaluate to int or long constants"));
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static long opaqueLong(long value) {
        return value;
    }

    @Test
    public void equalInputsDoNotMergeKeyMarkers() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        ConstantNode value = ConstantNode.forInt(0, graph);
        /* Exercise normal graph uniquing, not the decoder's addWithoutUnique fast path. */
        LoopExplosionKeyNode first = graph.addOrUnique(new LoopExplosionKeyNode(value));
        LoopExplosionKeyNode second = graph.addOrUnique(new LoopExplosionKeyNode(value));
        Assert.assertNotSame("Distinct key markers must not be value-numbered", first, second);
        Assert.assertFalse(first.valueEquals(second));
        Assert.assertNull(graph.findDuplicate(first));
    }

    @Test
    public void sameInitialKeyInRepeatedInlinings() {
        RootNode root = repeatedInliningKeyProgram();
        Assert.assertEquals(4, root.getCallTarget().call());
        OptimizedCallTarget target = compileHelper("sameInitialKeyInRepeatedInlinings", root, new Object[0]);
        Assert.assertEquals(4, target.call());
    }

    private static RootNode repeatedInliningKeyProgram() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                /* Separate method scopes share the same graph and initial-key constant. */
                return dispatch() + dispatch();
            }

            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int dispatch() {
                int key = 0;
                key = CompilerDirectives.mergeExplodeKey(key);
                while (key < 2) {
                    CompilerAsserts.partialEvaluationConstant(key);
                    key++;
                }
                return key;
            }
        };
    }

    @Test
    public void scalarKeyInIrreducibleLoop() {
        byte[] bytecodes = new byte[]{
                        /* 0: */Bytecode.ARGUMENT,
                        /* 1: */0,
                        /* 2: */Bytecode.IFZERO,
                        /* 3: */6,
                        /* 4: */Bytecode.JMP,
                        /* 5: */12,
                        /* 6: */Bytecode.ARGUMENT,
                        /* 7: */1,
                        /* 8: */Bytecode.IFZERO,
                        /* 9: */18,
                        /* 10: */Bytecode.JMP,
                        /* 11: */12,
                        /* 12: */Bytecode.ARGUMENT,
                        /* 13: */1,
                        /* 14: */Bytecode.IFZERO,
                        /* 15: */18,
                        /* 16: */Bytecode.JMP,
                        /* 17: */6,
                        /* 18: */Bytecode.CONST,
                        /* 19: */42,
                        /* 20: */Bytecode.RETURN};
        RootNode root = Program.create("scalarKeyInIrreducibleLoop", bytecodes, 1, false);
        Assert.assertEquals(42, root.getCallTarget().call(0, 0));
        Assert.assertEquals(42, root.getCallTarget().call(1, 0));
        OptimizedCallTarget target = compileHelper("scalarKeyInIrreducibleLoop", root, new Object[]{0, 0});
        Assert.assertEquals(42, target.call(0, 0));
        Assert.assertEquals(42, target.call(1, 0));
        Assert.assertTrue(target.isValid());
    }

    @Test
    public void nestedFieldKeyInIrreducibleLoop() {
        RootNode root = irreducibleNestedFieldKeyProgram();
        /* Exercise both entries and both backedges before compilation. */
        Assert.assertEquals(1, root.getCallTarget().call(true, 2));
        Assert.assertEquals(2, root.getCallTarget().call(false, 2));
        OptimizedCallTarget target = compileHelper("nestedFieldKeyInIrreducibleLoop", root, new Object[]{true, 2});
        for (boolean enterAtOne : new boolean[]{true, false}) {
            for (int transitions = 0; transitions <= 4; transitions++) {
                int initialKey = enterAtOne ? 1 : 2;
                int expected = transitions % 2 == 0 ? initialKey : 3 - initialKey;
                Assert.assertEquals(expected, target.call(enterAtOne, transitions));
                Assert.assertTrue("Calls through either entry must keep the compiled target valid", target.isValid());
            }
        }
    }

    private static RootNode irreducibleNestedFieldKeyProgram() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                LoopControl control = new LoopControl((boolean) frame.getArguments()[0], (int) frame.getArguments()[1]);
                return executeWithIrreducibleKey(control);
            }

            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithIrreducibleKey(LoopControl control) {
                NestedVirtualState state = new NestedVirtualState(new VirtualState(0, 0));
                state.inner.key = CompilerDirectives.mergeExplodeKey(state.inner.key);

                while (true) {
                    switch (state.inner.key) {
                        case 0:
                            /* Residual calls prevent the two entries from folding to a select. */
                            if (enterAtOne(control)) {
                                state.inner.key = 1;
                                opaqueAdd(0, 1);
                                continue;
                            }
                            state.inner.key = 2;
                            opaqueAdd(0, 2);
                            continue;
                        case 1:
                            if (exitIrreducibleLoop(control)) {
                                return 1;
                            }
                            state.inner.key = 2;
                            continue;
                        case 2:
                            if (exitIrreducibleLoop(control)) {
                                return 2;
                            }
                            state.inner.key = 1;
                            continue;
                        default:
                            throw shouldNotReachHere();
                    }
                }
            }
        };
    }

    @CompilerDirectives.TruffleBoundary
    private static boolean enterAtOne(LoopControl control) {
        return control.enterAtOne;
    }

    @Test
    public void repeatedEarlyInlinedUnmarkedInnerLoops() {
        for (boolean explicitOuterKey : new boolean[]{false, true}) {
            RootNode root = earlyInlinedUnmarkedInnerLoopProgram(explicitOuterKey);
            Assert.assertEquals(18, root.getCallTarget().call(4));
            OptimizedCallTarget target = compileHelper("repeatedEarlyInlinedUnmarkedInnerLoops", root, new Object[]{4});
            for (int input : new int[]{0, 4, 7}) {
                Assert.assertEquals(3 * (input + 2), target.call(input));
                Assert.assertTrue(target.isValid());
            }
        }
    }

    private static RootNode earlyInlinedUnmarkedInnerLoopProgram(boolean explicitOuterKey) {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return dispatch((int) frame.getArguments()[0], explicitOuterKey);
            }

            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int dispatch(int input, boolean explicitKey) {
                int outer = 0;
                if (explicitKey) {
                    outer = CompilerDirectives.mergeExplodeKey(outer);
                }
                int result = 0;
                while (outer < 3) {
                    result += innerLoop(input);
                    outer++;
                }
                return result;
            }

            @EarlyInline
            private static int innerLoop(int input) {
                int inner = 0;
                while (inner < 2) {
                    inner++;
                }
                return input + inner;
            }
        };
    }

    @Test
    public void repeatedInnerLoopKeys() {
        int iterations = 128;
        RootNode root = repeatedInnerLoopKeyProgram(iterations);
        int expected = iterations * (iterations + 1) / 2;
        Assert.assertEquals(expected, root.getCallTarget().call());
        OptimizedCallTarget target = compileHelper("repeatedInnerLoopKeys", root, new Object[0]);
        Assert.assertEquals(expected, target.call());
    }

    private static RootNode repeatedInnerLoopKeyProgram(int iterations) {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithInnerKeys(iterations);
            }

            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithInnerKeys(int count) {
                CompilerAsserts.partialEvaluationConstant(count);
                int result = 0;
                for (int outer = 1; outer <= count; outer++) {
                    int key = outer;
                    key = CompilerDirectives.mergeExplodeKey(key);
                    while (true) {
                        CompilerAsserts.partialEvaluationConstant(key);
                        result = opaqueAdd(result, key);
                        if (key == 0) {
                            break;
                        }
                        /* All inner-loop instances revisit zero with a different filter. */
                        key = 0;
                    }
                }
                return result;
            }
        };
    }

    @Test
    public void mergeStatesAreLocalToLoopEntries() {
        for (boolean explicitKey : new boolean[]{false, true}) {
            createMergeKeyDecoder().assertEntryLocalMergeStates(explicitKey);
        }
    }

    @Test
    public void markerReplacementStartsAtRecognitionAndSurvivesDeletion() {
        createMergeKeyDecoder().assertMarkerReplacementLifecycle();
    }

    @Test
    public void markerPathBacktracksAcrossRootsAndCycles() {
        createMergeKeyDecoder().assertMarkerPathBacktracking(getMetaAccess().lookupJavaType(Object.class), getMetaAccess().lookupJavaType(int.class));
    }

    private MergeKeyDecoder createMergeKeyDecoder() {
        StructuredGraph sourceGraph = parseEager("opaqueAdd", StructuredGraph.AllowAssumptions.YES);
        EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(sourceGraph, getTarget().arch);
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).method(sourceGraph.method()).build();
        return new MergeKeyDecoder(getTarget().arch, graph, encodedGraph);
    }

    @Test
    public void multipleTopLevelLoops() {
        try {
            partialEval(multipleTopLevelLoopsProgram());
            Assert.fail("Expected a bailout for multiple top-level loops");
        } catch (BailoutException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("must not have more than one top-level loop"));
        }
    }

    private static RootNode multipleTopLevelLoopsProgram() {
        return new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return executeWithMultipleTopLevelLoops();
            }

            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            private static int executeWithMultipleTopLevelLoops() {
                int bci = 0;
                bci = CompilerDirectives.mergeExplodeKey(bci);
                while (true) {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    if (bci == 0) {
                        bci = 1;
                        continue;
                    }
                    if (shouldExitLoop()) {
                        break;
                    }
                }
                bci = 0;
                while (true) {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    if (bci == 0) {
                        bci = 1;
                        continue;
                    }
                    if (shouldExitLoop()) {
                        break;
                    }
                }
                return bci;
            }
        };
    }

    @Test
    public void deoptimizeWithPublishedObjects() {
        // Deoptimize at each noninitial dispatcher entry and check the published identity before
        // another interpreter transition can overwrite it.
        for (boolean mark : new boolean[]{false, true}) {
            int tested = 0;
            for (int keyIndex = 1;; keyIndex++) {
                RootNode root = publishedObjectProgram(mark);
                StructuredGraph graph = partialEval(root, new Object[5], new int[]{0, 5, 1});
                IntegerSwitchNode dispatcher = null;
                for (IntegerSwitchNode candidate : graph.getNodes().filter(IntegerSwitchNode.class)) {
                    if (candidate.value() instanceof ValuePhiNode phi && phi.merge() instanceof LoopBeginNode) {
                        Assert.assertNull(dispatcher);
                        dispatcher = candidate;
                    }
                }
                Assert.assertNotNull(dispatcher);
                if (keyIndex == dispatcher.keyCount()) {
                    break;
                }
                FrameState state = ((ValuePhiNode) dispatcher.value()).merge().stateAfter();
                Assert.assertTrue(state.virtualObjectMappings().filter(MaterializedObjectState.class).isNotEmpty());
                int id = dispatcher.keyAt(keyIndex).asInt();
                FixedGuardNode guard = graph.add(new FixedGuardNode(graph.addOrUnique(new IntegerEqualsNode(dispatcher.value(), ConstantNode.forInt(id, graph))),
                                DeoptimizationReason.TransferToInterpreter, DeoptimizationAction.None, true));
                guard.setStateBefore(state);
                graph.addBeforeFixed(dispatcher, guard);
                OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
                compile(target, graph);
                int deopts = 0;
                for (int entry = 0; entry < 2; entry++) {
                    for (int branch = 0; branch < 2; branch++) {
                        Object[] published = new Object[5];
                        int[] control = {entry, 10, branch};
                        int resumed = (int) target.call(published, control);
                        if (resumed != 0) {
                            int expected = 0;
                            for (int remaining = 10; remaining > control[1]; remaining--) {
                                expected = switch (expected) {
                                    case 0, 3 -> entry == 0 ? 1 : 2;
                                    case 1 -> branch == 0 ? 2 : 3;
                                    case 2 -> 3;
                                    default -> throw new AssertionError(expected);
                                };
                            }
                            Assert.assertNotEquals("Expected to leave compiled code at a noninitial entry", 0, expected);
                            Assert.assertSame(published[expected], published[4]);
                            deopts++;
                        }
                        Assert.assertTrue(target.isValid());
                    }
                }
                Assert.assertTrue("Expected deoptimization for dispatcher ID " + id, deopts > 0);
                tested++;
            }
            Assert.assertTrue(tested > 0);
        }
    }

    private static RootNode publishedObjectProgram(boolean mark) {
        return new RootNode(null) {
            @Override
            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            public Object execute(VirtualFrame frame) {
                Object[] published = (Object[]) frame.getArguments()[0];
                int[] control = (int[]) frame.getArguments()[1];
                int key = 0;
                // Publishing forces materialization; keep the same objects as identity witnesses.
                Object zero = new Object();
                Object one = new Object();
                Object two = new Object();
                Object three = new Object();
                published[0] = zero;
                published[1] = one;
                published[2] = two;
                published[3] = three;
                Object value = zero;
                if (mark) {
                    key = CompilerDirectives.mergeExplodeKey(key);
                }
                while (true) {
                    CompilerAsserts.partialEvaluationConstant(key);
                    if (!CompilerDirectives.inCompiledCode()) {
                        published[4] = value;
                        return 1;
                    }
                    if (--control[1] == 0) {
                        return 0;
                    }
                    if (key == 0 || key == 3) {
                        if (control[0] == 0) {
                            opaqueAdd(0, 1);
                            key = 1;
                            value = one;
                            continue;
                        } else {
                            opaqueAdd(0, 2);
                            key = 2;
                            value = two;
                            continue;
                        }
                    } else if (key == 1 && control[2] == 0) {
                        opaqueAdd(1, 2);
                        key = 2;
                        value = two;
                        continue;
                    } else {
                        opaqueAdd(2, 3);
                        key = 3;
                        value = three;
                        continue;
                    }
                }
            }
        };
    }

    /**
     * GR-81497: distinct interpreter BCIs can have different, individually constant stack
     * pointers. Two entries to the 1 <-> 2 cycle require an irreducible-loop dispatcher, whose
     * non-key state must not be required to match across those different BCIs.
     */
    @Test
    public void irreducibleLoopWithVirtualStackPointer() {
        RootNode root = virtualStackPointerProgram();
        assertVirtualStackPointerResults(root.getCallTarget());
        OptimizedCallTarget target = compileHelper("irreducibleLoopWithVirtualStackPointer", root, new Object[]{true, 2});
        assertVirtualStackPointerResults(target);
        Assert.assertTrue("Both entries must execute without interpreter fallback", target.isValid());
    }

    private static void assertVirtualStackPointerResults(CallTarget target) {
        for (boolean enterAtOne : new boolean[]{true, false}) {
            for (int transitions = 0; transitions <= 4; transitions++) {
                int initialStackPointer = enterAtOne ? 65 : 66;
                int expected = transitions % 2 == 0 ? initialStackPointer : 131 - initialStackPointer;
                Assert.assertEquals(expected, target.call(enterAtOne, transitions));
            }
        }
    }

    static final class VirtualStackPointer {
        int stackPointer;

        @EarlyInline
        VirtualStackPointer(int stackPointer) {
            this.stackPointer = stackPointer;
        }
    }

    private static RootNode virtualStackPointerProgram() {
        return new RootNode(null) {
            @Override
            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            public Object execute(VirtualFrame frame) {
                LoopControl control = new LoopControl((boolean) frame.getArguments()[0], (int) frame.getArguments()[1]);
                VirtualStackPointer state = new VirtualStackPointer(66);
                CompilerDirectives.ensureVirtualized(state);
                int bci = CompilerDirectives.mergeExplodeKey(0);
                while (true) {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    CompilerAsserts.partialEvaluationConstant(state.stackPointer);
                    switch (bci) {
                        case 0:
                            if (enterAtOne(control)) {
                                bci = 1;
                                state.stackPointer = 65;
                                // Keep the entries distinct instead of folding to a select.
                                opaqueAdd(0, 1);
                                continue;
                            }
                            bci = 2;
                            state.stackPointer = 66;
                            opaqueAdd(0, 2);
                            continue;
                        case 1:
                            if (exitIrreducibleLoop(control)) {
                                return state.stackPointer;
                            }
                            bci = 2;
                            state.stackPointer = 66;
                            continue;
                        case 2:
                            if (exitIrreducibleLoop(control)) {
                                return state.stackPointer;
                            }
                            bci = 1;
                            state.stackPointer = 65;
                            continue;
                        default:
                            throw shouldNotReachHere();
                    }
                }
            }
        };
    }

    /**
     * Deoptimize before the dispatch switch and immediately return the restored auxiliary state.
     * Normal frame-state assignment supplies the guard's state; the test does not construct it.
     */
    @Test
    public void irreducibleLoopDeoptimizationNeedsAuxiliaryPhis() {
        assertIrreducibleLoopDeoptimizationState(true, true);
    }

    @Test
    public void irreducibleLoopDeoptimizationNeedsVirtualPhis() {
        assertIrreducibleLoopDeoptimizationState(true, false);
    }

    @Test
    public void unmarkedIrreducibleLoopDeoptimizationNeedsVirtualPhis() {
        assertIrreducibleLoopDeoptimizationState(false, false);
    }

    @Test
    public void unmarkedIrreducibleLoopDeoptimizationNeedsAuxiliaryPhis() {
        assertIrreducibleLoopDeoptimizationState(false, true);
    }

    private void assertIrreducibleLoopDeoptimizationState(boolean markKey, boolean scalarAuxiliary) {
        RootNode root = irreducibleNonKeyStateProgram(false, false, markKey, scalarAuxiliary);
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        StructuredGraph graph = partialEval(root, 0, 2);
        IntegerSwitchNode dispatcher = null;
        LoopBeginNode header = null;
        for (IntegerSwitchNode candidate : graph.getNodes().filter(IntegerSwitchNode.class)) {
            ValueNode keyValue = candidate.value();
            if (keyValue instanceof NarrowNode narrow) {
                keyValue = narrow.getValue();
            }
            if (keyValue instanceof ValuePhiNode phi && phi.merge() instanceof LoopBeginNode loopBegin) {
                Assert.assertNull("Expected one irreducible-loop dispatcher", dispatcher);
                dispatcher = candidate;
                header = loopBegin;
            }
        }
        Assert.assertNotNull("Missing irreducible-loop dispatcher", dispatcher);
        Assert.assertSame("Deoptimize immediately after the synthetic header", header, dispatcher.predecessor());
        // Do not let switch/guard optimization move deoptimization into a specialized entry,
        // where constants in that entry's state could hide a broken shared header state.
        ValueNode key = dispatcher.value();
        if (!markKey) {
            // Synthetic selector IDs do not identify interpreter BCIs. Find the actual long key
            // phi by its unique input 3 (the auxiliary long has input 20 at that entry).
            key = null;
            for (ValueNode value : header.stateAfter().values()) {
                if (value instanceof ValuePhiNode phi && phi.getStackKind() == JavaKind.Long) {
                    for (ValueNode input : phi.values()) {
                        if (input.isJavaConstant() && input.asJavaConstant().asLong() == 3) {
                            Assert.assertTrue(key == null || key == phi);
                            key = phi;
                            break;
                        }
                    }
                }
            }
            Assert.assertNotNull("Missing interpreter key phi", key);
            Assert.assertNotSame("The selector must be separate from the interpreter state", dispatcher.value(), key);
        }
        ValueNode opaqueKey = graph.addWithoutUnique(new OpaqueValueNode(key));
        FixedGuardNode guard = graph.add(new FixedGuardNode(
                        graph.addOrUnique(new IntegerEqualsNode(opaqueKey, ConstantNode.forIntegerKind(key.getStackKind(), 3, graph))),
                        DeoptimizationReason.UnreachedCode, DeoptimizationAction.InvalidateRecompile, true));
        graph.addBeforeFixed(dispatcher, guard);
        compile(target, graph);
        Assert.assertTrue(target.isValid());
        // Reach key 3 with no transitions remaining and return before overwriting the restored
        // values: 100 * key + auxiliary + state.value = 300 + (scalarAuxiliary ? 20 : 0) + 34.
        // Both marked and synthetic-ID dispatchers must preserve scalar and virtual fields
        // independently of the selector used for dispatch.
        Object result = target.call(0, 2);
        Assert.assertFalse("Expected deoptimization at the dispatcher", target.isValid());
        Assert.assertEquals(300L + (scalarAuxiliary ? 20 : 0) + 34, result);
    }

    @Test
    public void irreducibleLoopPreservesNonKeyState() {
        assertIrreducibleNonKeyState(false);
    }

    @Test
    public void irreducibleLoopPreservesNonKeyStateWithNestedKey() {
        assertIrreducibleNonKeyState(true);
    }

    @Test
    public void unmarkedIrreducibleLoopPreservesScalarAndVirtualState() {
        RootNode root = irreducibleNonKeyStateProgram(false, false, false, true);
        assertIrreducibleNonKeyStateResults(root.getCallTarget());
        OptimizedCallTarget target = compileHelper("unmarkedIrreducibleLoopPreservesScalarAndVirtualState", root, new Object[]{0, 0});
        assertIrreducibleNonKeyStateResults(target);
        Assert.assertTrue(target.isValid());
    }

    private void assertIrreducibleNonKeyState(boolean nestedKey) {
        RootNode root = irreducibleNonKeyStateProgram(nestedKey);
        assertIrreducibleNonKeyStateResults(root.getCallTarget());
        OptimizedCallTarget target = compileHelper("irreducibleNonKeyState", root, new Object[]{0, 0});
        assertIrreducibleNonKeyStateResults(target);
        Assert.assertTrue(target.isValid());

        // Deoptimize at the synthetic dispatcher, not in an entry's specialized body. Resume
        // with each of its noninitial keys to check both scalar and virtual-object phi state.
        int deoptimizedEntries = 0;
        for (int keyIndex = 0;; keyIndex++) {
            RootNode deoptRoot = irreducibleNonKeyStateProgram(nestedKey, true);
            OptimizedCallTarget deoptTarget = (OptimizedCallTarget) deoptRoot.getCallTarget();
            StructuredGraph graph = partialEval(deoptRoot, 0, 0);
            IntegerSwitchNode dispatcher = null;
            LoopBeginNode header = null;
            for (IntegerSwitchNode candidate : graph.getNodes().filter(IntegerSwitchNode.class)) {
                ValueNode keyValue = candidate.value();
                if (keyValue instanceof NarrowNode narrow) {
                    keyValue = narrow.getValue();
                }
                if (keyValue instanceof ValuePhiNode phi && phi.merge() instanceof LoopBeginNode loopBegin) {
                    Assert.assertNull("Expected one irreducible-loop dispatcher", dispatcher);
                    dispatcher = candidate;
                    header = loopBegin;
                }
            }
            Assert.assertNotNull("Missing irreducible-loop dispatcher", dispatcher);
            if (keyIndex == dispatcher.keyCount()) {
                break;
            }
            int key = dispatcher.keyAt(keyIndex).asInt();
            if (key == 0) {
                continue;
            }
            Assert.assertNotNull(header);
            Assert.assertTrue("Dispatcher must preserve non-key state", header.phis().count() > 1);
            FixedGuardNode guard = graph.add(new FixedGuardNode(
                            graph.addOrUnique(new IntegerEqualsNode(dispatcher.value(), ConstantNode.forInt(key, graph))),
                            DeoptimizationReason.UnreachedCode, DeoptimizationAction.None, true));
            guard.setStateBefore(header.stateAfter());
            graph.addBeforeFixed(dispatcher, guard);
            compile(deoptTarget, graph);
            Assert.assertTrue(deoptTarget.isValid());
            // Keep the code installed and try every exit position. In particular, exiting
            // immediately after deoptimization must read the restored auxiliary values before
            // another transition can overwrite them. Long.MIN_VALUE identifies calls that
            // return in compiled code without visiting the guarded dispatcher entry.
            Assert.assertTrue("Expected deoptimization at key " + key, assertIrreducibleNonKeyStateResults(deoptTarget, true) > 0);
            Assert.assertTrue(deoptTarget.isValid());
            deoptimizedEntries++;
        }
        Assert.assertTrue("Expected noninitial dispatcher entries", deoptimizedEntries > 0);
    }

    private static void assertIrreducibleNonKeyStateResults(CallTarget target) {
        assertIrreducibleNonKeyStateResults(target, false);
    }

    private static int assertIrreducibleNonKeyStateResults(CallTarget target, boolean deoptimizationTest) {
        int deoptimizations = 0;
        int[][] keys = {
                        {Integer.MIN_VALUE, Integer.MAX_VALUE, 3, Integer.MIN_VALUE, Integer.MAX_VALUE, 3},
                        {Integer.MIN_VALUE, 3, Integer.MIN_VALUE, 3, Integer.MIN_VALUE, 3},
                        {Integer.MIN_VALUE, 3, Integer.MAX_VALUE, 3, Integer.MAX_VALUE, 3}};
        for (int entry = 0; entry < keys.length; entry++) {
            for (int transitions = 0; transitions < keys[entry].length; transitions++) {
                Object result = target.call(entry, transitions);
                if (deoptimizationTest && result.equals(Long.MIN_VALUE)) {
                    continue;
                }
                Assert.assertEquals(nonKeyStateResult(keys[entry][transitions]), result);
                deoptimizations++;
            }
        }
        return deoptimizations;
    }

    private static long nonKeyStateResult(int key) {
        return 100L * key + ((long) key + 17) + (key + 31);
    }

    private static RootNode irreducibleNonKeyStateProgram(boolean nestedKey) {
        return irreducibleNonKeyStateProgram(nestedKey, false);
    }

    private static RootNode irreducibleNonKeyStateProgram(boolean nestedKey, boolean reportCompiled) {
        return irreducibleNonKeyStateProgram(nestedKey, reportCompiled, true, true);
    }

    private static RootNode irreducibleNonKeyStateProgram(boolean nestedKey, boolean reportCompiled, boolean markKey, boolean scalarAuxiliary) {
        return new RootNode(null) {
            @Override
            @EarlyEscapeAnalysis
            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            public Object execute(VirtualFrame frame) {
                int entry = (int) frame.getArguments()[0];
                LoopControl control = new LoopControl(false, (int) frame.getArguments()[1]);
                VirtualState state = new VirtualState(0, 0);
                CompilerDirectives.ensureVirtualized(state);
                long key = 0;
                long auxiliary = 0;
                if (markKey) {
                    if (nestedKey) {
                        state.key = CompilerDirectives.mergeExplodeKey(state.key);
                    } else {
                        key = CompilerDirectives.mergeExplodeKey(key);
                    }
                }
                while (true) {
                    int currentKey = nestedKey ? state.key : (int) key;
                    CompilerAsserts.partialEvaluationConstant(currentKey);
                    if (currentKey != 0 && exitIrreducibleLoop(control)) {
                        if (reportCompiled && CompilerDirectives.inCompiledCode()) {
                            return Long.MIN_VALUE;
                        }
                        return 100L * currentKey + auxiliary + state.value;
                    }
                    switch (currentKey) {
                        case 0:
                            opaqueAdd(0, entry);
                            key = Integer.MIN_VALUE;
                            state.key = Integer.MIN_VALUE;
                            auxiliary = scalarAuxiliary ? (long) Integer.MIN_VALUE + 17 : 0;
                            state.value = Integer.MIN_VALUE + 31;
                            continue;
                        case Integer.MIN_VALUE:
                            if (entry == 0) {
                                opaqueAdd(1, 1);
                                key = Integer.MAX_VALUE;
                                state.key = Integer.MAX_VALUE;
                                auxiliary = scalarAuxiliary ? (long) Integer.MAX_VALUE + 17 : 0;
                                state.value = Integer.MAX_VALUE + 31;
                                continue;
                            }
                            opaqueAdd(1, 2);
                            key = 3;
                            state.key = 3;
                            auxiliary = scalarAuxiliary ? 20 : 0;
                            state.value = 34;
                            continue;
                        case Integer.MAX_VALUE:
                            key = 3;
                            state.key = 3;
                            auxiliary = scalarAuxiliary ? 20 : 0;
                            state.value = 34;
                            continue;
                        case 3:
                            if (entry == 2) {
                                opaqueAdd(2, 1);
                                key = Integer.MAX_VALUE;
                                state.key = Integer.MAX_VALUE;
                                auxiliary = scalarAuxiliary ? (long) Integer.MAX_VALUE + 17 : 0;
                                state.value = Integer.MAX_VALUE + 31;
                                continue;
                            }
                            opaqueAdd(2, 2);
                            key = Integer.MIN_VALUE;
                            state.key = Integer.MIN_VALUE;
                            auxiliary = scalarAuxiliary ? (long) Integer.MIN_VALUE + 17 : 0;
                            state.value = Integer.MIN_VALUE + 31;
                            continue;
                        default:
                            throw shouldNotReachHere();
                    }
                }
            }
        };
    }

    @CompilerDirectives.TruffleBoundary
    private static boolean shouldExitLoop() {
        return true;
    }

    @CompilerDirectives.TruffleBoundary
    private static boolean exitIrreducibleLoop(LoopControl control) {
        return control.remainingTransitions-- == 0;
    }

    @CompilerDirectives.TruffleBoundary
    private static int opaqueAdd(int left, int right) {
        return left + right;
    }

    /** Exercises marker tracking and key hashing without large exploded graphs. */
    private static final class MergeKeyDecoder extends GraphDecoder {
        private final MethodScope methodScope;

        MergeKeyDecoder(Architecture architecture, StructuredGraph graph, EncodedGraph encodedGraph) {
            super(architecture, graph);
            methodScope = new MethodScope(null, graph, encodedGraph, LoopExplosionPlugin.LoopExplosionKind.MERGE_EXPLODE) {
            };
        }

        void assertMarkerReplacementLifecycle() {
            /* Exercise both the linear and hashed representations of the replacement map. */
            for (int i = 0; i < 16; i++) {
                LoopScope loopScope = createInitialLoopScope(methodScope, null);
                ConstantNode value = ConstantNode.forInt(i, graph);
                LoopExplosionKeyNode marker = graph.addWithoutUnique(new LoopExplosionKeyNode(value));
                int orderId = GraphEncoder.FIRST_NODE_ORDER_ID;
                registerNode(loopScope, orderId, marker, false, false);
                FrameState state = graph.add(new FrameState(null, null, 0, List.of(marker), 1, 0, 0, FrameState.StackState.BeforePop, false, null, null, null));

                Assert.assertFalse(methodScope.loopExplosionKeyReplacements.containsKey(marker));
                Assert.assertSame("Unrecognized markers must remain visible", marker, loopScope.getNode(orderId));
                Assert.assertNotNull(computeMergeKeyFilter(loopScope, state));
                Assert.assertSame(value, methodScope.loopExplosionKeyReplacements.get(marker));
                Assert.assertSame(value, loopScope.getNode(orderId));

                marker.safeDelete();
                Assert.assertNull(marker.value());
                Assert.assertSame("Stale references must use the cached value after deletion", value, loopScope.getNode(orderId));
            }
        }

        void assertMarkerPathBacktracking(ResolvedJavaType objectType, ResolvedJavaType intType) {
            LoopScope loopScope = createInitialLoopScope(methodScope, null);
            ConstantNode value = ConstantNode.forInt(0, graph);
            LoopExplosionKeyNode marker = graph.addWithoutUnique(new LoopExplosionKeyNode(value));
            VirtualArrayNode emptyRoot = graph.addWithoutUnique(new VirtualArrayNode(objectType, 0));
            VirtualArrayNode root = graph.addWithoutUnique(new VirtualArrayNode(objectType, 3));
            VirtualArrayNode unmarked = graph.addWithoutUnique(new VirtualArrayNode(objectType, 1));
            VirtualArrayNode leaf = graph.addWithoutUnique(new VirtualArrayNode(intType, 1));
            VirtualObjectState emptyState = graph.addWithoutUnique(new VirtualObjectState(emptyRoot, List.of()));
            VirtualObjectState rootState = graph.addWithoutUnique(new VirtualObjectState(root, List.of(unmarked, unmarked, leaf)));
            VirtualObjectState unmarkedState = graph.addWithoutUnique(new VirtualObjectState(unmarked, List.of(root)));
            VirtualObjectState leafState = graph.addWithoutUnique(new VirtualObjectState(leaf, List.of(marker)));
            FrameState state = graph.add(new FrameState(null, null, 0, List.of(emptyRoot, root), 2, 0, 0, FrameState.StackState.BeforePop, false, null,
                            List.of(emptyState, rootState, unmarkedState, leafState), null));

            /*
             * Backtrack from an empty root, a cyclic child, and its alias before finding the key.
             */
            loopScope.loopEntryState.loopExplosionMergeKeyFilter = computeMergeKeyFilter(loopScope, state);
            Assert.assertNotNull(loopScope.loopEntryState.loopExplosionMergeKeyFilter);
            Assert.assertSame(value, createLoopExplosionKey(loopScope, state).values.getFirst());

            /* Reuse the recorded path with the next iteration's virtual state. */
            ConstantNode nextValue = ConstantNode.forInt(1, graph);
            FrameState nextState = state.duplicate();
            nextState.virtualObjectMappings().set(3, graph.addWithoutUnique(new VirtualObjectState(leaf, List.of(nextValue))));
            Assert.assertSame(nextValue, createLoopExplosionKey(loopScope, nextState).values.getFirst());
            Assert.assertSame(value, createLoopExplosionKey(loopScope, state).values.getFirst());
        }

        void assertEntryLocalMergeStates(boolean explicitKey) {
            LoopScope[] entries = new LoopScope[16];
            int keysPerEntry = 16;
            for (int entry = 0; entry < entries.length; entry++) {
                LoopScope loopScope = createInitialLoopScope(methodScope, null);
                entries[entry] = loopScope;
                var states = loopScope.loopEntryState.iterationStates;
                Assert.assertTrue("A new loop entry must have its own empty merge-state map", states.isEmpty());
                ValueNode value = ConstantNode.forInt(0, graph);
                if (explicitKey) {
                    value = graph.addWithoutUnique(new LoopExplosionKeyNode(value));
                }
                FrameState initialState = graph.add(new FrameState(null, null, 0, List.of(value), 1, 0, 0, FrameState.StackState.BeforePop, false, null, null, null));
                loopScope.loopEntryState.loopExplosionMergeKeyFilter = computeMergeKeyFilter(loopScope, initialState);
                /* Exercise both the linear and hashed representations of each entry's map. */
                for (int i = 0; i < keysPerEntry; i++) {
                    FrameState state = graph.add(new FrameState(null, null, 0, List.of(ConstantNode.forInt(i, graph)), 1, 0, 0, FrameState.StackState.BeforePop, false, null, null, null));
                    LoopExplosionKey key = createLoopExplosionKey(loopScope, state);
                    Assert.assertFalse(states.containsKey(key));
                    states.put(key, null);
                    LoopExplosionKey equivalentKey = createLoopExplosionKey(loopScope, state.duplicate());
                    Assert.assertEquals("Equal states in one loop entry must still match", key, equivalentKey);
                    Assert.assertEquals(key.hashCode(), equivalentKey.hashCode());
                    Assert.assertTrue(states.containsKey(equivalentKey));
                    Assert.assertEquals(i + 1, states.size());
                }
            }
            for (LoopScope loopScope : entries) {
                var states = loopScope.loopEntryState.iterationStates;
                Assert.assertEquals("Later entries must not change an earlier entry's map", keysPerEntry, states.size());
                for (int i = 0; i < keysPerEntry; i++) {
                    FrameState state = graph.add(new FrameState(null, null, 0, List.of(ConstantNode.forInt(i, graph)), 1, 0, 0, FrameState.StackState.BeforePop, false, null, null, null));
                    Assert.assertTrue(states.containsKey(createLoopExplosionKey(loopScope, state)));
                }
            }
        }
    }
}
