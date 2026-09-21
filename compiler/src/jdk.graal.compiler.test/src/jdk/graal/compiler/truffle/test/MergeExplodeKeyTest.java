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

import org.junit.Assert;
import org.junit.Test;

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
import jdk.graal.compiler.nodes.LoopExplosionKeyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.code.BailoutException;

/**
 * Tests the {@link CompilerDirectives#mergeExplodeKey(int)} system for explicit merge explode keys.
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
                        throw new IllegalStateException();
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
                            throw new IllegalStateException();
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
                            throw new IllegalStateException();
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
                            throw new IllegalStateException();
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
                            throw new IllegalStateException();
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
}
