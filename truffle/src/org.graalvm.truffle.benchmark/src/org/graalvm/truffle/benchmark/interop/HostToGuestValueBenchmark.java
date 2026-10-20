/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.interop;

import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Host-to-guest argument conversion through {@link Value#execute(Object...)}. Values are created
 * outside measurement, and each operation crosses the boundary once. Small guest computations make
 * both conversion and subsequent use of the unwrapped argument visible.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HostToGuestValueBenchmark extends TruffleBenchmark {

    private static final Source ADD_ONE = Source.create(HostToGuestValueBenchmarkLanguage.ID, "addOne");
    private static final Source CALL = Source.create(HostToGuestValueBenchmarkLanguage.ID, "call");

    @Benchmark
    public double number(NumberState state) {
        return state.executable.execute(state.arguments[state.index++ & 1]).asDouble();
    }

    @Benchmark
    public double callable(CallableState state) {
        return state.executable.execute(state.arguments[state.index++ & 1], state.function).asDouble();
    }

    /**
     * The shared case exercises the same language in two live contexts of one engine, including a
     * call in each context. Arguments always belong to the context making the call: this measures
     * ordinary conversion, without cross-context migration. Context entry is outside measurement.
     */
    @State(Scope.Thread)
    public abstract static class ContextState {

        @Param({"single", "shared"}) public String contexts;
        private Engine engine;
        private Context context;
        private Context otherContext;
        private boolean entered;
        Value executable;
        Object[] arguments;
        double[] expected;
        int index;

        @Setup
        public void setup() {
            try {
                if (contexts.equals("shared")) {
                    engine = Engine.create();
                    otherContext = Context.newBuilder(HostToGuestValueBenchmarkLanguage.ID).engine(engine).build();
                    context = Context.newBuilder(HostToGuestValueBenchmarkLanguage.ID).engine(engine).build();
                    otherContext.initialize(HostToGuestValueBenchmarkLanguage.ID);
                    context.initialize(HostToGuestValueBenchmarkLanguage.ID);
                    otherContext.enter();
                    try {
                        initialize(otherContext);
                        verify();
                    } finally {
                        otherContext.leave();
                    }
                } else if (contexts.equals("single")) {
                    context = Context.create(HostToGuestValueBenchmarkLanguage.ID);
                } else {
                    throw new IllegalArgumentException(contexts);
                }
                context.enter();
                entered = true;
                initialize(context);
                verify();
                index = 0;
            } catch (RuntimeException | Error e) {
                close();
                throw e;
            }
        }

        abstract void initialize(Context targetContext);

        abstract double execute(int argumentIndex);

        private void verify() {
            for (int i = 0; i < arguments.length; i++) {
                double actual = execute(i);
                if (actual != expected[i]) {
                    throw new AssertionError("Expected " + expected[i] + " but got " + actual);
                }
            }
        }

        @TearDown
        public void tearDown() {
            try {
                verify();
            } finally {
                close();
            }
        }

        private void close() {
            try {
                if (entered) {
                    context.leave();
                    entered = false;
                }
            } finally {
                try {
                    if (context != null) {
                        context.close();
                    }
                } finally {
                    try {
                        if (otherContext != null) {
                            otherContext.close();
                        }
                    } finally {
                        if (engine != null) {
                            engine.close();
                        }
                    }
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class NumberState extends ContextState {

        @Param({"raw-int", "raw-double", "value-int", "value-double", "value-mixed"}) public String argumentKind;

        @Override
        void initialize(Context targetContext) {
            executable = targetContext.eval(ADD_ONE);
            switch (argumentKind) {
                case "raw-int", "value-int":
                    arguments = new Object[]{42, 43};
                    expected = new double[]{43, 44};
                    break;
                case "raw-double", "value-double":
                    arguments = new Object[]{42.5, 43.5};
                    expected = new double[]{43.5, 44.5};
                    break;
                case "value-mixed":
                    arguments = new Object[]{42, 43.5};
                    expected = new double[]{43, 44.5};
                    break;
                default:
                    throw new IllegalArgumentException(argumentKind);
            }
            if (argumentKind.startsWith("value-")) {
                for (int i = 0; i < arguments.length; i++) {
                    arguments[i] = targetContext.asValue(arguments[i]);
                }
            }
        }

        @Override
        double execute(int argumentIndex) {
            return executable.execute(arguments[argumentIndex]).asDouble();
        }
    }

    @State(Scope.Thread)
    public static class CallableState extends ContextState {

        Value function;

        @Override
        void initialize(Context targetContext) {
            executable = targetContext.eval(CALL);
            function = targetContext.eval(ADD_ONE);
            arguments = new Object[]{42, 43};
            expected = new double[]{43, 44};
        }

        @Override
        double execute(int argumentIndex) {
            return executable.execute(arguments[argumentIndex], function).asDouble();
        }
    }
}
