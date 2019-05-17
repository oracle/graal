/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.test;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public abstract class WasmTest {
    private TestElement[] testElements = {
                    test("(module (func (result i32) i32.const 42))", expected(42)),
                    test("(module (func (result i32) i32.const 1895633))", expected(1_895_633)),
                    test("(module (func (result i32) i32.const 42 i32.const 17 drop))", expected(42)),
                    test("(module (func (result i32) i32.const 42 i32.const 17 i32.add))", expected(59)),
                    test("(module (func (result i32) i32.const 42 i32.const 17 i32.sub))", expected(25)),
                    test("(module (func (result i32) i32.const 42 i32.const 43 i32.sub))", expected(-1)),
                    test("(module (func (result i32) i32.const 11 i32.const 12 i32.mul))", expected(132)),
                    test("(module (func (result i32) i32.const 11 i32.const 3 i32.div_s))", expected(3)),
                    test("(module (func (result i32) i32.const -11 i32.const 3 i32.div_s))", expected(-3)),
                    test("(module (func (result i32) i32.const 118 i32.const 11 i32.div_u))", expected(10)),
                    test("(module (func (result i32) i32.const 0x80000001 i32.const 0x1000 i32.div_u))", expected(524288)),
                    test("(module (func (result i32) i32.const 0x80000001 i32.const 0x1000 i32.div_u))", expected(524288)),
                    test("(module (func (result i32) i32.const 118 i32.const 11 i32.div_u))", expected(10)),
                    test("(module (func (result i32) i32.const 11 i32.const 3 i32.rem_s))", expected(2)),
                    test("(module (func (result i32) i32.const 11 i32.const -3 i32.rem_s))", expected(2)),
                    test("(module (func (result i32) i32.const -11 i32.const -3 i32.rem_s))", expected(-2)),
                    test("(module (func (result i32) i32.const -11 i32.const 3 i32.rem_s))", expected(-2)),
                    test("(module (func (result i32) i32.const 0x80000001 i32.const 0x1001 i32.rem_u))", expected(129)),
                    test("(module (func (result i32) i32.const 10 i32.const 7 i32.and))", expected(2)),
                    test("(module (func (result i32) i32.const -1 i32.const 7 i32.and))", expected(7)),
                    test("(module (func (result i32) i32.const -2 i32.const 7 i32.or))", expected(-1)),
                    test("(module (func (result i32) i32.const 6 i32.const 9 i32.or))", expected(15)),
                    test("(module (func (result i32) i32.const 11 i32.const 4 i32.xor))", expected(15)),
                    test("(module (func (result i32) i32.const -2 i32.const 3 i32.xor))", expected(-3)),
                    test("(module (func (result f32) f32.const -2.0 f32.const -4.5 f32.add))", expected(-6.5f)),
                    test("(module (func (result i32) i32.const 1690433))", expected(1690433)),
                    test("(module (func (result f32) f32.const 3.14))", expected(3.14f, 0.001f)),
                    test("(module (func (result f32) f32.const 3.14 f32.const 2.71 f32.add))", expected(5.85f, 0.001f)),
                    test("(module (func (result f64) f64.const 340.75))", expected(340.75, 0.001)),
                    test("(module (func (result i32) block $B0 (result i32) i32.const 11 end i32.const 21 i32.add))", expected(32)),
    };

    private static TestElement test(String program, TestData data) {
        return new TestElement(program, data);
    }

    private static TestData expected(Object expectedValue) {
        return new TestData((result) -> Assert.assertEquals("", expectedValue, result.as(Object.class)));
    }

    private static TestData expected(float expectedValue, float e) {
        return new TestData((result) -> Assert.assertEquals(expectedValue, result.as(Float.class), e));
    }

    private static TestData expected(double expectedValue, double e) {
        return new TestData((result) -> Assert.assertEquals(expectedValue, result.as(Double.class), e));
    }

    @Test
    public void runTests() throws InterruptedException {
        Map<TestElement, Throwable> errors = new LinkedHashMap<>();
        for (TestElement element : testElements) {
            try {
                runTest(element);
            } catch (Throwable e) {
                errors.put(element, e);
            }
        }
        if (!errors.isEmpty()) {
            for (Map.Entry<TestElement, Throwable> entry : errors.entrySet()) {
                System.err.println("Failure in: " + entry.getKey().name);
                System.err.println(entry.getValue().getClass().getSimpleName() + ": " + entry.getValue().getMessage());
                entry.getValue().printStackTrace();
            }
            System.err.println("\u001B[31m" + (testElements.length - errors.size()) + "/" + testElements.length + " tests passed.\u001B[0m");
            throw new RuntimeException("Tests failed!");
        } else {
            System.out.println("\u001B[32m" + testElements.length + "/" + testElements.length + " tests passed.\u001B[0m");
        }
        Thread.sleep(10000);
    }

    protected abstract void runTest(TestElement element);

    protected static class TestElement {
        public String name;
        public String program;
        public TestData data;

        TestElement(String name, String program, TestData data) {
            this.name = name;
            this.program = program;
            this.data = data;
        }

        TestElement(String program, TestData data) {
            this.name = program;
            this.program = program;
            this.data = data;
        }
    }
}
