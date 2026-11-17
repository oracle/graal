/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOError;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.bytecode.BytecodeParser;
import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.InstructionTracer;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/** Tests payload selection, malformed serialization inputs, and subsequent builder reparsing. */
public class SerializationTest extends AbstractBasicInterpreterTest {

    private static final Source SOURCE = Source.newBuilder(BytecodeDSLTestLanguage.ID, "return 42;", "serialization.test").build();

    public SerializationTest(TestRun run) {
        super(run);
    }

    @Parameters(name = "{0}")
    public static List<TestRun> getParameters() {
        // These tests explicitly control serialization and tracing rather than using createNodes.
        return allVariants().stream().map(bytecode -> new TestRun(bytecode, false, false, false)).toList();
    }

    @Test
    public void testInvalidExecutionBytecodeMagic() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        byte[] bytes = payloads.executionBytecode().clone();
        bytes[0] ^= 1;
        assertMalformedExecutionBytecode("Invalid magic", payloads, bytes);
    }

    @Test
    public void testUnsupportedExecutionBytecodeVersion() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        byte[] bytes = payloads.executionBytecode().clone();
        ByteBuffer.wrap(bytes).putShort(Integer.BYTES, (short) 0);
        assertMalformedExecutionBytecode("Unsupported version", payloads, bytes);
    }

    @Test
    public void testTruncatedExecutionBytecodePayload() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        assertMalformedExecutionBytecode("Truncated execution bytecode payload", payloads, Arrays.copyOf(payloads.executionBytecode(), payloads.executionBytecode().length - 1));
    }

    @Test
    public void testInvalidBuilderMagic() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        byte[] bytes = payloads.builder().clone();
        bytes[0] ^= 1;
        assertMalformedBuilderBytecode("Invalid magic", bytes);
    }

    @Test
    public void testUnsupportedBuilderVersion() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        byte[] bytes = payloads.builder().clone();
        ByteBuffer.wrap(bytes).putShort(Integer.BYTES, (short) 0);
        assertMalformedBuilderBytecode("Unsupported version", bytes);
    }

    @Test
    public void testTruncatedBuilderPayload() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        assertMalformedBuilderBytecode("Truncated builder payload", Arrays.copyOf(payloads.builder(), payloads.builder().length - 1));
    }

    @Test
    public void testSwappedPayloads() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        assertMalformedExecutionBytecode("Builder input used as execution bytecode input", payloads, payloads.builder());
        assertMalformedBuilderBytecode("Execution bytecode input used as builder input", payloads.executionBytecode());
    }

    @Test
    public void testConfigMismatch() throws IOException {
        for (BytecodeConfig executionBytecodeConfig : List.of(BytecodeConfig.DEFAULT, BytecodeConfig.WITH_SOURCE)) {
            BytecodeConfig requestedConfig = executionBytecodeConfig.matches(BytecodeConfig.DEFAULT) ? BytecodeConfig.WITH_SOURCE : BytecodeConfig.DEFAULT;
            Payloads payloads = serialize(executionBytecodeConfig);
            TestInput builderBytecodeInput = new TestInput(payloads.builder(), 0);
            TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
            assertThrowsWithMessage("Execution bytecode configuration does not match the requested configuration.", IOException.class,
                            () -> run.bytecode().deserializeExecutionBytecode(LANGUAGE, requestedConfig, builderBytecodeInput, executionBytecodeInput, DESERIALIZER));
            assertEquals(0, builderBytecodeInput.invocations);
            assertEquals(1, executionBytecodeInput.invocations);
        }
    }

    @Test
    public void testMalformedBuilderWithoutExecutionBytecode() {
        TestInput builderBytecodeInput = new TestInput(new byte[0], 1);
        AtomicInteger requests = new AtomicInteger();
        assertThrows(EOFException.class, () -> run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.DEFAULT, builderBytecodeInput, config -> {
            requests.incrementAndGet();
            return null;
        }, DESERIALIZER));
        assertEquals(1, builderBytecodeInput.invocations);
        assertEquals(1, requests.get());
    }

    @Test
    public void testMatchingExecutionBytecode() throws IOException {
        for (BytecodeConfig config : List.of(BytecodeConfig.DEFAULT, BytecodeConfig.WITH_SOURCE, run.bytecode().newConfigBuilder().addSource().build())) {
            Payloads payloads = serialize(config);
            TestInput builderBytecodeInput = new TestInput(new byte[0], 0);
            TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
            BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(LANGUAGE, config, builderBytecodeInput, requestedConfig -> {
                assertTrue(config.matches(requestedConfig));
                return executionBytecodeInput.get();
            }, DESERIALIZER);
            assertEquals(42L, nodes.getNode(0).getCallTarget().call());
            assertEquals(0, builderBytecodeInput.invocations);
            assertEquals(1, executionBytecodeInput.invocations);
        }
    }

    @Test
    public void testExecutionBytecodeInputSupportsMultipleConfigs() throws IOException {
        List<Payloads> payloads = List.of(serialize(BytecodeConfig.DEFAULT), serialize(BytecodeConfig.WITH_SOURCE));
        AtomicInteger requests = new AtomicInteger();
        Function<BytecodeConfig, DataInput> executionBytecodeInput = config -> {
            requests.incrementAndGet();
            for (Payloads payload : payloads) {
                if (config.matches(payload.config())) {
                    return new DataInputStream(new ByteArrayInputStream(payload.executionBytecode()));
                }
            }
            return null;
        };
        for (Payloads payload : payloads) {
            BytecodeConfig config = payload.config();
            TestInput builderBytecodeInput = new TestInput(payload.builder(), 0);
            BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(LANGUAGE, config, builderBytecodeInput, executionBytecodeInput, DESERIALIZER);
            BasicInterpreter root = nodes.getNode(0);
            assertEquals(config.matches(BytecodeConfig.WITH_SOURCE), root.getBytecodeNode().hasSourceInformation());
            assertEquals(42L, root.getCallTarget().call());
            assertEquals(0, builderBytecodeInput.invocations);
        }
        assertEquals(2, requests.get());
    }

    @Test
    public void testExecutionBytecodeInputFailure() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        TestInput builderBytecodeInput = new TestInput(payloads.builder(), 0);
        IllegalStateException failure = new IllegalStateException("Cannot open execution bytecode.");
        AtomicInteger requests = new AtomicInteger();
        assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.DEFAULT, builderBytecodeInput, config -> {
                            requests.incrementAndGet();
                            throw failure;
                        }, DESERIALIZER)));
        assertEquals(1, requests.get());
        assertEquals(0, builderBytecodeInput.invocations);
    }

    @Test
    public void testExecutionBytecodeWithSources() throws IOException {
        // Sources can be included in the execution bytecode config.
        Payloads payloads = serialize(BytecodeConfig.WITH_SOURCE);
        TestInput builderBytecodeInput = new TestInput(payloads.builder(), 0);
        TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
        BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.WITH_SOURCE, builderBytecodeInput, executionBytecodeInput, DESERIALIZER);
        BasicInterpreter root = nodes.getNode(0);
        assertFalse(root.getBytecodeNode().getSourceInformation().isEmpty());
        SourceSection section = root.getSourceSection();
        assertNotNull(section);
        assertEquals(SOURCE.getName(), section.getSource().getName());
        assertEquals(SOURCE.getLanguage(), section.getSource().getLanguage());
        assertEquals(0, section.getCharIndex());
        assertEquals(SOURCE.getLength(), section.getCharLength());
        assertEquals(SOURCE.getCharacters().toString(), section.getCharacters().toString());
        assertEquals(42L, root.getCallTarget().call());
        assertFalse(nodes.ensureSourceInformation());
        assertEquals(0, builderBytecodeInput.invocations);
        assertEquals(1, executionBytecodeInput.invocations);
    }

    @Test
    public void testExecutionBytecodeRootReferencesInExistingFields() throws IOException {
        BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().create(LANGUAGE, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            BasicInterpreter child = b.endRoot();
            b.beginReturn();
            b.emitLoadConstant(child);
            b.endReturn();
            b.endRoot();
        });

        // These references come from the existing roots, while the bytecode constant refers to a
        // freshly parsed child during serialization.
        nodes.getNode(0).referencedRoot = nodes.getNode(1);
        nodes.getNode(1).referencedRoot = nodes.getNode(1);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        nodes.serializeExecutionBytecode(new DataOutputStream(output), BytecodeConfig.DEFAULT, SERIALIZER);

        BytecodeRootNodes<BasicInterpreter> deserialized = deserialize(new Payloads(BytecodeConfig.DEFAULT, new byte[0], output.toByteArray()));
        assertEquals(2, deserialized.count());
        BasicInterpreter root = deserialized.getNode(0);
        BasicInterpreter child = deserialized.getNode(1);
        assertSame(child, root.referencedRoot);
        assertSame(child, child.referencedRoot);
        assertSame(child, root.getCallTarget().call());
        assertEquals(42L, child.getCallTarget().call());
    }

    @Test
    public void testSharedConstants() throws IOException {
        for (Object constant : new Object[]{true, 42L, "shared"}) {
            Payloads payloads = serialize(BytecodeConfig.DEFAULT, b -> emitConstantRoots(b, 2, constant));
            BytecodeRootNodes<BasicInterpreter> nodes = deserialize(payloads);
            assertEquals(2, nodes.count());
            Object first = nodes.getNode(0).getCallTarget().call();
            Object second = nodes.getNode(1).getCallTarget().call();
            assertEquals(constant, first);
            assertEquals(constant, second);
            if (constant instanceof String) {
                // Roots use a shared constant pool.
                // For reference types like strings, we should observe the same identity.
                assertSame(first, second);
                assertSame(first, nodes.getNode(0).getName());
                assertSame(first, nodes.getNode(1).getName());
            } else {
                // Null is allowed in root fields, but not in bytecode constants.
                assertNull(nodes.getNode(0).getName());
                assertNull(nodes.getNode(1).getName());
            }
        }
    }

    @Test
    public void testSharedLargeConstant() throws IOException {
        long[] constant = new long[4096];
        Arrays.setAll(constant, i -> i);
        Payloads oneRoot = serialize(BytecodeConfig.DEFAULT, b -> emitConstantRoots(b, 1, constant));
        Payloads twoRoots = serialize(BytecodeConfig.DEFAULT, b -> emitConstantRoots(b, 2, constant));
        assertTrue("The second root should not encode the large constant again.",
                        twoRoots.executionBytecode().length - oneRoot.executionBytecode().length < constant.length * Long.BYTES);

        BytecodeRootNodes<BasicInterpreter> nodes = deserialize(twoRoots);
        long[] first = (long[]) nodes.getNode(0).getCallTarget().call();
        assertArrayEquals(constant, first);
        assertSame(first, nodes.getNode(1).getCallTarget().call());
    }

    @Test
    public void testSharedSource() throws IOException {
        Source source = Source.newBuilder(BytecodeDSLTestLanguage.ID, "x".repeat(8192), "shared.test").build();
        Payloads oneRoot = serialize(BytecodeConfig.WITH_SOURCE, b -> emitSourceRoots(b, 1, source));
        Payloads twoRoots = serialize(BytecodeConfig.WITH_SOURCE, b -> emitSourceRoots(b, 2, source));
        int sizeIncrease = twoRoots.executionBytecode().length - oneRoot.executionBytecode().length;
        assertTrue("The second root should not encode the source again.", sizeIncrease < source.getLength());

        BytecodeRootNodes<BasicInterpreter> nodes = deserialize(twoRoots);
        Source sharedSource = (Source) nodes.getNode(0).getCallTarget().call();
        assertEquals(source.getCharacters().toString(), sharedSource.getCharacters().toString());
        assertSame(sharedSource, nodes.getNode(1).getCallTarget().call());
        for (BasicInterpreter root : nodes.getNodes()) {
            assertSame(sharedSource, root.getSourceSection().getSource());
        }
    }

    @Test
    public void testSharedLocalAccessor() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT, b -> {
            for (int i = 0; i < 2; i++) {
                b.beginRoot();
                // Use an accessor beyond the factory's cache to test sharing through the pool.
                for (int localIndex = 0; localIndex < 65; localIndex++) {
                    b.createLocal();
                }
                BytecodeLocal local = b.createLocal();
                b.beginReturn();
                b.beginTeeLocal(local);
                b.emitLoadConstant(42L);
                b.endTeeLocal();
                b.endReturn();
                b.endRoot();
            }
        });
        BytecodeRootNodes<BasicInterpreter> nodes = deserialize(payloads);
        assertSame(getLocalAccessor(nodes.getNode(0)), getLocalAccessor(nodes.getNode(1)));
        assertEquals(42L, nodes.getNode(0).getCallTarget().call());
        assertEquals(42L, nodes.getNode(1).getCallTarget().call());
    }

    @Test
    public void testExecutionBytecodeWithTracing() throws IOException {
        // The tracer is treated as a special constant in execution bytecode serialization. It should
        // serialize + deserialize properly.
        try (Context context = BytecodeDSLTestLanguage.createPolyglotContextWithCompilationDisabled()) {
            context.initialize(BytecodeDSLTestLanguage.ID);
            BytecodeDSLTestLanguage language = BytecodeDSLTestLanguage.REF.get(null);

            // Tracing cannot be added through public BytecodeConfig APIs, but COMPLETE includes it.
            Payloads payloads = serialize(language, BytecodeConfig.COMPLETE, SerializationTest::emitProgram);
            TestInput builderBytecodeInput = new TestInput(payloads.builder(), 0);
            TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
            BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(language, BytecodeConfig.COMPLETE, builderBytecodeInput, executionBytecodeInput, DESERIALIZER);
            AtomicInteger tracedInstructions = new AtomicInteger();
            nodes.addInstructionTracer((access, bytecode, bytecodeIndex, frame) -> tracedInstructions.incrementAndGet());

            // COMPLETE also enables the IncrementValue instrumentation emitted by the fixture.
            assertEquals(43L, nodes.getNode(0).getCallTarget().call());
            assertTrue(tracedInstructions.get() > 0);
            assertEquals(0, builderBytecodeInput.invocations);
            assertEquals(1, executionBytecodeInput.invocations);
        }
    }

    @Test
    public void testReparseAfterMissingExecutionBytecode() throws IOException {
        // Execution bytecodes must not be requested on reparse.
        Payloads payloads = serialize(BytecodeConfig.WITH_SOURCE);
        TestInput builderBytecodeInput = new TestInput(payloads.builder(), 2);
        TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 0);
        AtomicInteger requests = new AtomicInteger();
        BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.DEFAULT, builderBytecodeInput, config -> {
            requests.incrementAndGet();
            return config.matches(BytecodeConfig.WITH_SOURCE) ? executionBytecodeInput.get() : null;
        }, DESERIALIZER);
        BasicInterpreter root = nodes.getNode(0);
        assertNull(root.getBytecodeNode().getSourceInformation());
        assertEquals(42L, root.getCallTarget().call());
        assertEquals(1, builderBytecodeInput.invocations);
        assertEquals(0, executionBytecodeInput.invocations);
        assertEquals(1, requests.get());

        // The image now matches the requested config, but updates must still replay builder input.
        assertTrue(nodes.update(BytecodeConfig.WITH_SOURCE));
        assertSame(root, nodes.getNode(0));
        assertFalse(root.getBytecodeNode().getSourceInformation().isEmpty());
        assertEquals(42L, root.getCallTarget().call());
        assertEquals(2, builderBytecodeInput.invocations);
        assertEquals(0, executionBytecodeInput.invocations);
        assertEquals(1, requests.get());
    }

    @Test
    public void testReparseAfterExecutionBytecodeLoad() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        TestInput builderBytecodeInput = new TestInput(payloads.builder(), 2);
        TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
        BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.DEFAULT, builderBytecodeInput, executionBytecodeInput, DESERIALIZER);
        BasicInterpreter root = nodes.getNode(0);
        assertEquals(42L, root.getCallTarget().call());
        assertNull(root.getBytecodeNode().getSourceInformation());
        assertEquals(0, builderBytecodeInput.invocations);

        assertTrue(nodes.ensureSourceInformation());
        assertSame(root, nodes.getNode(0));
        assertFalse(root.getBytecodeNode().getSourceInformation().isEmpty());
        assertEquals(42L, root.getCallTarget().call());
        assertEquals(1, builderBytecodeInput.invocations);
        assertEquals(1, executionBytecodeInput.invocations);

        assertTrue(nodes.update(run.bytecode().newConfigBuilder().addInstrumentation(BasicInterpreter.IncrementValue.class).build()));
        assertSame(root, nodes.getNode(0));
        assertEquals(43L, root.getCallTarget().call());
        assertEquals(2, builderBytecodeInput.invocations);
        assertEquals(1, executionBytecodeInput.invocations);

        assertFalse(nodes.ensureSourceInformation());
        assertEquals(2, builderBytecodeInput.invocations);
        assertEquals(1, executionBytecodeInput.invocations);
    }

    @Test
    public void testMalformedBuilderDuringReparse() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        TestInput builderBytecodeInput = new TestInput(new byte[0], 1);
        TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
        BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.DEFAULT, builderBytecodeInput, executionBytecodeInput, DESERIALIZER);
        assertEquals(42L, nodes.getNode(0).getCallTarget().call());
        assertEquals(0, builderBytecodeInput.invocations);

        IOError error = assertThrows(IOError.class, nodes::ensureSourceInformation);
        assertTrue(error.getCause() instanceof EOFException);
        assertEquals(1, builderBytecodeInput.invocations);
        assertEquals(1, executionBytecodeInput.invocations);
    }

    @Test
    public void testGlobalConfigMismatch() throws IOException {
        Payloads payloads = serialize(BytecodeConfig.DEFAULT);
        try (Context context = BytecodeDSLTestLanguage.createPolyglotContextWithCompilationDisabled()) {
            context.initialize(BytecodeDSLTestLanguage.ID);
            BytecodeDSLTestLanguage language = BytecodeDSLTestLanguage.REF.get(null);
            AtomicInteger tracedInstructions = new AtomicInteger();
            InstructionTracer tracer = (access, bytecode, bytecodeIndex, frame) -> tracedInstructions.incrementAndGet();
            run.bytecode().addInstructionTracer(language, tracer);
            try {
                TestInput builderBytecodeInput = new TestInput(payloads.builder(), 1);
                TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 0);
                AtomicInteger requests = new AtomicInteger();
                // The user config matches the image, but global tracing extends the effective config.
                BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(language, BytecodeConfig.DEFAULT, builderBytecodeInput, config -> {
                    requests.incrementAndGet();
                    if (config.matches(BytecodeConfig.DEFAULT)) {
                        fail("requested config should be augmented with global config");
                    }
                    return null;
                }, DESERIALIZER);
                assertEquals(42L, nodes.getNode(0).getCallTarget().call());
                assertTrue(tracedInstructions.get() > 0);
                assertEquals(1, builderBytecodeInput.invocations);
                assertEquals(0, executionBytecodeInput.invocations);
                assertEquals(1, requests.get());
            } finally {
                run.bytecode().removeInstructionTracer(language, tracer);
            }
        }
    }

    @Test
    public void testGlobalConfigAlreadyIncluded() throws IOException {
        try (Context context = BytecodeDSLTestLanguage.createPolyglotContextWithCompilationDisabled()) {
            context.initialize(BytecodeDSLTestLanguage.ID);
            BytecodeDSLTestLanguage language = BytecodeDSLTestLanguage.REF.get(null);
            InstructionTracer tracer = (access, bytecode, bytecodeIndex, frame) -> {
            };
            run.bytecode().addInstructionTracer(language, tracer);
            try {
                Payloads payloads = serialize(language, BytecodeConfig.COMPLETE, SerializationTest::emitProgram);
                TestInput builderBytecodeInput = new TestInput(payloads.builder(), 0);
                TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
                BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(language, BytecodeConfig.COMPLETE, builderBytecodeInput, config -> {
                    assertTrue(BytecodeConfig.COMPLETE.matches(config));
                    return executionBytecodeInput.get();
                }, DESERIALIZER);
                assertEquals(43L, nodes.getNode(0).getCallTarget().call());
                assertEquals(0, builderBytecodeInput.invocations);
                assertEquals(1, executionBytecodeInput.invocations);
            } finally {
                run.bytecode().removeInstructionTracer(language, tracer);
            }
        }
    }

    /**
     * There is a risk that serialization triggers reparsing of the root node being serialized,
     * which would cause an inconsistent result. The generated code should detect and prevent
     * reparsing.
     */
    @Test
    public void testBadReparseDuringSerialization() {
        try (Context context = BytecodeDSLTestLanguage.createPolyglotContextWithCompilationDisabled()) {
            context.initialize(BytecodeDSLTestLanguage.ID);
            BytecodeDSLTestLanguage language = BytecodeDSLTestLanguage.REF.get(null);

            BytecodeParser<BasicInterpreterBuilder> parser = b -> {
                b.beginRoot();
                b.beginReturn();
                b.beginRoot();
                b.beginReturn();
                b.emitLoadConstant(42L);
                b.endReturn();
                BasicInterpreter innerRoot = b.endRoot();
                b.emitLoadConstant(innerRoot);
                b.endReturn();
                b.endRoot();
            };

            // updateImpl should detect a reparse performed during serialization.
            BytecodeSerializer reparsingSerializer = (serializerContext, buffer, object) -> {
                if (object instanceof BasicInterpreter root) {
                    root.getRootNodes().ensureSourceInformation();
                }
                SERIALIZER.serialize(serializerContext, buffer, object);
            };
            assertThrows(NullPointerException.class, () -> run.bytecode().serializeBuilderBytecode(new DataOutputStream(new ByteArrayOutputStream()), reparsingSerializer, parser));
            assertThrowsWithMessage("A reparse was attempted before parsing completed.", IllegalStateException.class,
                            () -> run.bytecode().serializeExecutionBytecode(language, new DataOutputStream(new ByteArrayOutputStream()), BytecodeConfig.DEFAULT, reparsingSerializer, parser));

            // Preparing a root node can implicitly trigger reparsing when the global config has
            // additional metadata/instrumentation. prepareForCall should detect this.
            BytecodeSerializer preparingSerializer = (serializerContext, buffer, object) -> {
                if (object instanceof BasicInterpreter root) {
                    root.getCallTarget();
                }
                SERIALIZER.serialize(serializerContext, buffer, object);
            };
            assertThrowsWithMessage("method should not be called", IllegalStateException.class,
                            () -> run.bytecode().serializeBuilderBytecode(new DataOutputStream(new ByteArrayOutputStream()), preparingSerializer, parser));
            assertThrowsWithMessage("A call target cannot be created until bytecode parsing completes.", IllegalStateException.class,
                            () -> run.bytecode().serializeExecutionBytecode(language, new DataOutputStream(new ByteArrayOutputStream()), BytecodeConfig.DEFAULT, preparingSerializer, parser));
        }
    }

    /**
     * Like {@link #testBadReparseDuringSerialization()} but for deserialization.
     */
    @Test
    public void testBadReparseDuringDeserialization() throws IOException {
        BytecodeParser<BasicInterpreterBuilder> parser = b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42L);
            b.endReturn();
            BasicInterpreter innerRoot = b.endRoot();
            b.emitLoadConstant(innerRoot);
            b.endReturn();
            b.endRoot();
        };
        ByteArrayOutputStream builderBytecodeOutput = new ByteArrayOutputStream();
        run.bytecode().serializeBuilderBytecode(new DataOutputStream(builderBytecodeOutput), SERIALIZER, parser);
        ByteArrayOutputStream executionBytecodeOutput = new ByteArrayOutputStream();
        run.bytecode().serializeExecutionBytecode(LANGUAGE, new DataOutputStream(executionBytecodeOutput), BytecodeConfig.DEFAULT, SERIALIZER, parser);
        byte[] builderBytecodeBytes = builderBytecodeOutput.toByteArray();
        byte[] executionBytecodeBytes = executionBytecodeOutput.toByteArray();

        BytecodeDeserializer reparsingDeserializer = (deserializerContext, buffer) -> {
            Object object = DESERIALIZER.deserialize(deserializerContext, buffer);
            if (object instanceof BasicInterpreter root) {
                root.getRootNodes().ensureSourceInformation();
            }
            return object;
        };
        assertThrowsWithMessage("A reparse was attempted before parsing completed.", IllegalStateException.class,
                        () -> run.bytecode().deserializeBuilderBytecode(LANGUAGE, BytecodeConfig.DEFAULT, new TestInput(builderBytecodeBytes, 1), reparsingDeserializer));
        assertThrowsWithMessage("A reparse was attempted before parsing completed.", IllegalStateException.class,
                        () -> run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.DEFAULT, new TestInput(builderBytecodeBytes, 0), new TestInput(executionBytecodeBytes, 1),
                                        reparsingDeserializer));

        BytecodeDeserializer preparingDeserializer = (deserializerContext, buffer) -> {
            Object object = DESERIALIZER.deserialize(deserializerContext, buffer);
            if (object instanceof BasicInterpreter root) {
                root.getCallTarget();
            }
            return object;
        };
        assertThrowsWithMessage("A call target cannot be created until bytecode parsing completes.", IllegalStateException.class,
                        () -> run.bytecode().deserializeBuilderBytecode(LANGUAGE, BytecodeConfig.DEFAULT, new TestInput(builderBytecodeBytes, 1), preparingDeserializer));
        assertThrowsWithMessage("A call target cannot be created until bytecode parsing completes.", IllegalStateException.class,
                        () -> run.bytecode().deserializeExecutionBytecode(LANGUAGE, BytecodeConfig.DEFAULT, new TestInput(builderBytecodeBytes, 0), new TestInput(executionBytecodeBytes, 1),
                                        preparingDeserializer));
    }

    private void assertMalformedExecutionBytecode(String message, Payloads payloads, byte[] bytes) {
        TestInput builderBytecodeInput = new TestInput(payloads.builder(), 0);
        TestInput executionBytecodeInput = new TestInput(bytes, 1);
        assertThrows(message, IOException.class, () -> run.bytecode().deserializeExecutionBytecode(LANGUAGE, payloads.config(), builderBytecodeInput, executionBytecodeInput, DESERIALIZER));
        assertEquals(0, builderBytecodeInput.invocations);
        assertEquals(1, executionBytecodeInput.invocations);
    }

    private void assertMalformedBuilderBytecode(String message, byte[] bytes) {
        TestInput builderBytecodeInput = new TestInput(bytes, 1);
        assertThrows(message, IOException.class, () -> run.bytecode().deserializeBuilderBytecode(LANGUAGE, BytecodeConfig.DEFAULT, builderBytecodeInput, DESERIALIZER));
        assertEquals(1, builderBytecodeInput.invocations);
    }

    private Payloads serialize(BytecodeConfig config) throws IOException {
        return serialize(config, SerializationTest::emitProgram);
    }

    private Payloads serialize(BytecodeConfig config, BytecodeParser<BasicInterpreterBuilder> parser) throws IOException {
        return serialize(LANGUAGE, config, parser);
    }

    private Payloads serialize(BytecodeDSLTestLanguage language, BytecodeConfig config, BytecodeParser<BasicInterpreterBuilder> parser) throws IOException {
        ByteArrayOutputStream builderBytecodeOutput = new ByteArrayOutputStream();
        run.bytecode().serializeBuilderBytecode(new DataOutputStream(builderBytecodeOutput), SERIALIZER, parser);
        ByteArrayOutputStream executionBytecodeOutput = new ByteArrayOutputStream();
        run.bytecode().serializeExecutionBytecode(language, new DataOutputStream(executionBytecodeOutput), config, SERIALIZER, parser);
        return new Payloads(config, builderBytecodeOutput.toByteArray(), executionBytecodeOutput.toByteArray());
    }

    private BytecodeRootNodes<BasicInterpreter> deserialize(Payloads payloads) throws IOException {
        TestInput builderBytecodeInput = new TestInput(payloads.builder(), 0);
        TestInput executionBytecodeInput = new TestInput(payloads.executionBytecode(), 1);
        BytecodeRootNodes<BasicInterpreter> nodes = run.bytecode().deserializeExecutionBytecode(LANGUAGE, payloads.config(), builderBytecodeInput, executionBytecodeInput, DESERIALIZER);
        assertEquals(0, builderBytecodeInput.invocations);
        assertEquals(1, executionBytecodeInput.invocations);
        return nodes;
    }

    private static void emitConstantRoots(BasicInterpreterBuilder b, int rootCount, Object constant) {
        for (int i = 0; i < rootCount; i++) {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(constant);
            b.endReturn();
            BasicInterpreter root = b.endRoot();
            if (constant instanceof String name) {
                root.setName(name);
            }
        }
    }

    private static void emitSourceRoots(BasicInterpreterBuilder b, int rootCount, Source source) {
        b.beginSource(source);
        b.beginSourceSection(0, source.getLength());
        emitConstantRoots(b, rootCount, source);
        b.endSourceSection();
        b.endSource();
    }

    private static LocalAccessor getLocalAccessor(BasicInterpreter root) {
        for (Instruction instruction : root.getBytecodeNode().getInstructions()) {
            for (Instruction.Argument argument : instruction.getArguments()) {
                if (argument.getKind() == Instruction.Argument.Kind.CONSTANT && argument.asConstant() instanceof LocalAccessor accessor) {
                    return accessor;
                }
            }
        }
        throw new AssertionError("Missing local accessor.");
    }

    private static void emitProgram(BasicInterpreterBuilder b) {
        b.beginSource(SOURCE);
        b.beginSourceSection(0, SOURCE.getLength());
        b.beginRoot();
        b.beginReturn();
        b.beginIncrementValue();
        b.emitLoadConstant(42L);
        b.endIncrementValue();
        b.endReturn();
        b.endRoot();
        b.endSourceSection();
        b.endSource();
    }

    private record Payloads(BytecodeConfig config, byte[] builder, byte[] executionBytecode) {
    }

    private static final class TestInput implements Supplier<DataInput>, Function<BytecodeConfig, DataInput> {
        private final byte[] bytes;
        private final int maxInvocations;
        private int invocations;

        TestInput(byte[] bytes, int maxInvocations) {
            this.bytes = bytes;
            this.maxInvocations = maxInvocations;
        }

        @Override
        public DataInput get() {
            assertTrue("Input supplier invoked more than " + maxInvocations + " times.", invocations < maxInvocations);
            invocations++;
            return new DataInputStream(new ByteArrayInputStream(bytes));
        }

        @Override
        public DataInput apply(BytecodeConfig config) {
            return get();
        }
    }
}
