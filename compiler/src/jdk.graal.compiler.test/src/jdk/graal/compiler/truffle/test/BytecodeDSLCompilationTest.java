/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest.parseNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterBuilder;
import com.oracle.truffle.runtime.OptimizedCallTarget;

@RunWith(Parameterized.class)
public class BytecodeDSLCompilationTest extends TestWithSynchronousCompiling {
    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends BasicInterpreter>> getInterpreterClasses() {
        return AbstractBasicInterpreterTest.allInterpreters();
    }

    @Parameter(0) public Class<? extends BasicInterpreter> interpreterClass;

    @Before
    @Override
    public void before() {
        super.before();
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
     * The benchmark programs implement:
     *
     * <pre>
     * var j = 0;
     * var i = 0;
     * var sum = 0;
     * while (i < 500000) {
     *     var j = j + 1;
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
        BasicInterpreter root = parseNode(interpreterClass, false, "osrRoot", b -> {
            b.beginRoot(LANGUAGE);

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
     * The benchmark programs implement:
     *
     * <pre>
     * int i = 0;
     * int sum = 0;
     * while (i < 5000) {
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
        BasicInterpreter root = parseNode(interpreterClass, false, "osrRoot", b -> {
            b.beginRoot(LANGUAGE);

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
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAddOperation();
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
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
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
            b.endAddOperation();
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

    @Test
    public void testInstrumentation() {
        BasicInterpreter root = parseNodeForCompilation(interpreterClass, "addTwoConstantsInstrumented", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginIncrementValue();
            b.beginAddOperation();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAddOperation();
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
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(20L);
            b.beginYield();
            b.emitLoadConstant(123L);
            b.endYield();
            b.endAddOperation();
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
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginIncrementValue();
            b.beginAddOperation();
            b.emitLoadConstant(20L);
            b.beginYield();
            b.emitLoadConstant(123L);
            b.endYield();
            b.endAddOperation();
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

    private static <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeForCompilation(Class<? extends BasicInterpreter> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BasicInterpreter result = parseNode(interpreterClass, false, rootName, builder);
        result.getBytecodeNode().setUncachedThreshold(0); // force interpreter to skip tier 0
        return result;
    }
}
