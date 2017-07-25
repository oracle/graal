/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import java.util.function.Function;

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
    private final PolyglotEngine engine;

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
     * Constructs a new debugger tester instance. Boots up a new {@link PolyglotEngine engine} on
     * Thread in the background. The tester instance needs to be {@link #close() closed} after use.
     * Throws an AssertionError if the engine initialization fails.
     *
     * @since 0.16
     */
    public DebuggerTester() {
        this(null);
    }

    /**
     * Constructs a new debugger tester instance. Boots up a new {@link PolyglotEngine engine} on
     * Thread in the background. The tester instance needs to be {@link #close() closed} after use.
     * Throws an AssertionError if the engine initialization fails.
     *
     * @param engineBuilderDecorator a decorator function that allows to customize the engine
     *            builder
     * @since 0.26
     */
    public DebuggerTester(Function<PolyglotEngine.Builder, PolyglotEngine.Builder> engineBuilderDecorator) {
        this.newEvent = new ArrayBlockingQueue<>(1);
        this.executing = new Semaphore(0);
        this.initialized = new Semaphore(0);
        final AtomicReference<PolyglotEngine> engineRef = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        this.executingLoop = new ExecutingLoop(engineRef, engineBuilderDecorator, error);
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
     * Returns the error output of the underlying {@link PolyglotEngine engine}.
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
     * Returns the standard output of the underlying {@link PolyglotEngine engine}.
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
        return Debugger.find(engine);
    }

    /**
     * Starts a new {@link Debugger#startSession(SuspendedCallback) debugger session} in the
     * {@link PolyglotEngine engine}. The debugger session allows to suspend the execution and to
     * install breakpoints. If multiple sessions are created for one {@link #startEval(Source)
     * evaluation} then all suspended events are delegated to this debugger tester instance.
     *
     * @return a new debugger session
     * @since 0.16
     */
    public DebuggerSession startSession() {
        return Debugger.find(engine).startSession(new SuspendedCallback() {
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
        if (error.getClass().getSimpleName().equals("KillException")) {
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
        trace("kill session " + this);
        // trying to interrupt if execution is in IO.
        notifyNextAction();
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

        private final AtomicReference<PolyglotEngine> engineRef;
        private final Function<PolyglotEngine.Builder, PolyglotEngine.Builder> engineBuilderDecorator;
        private final AtomicReference<Throwable> error;

        ExecutingLoop(AtomicReference<PolyglotEngine> engineRef, Function<PolyglotEngine.Builder, PolyglotEngine.Builder> engineBuilderDecorator, AtomicReference<Throwable> error) {
            this.engineRef = engineRef;
            this.engineBuilderDecorator = engineBuilderDecorator;
            this.error = error;
        }

        @Override
        public void run() {
            PolyglotEngine localEngine;
            try {
                PolyglotEngine.Builder builder = PolyglotEngine.newBuilder().setOut(out).setErr(err);
                if (engineBuilderDecorator != null) {
                    builder = engineBuilderDecorator.apply(builder);
                }
                localEngine = builder.build();
                engineRef.set(localEngine);
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
                    source.returnValue = localEngine.eval(source.source).as(String.class);
                    trace("Done executing " + this);
                } catch (Throwable e) {
                    source.error = e;
                } finally {
                    putEvent(source);
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
