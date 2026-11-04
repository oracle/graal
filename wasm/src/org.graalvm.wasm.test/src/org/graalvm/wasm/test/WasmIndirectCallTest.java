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
package org.graalvm.wasm.test;

import static org.graalvm.wasm.test.WasmJsApiSuite.moduleInstantiate;
import static org.graalvm.wasm.test.WasmJsApiSuite.runTest;
import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

import java.io.IOException;

import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.api.Dictionary;
import org.graalvm.wasm.api.Executable;
import org.graalvm.wasm.api.WebAssembly;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;

public class WasmIndirectCallTest {

    @Test
    public void testMixedRootsAndCacheOverflow() throws IOException, InterruptedException {
        byte[] binary = compileWat("mixed-indirect-roots", """
                        (module
                            (type $t (func (param i32) (result i32)))
                            (import "host" "f0" (func $h0 (type $t)))
                            (import "host" "f1" (func $h1 (type $t)))
                            (import "host" "f2" (func $h2 (type $t)))
                            (table 6 funcref)
                            (elem (i32.const 0) $h0 $g0 $h1 $g1 $h2 $g2)
                            (func $g0 (type $t) local.get 0 i32.const 1 i32.add)
                            (func $g1 (type $t) local.get 0 i32.const 3 i32.add)
                            (func $g2 (type $t) local.get 0 i32.const 5 i32.add)
                            (func (export "call") (param i32 i32) (result i32)
                                local.get 0
                                local.get 1
                                call_indirect (type $t))
                        )
                        """);
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            Dictionary imports = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "f0", new Executable(args -> args[0]),
                                            "f1", new Executable(args -> (int) args[0] + 2),
                                            "f2", new Executable(args -> (int) args[0] + 4)
                            })
            });
            var instance = moduleInstantiate(wasm, binary, imports);
            Object call = WebAssembly.instanceExport(instance, "call");
            try {
                for (int i = 0; i < 24; i++) {
                    int index = i % 6;
                    Assert.assertEquals(40 + index, InteropLibrary.getUncached().execute(call, 40, index));
                }
                for (int i = 0; i < instance.module().numFunctions(); i++) {
                    WasmFunctionInstance function = instance.functionInstance(instance.module().function(i));
                    Assert.assertSame(function.target(), function.rootNode().getCallTarget());
                    Assert.assertNull(function.rootNode().getParent());
                }
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    public void testSharedHostRootUsesActualModuleInstance() throws IOException, InterruptedException {
        byte[] binary = compileWat("shared-host-root", """
                        (module
                            (type $t (func (param i32) (result i32)))
                            (import "host" "inc" (func $inc (type $t)))
                            (table 1 funcref)
                            (elem (i32.const 0) $inc)
                            (export "inc" (func $inc))
                            (func (export "call") (type $t)
                                local.get 0
                                i32.const 0
                                call_indirect (type $t))
                        )
                        """);
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            WasmModule module = wasm.moduleDecode(binary);
            Dictionary firstImports = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{"inc", new Executable(args -> (int) args[0] + 1)})
            });
            Dictionary secondImports = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{"inc", new Executable(args -> (int) args[0] + 2)})
            });
            var first = wasm.moduleInstantiate(module, firstImports);
            var second = wasm.moduleInstantiate(module, secondImports);
            WasmFunctionInstance firstInc = (WasmFunctionInstance) WebAssembly.instanceExport(first, "inc");
            WasmFunctionInstance secondInc = (WasmFunctionInstance) WebAssembly.instanceExport(second, "inc");
            Assert.assertNotSame(firstInc, secondInc);
            Assert.assertSame(firstInc.rootNode(), secondInc.rootNode());
            Object firstCall = WebAssembly.instanceExport(first, "call");
            Object secondCall = WebAssembly.instanceExport(second, "call");
            try {
                for (int i = 0; i < 10; i++) {
                    Assert.assertEquals(i + 1, InteropLibrary.getUncached().execute(firstInc, i));
                    Assert.assertEquals(i + 2, InteropLibrary.getUncached().execute(secondInc, i));
                    Assert.assertEquals(i + 1, InteropLibrary.getUncached().execute(firstCall, i));
                    Assert.assertEquals(i + 2, InteropLibrary.getUncached().execute(secondCall, i));
                }
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        });
    }
}
