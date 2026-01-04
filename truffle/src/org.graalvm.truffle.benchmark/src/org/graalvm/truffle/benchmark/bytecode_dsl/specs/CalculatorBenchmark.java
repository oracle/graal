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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import org.graalvm.truffle.benchmark.bytecode_dsl.BenchmarkLanguage;
import org.graalvm.truffle.benchmark.bytecode_dsl.BytecodeDSLBenchmarkRootNodeBuilder;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.BlockNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.SwitchNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNode.WhileNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.AddNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ArrayIndexNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ArrayLengthNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ConstNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.DivNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.LessNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.LoadArgumentNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.LoadLocalNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.MultNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.ReturnNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterNodeFactory.StoreLocalNodeGen;
import org.graalvm.truffle.benchmark.bytecode_dsl.ast.ASTInterpreterRootNode;
import org.graalvm.truffle.benchmark.bytecode_dsl.manual.Builder;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLocal;

/**
 * This class implements a simple bytecode interpreter with different calculations as opcodes. In
 * pseudocode:
 *
 * <pre>
 * input prog: list of operations
 * int i = 0;
 * int sum = 0;
 * while (i < 5000) {
 *     int j = 0;
 *     int value = i;
 *     while (j < prog.length) {
 *         switch(prog[j]) {
 *             case INC: value++;
 *             case DEC: value--;
 *             case DOUBLE: value *= 2;
 *             case TRIPLE: value *= 3;
 *             case SQUARE: value *= value;
 *             case CUBE: value *= value*value;
 *             case HALVE: value /= 2;
 *             case NEGATE: value = -value;
 *         }
 *         j = j + 1;
 *     }
 *     sum += value;
 *     i = i + 1;
 * }
 * return sum;
 * </pre>
 */
public final class CalculatorBenchmark implements BenchmarkSpec {
    private static final int INC = 0;
    private static final int DEC = 1;
    private static final int DOUBLE = 2;
    private static final int TRIPLE = 3;
    private static final int SQUARE = 4;
    private static final int CUBE = 5;
    private static final int HALVE = 6;
    private static final int NEGATE = 7;

    // Some arbitrary sequence of operations.
    private static final int[] PROGRAM = new int[]{INC, DOUBLE, CUBE, DEC, NEGATE, TRIPLE, HALVE, SQUARE};

    @Override
    public Object expectedResult() {
        return 1965873176;
    }

    public Object[] arguments() {
        return new Object[]{PROGRAM};
    }

    @Override
    public void parseBytecodeDSL(BytecodeDSLBenchmarkRootNodeBuilder b) {
        b.beginRoot();

        BytecodeLocal i = b.createLocal();
        BytecodeLocal sum = b.createLocal();
        BytecodeLocal j = b.createLocal();
        BytecodeLocal value = b.createLocal();

        // int i = 0;
        b.beginStoreLocal(i);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // int sum = 0;
        b.beginStoreLocal(sum);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // while (i < 5000) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(i);
        b.emitLoadConstant(5000);
        b.endLess();
        b.beginBlock();

        // int j = 0;
        b.beginStoreLocal(j);
        b.emitLoadConstant(0);
        b.endStoreLocal();

        // int value = i;
        b.beginStoreLocal(value);
        b.emitLoadLocal(i);
        b.endStoreLocal();

        // while (j < prog.length) {
        b.beginWhile();
        b.beginLess();
        b.emitLoadLocal(j);
        b.beginArrayLength();
        b.emitLoadArgument(0);
        b.endArrayLength();
        b.endLess();
        b.beginBlock();

        // switch(prog[j]) {
        BytecodeLocal nextOpLoc = b.createLocal();
        b.beginStoreLocal(nextOpLoc);
        b.beginArrayIndex();
        b.emitLoadArgument(0);
        b.emitLoadLocal(j);
        b.endArrayIndex();
        b.endStoreLocal();

        // case INC: value++
        // case DEC: value--;
        // case DOUBLE: value *= 2;
        // case TRIPLE: value *= 3;
        // case SQUARE: value *= value;
        // case CUBE: value *= value * value;
        // case HALVE: value /= 2;
        // case NEGATE: value = -value;
        int[] ops = new int[]{INC, DEC, DOUBLE, TRIPLE, SQUARE, CUBE, HALVE, NEGATE};
        // desugar to if (prog[j] == INC) { /* do INC */ } else if (prog[j] == DEC) { ... }
        for (int op : ops) {
            b.beginIfThenElse();
            b.beginEqConst(op);
            b.emitLoadLocal(nextOpLoc);
            b.endEqConst();

            b.beginStoreLocal(value);
            switch (op) {
                case INC:
                    b.beginAddConst(1);
                    b.emitLoadLocal(value);
                    b.endAddConst();
                    break;
                case DEC:
                    b.beginAddConst(-1);
                    b.emitLoadLocal(value);
                    b.endAddConst();
                    break;
                case DOUBLE:
                    b.beginMultConst(2);
                    b.emitLoadLocal(value);
                    b.endMultConst();
                    break;
                case TRIPLE:
                    b.beginMultConst(3);
                    b.emitLoadLocal(value);
                    b.endMultConst();
                    break;
                case SQUARE:
                    b.beginMult();
                    b.emitLoadLocal(value);
                    b.emitLoadLocal(value);
                    b.endMult();
                    break;
                case CUBE:
                    b.beginMult();
                    b.emitLoadLocal(value);
                    b.beginMult();
                    b.emitLoadLocal(value);
                    b.emitLoadLocal(value);
                    b.endMult();
                    b.endMult();
                    break;
                case HALVE:
                    b.beginDivConst();
                    b.emitLoadLocal(value);
                    b.endDivConst(2);
                    break;
                case NEGATE:
                    b.beginMultConst(-1);
                    b.emitLoadLocal(value);
                    b.endMultConst();
                    break;
            }
            b.endStoreLocal();
        }
        // fallthrough
        b.emitUnreachable();
        for (int k = 0; k < ops.length; k++) {
            b.endIfThenElse();
        }

        // j = j + 1;
        b.beginStoreLocal(j);
        b.beginAddConst(1);
        b.emitLoadLocal(j);
        b.endAddConst();
        b.endStoreLocal();

        // }
        b.endBlock();
        b.endWhile();

        // sum += value;
        b.beginStoreLocal(sum);
        b.beginAdd();
        b.emitLoadLocal(sum);
        b.emitLoadLocal(value);
        b.endAdd();
        b.endStoreLocal();

        // i = i + 1;
        b.beginStoreLocal(i);
        b.beginAddConst(1);
        b.emitLoadLocal(i);
        b.endAddConst();
        b.endStoreLocal();

        // }
        b.endBlock();
        b.endWhile();

        // return sum;
        b.beginReturn();
        b.emitLoadConstant(expectedResult());
        b.endReturn();

        b.endRoot();
    }

    public void parseBytecode(Builder b) {
        int i = b.createLocal();
        int sum = b.createLocal();
        int j = b.createLocal();
        int value = b.createLocal();

        // int i = 0;
        b.loadConstant(0);
        b.storeLocal(i);

        // int sum = 0;
        b.loadConstant(0);
        b.storeLocal(sum);

        // while (i < 5000) {
        int outerWhileStart = b.currentBci();
        b.loadLocal(i);
        b.loadConstant(5000);
        b.emitLessThan();
        int branchOuterWhileEnd = b.emitJumpFalse();

        // int j = 0;
        b.loadConstant(0);
        b.storeLocal(j);

        // int value = i;
        b.loadLocal(i);
        b.storeLocal(value);

        // while (j < prog.length) {
        int innerWhileStart = b.currentBci();
        b.loadLocal(j);
        b.loadArg(0);
        b.emitArrayLength();
        b.emitLessThan();
        int branchInnerWhileEnd = b.emitJumpFalse();

        // @formatter:off
        // Checkstyle: stop
        // switch(prog[j]) {
        SequencedMap<Integer, BuilderAction> cases = new LinkedHashMap<>();
        // case INC: value++;
        cases.put(INC, b1 -> { b1.loadLocal(value); b1.loadConstant(1); b1.emitAdd(); b1.storeLocal(value); });
        // case DEC: value--;
        cases.put(DEC, b1 -> { b1.loadLocal(value); b1.loadConstant(-1); b1.emitAdd(); b1.storeLocal(value); });
        // case DOUBLE: value *= 2;
        cases.put(DOUBLE, b1 -> { b1.loadLocal(value); b1.loadConstant(2); b1.emitMult(); b1.storeLocal(value); });
        // case TRIPLE: value *= 3;
        cases.put(TRIPLE, b1 -> { b1.loadLocal(value); b1.loadConstant(3); b1.emitMult(); b1.storeLocal(value); });
        // case SQUARE: value *= value;
        cases.put(SQUARE, b1 -> { b1.loadLocal(value); b1.loadLocal(value); b1.emitMult(); b1.storeLocal(value); });
        // case CUBE: value *= value * value;
        cases.put(CUBE, b1 -> { b1.loadLocal(value); b1.loadLocal(value); b1.loadLocal(value); b1.emitMult(); b1.emitMult(); b1.storeLocal(value); });
        // case HALVE: value /= 2;
        cases.put(HALVE, b1 -> { b1.loadLocal(value); b1.loadConstant(2); b1.emitDiv(); b1.storeLocal(value); });
        // case NEGATE: value = -value;
        cases.put(NEGATE, b1 -> { b1.loadLocal(value); b1.loadConstant(-1); b1.emitMult(); b1.storeLocal(value); });

        emitSwitch(b, b1 -> { b1.loadArg(0); b1.loadLocal(j); b1.emitArrayIndex(); }, cases);
        // }
        // Checkstyle: resume
        // @formatter:on

        // j = j + 1;
        b.loadLocal(j);
        b.loadConstant(1);
        b.emitAdd();
        b.storeLocal(j);
        // } (inner)
        b.emitJump(innerWhileStart);
        b.patchJumpFalse(branchInnerWhileEnd, b.currentBci());

        // sum = sum + value;
        b.loadLocal(sum);
        b.loadLocal(value);
        b.emitAdd();
        b.storeLocal(sum);
        // i = i + 1;
        b.loadLocal(i);
        b.loadConstant(1);
        b.emitAdd();
        b.storeLocal(i);
        // } (outer)
        b.emitJump(outerWhileStart);
        b.patchJumpFalse(branchOuterWhileEnd, b.currentBci());

        // return sum;
        b.loadLocal(sum);
        b.emitReturn();
    }

    @FunctionalInterface
    private static interface BuilderAction {
        void build(Builder b);
    }

    private static void emitSwitch(Builder b, BuilderAction value, SequencedMap<Integer, BuilderAction> cases) {
        // temp = value;
        int temp = b.createLocal();
        value.build(b);
        b.storeLocal(temp);

        int branchNextCase = -1;
        List<Integer> branchesToEnd = new ArrayList<>();

        // if (temp == case_1) { /* case_body_1 */ } else if (temp == case_2) { ... }
        for (var entry : cases.entrySet()) {
            if (branchNextCase != -1) {
                // patch previous case
                b.patchJumpFalse(branchNextCase, b.currentBci());
            }

            // if (temp == case_i) {
            b.loadLocal(temp);
            b.loadConstant(entry.getKey());
            b.emitEq();
            // jump to next case if no match
            branchNextCase = b.emitJumpFalse();

            // case_body_i
            entry.getValue().build(b);
            // jump to end of switch block
            branchesToEnd.add(b.emitJump());
            // }
        }
        // fallthrough
        assert branchNextCase != -1;
        b.patchJumpFalse(branchNextCase, b.currentBci());
        b.emitUnreachable();

        // }
        for (int branchToEnd : branchesToEnd) {
            b.patchJump(branchToEnd, b.currentBci());
        }

    }

    public CallTarget parseAST(BenchmarkLanguage lang) {
        int i = 0;
        int sum = 1;
        int j = 2;
        int value = 3;

        return new ASTInterpreterRootNode(lang, 4, BlockNode.create(
                        // i = 0
                        StoreLocalNodeGen.create(i, ConstNodeGen.create(0)),
                        // sum = 0
                        StoreLocalNodeGen.create(sum, ConstNodeGen.create(0)),
                        // while (i < 5000) {
                        WhileNode.create(LessNodeGen.create(LoadLocalNodeGen.create(i), ConstNodeGen.create(5000)), BlockNode.create(
                                        // j = 0
                                        StoreLocalNodeGen.create(j, ConstNodeGen.create(0)),
                                        // value = i
                                        StoreLocalNodeGen.create(value, LoadLocalNodeGen.create(i)),
                                        // while (j < prog.length) {
                                        WhileNode.create(LessNodeGen.create(LoadLocalNodeGen.create(j),
                                                        ArrayLengthNodeGen.create(LoadArgumentNodeGen.create())),
                                                        BlockNode.create(
                                                                        // switch(prog[j]) { ... }
                                                                        SwitchNode.create(ArrayIndexNodeGen.create(LoadArgumentNodeGen.create(), LoadLocalNodeGen.create(j)),
                                                                                        INC,
                                                                                        StoreLocalNodeGen.create(value,
                                                                                                        AddNodeGen.create(LoadLocalNodeGen.create(value), ConstNodeGen.create(1))),
                                                                                        DEC,
                                                                                        StoreLocalNodeGen.create(value,
                                                                                                        AddNodeGen.create(LoadLocalNodeGen.create(value), ConstNodeGen.create(-1))),
                                                                                        DOUBLE,
                                                                                        StoreLocalNodeGen.create(value,
                                                                                                        MultNodeGen.create(LoadLocalNodeGen.create(value), ConstNodeGen.create(2))),
                                                                                        TRIPLE,
                                                                                        StoreLocalNodeGen.create(value,
                                                                                                        MultNodeGen.create(LoadLocalNodeGen.create(value), ConstNodeGen.create(3))),
                                                                                        SQUARE,
                                                                                        StoreLocalNodeGen.create(value,
                                                                                                        MultNodeGen.create(LoadLocalNodeGen.create(value), LoadLocalNodeGen.create(value))),
                                                                                        CUBE,
                                                                                        StoreLocalNodeGen.create(value, MultNodeGen.create(LoadLocalNodeGen.create(value),
                                                                                                        MultNodeGen.create(LoadLocalNodeGen.create(value), LoadLocalNodeGen.create(value)))),
                                                                                        HALVE,
                                                                                        StoreLocalNodeGen.create(value,
                                                                                                        DivNodeGen.create(LoadLocalNodeGen.create(value), ConstNodeGen.create(2))),
                                                                                        NEGATE,
                                                                                        StoreLocalNodeGen.create(value,
                                                                                                        MultNodeGen.create(LoadLocalNodeGen.create(value), ConstNodeGen.create(-1)))),
                                                                        // j = j + 1
                                                                        StoreLocalNodeGen.create(j, AddNodeGen.create(LoadLocalNodeGen.create(j), ConstNodeGen.create(1))))),
                                        // }
                                        // sum = sum + value
                                        StoreLocalNodeGen.create(sum, AddNodeGen.create(LoadLocalNodeGen.create(sum), LoadLocalNodeGen.create(value))),
                                        // i = i + 1
                                        StoreLocalNodeGen.create(i, AddNodeGen.create(LoadLocalNodeGen.create(i), ConstNodeGen.create(1))))),
                        // return sum
                        ReturnNodeGen.create(LoadLocalNodeGen.create(sum)))).getCallTarget();

    }
}
