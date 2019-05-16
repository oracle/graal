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

import org.junit.Assert;
import org.junit.Test;

public abstract class WasmTest {
    private TestElement[] testElements = {
           test("(module (func (result i32) (i32.const 42)))", expected(42)),
           test("(module (func (result i32) (i32.const 1895633)))", expected(1_895_633)),
           test("(module (func (result i32) i32.const 42 i32.const 17 drop))", expected(42)),
           test("(module (func (result i32) i32.const 42 i32.const 17 i32.add))", expected(59)),
           test("(module (func (result i32) i32.const 42 i32.const 17 i32.sub))", expected(25)),
           test("(module (func (result i32) i32.const 11 i32.const 12 i32.sub))", expected(132)),
           test("(module (func (result i32) (i32.const 1690433)))", expected(1690433)),
           test("(module (func (result f32) (f32.const 3.14)))", expected(3.14f)),
           test("(module (func (result f64) (f64.const 340.75)))", expected(340.75)),
    };

    private static TestElement test(String program, TestData data) {
        return new TestElement(program, data);
    }

    private static TestData expected(Object expectedValue) {
        return new TestData((result) -> Assert.assertEquals("", result.as(Object.class), expectedValue));
    }

    @Test
    public void runTests() throws InterruptedException {
        for (TestElement element : testElements) {
            runTest(element);
        }
        Thread.sleep(10000);
    }

    protected abstract void runTest(TestElement element);

    protected static class TestElement {
        public String program;
        public TestData data;

        TestElement(String program, TestData data) {
            this.program = program;
            this.data = data;
        }

    }
}
