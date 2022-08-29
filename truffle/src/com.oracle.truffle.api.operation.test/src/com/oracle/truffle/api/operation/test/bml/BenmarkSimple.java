/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation.test.bml;

import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_ADD;
import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_CONST;
import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_JUMP;
import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_JUMP_FALSE;
import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_LD_LOC;
import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_LESS;
import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_RETURN;
import static com.oracle.truffle.api.operation.test.bml.ManualBytecodeNode.OP_ST_LOC;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.test.bml.BMOperationRootNodeGen.Builder;

@State(Scope.Benchmark)
public class BenmarkSimple extends BaseBenchmark {

    private static final int TARGET_AMOUNT = 100000;

    private static final String NAME_TEST_LOOP = "simple:test-loop";
    private static final String NAME_TEST_LOOP_NO_BE = "simple:test-loop-no-be";
    private static final String NAME_TEST_LOOP_QUICKEN = "simple:test-loop-quicken";
    private static final String NAME_MANUAL = "simple:manual";
    private static final String NAME_MANUAL_NO_BE = "simple:manual-no-be";
    private static final String NAME_AST = "simple:ast";

    private static final Source SOURCE_TEST_LOOP = Source.create("bm", NAME_TEST_LOOP);
    private static final Source SOURCE_TEST_LOOP_NO_BE = Source.create("bm", NAME_TEST_LOOP_NO_BE);
    private static final Source SOURCE_TEST_LOOP_QUICKEN = Source.create("bm", NAME_TEST_LOOP_QUICKEN);
    private static final Source SOURCE_MANUAL = Source.create("bm", NAME_MANUAL);
    private static final Source SOURCE_MANUAL_NO_BE = Source.create("bm", NAME_MANUAL_NO_BE);
    private static final Source SOURCE_AST = Source.create("bm", NAME_AST);

    private static final short[] BYTECODE = {
                    /* 00 */ OP_CONST, 0, 0,
                    /* 03 */ OP_ST_LOC, 2,
                    /* 05 */ OP_LD_LOC, 2,
                    /* 07 */ OP_CONST, (short) (TARGET_AMOUNT >> 16), (short) (TARGET_AMOUNT & 0xffff),
                    /* 10 */ OP_LESS,
                    /* 11 */ OP_JUMP_FALSE, 35,
                    /* 13 */ OP_LD_LOC, 2,
                    /* 15 */ OP_CONST, 0, 1,
                    /* 18 */ OP_ADD,
                    /* 19 */ OP_CONST, 0, 1,
                    /* 22 */ OP_ADD,
                    /* 23 */ OP_CONST, 0, 1,
                    /* 26 */ OP_ADD,
                    /* 27 */ OP_CONST, 0, 1,
                    /* 30 */ OP_ADD,
                    /* 31 */ OP_ST_LOC, 2,
                    /* 33 */ OP_JUMP, 5,
                    /* 35 */ OP_CONST, 0, 0,
                    /* 38 */ OP_RETURN
    };

    private Context context;

    private static final int MODE_NORMAL = 0;
    private static final int MODE_NO_BE = 1;
    private static final int MODE_QUICKEN = 2;

    /**
     * The code is equivalent to:
     *
     * <pre>
     * int i = 0;
     * while (i < 100000) {
     *     i = i + 1 + 1 + 1 + 1
     * }
     * return 0;
     * </pre>
     */
    static {
        BenchmarkLanguage.registerName(NAME_TEST_LOOP, (lang, b) -> {
            createSimpleLoop(lang, b, MODE_NORMAL);
        });
        BenchmarkLanguage.registerName(NAME_TEST_LOOP_NO_BE, (lang, b) -> {
            createSimpleLoop(lang, b, MODE_NO_BE);
        });
        BenchmarkLanguage.registerName(NAME_TEST_LOOP_QUICKEN, (lang, b) -> {
            createSimpleLoop(lang, b, MODE_QUICKEN);
        });
        BenchmarkLanguage.registerName2(NAME_MANUAL, lang -> {
            FrameDescriptor.Builder b = FrameDescriptor.newBuilder(3);
            b.addSlots(3, FrameSlotKind.Illegal);
            ManualBytecodeNode node = new ManualBytecodeNode(lang, b.build(), BYTECODE);
            return node.getCallTarget();
        });
        BenchmarkLanguage.registerName2(NAME_MANUAL_NO_BE, lang -> {
            FrameDescriptor.Builder b = FrameDescriptor.newBuilder(3);
            b.addSlots(3, FrameSlotKind.Illegal);
            ManualBytecodeNodeNBE node = new ManualBytecodeNodeNBE(lang, b.build(), BYTECODE);
            return node.getCallTarget();
        });
        BenchmarkLanguage.registerName2(NAME_AST, lang -> {
            return new BMLRootNode(lang, 1,
                            StoreLocalNodeGen.create(0, ConstNodeGen.create(0)),
                            WhileNode.create(
                                            LessNodeGen.create(LoadLocalNodeGen.create(0), ConstNodeGen.create(TARGET_AMOUNT)),
                                            StoreLocalNodeGen.create(0,
                                                            AddNodeGen.create(
                                                                            AddNodeGen.create(
                                                                                            AddNodeGen.create(
                                                                                                            AddNodeGen.create(
                                                                                                                            LoadLocalNodeGen.create(0),
                                                                                                                            ConstNodeGen.create(1)),
                                                                                                            ConstNodeGen.create(1)),
                                                                                            ConstNodeGen.create(1)),
                                                                            ConstNodeGen.create(1)))),
                            ReturnNodeGen.create(ConstNodeGen.create(0))).getCallTarget();
        });
    }

    private static void beginAdd(Builder b, int mode) {
        switch (mode) {
            case MODE_NORMAL:
                b.beginAdd();
                break;
            case MODE_NO_BE:
                b.beginAddBoxed();
                break;
            case MODE_QUICKEN:
                b.beginAddQuickened();
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static void endAdd(Builder b, int mode) {
        switch (mode) {
            case MODE_NORMAL:
                b.endAdd();
                break;
            case MODE_NO_BE:
                b.endAddBoxed();
                break;
            case MODE_QUICKEN:
                b.endAddQuickened();
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static void createSimpleLoop(BenchmarkLanguage lang, Builder b, int mode) {
        OperationLocal i = b.createLocal();

        // i = 0
        b.beginStoreLocal(i);
        b.emitConstObject(0);
        b.endStoreLocal();

        // while (i < 100000) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(i);
        b.emitConstObject(100000);
        b.endLess();

        // i = i + 1 + 1 + 1 + 1
        b.beginStoreLocal(i);
        beginAdd(b, mode);
        beginAdd(b, mode);
        beginAdd(b, mode);
        beginAdd(b, mode);
        b.emitLoadLocal(i);
        b.emitConstObject(1);
        endAdd(b, mode);
        b.emitConstObject(1);
        endAdd(b, mode);
        b.emitConstObject(1);
        endAdd(b, mode);
        b.emitConstObject(1);
        endAdd(b, mode);
        b.endStoreLocal();

        // }
        b.endWhile();

        // return 0
        b.beginReturn();
        b.emitConstObject(0);
        b.endReturn();

        b.publish(lang);
    }

    @Setup(Level.Trial)
    public void setup() {
        context = Context.create();
    }

    @Setup(Level.Iteration)
    public void enterContext() {
        context.enter();
    }

    @TearDown(Level.Iteration)
    public void leaveContext() {
        context.leave();
    }

    @Benchmark
    public void operation() {
        context.eval(SOURCE_TEST_LOOP);
    }

    @Benchmark
    public void operationNoBe() {
        context.eval(SOURCE_TEST_LOOP_NO_BE);
    }

    @Benchmark
    public void operationQuicken() {
        context.eval(SOURCE_TEST_LOOP_QUICKEN);
    }

    @Benchmark
    public void manual() {
        context.eval(SOURCE_MANUAL);
    }

    @Benchmark
    public void manualNoBE() {
        context.eval(SOURCE_MANUAL_NO_BE);
    }

    @Benchmark
    public void ast() {
        context.eval(SOURCE_AST);
    }
}

@Warmup(iterations = BaseBenchmark.WARMUP_ITERATIONS, time = BaseBenchmark.ITERATION_TIME)
@Measurement(iterations = BaseBenchmark.MEASUREMENT_ITERATIONS, time = BaseBenchmark.ITERATION_TIME)
@Fork(BaseBenchmark.FORKS)
class BaseBenchmark {
    public static final int MEASUREMENT_ITERATIONS = 10;
    public static final int WARMUP_ITERATIONS = 10;
    public static final int ITERATION_TIME = 1;
    public static final int FORKS = 1;
}
