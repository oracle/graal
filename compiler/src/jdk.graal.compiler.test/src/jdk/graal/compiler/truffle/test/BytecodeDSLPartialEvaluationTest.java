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

import java.util.List;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;

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
            b.beginAdd();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAdd();
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
            b.beginAdd();

            b.beginAdd();
            b.emitLoadConstant(40L);
            b.emitLoadConstant(22L);
            b.endAdd();

            b.emitLoadConstant(-20L);

            b.endAdd();

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
            b.beginAdd();
            b.emitLoadLocal(i);
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endStoreLocal();

            b.beginStoreLocal(sum);
            b.beginAdd();
            b.emitLoadLocal(sum);
            b.emitLoadLocal(i);
            b.endAdd();
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
            b.beginAdd();

            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();

            b.emitLoadConstant(1L);

            b.endAdd();
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
            b.beginAdd();
            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();
            b.emitLoadConstant(1L);
            b.endAdd();
            b.endThrowOperation();

            b.endTryCatch();

            b.beginReturn();
            b.beginAdd();

            b.beginReadExceptionOperation();
            b.emitLoadException();
            b.endReadExceptionOperation();

            b.emitLoadConstant(1L);

            b.endAdd();
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
            b.beginVariadicOperation();
            b.emitLoadConstant(3L);
            for (int i = 0; i < numVariadic; i++) {
                b.emitLoadNull();
            }
            b.endVariadicOperation();
            b.endReturn();

            b.endBlock();
            b.endRoot();
        });

        assertPartialEvalEquals(RootNode.createConstantNode(3L + numVariadic), root);
    }

    @Test
    public void testEmptyTagInstrumentation() {
        // make sure empty tag instrumentation does not cause deopts
        try (Context c = Context.create()) {
            c.enter();

            BasicInterpreter root = parseNodeForPE(interpreterClass, "testEmptyTagInstrumentation", b -> {
                b.beginRoot();

                b.beginTag(ExpressionTag.class);
                b.beginAdd();

                b.beginTag(ExpressionTag.class);
                b.emitLoadConstant(20L);
                b.endTag(ExpressionTag.class);

                b.beginTag(ExpressionTag.class);
                b.emitLoadConstant(22L);
                b.endTag(ExpressionTag.class);

                b.endAdd();
                b.endTag(ExpressionTag.class);

                b.endRoot();
            });

            root.getRootNodes().ensureComplete();

            assertPartialEvalEquals(RootNode.createConstantNode(42L), root);
        }
    }

    @Test
    public void testUnwindTagInstrumentation() {
        // make sure an unwound value optimizes correctly in place.
        try (Context c = Context.create()) {
            c.initialize(BytecodeDSLTestLanguage.ID);
            c.enter();

            String text = "return 20 + 22";
            Source s = Source.newBuilder("test", text, "testUnwindTagInstrumentation").build();
            BasicInterpreter root = parseNodeForPE(BytecodeDSLTestLanguage.REF.get(null), interpreterClass, "testUnwindTagInstrumentation", b -> {
                b.beginSource(s);
                b.beginSourceSection(0, text.length());
                b.beginRoot();

                b.beginSourceSection(text.indexOf("20 + 22"), 7);
                b.beginTag(ExpressionTag.class);
                b.beginAdd();

                b.beginSourceSection(text.indexOf("20"), 2);
                b.beginTag(ExpressionTag.class);
                b.emitLoadConstant(20L);
                b.endTag(ExpressionTag.class);
                b.endSourceSection();

                b.beginSourceSection(text.indexOf("22"), 2);
                b.beginTag(ExpressionTag.class);
                b.emitLoadConstant(22L);
                b.endTag(ExpressionTag.class);
                b.endSourceSection();

                b.endAdd();
                b.endTag(ExpressionTag.class);
                b.endSourceSection();

                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });

            ProxyInstrument.findEnv(c).getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder() //
                            .tagIs(ExpressionTag.class).indexIn(text.indexOf("20"), 2).build(),
                            new ExecutionEventListener() {

                                @Override
                                public void onEnter(EventContext context, VirtualFrame frame) {
                                }

                                @Override
                                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                                    if (context.getInstrumentedSourceSection().getCharLength() == 2) {
                                        throw context.createUnwind(21L);
                                    }
                                }

                                @Override
                                public Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
                                    // return info
                                    return info;
                                }

                                @Override
                                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                                }
                            });

            // if this is 42 the instrumentation did not trigger correctly
            Assert.assertEquals(43L, root.getCallTarget().call());

            assertPartialEvalEquals(RootNode.createConstantNode(43L), root);
        }
    }

    private static Supplier<Object> supplier(Object result) {
        return () -> result;
    }

    private static <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeForPE(Class<? extends BasicInterpreter> interpreterClass, String rootName, BytecodeParser<T> builder) {
        return parseNodeForPE(LANGUAGE, interpreterClass, rootName, builder);
    }

    private static <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeForPE(BytecodeDSLTestLanguage language, Class<? extends BasicInterpreter> interpreterClass, String rootName,
                    BytecodeParser<T> builder) {
        BasicInterpreter result = parseNode(interpreterClass, language, false, rootName, builder);
        result.getBytecodeNode().setUncachedThreshold(0); // force interpreter to skip tier 0
        return result;
    }

}
