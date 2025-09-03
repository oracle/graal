/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmTable;
import org.graalvm.wasm.api.Dictionary;
import org.graalvm.wasm.api.WebAssembly;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;

public class WasmMemoryLeakSuite {

    @Test
    public void testMemoryLeak() throws IOException, InterruptedException {
        final byte[] binary = compileWat("leak", """
                        (module
                        (memory (export "mem") 128) ;; 8 MiB
                        (func $fun (export "fun") (result i32) i32.const 42)
                        (table (export "tab") 1 funcref)
                        (elem (i32.const 0) $fun)
                        )
                        """);
        WasmJsApiSuite.runTest(b -> b.engine(Engine.create()), context -> {
            InteropLibrary lib = InteropLibrary.getUncached();
            WebAssembly wasm = new WebAssembly(context);
            WasmModule module = wasm.moduleDecode(binary);
            Object importObject = new Dictionary();
            try {
                var weakStores = new ArrayList<WeakReference<Object>>();
                var weakInstances = new ArrayList<WeakReference<Object>>();
                var weakMemories = new ArrayList<WeakReference<Object>>();
                var weakFunctions = new ArrayList<WeakReference<Object>>();
                var weakTables = new ArrayList<WeakReference<Object>>();
                var strongInstances = new ArrayList<>();
                for (int iter = 0; iter < 16; iter++) {
                    var instance = wasm.moduleInstantiate(module, importObject);
                    Object exports = lib.readMember(instance, "exports");
                    Object mem = lib.readMember(exports, "mem");
                    Object fun = lib.readMember(exports, "fun");
                    Object tab = lib.readMember(exports, "tab");

                    weakStores.add(new WeakReference<>(instance.store()));
                    weakInstances.add(new WeakReference<>(instance));
                    weakMemories.add(new WeakReference<>(mem));
                    weakFunctions.add(new WeakReference<>(fun));
                    weakTables.add(new WeakReference<>(tab));

                    // a single WasmInstance, WasmFunctionInstance, or WasmMemory instance
                    // should prevent the context from being closed and garbage-collected.
                    switch (iter % 4) {
                        case 0 -> strongInstances.add(instance);
                        case 1 -> strongInstances.add(fun);
                        case 2 -> strongInstances.add(mem);
                        case 3 -> strongInstances.add(tab);
                    }
                }

                processReferenceQueueAndGC();

                for (int i = 0; i < strongInstances.size(); i++) {
                    Object instance = strongInstances.get(i);
                    Assert.assertNotNull(instance);
                    switch (i % 4) {
                        case 0 -> {
                            // WasmInstance
                            Object exports = lib.readMember(instance, "exports");
                            Assert.assertTrue(lib.isMemberReadable(exports, "fun"));
                        }
                        case 1 -> {
                            // WasmFunction
                            Assert.assertTrue(lib.isExecutable(instance));
                            Assert.assertEquals(42, lib.execute(instance));
                        }
                        case 2 -> {
                            // WasmMemory
                            Assert.assertEquals(128 * 64 * 1024, lib.getBufferSize(instance));
                            Assert.assertEquals(0, lib.readBufferInt(instance, ByteOrder.LITTLE_ENDIAN, 0));
                        }
                        case 3 -> {
                            // WasmTable
                            // No table interop support (GR-57847).
                            Object fun = WebAssembly.tableRead((WasmTable) instance, 0);
                            Assert.assertTrue(lib.isExecutable(fun));
                            Assert.assertEquals(42, lib.execute(fun));
                        }
                    }
                }

                // memories are kept alive either by the WasmMemory object itself or by the context
                Assert.assertTrue("WebAssembly memories should not have been freed yet.",
                                weakMemories.stream().noneMatch(weakRef -> weakRef.refersTo(null)));
                // contexts may be freed in the case of only WasmMemory references

                strongInstances.clear();
                processReferenceQueueAndGC();

                Assert.assertTrue("WebAssembly memories should have been freed.",
                                weakMemories.stream().anyMatch(weakRef -> weakRef.refersTo(null)));
                Assert.assertTrue("WebAssembly stores should have been freed.",
                                weakStores.stream().anyMatch(weakRef -> weakRef.refersTo(null)));
            } catch (InteropException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void processReferenceQueueAndGC() {
        System.gc();
        Context.create().close(); // process reference queue
        System.gc();
    }
}
