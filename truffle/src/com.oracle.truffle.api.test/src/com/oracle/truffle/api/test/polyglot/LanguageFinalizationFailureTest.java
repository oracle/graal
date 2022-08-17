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
package com.oracle.truffle.api.test.polyglot;

import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;

public class LanguageFinalizationFailureTest {
    private static final Node DUMMY_NODE = new Node() {
    };

    @ExportLibrary(InteropLibrary.class)
    static final class DummyRuntimeException extends AbstractTruffleException {
        private static final long serialVersionUID = 5292066718048069141L;

        DummyRuntimeException(Node location) {
            super("Dummy runtime error.", location);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.RUNTIME_ERROR;
        }
    }

    private static final AtomicBoolean disposeCalled = new AtomicBoolean();

    @Before
    @After
    public void cleanStaticBooleans() {
        disposeCalled.set(false);
    }

    @Registration
    static class FinalizationFailureTruffleExceptionTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(FinalizationFailureTruffleExceptionTestLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean expectedValue = (Boolean) contextArguments[0];
            Assert.assertEquals(expectedValue, disposeCalled.get());
            return null;
        }

        @Override
        protected void finalizeContext(ExecutableContext context) {
            if (!disposeCalled.get()) {
                throw new DummyRuntimeException(DUMMY_NODE);
            }
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            disposeCalled.set(true);
        }
    }

    @Test
    public void testFinalizationFailureTruffleException() {
        try (Engine engine = Engine.create()) {
            Context context = Context.newBuilder().engine(engine).build();
            context.initialize(FinalizationFailureTruffleExceptionTestLanguage.ID);
            try {
                context.close();
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!"Dummy runtime error.".equals(pe.getMessage())) {
                    throw pe;
                }
                Assert.assertFalse(pe.isInternalError());
            }
            try (Context assertContext = Context.newBuilder().engine(engine).build()) {
                evalTestLanguage(assertContext, FinalizationFailureTruffleExceptionTestLanguage.class, "1", false);
                context.close(true);
                evalTestLanguage(assertContext, FinalizationFailureTruffleExceptionTestLanguage.class, "2", true);
            }
        }
    }

    @Registration
    static class FinalizationFailureCancelExceptionTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(FinalizationFailureCancelExceptionTestLanguage.class);

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            boolean expectedValue = (Boolean) contextArguments[0];
            Assert.assertEquals(expectedValue, disposeCalled.get());
            return null;
        }

        @Override
        protected void finalizeContext(ExecutableContext context) {
            TruffleSafepoint.pollHere(DUMMY_NODE);
            if (!disposeCalled.get()) {
                Assert.fail();
            }
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            disposeCalled.set(true);
        }
    }

    @Test
    public void testFinalizationFailureCancelException() {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try (Engine engine = Engine.newBuilder().option("log.engine.level", "FINE").logHandler(outStream).build()) {
            Context context = Context.newBuilder().engine(engine).build();
            context.initialize(FinalizationFailureCancelExceptionTestLanguage.ID);
            context.close(true);
            try (Context assertContext = Context.newBuilder().engine(engine).build()) {
                evalTestLanguage(assertContext, FinalizationFailureCancelExceptionTestLanguage.class, "1", true);
            }
            Assert.assertTrue(outStream.toString().contains(
                            "Exception was thrown while finalizing a polyglot context that is being cancelled or exited. Such exceptions are expected during cancelling or exiting."));
        }
    }

    @Registration
    static class DisposeFailureTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(DisposeFailureTestLanguage.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            if (!disposeCalled.get()) {
                disposeCalled.set(true);
                throw new DummyRuntimeException(DUMMY_NODE);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testDisposeFailure() {
        try (Context context = Context.create()) {
            context.initialize(DisposeFailureTestLanguage.ID);
            try {
                context.close();
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!"java.lang.IllegalStateException: Guest language code was run during language disposal!".equals(pe.getMessage())) {
                    throw pe;
                }
                Assert.assertTrue(pe.isInternalError());
            }
        }
    }

    @Registration
    static class DisposeCreateThreadTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(DisposeCreateThreadTestLanguage.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }

        @Override
        protected void disposeContext(ExecutableContext context) {
            AtomicReference<Throwable> throwableRef = new AtomicReference<>();
            Thread t = context.env.createThread(() -> {
            });
            t.setUncaughtExceptionHandler((t1, e) -> throwableRef.set(e));
            t.start();
            try {
                t.join();
            } catch (InterruptedException ie) {
                throw new AssertionError(ie);
            }
            Assert.assertTrue(throwableRef.get() instanceof IllegalStateException);
            Assert.assertEquals("The Context is already closed.", throwableRef.get().getMessage());
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testDisposeCreateThread() {
        try (Context context = Context.newBuilder().allowCreateThread(true).build()) {
            context.initialize(DisposeCreateThreadTestLanguage.ID);
        }
    }

}
