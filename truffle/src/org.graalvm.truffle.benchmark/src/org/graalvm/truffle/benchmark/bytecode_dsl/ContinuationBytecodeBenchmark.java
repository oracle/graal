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

import org.graalvm.polyglot.Context;
import org.graalvm.truffle.benchmark.TruffleBenchmark;
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class ContinuationBytecodeBenchmark extends TruffleBenchmark {

    private static final int LOCAL_LOOP_ITERS = 4096;
    private static final int YIELD_LOOP_ITERS = 1024;
    private static final int BIG_FRAME_LOCAL_COUNT = 1024;
    private static final int SUM16_OPERAND_COUNT = 16;
    private static final int DEEP_STACK_SUM16_DEPTH = 17;
    private static final int RESUME_INPUT = 1;

    @Param({"interpreter", "default"}) private String mode;
    @Param({"NoOpts", "AllOpts", "ThreadedAllOpts", "TailCallAllOpts"}) private String variant;

    private Context context;
    private CallTarget localLoopBaseline;
    private CallTarget localLoopResumedProducer;
    private CallTarget stackLoopBaseline;
    private CallTarget stackLoopResumedProducer;
    private CallTarget yieldResumeLoopBigFrameProducer;
    private CallTarget yieldResumeLoopDeepStackProducer;
    private Object result;

    @Setup(Level.Trial)
    public void setup() {
        Context.Builder builder = Context.newBuilder("bm").allowExperimentalOptions(true);
        switch (mode) {
            case "interpreter" -> builder.option("engine.Compilation", "false");
            case "default" -> builder.option("engine.Compilation", "true");
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
        context = builder.build();

        context.enter();
        BytecodeRootNodes<ContinuationBenchmarkRootNode> nodes = getVariant().create(null, BytecodeConfig.DEFAULT, b -> {
            buildLocalLoopBaselineRoot(b);
            buildLocalLoopResumedRoot(b);
            buildStackLoopBaselineRoot(b);
            buildStackLoopResumedRoot(b);
            buildYieldResumeLoopBigFrameRoot(b);
            buildYieldResumeLoopDeepStackRoot(b);
        });
        localLoopBaseline = nodes.getNode(0).getCallTarget();
        localLoopResumedProducer = nodes.getNode(1).getCallTarget();
        stackLoopBaseline = nodes.getNode(2).getCallTarget();
        stackLoopResumedProducer = nodes.getNode(3).getCallTarget();
        yieldResumeLoopBigFrameProducer = nodes.getNode(4).getCallTarget();
        yieldResumeLoopDeepStackProducer = nodes.getNode(5).getCallTarget();
        context.leave();
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

    private ContinuationBenchmarkRootNodeBuilder.BytecodeVariant getVariant() {
        return switch (variant) {
            case "NoOpts" -> ContinuationBenchmarkRootNodeNoOpts.BYTECODE;
            case "AllOpts" -> ContinuationBenchmarkRootNodeAllOpts.BYTECODE;
            case "ThreadedAllOpts" -> ContinuationBenchmarkRootNodeThreadedAllOpts.BYTECODE;
            case "TailCallAllOpts" -> ContinuationBenchmarkRootNodeTailCallAllOpts.BYTECODE;
            default -> throw new IllegalArgumentException("unknown variant " + variant);
        };
    }

    private void checkExpectedResult(Object actualResult, String benchMethod) {
        if (System.getProperty("CheckResults") == null) {
            return;
        }
        Object expected = switch (benchMethod) {
            case "localLoopBaseline", "localLoopResumed" -> expectedLocalLoopResult();
            case "stackLoopBaseline", "stackLoopResumed" -> expectedStackLoopResult();
            case "yieldResumeLoopBigFrame" -> expectedBigFrameResult();
            case "yieldResumeLoopDeepStack" -> expectedDeepStackResult();
            default -> throw new AssertionError("Unexpected benchmark method " + benchMethod);
        };
        if (!expected.equals(actualResult)) {
            throw new AssertionError(benchMethod + " produced the wrong result. Received " + actualResult + " but expected " + expected);
        }
    }

    private void benchmark(CallTarget callTarget, int iterations) {
        result = callTarget.call(iterations);
    }

    private static String getBenchmarkMethod(BenchmarkParams params) {
        String[] parts = params.getBenchmark().split("\\.");
        return parts[parts.length - 1];
    }

    private void benchmarkResumeOnce(CallTarget producer, int iterations) {
        ContinuationResult continuation = (ContinuationResult) producer.call(iterations);
        result = continuation.continueWith(RESUME_INPUT);
    }

    private void benchmarkResumeRepeatedly(CallTarget producer, int iterations) {
        Object current = producer.call(iterations);
        while (current instanceof ContinuationResult continuation) {
            current = continuation.continueWith(RESUME_INPUT);
        }
        result = current;
    }

    /**
     * Baseline local-heavy loop with no yields. See
     * {@link #buildLocalLoopBaselineRoot(ContinuationBenchmarkRootNodeBuilder)}.
     * <p>
     * This benchmark is a baseline measurement for {@link #localLoopResumed}.
     */
    @Benchmark
    public void localLoopBaseline() {
        benchmark(localLoopBaseline, LOCAL_LOOP_ITERS);
    }

    /**
     * Yields once after local initialization, then resumes into the same local-heavy loop as the
     * baseline. See {@link #buildLocalLoopResumedRoot(ContinuationBenchmarkRootNodeBuilder)}.
     * <p>
     * We expect this benchmark to perform worse than {@link #localLoopBaseline} in peak mode
     * because local accesses use a materialized frame.
     */
    @Benchmark
    public void localLoopResumed() {
        benchmarkResumeOnce(localLoopResumedProducer, LOCAL_LOOP_ITERS);
    }

    /**
     * Baseline loop with the hot arithmetic performed on the operand stack. Built by
     * {@link #buildStackLoopBaselineRoot(ContinuationBenchmarkRootNodeBuilder)}.
     * <p>
     * This benchmark is a baseline measurement for {@link #stackLoopResumed}.
     */
    @Benchmark
    public void stackLoopBaseline() {
        benchmark(stackLoopBaseline, LOCAL_LOOP_ITERS);
    }

    /**
     * Yields once before resuming into the same stack-heavy loop as the baseline. Built by
     * {@link #buildStackLoopResumedRoot(ContinuationBenchmarkRootNodeBuilder)}.
     * <p>
     * We expect this benchmark to have comparable performance to {@link #stackLoopBaseline}.
     */
    @Benchmark
    public void stackLoopResumed() {
        benchmarkResumeOnce(stackLoopResumedProducer, LOCAL_LOOP_ITERS);
    }

    /**
     * Yields every iteration with a very wide local frame and minimal post-resume work. See
     * {@link #buildYieldResumeLoopBigFrameRoot(ContinuationBenchmarkRootNodeBuilder)}.
     */
    @Benchmark
    public void yieldResumeLoopBigFrame() {
        benchmarkResumeRepeatedly(yieldResumeLoopBigFrameProducer, YIELD_LOOP_ITERS);
    }

    /**
     * Yields every iteration with a deliberately deep operand stack. See
     * {@link #buildYieldResumeLoopDeepStackRoot(ContinuationBenchmarkRootNodeBuilder)}.
     */
    @Benchmark
    public void yieldResumeLoopDeepStack() {
        benchmarkResumeRepeatedly(yieldResumeLoopDeepStackProducer, YIELD_LOOP_ITERS);
    }

    /**
     * Builds the bytecode for {@link #localLoopBaseline()}. The bytecode should have the same
     * semantics as {@link #expectedLocalLoopResult()}.
     */
    private static void buildLocalLoopBaselineRoot(ContinuationBenchmarkRootNodeBuilder b) {
        b.beginRoot();
        LocalLoopState state = createLocalLoopState(b);
        emitLocalLoopBody(b, state);
        b.beginReturn();
        b.emitLoadLocal(state.sum());
        b.endReturn();
        b.endRoot();
    }

    /**
     * Builds the bytecode for {@link #localLoopResumed()}. The bytecode should have the same
     * semantics as {@link #expectedLocalLoopResult()}.
     */
    private static void buildLocalLoopResumedRoot(ContinuationBenchmarkRootNodeBuilder b) {
        b.beginRoot();
        LocalLoopState state = createLocalLoopState(b);
        b.beginYield();
        b.emitLoadConstant(0);
        b.endYield();
        emitLocalLoopBody(b, state);
        b.beginReturn();
        b.emitLoadLocal(state.sum());
        b.endReturn();
        b.endRoot();
    }

    /**
     * Builds the bytecode for {@link #stackLoopBaseline()}. The bytecode should have the same
     * semantics as {@link #expectedStackLoopResult()}.
     */
    private static void buildStackLoopBaselineRoot(ContinuationBenchmarkRootNodeBuilder b) {
        b.beginRoot();
        StackLoopState state = createStackLoopState(b);
        emitStackLoopBody(b, state);
        b.beginReturn();
        b.emitLoadLocal(state.sum());
        b.endReturn();
        b.endRoot();
    }

    /**
     * Builds the bytecode for {@link #stackLoopResumed()}. The bytecode should have the same
     * semantics as {@link #expectedStackLoopResult()}.
     */
    private static void buildStackLoopResumedRoot(ContinuationBenchmarkRootNodeBuilder b) {
        b.beginRoot();
        StackLoopState state = createStackLoopState(b);
        b.beginYield();
        b.emitLoadConstant(0);
        b.endYield();
        emitStackLoopBody(b, state);
        b.beginReturn();
        b.emitLoadLocal(state.sum());
        b.endReturn();
        b.endRoot();
    }

    /**
     * Allocates and initializes the locals used by the baseline and resumed local-loop workloads.
     */
    private static LocalLoopState createLocalLoopState(ContinuationBenchmarkRootNodeBuilder b) {
        BytecodeLocal i = b.createLocal();
        BytecodeLocal a = b.createLocal();
        BytecodeLocal c = b.createLocal();
        BytecodeLocal d = b.createLocal();
        BytecodeLocal e = b.createLocal();
        BytecodeLocal sum = b.createLocal();

        b.beginStoreLocal(i);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        b.beginStoreLocal(a);
        b.emitLoadConstant(1);
        b.endStoreLocal();

        b.beginStoreLocal(c);
        b.emitLoadConstant(2);
        b.endStoreLocal();

        b.beginStoreLocal(d);
        b.emitLoadConstant(3);
        b.endStoreLocal();

        b.beginStoreLocal(e);
        b.emitLoadConstant(4);
        b.endStoreLocal();

        b.beginStoreLocal(sum);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        return new LocalLoopState(i, a, c, d, e, sum);
    }

    /**
     * Allocates and initializes the locals used by the baseline and resumed stack-loop workloads.
     */
    private static StackLoopState createStackLoopState(ContinuationBenchmarkRootNodeBuilder b) {
        BytecodeLocal i = b.createLocal();
        BytecodeLocal sum = b.createLocal();

        b.beginStoreLocal(i);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        b.beginStoreLocal(sum);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        return new StackLoopState(i, sum);
    }

    /**
     * Emits the shared hot loop body used by the baseline and resumed local-loop workloads.
     */
    private static void emitLocalLoopBody(ContinuationBenchmarkRootNodeBuilder b, LocalLoopState state) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(state.i());
        b.emitLoadArgument(0);
        b.endLess();

        b.beginBlock();
        emitStoreAdd(b, state.a(), state.i(), state.a());
        emitStoreAdd(b, state.c(), state.a(), state.c());
        emitStoreAdd(b, state.d(), state.c(), state.d());
        emitStoreAdd(b, state.e(), state.d(), state.e());
        emitStoreAdd(b, state.sum(), state.e(), state.sum());
        emitStoreAddConst(b, state.i(), state.i(), 1);
        b.endBlock();
        b.endWhile();
    }

    /**
     * Emits the shared hot loop body used by the baseline and resumed stack-loop workloads.
     */
    private static void emitStackLoopBody(ContinuationBenchmarkRootNodeBuilder b, StackLoopState state) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(state.i());
        b.emitLoadArgument(0);
        b.endLess();

        b.beginBlock();
        b.beginStoreLocal(state.sum());
        b.beginAdd();
        b.emitLoadLocal(state.sum());
        b.beginSum16();
        b.emitLoadLocal(state.i());
        for (int i = 1; i < SUM16_OPERAND_COUNT; i++) {
            b.emitOpaqueConstant(i);
        }
        b.endSum16();
        b.endAdd();
        b.endStoreLocal();
        emitStoreAddConst(b, state.i(), state.i(), 1);
        b.endBlock();
        b.endWhile();
    }

    /**
     * Builds the bytecode for {@link #yieldResumeLoopBigFrame()}. The bytecode should have the same
     * semantics as {@link #expectedBigFrameResult()}.
     */
    private static void buildYieldResumeLoopBigFrameRoot(ContinuationBenchmarkRootNodeBuilder b) {
        b.beginRoot();
        BytecodeLocal i = b.createLocal();
        BytecodeLocal sum = b.createLocal();
        BytecodeLocal[] locals = createLocals(b, BIG_FRAME_LOCAL_COUNT);

        b.beginStoreLocal(i);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        b.beginStoreLocal(sum);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        b.beginStoreLocal(locals[BIG_FRAME_LOCAL_COUNT - 1]);
        b.emitLoadConstant(1);
        b.endStoreLocal();

        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(i);
        b.emitLoadArgument(0);
        b.endLess();

        b.beginBlock();
        b.beginYield();
        b.emitLoadConstant(0);
        b.endYield();

        emitStoreAddConst(b, locals[BIG_FRAME_LOCAL_COUNT - 1], locals[BIG_FRAME_LOCAL_COUNT - 1], 1);
        emitStoreAdd(b, sum, sum, locals[BIG_FRAME_LOCAL_COUNT - 1]);
        emitStoreAddConst(b, i, i, 1);
        b.endBlock();
        b.endWhile();

        b.beginReturn();
        b.emitLoadLocal(sum);
        b.endReturn();
        b.endRoot();
    }

    /**
     * Builds the bytecode for {@link #yieldResumeLoopDeepStack()}. The bytecode should have the
     * same semantics as {@link #expectedDeepStackResult()}.
     */
    private static void buildYieldResumeLoopDeepStackRoot(ContinuationBenchmarkRootNodeBuilder b) {
        b.beginRoot();
        BytecodeLocal i = b.createLocal();
        BytecodeLocal sum = b.createLocal();

        b.beginStoreLocal(i);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        b.beginStoreLocal(sum);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(i);
        b.emitLoadArgument(0);
        b.endLess();

        b.beginBlock();
        b.beginStoreLocal(sum);
        b.beginAdd();
        b.emitLoadLocal(sum);
        emitDeepStackExpression(b, DEEP_STACK_SUM16_DEPTH);
        b.endAdd();
        b.endStoreLocal();
        emitStoreAddConst(b, i, i, 1);
        b.endBlock();
        b.endWhile();

        b.beginReturn();
        b.emitLoadLocal(sum);
        b.endReturn();
        b.endRoot();
    }

    /**
     * Allocates a fixed number of anonymous locals for a workload.
     */
    private static BytecodeLocal[] createLocals(ContinuationBenchmarkRootNodeBuilder b, int count) {
        BytecodeLocal[] locals = new BytecodeLocal[count];
        for (int i = 0; i < count; i++) {
            locals[i] = b.createLocal();
        }
        return locals;
    }

    /**
     * Stores the sum of two locals into a target local.
     */
    private static void emitStoreAdd(ContinuationBenchmarkRootNodeBuilder b, BytecodeLocal target, BytecodeLocal left, BytecodeLocal right) {
        b.beginStoreLocal(target);
        b.beginAdd();
        b.emitLoadLocal(left);
        b.emitLoadLocal(right);
        b.endAdd();
        b.endStoreLocal();
    }

    /**
     * Adds a constant to a local and stores the result.
     */
    private static void emitStoreAddConst(ContinuationBenchmarkRootNodeBuilder b, BytecodeLocal target, BytecodeLocal left, int constant) {
        b.beginStoreLocal(target);
        b.beginAddConst(constant);
        b.emitLoadLocal(left);
        b.endAddConst();
        b.endStoreLocal();
    }

    /**
     * Emits a nested {@link ContinuationBenchmarkRootNode.Sum16} tree whose deepest last operand is
     * a yield, keeping 15 operands live per nesting level.
     */
    private static void emitDeepStackExpression(ContinuationBenchmarkRootNodeBuilder b, int depth) {
        b.beginSum16();
        for (int constant = 1; constant < SUM16_OPERAND_COUNT; constant++) {
            b.emitOpaqueConstant(constant);
        }
        if (depth == 1) {
            b.beginYield();
            b.emitLoadConstant(0);
            b.endYield();
        } else {
            emitDeepStackExpression(b, depth - 1);
        }
        b.endSum16();
    }

    /**
     * Java version of the computation performed by
     * {@link #buildLocalLoopBaselineRoot(ContinuationBenchmarkRootNodeBuilder)} and
     * {@link #buildLocalLoopResumedRoot(ContinuationBenchmarkRootNodeBuilder)}.
     */
    private static Integer expectedLocalLoopResult() {
        int i = 0;
        int a = 1;
        int c = 2;
        int d = 3;
        int e = 4;
        int sum = 0;
        while (i < LOCAL_LOOP_ITERS) {
            a = a + i;
            c = c + a;
            d = d + c;
            e = e + d;
            sum = sum + e;
            i++;
        }
        return sum;
    }

    /**
     * Java version of the computation performed by
     * {@link #buildStackLoopBaselineRoot(ContinuationBenchmarkRootNodeBuilder)} and
     * {@link #buildStackLoopResumedRoot(ContinuationBenchmarkRootNodeBuilder)}.
     */
    private static Integer expectedStackLoopResult() {
        int i = 0;
        int sum = 0;
        while (i < LOCAL_LOOP_ITERS) {
            int stack = i;
            for (int value = 1; value < SUM16_OPERAND_COUNT; value++) {
                stack += value;
            }
            sum += stack;
            i++;
        }
        return sum;
    }

    /**
     * Java version of the computation performed by
     * {@link #buildYieldResumeLoopBigFrameRoot(ContinuationBenchmarkRootNodeBuilder)}.
     */
    private static Integer expectedBigFrameResult() {
        int i = 0;
        int sum = 0;
        int[] locals = new int[BIG_FRAME_LOCAL_COUNT];
        locals[BIG_FRAME_LOCAL_COUNT - 1] = 1;
        while (i < YIELD_LOOP_ITERS) {
            locals[BIG_FRAME_LOCAL_COUNT - 1] = locals[BIG_FRAME_LOCAL_COUNT - 1] + 1;
            sum = sum + locals[BIG_FRAME_LOCAL_COUNT - 1];
            i++;
        }
        return sum;
    }

    /**
     * Java version of the computation performed by
     * {@link #buildYieldResumeLoopDeepStackRoot(ContinuationBenchmarkRootNodeBuilder)}.
     */
    private static Integer expectedDeepStackResult() {
        int i = 0;
        int sum = 0;
        while (i < YIELD_LOOP_ITERS) {
            sum = sum + expectedDeepStackValue();
            i++;
        }
        return sum;
    }

    /**
     * Java version of {@link #emitDeepStackExpression(ContinuationBenchmarkRootNodeBuilder, int)}.
     */
    private static int expectedDeepStackValue() {
        int value = RESUME_INPUT;
        for (int depth = 0; depth < DEEP_STACK_SUM16_DEPTH; depth++) {
            for (int constant = 1; constant < SUM16_OPERAND_COUNT; constant++) {
                value += constant;
            }
        }
        return value;
    }

    private record LocalLoopState(BytecodeLocal i, BytecodeLocal a, BytecodeLocal c, BytecodeLocal d, BytecodeLocal e, BytecodeLocal sum) {
    }

    private record StackLoopState(BytecodeLocal i, BytecodeLocal sum) {
    }

    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "NoOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, enableYield = true, enableThreadedSwitch = false)),
                    @Variant(suffix = "AllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, enableYield = true, enableThreadedSwitch = false, //
                                    enableUncachedInterpreter = true, boxingEliminationTypes = {int.class, boolean.class})),
                    @Variant(suffix = "ThreadedAllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, enableYield = true, enableThreadedSwitch = true, //
                                    enableUncachedInterpreter = true, boxingEliminationTypes = {int.class, boolean.class})),
                    @Variant(suffix = "TailCallAllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, enableYield = true, enableThreadedSwitch = true, //
                                    enableUncachedInterpreter = true, boxingEliminationTypes = {int.class, boolean.class}, enableTailCallHandlers = true))
    })
    public abstract static class ContinuationBenchmarkRootNode extends RootNode implements BytecodeRootNode {

        protected ContinuationBenchmarkRootNode(BenchmarkLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            public static int doInt(int left, int right) {
                return left + right;
            }
        }

        @Operation
        @ConstantOperand(type = int.class)
        public static final class AddConst {
            @Specialization
            public static int doInt(int constant, int value) {
                return constant + value;
            }
        }

        @Operation
        public static final class Less {
            @Specialization
            public static boolean doInt(int left, int right) {
                return left < right;
            }
        }

        @Operation
        public static final class Sum16 {
            @Specialization
            public static int doInt(int a0, int a1, int a2, int a3, int a4, int a5, int a6, int a7,
                            int a8, int a9, int a10, int a11, int a12, int a13, int a14, int a15) {
                return a0 + a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + a13 + a14 + a15;
            }
        }

        /**
         * Returns a constant integer value, but prevents PE from seeing through it.
         */
        @Operation
        @ConstantOperand(type = int.class)
        public static final class OpaqueConstant {
            @Specialization
            public static int doInt(int constant) {
                return opaqueInt(constant);
            }

            @TruffleBoundary(allowInlining = false)
            private static int opaqueInt(int constant) {
                return constant;
            }
        }
    }
}
