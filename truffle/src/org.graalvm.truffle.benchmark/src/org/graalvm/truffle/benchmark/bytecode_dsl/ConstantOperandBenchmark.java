/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.graalvm.truffle.benchmark.bytecode_dsl.ConstantOperandBenchmarkRootNodeBuilder.BytecodeVariant;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 5, time = 1)
public class ConstantOperandBenchmark extends TruffleBenchmark {
    RootCallTarget rootCallTarget;

    @Param({"ConstantOperandBenchmarkRootNodeRegular", "ConstantOperandBenchmarkRootNodeInlined"}) private String implClassName;

    @SuppressWarnings("unchecked")
    private BytecodeVariant getVariant() {
        try {
            return (BytecodeVariant) Class.forName(ConstantOperandBenchmark.class.getPackageName() + "." + implClassName).getField("BYTECODE").get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not load bytecode class " + implClassName, e);
        }
    }

    @Setup
    public void parseRootNode() {
        rootCallTarget = getVariant().create(null, BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.beginBlock();

            BytecodeLocal x = b.createLocal();
            b.beginStoreLocal(x);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            BytecodeLocal count = b.createLocal();
            b.beginStoreLocal(count);
            b.emitLoadConstant(0);
            b.endStoreLocal();

            b.beginWhile();

            b.beginLt();
            b.emitLoadLocal(count);
            b.emitLoadArgument(0);
            b.endLt();

            b.beginBlock();
            b.beginStoreLocal(x);
            b.beginAddMany(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1);
            b.emitLoadLocal(x);
            b.endAddMany();
            b.endStoreLocal();

            b.beginStoreLocal(count);
            b.beginAdd(1);
            b.emitLoadLocal(count);
            b.endAdd();
            b.endStoreLocal();
            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(x);
            b.endReturn();

            b.endBlock();
            b.endRoot();
        }).getNode(0).getCallTarget();
    }

    static final int ITERS = 1_000_000;

    @Benchmark
    public void execute() {
        Object result = rootCallTarget.call(ITERS);
        if ((int) result != ITERS * 30) {
            throw new AssertionError("bad result: " + result);
        }
    }

    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "Regular", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, inlinePrimitiveConstants = false)),
                    @Variant(suffix = "Inlined", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class))
    })
    public abstract static class ConstantOperandBenchmarkRootNode extends RootNode implements BytecodeRootNode {

        protected ConstantOperandBenchmarkRootNode(BenchmarkLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @ConstantOperand(type = int.class)
        @Operation
        public static final class Add {
            @Specialization
            public static int doInt(int n, int x) {
                return n + x;
            }
        }

        @Operation
        public static final class Lt {
            @Specialization
            public static boolean doInt(int x, int y) {
                return x < y;
            }
        }

        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @ConstantOperand(type = int.class)
        @Operation
        public static final class AddMany {
            @Specialization
            public static int doAdd(int n1, int n2, int n3, int n4, int n5, int n6, int n7, int n8, int n9, int n10, //
                            int n11, int n12, int n13, int n14, int n15, int n16, int n17, int n18, int n19, int n20, //
                            int n21, int n22, int n23, int n24, int n25, int n26, int n27, int n28, int n29, int n30, int x) {
                return n1 + n2 + n3 + n4 + n5 + n6 + n7 + n8 + n9 + n10 + //
                                n11 + n12 + n13 + n14 + n15 + n16 + n17 + n18 + n19 + n20 + //
                                n21 + n22 + n23 + n24 + n25 + n26 + n27 + n28 + n29 + n30 + //
                                x;
            }
        }

    }

}
