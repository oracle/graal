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
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
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

    private static final TestLanguage LANGUAGE = null;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static RootCallTarget parse(OperationParser<TestOperationsGen.Builder> builder) {
        OperationRootNode operationsNode = parseNode(builder);
        return ((RootNode) operationsNode).getCallTarget();
    }

    private static TestOperations parseNode(OperationParser<TestOperationsGen.Builder> builder) {
        OperationNodes<TestOperations> nodes = TestOperationsGen.create(OperationConfig.DEFAULT, builder);
        TestOperations op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        System.out.println(op.dump());
        return op;
    }
    private static TestOperations parseNodeWithSource(OperationParser<TestOperationsGen.Builder> builder) {
        OperationNodes<TestOperations> nodes = TestOperationsGen.create(OperationConfig.WITH_SOURCE, builder);
        TestOperations op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        System.out.println(op.dump());
        return op;
    }

    @Test
    public void testExampleAdd() {
        RootCallTarget root = parse(b -> {
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
    public void testExampleMax() {
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

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

        RootCallTarget root = parse(b -> {
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
    public void testExampleSumLoop() {

        // i = 0;j = 0;
        // while ( i < arg0 ) { j = j + i;i = i + 1;}
        // return j;

        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal local = b.createLocal();
            b.beginTryCatch(local);

            b.beginIfThen();
            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(0L);
            b.endLessThanOperation();

            b.emitThrowOperation();

            b.endIfThen();

            b.beginReturn();
            b.emitLoadConstant(1L);
            b.endReturn();

            b.endTryCatch();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call(-1L));
        assertEquals(0L, root.call(1L));
    }

    @Test
    public void testVariableBoxingElim() {
        RootCallTarget root = parse(b -> {
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

    private static void testOrdering(boolean expectException, RootCallTarget root, Long... order) {
        List<Object> result = new ArrayList<>();

        try {
            root.call(result);
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

    @Test
    public void testFinallyTryBasic() {

        // try { 1;} finally { 2;}
        // expected 1, 2

        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry();
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitLoadConstant(2L);
                b.endAppenderOperation();

                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitLoadConstant(1L);
                b.endAppenderOperation();
            b.endFinallyTry();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();


            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L);
    }

    @Test
    public void testFinallyTryException() {

        // try { 1;throw;2;} finally { 3;}
        // expected: 1, 3

        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry();
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitLoadConstant(3L);
                b.endAppenderOperation();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.emitThrowOperation();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();
                b.endBlock();
            b.endFinallyTry();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();


            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L);
    }

    @Test
    public void testFinallyTryReturn() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);
            b.beginFinallyTry();
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitLoadConstant(1L);
                b.endAppenderOperation();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitLoadConstant(0L);
                    b.endReturn();
                b.endBlock();
            b.endFinallyTry();

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(3L);
            b.endAppenderOperation();


            b.endRoot();
        });

        testOrdering(false, root, 2L, 1L);
    }

    @Test
    public void testFinallyTryBranchOut() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // try { 1;goto lbl;2;} finally { 3;} 4;lbl: 5;
            // expected: 1, 3, 5

            OperationLabel lbl = b.createLabel();

            b.beginFinallyTry();
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitLoadConstant(3L);
                b.endAppenderOperation();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.emitBranch(lbl);

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();
                b.endBlock();
            b.endFinallyTry();

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(4L);
            b.endAppenderOperation();

            b.emitLabel(lbl);

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(5L);
            b.endAppenderOperation();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();


            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryCancel() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // try { 1;return;} finally { 2;goto lbl;} 3;lbl: 4;
            // expected: 1, 2, 4

            OperationLabel lbl = b.createLabel();

            b.beginFinallyTry();
                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();

                    b.emitBranch(lbl);
                b.endBlock();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitLoadConstant(0L);
                    b.endReturn();
                b.endBlock();
            b.endFinallyTry();

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(3L);
            b.endAppenderOperation();

            b.emitLabel(lbl);

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitLoadConstant(4L);
            b.endAppenderOperation();

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();


            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L, 4L);
    }

    @Test
    public void testFinallyTryInnerCf() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // try { 1;return;2 } finally { 3;goto lbl;4;lbl: 5;}
            // expected: 1, 3, 5

            b.beginFinallyTry();
                b.beginBlock();
                    OperationLabel lbl = b.createLabel();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(3L);
                    b.endAppenderOperation();

                    b.emitBranch(lbl);

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(4L);
                    b.endAppenderOperation();

                    b.emitLabel(lbl);

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(5L);
                    b.endAppenderOperation();
                b.endBlock();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitLoadConstant(0L);
                    b.endReturn();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();
                b.endBlock();
            b.endFinallyTry();


            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNestedTry() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // try { try { 1;return;2;} finally { 3;} } finally { 4;}
            // expected: 1, 3, 4

            b.beginFinallyTry();
                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(4L);
                    b.endAppenderOperation();
                b.endBlock();

                b.beginFinallyTry();
                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(3L);
                        b.endAppenderOperation();
                    b.endBlock();

                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(1L);
                        b.endAppenderOperation();

                        b.beginReturn();
                        b.emitLoadConstant(0L);
                        b.endReturn();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(2L);
                        b.endAppenderOperation();
                    b.endBlock();
                b.endFinallyTry();
            b.endFinallyTry();


            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 4L);
    }

    @Test
    public void testFinallyTryNestedFinally() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // try { 1;return;2;} finally { try { 3;return;4;} finally { 5;} }
            // expected: 1, 3, 5

            b.beginFinallyTry();
                b.beginFinallyTry();
                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(5L);
                        b.endAppenderOperation();
                    b.endBlock();

                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(3L);
                        b.endAppenderOperation();

                        b.beginReturn();
                        b.emitLoadConstant(0L);
                        b.endReturn();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(4L);
                        b.endAppenderOperation();
                    b.endBlock();
                b.endFinallyTry();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitLoadConstant(0L);
                    b.endReturn();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();
                b.endBlock();
            b.endFinallyTry();


            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNestedTryThrow() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // try { try { 1;throw;2;} finally { 3;} } finally { 4;}
            // expected: 1, 3, 4

            b.beginFinallyTry();
                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(4L);
                    b.endAppenderOperation();
                b.endBlock();

                b.beginFinallyTry();
                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(3L);
                        b.endAppenderOperation();
                    b.endBlock();

                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(1L);
                        b.endAppenderOperation();

                        b.emitThrowOperation();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(2L);
                        b.endAppenderOperation();
                    b.endBlock();
                b.endFinallyTry();
            b.endFinallyTry();


            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L, 4L);
    }

    @Test
    public void testFinallyTryNestedFinallyThrow() {
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // try { 1;throw;2;} finally { try { 3;throw;4;} finally { 5;} }
            // expected: 1, 3, 5

            b.beginFinallyTry();
                b.beginFinallyTry();
                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(5L);
                        b.endAppenderOperation();
                    b.endBlock();

                    b.beginBlock();
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(3L);
                        b.endAppenderOperation();

                        b.emitThrowOperation();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(4L);
                        b.endAppenderOperation();
                    b.endBlock();
                b.endFinallyTry();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.emitThrowOperation();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();
                b.endBlock();
            b.endFinallyTry();


            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNoExceptReturn() {

        // try { 1;return;2;} finally noexcept { 3;}
        // expected: 1, 3

        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTryNoExcept();
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitLoadConstant(3L);
                b.endAppenderOperation();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitLoadConstant(0L);
                    b.endReturn();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();
                b.endBlock();
            b.endFinallyTryNoExcept();


            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L);
    }

    @Test
    public void testFinallyTryNoExceptException() {

        // try { 1;throw;2;} finally noexcept { 3;}
        // expected: 1

        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTryNoExcept();
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitLoadConstant(3L);
                b.endAppenderOperation();

                b.beginBlock();
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(1L);
                    b.endAppenderOperation();

                    b.emitThrowOperation();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(2L);
                    b.endAppenderOperation();
                b.endBlock();
            b.endFinallyTryNoExcept();


            b.endRoot();
        });

        testOrdering(true, root, 1L);
    }


    @Test
    public void testTeeLocal() {
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            b.beginYield();
            b.emitLoadConstant(1L);
            b.endYield();

            b.beginYield();
            b.emitLoadConstant(2L);
            b.endYield();

            b.beginReturn();
            b.emitLoadConstant(3L);
            b.endReturn();

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
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal loc = b.createLocal();

            // loc = 0
            // yield loc
            // loc = loc + 1
            // yield loc
            // loc = loc + 1
            // return loc

            b.beginStoreLocal(loc);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadLocal(loc);
            b.endYield();

            b.beginStoreLocal(loc);
            b.beginAddOperation();
            b.emitLoadLocal(loc);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginYield();
            b.emitLoadLocal(loc);
            b.endYield();

            b.beginStoreLocal(loc);
            b.beginAddOperation();
            b.emitLoadLocal(loc);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginReturn();
            b.emitLoadLocal(loc);
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
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

            // return (yield 1) + (yield 2)
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
        RootCallTarget root = parse(b -> {
            b.beginRoot(LANGUAGE);

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

                        b.beginReturn();
                        b.emitLoadConstant(2L);
                        b.endReturn();

                        b.beginReturn();
                        b.emitLoadConstant(3L);
                        b.endReturn();

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
    public void testExampleNestedFunctions() {
        RootCallTarget root = parse(b -> {
            // this simulates following in python:
            // return (lambda: 1)()
            b.beginRoot(LANGUAGE);

            b.beginReturn();

            b.beginInvoke();

                b.beginRoot(LANGUAGE);

                b.beginReturn();
                b.emitLoadConstant(1L);
                b.endReturn();

                TestOperations innerRoot = b.endRoot();

            b.emitLoadConstant(innerRoot);
            b.endInvoke();

            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }

    @Test
    public void testLocalsNonlocalRead() {
        // todo: this test fails when boxing elimination is enabled
        // locals accessed non-locally must have boxing elimination disabled
        // since non-local reads do not do boxing elimination

        // this can be done automatically, or by
        // having `createLocal(boolean accessedFromClosure)` or similar
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
            // x = 1
            // (lambda: x = 2)()
            // return x
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
        RootCallTarget root = parse(b -> {
            // goto lbl;
            // return 0;
            // lbl: return 1;
            b.beginRoot(LANGUAGE);

            OperationLabel lbl = b.createLabel();

            b.emitBranch(lbl);

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.emitLabel(lbl);

            b.beginReturn();
            b.emitLoadConstant(1L);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(1L, root.call());
    }


    @Test
    public void testBranchBackwards() {
        RootCallTarget root = parse(b -> {
            // x = 0
            // lbl:
            // if (5 < x) return x;
            // x = x + 1;
            // goto lbl;
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

        assertEquals(6L, root.call());
    }

    @Test
    public void testBranchOutwards() {
        RootCallTarget root = parse(b -> {
            // return 1 + { goto lbl; 2 }
            // lbl:
            // return 0;
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

            b.beginReturn();
            b.emitLoadConstant(0L);
            b.endReturn();

            b.endRoot();
        });

        assertEquals(0L, root.call());
    }

    @Test
    public void testBranchInwards() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("OperationLabel must be emitted inside the same operation it was created in.");
        parse(b -> {
            // goto lbl;
            // return 1 + { lbl: 2 }
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
    public void testVariadicZeroVarargs()  {
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
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
        thrown.expectMessage("Operation VeryComplexOperation expected at least 1 children, but 0 provided. This is probably a bug in the parser.");

        parse(b -> {
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

        parse(b -> {
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

        parse(b -> {
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

        parse(b -> {
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
        TestOperations node = parseNodeWithSource(b -> {
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

        assertEquals(node.getSourceSectionAtBci(0).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(0).getCharIndex(), 7);
        assertEquals(node.getSourceSectionAtBci(0).getCharLength(), 1);

        assertEquals(node.getSourceSectionAtBci(1).getSource(), source);
        assertEquals(node.getSourceSectionAtBci(1).getCharIndex(), 0);
        assertEquals(node.getSourceSectionAtBci(1).getCharLength(), 8);
    }

    @Test
    public void testSourceNoSourceSet() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.");
        parseNodeWithSource(b -> {
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
        TestOperations root = parseNodeWithSource(b -> {
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
            if (expected[i] == null) {
                assertEquals("Mismatch at bci " + i, root.getSourceSectionAtBci(i), null);
            } else {
                assertNotNull("Mismatch at bci " + i, root.getSourceSectionAtBci(i));
                assertEquals("Mismatch at bci " + i, root.getSourceSectionAtBci(i).getSource(), sources[expected[i][0]]);
                assertEquals("Mismatch at bci " + i, root.getSourceSectionAtBci(i).getCharIndex(), expected[i][1]);
                assertEquals("Mismatch at bci " + i, root.getSourceSectionAtBci(i).getCharLength(), expected[i][2]);
            }
        }
    }

    @Test
    public void testShortCircuitingAllPass() {
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
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
        RootCallTarget root = parse(b -> {
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
        // todo: this fails since there is no check, since sc is considered as taking 0 (only variadic) args
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation ScAnd expected at least 1 children, but 0 provided. This is probably a bug in the parser.");
        parse(b -> {
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
        // todo: this message should be improved, since all variadic children are treated as the same position (e.g. message should be "at position 1".
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage("Operation ScAnd expected a value-producing child at position 0, but a void one was provided. This likely indicates a bug in the parser.");
        parse(b -> {
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

    private static void assertInstructionEquals(Instruction instr, int index, String name) {
        assertEquals(index, instr.getIndex());
        assertEquals(name, instr.getName());
    }

    @Test
    public void testIntrospectionData() {
        TestOperations node = parseNode(b -> {
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
        assertInstructionEquals(data.getInstructions().get(1), 1, "load.argument");
        assertInstructionEquals(data.getInstructions().get(2), 2, "c.AddOperation");
        assertInstructionEquals(data.getInstructions().get(3), 3, "return");
        // todo: with DCE, this pop will go away (since return is considered as returning a value)
        assertInstructionEquals(data.getInstructions().get(4), 4, "pop");
    }

    @Test
    public void testDecisionQuicken() {
        TestOperations node = parseNode(b -> {
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
    public void testDecisionSuperInstruction() {
        TestOperations node = parseNode(b -> {
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
