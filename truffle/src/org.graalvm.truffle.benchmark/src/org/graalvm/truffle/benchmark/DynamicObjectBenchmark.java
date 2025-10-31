/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;

@Warmup(iterations = 10, time = 1)
public class DynamicObjectBenchmark extends TruffleBenchmark {

    private static final int PROPERTY_KEYS_PER_ITERATION = 1000;
    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static final String[] PROPERTY_KEYS = IntStream.range(0, PROPERTY_KEYS_PER_ITERATION).mapToObj(i -> "testKey" + i).toArray(String[]::new);
    static final String SOME_KEY = PROPERTY_KEYS[0];
    static final String SOME_KEY_INT = PROPERTY_KEYS[1];
    static final String SOME_KEY_LONG = PROPERTY_KEYS[2];
    static final String SOME_KEY_DOUBLE = PROPERTY_KEYS[3];

    private static final class MyDynamicObject extends DynamicObject {
        private MyDynamicObject(Shape shape) {
            super(shape);
        }
    }

    private static final class MyDynamicObjectWithFields extends DynamicObject {
        @DynamicField private long lf1;
        @DynamicField private long lf2;
        @DynamicField private long lf3;
        @DynamicField private Object of0;
        @DynamicField private Object of1;

        MyDynamicObjectWithFields(Shape shape) {
            super(shape);
        }
    }

    @State(Scope.Benchmark)
    public static class SharedEngineState {
        final Shape rootShape = Shape.newBuilder().layout(MyDynamicObject.class, LOOKUP).build();
        final Shape[] expectedShapes = new Shape[PROPERTY_KEYS_PER_ITERATION];

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
        DynamicObject object;

        @Setup
        public void setup(SharedEngineState shared) {
            object = new MyDynamicObject(shared.rootShape);
            for (int i = 0; i < PROPERTY_KEYS_PER_ITERATION; i++) {
                String key = PROPERTY_KEYS[i];
                DynamicObject.PutNode.getUncached().put(object, key, "testValue");
            }
        }
    }

    /**
     * Benchmark that stresses the shape transition cache with multi-threaded lookups.
     */
    @Benchmark
    @Threads(8)
    public void shapeTransitionMapContended(SharedEngineState shared, PerThreadContextState perThread) {
        for (int i = 0; i < PROPERTY_KEYS_PER_ITERATION; i++) {
            DynamicObject object = new MyDynamicObject(shared.rootShape);
            DynamicObject.PutNode.getUncached().put(object, PROPERTY_KEYS[i], "testValue");
            shared.assertSameShape(i, object.getShape());
        }
    }

    /**
     * Benchmark that stresses the shape transition cache with single-threaded lookups.
     */
    @Benchmark
    @Threads(1)
    public void shapeTransitionMapUncontended(SharedEngineState shared, PerThreadContextState perThread) {
        for (int i = 0; i < PROPERTY_KEYS_PER_ITERATION; i++) {
            DynamicObject object = new MyDynamicObject(shared.rootShape);
            DynamicObject.PutNode.getUncached().put(object, PROPERTY_KEYS[i], "testValue");
            shared.assertSameShape(i, object.getShape());
        }
    }

    @State(Scope.Benchmark)
    public static class AccessNodeBenchState {

        @Param({"false", "true"}) boolean field;

        Shape rootShape;
        DynamicObject object;

        final DynamicObject.GetNode getNode = DynamicObject.GetNode.create();
        final DynamicObject.PutNode putNode = DynamicObject.PutNode.create();

        @Setup
        public void setup() {
            rootShape = Shape.newBuilder().layout(field ? MyDynamicObjectWithFields.class : MyDynamicObject.class, LOOKUP).build();
            object = field ? new MyDynamicObjectWithFields(rootShape) : new MyDynamicObject(rootShape);

            for (int i = 0; i < 10; i++) {
                String key = PROPERTY_KEYS[i];
                if (key.equals(SOME_KEY_INT)) {
                    DynamicObject.PutNode.getUncached().put(object, key, i);
                } else if (key.equals(SOME_KEY_LONG)) {
                    DynamicObject.PutNode.getUncached().put(object, key, (long) i);
                } else if (key.equals(SOME_KEY_DOUBLE)) {
                    DynamicObject.PutNode.getUncached().put(object, key, (double) i);
                } else {
                    DynamicObject.PutNode.getUncached().put(object, key, "testValue");
                }
            }
        }
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Object get(AccessNodeBenchState state) {
        DynamicObject object = state.object;
        return state.getNode.getOrDefault(object, SOME_KEY, null);
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int getInt(AccessNodeBenchState state) throws UnexpectedResultException {
        DynamicObject object = state.object;
        return state.getNode.getIntOrDefault(object, SOME_KEY_INT, null);
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long getLong(AccessNodeBenchState state) throws UnexpectedResultException {
        DynamicObject object = state.object;
        return state.getNode.getLongOrDefault(object, SOME_KEY_LONG, null);
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public double getDouble(AccessNodeBenchState state) throws UnexpectedResultException {
        DynamicObject object = state.object;
        return state.getNode.getDoubleOrDefault(object, SOME_KEY_DOUBLE, null);
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void put(AccessNodeBenchState state) {
        DynamicObject object = state.object;
        state.putNode.put(object, SOME_KEY, "updated value");
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void putInt(AccessNodeBenchState state) {
        DynamicObject object = state.object;
        state.putNode.put(object, SOME_KEY_INT, 42);
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void putLong(AccessNodeBenchState state) {
        DynamicObject object = state.object;
        state.putNode.put(object, SOME_KEY_LONG, (long) 42);
    }

    @Benchmark
    @Threads(1)
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void putDouble(AccessNodeBenchState state) {
        DynamicObject object = state.object;
        state.putNode.put(object, SOME_KEY_DOUBLE, (double) 42);
    }
}
