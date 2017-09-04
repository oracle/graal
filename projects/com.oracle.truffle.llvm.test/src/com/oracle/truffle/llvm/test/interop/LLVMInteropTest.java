/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test.interop;

import com.oracle.truffle.api.TruffleOptions;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.llvm.test.options.TestOptions;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.Assume;

@SuppressWarnings({"static-method"})
public final class LLVMInteropTest {
    @Test
    public void test001() {
        Runner runner = new Runner("interop001");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test002() {
        Runner runner = new Runner("interop002");
        runner.export(makeObjectA(), "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test003() {
        Runner runner = new Runner("interop003");
        runner.export(makeObjectA(), "foreign");
        Assert.assertEquals(215, runner.run());
    }

    @Test
    public void test004() {
        Runner runner = new Runner("interop004");
        Map<String, Object> a = makeObjectB();
        runner.export(a, "foreign");
        Assert.assertEquals(73, runner.run());
    }

    @Test
    public void test005() {
        Runner runner = new Runner("interop005");
        Map<String, Object> a = makeObjectA();
        runner.export(a, "foreign");
        runner.run();

        Assert.assertEquals(false, (boolean) a.get("valueBool"));
        Assert.assertEquals(2, (int) a.get("valueI"));
        Assert.assertEquals(3, (byte) a.get("valueB"));
        Assert.assertEquals(4, (long) a.get("valueL"));
        Assert.assertEquals(5.5, (float) a.get("valueF"), 0.1);
        Assert.assertEquals(6.5, (double) a.get("valueD"), 0.1);
    }

    @Test
    public void test006() {
        Runner runner = new Runner("interop006");
        Map<String, Object> a = makeObjectB();
        runner.export(a, "foreign");
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

    @Test
    public void test007() {
        Assume.assumeFalse("JavaInterop not supported", TruffleOptions.AOT);
        Runner runner = new Runner("interop007");
        ClassC a = new ClassC();
        runner.export(a, "foreign");
        Assert.assertEquals(36, runner.run());

        Assert.assertEquals(a.valueI, 4);
        Assert.assertEquals(a.valueB, 3);
        Assert.assertEquals(a.valueL, 7);
        Assert.assertEquals(a.valueF, 10, 0.1);
        Assert.assertEquals(a.valueD, 12, 0.1);
    }

    @Test
    public void test008() {
        Runner runner = new Runner("interop008");
        runner.export(new ProxyExecutable() {

            @Override
            public Object execute(Value... t) {
                return t[0].asByte() + t[1].asByte();
            }
        }, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test009() {
        Runner runner = new Runner("interop009");
        runner.export(new ProxyExecutable() {

            @Override
            public Object execute(Value... t) {
                return t[0].asInt() + t[1].asInt();
            }
        }, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test010() {
        Runner runner = new Runner("interop010");
        runner.export(new ProxyExecutable() {

            @Override
            public Object execute(Value... t) {
                return t[0].asLong() + t[1].asLong();
            }
        }, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test011() {
        Runner runner = new Runner("interop011");
        runner.export(new ProxyExecutable() {

            @Override
            public Object execute(Value... t) {
                return t[0].asFloat() + t[1].asFloat();
            }
        }, "foreign");
        Assert.assertEquals(42.0, runner.run(), 0.1);
    }

    @Test
    public void test012() {
        Runner runner = new Runner("interop012");
        runner.export(new ProxyExecutable() {

            @Override
            public Object execute(Value... t) {
                Assert.assertEquals("argument count", 2, t.length);
                return t[0].asDouble() + t[1].asDouble();
            }
        }, "foreign");
        Assert.assertEquals(42.0, runner.run(), 0.1);
    }

    @Test
    public void test013() {
        Runner runner = new Runner("interop013");
        runner.export(new MyBoxedInt(), "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test014() {
        Runner runner = new Runner("interop014");
        runner.export(new MyBoxedInt(), "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test015() {
        Runner runner = new Runner("interop015");
        runner.export(new ProxyExecutable() {

            @Override
            public Object execute(Value... t) {
                Assert.assertEquals("argument count", 2, t.length);
                return t[0].asDouble() + t[1].asDouble();
            }
        }, "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test016() {
        Runner runner = new Runner("interop016");
        runner.export(null, "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test017() {
        Runner runner = new Runner("interop017");
        runner.export(new int[]{1, 2, 3}, "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test018() {
        Runner runner = new Runner("interop018");
        runner.export(new int[]{1, 2, 3}, "foreign");
        Assert.assertEquals(3, runner.run());
    }

    @Test
    public void test019() {
        Runner runner = new Runner("interop019");
        runner.export(new int[]{40, 41, 42, 43, 44}, "foreign");
        Assert.assertEquals(210, runner.run());
    }

    @Test
    public void test020() {
        Runner runner = new Runner("interop020");
        int[] arr = new int[]{40, 41, 42, 43, 44};
        runner.export(arr, "foreign");
        runner.run();
        Assert.assertArrayEquals(new int[]{30, 31, 32, 33, 34}, arr);
    }

    @Test
    public void test021() {
        Runner runner = new Runner("interop021");
        runner.export(new double[]{40, 41, 42, 43, 44}, "foreign");
        Assert.assertEquals(210, runner.run());
    }

    @Test
    public void test022() {
        Runner runner = new Runner("interop022");
        double[] arr = new double[]{40, 41, 42, 43, 44};
        runner.export(arr, "foreign");
        runner.run();
        Assert.assertArrayEquals(new double[]{30, 31, 32, 33, 34}, arr, 0.1);
    }

    @Test
    public void test023() {
        Runner runner = new Runner("interop023");
        Map<String, Object> a = makeObjectA();
        Map<String, Object> b = makeObjectA();
        runner.export(a, "foreign");
        runner.export(b, "foreign2");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test024() {
        Runner runner = new Runner("interop024");
        Map<String, Object> a = makeObjectA();
        Map<String, Object> b = makeObjectA();
        b.put("valueI", 55);
        runner.export(a, "foreign");
        runner.export(b, "foreign2");
        Assert.assertEquals(55, runner.run());
    }

    @Test
    public void test025() {
        Runner runner = new Runner("interop025");
        Map<String, Object> a = makeObjectA();
        Map<String, Object> b = makeObjectA();
        Map<String, Object> c = makeObjectA();
        b.put("valueI", 55);
        c.put("valueI", 66);
        runner.export(a, "foreign");
        runner.export(b, "foreign2");
        runner.export(c, "foreign3");
        Assert.assertEquals(66, runner.run());
    }

    @Test
    public void test026() {
        Runner runner = new Runner("interop026");
        ReturnObject result = new ReturnObject();
        runner.export(result, "foo");
        Assert.assertEquals(14, runner.run());
        Assert.assertEquals("bar", result.storage);
    }

    @Test
    public void test027() {
        Runner runner = new Runner("interop027");
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

    @Test
    public void test028() {
        Runner runner = new Runner("interop028");
        ReturnObject result = new ReturnObject();
        runner.export(result, "foo");
        Assert.assertEquals(72, runner.run());
        Assert.assertEquals("foo\u0000 bar\u0080 ", result.storage);
    }

    @Test
    public void test029() {
        Runner runner = new Runner("interop029");
        ReturnObject result = new ReturnObject();
        runner.export(result, "foo");
        Assert.assertEquals(36, runner.run());
        byte[] actualResult = (byte[]) result.storage;
        Assert.assertArrayEquals(new byte[]{102, 111, 111, 0, 32, 98, 97, 114, -128, 32}, actualResult);
    }

    // implicit interop
    // structs not yet implemented
    @Test
    @Ignore
    public void test030() throws Exception {
        Runner runner = new Runner("interop030");
        runner.run();
        Value get = runner.findGlobalSymbol("getValueI");
        int result = get.execute(makeObjectA()).asInt();
        Assert.assertEquals(42, result);
    }

    @Test
    @Ignore
    public void test031() throws Exception {
        Runner runner = new Runner("interop031");
        runner.run();
        Value apply = runner.findGlobalSymbol("complexAdd");

        ComplexNumber a = new ComplexNumber(32, 10);
        ComplexNumber b = new ComplexNumber(10, 32);

        apply.execute(a, b);

        Assert.assertEquals(42.0, a.real, 0.1);
        Assert.assertEquals(42.0, a.imaginary, 0.1);
    }

    // arrays: foreign array to llvm
    @Test
    public void test032() throws Exception {
        Runner runner = new Runner("interop032");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        int[] a = new int[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    @Test
    public void test033() throws Exception {
        Runner runner = new Runner("interop033");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        short[] a = new short[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    @Test
    public void test034() throws Exception {
        Runner runner = new Runner("interop034");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        byte[] a = new byte[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    @Test
    public void test035() throws Exception {
        Runner runner = new Runner("interop035");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        long[] a = new long[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    @Test
    public void test036() throws Exception {
        Runner runner = new Runner("interop036");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        float[] a = new float[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    @Test
    public void test037() throws Exception {
        Runner runner = new Runner("interop037");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        double[] a = new double[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    // foreign array with different type
    @Test
    public void test038() throws Exception {
        Runner runner = new Runner("interop038");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        long[] a = new long[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    @Test
    public void test039() throws Exception {
        Runner runner = new Runner("interop039");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        byte[] a = new byte[]{1, 2, 3, 4, 5};
        int result = get.execute(a, 2).asInt();
        Assert.assertEquals(3, result);
    }

    @Test
    public void test040() throws Exception {
        Runner runner = new Runner("interop040");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        Value value = get.execute();
        Assert.assertEquals(16, value.getArrayElement(4).asInt());
    }

    // llvm array to foreign language
    @Test
    public void test041() throws Exception {
        Runner runner = new Runner("interop041");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        Value getval = runner.findGlobalSymbol("getval");
        get.execute().setArrayElement(3, 9);
        int value = getval.execute(3).asInt();
        Assert.assertEquals(9, value);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test042() throws Exception {
        Runner runner = new Runner("interop042");
        runner.run();
        Value get = runner.findGlobalSymbol("get");
        get.execute().getArraySize();
    }

    @Test
    public void test043() {
        Runner runner = new Runner("interop043");
        runner.export(makeObjectA(), "foreign");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test044() {
        Runner runner = new Runner("interop044");
        runner.export(new Object(), "a");
        runner.export(14, "b");
        runner.export(14.5, "c");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test045a() {
        Runner runner = new Runner("interop045");
        runner.export(14, "a");
        runner.export(15, "b");
        Assert.assertEquals(1, runner.run());
    }

    @Test
    public void test046a() {
        Runner runner = new Runner("interop046");
        runner.export(14, "a");
        runner.export(14, "b");
        Assert.assertEquals(1, runner.run());
    }

    @Test
    public void test046b() {
        Runner runner = new Runner("interop046");
        runner.export(14, "a");
        runner.export(15, "b");
        Assert.assertEquals(1, runner.run());
    }

    @Test
    public void test047a() {
        Runner runner = new Runner("interop047");
        runner.export(14, "a");
        runner.export(15, "b");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test048a() {
        Runner runner = new Runner("interop048");
        runner.export(14, "a");
        runner.export(15, "b");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test048b() {
        Runner runner = new Runner("interop048");
        runner.export(14, "a");
        runner.export(14, "b");
        Assert.assertEquals(1, runner.run());
    }

    @Test
    public void test049a() {
        Runner runner = new Runner("interop049");
        runner.export(14, "a");
        runner.export(14, "b");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test049b() {
        Runner runner = new Runner("interop049");
        Object object = new Object();
        runner.export(object, "a");
        runner.export(object, "b");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test050a() {
        Runner runner = new Runner("interop050");
        runner.export(14, "a");
        runner.export(14, "b");
        Assert.assertEquals(1, runner.run());
    }

    @Test
    public void test050b() {
        Runner runner = new Runner("interop050");
        Object object = new Object();
        runner.export(object, "a");
        runner.export(object, "b");
        Assert.assertEquals(1, runner.run());
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
        Runner runner = new Runner("interop051");
        testGlobal(runner);
    }

    @Test
    public void test052a() {
        Runner runner = new Runner("interop052");
        testGlobal(runner);
    }

    @Test
    public void test053a() {
        Runner runner = new Runner("interop053");
        testGlobal(runner);
    }

    @Test
    public void test054a() {
        Runner runner = new Runner("interop054");
        testGlobal(runner);
    }

    @Test
    public void test055a() {
        Runner runner = new Runner("interop055");
        testGlobal(runner);
    }

    @Test
    public void test056a() {
        Runner runner = new Runner("interop056");
        testGlobal(runner);
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
        Runner runner = new Runner("interop057");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        runner.export(a, "foreign");
        Assert.assertEquals(0, runner.run());
        Assert.assertEquals(101, a[0]);
        Assert.assertEquals(102, a[1]);
    }

    @Test
    public void test058() {
        Runner runner = new Runner("interop058");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        runner.export(a, "foreign");
        Assert.assertEquals(0, runner.run());
        Assert.assertEquals(101, a[0]);
        Assert.assertEquals(102, a[1]);
    }

    @Test
    public void test059() {
        Runner runner = new Runner("interop059");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        runner.export(a, "foreign");
        Assert.assertEquals(0, runner.run());
        Assert.assertEquals(101, ((Value) a[0]).asNativePointer());
        Assert.assertEquals(102, ((Value) a[1]).asNativePointer());
    }

    @Test
    public void test060() {
        Runner runner = new Runner("interop060");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        runner.export(a, "foreign");
        Assert.assertEquals(0, runner.run());
        long a0 = ((Value) a[0]).asNativePointer();
        long a1 = ((Value) a[1]).asNativePointer();
        Assert.assertEquals(101, a0);
        Assert.assertEquals(102, a1);
    }

    @Test
    public void test061() {
        Runner runner = new Runner("interop061");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test062() {
        Runner runner = new Runner("interop062");
        Object a = new Object();
        runner.export(a, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test063() {
        Runner runner = new Runner("interop063");
        Object a = new Object();
        runner.export(a, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test064() {
        Runner runner = new Runner("interop064");
        Object a = new Object();
        runner.export(a, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test(expected = PolyglotException.class)
    public void test065() {
        Runner runner = new Runner("interop065");
        Object a = new Object();
        runner.export(a, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test(expected = PolyglotException.class)
    public void test066() throws Throwable {
        Runner runner = new Runner("interop066");
        Object a = new Object();
        runner.export(a, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test067() {
        Runner runner = new Runner("interop067");
        Object a = new Object();
        runner.export(a, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test068() {
        Runner runner = new Runner("interop068");
        runner.export(true, "boxed_true");
        runner.export(false, "boxed_false");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test069() {
        Runner runner = new Runner("interop069");
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

    @Test
    public void test070() {
        Runner runner = new Runner("interop070");
        runner.run();
        try {
            Value pointer = runner.findGlobalSymbol("returnPointerToGlobal");
            pointer.execute().setArrayElement(0, 42);
            Value value = runner.findGlobalSymbol("returnGlobal");
            Object result = value.execute().asInt();
            Assert.assertTrue(result instanceof Integer && (int) result == 42);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void test071() {
        Runner runner = new Runner("interop071");
        runner.run();
        try {
            Object obj = new Object();
            Value function = runner.findGlobalSymbol("returnPointerToGlobal");
            Value pointer = function.execute();
            pointer.setArrayElement(0, obj);
            Value value = runner.findGlobalSymbol("returnGlobal");
            Object result = value.execute().asHostObject();
            Assert.assertTrue(result == obj);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void test072() {
        Runner runner = new Runner("interop072");
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

    @Test
    public void test072a() {
        Runner runner = new Runner("interop072");
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

    @Test
    public void test072b() {
        Runner runner = new Runner("interop072");
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

    @Test
    public void test073() {
        Runner runner = new Runner("interop073");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test074() {
        Runner runner = new Runner("interop074");
        testGlobal(runner);
    }

    @Test
    public void test076() {
        Runner runner = new Runner("interop076");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test077() {
        Runner runner = new Runner("interop077");
        final String testString = "this is a test";
        runner.export((ProxyExecutable) (Value... t) -> testString, "getstring");
        Assert.assertEquals(testString.length(), runner.run());
    }

    @Test
    public void testStrlen() throws Exception {
        Runner runner = new Runner("strlen");
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

    @Test
    public void testStrcmp() throws Exception {
        Runner runner = new Runner("strcmp");
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
        Assert.assertEquals(0, test1.asInt());
        Assert.assertEquals(97, test2.asInt());
        Assert.assertEquals(-97, test3.asInt());
        Assert.assertEquals(-3, test4.asInt());
        Assert.assertEquals(3, test5.asInt());
        Assert.assertEquals(0, test6.asInt());
        Assert.assertEquals(-100, test7.asInt());
        Assert.assertEquals(100, test8.asInt());
        Assert.assertEquals(-32, test9.asInt());
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

    @Test
    public void testHandleFromNativeCallback() throws Exception {
        Runner runner = new Runner("handleFromNativeCallback");
        runner.run();
        Value testHandleFromNativeCallback = runner.findGlobalSymbol("testHandleFromNativeCallback");
        Value ret = testHandleFromNativeCallback.execute(makeObjectA());
        Assert.assertEquals(42, ret.asInt());
    }

    @Test
    public void testPointerThroughNativeCallback() throws Exception {
        Runner runner = new Runner("pointerThroughNativeCallback");
        int result = runner.run();
        Assert.assertEquals(42, result);
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
    private static final String FILENAME = "O0_MEM2REG.bc";

    private static final class Runner {
        private final String testName;
        private final Context context;

        Runner(String testName) {
            this.testName = testName;
            this.context = Context.create();
        }

        public Value findGlobalSymbol(String string) {
            return context.lookup("llvm", string);
        }

        void export(Object foreignObject, String name) {
            context.exportSymbol(name, foreignObject);
        }

        int run() {
            try {
                File file = new File(TEST_DIR.toFile(), testName + "/" + FILENAME);
                Source source = Source.newBuilder("llvm", file).build();
                return context.eval(source).asInt();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }
}
