/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.RootNode;

public class TryFinallyTest extends AbstractBasicInterpreterTest {
    // @formatter:off

    public TryFinallyTest(TestRun run) {
        super(run);
    }

    private static void testOrdering(boolean expectException, RootCallTarget root, Long... order) {
        testOrderingWithArguments(expectException, root, null, order);
    }

    private static void testOrderingWithArguments(boolean expectException, RootCallTarget root, Object[] args, Long... order) {
        List<Object> result = new ArrayList<>();

        Object[] allArgs;
        if (args == null) {
            allArgs = new Object[]{result};
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

    @Test
    public void testTryFinallyBasic() {
        // try {
        //   arg0.append(1);
        // } finally {
        //   arg0.append(2);
        // }

        RootCallTarget root = parse("finallyTryBasic", b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitAppend(b, 2));
            emitAppend(b, 1);
            b.endTryFinally();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L);
    }

    @Test
    public void testTryFinallyException() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        // }

        RootCallTarget root = parse("finallyTryException", b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L);
    }

    @Test
    public void testTryFinallyReturn() {
        // try {
        //   arg0.append(2);
        //   return 0;
        // } finally {
        //   arg0.append(1);
        // }
        // arg0.append(3);

        RootCallTarget root = parse("finallyTryReturn", b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitAppend(b, 1));
                b.beginBlock();
                    emitAppend(b, 2);

                    emitReturn(b, 0);
                b.endBlock();
            b.endTryFinally();

            emitAppend(b, 3);

            b.endRoot();
        });

        testOrdering(false, root, 2L, 1L);
    }

    @Test
    public void testTryFinallyBranchOut() {
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
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryFinally(() -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    b.emitBranch(lbl);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();

            emitAppend(b, 4);
            b.emitLabel(lbl);
            emitAppend(b, 5);
            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testTryFinallyBranchForwardOutOfHandler() {
        // try {
        //   arg0.append(1);
        // } finally {
        //   arg0.append(2);
        //   goto lbl;
        // }
        // arg0.append(3);
        // lbl:
        // arg0.append(4);

        BasicInterpreter root = parseNode("finallyTryBranchForwardOutOfHandler", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 2);
                    b.emitBranch(lbl);
                b.endBlock();
            });

                b.beginBlock();
                    emitAppend(b, 1);
                b.endBlock();
            b.endTryFinally();

            emitAppend(b, 3);
            b.emitLabel(lbl);
            emitAppend(b, 4);
            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root.getCallTarget(), 1L, 2L, 4L);
    }

    @Test
    public void testTryFinallyBranchForwardOutOfHandlerUnbalanced() {
        /**
         * This test is the same as the previous, but because of the "return 0",
         * the sp at the branch does not match the sp at the label.
         */

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

        BasicInterpreter root = parseNode("finallyTryBranchForwardOutOfHandler", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 2);
                    b.emitBranch(lbl);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                b.endBlock();
            b.endTryFinally();

            emitAppend(b, 3);
            b.emitLabel(lbl);
            emitAppend(b, 4);
            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(false, root.getCallTarget(), 1L, 2L, 4L);
    }

    @Test
    public void testTryFinallyBranchBackwardOutOfHandler() {
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


        assertThrowsWithMessage("Backward branches are unsupported. Use a While operation to model backward control flow.", IllegalStateException.class, () -> {
            parse("finallyTryBranchBackwardOutOfHandler", b -> {
                b.beginRoot();
                BytecodeLabel lbl = b.createLabel();
                BytecodeLocal local = b.createLocal();

                b.beginTeeLocal(local);
                b.emitLoadConstant(0L);
                b.endTeeLocal();

                emitAppend(b, 1);

                b.emitLabel(lbl);
                b.beginIfThen();
                    b.beginLess();
                    b.emitLoadConstant(0L);
                    b.emitLoadLocal(local);
                    b.endLess();

                    b.beginBlock();
                        emitAppend(b, 4);
                        emitReturn(b, 0);
                    b.endBlock();
                b.endIfThen();

                b.beginTryFinally(() -> {
                    b.beginBlock();
                        emitAppend(b, 3);
                        b.emitBranch(lbl);
                    b.endBlock();
                });
                    b.beginBlock();
                        b.beginTeeLocal(local);
                        b.emitLoadConstant(1L);
                        b.endTeeLocal();
                        emitAppend(b, 2);
                        emitReturn(b, 0);
                    b.endBlock();
                b.endTryFinally();

                emitAppend(b, 5);

                b.endRoot();
            });
        });
    }

    /*
     * The following few test cases have local control flow inside finally handlers.
     * Since finally handlers are relocated, these local branches should be properly
     * adjusted by the builder.
     */

    @Test
    public void testTryFinallyBranchWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        //   if (arg2) goto outerLbl;
        //   arg0.append(3);
        //   if (arg3) throw 123
        //   arg0.append(4);
        // } finally {
        //   arg0.append(5);
        //   goto lbl;
        //   arg0.append(6);
        //   lbl:
        //   arg0.append(7);
        // }
        // outerLbl:
        // arg0.append(8);

        BasicInterpreter root = parseNode("finallyTryBranchWithinHandler", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel outerLbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    BytecodeLabel lbl = b.createLabel();
                    emitAppend(b, 5);
                    b.emitBranch(lbl);
                    emitAppend(b, 6);
                    b.emitLabel(lbl);
                    emitAppend(b, 7);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                    emitBranchIf(b, 2, outerLbl);
                    emitAppend(b, 3);
                    emitThrowIf(b, 3, 123);
                    emitAppend(b, 4);
                b.endBlock();
            b.endTryFinally();
            b.emitLabel(outerLbl);
            emitAppend(b, 8);
            b.endBlock();
            b.endRoot();
        });

        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false}, 1L, 5L, 7L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false}, 1L, 2L, 5L, 7L, 8L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 7L);
    }

    @Test
    public void testTryFinallyIfThenWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        //   if (arg2) goto outerLbl;
        //   arg0.append(3);
        //   if (arg3) throw 123
        //   arg0.append(4);
        // } finally {
        //   arg0.append(5);
        //   if (arg4) {
        //     arg0.append(6);
        //   }
        //   arg0.append(7);
        // }
        // arg0.append(8);

        BasicInterpreter root = parseNode("finallyTryIfThenWithinHandler", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel outerLbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 5);
                    b.beginIfThen();
                        b.emitLoadArgument(4);
                        emitAppend(b, 6);
                    b.endIfThen();
                    emitAppend(b, 7);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                    emitBranchIf(b, 2, outerLbl);
                    emitAppend(b, 3);
                    emitThrowIf(b, 3, 123);
                    emitAppend(b, 4);
                b.endBlock();
            b.endTryFinally();

            b.emitLabel(outerLbl);
            emitAppend(b, 8);
            b.endBlock();
            b.endRoot();
        });
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, false}, 1L, 5L, 7L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, true}, 1L, 5L, 6L, 7L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, false}, 1L, 2L, 5L, 7L, 8L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, true}, 1L, 2L, 5L, 6L, 7L, 8L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, false}, 1L, 2L, 3L, 5L, 7L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, true}, 1L, 2L, 3L, 5L, 6L, 7L);
    }

    @Test
    public void testTryFinallyIfThenElseWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        //   if (arg2) goto outerLbl;
        //   arg0.append(3);
        //   if (arg3) throw 123
        //   arg0.append(4);
        // } finally {
        //   arg0.append(5);
        //   if (arg4) {
        //     arg0.append(6);
        //   } else {
        //     arg0.append(7);
        //   }
        //   arg0.append(8);
        // }
        // outerLbl:
        // arg0.append(9);

        BasicInterpreter root = parseNode("finallyTryIfThenElseWithinHandler", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel outerLbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 5);
                    b.beginIfThenElse();
                        b.emitLoadArgument(4);
                        emitAppend(b, 6);
                        emitAppend(b, 7);
                    b.endIfThenElse();
                    emitAppend(b, 8);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                    emitBranchIf(b, 2, outerLbl);
                    emitAppend(b, 3);
                    emitThrowIf(b, 3, 123);
                    emitAppend(b, 4);
                b.endBlock();
            b.endTryFinally();

            b.emitLabel(outerLbl);
            emitAppend(b, 9);
            b.endBlock();
            b.endRoot();
        });

        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L, 9L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, false}, 1L, 5L, 7L, 8L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, true}, 1L, 5L, 6L, 8L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, false}, 1L, 2L, 5L, 7L, 8L, 9L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, true}, 1L, 2L, 5L, 6L, 8L, 9L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, false}, 1L, 2L, 3L, 5L, 7L, 8L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, true}, 1L, 2L, 3L, 5L, 6L, 8L);
    }

    @Test
    public void testTryFinallyConditionalWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        //   if (arg2) goto outerLbl;
        //   arg0.append(3);
        //   if (arg3) throw 123
        //   arg0.append(4);
        // } finally {
        //   arg0.append(5);
        //   (arg4) ? { arg0.append(6); 0 } : { arg0.append(7); 0 }
        //   (arg5) ? { arg0.append(8); 0 } : { arg0.append(9); 0 }
        //   arg0.append(10);
        // }
        // arg0.append(11);

        BasicInterpreter root = parseNode("finallyTryConditionalWithinHandler", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel outerLbl = b.createLabel();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 5);
                    b.beginConditional();
                        b.emitLoadArgument(4);
                        b.beginBlock();
                            emitAppend(b, 6);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                        b.beginBlock();
                            emitAppend(b, 7);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                    b.endConditional();

                    b.beginConditional();
                        b.emitLoadArgument(5);
                        b.beginBlock();
                            emitAppend(b, 8);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                        b.beginBlock();
                            emitAppend(b, 9);
                            b.emitLoadConstant(0L);
                        b.endBlock();
                    b.endConditional();

                    emitAppend(b, 10);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                    emitBranchIf(b, 2, outerLbl);
                    emitAppend(b, 3);
                    emitThrowIf(b, 3, 123);
                    emitAppend(b, 4);
                b.endBlock();
            b.endTryFinally();

            b.emitLabel(outerLbl);
            emitAppend(b, 11);
            b.endBlock();
            b.endRoot();
        });

        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 7L, 8L, 10L, 11L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false, true, false}, 1L, 2L, 3L, 4L, 5L, 6L, 9L, 10L, 11L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, false, true}, 1L, 5L, 7L, 8L, 10L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false, true, false}, 1L, 5L, 6L, 9L, 10L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, false, true}, 1L, 2L, 5L, 7L, 8L, 10L, 11L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L, 11L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, false, true}, 1L, 2L, 3L, 5L, 7L, 8L, 10L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true, true, false}, 1L, 2L, 3L, 5L, 6L, 9L, 10L);
    }

    @Test
    public void testTryFinallyLoopWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
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
            b.beginRoot();

            BytecodeLocal local = b.createLocal();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 3);

                    b.beginTeeLocal(local);
                    b.emitLoadConstant(4L);
                    b.endTeeLocal();

                    b.beginWhile();
                        b.beginLess();
                            b.emitLoadLocal(local);
                            b.emitLoadConstant(7L);
                        b.endLess();

                        b.beginBlock();
                            b.beginAppenderOperation();
                            b.emitLoadArgument(0);
                            b.emitLoadLocal(local);
                            b.endAppenderOperation();

                            b.beginTeeLocal(local);
                                b.beginAdd();
                                    b.emitLoadLocal(local);
                                    b.emitLoadConstant(1L);
                                b.endAdd();
                            b.endTeeLocal();
                        b.endBlock();
                    b.endWhile();

                    emitAppend(b, 8);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();

            emitAppend(b, 9);

            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 5L, 6L, 8L);
        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 5L, 6L, 8L, 9L);
    }


    @Test
    public void testTryFinallyShortCircuitOpWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg0) return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   { arg0.append(4); true } && { arg0.append(5); false } && { arg0.append(6); true }
        //   { arg0.append(7); false } || { arg0.append(8); true } || { arg0.append(9); false }
        //   arg0.append(10);
        // }
        // arg0.append(11);

        RootCallTarget root = parse("finallyTryShortCircuitOpWithinHandler", b -> {
            b.beginRoot();

            b.beginTryFinally(() -> {
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
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();

            emitAppend(b, 11);

            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 5L, 7L, 8L, 10L);
        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 5L, 7L, 8L, 10L, 11L);
    }

    @Test
    public void testTryFinallyNonThrowingTryCatchWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
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
            b.beginRoot();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 3);
                    b.beginTryCatch();
                        emitAppend(b, 4);
                        emitAppend(b, 5);
                    b.endTryCatch();
                    emitAppend(b, 6);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();

            emitAppend(b, 7);
            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 6L);
        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 6L, 7L);
    }

    @Test
    public void testTryFinallyThrowingTryCatchWithinHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
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
            b.beginRoot();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 3);
                    b.beginTryCatch();
                        b.beginBlock();
                            emitAppend(b, 4);
                            emitThrow(b, 0);
                            emitAppend(b, 5);
                        b.endBlock();

                        emitAppend(b, 6);
                    b.endTryCatch();

                    emitAppend(b, 7);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();
            emitAppend(b, 8);
            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 3L, 4L, 6L, 7L);
        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 4L, 6L, 7L, 8L);
    }

    @Test
    public void testTryFinallyBranchWithinHandlerNoLabel() {
        // try {
        //   return 0;
        // } finally {
        //   goto lbl;
        //   return 0;
        // }

        assertThrowsWithMessage("Operation Block ended without emitting one or more declared labels.", IllegalStateException.class, () -> {
            parse("finallyTryBranchWithinHandlerNoLabel", b -> {
                b.beginRoot();

                b.beginTryFinally(() -> {
                    b.beginBlock();
                        b.emitBranch(b.createLabel());
                        emitReturn(b, 0);
                    b.endBlock();
                });
                    b.beginBlock();
                        emitReturn(b, 0);
                    b.endBlock();
                b.endTryFinally();
                b.endRoot();
            });
        });

    }

    @Test
    public void testTryFinallyBranchIntoTry() {
        // try {
        //   return 0;
        //   lbl:
        //   return 0;
        // } finally {
        //   goto lbl;
        //   return 0;
        // }

        // This error has nothing to do with try-finally, but it's still useful to ensure this doesn't work.
        assertThrowsWithMessage("BytecodeLabel must be emitted inside the same operation it was created in.", IllegalStateException.class, () -> {
            parse("finallyTryBranchIntoTry", b -> {
                b.beginRoot();
                BytecodeLabel lbl = b.createLabel();
                b.beginTryFinally(() -> {
                    b.beginBlock();
                        b.emitBranch(lbl);
                        emitReturn(b, 0);
                    b.endBlock();
                });
                    b.beginBlock();
                        emitReturn(b, 0);
                        b.emitLabel(lbl);
                        emitReturn(b, 0);
                    b.endBlock();
                b.endTryFinally();

                b.endRoot();
            });
        });
    }

    @Test
    public void testTryFinallyBranchIntoFinally() {
        // try {
        //   goto lbl;
        //   return 0;
        // } finally {
        //   lbl:
        //   return 0;
        // }

        // This error has nothing to do with try-finally, but it's still useful to ensure this doesn't work.
        assertThrowsWithMessage("BytecodeLabel must be emitted inside the same operation it was created in.", IllegalStateException.class, () -> {
            parse("finallyTryBranchIntoFinally", b -> {
                b.beginRoot();
                BytecodeLabel lbl = b.createLabel();
                b.beginTryFinally(() -> {
                    b.beginBlock();
                        b.emitLabel(lbl);
                        emitReturn(b, 0);
                    b.endBlock();
                });
                    b.beginBlock();
                        b.emitBranch(lbl);
                        emitReturn(b, 0);
                    b.endBlock();
                b.endTryFinally();

                b.endRoot();
            });
        });
    }

    @Test
    public void testTryFinallyBranchIntoOuterFinally() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        //   if (arg2) goto outerLbl;
        //   arg0.append(3);
        //   if (arg3) throw 123;
        //   arg0.append(4);
        // } finally {
        //   try {
        //     arg0.append(5);
        //   } finally {
        //     arg0.append(6);
        //     goto lbl;
        //     arg0.append(7);
        //   }
        //   arg0.append(8);
        //   lbl:
        //   arg0.append(9);
        // }
        // outerLbl:
        // arg0.append(10);
        // return 0;
        BasicInterpreter root = parseNode("finallyTryBranchIntoOuterFinally", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel outerLbl = b.createLabel();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    BytecodeLabel lbl = b.createLabel();
                    b.beginTryFinally(() -> {
                        b.beginBlock();
                            emitAppend(b, 6);
                            b.emitBranch(lbl);
                            emitAppend(b, 7);
                        b.endBlock();
                    });
                        emitAppend(b, 5);
                    b.endTryFinally();

                    emitAppend(b, 8);
                    b.emitLabel(lbl);
                    emitAppend(b, 9);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                    emitBranchIf(b, 2, outerLbl);
                    emitAppend(b, 3);
                    emitThrowIf(b, 3, 123);
                    emitAppend(b, 4);
                b.endBlock();
            b.endTryFinally();

            b.emitLabel(outerLbl);
            emitAppend(b, 10);
            b.endBlock();
            b.endRoot();
        });

        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 9L, 10L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true, false, false}, 1L, 5L, 6L, 9L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L);
        testOrderingWithArguments(true, root.getCallTarget(), new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L, 9L);
    }


    @Test
    public void testTryFinallyBranchWhileInParentHandler() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        // } finally {
        //   arg0.append(3);
        //   try {
        //     arg0.append(4);
        //     goto lbl;
        //     arg0.append(5);
        //     lbl:
        //     arg0.append(6);
        //   } finally {
        //     arg0.append(7);
        //   }
        //   arg0.append(8);
        // }
        // arg0.append(9);

        BasicInterpreter root = parseNode("finallyTryBranchWhileInParentHandler", b -> {
            b.beginRoot();
            b.beginBlock();
                b.beginTryFinally(() -> {
                    b.beginBlock();
                        emitAppend(b, 3);

                        b.beginTryFinally(() -> emitAppend(b, 7));
                            b.beginBlock();
                                BytecodeLabel lbl = b.createLabel();
                                emitAppend(b, 4);
                                b.emitBranch(lbl);
                                emitAppend(b, 5);
                                b.emitLabel(lbl);
                                emitAppend(b, 6);
                            b.endBlock();
                        b.endTryFinally();

                        emitAppend(b, 8);
                    b.endBlock();
                });
                    b.beginBlock();
                        emitAppend(b, 1);
                        emitReturnIf(b, 1, 0);
                        emitAppend(b, 2);
                    b.endBlock();
                b.endTryFinally();

                emitAppend(b, 9);
            b.endBlock();
            b.endRoot();
        });

        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {false}, 1L, 2L, 3L, 4L, 6L, 7L, 8L, 9L);
        testOrderingWithArguments(false, root.getCallTarget(), new Object[] {true}, 1L, 3L, 4L, 6L, 7L, 8L);
    }

    @Test
    public void testTryFinallyNestedFinally() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        // } finally {
        //   try {
        //     arg0.append(3);
        //     if (arg2) return 0;
        //     arg0.append(4);
        //   } finally {
        //     arg0.append(5);
        //   }
        // }

        RootCallTarget root = parse("finallyTryNestedFinally", b -> {
            b.beginRoot();

            b.beginTryFinally(() -> {
                b.beginTryFinally(() -> emitAppend(b, 5));
                    b.beginBlock();
                        emitAppend(b, 3);
                        emitReturnIf(b, 2, 0);
                        emitAppend(b, 4);
                    b.endBlock();
                b.endTryFinally();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();

            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false, false}, 1L, 2L, 3L, 4L, 5L);
        testOrderingWithArguments(false, root, new Object[] {true, false}, 1L, 3L, 4L, 5L);
        testOrderingWithArguments(false, root, new Object[] {false, true}, 1L, 2L, 3L, 5L);
        testOrderingWithArguments(false, root, new Object[] {true, true}, 1L, 3L, 5L);
    }

    @Test
    public void testTryFinallyNestedInTry() {
        // try {
        //   try {
        //     arg0.append(1);
        //     if (arg1) return 0;
        //     arg0.append(2);
        //     if (arg2) goto outerLbl;
        //     arg0.append(3);
        //     if (arg3) throw 123
        //     arg0.append(4);
        //   } finally {
        //     arg0.append(5);
        //   }
        // } finally {
        //   arg0.append(6);
        // }
        // outerLbl:
        // arg0.append(7);

        RootCallTarget root = parse("finallyTryNestedInTry", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel outerLbl = b.createLabel();
            b.beginTryFinally(() -> emitAppend(b, 6));
                b.beginTryFinally(() -> emitAppend(b, 5));
                    b.beginBlock();
                        emitAppend(b, 1);
                        emitReturnIf(b, 1, 0);
                        emitAppend(b, 2);
                        emitBranchIf(b, 2, outerLbl);
                        emitAppend(b, 3);
                        emitThrowIf(b, 3, 123);
                        emitAppend(b, 4);
                    b.endBlock();
                b.endTryFinally();
            b.endTryFinally();
            b.emitLabel(outerLbl);
            emitAppend(b, 7);
            b.endBlock();
            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        testOrderingWithArguments(false, root, new Object[] {true, false, false}, 1L, 5L, 6L);
        testOrderingWithArguments(false, root, new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 7L);
        testOrderingWithArguments(true, root, new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L);
    }

    @Test
    public void testTryFinallyNestedInFinally() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0;
        //   arg0.append(2);
        //   if (arg2) goto outerLbl;
        //   arg0.append(3);
        //   if (arg3) throw 123
        //   arg0.append(4);
        // } finally {
        //   try {
        //     arg0.append(5);
        //     if (arg1) return 0;
        //     arg0.append(6);
        //     if (arg2) goto outerLbl;
        //     arg0.append(7);
        //     if (arg3) throw 123
        //     arg0.append(8);
        //   } finally {
        //     arg0.append(9);
        //   }
        // }
        // outerLbl:
        // arg0.append(10);

        RootCallTarget root = parse("finallyTryNestedInFinally", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel outerLbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginTryFinally(() -> emitAppend(b, 9));
                    b.beginBlock();
                        emitAppend(b, 5);
                        emitReturnIf(b, 1, 0);
                        emitAppend(b, 6);
                        emitBranchIf(b, 2, outerLbl);
                        emitAppend(b, 7);
                        emitThrowIf(b, 3, 123);
                        emitAppend(b, 8);
                    b.endBlock();
                b.endTryFinally();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                    emitBranchIf(b, 2, outerLbl);
                    emitAppend(b, 3);
                    emitThrowIf(b, 3, 123);
                    emitAppend(b, 4);
                b.endBlock();
            b.endTryFinally();

            b.emitLabel(outerLbl);
            emitAppend(b, 10);
            b.endBlock();
            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        testOrderingWithArguments(false, root, new Object[] {true, false, false}, 1L, 5L, 9L);
        testOrderingWithArguments(false, root, new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L);
        testOrderingWithArguments(true, root, new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L, 7L, 9L);
    }

    @Test
    public void testTryFinallyNestedInFinallyWithinAnotherTryFinally() {
        // Same as the previous test, but put it all within another TryFinally.
        // The unwinding step should skip over some open operations but include the outermost TryFinally.

        // try {
        //   try {
        //     arg0.append(1);
        //     if (arg1) return 0;
        //     arg0.append(2);
        //     if (arg2) goto outerLbl;
        //     arg0.append(3);
        //     if (arg3) throw 123
        //     arg0.append(4);
        //   } finally {
        //     try {
        //       arg0.append(5);
        //       if (arg1) return 0;
        //       arg0.append(6);
        //       if (arg2) goto outerLbl;
        //       arg0.append(7);
        //       if (arg3) throw 123
        //       arg0.append(8);
        //     } finally {
        //       arg0.append(9);
        //     }
        //   }
        //   outerLbl:
        //   arg0.append(10);
        // } finally {
        //   arg0.append(11);
        // }

        RootCallTarget root = parse("finallyTryNestedInFinally", b -> {
            b.beginRoot();
            b.beginTryFinally(() -> emitAppend(b, 11));
                b.beginBlock();
                BytecodeLabel outerLbl = b.createLabel();

                b.beginTryFinally(() -> {
                    b.beginTryFinally(() -> emitAppend(b, 9));
                        b.beginBlock();
                            emitAppend(b, 5);
                            emitReturnIf(b, 1, 0);
                            emitAppend(b, 6);
                            emitBranchIf(b, 2, outerLbl);
                            emitAppend(b, 7);
                            emitThrowIf(b, 3, 123);
                            emitAppend(b, 8);
                        b.endBlock();
                    b.endTryFinally();
                });
                    b.beginBlock();
                        emitAppend(b, 1);
                        emitReturnIf(b, 1, 0);
                        emitAppend(b, 2);
                        emitBranchIf(b, 2, outerLbl);
                        emitAppend(b, 3);
                        emitThrowIf(b, 3, 123);
                        emitAppend(b, 4);
                    b.endBlock();
                b.endTryFinally();

                b.emitLabel(outerLbl);
                emitAppend(b, 10);
                b.endBlock();
            b.endTryFinally();
            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false, false, false}, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
        testOrderingWithArguments(false, root, new Object[] {true, false, false}, 1L, 5L, 9L, 11L);
        testOrderingWithArguments(false, root, new Object[] {false, true, false}, 1L, 2L, 5L, 6L, 9L, 10L, 11L);
        testOrderingWithArguments(true, root, new Object[] {false, false, true}, 1L, 2L, 3L, 5L, 6L, 7L, 9L, 11L);
    }

    @Test
    public void testTryFinallyNestedTryCatchWithEarlyReturn() {
        /**
         * The try-catch handler should take precedence over the finally handler.
         */

        // try {
        //   try {
        //     arg0.append(1);
        //     throw 0;
        //     arg0.append(2);
        //   } catch ex {
        //     arg0.append(3);
        //     return 0;
        //     arg0.append(4);
        //   }
        // } finally {
        //   arg0.append(5);
        // }

        BasicInterpreter root = parseNode("finallyTryNestedTryThrow", b -> {
            b.beginRoot();

            b.beginTryFinally(() -> emitAppend(b, 5));
                b.beginTryCatch();
                    b.beginBlock();
                        emitAppend(b, 1);
                        emitThrow(b, 0);
                        emitAppend(b, 2);
                    b.endBlock();

                    b.beginBlock();
                        emitAppend(b, 3);
                        emitReturn(b, 0);
                        emitAppend(b, 4);
                    b.endBlock();
                b.endTryCatch();
            b.endTryFinally();

            b.endRoot();
        });

        testOrdering(false, root.getCallTarget(), 1L, 3L, 5L);
    }

    @Test
    public void testTryFinallyHandlerNotGuarded() {
        /**
         * A finally handler should not be guarded by itself. If it throws, the throw should go uncaught.
         */
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0
        //   arg0.append(2);
        //   if (arg2) goto lbl
        //   arg0.append(3);
        // } finally {
        //   arg0.append(4);
        //   throw MyException(123);
        // }
        // lbl:

        RootCallTarget root = parse("finallyTryHandlerNotGuarded", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 4L);
                    emitThrow(b, 123);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    b.beginIfThen();
                        b.emitLoadArgument(1);
                        b.beginReturn();
                            b.emitLoadConstant(0L);
                        b.endReturn();
                    b.endIfThen();
                    emitAppend(b, 2);
                    b.beginIfThen();
                        b.emitLoadArgument(2);
                        b.emitBranch(lbl);
                    b.endIfThen();
                    emitAppend(b, 3);
                b.endBlock();
            b.endTryFinally();
            b.emitLabel(lbl);

            b.endRoot();
        });

        testOrderingWithArguments(true, root,  new Object[] {false, false}, 1L, 2L, 3L, 4L);
        testOrderingWithArguments(true, root,  new Object[] {true, false}, 1L, 4L);
        testOrderingWithArguments(true, root,  new Object[] {false, true}, 1L, 2L, 4L);
    }

    @Test
    public void testTryFinallyOuterHandlerNotGuarded() {
        /**
         * A finally handler should not guard an outer handler. If the outer throws, the inner should not catch it.
         */
        // try {
        //   arg0.append(1);
        //   try {
        //      if (arg1) return 0;
        //      arg0.append(2);
        //      if (arg2) goto lbl;
        //      arg0.append(3);
        //   } finally {
        //      arg0.append(4);
        //   }
        // } finally {
        //   arg0.append(5);
        //   throw MyException(123);
        // }
        // lbl:

        RootCallTarget root = parse("finallyTryOuterHandlerNotGuarded", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 5);
                    emitThrow(b, 123);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    b.beginTryFinally(() -> emitAppend(b, 4));
                        b.beginBlock();
                            b.beginIfThen();
                                b.emitLoadArgument(1);
                                b.beginReturn();
                                    b.emitLoadConstant(0L);
                                b.endReturn();
                            b.endIfThen();
                            emitAppend(b, 2);
                            b.beginIfThen();
                                b.emitLoadArgument(2);
                                b.emitBranch(lbl);
                            b.endIfThen();
                            emitAppend(b, 3);
                        b.endBlock();
                    b.endTryFinally();
                b.endBlock();
            b.endTryFinally();
            b.emitLabel(lbl);

            b.endRoot();
        });

        testOrderingWithArguments(true, root, new Object[] {false, false}, 1L, 2L, 3L, 4L, 5L);
        testOrderingWithArguments(true, root, new Object[] {true, false}, 1L, 4L, 5L);
        testOrderingWithArguments(true, root, new Object[] {false, true}, 1L, 2L, 4L, 5L);
    }

    @Test
    public void testTryFinallyOuterHandlerNotGuardedByTryCatch() {
        /**
         * The try-catch should not guard the outer finally handler.
         */
        // try {
        //   arg0.append(1);
        //   try {
        //      if (arg1) return 0;
        //      arg0.append(2);
        //      if (arg2) goto lbl;
        //      arg0.append(3);
        //   } catch ex {
        //      arg0.append(4);
        //   }
        // } finally {
        //   arg0.append(5);
        //   throw MyException(123);
        // }
        // lbl:

        RootCallTarget root = parse("finallyTryOuterHandlerNotGuardedByTryCatch", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    emitAppend(b, 5);
                    emitThrow(b, 123);
                b.endBlock();
            });
                b.beginBlock(); // begin outer try
                    emitAppend(b, 1);
                    b.beginTryCatch();
                        b.beginBlock(); // begin inner try
                            b.beginIfThen();
                                b.emitLoadArgument(1);
                                b.beginReturn();
                                    b.emitLoadConstant(0L);
                                b.endReturn();
                            b.endIfThen();
                            emitAppend(b, 2);
                            b.beginIfThen();
                                b.emitLoadArgument(2);
                                b.emitBranch(lbl);
                            b.endIfThen();
                            emitAppend(b, 3);
                        b.endBlock(); // end inner try

                        emitAppend(b, 4); // inner catch
                    b.endTryCatch();
                b.endBlock(); // end outer try

            b.endTryFinally();

            b.emitLabel(lbl);

            b.endRoot();
        });

        testOrderingWithArguments(true, root, new Object[] {false, false}, 1L, 2L, 3L, 5L);
        testOrderingWithArguments(true, root, new Object[] {true, false}, 1L, 5L);
        testOrderingWithArguments(true, root, new Object[] {false, true}, 1L, 2L, 5L);
    }

    @Test
    public void testTryCatchOtherwiseBasic() {
        // try {
        //   arg0.append(1);
        // } catch ex {
        //   arg0.append(3);
        // } otherwise {
        //   arg0.append(2);
        // }

        RootCallTarget root = parse("tryCatchOtherwiseBasic", b -> {
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> emitAppend(b, 2));
            emitAppend(b, 1);
            emitAppend(b, 3);
            b.endTryCatchOtherwise();
            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L);
    }

    @Test
    public void testTryCatchOtherwiseException() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } catch ex {
        //   arg0.append(4);
        // } otherwise {
        //   arg0.append(3);
        // }

        RootCallTarget root = parse("tryCatchOtherwiseException", b -> {
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();

                emitAppend(b, 4);
            b.endTryCatchOtherwise();
            b.endRoot();
        });

        testOrdering(false, root, 1L, 4L);
    }

    @Test
    public void testTryCatchOtherwiseReturn() {
        // try {
        //   arg0.append(1);
        //   return 0;
        // } catch ex {
        //   arg0.append(3);
        // } otherwise {
        //   arg0.append(2);
        // }
        // arg0.append(4);

        RootCallTarget root = parse("tryCatchOtherwiseReturn", b -> {
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> emitAppend(b, 2));
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                b.endBlock();

                emitAppend(b, 3);
            b.endTryCatchOtherwise();

            emitAppend(b, 4);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 2L);
    }

    @Test
    public void testTryCatchOtherwiseBindException() {
        // try {
        //   arg0.append(1);
        //   if (arg1) throw arg2
        // } catch ex {
        //   arg0.append(ex.value);
        // } otherwise {
        //   arg0.append(2);
        // }

        RootCallTarget root = parse("tryCatchOtherwiseBindBasic", b -> {
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> emitAppend(b, 2));
                b.beginBlock();
                    emitAppend(b, 1);
                    b.beginIfThen();
                        b.emitLoadArgument(1);
                        b.beginThrowOperation();
                            b.emitLoadArgument(2);
                        b.endThrowOperation();
                    b.endIfThen();
                b.endBlock();

                b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.beginReadExceptionOperation();
                    b.emitLoadException();
                    b.endReadExceptionOperation();
                b.endAppenderOperation();
            b.endTryCatchOtherwise();

            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false, 42L}, 1L, 2L);
        testOrderingWithArguments(false, root, new Object[] {true, 42L}, 1L, 42L);
        testOrderingWithArguments(false, root, new Object[] {false, 33L}, 1L, 2L);
        testOrderingWithArguments(false, root, new Object[] {true, 33L}, 1L, 33L);
    }

    @Test
    public void testTryCatchOtherwiseBranchOut() {
        // try {
        //   arg0.append(1);
        //   goto lbl;
        //   arg0.append(2);
        // } catch ex {
        //   arg0.append(4);
        // } otherwise {
        //   arg0.append(3);
        // }
        // arg0.append(5)
        // lbl:
        // arg0.append(6);

        RootCallTarget root = parse("tryCatchOtherwiseBranchOut", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryCatchOtherwise(() -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    b.emitBranch(lbl);
                    emitAppend(b, 2);
                b.endBlock();

                emitAppend(b, 4);
            b.endTryCatchOtherwise();

            emitAppend(b, 5);
            b.emitLabel(lbl);
            emitAppend(b, 6);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 6L);
    }

    @Test
    public void testTryCatchOtherwiseBranchOutOfCatch() {
        // try {
        //   arg0.append(1);
        //   if (arg1) throw 0;
        //   arg0.append(2);
        // } catch ex {
        //   arg0.append(4);
        //   goto lbl
        //   arg0.append(5);
        // } otherwise {
        //   arg0.append(3);
        // }
        // arg0.append(6)
        // lbl:
        // arg0.append(7);

        RootCallTarget root = parse("tryCatchOtherwiseBranchOutOfCatch", b -> {
            b.beginRoot();
            BytecodeLabel lbl = b.createLabel();

            b.beginTryCatchOtherwise(() -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrowIf(b, 1, 0);
                    emitAppend(b, 2);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 4);
                    b.emitBranch(lbl);
                    emitAppend(b, 5);
                b.endBlock();
            b.endTryCatchOtherwise();

            emitAppend(b, 6);
            b.emitLabel(lbl);
            emitAppend(b, 7);
            emitReturn(b, 0);

            b.endRoot();
        });

        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 2L, 3L, 6L, 7L);
        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 4L, 7L);
    }

    @Test
    public void testTryCatchOtherwiseBranchWithinHandler() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } catch ex {
        //   arg0.append(6);
        // } otherwise {
        //   arg0.append(3);
        //   goto lbl;
        //   arg0.append(4);
        //   lbl:
        //   arg0.append(5);
        // }
        // arg0.append(7);

        RootCallTarget root = parse("tryCatchOtherwiseBranchWithinHandler", b -> {
            b.beginRoot();

            b.beginTryCatchOtherwise(() -> {
                b.beginBlock();
                    BytecodeLabel lbl = b.createLabel();
                    emitAppend(b, 3);
                    b.emitBranch(lbl);
                    emitAppend(b, 4);
                    b.emitLabel(lbl);
                    emitAppend(b, 5);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();

                emitAppend(b, 6);
            b.endTryCatchOtherwise();

            emitAppend(b, 7);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testTryCatchOtherwiseBranchWithinCatchHandler() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } catch ex {
        //   arg0.append(4);
        //   goto lbl;
        //   arg0.append(5);
        //   lbl:
        //   arg0.append(6);
        // } otherwise {
        //   arg0.append(3);
        // }
        // arg0.append(7);

        RootCallTarget root = parse("tryCatchOtherwiseBranchWithinCatchHandler", b -> {
            b.beginRoot();

            b.beginTryCatchOtherwise(() -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();

                b.beginBlock();
                    BytecodeLabel lbl = b.createLabel();
                    emitAppend(b, 4);
                    b.emitBranch(lbl);
                    emitAppend(b, 5);
                    b.emitLabel(lbl);
                    emitAppend(b, 6);
                b.endBlock();
            b.endTryCatchOtherwise();

            emitAppend(b, 7);

            b.endRoot();
        });

        testOrdering(false, root, 1L, 4L, 6L, 7L);
    }

    @Test
    public void testTryCatchOtherwiseExceptionInCatch() {
        // try {
        //   arg0.append(1);
        //   throw 0;
        //   arg0.append(2);
        // } catch ex {
        //   arg0.append(4);
        //   throw 1;
        //   arg0.append(5);
        // } otherwise {
        //   arg0.append(3);
        // }

        RootCallTarget root = parse("tryCatchOtherwiseException", b -> {
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> emitAppend(b, 3));
                b.beginBlock();
                    emitAppend(b, 1);
                    emitThrow(b, 0);
                    emitAppend(b, 2);
                b.endBlock();

                b.beginBlock();
                    emitAppend(b, 4);
                    emitThrow(b, 1);
                    emitAppend(b, 5);
                b.endBlock();
            b.endTryCatchOtherwise();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(true, root, 1L, 4L);
    }

    @Test
    public void testTryCatchOtherwiseExceptionInOtherwise() {
        // try {
        //   arg0.append(1);
        //   return 0;
        //   arg0.append(2);
        // } catch ex {
        //   arg0.append(5);
        // } otherwise {
        //   arg0.append(3);
        //   throw 0;
        //   arg0.append(4);
        // }

        RootCallTarget root = parse("tryCatchOtherwiseExceptionInOtherwise", b -> {
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> {
                b.beginBlock();
                    emitAppend(b, 3);
                    emitThrow(b, 0);
                    emitAppend(b, 4);
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturn(b, 0);
                    emitAppend(b, 2);
                b.endBlock();

                emitAppend(b, 5);
            b.endTryCatchOtherwise();

            emitReturn(b, 0);

            b.endRoot();
        });

        testOrdering(true, root, 1L, 3L);
    }

    @Test
    public void testTryFinallyNestedFunction() {
        // try {
        //   arg0.append(1);
        //   if (arg1) return 0
        //   arg0.append(2);
        //   if (arg2) goto lbl
        //   arg0.append(3);
        //   if (arg3) throw 123
        //   arg0.append(4);
        // } finally {
        //   def f() { arg0.append(5) }
        //   def g() { arg0.append(6) }
        //   if (arg4) f() else g()
        // }
        // arg0.append(7)
        // lbl:
        // arg0.append(8);
        RootCallTarget root = parse("finallyTryNestedFunction", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();

                    for (int i = 0; i < 10; i++) {
                        // Create extra root nodes to detect any serialization mismatches
                        b.beginRoot();
                            emitThrow(b, -123);
                        b.endRoot();
                    }

                    b.beginRoot();
                        emitAppend(b, 5);
                    BasicInterpreter f = b.endRoot();

                    b.beginRoot();
                        emitAppend(b, 6);
                    BasicInterpreter g = b.endRoot();

                    b.beginInvoke();
                        b.beginConditional();
                            b.emitLoadArgument(4);
                            b.emitLoadConstant(f);
                            b.emitLoadConstant(g);
                        b.endConditional();

                        b.emitLoadArgument(0);
                    b.endInvoke();
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitReturnIf(b, 1, 0);
                    emitAppend(b, 2);
                    emitBranchIf(b, 2, lbl);
                    emitAppend(b, 3);
                    emitThrowIf(b, 3, 123);
                    emitAppend(b, 4);
                b.endBlock();
            b.endTryFinally();
            emitAppend(b, 7);
            b.emitLabel(lbl);
            emitAppend(b, 8);
            b.endBlock();
            b.endRoot();

            for (int i = 0; i < 20; i++) {
                // Create extra root nodes to detect any serialization mismatches
                b.beginRoot();
                    emitThrow(b, -456);
                b.endRoot();
            }
        });

        testOrderingWithArguments(false, root, new Object[] {false, false, false, false}, 1L, 2L, 3L, 4L, 6L, 7L, 8L);
        testOrderingWithArguments(false, root, new Object[] {false, false, false, true}, 1L, 2L, 3L, 4L, 5L, 7L, 8L);
        testOrderingWithArguments(false, root, new Object[] {true, false, false, false}, 1L, 6L);
        testOrderingWithArguments(false, root, new Object[] {true, false, false, true}, 1L, 5L);
        testOrderingWithArguments(false, root, new Object[] {false, true, false, false}, 1L, 2L, 6L,  8L);
        testOrderingWithArguments(false, root, new Object[] {false, true, false, true}, 1L, 2L, 5L, 8L);
        testOrderingWithArguments(true, root, new Object[] {false, false, true, false}, 1L, 2L, 3L, 6L);
        testOrderingWithArguments(true, root, new Object[] {false, false, true, true}, 1L, 2L, 3L, 5L);
    }

    @Test
    public void testTryFinallyNestedFunctionEscapes() {
        // try {
        //   arg0.append(1);
        //   if (arg1) goto lbl
        //   arg0.append(2);
        // } finally {
        //   def f() { arg0.append(4) }
        //   def g() { arg0.append(5) }
        //   x = if (arg2) f else g
        // }
        // arg0.append(3)
        // lbl:
        // x()
        RootCallTarget root = parse("finallyTryNestedFunction", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();
            BytecodeLocal x = b.createLocal();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    for (int i = 0; i < 10; i++) {
                        // Create extra root nodes to detect any serialization mismatches
                        b.beginRoot();
                            emitThrow(b, -123);
                        b.endRoot();
                    }

                    b.beginRoot();
                        emitAppend(b, 4);
                    BasicInterpreter f = b.endRoot();

                    b.beginRoot();
                        emitAppend(b, 5);
                    BasicInterpreter g = b.endRoot();

                    b.beginStoreLocal(x);
                        b.beginConditional();
                            b.emitLoadArgument(2);
                            b.emitLoadConstant(f);
                            b.emitLoadConstant(g);
                        b.endConditional();
                    b.endStoreLocal();
                b.endBlock();
            });
                b.beginBlock();
                    emitAppend(b, 1);
                    emitBranchIf(b, 1, lbl);
                    emitAppend(b, 2);
                b.endBlock();
            b.endTryFinally();
            emitAppend(b, 3);
            b.emitLabel(lbl);
            b.beginInvoke();
            b.emitLoadLocal(x);
            b.emitLoadArgument(0);
            b.endInvoke();
            b.endBlock();
            b.endRoot();

            for (int i = 0; i < 20; i++) {
                // Create extra root nodes to detect any serialization mismatches
                b.beginRoot();
                    emitThrow(b, -456);
                b.endRoot();
            }
        });

        testOrderingWithArguments(false, root, new Object[] {false, false}, 1L, 2L, 3L, 5L);
        testOrderingWithArguments(false, root, new Object[] {false, true}, 1L, 2L, 3L, 4L);
        testOrderingWithArguments(false, root, new Object[] {true, false}, 1L, 5L);
        testOrderingWithArguments(false, root, new Object[] {true, true}, 1L, 4L);
    }

    @Test
    public void testTryFinallyNestedFunctionFields() {
        // This test validates that fields are set properly, even after a serialization round trip.

        // try {
        //   nop
        //   if (arg0) goto lbl
        //   nop
        //   if (arg1) throw 123
        //   nop
        // } finally {
        //   def f() { }
        //   def g() { }
        //   return if (arg2) f else g
        // }
        // lbl:
        BasicInterpreter main = parseNode("finallyTryNestedFunctionFields", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    for (int i = 0; i < 10; i++) {
                        // Create extra root nodes to detect any serialization mismatches
                        b.beginRoot();
                            emitThrow(b, -123);
                        BasicInterpreter dummy = b.endRoot();
                        dummy.setName("dummy" + i);
                    }

                    b.beginRoot();
                        b.emitVoidOperation();
                    BasicInterpreter f = b.endRoot();
                    f.name = "f";

                    b.beginRoot();
                        b.emitVoidOperation();
                    BasicInterpreter g = b.endRoot();
                    g.name = "g";

                    b.beginReturn();
                        b.beginConditional();
                            b.emitLoadArgument(2);
                            b.emitLoadConstant(f);
                            b.emitLoadConstant(g);
                        b.endConditional();
                    b.endReturn();
                b.endBlock();
            });
                b.beginBlock();
                    b.emitVoidOperation();
                    emitBranchIf(b, 0, lbl);
                    b.emitVoidOperation();
                    emitThrowIf(b, 1, 123);
                    b.emitVoidOperation();
                b.endBlock();
            b.endTryFinally();
            b.emitLabel(lbl);
            b.endBlock();
            BasicInterpreter mainRoot = b.endRoot();
            mainRoot.setName("main");

            for (int i = 0; i < 20; i++) {
                // Create extra root nodes to detect any serialization mismatches
                b.beginRoot();
                    emitThrow(b, -456);
                BasicInterpreter dummy = b.endRoot();
                dummy.setName("outerDummy" + i);
            }
        });

        /**
         * Because f and g are declared in the finally handler, each copy of the
         * handler declares a different copy of f and g.
         */
        BasicInterpreter f1 = (BasicInterpreter) main.getCallTarget().call(false, false, true);
        BasicInterpreter f2 = (BasicInterpreter) main.getCallTarget().call(true, false, true);
        BasicInterpreter f3 = (BasicInterpreter) main.getCallTarget().call(false, true, true);
        assertEquals("f", f1.name);
        assertEquals("f", f2.name);
        assertEquals("f", f3.name);
        assertTrue(f1 != f2);
        assertTrue(f2 != f3);
        assertTrue(f1 != f3);

        BasicInterpreter g1 = (BasicInterpreter) main.getCallTarget().call(false, false, false);
        BasicInterpreter g2 = (BasicInterpreter) main.getCallTarget().call(true, false, false);
        BasicInterpreter g3 = (BasicInterpreter) main.getCallTarget().call(false, true, false);
        assertEquals("g", g1.name);
        assertEquals("g", g2.name);
        assertEquals("g", g3.name);
        assertTrue(g1 != g2);
        assertTrue(g2 != g3);
        assertTrue(g1 != g3);

        // There should be exactly 3 of these copies (one for the early exit, one for the exceptional
        // case, and one for the fallthrough case).
        int numF = 0;
        int numG = 0;
        for (RootNode rootNode : main.getRootNodes().getNodes()) {
           BasicInterpreter basicInterpreter = (BasicInterpreter) rootNode;
           if ("f".equals(basicInterpreter.name)) {
               numF++;
           } else if ("g".equals(basicInterpreter.name)) {
               numG++;
           }
        }
        assertEquals(3, numF);
        assertEquals(3, numG);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTryFinallyNestedFunctionInstanceFields() {
        // This test is the same as above, but uses the field values (overridden by us) on the root node instances.

        // try {
        //   nop
        //   if (arg0) goto lbl
        //   nop
        //   if (arg1) throw 123
        //   nop
        // } finally {
        //   def f() { }
        //   def g() { }
        //   return if (arg2) f else g
        // }
        // lbl:
        BasicInterpreter main = parseNode("finallyTryNestedFunctionFields", b -> {
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel lbl = b.createLabel();
            b.beginTryFinally(() -> {
                b.beginBlock();
                    for (int i = 0; i < 10; i++) {
                        // Create extra root nodes to detect any serialization mismatches
                        b.beginRoot();
                            emitThrow(b, -123);
                        b.endRoot();
                    }

                    b.beginRoot();
                        b.emitVoidOperation();
                    BasicInterpreter f = b.endRoot();

                    b.beginRoot();
                        b.emitVoidOperation();
                    BasicInterpreter g = b.endRoot();

                    b.beginReturn();
                        b.beginConditional();
                            b.emitLoadArgument(2);
                            b.emitLoadConstant(f);
                            b.emitLoadConstant(g);
                        b.endConditional();
                    b.endReturn();
                b.endBlock();
            });
                b.beginBlock();
                    b.emitVoidOperation();
                    emitBranchIf(b, 0, lbl);
                    b.emitVoidOperation();
                    emitThrowIf(b, 1, 123);
                    b.emitVoidOperation();
                b.endBlock();
            b.endTryFinally();
            b.emitLabel(lbl);
            b.endBlock();
            b.endRoot();

            for (int i = 0; i < 20; i++) {
                // Create extra root nodes to detect any serialization mismatches
                b.beginRoot();
                    emitThrow(b, -456);
                b.endRoot();
            }
        });

        // Set names on the instances themselves.
        int i = 0;
        for (RootNode rootNode : main.getRootNodes().getNodes()) {
            BasicInterpreter basicInterpreter = (BasicInterpreter) rootNode;
            basicInterpreter.setName("rootNode" + i++);
        }

        // Check that the instance fields are used for serialization.
        BytecodeRootNodes<BasicInterpreter> roundTripped = doRoundTrip((BytecodeRootNodes<BasicInterpreter>) main.getRootNodes());
        i = 0;
        for (RootNode rootNode : roundTripped.getNodes()) {
            BasicInterpreter basicInterpreter = (BasicInterpreter) rootNode;
            assertEquals("rootNode" + i++, basicInterpreter.getName());
        }
    }

    @Test
    public void testTryFinallyCallOuterFunction() {
        // def f() { arg0.append(2); }
        // def g() { arg0.append(3); }
        // try {
        //   arg0.append(1);
        // } finally {
        //   if (arg1) f() else g()
        // }
        RootCallTarget root = parse("finallyTryCallOuterFunction", b -> {
            b.beginRoot();

            b.beginRoot();
            emitAppend(b, 2);
            BasicInterpreter f = b.endRoot();

            b.beginRoot();
            emitAppend(b, 3);
            BasicInterpreter g = b.endRoot();

            b.beginTryFinally(() -> {
                b.beginBlock();
                    for (int i = 0; i < 10; i++) {
                        // Create extra root nodes to detect any serialization mismatches
                        b.beginRoot();
                            emitThrow(b, -123);
                        b.endRoot();
                    }

                    b.beginInvoke();
                        b.beginConditional();
                            b.emitLoadArgument(1);
                            b.emitLoadConstant(f);
                            b.emitLoadConstant(g);
                        b.endConditional();

                        b.emitLoadArgument(0);
                    b.endInvoke();
                b.endBlock();
            });
                emitAppend(b, 1);
            b.endTryFinally();

            b.endRoot();

            for (int i = 0; i < 20; i++) {
                // Create extra root nodes to detect any serialization mismatches
                b.beginRoot();
                    emitThrow(b, -456);
                b.endRoot();
            }
        });

        testOrderingWithArguments(false, root, new Object[] {false}, 1L, 3L);
        testOrderingWithArguments(false, root, new Object[] {true}, 1L, 2L);
    }
}
