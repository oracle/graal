/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.test.suites.validation;

import java.util.Arrays;
import java.util.Collection;

import org.graalvm.polyglot.Context;
import org.graalvm.wasm.exception.Failure;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MultiValueValidationSuite extends ValidationSuite {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                        stringCase("If - param type missing else", "Expected else branch. If with incompatible param and result types requires else branch.",
                                        "(type (func)) " +
                                                        "(type (func (param i32))) " +
                                                        "(func (export \"f\") (type 0) " +
                                                        "   i32.const 1 " +
                                                        "   i32.const 0 " +
                                                        "   if (type 1)" +
                                                        "       drop" +
                                                        "   end" +
                                                        ")",
                                        Failure.Type.INVALID),
                        stringCase("If - result type missing else", "Expected else branch. If with incompatible param and result types requires else branch.",
                                        "(type (func)) " +
                                                        "(type (func (result i32))) " +
                                                        "(func (export \"f\") (type 0) " +
                                                        "   i32.const 0 " +
                                                        "   if (type 1)" +
                                                        "       i32.const 1" +
                                                        "   end " +
                                                        "   drop " +
                                                        ")",
                                        Failure.Type.INVALID),
                        stringCase("If - param and result type missing else", "Expected else branch. If with incompatible param and result types requires else branch.",
                                        "(type (func)) " +
                                                        "(type (func (param i32) (result i32 i64))) " +
                                                        "(func (export \"f\") (type 0) " +
                                                        "   i32.const 1 " +
                                                        "   i32.const 0 " +
                                                        "   if (type 1)" +
                                                        "       i64.const 1" +
                                                        "   end " +
                                                        "   drop " +
                                                        "   drop" +
                                                        "   drop" +
                                                        ")",
                                        Failure.Type.INVALID),
                        binaryCase("Return - invalid reference type",
                                        "malformed value type",
                                        // (module
                                        // (func (result funcref)
                                        // return)
                                        // )
                                        "00 61 73 6D 01 00 00 00 01 05 01 60 00 01 6F 03 02 01 00 0A 03 01 00 0B",
                                        Failure.Type.MALFORMED));
    }

    public MultiValueValidationSuite(String basename, String expectedErrorMessage, byte[] bytecode, Failure.Type failureType) {
        super(basename, expectedErrorMessage, bytecode, failureType);
    }

    @Override
    protected void addContextOptions(Context.Builder contextBuilder) {
        contextBuilder.option("wasm.MultiValue", "true");
        contextBuilder.option("wasm.BulkMemoryAndRefTypes", "false");
    }
}
