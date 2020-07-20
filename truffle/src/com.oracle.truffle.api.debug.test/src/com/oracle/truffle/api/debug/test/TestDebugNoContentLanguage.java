/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * A language that provide no source content information for debugger tests. Use
 * {@link ProxyLanguage#setDelegate(ProxyLanguage)} to register instances of this class. The
 * language produces one root node containing one statement node with <code>a</code> local variable.
 * All source sections provided are created on the provided {@link Source}.
 * <p>
 * It returns the whole source as the root source section, the first line as the statement section
 * and the last word as source location of the value of <code>a</code> local variable.
 */
public final class TestDebugNoContentLanguage extends ProxyLanguage {

    private final SourceInfo sourceInfo;
    private SourceSection varLocation;

    public TestDebugNoContentLanguage(String path, boolean lineInfo, boolean columnInfo) {
        this.sourceInfo = new SourceInfo(path, lineInfo, columnInfo);
    }

    @Override
    protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
        Source source = request.getSource();
        sourceInfo.createSource(getCurrentContext().getEnv(), source);
        CharSequence characters = source.getCharacters();
        int varStartPos = source.getLength() - 1;
        while (varStartPos > 0) {
            if (Character.isWhitespace(characters.charAt(varStartPos))) {
                varStartPos++;
                break;
            } else {
                varStartPos--;
            }
        }
        varLocation = sourceInfo.copySection(source.createSection(varStartPos, source.getLength() - varStartPos));
        return Truffle.getRuntime().createCallTarget(new TestRootNode(languageInstance, source, sourceInfo));
    }

    @Override
    protected SourceSection findSourceLocation(LanguageContext context, Object value) {
        if ("A".equals(value)) {
            return varLocation;
        }
        return super.findSourceLocation(context, value);
    }

    private static final class SourceInfo {

        private final String path;
        private final boolean lineInfo;
        private final boolean columnInfo;
        private Source source;

        SourceInfo(String path, boolean lineInfo, boolean columnInfo) {
            this.path = path;
            this.lineInfo = lineInfo;
            this.columnInfo = columnInfo;
        }

        void createSource(Env env, Source parsedSource) {
            this.source = Source.newBuilder(ProxyLanguage.ID, env.getPublicTruffleFile(path)).content(Source.CONTENT_NONE).cached(false).interactive(parsedSource.isInteractive()).internal(
                            parsedSource.isInternal()).mimeType(parsedSource.getMimeType()).build();
        }

        private SourceSection copySection(SourceSection section) {
            SourceSection copy;
            if (columnInfo) {
                copy = source.createSection(section.getStartLine(), section.getStartColumn(), section.getEndLine(), section.getEndColumn());
            } else if (lineInfo) {
                copy = source.createSection(section.getStartLine(), -1, section.getEndLine(), -1);
            } else {
                copy = source.createSection(section.getCharIndex(), section.getCharLength());
            }
            return copy;
        }
    }

    private static final class TestRootNode extends RootNode {

        @Node.Child private TestStatementNoContentNode statement;
        private final String name;
        private final SourceSection rootSection;
        private final FrameSlot slotA;

        TestRootNode(TruffleLanguage<?> language, Source parsedSource, SourceInfo sourceInfo) {
            super(language);
            int startIndex = 0;
            while (Character.isWhitespace(parsedSource.getCharacters().charAt(startIndex))) {
                startIndex++;
            }
            SourceSection parseRootSection = parsedSource.createSection(startIndex, parsedSource.getLength() - startIndex);
            rootSection = sourceInfo.copySection(parseRootSection);
            int startIndexLine = parsedSource.getLineNumber(startIndex);
            int statementEndIndex = parsedSource.getLineStartOffset(startIndexLine) + parsedSource.getLineLength(startIndexLine);
            SourceSection statementSection = sourceInfo.copySection(parsedSource.createSection(startIndex, statementEndIndex - startIndex));
            statement = new TestStatementNoContentNode(statementSection);
            name = word(parseRootSection.getCharacters().toString());
            insert(statement);
            slotA = getFrameDescriptor().findOrAddFrameSlot("a", FrameSlotKind.Object);
        }

        private static String word(String str) {
            int i = 0;
            while (!Character.isWhitespace(str.charAt(i))) {
                i++;
            }
            return str.substring(0, i);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public SourceSection getSourceSection() {
            return rootSection;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(slotA, "A");
            return statement.execute(frame);
        }

        @Override
        protected boolean isInstrumentable() {
            return true;
        }

    }

    @GenerateWrapper
    static class TestStatementNoContentNode extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;

        TestStatementNoContentNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new TestStatementNoContentNodeWrapper(sourceSection, this, probe);
        }

        public Object execute(VirtualFrame frame) {
            assert frame != null;
            return 10;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.StatementTag.class.equals(tag);
        }

    }

}
