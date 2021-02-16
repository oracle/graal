/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import static org.junit.Assert.fail;

/**
 * Test that {@link TruffleLanguage#parse(TruffleLanguage.InlineParsingRequest)} is called for
 * non-interactive languages as well from {@link DebugStackFrame#eval(String)}.
 */
public class TestNonInteractiveInlineParse extends AbstractDebugTest {

    @Registration(id = NonInteractiveTestLanguage.ID, name = NonInteractiveTestLanguage.NAME, interactive = false, version = "1.0")
    @ProvidedTags({StandardTags.StatementTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class})
    public static class NonInteractiveTestLanguage extends TruffleLanguage<Object> {
        static final String ID = "non-interactive-test-language";
        static final String NAME = "NonInteractive Test Language";

        @Override
        protected Object createContext(Env env) {
            return new Object();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new TestRootNode(this, request.getSource()));
        }

        @Override
        protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
            // Implement only addition of var with an integer:
            if (!request.getSource().hasCharacters()) {
                return null;
            }
            String characters = request.getSource().getCharacters().toString();
            if (characters.startsWith("var+")) {
                int i = Integer.parseInt(characters.substring(4));
                i += request.getFrame().getInt(request.getFrame().getFrameDescriptor().findFrameSlot("var"));
                return new TestExecutableNode(this, i);
            } else {
                return null;
            }
        }

        private static class TestExecutableNode extends ExecutableNode {

            private final int retValue;

            TestExecutableNode(TruffleLanguage<?> language, int retValue) {
                super(language);
                this.retValue = retValue;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                assert frame != null;
                return retValue;
            }

        }

        private static class TestRootNode extends RootNode {

            @Node.Child private TestNIRootNode main;
            private final Source source;
            private final SourceSection rootSection;
            private final FrameSlot var;

            TestRootNode(TruffleLanguage<?> language, Source source) {
                super(language);
                this.source = source;
                this.main = new TestNIRootNode(source.createSection(1));
                this.rootSection = source.createSection(0, source.getLength());
                this.var = getFrameDescriptor().addFrameSlot("var", FrameSlotKind.Int);
            }

            @Override
            public String getName() {
                return source.getCharacters(1).toString();
            }

            @Override
            public SourceSection getSourceSection() {
                return rootSection;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                frame.setInt(var, 42);
                return main.execute(frame);
            }
        }

        @GenerateWrapper
        static class TestNIRootNode extends Node implements InstrumentableNode {

            @Node.Child private TestNIStatementNode statement;
            private final SourceSection sourceSection;

            TestNIRootNode(SourceSection sourceSection) {
                this.sourceSection = sourceSection;
                this.statement = new TestNIStatementNode(sourceSection);
            }

            TestNIRootNode(TestNIRootNode delegate) {
                this.sourceSection = delegate.sourceSection;
                this.statement = delegate.statement;
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
                return new TestNIRootNodeWrapper(this, this, probe);
            }

            public Object execute(VirtualFrame frame) {
                return statement.execute(frame);
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.RootTag.class.equals(tag) || StandardTags.RootBodyTag.class.equals(tag);
            }

        }

        @GenerateWrapper
        static class TestNIStatementNode extends Node implements InstrumentableNode {

            private final SourceSection sourceSection;

            TestNIStatementNode(SourceSection sourceSection) {
                this.sourceSection = sourceSection;
            }

            TestNIStatementNode(TestNIStatementNode delegate) {
                this.sourceSection = delegate.sourceSection;
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
                return new TestNIStatementNodeWrapper(this, this, probe);
            }

            public Object execute(VirtualFrame frame) {
                assert frame != null;
                return 42 * 42;
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

    @Test
    public void testInlineParse() {
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create(NonInteractiveTestLanguage.ID, "Something");
        try (DebuggerSession session = tester.startSession()) {
            session.suspendNextExecution();
            tester.startEval(source);
            tester.expectSuspended((SuspendedEvent event) -> {
                DebugValue value = event.getTopStackFrame().eval("var+10");
                assertEquals(42 + 10, value.asInt());
            });
            tester.expectDone();
        }
    }

    @Test
    public void testNoInlineParse() {
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create(NonInteractiveTestLanguage.ID, "Something");
        try (DebuggerSession session = tester.startSession()) {
            session.suspendNextExecution();
            tester.startEval(source);
            tester.expectSuspended((SuspendedEvent event) -> {
                try {
                    event.getTopStackFrame().eval("var");
                    fail("Should not be able to evaluate expressions as inline parse did not return anything.");
                } catch (IllegalStateException ex) {
                    // Can not evaluate in a non-interactive language. O.K.
                }
            });
            tester.expectDone();
        }
    }
}
