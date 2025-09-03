/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.test.InstrumentationTest.InstrumentationTestRootNode.InstrumentationDecrement;
import com.oracle.truffle.api.bytecode.test.InstrumentationTest.InstrumentationTestRootNode.PointInstrumentation1;
import com.oracle.truffle.api.bytecode.test.InstrumentationTest.InstrumentationTestRootNode.PointInstrumentation2;
import com.oracle.truffle.api.bytecode.test.InstrumentationTest.InstrumentationTestRootNode.PointInstrumentationRecursive1;
import com.oracle.truffle.api.bytecode.test.InstrumentationTest.InstrumentationTestRootNode.PointInstrumentationRecursive2;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.DirectCallNode;

public class InstrumentationTest extends AbstractInstructionTest {

    private static InstrumentationTestRootNode parse(BytecodeParser<InstrumentationTestRootNodeGen.Builder> parser) {
        BytecodeRootNodes<InstrumentationTestRootNode> nodes = InstrumentationTestRootNodeGen.create(BytecodeInstrumentationTestLanguage.REF.get(null), BytecodeConfig.WITH_SOURCE, parser);
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
            b.beginRoot();

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
            b.beginRoot();

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

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertEquals(3, d.events.size());
                assertEquals(PointInstrumentation1.class, d.events.get(0));
                assertEquals(PointInstrumentation1.class, d.events.get(1));
                assertEquals(PointInstrumentation2.class, d.events.get(2));
            });
            b.endRunAsserts();

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());
    }

    /*
     * Tests behavior when instruments are attached added in instruments.
     */
    @Test
    public void testPointInstrumentationRecursive() {
        InstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            b.emitPointInstrumentation1();
            b.beginEnableInstrumentation();
            b.emitLoadConstant(PointInstrumentationRecursive1.class);
            b.endEnableInstrumentation();
            b.emitPointInstrumentationRecursive1();
            b.emitPointInstrumentation1();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertEquals(2, d.events.size());
                assertEquals(PointInstrumentationRecursive1.class, d.events.get(0));
                assertEquals(PointInstrumentation1.class, d.events.get(1));
            });
            b.endRunAsserts();

            b.beginEnableInstrumentation();
            b.emitLoadConstant(PointInstrumentationRecursive2.class);
            b.endEnableInstrumentation();

            b.emitPointInstrumentationRecursive2();

            // this bytecode should be skipped
            b.emitPointInstrumentation2();

            // the second invocation triggers PointInstrumentation2
            b.emitPointInstrumentationRecursive2();

            // after transition we should continue here
            // we must remember which instrumentation instruction triggered the transition
            b.emitPointInstrumentation2();

            b.emitPointInstrumentationRecursive1();
            b.emitPointInstrumentation1();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertEquals(7, d.events.size());
                assertEquals(PointInstrumentationRecursive1.class, d.events.get(0));
                assertEquals(PointInstrumentation1.class, d.events.get(1));
                assertEquals(PointInstrumentationRecursive2.class, d.events.get(2));
                assertEquals(PointInstrumentationRecursive2.class, d.events.get(3));
                assertEquals(PointInstrumentation2.class, d.events.get(4));
                assertEquals(PointInstrumentationRecursive1.class, d.events.get(5));
                assertEquals(PointInstrumentation1.class, d.events.get(6));
            });
            b.endRunAsserts();

            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();

            b.endRoot();
        });
        assertEquals(42, node.getCallTarget().call());
    }

    /*
     * Verifies that boxing elimination does not crash when instrumentation is changed while
     * executing quickened instructions.
     */
    @Test
    public void testBoxingElimination() {
        InstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            BytecodeLocal l = b.createLocal();
            b.beginStoreLocal(l);
            b.emitLoadConstant(6);
            b.endStoreLocal();

            b.beginWhile();
            b.beginIsNot();
            b.emitLoadLocal(l);
            b.emitLoadConstant(0);
            b.endIsNot();

            b.beginBlock();

            b.beginStoreLocal(l);
            b.beginBlock();

            b.beginInstrumentationDecrement();
            // enabling the instrumentation with values on the stack is not super straight forward
            // the easiest way is to enable it as a side effect of a stackful operation.
            b.beginDecrementEnableInstrumentationIf4();
            b.emitLoadLocal(l);
            b.endDecrementEnableInstrumentationIf4();
            b.endInstrumentationDecrement();

            b.endBlock();

            b.endStoreLocal();

            b.endBlock();
            b.endWhile();

            b.beginRunAsserts();
            b.emitLoadConstant((Consumer<ThreadLocalData>) (d) -> {
                assertEquals(2, d.events.size());
                assertEquals(InstrumentationDecrement.class, d.events.get(0));
                assertEquals(3, d.operands.get(0));
                assertEquals(InstrumentationDecrement.class, d.events.get(1));
                assertEquals(1, d.operands.get(1));
            });
            b.endRunAsserts();

            b.beginReturn();
            b.emitLoadLocal(l);
            b.endReturn();
            b.endRoot();
        });

        node.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(0, node.getCallTarget().call());
    }

    @Test
    public void testCachedTagsPreservedInInstrumentation() {
        InstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            BytecodeLocal l = b.createLocal();
            b.beginStoreLocal(l);
            b.emitLoadConstant(6);
            b.endStoreLocal();

            b.beginEnableInstrumentation();
            b.emitLoadConstant(PointInstrumentation1.class);
            b.endEnableInstrumentation();

            b.beginReturn();
            b.emitLoadLocal(l);
            b.endReturn();
            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(6, node.getCallTarget().call());
        assertEquals("load.local$Int", node.getBytecodeNode().getInstructionsAsList().get(4).getName());
        assertEquals(6, node.getCallTarget().call());
        assertEquals("load.local$Int", node.getBytecodeNode().getInstructionsAsList().get(4).getName());
    }

    @Test
    public void testCachedTagsPreservedInInstrumentationWithSplitting() {
        InstrumentationTestRootNode node = parse((b) -> {
            b.beginRoot();

            BytecodeLocal l = b.createLocal();
            b.beginStoreLocal(l);
            b.emitLoadConstant(6);
            b.endStoreLocal();

            b.beginEnableInstrumentation();
            b.emitLoadConstant(PointInstrumentation1.class);
            b.endEnableInstrumentation();

            b.beginReturn();
            b.emitLoadLocal(l);
            b.endReturn();
            b.endRoot();
        });
        node.getBytecodeNode().setUncachedThreshold(0);
        DirectCallNode cn = DirectCallNode.create(node.getCallTarget());

        // cloning only supported in optimizing runtimes
        Assume.assumeTrue(cn.cloneCallTarget());

        RootCallTarget clone = (RootCallTarget) cn.getClonedCallTarget();
        InstrumentationTestRootNode clonedNode = (InstrumentationTestRootNode) clone.getRootNode();
        clonedNode.getBytecodeNode().setUncachedThreshold(0);

        assertEquals(6, clone.call());
        assertEquals("load.local$Int", clonedNode.getBytecodeNode().getInstructionsAsList().get(4).getName());
        assertEquals(6, clone.call());
        assertEquals("load.local$Int", clonedNode.getBytecodeNode().getInstructionsAsList().get(4).getName());
    }

    @GenerateBytecode(languageClass = BytecodeInstrumentationTestLanguage.class, //
                    enableQuickening = true, //
                    enableUncachedInterpreter = true,  //
                    boxingEliminationTypes = {int.class})
    public abstract static class InstrumentationTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected InstrumentationTestRootNode(BytecodeInstrumentationTestLanguage language,
                        FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class EnableInstrumentation {
            @Specialization
            @TruffleBoundary
            public static void doDefault(Class<?> instrumentationClass,
                            @Bind BytecodeLocation location) {

                location.getBytecodeNode().getBytecodeRootNode().getRootNodes().update(InstrumentationTestRootNodeGen.newConfigBuilder().addInstrumentation(instrumentationClass).build());

            }
        }

        @Operation
        static final class RunAsserts {

            @SuppressWarnings("unchecked")
            @Specialization
            @TruffleBoundary
            public static void doDefault(Consumer<?> consumer,
                            @Bind InstrumentationTestRootNode root) {
                ((Consumer<ThreadLocalData>) consumer).accept(root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get());
            }

        }

        @Instrumentation
        static final class PointInstrumentation1 {

            @Specialization
            public static void doDefault(@Bind InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(PointInstrumentation1.class, null);
            }

        }

        @Instrumentation
        static final class PointInstrumentation2 {

            @Specialization
            public static void doDefault(@Bind InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(PointInstrumentation2.class, null);
            }

        }

        @Instrumentation
        static final class PointInstrumentationRecursive1 {

            @Specialization
            public static void doDefault(@Bind InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(PointInstrumentationRecursive1.class, null);
                root.getRootNodes().update(InstrumentationTestRootNodeGen.newConfigBuilder().addInstrumentation(PointInstrumentation1.class).build());
            }

        }

        @Instrumentation
        static final class PointInstrumentationRecursive2 {

            @Specialization
            public static void doDefault(@Bind InstrumentationTestRootNode root) {
                ThreadLocalData tl = root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get();
                tl.add(PointInstrumentationRecursive2.class, null);

                if (tl.pointInstrumentationRecursive2Counter <= 0) {
                    root.getRootNodes().update(InstrumentationTestRootNodeGen.newConfigBuilder().addInstrumentation(PointInstrumentation2.class).build());
                }
                tl.pointInstrumentationRecursive2Counter--;
            }

        }

        @Instrumentation
        static final class InstrumentationOperandReturn {

            @Specialization
            public static Object doDefault(Object operand, @Bind InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(InstrumentationOperandReturn.class, operand);
                return operand;
            }
        }

        @Operation
        static final class DecrementEnableInstrumentationIf4 {

            @Specialization
            public static int doInt(int operand, @Bind InstrumentationTestRootNode root) {
                if (operand == 4) {
                    root.getRootNodes().update(InstrumentationTestRootNodeGen.newConfigBuilder().addInstrumentation(InstrumentationDecrement.class).build());
                }
                return operand - 1;
            }
        }

        @Operation
        static final class IsNot {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand != value;
            }
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

        @Instrumentation
        static final class InstrumentationDecrement {

            @Specialization
            public static int doInt(int operand, @Bind InstrumentationTestRootNode root) {
                root.getLanguage(BytecodeInstrumentationTestLanguage.class).threadLocal.get().add(InstrumentationDecrement.class, operand);
                return operand - 1;
            }
        }

    }

    static class ThreadLocalData {

        final List<Class<?>> events = new ArrayList<>();
        final List<Object> operands = new ArrayList<>();

        private int pointInstrumentationRecursive2Counter = 1;

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

    @GenerateBytecode(languageClass = BytecodeInstrumentationTestLanguage.class)
    public abstract static class InstrumentationErrorRootNode1 extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected InstrumentationErrorRootNode1(BytecodeInstrumentationTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Identity {

            @Specialization
            public static Object doDefault(Object operand) {
                return operand;
            }
        }

        // assert no error
        @Instrumentation
        static final class ValidInstrumentation1 {

            @Specialization
            public static void doInt() {
            }
        }

        // assert no error
        @Instrumentation
        static final class ValidInstrumentation2 {

            @Specialization
            public static Object doInt(Object arg) {
                return arg;
            }
        }

        @ExpectError("An @Instrumentation operation cannot have more than one dynamic operand. " +
                        "Instrumentations must have transparent stack effects. " + //
                        "Remove the additional operands to resolve this.")
        @Instrumentation
        static final class InvalidInstrumentation1 {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

        @ExpectError("An @Instrumentation operation cannot have a return value without also specifying a single dynamic operand. " + //
                        "Instrumentations must have transparent stack effects. " + //
                        "Use void as the return type or specify a single dynamic operand value to resolve this.")
        @Instrumentation
        static final class InvalidInstrumentation2 {

            @Specialization
            public static int doInt() {
                return 42;
            }
        }

        @ExpectError("An @Instrumentation operation cannot use @Variadic for its dynamic operand. " + //
                        "Instrumentations must have transparent stack effects. Remove the variadic annotation to resolve this.")
        @Instrumentation
        static final class InvalidInstrumentation3 {

            @Specialization
            public static int doInt(@SuppressWarnings("unused") @Variadic Object... args) {
                return 42;
            }
        }

        @ExpectError("An @Instrumentation operation cannot be void and also specify a dynamic operand. " + //
                        "Instrumentations must have transparent stack effects. " +
                        "Change the return type or remove the dynamic operand to resolve this.")
        @Instrumentation
        static final class InvalidInstrumentation4 {

            @Specialization
            public static void doInt(@SuppressWarnings("unused") Object arg) {
                return;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeInstrumentationTestLanguage.class, //
                    enableTagInstrumentation = true, //
                    enableRootBodyTagging = false, enableRootTagging = false)
    public abstract static class ManyInstrumentationsRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ManyInstrumentationsRootNode(BytecodeInstrumentationTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

        @Instrumentation
        static final class Instrumentation1 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation2 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation3 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation4 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation5 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation6 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation7 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation8 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation9 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation10 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation11 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation12 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation13 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation14 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation15 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation16 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation17 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation18 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation19 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation20 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation21 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation22 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation23 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation24 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation25 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation26 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation27 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation28 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation29 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation30 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation31 {
            @Specialization
            public static void doDefault() {
            }
        }

    }

    @ExpectError("Too many @Instrumentation annotated operations specified. %")
    @GenerateBytecode(languageClass = BytecodeInstrumentationTestLanguage.class, //
                    enableTagInstrumentation = true, //
                    enableRootBodyTagging = false, enableRootTagging = false)
    public abstract static class TooManyInstrumentationsRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected TooManyInstrumentationsRootNode(BytecodeInstrumentationTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Is {

            @Specialization
            public static boolean doInt(int operand, int value) {
                return operand == value;
            }
        }

        @Instrumentation
        static final class Instrumentation1 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation2 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation3 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation4 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation5 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation6 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation7 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation8 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation9 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation10 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation11 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation12 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation13 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation14 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation15 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation16 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation17 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation18 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation19 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation20 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation21 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation22 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation23 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation24 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation25 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation26 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation27 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation28 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation29 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation30 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation31 {
            @Specialization
            public static void doDefault() {
            }
        }

        @Instrumentation
        static final class Instrumentation32 {
            @Specialization
            public static void doDefault() {
            }
        }

    }

}
