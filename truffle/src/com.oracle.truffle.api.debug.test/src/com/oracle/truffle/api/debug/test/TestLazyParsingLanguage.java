/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * Language that parses functions lazily (feature of Sulong).
 * <p>
 * This language expects function names, one per line. Every function have one statement, which
 * loads and executes function on a next non-empty line.
 */
public class TestLazyParsingLanguage extends ProxyLanguage {

    @Override
    protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
        return Truffle.getRuntime().createCallTarget(new LazyRootNode(languageInstance, request.getSource(), 1));
    }

    private static class LazyRootNode extends RootNode {

        @Node.Child private LazyStatementNode statement;
        private final Source source;
        private final int line;
        private final SourceSection rootSection;

        LazyRootNode(TruffleLanguage<?> language, Source source, int line) {
            super(language);
            this.source = source;
            this.line = line;
            int endLine = line + 1;
            while (endLine <= source.getLineCount() && source.getCharacters(endLine).length() == 0) {
                endLine++;
            }
            this.statement = new LazyStatementNode(language, source.createSection(line, 1, line, source.getCharacters(line).length()), endLine);
            this.rootSection = source.createSection(line, 1, endLine - 1, 1 + source.getCharacters(endLine - 1).length());
        }

        @Override
        public String getName() {
            return source.getCharacters(line).toString();
        }

        @Override
        public SourceSection getSourceSection() {
            return rootSection;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return statement.execute(frame);
        }
    }

    @GenerateWrapper
    static class LazyStatementNode extends Node implements InstrumentableNode {

        private final TruffleLanguage<?> language;
        private final SourceSection sourceSection;
        private final int nextLine;

        LazyStatementNode(TruffleLanguage<?> language, SourceSection sourceSection, int nextLine) {
            this.language = language;
            this.sourceSection = sourceSection;
            this.nextLine = nextLine;
        }

        LazyStatementNode(LazyStatementNode delegate) {
            this.language = delegate.language;
            this.sourceSection = delegate.sourceSection;
            this.nextLine = delegate.nextLine;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new LazyStatementNodeWrapper(this, this, probe);
        }

        public Object execute(VirtualFrame frame) {
            assert frame != null;
            Source source = sourceSection.getSource();
            if (nextLine <= source.getLineCount()) {
                LazyRootNode root = new LazyRootNode(language, source, nextLine);
                Truffle.getRuntime().createCallTarget(root).call();
            }
            return 42;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.StatementTag.class.equals(tag) || StandardTags.RootTag.class.equals(tag) || StandardTags.RootBodyTag.class.equals(tag);
        }

    }

}
