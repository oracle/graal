package com.oracle.truffle.api.operation.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;

public class TestOperationsBranchTest extends AbstractTestOperationsTest {
    // @formatter:off

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

}
