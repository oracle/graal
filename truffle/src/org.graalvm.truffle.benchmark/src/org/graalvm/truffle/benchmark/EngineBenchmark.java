/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.truffle.benchmark.InterpreterCallBenchmark.BenchmarkState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 30, time = 1)
@SuppressWarnings("deprecation")
public class EngineBenchmark extends TruffleBenchmark {

    public static final String TEST_LANGUAGE = "benchmark-test-language";

    public static final String CONTEXT_LOOKUP = "contextLookup";

    @Benchmark
    public Object createEngine() {
        return Engine.create();
    }

    @Benchmark
    public Object createContext() {
        return Context.create();
    }

    @State(org.openjdk.jmh.annotations.Scope.Benchmark)
    public static class ContextLookupInterpreter {
        final Source source = Source.newBuilder(TEST_LANGUAGE, "100", CONTEXT_LOOKUP).buildLiteral();
        final Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.Compilation", "false").build();
        final Context context1 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context2 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context3 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Value value1 = context1.eval(source);
        final Value value2 = context2.eval(source);
        final Value value3 = context3.eval(source);

        public ContextLookupInterpreter() {
        }

        @TearDown
        public void tearDown() {
            context1.close();
            context2.close();
            context3.close();
        }
    }

    @Benchmark
    @Threads(10)
    public void lookupContextInterpreter(ContextLookupInterpreter state) {
        state.context1.enter();
        for (int i = 0; i < CONTEXT_LOOKUP_ITERATIONS; i++) {
            state.value1.executeVoid();
        }
        state.context1.leave();
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ContextLookupSingleContext {
        final Source source = Source.newBuilder(TEST_LANGUAGE, "100", CONTEXT_LOOKUP).buildLiteral();
        final Context context = Context.create(TEST_LANGUAGE);
        final Value value = context.eval(source);

        public ContextLookupSingleContext() {
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    private static final int CONTEXT_LOOKUP_ITERATIONS = 1000;

    @Benchmark
    public void lookupContextSingleContext(ContextLookupSingleContext state) {
        state.context.enter();
        for (int i = 0; i < CONTEXT_LOOKUP_ITERATIONS; i++) {
            state.value.executeVoid();
        }
        state.context.leave();
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ContextLookupMultiContext {
        final Source lookup = Source.newBuilder(TEST_LANGUAGE, "100", CONTEXT_LOOKUP).buildLiteral();
        final Engine engine = Engine.create();
        final Context context1 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context2 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context3 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Value value1 = context1.eval(lookup);
        final Value value2 = context2.eval(lookup);
        final Value value3 = context3.eval(lookup);

        public ContextLookupMultiContext() {
        }

        @TearDown()
        public void tearDown() {
            context1.close();
            context2.close();
            context3.close();
        }
    }

    @Benchmark
    public void lookupContextMultiContext(ContextLookupMultiContext state) {
        state.context1.enter();
        for (int i = 0; i < CONTEXT_LOOKUP_ITERATIONS; i++) {
            state.value1.executeVoid();
        }
        state.context1.leave();
    }

    @State(org.openjdk.jmh.annotations.Scope.Benchmark)
    public static class ContextLookupMultiThread {

        final Source source = Source.newBuilder(TEST_LANGUAGE, "100", CONTEXT_LOOKUP).buildLiteral();
        final Context context = Context.create(TEST_LANGUAGE);
        final Value value = context.eval(source);

        public ContextLookupMultiThread() {
        }

        @TearDown
        public void tearDown() {
            context.close();
        }

    }

    @Benchmark
    @Threads(10)
    public void lookupContextMultiThread(ContextLookupMultiThread state) {
        state.context.enter();
        for (int i = 0; i < CONTEXT_LOOKUP_ITERATIONS; i++) {
            state.value.executeVoid();
        }
        state.context.leave();
    }

    @State(org.openjdk.jmh.annotations.Scope.Benchmark)
    public static class ContextLookupMultiThreadMultiContext {
        final Source source = Source.newBuilder(TEST_LANGUAGE, "100", CONTEXT_LOOKUP).buildLiteral();
        final Engine engine = Engine.create();
        final Context context1 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context2 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context3 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Value value1 = context1.eval(source);
        final Value value2 = context2.eval(source);
        final Value value3 = context3.eval(source);

        public ContextLookupMultiThreadMultiContext() {
        }

        @Setup(Level.Trial)
        public void enterThread() {
        }

        @TearDown
        public void tearDown() {
            context1.close();
            context2.close();
            context3.close();
        }
    }

    @Benchmark
    @Threads(10)
    public void lookupContextMultiThreadMultiContext(ContextLookupMultiThreadMultiContext state) {
        state.context1.enter();
        for (int i = 0; i < CONTEXT_LOOKUP_ITERATIONS; i++) {
            state.value1.executeVoid();
        }
        state.context1.leave();
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ContextState {
        final Source source = Source.create(TEST_LANGUAGE, "");
        final Context context = Context.create(TEST_LANGUAGE);
        final Value value = context.eval(source);
        final Integer intValue = 42;
        final Value hostValue = context.asValue(new Object());

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ContextStateEnterLeave extends ContextState {

        public ContextStateEnterLeave() {
            context.enter();
        }

        @Override
        public void tearDown() {
            context.leave();
            super.tearDown();
        }
    }

    @Benchmark
    public Object eval(ContextState state) {
        return state.context.eval(state.source);
    }

    @Benchmark
    public void executePolyglot1(ContextState state) {
        state.value.execute();
    }

    @Benchmark
    public void executePolyglot1Void(ContextState state) {
        state.value.executeVoid();
    }

    @Benchmark
    public void executePolyglot1VoidEntered(ContextStateEnterLeave state) {
        state.value.executeVoid();
    }

    @Benchmark
    public Object executeCallTarget1(CallTargetCallState state) {
        return state.callTarget.call();
    }

    @Benchmark
    public int executePolyglot2(ContextState state) {
        int result = 0;
        Value value = state.value;
        result += value.execute(state.intValue).asInt();
        result += value.execute(state.intValue, state.intValue).asInt();
        result += value.execute(state.intValue, state.intValue, state.intValue).asInt();
        result += value.execute(state.intValue, state.intValue, state.intValue, state.intValue).asInt();
        return result;
    }

    @Benchmark
    public Object executeCallTarget2(CallTargetCallState state) {
        int result = 0;
        result += (int) state.callTarget.call(state.intValue);
        result += (int) state.callTarget.call(state.intValue, state.intValue);
        result += (int) state.callTarget.call(state.intValue, state.intValue, state.intValue);
        result += (int) state.callTarget.call(state.intValue, state.intValue, state.intValue, state.intValue);
        return result;
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class CallTargetCallState {
        final Source source = Source.create(TEST_LANGUAGE, "");
        final Context context = Context.create(TEST_LANGUAGE);
        {
            context.initialize(TEST_LANGUAGE);
        }
        final Integer intValue = 42;
        final CallTarget callTarget = new RootNode(null) {

            private final Integer constant = 42;

            @Override
            public Object execute(VirtualFrame frame) {
                return constant;
            }
        }.getCallTarget();

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    public Object isNativePointer(ContextState state) {
        return state.value.isNativePointer();
    }

    @Benchmark
    public Object asNativePointer(ContextState state) {
        return state.value.asNativePointer();
    }

    @Benchmark
    public Object hasMembers(ContextState state) {
        return state.value.hasMembers();
    }

    @Benchmark
    public void putMember(ContextState state) {
        state.value.putMember("42", state.intValue);
    }

    @Benchmark
    public Object getMember(ContextState state) {
        return state.value.getMember("42");
    }

    @Benchmark
    public Object hasArrayElements(ContextState state) {
        return state.value.hasArrayElements();
    }

    @Benchmark
    public Object getArrayElement(ContextState state) {
        return state.value.getArrayElement(42);
    }

    @Benchmark
    public Object setArrayElement(ContextState state) {
        return state.value.getArrayElement(42);
    }

    @Benchmark
    public Object canExecute(ContextState state) {
        return state.value.canExecute();
    }

    @Benchmark
    public Object isHostObject(ContextState state) {
        return state.hostValue.isHostObject();
    }

    @Benchmark
    public Object asHostObject(ContextState state) {
        return state.hostValue.asHostObject();
    }

    @State(Scope.Thread)
    public static class SourceEmbedderCreate extends BenchmarkState {

        final File file;

        public SourceEmbedderCreate() {
            try {
                Path p = Files.createTempFile("embedder_create", "js");
                Files.write(p, "Foobarbazz".getBytes());
                file = p.toFile();
                file.deleteOnExit();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

    }

    @Benchmark
    public Object sourceEmbedderCreate(SourceEmbedderCreate state) throws IOException {
        return org.graalvm.polyglot.Source.newBuilder(TEST_LANGUAGE, state.file).build();
    }

    @State(Scope.Thread)
    public static class SourceLanguageCreate {

        TruffleFile file;

        final Context context;

        public SourceLanguageCreate() {
            try {
                context = Context.newBuilder().allowIO(true).build();
                context.initialize(TEST_LANGUAGE);
                context.enter();
                Path p = Files.createTempFile("embedder_create", "js");
                Files.write(p, "Foobarbazz".getBytes());
                p.toFile().deleteOnExit();
                file = EngineBenchmarkTestLanguage.getCurrentEnv().getInternalTruffleFile(p.toString());
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }

    }

    @Benchmark
    public Object sourceLanguageCreate(SourceLanguageCreate state) throws IOException {
        return com.oracle.truffle.api.source.Source.newBuilder(TEST_LANGUAGE, state.file).build();
    }

}
