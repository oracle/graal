/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;
import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.execute;
import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.NullObject;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/*
 * There is other TruffleContextTest in com.oracle.truffle.api.instrumentation.test package.
 * We should move its tests that don't (or shouldn't) require instrumentation to this test.
 */
public class TruffleContextTest {

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static final class OtherContextDiedException extends AbstractTruffleException {

        OtherContextDiedException(@SuppressWarnings("unused") TruffleContext outerCreatorContext, String name) {
            super(name);
            TruffleContext currentContext = TestAPIAccessor.engineAccess().getCurrentCreatorTruffleContext();
            /*
             * The current context should be the outer context and it should still be usable.
             */
            currentContext.leave(null, currentContext.enter(null));
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.RUNTIME_ERROR;
        }
    }

    private static final String INSTRUMENTATION_TEST_LANGUAGE = "instrumentation-test-language";

    @Registration
    static class InnerContextCancelTestLanguage1 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).build()) {
                Object executable = innerContext.evalPublic(node, Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "BLOCK(DEFINE(cancel, CANCEL()), RETURN(cancel))", "").build());

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), IllegalStateException.class, (e) -> {
                    assertEquals("Context cancel exception of inner context leaks outside to a non-cancelled context!", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that the cancel exception of an inner context reaching the outer context produces an
     * internal error if {@link TruffleContext.Builder#onCancelled(Runnable)} was not used for the
     * inner context.
     */
    @Test
    public void testInnerContextCancelInvalidAccess() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextCancelTestLanguage1.class, "");
        }
    }

    @Registration
    static class InnerContextCancelTestLanguage2 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext outerContext = TestAPIAccessor.engineAccess().getCurrentCreatorTruffleContext();
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).onCancelled(new Runnable() {
                @Override
                public void run() {
                    throw new OtherContextDiedException(outerContext, "Inner context cancelled");
                }
            }).build()) {
                Object executable = innerContext.evalPublic(node, Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "BLOCK(DEFINE(cancel, CANCEL()), RETURN(cancel))", "").build());

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context cancelled", e.getMessage());
                });

            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that the cancel exception of an inner context reaching the outer context causes the
     * runnable specified in {@link TruffleContext.Builder#onCancelled(Runnable)} to be called and a
     * custom truffle exception can be thrown in that situation.
     */
    @Test
    public void testInnerContextCancel() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextCancelTestLanguage2.class, "");
        }
    }

    @Registration
    static class InnerContextCancelTestLanguage3 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).build()) {
                Source src = Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "DEFINE(statememt, STATEMENT)", "").build();
                Object executable = innerContext.evalPublic(node, src);

                innerContext.closeCancelled(null, "cancel upfront");

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), IllegalStateException.class, (e) -> {
                    assertEquals("Context cancel exception of inner context leaks outside to a non-cancelled context!", e.getMessage());
                });
                AbstractPolyglotTest.assertFails(() -> innerContext.evalPublic(node, src), IllegalStateException.class, (e) -> {
                    assertEquals("Context cancel exception of inner context leaks outside to a non-cancelled context!", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that using a cancelled inner context produces an internal error if
     * {@link TruffleContext.Builder#onCancelled(Runnable)} was not used for the inner context.
     */
    @Test
    public void testCancelledInnerContextInvalidAccess() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextCancelTestLanguage3.class, "");
        }
    }

    @Registration
    static class InnerContextTestLanguage4 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext outerContext = TestAPIAccessor.engineAccess().getCurrentCreatorTruffleContext();
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).onCancelled(new Runnable() {
                @Override
                public void run() {
                    throw new OtherContextDiedException(outerContext, "Inner context cancelled");
                }
            }).build()) {
                Source src = Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "DEFINE(statememt, STATEMENT)", "").build();
                Object executable = innerContext.evalPublic(node, src);

                innerContext.closeCancelled(null, "cancel upfront");

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context cancelled", e.getMessage());
                });
                AbstractPolyglotTest.assertFails(() -> innerContext.evalPublic(node, src), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context cancelled", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that using a cancelled inner context causes the runnable specified in
     * {@link TruffleContext.Builder#onCancelled(Runnable)} to be called and a custom truffle
     * exception can be thrown in that situation.
     */
    @Test
    public void testCancelledInnerContext() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextTestLanguage4.class, "");
        }
    }

    @Registration
    static class InnerContextExitTestLanguage1 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).build()) {
                Object executable = innerContext.evalPublic(node, Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "BLOCK(DEFINE(exit, EXIT(42)), RETURN(exit))", "").build());

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), IllegalStateException.class, (e) -> {
                    assertEquals("Context exit exception of inner context leaks outside to a non-exited context!", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that the exit exception of an inner context reaching the outer context produces an
     * internal error if {@link TruffleContext.Builder#onExited(Consumer)} was not used for the
     * inner context.
     */
    @Test
    public void testInnerContextExitInvalidAccess() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextExitTestLanguage1.class, "");
        }
    }

    @Registration
    static class InnerContextExitTestLanguage2 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext outerContext = TestAPIAccessor.engineAccess().getCurrentCreatorTruffleContext();
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).onExited(new Consumer<Integer>() {
                @Override
                public void accept(Integer exitCode) {
                    throw new OtherContextDiedException(outerContext, "Inner context exited with exit code " + exitCode);
                }
            }).build()) {
                Object executable = innerContext.evalPublic(node, Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "BLOCK(DEFINE(exit, EXIT(42)), RETURN(exit))", "").build());

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context exited with exit code 42", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that the exit exception of an inner context reaching the outer context causes the
     * consumer specified in {@link TruffleContext.Builder#onExited(Consumer)} to be called and a
     * custom truffle exception can be thrown in that situation.
     */
    @Test
    public void testInnerContextExit() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextExitTestLanguage2.class, "");
        }
    }

    @Registration
    static class InnerContextExitTestLanguage3 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).build()) {
                Source src = Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "STATEMENT", "").build();
                Object executable = innerContext.evalPublic(node, src);

                Object prev = innerContext.enter(node);
                try {
                    innerContext.closeExited(node, 42);
                } catch (ThreadDeath e) {
                    assertEquals("Exit was called with exit code 42.", e.getMessage());
                } finally {
                    innerContext.leave(node, prev);
                }

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), IllegalStateException.class, (e) -> {
                    assertEquals("Context exit exception of inner context leaks outside to a non-exited context!", e.getMessage());
                });
                AbstractPolyglotTest.assertFails(() -> innerContext.evalPublic(node, src), IllegalStateException.class, (e) -> {
                    assertEquals("Context exit exception of inner context leaks outside to a non-exited context!", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that using an exited inner context produces an internal error if
     * {@link TruffleContext.Builder#onExited(Consumer)} was not used for the inner context.
     */
    @Test
    public void testExitedInnerContextInvalidAccess() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextExitTestLanguage3.class, "");
        }
    }

    @Registration
    static class InnerContextExitTestLanguage4 extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext outerContext = TestAPIAccessor.engineAccess().getCurrentCreatorTruffleContext();
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).onExited(new Consumer<Integer>() {
                @Override
                public void accept(Integer exitCode) {
                    throw new OtherContextDiedException(outerContext, "Inner context exited with exit code " + exitCode);
                }
            }).build()) {
                Source src = Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "STATEMENT", "").build();
                Object executable = innerContext.evalPublic(node, src);

                Object prev = innerContext.enter(node);
                try {
                    innerContext.closeExited(node, 42);
                } catch (ThreadDeath e) {
                    assertEquals("Exit was called with exit code 42.", e.getMessage());
                } finally {
                    innerContext.leave(node, prev);
                }

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context exited with exit code 42", e.getMessage());
                });
                AbstractPolyglotTest.assertFails(() -> innerContext.evalPublic(node, src), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context exited with exit code 42", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that using an exited inner context causes the consumer specified in
     * {@link TruffleContext.Builder#onExited(Consumer)} to be called and a custom truffle exception
     * can be thrown in that situation.
     */
    @Test
    public void testExitedInnerContext() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextExitTestLanguage4.class, "");
        }
    }

    @Registration
    static class InnerContextCloseTestLanguage1 extends AbstractExecutableTestLanguage {

        @SuppressWarnings("try")
        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).build()) {
                Source src = Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "STATEMENT", "").build();
                Object executable = innerContext.evalPublic(node, src);

                innerContext.close();

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), IllegalStateException.class, (e) -> {
                    assertEquals("Context close exception of inner context leaks outside to a non-closed context!", e.getMessage());
                });
                AbstractPolyglotTest.assertFails(() -> innerContext.evalPublic(node, src), IllegalStateException.class, (e) -> {
                    assertEquals("Context close exception of inner context leaks outside to a non-closed context!", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that using a closed inner context produces an internal error if
     * {@link TruffleContext.Builder#onClosed(Runnable)} was not used for the inner context.
     */
    @Test
    public void testClosedInnerContextInvalidAccess() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextCloseTestLanguage1.class, "");
        }
    }

    @Registration
    static class InnerContextCloseTestLanguage2 extends AbstractExecutableTestLanguage {

        @SuppressWarnings("try")
        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext outerContext = TestAPIAccessor.engineAccess().getCurrentCreatorTruffleContext();
            try (TruffleContext innerContext = env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).onClosed(new Runnable() {
                @Override
                public void run() {
                    throw new OtherContextDiedException(outerContext, "Inner context closed");
                }
            }).build()) {
                Source src = Source.newBuilder(INSTRUMENTATION_TEST_LANGUAGE, "STATEMENT", "").build();
                Object executable = innerContext.evalPublic(node, src);

                innerContext.close();

                AbstractPolyglotTest.assertFails(() -> InteropLibrary.getUncached().execute(executable), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context closed", e.getMessage());
                });
                AbstractPolyglotTest.assertFails(() -> innerContext.evalPublic(node, src), OtherContextDiedException.class, (e) -> {
                    assertEquals("Inner context closed", e.getMessage());
                });
            }
            return NullObject.SINGLETON;
        }
    }

    /**
     * Test that using a closed inner context causes the runnable specified in
     * {@link TruffleContext.Builder#onClosed(Runnable)} to be called and a custom truffle exception
     * can be thrown in that situation.
     */
    @Test
    public void testClosedInnerContext() {
        try (Context context = Context.newBuilder().allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            evalTestLanguage(context, InnerContextCloseTestLanguage2.class, "");
        }
    }

    @Registration
    static class EnclosingTestLanguage extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Object executable = contextArguments[0];
            InteropLibrary.getUncached().execute(executable);
            fail();
            return null;
        }
    }

    /**
     * Test that if an outer context's value is being executed from a different outer context and
     * that value's context (enclosed context) is cancelled, the cancel exception reaching the other
     * context is wrapped as a PolyglotException (host exception).
     */
    @Test
    public void testCancelEnclosedOuterContext() {
        try (Context context1 = Context.create(); Context context2 = Context.create()) {
            Value val = context1.eval(INSTRUMENTATION_TEST_LANGUAGE, "BLOCK(DEFINE(cancel, CANCEL()), RETURN(cancel))");
            evalTestLanguage(context2, EnclosingTestLanguage.class, "", val);
        } catch (PolyglotException pe) {
            assertTrue(pe.isHostException());
            assertTrue(pe.asHostException() instanceof PolyglotException);
            assertTrue(((PolyglotException) pe.asHostException()).isCancelled());
            assertEquals("Context execution was cancelled.", pe.asHostException().getMessage());
        }
    }

    /**
     * Test that if a cancelled outer context's value is used from a different outer context, the
     * cancel exception reaching the other context is wrapped as a PolyglotException (host
     * exception).
     */
    @Test
    public void testCanceledEnclosedOuterContext() {
        try (Context context1 = Context.create(); Context context2 = Context.create()) {
            Value val = context1.eval(INSTRUMENTATION_TEST_LANGUAGE, "");
            context1.close(true);
            evalTestLanguage(context2, EnclosingTestLanguage.class, "", val);
        } catch (PolyglotException pe) {
            assertTrue(pe.isHostException());
            assertTrue(pe.asHostException() instanceof PolyglotException);
            assertTrue(((PolyglotException) pe.asHostException()).isCancelled());
            assertEquals("Context execution was cancelled.", pe.asHostException().getMessage());
        }
    }

    /**
     * Test that if an outer context's value is being executed from a different outer context and
     * that value's context (enclosed context) is exited, the exit exception reaching the other
     * context is wrapped as a PolyglotException (host exception).
     */
    @Test
    public void testExitEnclosedOuterContext() {
        try (Context context1 = Context.create(); Context context2 = Context.create()) {
            Value val = context1.eval(INSTRUMENTATION_TEST_LANGUAGE, "BLOCK(DEFINE(exit, EXIT(42)), RETURN(exit))");
            evalTestLanguage(context2, EnclosingTestLanguage.class, "", val);
        } catch (PolyglotException pe) {
            assertTrue(pe.isHostException());
            assertTrue(pe.asHostException() instanceof PolyglotException);
            assertTrue(((PolyglotException) pe.asHostException()).isExit());
            assertEquals("Exit was called with exit code 42.", pe.asHostException().getMessage());
        }
    }

    /**
     * Test that if an exited outer context's value is used from a different outer context, the exit
     * exception reaching the other context is wrapped as a PolyglotException (host exception).
     */
    @Test
    public void testExitedEnclosedOuterContext() {
        try (Context context1 = Context.create(); Context context2 = Context.create()) {
            Value val = context1.eval(INSTRUMENTATION_TEST_LANGUAGE, "BLOCK(DEFINE(exit, EXIT(42)), RETURN(exit))");
            try {
                val.execute();
                fail();
            } catch (PolyglotException pe) {
                assertTrue(pe.isExit());
                assertEquals(pe.getExitStatus(), 42);
            }
            evalTestLanguage(context2, EnclosingTestLanguage.class, "", val);
        } catch (PolyglotException pe) {
            assertTrue(pe.isHostException());
            assertTrue(pe.asHostException() instanceof PolyglotException);
            assertTrue(((PolyglotException) pe.asHostException()).isExit());
            assertEquals("Exit was called with exit code 42.", pe.asHostException().getMessage());
        }
    }

    /**
     * Test that if a closed outer context's value is used from a different outer context, the close
     * exception reaching the other context is wrapped as a PolyglotException (host exception).
     */
    @Test
    @SuppressWarnings("try")
    public void testClosedEnclosedOuterContext() {
        try (Context context1 = Context.create(); Context context2 = Context.create()) {
            Value val = context1.eval(INSTRUMENTATION_TEST_LANGUAGE, "");
            context1.close();
            evalTestLanguage(context2, EnclosingTestLanguage.class, "", val);
        } catch (PolyglotException pe) {
            assertTrue(pe.isHostException());
            assertTrue(pe.asHostException() instanceof IllegalStateException);
            assertEquals("The Context is already closed.", pe.asHostException().getMessage());
        }
    }

    @Registration
    static class InitializePublicInnerContextLanguage extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext innerContext = env.newInnerContextBuilder().build();
            assertEquals(true, innerContext.initializePublic(null, PublicLanguage.ID));
            assertFails(() -> innerContext.initializePublic(null, InternalLanguage.ID), IllegalArgumentException.class);
            innerContext.close();
            return null;
        }
    }

    @Test
    public void testInitializePublicInnerContext() {
        try (Context c = Context.create()) {
            execute(c, InitializePublicInnerContextLanguage.class);
        }
    }

    @Registration
    static class InitializeInternalInnerContextLanguage extends AbstractExecutableTestLanguage {

        @TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            TruffleContext innerContext = env.newInnerContextBuilder().build();
            assertEquals(true, innerContext.initializeInternal(null, PublicLanguage.ID));
            assertEquals(true, innerContext.initializeInternal(null, InternalLanguage.ID));
            innerContext.close();
            return null;
        }
    }

    @Test
    public void testInitializeInternalInnerContext() {
        try (Context c = Context.create()) {
            execute(c, InitializeInternalInnerContextLanguage.class);
        }
    }

    @TruffleLanguage.Registration
    static class PublicLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(PublicLanguage.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }

    }

    @TruffleLanguage.Registration(internal = true)
    static class InternalLanguage extends AbstractExecutableTestLanguage {

        static final String ID = TestUtils.getDefaultLanguageId(InternalLanguage.class);

        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            return null;
        }

    }

}
