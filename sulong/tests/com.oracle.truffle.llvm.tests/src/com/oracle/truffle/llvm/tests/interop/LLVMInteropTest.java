/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.tests.interop.values.ArrayObject;
import com.oracle.truffle.llvm.tests.interop.values.BoxedIntValue;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

public class LLVMInteropTest {
    @Test
    public void test001() {
        try (Runner runner = new Runner("interop001")) {
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test002() {
        try (Runner runner = new Runner("interop002")) {
            runner.export(ProxyObject.fromMap(makeObjectA()), "foreign");
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test003() {
        try (Runner runner = new Runner("interop003")) {
            runner.export(ProxyObject.fromMap(makeObjectA()), "foreign");
            Assert.assertEquals(215, runner.run());
        }
    }

    @Test
    public void test004() {
        try (Runner runner = new Runner("interop004")) {
            Map<String, Object> a = makeObjectB();
            runner.export(ProxyObject.fromMap(a), "foreign");
            Assert.assertEquals(73, runner.run());
        }
    }

    @Test
    public void test005() {
        try (Runner runner = new Runner("interop005")) {
            Map<String, Object> a = makeObjectA();
            runner.export(ProxyObject.fromMap(a), "foreign");
            runner.run();

            Assert.assertEquals(2, ((Value) a.get("valueI")).asInt());
            Assert.assertEquals(3, ((Value) a.get("valueB")).asByte());
            Assert.assertEquals(4, ((Value) a.get("valueL")).asLong());
            Assert.assertEquals(5.5, ((Value) a.get("valueF")).asFloat(), 0.1);
            Assert.assertEquals(6.5, ((Value) a.get("valueD")).asDouble(), 0.1);
        }
    }

    @Test
    public void test006() {
        try (Runner runner = new Runner("interop006")) {
            Map<String, Object> a = makeObjectB();
            runner.export(ProxyObject.fromMap(a), "foreign");
            runner.run();

            Assert.assertEquals(1, ((int[]) a.get("valueI"))[0]);
            Assert.assertEquals(2, ((int[]) a.get("valueI"))[1]);

            Assert.assertEquals(3, ((long[]) a.get("valueL"))[0]);
            Assert.assertEquals(4, ((long[]) a.get("valueL"))[1]);

            Assert.assertEquals(5, ((byte[]) a.get("valueB"))[0]);
            Assert.assertEquals(6, ((byte[]) a.get("valueB"))[1]);

            Assert.assertEquals(7.5f, ((float[]) a.get("valueF"))[0], 0.1);
            Assert.assertEquals(8.5f, ((float[]) a.get("valueF"))[1], 0.1);

            Assert.assertEquals(9.5, ((double[]) a.get("valueD"))[0], 0.1);
            Assert.assertEquals(10.5, ((double[]) a.get("valueD"))[1], 0.1);
        }
    }

    @Test
    public void testInvoke() {
        Assume.assumeFalse("JavaInterop not supported", TruffleOptions.AOT);
        try (Runner runner = new Runner("invoke")) {
            ClassC a = new ClassC();
            runner.export(a, "foreign");
            Assert.assertEquals(36, runner.run());

            Assert.assertEquals(a.valueI, 4);
            Assert.assertEquals(a.valueB, 3);
            Assert.assertEquals(a.valueL, 7);
            Assert.assertEquals(a.valueF, 10, 0.1);
            Assert.assertEquals(a.valueD, 12, 0.1);
        }
    }

    @Test
    public void testReadExecute() {
        Assume.assumeFalse("JavaInterop not supported", TruffleOptions.AOT);
        try (Runner runner = new Runner("readExecute")) {
            ClassC a = new ClassC();
            runner.export(a, "foreign");
            Assert.assertEquals(36, runner.run());

            Assert.assertEquals(a.valueI, 4);
            Assert.assertEquals(a.valueB, 3);
            Assert.assertEquals(a.valueL, 7);
            Assert.assertEquals(a.valueF, 10, 0.1);
            Assert.assertEquals(a.valueD, 12, 0.1);
        }
    }

    @Test
    public void test008() {
        try (Runner runner = new Runner("interop008")) {
            runner.export(new ProxyExecutable() {

                @Override
                public Object execute(Value... t) {
                    return t[0].asByte() + t[1].asByte();
                }
            }, "foreign");
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test009() {
        try (Runner runner = new Runner("interop009")) {
            runner.export(new ProxyExecutable() {

                @Override
                public Object execute(Value... t) {
                    return t[0].asInt() + t[1].asInt();
                }
            }, "foreign");
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test010() {
        try (Runner runner = new Runner("interop010")) {
            runner.export(new ProxyExecutable() {

                @Override
                public Object execute(Value... t) {
                    return t[0].asLong() + t[1].asLong();
                }
            }, "foreign");
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test011() {
        try (Runner runner = new Runner("interop011")) {
            runner.export(new ProxyExecutable() {

                @Override
                public Object execute(Value... t) {
                    return t[0].asFloat() + t[1].asFloat();
                }
            }, "foreign");
            Assert.assertEquals(42.0, runner.run(), 0.1);
        }
    }

    @Test
    public void test012() {
        try (Runner runner = new Runner("interop012")) {
            runner.export(new ProxyExecutable() {

                @Override
                public Object execute(Value... t) {
                    Assert.assertEquals("argument count", 2, t.length);
                    return t[0].asDouble() + t[1].asDouble();
                }
            }, "foreign");
            Assert.assertEquals(42.0, runner.run(), 0.1);
        }
    }

    @Test
    public void test013() {
        try (Runner runner = new Runner("interop013")) {
            runner.export(new BoxedIntValue(42), "foreign");
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test014() {
        try (Runner runner = new Runner("interop014")) {
            runner.export(new BoxedIntValue(42), "foreign");
            Assert.assertEquals(42, runner.run(), 0.1);
        }
    }

    @Test
    public void test015() {
        try (Runner runner = new Runner("interop015")) {
            runner.export(new ProxyExecutable() {

                @Override
                public Object execute(Value... t) {
                    Assert.assertEquals("argument count", 2, t.length);
                    return t[0].asDouble() + t[1].asDouble();
                }
            }, "foreign");
            Assert.assertEquals(42, runner.run(), 0.1);
        }
    }

    @Test
    public void test016() {
        try (Runner runner = new Runner("interop016")) {
            runner.export(null, "foreign");
            Assert.assertEquals(42, runner.run(), 0.1);
        }
    }

    @Test
    public void test017() {
        try (Runner runner = new Runner("interop017")) {
            runner.export(new int[]{1, 2, 3}, "foreign");
            Assert.assertEquals(42, runner.run(), 0.1);
        }
    }

    @Test
    public void test018() {
        try (Runner runner = new Runner("interop018")) {
            runner.export(new int[]{1, 2, 3}, "foreign");
            Assert.assertEquals(3, runner.run());
        }
    }

    @Test
    public void test019() {
        try (Runner runner = new Runner("interop019")) {
            runner.export(new int[]{40, 41, 42, 43, 44}, "foreign");
            Assert.assertEquals(210, runner.run());
        }
    }

    @Test
    public void test020() {
        try (Runner runner = new Runner("interop020")) {
            int[] arr = new int[]{40, 41, 42, 43, 44};
            runner.export(arr, "foreign");
            runner.run();
            Assert.assertArrayEquals(new int[]{30, 31, 32, 33, 34}, arr);
        }
    }

    @Test
    public void test021() {
        try (Runner runner = new Runner("interop021")) {
            runner.export(new double[]{40, 41, 42, 43, 44}, "foreign");
            Assert.assertEquals(210, runner.run());
        }
    }

    @Test
    public void test022() {
        try (Runner runner = new Runner("interop022")) {
            double[] arr = new double[]{40, 41, 42, 43, 44};
            runner.export(arr, "foreign");
            runner.run();
            Assert.assertArrayEquals(new double[]{30, 31, 32, 33, 34}, arr, 0.1);
        }
    }

    @Test
    public void test023() {
        try (Runner runner = new Runner("interop023")) {
            Map<String, Object> a = makeObjectA();
            Map<String, Object> b = makeObjectA();
            runner.export(ProxyObject.fromMap(a), "foreign");
            runner.export(ProxyObject.fromMap(b), "foreign2");
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test024() {
        try (Runner runner = new Runner("interop024")) {
            Map<String, Object> a = makeObjectA();
            Map<String, Object> b = makeObjectA();
            b.put("valueI", 55);
            runner.export(ProxyObject.fromMap(a), "foreign");
            runner.export(ProxyObject.fromMap(b), "foreign2");
            Assert.assertEquals(55, runner.run());
        }
    }

    @Test
    public void test025() {
        try (Runner runner = new Runner("interop025")) {
            Map<String, Object> a = makeObjectA();
            Map<String, Object> b = makeObjectA();
            Map<String, Object> c = makeObjectA();
            b.put("valueI", 55);
            c.put("valueI", 66);
            runner.export(ProxyObject.fromMap(a), "foreign");
            runner.export(ProxyObject.fromMap(b), "foreign2");
            runner.export(ProxyObject.fromMap(c), "foreign3");
            Assert.assertEquals(66, runner.run());
        }
    }

    @Test
    public void test026() {
        try (Runner runner = new Runner("interop026")) {
            ReturnObject result = new ReturnObject();
            runner.export(result, "foo");
            Assert.assertEquals(14, runner.run());
            Assert.assertEquals("bar", result.storage);
        }
    }

    @Test
    public void test027() {
        try (Runner runner = new Runner("interop027")) {
            ReturnObject result = new ReturnObject();
            runner.export(result, "foo");
            Assert.assertEquals(14, runner.run());
            Assert.assertEquals("\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087\u0088\u0089\u008a\u008b\u008c\u008d\u008e\u008f" +
                            "\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097\u0098\u0099\u009a\u009b\u009c\u009d\u009e\u009f" +
                            "\u00a0\u00a1\u00a2\u00a3\u00a4\u00a5\u00a6\u00a7\u00a8\u00a9\u00aa\u00ab\u00ac\u00ad\u00ae\u00af" +
                            "\u00b0\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6\u00b7\u00b8\u00b9\u00ba\u00bb\u00bc\u00bd\u00be\u00bf" +
                            "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf" +
                            "\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df" +
                            "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef" +
                            "\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff",
                            result.storage);
        }
    }

    @Test
    public void test028() {
        try (Runner runner = new Runner("interop028")) {
            ReturnObject result = new ReturnObject();
            runner.export(result, "foo");
            Assert.assertEquals(72, runner.run());
            Assert.assertEquals("foo\u0000 bar\u0080 ", result.storage);
        }
    }

    // implicit interop
    // structs not yet implemented
    @Test
    public void test030() {
        try (Runner runner = new Runner("interop030")) {
            runner.run();
            Value get = runner.findGlobalSymbol("getValueI");
            int result = get.execute(ProxyObject.fromMap(makeObjectA())).asInt();
            Assert.assertEquals(42, result);
        }
    }

    @Test
    @Ignore
    public void test031() {
        try (Runner runner = new Runner("interop031")) {
            runner.run();
            Value apply = runner.findGlobalSymbol("complexAdd");

            ComplexNumber a = new ComplexNumber(32, 10);
            ComplexNumber b = new ComplexNumber(10, 32);

            apply.execute(a, b);

            Assert.assertEquals(42.0, a.real, 0.1);
            Assert.assertEquals(42.0, a.imaginary, 0.1);
        }
    }

    // arrays: foreign array to llvm
    @Test
    public void test032() {
        try (Runner runner = new Runner("interop032")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            int[] a = new int[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    @Test
    public void test033() {
        try (Runner runner = new Runner("interop033")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            short[] a = new short[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    @Test
    public void test034() {
        try (Runner runner = new Runner("interop034")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            byte[] a = new byte[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    @Test
    public void test035() {
        try (Runner runner = new Runner("interop035")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            long[] a = new long[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    @Test
    public void test036() {
        try (Runner runner = new Runner("interop036")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            float[] a = new float[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    @Test
    public void test037() {
        try (Runner runner = new Runner("interop037")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            double[] a = new double[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    // foreign array with different type
    @Test
    public void test038() {
        try (Runner runner = new Runner("interop038")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            long[] a = new long[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    @Test
    public void test039() {
        try (Runner runner = new Runner("interop039")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            byte[] a = new byte[]{1, 2, 3, 4, 5};
            int result = get.execute(a, 2).asInt();
            Assert.assertEquals(3, result);
        }
    }

    @Test
    @Ignore(value = "test semantics not clear")
    public void test040() {
        try (Runner runner = new Runner("interop040")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            Value value = get.execute();
            Assert.assertEquals(16, value.getArrayElement(4).asInt());
        }
    }

    @Test
    @Ignore(value = "test semantics not clear")
    public void test041() {
        try (Runner runner = new Runner("interop041")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            Value getval = runner.findGlobalSymbol("getval");
            get.execute().setArrayElement(3, 9);
            int value = getval.execute(3).asInt();
            Assert.assertEquals(9, value);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test042() {
        try (Runner runner = new Runner("interop042")) {
            runner.run();
            Value get = runner.findGlobalSymbol("get");
            get.execute().getArraySize();
        }
    }

    @Test
    public void test043() {
        try (Runner runner = new Runner("interop043")) {
            runner.export(ProxyObject.fromMap(makeObjectA()), "foreign");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test044() {
        try (Runner runner = new Runner("interop044")) {
            runner.export(new Object(), "a");
            runner.export(14, "b");
            runner.export(14.5, "c");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test045a() {
        try (Runner runner = new Runner("interop045")) {
            runner.export(14, "a");
            runner.export(15, "b");
            Assert.assertEquals(1, runner.run());
        }
    }

    @Test
    public void test046a() {
        try (Runner runner = new Runner("interop046")) {
            runner.export(14, "a");
            runner.export(14, "b");
            Assert.assertEquals(1, runner.run());
        }
    }

    @Test
    public void test046b() {
        try (Runner runner = new Runner("interop046")) {
            runner.export(14, "a");
            runner.export(15, "b");
            Assert.assertEquals(1, runner.run());
        }
    }

    @Test
    public void test047a() {
        try (Runner runner = new Runner("interop047")) {
            runner.export(14, "a");
            runner.export(15, "b");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test048a() {
        try (Runner runner = new Runner("interop048")) {
            runner.export(14, "a");
            runner.export(15, "b");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test048b() {
        try (Runner runner = new Runner("interop048")) {
            runner.export(14, "a");
            runner.export(14, "b");
            Assert.assertEquals(1, runner.run());
        }
    }

    @Test
    public void test049a() {
        try (Runner runner = new Runner("interop049")) {
            runner.export(14, "a");
            runner.export(14, "b");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test049b() {
        try (Runner runner = new Runner("interop049")) {
            Object object = new Object();
            runner.export(object, "a");
            runner.export(object, "b");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test050a() {
        try (Runner runner = new Runner("interop050")) {
            runner.export(14, "a");
            runner.export(14, "b");
            Assert.assertEquals(1, runner.run());
        }
    }

    @Test
    public void test050b() {
        try (Runner runner = new Runner("interop050")) {
            Object object = new Object();
            runner.export(object, "a");
            runner.export(object, "b");
            Assert.assertEquals(1, runner.run());
        }
    }

    public class ReturnObject implements ProxyExecutable {

        Object storage;

        @Override
        public Object execute(Value... t) {
            Assert.assertEquals("argument count", 1, t.length);
            if (t[0].isHostObject()) {
                storage = t[0].asHostObject();
            } else if (t[0].isString()) {
                storage = t[0].asString();
            } else {
                Assert.fail("unexpected value type");
            }
            return null;
        }
    }

    @Test
    public void test051a() {
        try (Runner runner = new Runner("interop051")) {
            testGlobal(runner);
        }
    }

    @Test
    public void test052a() {
        try (Runner runner = new Runner("interop052")) {
            testGlobal(runner);
        }
    }

    @Test
    public void test053a() {
        try (Runner runner = new Runner("interop053")) {
            testGlobal(runner);
        }
    }

    @Test
    public void test054a() {
        try (Runner runner = new Runner("interop054")) {
            testGlobal(runner);
        }
    }

    @Test
    public void test055a() {
        try (Runner runner = new Runner("interop055")) {
            testGlobal(runner);
        }
    }

    @Test
    public void test056a() {
        try (Runner runner = new Runner("interop056")) {
            testGlobal(runner);
        }
    }

    private void testGlobal(Runner runner) {
        ReturnObject returnObject = new ReturnObject();
        Object original = new Object();
        runner.export(original, "object");
        runner.export(returnObject, "returnObject");
        runner.run();
        Assert.assertSame(original, returnObject.storage);
    }

    @Test
    public void test057() {
        try (Runner runner = new Runner("interop057")) {
            Map<String, Object> a = new HashMap<>();
            a.put("a", 0);
            a.put("b", 1);
            runner.export(ProxyObject.fromMap(a), "foreign");
            Assert.assertEquals(0, runner.run());
            Assert.assertEquals(101, ((Value) a.get("a")).asInt());
            Assert.assertEquals(102, ((Value) a.get("b")).asInt());
        }
    }

    @Test
    public void test058() {
        try (Runner runner = new Runner("interop058")) {
            Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            runner.export(a, "foreign");
            Assert.assertEquals(0, runner.run());
            Assert.assertEquals(101, a[0]);
            Assert.assertEquals(102, a[1]);
        }
    }

    @Ignore
    @Test
    public void test059() {
        try (Runner runner = new Runner("interop059")) {
            Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            runner.export(a, "foreign");
            Assert.assertEquals(0, runner.run());
            Assert.assertEquals(101, ((Value) a[0]).asNativePointer());
            Assert.assertEquals(102, ((Value) a[1]).asNativePointer());
        }
    }

    @Test
    public void testForeignImport() {
        try (Runner runner = new Runner("foreignImport")) {
            Map<String, Object> a = new HashMap<>();
            a.put("a", 0);
            a.put("b", 1);
            runner.export(ProxyObject.fromMap(a), "foreign");
            Assert.assertEquals(0, runner.run());
            long a0 = ((Value) a.get("a")).asNativePointer();
            long a1 = ((Value) a.get("b")).asNativePointer();
            Assert.assertEquals(101, a0);
            Assert.assertEquals(102, a1);

            Map<String, Object> b = new HashMap<>();
            b.put("a", (short) 3);
            b.put("b", (short) 4);
            runner.export(ProxyObject.fromMap(b), "foreign");
            Assert.assertEquals(103, runner.run());
            short b0 = Value.asValue(b.get("a")).asShort();
            short b1 = Value.asValue(b.get("b")).asShort();
            Assert.assertEquals((short) 3, b0);
            Assert.assertEquals((short) 4, b1);
        }
    }

    @Test
    public void test061() {
        try (Runner runner = new Runner("interop061")) {
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test062() {
        try (Runner runner = new Runner("interop062")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test063() {
        try (Runner runner = new Runner("interop063")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test064() {
        try (Runner runner = new Runner("interop064")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test(expected = PolyglotException.class)
    public void test065() {
        try (Runner runner = new Runner("interop065")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test(expected = PolyglotException.class)
    public void test066() throws Throwable {
        try (Runner runner = new Runner("interop066")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test067() {
        try (Runner runner = new Runner("interop067")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void testBoxedboolean() {
        try (Runner runner = new Runner("interop_conditionalWithBoxedBoolean")) {
            runner.export(true, "boxed_true");
            runner.export(false, "boxed_false");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void testUnboxedboolean() {
        try (Runner runner = new Runner("interop_conditionalWithUnboxedBoolean")) {
            runner.export(true, "boxed_true");
            runner.export(false, "boxed_false");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test069() {
        try (Runner runner = new Runner("interop069")) {
            runner.export(42, "a");
            runner.run();
            try {
                Value globalSymbol = runner.findGlobalSymbol("registered_tagged_address");
                Object result = globalSymbol.execute().asInt();
                Assert.assertTrue(result instanceof Integer && (int) result == 42);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void test070() {
        try (Runner runner = new Runner("interop070")) {
            runner.run();
            try {
                Value pointer = runner.findGlobalSymbol("returnPointerToGlobal").execute();
                runner.findGlobalSymbol("setPointer").execute(pointer, 42);
                Value value = runner.findGlobalSymbol("returnGlobal");
                Object result = value.execute().asInt();
                Assert.assertTrue(result instanceof Integer && (int) result == 42);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void test071() {
        try (Runner runner = new Runner("interop071")) {
            runner.run();
            try {
                Object obj = new Object();
                Value pointer = runner.findGlobalSymbol("returnPointerToGlobal").execute();
                runner.findGlobalSymbol("setPointer").execute(pointer, obj);
                Value value = runner.findGlobalSymbol("returnGlobal");
                Object result = value.execute().asHostObject();
                Assert.assertTrue(result == obj);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void test072() {
        try (Runner runner = new Runner("interop072")) {
            runner.run();
            try {
                Object obj = new Object();
                Value pointer = runner.findGlobalSymbol("returnPointerToGlobal");
                Object pointerTruffleObject = pointer.execute();
                Value setter = runner.findGlobalSymbol("setter");
                setter.execute(pointerTruffleObject, obj);

                Value value = runner.findGlobalSymbol("returnGlobal");
                Object result = value.execute().asHostObject();
                Assert.assertTrue(result == obj);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void test072a() {
        try (Runner runner = new Runner("interop072")) {
            runner.run();
            try {
                Value pointer = runner.findGlobalSymbol("returnPointerToGlobal");
                Object pointerTruffleObject = pointer.execute();

                Value setter = runner.findGlobalSymbol("setter");
                setter.execute(pointerTruffleObject, 42);

                Value value = runner.findGlobalSymbol("returnGlobal");
                Object result = value.execute().asInt();
                Assert.assertTrue(result instanceof Integer && (int) result == 42);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void test072b() {
        try (Runner runner = new Runner("interop072")) {
            runner.run();
            try {
                Value pointer = runner.findGlobalSymbol("returnPointerToGlobal");
                Object pointerTruffleObject = pointer.execute();

                Value setter = runner.findGlobalSymbol("setter");
                setter.execute(pointerTruffleObject, 42);

                Value value = runner.findGlobalSymbol("returnGlobal");
                int result = value.execute().asInt();
                Assert.assertTrue(result == 42);

                Object obj = new Object();
                setter.execute(pointerTruffleObject, obj);
                Object r = value.execute().asHostObject();
                Assert.assertTrue(r == obj);

            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Test
    public void test073() {
        try (Runner runner = new Runner("interop073")) {
            Assert.assertEquals(42, runner.run());
        }
    }

    @Test
    public void test074() {
        try (Runner runner = new Runner("interop074")) {
            testGlobal(runner);
        }
    }

    @Test
    public void test076() {
        try (Runner runner = new Runner("interop076")) {
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void test077() {
        try (Runner runner = new Runner("interop077")) {
            final String testString = "this is a test";
            runner.export((ProxyExecutable) (Value... t) -> testString, "getstring");
            Assert.assertEquals(testString.length(), runner.run());
        }
    }

    @Test
    public void testTypeCheckNative() {
        try (Runner runner = new Runner("typeCheck")) {
            runner.load();
            int ret = runner.findGlobalSymbol("check_types_nativeptr").execute().asInt();
            Assert.assertEquals(0, ret);
        }
    }

    @Test
    public void testFitsInNative() {
        try (Runner runner = new Runner("fitsIn")) {
            runner.load();
            int ret = runner.findGlobalSymbol("test_fits_in_nativeptr").execute().asInt();
            Assert.assertEquals(0, ret);
        }
    }

    @Test
    public void testIsHandle() {
        try (Runner runner = new Runner("isHandle")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void testReleaseHandle() {
        try (Runner runner = new Runner("releaseHandle")) {
            Object a = new Object();
            runner.export(a, "object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class ForeignObject implements TruffleObject {
        protected int foo;

        ForeignObject(int i) {
            this.foo = i;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            return "foo".equals(member);
        }

        @ExportMessage
        int readMember(String member) {
            Assert.assertEquals("foo", member);
            return foo;
        }

        @ExportMessage
        boolean isMemberModifiable(String member) {
            return "foo".equals(member);
        }

        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage(limit = "3")
        void writeMember(String member, Object value,
                        @CachedLibrary("value") InteropLibrary numbers) throws UnsupportedTypeException {
            Assert.assertEquals("foo", member);
            try {
                foo = numbers.asInt(value) * 2;
            } catch (InteropException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new ArrayObject("foo");
        }

        static TruffleObject getTestToNative() {
            return (TruffleObject) LLVMLanguage.getLLVMContextReference().get().getEnv().importSymbol("test_to_native");
        }

        @ExportMessage
        void toNative(
                        @Cached(value = "getTestToNative()", allowUncached = true) TruffleObject testToNative,
                        @CachedLibrary("testToNative") InteropLibrary interop) {
            try {
                interop.execute(testToNative, this);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                Assert.fail("TO_NATIVE should have created a handle");
            }
        }
    }

    @Test
    public void testRegisterHandle() {
        try (Runner runner = new Runner("registerHandle")) {
            runner.export(new ForeignObject(1), "global_object");
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void testStrlen() {
        try (Runner runner = new Runner("strlen")) {
            runner.run();
            Value strlenFunction = runner.findGlobalSymbol("func");
            Value nullString = strlenFunction.execute(new char[]{});
            Value a = strlenFunction.execute(new char[]{'a'});
            Value abcd = strlenFunction.execute(new char[]{'a', 'b', 'c', 'd'});
            Value abcdWithTerminator = strlenFunction.execute(new char[]{'a', 'b', 'c', 'd', '\0'});
            Assert.assertEquals(0, nullString.asInt());
            Assert.assertEquals(1, a.asInt());
            Assert.assertEquals(4, abcd.asInt());
            Assert.assertEquals(5, abcdWithTerminator.asInt());
        }
    }

    @Test
    public void testStrcmp() {
        try (Runner runner = new Runner("strcmp")) {
            runner.run();
            Value strcmpFunction = runner.findGlobalSymbol("func");
            Value test1 = strcmpFunction.execute(new char[]{}, new char[]{});
            Value test2 = strcmpFunction.execute(new char[]{'a'}, new char[]{});
            Value test3 = strcmpFunction.execute(new char[]{}, new char[]{'a'});
            Value test4 = strcmpFunction.execute(new char[]{'a'}, new char[]{'d'});
            Value test5 = strcmpFunction.execute(new char[]{'d'}, new char[]{'a'});
            Value test6 = strcmpFunction.execute(new char[]{'d'}, new char[]{'d'});
            Value test7 = strcmpFunction.execute(new char[]{'a', 'b', 'c'}, new char[]{'a', 'b', 'c', 'd'});
            Value test8 = strcmpFunction.execute(new char[]{'a', 'b', 'c', 'd'}, new char[]{'a', 'b', 'c'});
            Value test9 = strcmpFunction.execute(new char[]{'A', 'B', 'C', 'D'}, new char[]{'a', 'b', 'c', 'd'});
            Value compareUpToZero = strcmpFunction.execute(new char[]{'A', 'B', '\0', 'D'}, new char[]{'A', 'B', '\0'});
            Assert.assertEquals(0, test1.asInt());
            Assert.assertEquals(97, test2.asInt());
            Assert.assertEquals(-97, test3.asInt());
            Assert.assertEquals(-3, test4.asInt());
            Assert.assertEquals(3, test5.asInt());
            Assert.assertEquals(0, test6.asInt());
            Assert.assertEquals(-100, test7.asInt());
            Assert.assertEquals(100, test8.asInt());
            Assert.assertEquals(-32, test9.asInt());
            Assert.assertEquals(0, compareUpToZero.asInt());
            Value strcmpWithNativeFunction = runner.findGlobalSymbol("compare_with_native");
            Value test10 = strcmpWithNativeFunction.execute(new char[]{});
            Value test11 = strcmpWithNativeFunction.execute(new char[]{'f', 'o', 'o'});
            Value test12 = strcmpWithNativeFunction.execute(new char[]{'e'});
            Value test13 = strcmpWithNativeFunction.execute(new char[]{'g'});
            Assert.assertEquals('f', test10.asInt());
            Assert.assertEquals(0, test11.asInt());
            Assert.assertEquals(1, test12.asInt());
            Assert.assertEquals(-1, test13.asInt());
        }
    }

    @Test
    public void testHandleFromNativeCallback() {
        try (Runner runner = new Runner("handleFromNativeCallback")) {
            runner.run();
            Value testHandleFromNativeCallback = runner.findGlobalSymbol("testHandleFromNativeCallback");
            Value ret = testHandleFromNativeCallback.execute(ProxyObject.fromMap(makeObjectA()));
            Assert.assertEquals(42, ret.asInt());
        }
    }

    @Test
    public void testAutoDerefHandle() {
        try (Runner runner = new Runner("autoDerefHandle")) {
            runner.run();
            Value testHandleFromNativeCallback = runner.findGlobalSymbol("testAutoDerefHandle");
            ProxyExecutable proxyExecutable = new ProxyExecutable() {
                @Override
                public Object execute(Value... t) {
                    return 13;
                }
            };

            Object intArray = runner.context.asValue(new int[]{7});
            Value ret = testHandleFromNativeCallback.execute(proxyExecutable, intArray);
            Assert.assertEquals(33, ret.asInt());
        }
    }

    @Test
    public void testPointerThroughNativeCallback() {
        try (Runner runner = new Runner("pointerThroughNativeCallback")) {
            int result = runner.run();
            Assert.assertEquals(42, result);
        }
    }

    @Test
    public void testManagedMallocMemSet() {
        try (Runner runner = new Runner("managedMallocMemset")) {
            Assert.assertEquals(0, runner.run());
        }
    }

    @Test
    public void testVirtualMallocArray() {
        try (Runner runner = new Runner("virtualMallocArray")) {
            runner.load();
            Value test = runner.findGlobalSymbol("test");
            Assert.assertEquals(test.execute().asInt(), 42);
        }
    }

    @Test
    public void testVirtualMallocArray2() {
        try (Runner runner = new Runner("virtualMallocArray2")) {
            runner.load();
            Value test = runner.findGlobalSymbol("test");
            Assert.assertEquals(test.execute().asInt(), 42);
        }
    }

    @Test
    public void testVirtualMallocArrayPointer() {
        try (Runner runner = new Runner("virtualMallocArrayPointer")) {
            runner.load();
            Value test1 = runner.findGlobalSymbol("test1");
            Value test2 = runner.findGlobalSymbol("test2");
            Assert.assertEquals(test1.execute().asInt(), 42);
            Assert.assertEquals(test2.execute().asInt(), 43);
        }
    }

    @Test
    public void testVirtualMallocGlobal() {
        try (Runner runner = new Runner("virtualMallocGlobal")) {
            runner.load();
            Value test = runner.findGlobalSymbol("test");
            Assert.assertEquals(test.execute().asLong(), 42);
        }
    }

    @Test
    public void testVirtualMallocGlobaAssignl() {
        try (Runner runner = new Runner("virtualMallocGlobalAssign")) {
            runner.load();
            Value test = runner.findGlobalSymbol("test");
            Assert.assertEquals(test.execute().asLong(), 42);
        }
    }

    @Test
    public void testVirtualMallocObject() {
        try (Runner runner = new Runner("virtualMallocObject")) {
            runner.load();
            Value setA = runner.findGlobalSymbol("testGetA");
            Value setB = runner.findGlobalSymbol("testGetB");
            Value setC = runner.findGlobalSymbol("testGetC");
            Value setD = runner.findGlobalSymbol("testGetD");
            Value setE = runner.findGlobalSymbol("testGetE");
            Value setF = runner.findGlobalSymbol("testGetF");
            Assert.assertEquals(setA.execute().asLong(), 42);
            Assert.assertEquals(setB.execute().asDouble(), 13.4, 0.1);
            Assert.assertEquals(setC.execute().asFloat(), 13.5f, 0.1);
            Assert.assertEquals(setD.execute().asInt(), 56);
            Assert.assertEquals(setE.execute().asByte(), 5);
            Assert.assertEquals(setF.execute().asBoolean(), true);
        }
    }

    @Test
    public void testVirtualMallocObjectCopy() {
        try (Runner runner = new Runner("virtualMallocObjectCopy")) {
            runner.load();
            Value setA = runner.findGlobalSymbol("testGetA");
            Value setB = runner.findGlobalSymbol("testGetB");
            Value setC = runner.findGlobalSymbol("testGetC");
            Value setD = runner.findGlobalSymbol("testGetD");
            Value setE = runner.findGlobalSymbol("testGetE");
            Value setF = runner.findGlobalSymbol("testGetF");
            Assert.assertEquals(setA.execute().asLong(), 42);
            Assert.assertEquals(setB.execute().asDouble(), 13.4, 0.1);
            Assert.assertEquals(setC.execute().asFloat(), 13.5f, 0.1);
            Assert.assertEquals(setD.execute().asInt(), 56);
            Assert.assertEquals(setE.execute().asByte(), 5);
            Assert.assertEquals(setF.execute().asBoolean(), true);
        }
    }

    @Test
    public void testVirtualMallocCompare1() {
        try (Runner runner = new Runner("virtualMallocCompare1")) {
            runner.load();
            Value test1 = runner.findGlobalSymbol("test1");
            Value test2 = runner.findGlobalSymbol("test2");
            Value test3 = runner.findGlobalSymbol("test3");
            Value test4 = runner.findGlobalSymbol("test4");
            Value test5 = runner.findGlobalSymbol("test5");
            Value test6 = runner.findGlobalSymbol("test6");
            Assert.assertTrue(test1.execute().asInt() == 0);
            Assert.assertTrue(test2.execute().asInt() != 0);
            Assert.assertTrue(test3.execute().asInt() == 0);
            Assert.assertTrue(test4.execute().asInt() != 0);
            Assert.assertTrue(test5.execute().asInt() == 0);
            Assert.assertTrue(test6.execute().asInt() != 0);
        }
    }

    @Test
    public void testConstruct001() {
        final StringBuilder buf;
        try (Runner runner = new Runner("construct001")) {
            buf = new StringBuilder();
            runner.export(new ProxyExecutable() {
                @Override
                public Object execute(Value... t) {
                    Assert.assertEquals("argument count", 1, t.length);
                    if (t[0].isString()) {
                        buf.append(t[0].asString());
                    } else {
                        Assert.fail("unexpected value type");
                    }
                    return 0;
                }
            }, "callback");
            runner.load();
            Assert.assertEquals("construct\n", buf.toString());
        }
        Assert.assertEquals("construct\ndestruct\n", buf.toString());
    }

    @Test
    public void testScaleVector() {
        try (Runner runner = new Runner("scaleVector")) {
            runner.load();
            Value fn = runner.findGlobalSymbol("scale_vector");

            ProxyArray proxy = ProxyArray.fromArray(1.0, 2.0, 3.0, 4.0, 5.0);
            fn.execute(proxy, proxy.getSize(), 0.1);

            for (int i = 0; i < proxy.getSize(); i++) {
                double expected = 0.1 * (i + 1);
                Value actual = (Value) proxy.get(i);
                Assert.assertEquals("index " + i, expected, actual.asDouble(), 0.0001);
            }
        }
    }

    @Test
    public void testConstruct002() {
        final StringBuilder buf;
        try (Runner runner = new Runner("construct002")) {
            buf = new StringBuilder();
            runner.export(new ProxyExecutable() {
                @Override
                public Object execute(Value... t) {
                    Assert.assertEquals("argument count", 1, t.length);
                    if (t[0].isString()) {
                        buf.append(t[0].asString());
                    } else {
                        Assert.fail("unexpected value type");
                    }
                    return 0;
                }
            }, "callback");
            runner.load();
            Assert.assertEquals("construct\n", buf.toString());
        }
        Assert.assertEquals("construct\natexit\ndestruct\n", buf.toString());
    }

    private static Map<String, Object> makeObjectA() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("valueBool", true);
        values.put("valueB", (byte) 40);
        values.put("valueC", (char) 41);
        values.put("valueI", 42);
        values.put("valueL", 43L);
        values.put("valueF", 44.5F);
        values.put("valueD", 45.5);
        return values;
    }

    private static Map<String, Object> makeObjectB() {
        HashMap<String, Object> values = new HashMap<>();
        values.put("valueB", new byte[]{1, 2});
        values.put("valueI", new int[]{5, 6});
        values.put("valueL", new long[]{7, 8});
        values.put("valueF", new float[]{9.5F, 10.5F});
        values.put("valueD", new double[]{11.5, 12.5});
        return values;
    }

    public static final class ClassC {
        public boolean valueBool = true;
        public byte valueB = 1;
        public char valueC = 2;
        public int valueI = 3;
        public long valueL = 4;
        public float valueF = 5.5F;
        public double valueD = 6.5F;

        public boolean addBool(boolean b) {
            valueBool ^= b;
            return valueBool;
        }

        public byte addB(byte b) {
            valueB += b;
            return valueB;
        }

        public char addC(char c) {
            valueC += c;
            return valueC;
        }

        public int addI(int i) {
            valueI += i;
            return valueI;
        }

        public long addL(long l) {
            valueL += l;
            return valueL;
        }

        public float addF(float f) {
            valueF += f;
            return valueF;
        }

        public double addD(double d) {
            valueD += d;
            return valueD;
        }
    }

    class ComplexNumber {
        public double real;
        public double imaginary;

        ComplexNumber(double real, double imaginary) {
            this.real = real;
            this.imaginary = imaginary;
        }
    }

    private static final Path TEST_DIR = new File(TestOptions.TEST_SUITE_PATH, "interop").toPath();
    public static final String FILENAME = "O0_MEM2REG." + NFIContextExtension.getNativeLibrarySuffix();

    protected static Map<String, String> getSulongTestLibContextOptions() {
        Map<String, String> map = new HashMap<>();
        String lib = System.getProperty("test.sulongtest.lib.path");
        map.put("llvm.libraryPath", lib);
        return map;
    }

    private static final class Runner implements AutoCloseable {
        private final String testName;
        private final Context context;

        private Value library;

        Runner(String testName) {
            this(testName, getSulongTestLibContextOptions());
        }

        Runner(String testName, Map<String, String> options) {
            this.testName = testName;
            this.context = Context.newBuilder().options(options).allowAllAccess(true).build();
            this.library = null;
        }

        public Value findGlobalSymbol(String string) {
            return library.getMember(string);
        }

        void export(Object foreignObject, String name) {
            context.getPolyglotBindings().putMember(name, foreignObject);
        }

        @Override
        public void close() {
            context.close();
        }

        Value load() {
            if (library == null) {
                try {
                    File file = new File(TEST_DIR.toFile(), testName + "/" + FILENAME);
                    Source source = Source.newBuilder("llvm", file).build();
                    library = context.eval(source);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return library;
        }

        int run() {
            return load().execute().asInt();
        }
    }
}
