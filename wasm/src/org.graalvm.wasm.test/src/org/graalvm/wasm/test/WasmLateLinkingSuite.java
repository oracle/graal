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

import java.io.IOException;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.junit.Assert;
import org.junit.Test;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

public class WasmLateLinkingSuite {
    @Test
    public void testLateMemoryLink() throws IOException {
        runTest(binaryWithMemoryExport, context -> {
            Value main = context.getBindings("wasm").getMember("main").getMember("main");
            main.execute();
            Value memory = context.getBindings("wasm").getMember("main").getMember("memory");
            memory.setArrayElement(0, 11);
            Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(binaryWithMemoryImport), "aux");
            try {
                context.eval(sourceBuilder.build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Value loadZero = context.getBindings("wasm").getMember("aux").getMember("loadZero");
            Value result = loadZero.execute();
            Assert.assertEquals(11, result.asInt());
        });
    }

    @Test
    public void linkingTooLate() throws IOException, InterruptedException {
        final Context context = Context.newBuilder("wasm").build();
        final ByteSequence binaryAux = ByteSequence.create(compileWat("file1", textWithExportFun));
        final ByteSequence binaryMain = ByteSequence.create(compileWat("file1", textWithImportFunExportFun));
        final Source sourceAux = Source.newBuilder("wasm", binaryAux, "m1").build();
        final Source sourceMain = Source.newBuilder("wasm", binaryMain, "m2").build();
        context.parse(sourceMain); // main
        context.parse(sourceAux); // m1
        final Value g = context.getBindings("wasm").getMember("main").getMember("g");
        Assert.assertEquals(42, g.execute().asInt());
    }

    private static void runTest(byte[] firstBinary, Consumer<Context> testCase) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder("wasm");
        contextBuilder.option("wasm.Builtins", "testutil:testutil");
        final Context context = contextBuilder.build();
        Source.Builder sourceBuilder = Source.newBuilder("wasm", ByteSequence.create(firstBinary), "main");
        Source source = sourceBuilder.build();
        context.eval(source);
        testCase.accept(context);
    }

    // (module
    // (type (;0;) (func))
    // (type (;1;) (func (result i32)))
    // (func (;0;) (type 0))
    // (func (;1;) (type 1) (result i32)
    // i32.const 42
    // )
    // (table (;0;) 1 1 anyfunc)
    // (memory (;0;) 4)
    // (global (;0;) (mut i32) (i32.const 66560))
    // (global (;1;) i32 (i32.const 66560))
    // (global (;2;) i32 (i32.const 1024))
    // (export "main" (func 1))
    // (export "memory" (memory 0))
    // (export "__heap_base" (global 1))
    // (export "__data_end" (global 2))
    // )
    private static final byte[] binaryWithMemoryExport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x08, (byte) 0x02, (byte) 0x60, (byte) 0x00,
                    (byte) 0x00, (byte) 0x60, (byte) 0x00, (byte) 0x01, (byte) 0x7f, (byte) 0x03, (byte) 0x03, (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x05, (byte) 0x01,
                    (byte) 0x70, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x06, (byte) 0x15, (byte) 0x03, (byte) 0x7f,
                    (byte) 0x01, (byte) 0x41, (byte) 0x80, (byte) 0x88, (byte) 0x04, (byte) 0x0b, (byte) 0x7f, (byte) 0x00, (byte) 0x41, (byte) 0x80, (byte) 0x88, (byte) 0x04, (byte) 0x0b,
                    (byte) 0x7f, (byte) 0x00, (byte) 0x41, (byte) 0x80, (byte) 0x08, (byte) 0x0b, (byte) 0x07, (byte) 0x2c, (byte) 0x04, (byte) 0x04, (byte) 0x6d, (byte) 0x61, (byte) 0x69,
                    (byte) 0x6e, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x6d, (byte) 0x65, (byte) 0x6d, (byte) 0x6f, (byte) 0x72, (byte) 0x79, (byte) 0x02, (byte) 0x00, (byte) 0x0b,
                    (byte) 0x5f, (byte) 0x5f, (byte) 0x68, (byte) 0x65, (byte) 0x61, (byte) 0x70, (byte) 0x5f, (byte) 0x62, (byte) 0x61, (byte) 0x73, (byte) 0x65, (byte) 0x03, (byte) 0x01,
                    (byte) 0x0a, (byte) 0x5f, (byte) 0x5f, (byte) 0x64, (byte) 0x61, (byte) 0x74, (byte) 0x61, (byte) 0x5f, (byte) 0x65, (byte) 0x6e, (byte) 0x64, (byte) 0x03, (byte) 0x02,
                    (byte) 0x0a, (byte) 0x09, (byte) 0x02, (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x04, (byte) 0x00, (byte) 0x41, (byte) 0x2a, (byte) 0x0b, (byte) 0x00, (byte) 0x0c,
                    (byte) 0x04, (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x02, (byte) 0x05, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00,
    };

    // (module
    // (import "main" "memory" (memory (;0;) 4))
    // (func $loadZero (result i32)
    // i32.const 0
    // i32.load
    // )
    // (export "loadZero" (func $loadZero))
    // )
    private static final byte[] binaryWithMemoryImport = new byte[]{
                    (byte) 0x00, (byte) 0x61, (byte) 0x73, (byte) 0x6d, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x05, (byte) 0x01, (byte) 0x60, (byte) 0x00,
                    (byte) 0x01, (byte) 0x7f, (byte) 0x02, (byte) 0x10, (byte) 0x01, (byte) 0x04, (byte) 0x6d, (byte) 0x61, (byte) 0x69, (byte) 0x6e, (byte) 0x06, (byte) 0x6d, (byte) 0x65,
                    (byte) 0x6d, (byte) 0x6f, (byte) 0x72, (byte) 0x79, (byte) 0x02, (byte) 0x00, (byte) 0x04, (byte) 0x03, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x07, (byte) 0x0c,
                    (byte) 0x01, (byte) 0x08, (byte) 0x6c, (byte) 0x6f, (byte) 0x61, (byte) 0x64, (byte) 0x5a, (byte) 0x65, (byte) 0x72, (byte) 0x6f, (byte) 0x00, (byte) 0x00, (byte) 0x0a,
                    (byte) 0x09, (byte) 0x01, (byte) 0x07, (byte) 0x00, (byte) 0x41, (byte) 0x00, (byte) 0x28, (byte) 0x02, (byte) 0x00, (byte) 0x0b, (byte) 0x00, (byte) 0x17, (byte) 0x04,
                    (byte) 0x6e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x01, (byte) 0x0b, (byte) 0x01, (byte) 0x00, (byte) 0x08, (byte) 0x6c, (byte) 0x6f, (byte) 0x61, (byte) 0x64,
                    (byte) 0x5a, (byte) 0x65, (byte) 0x72, (byte) 0x6f, (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x00,
    };

    private static final String textWithExportFun = "" +
                    "(func $f (result i32) (i32.const 42))\n" +
                    "(export \"f\" (func $f))";

    private static final String textWithImportFunExportFun = "" +
                    "(import \"m1\" \"f\" (func $f (result i32)))\n" +
                    "(memory (;0;) 4)\n" +
                    "(func $h (result i32) (i32.const 43))\n" +
                    "(export \"memory\" (memory 0))\n" +
                    "(export \"g\" (func $f))\n" +
                    "(export \"h\" (func $h))";
}
