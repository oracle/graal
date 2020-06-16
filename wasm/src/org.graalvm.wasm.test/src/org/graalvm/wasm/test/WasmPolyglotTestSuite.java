/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.test;

import org.graalvm.wasm.utils.Assert;
import org.junit.Test;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

public class WasmPolyglotTestSuite {
    @Test
    public void test() throws IOException {
        Context.Builder contextBuilder = Context.newBuilder("wasm");
        Source.Builder sourceBuilder = Source.newBuilder("wasm",
                        ByteSequence.create(binary),
                        "main");
        Source source = sourceBuilder.build();
        Context context = contextBuilder.build();
        context.eval(source);
        Value mainFunction = context.getBindings("wasm").getMember("main");
        Value result = mainFunction.execute();
        Assert.assertEquals("Should be equal: ", 42, result.asInt());
    }

    private static final byte[] binary = new byte[]{
                    (byte) 0x00,
                    (byte) 0x61,
                    (byte) 0x73,
                    (byte) 0x6d,
                    (byte) 0x01,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x01,
                    (byte) 0x08,
                    (byte) 0x02,
                    (byte) 0x60,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x60,
                    (byte) 0x00,
                    (byte) 0x01,
                    (byte) 0x7f,
                    (byte) 0x03,
                    (byte) 0x03,
                    (byte) 0x02,
                    (byte) 0x00,
                    (byte) 0x01,
                    (byte) 0x04,
                    (byte) 0x05,
                    (byte) 0x01,
                    (byte) 0x70,
                    (byte) 0x01,
                    (byte) 0x01,
                    (byte) 0x01,
                    (byte) 0x05,
                    (byte) 0x03,
                    (byte) 0x01,
                    (byte) 0x00,
                    (byte) 0x00,
                    (byte) 0x06,
                    (byte) 0x15,
                    (byte) 0x03,
                    (byte) 0x7f,
                    (byte) 0x01,
                    (byte) 0x41,
                    (byte) 0x80,
                    (byte) 0x88,
                    (byte) 0x04,
                    (byte) 0x0b,
                    (byte) 0x7f,
                    (byte) 0x00,
                    (byte) 0x41,
                    (byte) 0x80,
                    (byte) 0x88,
                    (byte) 0x04,
                    (byte) 0x0b,
                    (byte) 0x7f,
                    (byte) 0x00,
                    (byte) 0x41,
                    (byte) 0x80,
                    (byte) 0x08,
                    (byte) 0x0b,
                    (byte) 0x07,
                    (byte) 0x2c,
                    (byte) 0x04,
                    (byte) 0x04,
                    (byte) 0x6d,
                    (byte) 0x61,
                    (byte) 0x69,
                    (byte) 0x6e,
                    (byte) 0x00,
                    (byte) 0x01,
                    (byte) 0x06,
                    (byte) 0x6d,
                    (byte) 0x65,
                    (byte) 0x6d,
                    (byte) 0x6f,
                    (byte) 0x72,
                    (byte) 0x79,
                    (byte) 0x02,
                    (byte) 0x00,
                    (byte) 0x0b,
                    (byte) 0x5f,
                    (byte) 0x5f,
                    (byte) 0x68,
                    (byte) 0x65,
                    (byte) 0x61,
                    (byte) 0x70,
                    (byte) 0x5f,
                    (byte) 0x62,
                    (byte) 0x61,
                    (byte) 0x73,
                    (byte) 0x65,
                    (byte) 0x03,
                    (byte) 0x01,
                    (byte) 0x0a,
                    (byte) 0x5f,
                    (byte) 0x5f,
                    (byte) 0x64,
                    (byte) 0x61,
                    (byte) 0x74,
                    (byte) 0x61,
                    (byte) 0x5f,
                    (byte) 0x65,
                    (byte) 0x6e,
                    (byte) 0x64,
                    (byte) 0x03,
                    (byte) 0x02,
                    (byte) 0x0a,
                    (byte) 0x09,
                    (byte) 0x02,
                    (byte) 0x02,
                    (byte) 0x00,
                    (byte) 0x0b,
                    (byte) 0x04,
                    (byte) 0x00,
                    (byte) 0x41,
                    (byte) 0x2a,
                    (byte) 0x0b
    };
}
