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

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.llvm.runtime.LLVMTruffleAddress;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

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
        ClassA a = new ClassA();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test003() {
        Runner runner = new Runner("interop003");
        ClassA a = new ClassA();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(215, runner.run());
    }

    @Test
    public void test004() {
        Runner runner = new Runner("interop004");
        ClassB a = new ClassB();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(73, runner.run());
    }

    @Test
    public void test005() {
        Runner runner = new Runner("interop005");
        ClassA a = new ClassA();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        runner.run();

        Assert.assertEquals(a.valueBool, false);
        Assert.assertEquals(a.valueI, 2);
        Assert.assertEquals(a.valueB, 3);
        Assert.assertEquals(a.valueL, 4);
        Assert.assertEquals(a.valueF, 5.5, 0.1);
        Assert.assertEquals(a.valueD, 6.5, 0.1);
    }

    @Test
    public void test006() {
        Runner runner = new Runner("interop006");
        ClassB a = new ClassB();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        runner.run();

        Assert.assertEquals(a.valueI[0], 1);
        Assert.assertEquals(a.valueI[1], 2);

        Assert.assertEquals(a.valueL[0], 3);
        Assert.assertEquals(a.valueL[1], 4);

        Assert.assertEquals(a.valueB[0], 5);
        Assert.assertEquals(a.valueB[1], 6);

        Assert.assertEquals(a.valueF[0], 7.5, 0.1);
        Assert.assertEquals(a.valueF[1], 8.5, 0.1);

        Assert.assertEquals(a.valueD[0], 9.5, 0.1);
        Assert.assertEquals(a.valueD[1], 10.5, 0.1);
    }

    @Test
    public void test007() {
        Runner runner = new Runner("interop007");
        ClassC a = new ClassC();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
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
        TruffleObject to = JavaInterop.asTruffleFunction(FuncBInterface.class, (a, b) -> (byte) (a + b));
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test009() {
        Runner runner = new Runner("interop009");
        TruffleObject to = JavaInterop.asTruffleFunction(FuncIInterface.class, (a, b) -> (a + b));
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test010() {
        Runner runner = new Runner("interop010");
        TruffleObject to = JavaInterop.asTruffleFunction(FuncLInterface.class, (a, b) -> (a + b));
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test011() {
        Runner runner = new Runner("interop011");
        TruffleObject to = JavaInterop.asTruffleFunction(FuncFInterface.class, (a, b) -> (a + b));
        runner.export(to, "foreign");
        Assert.assertEquals(42.0, runner.run(), 0.1);
    }

    @Test
    public void test012() {
        Runner runner = new Runner("interop012");
        TruffleObject to = JavaInterop.asTruffleFunction(FuncDInterface.class, (a, b) -> (a + b));
        runner.export(to, "foreign");
        Assert.assertEquals(42.0, runner.run(), 0.1);
    }

    @Test
    public void test013() {
        Runner runner = new Runner("interop013");
        TruffleObject to = JavaInterop.asTruffleObject(new MyBoxedInt());
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test014() {
        Runner runner = new Runner("interop014");
        TruffleObject to = JavaInterop.asTruffleObject(new MyBoxedInt());
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test015() {
        Runner runner = new Runner("interop015");
        TruffleObject to = JavaInterop.asTruffleFunction(FuncDInterface.class, (a, b) -> (a + b));
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test016() {
        Runner runner = new Runner("interop016");
        TruffleObject to = JavaInterop.asTruffleObject(null);
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test017() {
        Runner runner = new Runner("interop017");
        TruffleObject to = JavaInterop.asTruffleObject(new int[]{1, 2, 3});
        runner.export(to, "foreign");
        Assert.assertEquals(42, runner.run(), 0.1);
    }

    @Test
    public void test018() {
        Runner runner = new Runner("interop018");
        TruffleObject to = JavaInterop.asTruffleObject(new int[]{1, 2, 3});
        runner.export(to, "foreign");
        Assert.assertEquals(3, runner.run());
    }

    @Test
    public void test019() {
        Runner runner = new Runner("interop019");
        TruffleObject to = JavaInterop.asTruffleObject(new int[]{40, 41, 42, 43, 44});
        runner.export(to, "foreign");
        Assert.assertEquals(210, runner.run());
    }

    @Test
    public void test020() {
        Runner runner = new Runner("interop020");
        int[] arr = new int[]{40, 41, 42, 43, 44};
        TruffleObject to = JavaInterop.asTruffleObject(arr);
        runner.export(to, "foreign");
        runner.run();
        Assert.assertArrayEquals(new int[]{30, 31, 32, 33, 34}, arr);
    }

    @Test
    public void test021() {
        Runner runner = new Runner("interop021");
        TruffleObject to = JavaInterop.asTruffleObject(new double[]{40, 41, 42, 43, 44});
        runner.export(to, "foreign");
        Assert.assertEquals(210, runner.run());
    }

    @Test
    public void test022() {
        Runner runner = new Runner("interop022");
        double[] arr = new double[]{40, 41, 42, 43, 44};
        TruffleObject to = JavaInterop.asTruffleObject(arr);
        runner.export(to, "foreign");
        runner.run();
        Assert.assertArrayEquals(new double[]{30, 31, 32, 33, 34}, arr, 0.1);
    }

    @Test
    public void test023() {
        Runner runner = new Runner("interop023");
        ClassA a = new ClassA();
        ClassA b = new ClassA();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        TruffleObject to2 = JavaInterop.asTruffleObject(b);
        runner.export(to, "foreign");
        runner.export(to2, "foreign2");
        Assert.assertEquals(42, runner.run());
    }

    @Test
    public void test024() {
        Runner runner = new Runner("interop024");
        ClassA a = new ClassA();
        ClassA b = new ClassA();
        b.valueI = 55;
        TruffleObject to = JavaInterop.asTruffleObject(a);
        TruffleObject to2 = JavaInterop.asTruffleObject(b);
        runner.export(to, "foreign");
        runner.export(to2, "foreign2");
        Assert.assertEquals(55, runner.run());
    }

    @Test
    public void test025() {
        Runner runner = new Runner("interop025");
        ClassA a = new ClassA();
        ClassA b = new ClassA();
        ClassA c = new ClassA();
        b.valueI = 55;
        c.valueI = 66;
        TruffleObject to = JavaInterop.asTruffleObject(a);
        TruffleObject to2 = JavaInterop.asTruffleObject(b);
        TruffleObject to3 = JavaInterop.asTruffleObject(c);
        runner.export(to, "foreign");
        runner.export(to2, "foreign2");
        runner.export(to3, "foreign3");
        Assert.assertEquals(66, runner.run());
    }

    @Test
    public void test026() {
        Runner runner = new Runner("interop026");
        final Object[] result = new Object[]{null};
        runner.export(JavaInterop.asTruffleFunction(FuncEInterface.class, x -> result[0] = x), "foo");
        Assert.assertEquals(14, runner.run());
        Assert.assertEquals("bar", result[0]);
    }

    @Test
    public void test027() {
        Runner runner = new Runner("interop027");
        final Object[] result = new Object[]{null};
        runner.export(JavaInterop.asTruffleFunction(FuncEInterface.class, x -> result[0] = x), "foo");
        Assert.assertEquals(14, runner.run());
        Assert.assertEquals("\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087\u0088\u0089\u008a\u008b\u008c\u008d\u008e\u008f" +
                        "\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097\u0098\u0099\u009a\u009b\u009c\u009d\u009e\u009f" +
                        "\u00a0\u00a1\u00a2\u00a3\u00a4\u00a5\u00a6\u00a7\u00a8\u00a9\u00aa\u00ab\u00ac\u00ad\u00ae\u00af" +
                        "\u00b0\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6\u00b7\u00b8\u00b9\u00ba\u00bb\u00bc\u00bd\u00be\u00bf" +
                        "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf" +
                        "\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df" +
                        "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef" +
                        "\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff",
                        result[0]);
    }

    @Test
    public void test028() {
        Runner runner = new Runner("interop028");
        final Object[] result = new Object[]{null};
        runner.export(JavaInterop.asTruffleFunction(FuncEInterface.class, x -> result[0] = x), "foo");
        Assert.assertEquals(72, runner.run());
        Assert.assertEquals("foo\u0000 bar\u0080 ", result[0]);
    }

    @Test
    public void test029() {
        Runner runner = new Runner("interop029");
        final Object[] result = new Object[]{null};
        runner.export(JavaInterop.asTruffleFunction(FuncEInterface.class, x -> result[0] = x), "foo");
        Assert.assertEquals(36, runner.run());
        byte[] actualResult = (byte[]) (result[0]);
        Assert.assertArrayEquals(new byte[]{102, 111, 111, 0, 32, 98, 97, 114, -128, 32}, actualResult);
    }

    // implicit interop
    // structs not yet implemented
    @Test
    @Ignore
    public void test030() throws Exception {
        Runner runner = new Runner("interop030");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("getValueI");
            ClassA a = new ClassA();
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a)).get();
            Assert.assertEquals(42, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    @Ignore
    public void test031() throws Exception {
        Runner runner = new Runner("interop031");
        try {
            PolyglotEngine.Value apply = runner.findGlobalSymbol("complexAdd");

            ComplexNumber a = new ComplexNumber(32, 10);
            ComplexNumber b = new ComplexNumber(10, 32);

            apply.execute(JavaInterop.asTruffleObject(a), JavaInterop.asTruffleObject(b));

            Assert.assertEquals(42.0, a.real, 0.1);
            Assert.assertEquals(42.0, a.imaginary, 0.1);
        } finally {
            runner.dispose();
        }
    }

    // arrays: foreign array to llvm
    @Test
    public void test032() throws Exception {
        Runner runner = new Runner("interop032");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            int[] a = new int[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test033() throws Exception {
        Runner runner = new Runner("interop033");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            short[] a = new short[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test034() throws Exception {
        Runner runner = new Runner("interop034");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            byte[] a = new byte[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test035() throws Exception {
        Runner runner = new Runner("interop035");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            long[] a = new long[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test036() throws Exception {
        Runner runner = new Runner("interop036");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            float[] a = new float[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test037() throws Exception {
        Runner runner = new Runner("interop037");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            double[] a = new double[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    // foreign array with different type
    @Test
    public void test038() throws Exception {
        Runner runner = new Runner("interop038");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            long[] a = new long[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test039() throws Exception {
        Runner runner = new Runner("interop039");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            byte[] a = new byte[]{1, 2, 3, 4, 5};
            Number result = (Number) get.execute(JavaInterop.asTruffleObject(a), 2).get();
            Assert.assertEquals(3, result.intValue());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test040() throws Exception {
        Runner runner = new Runner("interop040");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            TruffleObject result = get.execute().as(TruffleObject.class);
            @SuppressWarnings("unchecked")
            List<Integer> array = JavaInterop.asJavaObject(List.class, result);
            Assert.assertEquals(16, (int) array.get(4));
        } finally {
            runner.dispose();
        }
    }

    // llvm array to foreign language
    @Test
    public void test041() throws Exception {
        Runner runner = new Runner("interop041");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            PolyglotEngine.Value getval = runner.findGlobalSymbol("getval");
            TruffleObject result = get.execute().as(TruffleObject.class);
            @SuppressWarnings("unchecked")
            List<Integer> array = JavaInterop.asJavaObject(List.class, result);
            array.set(3, 9);
            int value = (int) getval.execute(3).get();
            Assert.assertEquals(9, value);
        } finally {
            runner.dispose();
        }
    }

    @Test(expected = UnsupportedMessageException.class)
    public void test042() throws Exception {
        Runner runner = new Runner("interop042");
        try {
            PolyglotEngine.Value get = runner.findGlobalSymbol("get");
            TruffleObject result = get.execute().as(TruffleObject.class);
            @SuppressWarnings("unchecked")
            List<Integer> array = JavaInterop.asJavaObject(List.class, result);
            array.size(); // GET_SIZE is not supported
            Assert.fail("IllegalStateException expected");
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void test043() {
        Runner runner = new Runner("interop043");
        ClassA a = new ClassA();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test044() {
        Runner runner = new Runner("interop044");
        runner.export(JavaInterop.asTruffleObject(new Object()), "a");
        runner.export(JavaInterop.asTruffleObject(14), "b");
        runner.export(JavaInterop.asTruffleObject(14.5), "c");
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
        runner.export(JavaInterop.asTruffleObject(14), "a");
        runner.export(JavaInterop.asTruffleObject(14), "b");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test049b() {
        Runner runner = new Runner("interop049");
        TruffleObject object = JavaInterop.asTruffleObject(new Object());
        runner.export(object, "a");
        runner.export(object, "b");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test050a() {
        Runner runner = new Runner("interop050");
        runner.export(JavaInterop.asTruffleObject(14), "a");
        runner.export(JavaInterop.asTruffleObject(14), "b");
        Assert.assertEquals(1, runner.run());
    }

    @Test
    public void test050b() {
        Runner runner = new Runner("interop050");
        TruffleObject object = JavaInterop.asTruffleObject(new Object());
        runner.export(object, "a");
        runner.export(object, "b");
        Assert.assertEquals(1, runner.run());
    }

    static Object staticStorage;

    interface ReturnObject {
        void storeObject(Object o);
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
        TruffleObject returnObject = JavaInterop.asTruffleFunction(ReturnObject.class, new ReturnObject() {

            @Override
            public void storeObject(Object o) {
                staticStorage = o;
            }
        });
        Object original = new Object();
        TruffleObject object = JavaInterop.asTruffleObject(original);
        runner.export(object, "object");
        runner.export(returnObject, "returnObject");
        staticStorage = null;
        runner.run();
        Assert.assertSame(original, staticStorage);
    }

    @Test
    public void test057() {
        Runner runner = new Runner("interop057");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(0, runner.run());
        Assert.assertEquals(101, a[0]);
        Assert.assertEquals(102, a[1]);
    }

    @Test
    public void test058() {
        Runner runner = new Runner("interop058");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(0, runner.run());
        Assert.assertEquals(101, a[0]);
        Assert.assertEquals(102, a[1]);
    }

    @Test
    public void test059() {
        Runner runner = new Runner("interop059");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(0, runner.run());
        Assert.assertEquals(101, ((LLVMTruffleAddress) a[0]).getAddress().getVal());
        Assert.assertEquals(102, ((LLVMTruffleAddress) a[1]).getAddress().getVal());
    }

    @Test
    public void test060() {
        Runner runner = new Runner("interop060");
        Object[] a = new Object[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "foreign");
        Assert.assertEquals(0, runner.run());
        Assert.assertEquals(101, ((LLVMTruffleAddress) a[0]).getAddress().getVal());
        Assert.assertEquals(102, ((LLVMTruffleAddress) a[1]).getAddress().getVal());
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
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test063() {
        Runner runner = new Runner("interop063");
        Object a = new Object();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test064() {
        Runner runner = new Runner("interop064");
        Object a = new Object();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test065() {
        Runner runner = new Runner("interop065");
        Object a = new Object();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void test066() {
        Runner runner = new Runner("interop066");
        Object a = new Object();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "object");
        Assert.assertEquals(0, runner.run());
    }

    @Test
    public void test067() {
        Runner runner = new Runner("interop067");
        Object a = new Object();
        TruffleObject to = JavaInterop.asTruffleObject(a);
        runner.export(to, "object");
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
            Object result = globalSymbol.execute().get();
            Assert.assertTrue(result instanceof Integer && (int) result == 42);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testStrlen() throws Exception {
        Runner runner = new Runner("strlen");
        try {
            Value strlenFunction = runner.findGlobalSymbol("func");
            Value nullString = strlenFunction.execute(JavaInterop.asTruffleObject(new char[]{}));
            Value a = strlenFunction.execute(JavaInterop.asTruffleObject(new char[]{'a'}));
            Value abcd = strlenFunction.execute(JavaInterop.asTruffleObject(new char[]{'a', 'b', 'c', 'd'}));
            Value abcdWithTerminator = strlenFunction.execute(JavaInterop.asTruffleObject(new char[]{'a', 'b', 'c', 'd', '\0'}));
            Assert.assertEquals(0, nullString.get());
            Assert.assertEquals(1, a.get());
            Assert.assertEquals(4, abcd.get());
            Assert.assertEquals(5, abcdWithTerminator.get());
        } finally {
            runner.dispose();
        }
    }

    @Test
    public void testStrcmp() throws Exception {
        Runner runner = new Runner("strcmp");
        try {
            Value strcmpFunction = runner.findGlobalSymbol("func");
            Value test1 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{}), JavaInterop.asTruffleObject(new char[]{}));
            Value test2 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{'a'}), JavaInterop.asTruffleObject(new char[]{}));
            Value test3 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{}), JavaInterop.asTruffleObject(new char[]{'a'}));
            Value test4 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{'a'}), JavaInterop.asTruffleObject(new char[]{'d'}));
            Value test5 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{'d'}), JavaInterop.asTruffleObject(new char[]{'a'}));
            Value test6 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{'d'}), JavaInterop.asTruffleObject(new char[]{'d'}));
            Value test7 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{'a', 'b', 'c'}), JavaInterop.asTruffleObject(new char[]{'a', 'b', 'c', 'd'}));
            Value test8 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{'a', 'b', 'c', 'd'}), JavaInterop.asTruffleObject(new char[]{'a', 'b', 'c'}));
            Value test9 = strcmpFunction.execute(JavaInterop.asTruffleObject(new char[]{'A', 'B', 'C', 'D'}), JavaInterop.asTruffleObject(new char[]{'a', 'b', 'c', 'd'}));
            Assert.assertEquals(0, test1.get());
            Assert.assertEquals(97, test2.get());
            Assert.assertEquals(-97, test3.get());
            Assert.assertEquals(-3, test4.get());
            Assert.assertEquals(3, test5.get());
            Assert.assertEquals(0, test6.get());
            Assert.assertEquals(-100, test7.get());
            Assert.assertEquals(100, test8.get());
            Assert.assertEquals(-32, test9.get());
            Value strcmpWithNativeFunction = runner.findGlobalSymbol("compare_with_native");
            Value test10 = strcmpWithNativeFunction.execute(JavaInterop.asTruffleObject(new char[]{}));
            Value test11 = strcmpWithNativeFunction.execute(JavaInterop.asTruffleObject(new char[]{'f', 'o', 'o'}));
            Value test12 = strcmpWithNativeFunction.execute(JavaInterop.asTruffleObject(new char[]{'e'}));
            Value test13 = strcmpWithNativeFunction.execute(JavaInterop.asTruffleObject(new char[]{'g'}));
            Assert.assertEquals((int) 'f', test10.get());
            Assert.assertEquals(0, test11.get());
            Assert.assertEquals(1, test12.get());
            Assert.assertEquals(-1, test13.get());
        } finally {
            runner.dispose();
        }
    }

    public static final class ClassA {
        public boolean valueBool = true;
        public byte valueB = 40;
        public char valueC = 41;
        public int valueI = 42;
        public long valueL = 43;
        public float valueF = 44.5F;
        public double valueD = 45.5;
    }

    public static final class ClassB {

        public byte[] valueB = {1, 2};
        public int[] valueI = {5, 6};
        public long[] valueL = {7, 8};
        public float[] valueF = {9.5F, 10.5F};
        public double[] valueD = {11.5, 12.5};

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

    @FunctionalInterface
    public interface FuncIInterface {
        int eval(int a, int b);
    }

    @FunctionalInterface
    public interface FuncBInterface {
        byte eval(byte a, byte b);
    }

    @FunctionalInterface
    public interface FuncLInterface {
        long eval(long a, long b);
    }

    @FunctionalInterface
    public interface FuncFInterface {
        float eval(float a, float b);
    }

    @FunctionalInterface
    public interface FuncDInterface {
        double eval(double a, double b);
    }

    @FunctionalInterface
    public interface FuncEInterface {
        Object eval(Object string);
    }

    private static final Path TEST_DIR = new File(LLVMOptions.ENGINE.projectRoot() + "/../cache/tests/interoptests").toPath();
    private static final String FILE_SUFFIX = "_clang_O0_MEM2REG.bc";

    private static final class Runner {
        private final Builder builder = PolyglotEngine.newBuilder();
        private final String fileName;

        Runner(String fileName) {
            this.fileName = fileName;
        }

        void export(Object foreignObject, String name) {
            builder.globalSymbol(name, foreignObject);
        }

        int run() {
            final PolyglotEngine engine = builder.build();
            try {
                File file = new File(TEST_DIR.toFile(), "/" + fileName + "/" + fileName + FILE_SUFFIX);
                return engine.eval(Source.newBuilder(file).build()).as(Integer.class);
            } catch (IOException e) {
                throw new AssertionError(e);
            } finally {
                engine.dispose();
            }
        }

        protected PolyglotEngine prepareVM() throws Exception {
            PolyglotEngine engine = builder.build();
            try {
                File file = new File(TEST_DIR.toFile(), "/" + fileName + "/" + fileName + FILE_SUFFIX);
                engine.eval(Source.newBuilder(file).build()).as(Integer.class);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return engine;
        }

        private PolyglotEngine vm = null;
        private static Reference<PolyglotEngine> previousVMReference = new WeakReference<>(null);

        PolyglotEngine vm() throws Exception {
            if (vm == null) {
                vm = prepareVM();
                replacePreviousVM(vm);
            }
            return vm;
        }

        private static void replacePreviousVM(PolyglotEngine newVM) {
            PolyglotEngine vm = previousVMReference.get();
            if (vm == newVM) {
                return;
            }
            if (vm != null) {
                vm.dispose();
            }
            previousVMReference = new WeakReference<>(newVM);
        }

        PolyglotEngine.Value findGlobalSymbol(String name) throws Exception {
            PolyglotEngine.Value s = vm().findGlobalSymbol(name);
            assert s != null : "Symbol " + name + " is not found!";
            return s;
        }

        void dispose() {
            replacePreviousVM(null);
        }
    }
}
