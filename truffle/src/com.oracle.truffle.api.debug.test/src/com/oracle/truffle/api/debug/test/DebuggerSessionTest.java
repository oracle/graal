/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.test.ReflectionUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;

public class DebuggerSessionTest extends AbstractDebugTest {

    private static void suspend(DebuggerSession session, Thread thread) {
        invoke(session, "suspend", new Class<?>[]{Thread.class}, thread);
    }

    private static void suspendAll(DebuggerSession session) {
        invoke(session, "suspendAll", new Class<?>[]{});
    }

    private static void resume(DebuggerSession session, Thread thread) {
        invoke(session, "resume", new Class<?>[]{Thread.class}, thread);
    }

    private static void invoke(DebuggerSession session, String name, Class<?>[] classes, Object... arguments) throws AssertionError {
        try {
            Method method = session.getClass().getDeclaredMethod(name, classes);
            ReflectionUtils.setAccessible(method, true);
            method.invoke(session, arguments);
        } catch (Throwable e) {
            if (e instanceof InvocationTargetException && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new AssertionError(e);
        }
    }

    @Test
    public void testSuspendNextExecution1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testSuspendNextExecution2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {

            // calling it multiple times should not make a difference
            session.suspendNextExecution();
            session.suspendNextExecution();
            session.suspendNextExecution();

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });
            expectDone();
        }
    }

    @Test
    public void testSuspendNextExecution3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {

            // do suspend next for a few times
            for (int i = 0; i < 100; i++) {
                session.suspendNextExecution();

                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareContinue();
                });
                expectDone();
            }
        }
    }

    @Test
    public void testSuspendNextExecution4() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();

            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
                // use suspend next in an event
                event.getSession().suspendNextExecution();
            });

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testSuspendThread1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            // do suspend next for a few times
            for (int i = 0; i < 100; i++) {
                suspend(session, getEvalThread());
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareContinue();
                });
                expectDone();
            }
        }
    }

    @Test
    public void testSuspendThread2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            suspend(session, getEvalThread());
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });

            // prepareContinue should be ignored here as suspensions counts more
            suspend(session, getEvalThread());

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testSuspendThread3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            suspend(session, getEvalThread());
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareKill();
            });

            // For prepareKill additional suspensions should be ignored
            suspend(session, getEvalThread());

            expectKilled();
        }
    }

    @Test
    public void testSuspendAll1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                suspendAll(session);
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareContinue();
                });
                expectDone();
            }
        }
    }

    @Test
    public void testSuspendAll2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            suspendAll(session);
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareContinue();
            });

            // prepareContinue should be ignored here as suspenions counts higher
            suspendAll(session);

            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 3, true, "STATEMENT").prepareContinue();
            });

            expectDone();
        }
    }

    @Test
    public void testSuspendAll3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            suspendAll(session);
            startEval(testSource);
            expectSuspended((SuspendedEvent event) -> {
                checkState(event, 2, true, "STATEMENT").prepareKill();
            });

            // For prepareKill additional suspensions should be ignored
            suspendAll(session);

            expectKilled();
        }
    }

    @Test
    public void testResumeThread1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
                });
                // resume events are ignored by stepping
                resume(session, getEvalThread());
                expectDone();
            }
        }
    }

    @Test
    public void testResumeThread2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                resume(session, getEvalThread());
                startEval(testSource);

                // even if the thread is resumed suspend next execution will trigger.
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
                });
                resume(session, getEvalThread());
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                session.resumeAll(); // resume all invalidates suspendNextExecution
                startEval(testSource);
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll2() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                suspendAll(session);
                session.resumeAll();
                startEval(testSource);
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll3() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                suspend(session, getEvalThread());
                session.resumeAll();
                startEval(testSource);
                expectDone();
            }
        }
    }

    @Test
    public void testResumeAll4() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        try (DebuggerSession session = startSession()) {
            for (int i = 0; i < 10; i++) {
                session.suspendNextExecution();
                startEval(testSource);
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
                });
                session.resumeAll(); // test that resume does not affect current stepping behavior
                expectSuspended((SuspendedEvent event) -> {
                    checkState(event, 3, true, "STATEMENT").prepareStepOver(1);
                });
                expectDone();
            }
        }
    }

    @Test
    public void testClosing1() {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        DebuggerSession session = startSession();

        session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(3).build());
        session.suspendNextExecution();
        startEval(testSource);
        expectSuspended((SuspendedEvent event) -> {
            checkState(event, 2, true, "STATEMENT").prepareStepOver(1);
        });

        // closing the session should disable breakpoints and current stepping
        session.close();

        expectDone();
    }

    @Test
    public void testClosing2() throws Exception {
        Source testSource = testSource("ROOT(\n" +
                        "STATEMENT,\n" +
                        "STATEMENT)");

        Context context = Context.create();
        final AtomicBoolean suspend = new AtomicBoolean();
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
        DebuggerSession session = debugger.startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                suspend.set(true);
            }
        });
        context.eval(testSource);

        context.close();

        // if the engine disposes the session should still work
        session.suspendNextExecution();
        suspend(session, Thread.currentThread());
        suspendAll(session);
        session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build());
        resume(session, Thread.currentThread());
        session.resumeAll();
        session.getDebugger();
        session.getBreakpoints();

        // after closing the session none of these methods should work
        session.close();

        try {
            session.suspendNextExecution();
            Assert.fail();
        } catch (IllegalStateException e) {
        }

        try {
            suspend(session, Thread.currentThread());
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            suspendAll(session);
            Assert.fail();
        } catch (IllegalStateException e) {
        }

        try {
            session.install(Breakpoint.newBuilder(getSourceImpl(testSource)).lineIs(2).build());
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            resume(session, Thread.currentThread());
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            session.resumeAll();
            Assert.fail();
        } catch (IllegalStateException e) {
        }
        try {
            session.getBreakpoints();
            Assert.fail();
        } catch (IllegalStateException e) {
        }

        // still works after closing
        session.getDebugger();
    }

}
