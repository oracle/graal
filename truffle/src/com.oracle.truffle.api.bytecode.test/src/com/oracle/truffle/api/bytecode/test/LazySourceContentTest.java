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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeVersion;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.LazySourcesBytecodeRootNode.CachingSourceSupplierContext;
import com.oracle.truffle.api.bytecode.test.LazySourcesBytecodeRootNode.SourceSupplierContext;
import com.oracle.truffle.api.bytecode.test.TagTest.TagTestInstrumentation;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class LazySourceContentTest {
    @Parameters(name = "serialize={0}")
    public static List<Boolean> getParameters() {
        return List.of(false, true);
    }

    @Parameter public boolean serialize;

    Context context;
    Instrumenter instrumenter;

    @Before
    public void setup() {
        context = Context.create(BytecodeDSLTestLanguage.ID);
        context.initialize(BytecodeDSLTestLanguage.ID);
        context.enter();
        instrumenter = context.getEngine().getInstruments().get(TagTestInstrumentation.ID).lookup(Instrumenter.class);
    }

    @After
    public void tearDown() {
        context.close();
    }

    private BytecodeRootNodes<LazySourcesBytecodeRootNode> parse(BytecodeConfig config, BytecodeParser<LazySourcesBytecodeRootNodeGen.Builder> parser) {
        if (serialize) {
            return doRoundTrip(config, parser);
        } else {
            return LazySourcesBytecodeRootNodeGen.BYTECODE.create(BytecodeDSLTestLanguage.REF.get(null), config, parser);
        }
    }

    private static BytecodeRootNodes<LazySourcesBytecodeRootNode> doRoundTrip(BytecodeConfig config, BytecodeParser<LazySourcesBytecodeRootNodeGen.Builder> parser) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            LazySourcesBytecodeRootNodeGen.BYTECODE.serializeBuilderBytecode(new DataOutputStream(output), SERIALIZER, parser);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        Supplier<DataInput> builderBytecodeInput = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(output.toByteArray()));
        try {
            return LazySourcesBytecodeRootNodeGen.BYTECODE.deserializeBuilderBytecode(null, config, builderBytecodeInput, DESERIALIZER);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof Source s) {
                buffer.writeByte(0);
                buffer.writeUTF(s.getName());
                buffer.writeBoolean(s.hasCharacters());
                if (s.hasCharacters()) {
                    buffer.writeUTF(s.getCharacters().toString());
                }
            } else {
                throw new AssertionError("unsupported object " + object);
            }

        }
    };

    private static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            byte objectCode = buffer.readByte();
            return switch (objectCode) {
                case 0 -> {
                    String name = buffer.readUTF();
                    if (buffer.readBoolean()) {
                        CharSequence characters = buffer.readUTF();
                        yield Source.newBuilder(BytecodeDSLTestLanguage.ID, characters, name).build();
                    } else {
                        yield Source.newBuilder(BytecodeDSLTestLanguage.ID, "", name).content(Source.CONTENT_NONE).build();
                    }
                }
                default -> throw new AssertionError("unsupported object code " + objectCode);
            };
        }
    };

    private BytecodeRootNodes<LazySourcesBytecodeRootNode> parse(BytecodeParser<LazySourcesBytecodeRootNodeGen.Builder> parser) {
        return parse(BytecodeConfig.WITH_SOURCE, parser);
    }

    private LazySourcesBytecodeRootNode parseNode(BytecodeParser<LazySourcesBytecodeRootNodeGen.Builder> parser) {
        return parse(parser).getNode(0);
    }

    private static Source createSourceWithoutCharacters(String name) {
        return Source.newBuilder(BytecodeDSLTestLanguage.ID, "", name).content(Source.CONTENT_NONE).build();
    }

    private static Source createSourceWithCharacters(String name, String content) {
        return Source.newBuilder(BytecodeDSLTestLanguage.ID, content, name).build();
    }

    private static void assertSectionWithoutCharacters(SourceSection s, int charIndex, int charLength) {
        assertFalse(s.getSource().hasCharacters());
        assertEquals(charIndex, s.getCharIndex());
        assertEquals(charLength, s.getCharLength());
        assertEquals("", s.getCharacters());
    }

    private static void assertSectionWithCharacters(SourceSection s, int charIndex, int charLength, String contents) {
        assertTrue(s.getSource().hasCharacters());
        assertEquals(charIndex, s.getCharIndex());
        assertEquals(charLength, s.getCharLength());
        assertEquals(contents, s.getCharacters());
    }

    @Test
    public void testLoadSourceCharacters() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            LazySourcesBytecodeRootNode root = parse(BytecodeConfig.DEFAULT, b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.beginSourceSection(1, 3);
                b.emitLoadNull();
                b.endSourceSection();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            }).getNode(0);

            assertFalse(root.getBytecodeNode().hasSourceInformation());
            assertFalse(root.getBytecodeNode().hasSourceInformationWithContent());
            assertNull(root.getSourceSection());

            BytecodeNode withSourceInfo = root.getBytecodeNode().ensureSourceInformation();
            assertFalse(withSourceInfo.hasSourceInformationWithContent());
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);
            assertSectionWithoutCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3);

            BytecodeNode withCharacters = root.getBytecodeNode().ensureSourceInformationWithContent();
            assertNotEquals(withSourceInfo, withCharacters);
            assertTrue(withCharacters.hasSourceInformationWithContent());
            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
            assertSectionWithCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3, "ell");

            assertEquals(1, ctx.loadCount);
            root.getBytecodeNode().ensureSourceInformationWithContent();
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testLoadMultipleSourceCharacters() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> switch (s.getName()) {
            case "a" -> createSourceWithCharacters("a", "hello");
            case "b" -> createSourceWithCharacters("b", "bonjour");
            default -> throw new AssertionError("unexpected source");
        })) {
            LazySourcesBytecodeRootNode root = parseNode(b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.beginSource(createSourceWithoutCharacters("b"));
                b.beginSourceSection(1, 3);
                b.emitLoadNull();
                b.endSourceSection();
                b.endSource();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });

            assertFalse(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);
            assertSectionWithoutCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3);

            BytecodeNode withCharacters = root.getBytecodeNode().ensureSourceInformationWithContent();
            assertTrue(withCharacters.hasSourceInformationWithContent());
            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
            assertSectionWithCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3, "onj");

            assertEquals(2, ctx.loadCount);
            root.getBytecodeNode().ensureSourceInformationWithContent();
            assertEquals(2, ctx.loadCount);
        }
    }

    @Test
    public void testSupplierReturnsNull() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> null)) {
            LazySourcesBytecodeRootNode root = parseNode(b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.emitLoadNull();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });

            assertFalse(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);
            // The supplier cannot return null.
            assertThrows(IllegalStateException.class, () -> root.getBytecodeNode().ensureSourceInformationWithContent());
        }
    }

    @Test
    public void testSupplierReturnsSourceWithoutCharacters() {
        // The supplier can return a source without characters if loading fails.
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithoutCharacters("a"))) {
            LazySourcesBytecodeRootNode root = parseNode(b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.emitLoadNull();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });

            assertFalse(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);

            BytecodeNode withCharacters = root.getBytecodeNode().ensureSourceInformationWithContent();
            assertTrue(withCharacters.hasSourceInformationWithContent());
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);

            assertEquals(1, ctx.loadCount);
            root.getBytecodeNode().ensureSourceInformationWithContent();
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testSomeCharactersAlreadyAvailable() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> switch (s.getName()) {
            case "b" -> createSourceWithCharacters("b", "bonjour");
            default -> throw new AssertionError("unexpected source");
        })) {
            LazySourcesBytecodeRootNode root = parseNode(b -> {
                b.beginSource(createSourceWithCharacters("a", "hello"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.beginSource(createSourceWithoutCharacters("b"));
                b.beginSourceSection(1, 3);
                b.emitLoadNull();
                b.endSourceSection();
                b.endSource();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });
            assertFalse(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
            assertSectionWithoutCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3);

            BytecodeNode withCharacters = root.getBytecodeNode().ensureSourceInformationWithContent();
            assertTrue(withCharacters.hasSourceInformationWithContent());
            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
            assertSectionWithCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3, "onj");

            // Loading is only triggered on sources that don't already have characters.
            assertEquals(1, ctx.loadCount);
            root.getBytecodeNode().ensureSourceInformationWithContent();
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testMultipleRootNodes() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "foo bar"))) {
            BytecodeRootNodes<LazySourcesBytecodeRootNode> roots = parse(b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 3);
                b.beginRoot();
                b.endRoot();
                b.endSourceSection();
                b.beginSourceSection(4, 3);
                b.beginRoot();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });
            LazySourcesBytecodeRootNode foo = roots.getNode(0);
            LazySourcesBytecodeRootNode bar = roots.getNode(1);

            assertSectionWithoutCharacters(foo.getSourceSection(), 0, 3);
            assertSectionWithoutCharacters(bar.getSourceSection(), 4, 3);
            // Loading sources triggers loading for all roots.
            foo.ensureSourceSectionWithContent();
            assertSectionWithCharacters(foo.getSourceSection(), 0, 3, "foo");
            assertSectionWithCharacters(bar.getSourceSection(), 4, 3, "bar");

            assertEquals(1, ctx.loadCount);
            foo.ensureSourceSectionWithContent();
            bar.ensureSourceSectionWithContent();
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testBytecodeLocationUpdate() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            LazySourcesBytecodeRootNode root = parseNode(b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.beginSourceSection(1, 3);
                b.emitGetBytecodeLocation();
                b.endSourceSection();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });

            BytecodeLocation location = (BytecodeLocation) root.getCallTarget().call();
            assertFalse(location.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithoutCharacters(location.getSourceLocation(), 1, 3);

            // Source characters can be loaded using a BytecodeLocation
            BytecodeLocation withCharacters = location.ensureSourceInformationWithContent();
            assertTrue(withCharacters.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithCharacters(withCharacters.getSourceLocation(), 1, 3, "ell");

            assertEquals(1, ctx.loadCount);
            location.ensureSourceInformationWithContent();
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testBytecodeRootNodesUpdate() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            BytecodeRootNodes<LazySourcesBytecodeRootNode> nodes = parse(b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.beginSourceSection(1, 3);
                b.emitLoadNull();
                b.endSourceSection();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });

            assertFalse(nodes.getNode(0).getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithoutCharacters(nodes.getNode(0).getSourceSection(), 0, 5);

            // Source characters can be loaded using the BytecodeRootNodes instance
            nodes.ensureSourceInformationWithContent();
            assertTrue(nodes.getNode(0).getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithCharacters(nodes.getNode(0).getSourceSection(), 0, 5, "hello");

            assertEquals(1, ctx.loadCount);
            nodes.ensureSourceInformationWithContent();
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testLoadOnFirstParse() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            BytecodeConfig configWithSourceCharacters = LazySourcesBytecodeRootNodeGen.newConfigBuilder().addSourceContent().build();
            BytecodeRootNodes<LazySourcesBytecodeRootNode> nodes = parse(configWithSourceCharacters, b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.beginSourceSection(1, 3);
                b.emitLoadNull();
                b.endSourceSection();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });
            // Source characters can be eagerly loaded on first parse.
            assertTrue(nodes.getNode(0).getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithCharacters(nodes.getNode(0).getSourceSection(), 0, 5, "hello");

            assertEquals(1, ctx.loadCount);
            nodes.ensureSourceInformationWithContent();
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testCachingSupplier() {
        BytecodeParser<LazySourcesBytecodeRootNodeGen.Builder> parser = b -> {
            b.beginSource(createSourceWithoutCharacters("a"));
            b.beginSourceSection(0, 5);
            b.beginRoot();
            b.beginReturn();
            b.emitLoadNull();
            b.endReturn();
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        };
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            BytecodeRootNodes<LazySourcesBytecodeRootNode> nodes = parse(parser);
            assertSectionWithoutCharacters(nodes.getNode(0).getSourceSection(), 0, 5);
            assertEquals(0, ctx.loadCount);
            nodes.ensureSourceInformationWithContent();
            assertSectionWithCharacters(nodes.getNode(0).getSourceSection(), 0, 5, "hello");
            assertEquals(1, ctx.loadCount);
            // Bytecode reparsing can cause the supplier to be invoked multiple times.
            nodes.update(LazySourcesBytecodeRootNodeGen.newConfigBuilder().addTag(ExpressionTag.class).build());
            assertSectionWithCharacters(nodes.getNode(0).getSourceSection(), 0, 5, "hello");
            assertEquals(2, ctx.loadCount);
        }

        try (CachingSourceSupplierContext ctx = new CachingSourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            BytecodeRootNodes<LazySourcesBytecodeRootNode> nodes = parse(parser);
            assertSectionWithoutCharacters(nodes.getNode(0).getSourceSection(), 0, 5);
            assertEquals(0, ctx.loadCount);
            nodes.ensureSourceInformationWithContent();
            assertSectionWithCharacters(nodes.getNode(0).getSourceSection(), 0, 5, "hello");
            assertEquals(1, ctx.loadCount);
            // Caching in the supplier can avoid re-loading.
            nodes.update(LazySourcesBytecodeRootNodeGen.newConfigBuilder().addTag(ExpressionTag.class).build());
            assertSectionWithCharacters(nodes.getNode(0).getSourceSection(), 0, 5, "hello");
            assertEquals(1, ctx.loadCount);
        }
    }

    @Test
    public void testInstrumentationTriggersLoading() {
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            LazySourcesBytecodeRootNode root = parseNode(b -> {
                b.beginSource(createSourceWithoutCharacters("a"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.emitLoadNull();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            });

            assertFalse(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);
            assertNull(root.getCallTarget().call());

            // Enabling instrumentation triggers source content loading
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.ANY, e -> new ExecutionEventNode() {
            });

            assertTrue(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
        }
    }

    @Test
    public void testExecutionBytecodeWithSourceContent() throws IOException {
        BytecodeDSLTestLanguage language = BytecodeDSLTestLanguage.REF.get(null);
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            ByteArrayOutputStream builderBytecodeOutput = new ByteArrayOutputStream();
            LazySourcesBytecodeRootNodeGen.BYTECODE.serializeBuilderBytecode(new DataOutputStream(builderBytecodeOutput), SERIALIZER, LazySourceContentTest::emitExecutionBytecodeProgram);
            assertEquals(0, ctx.loadCount);

            // Materialize the content while constructing the execution bytecode image.
            ByteArrayOutputStream executionBytecodeOutput = new ByteArrayOutputStream();
            LazySourcesBytecodeRootNodeGen.BYTECODE.serializeExecutionBytecode(language, new DataOutputStream(executionBytecodeOutput), BytecodeConfig.WITH_SOURCE_CONTENT, SERIALIZER,
                            LazySourceContentTest::emitExecutionBytecodeProgram);
            assertEquals(1, ctx.loadCount);
            // Count loading separately from serialization.
            ctx.loadCount = 0;

            TestInput builderBytecodeInput = new TestInput(builderBytecodeOutput.toByteArray());
            TestInput executionBytecodeInput = new TestInput(executionBytecodeOutput.toByteArray());
            BytecodeRootNodes<LazySourcesBytecodeRootNode> nodes = LazySourcesBytecodeRootNodeGen.BYTECODE.deserializeExecutionBytecode(
                            language, BytecodeConfig.WITH_SOURCE_CONTENT, builderBytecodeInput,
                            config -> config.matches(BytecodeConfig.WITH_SOURCE_CONTENT) ? executionBytecodeInput.get() : null,
                            DESERIALIZER);
            assertEquals(0, builderBytecodeInput.invocations);
            assertEquals(1, executionBytecodeInput.invocations);
            assertEquals(0, ctx.loadCount);

            LazySourcesBytecodeRootNode root = nodes.getNode(0);
            assertTrue(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
            assertSectionWithCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3, "ell");
            assertNull(root.getCallTarget().call());
            assertFalse(nodes.ensureSourceInformationWithContent());
            assertEquals(0, builderBytecodeInput.invocations);
            assertEquals(1, executionBytecodeInput.invocations);
            assertEquals(0, ctx.loadCount);
        }
    }

    @Test
    public void testExecutionBytecodeWithoutSourceContent() throws IOException {
        BytecodeDSLTestLanguage language = BytecodeDSLTestLanguage.REF.get(null);
        try (SourceSupplierContext ctx = new SourceSupplierContext(s -> createSourceWithCharacters("a", "hello"))) {
            ByteArrayOutputStream builderBytecodeOutput = new ByteArrayOutputStream();
            LazySourcesBytecodeRootNodeGen.BYTECODE.serializeBuilderBytecode(new DataOutputStream(builderBytecodeOutput), SERIALIZER, LazySourceContentTest::emitExecutionBytecodeProgram);
            assertEquals(0, ctx.loadCount);

            // A source-only image does not load content during serialization.
            ByteArrayOutputStream executionBytecodeOutput = new ByteArrayOutputStream();
            LazySourcesBytecodeRootNodeGen.BYTECODE.serializeExecutionBytecode(language, new DataOutputStream(executionBytecodeOutput), BytecodeConfig.WITH_SOURCE, SERIALIZER,
                            LazySourceContentTest::emitExecutionBytecodeProgram);
            assertEquals(0, ctx.loadCount);

            // No image includes content, so requesting it loads the characters during builder replay.
            TestInput builderBytecodeInput = new TestInput(builderBytecodeOutput.toByteArray());
            TestInput executionBytecodeInput = new TestInput(executionBytecodeOutput.toByteArray());
            BytecodeRootNodes<LazySourcesBytecodeRootNode> nodes = LazySourcesBytecodeRootNodeGen.BYTECODE.deserializeExecutionBytecode(
                            language, BytecodeConfig.WITH_SOURCE_CONTENT, builderBytecodeInput,
                            config -> config.matches(BytecodeConfig.WITH_SOURCE) ? executionBytecodeInput.get() : null, DESERIALIZER);
            assertEquals(1, builderBytecodeInput.invocations);
            assertEquals(0, executionBytecodeInput.invocations);
            assertEquals(1, ctx.loadCount);

            LazySourcesBytecodeRootNode root = nodes.getNode(0);
            assertTrue(root.getBytecodeNode().hasSourceInformationWithContent());
            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
            assertSectionWithCharacters(root.getBytecodeNode().getInstruction(0).getSourceSection(), 1, 3, "ell");
            assertNull(root.getCallTarget().call());
            assertFalse(nodes.ensureSourceInformationWithContent());
            assertEquals(1, ctx.loadCount);
            assertEquals(1, builderBytecodeInput.invocations);
            assertEquals(0, executionBytecodeInput.invocations);
        }
    }

    private static void emitExecutionBytecodeProgram(LazySourcesBytecodeRootNodeGen.Builder b) {
        b.beginSource(createSourceWithoutCharacters("a"));
        b.beginSourceSection(0, 5);
        b.beginRoot();
        b.beginReturn();
        b.beginSourceSection(1, 3);
        b.emitLoadNull();
        b.endSourceSection();
        b.endReturn();
        b.endRoot();
        b.endSourceSection();
        b.endSource();
    }

    private static final class TestInput implements Supplier<DataInput> {
        private final byte[] bytes;
        private int invocations;

        TestInput(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public DataInput get() {
            invocations++;
            return SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(bytes));
        }
    }

    // Non-parametrized tests for expected behaviour when no source content supplier is used.
    public static class NonLazySourceContentTest {
        @Test
        public void testNoCharacters() {
            NonLazySourcesBytecodeRootNode root = NonLazySourcesBytecodeRootNodeGen.BYTECODE.create(null, BytecodeConfig.WITH_SOURCE, b -> {
                b.beginSource(createSourceWithoutCharacters("foo"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.emitLoadNull();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            }).getNode(0);

            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);
            BytecodeNode existingBytecodeNode = root.getBytecodeNode();
            // Requesting content should be a no-op.
            BytecodeNode withSourceContent = existingBytecodeNode.ensureSourceInformationWithContent();
            assertEquals(existingBytecodeNode, withSourceContent);
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);
            // Same if we use the update API.
            root.getRootNodes().update(NonLazySourcesBytecodeRootNodeGen.BYTECODE.newConfigBuilder().addSourceContent().build());
            assertEquals(existingBytecodeNode, root.getBytecodeNode());
            assertSectionWithoutCharacters(root.getSourceSection(), 0, 5);
        }

        @Test
        public void testHasCharacters() {
            NonLazySourcesBytecodeRootNode root = NonLazySourcesBytecodeRootNodeGen.BYTECODE.create(null, BytecodeConfig.WITH_SOURCE, b -> {
                b.beginSource(createSourceWithCharacters("foo", "hello"));
                b.beginSourceSection(0, 5);
                b.beginRoot();
                b.beginReturn();
                b.emitLoadNull();
                b.endReturn();
                b.endRoot();
                b.endSourceSection();
                b.endSource();
            }).getNode(0);

            assertSectionWithCharacters(root.getSourceSection(), 0, 5, "hello");
            BytecodeNode existingBytecodeNode = root.getBytecodeNode();
            // Requesting content should be a no-op.
            BytecodeNode withSourceContent = existingBytecodeNode.ensureSourceInformationWithContent();
            assertEquals(existingBytecodeNode, withSourceContent);
            // Same if we use the update API.
            root.getRootNodes().update(NonLazySourcesBytecodeRootNodeGen.BYTECODE.newConfigBuilder().addSourceContent().build());
            assertEquals(existingBytecodeNode, root.getBytecodeNode());
        }

        @SuppressWarnings("truffle")
        @GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class)
        abstract static class NonLazySourcesBytecodeRootNode extends RootNode implements BytecodeRootNode {

            protected NonLazySourcesBytecodeRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
                super(language, fd);
            }

        }
    }
}

@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true, enableTagInstrumentation = true, sourceContentSupplier = "loadSourceCharacters")
abstract class LazySourcesBytecodeRootNode extends RootNode implements BytecodeRootNode {
    @FunctionalInterface
    interface SourceContentSupplier extends Function<Source, Source> {
    }

    static SourceContentSupplier sourceSupplier;

    static class SourceSupplierContext implements AutoCloseable {
        int loadCount;

        SourceSupplierContext(SourceContentSupplier supplier) {
            loadCount = 0;
            LazySourcesBytecodeRootNode.sourceSupplier = (s) -> {
                loadCount++;
                return supplier.apply(s);
            };
        }

        public void close() {
            LazySourcesBytecodeRootNode.sourceSupplier = null;
        }
    }

    static class CachingSourceSupplierContext implements AutoCloseable {
        final Map<Source, Source> cache = new HashMap<>();
        int loadCount;

        CachingSourceSupplierContext(SourceContentSupplier supplier) {
            loadCount = 0;
            LazySourcesBytecodeRootNode.sourceSupplier = (s) -> cache.computeIfAbsent(s, unused -> {
                loadCount++;
                return supplier.apply(s);
            });
        }

        public void close() {
            LazySourcesBytecodeRootNode.sourceSupplier = null;
        }
    }

    protected LazySourcesBytecodeRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    public static Source loadSourceCharacters(@SuppressWarnings("unused") BytecodeDSLTestLanguage language, Source unloadedSource) {
        return sourceSupplier.apply(unloadedSource);
    }

    @Operation
    public static final class GetBytecodeLocation {
        @Specialization
        public static BytecodeLocation perform(@Bind BytecodeLocation location) {
            return location;
        }
    }

}

@ExpectError("The sourceContentSupplier attribute cannot be empty.%")
@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, sourceContentSupplier = "")
abstract class SourceSupplierMethodEmptyRootNode extends RootNode implements BytecodeRootNode {
    protected SourceSupplierMethodEmptyRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@ExpectError("No method 'foo' was declared on the root node with signature Source(BytecodeDSLTestLanguage, Source).%")
@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, sourceContentSupplier = "foo")
abstract class SourceSupplierMethodNotFoundRootNode extends RootNode implements BytecodeRootNode {
    protected SourceSupplierMethodNotFoundRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }
}

@ExpectError("No method 'foo' was declared on the root node with signature Source(BytecodeDSLTestLanguage, Source).%")
@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, sourceContentSupplier = "foo")
abstract class SourceSupplierMethodBadParametersRootNode extends RootNode implements BytecodeRootNode {
    protected SourceSupplierMethodBadParametersRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @SuppressWarnings("unused")
    private static Source foo(TruffleLanguage<?> language, Object o) {
        return null;
    }
}

@ExpectError("No method 'foo' was declared on the root node with signature Source(BytecodeDSLTestLanguage, Source).%")
@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, sourceContentSupplier = "foo")
abstract class SourceSupplierMethodBadReturnTypeRootNode extends RootNode implements BytecodeRootNode {
    protected SourceSupplierMethodBadReturnTypeRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @SuppressWarnings("unused")
    private static Object foo(BytecodeDSLTestLanguage language, Source o) {
        return null;
    }
}

@ExpectError("The method 'foo' must be static.%")
@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, sourceContentSupplier = "foo")
abstract class SourceSupplierMethodNotStaticRootNode extends RootNode implements BytecodeRootNode {
    protected SourceSupplierMethodNotStaticRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @SuppressWarnings("unused")
    public Source foo(BytecodeDSLTestLanguage language, Source s) {
        return null;
    }
}

@ExpectError("The method 'foo' must be visible to subclasses.%")
@GenerateBytecode(version = BytecodeVersion.LATEST, languageClass = BytecodeDSLTestLanguage.class, sourceContentSupplier = "foo")
abstract class SourceSupplierMethodNotVisibleRootNode extends RootNode implements BytecodeRootNode {
    protected SourceSupplierMethodNotVisibleRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @SuppressWarnings("unused")
    private static Source foo(BytecodeDSLTestLanguage language, Source s) {
        return null;
    }
}
