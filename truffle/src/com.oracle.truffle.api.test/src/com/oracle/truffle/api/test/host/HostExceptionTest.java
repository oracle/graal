/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
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

public class HostExceptionTest {
    private Context context;
    private Env env;
    private Class<? extends Throwable> expectedException;
    private boolean checkHostExceptionElements;

    @Before
    public void before() {
        context = Context.newBuilder().allowAllAccess(true).build();
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
                    case "catcher":
                        rootNode = new CatcherRootNode();
                        break;
                    case "runner":
                        rootNode = new RunnerRootNode();
                        break;
                    case "rethrower":
                        rootNode = new RethrowerRootNode();
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new CatcherObject(Truffle.getRuntime().createCallTarget(rootNode))));
            }
        });
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertNotNull(env);
    }

    @After
    public void after() {
        context.leave();
        context.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAsHostExceptionIllegalArgument() {
        env.asHostException(new Exception());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAsHostExceptionNull() {
        env.asHostException(null);
    }

    public static void thrower() {
        throw new NoSuchElementException();
    }

    @Test
    public void testUncaughtHostException() {
        Value catcher = context.eval(ProxyLanguage.ID, "runner");
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
            assertEquals("runner", sf.getRootName());
            sf = iterator.next();
            assertTrue(sf.isHostFrame());
            assertThat(sf.getRootName(), containsString("execute"));
        }
    }

    @Test
    public void testExceptionObject() {
        expectedException = NoSuchElementException.class;
        Value catcher = context.eval(ProxyLanguage.ID, "catcher");
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
            assertThat(sf.getMethodName(), containsString("catcher"));
            assertEquals(4, sf.getLineNumber());
            sf = iterator.next();
            assertThat(sf.getMethodName(), containsString("execute"));
        }
    }

    @Test
    public void testCatchAndThrow() {
        expectedException = NoSuchElementException.class;
        Value runner = context.eval(ProxyLanguage.ID, "runner");
        Value catcher = context.eval(ProxyLanguage.ID, "catcher");
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
        class BadException extends RuntimeException {
            @Override
            public void setStackTrace(StackTraceElement[] stackTrace) {
                throw new UnsupportedOperationException();
            }
        }

        expectedException = BadException.class;
        Value catcher = context.eval(ProxyLanguage.ID, "catcher");
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
        class BadException extends RuntimeException {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new UnsupportedOperationException();
            }
        }

        expectedException = BadException.class;
        Value catcher = context.eval(ProxyLanguage.ID, "catcher");
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
        class BadException extends RuntimeException {
            @Override
            public StackTraceElement[] getStackTrace() {
                return null;
            }
        }

        expectedException = BadException.class;
        Value runner = context.eval(ProxyLanguage.ID, "runner");
        Value catcher = context.eval(ProxyLanguage.ID, "catcher");
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
            assertTrue(polyglotException.asHostException() instanceof BadException);
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

    @Test
    public void testNestedPolyglotException() {
        expectedException = TestHostException.class;
        Value runner = context.eval(ProxyLanguage.ID, "runner");
        Value catcher = context.eval(ProxyLanguage.ID, "catcher");
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
            assertNull(polyglotException.asHostException().getCause());

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
            assertNull(inner.asHostException().getCause());

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
        expectedException = NoSuchElementException.class;
        Runnable thrower = HostExceptionTest::thrower;
        Value rethrower = context.eval(ProxyLanguage.ID, "rethrower");

        // throw and rethrow a host exception
        try {
            rethrower.executeVoid(thrower);
            shouldHaveThrown(PolyglotException.class);
        } catch (PolyglotException polyglotException) {
            assertNull("cause must be null", polyglotException.getCause());
            assertTrue(polyglotException.isHostException());
            assertThat(polyglotException.asHostException(), instanceOf(expectedException));
            assertNull(polyglotException.asHostException().getCause());
        }

        // throw, rethrow, and then catch a host exception
        Value catcher = context.eval(ProxyLanguage.ID, "catcher");
        Value result = catcher.execute(rethrower, thrower);
        assertTrue(result.isHostObject());
        assertThat(result.asHostObject(), instanceOf(NoSuchElementException.class));

        NoSuchElementException exception = result.asHostObject();
        assertNotNull(exception);
        assertThat(exception, instanceOf(expectedException));
    }

    static void shouldHaveThrown(Class<? extends Throwable> expected) {
        fail("Expected a " + expected + " but none was thrown");
    }

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
            super(ProxyLanguage.getCurrentLanguage());
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "\na\nb\nc\n", "catcher").build().createSection(4);
        }

        @Override
        public String getName() {
            return "catcher";
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject thrower = (TruffleObject) frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                return interop.execute(thrower, args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            } catch (Exception ex) {
                if (ex instanceof TruffleException) {
                    return checkAndUnwrapException(ex);
                }
                throw ex;
            }
        }
    }

    @TruffleBoundary
    Object checkAndUnwrapException(Throwable ex) {
        Object exceptionObject = ((TruffleException) ex).getExceptionObject();
        assertNotNull(exceptionObject);
        assertTrue(env.isHostObject(exceptionObject));
        assertNotNull("Unexpected exception: " + ex, expectedException);
        assertThat(env.asHostObject(exceptionObject), instanceOf(expectedException));
        assertThat(ProxyLanguage.getCurrentContext().getEnv().asHostException(ex), instanceOf(expectedException));
        assertTrue(InteropLibrary.getFactory().getUncached().isException(exceptionObject));
        return exceptionObject;
    }

    class RunnerRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        RunnerRootNode() {
            super(ProxyLanguage.getCurrentLanguage());
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "\na\nb\nc\n", "runner").build().createSection(4);
        }

        @Override
        public String getName() {
            return "runner";
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject thrower = (TruffleObject) frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                return interop.execute(thrower, args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
    }

    class RethrowerRootNode extends RootNode {
        @Child InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

        RethrowerRootNode() {
            super(ProxyLanguage.getCurrentLanguage());
        }

        @TruffleBoundary
        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ProxyLanguage.ID, "rethrow", "rethrower").build().createSection(1);
        }

        @Override
        public String getName() {
            return "rethrower";
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject thrower = (TruffleObject) frame.getArguments()[0];
            Object[] args = Arrays.copyOfRange(frame.getArguments(), 1, frame.getArguments().length);
            try {
                return interop.execute(thrower, args);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            } catch (Exception ex) {
                if (ex instanceof TruffleException) {
                    Object exObj = ((TruffleException) ex).getExceptionObject();
                    assertTrue(env.isHostObject(exObj));
                    assertTrue(interop.isException(exObj));
                    try {
                        throw interop.throwException(exObj);
                    } catch (UnsupportedMessageException e) {
                        throw new AssertionError(e);
                    }
                }
                throw new AssertionError(ex);
            }
        }
    }
}
