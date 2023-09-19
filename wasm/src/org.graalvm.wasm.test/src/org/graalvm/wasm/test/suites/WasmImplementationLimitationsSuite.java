/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.test.suites;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.exception.Failure;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

@RunWith(Parameterized.class)
public class WasmImplementationLimitationsSuite {
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
                        stringCase("Table instance - initial size out of bounds",
                                        "table instance size exceeds limit: 2147483648 should be <= 2147483647",
                                        "(table $table1 2147483648 funcref)", Failure.Type.TRAP),
                        stringCase("Memory instance - initial size out of bounds",
                                        "memory instance size exceeds limit: 32768 should be <= 32767",
                                        "(memory $memory1 32768)", Failure.Type.TRAP));
    }

    private final String expectedErrorMessage;
    private final byte[] bytecode;
    private final Failure.Type expectedFailureType;

    @SuppressWarnings("unused")
    public WasmImplementationLimitationsSuite(String basename, String expectedErrorMessage, byte[] bytecode, Failure.Type failureType) {
        this.expectedErrorMessage = expectedErrorMessage;
        this.bytecode = bytecode;
        this.expectedFailureType = failureType;
    }

    @Test
    public void test() throws IOException {
        final Context context = Context.newBuilder(WasmLanguage.ID).build();
        final Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(bytecode), "dummy_main").build();
        try {
            context.eval(source).getMember("_main").execute();
        } catch (final PolyglotException e) {
            final Value actualFailureObject = e.getGuestObject();

            if (e.isInternalError()) {
                throw e;
            }

            Assert.assertNotNull(actualFailureObject);
            Assert.assertTrue(actualFailureObject.hasMember("failureType"));

            Assert.assertEquals("unexpected error message", expectedErrorMessage, e.getMessage());
            final String failureType = actualFailureObject.getMember("failureType").asString();
            Assert.assertEquals("unexpected failure type", expectedFailureType.name, failureType);
            return;
        } finally {
            context.close();
        }
        throw new AssertionError("expected to be invalid");
    }

    private static Object[] stringCase(String name, String errorMessage, String textString, Failure.Type failureType) {
        try {
            return new Object[]{name, errorMessage, compileWat(name, textString + "(func (export \"_main\") (result i32) i32.const 42)"), failureType};
        } catch (final IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
