/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@State(Scope.Thread)
@SuppressWarnings("deprecation")
public class EncapsulatingNodeBenchmark extends TruffleBenchmark {

    static final String TEST_LANGUAGE = "benchmark-test-language";

    private static final Object[] EMPTY_ARGS = new Object[0];

    static volatile long sideEffect = 0;

    @State(Scope.Thread)
    @SuppressWarnings("this-escape")
    public static class PushPopNewCompiled {
        final Source source = Source.create(TEST_LANGUAGE, "");
        final Context context = Context.newBuilder(TEST_LANGUAGE).allowExperimentalOptions(true).build();
        {
            maybeEnter();
            context.initialize(TEST_LANGUAGE);
        }
        final Integer intValue = 42;
        final CallTarget callTarget = new RootNode(null) {

            @Override
            public Object execute(VirtualFrame frame) {
                EncapsulatingNodeReference nodeRef0 = EncapsulatingNodeReference.getCurrent();
                Node prev0 = nodeRef0.set(node);

                EncapsulatingNodeReference nodeRef1 = EncapsulatingNodeReference.getCurrent();
                Node prev1 = nodeRef0.set(node);

                EncapsulatingNodeReference nodeRef2 = EncapsulatingNodeReference.getCurrent();
                Node prev2 = nodeRef0.set(node);
                sideEffect++;

                nodeRef2.set(prev2);
                nodeRef1.set(prev1);
                nodeRef0.set(prev0);
                return null;
            }
        }.getCallTarget();

        final Node node = new Node() {
        };

        protected void maybeEnter() {
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    @Benchmark
    public Object pushPopNewInterpreterNotEntered(PushPopNewCompiled state) {
        Node node = state.node;
        EncapsulatingNodeReference nodeRef0 = EncapsulatingNodeReference.getCurrent();
        Node prev0 = nodeRef0.set(node);

        EncapsulatingNodeReference nodeRef1 = EncapsulatingNodeReference.getCurrent();
        Node prev1 = nodeRef0.set(node);

        EncapsulatingNodeReference nodeRef2 = EncapsulatingNodeReference.getCurrent();
        Node prev2 = nodeRef0.set(node);
        sideEffect++;

        nodeRef2.set(prev2);
        nodeRef1.set(prev1);
        nodeRef0.set(prev0);
        return null;
    }

    @Benchmark
    public Object pushPopNewCompiledNotEntered(PushPopNewCompiled state) {
        return state.callTarget.call(EMPTY_ARGS);
    }

    @State(Scope.Thread)
    public static class PushPopNewCompiledEnteredSingleThread extends PushPopNewCompiled {

        @Override
        protected void maybeEnter() {
            context.enter();
        }

    }

    @State(Scope.Thread)
    public static class PushPopNewCompiledEnteredMultiThread extends PushPopNewCompiled {

        @Override
        protected void maybeEnter() {
            context.enter();

            // touch context on a second thread
            Thread t = new Thread(() -> {
                context.enter();
                context.leave();
            });
            t.start();
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

    }

    @Benchmark
    public Object pushPopNewInterpreterEnteredSingleThread(PushPopNewCompiledEnteredSingleThread state) {
        Node node = state.node;
        EncapsulatingNodeReference nodeRef0 = EncapsulatingNodeReference.getCurrent();
        Node prev0 = nodeRef0.set(node);

        EncapsulatingNodeReference nodeRef1 = EncapsulatingNodeReference.getCurrent();
        Node prev1 = nodeRef0.set(node);

        EncapsulatingNodeReference nodeRef2 = EncapsulatingNodeReference.getCurrent();
        Node prev2 = nodeRef0.set(node);
        sideEffect++;

        nodeRef2.set(prev2);
        nodeRef1.set(prev1);
        nodeRef0.set(prev0);
        return null;
    }

    @Benchmark
    public Object pushPopNewCompiledEnteredSingleThread(PushPopNewCompiledEnteredSingleThread state) {
        return state.callTarget.call(EMPTY_ARGS);
    }

    @Benchmark
    public Object pushPopNewInterpreterEnteredMultiThread(PushPopNewCompiledEnteredMultiThread state) {
        Node node = state.node;
        EncapsulatingNodeReference nodeRef0 = EncapsulatingNodeReference.getCurrent();
        Node prev0 = nodeRef0.set(node);

        EncapsulatingNodeReference nodeRef1 = EncapsulatingNodeReference.getCurrent();
        Node prev1 = nodeRef0.set(node);

        EncapsulatingNodeReference nodeRef2 = EncapsulatingNodeReference.getCurrent();
        Node prev2 = nodeRef0.set(node);
        sideEffect++;

        nodeRef2.set(prev2);
        nodeRef1.set(prev1);
        nodeRef0.set(prev0);
        return null;
    }

    @Benchmark
    public Object pushPopNewCompiledEnteredMultiThread(PushPopNewCompiledEnteredMultiThread state) {
        return state.callTarget.call(EMPTY_ARGS);
    }

}
