/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.junit.Test;

import java.io.IOException;

public class GR14706Test {

    @Test
    public void testInvalidMaxOpsPerInstruction() throws IOException {
        final byte[] module = new byte[]{
                (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x01, (byte) 0x06, (byte) 0x01, (byte) 0x60, (byte) 0x01, (byte) 0x7f, (byte) 0x01, (byte) 0x7f,
                (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x05, (byte) 0x01,
                (byte) 0x70, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x03, (byte) 0x01, (byte) 0x00,
                (byte) 0x01, (byte) 0x07, (byte) 0x1a, (byte) 0x02, (byte) 0x06, (byte) 0x6d, (byte) 0x65, (byte) 0x6d,
                (byte) 0x6f, (byte) 0x72, (byte) 0x79, (byte) 0x02, (byte) 0x00, (byte) 0x0d, (byte) 0x6d, (byte) 0x75,
                (byte) 0x74, (byte) 0x75, (byte) 0x61, (byte) 0x6c, (byte) 0x5f, (byte) 0x70, (byte) 0x61, (byte) 0x72,
                (byte) 0x69, (byte) 0x74, (byte) 0x79, (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x3d, (byte) 0x02,
                (byte) 0x10, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0x41, (byte) 0x1f, (byte) 0x71, (byte) 0x41,
                (byte) 0x15, (byte) 0x6a, (byte) 0x10, (byte) 0x81, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x00,
                (byte) 0x0b, (byte) 0x2a, (byte) 0x01, (byte) 0x01, (byte) 0x7f, (byte) 0x41, (byte) 0x01, (byte) 0x21,
                (byte) 0x01, (byte) 0x02, (byte) 0x40, (byte) 0x02, (byte) 0x40, (byte) 0x02, (byte) 0x40, (byte) 0x20,
                (byte) 0x00, (byte) 0x0e, (byte) 0x02, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x0b, (byte) 0x20,
                (byte) 0x00, (byte) 0x41, (byte) 0x7e, (byte) 0x6a, (byte) 0x10, (byte) 0x81, (byte) 0x80, (byte) 0x80,
                (byte) 0x80, (byte) 0x00, (byte) 0x0f, (byte) 0x0b, (byte) 0x41, (byte) 0x00, (byte) 0x21, (byte) 0x01,
                (byte) 0x0b, (byte) 0x20, (byte) 0x01, (byte) 0x0b, (byte) 0x00, (byte) 0x20, (byte) 0x0d, (byte) 0x2e,
                (byte) 0x64, (byte) 0x65, (byte) 0x62, (byte) 0x75, (byte) 0x67, (byte) 0x5f, (byte) 0x61, (byte) 0x62,
                (byte) 0x62, (byte) 0x72, (byte) 0x65, (byte) 0x76, (byte) 0x01, (byte) 0x11, (byte) 0x01, (byte) 0x25,
                (byte) 0x0e, (byte) 0x13, (byte) 0x05, (byte) 0x03, (byte) 0x0e, (byte) 0x10, (byte) 0x17, (byte) 0x1b,
                (byte) 0x0e, (byte) 0x0b, (byte) 0x0b, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x2a,
                (byte) 0x0b, (byte) 0x2e, (byte) 0x64, (byte) 0x65, (byte) 0x62, (byte) 0x75, (byte) 0x67, (byte) 0x5f,
                (byte) 0x69, (byte) 0x6e, (byte) 0x66, (byte) 0x6f, (byte) 0xbb, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04, (byte) 0x01,
                (byte) 0x75, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0c, (byte) 0x00, (byte) 0x62, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x81, (byte) 0x01, (byte) 0x0a, (byte) 0x2e, (byte) 0x64,
                (byte) 0x65, (byte) 0x62, (byte) 0x75, (byte) 0x67, (byte) 0x5f, (byte) 0x73, (byte) 0x74, (byte) 0x72,
                (byte) 0x73, (byte) 0x65, (byte) 0x74, (byte) 0x00, (byte) 0x73, (byte) 0x00, (byte) 0x75, (byte) 0x6e,
                (byte) 0x73, (byte) 0x69, (byte) 0x67, (byte) 0x6e, (byte) 0x65, (byte) 0x64, (byte) 0x20, (byte) 0x63,
                (byte) 0x68, (byte) 0x61, (byte) 0x72, (byte) 0x00, (byte) 0x69, (byte) 0x73, (byte) 0x5f, (byte) 0x65,
                (byte) 0x76, (byte) 0x65, (byte) 0x6e, (byte) 0x00, (byte) 0x75, (byte) 0x6e, (byte) 0x73, (byte) 0x69,
                (byte) 0x67, (byte) 0x6e, (byte) 0x65, (byte) 0x64, (byte) 0x20, (byte) 0x6c, (byte) 0x6f, (byte) 0x6e,
                (byte) 0x67, (byte) 0x00, (byte) 0x6d, (byte) 0x65, (byte) 0x6d, (byte) 0x6d, (byte) 0x6f, (byte) 0x76,
                (byte) 0x65, (byte) 0x00, (byte) 0x69, (byte) 0x73, (byte) 0x5f, (byte) 0x6f, (byte) 0x64, (byte) 0x64,
                (byte) 0x00, (byte) 0x73, (byte) 0x72, (byte) 0x63, (byte) 0x00, (byte) 0x72, (byte) 0x74, (byte) 0x2e,
                (byte) 0x63, (byte) 0x00, (byte) 0x6d, (byte) 0x75, (byte) 0x74, (byte) 0x75, (byte) 0x61, (byte) 0x6c,
                (byte) 0x5f, (byte) 0x72, (byte) 0x65, (byte) 0x63, (byte) 0x75, (byte) 0x72, (byte) 0x73, (byte) 0x69,
                (byte) 0x6f, (byte) 0x6e, (byte) 0x2e, (byte) 0x63, (byte) 0x00, (byte) 0x55, (byte) 0x62, (byte) 0x75,
                (byte) 0x6e, (byte) 0x74, (byte) 0x75, (byte) 0x20, (byte) 0x63, (byte) 0x6c, (byte) 0x61, (byte) 0x6e,
                (byte) 0x67, (byte) 0x20, (byte) 0x76, (byte) 0x65, (byte) 0x72, (byte) 0x73, (byte) 0x69, (byte) 0x6f,
                (byte) 0x6e, (byte) 0x20, (byte) 0x31, (byte) 0x34, (byte) 0x2e, (byte) 0x30, (byte) 0x2e, (byte) 0x30,
                (byte) 0x2d, (byte) 0x31, (byte) 0x75, (byte) 0x62, (byte) 0x75, (byte) 0x6e, (byte) 0x00, (byte) 0x41,
                (byte) 0x0b, (byte) 0x2e, (byte) 0x64, (byte) 0x65, (byte) 0x62, (byte) 0x75, (byte) 0x67, (byte) 0x5f,
                (byte) 0x6c, (byte) 0x69, (byte) 0x6e, (byte) 0x65, (byte) 0x6a, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x04, (byte) 0x00, (byte) 0x2a, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
                (byte) 0x01, (byte) 0xfb, (byte) 0x0e, (byte) 0x0d, (byte) 0x00, (byte) 0x01, (byte) 0x01, (byte) 0x01,
                (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x00, (byte) 0x6d, (byte) 0x75, (byte) 0x74, (byte) 0x75, (byte) 0x61, (byte) 0x6c, (byte) 0x5f,
                (byte) 0x72, (byte) 0x65, (byte) 0x63, (byte) 0x75, (byte) 0x72, (byte) 0x73, (byte) 0x69, (byte) 0x6f,
                (byte) 0x6e, (byte) 0x2e, (byte) 0x63, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x20,
        };

        final ByteSequence binary = ByteSequence.create(module);
        final Source source = Source.newBuilder(WasmLanguage.ID, binary, "test").build();

        try (Context context = Context.create(WasmLanguage.ID)) {
            Value instance = context.eval(source).newInstance();
            Value exports = instance.getMember("exports");
            Value mutualParity = exports.getMember("mutual_parity");
            mutualParity.execute(1);
        }
    }
}
