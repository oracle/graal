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
package com.oracle.truffle.api.instrumentation.test.examples;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;

/**
 * This is an example how debugging can be implemented using the instrumentation framework. This
 * class itself shall be hidden in an implementation package. The actual API that
 * {@link DebuggerExampleTest clients} can use to talk to the debugger is exposed in a separate
 * {@link DebuggerController} interface.
 */
// BEGIN: DebuggerExample
@Registration(id = DebuggerExample.ID, services = DebuggerController.class)
public final class DebuggerExample extends TruffleInstrument {
    private Controller controller;

    @Override
    protected void onCreate(Env env) {
        assert this.controller == null;
        this.controller = new Controller(env.getInstrumenter());
        env.registerService(controller);
    }

    private static final class Controller extends DebuggerController {
        private final Instrumenter instrumenter;
        private EventBinding<?> stepping;
        private Callback currentStatementCallback;

        Controller(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
        }

        // FINISH: DebuggerExample

        @Override
        public void installBreakpoint(int line, final Callback callback) {
            instrumenter.attachExecutionEventListener(SourceSectionFilter.newBuilder().lineIs(line).tagIs(InstrumentationTestLanguage.STATEMENT).build(), new Breakpoint(callback));
        }

        @Override
        public void stepInto(Callback next) {
            runToNextStatement(new StepIntoCallback(next));
        }

        @Override
        public void stepOver(Callback next) {
            runToNextStatement(new StepOverCallback(next));
        }

        @Override
        public void stepOut(Callback next) {
            runToNextStatement(new StepOutCallback(next));
        }

        @TruffleBoundary
        private void ontStatementStep(EventContext context) {
            Callback callback = currentStatementCallback;
            if (callback != null) {
                currentStatementCallback = null;
                callback.halted(this, context);
            }
        }

        private void runToNextStatement(Callback callback) {
            setStepping(true);
            if (currentStatementCallback != null) {
                throw new IllegalStateException("Debug command already scheduled.");
            }
            this.currentStatementCallback = callback;
        }

        private void setStepping(boolean stepping) {
            EventBinding<?> step = this.stepping;
            if (stepping) {
                if (step == null) {
                    this.stepping = instrumenter.attachExecutionEventListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new Stepping());
                }
            } else {
                if (step != null && !step.isDisposed()) {
                    step.dispose();
                    this.stepping = null;
                }
            }
        }

        @TruffleBoundary
        private static int currentStackDepth() {
            final int[] count = {0};
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
                @Override
                public Void visitFrame(FrameInstance frameInstance) {
                    count[0] = count[0] + 1;
                    return null;
                }
            });
            return count[0] == 0 ? 0 : count[0] + 1;
        }

        private final class Breakpoint implements ExecutionEventListener {
            private final Callback delegate;

            private Breakpoint(Callback callback) {
                this.delegate = callback;
            }

            public void onEnter(EventContext context, VirtualFrame frame) {
                CompilerDirectives.transferToInterpreter();
                onBreakpoint(delegate, context);
            }

            @TruffleBoundary
            protected void onBreakpoint(final Callback callback, EventContext context) {
                callback.halted(Controller.this, context);
            }

            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            }

            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }
        }

        private class Stepping implements ExecutionEventListener {

            public void onEnter(EventContext context, VirtualFrame frame) {
                ontStatementStep(context);
            }

            public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
            }

            public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
            }
        }

        private abstract class StepCallBack implements Callback {

            private final Callback delegate;

            StepCallBack(Callback delegate) {
                this.delegate = delegate;
            }

            public void halted(DebuggerController debugger, EventContext haltedAt) {
                if (shouldHalt()) {
                    currentStatementCallback = null;
                    delegate.halted(debugger, haltedAt);
                } else {
                    currentStatementCallback = this;
                }
            }

            protected abstract boolean shouldHalt();

        }

        private class StepOverCallback extends StepCallBack {

            protected final int stackDepth;

            StepOverCallback(Callback delegate) {
                super(delegate);
                this.stackDepth = currentStackDepth();
            }

            @Override
            protected boolean shouldHalt() {
                return currentStackDepth() <= stackDepth;
            }
        }

        private class StepOutCallback extends StepCallBack {

            protected final int stackDepth;

            StepOutCallback(Callback delegate) {
                super(delegate);
                this.stackDepth = currentStackDepth();
            }

            @Override
            protected boolean shouldHalt() {
                return currentStackDepth() < stackDepth;
            }

        }

        private class StepIntoCallback extends StepCallBack {

            StepIntoCallback(Callback delegate) {
                super(delegate);
            }

            @Override
            protected boolean shouldHalt() {
                return true;
            }

        }
    }

    @Override
    protected void onDispose(Env env) {
    }

    public static final String ID = "example-debugger";
}
