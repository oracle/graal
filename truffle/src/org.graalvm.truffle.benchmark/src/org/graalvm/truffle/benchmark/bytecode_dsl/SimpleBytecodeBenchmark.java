/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode_dsl;

import static org.graalvm.truffle.benchmark.bytecode_dsl.BenchmarkLanguage.createBytecodeDSLNodes;
import static org.graalvm.truffle.benchmark.bytecode_dsl.BenchmarkLanguage.createBytecodeNodes;

import org.graalvm.polyglot.Context;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.Builder;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.BytecodeInterpreterAllOpts;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.BytecodeInterpreterNoOpts;
import org.graalvm.truffle.benchmark.bytecode_dsl.specs.BenchmarkSpec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;

import com.oracle.truffle.api.CallTarget;

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class SimpleBytecodeBenchmark extends AbstractBytecodeBenchmark {

    @Param({"NestedLoopBenchmark", "CalculatorBenchmark"}) private String benchmarkSpecClass;
    private BenchmarkSpec benchmarkSpec;
    private Context context;
    // The call target under test (populated during setup with the correct call target)
    private CallTarget callTarget;
    // For expected result checking, when enabled.
    private Object result;

    @Override
    protected String getBenchmarkSpecClassName() {
        return benchmarkSpecClass;
    }

    @Setup(Level.Trial)
    public void setup(BenchmarkParams params) {
        context = Context.newBuilder("bm").allowExperimentalOptions(true).build();

        benchmarkSpec = getBenchmarkSpec();
        String benchMethod = getBenchmarkMethod(params);
        callTarget = switch (benchMethod) {
            case "bytecodeDSLNoOpts" -> createBytecodeDSLNodes(BytecodeDSLBenchmarkRootNodeNoOpts.class, null, benchmarkSpec::parseBytecodeDSL).getNodes().getLast().getCallTarget();
            case "bytecodeDSLAllOpts" -> createBytecodeDSLNodes(BytecodeDSLBenchmarkRootNodeAllOpts.class, null, benchmarkSpec::parseBytecodeDSL).getNodes().getLast().getCallTarget();
            case "bytecodeDSLUncached" -> {
                var node = createBytecodeDSLNodes(BytecodeDSLBenchmarkRootNodeUncached.class, null, benchmarkSpec::parseBytecodeDSL).getNodes().getLast();
                node.getBytecodeNode().setUncachedThreshold(Integer.MIN_VALUE);
                yield node.getCallTarget();
            }
            case "manualNoOpts" -> {
                var builder = Builder.newBuilder();
                benchmarkSpec.parseBytecode(builder);
                yield createBytecodeNodes(BytecodeInterpreterNoOpts.class, null, builder).getCallTarget();
            }
            case "manualAllOpts" -> {
                var builder = Builder.newBuilder();
                benchmarkSpec.parseBytecode(builder);
                yield createBytecodeNodes(BytecodeInterpreterAllOpts.class, null, builder).getCallTarget();
            }
            case "ast" -> benchmarkSpec.parseAST(null);
            default -> throw new AssertionError("Unexpected benchmark method " + benchMethod);
        };
    }

    @Setup(Level.Iteration)
    public void enterContext() {
        context.enter();
    }

    @TearDown(Level.Iteration)
    public void leaveContext(BenchmarkParams params) {
        checkExpectedResult(result, getBenchmarkMethod(params));
        context.leave();
    }

    private void benchmark(CallTarget ct) {
        result = ct.call(benchmarkSpec.arguments());
    }

    @Benchmark
    public void bytecodeDSLNoOpts() {
        benchmark(callTarget);
    }

    @Benchmark
    public void bytecodeDSLAllOpts() {
        benchmark(callTarget);
    }

    @Benchmark
    public void bytecodeDSLUncached() {
        benchmark(callTarget);
    }

    @Benchmark
    public void manualNoOpts() {
        benchmark(callTarget);
    }

    @Benchmark
    public void manualAllOpts() {
        benchmark(callTarget);
    }

    @Benchmark
    public void ast() {
        benchmark(callTarget);
    }

}
