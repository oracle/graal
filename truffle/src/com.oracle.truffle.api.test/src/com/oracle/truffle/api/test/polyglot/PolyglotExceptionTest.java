/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.api.test.common.TestUtils.getDefaultLanguageId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class PolyglotExceptionTest extends AbstractPolyglotTest {

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("serial")
    static class TestGuestError extends AbstractTruffleException {

        String exceptionMessage;

        TestGuestError() {
            super("MyError");
        }

        @ExportMessage
        boolean hasExceptionMessage() {
            return exceptionMessage != null;
        }

        @ExportMessage
        Object getExceptionMessage() throws UnsupportedMessageException {
            if (exceptionMessage != null) {
                return exceptionMessage;
            } else {
                throw UnsupportedMessageException.create();
            }
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
        try (Context c = Context.create()) {

            Value throwError = c.asValue(new ProxyExecutable() {
                public Object execute(Value... arguments) {
                    throw new RuntimeException();
                }
            });

            VerifyErrorTruffleObject checkErrorObj = new VerifyErrorTruffleObject();
            Value checkError = c.asValue(checkErrorObj);

            checkErrorObj.verifyError = (e) -> {
                Assert.assertTrue(InteropLibrary.getUncached().isException(e));
                Assert.assertEquals("HostException", e.getClass().getSimpleName());
            };
            Assert.assertTrue(checkError.execute(throwError).asBoolean());
        }
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
                fail();
            } catch (Throwable e) {
                consumeError(e);
            }
            return true;
        }

        @TruffleBoundary
        private void consumeError(Throwable e) {
            verifyError.accept(e);
        }

        @TruffleBoundary
        private static void fail() {
            Assert.fail();
        }

    }

    @Test
    public void testRecursiveHostException() {
        try (Context c = Context.create()) {
            RuntimeException e1 = new RuntimeException();
            RuntimeException e2 = new RuntimeException();
            e1.initCause(e2);
            e2.initCause(e1);
            Value throwError = c.asValue(new ProxyExecutable() {
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
        try (Context c = Context.create()) {
            Value throwError = c.asValue(new ProxyExecutable() {
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
    }

    @TruffleLanguage.Registration
    static class GuestOOMLanguage extends AbstractExecutableTestLanguage {

        @Override
        protected String getRootName(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
            return "testRootName";
        }

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            List<byte[]> blocks = new ArrayList<>();
            while (true) {
                blocks.add(new byte[1024 * 1024]);
            }
        }
    }

    @SuppressWarnings("unused")
    @Test
    public void testGuestOOMResourceLimit() {
        Context.Builder builder = Context.newBuilder();
        if (TruffleTestAssumptions.isStrongEncapsulation()) {
            builder.option("engine.IsolateOption.MaxHeapSize", "1g");
        }
        try (Context c = builder.build()) {

            assertFails(() -> evalTestLanguage(c, GuestOOMLanguage.class, "test"), PolyglotException.class, (e) -> {
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
                if (TruffleTestAssumptions.isWeakEncapsulation()) { // GR-35913
                    assertTrue(foundFrame);
                }
            });
        }
    }

    @TruffleLanguage.Registration
    static class GuestStackOverflowLanguage extends AbstractExecutableTestLanguage {

        @Override
        protected String getRootName(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
            return "testRootName";
        }

        @Override
        @TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            return execute(node, env, contextArguments, frameArguments);
        }
    }

    @Test
    public void testGuestStackOverflowResourceLimit() {
        try (Context c = Context.create()) {
            assertFails(() -> evalTestLanguage(c, GuestStackOverflowLanguage.class, "test"), PolyglotException.class, (e) -> {
                assertTrue(e.isResourceExhausted());
                assertFalse(e.isInternalError());
                assertTrue(e.isGuestException());
                assertFalse(e.isHostException());
                assertFalse(e.isCancelled());
                assertEquals(e.getMessage(), "Resource exhausted: Stack overflow");
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
                if (TruffleTestAssumptions.isWeakEncapsulation()) { // GR-35913
                    assertTrue(foundFrame);
                }
            });
        }
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
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value v = c.asValue(new ThrowOOM());
            assertFails(() -> v.execute(), PolyglotException.class, (e) -> {
                assertTrue(e.isResourceExhausted());
                assertFalse(e.isInternalError());
                assertFalse(e.isGuestException());
                assertTrue(e.isHostException());
                assertFalse(e.isCancelled());
                // no guarantees for stack frames.
            });
        }
    }

    @Test
    public void testCancelExceptionNoResourceLimit() {
        try (Context c = Context.newBuilder().build()) {
            c.close(true);
            assertFails(() -> c.eval("sl", "function main() {}"), PolyglotException.class, (e) -> {
                assertFalse(e.isResourceExhausted());
                assertFalse(e.isInternalError());
                assertTrue(e.isGuestException());
                assertFalse(e.isHostException());
                assertTrue(e.isCancelled());
            });
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @TruffleLanguage.Registration
    static class HostExceptionLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = getDefaultLanguageId(HostExceptionLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            Source source = request.getSource();
            SizeNode callHost = PolyglotExceptionTestFactory.SizeNodeGen.create(
                            PolyglotExceptionTestFactory.ReadBindingsNodeGen.create());
            return new CallHostRootNode(this, source, callHost).getCallTarget();
        }
    }

    @Test
    public void testHostException() {
        try (Context c = Context.newBuilder().allowAllAccess(true).build()) {
            c.getPolyglotBindings().putMember("receiver", new BrokenList<>());
            assertFails(() -> c.eval(HostExceptionLanguage.ID, ""), PolyglotException.class, (pe) -> {
                assertTrue(pe.isHostException());
                StackTraceElement prev = null;
                for (StackTraceElement element : pe.getStackTrace()) {
                    if (("<" + HostExceptionLanguage.ID + ">").equals(element.getClassName())) {
                        break;
                    }
                    prev = element;
                    if (TruffleTestAssumptions.isStrongEncapsulation()) { // GR-35913
                        break;
                    }
                }
                assertNotNull("No host frame found.", prev);
                assertEquals(BrokenList.class.getName(), prev.getClassName());
                assertEquals("size", prev.getMethodName());
            });
        }
    }

    static class ThrowStackOverflow implements Runnable {

        public void run() {
            run();
        }
    }

    @Test
    public void testHostStackOverflowResourceLimit() {
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            Value v = c.asValue(new ThrowStackOverflow());
            assertFails(() -> v.execute(), PolyglotException.class, (e) -> {
                assertTrue(e.isResourceExhausted());
                assertFalse(e.isInternalError());
                assertFalse(e.isGuestException());
                assertTrue(e.isHostException());
                Iterator<StackFrame> iterator = e.getPolyglotStackTrace().iterator();
                /*
                 * In case of AOT, the first three frames are the following:
                 *
                 * com.oracle.svm.core.graal.snippets.StackOverflowCheckImpl.newStackOverflowError0
                 * com.oracle.svm.core.graal.snippets.StackOverflowCheckImpl.newStackOverflowError
                 * com.oracle.svm.core.graal.snippets.StackOverflowCheckImpl.
                 * throwNewStackOverflowError
                 *
                 * That is why we skip them and check the subsequent frames.
                 */
                if (TruffleTestAssumptions.isAOT()) {
                    iterator.next();
                    iterator.next();
                    iterator.next();
                }
                StackFrame frame = iterator.next();
                assertTrue(frame.isHostFrame());
                assertTrue(frame.getRootName(), frame.getRootName().endsWith("ThrowStackOverflow.run"));
                frame = iterator.next();
                assertTrue(frame.isHostFrame());
            });
        }
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static class DetectWaitingStartedExecutable implements TruffleObject {
        CountDownLatch waitingStarted = new CountDownLatch(1);

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("unused")
        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        final Object execute(Object[] arguments) {
            waitingStarted.countDown();
            return true;
        }
    }

    @TruffleLanguage.Registration
    static class InternalErrorNotMaskedLanguage extends AbstractExecutableTestLanguage {

        @Override
        protected String getRootName(ParsingRequest request, Env env, Object[] contextArguments) throws Exception {
            return "testRootName";
        }

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) {
            waitForever(contextArguments);
            return null;
        }

        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
        @TruffleBoundary
        private void waitForever(Object[] contextArguments) {
            Object detectWaitingStartedExecutable = contextArguments[0];
            final Object waitObject = new Object();
            try {
                interop.execute(detectWaitingStartedExecutable);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            synchronized (waitObject) {
                try {
                    waitObject.wait();
                } catch (InterruptedException ie) {
                    /*
                     * This is the internal error.
                     */
                    Assert.fail();
                }
            }
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

    @Test
    public void testCancelDoesNotMaskInternalError() throws InterruptedException, ExecutionException {
        DetectWaitingStartedExecutable detectWaitingStartedExecutable = new DetectWaitingStartedExecutable();
        try (Context c = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            Future<?> future = executorService.submit(() -> {
                assertFails(() -> evalTestLanguage(c, InternalErrorNotMaskedLanguage.class, "", detectWaitingStartedExecutable),
                                PolyglotException.class, (e) -> {
                                    assertTrue(e.isInternalError());
                                    assertTrue(e.isGuestException());
                                    assertFalse(e.isHostException());
                                    assertFalse(e.isCancelled());
                                    Iterator<StackFrame> iterator = e.getPolyglotStackTrace().iterator();
                                    boolean foundGuestFrame = false;
                                    boolean foundHostFrame = false;
                                    while (iterator.hasNext()) {
                                        StackFrame frame = iterator.next();
                                        if (frame.isGuestFrame()) {
                                            foundGuestFrame = true;
                                            assertTrue(frame.isGuestFrame());
                                            assertEquals("testRootName", frame.getRootName());
                                        } else {
                                            if ("waitForever".equals(frame.toHostFrame().getMethodName())) {
                                                foundHostFrame = true;
                                            }
                                        }
                                    }
                                    if (TruffleTestAssumptions.isWeakEncapsulation()) { // GR-35913
                                        assertTrue(foundGuestFrame);
                                    }
                                    assertTrue(foundHostFrame);
                                });
            });
            detectWaitingStartedExecutable.waitingStarted.await();
            c.close(true);
            future.get();
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(100, TimeUnit.SECONDS));
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testExceptionMessage() {
        try (Context ctx = Context.create()) {
            TestGuestError guestError = new TestGuestError();
            guestError.exceptionMessage = "interop exception message";
            CauseErrorTruffleObject causeError = new CauseErrorTruffleObject();
            causeError.thrownError = guestError;
            Value throwError = ctx.asValue(causeError);
            try {
                throwError.execute();
                Assert.fail();
            } catch (PolyglotException e) {
                Assert.assertEquals("interop exception message", e.getMessage());
                Assert.assertTrue(e.isGuestException());
            }

            guestError.exceptionMessage = null;
            try {
                throwError.execute();
                Assert.fail();
            } catch (PolyglotException e) {
                Assert.assertEquals("MyError", e.getMessage());
                Assert.assertTrue(e.isGuestException());
            }
        }
    }

    @Test
    public void testSerialization() throws IOException {
        try (Context c = Context.create()) {
            Value throwError = c.asValue((ProxyExecutable) arguments -> {
                throw new RuntimeException();
            });
            AtomicReference<PolyglotException> polyglotExceptionHolder = new AtomicReference<>();
            AbstractPolyglotTest.assertFails(() -> throwError.execute(), PolyglotException.class, (pe) -> polyglotExceptionHolder.set(pe));
            assertNotNull(polyglotExceptionHolder.get());
            try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
                AbstractPolyglotTest.assertFails(() -> {
                    out.writeObject(polyglotExceptionHolder.get());
                    return null;
                }, IOException.class, (ioe) -> assertEquals("PolyglotException serialization is not supported.", ioe.getMessage()));
            }
        }
    }

    abstract static class BaseNode extends Node {
        abstract Object execute(VirtualFrame frame);
    }

    abstract static class ReadBindingsNode extends BaseNode {

        @Specialization
        Object doGeneric(@CachedLibrary(limit = "1") InteropLibrary interop) {
            try {
                Object bindings = TestAPIAccessor.engineAccess().getPolyglotBindingsObject();
                return interop.readMember(bindings, "receiver");
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @NodeChild("receiver")
    abstract static class SizeNode extends BaseNode {

        @Specialization(limit = "1")
        Object doGeneric(Object arg, @CachedLibrary(value = "arg") InteropLibrary interop) {
            try {
                return interop.getArraySize(arg);
            } catch (UnsupportedMessageException unsupportedMessageException) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    private static final class CallHostRootNode extends RootNode {

        @Child BaseNode body;
        Source source;

        CallHostRootNode(TruffleLanguage<?> language, Source source, BaseNode body) {
            super(language);
            this.source = source;
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return body.execute(frame);
        }

        @Override
        @TruffleBoundary
        public SourceSection getSourceSection() {
            return source.createSection(1);
        }
    }

    @SuppressWarnings("serial")
    private static final class BrokenList<T> extends ArrayList<T> {

        @Override
        public int size() {
            throw new NullPointerException();
        }
    }
}
