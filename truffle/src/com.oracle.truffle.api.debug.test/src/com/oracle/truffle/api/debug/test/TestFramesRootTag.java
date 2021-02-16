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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.util.Iterator;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import org.graalvm.polyglot.Source;

/**
 * Test that debugger stack frames are based only on call nodes that are enclosed in a node tagged
 * with {@link StandardTags.RootTag}.
 */
public class TestFramesRootTag extends AbstractDebugTest {

    @Test
    public void testStack1() {
        assertStack("TTTT", 3, 2, 1, 0);
    }

    @Test
    public void testStack2() {
        assertStack("RRRR", 3);
    }

    @Test
    public void testStack3() {
        assertStack("TRTRT", 4, 2, 0);
    }

    @Test
    public void testStack4() {
        assertStack("RTRTR", 4, 3, 1);
    }

    @Test
    public void testStack5() {
        assertStack("RTRRRT", 5, 1);
    }

    private void assertStack(String code, int... positions) {
        Source source = Source.create(SyntheticRootsLanguage.ID, code);
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                Iterable<DebugStackFrame> stackFrames = event.getStackFrames();
                assertFrames(stackFrames, positions);
            });
        }
    }

    private static void assertFrames(Iterable<DebugStackFrame> stackFrames, int... positions) {
        Iterator<DebugStackFrame> frames = stackFrames.iterator();
        for (int p : positions) {
            assertTrue("No frame at character position " + p, frames.hasNext());
            DebugStackFrame frame = frames.next();
            assertNotNull("No source section at character position " + p, frame.getSourceSection());
            assertEquals(p, frame.getSourceSection().getCharIndex());
        }
        assertFalse("Extra frames", frames.hasNext());
    }

    /**
     * Creates root nodes chain based on code elements 'R' and 'T'. <b>R</b> creates a
     * <code>RootNode</code> that does not contain a node tagged with <code>RootTag</code>, <b>T</b>
     * creates a <code>RootNode</code> that contains a node tagged with <code>RootTag</code>. The
     * last node executes a statement where debugger suspends at.
     */
    @TruffleLanguage.Registration(id = SyntheticRootsLanguage.ID, name = SyntheticRootsLanguage.NAME, version = "1.0")
    @ProvidedTags({StandardTags.RootTag.class, StandardTags.CallTag.class, StandardTags.StatementTag.class})
    public static final class SyntheticRootsLanguage extends TruffleLanguage<Object> {

        static final String NAME = "Synthetic Roots Test Language";
        static final String ID = "truffle-test-synthetic-roots-language";

        @Override
        protected Object createContext(Env env) {
            return Void.TYPE;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            CharSequence code = request.getSource().getCharacters();
            int length = code.length();
            RootNode root = null;
            TestRoot lastRoot = null;
            for (int i = 0; i < length; i++) {
                boolean statement = i == length - 1;
                SourceSection sourceSection = request.getSource().createSection(i, 1);
                TestRoot newRoot;
                switch (code.charAt(i)) {
                    case 'R':
                        newRoot = new TestRoot(this, sourceSection, false, statement);
                        break;
                    case 'T':
                        newRoot = new TestRoot(this, sourceSection, true, statement);
                        break;
                    default:
                        throw new IllegalArgumentException("Illegal character at position " + i + ": " + code.charAt(i));
                }
                if (root == null) {
                    root = newRoot;
                } else {
                    lastRoot.setDescendant(Truffle.getRuntime().createCallTarget(newRoot));
                }
                lastRoot = newRoot;
            }
            return Truffle.getRuntime().createCallTarget(root);
        }

        private static final class TestRoot extends RootNode {

            @Child BaseNode child;
            private final SourceSection sourceSection;

            TestRoot(SyntheticRootsLanguage language, SourceSection sourceSection, boolean hasTag, boolean hasStatement) {
                super(language);
                this.sourceSection = sourceSection;
                if (hasTag) {
                    child = new TestTaggedRoot(sourceSection, hasStatement);
                } else if (hasStatement) {
                    child = new Statement(sourceSection);
                }
            }

            void setDescendant(CallTarget descendant) {
                if (child instanceof TestTaggedRoot) {
                    ((TestTaggedRoot) child).setDescendant(descendant);
                } else {
                    assert this.child == null;
                    this.child = insert(new Call(sourceSection, descendant));
                    notifyInserted(this.child);
                }
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                if (child != null) {
                    child.execute(frame);
                }
                return 42;
            }
        }

        abstract static class BaseNode extends Node implements InstrumentableNode {

            protected final SourceSection sourceSection;

            protected BaseNode(SourceSection sourceSection) {
                this.sourceSection = sourceSection;
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }

            abstract void execute(VirtualFrame frame);

        }

        @GenerateWrapper
        static class TestTaggedRoot extends BaseNode {

            @Child BaseNode child;

            TestTaggedRoot(SourceSection sourceSection, boolean hasStatement) {
                super(sourceSection);
                if (hasStatement) {
                    this.child = new Statement(sourceSection);
                }
            }

            TestTaggedRoot(TestTaggedRoot orig) {
                super(orig.sourceSection);
                this.child = orig.child;
            }

            void setDescendant(CallTarget descendant) {
                assert this.child == null;
                this.child = new Call(sourceSection, descendant);
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new TestTaggedRootWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.RootTag.class;
            }

            @Override
            public void execute(VirtualFrame frame) {
                if (child != null) {
                    child.execute(frame);
                }
            }
        }

        @GenerateWrapper
        static class Call extends BaseNode {

            @Child private DirectCallNode callNode;

            Call(SourceSection sourceSection, CallTarget descendant) {
                super(sourceSection);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(descendant));
            }

            Call(Call orig) {
                super(orig.sourceSection);
                callNode = orig.callNode;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new CallWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.CallTag.class;
            }

            @Override
            public void execute(VirtualFrame frame) {
                callNode.call();
            }
        }

        @GenerateWrapper
        static class Statement extends BaseNode {

            Statement(SourceSection sourceSection) {
                super(sourceSection);
            }

            Statement(Statement orig) {
                super(orig.sourceSection);
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new StatementWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.StatementTag.class;
            }

            @Override
            public void execute(VirtualFrame frame) {
            }
        }
    }
}
