/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark designed to measure the guest to host compiled performance for polyglot proxies.
 * Interpreter-only numbers are not useful for this benchmark as it uses the ReflectionLibrary to
 * call.
 */
@Warmup(iterations = 5, time = 1)
public class HostProxyBenchmark extends TruffleBenchmark {

    public static final String TEST_LANGUAGE = HostProxyBenchmarkLanguage.ID;
    public static final int INNER_LOOP = 100;

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void arrayRead(ProxyArrayState state) {
        state.arrayRead.executeVoid();
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void arrayWrite(ProxyArrayState state) {
        state.arrayWrite.executeVoid();
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void arrayGetSize(ProxyArrayState state) {
        state.arrayGetSize.executeVoid();
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void executableExecute(ProxyArrayState state) {
        state.executableExecute.executeVoid();
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void objectPutMember(ProxyArrayState state) {
        state.objectPutMember.executeVoid();
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void objectHasMember(ProxyArrayState state) {
        state.objectHasMember.executeVoid();
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void objectGetKeys(ProxyArrayState state) {
        state.objectGetMemberKeys.executeVoid();
    }

    @Benchmark
    @OperationsPerInvocation(INNER_LOOP)
    public void objectGetMember(ProxyArrayState state) {
        state.objectGetMember.executeVoid();
    }

    static final class BenchmarkProxyObject implements ProxyObject {
        Object foo = "bar";
        Object bar = "baz";

        public void putMember(String key, Value value) {
            switch (key) {
                case "foo":
                    this.foo = value;
                    return;
                case "bar":
                    this.bar = value;
                    return;
            }
            throw new UnsupportedOperationException();
        }

        public boolean hasMember(String key) {
            switch (key) {
                case "foo":
                case "bar":
                    return true;
            }
            return false;
        }

        public Object getMemberKeys() {
            return new ProxyArray() {
                private static final Object[] KEYS = new Object[]{"foo", "bar"};

                public void set(long index, Value value) {
                    throw new UnsupportedOperationException();
                }

                public long getSize() {
                    return KEYS.length;
                }

                public Object get(long index) {
                    if (index < 0 || index > Integer.MAX_VALUE) {
                        throw new ArrayIndexOutOfBoundsException();
                    }
                    return KEYS[(int) index];
                }

            };
        }

        public Object getMember(String key) {
            switch (key) {
                case "foo":
                    return this.foo;
                case "bar":
                    return this.bar;
            }
            throw new UnsupportedOperationException();
        }

    }

    static final class BenchmarkProxyArray implements ProxyArray {

        private final Object[] values;

        BenchmarkProxyArray(Object[] values) {
            this.values = values;
        }

        public Object get(long index) {
            int intIndex = coerceIndex(index);
            return values[intIndex];
        }

        public void set(long index, Value value) {
            int intIndex = coerceIndex(index);
            values[intIndex] = value;
        }

        private static int coerceIndex(long index) {
            int intIndex = (int) index;
            if (intIndex != index || intIndex < 0) {
                throw new ArrayIndexOutOfBoundsException("invalid index.");
            }
            return intIndex;
        }

        public long getSize() {
            return values.length;
        }
    }

    @State(Scope.Benchmark)
    public static class ProxyArrayState {
        final Context context;
        final Engine engine;

        final Value arrayRead;
        final Value arrayWrite;
        final Value arrayGetSize;

        final Value executableExecute;

        final Value objectPutMember;
        final Value objectHasMember;
        final Value objectGetMemberKeys;
        final Value objectGetMember;

        public ProxyArrayState() {
            engine = Engine.newBuilder(TEST_LANGUAGE).allowExperimentalOptions(true).build();
            context = Context.newBuilder(TEST_LANGUAGE).engine(engine).allowExperimentalOptions(true).build();
            context.enter();
            Value bind = context.eval(HostProxyBenchmarkLanguage.ID, "");
            ProxyArray proxyArray = new BenchmarkProxyArray(new Object[]{"0", "42", "1"});
            this.arrayRead = bind.execute(proxyArray, "readArrayElement", new Object[]{1L});
            this.arrayWrite = bind.execute(proxyArray, "writeArrayElement", new Object[]{1L, "42"});
            this.arrayGetSize = bind.execute(proxyArray, "getArraySize", new Object[]{});

            ProxyExecutable executable = (arguments) -> arguments[1];
            this.executableExecute = bind.execute(executable, "execute", new Object[]{new Object[]{1L, "42"}});
            ProxyObject proxyObject = new BenchmarkProxyObject();

            this.objectPutMember = bind.execute(proxyObject, "writeMember", new Object[]{"foo", "bar"});
            this.objectHasMember = bind.execute(proxyObject, "isMemberReadable", new Object[]{"bar"});
            this.objectGetMemberKeys = bind.execute(proxyObject, "getMembers", new Object[]{false});
            this.objectGetMember = bind.execute(proxyObject, "readMember", new Object[]{"bar"});
        }

        @TearDown
        public void tearDown() {
            context.close();
        }
    }

}
