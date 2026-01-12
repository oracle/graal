/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class ExceptionHandlerTest extends AbstractPolyglotTest {

    @SuppressWarnings("serial")
    public static class TestException extends RuntimeException {

        @HostAccess.Export
        public TestException(String message) {
            super(message);
        }
    }

    static void rethrowHostRuntimeException(PolyglotException e) {
        if (e.isHostException()) {
            Throwable t = e.asHostException();
            if (t instanceof RuntimeException rt) {
                throw rt;
            }
        }
        // fall-through to default behavior
    }

    @Test
    public void testHostRuntimeException() {
        AtomicInteger counter = new AtomicInteger();
        try (Context c = Context.newBuilder().exceptionHandler((e) -> {
            counter.incrementAndGet();
            rethrowHostRuntimeException(e);
        }).build()) {
            try {
                c.asValue(new TestException("test")).throwException();
                fail();
            } catch (TestException e) {
                assertEquals("test", e.getMessage());
                assertEquals(1, counter.get());
            } catch (Throwable t) {
                fail();
            }
        }
    }

    @Test
    public void testHostRuntimeExceptionFallthrough() {
        try (Context c = Context.newBuilder().exceptionHandler(ExceptionHandlerTest::rethrowHostRuntimeException).build()) {
            try {
                c.asValue(new IOException("test")).throwException();
                fail();
            } catch (PolyglotException t) {
                assertTrue(t.asHostException() instanceof IOException);
            } catch (Throwable t) {
                fail();
            }
        }
    }

    static void sneakyRethrowHostException(PolyglotException e) {
        if (e.isHostException()) {
            throw sneakyThrow(e.asHostException());
        }
        // fall-through to default behavior
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    @Test
    public void testSneakyThrowException() {
        try (Context c = Context.newBuilder().exceptionHandler(ExceptionHandlerTest::sneakyRethrowHostException).build()) {
            try {
                c.asValue(new IOException("test")).throwException();
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IOException);
                assertEquals("test", e.getMessage());
            } catch (Throwable t) {
                fail();
            }

        }
    }

    @Test
    public void testOnlyEngine() {
        try (Engine engine = Engine.newBuilder().exceptionHandler(ExceptionHandlerTest::rethrowHostRuntimeException).build()) {
            try (Context c = Context.newBuilder().engine(engine).build()) {
                try {
                    c.asValue(new TestException("test")).throwException();
                    fail();
                } catch (TestException e) {
                    assertEquals("test", e.getMessage());
                } catch (Throwable t) {
                    fail();
                }
            }
        }
    }

    @Test
    public void testSameWithEngine() {
        Consumer<PolyglotException> handler = ExceptionHandlerTest::rethrowHostRuntimeException;
        try (Engine engine = Engine.newBuilder().exceptionHandler(handler).build()) {
            try (Context c = Context.newBuilder().engine(engine).exceptionHandler(handler).build()) {
                try {
                    c.asValue(new TestException("test")).throwException();
                    fail();
                } catch (TestException e) {
                    assertEquals("test", e.getMessage());
                } catch (Throwable t) {
                    fail();
                }
            }
        }
    }

    @Test
    public void testNullWithEngine() {
        try (Engine engine = Engine.newBuilder().exceptionHandler(ExceptionHandlerTest::rethrowHostRuntimeException).build()) {
            try (Context c = Context.newBuilder().engine(engine).exceptionHandler(null).build()) {
                try {
                    c.asValue(new TestException("test")).throwException();
                    fail();
                } catch (TestException e) {
                    assertEquals("test", e.getMessage());
                } catch (Throwable t) {
                    fail();
                }
            }
        }
    }

    @Test
    public void testDifferentWithEngine() {
        try (Engine engine = Engine.newBuilder().exceptionHandler(ExceptionHandlerTest::rethrowHostRuntimeException).build()) {
            Context.Builder b = Context.newBuilder().engine(engine).exceptionHandler(ExceptionHandlerTest::sneakyRethrowHostException);
            assertFails(() -> {
                b.build();
            }, IllegalArgumentException.class, (e) -> {
                assertEquals("Contexts with explicit engines must not specify a different exception handler than the engine. " +
                                "Use Engine.newBuilder().exceptionHandler(...).build() to  configure the exception handler and pass the same handler to the context, " +
                                "or set the context exception handler to null to inherit it from the engine.",
                                e.getMessage());
            });
        }
    }

    @SuppressWarnings("serial")
    static class GuestExceptionText extends AbstractTruffleException {

        GuestExceptionText(String message, Node location) {
            super(message, location);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class InteropTest implements TruffleObject {

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("unused")
        Object execute(Object[] args, @CachedLibrary("this") InteropLibrary lib) {
            throw new GuestExceptionText("test", lib);
        }

    }

    @Test
    public void testThrowInInterop() {
        AtomicInteger counter = new AtomicInteger();
        try (Context c = Context.newBuilder().exceptionHandler((e) -> {
            counter.incrementAndGet();
            assertTrue(e.isGuestException());
            assertEquals("test", e.getMessage());
        }).build()) {
            Value v = c.asValue(new InteropTest());
            try {
                v.execute();
                fail();
            } catch (PolyglotException e) {
                assertEquals(1, counter.get());
            } catch (Throwable t) {
                fail();
            }
        }
    }

    @Registration(id = ExceptionHandlerTestLanguage.ID)
    static class ExceptionHandlerTestLanguage extends TruffleLanguage<Env> {

        public static final String ID = "ExceptionHandlerTestLanguage";

        @Override
        protected Env createContext(Env env) {
            try {
                Object symbol = env.asHostSymbol(TestException.class);
                Object clazz = InteropLibrary.getUncached().readMember(symbol, "class");
                Object hostException = InteropLibrary.getUncached().instantiate(clazz, "myMessage");
                InteropLibrary.getUncached().throwException(hostException);
                return env;
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }

    }

    @Test
    public void testThrowInContextInit() {
        TruffleTestAssumptions.assumeNoIsolateEncapsulation();
        TruffleTestAssumptions.assumeNotAOT(); // internal reflection
        AtomicInteger counter = new AtomicInteger();
        try (Context c = Context.newBuilder().exceptionHandler((e) -> {
            counter.incrementAndGet();
            rethrowHostRuntimeException(e);
        }).build()) {
            try {
                c.initialize(ExceptionHandlerTestLanguage.ID);
                fail();
            } catch (TestException e) {
                assertEquals("myMessage", e.getMessage());
                assertEquals(1, counter.get());
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            }
        }
    }

}
