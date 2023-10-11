package com.oracle.truffle.api.bytecode.test.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.OperationLocal;

public class OperationsExampleYieldTest extends AbstractOperationsExampleTest {

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
    public void testYieldTee() {
        // yield tee(local, 1);
        // yield tee(local, local + 1);
        // return local + 1;

        // Unlike with testYieldLocal, the local here is set using a LocalSetter in a custom
        // operation. The localFrame should be passed to the custom operation (as opposed to the
        // frame containing the stack locals).

        RootCallTarget root = parse("yieldTee", b -> {
            b.beginRoot(LANGUAGE);
            OperationLocal local = b.createLocal();

            b.beginYield();
            b.beginTeeLocal(local);
            b.emitLoadConstant(1L);
            b.endTeeLocal();
            b.endYield();

            b.beginYield();
            b.beginTeeLocal(local);
            b.beginAddOperation();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endTeeLocal();
            b.endYield();

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadLocal(local);
            b.emitLoadConstant(1L);
            b.endAddOperation();
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
        // @formatter:off
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
        // @formatter:on

        RootCallTarget root = parse("yieldFromFinally", b -> {
            b.beginRoot(LANGUAGE);

            b.beginFinallyTry(b.createLocal());

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
    public void testYieldUpdateArguments() {
        // yield arg0
        // return arg0

        // If we update arguments, the resumed code should see the updated value.
        RootCallTarget root = parse("yieldUpdateArguments", b -> {
            b.beginRoot(LANGUAGE);

            b.beginYield();
            b.emitLoadArgument(0);
            b.endYield();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) root.call(42L);
        assertEquals(42L, r1.getResult());
        r1.frame.getArguments()[0] = 123L;
        assertEquals(123L, r1.continueWith(null));
    }

    @Test
    public void testYieldGetSourceRootNode() {
        OperationsExample rootNode = OperationsExampleCommon.parseNode(interpreterClass, "yieldGetSourceRootNode", b -> {
            b.beginRoot(LANGUAGE);

            b.beginYield();
            b.emitLoadArgument(0);
            b.endYield();

            b.endRoot();
        });

        ContinuationResult r1 = (ContinuationResult) rootNode.getCallTarget().call(42L);
        if (r1.getContinuationCallTarget().getRootNode() instanceof ContinuationRootNode continuationRootNode) {
            OperationsExample sourceRootNode = (OperationsExample) continuationRootNode.getSourceRootNode();
            assertEquals(rootNode, sourceRootNode);
        } else {
            fail("yield did not return a continuation");
        }
    }

}
