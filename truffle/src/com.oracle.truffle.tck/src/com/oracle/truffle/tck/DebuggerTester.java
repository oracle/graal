/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.tck;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.vm.PolyglotEngine;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

/**
 * Test utility class that makes it easier to test and debug debugger functionality for guest
 * languages. Testing suspended callbacks can be cumbersome when having to assert multiple
 * sequential events. The debugger tester allows to test suspended callbacks using sequential code
 * that allows to assert multiple {@link SuspendedEvent events}. It does so by running the engine on
 * a separate thread and it uses internal APIs to allow access to the {@link SuspendedEvent} from
 * another Thread. Do not use this class for anything else than testing.
 * <p>
 * The debugger tester can print debug traces to standard output with -Dtruffle.debug.trace=true.
 *
 * Example usage: {@link com.oracle.truffle.tck.DebuggerTesterSnippets#testDebugging()}
 *
 * @since 0.16
 */
public final class DebuggerTester implements AutoCloseable {

    static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");

    private final BlockingQueue<Object> newEvent;
    private final Semaphore executing;
    private final Semaphore initialized;
    private final Thread evalThread;
    private final Engine engine;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private volatile boolean closed;
    private volatile ExecutingSource source;

    private static void trace(String message) {
        if (TRACE) {
            PrintStream out = System.out;
            out.println("DebuggerTester: " + message);
        }
    }

    private final ExecutingLoop executingLoop;
    private SuspendedCallback handler;

    /**
     * Constructs a new debugger tester instance. Boots up a new {@link Context context} on Thread
     * in the background. The tester instance needs to be {@link #close() closed} after use. Throws
     * an AssertionError if the engine initialization fails.
     *
     * @since 0.16
     */
    public DebuggerTester() {
        this.newEvent = new ArrayBlockingQueue<>(1);
        this.executing = new Semaphore(0);
        this.initialized = new Semaphore(0);
        final AtomicReference<Engine> engineRef = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        this.executingLoop = new ExecutingLoop(engineRef, error);
        this.evalThread = new Thread(executingLoop);
        this.evalThread.start();
        try {
            initialized.acquire();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        this.engine = engineRef.get();
        if (error.get() != null) {
            throw new AssertionError("Engine initialization failed", error.get());
        }
    }

    /**
     * Constructs a new debugger tester instance. Boots up a new {@link PolyglotEngine engine} on
     * Thread in the background. The tester instance needs to be {@link #close() closed} after use.
     * Throws an AssertionError if the engine initialization fails.
     *
     * @param engineBuilderDecorator a decorator function that allows to customize the engine
     *            builder
     * @since 0.26
     * @deprecated Do not use {@link PolyglotEngine}, call {@link DebuggerTester()} instead.
     */
    @Deprecated
    public DebuggerTester(Function<PolyglotEngine.Builder, PolyglotEngine.Builder> engineBuilderDecorator) {
        this();
    }

    /**
     * Returns the error output of the underlying {@link Context context}.
     *
     * @since 0.16
     */
    public String getErr() {
        try {
            err.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String(err.toByteArray());
    }

    /**
     * Returns the standard output of the underlying {@link Context context}.
     *
     * @since 0.16
     */
    public String getOut() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String(out.toByteArray());
    }

    /**
     * Get the Debugger instance associated with the current engine.
     *
     * @since 0.27
     */
    public Debugger getDebugger() {
        return engine.getInstruments().get("debugger").lookup(Debugger.class);
    }

    /**
     * Starts a new {@link Debugger#startSession(SuspendedCallback) debugger session} in the
     * context's {@link Engine engine}. The debugger session allows to suspend the execution and to
     * install breakpoints. If multiple sessions are created for one {@link #startEval(Source)
     * evaluation} then all suspended events are delegated to this debugger tester instance.
     *
     * @return a new debugger session
     * @since 0.16
     */
    public DebuggerSession startSession() {
        return getDebugger().startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                DebuggerTester.this.onSuspend(event);
            }
        });
    }

    /**
     * Starts a new {@link PolyglotEngine#eval(Source) evaluation} on the background thread. Only
     * one evaluation can be active at a time. Please ensure that {@link #expectDone()} completed
     * successfully before starting a new evaluation. Throws an {@link IllegalStateException} if
     * another evaluation is still executing or the tester is already closed.
     *
     * @since 0.16
     * @deprecated Use {@link #startEval(org.graalvm.polyglot.Source)} instead.
     */
    @Deprecated
    public void startEval(com.oracle.truffle.api.source.Source s) {
        if (this.source != null) {
            throw new IllegalStateException("Already executing other source " + s);
        }
        throw new UnsupportedOperationException("Call startEval(org.graalvm.polyglot.Source) instead.");
    }

    /**
     * Starts a new {@link Context#eval(Source) evaluation} on the background thread. Only one
     * evaluation can be active at a time. Please ensure that {@link #expectDone()} completed
     * successfully before starting a new evaluation. Throws an {@link IllegalStateException} if
     * another evaluation is still executing or the tester is already closed.
     *
     * @since 0.27
     */
    public void startEval(Source s) {
        if (this.source != null) {
            throw new IllegalStateException("Already executing other source " + s);
        }
        this.source = new ExecutingSource(s);
    }

    /**
     * Expects an suspended event and returns it for potential assertions. If the execution
     * completed or was killed instead then an assertion error is thrown. The returned suspended
     * event is only valid until on of {@link #expectKilled()},
     * {@link #expectSuspended(SuspendedCallback)} or {@link #expectDone()} is called again. Throws
     * an {@link IllegalStateException} if the tester is already closed.
     *
     * @param callback handler to be called when the execution is suspended
     * @since 0.16
     */
    public void expectSuspended(SuspendedCallback callback) {
        if (closed) {
            throw new IllegalStateException("Already closed.");
        }
        SuspendedCallback previous = this.handler;
        this.handler = callback;
        notifyNextAction();
        Object event;
        try {
            event = takeEvent();
            String e = getErr();
            if (!e.isEmpty()) {
                throw new AssertionError("Error output is not empty: " + e);
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        if (event instanceof ExecutingSource) {
            ExecutingSource s = (ExecutingSource) event;
            if (s.error != null) {
                throw new AssertionError("Error in eval", s.error);
            }
            throw new AssertionError("Expected suspended event got return value " + s.returnValue);
        } else if (event instanceof SuspendedEvent) {
            this.handler = previous;
        } else {
            if (event instanceof Error) {
                throw (Error) event;
            }
            if (event instanceof RuntimeException) {
                throw (RuntimeException) event;
            }
            throw new AssertionError("Got unknown event.", (event instanceof Throwable ? (Throwable) event : null));
        }
    }

    /**
     * Expects the current evaluation to be completed with an error and not be killed or to produce
     * further suspended events. It returns a string representation of the result value to be
     * asserted. If the evaluation caused any errors they are thrown as {@link AssertionError}.
     * Throws an {@link IllegalStateException} if the tester is already closed.
     *
     * @since 0.16
     */
    public Throwable expectThrowable() {
        return (Throwable) expectDoneImpl(true);
    }

    /**
     * Expects the current evaluation to be completed successfully and not be killed or to produce
     * further suspended events. It returns a string representation of the result value to be
     * asserted. If the evaluation caused any errors they are thrown as {@link AssertionError}.
     * Throws an {@link IllegalStateException} if the tester is already closed.
     *
     * @since 0.16
     */
    public String expectDone() {
        return (String) expectDoneImpl(false);
    }

    private Object expectDoneImpl(boolean expectError) throws AssertionError {
        if (closed) {
            throw new IllegalStateException("Already closed.");
        }
        try {
            notifyNextAction();
            Object event;
            try {
                event = takeEvent(); // waits for next event.
                String e = getErr();
                if (!e.isEmpty()) {
                    throw new AssertionError("Error output is not empty: " + e);
                }
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
            if (event instanceof ExecutingSource) {
                ExecutingSource s = (ExecutingSource) event;
                if (expectError) {
                    if (s.error == null) {
                        throw new AssertionError("Error expected exception bug got return value: " + s.returnValue);
                    }
                    return s.error;
                } else {
                    if (s.error != null) {
                        throw new AssertionError("Error in eval", s.error);
                    }
                    return s.returnValue;
                }
            } else if (event instanceof SuspendedEvent) {
                throw new AssertionError("Expected done but got " + event);
            } else {
                throw new AssertionError("Got unknown");
            }
        } finally {
            source = null;
        }
    }

    /**
     * Expects the current evaluation to be killed and not be completed or to produce further
     * suspended events. Throws an {@link IllegalStateException} if the tester is already closed. If
     * the evaluation caused any errors besides the kill exception then they are thrown as
     * {@link AssertionError}.
     *
     * @since 0.16
     */
    public void expectKilled() {
        Throwable error = expectThrowable();
        if (error instanceof PolyglotException) {
            Assert.assertTrue(error.getMessage(), error.getMessage().contains("KillException"));
            return;
        }
        throw new AssertionError("Expected killed bug got error: " + error, error);
    }

    /**
     * Returns the thread that the execution started with {@link #startEval(Source)} is running on.
     *
     * @return the thread instance
     * @since 0.16
     */
    public Thread getEvalThread() {
        return evalThread;
    }

    /**
     * Closes the current debugger tester session and all its associated resources like the
     * background thread. The debugger tester becomes unusable after closing.
     *
     * @since 0.16
     */
    public void close() {
        if (closed) {
            throw new IllegalStateException("Already closed.");
        }
        closed = true;
        engine.close();
        trace("kill session " + this);
        // trying to interrupt if execution is in IO.
        notifyNextAction();
    }

    /**
     * Get {@link com.oracle.truffle.api.source.Source Truffle Source} that corresponds to the
     * {@link Source Polyglot Source}. This is a bridge between the two Source implementations.
     *
     * @since 0.28
     */
    public static com.oracle.truffle.api.source.Source getSourceImpl(Source source) {
        return (com.oracle.truffle.api.source.Source) getField(source, "impl");
    }

    // Copied from ReflectionUtils.
    private static Object getField(Object value, String name) {
        try {
            Field f = value.getClass().getDeclaredField(name);
            setAccessible(f, true);
            return f.get(value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void setAccessible(Field field, boolean flag) {
        if (!Java8OrEarlier) {
            openForReflectionTo(field.getDeclaringClass(), DebuggerTester.class);
        }
        field.setAccessible(flag);
    }

    private static final boolean Java8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    /**
     * Opens {@code declaringClass}'s package to allow a method declared in {@code accessor} to call
     * {@link AccessibleObject#setAccessible(boolean)} on an {@link AccessibleObject} representing a
     * field or method declared by {@code declaringClass}.
     */
    private static void openForReflectionTo(Class<?> declaringClass, Class<?> accessor) {
        try {
            Method getModule = Class.class.getMethod("getModule");
            Class<?> moduleClass = getModule.getReturnType();
            Class<?> modulesClass = Class.forName("jdk.internal.module.Modules");
            Method addOpens = maybeGetAddOpensMethod(moduleClass, modulesClass);
            if (addOpens != null) {
                Object moduleToOpen = getModule.invoke(declaringClass);
                Object accessorModule = getModule.invoke(accessor);
                if (moduleToOpen != accessorModule) {
                    addOpens.invoke(null, moduleToOpen, declaringClass.getPackage().getName(), accessorModule);
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Method maybeGetAddOpensMethod(Class<?> moduleClass, Class<?> modulesClass) {
        try {
            return modulesClass.getDeclaredMethod("addOpens", moduleClass, String.class, moduleClass);
        } catch (NoSuchMethodException e) {
            // This method was introduced by JDK-8169069
            return null;
        }
    }

    private void putEvent(Object event) {
        trace("Put event " + this + ": " + Thread.currentThread());
        if (event instanceof SuspendedEvent) {
            try {
                handler.onSuspend((SuspendedEvent) event);
            } catch (Throwable e) {
                newEvent.add(e);
                return;
            }
        }
        newEvent.add(event);
    }

    private Object takeEvent() throws InterruptedException {
        trace("Take event " + this + ": " + Thread.currentThread());
        try {
            return newEvent.take();
        } finally {
            trace("Taken event " + this + ": " + Thread.currentThread());
        }
    }

    private void onSuspend(SuspendedEvent event) {
        if (closed) {
            return;
        }
        try {
            putEvent(event);
        } finally {
            waitForExecuting();
        }
    }

    private void waitForExecuting() {
        trace("Wait for executing " + this + ": " + Thread.currentThread());
        if (closed) {
            return;
        }
        try {
            executing.acquire();
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        trace("Wait for executing released " + this + ": " + Thread.currentThread());
    }

    private void notifyNextAction() {
        trace("Notify next action " + this + ": " + Thread.currentThread());
        executing.release();
    }

    private static final class ExecutingSource {

        private final Source source;
        private Throwable error;
        private String returnValue;

        ExecutingSource(Source source) {
            this.source = source;
        }

    }

    class ExecutingLoop implements Runnable {

        private final AtomicReference<Engine> engineRef;
        private final AtomicReference<Throwable> error;

        ExecutingLoop(AtomicReference<Engine> engineRef, AtomicReference<Throwable> error) {
            this.engineRef = engineRef;
            this.error = error;
        }

        @Override
        public void run() {
            Context context = null;
            try {
                try {
                    context = Context.newBuilder().out(out).err(err).build();
                    engineRef.set(context.getEngine());
                } catch (Throwable t) {
                    error.set(t);
                    return;
                } finally {
                    initialized.release();
                }
                while (true) {
                    waitForExecuting();
                    if (closed) {
                        return;
                    }
                    try {
                        trace("Start executing " + this);
                        source.returnValue = context.eval(source.source).toString();
                        trace("Done executing " + this);
                    } catch (Throwable e) {
                        source.error = e;
                    } finally {
                        putEvent(source);
                    }
                }
            } finally {
                if (context != null) {
                    context.close();
                }
            }
        }
    }
}

class DebuggerTesterSnippets {

    // BEGIN: DebuggerTesterSnippets.testDebugging
    public void testDebugging() {
        try (DebuggerTester tester = new DebuggerTester()) {
            // use your guest language source here
            Source source = null;
            try (DebuggerSession session = tester.startSession()) {
                session.suspendNextExecution();
                tester.startEval(source);

                tester.expectSuspended(new SuspendedCallback() {
                    @Override
                    public void onSuspend(SuspendedEvent event) {
                        // assert suspended event is proper here
                        event.prepareStepInto(1);

                    }
                });
                tester.expectSuspended(new SuspendedCallback() {
                    @Override
                    public void onSuspend(SuspendedEvent event) {
                        // assert another suspended event is proper here
                        event.prepareContinue();
                    }
                });

                // expect no more suspended events
                tester.expectDone();
            }
        }
    }
    // END: DebuggerTesterSnippets.testDebugging
}
