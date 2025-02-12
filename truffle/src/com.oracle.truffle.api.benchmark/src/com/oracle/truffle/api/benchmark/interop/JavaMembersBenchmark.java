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
package com.oracle.truffle.api.benchmark.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.benchmark.TruffleBenchmark;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Thread)
@CompilerControl(Mode.DONT_INLINE)
@OperationsPerInvocation(JavaMembersBenchmark.REPEAT)
public class JavaMembersBenchmark extends TruffleBenchmark {

    private static final int NUM_OVERLOADS = 6;
    private static final int NUM_OBJECTS = 2 * NUM_OVERLOADS;
    private static final int REPEAT_MEGA = 1000;
    static final int REPEAT = REPEAT_MEGA * NUM_OBJECTS;

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
            Source source = Source.create(ProxyLanguage.ID, code);
            ProxyLanguage.setDelegate(new MembersBenchmarkLanguage());
            context = Context.newBuilder(ProxyLanguage.ID).allowAllAccess(true).allowHostAccess(HostAccess.ALL).build();
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
            ProxyLanguage.setDelegate(new ProxyLanguage());
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

    static final class MembersBenchmarkLanguage extends ProxyLanguage {

        @Override
        protected CallTarget parse(TruffleLanguage.ParsingRequest request) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
            return new MembersBenchmarkRootNode(languageInstance, source).getCallTarget();
        }

        static final class MembersBenchmarkRootNode extends RootNode {

            @Node.Child private BenchmarkNode child;
            @Node.Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

            MembersBenchmarkRootNode(TruffleLanguage<?> language, com.oracle.truffle.api.source.Source parsedSource) {
                super(language);
                String source = parsedSource.getCharacters().toString();
                Object polyglotBindings = LanguageContext.get(this).getEnv().getPolyglotBindings();
                Object object0;
                try {
                    object0 = InteropLibrary.getUncached().readMember(polyglotBindings, "object0");
                } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
                child = switch (source) {
                    case "Read" -> new InteropReadNode(object0);
                    case "Write" -> new InteropWriteNode(object0);
                    case "Execute" -> new InteropExecuteNode(object0);
                    case "Invoke" -> new InteropInvokeNode(object0);
                    case "InvokeOverloads" -> new InteropInvokeOverloadsNode(object0);
                    case "InvokeMegamorphic" -> new InteropInvokeMegamorphicNode(polyglotBindings);
                    case "EMPTY" -> new InteropEmptyNode(object0);
                    default -> throw CompilerDirectives.shouldNotReachHere(source);
                };
            }

            @Override
            public Object execute(VirtualFrame frame) {
                Object[] args = frame.getArguments();
                try {
                    child.run(interop, args);
                } catch (InteropException ex) {
                    throw CompilerDirectives.shouldNotReachHere(ex);
                }
                return 0;
            }

            abstract static class BenchmarkNode extends Node {

                abstract void run(InteropLibrary interop, Object... args) throws InteropException;
            }

            @CompilerControl(Mode.DONT_INLINE)
            static class InteropReadNode extends BenchmarkNode {

                private final Object object;

                InteropReadNode(Object object) {
                    this.object = object;
                }

                @Override
                void run(InteropLibrary interop, Object... args) throws InteropException {
                    for (int i = 0; i < REPEAT; i++) {
                        Object value = interop.readMember(object, "field");
                        CompilerDirectives.blackhole(value);
                    }
                }
            }

            @CompilerControl(Mode.DONT_INLINE)
            static class InteropWriteNode extends BenchmarkNode {

                private final Object object;

                InteropWriteNode(Object object) {
                    this.object = object;
                }

                @Override
                void run(InteropLibrary interop, Object... args) throws InteropException {
                    for (int i = 0; i < REPEAT; i++) {
                        interop.writeMember(object, "field", 0);
                    }
                }
            }

            @CompilerControl(Mode.DONT_INLINE)
            static class InteropExecuteNode extends BenchmarkNode {

                private final Object object;

                InteropExecuteNode(Object object) {
                    this.object = object;
                }

                @Override
                void run(InteropLibrary interop, Object... args) throws InteropException {
                    Object process = interop.readMember(object, "process");
                    for (int i = 0; i < REPEAT; i++) {
                        interop.execute(process, 0);
                    }
                }
            }

            @CompilerControl(Mode.DONT_INLINE)
            static class InteropInvokeNode extends BenchmarkNode {

                private final Object object;

                InteropInvokeNode(Object object) {
                    this.object = object;
                }

                @Override
                void run(InteropLibrary interop, Object... args) throws InteropException {
                    for (int i = 0; i < REPEAT; i++) {
                        interop.invokeMember(object, "process", 0);
                    }
                }
            }

            @CompilerControl(Mode.DONT_INLINE)
            static class InteropInvokeOverloadsNode extends BenchmarkNode {

                private final Object object;

                InteropInvokeOverloadsNode(Object object) {
                    this.object = object;
                }

                @Override
                void run(InteropLibrary interop, Object... args) throws InteropException {
                    for (int i = 0; i < REPEAT / NUM_OVERLOADS; i++) {
                        interop.invokeMember(object, "process", "");
                        interop.invokeMember(object, "process", i);
                        interop.invokeMember(object, "process", i, i);
                        interop.invokeMember(object, "process", i, i, i);
                        interop.invokeMember(object, "process", i, i, i, i);
                        interop.invokeMember(object, "process", i, i, i, i, i);
                    }
                }
            }

            @CompilerControl(Mode.DONT_INLINE)
            static class InteropInvokeMegamorphicNode extends BenchmarkNode {

                private final Object object0;
                private final Object object1;
                private final Object object2;
                private final Object object3;
                private final Object object4;
                private final Object object5;
                private final Object object6;
                private final Object object7;
                private final Object object8;
                private final Object object9;
                private final Object object10;
                private final Object object11;

                InteropInvokeMegamorphicNode(Object polyglotBindings) {
                    try {
                        object0 = InteropLibrary.getUncached().readMember(polyglotBindings, "object0");
                        object1 = InteropLibrary.getUncached().readMember(polyglotBindings, "object1");
                        object2 = InteropLibrary.getUncached().readMember(polyglotBindings, "object2");
                        object3 = InteropLibrary.getUncached().readMember(polyglotBindings, "object3");
                        object4 = InteropLibrary.getUncached().readMember(polyglotBindings, "object4");
                        object5 = InteropLibrary.getUncached().readMember(polyglotBindings, "object5");
                        object6 = InteropLibrary.getUncached().readMember(polyglotBindings, "object6");
                        object7 = InteropLibrary.getUncached().readMember(polyglotBindings, "object7");
                        object8 = InteropLibrary.getUncached().readMember(polyglotBindings, "object8");
                        object9 = InteropLibrary.getUncached().readMember(polyglotBindings, "object9");
                        object10 = InteropLibrary.getUncached().readMember(polyglotBindings, "object10");
                        object11 = InteropLibrary.getUncached().readMember(polyglotBindings, "object11");
                    } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                        throw CompilerDirectives.shouldNotReachHere(ex);
                    }
                }

                @Override
                void run(InteropLibrary interop, Object... args) throws InteropException {
                    for (int i = 0; i < REPEAT_MEGA; i++) {
                        interop.invokeMember(object0, "process", "");
                        interop.invokeMember(object1, "process", "");
                        interop.invokeMember(object2, "process", i);
                        interop.invokeMember(object3, "process", i);
                        interop.invokeMember(object4, "process", i, i);
                        interop.invokeMember(object5, "process", i, i);
                        interop.invokeMember(object6, "process", i, i, i);
                        interop.invokeMember(object7, "process", i, i, i);
                        interop.invokeMember(object8, "process", i, i, i, i);
                        interop.invokeMember(object9, "process", i, i, i, i);
                        interop.invokeMember(object10, "process", i, i, i, i, i);
                        interop.invokeMember(object11, "process", i, i, i, i, i);
                    }
                }
            }

            @CompilerControl(Mode.DONT_INLINE)
            static class InteropEmptyNode extends BenchmarkNode {

                private final Object object;

                InteropEmptyNode(Object object) {
                    this.object = object;
                }

                @Override
                void run(InteropLibrary interop, Object... args) {
                    for (int i = 0; i < REPEAT; i++) {
                        doEmpty(object);
                    }
                }

                private static void doEmpty(Object receiver) {
                    CompilerDirectives.blackhole(receiver);
                }
            }
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
