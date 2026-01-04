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
package org.graalvm.truffle.benchmark.bytecode_dsl.specs;

import org.graalvm.truffle.benchmark.bytecode_dsl.BenchmarkLanguage;
import org.graalvm.truffle.benchmark.bytecode_dsl.BytecodeDSLBenchmarkRootNodeBuilder;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.BlockNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.IfNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.WhileNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.AddNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ConstNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.LessNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.LoadLocalNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ModNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ReturnNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.StoreLocalNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterRootNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.Builder;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLocal;

/**
 * This is a workload with nested loops. In pseudocode:
 *
 * <pre>
 * int i = 0;
 * int sum = 0;
 * while (i < 5000) {
 *     int j = 0;
 *     while (j < i) {
 *         int temp;
 *         if (i % 3 < 1) {
 *             temp = 1;
 *         } else {
 *             temp = i % 3;
 *         }
 *         j = j + temp;
 *     }
 *     sum = sum + j;
 *     i = i + 1;
 * }
 * return sum;
 * </pre>
 */
public final class NestedLoopBenchmark implements BenchmarkSpec {
    private static final int TOTAL_ITERATIONS = 5000;

    @Override
    public Object expectedResult() {
        return 12498333;
    }

    @Override
    public void parseBytecodeDSL(BytecodeDSLBenchmarkRootNodeBuilder b) {
        b.beginRoot();

        BytecodeLocal iLoc = b.createLocal();
        BytecodeLocal sumLoc = b.createLocal();
        BytecodeLocal jLoc = b.createLocal();
        BytecodeLocal tempLoc = b.createLocal();

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

        // return sum;
        b.beginReturn();
        b.emitLoadLocal(sumLoc);
        b.endReturn();

        b.endRoot();
    }

    public void parseBytecode(Builder b) {
        int i = b.createLocal();
        int sum = b.createLocal();
        int j = b.createLocal();
        int temp = b.createLocal();

        // i = 0
        b.loadConstant(0);
        b.storeLocal(i);

        // sum = 0
        b.loadConstant(0);
        b.storeLocal(sum);

        // while (i < TOTAL_ITERATIONS) {
        int outerWhileStart = b.currentBci();
        b.loadLocal(i);
        b.loadConstant(TOTAL_ITERATIONS);
        b.emitLessThan();
        int branchOuterWhileEnd = b.emitJumpFalse();

        // j = 0
        b.loadConstant(0);
        b.storeLocal(j);

        // while (j < i) {
        int innerWhileStart = b.currentBci();
        b.loadLocal(j);
        b.loadLocal(i);
        b.emitLessThan();
        int branchInnerWhileEnd = b.emitJumpFalse();

        // if (i % 3 < 1) {
        b.loadLocal(i);
        b.loadConstant(3);
        b.emitMod();
        b.loadConstant(1);
        b.emitLessThan();
        int branchElse = b.emitJumpFalse();

        // temp = 1
        b.loadConstant(1);
        b.storeLocal(temp);
        int branchEnd = b.emitJump();

        // temp = i % 3
        b.patchJumpFalse(branchElse, b.currentBci());
        b.loadLocal(i);
        b.loadConstant(3);
        b.emitMod();
        b.storeLocal(temp);

        // j = j + temp
        b.patchJump(branchEnd, b.currentBci());
        b.loadLocal(j);
        b.loadLocal(temp);
        b.emitAdd();
        b.storeLocal(j);
        b.emitJump(innerWhileStart);

        // sum = sum + j
        b.patchJumpFalse(branchInnerWhileEnd, b.currentBci());
        b.loadLocal(sum);
        b.loadLocal(j);
        b.emitAdd();
        b.storeLocal(sum);

        // i = i + 1
        b.loadLocal(i);
        b.loadConstant(1);
        b.emitAdd();
        b.storeLocal(i);
        b.emitJump(outerWhileStart);

        // return sum
        b.patchJumpFalse(branchOuterWhileEnd, b.currentBci());
        b.loadLocal(sum);
        b.emitReturn();
    }

    public CallTarget parseAST(BenchmarkLanguage lang) {
        int iLoc = 0;
        int sumLoc = 1;
        int jLoc = 2;
        int tempLoc = 3;
        return new ASTInterpreterRootNode(lang, 4, BlockNode.create(
                        // i = 0
                        StoreLocalNodeGen.create(iLoc, ConstNodeGen.create(0)),
                        // sum = 0
                        StoreLocalNodeGen.create(sumLoc, ConstNodeGen.create(0)),
                        // while (i < 5000) {
                        WhileNode.create(LessNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(TOTAL_ITERATIONS)), BlockNode.create(
                                        // j = 0
                                        StoreLocalNodeGen.create(jLoc, ConstNodeGen.create(0)),
                                        // while (j < i) {
                                        WhileNode.create(LessNodeGen.create(LoadLocalNodeGen.create(jLoc), LoadLocalNodeGen.create(iLoc)), BlockNode.create(
                                                        // if (i % 3 < 1) {
                                                        IfNode.create(LessNodeGen.create(ModNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(3)), ConstNodeGen.create(1)),
                                                                        // temp = 1
                                                                        StoreLocalNodeGen.create(tempLoc, ConstNodeGen.create(1)),
                                                                        // } else {
                                                                        // temp = i % 3
                                                                        StoreLocalNodeGen.create(tempLoc, ModNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(3)))),
                                                        // }
                                                        // j = j + temp
                                                        StoreLocalNodeGen.create(jLoc, AddNodeGen.create(LoadLocalNodeGen.create(jLoc), LoadLocalNodeGen.create(tempLoc))))),
                                        // }
                                        // sum = sum + j
                                        StoreLocalNodeGen.create(sumLoc, AddNodeGen.create(LoadLocalNodeGen.create(sumLoc), LoadLocalNodeGen.create(jLoc))),
                                        // i = i + 1
                                        StoreLocalNodeGen.create(iLoc, AddNodeGen.create(LoadLocalNodeGen.create(iLoc), ConstNodeGen.create(1))))),
                        // return sum
                        ReturnNodeGen.create(LoadLocalNodeGen.create(sumLoc)))).getCallTarget();

    }
}
