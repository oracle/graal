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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.TagTreeNode;
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

public class InstrumentationNodeObjectTest {

    private static BytecodeRootNodes<MetadataRoot> parse(Source source, BytecodeConfig config) {
        return MetadataRootGen.create(MetadataLanguage.REF.get(null), config, b -> {
            b.beginSource(source);
            b.beginSourceSection(0, source.getLength());
            b.beginRoot();
            BytecodeLocal local = b.createLocal("x", null);
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
            b.beginSourceSection(8, 1);
            b.beginTag(ExpressionTag.class);
            b.beginTag(ReadVariableTag.class);
            b.emitLoadLocal(local);
            b.endTag(ReadVariableTag.class);
            b.endTag(ExpressionTag.class);
            b.endSourceSection();
            b.endReturn();
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });
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

    @GenerateBytecode(languageClass = MetadataLanguage.class, enableTagInstrumentation = true, enableRootTagging = false, enableRootBodyTagging = false, enableUncachedInterpreter = true)
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

        @Override
        public Object getInstrumentationNodeObject(TagTreeNode node) {
            assertSame(this, node.getRootNode());
            if (node.hasTag(ReadVariableTag.class) || node.hasTag(WriteVariableTag.class)) {
                SourceSection section = node.getSourceSection();
                return new Descriptor(node.hasTag(ReadVariableTag.class) ? ReadVariableTag.NAME : WriteVariableTag.NAME,
                                new Symbol("x", section.getSource().createSection(section.getCharIndex(), 1)));
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

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    record Descriptor(String key, Symbol symbol) implements TruffleObject {
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
            return symbol;
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
