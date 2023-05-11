package com.oracle.truffle.api.operation.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;

public class TestOperationsFinallyTryTest extends AbstractTestOperationsTest {
    // @formatter:off

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

}
