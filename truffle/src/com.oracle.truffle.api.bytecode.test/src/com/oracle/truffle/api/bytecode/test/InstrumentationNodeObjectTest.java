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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.bytecode.test.TagTest.TagTestInstrumentation;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;
import com.oracle.truffle.api.instrumentation.StandardTags.WriteVariableTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@RunWith(Parameterized.class)
public class InstrumentationNodeObjectTest {

    @Parameters(name = "serialize={0}")
    public static List<Boolean> getParameters() {
        return List.of(false, true);
    }

    @Parameter public boolean serialize;

    private BytecodeRootNodes<MetadataRoot> parse(Source source, BytecodeConfig config) {
        BytecodeParser<MetadataRootGen.Builder> parser = b -> {
            Map<SectionTagKey, TagMetadata> nodeObjectMetadata = new HashMap<>();
            b.beginSource(source);
            b.beginSourceSection(0, source.getLength());
            b.beginRoot();
            BytecodeLocal local = b.createLocal("x", null);

            // Store metadata keyed on the source range + tag so the root can resolve it from the TagRootTree.
            nodeObjectMetadata.put(new SectionTagKey(0, 6, WriteVariableTag.class), new WriteVariableMetadata("x", 0));
            // x = 42
            b.beginSourceSection(0, 6);
            b.beginTag(WriteVariableTag.class);
            b.beginStoreLocal(local);
            b.beginIdentity();
            b.emitLoadConstant(42);
            b.endIdentity();
            b.endStoreLocal();
            b.endTag(WriteVariableTag.class);
            b.endSourceSection();
            b.beginReturn();

            nodeObjectMetadata.put(new SectionTagKey(8, 1, ReadVariableTag.class), new ReadVariableMetadata("x"));
            b.beginSourceSection(8, 1);
            b.beginTag(ExpressionTag.class);
            b.beginTag(ReadVariableTag.class);
            b.emitLoadLocal(local);
            b.endTag(ReadVariableTag.class);
            b.endTag(ExpressionTag.class);
            b.endSourceSection();
            b.endReturn();
            b.endRoot().setNodeObjectMetadata(Map.copyOf(nodeObjectMetadata));
            b.endSourceSection();
            b.endSource();
        };
        MetadataLanguage language = MetadataLanguage.REF.get(null);
        if (serialize) {
            return doRoundTrip(language, config, parser);
        }
        return MetadataRootGen.create(language, config, parser);
    }

    private static BytecodeRootNodes<MetadataRoot> doRoundTrip(MetadataLanguage language, BytecodeConfig config, BytecodeParser<MetadataRootGen.Builder> parser) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            MetadataRootGen.serialize(new DataOutputStream(output), SERIALIZER, parser);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        byte[] serialized = output.toByteArray();
        Supplier<DataInput> input = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(serialized));
        try {
            return MetadataRootGen.deserialize(language, config, input, DESERIALIZER);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static final byte TYPE_INT = 0;
    private static final byte TYPE_STRING = 1;
    private static final byte TYPE_SOURCE = 2;
    private static final byte TYPE_METADATA_MAP = 3;
    private static final byte TYPE_SECTION_TAG_KEY = 4;
    private static final byte TYPE_READ_VARIABLE_METADATA = 5;
    private static final byte TYPE_WRITE_VARIABLE_METADATA = 6;

    private static final BytecodeSerializer SERIALIZER = (context, buffer, object) -> serializeObject(buffer, object);

    private static void serializeObject(DataOutput buffer, Object object) throws IOException {
        if (object instanceof Integer value) {
            buffer.writeByte(TYPE_INT);
            buffer.writeInt(value);
        } else if (object instanceof String value) {
            buffer.writeByte(TYPE_STRING);
            buffer.writeUTF(value);
        } else if (object instanceof Source source) {
            buffer.writeByte(TYPE_SOURCE);
            buffer.writeUTF(source.getLanguage());
            buffer.writeUTF(source.getName());
            buffer.writeUTF(source.getCharacters().toString());
        } else if (object instanceof Map<?, ?> map) {
            buffer.writeByte(TYPE_METADATA_MAP);
            buffer.writeInt(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                serializeObject(buffer, entry.getKey());
                serializeObject(buffer, entry.getValue());
            }
        } else if (object instanceof SectionTagKey sectionTagKey) {
            buffer.writeByte(TYPE_SECTION_TAG_KEY);
            buffer.writeInt(sectionTagKey.charIndex());
            buffer.writeInt(sectionTagKey.charLength());
            if (sectionTagKey.tag() == ReadVariableTag.class) {
                buffer.writeByte(0);
            } else if (sectionTagKey.tag() == WriteVariableTag.class) {
                buffer.writeByte(1);
            } else {
                throw new AssertionError("Unsupported tag " + sectionTagKey.tag());
            }
        } else if (object instanceof ReadVariableMetadata metadata) {
            buffer.writeByte(TYPE_READ_VARIABLE_METADATA);
            buffer.writeUTF(metadata.name());
        } else if (object instanceof WriteVariableMetadata metadata) {
            buffer.writeByte(TYPE_WRITE_VARIABLE_METADATA);
            buffer.writeUTF(metadata.name());
            buffer.writeInt(metadata.charIndex());
        } else {
            throw new AssertionError("Unsupported serialization object " + object);
        }
    }

    private static final BytecodeDeserializer DESERIALIZER = (context, buffer) -> deserializeObject(buffer);

    private static Object deserializeObject(DataInput buffer) throws IOException {
        return switch (buffer.readByte()) {
            case TYPE_INT -> buffer.readInt();
            case TYPE_STRING -> buffer.readUTF();
            case TYPE_SOURCE -> {
                String language = buffer.readUTF();
                String name = buffer.readUTF();
                String characters = buffer.readUTF();
                yield Source.newBuilder(language, characters, name).build();
            }
            case TYPE_METADATA_MAP -> {
                int size = buffer.readInt();
                Map<SectionTagKey, TagMetadata> metadata = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    SectionTagKey key = (SectionTagKey) deserializeObject(buffer);
                    TagMetadata value = (TagMetadata) deserializeObject(buffer);
                    metadata.put(key, value);
                }
                yield Map.copyOf(metadata);
            }
            case TYPE_SECTION_TAG_KEY -> {
                int charIndex = buffer.readInt();
                int charLength = buffer.readInt();
                Class<? extends Tag> tag = switch (buffer.readByte()) {
                    case 0 -> ReadVariableTag.class;
                    case 1 -> WriteVariableTag.class;
                    default -> throw new AssertionError("Unsupported tag code");
                };
                yield new SectionTagKey(charIndex, charLength, tag);
            }
            case TYPE_READ_VARIABLE_METADATA -> new ReadVariableMetadata(buffer.readUTF());
            case TYPE_WRITE_VARIABLE_METADATA -> new WriteVariableMetadata(buffer.readUTF(), buffer.readInt());
            default -> throw new AssertionError("Unsupported serialization type");
        };
    }

    @Test
    public void testStaticMetadataAndExecutionEvents() throws Exception {
        for (int threshold : new int[]{0, 2, Integer.MAX_VALUE}) {
            try (Context context = Context.create(MetadataLanguage.ID)) {
                context.initialize(MetadataLanguage.ID);
                context.enter();
                try {
                    Source source = Source.newBuilder(MetadataLanguage.ID, "x = 42; x", "variables").build();
                    MetadataRoot root = parse(source, BytecodeConfig.COMPLETE).getNode(0);
                    root.getBytecodeNode().setUncachedThreshold(threshold);
                    root.getCallTarget();
                    Instrumenter instrumenter = context.getEngine().getInstruments().get(TagTestInstrumentation.ID).lookup(Instrumenter.class);
                    List<String> loaded = new ArrayList<>();
                    instrumenter.visitLoadedSourceSections(SourceSectionFilter.newBuilder().sourceIs(source).build(), event -> {
                        InstrumentableNode node = (InstrumentableNode) event.getNode();
                        if (node.hasTag(ReadVariableTag.class) || node.hasTag(WriteVariableTag.class)) {
                            loaded.add(assertMetadata(node.getNodeObject(), event.getSourceSection()));
                        } else {
                            assertNull(node.getNodeObject());
                        }
                    });
                    assertEquals(List.of("writeVariableName:x", "readVariableName:x"), loaded);
                    List<String> events = new ArrayList<>();
                    var binding = instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().sourceIs(source).tagIs(ReadVariableTag.class, WriteVariableTag.class).build(), event -> {
                        String metadata = assertMetadata(event.getNodeObject(), event.getInstrumentedSourceSection());
                        return new ExecutionEventNode() {
                            @Override
                            protected void onEnter(VirtualFrame frame) {
                                events.add(metadata);
                            }
                        };
                    });
                    try {
                        assertTrue(events.isEmpty());
                        for (int i = 0; i < 3; i++) {
                            assertEquals(42, root.getCallTarget().call());
                        }
                        assertEquals(List.of("writeVariableName:x", "readVariableName:x", "writeVariableName:x", "readVariableName:x", "writeVariableName:x", "readVariableName:x"), events);
                    } finally {
                        binding.dispose();
                    }
                } finally {
                    context.leave();
                }
            }
        }
    }

    @Test
    public void testReparseCloneAndConcurrentLookupWithoutContext() throws Exception {
        try (Context context = Context.create(MetadataLanguage.ID)) {
            context.initialize(MetadataLanguage.ID);
            context.enter();
            MetadataRoot root;
            MetadataRoot clone;
            try {
                Source source = Source.newBuilder(MetadataLanguage.ID, "x = 42; x", "variables").build();
                BytecodeRootNodes<MetadataRoot> roots = parse(source, BytecodeConfig.DEFAULT);
                root = roots.getNode(0);
                assertNull(root.getBytecodeNode().getTagTree());
                roots.update(MetadataRootGen.newConfigBuilder().addSource().addTag(ReadVariableTag.class).build());
                assertMetadataTree(root, 1);
                roots.update(BytecodeConfig.COMPLETE);
                assertMetadataTree(root, 2);
                clone = NodeUtil.cloneNode(root);
                clone.getCallTarget();
                assertMetadataTree(clone, 2);
            } finally {
                context.leave();
            }
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> assertMetadataTree(root, 2));
                var second = executor.submit(() -> assertMetadataTree(clone, 2));
                first.get();
                second.get();
            }
        }
    }

    private static void assertMetadataTree(MetadataRoot root, int count) {
        assertEquals(count, assertMetadataTree(root.getBytecodeNode().getTagTree()));
    }

    private static int assertMetadataTree(TagTree tree) {
        int count = 0;
        Object metadata = ((InstrumentableNode) tree).getNodeObject();
        if (tree.hasTag(ReadVariableTag.class) || tree.hasTag(WriteVariableTag.class)) {
            assertMetadata(metadata, ((TagTreeNode) tree).getSourceSection());
            count++;
        } else {
            assertNull(metadata);
        }
        for (TagTree child : tree.getTreeChildren()) {
            count += assertMetadataTree(child);
        }
        return count;
    }

    private static String assertMetadata(Object metadata, SourceSection section) {
        try {
            InteropLibrary interop = InteropLibrary.getUncached();
            assertTrue(interop.hasMembers(metadata));
            Object members = interop.getMembers(metadata);
            assertEquals(1, interop.getArraySize(members));
            String key = interop.asString(interop.readArrayElement(members, 0));
            assertEquals(section.getCharIndex() == 0 ? WriteVariableTag.NAME : ReadVariableTag.NAME, key);
            Object symbol = interop.readMember(metadata, key);
            assertEquals("x", interop.asString(symbol));
            assertSame(section.getSource(), interop.getSourceLocation(symbol).getSource());
            assertEquals("x", interop.getSourceLocation(symbol).getCharacters().toString());
            return key + ":" + interop.asString(symbol);
        } catch (com.oracle.truffle.api.interop.InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    @GenerateBytecode(languageClass = MetadataLanguage.class, enableTagInstrumentation = true, enableRootTagging = false, enableRootBodyTagging = false, enableUncachedInterpreter = true, enableSerialization = true)
    public abstract static class MetadataRoot extends RootNode implements BytecodeRootNode {
        protected MetadataRoot(MetadataLanguage language, FrameDescriptor descriptor) {
            super(language, descriptor);
        }

        @Operation
        static final class Identity {
            @Specialization
            static int identity(int value) {
                return value;
            }
        }

        Map<SectionTagKey, TagMetadata> nodeObjectMetadata = null;

        private void setNodeObjectMetadata(Map<SectionTagKey, TagMetadata> nodeObjectMetadata) {
            this.nodeObjectMetadata = nodeObjectMetadata;
        }

        @Override
        public Object getInstrumentationNodeObject(TagTreeNode node) {
            assertSame(this, node.getRootNode());
            SourceSection section = node.getSourceSection();
            if (section != null) {
                if (node.hasTag(ReadVariableTag.class)) {
                    TagMetadata metadata = nodeObjectMetadata.get(new SectionTagKey(section.getCharIndex(), section.getCharLength(), ReadVariableTag.class));
                    return new Descriptor(ReadVariableTag.NAME, metadata.toInteropObject(section));
                }
                if (node.hasTag(WriteVariableTag.class)) {
                    TagMetadata metadata = nodeObjectMetadata.get(new SectionTagKey(section.getCharIndex(), section.getCharLength(), WriteVariableTag.class));
                    return new Descriptor(WriteVariableTag.NAME, metadata.toInteropObject(section));
                }
            }
            return BytecodeRootNode.super.getInstrumentationNodeObject(node);
        }
    }

    @TruffleLanguage.Registration(id = MetadataLanguage.ID)
    @ProvidedTags({ReadVariableTag.class, WriteVariableTag.class, ExpressionTag.class})
    public static class MetadataLanguage extends TruffleLanguage<Object> {
        static final String ID = "bytecode-metadata-test";
        static final LanguageReference<MetadataLanguage> REF = LanguageReference.create(MetadataLanguage.class);

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }
    }

    record SectionTagKey(int charIndex, int charLength, Class<? extends Tag> tag) {
    }

    interface TagMetadata {
        TruffleObject toInteropObject(SourceSection section);
    }

    record ReadVariableMetadata(String name) implements TagMetadata {
        @Override
        public TruffleObject toInteropObject(SourceSection section) {
            return new Symbol(name, section);
        }
    }

    record WriteVariableMetadata(String name, int charIndex) implements TagMetadata {
        @Override
        public TruffleObject toInteropObject(SourceSection section) {
            // The source section of the variable differs from the section of the write.
            return new Symbol(name, section.getSource().createSection(charIndex, name.length()));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    record Descriptor(String key, Object value) implements TruffleObject {
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new Keys(key);
        }

        @ExportMessage
        boolean isMemberReadable(String name) {
            return key.equals(name);
        }

        @ExportMessage
        Object readMember(String name) throws UnknownIdentifierException {
            if (!isMemberReadable(name)) {
                throw UnknownIdentifierException.create(name);
            }
            return value;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    record Keys(String key) implements TruffleObject {
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return 1;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index == 0;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (index != 0) {
                throw InvalidArrayIndexException.create(index);
            }
            return key;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    record Symbol(String name, SourceSection location) implements TruffleObject {
        @ExportMessage
        boolean isString() {
            return true;
        }

        @ExportMessage
        String asString() {
            return name;
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return true;
        }

        @ExportMessage
        SourceSection getSourceLocation() {
            return location;
        }
    }
}
