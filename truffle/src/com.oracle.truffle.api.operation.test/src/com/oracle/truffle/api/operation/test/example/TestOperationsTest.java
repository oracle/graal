package com.oracle.truffle.api.operation.test.example;

import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationsNode;

@RunWith(JUnit4.class)
public class TestOperationsTest {

    private static class OperationsRootNode extends RootNode {

        @Child private OperationsNode executable;

        protected OperationsRootNode(OperationsNode executable) {
            super(null);
            this.executable = executable;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return this.executable.execute(frame);
        }

    }

    private static void parseAdd(SlOperationsBuilder b) {
        // simple test:
        // function foo(a, b) {
        // return (a + b);
        // }
        b.beginReturn();
        b.beginAddOperation();
        b.emitLoadArgument(0);
        b.emitLoadArgument(1);
        b.endAddOperation();
        b.endReturn();
    }

    private static void parseMax(SlOperationsBuilder b) {
        // control flow test:
        // function max(a, b) {
        // if (a < b) {
        // return b;
        // } else {
        // return a;
        // }
        b.beginIfThenElse();

        b.beginLessThanOperation();
        b.emitLoadArgument(0); // a
        b.emitLoadArgument(1); // b
        b.endLessThanOperation();

        b.beginReturn();
        b.emitLoadArgument(1); // b
        b.endReturn();

        b.beginReturn();
        b.emitLoadArgument(0); // a
        b.endReturn();

        b.endIfThenElse();
    }

    @Test
    public void testAdd() {
        runTest(TestOperationsTest::parseAdd, 42L, 20L, 22L);
    }

    @Test
    public void testMax() {
        runTest(TestOperationsTest::parseMax, 42L, 42L, 13L);
        runTest(TestOperationsTest::parseMax, 42L, 13L, 42L);
    }

    @Test
    public void testSumLoop() {
        runTest(TestOperationsTest::parseSumLoop, 45L, 10L);
    }

    @Test
    public void testBreakLoop() {
        runTest(TestOperationsTest::parseBreakLoop, 6L, 5L);
        runTest(TestOperationsTest::parseBreakLoop, 10L, 15L);
    }

    private static void runTest(Consumer<SlOperationsBuilder> parse, Object expectedResult, Object... args) {
        SlOperationsBuilder b = SlOperationsBuilder.createBuilder();
        parse.accept(b);
        OperationsNode executable = b.build();
        System.out.println(executable.dump());
        b.reset();
        System.out.println(executable);
        CallTarget target = new OperationsRootNode(executable).getCallTarget();
        Object result = target.call(args);
        Assert.assertEquals(expectedResult, result);
    }

    private static void parseSumLoop(SlOperationsBuilder b) {
        // control flow test:
        // function sum(length) {
        // sum = 0;
        // i = 0;
        // while (i < length) {
        // sum += i;
        // i += 1;
        // }
        // return sum;

        b.beginStoreLocal(0); // sum
        b.emitConstLong(0);
        b.endStoreLocal();

        b.beginStoreLocal(1); // i
        b.emitConstLong(0);
        b.endStoreLocal();

        b.beginWhile();

        b.beginLessThanOperation();
        b.emitLoadLocal(1); // i
        b.emitLoadArgument(0); // length
        b.endLessThanOperation();

        b.beginBlock();

        b.beginStoreLocal(0); // sum
        b.beginAddOperation();
        b.emitLoadLocal(0);
        b.emitLoadLocal(1); // i
        b.endAddOperation();
        b.endStoreLocal();

        b.beginStoreLocal(1);
        b.beginAddOperation();
        b.emitLoadLocal(1);
        b.emitConstLong(1);
        b.endAddOperation();
        b.endStoreLocal();

        b.endBlock();

        b.endWhile();

        b.beginReturn();
        b.emitLoadLocal(0);
        b.endReturn();
    }

    private static void parseBreakLoop(SlOperationsBuilder b) {
        // function breakLoop(input) {
        // i = 0;
        // while (i < 10) {
        // if (input < i) break;
        // i += 1;
        // }
        // return i;

        b.beginStoreLocal(0); // i
        b.emitConstLong(0);
        b.endStoreLocal();

        OperationLabel breakLbl = b.createLabel();

        b.beginWhile();

        b.beginLessThanOperation();
        b.emitLoadLocal(0); // i
        b.emitConstLong(10);
        b.endLessThanOperation();

        b.beginBlock();

        b.beginIfThen();

        b.beginLessThanOperation();
        b.emitLoadArgument(0); // input
        b.emitLoadLocal(0); // i
        b.endLessThanOperation();

        b.emitBranch(breakLbl);

        b.endIfThen();

        b.beginStoreLocal(0);
        b.beginAddOperation();
        b.emitLoadLocal(0);
        b.emitConstLong(1);
        b.endAddOperation();
        b.endStoreLocal();

        b.endBlock();

        b.endWhile();

        b.markLabel(breakLbl);

        b.beginReturn();
        b.emitLoadLocal(0);
        b.endReturn();
    }

}
