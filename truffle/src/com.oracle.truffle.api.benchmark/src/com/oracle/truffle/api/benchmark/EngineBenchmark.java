/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.benchmark;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@Warmup(iterations = 5)
@Measurement(iterations = 3)
public class EngineBenchmark extends TruffleBenchmark {

    private static final String TEST_LANGUAGE = "benchmark-test-language";

    @Benchmark
    public Object createEngine() {
        return Engine.create();
    }

    @Benchmark
    public Object createContext() {
        return Context.create();
    }

    @State(Scope.Thread)
    public static class ContextState {
        final Source source = Source.create(TEST_LANGUAGE, "");
        final Context context = Context.create(TEST_LANGUAGE);
        final Value value = context.eval(source);
        final Integer intValue = 42;
    }

    @Benchmark
    public Object eval(ContextState state) {
        return state.context.eval(state.source);
    }

    @Benchmark
    public int executePolyglot(ContextState state) {
        return state.value.execute(state.intValue).asInt();
    }

    @State(Scope.Thread)
    public static class CallTargetCallState {
        final Source source = Source.create(TEST_LANGUAGE, "");
        final Context context = Context.create(TEST_LANGUAGE);
        final BenchmarkContext internalContext = context.lookup(TEST_LANGUAGE, "context").asHostObject();
        final Node executeNode = Message.createExecute(0).createNode();
        final Integer intValue = 42;
        final CallTarget callTarget = Truffle.getRuntime().createCallTarget(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return frame.getArguments()[1];
            }
        });
    }

    @Benchmark
    public Object executeForeign(CallTargetCallState state) {
        try {
            return ForeignAccess.sendExecute(state.executeNode, state.internalContext.object, state.intValue);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    @Benchmark
    public Object executeCallTarget(CallTargetCallState state) {
        return state.callTarget.call(state.internalContext.object, state.intValue);
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
    public Object getArrayElement(ContextState state) {
        return state.value.getArrayElement(42);
    }

    @Benchmark
    public Object setArrayElement(ContextState state) {
        return state.value.getArrayElement(42);
    }

    /*
     * Test language that ensures that only engine overhead is tested.
     */
    @TruffleLanguage.Registration(id = TEST_LANGUAGE, name = "", version = "", mimeType = "")
    public static class BenchmarkTestLanguage extends TruffleLanguage<BenchmarkContext> {

        @Override
        protected BenchmarkContext createContext(Env env) {
            return new BenchmarkContext(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(getCurrentContext(BenchmarkTestLanguage.class).object));
        }

        @Override
        protected Object lookupSymbol(BenchmarkContext context, String symbolName) {
            switch (symbolName) {
                case "context":
                    return context;
            }
            return context.object;
        }

        @Override
        protected Object getLanguageGlobal(BenchmarkContext context) {
            return context.object;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return object instanceof BenchmarkObject;
        }

    }

    private static class BenchmarkContext {

        private final Env env;
        final BenchmarkObject object = new BenchmarkObject();

        BenchmarkContext(Env env) {
            this.env = env;
        }

    }

    public static class BenchmarkObject implements TruffleObject {

        Object value = 42;

        public ForeignAccess getForeignAccess() {
            return BenchmarkObjectMRForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof BenchmarkObject;
        }

    }

    @MessageResolution(receiverType = BenchmarkObject.class)
    static class BenchmarkObjectMR {

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {

            public Object access(BenchmarkObject obj, String name) {
                return obj.value;
            }
        }

        @Resolve(message = "EXECUTE")
        abstract static class ExecuteNode extends Node {

            public Object access(Object obj, Object[] args) {
                // identity function
                return args[0];
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

        @Resolve(message = "WRITE")
        abstract static class WriteNode extends Node {

            public Object access(BenchmarkObject obj, String name, Object value) {
                obj.value = value;
                return value;
            }

            public Object access(BenchmarkObject obj, int index, Object value) {
                obj.value = value;
                return value;
            }

        }

    }

}
