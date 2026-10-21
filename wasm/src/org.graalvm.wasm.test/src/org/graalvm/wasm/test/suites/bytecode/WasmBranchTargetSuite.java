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
package org.graalvm.wasm.test.suites.bytecode;

import static org.graalvm.wasm.utils.WasmBinaryTools.compileWat;

import java.io.IOException;
import java.util.EnumSet;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.utils.WasmBinaryTools.WabtOption;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Exercises shared branch and exception destinations in interpreted and compiled execution. Stack
 * heights cover all label widths; repeated instantiation also exercises bytecode metadata rereading.
 */
@RunWith(Parameterized.class)
public class WasmBranchTargetSuite {
    @Parameterized.Parameters(name = "stackSize={0}, compiled={1}")
    public static Object[][] data() {
        return new Object[][]{{0, false}, {64, false}, {256, false}, {0, true}, {64, true}, {256, true}};
    }

    private final int stackSize;
    private final boolean compiled;

    public WasmBranchTargetSuite(int stackSize, boolean compiled) {
        this.stackSize = stackSize;
        this.compiled = compiled;
    }

    private static final String THROW_AT_DIFFERENT_STACK_HEIGHTS = """
                    local.get $choice
                    if
                        i64.const 99
                        i32.const 42
                        local.get $ref
                        throw $tag
                    end
                    i32.const 42
                    local.get $ref
                    throw $tag
                    """;

    /**
     * Checks that a modern typed catch discards operands above its target stack height and delivers
     * its numeric and reference payload, with legacy EH enabled and disabled.
     */
    @Test
    public void testCatch() throws IOException, InterruptedException {
        runCatch("catch $tag", "i32 externref", "");
    }

    /**
     * Checks typed catch_ref transfers from different stack heights, including removing discarded
     * operands before pushing the payload and exception reference to avoid transient frame
     * overflow.
     */
    @Test
    public void testCatchRef() throws IOException, InterruptedException {
        runCatch("catch_ref $tag", "i32 externref exnref", "drop");
    }

    /**
     * Checks that a modern catch_all discards the thrown payload and excess operands while
     * preserving the stack below its target, with legacy EH enabled and disabled.
     */
    @Test
    public void testCatchAll() throws IOException, InterruptedException {
        runCatch("catch_all", "", "i32.const 42 local.get $ref");
    }

    /**
     * Checks that catch_all_ref transfers only the exception reference after discarding the payload
     * and excess operands, with legacy EH enabled and disabled.
     */
    @Test
    public void testCatchAllRef() throws IOException, InterruptedException {
        runCatch("catch_all_ref", "exnref", "drop i32.const 42 local.get $ref");
    }

    private void runCatch(String catchClause, String resultTypes, String cleanup) throws IOException, InterruptedException {
        String instructions = """
                        block $target (result %s)
                            try_table (%s $target)
                                %s
                            end
                            unreachable
                        end
                        %s
                        """.formatted(resultTypes, catchClause, THROW_AT_DIFFERENT_STACK_HEIGHTS, cleanup);
        // Modern handlers must work with legacy EH both enabled and disabled.
        run(instructions, true);
        run(instructions, false);
    }

    /** Checks prefixed backward branches in both displacement widths and execution modes. */
    @Test
    public void testBackwardReferenceBranches() throws IOException, InterruptedException {
        for (boolean branchOnNull : new boolean[]{true, false}) {
            String wat = """
                            (module
                                (func (export "test") (param $ref externref) (result i32)
                                    (local $count i32)
                                    %s
                                    loop $again %s
                                        %s
                                        local.get $count
                                        i32.const 1
                                        i32.add
                                        local.tee $count
                                        i32.const 3
                                        i32.lt_s
                                        if
                                            %s
                                            %s
                                        end
                                    end
                                    local.get $count
                                )
                            )
                            """.formatted(branchOnNull ? "" : "ref.null extern", branchOnNull ? "" : "(param externref)",
                            branchOnNull ? "" : "drop", "nop\n".repeat(stackSize),
                            branchOnNull ? "ref.null extern br_on_null $again drop" : "local.get $ref br_on_non_null $again");
            Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(compileWat("backward-reference-branch", wat)), "backward-reference-branch").build();
            try (Context context = contextBuilder().build()) {
                Value module = context.eval(source);
                for (int instance = 0; instance < 2; instance++) {
                    Assert.assertEquals(3, module.newInstance().getMember("exports").getMember("test").execute(new Object()).asInt());
                }
            }
        }
    }

    private Context.Builder contextBuilder() {
        Context.Builder builder = Context.newBuilder(WasmLanguage.ID).allowExperimentalOptions(true);
        builder.option("engine.Compilation", Boolean.toString(compiled));
        builder.option("engine.CompileImmediately", Boolean.toString(compiled));
        builder.option("engine.BackgroundCompilation", "false");
        builder.option("engine.CompilationFailureAction", "Throw");
        return builder;
    }

    private void run(String body, boolean legacyExceptions) throws IOException, InterruptedException {
        String wat = """
                        (module
                            (tag $tag (param i32 externref))
                            (func (export "test") (param $choice i32) (param $ref externref) (result i32 externref)
                                (local $caught externref)
                                %s
                                %s
                                local.set $caught
                                %s
                                local.get $caught
                            )
                        )
                        """.formatted("i32.const 7\n".repeat(stackSize), body, "i32.add\n".repeat(stackSize));
        ByteSequence binary = ByteSequence.create(compileWat("branch-target", wat, EnumSet.of(WabtOption.EXCEPTIONS, WabtOption.FUNCTION_REFERENCES)));
        Source source = Source.newBuilder(WasmLanguage.ID, binary, "branch-target").build();
        Context.Builder builder = contextBuilder();
        builder.option("wasm.LegacyExceptions", Boolean.toString(legacyExceptions));
        try (Context context = builder.build()) {
            Value module = context.eval(source);
            Object sentinel = new Object();
            for (int instance = 0; instance < 2; instance++) {
                Value function = module.newInstance().getMember("exports").getMember("test");
                for (int choice : new int[]{0, 1, 2, -1}) {
                    Value result = function.execute(choice, sentinel);
                    Assert.assertEquals(42 + 7 * stackSize, result.getArrayElement(0).asInt());
                    Assert.assertSame(sentinel, result.getArrayElement(1).asHostObject());
                }
            }
        }
    }
}
