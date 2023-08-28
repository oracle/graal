/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;
import static org.hamcrest.CoreMatchers.containsString;

import java.io.IOException;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.junit.Assert;
import org.junit.Test;

public class WasmLateLinkingSuite {
    private static final int N_CONTEXTS = 3;

    @Test
    public void testLateMemoryLink() throws IOException {
        runTest(binaryWithMemoryExport, context -> {
            Value main = context.getBindings(WasmLanguage.ID).getMember("main").getMember("main");
            main.execute();
            Value memory = context.getBindings(WasmLanguage.ID).getMember("main").getMember("memory");
            memory.setArrayElement(0, 11);
            Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(binaryWithMemoryImport), "aux");
            try {
                context.eval(sourceBuilder.build());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Value loadZero = context.getBindings(WasmLanguage.ID).getMember("aux").getMember("loadZero");
            Value result = loadZero.execute();
            Assert.assertEquals(11, result.asInt());
        });
    }

    @Test
    public void linkingTooLate() throws IOException, InterruptedException {
        final ByteSequence binaryAux = ByteSequence.create(compileWat("file1", textWithExportFun));
        final ByteSequence binaryMain = ByteSequence.create(compileWat("file1", textWithImportFunExportFun));
        final Source sourceAux = Source.newBuilder(WasmLanguage.ID, binaryAux, "m1").build();
        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "m2").build();
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            context.eval(sourceMain); // main
            context.eval(sourceAux); // m1
            final Value g = context.getBindings(WasmLanguage.ID).getMember("main").getMember("g");
            Assert.assertEquals(42, g.execute().asInt());
        }
    }

    @Test
    public void linkingTooLateShared() throws IOException, InterruptedException {
        final ByteSequence binaryAux = ByteSequence.create(compileWat("file1", textWithExportFun));
        final ByteSequence binaryMain = ByteSequence.create(compileWat("file1", textWithImportFunExportFun));
        final Source sourceAux = Source.newBuilder(WasmLanguage.ID, binaryAux, "m1").build();
        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "m2").build();
        try (Engine engine = Engine.create()) {
            for (int i = 0; i < N_CONTEXTS; i++) {
                try (Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).build()) {
                    Value main = context.eval(sourceMain); // main
                    Value main2 = context.eval(sourceMain); // main
                    Assert.assertEquals(main, main2);
                    context.eval(sourceAux); // m1
                    final Value g = context.getBindings(WasmLanguage.ID).getMember("main").getMember("g");
                    Assert.assertEquals(42, g.execute().asInt());
                }
            }
        }
    }

    @Test
    public void linkingTooLateDifferentImportedFunctions() throws IOException, InterruptedException {
        final ByteSequence binaryMain = ByteSequence.create(compileWat("file0", textWithImportFunExportFun));
        final ByteSequence binaryAux1 = ByteSequence.create(compileWat("file1", textWithExportFun));
        final ByteSequence binaryAux2 = ByteSequence.create(compileWat("file1", textWithExportFun2));
        final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "m0").build();
        final Source sourceAux1 = Source.newBuilder(WasmLanguage.ID, binaryAux1, "m1").build();
        final Source sourceAux2 = Source.newBuilder(WasmLanguage.ID, binaryAux2, "m1").build();
        try (Engine engine = Engine.create()) {
            for (int i = 0; i < N_CONTEXTS; i++) {
                try (Context context = Context.newBuilder(WasmLanguage.ID).engine(engine).build()) {
                    Value mainModule = context.eval(sourceMain); // main
                    int expected;
                    if (i < N_CONTEXTS - 1) {
                        context.eval(sourceAux1); // m1
                        expected = 42;
                    } else {
                        context.eval(sourceAux2); // m1'
                        expected = 43;
                    }
                    // call g(), which is the reexported f() from m1.
                    final Value g = mainModule.getMember("g");
                    Assert.assertEquals(expected, g.execute().asInt());
                    // call f() via call_f() function in main module.
                    final Value f = mainModule.getMember("call_f");
                    Assert.assertEquals(expected, f.execute().asInt());
                }
            }
        }
    }

    @Test
    public void linkingFailureInvertedOnlyAux() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final ByteSequence binaryAux = ByteSequence.create(compileWat("file1", "(import \"non_existing\" \"f\" (func))"));
            final ByteSequence binaryMain = ByteSequence.create(compileWat("file2", "(func (export \"g\") (result i32) (i32.const 42))"));
            final Source sourceAux = Source.newBuilder(WasmLanguage.ID, binaryAux, "module1").build();
            final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "module2").build();
            context.eval(sourceAux);
            final Value module2Instance = context.eval(sourceMain);

            // Fails: "the module 'non_existing',
            // referenced by the import 'f' in the module 'module1',
            // does not exist."
            try {
                final Value g = module2Instance.getMember("g");
                g.execute();
                Assert.assertFalse("Should not reach here.", true);
            } catch (Throwable e) {
                // We can later refine these semantics, and avoid a failure here.
                // To do this, we should only lazily link exactly the required modules, instead of
                // linking all of them.
                Assert.assertThat("Should fail due to unresolved import in the other module.", e.getMessage(),
                                containsString("module 'non_existing', referenced by the import 'f' in the module 'module1', does not exist"));
            }

            try {
                final Value g2 = module2Instance.getMember("g");
                final Value result2 = g2.execute();
                Assert.assertEquals(42, result2.asInt());
            } catch (Throwable e) {
                Assert.assertFalse("Should not reach here, got: " + e.getMessage(), true);
            }
        }
    }

    @Test
    public void linkingFailureOnlyAux() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final ByteSequence binaryAux = ByteSequence.create(compileWat("file1", "(import \"non_existing\" \"f\" (func))"));
            final ByteSequence binaryMain = ByteSequence.create(compileWat("file2", "(func (export \"g\") (result i32) (i32.const 42))"));
            final Source sourceAux = Source.newBuilder(WasmLanguage.ID, binaryAux, "module1").build();
            final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "module2").build();
            final Value module2Instance = context.eval(sourceMain);
            context.eval(sourceAux);

            // Fails: "the module 'non_existing',
            // referenced by the import 'f' in the module 'module1',
            // does not exist."
            try {
                final Value g = module2Instance.getMember("g");
                g.execute();
                Assert.assertFalse("Should not reach here.", true);
            } catch (Throwable e) {
                // We can later refine these semantics, and avoid a failure here.
                // To do this, we should only lazily link exactly the required modules, instead of
                // linking all of them.
                Assert.assertThat("Should fail due to unresolved import in the other module.", e.getMessage(),
                                containsString("module 'non_existing', referenced by the import 'f' in the module 'module1', does not exist"));
            }

            try {
                final Value g2 = module2Instance.getMember("g");
                final Value result2 = g2.execute();
                Assert.assertEquals(42, result2.asInt());
            } catch (Throwable e) {
                Assert.assertFalse("Should not reach here, got: " + e.getMessage(), true);
            }
        }
    }

    @Test
    public void linkingFailureDueToDependency() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final ByteSequence binaryAux = ByteSequence.create(compileWat("file1", "(import \"non_existing\" \"f\" (func)) (func (export \"h\") (result i32) (i32.const 42))"));
            final ByteSequence binaryMain = ByteSequence.create(compileWat("file2", "(import \"main\" \"h\" (func)) (func (export \"g\") (result i32) (i32.const 42))"));
            final Source sourceAux = Source.newBuilder(WasmLanguage.ID, binaryAux, "module1").build();
            final Source sourceMain = Source.newBuilder(WasmLanguage.ID, binaryMain, "module2").build();
            context.eval(sourceAux);
            final Value module2Instance = context.eval(sourceMain);

            try {
                final Value g = module2Instance.getMember("g");
                g.execute();
                Assert.fail("Should not reach here.");
            } catch (Throwable e) {
                Assert.assertThat("Should fail due to unresolved import in the other module.", e.getMessage(),
                                containsString("module 'non_existing', referenced by the import 'f' in the module 'module1', does not exist"));
            }

            try {
                final Value g2 = module2Instance.getMember("g");
                g2.execute();
                Assert.fail("Should not reach here.");
            } catch (Throwable e) {
                Assert.assertThat("Should fail due to both modules being in a failed linking state.", e.getMessage(),
                                containsString("Linking of module wasm-module(module2) previously failed"));
            }
        }
    }

    @Test
    public void lazyLinkEquivalenceClasses() throws IOException, InterruptedException {
        // Exports table with a function
        final byte[] exportBytes = compileWat("exportTable", """
                        (module
                        (func $f0 (result i32) i32.const 42)
                        (table 1 1 funcref)
                        (export "table" (table 0))
                        (elem (i32.const 0) $f0)
                        )
                        """);

        // Imports table and exports function that invokes functions from the table
        final byte[] importBytes = compileWat("importTable", """
                        (module
                        (type (func (param i32) (result i32)))
                        (type (func (result i32)))
                        (import "main" "table" (table 1 1 funcref))
                        (func (type 0) (param i32) (result i32) local.get 0 call_indirect (type 1))
                        (export "testFunc" (func 0))
                        )
                        """);

        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final ByteSequence exportByteSeq = ByteSequence.create(exportBytes);
            final ByteSequence importByteSeq = ByteSequence.create(importBytes);
            final Source exportSource = Source.newBuilder(WasmLanguage.ID, exportByteSeq, "exportModule").build();
            final Source importSource = Source.newBuilder(WasmLanguage.ID, importByteSeq, "importModule").build();
            final Value exportModuleInstance = context.eval(exportSource);
            exportModuleInstance.getMember("table");
            // Linking of the first module was triggered by this point.
            final Value importModuleInstance = context.eval(importSource);
            importModuleInstance.getMember("testFunc").execute(0);
            // Linking of the second module was triggered by this point.
        }
    }

    private static void runTest(byte[] firstBinary, Consumer<Context> testCase) throws IOException {
        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.option("wasm.Builtins", "testutil:testutil");
        try (Context context = contextBuilder.build()) {
            Source.Builder sourceBuilder = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(firstBinary), "main");
            Source source = sourceBuilder.build();
            context.eval(source);
            testCase.accept(context);
        }
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

    private static final String textWithExportFun = """
                    (func $f (result i32) (i32.const 42))
                    (export "f" (func $f))
                    """;

    private static final String textWithExportFun2 = """
                    (func $f (result i32) (i32.const 43))
                    (export "f" (func $f))
                    """;

    private static final String textWithImportFunExportFun = """
                    (import "m1" "f" (func $f (result i32)))
                    (memory (;0;) 4)
                    (func $h (result i32) (i32.const 43))
                    (func $call_f (result i32) (call $f))
                    (export "memory" (memory 0))
                    (export "g" (func $f))
                    (export "h" (func $h))
                    (export "call_f" (func $call_f))
                    """;
}
