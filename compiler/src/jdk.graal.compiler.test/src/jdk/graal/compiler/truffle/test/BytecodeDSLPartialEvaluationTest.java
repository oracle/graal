/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.AbstractBasicInterpreterTest;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterBuilder;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Parameterized.class)
public class BytecodeDSLPartialEvaluationTest extends PartialEvaluationTest {

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends BasicInterpreter>> getInterpreterClasses() {
        return AbstractBasicInterpreterTest.allInterpreters();
    }

    @Parameter(0) public Class<? extends BasicInterpreter> interpreterClass;

    @Test
    public void testAddTwoConstants() {
        // return 20 + 22;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "addTwoConstants", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        assertPartialEvalEquals(supplier(42L), root);
    }

    @Test
    public void testAddThreeConstants() {
        // return 40 + 22 + - 20;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "addThreeConstants", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();

            b.beginAddOperation();
            b.emitLoadConstant(40L);
            b.emitLoadConstant(22L);
            b.endAddOperation();

            b.emitLoadConstant(-20L);

            b.endAddOperation();

            b.endReturn();

            b.endRoot();
        });

        assertPartialEvalEquals(supplier(42L), root);
    }

    @Test
    public void testSum() {
        // i = 0;
        // sum = 0;
        // while (i < 10) {
        // i += 1;
        // sum += i;
        // }
        // return sum

        long endValue = 10L;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "sum", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal i = b.createLocal();
            BytecodeLocal sum = b.createLocal();

            b.beginStoreLocal(i);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(sum);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLessThanOperation();
            b.emitLoadLocal(i);
            b.emitLoadConstant(endValue);
            b.endLessThanOperation();

            b.beginBlock();

            b.beginStoreLocal(i);
            b.beginAddOperation();
            b.emitLoadLocal(i);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginStoreLocal(sum);
            b.beginAddOperation();
            b.emitLoadLocal(sum);
            b.emitLoadLocal(i);
            b.endAddOperation();
            b.endStoreLocal();

            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(sum);
            b.endReturn();

            b.endRoot();
        });

        assertPartialEvalEquals(supplier(endValue * (endValue + 1) / 2), root);
    }

    @Test
    public void testTryCatch() {
        // try {
        // throw 1;
        // } catch x {
        // return x + 1;
        // }
        // return 3;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "sum", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal ex = b.createLocal();

            b.beginTryCatch(ex);

            b.beginThrowOperation();
            b.emitLoadConstant(1L);
            b.endThrowOperation();

            b.beginReturn();
            b.beginAddOperation();

            b.beginReadExceptionOperation();
            b.emitLoadLocal(ex);
            b.endReadExceptionOperation();

            b.emitLoadConstant(1L);

            b.endAddOperation();
            b.endReturn();

            b.endTryCatch();

            b.beginReturn();
            b.emitLoadConstant(3L);
            b.endReturn();

            b.endRoot();
        });

        assertPartialEvalEquals(supplier(2L), root);
    }

    @Test
    public void testConditionalTrue() {
        // return 20 + 22;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "conditionalTrue", b -> {
            b.beginRoot(LANGUAGE);
            b.beginReturn();
            b.beginConditional();
            b.emitLoadConstant(Boolean.TRUE);
            b.emitLoadConstant(42L);
            b.emitLoadConstant(21L);
            b.endConditional();
            b.endReturn();
            b.endRoot();
        });

        // Conditional uses quickening for BE. Call once to trigger quickening.
        Assert.assertEquals(42L, root.getCallTarget().call());

        assertPartialEvalEquals(RootNode.createConstantNode(42L), root);
    }

    @Test
    public void testConditionalFalse() {
        // return 20 + 22;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "conditionalFalse", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginConditional();
            b.emitLoadConstant(Boolean.FALSE);
            b.emitLoadConstant(21L);
            b.emitLoadConstant(42L);
            b.endConditional();
            b.endReturn();

            b.endRoot();
        });

        // Conditional uses quickening for BE. Call once to trigger quickening.
        Assert.assertEquals(42L, root.getCallTarget().call());

        assertPartialEvalEquals(RootNode.createConstantNode(42L), root);
    }

    @Test
    public void testEarlyReturn() {
        BasicInterpreter root = parseNodeForPE(interpreterClass, "earlyReturn", b -> {
            b.beginRoot(LANGUAGE);
            b.beginBlock();

            b.beginEarlyReturn();
            b.emitLoadConstant(42L);
            b.endEarlyReturn();

            b.beginReturn();
            b.emitLoadConstant(123L);
            b.endReturn();

            b.endBlock();
            b.endRoot();
        });

        assertPartialEvalEquals(RootNode.createConstantNode(42L), root);
    }

    private static Supplier<Object> supplier(Object result) {
        return () -> result;
    }

    private static <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeForPE(Class<? extends BasicInterpreter> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BasicInterpreter result = parseNode(interpreterClass, false, rootName, builder);
        result.getBytecodeNode().setUncachedThreshold(0); // force interpreter to skip tier 0
        return result;
    }

}
