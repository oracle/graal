/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

/**
 * A language that provides names of root instance executables that are different from
 * {@link RootNode#getName()}. Use {@link ProxyLanguage#setDelegate(ProxyLanguage)} to register
 * instances of this class.
 */
public class TestExecutableNamesLanguage extends ProxyLanguage {

    private final int depth;
    private final String rootName;
    private final String[] executableNames;

    public TestExecutableNamesLanguage(int depth, String rootName, String... executableNames) {
        this.depth = depth;
        this.rootName = rootName;
        this.executableNames = executableNames;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source source = request.getSource();
        return new RootNode(languageInstance) {
            @Override
            public Object execute(VirtualFrame frame) {
                CallTarget target = createCallTarget();
                Object last = null;
                if (executableNames.length == 0) {
                    last = target.call();
                } else {
                    for (String name : executableNames) {
                        last = target.call(name);
                    }
                }
                return last;
            }

            @TruffleBoundary
            private CallTarget createCallTarget() {
                return new TestNamedRoot(languageInstance, depth, rootName, source).getCallTarget();
            }
        }.getCallTarget();
    }

    private static class TestNamedRoot extends RootNode {

        private final String rootName;
        private final Source source;
        @Node.Child private TestNamedRootTagged rootTaggedNode;

        TestNamedRoot(ProxyLanguage language, int depth, String rootName, Source source) {
            super(language);
            this.rootName = rootName;
            this.source = source;
            CallTarget target = null;
            TestNamedRootStatement statement = null;
            if (depth > 0) {
                target = new TestNamedRoot(language, depth - 1, rootName, source).getCallTarget();
            } else {
                statement = new TestNamedRootStatement(source.createSection(1));
            }
            this.rootTaggedNode = new TestNamedRootTagged(target, statement);
        }

        @Override
        public String getName() {
            return rootName;
        }

        @Override
        protected boolean isInstrumentable() {
            return true;
        }

        @Override
        public SourceSection getSourceSection() {
            return source.createSection(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return rootTaggedNode.execute(frame);
        }

    }

    @GenerateWrapper
    static class TestNamedRootTagged extends Node implements InstrumentableNode {

        private final CallTarget target;
        @Node.Child private TestNamedRootCall callNode;
        @Node.Child private TestNamedRootStatement statement;

        TestNamedRootTagged(CallTarget target, TestNamedRootStatement statement) {
            this.target = target;
            if (target != null) {
                this.callNode = new TestNamedRootCall(target);
            } else {
                this.statement = statement;
            }
        }

        TestNamedRootTagged(TestNamedRootTagged delegate) {
            this.target = delegate.target;
            this.callNode = delegate.callNode;
            this.statement = delegate.statement;
        }

        @Override
        public final boolean isInstrumentable() {
            return true;
        }

        @Override
        public SourceSection getSourceSection() {
            if (target != null) {
                return ((RootCallTarget) target).getRootNode().getSourceSection();
            } else {
                return statement.getSourceSection();
            }
        }

        @Override
        public final InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new TestNamedRootTaggedWrapper(this, this, probe);
        }

        public Object execute(VirtualFrame frame) {
            if (callNode != null) {
                return callNode.execute(frame);
            } else {
                return statement.execute(frame);
            }
        }

        @Override
        public final boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.RootTag.class.equals(tag);
        }

    }

    @GenerateWrapper
    @ExportLibrary(NodeLibrary.class)
    static class TestNamedRootCall extends Node implements InstrumentableNode {

        private final CallTarget target;
        @Node.Child private DirectCallNode callNode;

        TestNamedRootCall(TestNamedRootCall delegate) {
            this.target = delegate.target;
            this.callNode = delegate.callNode;
        }

        TestNamedRootCall(CallTarget target) {
            this.target = target;
            this.callNode = Truffle.getRuntime().createDirectCallNode(target);
        }

        @Override
        public final boolean isInstrumentable() {
            return true;
        }

        @Override
        public SourceSection getSourceSection() {
            return ((RootCallTarget) target).getRootNode().getSourceSection();
        }

        @Override
        public final InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new TestNamedRootCallWrapper(this, this, probe);
        }

        public Object execute(VirtualFrame frame) {
            assert frame != null;
            return callNode.call();
        }

        @Override
        public final boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.CallTag.class.equals(tag);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean hasRootInstance(Frame frame) {
            return frame != null && frame.getArguments().length > 0;
        }

        @ExportMessage
        final Object getRootInstance(Frame frame) throws UnsupportedMessageException {
            if (frame != null && frame.getArguments().length > 0) {
                return new Executable((String) frame.getArguments()[0], getRootNode());
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }

    @GenerateWrapper
    @ExportLibrary(NodeLibrary.class)
    static class TestNamedRootStatement extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;

        TestNamedRootStatement(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public final boolean isInstrumentable() {
            return true;
        }

        @Override
        public final InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new TestNamedRootStatementWrapper(sourceSection, this, probe);
        }

        public Object execute(VirtualFrame frame) {
            assert frame != null;
            return 10;
        }

        @Override
        public final SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public final boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.StatementTag.class.equals(tag);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean hasRootInstance(Frame frame) {
            return frame != null && frame.getArguments().length > 0;
        }

        @ExportMessage
        final Object getRootInstance(Frame frame) throws UnsupportedMessageException {
            if (frame != null && frame.getArguments().length > 0) {
                return new Executable((String) frame.getArguments()[0], getRootNode());
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class Executable implements TruffleObject {

        private final String name;
        private final CallTarget target;

        Executable(String name, RootNode rootNode) {
            this.name = name;
            this.target = rootNode.getCallTarget();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        final Object execute(Object[] arguments) throws ArityException {
            if (arguments.length > 0) {
                throw ArityException.create(0, 0, arguments.length);
            }
            return target.call(name);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean hasExecutableName() {
            return true;
        }

        @ExportMessage
        final Object getExecutableName() {
            return name;
        }
    }
}
