/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class PolyglotExceptionTest extends AbstractPolyglotTest {

    @SuppressWarnings("serial")
    private static class TestGuestError extends RuntimeException implements TruffleException {

        TestGuestError() {
            super("MyError");
        }

        public Node getLocation() {
            return null;
        }

    }

    @Test
    public void testExceptionWrapping() {
        try (Context context1 = Context.create();
                        Context context2 = Context.create()) {

            CauseErrorTruffleObject causeError = new CauseErrorTruffleObject();
            causeError.thrownError = new TestGuestError();

            Value throwError = context1.asValue(causeError);
            Value throwErrorOtherContext = context2.asValue(causeError);

            try {
                throwError.execute();
                Assert.fail();
            } catch (PolyglotException e) {
                Assert.assertEquals(e.getMessage(), "MyError");
                Assert.assertTrue(e.isGuestException());
            }

            Value verifyError = context1.asValue(new ProxyExecutable() {
                public Object execute(Value... arguments) {
                    try {
                        throwError.execute();
                        Assert.fail();
                    } catch (PolyglotException e) {
                        Assert.assertEquals(e.getMessage(), "MyError");
                        Assert.assertTrue(e.isGuestException());
                        throw e;
                    }
                    return null;
                }
            });
            try {
                verifyError.execute();
                Assert.fail();
            } catch (PolyglotException e) {
                Assert.assertEquals(e.getMessage(), "MyError");
                Assert.assertTrue(e.isGuestException());
            }

            // if the exception was thrown by a different context it will be treated
            // as a host exception.
            Value verifyErrorOtherContext = context1.asValue(new ProxyExecutable() {
                public Object execute(Value... arguments) {
                    try {
                        throwErrorOtherContext.execute();
                    } catch (PolyglotException e) {
                        Assert.assertEquals(e.getMessage(), "MyError");
                        Assert.assertTrue(e.isGuestException());
                        throw e;
                    }
                    return null;
                }
            });
            try {
                verifyErrorOtherContext.execute();
                Assert.fail();
            } catch (PolyglotException e) {
                // assert that polyglot exception was not unboxed if from other context
                Assert.assertTrue(e.asHostException() instanceof PolyglotException);
                PolyglotException polyglot = (PolyglotException) e.asHostException();
                Assert.assertEquals(polyglot.getMessage(), "MyError");
                Assert.assertTrue(polyglot.isGuestException());
            }
        }
    }

    @Test
    public void testLanguageExceptionUnwrapping() {
        setupEnv();

        Value throwError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                throw new RuntimeException();
            }
        });

        VerifyErrorTruffleObject checkErrorObj = new VerifyErrorTruffleObject();
        Value checkError = context.asValue(checkErrorObj);

        checkErrorObj.verifyError = (e) -> {
            Assert.assertTrue(e instanceof TruffleException);
            Assert.assertEquals("HostException", e.getClass().getSimpleName());
        };
        Assert.assertTrue(checkError.execute(throwError).asBoolean());
    }

    @ExportLibrary(InteropLibrary.class)
    static class CauseErrorTruffleObject implements TruffleObject {

        RuntimeException thrownError;

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        final Object execute(@SuppressWarnings("unused") Object[] arguments) {
            throw thrownError;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class VerifyErrorTruffleObject implements TruffleObject {

        Consumer<Throwable> verifyError;

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        final Object execute(Object[] arguments) {
            Object arg = arguments[0];
            try {
                InteropLibrary.getFactory().getUncached().execute(arg);
                Assert.fail();
            } catch (Throwable e) {
                verifyError.accept(e);
            }
            return true;
        }

    }

    @Test
    public void testRecursiveHostException() {
        setupEnv();
        RuntimeException e1 = new RuntimeException();
        RuntimeException e2 = new RuntimeException();
        e1.initCause(e2);
        e2.initCause(e1);
        Value throwError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                throw e1;
            }
        });
        try {
            throwError.execute();
            fail();
        } catch (PolyglotException e) {
            assertSame(e1, e.asHostException());
        }
    }

    @SuppressWarnings("serial")
    public static class TruncatedException extends RuntimeException {

        private final int limit;

        public TruncatedException(int limit) {
            assert limit >= 0;
            this.limit = limit;
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            StackTraceElement[] stackTrace = super.getStackTrace();
            return Arrays.copyOf(stackTrace, Math.min(limit, stackTrace.length));
        }

        @SuppressWarnings("unused")
        public static TruncatedException newTruncatedException(int limit) {
            return new TruncatedException(limit);
        }

        /**
         * Only reflective Method calls trigger GR-22058, calling the constructor reflectively
         * doesn't, but Constructor .newInstance frames could also be skipped and trigger GR-22058
         * in the future.
         */
        public static TruncatedException createViaConstructorReflectively(int limit) {
            try {
                // Create an exception with truncated stack trace including reflective .newInstance
                // frames.
                Constructor<TruncatedException> init = TruncatedException.class.getConstructor(int.class);
                return init.newInstance(limit);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
                throw new AssertionError(e);
            }
        }

        public static TruncatedException createViaMethodReflectively(int limit) {
            try {
                // Create an exception with truncated stack trace including reflective .invoke*
                // frames.
                Method init = TruncatedException.class.getDeclaredMethod("newTruncatedException", int.class);
                return (TruncatedException) init.invoke(null, limit);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new AssertionError(e);
            }
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void testTruncatedStackTrace() {
        // Regression test for GR-22058.
        setupEnv();
        Value throwError = context.asValue(new ProxyExecutable() {
            public Object execute(Value... arguments) {
                throw arguments[0].<RuntimeException> asHostObject();
            }
        });
        for (int limit = 0; limit < 16; ++limit) {
            for (TruncatedException ex : Arrays.asList(
                            TruncatedException.createViaMethodReflectively(limit),
                            TruncatedException.createViaConstructorReflectively(limit))) {
                try {
                    throwError.execute(ex);
                    fail();
                } catch (PolyglotException e) {
                    assertSame(ex, e.asHostException()); // GR-22058 throws here
                    int frameCount = 0;
                    for (PolyglotException.StackFrame unused : e.getPolyglotStackTrace()) {
                        frameCount++;
                    }
                    // Sanity checks.
                    assertTrue(ex.getStackTrace().length <= limit);
                    assertEquals(ex.getStackTrace().length, frameCount);
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void testGuestOOMResourceLimit() {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(getCurrentLanguage()) {

                    @Override
                    public Object execute(VirtualFrame frame) {
                        allocateALot();
                        return 42;
                    }

                    @TruffleBoundary
                    private void allocateALot() {
                        List<byte[]> blocks = new ArrayList<>();
                        while (true) {
                            blocks.add(new byte[1024 * 1024]);
                        }
                    }

                    @Override
                    public String getName() {
                        return "testRootName";
                    }

                });
            }
        });

        assertFails(() -> context.eval(ProxyLanguage.ID, "test"), PolyglotException.class, (e) -> {
            assertTrue(e.isResourceExhausted());
            assertFalse(e.isInternalError());
            assertTrue(e.isGuestException());
            assertFalse(e.isHostException());
            assertFalse(e.isCancelled());
            Iterator<StackFrame> iterator = e.getPolyglotStackTrace().iterator();
            boolean foundFrame = false;
            while (iterator.hasNext()) {
                StackFrame frame = iterator.next();
                if (frame.isGuestFrame()) {
                    foundFrame = true;
                    assertTrue(frame.isGuestFrame());
                    assertEquals("testRootName", frame.getRootName());
                }
            }
            assertTrue(foundFrame);
        });
    }

    @Test
    public void testGuestStackOverflowResourceLimit() {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                return Truffle.getRuntime().createCallTarget(new RootNode(getCurrentLanguage()) {

                    @Override
                    @TruffleBoundary
                    public Object execute(VirtualFrame frame) {
                        return execute(frame);
                    }

                    @Override
                    public String getName() {
                        return "testRootName";
                    }

                });
            }
        });
        assertFails(() -> context.eval(ProxyLanguage.ID, "test"), PolyglotException.class, (e) -> {
            assertTrue(e.isResourceExhausted());
            assertFalse(e.isInternalError());
            assertTrue(e.isGuestException());
            assertFalse(e.isHostException());
            assertFalse(e.isCancelled());
            Iterator<StackFrame> iterator = e.getPolyglotStackTrace().iterator();
            boolean foundFrame = false;
            while (iterator.hasNext()) {
                StackFrame frame = iterator.next();
                if (frame.isGuestFrame()) {
                    foundFrame = true;
                    assertTrue(frame.isGuestFrame());
                    assertEquals("testRootName", frame.getRootName());
                }
            }
            assertTrue(foundFrame);
        });
    }

    static class ThrowOOM implements Runnable {

        public void run() {
            List<byte[]> blocks = new ArrayList<>();
            while (true) {
                blocks.add(new byte[1024 * 1024]);
            }
        }
    }

    @Test
    public void testHostOOMResourceLimit() {
        setupEnv(Context.newBuilder().allowHostAccess(HostAccess.ALL).build());
        Value v = context.asValue(new ThrowOOM());
        assertFails(() -> v.execute(), PolyglotException.class, (e) -> {
            assertTrue(e.isResourceExhausted());
            assertFalse(e.isInternalError());
            assertFalse(e.isGuestException());
            assertTrue(e.isHostException());
            assertFalse(e.isCancelled());
            // no guarantees for stack frames.
        });
    }

    static class ThrowStackOverflow implements Runnable {

        public void run() {
            run();
        }
    }

    @Test
    public void testHostStackOverflowResourceLimit() {
        setupEnv(Context.newBuilder().allowHostAccess(HostAccess.ALL).build());
        Value v = context.asValue(new ThrowStackOverflow());
        assertFails(() -> v.execute(), PolyglotException.class, (e) -> {
            assertTrue(e.isResourceExhausted());
            assertFalse(e.isInternalError());
            assertFalse(e.isGuestException());
            assertTrue(e.isHostException());
            Iterator<StackFrame> iterator = e.getPolyglotStackTrace().iterator();
            StackFrame frame = iterator.next();
            assertTrue(frame.isHostFrame());
            assertTrue(frame.getRootName(), frame.getRootName().endsWith("ThrowStackOverflow.run"));
            frame = iterator.next();
            assertTrue(frame.isHostFrame());
        });
    }

}
