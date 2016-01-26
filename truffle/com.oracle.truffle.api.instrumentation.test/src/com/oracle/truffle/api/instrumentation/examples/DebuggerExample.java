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
package com.oracle.truffle.api.instrumentation.examples;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.EventListener;
import com.oracle.truffle.api.instrumentation.InstrumentationTestLanguage;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

/**
 * This is an example how debugging can be implemented using the instrumentation framework. Debugger
 * front-ends can be used by installing a {@link DebuggerFrontEnd} implementation with
 * {@link #installFrontEnd(Class)}.
 */
@Registration(id = DebuggerExample.ID)
public final class DebuggerExample extends TruffleInstrument {

    public static final String ID = "example-debugger";

    private Instrumenter instrumenter;
    private EventBinding<?> stepping;

    private final List<DebuggerFrontEnd> frontEnds = new ArrayList<>();
    private Callback currentStatementCallback;

    @Override
    protected void onCreate(Env env, Instrumenter originalInstrumenter) {
        assert this.instrumenter == null;
        this.instrumenter = originalInstrumenter;
        for (Class<? extends DebuggerFrontEnd> frontEnd : installedFrontEnds) {
            try {
                DebuggerFrontEnd frontEndInstance = frontEnd.newInstance();
                frontEndInstance.onAttach(this);
                frontEnds.add(frontEndInstance);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void onDispose(Env env) {
        for (DebuggerFrontEnd debugger : frontEnds) {
            debugger.onDispose();
        }
    }

    public boolean isStepping() {
        return stepping != null;
    }

    public void installBreakpoint(int line, final Callback callback) {
        instrumenter.attachListener(SourceSectionFilter.newBuilder().lineIs(line).tagIs(InstrumentationTestLanguage.STATEMENT).build(), new Breakpoint(callback));
    }

    public void stepInto(Callback next) {
        runToNextStatement(new StepIntoCallback(next));
    }

    public void stepOver(Callback next) {
        runToNextStatement(new StepOverCallback(next));
    }

    public void stepOut(Callback next) {
        runToNextStatement(new StepOutCallback(next));
    }

    public void run() {
        currentStatementCallback = null;
        setStepping(false);
    }

    @TruffleBoundary
    private void ontStatementStep(EventContext context) {
        Callback callback = currentStatementCallback;
        if (callback != null) {
            currentStatementCallback = null;
            callback.halted(DebuggerExample.this, context);
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
                this.stepping = instrumenter.attachListener(SourceSectionFilter.newBuilder().tagIs(InstrumentationTestLanguage.STATEMENT).build(), new Stepping());
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

    private final class Breakpoint implements EventListener {
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
            callback.halted(DebuggerExample.this, context);
        }

        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
        }

        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
        }
    }

    private class Stepping implements EventListener {

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

        public void halted(DebuggerExample debugger, EventContext haltedAt) {
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

    public interface Callback {

        void halted(DebuggerExample debugger, EventContext haltedAt);

    }

    // in a production debugger this should be implemented using a proper service provider interface

    private static final List<Class<? extends DebuggerFrontEnd>> installedFrontEnds = new ArrayList<>();

    public static void installFrontEnd(Class<? extends DebuggerFrontEnd> frontEndClass) {
        installedFrontEnds.add(frontEndClass);
    }

    public interface DebuggerFrontEnd {

        void onAttach(DebuggerExample example);

        void onDispose();

    }

}
