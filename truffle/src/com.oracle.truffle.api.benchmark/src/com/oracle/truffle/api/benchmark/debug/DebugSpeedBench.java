/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark.debug;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;

@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Thread)
public class DebugSpeedBench implements SuspendedCallback {

    private static final String CODE_STEP = "ROOT(\n" +
                    "  DEFINE(mediumLoop,\n" +
                    "    LOOP(1000, STATEMENT(EXPRESSION, EXPRESSION))\n" +
                    "  ),\n" +
                    "  DEFINE(longLoop,\n" +
                    "    LOOP(10000, STATEMENT(EXPRESSION), CALL(mediumLoop))\n" +
                    "  ),\n" +
                    "  STATEMENT(EXPRESSION),\n" +
                    "  CALL(longLoop),\n" +
                    "  STATEMENT(EXPRESSION)\n" +
                    ")";

    private enum ACTION {
        STEP_INTO,
        STEP_OVER,
        STEP_OUT,
        STEP_IN_OUT,
        CONTINUE
    }

    private DebuggerSession session;
    private Source source;
    private Context context;
    private volatile ACTION action;

    @Setup
    public void beforeTesting() {
        source = Source.newBuilder("instrumentation-test-language", CODE_STEP, "StepTest.instr").buildLiteral();
        context = Context.create();
        Debugger debugger = context.getEngine().getInstruments().get("debugger").lookup(Debugger.class);
        session = debugger.startSession(this);
        session.suspendNextExecution();
    }

    @TearDown
    public void afterTesting() {
        if (session != null) {
            session.close();
        }
    }

    @Benchmark
    public void noDebugger() {
        action = ACTION.CONTINUE;
        if (session != null) {
            session.close();
            session = null;
        }
        context.eval(source);
    }

    @Benchmark
    public void debuggerNoAction() {
        action = ACTION.CONTINUE;
        context.eval(source);
    }

    @Benchmark
    public void doStepInto() {
        action = ACTION.STEP_INTO;
        context.eval(source);
    }

    @Benchmark
    public void doStepOver() {
        action = ACTION.STEP_OVER;
        context.eval(source);
    }

    @Benchmark
    public void doStepOut() {
        action = ACTION.STEP_IN_OUT;
        context.eval(source);
    }

    @Override
    public void onSuspend(SuspendedEvent event) {
        switch (action) {
            case STEP_INTO:
                event.prepareStepInto(1);
                break;
            case STEP_OVER:
                event.prepareStepOver(1);
                break;
            case STEP_OUT:
                event.prepareStepOut(1);
                break;
            case STEP_IN_OUT:
                event.prepareStepInto(1);
                action = ACTION.STEP_OUT;
                break;
            case CONTINUE:
                event.prepareContinue();
                break;
        }
    }
}
