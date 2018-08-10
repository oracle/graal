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
