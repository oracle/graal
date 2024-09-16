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
            b.beginRoot();

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
            b.beginRoot();

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
    public void testAddThreeConstantsWithConstantOperands() {
        // return 40 + 22 + - 20;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "addThreeConstantsWithConstantOperands", b -> {
            b.beginRoot();

            b.beginReturn();
            b.beginAddConstantOperationAtEnd();
            b.beginAddConstantOperation(40L);
            b.emitLoadConstant(22L);
            b.endAddConstantOperation();
            b.endAddConstantOperationAtEnd(-20L);

            b.endReturn();

            b.endRoot();
        });

        assertPartialEvalEquals(supplier(42L), root);
    }

    @Test
    public void testSum() {
        // @formatter:off
        // i = 0;
        // sum = 0;
        // while (i < 10) {
        //   i += 1;
        //   sum += i;
        // }
        // return sum
        // @formatter:on

        long endValue = 10L;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "sum", b -> {
            b.beginRoot();

            BytecodeLocal i = b.createLocal();
            BytecodeLocal sum = b.createLocal();

            b.beginStoreLocal(i);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(sum);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLess();
            b.emitLoadLocal(i);
            b.emitLoadConstant(endValue);
            b.endLess();

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
        // @formatter:off
        // try {
        //   throw 1;
        // } catch x {
        //   return x + 1;
        // }
        // return 3;
        // @formatter:on

        BasicInterpreter root = parseNodeForPE(interpreterClass, "sum", b -> {
            b.beginRoot();

            b.beginTryCatch();

            b.beginThrowOperation();
            b.emitLoadConstant(1L);
            b.endThrowOperation();

            b.beginReturn();
            b.beginAddOperation();

            b.beginReadExceptionOperation();
            b.emitLoadException();
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
    public void testTryCatch2() {
        // @formatter:off
        // try {
        //   try {
        //     throw 1;
        //   } catch x {
        //     throw x + 1
        //   }
        // } catch x {
        //   return x + 1;
        // }
        // return 42;
        // @formatter:on

        BasicInterpreter root = parseNodeForPE(interpreterClass, "sum", b -> {
            b.beginRoot();

            b.beginTryCatch();

            b.beginTryCatch();

            b.beginThrowOperation();
            b.emitLoadConstant(1L);
            b.endThrowOperation();

            b.beginThrowOperation();
            b.beginAddOperation();
            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endThrowOperation();

            b.endTryCatch();

            b.beginReturn();
            b.beginAddOperation();

            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();

            b.emitLoadConstant(1L);

            b.endAddOperation();
            b.endReturn();

            b.endTryCatch();

            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();

            b.endRoot();
        });

        assertPartialEvalEquals(supplier(3L), root);
    }

    @Test
    public void testConditionalTrue() {
        // return true ? 42 : 21;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "conditionalTrue", b -> {
            b.beginRoot();
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
        // return false ? 21 : 42;

        BasicInterpreter root = parseNodeForPE(interpreterClass, "conditionalFalse", b -> {
            b.beginRoot();

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
        // @formatter:off
        // earlyReturn(42)  // throws exception caught by intercept hook
        // return 123
        // @formatter:on
        BasicInterpreter root = parseNodeForPE(interpreterClass, "earlyReturn", b -> {
            b.beginRoot();
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

    @Test
    public void testVariadicLength() {
        // The length of a variadic argument should be PE constant.

        // Note: the variadic array length is not PE constant beyond 8 arguments.
        final int numVariadic = 8;
        BasicInterpreter root = parseNodeForPE(interpreterClass, "variadicLength", b -> {
            b.beginRoot();
            b.beginBlock();

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(3L);
            for (int i = 0; i < numVariadic; i++) {
                b.emitLoadNull();
            }
            b.endVeryComplexOperation();
            b.endReturn();

            b.endBlock();
            b.endRoot();
        });

        assertPartialEvalEquals(RootNode.createConstantNode(3L + numVariadic), root);
    }

    private static Supplier<Object> supplier(Object result) {
        return () -> result;
    }

    private static <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeForPE(Class<? extends BasicInterpreter> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BasicInterpreter result = parseNode(interpreterClass, LANGUAGE, false, rootName, builder);
        result.getBytecodeNode().setUncachedThreshold(0); // force interpreter to skip tier 0
        return result;
    }

}
