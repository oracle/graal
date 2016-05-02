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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.test.LLVMPaths;
import com.oracle.truffle.llvm.tools.Clang;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Opt;
import com.oracle.truffle.llvm.tools.Opt.OptOptions;
import com.oracle.truffle.llvm.tools.Opt.OptOptions.Pass;

@SuppressWarnings({"static-method"})
public final class LLVMInteropTest {

    private static final String PATH = LLVMPaths.LOCAL_TESTS + "/../interoptests";

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

    private static final class Runner {
        private final Builder builder = PolyglotEngine.newBuilder();
        private final String fileName;

        Runner(String fileName) {
            this.fileName = fileName;
        }

        void export(TruffleObject foreignObject, String name) {
            builder.globalSymbol(name, foreignObject);
        }

        int run() {
            final PolyglotEngine engine = builder.build();
            try {
                File cFile = new File(PATH, fileName + ".c");
                File bcFile = File.createTempFile(PATH + "/" + "bc_" + fileName, ".ll");
                File bcOptFile = File.createTempFile(PATH + "/" + "bcopt_" + fileName, ".ll");
                Clang.compileToLLVMIR(cFile, bcFile, ClangOptions.builder());
                Opt.optimizeBitcodeFile(bcFile, bcOptFile, OptOptions.builder().pass(Pass.MEM_TO_REG));
                return engine.eval(Source.fromFileName(bcOptFile.getPath())).as(Integer.class);
            } catch (IOException e) {
                throw new AssertionError(e);
            } finally {
                engine.dispose();
            }
        }
    }
}
