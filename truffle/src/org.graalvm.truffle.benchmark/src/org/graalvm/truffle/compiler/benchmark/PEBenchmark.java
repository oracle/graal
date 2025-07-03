/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.compiler.benchmark;

import org.graalvm.polyglot.Context;
import org.graalvm.truffle.benchmark.bytecode_dsl.AbstractBytecodeBenchmark;
import org.graalvm.truffle.benchmark.bytecode_dsl.BytecodeDSLBenchmarkRootNodeAllOpts;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.Builder;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.BytecodeInterpreterAllOpts;
import org.graalvm.truffle.benchmark.bytecode_dsl.specs.BenchmarkSpec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.runtime.OptimizedCallTarget;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 2)
@Measurement(iterations = 10)
@Fork(value = 10, jvmArgsAppend = {"-Djdk.graal.SystemicCompilationFailureRate=0"})
public class PEBenchmark extends AbstractBytecodeBenchmark {

    // Instead of registering sources, manually create call targets: we need access to them to force
    // compile and invalidate.
    private CallTarget bytecodeDSLCallTarget;
    private CallTarget bytecodeCallTarget;
    private CallTarget astCallTarget;

    @Param({"NestedLoopBenchmark", "CalculatorBenchmark"}) private String benchmarkSpecClass;
    @Param({"1", "2"}) private int tier;

    private Context context;

    @Override
    protected String getBenchmarkSpecClassName() {
        return benchmarkSpecClass;
    }

    @Setup(Level.Trial)
    public void setup(BenchmarkParams params) {
        Context.Builder builder = Context.newBuilder("bm").allowExperimentalOptions(true);
        builder.option("engine.BackgroundCompilation", Boolean.FALSE.toString());
        builder.option("engine.MaximumCompilations", "-1"); // no limit
        context = builder.build();
        context.enter();

        BenchmarkSpec spec = getBenchmarkSpec();
        String benchMethod = getBenchmarkMethod(params);
        switch (benchMethod) {
            case "ast" -> {
                astCallTarget = spec.parseAST(null);
                initializeCallTarget(astCallTarget, spec, "ast");
            }
            case "manual" -> {
                var bytecodeBuilder = Builder.newBuilder();
                spec.parseBytecode(bytecodeBuilder);
                bytecodeCallTarget = BytecodeInterpreterAllOpts.create(null, bytecodeBuilder).getCallTarget();
                initializeCallTarget(bytecodeCallTarget, spec, "manual");
            }
            case "bytecodeDSL" -> {
                var bytecodeDSLRootNodes = BytecodeDSLBenchmarkRootNodeAllOpts.create(null, BytecodeConfig.DEFAULT, spec::parseBytecodeDSL);
                bytecodeDSLCallTarget = bytecodeDSLRootNodes.getNodes().getLast().getCallTarget();
                initializeCallTarget(bytecodeDSLCallTarget, spec, "bytecodeDSL");
            }
            default -> throw new AssertionError("Unexpected benchmark method " + benchMethod);
        }
    }

    /**
     * Run the call target several times to ensure profiles are stable for compilation.
     */
    private void initializeCallTarget(CallTarget ct, BenchmarkSpec spec, String benchMethod) {
        for (int i = 0; i < 100; i++) {
            int result = (int) ct.call(spec.arguments());
            checkExpectedResult(result, benchMethod);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        context.leave();
    }

    // NB: Level.Invocation is discouraged for benchmark methods that take <1ms to run. This is OK
    // as long as our compilations take longer than that. :-)
    @TearDown(Level.Invocation)
    public void invalidateCallTarget() {
        for (CallTarget ct : new CallTarget[]{astCallTarget, bytecodeCallTarget, bytecodeDSLCallTarget}) {
            if (ct == null) {
                continue;
            }
            ((OptimizedCallTarget) ct).invalidate("benchmarking");
        }
    }

    private void benchmarkCompilation(CallTarget ct) {
        ((OptimizedCallTarget) ct).compile(tier == 2);
    }

    @Benchmark
    public void bytecodeDSL() {
        benchmarkCompilation(bytecodeDSLCallTarget);
    }

    @Benchmark
    public void manual() {
        benchmarkCompilation(bytecodeCallTarget);
    }

    @Benchmark
    public void ast() {
        benchmarkCompilation(astCallTarget);
    }

}
