/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import com.oracle.truffle.tck.tests.ValueAssert;

public class ContextSharingTest {

    private static Context context;
    private static Context secondaryContext;

    @BeforeClass
    public static void setUp() {
        context = createContext(true);
        secondaryContext = createContext(true);
    }

    private static Context createContext(boolean sharingEnabled) {
        return Context.newBuilder().allowHostAccess(HostAccess.ALL).allowValueSharing(sharingEnabled).build();
    }

    @AfterClass
    public static void tearDown() {
        if (context != null) {
            context.close();
            secondaryContext.close();
        }
    }

    static class NoIdentity implements TruffleObject {

    }

    @Test
    public void testMultiContextNoIdentity() {
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation(); // TruffleObject
        NoIdentity guest = new NoIdentity();
        Value c0v0 = secondaryContext.asValue(context.asValue(guest));
        Value c1v0 = context.asValue(secondaryContext.asValue(guest));

        assertTrue(c1v0.equals(c1v0));
        assertTrue(c0v0.equals(c0v0));
        assertFalse(c0v0.equals(c1v0));
        assertFalse(c1v0.equals(c0v0));

        ValueAssert.assertValue(c0v0);
        ValueAssert.assertValue(c1v0);
    }

    @ExportLibrary(InteropLibrary.class)
    static class WithIdentity implements TruffleObject {

        @ExportMessage
        TriState isIdenticalOrUndefined(Object other) {
            if (other instanceof WithIdentity) {
                return TriState.TRUE;
            }
            return TriState.UNDEFINED;
        }

        @ExportMessage
        @TruffleBoundary
        final int identityHashCode() {
            return System.identityHashCode(this.getClass());
        }

    }

    @Test
    public void testMultiContextWithIdentity() {
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation(); // TruffleObject
        Value c0v0 = secondaryContext.asValue(context.asValue(new WithIdentity()));
        Value c1v0 = context.asValue(secondaryContext.asValue(new WithIdentity()));

        assertTrue(c1v0.equals(c1v0));
        assertTrue(c0v0.equals(c0v0));
        assertFalse(c0v0.equals(c1v0));
        assertFalse(c1v0.equals(c0v0));

        ValueAssert.assertValue(c0v0);
        ValueAssert.assertValue(c1v0);
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static final class ExecutableThrowsError extends AbstractTruffleException {

        private final Context expectedContext;

        ExecutableThrowsError(Context c) {
            super("my message");
            this.expectedContext = c;
        }

        @ExportMessage
        @TruffleBoundary
        boolean isException() {
            assertEquals(expectedContext, Context.getCurrent());
            return true;
        }

        @ExportMessage
        RuntimeException throwException() {
            throw this;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class ExecutableThrows implements TruffleObject {

        private final Context expectedContext;

        ExecutableThrows(Context c) {
            this.expectedContext = c;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(@SuppressWarnings("unused") Object[] args) {
            assertEquals(expectedContext, Context.getCurrent());
            throw new ExecutableThrowsError(expectedContext);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }
    }

    @Test
    public void testError() {
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation(); // TruffleObject
        Value c0v0 = secondaryContext.asValue(context.asValue(new ExecutableThrows(context)));
        AbstractPolyglotTest.assertFails(() -> c0v0.execute(), PolyglotException.class, e -> {
            assertFalse(e.isInternalError());
            assertTrue(e.isGuestException());
            assertFalse(e.isHostException());
            assertEquals("my message", e.getMessage());
        });
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class ExecutableWaits implements TruffleObject {

        final CountDownLatch enterLatch;
        final CountDownLatch waitLatch;

        ExecutableWaits() {
            this.waitLatch = new CountDownLatch(1);
            this.enterLatch = new CountDownLatch(1);
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(@SuppressWarnings("unused") Object[] args) {
            try {
                enterLatch.countDown();
                waitLatch.await();
            } catch (InterruptedException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            return "done";
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class GetGuestReference implements TruffleObject {

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] args) {
            return new RelayObject((TruffleObject) args[0]);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }
    }

    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    static final class RelayObject implements TruffleObject {
        final TruffleObject delegate;

        RelayObject(TruffleObject delegate) {
            this.delegate = delegate;
        }
    }

    @Test
    public void testThreadCheck() throws InterruptedException, ExecutionException {
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation(); // TruffleObject
        ExecutorService service = Executors.newCachedThreadPool();

        Context singleThread = createContext(true);
        singleThread.initialize(SINGLE_THREAD);
        Context multiThread = createContext(true);
        multiThread.initialize(MULTI_THREAD);
        ExecutableWaits executable = new ExecutableWaits();
        /*
         * We have to make sure stValue is a guest value. singleThread.asValue(executable) produces
         * a guest value only in case of a non-isolated context. In case of an isolated context, the
         * returned value is just a host value with no relation to the guest, so executing it would
         * not add to the thread count of the guest language. Going through the GetGuestReference
         * makes stValue a guest RelayObject to the host value which is exactly what we need.
         */
        Value getGuestReference = singleThread.eval(SINGLE_THREAD, "");
        Value stValue = getGuestReference.execute(singleThread.asValue(executable));
        Value mtStValue = multiThread.asValue(stValue);

        assertTrue(stValue.canExecute());
        assertTrue(mtStValue.canExecute());

        Future<?> f = service.submit(() -> {
            mtStValue.execute();
        });

        // wait until execute is entered.
        executable.enterLatch.await();

        AbstractPolyglotTest.assertFails(() -> stValue.canExecute(), IllegalStateException.class, (e) -> {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Multi threaded access requested by thread "));
        });
        AbstractPolyglotTest.assertFails(() -> mtStValue.canExecute(), PolyglotException.class, (e) -> {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof IllegalStateException);
            assertTrue(e.getMessage(), e.getMessage().startsWith("Multi threaded access requested by thread "));
        });

        // free waiting execute
        executable.waitLatch.countDown();
        f.get();

        assertTrue(stValue.canExecute());
        assertTrue(mtStValue.canExecute());

        service.shutdownNow();
        service.awaitTermination(10L, TimeUnit.SECONDS);
    }

    static final String SINGLE_THREAD = "ContextSharingTest_SingleThread";
    static final String MULTI_THREAD = "ContextSharingTest_MultiThread";

    @Registration(id = SINGLE_THREAD, name = SINGLE_THREAD)
    public static class SingleThreadedLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return singleThreaded;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            RootNode rootNode = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return new GetGuestReference();
                }
            };
            return rootNode.getCallTarget();
        }
    }

    @Registration(id = MULTI_THREAD, name = MULTI_THREAD)
    public static class MultiThreadedLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static final class IdentityExecutable extends AbstractTruffleException {

        private final Context expectedContext;

        IdentityExecutable(Context c) {
            this.expectedContext = c;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(@SuppressWarnings("unused") Object[] args) {
            assertEquals(expectedContext, Context.getCurrent());
            return args[0];
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        TriState isIdenticalOrUndefined(Object other) {
            if (other instanceof IdentityExecutable) {
                return this == other ? TriState.TRUE : TriState.FALSE;
            }
            return TriState.UNDEFINED;
        }

        @TruffleBoundary
        @ExportMessage
        int identityHashCode() {
            return System.identityHashCode(this);
        }
    }

    @Test
    public void testClosedCheck() {
        TruffleTestAssumptions.assumeNoClassLoaderEncapsulation(); // AbstractTruffleException
        Context c0 = createContext(true);
        Context c1 = createContext(true);
        Value v0c0 = c0.asValue(new IdentityExecutable(c0));
        Value v0c1 = c1.asValue(v0c0);
        assertEquals(v0c1, v0c1.execute(v0c1));
        c0.close();

        AbstractPolyglotTest.assertFails(() -> {
            v0c1.execute(v0c1);
        }, PolyglotException.class, (e) -> {
            assertTrue(e.isHostException());
            assertTrue(e.asHostException() instanceof IllegalStateException);
            assertEquals("The Context is already closed.", e.getMessage());
        });

        c1.close();
    }

    @Test
    public void testSharingDisabled() {
        try (Context c0 = createContext(true);
                        Context c1 = createContext(false);) {

            // test guest value sharing
            Value c0ValueGuest = c0.asValue(new IdentityExecutable(c0));
            AbstractPolyglotTest.assertFails(() -> {
                c1.asValue(c0ValueGuest);
            }, PolyglotException.class, ContextSharingTest::assertMigrationError);

            // primitive
            Value c0ValueInt = c0.asValue(42);
            AbstractPolyglotTest.assertFails(() -> {
                c1.asValue(c0ValueInt);
            }, PolyglotException.class, ContextSharingTest::assertMigrationError);

            Value c0ValueString = c0.asValue("");
            AbstractPolyglotTest.assertFails(() -> {
                c1.asValue(c0ValueString);
            }, PolyglotException.class, ContextSharingTest::assertMigrationError);

            Value c0ValueHost = c0.asValue(this);
            AbstractPolyglotTest.assertFails(() -> {
                c1.asValue(c0ValueHost);
            }, PolyglotException.class, ContextSharingTest::assertMigrationError);

            Value c0ValueProxy = c0.asValue(new Proxy() {
            });
            AbstractPolyglotTest.assertFails(() -> {
                c1.asValue(c0ValueProxy);
            }, PolyglotException.class, ContextSharingTest::assertMigrationError);
        }
    }

    private static void assertMigrationError(PolyglotException e) {
        assertFalse(e.isHostException());
        assertTrue(e.isGuestException());
        assertEquals("A value was tried to be migrated from one context to a different context. " +
                        "Value migration for the current context was disabled and is therefore disallowed.",
                        e.getMessage());
    }

}
