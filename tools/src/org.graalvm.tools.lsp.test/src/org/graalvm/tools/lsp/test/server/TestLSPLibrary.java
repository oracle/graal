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
package org.graalvm.tools.lsp.test.server;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Future;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import org.graalvm.tools.api.lsp.LSPLibrary;
import org.graalvm.tools.lsp.server.types.CompletionItem;
import org.graalvm.tools.lsp.server.types.CompletionList;
import org.graalvm.tools.lsp.server.types.ParameterInformation;
import org.graalvm.tools.lsp.server.types.ServerCapabilities;
import org.graalvm.tools.lsp.server.types.SignatureHelp;
import org.graalvm.tools.lsp.server.types.SignatureHelpOptions;
import org.graalvm.tools.lsp.server.types.SignatureInformation;

/**
 * Test of {@link LSPLibrary}.
 */
public final class TestLSPLibrary extends TruffleLSPTest {

    private static final String OBJECT_DOC = "LSP Documentation of the language test object.";
    private static final String SIGNATURE_LABEL = "LSP Signature Test Label";
    private static final String SIGNATURE_DOC = "LSP test signature";

    @After
    @Override
    public void tearDown() {
        ProxyLanguage.setDelegate(new ProxyLanguage());
    }

    @Test
    public void testLSPDocs() throws Exception {
        ServerCapabilities capabilities = ServerCapabilities.create();
        SignatureHelpOptions signatureOptions = SignatureHelpOptions.create();
        signatureOptions.setTriggerCharacters(List.of(" "));
        capabilities.setSignatureHelpProvider(signatureOptions);
        truffleAdapter.setServerCapabilities(ProxyLanguage.ID, capabilities);
        URI uri = createDummyFileUriForSL();
        String text = "A test text 1";
        ProxyLanguage.setDelegate(new TestLSPLanguage());
        Future<?> future = truffleAdapter.parse(text, ProxyLanguage.ID, uri);
        future.get();
        Future<Boolean> futureCoverage = truffleAdapter.runCoverageAnalysis(uri);
        futureCoverage.get();

        Future<CompletionList> futureC = truffleAdapter.completion(uri, 0, 0, null);
        CompletionList completionList = futureC.get();
        List<CompletionItem> completionItems = completionList.getItems();
        assertEquals(2, completionItems.size());
        assertEquals("a", completionItems.get(0).getLabel());
        assertEquals("o", completionItems.get(1).getLabel());
        CompletionItem item1 = completionItems.get(1);
        assertEquals(OBJECT_DOC, item1.getDocumentation());

        Future<SignatureHelp> signatureHelpFuture = truffleAdapter.signatureHelp(uri, 0, 2);
        SignatureHelp signatureHelp = signatureHelpFuture.get();
        List<SignatureInformation> signatures = signatureHelp.getSignatures();
        assertEquals(1, signatures.size());
        SignatureInformation signatureInfo = signatures.get(0);
        assertEquals(SIGNATURE_LABEL, signatureInfo.getLabel());
        assertEquals(SIGNATURE_DOC, signatureInfo.getDocumentation());
        List<ParameterInformation> parameters = signatureInfo.getParameters();
        assertEquals(2, parameters.size());
        assertEquals("Test Parameter 1 Label", parameters.get(0).getLabel());
        assertEquals("Param 1 LSP Documentation", parameters.get(0).getDocumentation());
        assertEquals("Test Parameter 2 Label", parameters.get(1).getLabel());
        assertEquals("Param 2 LSP Documentation", parameters.get(1).getDocumentation());
    }

    public final class TestLSPLanguage extends ProxyLanguage {

        @Override
        protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            return new TestRootNode(languageInstance, request.getSource()).getCallTarget();
        }

        private static final class TestRootNode extends RootNode {

            @Node.Child private TestStatementNode statement;
            private final SourceSection statementSection;

            TestRootNode(TruffleLanguage<?> language, com.oracle.truffle.api.source.Source source) {
                super(language, createFrameDescriptor());
                statementSection = source.createSection(1);
                statement = new TestStatementNode(statementSection, false);
                insert(statement);
            }

            private static FrameDescriptor createFrameDescriptor() {
                FrameDescriptor.Builder descriptorBuilder = FrameDescriptor.newBuilder(2);
                descriptorBuilder.addSlot(FrameSlotKind.Int, "a", null);
                descriptorBuilder.addSlot(FrameSlotKind.Object, "o", null);
                return descriptorBuilder.build();
            }

            @Override
            public String getName() {
                String text = statementSection.getCharacters().toString();
                return text;
            }

            @Override
            public SourceSection getSourceSection() {
                return statementSection;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                setVariables(frame.materialize());
                return statement.execute(frame);
            }

            @CompilerDirectives.TruffleBoundary
            private void setVariables(MaterializedFrame frame) {
                String text = statementSection.getCharacters().toString();
                int index = 0;
                while (!Character.isDigit(text.charAt(index))) {
                    index++;
                }
                int num = Integer.parseInt(text.substring(index));
                frame.setInt(0, num);
                TruffleObject obj = new LSPEnhancedObject();
                frame.setObject(1, obj);
            }

            @Override
            protected boolean isInstrumentable() {
                return true;
            }

        }

        @GenerateWrapper
        @ExportLibrary(NodeLibrary.class)
        static class TestStatementNode extends Node implements InstrumentableNode {

            @Node.Child private TestStatementNode call;
            private final SourceSection sourceSection;
            private final Boolean isCall;

            TestStatementNode(SourceSection sourceSection, Boolean isCall) {
                this.sourceSection = sourceSection;
                this.isCall = isCall;
                if (isCall != null) {
                    Boolean nextCall = Boolean.TRUE.equals(isCall) ? null : !isCall;
                    call = new TestStatementNode(sourceSection, nextCall);
                    insert(call);
                }
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
                return new TestStatementNodeWrapper(sourceSection, isCall, this, probe);
            }

            public Object execute(VirtualFrame frame) {
                if (call != null) {
                    call.execute(frame);
                }
                return frame.getObject(1);
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return Boolean.TRUE.equals(isCall) ? CallTag.class.equals(tag) : StatementTag.class.equals(tag);
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
                return true;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
                return new TestScope(frame);
            }
        }

        @ExportLibrary(InteropLibrary.class)
        @SuppressWarnings("static-method")
        static final class TestScope implements TruffleObject {

            private final MaterializedFrame frame;

            TestScope(Frame frame) {
                this.frame = frame != null ? frame.materialize() : null;
            }

            @ExportMessage
            boolean isScope() {
                return true;
            }

            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            public Object getMembers(@SuppressWarnings("unused") boolean internal) {
                return new Array("a", "o");
            }

            @ExportMessage
            boolean isMemberReadable(String member) {
                return switch (member) {
                    case "a", "o" -> true;
                    default -> false;
                };
            }

            @ExportMessage
            public Object readMember(String member) throws UnknownIdentifierException {
                return switch (member) {
                    case "a" -> frame != null ? frame.getInt(0) : 0;
                    case "o" -> frame != null ? frame.getObject(1) : new Null();
                    default -> throw UnknownIdentifierException.create(member);
                };
            }

            @ExportMessage
            boolean hasLanguage() {
                return true;
            }

            @ExportMessage
            Class<? extends TruffleLanguage<?>> getLanguage() {
                return TestLSPLanguage.class;
            }

            @ExportMessage
            Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
                return "A scope of 'a' and 'o'.";
            }
        }

        @ExportLibrary(InteropLibrary.class)
        @SuppressWarnings("static-method")
        static final class Null implements TruffleObject {

            @ExportMessage
            boolean isNull() {
                return true;
            }
        }

        @ExportLibrary(InteropLibrary.class)
        @SuppressWarnings("static-method")
        static final class Array implements TruffleObject {

            @CompilationFinal(dimensions = 1) private final Object[] elements;

            Array(Object... elements) {
                this.elements = elements;
            }

            @ExportMessage
            public boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            public long getArraySize() {
                return elements.length;
            }

            @ExportMessage
            public boolean isArrayElementReadable(long idx) {
                return 0 <= idx && idx < elements.length;
            }

            @ExportMessage
            public Object readArrayElement(long idx,
                            @Bind Node node,
                            @Cached InlinedBranchProfile error) throws InvalidArrayIndexException {
                if (!isArrayElementReadable(idx)) {
                    error.enter(node);
                    throw InvalidArrayIndexException.create(idx);
                }
                return elements[(int) idx];
            }
        }

        @ExportLibrary(InteropLibrary.class)
        @ExportLibrary(LSPLibrary.class)
        @SuppressWarnings("static-method")
        static final class LSPEnhancedObject implements TruffleObject {

            private static final String DOCUMENTATION = "documentation";
            private static final String PARAMETERS = "parameters";

            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            public Object getMembers(@SuppressWarnings("unused") boolean internal) {
                return new Array("A", "B");
            }

            @ExportMessage
            boolean isMemberReadable(String member) {
                return "A".equals(member) || "B".equals(member);
            }

            @ExportMessage
            public Object readMember(String member) throws UnknownIdentifierException {
                if ("A".equals(member)) {
                    return "Value of A";
                } else if ("B".equals(member)) {
                    return "Value of B";
                } else {
                    throw UnknownIdentifierException.create(member);
                }
            }

            @ExportMessage
            Object getDocumentation() {
                return OBJECT_DOC;
            }

            @ExportMessage
            Object getSignature() {
                return new LSPSignature();
            }

            @ExportMessage
            boolean isExecutable() {
                return true;
            }

            @ExportMessage
            Object execute(@SuppressWarnings("unused") Object[] arguments) {
                return this;
            }

            @ExportLibrary(InteropLibrary.class)
            static final class LSPSignature implements TruffleObject {

                @ExportMessage
                boolean hasMembers() {
                    return true;
                }

                @ExportMessage
                public Object getMembers(@SuppressWarnings("unused") boolean internal) {
                    return new Array(DOCUMENTATION, PARAMETERS);
                }

                @ExportMessage
                boolean isMemberReadable(String member) {
                    return switch (member) {
                        case DOCUMENTATION, PARAMETERS -> true;
                        default -> false;
                    };
                }

                @ExportMessage
                public Object readMember(String member) throws UnknownIdentifierException {
                    return switch (member) {
                        case DOCUMENTATION -> SIGNATURE_DOC;
                        case PARAMETERS -> new Array(
                                        new ParameterInformation("Test Parameter 1 Label", "Param 1 LSP Documentation"),
                                        new ParameterInformation("Test Parameter 2 Label", "Param 2 LSP Documentation"));
                        default -> throw UnknownIdentifierException.create(member);
                    };
                }

                @ExportMessage
                Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
                    return SIGNATURE_LABEL;
                }
            }

            @ExportLibrary(InteropLibrary.class)
            static final class ParameterInformation implements TruffleObject {

                private static final String LABEL = "label";

                private final String label;
                private final String documentation;

                ParameterInformation(String label, String documentation) {
                    this.label = label;
                    this.documentation = documentation;
                }

                @ExportMessage
                boolean hasMembers() {
                    return true;
                }

                @ExportMessage
                public Object getMembers(@SuppressWarnings("unused") boolean internal) {
                    return new Array(LABEL, DOCUMENTATION);
                }

                @ExportMessage
                boolean isMemberReadable(String member) {
                    return switch (member) {
                        case LABEL, DOCUMENTATION -> true;
                        default -> false;
                    };
                }

                @ExportMessage
                public Object readMember(String member) throws UnknownIdentifierException {
                    return switch (member) {
                        case LABEL -> label;
                        case DOCUMENTATION -> documentation;
                        default -> throw UnknownIdentifierException.create(member);
                    };
                }
            }
        }
    }
}
