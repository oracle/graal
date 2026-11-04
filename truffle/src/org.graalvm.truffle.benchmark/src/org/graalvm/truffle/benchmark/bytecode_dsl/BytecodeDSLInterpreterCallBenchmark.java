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
package org.graalvm.truffle.benchmark.bytecode_dsl;

import static org.graalvm.truffle.benchmark.bytecode_dsl.BenchmarkLanguage.createBytecodeDSLNodes;

import org.graalvm.polyglot.Context;
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

/**
 * Same recursive workload and interpreter configuration for each call API. The indexed API pairs
 * differ only in whether the caller BCI is -1 or the actual instruction index. The non-indexed
 * methods provide non-specialized baselines. Every measured interpreter starts in the cached tier.
 * Explicit BCI propagation uses NONE mode, without implicit lookup or frame stores.
 */
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class BytecodeDSLInterpreterCallBenchmark extends AbstractBytecodeBenchmark {

    @Param({"RecursiveFibonacciBenchmark"}) private String benchmarkSpecClass;
    @Param({"interpreter", "default"}) private String mode;
    @Param({"none", "frame"}) private String bytecodeIndexMode;
    private Context context;
    private CallTarget callTarget;
    private Object[] arguments;
    private Object result;

    @Override
    protected String getBenchmarkSpecClassName() {
        return benchmarkSpecClass;
    }

    @Setup(Level.Trial)
    public void setup(BenchmarkParams params) {
        Context.Builder b = Context.newBuilder("bm").allowExperimentalOptions(true);
        switch (mode) {
            case "interpreter" -> b.option("engine.Compilation", "false");
            case "default" -> b.option("engine.Compilation", "true");
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
        context = b.build();
        boolean setupComplete = false;
        try {
            BenchmarkSpec benchmarkSpec = getBenchmarkSpec();
            String benchMethod = getBenchmarkMethod(params);
            arguments = benchmarkSpec.arguments();
            context.enter();
            try {
                BytecodeDSLBenchmarkRootNodeBuilder.BytecodeVariant variant = switch (bytecodeIndexMode) {
                    case "none" -> BytecodeDSLBenchmarkRootNodeTailCallExplicitBciAllOpts.BYTECODE;
                    case "frame" -> BytecodeDSLBenchmarkRootNodeTailCallStoredBciAllOpts.BYTECODE;
                    default -> throw new IllegalArgumentException("unknown bytecode index mode " + bytecodeIndexMode);
                };
                BytecodeDSLBenchmarkRootNode root = createBytecodeDSLNodes(variant, null,
                                b1 -> benchmarkSpec.parseBytecodeDSL(b1, benchMethod)).getNodes().getLast();
                root.getBytecodeNode().setUncachedThreshold(0);
                callTarget = root.getCallTarget();
            } finally {
                context.leave();
            }
            setupComplete = true;
        } finally {
            if (!setupComplete) {
                context.close();
            }
        }
    }

    @Setup(Level.Iteration)
    public void enterContext() {
        context.enter();
    }

    @TearDown(Level.Iteration)
    public void leaveContext(BenchmarkParams params) {
        try {
            checkExpectedResult(result, getBenchmarkMethod(params));
        } finally {
            context.leave();
        }
    }

    @TearDown(Level.Trial)
    public void closeContext() {
        context.close();
    }

    @Benchmark
    public void bytecodeDSLDirectCall() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLIndirectCall() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLCallTarget() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLCallTargetImplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLCallTargetExplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLDirectCallImplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLDirectCallExplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLIndirectCallImplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLIndirectCallExplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLDirectCallWithRootNodeImplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLDirectCallWithRootNodeExplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLIndirectCallWithRootNodeImplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLIndirectCallWithRootNodeExplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLRootNodeCallImplicitBci() {
        result = callTarget.call(arguments);
    }

    @Benchmark
    public void bytecodeDSLRootNodeCallExplicitBci() {
        result = callTarget.call(arguments);
    }

}
