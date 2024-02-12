/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.AbstractQuickeningTest;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.InstrumentationTest.InstrumentationTestRootNode.PointInstrumentation1;
import com.oracle.truffle.api.bytecode.test.basic_interpreter.InstrumentationTest.InstrumentationTestRootNode.PointInstrumentation2;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;

/**
 * Showcases how to implement the python set trace func feature.
 */
public class InstrumentationTest extends AbstractQuickeningTest {

    private static InstrumentationTestRootNode parse(BytecodeParser<InstrumentationTestRootNodeGen.Builder> parser) {
        BytecodeRootNodes<InstrumentationTestRootNode> nodes = InstrumentationTestRootNodeGen.create(BytecodeConfig.WITH_SOURCE, parser);
        return nodes.getNodes().get(nodes.getNodes().size() - 1);
    }

    Context context;

    @Before
    public void setup() {
        context = Context.create(BytecodeInstrumentationTestLanguage.ID);
        context.initialize(BytecodeInstrumentationTestLanguage.ID);
        context.enter();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testPointInstrumentation1() {
        InstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(BytecodeInstrumentationTestLanguage.REF.get(null));

            b.emitPointInstrumentation1();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertTrue(d.events.isEmpty());
            });
            b.endRunAsserts();

            b.beginEnableInstrumentation();
            b.emitLoadConstant(PointInstrumentation1.class);
            b.endEnableInstrumentation();

            b.emitPointInstrumentation1();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertEquals(1, d.events.size());
                assertEquals(PointInstrumentation1.class, d.events.get(0));
            });
            b.endRunAsserts();

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());
    }

    @Test
    public void testPointInstrumentation2() {
        InstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot(BytecodeInstrumentationTestLanguage.REF.get(null));

            b.emitPointInstrumentation1();
            b.emitPointInstrumentation2();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertTrue(d.events.isEmpty());
            });
            b.endRunAsserts();

            b.beginEnableInstrumentation();
            b.emitLoadConstant(PointInstrumentation1.class);
            b.endEnableInstrumentation();

            b.emitPointInstrumentation1();
            b.emitPointInstrumentation2();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertEquals(1, d.events.size());
                assertEquals(PointInstrumentation1.class, d.events.get(0));
            });
            b.endRunAsserts();

            b.beginEnableInstrumentation();
            b.emitLoadConstant(PointInstrumentation2.class);
            b.endEnableInstrumentation();

            b.emitPointInstrumentation1();
            b.emitPointInstrumentation2();
            b.emitPointInstrumentation1();
            b.emitPointInstrumentation2();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertEquals(5, d.events.size());
                assertEquals(PointInstrumentation1.class, d.events.get(0));
                assertEquals(PointInstrumentation1.class, d.events.get(1));
                assertEquals(PointInstrumentation2.class, d.events.get(2));
                assertEquals(PointInstrumentation1.class, d.events.get(3));
                assertEquals(PointInstrumentation2.class, d.events.get(4));
            });
            b.endRunAsserts();

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());
    }

    @GenerateBytecode(languageClass = BytecodeInstrumentationTestLanguage.class, //
                    enableYield = true, enableSerialization = true, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true, enableInstrumentation = true, //
                    boxingEliminationTypes = {long.class, int.class, boolean.class})
    public abstract static class InstrumentationTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected InstrumentationTestRootNode(TruffleLanguage<?> language,
                        FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class EnableInstrumentation {

            @Specialization
            @TruffleBoundary
            public static void doDefault(Class<?> instrumentationClass,
                            @Bind("$root") InstrumentationTestRootNode root) {
                root.getRootNodes().update(InstrumentationTestRootNodeGen.newConfigBuilder().addInstrumentations(instrumentationClass).build());
            }

        }

        @Operation
        static final class RunAsserts {

            @SuppressWarnings("unchecked")
            @Specialization
            @TruffleBoundary
            public static void doDefault(Consumer<?> consumer,
                            @Bind("$root") InstrumentationTestRootNode root) {
                ((Consumer<ThreadLocalData>) consumer).accept(root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get());
            }

        }

        @Instrumentation
        static final class PointInstrumentation1 {

            @Specialization
            public static void doDefault(@Bind("$root") InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(PointInstrumentation1.class, null);
            }

        }

        @Instrumentation
        static final class PointInstrumentation2 {

            @Specialization
            public static void doDefault(@Bind("$root") InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(PointInstrumentation2.class, null);
            }

        }

        @Instrumentation
        static final class InstrumentationOperandReturn {

            @Specialization
            public static Object doDefault(Object operand, @Bind("$root") InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(InstrumentationOperandReturn.class, operand);
                return operand;
            }
        }

    }

    static class ThreadLocalData {

        final List<Class<?>> events = new ArrayList<>();
        final List<Object> operands = new ArrayList<>();

        @TruffleBoundary
        void add(Class<?> c, Object operand) {
            events.add(c);
            operands.add(operand);
        }

    }

    @TruffleLanguage.Registration(id = BytecodeInstrumentationTestLanguage.ID)
    @ProvidedTags(StandardTags.ExpressionTag.class)
    public static class BytecodeInstrumentationTestLanguage extends TruffleLanguage<Object> {
        public static final String ID = "bytecode_BytecodeInstrumentationTestLanguage";

        final ContextThreadLocal<ThreadLocalData> threadLocal = this.locals.createContextThreadLocal((c, t) -> new ThreadLocalData());

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        static final LanguageReference<BytecodeInstrumentationTestLanguage> REF = LanguageReference.create(BytecodeInstrumentationTestLanguage.class);
    }

}
