/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.tools.insight.Insight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;

public class InsightContextTest {
    public static final class InsightTestLanguage extends ProxyLanguage {
        int parsingCounter;
        int executingCounter;
        TruffleContext lastContext;
        String lastFunctionName;
        final List<ParsingNode.OnEnterCallback> callbacks = new ArrayList<>();

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            parsingCounter++;

            int idx = request.getArgumentNames().indexOf("insight");
            assertNotEquals("insight is an argument", -1, idx);

            return Truffle.getRuntime().createCallTarget(new ParsingNode(idx));
        }

        class ParsingNode extends RootNode {
            private final ContextReference<LanguageContext> ref = ContextReference.create(ProxyLanguage.class);
            private final int idx;

            ParsingNode(int idx) {
                super(languageInstance);
                this.idx = idx;
            }

            @Override
            public Object execute(VirtualFrame locals) {
                executingCounter++;
                Object insightObject = locals.getArguments()[idx];

                InsightAPI api = Value.asValue(insightObject).as(InsightAPI.class);
                assertNotNull("API found", api);

                TruffleContext expectedContext = ref.get(this).getEnv().getContext();
                final InsightAPI.OnEventHandler callback = new OnEnterCallback(expectedContext);
                api.on("enter", callback, InsightObjectFactory.createConfig(false, false, true, null, null));

                return insightObject;
            }

            final class OnEnterCallback implements InsightAPI.OnEventHandler {
                private final TruffleContext expectedContext;

                OnEnterCallback(TruffleContext expectedContext) {
                    this.expectedContext = expectedContext;
                    callbacks.add(this);
                }

                @Override
                public void event(InsightAPI.OnEventHandler.Context ctx, Map<String, Object> frame) {
                    lastFunctionName = ctx.name();
                    TruffleContext currentContext = ref.get(ParsingNode.this).getEnv().getContext();
                    assertEquals("OnEnterCallback is called with expected context", expectedContext, currentContext);
                    lastContext = currentContext;
                }

                @Override
                public String toString() {
                    return "[OnEnterCallback: " + expectedContext + "]";
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
            assertEquals("Executed once", 1, itl.executingCounter);

            TruffleContext cLanguageInfo = itl.lastContext;
            assertEquals("Function foo has been called", "foo", itl.lastFunctionName);

            try (Context c2 = InsightObjectFactory.newContext(Context.newBuilder().engine(sharedEngine))) {
                c2.eval(sampleScript);
                TruffleContext c2LanguageInfo = itl.lastContext;
                assertNotEquals(cLanguageInfo, c2LanguageInfo);

                assertEquals("Executed second time for second context", 2, itl.executingCounter);
                assertEquals("Parsed once as the source is cached - but it is not yet", 2, itl.parsingCounter);
            }
            
            assertEquals("Two callbacks: " + itl.callbacks, 2, itl.callbacks.size());
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerInsight(Engine sharedEngine, Source insightScript) {
        Function<Source, AutoCloseable> insight = sharedEngine.getInstruments().get(Insight.ID).lookup(Function.class);
        insight.apply(insightScript);
    }

}
