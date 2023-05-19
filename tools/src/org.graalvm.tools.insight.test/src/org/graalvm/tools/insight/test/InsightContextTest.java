/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.tools.insight.Insight;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class InsightContextTest {
    public static final class InsightTestLanguage extends ProxyLanguage {
        int parsingCounter;
        int executingCounter;
        TruffleContext lastContext;
        String lastFunctionName;
        Node lastNode;
        final List<ParsingNode.OnEnterCallback> onEventCallbacks = new ArrayList<>();
        final List<ParsingNode.OnSourceCallback> onSourceCallbacks = new ArrayList<>();
        final List<ParsingNode.OnCloseCallback> onCloseCallbacks = new ArrayList<>();

        public TruffleContext lastContext() {
            TruffleContext tc = lastContext;
            assertNotNull("There is a last context", tc);
            lastContext = null;
            return tc;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            parsingCounter++;

            int idx = request.getArgumentNames().indexOf("insight");
            assertNotEquals("insight is an argument", -1, idx);

            return new ParsingNode(idx).getCallTarget();
        }

        class ParsingNode extends RootNode {
            private final ContextReference<LanguageContext> ref = ContextReference.create(ProxyLanguage.class);
            private final int idx;

            ParsingNode(int idx) {
                super(languageInstance);
                this.idx = idx;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                executingCounter++;
                Object insightObject = frame.getArguments()[idx];

                InsightAPI api = Value.asValue(insightObject).as(InsightAPI.class);
                assertNotNull("API found", api);

                TruffleContext expectedContext = ref.get(this).getEnv().getContext();
                final InsightAPI.OnEventHandler callback = new OnEnterCallback(expectedContext);
                api.on("enter", callback, InsightObjectFactory.createConfig(false, false, true, null, null));
                api.on("source", new OnSourceCallback(expectedContext));
                api.on("close", new OnCloseCallback(expectedContext));

                return insightObject;
            }

            abstract class AssertContext {
                private final Reference<TruffleContext> expectedContext;

                protected AssertContext(TruffleContext expectedContext) {
                    this.expectedContext = new WeakReference<>(expectedContext);
                }

                protected final TruffleContext getExpectedContext() {
                    return expectedContext.get();
                }
            }

            final class OnEnterCallback extends AssertContext implements InsightAPI.OnEventHandler {

                OnEnterCallback(TruffleContext expectedContext) {
                    super(expectedContext);
                    onEventCallbacks.add(this);
                }

                @Override
                public void event(InsightAPI.OnEventHandler.Context ctx, Map<String, Object> frame) {
                    lastFunctionName = ctx.name();
                    TruffleContext currentContext = ref.get(ParsingNode.this).getEnv().getContext();
                    assertEquals("OnEnterCallback is called with expected context", getExpectedContext(), currentContext);
                    lastContext = currentContext;

                    Truffle.getRuntime().iterateFrames((frameInstance) -> {
                        lastNode = frameInstance.getCallNode();
                        return null;
                    });
                }

                @Override
                public String toString() {
                    return "[OnEnterCallback: " + getExpectedContext() + "]";
                }
            }

            final class OnSourceCallback extends AssertContext implements InsightAPI.OnSourceLoadedHandler {
                int sourceLoadedCounter;
                String name;

                OnSourceCallback(TruffleContext expectedContext) {
                    super(expectedContext);
                    onSourceCallbacks.add(this);
                }

                @Override
                public void sourceLoaded(InsightAPI.SourceInfo info) {
                    sourceLoadedCounter++;
                    name = info.name();
                    TruffleContext currentContext = ref.get(ParsingNode.this).getEnv().getContext();
                    assertEquals("OnEnterCallback is called with expected context", getExpectedContext(), currentContext);
                }

                @Override
                public String toString() {
                    return "[OnSourceCallback: " + getExpectedContext() + "]";
                }
            }

            final class OnCloseCallback extends AssertContext implements InsightAPI.OnCloseHandler {
                int closeCounter;

                OnCloseCallback(TruffleContext expectedContext) {
                    super(expectedContext);
                    onCloseCallbacks.add(this);
                }

                @Override
                public void closed() {
                    closeCounter++;
                    TruffleContext currentContext = ref.get(ParsingNode.this).getEnv().getContext();
                    assertEquals("OnEnterCallback is called with expected context", getExpectedContext(), currentContext);
                }

                @Override
                public String toString() {
                    return "[OnSourceCallback: " + getExpectedContext() + "]";
                }
            }
        }
    }

    @Before
    public void cleanAgentObject() {
        InsightObjectFactory.cleanAgentObject();
    }

    @Test
    public void sharedEngineTest() throws Exception {
        InsightTestLanguage itl = new InsightTestLanguage();

        ProxyLanguage.setDelegate(itl);
        Engine sharedEngine = Engine.create();

        Source insightScript = Source.newBuilder(ProxyLanguage.ID, "\n" + "\n" + "\n",
                        "insight.script").build();

        registerInsight(sharedEngine, insightScript);

        Reference<?> closedContext1;
        Context c1 = InsightObjectFactory.newContext(Context.newBuilder().engine(sharedEngine));
        try {
            // @formatter:off
            Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                "ROOT(\n" +
                "  DEFINE(foo,\n" +
                "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                "  ),\n" +
                "  CALL(foo)\n" +
                ")",
                "sample.px"
            ).build();
            // @formatter:on
            c1.eval(sampleScript);

            assertEquals("Parsed once", 1, itl.parsingCounter);
            assertEquals("Executed once", 1, itl.executingCounter);

            TruffleContext c1LanguageInfo = itl.lastContext();
            assertEquals("Function foo has been called", "foo", itl.lastFunctionName);

            Node sampleNode = itl.lastNode;
            assertAgentNodes(sampleNode, 1);

            Reference<?> closedContext2;
            Context c2 = InsightObjectFactory.newContext(Context.newBuilder().engine(sharedEngine));
            try {
                c2.eval(sampleScript);
                // @formatter:off
                Source anotherScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                    "ROOT(\n" +
                    "  DEFINE(bar,\n" +
                    "    LOOP(5, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                    "  ),\n" +
                    "  CALL(bar)\n" +
                    ")",
                    "another.px"
                ).build();
                // @formatter:on
                c2.eval(anotherScript);

                Node anotherNode = itl.lastNode;
                TruffleContext c2LanguageInfo = itl.lastContext();
                assertNotEquals(c1LanguageInfo, c2LanguageInfo);

                assertEquals("Executed second time for second context", 2, itl.executingCounter);
                assertEquals("Parsed once as the source is cache", 1, itl.parsingCounter);

                assertAgentNodes(sampleNode, 1);
                assertAgentNodes(anotherNode, 1);

                closedContext1 = new WeakReference<>(c1LanguageInfo);
                closedContext2 = new WeakReference<>(c2LanguageInfo);
                c1LanguageInfo = null;
                c2LanguageInfo = null;
            } finally {
                c2.close();
                c2 = null;
            }

            GCUtils.assertGc("2nd context can disappear when closed", closedContext2);
        } finally {
            c1.close();
            c1 = null;
        }
        GCUtils.assertGc("Both contexts can disappear when closed", closedContext1);

        assertEquals("Two on enter callbacks: " + itl.onEventCallbacks, 2, itl.onEventCallbacks.size());
        assertEquals("Two source callbacks: " + itl.onSourceCallbacks, 2, itl.onSourceCallbacks.size());
        for (InsightTestLanguage.ParsingNode.OnSourceCallback callback : itl.onSourceCallbacks) {
            assertEquals("One loaded source", 1, callback.sourceLoadedCounter);
        }
        assertEquals("First context on source sees only first context load", "sample.px", itl.onSourceCallbacks.get(0).name);
        assertEquals("Second context on source sees only first context load", "another.px", itl.onSourceCallbacks.get(1).name);
        assertEquals("Two close callbacks: " + itl.onCloseCallbacks, 2, itl.onCloseCallbacks.size());
        for (InsightTestLanguage.ParsingNode.OnCloseCallback callback : itl.onCloseCallbacks) {
            assertEquals("Each is closed once", 1, callback.closeCounter);
        }
    }

    @Test
    public void closeHooksSoonerThanContext() throws Exception {
        closeHooksCheck(1);
    }

    @Test
    public void reuseTheInsightScriptMultipleTimes() throws Exception {
        closeHooksCheck(2);
    }

    private static void closeHooksCheck(int count) throws Exception {
        InsightTestLanguage itl = new InsightTestLanguage();

        ProxyLanguage.setDelegate(itl);
        Engine sharedEngine = Engine.create();

        Source insightScript = Source.newBuilder(ProxyLanguage.ID, "\n" + "\n" + "\n",
                        "insight.script").build();

        for (int i = 0; i < count; i++) {
            AutoCloseable insightHandle = registerInsight(sharedEngine, insightScript);

            try (Context c = InsightObjectFactory.newContext(Context.newBuilder().engine(sharedEngine))) {
                // @formatter:off
                Source sampleScript = Source.newBuilder(InstrumentationTestLanguage.ID,
                    "ROOT(\n" +
                    "  DEFINE(foo,\n" +
                    "    LOOP(10, STATEMENT(EXPRESSION,EXPRESSION))\n" +
                    "  ),\n" +
                    "  CALL(foo)\n" +
                    ")",
                    "sample.px"
                ).build();
                // @formatter:on
                c.eval(sampleScript);

                assertEquals("Parsed once", 1, itl.parsingCounter);
                assertEquals("Executed i-th time", i + 1, itl.executingCounter);
                assertEquals("Function foo has been called", "foo", itl.lastFunctionName);

                Node sampleNode = itl.lastNode;
                assertAgentNodes(sampleNode, 1);

                insightHandle.close();
            }
        }

        assertEquals("One on enter callbacks: " + itl.onEventCallbacks, count, itl.onEventCallbacks.size());
        assertEquals("One source callbacks: " + itl.onSourceCallbacks, count, itl.onSourceCallbacks.size());
        for (InsightTestLanguage.ParsingNode.OnSourceCallback callback : itl.onSourceCallbacks) {
            assertEquals("One loaded source", 1, callback.sourceLoadedCounter);
            break;
        }
        assertEquals("One close callbacks: " + itl.onCloseCallbacks, count, itl.onCloseCallbacks.size());
        for (InsightTestLanguage.ParsingNode.OnCloseCallback callback : itl.onCloseCallbacks) {
            assertEquals("No close callback is made", 0, callback.closeCounter);
        }
    }

    private static List<? extends Node> assertAgentNodes(Node rootNode, final int nodeCount) throws ClassNotFoundException {
        assertNotNull("Root node must be specified", rootNode);

        Class<? extends Node> aenClass = Class.forName("com.oracle.truffle.tools.agentscript.impl.InsightHookNode").asSubclass(Node.class);
        List<? extends Node> aenList = NodeUtil.findAllNodeInstances(rootNode.getRootNode(), aenClass);
        if (aenList.size() == nodeCount) {
            return aenList;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Expecting ").append(nodeCount).append(" nodes but got ").append(aenList.size()).append(":\n");
        for (Node n : aenList) {
            sb.append("  ").append(n).append("\n");
        }
        fail(sb.toString());
        return null;
    }

    @SuppressWarnings("unchecked")
    private static AutoCloseable registerInsight(Engine sharedEngine, Source insightScript) {
        Function<Source, AutoCloseable> insight = sharedEngine.getInstruments().get(Insight.ID).lookup(Function.class);
        return insight.apply(insightScript);
    }

}
