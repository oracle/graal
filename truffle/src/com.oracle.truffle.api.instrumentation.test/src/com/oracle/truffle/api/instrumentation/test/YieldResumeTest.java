/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.graalvm.polyglot.Source;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test of yield/resume.
 */
public class YieldResumeTest extends AbstractInstrumentationTest {

    public YieldResumeTest() {
        needsInstrumentEnv = true;
    }

    @Test
    public void testYieldResumeBasicListener() throws IOException {
        List<String> events = new ArrayList<>();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {

            @Override
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("enter " + c.getInstrumentedNode().getClass().getSimpleName());
            }

            @Override
            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                events.add("return " + c.getInstrumentedNode().getClass().getSimpleName());
            }

            @Override
            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                events.add("exception " + c.getInstrumentedNode().getClass().getSimpleName());
            }
        });
        run(Source.create(YieldResumeLanguage.ID, "yield"));
        run(Source.create(YieldResumeLanguage.ID, "resume"));
        Assert.assertEquals("[enter YRBlockNode, enter YieldTestNode, return YieldTestNode, return YRBlockNode, enter YRBlockNode, enter ResumeTestNode, return ResumeTestNode, return YRBlockNode]",
                        events.toString());
    }

    @Test
    public void testYieldResume() throws IOException {
        List<String> events = new ArrayList<>();
        instrumentEnv.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {

            @Override
            public void onEnter(EventContext c, VirtualFrame frame) {
                events.add("enter " + c.getInstrumentedNode().getClass().getSimpleName());
            }

            @Override
            public void onReturnValue(EventContext c, VirtualFrame frame, Object result) {
                events.add("return " + c.getInstrumentedNode().getClass().getSimpleName());
            }

            @Override
            public void onReturnExceptional(EventContext c, VirtualFrame frame, Throwable exception) {
                events.add("exception " + c.getInstrumentedNode().getClass().getSimpleName());
            }

            @Override
            public void onYield(EventContext c, VirtualFrame frame, Object value) {
                events.add("yield " + c.getInstrumentedNode().getClass().getSimpleName());
            }

            @Override
            public void onResume(EventContext c, VirtualFrame frame) {
                events.add("resume " + c.getInstrumentedNode().getClass().getSimpleName());
            }
        });
        run(Source.create(YieldResumeLanguage.ID, "yield"));
        run(Source.create(YieldResumeLanguage.ID, "resume"));
        Assert.assertEquals("[enter YRBlockNode, enter YieldTestNode, yield YieldTestNode, return YRBlockNode, enter YRBlockNode, resume ResumeTestNode, return ResumeTestNode, return YRBlockNode]",
                        events.toString());
    }

    @TruffleLanguage.Registration(id = YieldResumeLanguage.ID, name = "Yield Resume Language", version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    @ProvidedTags({StandardTags.StatementTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class})
    public static class YieldResumeLanguage extends ProxyLanguage {

        public static final String ID = "truffle-yield-resume-language";
        private static final LanguageReference<YieldResumeLanguage> REFERENCE = LanguageReference.create(YieldResumeLanguage.class);

        private final LinkedList<MaterializedFrame> yieldFrames = new LinkedList<>();
        private final FrameDescriptor descriptor = new FrameDescriptor();

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final com.oracle.truffle.api.source.Source code = request.getSource();
            return new RootNode(this, descriptor) {

                @Node.Child private YRBlockNode block = parse(code);

                @Override
                public Object execute(VirtualFrame frame) {
                    return block.execute(frame);
                }

            }.getCallTarget();
        }

        private static YRBlockNode parse(com.oracle.truffle.api.source.Source code) {
            String text = code.getCharacters().toString();
            YRBlockNode block = new YRBlockNode();
            if (text.startsWith("y")) {
                block.child = new YieldTestNode(code);
            } else if (text.startsWith("r")) {
                block.child = new ResumeTestNode(code);
            } else {
                block.child = new YRNoopTestNode(code);
            }
            return block;
        }

        @GenerateWrapper
        static class YRBlockNode extends Node implements InstrumentableNode {

            @Child YRNode child;

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new YRBlockNodeWrapper(this, probe);
            }

            public Object execute(VirtualFrame frame) {
                try {
                    return child.doRun(frame);
                } catch (YieldTestException ex) {
                    return ex.getYieldValue();
                }
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.RootTag.class;
            }
        }

        abstract static class YRNode extends Node {

            protected final com.oracle.truffle.api.source.Source code;

            YRNode(com.oracle.truffle.api.source.Source code) {
                this.code = code;
            }

            public abstract Object doRun(VirtualFrame frame);

            @Override
            public SourceSection getSourceSection() {
                return code.createSection(1);
            }
        }

        @GenerateWrapper(yieldExceptions = YieldTestException.class)
        static class YieldTestNode extends YRNode implements InstrumentableNode {

            YieldTestNode(com.oracle.truffle.api.source.Source code) {
                super(code);
            }

            YieldTestNode(YieldTestNode node) {
                super(node.code);
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new YieldTestNodeWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.StatementTag.class;
            }

            @Override
            public Object doRun(VirtualFrame frame) {
                return execute(frame);
            }

            public Object execute(VirtualFrame frame) {
                storeYieldFrame(frame.materialize());
                throw new YieldTestException();
            }

            @CompilerDirectives.TruffleBoundary
            private void storeYieldFrame(MaterializedFrame frame) {
                REFERENCE.get(this).yieldFrames.add(frame);
            }
        }

        public static class YieldTestException extends RuntimeException implements GenerateWrapper.YieldException {

            private static final long serialVersionUID = 0L;

            @Override
            public Object getYieldValue() {
                return "Promise";
            }
        }

        @GenerateWrapper(resumeMethodPrefix = "resume")
        static class ResumeTestNode extends YRNode implements InstrumentableNode {

            ResumeTestNode(com.oracle.truffle.api.source.Source code) {
                super(code);
            }

            ResumeTestNode(ResumeTestNode node) {
                super(node.code);
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new ResumeTestNodeWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.StatementTag.class;
            }

            @Override
            public Object doRun(VirtualFrame frame) {
                MaterializedFrame yieldFrame = getYieldFrame();
                return resumeExecute(yieldFrame);
            }

            @CompilerDirectives.TruffleBoundary
            private MaterializedFrame getYieldFrame() {
                return REFERENCE.get(this).yieldFrames.removeFirst();
            }

            public Object resumeExecute(@SuppressWarnings("unused") VirtualFrame frame) {
                return "Resumed.";
            }
        }

        @GenerateWrapper
        static class YRNoopTestNode extends YRNode implements InstrumentableNode {

            YRNoopTestNode(com.oracle.truffle.api.source.Source code) {
                super(code);
            }

            YRNoopTestNode(YRNoopTestNode node) {
                super(node.code);
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new YRNoopTestNodeWrapper(this, this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.StatementTag.class;
            }

            @Override
            public Object doRun(VirtualFrame frame) {
                return execute(frame);
            }

            public Object execute(@SuppressWarnings("unused") VirtualFrame frame) {
                return "Statement";
            }
        }
    }
}
