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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class LegacyLanguageSPITest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @After
    public void cleanup() {
        ProxyLanguage.setDelegate(new ProxyLanguage());
    }

    @Test
    public void testCancelExecutionWhileSleeping() throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(1);
        try {
            Engine engine = Engine.create();
            Context context = Context.newBuilder(LanguageSPITestLanguage.ID).engine(engine).build();

            CountDownLatch beforeSleep = new CountDownLatch(1);
            CountDownLatch interrupt = new CountDownLatch(1);
            AtomicInteger gotInterrupt = new AtomicInteger(0);
            Function<TruffleLanguage.Env, Object> f = new Function<TruffleLanguage.Env, Object>() {
                public Object apply(TruffleLanguage.Env t) {
                    try {
                        beforeSleep.countDown();
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        gotInterrupt.incrementAndGet();
                        interrupt.countDown();
                        throw new LegacyInterrupted();
                    }
                    return null;
                }
            };
            Future<Value> future = service.submit(() -> eval(context, f));
            beforeSleep.await(10000, TimeUnit.MILLISECONDS);
            context.close(true);

            interrupt.await(10000, TimeUnit.MILLISECONDS);
            assertEquals(1, gotInterrupt.get());

            try {
                future.get();
                fail();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof PolyglotException)) {
                    throw new AssertionError(cause);
                }
                PolyglotException polyglotException = (PolyglotException) cause;
                assertTrue(polyglotException.isCancelled());
            }
            engine.close();
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void testExceptionGetSourceLocation() {
        try (Context context = Context.create(LanguageSPITestLanguage.ID)) {
            final String text = "0123456789";
            LanguageSPITestLanguage.runinside = (env) -> {
                Source src = Source.newBuilder(LanguageSPITestLanguage.ID, text, "test.txt").build();
                throw new LegacyParseException(src, 1, 2);
            };
            try {
                context.eval(LanguageSPITestLanguage.ID, text);
                Assert.fail("PolyglotException expected.");
            } catch (PolyglotException pe) {
                Assert.assertTrue(pe.isSyntaxError());
                Assert.assertEquals("12", pe.getSourceLocation().getCharacters().toString());
            } finally {
                LanguageSPITestLanguage.runinside = null;
            }
        }
    }

    @Test
    public void testLanguageErrorDuringInitialization() {
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeContext(ProxyLanguage.LanguageContext c) throws Exception {
                throw new LegacyTestError();
            }
        });

        Context context = Context.create();
        try {
            context.eval(ProxyLanguage.ID, "");
            fail();
        } catch (PolyglotException e) {
            assertTrue(e.isGuestException());
            assertEquals("testLanguageErrorDuringInitialization", e.getSourceLocation().getSource().getName());
        }
        context.close();
    }

    private static Value eval(Context context, Function<TruffleLanguage.Env, Object> f) {
        LanguageSPITestLanguage.runinside = f;
        try {
            return context.eval(LanguageSPITestLanguage.ID, "");
        } finally {
            LanguageSPITestLanguage.runinside = null;
        }
    }

    @SuppressWarnings({"deprecation", "serial"})
    static class LegacyInterrupted extends RuntimeException implements com.oracle.truffle.api.TruffleException {

        @Override
        public boolean isCancelled() {
            return true;
        }

        @Override
        public Node getLocation() {
            return null;
        }
    }

    @SuppressWarnings({"deprecation", "serial"})
    private static final class LegacyParseException extends RuntimeException implements com.oracle.truffle.api.TruffleException {
        private final Source source;
        private final int start;
        private final int length;

        LegacyParseException(final Source source, final int start, final int length) {
            Objects.requireNonNull(source, "Source must be non null");
            this.source = source;
            this.start = start;
            this.length = length;
        }

        @Override
        public boolean isSyntaxError() {
            return true;
        }

        @Override
        public Node getLocation() {
            return null;
        }

        @Override
        public SourceSection getSourceLocation() {
            return source.createSection(start, length);
        }
    }

    static final Source TEST_SOURCE = Source.newBuilder("", "", "testLanguageErrorDuringInitialization").build();

    @SuppressWarnings({"serial", "deprecation"})
    static class LegacyTestError extends RuntimeException implements com.oracle.truffle.api.TruffleException {

        public SourceSection getSourceLocation() {
            return TEST_SOURCE.createSection(0, 0);
        }

        public Node getLocation() {
            return null;
        }

    }
}
