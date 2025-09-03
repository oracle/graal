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
package org.graalvm.truffle.benchmark.bytecode;

import java.util.function.Consumer;

import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.graalvm.truffle.benchmark.bytecode.BytecodeBenchmarkRootNodeAll.Builder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
public class BytecodeBuilderBenchmark extends TruffleBenchmark {

    private static void parse(Builder b, Consumer<Builder> inner) {
        b.beginRoot();

        BytecodeLocal iLoc = b.createLocal();
        BytecodeLocal sumLoc = b.createLocal();
        BytecodeLocal jLoc = b.createLocal();
        BytecodeLocal tempLoc = b.createLocal();

        b.beginTryFinally(() -> {
            b.beginStoreLocal(sumLoc);
            b.beginAdd();
            b.emitLoadLocal(sumLoc);
            b.emitLoadConstant(1);
            b.endAdd();
            b.endStoreLocal();
        });

        b.beginBlock();

        // int i = 0;
        b.beginStoreLocal(iLoc);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // int sum = 0;
        b.beginStoreLocal(sumLoc);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // while (i < TOTAL_ITERATIONS) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(5000);
        b.endLess();
        b.beginBlock();

        // int j = 0;
        b.beginStoreLocal(jLoc);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // while (j < i) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(jLoc);
        b.emitLoadLocal(iLoc);
        b.endLess();
        b.beginBlock();

        if (inner != null) {
            inner.accept(b);
        }

        // int temp;
        // if (i % 3 < 1) {
        b.beginIfThenElse();

        b.beginLess();
        b.beginMod();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(3);
        b.endMod();
        b.emitLoadConstant(1);
        b.endLess();

        // temp = 1;
        b.beginStoreLocal(tempLoc);
        b.emitLoadConstant(1);
        b.endStoreLocal();

        // } else {
        // temp = i % 3;
        b.beginStoreLocal(tempLoc);
        b.beginMod();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(3);
        b.endMod();
        b.endStoreLocal();

        // }
        b.endIfThenElse();

        // j = j + temp;
        b.beginStoreLocal(jLoc);
        b.beginAdd();
        b.emitLoadLocal(jLoc);
        b.emitLoadLocal(tempLoc);
        b.endAdd();
        b.endStoreLocal();

        // }
        b.endBlock();
        b.endWhile();

        // sum = sum + j;
        b.beginStoreLocal(sumLoc);
        b.beginAdd();
        b.emitLoadLocal(sumLoc);
        b.emitLoadLocal(jLoc);
        b.endAdd();
        b.endStoreLocal();

        // i = i + 1;
        b.beginStoreLocal(iLoc);
        b.beginAdd();
        b.emitLoadLocal(iLoc);
        b.emitLoadConstant(1);
        b.endAdd();
        b.endStoreLocal();

        // }
        b.endBlock();
        b.endWhile();

        // exercise some label
        var label = b.createLabel();
        b.emitBranch(label);
        b.beginStoreLocal(sumLoc);
        b.beginAdd();
        b.emitLoadLocal(sumLoc);
        b.emitLoadConstant(1);
        b.endAdd();
        b.endStoreLocal();
        b.emitLabel(label);

        // return sum;
        b.beginReturn();
        b.emitLoadLocal(sumLoc);
        b.endReturn();

        b.endBlock();
        b.endTryFinally();

        b.endRoot();
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public Object buildManyRoots() {
        return BytecodeBenchmarkRootNodeAll.create(null, BytecodeConfig.DEFAULT, (b) -> {
            for (int i = 0; i < 100; i++) {
                parse(b, null);
            }
        });
    }

    @Benchmark
    public BytecodeBenchmarkRootNode buildSingleRoot() {
        return BytecodeBenchmarkRootNodeAll.create(null, BytecodeConfig.DEFAULT, (b) -> {
            parse(b, null);
        }).getNode(0);
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public BytecodeBenchmarkRootNode buildRecursive() {
        return BytecodeBenchmarkRootNodeAll.create(null, BytecodeConfig.DEFAULT, (b) -> {
            decrementAndParse(b, 100);
        }).getNode(0);
    }

    private void decrementAndParse(Builder b, int count) {
        if (count == 0) {
            return;
        }
        parse(b, (b2) -> {
            decrementAndParse(b, count - 1);
        });
    }

}
