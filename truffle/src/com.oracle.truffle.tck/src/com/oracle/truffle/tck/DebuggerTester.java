/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.source.SourceSection;

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
    private volatile ExecutingSource executingSource;

    private static void trace(String message) {
        if (TRACE) {
            PrintStream out = System.out;
            out.println("DebuggerTester: " + message);
        }
    }

    private static void err(String message) {
        PrintStream out = System.err;
        out.println("DebuggerTester: " + message);
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
        this(null);
    }

    /**
     * Constructs a new debugger tester instance with a pre-set context builder.
     *
     * @param contextBuilder a pre-set context builder. Only out and err streams are set on this
     *            builder prior the {@link Context} instance creation.
     *
     * @see #DebuggerTester()
     * @since 0.31
     */
    public DebuggerTester(Context.Builder contextBuilder) {
        this.newEvent = new ArrayBlockingQueue<>(1);
        this.executing = new Semaphore(0);
        this.initialized = new Semaphore(0);
        final AtomicReference<Engine> engineRef = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        this.executingLoop = new ExecutingLoop(contextBuilder, engineRef, error);
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
     * Starts a new {@link Debugger#startSession(SuspendedCallback, SourceElement...) debugger
     * session} in the context's {@link Engine engine}. The debugger session allows to suspend the
     * execution on the provided source elements and to install breakpoints. If multiple sessions
     * are created for one {@link #startEval(Source) evaluation} then all suspended events are
     * delegated to this debugger tester instance.
     *
     * @param sourceElements a list of source elements
     * @return a new debugger session
     * @since 0.33
     */
    public DebuggerSession startSession(SourceElement... sourceElements) {
        return getDebugger().startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                DebuggerTester.this.onSuspend(event);
            }
        }, sourceElements);
    }

    /**
     * Starts a new {@link Context#eval(Source) evaluation} on the background thread. Only one
     * evaluation can be active at a time. Please ensure that {@link #expectDone()} completed
     * successfully before starting a new evaluation. When no source is available please refer to
     * {@link DebuggerTester#startExecute(Function)}. Throws an {@link IllegalStateException} if
     * another evaluation is still executing or the tester is already closed.
     *
     * @since 0.27
     */
    public void startEval(Source s) {
        startExecute(new Function<Context, Value>() {
            public Value apply(Context c) {
                return c.eval(s);
            }
        });
    }

    /**
     * Starts a new script evaluation on the background thread. Only one evaluation can be active at
     * a time. Please ensure that {@link #expectDone()} completed successfully before starting a new
     * evaluation. If a Source is available please refer to {@link DebuggerTester#startEval(Source)}
     * . Throws an {@link IllegalStateException} if another evaluation is still executing or the
     * tester is already closed.
     *
     * @since 20.0
     */
    public void startExecute(Function<Context, Value> script) {
        if (this.executingSource != null) {
            throw new IllegalStateException("Already executing other source ");
        }
        this.executingSource = new ExecutingSource(script);
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
            } else if (event instanceof Throwable) {
                throw new AssertionError("Got exception", (Throwable) event);
            } else {
                throw new AssertionError("Got unknown: " + event);
            }
        } finally {
            executingSource = null;
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
            Assert.assertTrue(((PolyglotException) error).isCancelled());
            Assert.assertTrue(error.getMessage(), error.getMessage().contains("Execution cancelled by a debugging session."));
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
     * Close the engine. It's not possible to evaluate code after engine is closed, use it when the
     * engine needs to be closed before the debugger session.
     *
     * @since 0.30
     */
    public void closeEngine() {
        engine.close();
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
        try {
            evalThread.join();
        } catch (InterruptedException iex) {
            throw new AssertionError("Interrupted while joining eval thread.", iex);
        }
        engine.close();
    }

    /**
     * Utility method that checks proper resolution of line breakpoints. Breakpoints are submitted
     * to every line of the source and their resolution location is checked.
     * <p>
     * The source need to contain resolution marks in the form of
     * "<code>R&lt;line number&gt;_</code>" where &lt;line number&gt; is line number of the original
     * breakpoint position and <code>R</code> is the resolved mark name. These marks need to be
     * placed at the proper breakpoint resolution line/column position, for a breakpoint submitted
     * to every line. When several submitted breakpoints resolve to the same position, an interval
     * can be specified in the form of
     * "<code>R&lt;line start number&gt;-&lt;line end number&gt;_</code>", where both start and end
     * line numbers are inclusive. The marks are stripped off before execution.
     * <p>
     * The guest language code with marks may look like:
     *
     * <pre>
     * // test
     * function test(n) {
     *   if (R1-3_n &lt;= 1) {
     *     return R4_2 * n;
     *   }
     *   return R5-7_n - 1;
     * }
     * </pre>
     *
     * @param sourceWithMarks a source text, which contains the resolution marks
     * @param resolvedMarkName the mark name. It is used in a regular expression, therefore the mark
     *            must not have a special meaning in a regular expression.
     * @param language the source language
     * @since 0.33
     */
    public void assertLineBreakpointsResolution(String sourceWithMarks, String resolvedMarkName, String language) {
        assertLineBreakpointsResolution(sourceWithMarks, null, resolvedMarkName, language);
    }

    /**
     * @param positionPredicate <code>null</code> to test line breakpoints on all lines, or a
     *            predicate that limits the testable lines.
     * @see #assertLineBreakpointsResolution(java.lang.String, java.lang.String, java.lang.String)
     * @since 19.3.0
     */
    public void assertLineBreakpointsResolution(String sourceWithMarks, PositionPredicate positionPredicate, String resolvedMarkName, String language) {
        Pattern br = Pattern.compile("(" + resolvedMarkName + "\\d+_|" + resolvedMarkName + "\\d+-\\d+_)");
        Map<Integer, Integer> bps = new HashMap<>();
        String sourceString = sourceWithMarks;
        Matcher bm = br.matcher(sourceString);
        while (bm.find()) {
            String bg = bm.group();
            int index = bm.start();
            int bn1;
            int bn2;
            String bpNums = bg.substring(1, bg.length() - 1);
            int rangeIndex = bpNums.indexOf('-');
            if (rangeIndex > 0) {
                bn1 = Integer.parseInt(bpNums.substring(0, rangeIndex));
                bn2 = Integer.parseInt(bpNums.substring(rangeIndex + 1));
            } else {
                bn1 = bn2 = Integer.parseInt(bpNums);
            }
            for (int bn = bn1; bn <= bn2; bn++) {
                Integer bp = bps.get(bn);
                if (bp == null) {
                    bps.put(bn, index + 1);
                } else {
                    Assert.fail(bg + " specified more than once.");
                }
            }
            sourceString = bm.replaceFirst("");
            bm.reset(sourceString);
        }
        if (TRACE) {
            trace("sourceString = '" + sourceString + "'");
        }
        final Source source = Source.newBuilder(language, sourceString, "testMisplacedLineBreakpoint." + language).buildLiteral();
        com.oracle.truffle.api.source.Source tsource = DebuggerTester.getSourceImpl(source);
        for (int l = 1; l < source.getLineCount(); l++) {
            if ((positionPredicate == null || positionPredicate.testLine(l)) && !bps.containsKey(l)) {
                Assert.fail("Line " + l + " is missing.");
            }
        }
        for (Map.Entry<Integer, Integer> bentry : bps.entrySet()) {
            int line = bentry.getKey();
            int indexResolved = bentry.getValue();
            int lineResolved = source.getLineNumber(indexResolved - 1);
            if (TRACE) {
                trace("TESTING breakpoint '" + line + "' => " + lineResolved + ":");
            }
            try (DebuggerSession session = startSession()) {

                startEval(source);
                int[] resolvedIndexPtr = new int[]{0};
                Breakpoint breakpoint = session.install(Breakpoint.newBuilder(tsource).lineIs(line).oneShot().resolveListener(new Breakpoint.ResolveListener() {
                    @Override
                    public void breakpointResolved(Breakpoint brkp, SourceSection section) {
                        resolvedIndexPtr[0] = section.getCharIndex() + 1;
                        if (TRACE) {
                            trace("BREAKPOINT resolved to " + section.getStartLine() + ":" + section.getStartColumn());
                        }
                    }
                }).build());

                expectSuspended((SuspendedEvent event) -> {
                    Assert.assertEquals("Expected " + line + " => " + lineResolved,
                                    lineResolved, event.getSourceSection().getStartLine());
                    Assert.assertSame(breakpoint, event.getBreakpoints().iterator().next());
                    event.prepareContinue();
                });
                expectDone();
                Assert.assertEquals("Expected resolved " + line + " => " + indexResolved,
                                indexResolved, resolvedIndexPtr[0]);
            }
        }
    }

    /**
     * Utility method that checks proper resolution of column breakpoints. Breakpoints are submitted
     * to marked positions and their resolution location is checked.
     * <p>
     * The source need to contain both breakpoint submission marks in the form of "B&lt;number&gt;_"
     * and breakpoint resolution marks in the form of "R&lt;number&gt;_" where &lt;number&gt; is an
     * identification of the breakpoint. These marks need to be placed at the proper breakpoint
     * submission/resolution line/column position. When several submitted breakpoints resolve to the
     * same position, an interval can be specified in the form of "R&lt;start number&gt;-&lt;end
     * number&gt;_", where both start and end line numbers are inclusive. The marks are stripped off
     * before execution.
     * <p>
     * The guest language code with marks may look like:
     *
     * <pre>
     * // B1_test
     * function B2_test(n) {B3_
     *   if (R1-4_n &lt;= B4_1) {B5_
     *     return R5_2 * n;
     *   }
     *   return R6_n - 1;
     * B6_}
     * </pre>
     *
     * @param sourceWithMarks a source text, which contains the resolution marks
     * @param breakpointMarkName the breakpoint submission mark name. It is used in a regular
     *            expression, therefore the mark must not have a special meaning in a regular
     *            expression.
     * @param resolvedMarkName the resolved mark name. It is used in a regular expression, therefore
     *            the mark must not have a special meaning in a regular expression.
     * @param language the source language
     * @since 0.33
     */
    public void assertColumnBreakpointsResolution(String sourceWithMarks, String breakpointMarkName, String resolvedMarkName, String language) {
        Pattern br = Pattern.compile("([" + breakpointMarkName + resolvedMarkName + "]\\d+_|" + resolvedMarkName + "\\d+-\\d+_)");
        Map<Integer, int[]> bps = new HashMap<>();
        String sourceString = sourceWithMarks;
        Matcher bm = br.matcher(sourceString);
        while (bm.find()) {
            String bg = bm.group();
            int index = bm.start();
            int state = (bg.charAt(0) == 'B') ? 0 : 1;
            String bpNums = bg.substring(1, bg.length() - 1);
            int bn1;
            int bn2;
            int rangeIndex = bpNums.indexOf('-');
            if (rangeIndex > 0) {
                bn1 = Integer.parseInt(bpNums.substring(0, rangeIndex));
                bn2 = Integer.parseInt(bpNums.substring(rangeIndex + 1));
            } else {
                bn1 = bn2 = Integer.parseInt(bpNums);
            }
            for (int bn = bn1; bn <= bn2; bn++) {
                int[] bp = bps.get(bn);
                if (bp == null) {
                    bp = new int[2];
                    bps.put(bn, bp);
                }
                if (bp[state] > 0) {
                    Assert.fail(bg + " specified more than once.");
                }
                bp[state] = index + 1;
            }
            sourceString = bm.replaceFirst("");
            bm.reset(sourceString);
        }
        if (TRACE) {
            trace("sourceString = '" + sourceString + "'");
        }
        final Source source = Source.newBuilder(language, sourceString, "testMisplacedColumnBreakpoint." + language).buildLiteral();

        for (Map.Entry<Integer, int[]> bentry : bps.entrySet()) {
            int bpId = bentry.getKey();
            int[] bp = bentry.getValue();
            Assert.assertTrue(Integer.toString(bpId), bp[0] > 0);
            Assert.assertTrue(Integer.toString(bpId), bp[1] > 0);
            int line = source.getLineNumber(bp[0] - 1);
            int column = source.getColumnNumber(bp[0] - 1);
            if (TRACE) {
                trace("TESTING BP_" + bpId + ": " + bp[0] + " (" + line + ":" + column + ") => " + bp[1] + ":");
            }
            try (DebuggerSession session = startSession()) {

                startEval(source);
                int[] resolvedIndexPtr = new int[]{0};
                Breakpoint breakpoint = session.install(
                                Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(line).columnIs(column).oneShot().resolveListener(new Breakpoint.ResolveListener() {
                                    @Override
                                    public void breakpointResolved(Breakpoint brkp, SourceSection section) {
                                        resolvedIndexPtr[0] = section.getCharIndex() + 1;
                                        if (TRACE) {
                                            trace("  resolved: " + (resolvedIndexPtr[0]));
                                        }
                                    }
                                }).build());

                expectSuspended((SuspendedEvent event) -> {
                    Assert.assertEquals("B" + bpId + ": Expected " + bp[0] + " => " + bp[1] + ", resolved at " + resolvedIndexPtr[0],
                                    bp[1], event.getSourceSection().getCharIndex() + 1);
                    Assert.assertSame(breakpoint, event.getBreakpoints().iterator().next());
                    event.prepareContinue();
                });
                expectDone();
                Assert.assertEquals("B" + bpId + ": Expected resolved " + bp[0] + " => " + bp[1],
                                bp[1], resolvedIndexPtr[0]);
            }
        }
    }

    /**
     * Utility method that tests if a breakpoint submitted to any location in the source code
     * suspends the execution. A two-pass test is performed. In the first pass, line breakpoints are
     * submitted to every line. In the second pass, breakpoints are submitted to every line and
     * column combination, even outside the source scope. It is expected that the breakpoints
     * resolve to a nearest suspendable location and it is checked that all breakpoints are hit.
     *
     * @param source a source to evaluate with breakpoints submitted everywhere
     * @since 0.33
     */
    public void assertBreakpointsBreakEverywhere(Source source) {
        assertBreakpointsBreakEverywhere(source, null);
    }

    /**
     * Utility method that tests if a breakpoint submitted to any location permitted by the
     * {@link PositionPredicate} in the source code suspends the execution. A two-pass test is
     * performed. In the first pass, line breakpoints are submitted to every testable line. In the
     * second pass, breakpoints are submitted to every testable line and column combination, even
     * outside the source scope, if permitted by the {@link PositionPredicate}. It is expected that
     * the breakpoints resolve to a nearest suspendable location and it is checked that all
     * submitted breakpoints are hit.
     *
     * @param source a source to evaluate with breakpoints submitted everywhere
     * @param positionPredicate <code>null</code> to submit breakpoints everywhere, or a predicate
     *            that limits the testable positions.
     * @since 19.3.0
     */
    public void assertBreakpointsBreakEverywhere(Source source, PositionPredicate positionPredicate) {
        int numLines = source.getLineCount();
        int numColumns = 0;
        for (int i = 1; i <= numLines; i++) {
            int ll = source.getLineLength(i);
            if (ll > numColumns) {
                numColumns = ll;
            }
        }
        com.oracle.truffle.api.source.Source tsource = DebuggerTester.getSourceImpl(source);
        final List<Breakpoint> breakpoints = new ArrayList<>();
        final Set<Breakpoint> breakpointsResolved = new HashSet<>();
        final List<Breakpoint> breakpointsHit = new ArrayList<>();
        Breakpoint.ResolveListener resolveListener = new Breakpoint.ResolveListener() {
            @Override
            public void breakpointResolved(Breakpoint breakpoint, SourceSection section) {
                Assert.assertFalse(breakpointsResolved.contains(breakpoint));
                breakpointsResolved.add(breakpoint);
            }
        };
        // Test all line breakpoints
        for (int l = 1; l < (numLines + 5); l++) {
            if (positionPredicate == null || positionPredicate.testLine(l)) {
                Breakpoint breakpoint = Breakpoint.newBuilder(tsource).lineIs(l).oneShot().resolveListener(resolveListener).build();
                breakpoints.add(breakpoint);
            }
        }
        assertBreakpoints(source, breakpoints, breakpointsResolved, breakpointsHit);

        breakpoints.clear();
        breakpointsResolved.clear();
        breakpointsHit.clear();

        // Test all line/column breakpoints
        for (int l = 1; l < (numLines + 5); l++) {
            for (int c = 1; c < (numColumns + 5); c++) {
                if (positionPredicate == null || positionPredicate.testLineColumn(l, c)) {
                    Breakpoint breakpoint = Breakpoint.newBuilder(tsource).lineIs(l).columnIs(c).oneShot().resolveListener(resolveListener).build();
                    breakpoints.add(breakpoint);
                }
            }
        }
        assertBreakpoints(source, breakpoints, breakpointsResolved, breakpointsHit);
    }

    private void assertBreakpoints(Source source, List<Breakpoint> breakpoints, Set<Breakpoint> breakpointsResolved, List<Breakpoint> breakpointsHit) {
        try (DebuggerSession session = startSession(new SourceElement[0])) {
            for (Breakpoint breakpoint : breakpoints) {
                session.install(breakpoint);
            }
            startEval(source);
            while (breakpointsHit.size() != breakpoints.size()) {
                try {
                    expectSuspended((SuspendedEvent event) -> {
                        breakpointsHit.addAll(event.getBreakpoints());
                        event.prepareContinue();
                    });
                } catch (Throwable t) {
                    Set<Breakpoint> notHit = new HashSet<>(breakpoints);
                    notHit.removeAll(breakpointsHit);
                    for (Breakpoint b : notHit) {
                        err("Not hit " + b + ": " + b.getLocationDescription());
                    }
                    err("---");
                    for (Breakpoint b : breakpointsHit) {
                        err("Hit     " + b + ": " + b.getLocationDescription());
                    }
                    throw t;
                }
            }
            expectDone();
        }
        Assert.assertEquals(breakpoints.size(), breakpointsResolved.size());
        Assert.assertEquals(breakpoints.size(), breakpointsHit.size());
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
                if (handler == null) {
                    throw new AssertionError("Expected done but got event " + event);
                }
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

        private final Function<Context, Value> function;
        private Throwable error;
        private String returnValue;

        ExecutingSource(Function<Context, Value> function) {
            this.function = function;
        }

    }

    /**
     * Predicate of testable positions.
     *
     * @since 19.3.0
     */
    public interface PositionPredicate {

        /**
         * Whether to test at the line.
         *
         * @since 19.3.0
         */
        boolean testLine(int line);

        /**
         * Whether to test at the line and column position.
         *
         * @since 19.3.0
         */
        boolean testLineColumn(int line, int column);
    }

    class ExecutingLoop implements Runnable {

        private final Context.Builder contextBuilder;
        private final AtomicReference<Engine> engineRef;
        private final AtomicReference<Throwable> error;

        ExecutingLoop(Context.Builder contextBuilder, AtomicReference<Engine> engineRef, AtomicReference<Throwable> error) {
            this.contextBuilder = (contextBuilder != null) ? contextBuilder : Context.newBuilder();
            this.engineRef = engineRef;
            this.error = error;
        }

        @Override
        public void run() {
            Context context = null;
            try {
                try {
                    context = contextBuilder.out(out).err(err).build();
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
                    ExecutingSource s = executingSource;
                    try {
                        trace("Start executing " + this);
                        s.returnValue = s.function.apply(context).toString();
                        trace("Done executing " + this);
                    } catch (Throwable e) {
                        s.error = e;
                    } finally {
                        putEvent(s);
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
