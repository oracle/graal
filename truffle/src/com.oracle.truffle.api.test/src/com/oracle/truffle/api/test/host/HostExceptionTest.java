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

import static java.util.function.Predicate.not;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
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
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
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
    private HostAccess hostAccess = HostAccess.ALL;
    private Class<? extends Throwable> expectedException;
    private Consumer<Throwable> hostExceptionVerifier = this::verifyHostException;
    private Consumer<Throwable> customExceptionVerifier;
    private boolean checkHostExceptionElements;

    private static final String INSTRUMENTATION_TEST_LANGUAGE = "instrumentation-test-language";

    private static final String CATCHER = "catcher";
    private static final String RUNNER = "runner";
    private static final String RETHROWER = "rethrower";
    private static final String THROW_EXCEPTION = "throwException";
    private static final String TRY_CATCH = "catchException";
    private static final String GET_STACK = "getStack";

    private static final String VALUE_EXECUTE = Value.class.getName() + "." + "execute";

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
        if (context != null) {
            after();
        }
        context = Context.newBuilder().allowHostAccess(hostAccess).out(outStream).build();
        if (TruffleTestAssumptions.isNoIsolateEncapsulation()) {
            ProxyLanguage.setDelegate(new ProxyLanguage() {
                @Override
                protected LanguageContext createContext(Env contextEnv) {
                    env = contextEnv;
                    return super.createContext(contextEnv);
                }

                @Override
                protected CallTarget parse(ParsingRequest request) throws Exception {
                    String requestedSource = request.getSource().getCharacters().toString();
                    RootNode rootNode = switch (requestedSource) {
                        case CATCHER -> new CatcherRootNode();
                        case RUNNER -> new RunnerRootNode();
                        case RETHROWER -> new RethrowerRootNode();
                        case THROW_EXCEPTION -> new ThrowExceptionRootNode();
                        case TRY_CATCH -> new TryCatchRootNode();
                        case GET_STACK -> new GetStackRootNode();
                        default -> throw new IllegalArgumentException(requestedSource);
                    };
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
        customExceptionVerifier = null;
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
        customExceptionVerifier = (t) -> {
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

    private static RuntimeException newExceptionWithCause(String expectedMessage) {
        return new NoSuchElementException(expectedMessage, new NoSuchFieldException("no"));
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
            throw newExceptionWithCause(expectedMessage);
        };

        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);

        customExceptionVerifier = (hostEx) -> {
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

        PolyglotException polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedMessage, polyglotException.getMessage());
        List<String> expectedStack = List.of(
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));
        assertThat(polyglotException.getPolyglotStackTrace().iterator().next().getRootName(), containsString("newExceptionWithCause"));
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
            return newExceptionWithCause(expectedMessage);
        };

        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value throwException = context.eval(ProxyLanguage.ID, THROW_EXCEPTION);
        Value rethrower = context.eval(ProxyLanguage.ID, RETHROWER);

        customExceptionVerifier = (hostEx) -> {
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

        PolyglotException polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedMessage, polyglotException.getMessage());
        List<String> expectedStack = List.of(
                        THROW_EXCEPTION,
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));
        assertThat(polyglotException.getPolyglotStackTrace().iterator().next().getRootName(), containsString("newExceptionWithCause"));

        // unwrapping and wrapping the host exception again should not lose the stack trace
        Function<Object, Object> passthrough = t -> {
            assertThat(t, instanceOf(expectedException));
            return t;
        };
        result = runner.execute(runner, passthrough, result);
        assertHostException(result, expectedException);

        polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));
        assertThat(polyglotException.getPolyglotStackTrace().iterator().next().getRootName(), containsString("newExceptionWithCause"));

        Consumer<Object> thrower = t -> {
            assertThat(t, instanceOf(expectedException));
            throw (RuntimeException) expectedException.cast(t);
        };
        result = catcher.execute(rethrower, thrower, result);
        assertHostException(result, expectedException);

        polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedMessage, polyglotException.getMessage());
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));
        assertThat(polyglotException.getPolyglotStackTrace().iterator().next().getRootName(), containsString("newExceptionWithCause"));
    }

    private static Value hostApply(Value[] args) {
        return args[0].execute((Object[]) Arrays.copyOfRange(args, 1, args.length));
    }

    private static Value hostCatch(Value[] args) {
        return args[0].execute((Object[]) Arrays.copyOfRange(args, 1, args.length));
    }

    private static Value hostRethrow(Value[] args) {
        return args[0].execute((Object[]) Arrays.copyOfRange(args, 1, args.length));
    }

    @FunctionalInterface
    public interface VarArgsFunction {
        @HostAccess.Export
        Value apply(Value... args);
    }

    @Test
    public void testMixedHostAndGuestStackTraceFromHostException() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        String expectedMessage = "oh";

        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Runnable hostThrower = () -> {
            throw newExceptionWithCause(expectedMessage);
        };
        ProxyExecutable proxyRunner = HostExceptionTest::hostApply;
        VarArgsFunction hostRunner = HostExceptionTest::hostApply;

        customExceptionVerifier = (hostEx) -> {
            assertEquals(List.of(expectedException.getSimpleName() + ": " + expectedMessage,
                            RUNNER,
                            RUNNER,
                            RUNNER,
                            CATCHER),
                            formatInteropExceptionStackTrace(hostEx, true, false));

            assertEquals(List.of(expectedException.getSimpleName() + ": " + expectedMessage,
                            hostQualify("newExceptionWithCause"),
                            hostQualify("lambda"),
                            RUNNER,
                            VALUE_EXECUTE,
                            hostQualify("hostApply"),
                            RUNNER,
                            VALUE_EXECUTE,
                            hostQualify("hostApply"),
                            RUNNER,
                            CATCHER), formatInteropExceptionStackTrace(hostEx, false, false));
        };

        Value result = catcher.execute(runner, hostRunner, runner, proxyRunner, runner, hostThrower);
        assertHostException(result, expectedException);

        PolyglotException polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedMessage, polyglotException.getMessage());
        List<String> expectedStack = List.of(
                        RUNNER,
                        RUNNER,
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));
        assertThat(polyglotException.getPolyglotStackTrace().iterator().next().getRootName(), containsString("newExceptionWithCause"));

        assertEquals(List.of(expectedMessage,
                        hostQualify("newExceptionWithCause"),
                        hostQualify("lambda"),
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        RUNNER,
                        CATCHER,
                        VALUE_EXECUTE),
                        formatPolyglotExceptionStackTrace(polyglotException));
    }

    @Test
    public void testMixedHostAndGuestStackTraceFromGuestException() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        String expectedMessage = "oh";

        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value throwException = context.eval(ProxyLanguage.ID, THROW_EXCEPTION);
        ProxyExecutable proxyRunner = HostExceptionTest::hostApply;
        VarArgsFunction hostRunner = HostExceptionTest::hostApply;

        hostExceptionVerifier = null;
        customExceptionVerifier = (hostEx) -> {
            assertEquals(List.of(expectedMessage,
                            THROW_EXCEPTION,
                            RUNNER,
                            RUNNER,
                            RUNNER,
                            CATCHER),
                            formatInteropExceptionStackTrace(hostEx, true, false));

            assertEquals(List.of(expectedMessage,
                            THROW_EXCEPTION,
                            RUNNER,
                            VALUE_EXECUTE,
                            hostQualify("hostApply"),
                            RUNNER,
                            VALUE_EXECUTE,
                            hostQualify("hostApply"),
                            RUNNER,
                            CATCHER),
                            formatInteropExceptionStackTrace(hostEx, false, false));
        };

        Value result = catcher.execute(runner, hostRunner, runner, proxyRunner, runner, throwException, expectedMessage);
        assertTrue(result.toString(), result.isException());
        assertFalse(result.toString(), result.isHostObject());

        PolyglotException polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedMessage, polyglotException.getMessage());
        List<String> expectedStack = List.of(
                        THROW_EXCEPTION,
                        RUNNER,
                        RUNNER,
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));

        assertEquals(List.of(expectedMessage,
                        THROW_EXCEPTION,
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        RUNNER,
                        CATCHER,
                        VALUE_EXECUTE),
                        formatPolyglotExceptionStackTrace(polyglotException));
    }

    /**
     * Simulate out-of-sync guest stack trace due to missing {@link TruffleStackTrace#fillIn}.
     */
    @Test
    public void testHostAndGuestStackTraceOutOfSync() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        expectedException = NoSuchElementException.class;
        String expectedMessage = "oh";

        Value tryCatch = context.eval(ProxyLanguage.ID, TRY_CATCH);
        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value getStack = context.eval(ProxyLanguage.ID, GET_STACK);
        Runnable hostThrower = () -> {
            throw newExceptionWithCause(expectedMessage);
        };

        hostExceptionVerifier = null;
        customExceptionVerifier = (hostEx) -> {
            assertEquals(List.of(expectedException.getSimpleName() + ": " + expectedMessage,
                            hostQualify("newExceptionWithCause"),
                            hostQualify("lambda"),
                            RUNNER,
                            RUNNER,
                            GET_STACK,
                            TRY_CATCH),
                            formatInteropExceptionStackTrace(hostEx, false, false));
        };

        Value result = tryCatch.execute(false,
                        ProxyArray.fromArray(runner, runner, hostThrower),
                        ProxyArray.fromArray(getStack));
        assertTrue(result.toString(), result.isException());

        PolyglotException polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedMessage, polyglotException.getMessage());

        assertEquals(List.of(expectedMessage,
                        hostQualify("newExceptionWithCause"),
                        hostQualify("lambda"),
                        RUNNER,
                        RUNNER,
                        GET_STACK,
                        TRY_CATCH,
                        VALUE_EXECUTE),
                        formatPolyglotExceptionStackTrace(polyglotException));
    }

    @Test
    public void testGuestExceptionCaughtByHost() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        String expectedMessage = "oh";
        expectedException = PolyglotException.class;

        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value throwException = context.eval(ProxyLanguage.ID, THROW_EXCEPTION);
        ProxyExecutable proxyRunner = HostExceptionTest::hostApply;
        VarArgsFunction hostCatcher = (args) -> {
            try {
                return hostCatch(args);
            } catch (PolyglotException polyglotException) {
                testGuestExceptionCaughtByHostVerify(polyglotException, expectedMessage);
                return context.asValue(polyglotException);
            }
        };
        VarArgsFunction hostRethrower = (args) -> {
            Value res = hostRethrow(args);
            throw res.throwException();
        };

        hostExceptionVerifier = null;
        customExceptionVerifier = (guestEx) -> {
            assertFalse(guestEx.toString(), env.isHostException(guestEx));
            assertTrue(guestEx.toString(), INTEROP.isException(guestEx));

            assertEquals(List.of(expectedMessage,
                            THROW_EXCEPTION,
                            RUNNER,
                            RUNNER,
                            CATCHER),
                            formatInteropExceptionStackTrace(guestEx, true, true));

            assertEquals(List.of(expectedMessage,
                            THROW_EXCEPTION,
                            RUNNER,
                            VALUE_EXECUTE,
                            hostQualify("hostApply"),
                            RUNNER,
                            VALUE_EXECUTE,
                            hostQualify("hostCatch"),
                            hostQualify("lambda"),
                            VALUE_EXECUTE,
                            hostQualify("hostApply"),
                            VALUE_EXECUTE,
                            hostQualify("hostRethrow"),
                            hostQualify("lambda"),
                            CATCHER),
                            formatInteropExceptionStackTrace(guestEx, false, true));
        };

        Value result = catcher.execute(hostRethrower, proxyRunner, hostCatcher, runner, proxyRunner, runner, throwException, expectedMessage);
        assertTrue(result.toString(), result.isException());
        assertFalse(result.toString(), result.isHostObject());
        testGuestExceptionCaughtByHostVerify(result.as(PolyglotException.class), expectedMessage);
    }

    private static void testGuestExceptionCaughtByHostVerify(PolyglotException polyglotException, String expectedMessage) {
        assertEquals(expectedMessage, polyglotException.getMessage());
        List<String> expectedStack = List.of(
                        THROW_EXCEPTION,
                        RUNNER,
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));

        assertEquals(List.of(expectedMessage,
                        THROW_EXCEPTION,
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostCatch"),
                        hostQualify("lambda"),
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        VALUE_EXECUTE,
                        hostQualify("hostRethrow"),
                        hostQualify("lambda"),
                        CATCHER,
                        VALUE_EXECUTE),
                        formatPolyglotExceptionStackTrace(polyglotException));
    }

    @Test
    public void testHideHostStackFrames() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        hostAccess = HostAccess.newBuilder(HostAccess.EXPLICIT).allowAccessInheritance(true).build();
        before();

        String expectedMessage = "oh";
        expectedException = PolyglotException.class;

        Value runner = context.eval(ProxyLanguage.ID, RUNNER);
        Value catcher = context.eval(ProxyLanguage.ID, CATCHER);
        Value throwException = context.eval(ProxyLanguage.ID, THROW_EXCEPTION);
        ProxyExecutable proxyRunner = HostExceptionTest::hostApply;
        VarArgsFunction hostRunner = HostExceptionTest::hostApply;

        hostExceptionVerifier = null;
        customExceptionVerifier = (guestEx) -> {
            assertFalse(guestEx.toString(), env.isHostException(guestEx));
            assertTrue(guestEx.toString(), INTEROP.isException(guestEx));

            List<String> expectedStack = List.of(expectedMessage,
                            THROW_EXCEPTION,
                            RUNNER,
                            RUNNER,
                            RUNNER,
                            CATCHER);

            assertEquals(expectedStack, formatInteropExceptionStackTrace(guestEx, true, true));
            assertEquals(expectedStack, formatInteropExceptionStackTrace(guestEx, false, true));
        };

        Value result = catcher.execute(runner, proxyRunner, runner, hostRunner, runner, throwException, expectedMessage);
        assertTrue(result.toString(), result.isException());
        assertFalse(result.toString(), result.isHostObject());

        PolyglotException polyglotException = result.as(PolyglotException.class);
        assertEquals(expectedMessage, polyglotException.getMessage());
        List<String> expectedStack = List.of(
                        THROW_EXCEPTION,
                        RUNNER,
                        RUNNER,
                        RUNNER,
                        CATCHER);
        assertEquals(expectedStack, getProxyLanguageStackTrace(polyglotException));

        assertEquals(List.of(expectedMessage,
                        THROW_EXCEPTION,
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        RUNNER,
                        VALUE_EXECUTE,
                        hostQualify("hostApply"),
                        RUNNER,
                        CATCHER,
                        VALUE_EXECUTE),
                        formatPolyglotExceptionStackTrace(polyglotException));
    }

    private static void assertHostException(Value result, Class<? extends Throwable> expectedException) {
        assertTrue(result.toString(), result.isException());
        assertTrue(result.toString(), result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(expectedException));
    }

    private static List<StackFrame> getProxyLanguageFrames(PolyglotException polyglotException) {
        return stream(polyglotException.getPolyglotStackTrace()).filter(s -> s.getLanguage().getId().equals(ProxyLanguage.ID)).toList();
    }

    private static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static List<String> formatInteropExceptionStackTrace(Object exception) {
        return formatInteropExceptionStackTrace(exception, true, false);
    }

    private static List<String> formatInteropExceptionStackTrace(Object exception, boolean skipHostFrames, boolean ignoreSourceLocation) {
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

                    String methodName;
                    if (INTEROP.hasExecutableName(stackTraceElement)) {
                        methodName = INTEROP.asString(INTEROP.getExecutableName(stackTraceElement));
                    } else {
                        methodName = "Unnamed";
                    }

                    String className;
                    if (INTEROP.hasDeclaringMetaObject(stackTraceElement)) {
                        className = INTEROP.asString(INTEROP.getMetaQualifiedName(INTEROP.getDeclaringMetaObject(stackTraceElement)));
                    } else {
                        className = "";
                    }

                    boolean isHostFrame = isHostStackTraceElement(stackTraceElement);
                    if (isHostFrame) {
                        if (skipHostFrames) {
                            continue;
                        } else {
                            // ensure stable method name.
                            methodName = censorLambdaName(methodName);
                        }
                    }

                    String line = className.isEmpty() ? methodName : className + "." + methodName;

                    lines.add(line);

                    if (!isHostFrame && !ignoreSourceLocation) {
                        assertTrue("Missing source location for stack trace element: " + line, INTEROP.hasSourceLocation(stackTraceElement));
                    }
                }
            }
            return lines;
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    /**
     * Crude detection of host stack trace elements.
     */
    private static boolean isHostStackTraceElement(Object stackTraceElement) throws UnsupportedMessageException {
        if (INTEROP.hasDeclaringMetaObject(stackTraceElement)) {
            return INTEROP.asString(INTEROP.getMetaQualifiedName(INTEROP.getDeclaringMetaObject(stackTraceElement))).contains(".");
        } else if (INTEROP.hasExecutableName(stackTraceElement)) {
            return INTEROP.asString(INTEROP.getExecutableName(stackTraceElement)).contains(".");
        } else {
            return false;
        }
    }

    private static List<String> formatPolyglotExceptionStackTrace(PolyglotException polyglotException) {
        Stream<String> stackTrace = stream(polyglotException.getPolyglotStackTrace()).takeWhile(not(HostExceptionTest::isTrailingFrame)).map(element -> {
            if (element.isGuestFrame()) {
                assertNotNull("Missing source location for stack trace element: " + element.getRootName(), element.getSourceLocation());
            }
            String methodName = element.getRootName();
            return censorLambdaName(methodName);
        });
        return Stream.concat(Stream.of(polyglotException.getMessage()), stackTrace).toList();
    }

    private static String censorLambdaName(String methodName) {
        if (methodName.contains("lambda")) {
            return methodName.substring(0, methodName.indexOf("lambda") + "lambda".length());
        } else {
            return methodName;
        }
    }

    private static List<String> getProxyLanguageStackTrace(PolyglotException polyglotException) {
        return getProxyLanguageFrames(polyglotException).stream().map(stackFrame -> {
            assertNotNull("Missing source location for stack trace element: " + stackFrame.getRootName(), stackFrame.getSourceLocation());
            return stackFrame.getRootName();
        }).toList();
    }

    private static boolean isTrailingFrame(PolyglotException.StackFrame s) {
        return s.getRootName().startsWith(HostExceptionTest.class.getName() + "." + "test");
    }

    private static String hostQualify(String methodName) {
        return HostExceptionTest.class.getName() + "." + methodName;
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
                checkShouldHaveThrown();
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

    private void verifyHostException(Throwable ex) {
        assertTrue(env.isHostObject(ex));
        assertNotNull("Unexpected exception: " + ex, expectedException);
        assertThat(env.asHostObject(ex), instanceOf(expectedException));
        assertThat(LanguageContext.get(null).getEnv().asHostException(ex), instanceOf(expectedException));
        try {
            assertTrue(InteropLibrary.getUncached().isMetaInstance(env.asHostSymbol(Throwable.class), ex));
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    @TruffleBoundary
    Object checkAndUnwrapException(Throwable ex) {
        // Avoid catching an AssertionError wrapped as a host exception.
        if (env.isHostException(ex)) {
            Throwable t = env.asHostException(ex);
            if (t instanceof AssertionError) {
                throw (AssertionError) t;
            }
        }

        if (hostExceptionVerifier != null) {
            hostExceptionVerifier.accept(ex);
        }
        if (customExceptionVerifier != null) {
            customExceptionVerifier.accept(ex);
        }
        return ex;
    }

    private void checkShouldHaveThrown() {
        if (expectedException != null) {
            shouldHaveThrown(expectedException);
        } else if (customExceptionVerifier != null || hostExceptionVerifier != null) {
            shouldHaveThrown(Throwable.class);
        }
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
            Object exceptionSupplier = frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                Object exception;
                if (interop.isExecutable(exceptionSupplier)) {
                    exception = interop.execute(exceptionSupplier, args);
                } else if (interop.isString(exceptionSupplier)) {
                    exception = new GuestException(interop.asString(exceptionSupplier), this);
                } else if (interop.isException(exceptionSupplier)) {
                    exception = exceptionSupplier;
                } else {
                    exception = new GuestException(null, this);
                }
                throw interop.throwException(exception);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    class TryCatchRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        TryCatchRootNode() {
            super(ProxyLanguage.get(null));
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, TRY_CATCH, TRY_CATCH).build().createSection(1);
        }

        @Override
        public String getName() {
            return TRY_CATCH;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            boolean fillIn = (boolean) frame.getArguments()[0];
            Object[] tryArgs = toArray(frame.getArguments()[1]);
            Object[] catchArgs = toArray(frame.getArguments()[2]);
            try {
                try {
                    Object result = interop.execute(tryArgs[0], Arrays.copyOfRange(tryArgs, 1, tryArgs.length));
                    checkShouldHaveThrown();
                    return result;
                } catch (AbstractTruffleException ex) {
                    if (fillIn) {
                        TruffleStackTrace.fillIn(ex);
                    }
                    Object exceptionObj = ex;
                    Object[] a = new Object[catchArgs.length];
                    System.arraycopy(catchArgs, 1, a, 0, catchArgs.length - 1);
                    a[a.length - 1] = exceptionObj;
                    exceptionObj = interop.execute(catchArgs[0], a);
                    return exceptionObj;
                }
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        private Object[] toArray(Object array) {
            try {
                int size = (int) interop.getArraySize(array);
                var result = new Object[size];
                for (int i = 0; i < size; i++) {
                    result[i] = interop.readArrayElement(array, i);
                }
                return result;
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    class GetStackRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        GetStackRootNode() {
            super(ProxyLanguage.get(null));
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, GET_STACK, GET_STACK).build().createSection(1);
        }

        @Override
        public String getName() {
            return GET_STACK;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            Object exceptionObj = arguments[0];
            if (!interop.hasExceptionStackTrace(exceptionObj)) {
                throw CompilerDirectives.shouldNotReachHere("!hasExceptionStackTrace");
            }
            checkAndUnwrapException((Throwable) exceptionObj);
            return exceptionObj;
        }
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static final class GuestException extends AbstractTruffleException {

        GuestException(String message, Node location) {
            super(message, location);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isException() {
            return true;
        }

        @ExportMessage
        RuntimeException throwException() {
            throw this;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        ExceptionType getExceptionType() {
            return ExceptionType.RUNTIME_ERROR;
        }

        @ExportMessage
        boolean hasSourceLocation() {
            Node location = getLocation();
            return location != null && location.getEncapsulatingSourceSection() != null;
        }

        @ExportMessage
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            Node location = getLocation();
            SourceSection section = location == null ? null : location.getEncapsulatingSourceSection();
            if (section == null) {
                throw UnsupportedMessageException.create();
            } else {
                return section;
            }
        }
    }
}
