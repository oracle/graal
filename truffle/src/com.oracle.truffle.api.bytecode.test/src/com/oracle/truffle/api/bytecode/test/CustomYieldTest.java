/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.Yield;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.ErrorLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

public class CustomYieldTest {

    /**
     * Tests basic usage of a custom yield.
     */
    @Test
    public void testBasic() {
        BytecodeRootNodes<CustomYieldTestRootNode> nodes = CustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCustomYield();
            b.emitLoadArgument(0);
            b.endCustomYield();
            b.endReturn();
            b.endRoot();
        });
        CustomYieldTestRootNode root = nodes.getNode(0);

        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(42);
        assertEquals(42, result.value());
        assertEquals(123, result.continueWith(123));
    }

    /**
     * Tests that a custom yield can be serialized/deserialized.
     */
    @Test
    public void testSerialization() throws IOException {
        BytecodeRootNodes<CustomYieldTestRootNode> nodes = CustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCustomYield();
            b.emitLoadArgument(0);
            b.endCustomYield();
            b.endReturn();
            b.endRoot();
        });

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        nodes.serialize(new DataOutputStream(output), SERIALIZER);
        Supplier<DataInput> input = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(output.toByteArray()));
        BytecodeRootNodes<CustomYieldTestRootNode> deserialized = CustomYieldTestRootNodeGen.deserialize(null, BytecodeConfig.DEFAULT, input, DESERIALIZER);
        CustomYieldTestRootNode root = deserialized.getNode(0);

        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(42);
        assertEquals(42, result.value());
        assertEquals(123, result.continueWith(123));
    }

    /**
     * Tests that stack state from ongoing operations is preserved and used in the resumed frame.
     */
    @Test
    public void testResumeOngoingOperation() {
        BytecodeRootNodes<CustomYieldTestRootNode> nodes = CustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.beginCustomYield();
            b.emitLoadArgument(0);
            b.endCustomYield();
            b.endAdd();
            b.endReturn();
            b.endRoot();
        });
        CustomYieldTestRootNode root = nodes.getNode(0);

        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(42);
        assertEquals(42, result.value());
        assertEquals(63, result.continueWith(21));
    }

    /**
     * A simple root node with a custom yield (and no built-in yield).
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true)
    public abstract static class CustomYieldTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected CustomYieldTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Yield(javadoc = "A simple custom yield operation.")
        public static final class CustomYield {
            @Specialization
            public static Object doYield(Object result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return new CustomYieldResult(root, frame, result);
            }
        }

        @Operation
        public static final class Add {
            @Specialization
            public static int doAdd(int x, int y) {
                return x + y;
            }
        }
    }

    /**
     * Tests that custom yields can be used in uncached.
     */
    @Test
    public void testUncached() {
        BytecodeRootNodes<CustomYieldUncachedRootNode> nodes = CustomYieldUncachedRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCustomYield();
            b.emitLoadArgument(0);
            b.endCustomYield();
            b.endReturn();
            b.endRoot();
        });
        CustomYieldUncachedRootNode root = nodes.getNode(0);

        final int uncachedThreshold = 10;
        root.getBytecodeNode().setUncachedThreshold(uncachedThreshold);
        for (int i = 0; i < uncachedThreshold / 2; i++) {
            assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
            CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(42);
            assertEquals(42, result.value());
            assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
            assertEquals(123, result.continueWith(123));
        }
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(42);
        assertEquals(42, result.value());
        assertEquals(BytecodeTier.CACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.continueWith(123));
    }

    /**
     * Tests that stack state from ongoing operations is preserved and used in the resumed frame in
     * uncached.
     */
    @Test
    public void testUncachedResumeOngoingOperation() {
        BytecodeRootNodes<CustomYieldUncachedRootNode> nodes = CustomYieldUncachedRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginAdd();
            b.emitLoadArgument(0);
            b.beginCustomYield();
            b.emitLoadArgument(0);
            b.endCustomYield();
            b.endAdd();
            b.endReturn();
            b.endRoot();
        });
        CustomYieldUncachedRootNode root = nodes.getNode(0);

        root.getBytecodeNode().setUncachedThreshold(10);
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(42);
        assertEquals(42, result.value());
        assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(63, result.continueWith(21));
    }

    /**
     * Tests that forceCached works as expected.
     */
    @Test
    public void testForceCached() {
        BytecodeRootNodes<CustomYieldUncachedRootNode> nodes = CustomYieldUncachedRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCachedOnlyCustomYield();
            b.beginCustomYield();
            b.emitLoadArgument(0);
            b.endCustomYield();
            b.endCachedOnlyCustomYield();
            b.endReturn();
            b.endRoot();
        });
        CustomYieldUncachedRootNode root = nodes.getNode(0);

        root.getBytecodeNode().setUncachedThreshold(10);
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());

        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(42);
        assertEquals(42, result.value());
        // The inner yield does not force the transition to cached.
        assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());

        result = (CustomYieldResult) result.continueWith(123);
        // The outer yield forces the transition to cached.
        assertEquals(BytecodeTier.CACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.value());

        assertEquals(456, result.continueWith(456));
    }

    /**
     * A root node with custom yield and uncached support.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true)
    public abstract static class CustomYieldUncachedRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected CustomYieldUncachedRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Yield
        public static final class CustomYield {
            @Specialization
            public static Object doYield(Object result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return new CustomYieldResult(root, frame, result);
            }
        }

        @SuppressWarnings("truffle-force-cached")
        @Yield(forceCached = true)
        public static final class CachedOnlyCustomYield {
            @Specialization
            public static Object doYield(Object result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return new CustomYieldResult(root, frame, result);
            }
        }

        @Operation
        public static final class Add {
            @Specialization
            public static int doAdd(int x, int y) {
                return x + y;
            }
        }
    }

    /**
     * Tests that custom yields can have multiple specializations (including fallbacks), in both
     * cached and uncached.
     */
    @Test
    public void testMultiSpecializationCustomYield() {
        BytecodeRootNodes<SpecializingCustomYieldTestRootNode> nodes = SpecializingCustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginDoubleYield();
            b.emitLoadArgument(0);
            b.endDoubleYield();
            b.endReturn();
            b.endRoot();
        });
        SpecializingCustomYieldTestRootNode root = nodes.getNode(0);

        root.getBytecodeNode().setUncachedThreshold(6);

        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(21);
        assertEquals(42, result.value());
        assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.continueWith(123));

        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        result = (CustomYieldResult) root.getCallTarget().call("hello");
        assertEquals("hellohello", result.value());
        assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.continueWith(123));

        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        result = (CustomYieldResult) root.getCallTarget().call(new int[0]);
        assertEquals(null, result.value());
        assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.continueWith(123));

        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
        result = (CustomYieldResult) root.getCallTarget().call(21);
        assertEquals(42, result.value());
        assertEquals(BytecodeTier.CACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.continueWith(123));

        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
        result = (CustomYieldResult) root.getCallTarget().call("hello");
        assertEquals("hellohello", result.value());
        assertEquals(BytecodeTier.CACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.continueWith(123));

        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
        result = (CustomYieldResult) root.getCallTarget().call(new int[0]);
        assertEquals(null, result.value());
        assertEquals(BytecodeTier.CACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
        assertEquals(123, result.continueWith(123));
    }

    /**
     * An uncachable root node with a multi-specialization custom yield.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true)
    public abstract static class SpecializingCustomYieldTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected SpecializingCustomYieldTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Yield
        public static final class DoubleYield {
            @Specialization
            public static Object doInt(int result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return new CustomYieldResult(root, frame, result * 2);
            }

            @Specialization
            public static Object doString(String result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return new CustomYieldResult(root, frame, result + result);
            }

            @Fallback
            public static Object doFallback(@SuppressWarnings("unused") Object result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return new CustomYieldResult(root, frame, null);
            }
        }
    }

    /**
     * Tests that multiple different yields -- including the built-in yield -- work as expected.
     */
    @Test
    public void testMultipleYields() {
        ComplexCustomYieldTestRootNode root = ComplexCustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginYield(); // C
            b.beginAddConstantsYield(1); // B
            b.emitNoResultYield(); // A
            b.endAddConstantsYield(10);
            b.endYield();
            b.endReturn();
            b.endRoot();
        }).getNode(0);

        // A: no result
        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(2);
        assertEquals("no result", result.value());
        // B: 1 + 10 + 100
        ContinuationResult contResult = (ContinuationResult) result.continueWith(100);
        assertEquals(111, contResult.getResult());
        // C: 77
        contResult = (ContinuationResult) contResult.continueWith(77);
        assertEquals(77, contResult.getResult());
        // return
        assertEquals(39, contResult.continueWith(39));
    }

    /**
     * Tests that multiple different yields work with serialization.
     */
    @Test
    public void testMultipleYieldsSerialization() throws IOException {
        BytecodeRootNodes<ComplexCustomYieldTestRootNode> nodes = ComplexCustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginYield(); // C
            b.beginAddConstantsYield(1); // B
            b.emitNoResultYield(); // A
            b.endAddConstantsYield(10);
            b.endYield();
            b.endReturn();
            b.endRoot();
        });

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        nodes.serialize(new DataOutputStream(output), SERIALIZER);
        Supplier<DataInput> input = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(output.toByteArray()));
        BytecodeRootNodes<ComplexCustomYieldTestRootNode> deserialized = ComplexCustomYieldTestRootNodeGen.deserialize(null, BytecodeConfig.DEFAULT, input, DESERIALIZER);
        ComplexCustomYieldTestRootNode root = deserialized.getNode(0);

        // A: no result
        CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(2);
        assertEquals("no result", result.value());
        // B: 1 + 10 + 100
        ContinuationResult contResult = (ContinuationResult) result.continueWith(100);
        assertEquals(111, contResult.getResult());
        // C: 77
        contResult = (ContinuationResult) contResult.continueWith(77);
        assertEquals(77, contResult.getResult());
        // return
        assertEquals(39, contResult.continueWith(39));
    }

    /**
     * Tests that stack state from ongoing operations is preserved and used in the resumed frame.
     */
    @Test
    public void testMultipleYieldsResumeOngoingOperation() {
        BytecodeRootNodes<ComplexCustomYieldTestRootNode> nodes = ComplexCustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();

            BytecodeLocal local = b.createLocal();
            b.beginStoreLocal(local);
            b.emitLoadConstant(42);
            b.endStoreLocal();

            b.beginReturn();
            b.beginAddMany();

            b.emitLoadArgument(0);

            b.emitNoResultYield(); // A

            b.beginYield(); // B
            b.emitLoadLocal(local);
            b.endYield();

            b.beginAddConstantsYield(10); // C
            b.emitLoadArgument(0);
            b.endAddConstantsYield(20);

            b.endAddMany();
            b.endReturn();
            b.endRoot();
        });
        ComplexCustomYieldTestRootNode root = nodes.getNode(0);

        // Test behaviour for uncached and cached (and a transition in between iterations).
        root.getBytecodeNode().setUncachedThreshold(6);
        for (int i = 0; i < 3; i++) {
            // A: no result
            CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call(7);
            assertEquals("no result", result.value());
            // B: 42
            ContinuationResult cont = (ContinuationResult) result.continueWith(70);
            assertEquals(42, cont.getResult());
            // C: 10 + 7 + 20
            cont = (ContinuationResult) cont.continueWith(700);
            assertEquals(37, cont.getResult());
            // return: 7 + 70 + 700 + 7000
            assertEquals(7777, cont.continueWith(7000));
        }
    }

    @Test
    public void testBoxingElimination() {
        BytecodeRootNodes<ComplexCustomYieldTestRootNode> nodes = ComplexCustomYieldTestRootNodeGen.create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginBlock();
            b.beginAddConstantsYield(30);
            b.emitLoadArgument(0);
            b.endAddConstantsYield(10);
            b.endBlock();
            b.endReturn();
            b.endRoot();
        });
        ComplexCustomYieldTestRootNode root = nodes.getNode(0);
        root.getBytecodeNode().setUncachedThreshold(0);

        AbstractInstructionTest.assertInstructions(root,
                        "load.argument",
                        "c.AddConstantsYield",
                        "return");
        ContinuationResult cont = (ContinuationResult) root.getCallTarget().call(2);
        assertEquals(42, cont.getResult());
        AbstractInstructionTest.assertInstructions(root,
                        "load.argument$Int",
                        "c.AddConstantsYield$Int",
                        "return");
        assertEquals(123, cont.continueWith(123));

        cont = (ContinuationResult) root.getCallTarget().call("foo");
        assertEquals("30foo10", cont.getResult());
        AbstractInstructionTest.assertInstructions(root,
                        "load.argument",
                        "c.AddConstantsYield",
                        "return");
        assertEquals(123, cont.continueWith(123));
    }

    /**
     * Tests that tag instrumentation works as expected.
     */
    @Test
    public void testTagInstrumentation() {
        runInstrumentationTest((context, instrumenter) -> {
            BytecodeRootNodes<ComplexCustomYieldTestRootNode> nodes = ComplexCustomYieldTestRootNodeGen.create(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.beginReturn();
                b.beginTag(StatementTag.class);
                b.emitNoResultYield();
                b.endTag(StatementTag.class);
                b.endReturn();
                b.endRoot();
            });
            ComplexCustomYieldTestRootNode root = nodes.getNode(0);

            root.getBytecodeNode().setUncachedThreshold(4);
            assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
            CustomYieldResult result = (CustomYieldResult) root.getCallTarget().call();
            assertEquals("no result", result.value());
            assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
            assertEquals(123, result.continueWith(123));

            List<Object> yieldValues = new ArrayList<>();
            AtomicInteger resumeCount = new AtomicInteger();
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), createFactory(yieldValues, resumeCount));

            assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
            result = (CustomYieldResult) root.getCallTarget().call();
            assertEquals("no result", result.value());
            assertEquals(BytecodeTier.UNCACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
            assertEquals(123, result.continueWith(123));

            assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
            result = (CustomYieldResult) root.getCallTarget().call(19);
            assertEquals("no result", result.value());
            assertEquals(BytecodeTier.CACHED, result.root().getSourceRootNode().getBytecodeNode().getTier());
            assertEquals(456, result.continueWith(456));

            assertEquals(Arrays.asList(null, null), yieldValues);
            assertEquals(2, resumeCount.get());
        });
    }

    /**
     * Tests that implicit tag instrumentation works as expected.
     */
    @Test
    public void testImplicitTagInstrumentation() {
        runInstrumentationTest((context, instrumenter) -> {
            BytecodeRootNodes<ComplexCustomYieldTestRootNode> nodes = ComplexCustomYieldTestRootNodeGen.create(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.beginReturn();
                b.beginAddConstantsYield(1);
                b.emitLoadArgument(0);
                b.endAddConstantsYield(1);
                b.endReturn();
                b.endRoot();
            });
            ComplexCustomYieldTestRootNode root = nodes.getNode(0);

            root.getBytecodeNode().setUncachedThreshold(4);
            assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
            ContinuationResult cont = (ContinuationResult) root.getCallTarget().call(40);
            assertEquals(42, cont.getResult());
            assertEquals(BytecodeTier.UNCACHED, cont.getContinuationRootNode().getSourceRootNode().getBytecodeNode().getTier());
            assertEquals(123, cont.continueWith(123));

            List<Object> yieldValues = new ArrayList<>();
            AtomicInteger resumeCount = new AtomicInteger();
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), createFactory(yieldValues, resumeCount));

            assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
            cont = (ContinuationResult) root.getCallTarget().call(40);
            assertEquals(42, cont.getResult());
            assertEquals(BytecodeTier.UNCACHED, cont.getContinuationRootNode().getSourceRootNode().getBytecodeNode().getTier());
            assertEquals(123, cont.continueWith(123));

            assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
            cont = (ContinuationResult) root.getCallTarget().call(19);
            assertEquals(21, cont.getResult());
            assertEquals(BytecodeTier.CACHED, cont.getContinuationRootNode().getSourceRootNode().getBytecodeNode().getTier());
            assertEquals(456, cont.continueWith(456));

            assertEquals(List.of(40, 19), yieldValues);
            assertEquals(2, resumeCount.get());
        });
    }

    @Test
    public void testYieldQuickeningRegressionTest() {
        /*
         * Regression test for a quickening bug. The yield's childBci calculation did not account
         * for tag.resume instructions, and under the right circumstances, the parent operation
         * would "quicken" an instruction operand, leading to unexpected results.
         */
        runInstrumentationTest((context, instrumenter) -> {
            BytecodeRootNodes<ComplexCustomYieldTestRootNode> nodes = ComplexCustomYieldTestRootNodeGen.create(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.emitLoadConstant(42);
                b.beginReturn();
                b.beginAddConstantsYield(1);
                b.emitLoadArgument(0);
                b.endAddConstantsYield(1);
                b.endReturn();
                b.endRoot();
            });
            ComplexCustomYieldTestRootNode root = nodes.getNode(0);
            root.getBytecodeNode().setUncachedThreshold(0);
            List<Object> yieldValues = new ArrayList<>();
            AtomicInteger resumeCount = new AtomicInteger();
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), createFactory(yieldValues, resumeCount));

            ContinuationResult cont = (ContinuationResult) root.getCallTarget().call(0);
            assertEquals(2, cont.getResult());
            assertEquals(123, cont.continueWith(123));

            cont = (ContinuationResult) root.getCallTarget().call(0);
            assertEquals(2, cont.getResult());
            assertEquals(123, cont.continueWith(123));
        });
    }

    private static void runInstrumentationTest(BiConsumer<Context, Instrumenter> test) {
        Context context = Context.create(BytecodeDSLTestLanguage.ID);
        try {
            context.initialize(BytecodeDSLTestLanguage.ID);
            context.enter();
            Instrumenter instrumenter = context.getEngine().getInstruments().get(CustomYieldTestInstrument.ID).lookup(Instrumenter.class);
            // run the test
            test.accept(context, instrumenter);
        } finally {
            context.close();
        }
    }

    private static ExecutionEventNodeFactory createFactory(List<Object> yieldValues, AtomicInteger resumeCount) {
        return (e) -> {
            return new ExecutionEventNode() {
                @Override
                public void onYield(VirtualFrame frame, Object value) {
                    yieldValues.add(value);
                }

                @Override
                protected void onResume(VirtualFrame frame) {
                    resumeCount.getAndIncrement();
                }
            };
        };
    }

    /**
     * A root node with most Bytecode DSL features enabled (uncached, tag instrumentation,
     * serialization, BE).
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableUncachedInterpreter = true, enableTagInstrumentation = true, enableSerialization = true, boxingEliminationTypes = {
                    int.class})
    public abstract static class ComplexCustomYieldTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected ComplexCustomYieldTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class AddMany {
            @Specialization
            static int add(int w, int x, int y, int z) {
                return w + x + y + z;
            }
        }

        @Yield
        public static final class NoResultYield {
            @Specialization
            public static Object doNoArgs(@Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return new CustomYieldResult(root, frame, "no result");
            }
        }

        @Yield(tags = {StatementTag.class})
        @ConstantOperand(type = int.class, name = "addend1")
        @ConstantOperand(type = int.class, name = "addend2", specifyAtEnd = true)
        public static final class AddConstantsYield {
            @Specialization
            public static Object doInt(int addend1, int result, int addend2, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return ContinuationResult.create(root, frame, addend1 + result + addend2);
            }

            @Specialization
            public static Object doString(int addend1, String result, int addend2, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return ContinuationResult.create(root, frame, addend1 + result + addend2);
            }
        }
    }

    @TruffleInstrument.Registration(id = CustomYieldTestInstrument.ID, services = Instrumenter.class)
    public static class CustomYieldTestInstrument extends TruffleInstrument {

        public static final String ID = "CustomYieldTestInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
        }
    }

    record CustomYieldResult(ContinuationRootNode root, MaterializedFrame frame, Object value) implements TruffleObject {
        public Object continueWith(Object resumeValue) {
            return root.getCallTarget().call(frame, resumeValue);
        }
    }

    static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof Integer i) {
                buffer.writeInt(i.intValue());
            } else {
                throw new IllegalArgumentException("Unsupported constant " + object);
            }
        }
    };

    static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            return buffer.readInt();
        }
    };

    @Test
    public void testCustomYieldWithBEableReturnType() {
        for (var instructionsClass : CustomYieldWithBEableReturnTypeTestGen.class.getDeclaredClasses()) {
            if (!instructionsClass.getSimpleName().equals("Instructions")) {
                continue;
            }

            String baseName = "CUSTOM_YIELD_WITH_BEABLE_RETURN_TYPE";
            List<String> yieldInstructions = Stream.of(instructionsClass.getDeclaredFields()).map(Field::getName).filter(name -> name.contains(baseName)).toList();
            if (yieldInstructions.size() != 1) {
                fail("Expected one instruction for custom yield, but %d were found (%s). Was a return-type BE variant generated?".formatted(yieldInstructions.size(), yieldInstructions));
            }
            return;
        }
        fail("Could not find Instructions class");
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class, boxingEliminationTypes = {int.class})
    public abstract static class CustomYieldWithBEableReturnTypeTest extends RootNode implements BytecodeRootNode {

        protected CustomYieldWithBEableReturnTypeTest(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Yield
        public static final class CustomYieldWithBEableReturnType {
            @Specialization
            public static int doYield() {
                return 42;
            }
        }

    }

    @SuppressWarnings("unused")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract static class InvalidYieldTest extends RootNode implements BytecodeRootNode {
        protected InvalidYieldTest(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            static int add(int x, int y) {
                return x + y;
            }
        }

        @ExpectError("A @Yield cannot be void. It must return a value, which becomes the result yielded to the caller.")
        @Yield
        public static final class CustomYieldBadReturnType {
            @Specialization
            public static void doYield(Object result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
            }
        }

        @ExpectError("A @Yield must take zero or one dynamic operands.")
        @Yield
        public static final class CustomYieldTooManyOperands {
            @Specialization
            public static Object doInt(int arg1, int arg2, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return null;
            }
        }

        @ExpectError("@Variadic can only be used on @Operation classes.")
        @Variadic
        @Yield
        public static final class CustomVariadicYield {
            @Specialization
            public static Object[] doYield(Object[] result) {
                return result;
            }
        }

        @Operation
        public static final class OperationBadBind {
            @Specialization
            public static void doYield(
                            @ExpectError("This expression binds a continuation root node, which can only be bound in a @Yield. " +
                                            "Remove this bind expression or redefine the operation as a @Yield to resolve this error.")//
                            @Bind ContinuationRootNode root) {
            }
        }

    }

}
