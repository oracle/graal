/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class ParsingFromInstrumentTest {
    public static final class ParsingTestLanguage extends ProxyLanguage {
        int parsingCounter;
        int executingCounter;

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            parsingCounter++;

            int idx = request.getArgumentNames().indexOf("emptyList");

            return new ParsingNode(idx).getCallTarget();
        }

        class ParsingNode extends RootNode {
            ParsingNode(int idx) {
                super(languageInstance);
                assertNotEquals("emptyList is an argument", -1, idx);
            }

            @Override
            public Object execute(VirtualFrame locals) {
                executingCounter++;
                return executingCounter;
            }
        }
    }

    @Test
    public void parsingTest() throws Exception {
        ParsingTestLanguage itl = new ParsingTestLanguage();

        ProxyLanguage.setDelegate(itl);
        Engine sharedEngine = Engine.create();

        Source script = Source.newBuilder(ProxyLanguage.ID, "\n" + "\n" + "\n",
                        "extra.script").build();

        registerHook(sharedEngine, script);

        try (Context c = Context.newBuilder().engine(sharedEngine).build()) {
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

            try (Context c2 = Context.newBuilder().engine(sharedEngine).build()) {
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

                assertEquals("Executed second time for second context", 2, itl.executingCounter);
                assertEquals("Parsed once as the source is cached", 1, itl.parsingCounter);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerHook(Engine sharedEngine, Source script) {
        Function<Source, Void> registrar = sharedEngine.getInstruments().get(ParsingInstrument.ID).lookup(Function.class);
        registrar.apply(script);
    }

    @TruffleInstrument.Registration(id = ParsingInstrument.ID, services = Function.class)
    public static final class ParsingInstrument extends TruffleInstrument {
        public static final String ID = "com-oracle-truffle-api-instrumentation-test-ParsingFromInstrumentTest-ParsingInstrument";
        private final ContextLocal<Object[]> parsedTargets = createContextLocal((c) -> new Object[1]);

        @Override
        protected void onCreate(Env env) {
            Function<org.graalvm.polyglot.Source, Void> f = (text) -> {
                final com.oracle.truffle.api.source.Source.LiteralBuilder b = com.oracle.truffle.api.source.Source.newBuilder(text.getLanguage(), text.getCharacters(), text.getName());
                b.uri(text.getURI());
                b.mimeType(text.getMimeType());
                b.internal(text.isInternal());
                b.interactive(text.isInteractive());
                com.oracle.truffle.api.source.Source src = b.build();
                env.getInstrumenter().attachContextsListener(new ContextsListener() {
                    @Override
                    public void onContextCreated(TruffleContext context) {
                    }

                    @Override
                    public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
                        Object[] target = parsedTargets.get(context);
                        if (target[0] != null) {
                            return;
                        }
                        try {
                            target[0] = src;
                            final CallTarget parsed = env.parse(src, "emptyList");
                            target[0] = parsed;
                            parsed.call(0);
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    }

                    @Override
                    public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
                    }

                    @Override
                    public void onContextClosed(TruffleContext context) {
                    }
                }, true);
                return null;
            };
            env.registerService(f);
        }
    }
}
