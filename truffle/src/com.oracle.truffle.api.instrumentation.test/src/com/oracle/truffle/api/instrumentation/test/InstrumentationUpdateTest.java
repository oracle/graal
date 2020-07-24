/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public class InstrumentationUpdateTest {

    private static Function<InstrumentationUpdateLanguage, RootNode> run;

    private Context context;
    private final List<EventContext> executionEvents = new ArrayList<>();
    private final List<LoadSourceSectionEvent> loadEvents = new ArrayList<>();
    private InstrumentationUpdateLanguage language;
    private TruffleInstrument.Env instrumentEnv;
    private EventBinding<?> eventBinding;

    @Before
    public void setup() {
        context = Context.create("InstrumentationUpdateLanguage");
        run = (lang) -> {
            this.language = lang;
            return RootNode.createConstantNode(42);
        };
        context.eval("InstrumentationUpdateLanguage", "");
        instrumentEnv = context.getEngine().getInstruments().get("InstrumentationUpdateInstrument").lookup(TruffleInstrument.Env.class);
    }

    @After
    public void teardown() {
        context.close();
    }

    /*
     * Test that if indexed based filters were applied we are notified if the an instrumentable node
     * was not contained within a root node's source section. (that was not the case at some point).
     */
    @Test
    public void testNotWithinRootSourceSection() {
        setEventFilter(SourceSectionFilter.newBuilder().indexIn(6, 5).build());
        MyRoot evalRoot = eval((lang) -> {
            MyRoot root = new MyRoot(language, language.request.getSource().createSection(0, 5));
            root.child = new InstrumentationUpdateNode(language.request.getSource().createSection(6, 5));
            return root;
        }, "root1, root2");

        assertLoaded(evalRoot.getChild());
        assertExecuted(evalRoot.getChild());
    }

    /*
     * Test that we can change an instrumentable node after the first execute by notifying the
     * framework.
     */
    @Test
    public void testInsertInstrumentableNode() {
        setEventFilter(SourceSectionFilter.newBuilder().indexIn(6, 5).build());
        MyRoot evalRoot = eval((lang) -> {
            MyRoot root = new MyRoot(language, language.request.getSource().createSection(0, 5));
            root.child = new InstrumentationUpdateNode(language.request.getSource().createSection(0, 5));
            return root;
        }, "root1, root2");

        evalRoot.setChild(new InstrumentationUpdateNode(evalRoot.getSourceSection().getSource().createSection(6, 5)));

        assertLoaded();
        assertExecuted();
        evalRoot.getCallTarget().call();

        assertLoaded();
        assertExecuted();

        // trigger the notification, now we get events
        evalRoot.notifyChildInsert();

        assertLoaded(evalRoot.getChild());
        assertExecuted();

        evalRoot.getCallTarget().call();

        assertLoaded(evalRoot.getChild());
        assertExecuted(evalRoot.getChild());
    }

    /*
     * Test that we can change instrumentable nodes after the first execute by notifying the
     * framework.
     */
    @Test
    public void testInsertInstrumentableNodes() {
        setEventFilter(SourceSectionFilter.newBuilder().indexIn(0, 6).build());
        MyRoot evalRoot = eval((lang) -> {
            MyRoot root = new MyRoot(language, language.request.getSource().createSection(0, 5));
            return root;
        }, "abcdef");

        evalRoot.getCallTarget().call();

        assertLoaded();
        assertExecuted();

        evalRoot.setChild(new InstrumentationUpdateNode(evalRoot.getSourceSection().getSource().createSection(0, 1)));
        evalRoot.notifyChildInsert();

        assertLoaded(evalRoot.getChild());
        assertExecuted();

        evalRoot.getCallTarget().call();

        assertLoaded(evalRoot.getChild());
        assertExecuted(evalRoot.getChild());

        executionEvents.clear();
        loadEvents.clear();

        evalRoot.setChild(new InstrumentationUpdateNode(evalRoot.getSourceSection().getSource().createSection(1, 2)));
        evalRoot.getChild().setChild(new InstrumentationUpdateNode(evalRoot.getSourceSection().getSource().createSection(2, 3)));

        evalRoot.notifyChildInsert();

        assertLoaded(evalRoot.getChild(), evalRoot.getChild().getChild());
        assertExecuted();

        evalRoot.getCallTarget().call();

        assertLoaded(evalRoot.getChild(), evalRoot.getChild().getChild());
        assertExecuted(evalRoot.getChild(), evalRoot.getChild().getChild());
    }

    private void assertLoaded(Node... children) {
        Iterator<LoadSourceSectionEvent> loadIterator = loadEvents.iterator();
        for (Node loadedChild : children) {
            Assert.assertTrue(loadIterator.hasNext());
            Assert.assertSame(loadedChild, loadIterator.next().getNode());
        }
        Assert.assertFalse(loadIterator.hasNext());
    }

    private void assertExecuted(Node... children) {
        Iterator<EventContext> executeIterator = executionEvents.iterator();
        for (Node loadedChild : children) {
            Assert.assertTrue(executeIterator.hasNext());
            Assert.assertSame(loadedChild, executeIterator.next().getInstrumentedNode());
        }
        Assert.assertFalse(executeIterator.hasNext());
    }

    private void setEventFilter(SourceSectionFilter filter) {
        EventBinding<?> old = this.eventBinding;
        eventBinding = instrumentEnv.getInstrumenter().attachExecutionEventListener(filter, new ExecutionEventListener() {
            public void onReturnValue(EventContext ctx, VirtualFrame frame, Object result) {
            }

            public void onReturnExceptional(EventContext ctx, VirtualFrame frame, Throwable exception) {
            }

            public void onEnter(EventContext ctx, VirtualFrame frame) {
                executionEvents.add(ctx);
            }
        });

        instrumentEnv.getInstrumenter().attachLoadSourceSectionListener(filter, new LoadSourceSectionListener() {
            public void onLoad(LoadSourceSectionEvent event) {
                loadEvents.add(event);
            }
        }, true);

        // dispose afterwards to avoid disposal of instrumentation wrappers
        if (old != null) {
            old.dispose();
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends RootNode> T eval(Function<InstrumentationUpdateLanguage, T> target, String source) {
        executionEvents.clear();
        RootNode[] roots = new RootNode[1];
        run = (lang) -> {
            T root = target.apply(lang);
            roots[0] = root;
            return root;
        };
        org.graalvm.polyglot.Source src = org.graalvm.polyglot.Source.create("InstrumentationUpdateLanguage", source);
        context.eval(src);
        return (T) roots[0];
    }

    @GenerateWrapper
    public static class InstrumentationUpdateNode extends Node implements InstrumentableNode {

        private final SourceSection sourceSection;
        @Child InstrumentationUpdateNode child;

        public InstrumentationUpdateNode(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        public void setChild(InstrumentationUpdateNode child) {
            this.child = insert(child);
        }

        public InstrumentationUpdateNode getChild() {
            if (child instanceof WrapperNode) {
                return (InstrumentationUpdateNode) ((WrapperNode) child).getDelegateNode();
            }
            return child;
        }

        void notifyChildInsert() {
            notifyInserted(child);
        }

        public void execute(VirtualFrame frame) {
            if (child != null) {
                child.execute(frame);
            }
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        public boolean isInstrumentable() {
            return getSourceSection() != null;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            return new InstrumentationUpdateNodeWrapper(sourceSection, this, probe);
        }

    }

    private static class MyRoot extends RootNode {

        final SourceSection sourceSection;
        @Child InstrumentationUpdateNode child;

        MyRoot(TruffleLanguage<?> language, SourceSection sourceSection) {
            super(language);
            this.sourceSection = sourceSection;
        }

        public void setChild(InstrumentationUpdateNode child) {
            this.child = insert(child);
        }

        public InstrumentationUpdateNode getChild() {
            if (child instanceof WrapperNode) {
                return (InstrumentationUpdateNode) ((WrapperNode) child).getDelegateNode();
            }
            return child;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        void notifyChildInsert() {
            notifyInserted(child);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (child != null) {
                child.execute(frame);
            }
            return "";
        }

    }

    @TruffleLanguage.Registration(id = "InstrumentationUpdateLanguage", name = "", version = "")
    public static class InstrumentationUpdateLanguage extends TruffleLanguage<Object> {

        Env env;
        ParsingRequest request;

        @Override
        protected CallTarget parse(@SuppressWarnings("hiding") com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
            this.request = request;
            return Truffle.getRuntime().createCallTarget(run.apply(this));
        }

        @Override
        protected Object createContext(@SuppressWarnings("hiding") com.oracle.truffle.api.TruffleLanguage.Env env) {
            this.env = env;
            return null;
        }

    }

    @TruffleInstrument.Registration(id = "InstrumentationUpdateInstrument", services = TruffleInstrument.Env.class)
    public static class InstrumentationUpdateInstrument extends TruffleInstrument {

        @Override
        protected void onCreate(Env env) {
            env.registerService(env);
        }

    }

}
