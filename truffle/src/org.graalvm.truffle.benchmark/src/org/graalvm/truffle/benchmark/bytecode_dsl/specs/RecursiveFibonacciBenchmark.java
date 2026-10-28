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
package org.graalvm.truffle.benchmark.bytecode_dsl.specs;

import org.graalvm.truffle.benchmark.bytecode_dsl.BenchmarkLanguage;
import org.graalvm.truffle.benchmark.bytecode_dsl.BytecodeDSLBenchmarkRootNodeBuilder;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.IfNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.LoadIntArgumentNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.RecursiveFibCallNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.AddNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ConstNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.LessNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ReturnNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterRootNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.Builder;

import com.oracle.truffle.api.CallTarget;

/**
 * Recursive Fibonacci benchmark used to measure interpreter call performance.
 */
public final class RecursiveFibonacciBenchmark implements BenchmarkSpec {
    private static final int ARGUMENT = 20;

    @Override
    public Object expectedResult() {
        return 6765;
    }

    @Override
    public Object[] arguments() {
        return new Object[]{ARGUMENT};
    }

    @Override
    public void parseBytecodeDSL(BytecodeDSLBenchmarkRootNodeBuilder b) {
        parseBytecodeDSL(b, "bytecodeDSLDirectCall");
    }

    @Override
    public void parseBytecodeDSL(BytecodeDSLBenchmarkRootNodeBuilder b, String benchmarkMethod) {
        b.beginRoot();

        b.beginIfThenElse();
        b.beginLess();
        b.emitLoadArgument(0);
        b.emitLoadConstant(2);
        b.endLess();

        b.beginReturn();
        b.emitLoadArgument(0);
        b.endReturn();

        b.beginReturn();
        b.beginAdd();
        beginRecursiveCall(b, benchmarkMethod, 1);
        b.emitLoadArgument(0);
        endRecursiveCall(b, benchmarkMethod);
        beginRecursiveCall(b, benchmarkMethod, 2);
        b.emitLoadArgument(0);
        endRecursiveCall(b, benchmarkMethod);
        b.endAdd();
        b.endReturn();

        b.endIfThenElse();
        b.endRoot();
    }

    private static void beginRecursiveCall(BytecodeDSLBenchmarkRootNodeBuilder b, String benchmarkMethod, int delta) {
        switch (benchmarkMethod) {
            case "bytecodeDSLIndirectCall" -> b.beginRecurseSubtractIndirectCall(delta);
            case "bytecodeDSLCallTarget" -> b.beginRecurseSubtractCallTarget(delta);
            case "bytecodeDSLCallTargetImplicitBci" -> b.beginRecurseSubtractCallTargetImplicitBci(delta);
            case "bytecodeDSLCallTargetExplicitBci" -> b.beginRecurseSubtractCallTargetExplicitBci(delta);
            case "bytecodeDSLDirectCallImplicitBci" -> b.beginRecurseSubtractDirectCallImplicitBci(delta);
            case "bytecodeDSLDirectCallExplicitBci" -> b.beginRecurseSubtractDirectCallExplicitBci(delta);
            case "bytecodeDSLIndirectCallImplicitBci" -> b.beginRecurseSubtractIndirectCallImplicitBci(delta);
            case "bytecodeDSLIndirectCallExplicitBci" -> b.beginRecurseSubtractIndirectCallExplicitBci(delta);
            case "bytecodeDSLDirectCallWithRootNodeImplicitBci" -> b.beginRecurseSubtractDirectCallWithRootNodeImplicitBci(delta);
            case "bytecodeDSLDirectCallWithRootNodeExplicitBci" -> b.beginRecurseSubtractDirectCallWithRootNodeExplicitBci(delta);
            case "bytecodeDSLIndirectCallWithRootNodeImplicitBci" -> b.beginRecurseSubtractIndirectCallWithRootNodeImplicitBci(delta);
            case "bytecodeDSLIndirectCallWithRootNodeExplicitBci" -> b.beginRecurseSubtractIndirectCallWithRootNodeExplicitBci(delta);
            case "bytecodeDSLRootNodeCallImplicitBci" -> b.beginRecurseSubtractRootNodeCallImplicitBci(delta);
            case "bytecodeDSLRootNodeCallExplicitBci" -> b.beginRecurseSubtractRootNodeCallExplicitBci(delta);
            default -> b.beginRecurseSubtractDirectCall(delta);
        }
    }

    private static void endRecursiveCall(BytecodeDSLBenchmarkRootNodeBuilder b, String benchmarkMethod) {
        switch (benchmarkMethod) {
            case "bytecodeDSLIndirectCall" -> b.endRecurseSubtractIndirectCall();
            case "bytecodeDSLCallTarget" -> b.endRecurseSubtractCallTarget();
            case "bytecodeDSLCallTargetImplicitBci" -> b.endRecurseSubtractCallTargetImplicitBci();
            case "bytecodeDSLCallTargetExplicitBci" -> b.endRecurseSubtractCallTargetExplicitBci();
            case "bytecodeDSLDirectCallImplicitBci" -> b.endRecurseSubtractDirectCallImplicitBci();
            case "bytecodeDSLDirectCallExplicitBci" -> b.endRecurseSubtractDirectCallExplicitBci();
            case "bytecodeDSLIndirectCallImplicitBci" -> b.endRecurseSubtractIndirectCallImplicitBci();
            case "bytecodeDSLIndirectCallExplicitBci" -> b.endRecurseSubtractIndirectCallExplicitBci();
            case "bytecodeDSLDirectCallWithRootNodeImplicitBci" -> b.endRecurseSubtractDirectCallWithRootNodeImplicitBci();
            case "bytecodeDSLDirectCallWithRootNodeExplicitBci" -> b.endRecurseSubtractDirectCallWithRootNodeExplicitBci();
            case "bytecodeDSLIndirectCallWithRootNodeImplicitBci" -> b.endRecurseSubtractIndirectCallWithRootNodeImplicitBci();
            case "bytecodeDSLIndirectCallWithRootNodeExplicitBci" -> b.endRecurseSubtractIndirectCallWithRootNodeExplicitBci();
            case "bytecodeDSLRootNodeCallImplicitBci" -> b.endRecurseSubtractRootNodeCallImplicitBci();
            case "bytecodeDSLRootNodeCallExplicitBci" -> b.endRecurseSubtractRootNodeCallExplicitBci();
            default -> b.endRecurseSubtractDirectCall();
        }
    }

    @Override
    public void parseBytecode(Builder b) {
        b.loadIntArg(0);
        b.loadConstant(2);
        b.emitLessThan();
        int elseBranch = b.emitJumpFalse();

        b.loadIntArg(0);
        b.emitReturn();

        b.patchJumpFalse(elseBranch, b.currentBci());
        b.loadIntArg(0);
        b.emitRecurseSubtract(1);
        b.loadIntArg(0);
        b.emitRecurseSubtract(2);
        b.emitAdd();
        b.emitReturn();
    }

    @Override
    public CallTarget parseAST(BenchmarkLanguage lang) {
        return new ASTInterpreterRootNode(lang, 0,
                        IfNode.create(
                                        LessNodeGen.create(LoadIntArgumentNode.create(), ConstNodeGen.create(2)),
                                        ReturnNodeGen.create(LoadIntArgumentNode.create()),
                                        ReturnNodeGen.create(
                                                        AddNodeGen.create(
                                                                        RecursiveFibCallNode.create(1, LoadIntArgumentNode.create()),
                                                                        RecursiveFibCallNode.create(2, LoadIntArgumentNode.create()))))).getCallTarget();
    }
}
