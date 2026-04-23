/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest.createNodes;
import static com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest.parseNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.graalvm.polyglot.Context;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest.TestRun;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterBuilder;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterBuilder.BytecodeVariant;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.runtime.OptimizedCallTarget;

@RunWith(Parameterized.class)
public class BytecodeDSLCompilationTest extends TestWithSynchronousCompiling {

    @Parameters(name = "{0}")
    public static List<TestRun> getParameters() {
        List<TestRun> result = new ArrayList<>();
        for (BytecodeVariant bc : AbstractBasicInterpreterTest.allVariants()) {
            result.add(new TestRun(bc, false, false));
        }
        return result;
    }

    @Parameter(0) public TestRun run;

    private boolean hasBoxingElimination() {
        return run.hasBoxingElimination();
    }

    Context context;
    Instrumenter instrumenter;

    @Before
    @Override
    public void before() {
        context = setupContext();
        context.initialize(BytecodeDSLTestLanguage.ID);
        instrumenter = context.getEngine().getInstruments().get(BytecodeDSLCompilationTestInstrumentation.ID).lookup(Instrumenter.class);
    }

    @BeforeClass
    public static void beforeClass() {
        /*
         * Note: we force load the EarlyReturnException class because compilation bails out when it
         * hasn't been loaded (the {@code interceptControlFlowException} method references it
         * directly).
         */
        try {
            Class.forName(BasicInterpreter.EarlyReturnException.class.getName());
        } catch (ClassNotFoundException ex) {
            fail("should not have failed to load EarlyReturnException class");
        }
    }

    /**
     * The program below implements:
     *
     * <pre>
     * var j = 0;
     * var i = 0;
     * var sum = 0;
     * while (i < 500000) {
     *     j = j + 1;
     *     sum = sum + j;
     *     i = i + 1;
     * }
     * return sum;
     * </pre>
     *
     * The result should be 125000250000.
     */
    @Test
    public void testOSR1() {
        BasicInterpreter root = parseNode(run, BytecodeDSLTestLanguage.REF.get(null), "osrRoot", b -> {
            b.beginRoot();

            BytecodeLocal iLoc = b.createLocal();
            BytecodeLocal sumLoc = b.createLocal();
            BytecodeLocal jLoc = b.createLocal();

            // int j = 0;
            b.beginStoreLocal(jLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // int i = 0;
            b.beginStoreLocal(iLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // int sum = 0;
            b.beginStoreLocal(sumLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // while (i < TOTAL_ITERATIONS) {
            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(iLoc);
            b.emitLoadConstant(500000L);
            b.endLess();
            b.beginBlock();

            // j = j + 1;
            b.beginStoreLocal(jLoc);
            b.beginAdd();
            b.emitLoadLocal(jLoc);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            // sum = sum + j;
            b.beginStoreLocal(sumLoc);
            b.beginAdd();
            b.emitLoadLocal(sumLoc);
            b.emitLoadLocal(jLoc);
            b.endAdd();
            b.endStoreLocal();

            // i = i + 1;
            b.beginStoreLocal(iLoc);
            b.beginAdd();
            b.emitLoadLocal(iLoc);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            // }
            b.endBlock();
            b.endWhile();

            // return sum;
            b.beginReturn();
            b.emitLoadLocal(sumLoc);
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        for (int i = 0; i < 10; i++) {
            target.resetCompilationProfile();
            assertEquals(125000250000L, target.call());
        }
    }

    /**
     * The program below implements:
     *
     * <pre>
     * int i = 0;
     * int sum = 0;
     * while (i < 500000) {
     *     int j = 0;
     *     while (j < i) {
     *         int temp;
     *         if (i % 3 < 1) {
     *             temp = 1;
     *         } else {
     *             temp = i % 3;
     *         }
     *         j = j + temp;
     *     }
     *     sum = sum + j;
     *     i = i + 1;
     * }
     * return sum;
     * </pre>
     *
     * The result should be 12497500.
     */
    @Test
    public void testOSR2() {
        BasicInterpreter root = parseNode(run, BytecodeDSLTestLanguage.REF.get(null), "osrRoot", b -> {
            b.beginRoot();

            BytecodeLocal iLoc = b.createLocal();
            BytecodeLocal sumLoc = b.createLocal();
            BytecodeLocal jLoc = b.createLocal();
            BytecodeLocal tempLoc = b.createLocal();

            // int j = 0;
            b.beginStoreLocal(jLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // int i = 0;
            b.beginStoreLocal(iLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // int sum = 0;
            b.beginStoreLocal(sumLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // while (i < TOTAL_ITERATIONS) {
            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(iLoc);
            b.emitLoadConstant(500000L);
            b.endLess();
            b.beginBlock();

            // while (j < i) {
            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(jLoc);
            b.emitLoadLocal(iLoc);
            b.endLess();
            b.beginBlock();

            // int temp;
            // if (i % 3 < 1) {
            b.beginIfThenElse();

            b.beginLess();
            b.beginMod();
            b.emitLoadLocal(iLoc);
            b.emitLoadConstant(3L);
            b.endMod();
            b.emitLoadConstant(1L);
            b.endLess();

            // temp = 1;
            b.beginStoreLocal(tempLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            // } else {
            // temp = i % 3;
            b.beginStoreLocal(tempLoc);
            b.beginMod();
            b.emitLoadLocal(iLoc);
            b.emitLoadConstant(3L);
            b.endMod();
            b.endStoreLocal();

            // }
            b.endIfThenElse();

            // j = j + temp;
            b.beginStoreLocal(jLoc);
            b.beginAdd();
            b.emitLoadLocal(jLoc);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            // }
            b.endBlock();
            b.endWhile();

            // sum = sum + j;
            b.beginStoreLocal(sumLoc);
            b.beginAdd();
            b.emitLoadLocal(sumLoc);
            b.emitLoadLocal(jLoc);
            b.endAdd();
            b.endStoreLocal();

            // i = i + 1;
            b.beginStoreLocal(iLoc);
            b.beginAdd();
            b.emitLoadLocal(iLoc);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            // }
            b.endBlock();
            b.endWhile();

            // return sum;
            b.beginReturn();
            b.emitLoadLocal(sumLoc);
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        for (int i = 0; i < 10; i++) {
            // reset profile to avoid regular compilation
            target.resetCompilationProfile();
            assertEquals(124999750000L, target.call());
        }
    }

    @Test
    public void testCompiles() {
        BasicInterpreter root = parseNodeForCompilation(run, "addTwoConstants", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        assertEquals(42L, target.call());
        target.compile(true);
        assertCompiled(target);
        assertEquals(42L, target.call());
        assertCompiled(target);
    }

    @Test
    public void testMultipleReturns() {
        // return 30 + (arg0 ? 12 : (return 123; 0))
        BasicInterpreter root = parseNodeForCompilation(run, "multipleReturns", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(30L);
            b.beginConditional();
            b.emitLoadArgument(0);
            b.emitLoadConstant(12L);
            b.beginBlock();
            b.beginReturn();
            b.emitLoadConstant(123L);
            b.endReturn();
            b.emitLoadConstant(0L);
            b.endBlock();
            b.endConditional();
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        assertEquals(42L, target.call(true));
        assertEquals(123L, target.call(false));
        target.compile(true);
        assertCompiled(target);
        assertEquals(42L, target.call(true));
        assertEquals(123L, target.call(false));
        assertCompiled(target);
    }

    /**
     * When an root changes its local tags, compiled code should be invalidated.
     */
    @Test
    public void testStoreInvalidatesCode() {
        assumeTrue(hasBoxingElimination());
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadArgument(0);
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadNull();
            b.endYield();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endRoot();
        });
        BasicInterpreter root = rootNodes.getNode(0);
        root.getBytecodeNode().setUncachedThreshold(0); // force cached

        // Run once and check profile.
        OptimizedCallTarget rootTarget = (OptimizedCallTarget) root.getCallTarget();
        ContinuationResult cont = (ContinuationResult) rootTarget.call(42L);
        OptimizedCallTarget contTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(42L, cont.continueWith(null));
        assertEquals(FrameSlotKind.Long, root.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Now, force compile root node and continuation.
        rootTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(rootTarget);
        assertCompiled(contTarget);

        // Run again to ensure nothing deopts.
        cont = (ContinuationResult) rootTarget.call(123L);
        assertCompiled(rootTarget);
        assertEquals(123L, cont.continueWith(null));
        assertCompiled(contTarget);
        assertEquals(FrameSlotKind.Long, root.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // If we store a value with a different tag, both call targets should invalidate.
        cont = (ContinuationResult) rootTarget.call("hello");
        assertNotCompiled(rootTarget);
        assertNotCompiled(contTarget);
        assertEquals("hello", cont.continueWith(null));
        assertEquals(FrameSlotKind.Object, root.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Both call targets should recompile.
        rootTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(rootTarget);
        assertCompiled(contTarget);
    }

    /**
     * When a BytecodeNode store changes the local tags, compiled code should be invalidated.
     */
    @Test
    public void testBytecodeNodeStoreInvalidatesCode() {
        assumeTrue(hasBoxingElimination());
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadNull();
            b.endYield();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endRoot();
        });
        BasicInterpreter root = rootNodes.getNode(0);
        root.getBytecodeNode().setUncachedThreshold(0); // force cached

        // Run once and check profile.
        OptimizedCallTarget rootTarget = (OptimizedCallTarget) root.getCallTarget();
        ContinuationResult cont = (ContinuationResult) rootTarget.call();
        OptimizedCallTarget contTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(42L, cont.continueWith(null));
        assertEquals(FrameSlotKind.Long, root.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Now, force compile root node and continuation.
        rootTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(rootTarget);
        assertCompiled(contTarget);

        // Run again to ensure nothing deopts.
        cont = (ContinuationResult) rootTarget.call();
        assertCompiled(rootTarget);
        assertEquals(42L, cont.continueWith(null));
        assertCompiled(contTarget);
        assertEquals(FrameSlotKind.Long, root.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // If we store a value with the same tag, both call targets should stay valid.
        cont = (ContinuationResult) rootTarget.call();
        BytecodeLocation location = cont.getBytecodeLocation();
        BytecodeNode bytecodeNode = location.getBytecodeNode();
        bytecodeNode.setLocalValue(location.getBytecodeIndex(), cont.getFrame(), 0, 123L);
        assertCompiled(rootTarget);
        assertCompiled(contTarget);
        assertEquals(123L, cont.continueWith(null));
        assertEquals(FrameSlotKind.Long, root.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // If we store a value with a different tag, both call targets should invalidate.
        cont = (ContinuationResult) rootTarget.call();
        location = cont.getBytecodeLocation();
        bytecodeNode = location.getBytecodeNode();
        bytecodeNode.setLocalValue(location.getBytecodeIndex(), cont.getFrame(), 0, "hello");
        assertNotCompiled(rootTarget);
        assertNotCompiled(contTarget);
        assertEquals("hello", cont.continueWith(null));
        assertEquals(FrameSlotKind.Object, root.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Both call targets should recompile.
        rootTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(rootTarget);
        assertCompiled(contTarget);
    }

    /**
     * When an inner root changes the local tags with a materialized store, compiled code should be
     * invalidated.
     */
    @Test
    public void testMaterializedStoreInvalidatesCode() {
        assumeTrue(hasBoxingElimination());
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadNull();
            b.endYield();

            b.beginRoot(); // inner
            b.beginStoreLocalMaterialized(x);
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endStoreLocalMaterialized();
            b.endRoot();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endRoot();
        });
        BasicInterpreter outer = rootNodes.getNode(0);
        outer.getBytecodeNode().setUncachedThreshold(0); // force cached
        BasicInterpreter inner = rootNodes.getNode(1);

        // Run once and check profile.
        OptimizedCallTarget outerTarget = (OptimizedCallTarget) outer.getCallTarget();
        ContinuationResult cont = (ContinuationResult) outerTarget.call();
        OptimizedCallTarget contTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(42L, cont.continueWith(null));
        assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Now, force compile root node and continuation.
        outerTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(outerTarget);
        assertCompiled(contTarget);

        // Run again to ensure nothing deopts.
        cont = (ContinuationResult) outerTarget.call();
        assertCompiled(outerTarget);
        assertEquals(42L, cont.continueWith(null));
        assertCompiled(contTarget);
        assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // If we store a value with the same tag, both call targets should stay valid.
        cont = (ContinuationResult) outerTarget.call();
        inner.getCallTarget().call(cont.getFrame(), 123L);
        assertCompiled(outerTarget);
        assertEquals(123L, cont.continueWith(null));
        assertCompiled(contTarget);
        assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // If we store a value with a different tag, both call targets should invalidate.
        cont = (ContinuationResult) outerTarget.call();
        inner.getCallTarget().call(cont.getFrame(), "hello");
        assertNotCompiled(outerTarget);
        assertNotCompiled(contTarget);
        assertEquals("hello", cont.continueWith(null));
        assertEquals(FrameSlotKind.Object, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Both call targets should recompile.
        outerTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(outerTarget);
        assertCompiled(contTarget);
    }

    /**
     * When an inner root changes the local tags with a materialized local accessor store, compiled
     * code should be invalidated.
     */
    @Test
    public void testMaterializedAccessorStoreInvalidatesCode() {
        assumeTrue(hasBoxingElimination());
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);
            b.beginStoreLocal(x);
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadNull();
            b.endYield();

            b.beginRoot(); // inner
            b.beginTeeMaterializedLocal(x);
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endTeeMaterializedLocal();
            b.endRoot();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endRoot();
        });
        BasicInterpreter outer = rootNodes.getNode(0);
        outer.getBytecodeNode().setUncachedThreshold(0); // force cached
        BasicInterpreter inner = rootNodes.getNode(1);

        // Run once and check profile.
        OptimizedCallTarget outerTarget = (OptimizedCallTarget) outer.getCallTarget();
        ContinuationResult cont = (ContinuationResult) outerTarget.call();
        OptimizedCallTarget contTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(42L, cont.continueWith(null));
        assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Now, force compile root node and continuation.
        outerTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(outerTarget);
        assertCompiled(contTarget);

        // Run again to ensure nothing deopts.
        cont = (ContinuationResult) outerTarget.call();
        assertCompiled(outerTarget);
        assertEquals(42L, cont.continueWith(null));
        assertCompiled(contTarget);
        assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // If we store a value with the same tag, both call targets should stay valid.
        cont = (ContinuationResult) outerTarget.call();
        inner.getCallTarget().call(cont.getFrame(), 123L);
        assertCompiled(outerTarget);
        assertEquals(123L, cont.continueWith(null));
        assertCompiled(contTarget);
        assertEquals(FrameSlotKind.Long, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // If we store a value with a different tag, both call targets should invalidate.
        cont = (ContinuationResult) outerTarget.call();
        inner.getCallTarget().call(cont.getFrame(), "hello");
        assertNotCompiled(outerTarget);
        assertNotCompiled(contTarget);
        assertEquals("hello", cont.continueWith(null));
        assertEquals(FrameSlotKind.Object, outer.getBytecodeNode().getLocals().get(0).getTypeProfile());

        // Both call targets should recompile.
        outerTarget.compile(true);
        contTarget.compile(true);
        assertCompiled(outerTarget);
        assertCompiled(contTarget);
    }

    @Test
    public void testInstrumentation() {
        BasicInterpreter root = parseNodeForCompilation(run, "addTwoConstantsInstrumented", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginIncrementValue();
            b.beginAdd();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAdd();
            b.endIncrementValue();
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        assertEquals(42L, target.call());
        target.compile(true);
        assertCompiled(target);

        // Instrumentation should invalidate the compiled code.
        root.getRootNodes().update(
                        run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build());
        assertNotCompiled(target);

        // The instrumented interpreter should be recompiled.
        assertEquals(43L, target.call());
        target.compile(true);
        assertCompiled(target);
        assertEquals(43L, target.call());
        assertCompiled(target);
    }

    @Test
    public void testYield() {
        BasicInterpreter root = parseNodeForCompilation(run, "addYield", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(20L);
            b.beginYield();
            b.emitLoadConstant(123L);
            b.endYield();
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        ContinuationResult cont = (ContinuationResult) target.call();
        assertEquals(123L, cont.getResult());
        OptimizedCallTarget continuationCallTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(42L, cont.continueWith(22L));
        assertNotCompiled(target);
        assertNotCompiled(continuationCallTarget);

        target.compile(true);
        cont = (ContinuationResult) target.call();
        continuationCallTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(40L, cont.continueWith(20L));
        assertCompiled(target);
        assertNotCompiled(continuationCallTarget);

        continuationCallTarget.compile(true);
        cont = (ContinuationResult) target.call();
        continuationCallTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(44L, cont.continueWith(24L));
        assertCompiled(target);
        assertCompiled(continuationCallTarget);
    }

    @Test
    public void testYieldInstrumentation() {
        BasicInterpreter root = parseNodeForCompilation(run, "addYieldInstrumented", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginIncrementValue();
            b.beginAdd();
            b.emitLoadConstant(20L);
            b.beginYield();
            b.emitLoadConstant(123L);
            b.endYield();
            b.endAdd();
            b.endIncrementValue();
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        OptimizedCallTarget continuationCallTarget = null;

        ContinuationResult cont = (ContinuationResult) target.call();
        assertEquals(123L, cont.getResult());
        continuationCallTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(42L, cont.continueWith(22L));
        assertNotCompiled(target);
        assertNotCompiled(continuationCallTarget);

        target.compile(true);
        continuationCallTarget.compile(true);
        assertCompiled(target);
        assertCompiled(continuationCallTarget);

        // Instrumentation should invalidate the compiled code.
        root.getRootNodes().update(
                        run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build());
        assertNotCompiled(target);
        assertNotCompiled(continuationCallTarget);

        // The instrumented interpreter should be recompiled.
        assertEquals(43L, ((ContinuationResult) target.call()).continueWith(22L));
        target.compile(true);
        continuationCallTarget.compile(true);
        assertCompiled(target);
        assertCompiled(continuationCallTarget);
        assertEquals(43L, ((ContinuationResult) target.call()).continueWith(22L));
        assertCompiled(target);
        assertCompiled(continuationCallTarget);
    }

    @Test
    public void testContinuationFrameStateTransfer() {
        testContinuationFrameStateTransfer("continuationFrameStateTransferYield", BasicInterpreterBuilder::beginYield, BasicInterpreterBuilder::endYield);
        testContinuationFrameStateTransfer("continuationFrameStateTransferCustomYield", BasicInterpreterBuilder::beginCustomYield, BasicInterpreterBuilder::endCustomYield);
    }

    @Test
    public void testContinuationStoreLocal() {
        testContinuationFrameLocalAccess("continuationStoreLocal", (b, x) -> {
            b.beginStoreLocal(x);
            b.emitLoadConstant(41L);
            b.endStoreLocal();
        });
    }

    @Test
    public void testContinuationLocalAccessor() {
        testContinuationFrameLocalAccess("continuationLocalAccessor", (b, x) -> {
            b.beginTeeLocal(x);
            b.emitLoadConstant(41L);
            b.endTeeLocal();
        });
    }

    @Test
    public void testContinuationBytecodeSetLocalValue() {
        testContinuationFrameLocalAccess("continuationBytecodeSetLocalValue", (b, x) -> {
            b.beginBytecodeSetLocalValue(x.getLocalOffset());
            b.emitLoadConstant(41L);
            b.endBytecodeSetLocalValue();
        });
    }

    private void testContinuationFrameLocalAccess(String rootNamePrefix, BiConsumer<BasicInterpreterBuilder, BytecodeLocal> emitStore) {
        testContinuationFrameLocalAccess(rootNamePrefix + "Yield", BasicInterpreterBuilder::beginYield, BasicInterpreterBuilder::endYield, emitStore, false);
        testContinuationFrameLocalAccess(rootNamePrefix + "YieldAfterDeopt", BasicInterpreterBuilder::beginYield, BasicInterpreterBuilder::endYield, emitStore, true);
        testContinuationFrameLocalAccess(rootNamePrefix + "CustomYield", BasicInterpreterBuilder::beginCustomYield, BasicInterpreterBuilder::endCustomYield, emitStore, false);
        testContinuationFrameLocalAccess(rootNamePrefix + "CustomYieldAfterDeopt", BasicInterpreterBuilder::beginCustomYield, BasicInterpreterBuilder::endCustomYield, emitStore, true);
    }

    private void testContinuationFrameLocalAccess(String rootName,
                    Consumer<BasicInterpreterBuilder> beginYield,
                    Consumer<BasicInterpreterBuilder> endYield,
                    BiConsumer<BasicInterpreterBuilder, BytecodeLocal> emitStore,
                    boolean deoptBeforeWrite) {
        BasicInterpreter root = parseNodeForCompilation(run, rootName, b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);

            b.beginIfThenElse();
            b.emitLoadArgument(0);

            b.beginBlock(); // write path
            beginYield.accept(b);
            b.emitLoadConstant(0L);
            endYield.accept(b);

            if (deoptBeforeWrite) {
                b.beginDeoptimize();
                b.emitLoadArgument(1);
                b.endDeoptimize();
            }

            emitStore.accept(b, x);

            b.beginReturn();
            b.beginInvokeRecursive();
            b.emitLoadConstant(false);
            b.emitMaterializeFrame();
            b.endInvokeRecursive();
            b.endReturn();
            b.endBlock();

            b.beginBlock(); // frame-checking path
            b.beginReturn();
            b.beginLoadLocalMaterialized(x);
            b.emitLoadArgument(1);
            b.endLoadLocalMaterialized();
            b.endReturn();
            b.endBlock();

            b.endIfThenElse();
            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();

        ContinuationResult warmup = (ContinuationResult) target.call(true, deoptBeforeWrite);
        assertEquals(41L, warmup.continueWith(null));

        ContinuationResult yielded = (ContinuationResult) target.call(true, deoptBeforeWrite);
        OptimizedCallTarget continuationTarget = (OptimizedCallTarget) yielded.getContinuationCallTarget();
        continuationTarget.compile(true);
        assertCompiled(continuationTarget);
        assertEquals(41L, yielded.continueWith(null));
        assertCompiled(continuationTarget);
    }

    private void testContinuationFrameStateTransfer(String rootName, Consumer<BasicInterpreterBuilder> beginYield, Consumer<BasicInterpreterBuilder> endYield) {
        BasicInterpreter root = parseNodeForCompilation(run, rootName, b -> {
            b.beginRoot();
            BytecodeLocal x = b.createLocal("x", null);

            b.beginStoreLocal(x);
            beginYield.accept(b);
            b.emitLoadConstant(0L);
            endYield.accept(b);
            b.endStoreLocal();

            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(1L);
            b.beginAdd();
            b.beginBlock();
            // Stack operands, locals, arguments should all be preserved if deopt occurs.
            b.beginDeoptimize();
            b.emitLoadArgument(0);
            b.endDeoptimize();
            b.emitLoadArgument(1);
            b.endBlock();
            b.emitLoadLocal(x);
            b.endAdd();
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        ContinuationResult cont = (ContinuationResult) target.call(false, 2L);
        OptimizedCallTarget contTarget = (OptimizedCallTarget) cont.getContinuationCallTarget();
        assertEquals(7L, cont.continueWith(4L));
        contTarget.compile(true);
        assertCompiled(contTarget);

        cont = (ContinuationResult) target.call(false, 2L);
        assertEquals(7L, cont.continueWith(4L));
        assertCompiled(contTarget);

        cont = (ContinuationResult) target.call(true, 2L);
        assertEquals(7L, cont.continueWith(4L));
        assertCompiled(contTarget);
    }

    @Test
    public void testContinuationSecondYieldPreservesStackOperands() {
        testContinuationSecondYieldPreservesStackOperands("continuationSecondYieldPreservesStackOperandsYield", BasicInterpreterBuilder::beginYield, BasicInterpreterBuilder::endYield);
        testContinuationSecondYieldPreservesStackOperands("continuationSecondYieldPreservesStackOperandsCustomYield", BasicInterpreterBuilder::beginCustomYield,
                        BasicInterpreterBuilder::endCustomYield);
    }

    private void testContinuationSecondYieldPreservesStackOperands(String rootName, Consumer<BasicInterpreterBuilder> beginYield, Consumer<BasicInterpreterBuilder> endYield) {
        BasicInterpreter root = parseNodeForCompilation(run, rootName, b -> {
            b.beginRoot();

            beginYield.accept(b);
            b.emitLoadConstant(1L);
            endYield.accept(b);

            b.beginReturn();
            b.beginAdd();
            b.emitLoadConstant(20L);
            beginYield.accept(b);
            b.emitLoadConstant(2L);
            endYield.accept(b);
            b.endAdd();
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();

        // Warm up end-to-end so both continuation roots transition out of uninitialized state.
        ContinuationResult warmupFirst = (ContinuationResult) target.call();
        ContinuationResult warmupSecond = (ContinuationResult) warmupFirst.continueWith(0L);
        assertEquals(2L, warmupSecond.getResult());
        assertEquals(42L, warmupSecond.continueWith(22L));

        ContinuationResult first = (ContinuationResult) target.call();
        OptimizedCallTarget firstTarget = (OptimizedCallTarget) first.getContinuationCallTarget();
        firstTarget.compile(true);
        assertCompiled(firstTarget);

        ContinuationResult second = (ContinuationResult) first.continueWith(0L);
        assertEquals(2L, second.getResult());
        assertEquals(42L, second.continueWith(22L));

        first = (ContinuationResult) target.call();
        firstTarget = (OptimizedCallTarget) first.getContinuationCallTarget();
        assertCompiled(firstTarget);
        second = (ContinuationResult) first.continueWith(0L);
        assertEquals(2L, second.getResult());
        OptimizedCallTarget secondTarget = (OptimizedCallTarget) second.getContinuationCallTarget();
        secondTarget.compile(true);
        assertCompiled(secondTarget);
        assertEquals(42L, second.continueWith(22L));
        assertCompiled(secondTarget);
    }

    @Test
    public void testContinuationFrameIdentity() {
        testContinuationFrameIdentity("continuationFrameIdentityYield", BasicInterpreterBuilder::beginYield, BasicInterpreterBuilder::endYield);
        testContinuationFrameIdentity("continuationFrameIdentityCustomYield", BasicInterpreterBuilder::beginCustomYield, BasicInterpreterBuilder::endCustomYield);
    }

    private void testContinuationFrameIdentity(String rootName, Consumer<BasicInterpreterBuilder> beginYield, Consumer<BasicInterpreterBuilder> endYield) {
        BasicInterpreter root = parseNodeForCompilation(run, rootName, b -> {
            b.beginRoot();

            beginYield.accept(b);
            b.emitLoadConstant(1L);
            endYield.accept(b);

            beginYield.accept(b);
            b.emitLoadConstant(2L);
            endYield.accept(b);

            b.beginReturn();
            b.emitLoadConstant(3L);
            b.endReturn();

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        ContinuationResult r1 = (ContinuationResult) target.call();
        OptimizedCallTarget contTarget1 = (OptimizedCallTarget) r1.getContinuationCallTarget();
        ContinuationResult r2 = (ContinuationResult) r1.continueWith(null);
        OptimizedCallTarget contTarget2 = (OptimizedCallTarget) r2.getContinuationCallTarget();
        assertEquals(2L, r2.getResult());
        // Compiled continuations may use a virtualized stack frame internally, but yield must
        // expose the stable materialized continuation frame to users.
        assertSame(r1.getFrame(), r2.getFrame());
        assertEquals(3L, r2.continueWith(null));
        contTarget1.compile(true);
        assertCompiled(contTarget1);
        contTarget2.compile(true);
        assertCompiled(contTarget2);

        r1 = (ContinuationResult) target.call();
        r2 = (ContinuationResult) r1.continueWith(null);
        assertEquals(2L, r2.getResult());
        assertSame(r1.getFrame(), r2.getFrame());
        assertEquals(3L, r2.continueWith(null));
        assertCompiled(contTarget1);
        assertCompiled(contTarget2);
    }

    @Test
    public void testCompiledSourceInfo() {
        Source s = Source.newBuilder("test", "return sourcePosition", "compiledSourceInfo").build();
        BasicInterpreter root = parseNodeForCompilation(run, "compiledSourceInfo", b -> {
            b.beginSource(s);
            b.beginSourceSection(0, 21);
            b.beginRoot();

            b.beginReturn();
            b.beginSourceSection(7, 14);
            b.beginEnsureAndGetSourcePosition();
            b.emitLoadArgument(0);
            b.endEnsureAndGetSourcePosition();
            b.endSourceSection();
            b.endReturn();

            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });
        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();

        assertNull(target.call(false));
        target.compile(true);
        assertCompiled(target);

        // Reparse with sources. The compiled code should not invalidate.
        root.getBytecodeNode().ensureSourceInformation();
        assertCompiled(target);

        // Calling the compiled code won't update the sources.
        assertNull(target.call(false));
        assertCompiled(target);

        // Calling ensureSourceInformation from compiled code should deopt and update the sources.
        assertEquals("sourcePosition", ((SourceSection) target.call(true)).getCharacters().toString());
        assertNotCompiled(target);

        // If we recompile, source information should be available.
        target.compile(true);
        assertCompiled(target);
        assertEquals("sourcePosition", ((SourceSection) target.call(false)).getCharacters().toString());
        assertCompiled(target);

        // Calling ensureSourceInformation when sources are available should not deopt.
        assertEquals("sourcePosition", ((SourceSection) target.call(true)).getCharacters().toString());
        assertCompiled(target);
    }

    @Test
    public void testTagInstrumentation() {
        BasicInterpreter root = parseNodeForCompilation(run, "tagInstrumentation", b -> {
            b.beginRoot();

            // i = 0
            BytecodeLocal i = b.createLocal();
            b.beginTag(StatementTag.class);
            b.beginStoreLocal(i);
            b.emitLoadConstant(0L);
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            // while i < arg0
            b.beginWhile();
            b.beginTag(StatementTag.class);
            b.beginLess();
            b.emitLoadLocal(i);
            b.emitLoadArgument(0);
            b.endLess();
            b.endTag(StatementTag.class);

            // i = i + 1;
            b.beginTag(StatementTag.class);
            b.beginStoreLocal(i);
            b.beginAdd();
            b.emitLoadLocal(i);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();
            b.endTag(StatementTag.class);

            b.endWhile();

            // return i
            b.beginTag(StatementTag.class);
            b.beginReturn();
            b.emitLoadLocal(i);
            b.endReturn();
            b.endTag(StatementTag.class);

            b.endRoot();
        });

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();
        assertEquals(5L, target.call(5L));

        // Ensure it compiles without tags.
        target.compile(true);
        assertCompiled(target);
        // It shouldn't deopt.
        assertEquals(42L, target.call(42L));
        assertCompiled(target);

        // Reparsing with tags should invalidate the code, but it should recompile.
        // Expected count: 1 enter + (n+1) condition + n loop body + 1 return = 2n + 3
        Counter c = attachCounter(StatementTag.class);
        assertNotCompiled(target);
        target.resetCompilationProfile();
        assertEquals(5L, target.call(5L));
        assertEquals(13, c.get());
        assertNotCompiled(target);
        target.compile(true);
        assertCompiled(target);
        // It shouldn't deopt.
        c.clear();
        assertEquals(11L, target.call(11L));
        assertEquals(25, c.get());
        assertCompiled(target);

        // Attaching a second binding with different tags should invalidate the code again.
        Counter c2 = attachCounter(RootTag.class);
        assertNotCompiled(target);
        c.clear();
        assertEquals(5L, target.call(5L));
        assertEquals(13, c.get());
        assertEquals(1, c2.get());
        assertNotCompiled(target);
        target.compile(true);
        assertCompiled(target);
        // It shouldn't deopt.
        c.clear();
        c2.clear();
        assertEquals(20L, target.call(20L));
        assertEquals(43, c.get());
        assertEquals(1, c2.get());
        assertCompiled(target);
    }

    @Test
    public void testCaptureFrame() {
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCaptureFrame();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endCaptureFrame();
            b.endReturn();
            BasicInterpreter callee = b.endRoot();
            callee.setName("callee");

            b.beginRoot();
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(123);
            b.endStoreLocal();
            b.beginInvoke();
            b.emitLoadConstant(callee);
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endInvoke();
            b.endRoot().setName("caller");
        });
        BasicInterpreter caller = rootNodes.getNode(1);

        OptimizedCallTarget target = (OptimizedCallTarget) caller.getCallTarget();

        // The callee frame (the top of the stack) should never be accessible.
        assertNull(target.call(0, FrameInstance.FrameAccess.READ_ONLY));

        // In the interpreter the caller frame should always be accessible.
        assertNotCompiled(target);
        checkCallerBytecodeFrame((BytecodeFrame) target.call(1, FrameInstance.FrameAccess.READ_ONLY), false);
        assertNotCompiled(target);
        checkCallerBytecodeFrame((BytecodeFrame) target.call(1, FrameInstance.FrameAccess.READ_WRITE), false);
        assertNotCompiled(target);
        checkCallerBytecodeFrame((BytecodeFrame) target.call(1, FrameInstance.FrameAccess.MATERIALIZE), false);

        // Force transition to cached.
        caller.getBytecodeNode().setUncachedThreshold(0);
        target.call(0, FrameInstance.FrameAccess.READ_ONLY);
        assertEquals(BytecodeTier.CACHED, caller.getBytecodeNode().getTier());

        // In compiled code the caller frame should always be accessible, but may be a copy.
        // Requesting the frame should not invalidate compiled code.
        target.compile(true);
        assertCompiled(target);
        checkCallerBytecodeFrame((BytecodeFrame) target.call(1, FrameInstance.FrameAccess.READ_ONLY), true);
        assertCompiled(target);
        checkCallerBytecodeFrame((BytecodeFrame) target.call(1, FrameInstance.FrameAccess.READ_WRITE), false);
        assertCompiled(target);
        checkCallerBytecodeFrame((BytecodeFrame) target.call(1, FrameInstance.FrameAccess.MATERIALIZE), false);
        assertCompiled(target);
    }

    @Test
    public void testCaptureNonVirtualFrame() {
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCaptureNonVirtualFrame();
            b.emitLoadArgument(0);
            b.endCaptureNonVirtualFrame();
            b.endReturn();
            BasicInterpreter callee = b.endRoot();
            callee.setName("callee");

            b.beginRoot();
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(123);
            b.endStoreLocal();
            b.beginInvoke();
            b.emitLoadConstant(callee);
            b.emitLoadArgument(0);
            b.endInvoke();
            b.endRoot().setName("caller");
        });
        BasicInterpreter caller = rootNodes.getNode(1);

        OptimizedCallTarget target = (OptimizedCallTarget) caller.getCallTarget();

        // The callee frame (the top of the stack) should never be accessible.
        assertNull(target.call(0));

        // In the interpreter the non-virtual caller frame should be accessible.
        assertNotCompiled(target);
        BytecodeFrame nonVirtualFrame = (BytecodeFrame) target.call(1);
        assertNotCompiled(target);
        checkCallerBytecodeFrame(nonVirtualFrame, false);

        // Force transition to cached.
        caller.getBytecodeNode().setUncachedThreshold(0);
        target.call(0);
        assertEquals(BytecodeTier.CACHED, caller.getBytecodeNode().getTier());

        // In compiled code the non-virtual caller frame should be inaccessible.
        target.compile(true);
        assertCompiled(target);
        assertNull(target.call(1));
        assertCompiled(target);
    }

    @Test
    public void testCaptureNonVirtualFrameAfterMaterialization() {
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCaptureNonVirtualFrame();
            b.emitLoadArgument(0);
            b.endCaptureNonVirtualFrame();
            b.endReturn();
            BasicInterpreter callee = b.endRoot();
            callee.setName("callee");

            b.beginRoot();
            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(123);
            b.endStoreLocal();

            b.beginBlackhole();
            b.emitMaterializeFrame(); // force materialize frame.
            b.endBlackhole();

            b.beginInvoke();
            b.emitLoadConstant(callee);
            b.emitLoadArgument(0);
            b.endInvoke();
            b.endRoot().setName("caller");
        });
        BasicInterpreter caller = rootNodes.getNode(1);

        OptimizedCallTarget target = (OptimizedCallTarget) caller.getCallTarget();

        // The callee frame (the top of the stack) should never be accessible.
        assertNull(target.call(0));

        // In the interpreter the non-virtual caller frame should be accessible.
        assertNotCompiled(target);
        BytecodeFrame nonVirtualFrame = (BytecodeFrame) target.call(1);
        assertNotCompiled(target);
        checkCallerBytecodeFrame(nonVirtualFrame, false);

        // Force transition to cached.
        caller.getBytecodeNode().setUncachedThreshold(0);
        target.call(0);
        assertEquals(BytecodeTier.CACHED, caller.getBytecodeNode().getTier());

        // In compiled code the frame should be accessible because it was materialized already.
        target.compile(true);
        assertCompiled(target);
        nonVirtualFrame = (BytecodeFrame) target.call(1);
        checkCallerBytecodeFrame(nonVirtualFrame, false);
        assertCompiled(target);
    }

    /**
     * The program below implements the following pseudocode.
     *
     * <pre>
     * var j = 0;
     * var i = 0;
     * var sum = 0;
     * while (i < arg[0]) {
     *     if (arg[1]) {
     *         transferToInterpreter();
     *     }
     *     j = j + 1;
     *     sum = sum + j;
     *     i = i + 1;
     * }
     * return sum;
     * </pre>
     *
     */
    @Test
    public void testGR73707() {
        List<String> transitionLogs = new ArrayList<>();
        Context.Builder builder = newContextBuilder().option("engine.TraceBytecodeTransition", "transferToInterpreter").option("engine.BackgroundCompilation", "false").option(
                        "engine.CompilationFailureAction", "Silent").option("engine.MultiTier", "false").option("engine.LastTierCompilationThreshold", "1000000000").option("engine.OSR",
                                        "false").logHandler(new Handler() {
                                            @Override
                                            public void publish(LogRecord record) {
                                                synchronized (transitionLogs) {
                                                    transitionLogs.add(record.getMessage());
                                                }
                                            }

                                            @Override
                                            public void close() {
                                            }

                                            @Override
                                            public void flush() {
                                            }
                                        });

        context = setupContext(builder);
        context.initialize(BytecodeDSLTestLanguage.ID);

        BasicInterpreter root = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();

            BytecodeLocal iLoc = b.createLocal();
            BytecodeLocal sumLoc = b.createLocal();
            BytecodeLocal jLoc = b.createLocal();

            // int j = 0;
            b.beginStoreLocal(jLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // int i = 0;
            b.beginStoreLocal(iLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // int sum = 0;
            b.beginStoreLocal(sumLoc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            // while (i < TOTAL_ITERATIONS) {
            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(iLoc);
            b.emitLoadArgument(0);
            b.endLess();
            b.beginBlock();

            b.beginDeoptimize();
            b.emitLoadArgument(1);
            b.endDeoptimize();

            // j = j + 1;
            b.beginStoreLocal(jLoc);
            b.beginAdd();
            b.emitLoadLocal(jLoc);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            // sum = sum + j;
            b.beginStoreLocal(sumLoc);
            b.beginAdd();
            b.emitLoadLocal(sumLoc);
            b.emitLoadLocal(jLoc);
            b.endAdd();
            b.endStoreLocal();

            // i = i + 1;
            b.beginStoreLocal(iLoc);
            b.beginAdd();
            b.emitLoadLocal(iLoc);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            // }
            b.endBlock();
            b.endWhile();

            // return sum;
            b.beginReturn();
            b.emitLoadLocal(sumLoc);
            b.endReturn();

            b.endRoot().setName("caller");
        }).getNode(0);

        OptimizedCallTarget target = (OptimizedCallTarget) root.getCallTarget();

        long iterations = 32L;
        long expected = triangularSum(iterations);

        assertEquals(expected, target.call(iterations, false));

        synchronized (transitionLogs) {
            transitionLogs.clear();
        }

        // Compile and repeatedly trigger transfer-to-interpreter transitions without logging spam.
        target.compile(true);
        assertCompiled(target);
        for (int i = 0; i < 8; i++) {
            assertEquals(expected, target.call(iterations, true));
        }

        assertTrue("Expected transferToInterpreter transition for repeated deopts in compiled code", hasTransitionLog(transitionLogs, "transferToInterpreter"));
    }

    @Test
    public void testGR73707Inlined() {
        assumeTrue("Only cached-interpreter variants currently report transfer transitions for this scenario", !run.hasUncachedInterpreter());

        List<String> transitionLogs = new ArrayList<>();
        Context.Builder builder = newContextBuilder().option("engine.TraceBytecodeTransition", "transferToInterpreter").option("engine.CompilationFailureAction", "Silent").option("engine.MultiTier",
                        "false").option("engine.BackgroundCompilation", "false").option("engine.OSR", "false").logHandler(new Handler() {
                            @Override
                            public void publish(LogRecord record) {
                                synchronized (transitionLogs) {
                                    transitionLogs.add(record.getMessage());
                                }
                            }

                            @Override
                            public void close() {
                            }

                            @Override
                            public void flush() {
                            }
                        });

        context = setupContext(builder);
        context.initialize(BytecodeDSLTestLanguage.ID);

        BasicInterpreter callee = createNodes(run, BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();

            b.beginDeoptimize();
            b.emitLoadArgument(0);
            b.endDeoptimize();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot().setName("callee");
        }).getNode(0);

        OptimizedCallTarget calleeTarget = (OptimizedCallTarget) callee.getCallTarget();
        OptimizedCallTarget callerTarget = (OptimizedCallTarget) new ForceInlineInvokeRoot(BytecodeDSLTestLanguage.REF.get(null), calleeTarget).getCallTarget();

        assertEquals(42L, callerTarget.call(false));

        callerTarget.compile(true);
        assertCompiled(callerTarget);
        assertNotCompiled(calleeTarget);

        synchronized (transitionLogs) {
            transitionLogs.clear();
        }

        assertEquals(42L, callerTarget.call(true));

        assertTrue("Expected transferToInterpreter transition for inlined runtime-compiled method", hasTransitionLog(transitionLogs, "transferToInterpreter"));
        assertTrue("Expected transition to reference the Deoptimize operation", hasTransitionDetail(transitionLogs, "load.constant"));
        assertNotCompiled(calleeTarget);
    }

    private static long triangularSum(long value) {
        return (value * (value + 1L)) / 2L;
    }

    private static boolean hasTransitionLog(List<String> messages, String kind) {
        synchronized (messages) {
            for (String msg : messages) {
                String prefix = "[bc-transition] kinds=";
                if (!msg.startsWith(prefix)) {
                    continue;
                }
                int langIndex = msg.indexOf(" lang=");
                String kinds = langIndex >= 0 ? msg.substring(prefix.length(), langIndex) : msg.substring(prefix.length());
                for (String token : kinds.split(",")) {
                    if (token.equals(kind)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasTransitionDetail(List<String> messages, String detail) {
        synchronized (messages) {
            for (String msg : messages) {
                if (msg.startsWith("[bc-transition]") && msg.contains(detail)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class ForceInlineInvokeRoot extends RootNode {
        @Child private DirectCallNode callNode;

        ForceInlineInvokeRoot(BytecodeDSLTestLanguage language, CallTarget calleeTarget) {
            super(language);
            this.callNode = DirectCallNode.create(calleeTarget);
            this.callNode.forceInlining();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }

        @Override
        public String getName() {
            return "forceInlineCaller";
        }
    }

    private void checkCallerBytecodeFrame(BytecodeFrame bytecodeFrame, boolean isCopy) {
        assertNotNull(bytecodeFrame);
        assertEquals(1, bytecodeFrame.getLocalCount());
        if (isCopy || AbstractBasicInterpreterTest.hasRootScoping(run.interpreterClass())) {
            assertEquals(123, bytecodeFrame.getLocalValue(0));
        } else {
            // the local gets cleared on exit.
            assertEquals(AbstractBasicInterpreterTest.getDefaultLocalValue(run.interpreterClass()), bytecodeFrame.getLocalValue(0));
        }
    }

    @TruffleInstrument.Registration(id = BytecodeDSLCompilationTestInstrumentation.ID, services = Instrumenter.class)
    public static class BytecodeDSLCompilationTestInstrumentation extends TruffleInstrument {

        public static final String ID = "bytecode_CompilationTestInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
        }
    }

    private static final class Counter {
        private int count = 0;

        public int get() {
            return count;
        }

        public void inc() {
            count++;
        }

        public void clear() {
            count = 0;
        }
    }

    private Counter attachCounter(Class<?>... tags) {
        Counter c = new Counter();
        instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(tags).build(), (_) -> {
            return new ExecutionEventNode() {
                @Override
                public void onEnter(VirtualFrame f) {
                    c.inc();
                }
            };
        });
        return c;
    }

    private static BasicInterpreter parseNodeForCompilation(TestRun run,
                    String rootName, BytecodeParser<BasicInterpreterBuilder> builder) {
        BasicInterpreter result = parseNode(run, BytecodeDSLTestLanguage.REF.get(null), rootName, builder);
        result.getBytecodeNode().setUncachedThreshold(0); // force interpreter to skip tier 0
        return result;
    }
}
