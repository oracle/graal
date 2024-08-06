/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test.regress;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class GR54505Test {

    @Test
    public void testWrongArity() throws IOException, InterruptedException {
        final ByteSequence binaryMain = ByteSequence.create(compileWat("main", """
                          (module
                          (type (;0;) (func (param f64 i32 i32) (result i32)))
                          (func (;0;) (type 0) (param f64 i32 i32) (result i32)
                            unreachable
                          )
                          (export "_main" (func 0))
                        )
                        """));

        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "main").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            Value mainModule = context.eval(sourceMain); // main
            final Value main = mainModule.getMember("_main");
            // Too few arguments
            try {
                main.execute();
                fail();
            } catch (IllegalArgumentException e) {
                assertThat(e.toString(), e.getMessage(), CoreMatchers.containsString("Invalid argument count"));
            }
            try {
                main.execute(0, 0);
                fail();
            } catch (IllegalArgumentException e) {
                assertThat(e.toString(), e.getMessage(), CoreMatchers.containsString("Invalid argument count"));
            }
            // Too many arguments
            try {
                main.execute(0.0, 0, 0, 0);
                fail();
            } catch (IllegalArgumentException e) {
                assertThat(e.toString(), e.getMessage(), CoreMatchers.containsString("Invalid argument count"));
            }
        }
    }

    @Test
    public void testDifferentArgumentType() throws IOException, InterruptedException {
        final ByteSequence binaryMain = ByteSequence.create(compileWat("main", """
                          (module
                          (type (;0;) (func (param f64 i32 i32) (result i32)))
                          (func (;0;) (type 0) (param f64 i32 i32) (result i32)
                            i32.const 42
                          )
                          (export "_main" (func 0))
                        )
                        """));

        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "main").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            Value mainModule = context.eval(sourceMain); // main
            final Value main = mainModule.getMember("_main");

            // Expected argument types
            assertEquals(42, main.execute(0.0, 0, 0).asInt());

            // Invalid argument type(s)
            try {
                main.execute(0, 0, 0);
                fail();
            } catch (IllegalArgumentException e) {
                assertThat(e.toString(), e.getMessage(), CoreMatchers.containsString("Invalid argument"));
            }
            try {
                main.execute(0.0, 0L, 0);
                fail();
            } catch (IllegalArgumentException e) {
                assertThat(e.toString(), e.getMessage(), CoreMatchers.containsString("Invalid argument"));
            }
        }
    }
}
