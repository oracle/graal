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
import java.util.EnumSet;

import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmFunctionInstance;
import org.graalvm.wasm.api.Dictionary;
import org.graalvm.wasm.api.Executable;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.nodes.WasmFunctionNode;
import org.graalvm.wasm.nodes.WasmReturnCallNode;
import org.graalvm.wasm.utils.WasmBinaryTools;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;

public class WasmReturnCallTest {

    @Test
    public void testHostImportsAndStartFunction() throws IOException, InterruptedException {
        byte[] binary = compileWat("tail-call-host-and-start", """
                        (module
                            (type $t (func (param i32) (result i32)))
                            (import "host" "inc" (func $inc (type $t)))
                            (import "host" "init" (func $init))
                            (table 2 funcref)
                            (elem (i32.const 0) $forward $inc)
                            (func $forward (type $t)
                                local.get 0
                                return_call $inc)
                            (func (export "direct") (type $t)
                                local.get 0
                                call $forward)
                            (func (export "indirect") (param i32 i32) (result i32)
                                local.get 0
                                local.get 1
                                return_call_indirect (type $t))
                            (func $startForward
                                return_call $init)
                            (func $start
                                return_call $startForward)
                            (start $start)
                        )
                        """, EnumSet.of(WasmBinaryTools.WabtOption.TAIL_CALLS));
        runTest(builder -> builder.allowExperimentalOptions(true).option("wasm.TailCallLoops", "false"), context -> {
            int[] starts = {0};
            Dictionary imports = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{
                                            "inc", new Executable(args -> {
                                                if ((int) args[0] < 0) {
                                                    throw WasmException.create(Failure.UNSPECIFIED_TRAP);
                                                }
                                                return (int) args[0] + 1;
                                            }),
                                            "init", new Executable(args -> {
                                                starts[0]++;
                                                return WasmConstant.VOID;
                                            })
                            })
            });
            WebAssembly wasm = new WebAssembly(context);
            var instance = moduleInstantiate(wasm, binary, imports);
            Object direct = WebAssembly.instanceExport(instance, "direct");
            Object indirect = WebAssembly.instanceExport(instance, "indirect");
            InteropLibrary interop = InteropLibrary.getUncached();
            try {
                for (int i = 0; i < 10; i++) {
                    Assert.assertEquals(i + 1, interop.execute(direct, i));
                    Assert.assertEquals(i + 1, interop.execute(indirect, i, i % 2));
                    Assert.assertNull(context.language().returnCallData().rootNode);
                    Assert.assertNull(context.language().returnCallData().args);
                }
                Assert.assertEquals(1, starts[0]);
                Assert.assertThrows(WasmException.class, () -> interop.execute(direct, -1));
                Assert.assertNull(context.language().returnCallData().rootNode);
                Assert.assertNull(context.language().returnCallData().args);
                Assert.assertEquals(42, interop.execute(direct, 41));
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    public void testMoreRootsThanInlineCacheLimit() throws IOException, InterruptedException {
        byte[] binary = compileWat("tail-call-root-cache", """
                        (module
                            (type $t (func (result i32)))
                            (table 6 funcref)
                            (elem (i32.const 0) $f0 $f1 $f2 $f3 $f4 $f5)
                            (func $f0 (result i32) i32.const 0)
                            (func $f1 (result i32) i32.const 1)
                            (func $f2 (result i32) i32.const 2)
                            (func $f3 (result i32) i32.const 3)
                            (func $f4 (result i32) i32.const 4)
                            (func $f5 (result i32) i32.const 5)
                            (func $dispatch (param i32) (result i32)
                                local.get 0
                                return_call_indirect (type $t))
                            (func (export "call") (param i32) (result i32)
                                local.get 0
                                call $dispatch)
                        )
                        """, EnumSet.of(WasmBinaryTools.WabtOption.TAIL_CALLS));
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            var instance = moduleInstantiate(wasm, binary, null);
            Object call = WebAssembly.instanceExport(instance, "call");
            try {
                for (int i = 0; i < 18; i++) {
                    Assert.assertEquals(i % 6, InteropLibrary.getUncached().execute(call, i % 6));
                    Assert.assertNull(context.language().returnCallData().rootNode);
                    Assert.assertNull(context.language().returnCallData().args);
                }
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    public void testDirectCallLocation() throws IOException, InterruptedException {
        byte[] binary = compileWat("direct-call-location", """
                        (module
                            (import "host" "check" (func $check))
                            (func $forward call $check)
                            (func (export "call") call $forward)
                        )
                        """);
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            Dictionary imports = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{"check", new Executable(args -> {
                                Boolean found = Truffle.getRuntime().iterateFrames(frame -> {
                                    if (((RootCallTarget) frame.getCallTarget()).getRootNode().getName().equals("call")) {
                                        Assert.assertTrue(frame.getCallNode() instanceof WasmFunctionNode<?>);
                                        Assert.assertSame(((RootCallTarget) frame.getCallTarget()).getRootNode(), frame.getCallNode().getRootNode());
                                        Assert.assertTrue(frame.getBytecodeIndex() >= 0);
                                        return true;
                                    }
                                    return null;
                                });
                                Assert.assertEquals(Boolean.TRUE, found);
                                return WasmConstant.VOID;
                            })})
            });
            var instance = moduleInstantiate(wasm, binary, imports);
            try {
                InteropLibrary.getUncached().execute(WebAssembly.instanceExport(instance, "call"));
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    public void testUncachedTrampoline() throws IOException, InterruptedException {
        byte[] binary = compileWat("tail-call-uncached", """
                        (module
                            (import "host" "inc" (func $inc (param i32) (result i32)))
                            (func (export "forward") (param i32) (result i32)
                                local.get 0
                                return_call $inc)
                        )
                        """, EnumSet.of(WasmBinaryTools.WabtOption.TAIL_CALLS));
        runTest(context -> {
            WebAssembly wasm = new WebAssembly(context);
            Dictionary imports = Dictionary.create(new Object[]{
                            "host", Dictionary.create(new Object[]{"inc", new Executable(args -> (int) args[0] + 1)})
            });
            var instance = moduleInstantiate(wasm, binary, imports);
            WasmFunctionInstance forward = (WasmFunctionInstance) WebAssembly.instanceExport(instance, "forward");
            var data = context.language().returnCallData();
            data.rootNode = forward.rootNode();
            data.args = WasmArguments.create(instance, 41);
            Assert.assertEquals(42, WasmReturnCallNode.getUncached().call(-1));
            Assert.assertNull(data.rootNode);
            Assert.assertNull(data.args);
        });
    }
}
