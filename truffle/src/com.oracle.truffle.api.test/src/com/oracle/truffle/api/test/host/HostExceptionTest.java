/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage.LanguageContext;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class HostExceptionTest {

    private Context context;
    private boolean entered;
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    private Env env;
    private Class<? extends Throwable> expectedException;
    private Consumer<Throwable> customExceptionVerfier;
    private boolean checkHostExceptionElements;

    private static final String INSTRUMENTATION_TEST_LANGUAGE = "instrumentation-test-language";

    private static final String CATCHER = "catcher";
    private static final String RUNNER = "runner";
    private static final String RETHROWER = "rethrower";
    private static final String THROW_EXCEPTION = "throwException";

    private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    @Test
    public void testExceptionFromExecutionListener() {
        ExecutionListener.newBuilder().statements(true).onEnter(new Consumer<ExecutionEvent>() {
            @Override
            public void accept(ExecutionEvent executionEvent) {
                throw new RuntimeException("ExceptionFromExecutionListener");
            }
        }).attach(context.getEngine());
        context.eval(INSTRUMENTATION_TEST_LANGUAGE, "TRY(STATEMENT, CATCH(RuntimeException, ex, PRINT(OUT, INVOKE_MEMBER(getMessage, READ_VAR(ex)))))");
        assertEquals("ExceptionFromExecutionListener", outStream.toString());
    }

    @Before
    public void before() {
        context = Context.newBuilder().allowAllAccess(true).out(outStream).build();
        if (TruffleTestAssumptions.isWeakEncapsulation()) {
            ProxyLanguage.setDelegate(new ProxyLanguage() {
                @Override
                protected LanguageContext createContext(Env contextEnv) {
                    env = contextEnv;
                    return super.createContext(contextEnv);
                }

                @Override
                protected CallTarget parse(ParsingRequest request) throws Exception {
                    RootNode rootNode;
                    switch (request.getSource().getCharacters().toString()) {
                        case CATCHER:
                            rootNode = new CatcherRootNode();
                            break;
                        case RUNNER:
                            rootNode = new RunnerRootNode();
                            break;
                        case RETHROWER:
                            rootNode = new RethrowerRootNode();
                            break;
                        case THROW_EXCEPTION:
                            rootNode = new ThrowExceptionRootNode();
                            break;
                        default:
                            throw new IllegalArgumentException();
                    }
                    return RootNode.createConstantNode(new CatcherObject(rootNode.getCallTarget())).getCallTarget();
                }
            });
            context.initialize(ProxyLanguage.ID);
            context.enter();
            entered = true;
            assertNotNull(env);
        }
    }

    @After
    public void after() {
        if (entered) {
            context.leave();
        }
        context.close();
        customExceptionVerfier = null;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAsHostExceptionIllegalArgument() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        env.asHostException(new Exception());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAsHostExceptionNull() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        env.asHostException(null);
    }

    @Test(expected = NullPointerException.class)
    public void testFillInNull() {
        TruffleStackTrace.fillIn(null);
    }

    @Test(expected = NullPointerException.class)
    public void testGetStackTraceOfNull() {
        TruffleStackTrace.getStackTrace(null);
    }

    public static void thrower() {
        throw new NoSuchElementException();
    }

    @Test
    public void testUncaughtHostException() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        Value catcher = context.eval(ProxyLanguage.ID, RUNNER);
        Runnable thrower = HostExceptionTest::thrower;
        try {
            catcher.execute(thrower);
            shouldHaveThrown(PolyglotException.class);
        } catch (PolyglotException polyglotException) {
            assertNull("cause must be null", polyglotException.getCause());
            assertTrue(polyglotException.isHostException());
            assertThat(polyglotException.asHostException(), instanceOf(NoSuchElementException.class));

            Iterator<StackFrame> iterator = polyglotException.getPolyglotStackTrace().iterator();
            StackFrame sf = iterator.next();
            assertTrue(sf.isHostFrame());
            assertThat(sf.getRootName(), containsString("thrower"));
            sf = iterator.next();
            assertTrue(sf.isGuestFrame());
            assertNotNull(sf.getSourceLocation());
            assertEquals(4, sf.getSourceLocation().getStartLine());
            assertEquals(RUNNER, sf.getRootName());
            sf = iterator.next();
            assertTrue(sf.isHostFrame());
            assertThat(sf.getRootName(), containsString("execute"));
        }
    }

    @Test
    public void testExceptionObject() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable thrower = HostExceptionTest::thrower;
        Value result = catcher.execute(thrower);
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(NoSuchElementException.class));

        NoSuchElementException exception = result.asHostObject();
        assertNotNull(exception);
        StackTraceElement[] stackTrace = exception.getStackTrace();

        if (checkHostExceptionElements) {
            Iterator<StackTraceElement> iterator = Arrays.asList(stackTrace).iterator();
            StackTraceElement sf = iterator.next();
            assertThat(sf.getMethodName(), containsString("thrower"));
            sf = iterator.next();
            assertThat(sf.getMethodName(), containsString(CATCHER));
            assertEquals(4, sf.getLineNumber());
            sf = iterator.next();
            assertThat(sf.getMethodName(), containsString("execute"));
        }
    }

    @Test
    public void testCatchAndThrow() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable thrower = HostExceptionTest::thrower;
        Consumer<Object> consumer = exceptionObject -> {
            throw (RuntimeException) exceptionObject;
        };
        try {
            Value ex = catcher.execute(runner, thrower);
            runner.execute(consumer, ex);
            shouldHaveThrown(PolyglotException.class);
        } catch (PolyglotException polyglotException) {
            assertNull("cause must be null", polyglotException.getCause());
            assertTrue(polyglotException.isHostException());
            assertThat(polyglotException.asHostException(), instanceOf(expectedException));

            NoSuchElementException exception = (NoSuchElementException) polyglotException.asHostException();
            assertNotNull(exception);
            assertNotNull(exception.getStackTrace());
        }
    }

    @SuppressWarnings("serial")
    @Test
    public void testSetStackTraceOverridden() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        class BadException extends RuntimeException {
            @Override
            public void setStackTrace(StackTraceElement[] stackTrace) {
                throw new UnsupportedOperationException();
            }
        }

        expectedException = BadException.class;
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable thrower = () -> {
            throw new BadException();
        };
        Value result = catcher.execute(thrower);
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(BadException.class));

        BadException exception = result.asHostObject();
        assertNotNull(exception);
        assertNotNull(exception.getStackTrace());
    }

    @SuppressWarnings("serial")
    @Test
    public void testGetStackTraceOverridden() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        class BadException extends RuntimeException {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new UnsupportedOperationException();
            }
        }

        expectedException = BadException.class;
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable thrower = () -> {
            throw new BadException();
        };
        Value result = catcher.execute(thrower);
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(BadException.class));

        BadException exception = result.asHostObject();
        assertNotNull(exception);
    }

    @SuppressWarnings("serial")
    @Test
    public void testNullStackTrace() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        class BadException extends RuntimeException {
            @Override
            public StackTraceElement[] getStackTrace() {
                return null;
            }
        }

        expectedException = BadException.class;
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable throwerInner = () -> {
            throw new BadException();
        };
        Runnable throwerOuter = () -> {
            runner.execute(throwerInner);
            shouldHaveThrown(PolyglotException.class);
        };

        try {
            runner.execute(throwerOuter);
            shouldHaveThrown(PolyglotException.class);
        } catch (PolyglotException polyglotException) {
            assertNull("cause must be null", polyglotException.getCause());
            assertTrue(polyglotException.isHostException());
            assertThat(polyglotException.asHostException(), instanceOf(BadException.class));
        }
        // should be caught
        catcher.execute(throwerOuter);
    }

    @SuppressWarnings("serial")
    private static class TestHostException extends RuntimeException {
        TestHostException() {
        }

        TestHostException(Throwable cause) {
            super(cause);
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[]{null, new StackTraceElement("", "", null, 0), new StackTraceElement("<host>", "(", ":", Integer.MIN_VALUE)};
        }
    }

    private static void assertCauseNullOrLazyStackTrace(Throwable cause) {
        // Ignore lazy stack trace injected as innermost cause by Truffle
        if (cause != null && cause.getClass().getName().equals(TruffleStackTrace.class.getName() + "$LazyStackTrace")) {
            return;
        }
        assertNull(cause);
    }

    @Test
    public void testNestedPolyglotException() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = TestHostException.class;
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable throwerInner = () -> {
            throw new TestHostException();
        };

        Runnable throwerOuterRethrow = () -> {
            try {
                runner.execute(throwerInner);
                shouldHaveThrown(PolyglotException.class);
            } catch (PolyglotException e) {
                throw e;
            } catch (Throwable e) {
                caughtUnexpected(PolyglotException.class, e);
            }
        };
        try {
            runner.execute(throwerOuterRethrow);
            shouldHaveThrown(PolyglotException.class);
        } catch (PolyglotException polyglotException) {
            assertNull("cause must be null", polyglotException.getCause());
            assertTrue(polyglotException.isHostException());
            assertThat(polyglotException.asHostException(), instanceOf(expectedException));
            assertCauseNullOrLazyStackTrace(polyglotException.asHostException().getCause());

            TestHostException exception = (TestHostException) polyglotException.asHostException();
            assertNotNull(exception);
            assertNotNull(exception.getStackTrace());
        }

        Runnable throwerOuterWrap = () -> {
            try {
                runner.execute(throwerInner);
                shouldHaveThrown(PolyglotException.class);
            } catch (PolyglotException e) {
                throw new TestHostException(e);
            } catch (Throwable e) {
                caughtUnexpected(PolyglotException.class, e);
            }
        };
        try {
            runner.execute(throwerOuterWrap);
            shouldHaveThrown(PolyglotException.class);
        } catch (PolyglotException outer) {
            assertNull("cause must be null", outer.getCause());
            assertTrue(outer.isHostException());
            assertThat(outer.asHostException(), instanceOf(expectedException));
            assertThat(outer.asHostException().getCause(), instanceOf(PolyglotException.class));
            PolyglotException inner = (PolyglotException) outer.asHostException().getCause();
            assertTrue(inner.isHostException());
            assertThat(inner.asHostException(), instanceOf(expectedException));
            assertCauseNullOrLazyStackTrace(inner.asHostException().getCause());

            TestHostException exception = (TestHostException) outer.asHostException();
            assertNotNull(exception);
            assertNotNull(exception.getStackTrace());
        }

        Value result = catcher.execute(throwerOuterRethrow);
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(TestHostException.class));
        TestHostException exception = result.asHostObject();
        assertNotNull(exception);
        assertNotNull(exception.getStackTrace());

        result = catcher.execute(throwerOuterWrap);
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(TestHostException.class));
        exception = result.asHostObject();
        assertNotNull(exception);
        assertNotNull(exception.getStackTrace());
    }

    @Test
    public void testRethrowHostException() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        Runnable thrower = HostExceptionTest::thrower;
        Value rethrower = context.eval(ProxyLanguage.ID, RETHROWER);

        // throw and rethrow a host exception
        try {
            rethrower.executeVoid(thrower);
            shouldHaveThrown(PolyglotException.class);
        } catch (PolyglotException polyglotException) {
            assertNull("cause must be null", polyglotException.getCause());
            assertTrue(polyglotException.isHostException());
            assertThat(polyglotException.asHostException(), instanceOf(expectedException));
            assertCauseNullOrLazyStackTrace(polyglotException.asHostException().getCause());
        }

        // throw, rethrow, and then catch a host exception
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value result = catcher.execute(rethrower, thrower);
        assertTrue(result.isException());
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(expectedException));
    }

    @Test
    public void testHostExceptionMetaInstance() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable thrower = HostExceptionTest::thrower;
        Value result = catcher.execute(thrower);
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(NoSuchElementException.class));

        Value expectedClass = context.asValue(expectedException);
        assertTrue(expectedClass.isMetaObject());
        assertTrue(expectedClass.isMetaInstance(result));
        Value throwableClass = context.asValue(Throwable.class);
        assertTrue(throwableClass.isMetaObject());
        assertTrue(throwableClass.isMetaInstance(result));
        Value objectClass = context.asValue(Object.class);
        assertTrue(objectClass.isMetaObject());
        assertTrue(objectClass.isMetaInstance(result));
        Value otherClass = context.asValue(Runnable.class);
        assertTrue(otherClass.isMetaObject());
        assertFalse(otherClass.isMetaInstance(result));
    }

    @Test
    public void testHostExceptionIsHostSymbol() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = RuntimeException.class;
        customExceptionVerfier = (t) -> {
            assertFalse(env.isHostSymbol(t));
        };
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable thrower = HostExceptionTest::thrower;
        catcher.execute(thrower);
    }

    @Test
    public void testHostExceptionWithContext() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = RuntimeException.class;
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Runnable thrower = HostExceptionTest::thrower;
        Value exception = catcher.execute(thrower);
        try (Context ctx2 = Context.create()) {
            ctx2.getPolyglotBindings().putMember("foo", exception);
            Value foo = ctx2.getPolyglotBindings().getMember("foo");
            assertTrue(foo.isException());
            assertTrue(foo.isHostObject());
            assertThat(foo.asHostObject(), instanceOf(expectedException));
        }
    }

    /**
     * Ensure {@link InteropLibrary#getExceptionStackTrace(Object)} works for host exception causes.
     * Also tests interop and polyglot stack traces.
     */
    @Test
    public void testHostExceptionCause() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        String expectedMessage = "oh";
        Runnable thrower = () -> {
            throw new NoSuchElementException(expectedMessage, new NoSuchFieldException("no"));
        };

        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);

        customExceptionVerfier = (hostEx) -> {
            assertTrue(INTEROP.isException(hostEx));
            try {
                assertTrue("should have exception cause", INTEROP.hasExceptionCause(hostEx));
                Object cause = INTEROP.getExceptionCause(hostEx);
                assertTrue("cause should be an exception", INTEROP.isException(cause));
                assertTrue("cause should be a host exception", env.isHostObject(cause));
                Class<? extends Throwable> causeClass = NoSuchFieldException.class;
                assertTrue("cause should be instanceof " + causeClass.getSimpleName(), INTEROP.isMetaInstance(env.asHostSymbol(causeClass), cause));
                assertFalse("cause should not have another cause", INTEROP.hasExceptionCause(cause));
                assertEquals(NoSuchFieldException.class.getSimpleName(), INTEROP.asString(INTEROP.getMetaSimpleName(INTEROP.getMetaObject(cause))));

                assertEquals(List.of(expectedException.getSimpleName() + ": " + expectedMessage,
                                RUNNER,
                                CATCHER),
                                formatInteropExceptionStackTrace(hostEx));
            } catch (UnsupportedMessageException e) {
                throw new AssertionError(e);
            }
        };

        Value result = catcher.execute(runner, thrower);
        assertHostException(result, expectedException);

        List<String> expectedStack = List.of(
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageRootNames(result.as(PolyglotException.class)));
    }

    /**
     * Tests that first instantiating the host exception in a host method and then throwing it in
     * the guest caller generates a useful stack trace.
     */
    @Test
    public void testThrowHostExceptionObject() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        String expectedMessage = "oh";
        Callable<Object> instantiate = () -> {
            return new NoSuchElementException(expectedMessage, new NoSuchFieldException("no"));
        };

        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value throwException = context.eval(ProxyLanguage.ID, THROW_EXCEPTION);
        Value rethrower = context.eval(ProxyLanguage.ID, RETHROWER);

        customExceptionVerfier = (hostEx) -> {
            assertTrue(INTEROP.isException(hostEx));
            try {
                assertTrue("should have exception cause", INTEROP.hasExceptionCause(hostEx));
                Object cause = INTEROP.getExceptionCause(hostEx);
                assertTrue("cause should be an exception", INTEROP.isException(cause));
                assertTrue("cause should be a host exception", env.isHostObject(cause));
                Class<? extends Throwable> causeClass = NoSuchFieldException.class;
                assertTrue("cause should be instanceof " + causeClass.getSimpleName(), INTEROP.isMetaInstance(env.asHostSymbol(causeClass), cause));
                assertFalse("cause should not have another cause", INTEROP.hasExceptionCause(cause));
                assertEquals(NoSuchFieldException.class.getSimpleName(), INTEROP.asString(INTEROP.getMetaSimpleName(INTEROP.getMetaObject(cause))));

                assertEquals(List.of(expectedException.getSimpleName() + ": " + expectedMessage,
                                THROW_EXCEPTION,
                                RUNNER,
                                CATCHER),
                                formatInteropExceptionStackTrace(hostEx));

            } catch (UnsupportedMessageException e) {
                throw new AssertionError(e);
            }
        };

        Value result = catcher.execute(runner, throwException, runner, instantiate);
        assertHostException(result, expectedException);

        List<String> expectedStack = List.of(
                        THROW_EXCEPTION,
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageRootNames(result.as(PolyglotException.class)));

        // unwrapping and wrapping the host exception again should not lose the stack trace
        Function<Object, Object> passthrough = t -> {
            assertThat(t, instanceOf(expectedException));
            return t;
        };
        result = runner.execute(runner, passthrough, result);
        assertHostException(result, expectedException);

        assertEquals(expectedStack, getProxyLanguageRootNames(result.as(PolyglotException.class)));

        Consumer<Object> thrower = t -> {
            assertThat(t, instanceOf(expectedException));
            throw (RuntimeException) expectedException.cast(t);
        };
        result = catcher.execute(rethrower, thrower, result);
        assertHostException(result, expectedException);

        assertEquals(expectedStack, getProxyLanguageRootNames(result.as(PolyglotException.class)));
    }

    private static void assertHostException(Value result, Class<? extends Throwable> expectedException) {
        assertTrue(result.toString(), result.isException());
        assertTrue(result.toString(), result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(expectedException));
    }

    private static List<String> getProxyLanguageRootNames(PolyglotException polyglotException) {
        return getProxyLanguageFrames(polyglotException).stream().map(StackFrame::getRootName).toList();
    }

    private static List<StackFrame> getProxyLanguageFrames(PolyglotException polyglotException) {
        return stream(polyglotException.getPolyglotStackTrace()).filter(s -> s.getLanguage().getId().equals(ProxyLanguage.ID)).toList();
    }

    private static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static List<String> formatInteropExceptionStackTrace(Object exception) {
        assertTrue(INTEROP.isException(exception));
        try {
            List<String> lines = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            if (INTEROP.hasMetaObject(exception)) {
                sb.append(INTEROP.asString(INTEROP.getMetaSimpleName(INTEROP.getMetaObject(exception))));
                sb.append(": ");
            }
            String message;
            if (INTEROP.hasExceptionMessage(exception)) {
                message = INTEROP.asString(INTEROP.getExceptionMessage(exception));
            } else {
                message = "null";
            }
            sb.append(message);
            lines.add(sb.toString());

            if (INTEROP.hasExceptionStackTrace(exception)) {
                Object stackTrace = INTEROP.getExceptionStackTrace(exception);
                long length = INTEROP.getArraySize(stackTrace);
                for (long i = 0; i < length; i++) {
                    Object stackTraceElement = INTEROP.readArrayElement(stackTrace, i);

                    String name;
                    if (INTEROP.hasExecutableName(stackTraceElement)) {
                        name = INTEROP.asString(INTEROP.getExecutableName(stackTraceElement));
                    } else {
                        name = "Unnamed";
                    }

                    if (name.contains(".")) {
                        // skip host frames
                        continue;
                    }

                    lines.add(name);
                }
            }
            return lines;
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    static void shouldHaveThrown(Class<? extends Throwable> expected) {
        fail("Expected a " + expected + " but none was thrown");
    }

    @TruffleBoundary
    static void caughtUnexpected(Class<? extends Throwable> expected, Throwable unexpected) {
        fail("Expected a " + expected + " but caught " + unexpected);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class CatcherObject implements TruffleObject {
        final CallTarget callTarget;

        CatcherObject(CallTarget callTarget) {
            this.callTarget = callTarget;
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof CatcherObject;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        abstract static class Execute {
            @Specialization
            static Object access(CatcherObject catcher, Object[] args,
                            @Cached IndirectCallNode callNode) {
                return callNode.call(catcher.callTarget, args);
            }
        }
    }

    class CatcherRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        CatcherRootNode() {
            super(ProxyLanguage.get(null));
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "\na\nb\nc\n", CATCHER).build().createSection(4);
        }

        @Override
        public String getName() {
            return CATCHER;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject thrower = (TruffleObject) frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                Object result = interop.execute(thrower, args);
                if (expectedException != null) {
                    shouldHaveThrown(expectedException);
                } else if (customExceptionVerfier != null) {
                    shouldHaveThrown(Throwable.class);
                }
                return result;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (Exception ex) {
                if (interop.isException(ex)) {
                    return checkAndUnwrapException(ex);
                }
                throw ex;
            }
        }
    }

    @TruffleBoundary
    Object checkAndUnwrapException(Throwable ex) {
        assertTrue(env.isHostObject(ex));
        assertNotNull("Unexpected exception: " + ex, expectedException);
        assertThat(env.asHostObject(ex), instanceOf(expectedException));
        assertThat(LanguageContext.get(null).getEnv().asHostException(ex), instanceOf(expectedException));
        try {
            assertTrue(InteropLibrary.getUncached().isMetaInstance(env.asHostSymbol(Throwable.class), ex));
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
        if (customExceptionVerfier != null) {
            customExceptionVerfier.accept(ex);
        }
        return ex;
    }

    class RunnerRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        RunnerRootNode() {
            super(ProxyLanguage.get(null));
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "\na\nb\nc\n", RUNNER).build().createSection(4);
        }

        @Override
        public String getName() {
            return RUNNER;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject thrower = (TruffleObject) frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                return interop.execute(thrower, args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    class RethrowerRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        RethrowerRootNode() {
            super(ProxyLanguage.get(null));
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "rethrow", RETHROWER).build().createSection(1);
        }

        @Override
        public String getName() {
            return RETHROWER;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject thrower = (TruffleObject) frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                return interop.execute(thrower, args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (Exception ex) {
                if (interop.isException(ex)) {
                    assertTrue(env.isHostObject(ex));
                    try {
                        throw interop.throwException(ex);
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError(e);
                    }
                }
                throw new AssertionError(ex);
            }
        }
    }

    class ThrowExceptionRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        ThrowExceptionRootNode() {
            super(ProxyLanguage.get(null));
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "throwException", THROW_EXCEPTION).build().createSection(1);
        }

        @Override
        public String getName() {
            return THROW_EXCEPTION;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject exceptionSupplier = (TruffleObject) frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                TruffleObject exception = (TruffleObject) interop.execute(exceptionSupplier, args);
                throw interop.throwException(exception);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }
}
