/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.stream.IntStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

@Warmup(iterations = 10, time = 1)
@SuppressWarnings("deprecation")
public class DynamicObjectBenchmark extends TruffleBenchmark {

    static final String TEST_LANGUAGE = "benchmark-test-language";
    private static final int PROPERTY_KEYS_PER_ITERATION = 1000;

    private static final class MyDynamicObject extends DynamicObject {
        private MyDynamicObject(Shape shape) {
            super(shape);
        }
    }

    @State(Scope.Benchmark)
    public static class SharedEngineState {
        final Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.Compilation", "false").build();
        final Shape rootShape = Shape.newBuilder().build();
        final String[] propertyKeys = IntStream.range(0, PROPERTY_KEYS_PER_ITERATION).mapToObj(i -> "testKey" + i).toArray(String[]::new);
        final Shape[] expectedShapes = new Shape[PROPERTY_KEYS_PER_ITERATION];

        @TearDown
        public void tearDown() {
            engine.close();
        }

        private void assertSameShape(int i, Shape actualShape) {
            Shape expectedShape = expectedShapes[i];
            if (expectedShape == null) {
                expectedShapes[i] = actualShape;
            } else if (expectedShape != actualShape) {
                throw new AssertionError("Expected shape: " + expectedShape + " but was: " + actualShape);
            }
        }
    }

    @State(Scope.Thread)
    public static class PerThreadContextState {
        Context context;

        @Setup
        public void setup(SharedEngineState shared) {
            context = Context.newBuilder(TEST_LANGUAGE).engine(shared.engine).build();
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

    /**
     * Benchmark that stresses the shape transition cache with multi-threaded lookups.
     */
    @Benchmark
    @Threads(8)
    public void shapeTransitionMapContended(SharedEngineState shared, PerThreadContextState perThread) {
        perThread.context.enter();
        for (int i = 0; i < PROPERTY_KEYS_PER_ITERATION; i++) {
            DynamicObject object = new MyDynamicObject(shared.rootShape);
            DynamicObjectLibrary.getUncached().put(object, shared.propertyKeys[i], "testValue");
            shared.assertSameShape(i, object.getShape());
        }
        perThread.context.leave();
    }

    /**
     * Benchmark that stresses the shape transition cache with single-threaded lookups.
     */
    @Benchmark
    @Threads(1)
    public void shapeTransitionMapUncontended(SharedEngineState shared, PerThreadContextState perThread) {
        perThread.context.enter();
        for (int i = 0; i < PROPERTY_KEYS_PER_ITERATION; i++) {
            DynamicObject object = new MyDynamicObject(shared.rootShape);
            DynamicObjectLibrary.getUncached().put(object, shared.propertyKeys[i], "testValue");
            shared.assertSameShape(i, object.getShape());
        }
        perThread.context.leave();
    }
}
