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
package com.oracle.truffle.api.benchmark;

import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 10)
@Measurement(iterations = 5)
public class EngineBenchmark extends TruffleBenchmark {

    private static final String TEST_LANGUAGE = "benchmark-test-language";

    private static final String CONTEXT_LOOKUP_SOURCE = "contextLookup";

    @Benchmark
    public Object createEngine() {
        return Engine.create();
    }

    @Benchmark
    public Object createContext() {
        return Context.create();
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class ContextLookupSingleContext {
        final Source source = Source.create(TEST_LANGUAGE, CONTEXT_LOOKUP_SOURCE);
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
        final Source source = Source.create(TEST_LANGUAGE, CONTEXT_LOOKUP_SOURCE);
        final Engine engine = Engine.create();
        final Context context1 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context2 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Context context3 = Context.newBuilder(TEST_LANGUAGE).engine(engine).build();
        final Value value1 = context1.eval(source);
        final Value value2 = context2.eval(source);
        final Value value3 = context3.eval(source);

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

        final Source source = Source.create(TEST_LANGUAGE, CONTEXT_LOOKUP_SOURCE);
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
        final Source source = Source.create(TEST_LANGUAGE, CONTEXT_LOOKUP_SOURCE);
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
        final Value hostValue = context.getPolyglotBindings().getMember("context");

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
    public Object executePolyglot1CallTarget(CallTargetCallState state) {
        return state.callTarget.call(state.internalContext.object);
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

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class CallTargetCallState {
        final Source source = Source.create(TEST_LANGUAGE, "");
        final Context context = Context.create(TEST_LANGUAGE);
        final Value hostValue = context.getBindings(TEST_LANGUAGE).getMember("context");
        final BenchmarkContext internalContext = hostValue.asHostObject();
        final Node executeNode = Message.EXECUTE.createNode();
        final Integer intValue = 42;
        final CallTarget callTarget = Truffle.getRuntime().createCallTarget(new RootNode(null) {

            private final Integer constant = 42;

            @Override
            public Object execute(VirtualFrame frame) {
                return constant;
            }
        });

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    public Object executeCallTarget2(CallTargetCallState state) {
        CallTarget callTarget = state.callTarget;
        int result = 0;
        result += (int) callTarget.call(state.internalContext.object, state.intValue);
        result += (int) callTarget.call(state.internalContext.object, state.intValue, state.intValue);
        result += (int) callTarget.call(state.internalContext.object, state.intValue, state.intValue, state.intValue);
        result += (int) callTarget.call(state.internalContext.object, state.intValue, state.intValue, state.intValue, state.intValue);
        return result;
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

    /*
     * Test language that ensures that only engine overhead is tested.
     */
    @TruffleLanguage.Registration(id = TEST_LANGUAGE, name = "")
    public static class BenchmarkTestLanguage extends TruffleLanguage<BenchmarkContext> {

        @Override
        protected BenchmarkContext createContext(Env env) {
            BenchmarkContext context = new BenchmarkContext(env, new Function<TruffleObject, Scope>() {
                @Override
                public Scope apply(TruffleObject obj) {
                    return Scope.newBuilder("Benchmark top scope", obj).build();
                }
            });
            return context;
        }

        @Override
        protected void initializeContext(BenchmarkContext context) throws Exception {
            ForeignAccess.sendWrite(Message.WRITE.createNode(), (TruffleObject) context.env.getPolyglotBindings(), "context", context.env.asGuestValue(context));
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            if (request.getSource().getCharacters().equals(CONTEXT_LOOKUP_SOURCE)) {
                BenchmarkObject object = new BenchmarkObject();
                object.runOnExecute = new Supplier<Object>() {
                    final ContextReference<BenchmarkContext> context = getContextReference();

                    public Object get() {
                        return context.get();
                    }

                };
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(object));
            } else {
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(getCurrentContext(BenchmarkTestLanguage.class).object));
            }
        }

        @Override
        protected Iterable<Scope> findLocalScopes(BenchmarkContext context, Node node, Frame frame) {
            if (node != null) {
                return super.findLocalScopes(context, node, frame);
            } else {
                return context.topScopes;
            }
        }

        @Override
        protected Iterable<Scope> findTopScopes(BenchmarkContext context) {
            return context.topScopes;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return object instanceof BenchmarkObject;
        }

    }

    static class BenchmarkContext {

        final Env env;
        final BenchmarkObject object = new BenchmarkObject();
        final Iterable<Scope> topScopes;

        BenchmarkContext(Env env, Function<TruffleObject, Scope> scopeProvider) {
            this.env = env;
            topScopes = Collections.singleton(scopeProvider.apply(new TopScopeObject(this)));
        }
    }

    public static class BenchmarkObject implements TruffleObject {

        private static final Integer constant = 42;

        Object value = 42;
        long longValue = 42L;
        Supplier<Object> runOnExecute = () -> {
            return constant;
        };

        public ForeignAccess getForeignAccess() {
            return BenchmarkObjectMRForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof BenchmarkObject;
        }

    }

    static final class TopScopeObject implements TruffleObject {

        private final BenchmarkContext context;

        private TopScopeObject(BenchmarkContext context) {
            this.context = context;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return TopScopeObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof TopScopeObject;
        }

        @MessageResolution(receiverType = TopScopeObject.class)
        static class TopScopeObjectMessageResolution {

            @Resolve(message = "READ")
            abstract static class VarsMapReadNode extends Node {

                @TruffleBoundary
                public Object access(TopScopeObject ts, String name) {
                    if ("context".equals(name)) {
                        return ts.context.env.asGuestValue(ts.context);
                    } else {
                        return ts.context.env.asGuestValue(ts.context.object);
                    }
                }
            }

            @Resolve(message = "KEY_INFO")
            abstract static class VarsMapKeyInfoNode extends Node {

                public int access(@SuppressWarnings("unused") TopScopeObject ts, String propertyName) {
                    if ("context".equals(propertyName)) {
                        return KeyInfo.READABLE;
                    }
                    return 0;
                }
            }

            @Resolve(message = "KEYS")
            abstract static class VarsMapKeysNode extends Node {

                @TruffleBoundary
                public Object access(TopScopeObject ts) {
                    return ts.context.env.asGuestValue(new String[]{"context"});
                }
            }
        }
    }

    @MessageResolution(receiverType = BenchmarkObject.class)
    @SuppressWarnings("unused")
    static class BenchmarkObjectMR {

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {

            public Object access(BenchmarkObject obj, String name) {
                return obj.value;
            }

            public Object access(BenchmarkObject obj, Number name) {
                return obj.value;
            }
        }

        @Resolve(message = "EXECUTE")
        abstract static class ExecuteNode extends Node {

            @CompilationFinal private Supplier<Object> runOnExecute;

            public Object access(Object obj, Object[] args) {
                if (runOnExecute == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    runOnExecute = ((BenchmarkObject) obj).runOnExecute;
                }
                return runOnExecute.get();
            }
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class IsExecutableNode extends Node {

            public boolean access(Object obj) {
                return true;
            }
        }

        @Resolve(message = "HAS_SIZE")
        abstract static class HasSizeNode extends Node {

            public boolean access(Object obj) {
                return true;
            }
        }

        @Resolve(message = "IS_POINTER")
        abstract static class IsNativeNode extends Node {

            public boolean access(Object obj) {
                return true;
            }
        }

        @Resolve(message = "AS_POINTER")
        abstract static class AsPointerNode extends Node {

            public long access(BenchmarkObject obj) {
                return obj.longValue;
            }
        }

        @Resolve(message = "WRITE")
        abstract static class WriteNode extends Node {

            public Object access(BenchmarkObject obj, String name, Object value) {
                obj.value = value;
                return value;
            }

            public Object access(BenchmarkObject obj, Number index, Object value) {
                obj.value = value;
                return value;
            }

        }

    }

}
