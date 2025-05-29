/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import com.oracle.truffle.api.CompilerDirectives;

@State(Scope.Thread)
@CompilerControl(Mode.DONT_INLINE)
@OperationsPerInvocation(JavaMembersBenchmark.REPEAT)
public class JavaMembersBenchmark extends TruffleBenchmark {

    public static final int NUM_OVERLOADS = 6;
    private static final int NUM_OBJECTS = 2 * NUM_OVERLOADS;
    public static final int REPEAT_MEGA = 1000;
    public static final int REPEAT = REPEAT_MEGA * NUM_OBJECTS;

    @Benchmark
    public void doMemberInterop(InteropContextState state) {
        state.value.executeVoid();
    }

    @Benchmark
    public void doJavaRead(JavaContextState state) {
        TestHostObject object = state.objects[0];
        for (int i = 0; i < REPEAT; i++) {
            doJavaRead(object);
        }
    }

    private static void doJavaRead(TestHostObject object) {
        Object value = object.field;
        CompilerDirectives.blackhole(value);
    }

    @Benchmark
    public void doJavaWrite(JavaContextState state) {
        TestHostObject object = state.objects[0];
        for (int i = 0; i < REPEAT; i++) {
            doJavaWrite(object);
        }
    }

    private static void doJavaWrite(TestHostObject object) {
        object.field = 0;
    }

    @Benchmark
    public void doJavaInvoke(JavaContextState state) {
        TestHostObject object = state.objects[0];
        for (int i = 0; i < REPEAT; i++) {
            object.process(0);
        }
    }

    @Benchmark
    public void doJavaInvokeOverloads(JavaContextState state) {
        TestHostObject object = state.objects[0];
        for (int i = 0; i < REPEAT / NUM_OVERLOADS; i++) {
            object.process("");
            object.process(i);
            object.process(i, i);
            object.process(i, i, i);
            object.process(i, i, i, i);
            object.process(i, i, i, i, i);
        }
    }

    @Benchmark
    public void doJavaInvokeMegamorphic(JavaContextState state) {
        TestHostObject[] objects = state.objects;
        for (int i = 0; i < REPEAT_MEGA; i++) {
            objects[0].process("");
            objects[1].process("");
            objects[2].process(i);
            objects[3].process(i);
            objects[4].process(i, i);
            objects[5].process(i, i);
            objects[6].process(i, i, i);
            objects[7].process(i, i, i);
            objects[8].process(i, i, i, i);
            objects[9].process(i, i, i, i);
            objects[10].process(i, i, i, i, i);
            objects[11].process(i, i, i, i, i);
        }
    }

    static TestHostObject[] createObjects() {
        TestHostObject[] objects = new TestHostObject[NUM_OBJECTS];
        objects[0] = new TestHostObject();
        objects[1] = new TestHostObject_01();
        objects[2] = new TestHostObject_02();
        objects[3] = new TestHostObject_03();
        objects[4] = new TestHostObject_04();
        objects[5] = new TestHostObject_05();
        objects[6] = new TestHostObject_06();
        objects[7] = new TestHostObject_07();
        objects[8] = new TestHostObject_08();
        objects[9] = new TestHostObject_09();
        objects[10] = new TestHostObject_10();
        objects[11] = new TestHostObject_11();
        return objects;
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class InteropContextState {

        @Param({"Read", "Write", "Execute", "Invoke", "InvokeOverloads", "InvokeMegamorphic", "EMPTY"}) //
        public String code;
        Context context;
        Value value;

        @Setup
        public void before() {
            Source source = Source.create(MembersBenchmarkLanguage.ID, code);
            context = Context.newBuilder(MembersBenchmarkLanguage.ID).allowAllAccess(true).allowHostAccess(HostAccess.ALL).build();
            context.enter();
            Object[] objects = createObjects();
            for (int i = 0; i < NUM_OBJECTS; i++) {
                context.getPolyglotBindings().putMember("object" + i, objects[i]);
            }
            value = context.parse(source);
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @State(org.openjdk.jmh.annotations.Scope.Thread)
    public static class JavaContextState {

        TestHostObject[] objects;

        @Setup
        public void before() {
            objects = createObjects();
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject {

        public volatile Object field = "TEST";

        public void process(String s) {
            CompilerDirectives.blackhole(s);
        }

        public void process(int i1) {
            CompilerDirectives.blackhole(i1);
        }

        public void process(int i1, int i2) {
            CompilerDirectives.blackhole(i1);
            CompilerDirectives.blackhole(i2);
        }

        public void process(int i1, int i2, int i3) {
            CompilerDirectives.blackhole(i1);
            CompilerDirectives.blackhole(i2);
            CompilerDirectives.blackhole(i3);
        }

        public void process(int i1, int i2, int i3, int i4) {
            CompilerDirectives.blackhole(i1);
            CompilerDirectives.blackhole(i2);
            CompilerDirectives.blackhole(i3);
            CompilerDirectives.blackhole(i4);
        }

        public void process(int i1, int i2, int i3, int i4, int i5) {
            CompilerDirectives.blackhole(i1);
            CompilerDirectives.blackhole(i2);
            CompilerDirectives.blackhole(i3);
            CompilerDirectives.blackhole(i4);
            CompilerDirectives.blackhole(i5);
        }

        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_01 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_02 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_03 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_04 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_05 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_06 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_07 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_08 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_09 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_10 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }

    @CompilerControl(Mode.DONT_INLINE)
    public static class TestHostObject_11 extends TestHostObject {

        @Override
        public void process(Object o) {
            CompilerDirectives.blackhole(o);
        }
    }
}
