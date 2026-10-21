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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.graalvm.wasm.utils.WasmBinaryTools.WabtOption;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Exercises shared branch and exception destinations in interpreted and compiled execution. Stack
 * heights cover all label widths; repeated instantiation also exercises bytecode metadata rereading.
 */
@RunWith(Parameterized.class)
public class WasmBranchTargetSuite extends AbstractBinarySuite {
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

    @Before
    public void requireCompilationSupport() {
        Assume.assumeTrue("Optimizing runtime is not available", !compiled || Engine.supportsCompilation());
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

    /**
     * Checks that a legacy typed catch receives its numeric and reference payload after unwinding
     * throws from different operand-stack heights.
     */
    @Test
    public void testLegacyCatch() throws IOException, InterruptedException {
        run("""
                        try (result i32 externref)
                            %s
                        catch $tag
                        end
                        """.formatted(THROW_AT_DIFFERENT_STACK_HEIGHTS));
    }

    /**
     * Checks that taken branches and ordinary label fallthrough preserve mixed numeric/reference
     * results for small and large result arities, with legacy EH enabled and disabled.
     */
    @Test
    public void testMixedResultBranchAndLabelFallthrough() throws IOException, InterruptedException {
        for (int pairs : new int[]{1, 32}) {
            String results = "i32.const 42 local.get $ref\n".repeat(pairs);
            String body = """
                            block $target (result %s)
                                local.get $choice
                                if
                                    ref.null extern
                                    %s
                                    br $target
                                end
                                %s
                            end
                            %s
                            """.formatted("i32 externref ".repeat(pairs), results, results, "drop\n".repeat(2 * pairs - 2));
            run(body, false);
            run(body, true);
        }
    }

    /**
     * Checks taken and untaken branches inside a legacy catch: taken branches unwind through the
     * target label, while fallthrough skips the label and its catch-unwind metadata.
     */
    @Test
    public void testLabelFallthroughInLegacyCatch() throws IOException, InterruptedException {
        // Taken branches normalize in the interpreter label or compiled branch handler.
        // Legacy fallthrough skips both the label and its catch-unwind metadata.
        run("""
                        try (result i32 externref)
                            i32.const 42
                            local.get $ref
                            throw $tag
                        catch $tag
                            local.set $caught
                            drop
                            block $target
                                local.get $choice
                                br_if $target
                            end
                            i32.const 42
                            local.get $caught
                        end
                        """);
    }

    /**
     * Checks that an exception leaving a nested catch releases its catch slot before the enclosing
     * handler stores the newly caught exception in that slot.
     */
    @Test
    public void testLegacyCatchUnwindsToEnclosingCatch() throws IOException, InterruptedException {
        runNestedLegacyCatch(false);
    }

    /**
     * Checks that replacing an exited nested catch does not clear a still-active outer catch;
     * rethrow must recover the outer exception and its original payload.
     */
    @Test
    public void testLegacyCatchUnwindsAndPreservesOuterCatch() throws IOException, InterruptedException {
        runNestedLegacyCatch(true);
    }

    private void runNestedLegacyCatch(boolean preserveOuterCatch) throws IOException, InterruptedException {
        // The nested catch and its enclosing handler share one reserved catch slot. The transfer
        // must unwind the nested catch before entering the enclosing handler, not after pushing
        // another exception into the reserved area.
        String instructions = """
                        try (result i32 externref)
                            try
                                i32.const 0
                                local.get $ref
                                throw $tag
                            catch_all
                                %s
                            end
                            unreachable
                        catch $tag
                        end
                        """.formatted(THROW_AT_DIFFERENT_STACK_HEIGHTS);
        if (preserveOuterCatch) {
            // Give the inner exception a different payload from the outer exception below.
            instructions = instructions.replace("i32.const 42", "i32.const 99").replace("local.get $ref", "ref.null extern");
            // Keep an outer catch active during the transfer, then rethrow its exception to verify
            // that unwinding discarded only the exited catch's slot.
            instructions = """
                            try (result i32 externref)
                                i32.const 42
                                local.get $ref
                                throw $tag
                            catch $tag
                                drop
                                drop
                                %s
                                drop
                                drop
                                try (result i32 externref)
                                    rethrow 1
                                catch $tag
                                end
                            end
                            """.formatted(instructions);
        }
        run(instructions);
    }

    /**
     * Checks that legacy catch_all discards the payload and excess operands from throws at
     * different stack heights, preserving the stack below the handler.
     */
    @Test
    public void testLegacyCatchAll() throws IOException, InterruptedException {
        run("""
                        try (result i32 externref)
                            %s
                        catch_all
                            i32.const 42
                            local.get $ref
                        end
                        """.formatted(THROW_AT_DIFFERENT_STACK_HEIGHTS));
    }

    /**
     * Checks that an explicit branch and a modern catch reach the same target with identical
     * numeric/reference results and normalized operand-stack state.
     */
    @Test
    public void testBranchAndCatchShareTarget() throws IOException, InterruptedException {
        run("""
                        block $target (result i32 externref)
                            try_table (catch $tag $target)
                                local.get $choice
                                if
                                    i64.const 99
                                    i32.const 42
                                    local.get $ref
                                    br $target
                                end
                                i32.const 42
                                local.get $ref
                                throw $tag
                            end
                            unreachable
                        end
                        """);
    }

    /**
     * Checks convergence of an explicit branch, exception transfer, and normal fallthrough at one
     * destination, with identical results and normalized stack state.
     */
    @Test
    public void testBranchCatchAndFallthroughShareTarget() throws IOException, InterruptedException {
        run("""
                        block $target (result i32 externref)
                            try_table (result i32 externref) (catch $tag $target)
                                local.get $choice
                                i32.eqz
                                if
                                    i64.const 99
                                    i32.const 42
                                    local.get $ref
                                    br $target
                                end
                                local.get $choice
                                i32.const 1
                                i32.eq
                                if
                                    i64.const 99
                                    i32.const 42
                                    local.get $ref
                                    throw $tag
                                end
                                i32.const 42
                                local.get $ref
                            end
                        end
                        """);
    }

    /**
     * Checks that a branch leaving the last active legacy catch normalizes catch depth to zero
     * before joining a path that never entered the catch.
     */
    @Test
    public void testBranchOutOfLegacyCatch() throws IOException, InterruptedException {
        run("""
                        block $target (result i32 externref)
                            local.get $choice
                            if
                                try
                                    i32.const 42
                                    local.get $ref
                                    throw $tag
                                catch $tag
                                    br $target
                                end
                            end
                            i32.const 42
                            local.get $ref
                        end
                        """);
    }

    /**
     * Checks catch-exiting backedges to a depth-zero loop header emitted before the catch is
     * encountered; the header must support explicit cleanup to zero. Inputs 0, 1, and 2 exercise
     * zero, one, and two catch-exiting backedges, respectively.
     */
    @Test
    public void testLegacyCatchBranchToZeroDepthLoop() throws IOException, InterruptedException {
        // The depth-zero loop label is emitted before the catch that branches back to it.
        run("""
                        block $done
                            loop $again
                                local.get $choice
                                i32.const 0
                                i32.le_s
                                br_if $done
                                try
                                    i32.const 42
                                    local.get $ref
                                    throw $tag
                                catch_all
                                    local.get $choice
                                    i32.const 1
                                    i32.sub
                                    local.set $choice
                                    br $again
                                end
                            end
                        end
                        i32.const 42
                        local.get $ref
                        """, true, 0, 1, 2);
    }

    /**
     * Checks br, br_if, br_table, and br_on_null backedges that exit a legacy catch while carrying
     * mixed loop parameters. Unwinding must use the parameter signature, not the different loop
     * result signature. Inputs 0, 1, and 2 exercise zero, one, and two catch-exiting backedges,
     * respectively.
     */
    @Test
    public void testLegacyCatchExitToLoopParameters() throws IOException, InterruptedException {
        for (String branch : new String[]{
                        "br $again",
                        "i32.const 1 br_if $again drop drop",
                        "i32.const 0 br_table $again $again",
                        "ref.null extern br_on_null $again drop drop drop"}) {
            // Catch-exiting branches carry mixed parameters back to the loop header for cleanup.
            // Normal loop exit carries a different result signature.
            run("""
                            i32.const 42
                            local.get $ref
                            loop $again (param i32 externref) (result i32)
                                drop
                                drop
                                local.get $choice
                                i32.const 0
                                i32.gt_s
                                if
                                    try
                                        i32.const 42
                                        local.get $ref
                                        throw $tag
                                    catch $tag
                                        local.get $choice
                                        i32.const 1
                                        i32.sub
                                        local.set $choice
                                        %s
                                    end
                                end
                                i32.const 42
                            end
                            local.get $ref
                            """.formatted(branch), true, 0, 1, 2);
        }
    }

    /**
     * Checks that the synthetic jump over an else clause has a valid label destination even when
     * neither arm contains an explicit branch.
     */
    @Test
    public void testIfElseWithoutExplicitBranches() throws IOException, InterruptedException {
        run("""
                        local.get $choice
                        if (result i32)
                            i32.const 42
                        else
                            i32.const 42
                        end
                        local.get $ref
                        """);
    }

    /**
     * Checks both br_if paths: a taken branch preserves mixed results while dropping an extra
     * operand, and fallthrough retains the operands for explicit cleanup.
     */
    @Test
    public void testConditionalBranch() throws IOException, InterruptedException {
        run("""
                        block $target (result i32 externref)
                            i64.const 99
                            i32.const 42
                            local.get $ref
                            local.get $choice
                            br_if $target
                            drop
                            drop
                            drop
                            i32.const 42
                            local.get $ref
                        end
                        """);
    }

    /**
     * Checks explicit and default br_table entries targeting nested blocks at different stack
     * heights, preserving mixed results while dropping target-specific excess operands. Inputs
     * 0 and 1 select the two explicit entries; input 2 selects the default.
     */
    @Test
    public void testBranchTable() throws IOException, InterruptedException {
        run("""
                        block $outer (result i32 externref)
                            i32.const 0
                            block $inner (result i32 externref)
                                i64.const 99
                                i32.const 42
                                local.get $ref
                                local.get $choice
                                br_table $inner $outer $outer
                            end
                            local.set $caught
                            i32.add
                            local.get $caught
                        end
                        """, true, 0, 1, 2);
    }

    /**
     * Checks that both explicit and default br_table entries can exit the last legacy catch and
     * unwind its state while preserving the branch results.
     */
    @Test
    public void testBranchTableOutOfLegacyCatch() throws IOException, InterruptedException {
        run("""
                        block $target (result i32 externref)
                            try (result i32 externref)
                                i32.const 42
                                local.get $ref
                                throw $tag
                            catch $tag
                                local.get $choice
                                br_table $target $target
                            end
                        end
                        """);
    }

    /**
     * Checks that branching from a legacy catch to its own try label exits that catch, matching
     * normal catch fallthrough rather than retaining the handler depth.
     */
    @Test
    public void testLegacyTryBranchTarget() throws IOException, InterruptedException {
        run("""
                        try $target (result i32 externref)
                            i32.const 42
                            local.get $ref
                            throw $tag
                        catch $tag
                            local.get $choice
                            br_if $target
                        end
                        """);
    }

    /**
     * Checks that loop backedges use the reference-only parameter signature rather than the mixed
     * result signature, preserving the reference across another iteration.
     */
    @Test
    public void testLoopWithDifferentParamAndResultTypes() throws IOException, InterruptedException {
        run("""
                        local.get $ref
                        loop $target (param externref) (result i32 externref)
                            local.set $caught
                            local.get $choice
                            if
                                i32.const 0
                                local.set $choice
                                local.get $caught
                                br $target
                            end
                            i32.const 42
                            local.get $caught
                        end
                        """);
    }

    /**
     * Checks taken and untaken br_on_null paths, including consumption of the null reference and
     * unwinding an extra operand on the taken path.
     */
    @Test
    public void testBranchOnNull() throws IOException, InterruptedException {
        run("""
                        block $target (result i32)
                            i64.const 99
                            i32.const 42
                            local.get $choice
                            if (result externref)
                                local.get $ref
                            else
                                ref.null extern
                            end
                            br_on_null $target
                            drop
                            drop
                            drop
                            i32.const 42
                        end
                        local.get $ref
                        """);
    }

    /**
     * Checks taken and untaken br_on_non_null paths, preserving the non-null reference as a branch
     * result while unwinding excess operands.
     */
    @Test
    public void testBranchOnNonNull() throws IOException, InterruptedException {
        run("""
                        block $target (result i32 externref)
                            i64.const 99
                            i32.const 42
                            local.get $choice
                            if (result externref)
                                local.get $ref
                            else
                                ref.null extern
                            end
                            br_on_non_null $target
                            drop
                            drop
                            i32.const 42
                            local.get $ref
                        end
                        """);
    }

    /**
     * Checks both br_on_cast outcomes for nullable function references, preserving the result
     * reference and unwinding an extra operand on the taken path.
     */
    @Test
    public void testBranchOnCast() throws IOException {
        runCast(false);
    }

    /**
     * Checks both br_on_cast_fail outcomes for nullable function references, preserving the result
     * reference and unwinding an extra operand on the taken path.
     */
    @Test
    public void testBranchOnCastFail() throws IOException {
        runCast(true);
    }

    private void runCast(boolean fail) throws IOException {
        Source source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(createCastModule(fail)), "cast-branch").build();
        Context.Builder builder = contextBuilder();
        builder.option("wasm.GC", "true");
        try (Context context = builder.build()) {
            Value module = context.eval(source);
            for (int instance = 0; instance < 2; instance++) {
                Value function = module.newInstance().getMember("exports").getMember("test");
                for (int choice : new int[]{0, 1}) {
                    Assert.assertEquals(42 + 7 * stackSize + (choice == 0 ? 1 : 0), function.execute(choice).asInt());
                }
            }
        }
    }

    /**
     * Builds the module below. Repeat the marked padding instructions {@code stackSize} times, and
     * replace {@code br_on_cast} with {@code br_on_cast_fail} when {@code fail} is true.
     *
     * <pre>
     * (module
     *   (func $test (export "test") (param $choice i32) (result i32) (local $ref funcref)
     *     i32.const 7 ;; repeat stackSize times
     *     block $target (result funcref)
     *       i64.const 99
     *       local.get $choice
     *       if (result funcref)
     *         ref.func $test
     *       else
     *         ref.null func
     *       end
     *       br_on_cast[_fail] $target (ref null func) (ref func)
     *       local.set $ref
     *       drop
     *       local.get $ref
     *     end
     *     ref.is_null
     *     i32.const 42
     *     i32.add
     *     i32.add ;; repeat stackSize times
     *   )
     * )
     * </pre>
     */
    private byte[] createCastModule(boolean fail) {
        String code = "41 07 ".repeat(stackSize) +
                        "02 70 42 E3 00 20 00 " +
                        "04 70 D2 00 05 D0 70 0B " +
                        "FB " + (fail ? "19" : "18") + " 01 00 70 70 " +
                        "21 01 1A 20 01 0B " +
                        "D1 41 2A 6A " + "6A ".repeat(stackSize) + "0B";
        return newBuilder().//
                        addType(new int[]{WasmType.I32_TYPE}, new int[]{WasmType.I32_TYPE}).//
                        addFunction(0, new int[]{WasmType.FUNCREF_TYPE}, code).//
                        addFunctionExport(0, "test").//
                        build();
    }

    private Context.Builder contextBuilder() {
        Context.Builder builder = Context.newBuilder(WasmLanguage.ID).allowExperimentalOptions(true);
        if (Engine.supportsCompilation()) {
            builder.option("engine.Compilation", Boolean.toString(compiled));
            builder.option("engine.CompileImmediately", Boolean.toString(compiled));
            builder.option("engine.BackgroundCompilation", "false");
            builder.option("engine.CompilationFailureAction", "Throw");
        }
        return builder;
    }

    private void run(String body) throws IOException, InterruptedException {
        run(body, true);
    }

    private void run(String body, boolean legacyExceptions) throws IOException, InterruptedException {
        run(body, legacyExceptions, 0, 1);
    }

    private void run(String body, boolean legacyExceptions, int... choices) throws IOException, InterruptedException {
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
                for (int choice : choices) {
                    Value result = function.execute(choice, sentinel);
                    Assert.assertEquals(42 + 7 * stackSize, result.getArrayElement(0).asInt());
                    Assert.assertSame(sentinel, result.getArrayElement(1).asHostObject());
                }
            }
        }
    }
}
