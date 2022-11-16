/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class RequireContextTest extends AbstractPolyglotTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    public RequireContextTest() {
        super();
        enterContext = false;
        needsLanguageEnv = true;
        needsInstrumentEnv = true;
    }

    @Test
    public void testGetCurrentContext() {
        setupEnv(Context.create());
        assertFails(() -> LanguageContext.get(null), AssertionError.class);
        context.enter();
        try {
            assertEquals(languageEnv, LanguageContext.get(null).getEnv());
        } finally {
            context.leave();
        }
    }

    @Test
    public void testInstrument() throws Exception {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(true).getCallTarget();
            }
        });
        assertFails(() -> instrumentEnv.getTruffleFile(null, "file"), IllegalStateException.class, NoCurrentContextVerifier.INSTANCE);
        assertFails(() -> instrumentEnv.getTruffleFile(null, Paths.get(".").toAbsolutePath().toUri()), IllegalStateException.class, NoCurrentContextVerifier.INSTANCE);
        assertFails(() -> instrumentEnv.parse(Source.newBuilder(ProxyLanguage.ID, "", "test").build()), IllegalStateException.class, NoCurrentContextVerifier.INSTANCE);
        assertFails(() -> instrumentEnv.lookup(instrumentEnv.getLanguages().get(LanguageWithService.ID), Service.class), IllegalStateException.class, NoCurrentContextVerifier.INSTANCE);
        context.enter();
        try {
            assertNotNull(instrumentEnv.getTruffleFile(null, "file"));
            assertNotNull(instrumentEnv.getTruffleFile(null, Paths.get(".").toAbsolutePath().toUri()));
            assertTrue((boolean) instrumentEnv.parse(Source.newBuilder(ProxyLanguage.ID, "", "test").build()).call());
            assertNotNull(instrumentEnv.lookup(instrumentEnv.getLanguages().get(LanguageWithService.ID), Service.class));
        } finally {
            context.leave();
        }
    }

    @Test
    public void testContextFile() throws Exception {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
                return RootNode.createConstantNode(true).getCallTarget();
            }
        });
        context.enter();
        try {
            TruffleContext truffleContext = instrumentEnv.getEnteredContext();
            AtomicReference<Throwable> throwable = new AtomicReference<>();
            Thread thread = instrumentEnv.createSystemThread(() -> {
                try {
                    assertNull(instrumentEnv.getEnteredContext());
                    assertFails(() -> instrumentEnv.getTruffleFile(null, "file"), IllegalStateException.class, NoCurrentContextVerifier.INSTANCE);
                    assertNotNull(instrumentEnv.getTruffleFile(truffleContext, "file"));
                    assertNotNull(instrumentEnv.getTruffleFile(truffleContext, Paths.get(".").toAbsolutePath().toUri()));
                } catch (Throwable t) {
                    throwable.set(t);
                }
            });
            thread.start();
            thread.join();
            assertNull(throwable.get());
        } finally {
            context.close();
        }
    }

    private static final class NoCurrentContextVerifier implements Consumer<IllegalStateException> {

        static final NoCurrentContextVerifier INSTANCE = new NoCurrentContextVerifier();

        private NoCurrentContextVerifier() {
        }

        @Override
        public void accept(IllegalStateException ise) {
            assertEquals("There is no current context available.", ise.getMessage());
        }
    }

    public interface Service {
    }

    @Registration(id = LanguageWithService.ID, name = LanguageWithService.ID, characterMimeTypes = LanguageWithService.MIME, services = {Service.class})
    public static final class LanguageWithService extends TruffleLanguage<TruffleLanguage.Env> {

        static final String ID = "RequireContextTest/LanguageWithService";
        static final String MIME = "text/x-LanguageWithService";

        @Override
        protected Env createContext(Env env) {
            env.registerService(new Service() {
            });
            return env;
        }
    }
}
