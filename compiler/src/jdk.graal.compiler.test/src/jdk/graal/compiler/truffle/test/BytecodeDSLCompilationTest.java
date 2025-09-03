/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterBuilder;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.runtime.OptimizedCallTarget;

@RunWith(Parameterized.class)
public class BytecodeDSLCompilationTest extends TestWithSynchronousCompiling {

    @Parameters(name = "{0}")
    public static List<Class<? extends BasicInterpreter>> getInterpreterClasses() {
        return AbstractBasicInterpreterTest.allInterpreters();
    }

    @Parameter(0) public Class<? extends BasicInterpreter> interpreterClass;

    private boolean hasBoxingElimination() {
        return new AbstractBasicInterpreterTest.TestRun(interpreterClass, false).hasBoxingElimination();
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
        /**
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
        BasicInterpreter root = parseNode(interpreterClass, BytecodeDSLTestLanguage.REF.get(null), false, "osrRoot", b -> {
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
        BasicInterpreter root = parseNode(interpreterClass, BytecodeDSLTestLanguage.REF.get(null), false, "osrRoot", b -> {
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
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "addTwoConstants", b -> {
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
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "multipleReturns", b -> {
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
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(interpreterClass, BytecodeDSLTestLanguage.REF.get(null), false, BytecodeConfig.DEFAULT, b -> {
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
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(interpreterClass, BytecodeDSLTestLanguage.REF.get(null), false, BytecodeConfig.DEFAULT, b -> {
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
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(interpreterClass, BytecodeDSLTestLanguage.REF.get(null), false, BytecodeConfig.DEFAULT, b -> {
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
        BytecodeRootNodes<BasicInterpreter> rootNodes = createNodes(interpreterClass, BytecodeDSLTestLanguage.REF.get(null), false, BytecodeConfig.DEFAULT, b -> {
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
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "addTwoConstantsInstrumented", b -> {
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
                        BasicInterpreterBuilder.invokeNewConfigBuilder(interpreterClass).addInstrumentation(BasicInterpreter.IncrementValue.class).build());
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
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "addYield", b -> {
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
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "addYieldInstrumented", b -> {
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
                        BasicInterpreterBuilder.invokeNewConfigBuilder(interpreterClass).addInstrumentation(BasicInterpreter.IncrementValue.class).build());
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
    public void testCompiledSourceInfo() {
        Source s = Source.newBuilder("test", "return sourcePosition", "compiledSourceInfo").build();
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "compiledSourceInfo", b -> {
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
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "tagInstrumentation", b -> {
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

    private static <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeForCompilation(Class<? extends BasicInterpreter> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BasicInterpreter result = parseNode(interpreterClass, BytecodeDSLTestLanguage.REF.get(null), false, rootName, builder);
        result.getBytecodeNode().setUncachedThreshold(0); // force interpreter to skip tier 0
        return result;
    }
}
