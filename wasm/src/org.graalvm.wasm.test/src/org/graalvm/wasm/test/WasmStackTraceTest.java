/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.EnumSet;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.PolyglotException.StackFrame;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.WasmBinaryTools.WabtOption;
import org.junit.Test;

public class WasmStackTraceTest {

    private static final String TRIGGER_UNREACHABLE = """
                    (module
                     (type $0 (func))
                     (memory $0 0)
                     (table $0 1 1 funcref)
                     (elem $0 (i32.const 1))
                     (export "main" (func $module/main))
                     (export "memory" (memory $0))
                     (func $module/foo
                       unreachable
                     )
                     (func $module/bar
                      call $module/foo
                     )
                     (func $module/main
                      call $module/bar
                     )
                    )
                    """;

    private static final String MULTIPLE_DIRECT_CALLS = """
                    (module
                     (func $trap
                      unreachable
                     )
                     (func $noop)
                     (func (export "main")
                      call $noop
                      i32.const 65536
                      drop
                      call $noop
                      call $trap
                     )
                    )
                    """;

    private static final String INDIRECT_CALL = """
                    (module
                     (type $t (func))
                     (table 1 funcref)
                     (elem (i32.const 0) $trap)
                     (func $trap
                      unreachable
                     )
                     (func (export "main")
                      i32.const 0
                      call_indirect (type $t)
                     )
                    )
                    """;

    private static final String MIXED_DIRECT_AND_INDIRECT_CALLS = """
                    (module
                     (type $t (func))
                     (table 1 funcref)
                     (elem (i32.const 0) $trap)
                     (func $trap
                      unreachable
                     )
                     (func $noop)
                     (func (export "main")
                      call $noop
                      i32.const 0
                      call_indirect (type $t)
                     )
                    )
                    """;

    private static final String REFERENCE_CALL = """
                    (module
                     (type $t (func))
                     (elem declare funcref (ref.func $trap))
                     (func $trap
                      unreachable
                     )
                     (func (export "main")
                      ref.func $trap
                      call_ref $t
                     )
                    )
                    """;

    @Test
    public void testStackTrace() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Value module = evalModule(context, "direct", TRIGGER_UNREACHABLE);
            // TODO wasm does not yet support capturing the bytecode index for
            // exception top-most exception frames.
            assertStackTrace(module.newInstance(), new String[]{"wasm-function:0", "wasm-function:1", "main"}, new int[]{-1, 19, 30});
        }
    }

    @Test
    public void testMultipleDirectCallBytecodeIndicesAndReinstantiation() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Value module = evalModule(context, "multiple-direct", MULTIPLE_DIRECT_CALLS);
            for (int i = 0; i < 2; i++) {
                assertStackTrace(module.newInstance(), new String[]{"wasm-function:0", "main"}, new int[]{-1, 31});
            }
        }
    }

    @Test
    public void testIndirectCallBytecodeIndexAndReinstantiation() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Value module = evalModule(context, "indirect", INDIRECT_CALL);
            for (int i = 0; i < 2; i++) {
                assertStackTrace(module.newInstance(), new String[]{"wasm-function:0", "main"}, new int[]{-1, 23});
            }
        }
    }

    @Test
    public void testMixedDirectAndIndirectCallIndices() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            final Value module = evalModule(context, "mixed", MIXED_DIRECT_AND_INDIRECT_CALLS);
            assertStackTrace(module.newInstance(), new String[]{"wasm-function:0", "main"}, new int[]{-1, 34});
        }
    }

    @Test
    public void testReferenceCallBytecodeIndexAndReinstantiation() throws IOException, InterruptedException {
        try (Context context = Context.newBuilder(WasmLanguage.ID).allowExperimentalOptions(true).option("wasm.TypedFunctionReferences", "true").build()) {
            final Value module = evalModule(context, "reference", REFERENCE_CALL, EnumSet.of(WabtOption.FUNCTION_REFERENCES));
            for (int i = 0; i < 2; i++) {
                assertStackTrace(module.newInstance(), new String[]{"wasm-function:0", "main"}, new int[]{-1, 26});
            }
        }
    }

    @Test
    public void testTailCallBytecodeIndicesAndReinstantiation() throws IOException, InterruptedException {
        String[] calls = {"call $forward", "i32.const 0 call_indirect (type $t)"};
        int[] bytecodeIndices = {30, 33};
        for (int call = 0; call < calls.length; call++) {
            String wat = """
                            (module
                             (type $t (func))
                             (table 1 funcref)
                             (elem (i32.const 0) $forward)
                             (func $trap unreachable)
                             (func $forward return_call $trap)
                             (func (export "main") %s)
                            )
                            """.formatted(calls[call]);
            try (Context context = Context.newBuilder(WasmLanguage.ID).allowExperimentalOptions(true).option("wasm.TailCallLoops", "false").build()) {
                final Value module = evalModule(context, "tail-call-" + call, wat, EnumSet.of(WabtOption.TAIL_CALLS));
                for (int i = 0; i < 2; i++) {
                    assertStackTrace(module.newInstance(), new String[]{"wasm-function:0", "main"}, new int[]{-1, bytecodeIndices[call]});
                }
            }
        }
    }

    private static Value evalModule(Context context, String name, String wat) throws IOException, InterruptedException {
        return evalModule(context, name, wat, EnumSet.noneOf(WabtOption.class));
    }

    private static Value evalModule(Context context, String name, String wat, EnumSet<WabtOption> wabtOptions) throws IOException, InterruptedException {
        final ByteSequence binary = ByteSequence.create(compileWat(name, wat, wabtOptions));
        final Source source = Source.newBuilder(WasmLanguage.ID, binary, name).build();
        return context.eval(source);
    }

    private static void assertStackTrace(Value instance, String[] rootNames, int[] bytecodeIndices) {
        assertEquals(rootNames.length, bytecodeIndices.length);
        final Value main = instance.getMember("exports").getMember("main");
        try {
            main.execute();
            throw new AssertionError("Expected the Wasm function to throw.");
        } catch (PolyglotException e) {
            var iterator = e.getPolyglotStackTrace().iterator();
            for (int i = 0; i < rootNames.length; i++) {
                StackFrame frame = iterator.next();
                assertEquals(rootNames[i], frame.getRootName());
                assertEquals(bytecodeIndices[i], frame.getBytecodeIndex());
            }
            // Value.execute
            assertTrue(iterator.next().isHostFrame());
        }
    }

}
