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
package com.oracle.truffle.api.operation.test.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.ContinuationResult;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.introspection.Instruction;
import com.oracle.truffle.api.operation.introspection.OperationIntrospection;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.operation.OperationParser;

public class TestOperationsParserTest {
    // @formatter:off

    private static final OperationTestLanguage LANGUAGE = null;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static RootCallTarget parse(String rootName, OperationParser<TestOperationsGen.Builder> builder) {
        OperationRootNode operationsNode = parseNode(rootName, builder);
        return ((RootNode) operationsNode).getCallTarget();
    }

    private static TestOperations parseNode(String rootName, OperationParser<TestOperationsGen.Builder> builder) {
        OperationNodes<TestOperations> nodes = TestOperationsGen.create(OperationConfig.DEFAULT, builder);
        TestOperations op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }
    private static TestOperations parseNodeWithSource(String rootName, OperationParser<TestOperationsGen.Builder> builder) {
        OperationNodes<TestOperations> nodes = TestOperationsGen.create(OperationConfig.WITH_SOURCE, builder);
        TestOperations op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    private static void testOrdering(boolean expectException, RootCallTarget root, Long... order) {
        testOrderingWithArguments(expectException, root, null, order);
    }

    private static void testOrderingWithArguments(boolean expectException, RootCallTarget root, Object[] args, Long... order) {
        List<Object> result = new ArrayList<>();

        Object[] allArgs;
        if (args == null) {
            allArgs = new Object[] {result};
        } else {
            allArgs = new Object[args.length + 1];
            allArgs[0] = result;
            System.arraycopy(args, 0, allArgs, 1, args.length);
        }

        try {
            root.call(allArgs);
            if (expectException) {
                Assert.fail();
            }
        } catch (AbstractTruffleException ex) {
            if (!expectException) {
                throw new AssertionError("unexpected", ex);
            }
        }

        Assert.assertArrayEquals("expected " + Arrays.toString(order) + " got " + result, order, result.toArray());
    }

    private static void emitReturn(TestOperationsGen.Builder b, long value) {
        b.beginReturn();
        b.emitLoadConstant(value);
        b.endReturn();
    }

    private static void emitAppend(TestOperationsGen.Builder b, long value) {
        b.beginAppenderOperation();
        b.emitLoadArgument(0);
        b.emitLoadConstant(value);
        b.endAppenderOperation();
    }

    private static void emitThrow(TestOperationsGen.Builder b, long value) {
        b.beginThrowOperation();
        b.emitLoadConstant(value);
        b.endThrowOperation();
    }

    private static void assertInstructionEquals(Instruction instr, int bci, String name) {
        assertEquals(bci, instr.getBci());
        assertEquals(name, instr.getName());
    }

    @Test
    public void testAdd() {
        // return arg0 + arg1;

        RootCallTarget root = parse("add", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(42L, root.call(20L, 22L));
        assertEquals("foobar", root.call("foo", "bar"));
        assertEquals(100L, root.call(120L, -20L));
    }

    @Test
    public void testMax() {
        // if (arg0 < arg1) {
        //   return arg1;
        // } else {
        //   return arg0;
        // }

        RootCallTarget root = parse("max", b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThenElse();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endLessThanOperation();

            b.beginReturn();
            b.emitLoadArgument(1);
            b.endReturn();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.endIfThenElse();

            b.endRoot();
        });

        assertEquals(42L, root.call(42L, 13L));
        assertEquals(42L, root.call(42L, 13L));
        assertEquals(42L, root.call(42L, 13L));
        assertEquals(42L, root.call(13L, 42L));
    }

    @Test
    public void testIfThen() {
        // if (arg0 < 0) {
        //   return 0;
        // }
        // return arg0;

        RootCallTarget root = parse("ifThen", b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            emitReturn(b, 0);

            b.endIfThen();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call(-2L));
        assertEquals(0L, root.call(-1L));
        assertEquals(0L, root.call(0L));
        assertEquals(1L, root.call(1L));
        assertEquals(2L, root.call(2L));
    }

    @Test
    public void testConditional() {
        // return arg0 < 0 ? 0 : arg0;

        RootCallTarget root = parse("conditional", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();

            b.beginConditional();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            b.emitLoadConstant(0L);

            b.emitLoadArgument(0);

            b.endConditional();

            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call(-2L));
        assertEquals(0L, root.call(-1L));
        assertEquals(0L, root.call(0L));
        assertEquals(1L, root.call(1L));
        assertEquals(2L, root.call(2L));
    }

    @Test
    public void testSumLoop() {
        // i = 0; j = 0;
        // while (i < arg0) { j = j + i; i = i + 1; }
        // return j;

        RootCallTarget root = parse("sumLoop", b -> {
            b.beginRoot(LANGUAGE);
            OperationLocal locI = b.createLocal();
            OperationLocal locJ = b.createLocal();

            b.beginStoreLocal(locI);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(locJ);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
                b.beginLessThanOperation();
                b.emitLoadLocal(locI);
                b.emitLoadArgument(0);
                b.endLessThanOperation();

                b.beginBlock();
                    b.beginStoreLocal(locJ);
                        b.beginAddOperation();
                        b.emitLoadLocal(locJ);
                        b.emitLoadLocal(locI);
                        b.endAddOperation();
                    b.endStoreLocal();

                    b.beginStoreLocal(locI);
                        b.beginAddOperation();
                        b.emitLoadLocal(locI);
                        b.emitLoadConstant(1L);
                        b.endAddOperation();
                    b.endStoreLocal();
                b.endBlock();
            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(locJ);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(45L, root.call(10L));
    }

    @Test
    public void testTryCatch() {
        // try {
        //   if (arg0 < 0) throw arg0+1
        // } catch ex {
        //   return ex.value;
        // }
        // return 0;

        RootCallTarget root = parse("tryCatch", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal local = b.createLocal();
            b.beginTryCatch(local);

            b.beginIfThen();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            b.beginThrowOperation();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endThrowOperation();

            b.endIfThen();

            b.beginReturn();
            b.beginReadExceptionOperation();
            b.emitLoadLocal(local);
            b.endReadExceptionOperation();
            b.endReturn();

            b.endTryCatch();

            emitReturn(b, 0);

            b.endRoot();
        });

        assertEquals(-42L, root.call(-43L));
        assertEquals(0L, root.call(1L));
    }

    @Test
    public void testVariableBoxingElim() {
        // local0 = 0;
        // local1 = 0;
        // while (local0 < 100) {
        //   local1 = box(local1) + local0;
        //   local0 = local0 + 1;
        // }
        // return local1;

        RootCallTarget root = parse("variableBoxingElim", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal local0 = b.createLocal();
            OperationLocal local1 = b.createLocal();

            b.beginStoreLocal(local0);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(local1);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();

            b.beginLessThanOperation();
            b.emitLoadLocal(local0);
            b.emitLoadConstant(100L);
            b.endLessThanOperation();

            b.beginBlock();

            b.beginStoreLocal(local1);
            b.beginAddOperation();
            b.beginAlwaysBoxOperation();
            b.emitLoadLocal(local1);
            b.endAlwaysBoxOperation();
            b.emitLoadLocal(local0);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginStoreLocal(local0);
            b.beginAddOperation();
            b.emitLoadLocal(local0);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(local1);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(4950L, root.call());
    }

    @Test
    public void testUndeclaredLabel() {
        // goto lbl;

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation Root ended without emitting one or more declared labels. This likely indicates a bug in the parser.");
        parse("undeclaredLabel", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();
            b.emitBranch(lbl);
            b.endRoot();
        });
    }

    @Test
    public void testUnusedLabel() {
        // lbl:
        // return 42;

        RootCallTarget root = parse("unusedLabel", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();
            b.emitLabel(lbl);
            emitReturn(b, 42);
            b.endRoot();
        });

        assertEquals(42L, root.call());
    }

    @Test
    public void testBranchInvalidStack() {
        // arg0.append({ goto lbl; 1 });  /* one value pushed to the stack already */
        // arg0.append(2);
        // lbl:
        // arg0.append(3);
        // return 0;

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Branch cannot be emitted in the middle of an operation.");
        parse("branchInvalidStack", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.beginBlock();
                b.emitBranch(lbl);
                b.emitLoadConstant(1L);
            b.endBlock();
            b.endAppenderOperation();

            emitAppend(b, 2);
            b.emitLabel(lbl);
            emitAppend(b, 3);
            emitReturn(b, 0);

            b.endRoot();
        });
    }

    @Test
    public void testFinallyTryBasic() {
        // try {
        //   arg0.append(1);
        // } finally {
        //   arg0.append(2);
        // }

        RootCallTarget root = parse("finallyTryBasic", b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry();
                emitAppend(b, 2);

                emitAppend(b, 1);
            b.endFinallyTry();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L);
    }

    @Test
    public void testFinallyTryException() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        // }

        RootCallTarget root = parse("finallyTryException", b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry();
                emitAppend(b, 3);

                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L);
    }

    @Test
    public void testFinallyTryReturn() {
        // try {
        //   arg0.append(2);
        //   return 0;
        // } finally {
        //   arg0.append(1);
        // }
        // arg0.append(3);

        RootCallTarget root = parse("finallyTryReturn", b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry();
                emitAppend(b, 1);

                b.beginBlock();
                    emitAppend(b, 2);

                    emitReturn(b, 0);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 3);

            b.endRoot();
        });

        testOrdering(false, root, 2L, 1L);
    }

    @Test
    public void testFinallyTryBranchOut() {
        // try {
        //   arg0.append(1);
        //   goto lbl;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        // }
        // arg0.append(4)
        // lbl:
        // arg0.append(5);

        RootCallTarget root = parse("finallyTryBranchOut", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();

            b.beginFinallyTry();
                emitAppend(b, 3);

                b.beginBlock();
                    emitAppend(b, 1);
                    b.emitBranch(lbl);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 4);
            b.emitLabel(lbl);
            emitAppend(b, 5);
            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryBranchForwardOutOfHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        // } finally {
        //   arg0.append(2);
        //   goto lbl;
        // }
        // arg0.append(3);
        // lbl:
        // arg0.append(4);

        RootCallTarget root = parse("finallyTryBranchForwardOutOfHandler", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 2);
                    b.emitBranch(lbl);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 3);
            b.emitLabel(lbl);
            emitAppend(b, 4);
            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L, 4L);
    }

    @Test
    public void testFinallyTryBranchBackwardOutOfHandler() {
        // tee(0, local);
        // arg0.append(1);
        // lbl:
        // if (0 < local) {
        //   arg0.append(4);
        //   return 0;
        // }
        // try {
        //   tee(1, local);
        //   arg0.append(2);
        //   return 0;
        // } finally {
        //   arg0.append(3);
        //   goto lbl;
        // }
        // arg0.append(5);

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Backward branches are unsupported. Use a While operation to model backward control flow.");
        parse("finallyTryBranchBackwardOutOfHandler", b -> {
            b.beginRoot(LANGUAGE);
            OperationLabel lbl = b.createLabel();
            OperationLocal local = b.createLocal();

            b.beginTeeLocal(local);
            b.emitLoadConstant(0L);
            b.endTeeLocal();

            emitAppend(b, 1);

            b.emitLabel(lbl);
            b.beginIfThen();
                b.beginLessThanOperation();
                b.emitLoadConstant(0L);
                b.emitLoadLocal(local);
                b.endLessThanOperation();

                b.beginBlock();
                    emitAppend(b, 4);
                    emitReturn(b, 0);
                b.endBlock();
            b.endIfThen();

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);
                    b.emitBranch(lbl);
                b.endBlock();

                b.beginBlock();
                    b.beginTeeLocal(local);
                    b.emitLoadConstant(1L);
                    b.endTeeLocal();
                    emitAppend(b, 2);
                    emitReturn(b, 0);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 5);

            b.endRoot();
        });
    }

    /*
     * The following few test cases have local control flow inside finally handlers.
     * Since finally handlers are relocated, these local branches should be properly
     * adjusted by the builder.
     */

    @Test
    public void testFinallyTryBranchWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   goto lbl;
        //   arg0.append(4);
        //   lbl:
        //   arg0.append(5);
        // }
        // arg0.append(6);

        RootCallTarget root = parse("finallyTryBranchWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    OperationLabel lbl = b.createLabel();
                    emitAppend(b, 3);
                    b.emitBranch(lbl);
                    emitAppend(b, 4);
                    b.emitLabel(lbl);
                    emitAppend(b, 5);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 6);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryIfThenWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   if (arg1) {
        //     arg0.append(4);
        //   }
        //   arg0.append(5);
        // }
        // arg0.append(6);

        RootCallTarget root = parse("finallyTryIfThenWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginIfThen();
                    b.emitLoadArgument(1);
                    emitAppend(b, 4);
                    b.endIfThen();

                    emitAppend(b, 5);

                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 6);

            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 3L, 5L);
        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 5L);
    }

    @Test
    public void testFinallyTryIfThenElseWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   if (arg1) {
        //     arg0.append(4);
        //   } else {
        //     arg0.append(5);
        //   }
        //   arg0.append(6);
        // }
        // arg0.append(7);

        RootCallTarget root = parse("finallyTryIfThenElseWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginIfThenElse();
                        b.emitLoadArgument(1);

                        emitAppend(b, 4);

                        emitAppend(b, 5);
                    b.endIfThenElse();

                    emitAppend(b, 6);

                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 7);

            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 3L, 5L, 6L);
        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 6L);
    }

    @Test
    public void testFinallyTryConditionalWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   (false) ? { arg0.append(4); 0 } : { arg0.append(5); 0 }
        //   (true) ? { arg0.append(6); 0 } : { arg0.append(7); 0 }
        //   arg0.append(8);
        // }
        // arg0.append(9);

        RootCallTarget root = parse("finallyTryConditionalWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginConditional();
                        b.emitLoadConstant(false);
                        b.beginBlock();
                            emitAppend(b, 4);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                        b.beginBlock();
                            emitAppend(b, 5);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                    b.endConditional();

                    b.beginConditional();
                        b.emitLoadConstant(true);
                        b.beginBlock();
                            emitAppend(b, 6);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                        b.beginBlock();
                            emitAppend(b, 7);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                    b.endConditional();

                    emitAppend(b, 8);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 9);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L, 6L, 8L);
    }

    @Test
    public void testFinallyTryLoopWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   tee(local, 4);
        //   while (local < 7) {
        //     arg0.append(local);
        //     tee(local, local+1);
        //   }
        //   arg0.append(8);
        // }
        // arg0.append(9);

        RootCallTarget root = parse("finallyTryLoopWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal local = b.createLocal();

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginTeeLocal(local);
                    b.emitLoadConstant(4L);
                    b.endTeeLocal();

                    b.beginWhile();
                        b.beginLessThanOperation();
                            b.emitLoadLocal(local);
                            b.emitLoadConstant(7L);
                        b.endLessThanOperation();

                        b.beginBlock();
                            b.beginAppenderOperation();
                            b.emitLoadArgument(0);
                            b.emitLoadLocal(local);
                            b.endAppenderOperation();

                            b.beginTeeLocal(local);
                                b.beginAddOperation();
                                    b.emitLoadLocal(local);
                                    b.emitLoadConstant(1L);
                                b.endAddOperation();
                            b.endTeeLocal();
                        b.endBlock();
                    b.endWhile();

                    emitAppend(b, 8);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 9);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 4L, 5L, 6L, 8L);
    }


    @Test
    public void testFinallyTryShortCircuitOpWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   { arg0.append(4); true } && { arg0.append(5); false } && { arg0.append(6); true }
        //   { arg0.append(7); false } || { arg0.append(8); true } || { arg0.append(9); false }
        //   arg0.append(10);
        // }
        // arg0.append(11);

        RootCallTarget root = parse("finallyTryShortCircuitOpWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginScAnd();
                        b.beginBlock();
                            emitAppend(b, 4);
                            b.emitLoadConstant(true);
                        b.endBlock();

                        b.beginBlock();
                            emitAppend(b, 5);
                            b.emitLoadConstant(false);
                        b.endBlock();

                        b.beginBlock();
                            emitAppend(b, 6);
                            b.emitLoadConstant(true);
                        b.endBlock();
                    b.endScAnd();

                    b.beginScOr();
                        b.beginBlock();
                            emitAppend(b, 7);
                            b.emitLoadConstant(false);
                        b.endBlock();

                        b.beginBlock();
                            emitAppend(b, 8);
                            b.emitLoadConstant(true);
                        b.endBlock();

                        b.beginBlock();
                            emitAppend(b, 9);
                            b.emitLoadConstant(false);
                        b.endBlock();
                    b.endScOr();

                    emitAppend(b, 10);

                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 11);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 4L, 5L, 7L, 8L, 10L);
    }

    @Test
    public void testFinallyTryNonThrowingTryCatchWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   try {
        //     arg0.append(4);
        //   } catch {
        //     arg0.append(5);
        //   }
        //   arg0.append(6);
        // }
        // arg0.append(7);

        RootCallTarget root = parse("finallyTryNonThrowingTryCatchWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginTryCatch(b.createLocal());
                        emitAppend(b, 4);

                        emitAppend(b, 5);
                    b.endTryCatch();

                    emitAppend(b, 6);

                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 7);
            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 4L, 6L);
    }

    @Test
    public void testFinallyTryThrowingTryCatchWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   try {
        //     arg0.append(4);
        //     throw 0;
        //     arg0.append(5);
        //   } catch {
        //     arg0.append(6);
        //   }
        //   arg0.append(7);
        // }
        // arg0.append(8);

        RootCallTarget root = parse("finallyTryThrowingTryCatchWithinHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginTryCatch(b.createLocal());
                        b.beginBlock();
                            emitAppend(b, 4);
                            emitThrow(b, 0);
                            emitAppend(b, 5);
                        b.endBlock();

                        emitAppend(b, 6);
                    b.endTryCatch();

                    emitAppend(b, 7);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            emitAppend(b, 8);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 4L, 6L, 7L);
    }

    @Test
    public void testFinallyTryBranchWithinHandlerNoLabel() {
        // try {
        //   return 0;
        // } finally {
        //   goto lbl;
        //   return 0;
        // }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation Block ended without emitting one or more declared labels. This likely indicates a bug in the parser.");
        parse("finallyTryBranchWithinHandlerNoLabel", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    OperationLabel lbl = b.createLabel();

                    b.emitBranch(lbl);

                    emitReturn(b, 0);
                b.endBlock();

                b.beginBlock();
                    emitReturn(b, 0);
                b.endBlock();
            b.endFinallyTry();
            b.endRoot();
        });
    }

    @Test
    public void testFinallyTryBranchIntoTry() {
        // try {
        //   return 0;
        //   lbl:
        //   return 0;
        // } finally {
        //   goto lbl;
        //   return 0;
        // }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation Block ended without emitting one or more declared labels. This likely indicates a bug in the parser.");
        parse("finallyTryBranchIntoTry", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    OperationLabel lbl = b.createLabel();

                    b.emitBranch(lbl);

                    emitReturn(b, 0);
                b.endBlock();

                b.beginBlock();
                    emitReturn(b, 0);
                    b.emitLabel(lbl);
                    emitReturn(b, 0);
                b.endBlock();
            b.endFinallyTry();

            b.endRoot();
        });
    }

    @Test
    public void testFinallyTryBranchIntoFinally() {
        // try {
        //   goto lbl;
        //   return 0;
        // } finally {
        //   lbl:
        //   return 0;
        // }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.");
        parse("finallyTryBranchIntoFinally", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    OperationLabel lbl = b.createLabel();
                    b.emitLabel(lbl);
                    emitReturn(b, 0);
                b.endBlock();

                b.beginBlock();
                    b.emitBranch(lbl);
                    emitReturn(b, 0);
                b.endBlock();
            b.endFinallyTry();

            b.endRoot();
        });
    }

    @Test
    public void testFinallyTryBranchIntoOuterFinally() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   try {
        //     arg0.append(3);
        //     return 0;
        //     arg0.append(4);
        //   } finally {
        //     arg0.append(5);
        //     goto lbl;
        //     arg0.append(6);
        //   }
        //   arg0.append(7);
        //   lbl:
        //   arg0.append(8);
        //   return 0;
        // }

        RootCallTarget root = parse("finallyTryBranchIntoOuterFinally", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    OperationLabel lbl = b.createLabel();

                    b.beginFinallyTry();
                        b.beginBlock();
                            emitAppend(b, 5);
                            b.emitBranch(lbl);
                            emitAppend(b, 6);
                        b.endBlock();

                        b.beginBlock();
                            emitAppend(b, 3);
                            emitReturn(b, 0);
                            emitAppend(b, 4);
                        b.endBlock();
                    b.endFinallyTry();

                    emitAppend(b, 7);
                    b.emitLabel(lbl);
                    emitAppend(b, 8);
                    emitReturn(b, 0);

                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();
            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L, 8L);
    }

    @Test
    public void testFinallyTryBranchIntoOuterFinallyNestedInAnotherFinally() {
        // try {                    // a
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   try {                  // b
        //     arg0.append(3);
        //     return 0;
        //     arg0.append(4);
        //   } finally {
        //     arg0.append(5);
        //     try {                // c
        //       arg0.append(6);
        //       return 0;
        //       arg0.append(7);
        //     } finally {
        //       arg0.append(8);
        //       goto lbl;
        //       arg0.append(9);
        //     }
        //     arg0.append(10);
        //     lbl:
        //     arg0.append(11);
        //   }
        //   arg0.append(12);
        //   return 0;
        // }

        RootCallTarget root = parse("finallyTryBranchIntoOuterFinallyNestedInAnotherFinally", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry(); // a
                b.beginBlock();
                    b.beginFinallyTry(); // b
                        b.beginBlock();
                            OperationLabel lbl = b.createLabel();

                            emitAppend(b, 5);
                            b.beginFinallyTry(); // c
                                b.beginBlock();
                                    emitAppend(b, 8);
                                    b.emitBranch(lbl);
                                    emitAppend(b, 9);
                                b.endBlock();

                                b.beginBlock();
                                    emitAppend(b, 6);
                                    emitReturn(b, 0);
                                    emitAppend(b, 7);
                                b.endBlock();
                            b.endFinallyTry();

                            emitAppend(b, 10);
                            b.emitLabel(lbl);
                            emitAppend(b, 11);
                        b.endBlock();

                        b.beginBlock(); // b try
                            emitAppend(b, 3);
                            emitReturn(b, 0);
                            emitAppend(b, 4);
                        b.endBlock();

                    b.endFinallyTry();

                    emitAppend(b, 12);
                    emitReturn(b, 0);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();
            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L, 6L, 8L, 11L);
    }

    @Test
    public void testFinallyTryBranchWhileInParentHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   try {
        //     arg0.append(4);
        //     // even though we're not in the inner handler, we are in the outer handler, so this branch should be relativized.
        //     goto lbl;
        //     arg0.append(5);
        //     lbl:
        //     arg0.append(6);
        //   } finally {
        //     arg0.append(7);
        //   }
        //   arg0.append(8);
        // }

        RootCallTarget root = parse("finallyTryBranchWhileInParentHandler", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginFinallyTry();
                        emitAppend(b, 7);

                        b.beginBlock();
                            OperationLabel lbl = b.createLabel();
                            emitAppend(b, 4);
                            b.emitBranch(lbl);
                            emitAppend(b, 5);
                            b.emitLabel(lbl);
                            emitAppend(b, 6);
                        b.endBlock();
                    b.endFinallyTry();

                    emitAppend(b, 8);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();

            b.endFinallyTry();
            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 4L, 6L, 7L, 8L);
    }

    @Test
    public void testFinallyTryNestedTry() {
        // try {
        //   try {
        //     arg0.append(1);
        //     return 0;
        //     arg0.append(2);
        //   } finally {
        //     arg0.append(3);
        //   }
        //   arg0.append(4);
        // } finally {
        //   arg0.append(5);
        // }

        RootCallTarget root = parse("finallyTryNestedTry", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 5);
                b.endBlock();

                b.beginBlock();
                    b.beginFinallyTry();
                        b.beginBlock();
                            emitAppend(b, 3);
                        b.endBlock();

                        b.beginBlock();
                            emitAppend(b, 1);
                            emitReturn(b, 0);
                            emitAppend(b, 2);
                        b.endBlock();
                    b.endFinallyTry();

                    emitAppend(b, 5);
                b.endBlock();

            b.endFinallyTry();

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNestedFinally() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally {
        //   try {
        //     arg0.append(3);
        //     return 0;
        //     arg0.append(4);
        //   } finally {
        //     arg0.append(5);
        //   }
        // }

        RootCallTarget root = parse("finallyTryNestedFinally", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginFinallyTry();
                    b.beginBlock();
                        emitAppend(b, 5);
                    b.endBlock();

                    b.beginBlock();
                        emitAppend(b, 3);
                        emitReturn(b, 0);
                        emitAppend(b, 4);
                    b.endBlock();
                b.endFinallyTry();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNestedTryThrow() {
        // try {
        //   try {
        //     arg0.append(1);
        //     throw 0;
        //     arg0.append(2);
        //   } finally {
        //     arg0.append(3);
        //   }
        // } finally {
        //   arg0.append(4);
        // }

        RootCallTarget root = parse("finallyTryNestedTryThrow", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginBlock();
                    emitAppend(b, 4);
                b.endBlock();

                b.beginFinallyTry();
                    b.beginBlock();
                        emitAppend(b, 3);
                    b.endBlock();

                    b.beginBlock();
                        emitAppend(b, 1);
                        emitThrow(b, 0);
                        emitAppend(b, 2);
                    b.endBlock();
                b.endFinallyTry();
            b.endFinallyTry();

            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L, 4L);
    }

    @Test
    public void testFinallyTryNestedFinallyThrow() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } finally {
        //   try {
        //     arg0.append(3);
        //     throw 0;
        //     arg0.append(4);
        //   } finally {
        //     arg0.append(5);
        //   }
        // }

        RootCallTarget root = parse("finallyTryNestedFinallyThrow", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();
                b.beginFinallyTry();
                    b.beginBlock();
                        emitAppend(b, 5);
                    b.endBlock();

                    b.beginBlock();
                        emitAppend(b, 3);
                        emitThrow(b, 0);
                        emitAppend(b, 4);
                    b.endBlock();
                b.endFinallyTry();

                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTry();

            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNoExceptReturn() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } finally noexcept {
        //   arg0.append(3);
        // }

        RootCallTarget root = parse("finallyTryNoExceptReturn", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTryNoExcept();
                emitAppend(b, 3);

                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTryNoExcept();

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L);
    }

    @Test
    public void testFinallyTryNoExceptException() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } finally noexcept {
        //   arg0.append(3);
        // }

        RootCallTarget root = parse("finallyTryNoExceptException", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTryNoExcept();
                emitAppend(b, 3);

                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endFinallyTryNoExcept();

            b.endRoot();
        });

        testOrdering(true, root, 1L);
    }


    @Test
    public void testTeeLocal() {
        // tee(local, 1);
        // return local;

        RootCallTarget root = parse("teeLocal", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal local = b.createLocal();

            b.beginTeeLocal(local);
            b.emitLoadConstant(1L);
            b.endTeeLocal();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testTeeLocalRange() {
        // teeRange([local1, local2], [1, 2]));
        // return local2;

        RootCallTarget root = parse("teeLocalRange", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal local1 = b.createLocal();
            OperationLocal local2 = b.createLocal();

            b.beginTeeLocalRange(new OperationLocal[] {local1, local2});
            b.emitLoadConstant(new long[] {1L, 2L});
            b.endTeeLocalRange();

            b.beginReturn();
            b.emitLoadLocal(local2);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(2L, root.call());
    }

    @Test
    public void testYield() {
        // yield 1;
        // yield 2;
        // return 3;

        RootCallTarget root = parse("yield", b -> {
            b.beginRoot(LANGUAGE);

            b.beginYield();
            b.emitLoadConstant(1L);
            b.endYield();

            b.beginYield();
            b.emitLoadConstant(2L);
            b.endYield();

            emitReturn(b, 3);

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(1L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(null);
        assertEquals(2L, r2.getResult());

        assertEquals(3L, r2.continueWith(null));
    }


    @Test
    public void testYieldLocal() {
        // local = 0;
        // yield local;
        // local = local + 1;
        // yield local;
        // local = local + 1;
        // return local;

        RootCallTarget root = parse("yieldLocal", b -> {
            b.beginRoot(LANGUAGE);
            OperationLocal local = b.createLocal();

            b.beginStoreLocal(local);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadLocal(local);
            b.endYield();

            b.beginStoreLocal(local);
            b.beginAddOperation();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadLocal(local);
            b.endYield();

            b.beginStoreLocal(local);
            b.beginAddOperation();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginReturn();
            b.emitLoadLocal(local);
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(0L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(null);
        assertEquals(1L, r2.getResult());

        assertEquals(2L, r2.continueWith(null));
    }
    @Test
    public void testYieldStack() {
        // return (yield 1) + (yield 2);

        RootCallTarget root = parse("yieldStack", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();

            b.beginYield();
            b.emitLoadConstant(1L);
            b.endYield();

            b.beginYield();
            b.emitLoadConstant(2L);
            b.endYield();

            b.endAddOperation();
            b.endReturn();


            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(1L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(2L, r2.getResult());

        assertEquals(7L, r2.continueWith(4L));
    }

    @Test
    public void testYieldFromFinally() {
        // try {
        //   yield 1;
        //   if (false) {
        //     return 2;
        //   } else {
        //     return 3;
        //   }
        // } finally {
        //   yield 4;
        // }

        RootCallTarget root = parse("yieldFromFinally", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry();

                b.beginYield();
                b.emitLoadConstant(4L);
                b.endYield();

                b.beginBlock();

                    b.beginYield();
                    b.emitLoadConstant(1L);
                    b.endYield();

                    b.beginIfThenElse();

                        b.emitLoadConstant(false);

                        emitReturn(b, 2);

                        emitReturn(b, 3);

                    b.endIfThenElse();

                b.endBlock();
            b.endFinallyTry();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call();
        assertEquals(1L, r1.getResult());

        ContinuationResult r2 = (ContinuationResult) r1.continueWith(3L);
        assertEquals(4L, r2.getResult());

        assertEquals(3L, r2.continueWith(4L));
    }

    @Test
    public void testNestedFunctions() {
        // return (() -> return 1)();

        RootCallTarget root = parse("nestedFunctions", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();

            b.beginInvoke();

                b.beginRoot(LANGUAGE);

                emitReturn(b, 1);

                TestOperations innerRoot = b.endRoot();

                b.emitLoadConstant(innerRoot);

            b.endInvoke();

            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    @Ignore
    public void testLocalsNonlocalRead() {
        // todo: this test fails when boxing elimination is enabled
        // locals accessed non-locally must have boxing elimination disabled
        // since non-local reads do not do boxing elimination

        // this can be done automatically, or by
        // having `createLocal(boolean accessedFromClosure)` or similar
        RootCallTarget root = parse("localsNonlocalRead", b -> {
            // x = 1
            // return (lambda: x)()
            b.beginRoot(LANGUAGE);

            OperationLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();

            b.beginReturn();

            b.beginInvoke();

                b.beginRoot(LANGUAGE);
                b.beginReturn();
                b.beginLoadLocalMaterialized(xLoc);
                b.emitLoadArgument(0);
                b.endLoadLocalMaterialized();
                b.endReturn();
                TestOperations inner = b.endRoot();

            b.beginCreateClosure();
            b.emitLoadConstant(inner);
            b.endCreateClosure();

            b.endInvoke();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testLocalsNonlocalWrite() {
        // x = 1;
        // ((x) -> x = 2)();
        // return x;

        RootCallTarget root = parse("localsNonlocalWrite", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal xLoc = b.createLocal();

            b.beginStoreLocal(xLoc);
            b.emitLoadConstant(1L);
            b.endStoreLocal();


            b.beginInvoke();

                b.beginRoot(LANGUAGE);

                b.beginStoreLocalMaterialized(xLoc);
                b.emitLoadArgument(0);
                b.emitLoadConstant(2L);
                b.endStoreLocalMaterialized();

                b.beginReturn();
                b.emitLoadConstant(null);
                b.endReturn();

                TestOperations inner = b.endRoot();

            b.beginCreateClosure();
            b.emitLoadConstant(inner);
            b.endCreateClosure();

            b.endInvoke();

            b.beginReturn();
            b.emitLoadLocal(xLoc);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(2L, root.call());
    }

    @Test
    public void testBranchForward() {
        // goto lbl;
        // return 0;
        // lbl:
        // return 1;

        RootCallTarget root = parse("branchForward", b -> {
            b.beginRoot(LANGUAGE);

            OperationLabel lbl = b.createLabel();

            b.emitBranch(lbl);
            emitReturn(b, 0);
            b.emitLabel(lbl);
            emitReturn(b, 1);
            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testBranchBackward() {
        // x = 0;
        // lbl:
        // if (5 < x) return x;
        // x = x + 1;
        // goto lbl;

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Backward branches are unsupported. Use a While operation to model backward control flow.");
        parse("branchBackward", b -> {
            b.beginRoot(LANGUAGE);

            OperationLabel lbl = b.createLabel();
            OperationLocal loc = b.createLocal();

            b.beginStoreLocal(loc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.emitLabel(lbl);

            b.beginIfThen();

                b.beginLessThanOperation();
                b.emitLoadConstant(5L);
                b.emitLoadLocal(loc);
                b.endLessThanOperation();

                b.beginReturn();
                b.emitLoadLocal(loc);
                b.endReturn();

            b.endIfThen();

            b.beginStoreLocal(loc);
            b.beginAddOperation();
            b.emitLoadLocal(loc);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.emitBranch(lbl);

            b.endRoot();
        });
    }

    @Test
    public void testBranchOutwardValid() {
        // {
        //   if(arg0 < 0) goto lbl;
        //   return 123;
        // }
        // lbl:
        // return 42;

        RootCallTarget root = parse("branchOutwardValid", b -> {
            b.beginRoot(LANGUAGE);

            OperationLabel lbl = b.createLabel();

            b.beginBlock();
              b.beginIfThen();

              b.beginLessThanOperation();
              b.emitLoadArgument(0);
              b.emitLoadConstant(0L);
              b.endLessThanOperation();

              b.emitBranch(lbl);

              b.endIfThen();

              emitReturn(b, 123L);
            b.endBlock();

            b.emitLabel(lbl);

            emitReturn(b, 42);

            b.endRoot();
        });

        assertEquals(123L, root.call(1L));
        assertEquals(42L, root.call(-1L));
    }

    @Test
    public void testBranchOutwardInvalid() {
        // return 1 + { goto lbl; 2 }
        // lbl:
        // return 0;

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Branch cannot be emitted in the middle of an operation.");
        parse("branchOutwardInvalid", b -> {
            b.beginRoot(LANGUAGE);

            OperationLabel lbl = b.createLabel();

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.beginBlock();
              b.emitBranch(lbl);
              b.emitLoadConstant(2L);
            b.endBlock();
            b.endAddOperation();
            b.endReturn();

            b.emitLabel(lbl);

            emitReturn(b, 0);

            b.endRoot();
        });

    }

    @Test
    public void testBranchInward() {
        // goto lbl;
        // return 1 + { lbl: 2 }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("OperationLabel must be emitted inside the same operation it was created in.");
        parse("branchInward", b -> {
            b.beginRoot(LANGUAGE);

            OperationLabel lbl = b.createLabel();
            b.emitBranch(lbl);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.beginBlock();
              b.emitLabel(lbl);
              b.emitLoadConstant(2L);
            b.endBlock();
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testInvalidLabelDeclaration() {
        // return 1 + {lbl: 2}

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("OperationLabel cannot be emitted in the middle of an operation.");
        parse("invalidLabelDeclaration", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.beginBlock();
                OperationLabel lbl = b.createLabel();
              b.emitLabel(lbl);
              b.emitLoadConstant(2L);
            b.endBlock();
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testBranchIntoAnotherBlock() {
        // { lbl: return 0 }
        // { goto lbl; }

        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Branch must be targeting a label that is declared in an enclosing operation. Jumps into other operations are not permitted.");
        parse("branchIntoAnotherBlock", b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();
                OperationLabel lbl = b.createLabel();
                b.emitLabel(lbl);
                emitReturn(b, 0);
            b.endBlock();

            b.beginBlock();
                b.emitBranch(lbl);
            b.endBlock();

            b.endRoot();
        });
    }

    @Test
    public void testVariadicZeroVarargs()  {
        // return veryComplex(7);

        RootCallTarget root = parse("variadicZeroVarargs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(7L, root.call());
    }

    @Test
    public void testVariadicOneVarargs()  {
        // return veryComplex(7, "foo");

        RootCallTarget root = parse("variadicOneVarargs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            b.emitLoadConstant("foo");
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(8L, root.call());
    }

    @Test
    public void testVariadicFewVarargs()  {
        // return veryComplex(7, "foo", "bar", "baz");

        RootCallTarget root = parse("variadicFewVarargs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            b.emitLoadConstant("foo");
            b.emitLoadConstant("bar");
            b.emitLoadConstant("baz");
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(10L, root.call());
    }

    @Test
    public void testVariadicManyVarargs()  {
        // return veryComplex(7, [1330 args]);

        RootCallTarget root = parse("variadicManyVarArgs", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.emitLoadConstant(7L);
            for (int i = 0; i < 1330; i++) {
                b.emitLoadConstant("test");
            }
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1337L, root.call());
    }

    @Test
    public void testVariadicTooFewArguments()  {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation VeryComplexOperation expected at least 1 child, but 0 provided. This is probably a bug in the parser.");

        parse("variadicTooFewArguments", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginVeryComplexOperation();
            b.endVeryComplexOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testValidationTooFewArguments() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation AddOperation expected exactly 2 children, but 1 provided. This is probably a bug in the parser.");

        parse("validationTooFewArguments", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testValidationTooManyArguments() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation AddOperation expected exactly 2 children, but 3 provided. This is probably a bug in the parser.");

        parse("validationTooManyArguments", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(1L);
            b.emitLoadConstant(2L);
            b.emitLoadConstant(3L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testValidationNotValueArgument() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation AddOperation expected a value-producing child at position 0, but a void one was provided. This likely indicates a bug in the parser.");

        parse("validationNotValueArgument", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitVoidOperation();
            b.emitLoadConstant(2L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testSource() {
        Source source = Source.newBuilder("test", "return 1", "test.test").build();
        TestOperations node = parseNodeWithSource("source", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSource(source);
            b.beginSourceSection(0, 8);

            b.beginReturn();

            b.beginSourceSection(7, 1);
            b.emitLoadConstant(1L);
            b.endSourceSection();

            b.endReturn();

            b.endSourceSection();
            b.endSource();
            b.endRoot();
        });

        assertEquals(node.getSourceSection().getSource(), source);
        assertEquals(node.getSourceSection().getCharIndex(), 0);
        assertEquals(node.getSourceSection().getCharLength(), 8);

        // load constant
        assertEquals(node.getSourceSectionAtBci(0).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(0).getCharIndex(), 7);
        assertEquals(node.getSourceSectionAtBci(0).getCharLength(), 1);

        // return
        assertEquals(node.getSourceSectionAtBci(2).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(2).getCharIndex(), 0);
        assertEquals(node.getSourceSectionAtBci(2).getCharLength(), 8);
    }

    @Test
    public void testSourceNoSourceSet() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.");
        parseNodeWithSource("sourceNoSourceSet", b -> {
            b.beginRoot(LANGUAGE);
            b.beginSourceSection(0, 8);

            b.beginReturn();

            b.beginSourceSection(7, 1);
            b.emitLoadConstant(1L);
            b.endSourceSection();

            b.endReturn();

            b.endSourceSection();
            b.endRoot();
        });
    }



    @Test
    public void testSourceMultipleSources() {
        Source source1 = Source.newBuilder("test", "This is just a piece of test source.", "test1.test").build();
        Source source2 = Source.newBuilder("test", "This is another test source.", "test2.test").build();
        TestOperations root = parseNodeWithSource("sourceMultipleSources", b -> {
            b.beginRoot(LANGUAGE);

            b.emitVoidOperation(); // no source

            b.beginSource(source1);
            b.beginBlock();

            b.emitVoidOperation(); // no source

            b.beginSourceSection(1, 2);
            b.beginBlock();

            b.emitVoidOperation(); // source1, 1, 2

            b.beginSource(source2);
            b.beginBlock();

            b.emitVoidOperation(); // no source

            b.beginSourceSection(3,  4);
            b.beginBlock();

            b.emitVoidOperation(); // source2, 3, 4

            b.beginSourceSection(5, 1);
            b.beginBlock();

            b.emitVoidOperation(); // source2, 5, 1

            b.endBlock();
            b.endSourceSection();

            b.emitVoidOperation(); // source2, 3, 4

            b.endBlock();
            b.endSourceSection();

            b.emitVoidOperation(); // no source

            b.endBlock();
            b.endSource();

            b.emitVoidOperation(); // source1, 1, 2

            b.endBlock();
            b.endSourceSection();

            b.emitVoidOperation(); // no source

            b.endBlock();
            b.endSource();

            b.emitVoidOperation(); // no source

            b.endRoot();
        });

        assertEquals(root.getSourceSection().getSource(), source1);
        assertEquals(root.getSourceSection().getCharIndex(), 1);
        assertEquals(root.getSourceSection().getCharLength(), 2);

        Source[] sources = {null, source1, source2};

        int[][] expected = {
                        null,
                        null,
                        {1, 1, 2},
                        null,
                        {2, 3, 4},
                        {2, 5, 1},
                        {2, 3, 4},
                        null,
                        {1, 1, 2},
                        null,
                        null,
        };

        for (int i = 0; i < expected.length; i++) {
            // Each Void operation is encoded as two shorts: the Void opcode, and a node index.
            // The source section for both should match the expected value.
            for (int j = i*2; j < i*2 + 2; j++) {
                if (expected[i] == null) {
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j), null);
                } else {
                    assertNotNull("Mismatch at bci " + j, root.getSourceSectionAtBci(j));
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j).getSource(), sources[expected[i][0]]);
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j).getCharIndex(), expected[i][1]);
                    assertEquals("Mismatch at bci " + j, root.getSourceSectionAtBci(j).getCharLength(), expected[i][2]);
                }
            }
        }
    }

    @Test
    public void testShortCircuitingAllPass() {
        // return 1 && true && "test";

        RootCallTarget root = parse("shortCircuitingAllPass", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant(1L);
            b.emitLoadConstant(true);
            b.emitLoadConstant("test");
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals("test", root.call());
    }

    @Test
    public void testShortCircuitingLastFail() {
        // return 1 && "test" && 0;

        RootCallTarget root = parse("shortCircuitingLastFail", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant(1L);
            b.emitLoadConstant("test");
            b.emitLoadConstant(0L);
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call());
    }

    @Test
    public void testShortCircuitingFirstFail() {
        // return 0 && "test" && 1;

        RootCallTarget root = parse("shortCircuitingFirstFail", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant(0L);
            b.emitLoadConstant("test");
            b.emitLoadConstant(1L);
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call());
    }

    @Test
    public void testShortCircuitingNoChildren() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation ScAnd expected at least 1 child, but 0 provided. This is probably a bug in the parser.");
        parse("shortCircuitingNoChildren", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginScAnd();
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testShortCircuitingNonValueChild() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation ScAnd expected a value-producing child at position 1, but a void one was provided. This likely indicates a bug in the parser.");
        parse("shortCircuitingNonValueChild", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginScAnd();
            b.emitLoadConstant("test");
            b.emitVoidOperation();
            b.emitLoadConstant("tost");
            b.endScAnd();
            b.endReturn();

            b.endRoot();
        });
    }

    @Test
    public void testIntrospectionData() {
        TestOperations node = parseNode("introspectionData", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        OperationIntrospection data = node.getIntrospectionData();

        assertEquals(5, data.getInstructions().size());
        assertInstructionEquals(data.getInstructions().get(0), 0, "load.argument");
        assertInstructionEquals(data.getInstructions().get(1), 2, "load.argument");
        assertInstructionEquals(data.getInstructions().get(2), 4, "c.AddOperation");
        assertInstructionEquals(data.getInstructions().get(3), 6, "return");
        // todo: with DCE, this pop will go away (since return is considered as returning a value)
        assertInstructionEquals(data.getInstructions().get(4), 7, "pop");
    }

    @Test
    public void testCloneUninitializedAdd() {
        // return arg0 + arg1;

        TestOperations testOperations = parseNode("cloneUninitializedAdd", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });
        RootCallTarget root = testOperations.getCallTarget();

        // Run enough times to trigger cached execution.
        for (int i = 0; i < 16; i++) {
            assertEquals(42L, root.call(20L, 22L));
            assertEquals("foobar", root.call("foo", "bar"));
            assertEquals(100L, root.call(120L, -20L));
        }

        TestOperations cloned = testOperations.doCloneUninitialized();
        assertNotEquals(testOperations.getCallTarget(), cloned.getCallTarget());
        root = cloned.getCallTarget();

        // Run enough times to trigger cached execution again. The transition should work without crashing.
        for (int i = 0; i < 16; i++) {
            assertEquals(42L, root.call(20L, 22L));
            assertEquals("foobar", root.call("foo", "bar"));
            assertEquals(100L, root.call(120L, -20L));
        }
    }

    @Test
    public void testCloneUninitializedFields() {
        TestOperations testOperations = parseNode("cloneUninitializedFields", b -> {
            b.beginRoot(LANGUAGE);
            emitReturn(b, 0);
            b.endRoot();
        });

        TestOperations cloned = testOperations.doCloneUninitialized();
        assertEquals("User field was not copied to the uninitialized clone.", testOperations.name, cloned.name);
    }

    @Test
    @Ignore
    public void testDecisionQuicken() {
        TestOperations node = parseNode("decisionQuicken", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        // todo: these tests do not pass, since quickening is not implemented yet properly

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(2), 2, "c.AddOperation");

        assertEquals(3L, node.getCallTarget().call(1L, 2L));

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(2), 2, "c.AddOperation.q.AddLongs");

        assertEquals("foobar", node.getCallTarget().call("foo", "bar"));

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(2), 2, "c.AddOperation");
    }

    @Test
    @Ignore
    public void testDecisionSuperInstruction() {
        TestOperations node = parseNode("decisionSuperInstruction", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endLessThanOperation();
            b.endReturn();

            b.endRoot();
        });

        // todo: these tests do not pass, since quickening is not implemented yet properly

        assertInstructionEquals(node.getIntrospectionData().getInstructions().get(1), 1, "si.load.argument.c.LessThanOperation");
    }
}
