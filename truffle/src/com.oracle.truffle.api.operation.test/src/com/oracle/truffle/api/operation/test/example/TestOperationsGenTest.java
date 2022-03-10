package com.oracle.truffle.api.operation.test.example;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.tracing.InstructionTrace;

@RunWith(JUnit4.class)
public class TestOperationsGenTest {

    private static OperationsNode doBuild(Consumer<TestOperationsBuilder> bm) {
        TestOperationsBuilder builder = TestOperationsBuilder.createBuilder();
        bm.accept(builder);
        return builder.build();
    }

    @Test
    public void testAdd() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseAdd);
        runTest(node, 42L, 20L, 22L);
        runTest(node, "foobar", "foo", "bar");
        runTest(node, 100L, 120L, -20L);

        InstructionTrace[] instructions = node.getNodeTrace().getInstructions();

        Assert.assertEquals(3, instructions[0].getHitCount());
        Assert.assertEquals(3, instructions[1].getHitCount());
        Assert.assertEquals(3, instructions[2].getHitCount());
        Assert.assertEquals(3, instructions[3].getHitCount());
    }

    @Test
    public void testMax() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseMax);
        runTest(node, 42L, 42L, 13L);
        runTest(node, 42L, 42L, 13L);
        runTest(node, 42L, 13L, 42L);
        runTest(node, 42L, 13L, 42L);

        InstructionTrace[] instructions = node.getNodeTrace().getInstructions();

        Assert.assertEquals(4, instructions[0].getHitCount());
        Assert.assertEquals(2, instructions[instructions.length - 1].getHitCount());
    }

    @Test
    public void testSumLoop() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseSumLoop);
        runTest(node, 45L, 10L);
    }

    @Test
    public void testBreakLoop() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseBreakLoop);
        runTest(node, 6L, 5L);
        runTest(node, 10L, 15L);
    }

    @Test
    public void testBlockPopping() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseBlockPopping);
        runTest(node, 5L);
    }

    @Test
    public void testVeryComplex() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseVeryComplex);
        runTest(node, 10L);
    }

    @Test
    public void testVoidOperation() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseVoidOperation);
        runTest(node, List.of(1, 2, 3), new ArrayList<Integer>(), 1, 2, 3);
    }

    @Test
    public void testTryCatchOperation() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseTryCatchOperation);
        runTest(node, 0L, 1L);
        runTest(node, 1L, -1L);
    }

    @Test
    public void testBooleanIfThingy() {
        OperationsNode node = doBuild(TestOperationsGenTest::parseBooleanIfThingy);
        runTest(node, 1L, 1L, -1L);
        runTest(node, 2L, -1L, 2L);
        runTest(node, -1L, -3L, -4L);
    }

    private static void runTest(OperationsNode executable, Object expectedResult, Object... args) {
        System.out.println(executable);
        CallTarget target = executable.getCallTarget();
        Object result = target.call(args);
        System.out.println(executable.dump());
        Assert.assertEquals(expectedResult, result);
    }

    private static void parseAdd(TestOperationsBuilder b) {
        // simple test:
        // function foo(a, b) {
        // return (a + b);
        // }
        b.beginReturn();
        b.beginAddOperation();
        b.emitLoadArgument((short) 0);
        b.emitLoadArgument((short) 1);
        b.endAddOperation();
        b.endReturn();
    }

    private static void parseMax(TestOperationsBuilder b) {
        // control flow test:
        // function max(a, b) {
        // if (a < b) {
        // return b;
        // } else {
        // return a;
        // }
        b.beginIfThenElse();

        b.beginLessThanOperation();
        b.emitLoadArgument((short) 0); // a
        b.emitLoadArgument((short) 1); // b
        b.endLessThanOperation();

        b.beginReturn();
        b.emitLoadArgument((short) 1); // b
        b.endReturn();

        b.beginReturn();
        b.emitLoadArgument((short) 0); // a
        b.endReturn();

        b.endIfThenElse();
    }

    private static void parseSumLoop(TestOperationsBuilder b) {
        // control flow test:
        // function sum(length) {
        // sum = 0;
        // i = 0;
        // while (i < length) {
        // sum += i;
        // i += 1;
        // }
        // return sum;

        b.beginStoreLocal((short) 0); // sum
        b.emitConstObject(0L);
        b.endStoreLocal();

        b.beginStoreLocal((short) 1); // i
        b.emitConstObject(0L);
        b.endStoreLocal();

        b.beginWhile();

        b.beginLessThanOperation();
        b.emitLoadLocal((short) 1); // i
        b.emitLoadArgument((short) 0); // length
        b.endLessThanOperation();

        b.beginBlock();

        b.beginStoreLocal((short) 0); // sum
        b.beginAddOperation();
        b.emitLoadLocal((short) 0);
        b.emitLoadLocal((short) 1); // i
        b.endAddOperation();
        b.endStoreLocal();

        b.beginStoreLocal((short) 1);
        b.beginAddOperation();
        b.emitLoadLocal((short) 1);
        b.emitConstObject(1L);
        b.endAddOperation();
        b.endStoreLocal();

        b.endBlock();

        b.endWhile();

        b.beginReturn();
        b.emitLoadLocal((short) 0);
        b.endReturn();
    }

    private static void parseBreakLoop(TestOperationsBuilder b) {
        // function breakLoop(input) {
        // i = 0;
        // while (i < 10) {
        // if (input < i) break;
        // i += 1;
        // }
        // return i;

        b.beginStoreLocal((short) 0); // i
        b.emitConstObject(0L);
        b.endStoreLocal();

        OperationLabel breakLbl = b.createLabel();

        b.beginWhile();

        b.beginLessThanOperation();
        b.emitLoadLocal((short) 0); // i
        b.emitConstObject(10L);
        b.endLessThanOperation();

        b.beginBlock();

        b.beginIfThen();

        b.beginLessThanOperation();
        b.emitLoadArgument((short) 0); // input
        b.emitLoadLocal((short) 0); // i
        b.endLessThanOperation();

        b.emitBranch(breakLbl);

        b.endIfThen();

        b.beginStoreLocal((short) 0);
        b.beginAddOperation();
        b.emitLoadLocal((short) 0);
        b.emitConstObject(1L);
        b.endAddOperation();
        b.endStoreLocal();

        b.endBlock();

        b.endWhile();

        b.emitLabel(breakLbl);

        b.beginReturn();
        b.emitLoadLocal((short) 0);
        b.endReturn();
    }

    private static void parseBlockPopping(TestOperationsBuilder b) {
        // function blockPopping() {
        // return 1 + {2; 3; 4}
        // }

        b.beginReturn();
        b.beginAddOperation();
        b.emitConstObject(1L);

        b.beginBlock();
        b.emitConstObject(2L);
        b.emitConstObject(3L);
        b.emitConstObject(4L);
        b.endBlock();

        b.endAddOperation();
        b.endReturn();
    }

    private static void parseVeryComplex(TestOperationsBuilder b) {
        // function veryComplex() {
        // return veryComplex(1, 2, 3, 4, 5) + 6
        // }

        b.beginReturn();
        b.beginAddOperation();
        b.emitConstObject(6L);
        b.beginVeryComplexOperation();
        b.emitConstObject(1L);
        b.emitConstObject(2L);
        b.emitConstObject(3L);
        b.emitConstObject(4L);
        b.emitConstObject(5L);
        b.endVeryComplexOperation();
        b.endAddOperation();
        b.endReturn();
    }

    private static void parseVoidOperation(TestOperationsBuilder b) {
        // function veryComplex(l1, a, b, c) {
        // addToList(l1, a);
        // ...
        // return l1;
        // }

        b.beginAddToListOperation();
        b.emitLoadArgument(0);
        b.emitLoadArgument(1);
        b.endAddToListOperation();
        b.beginAddToListOperation();
        b.emitLoadArgument(0);
        b.emitLoadArgument(2);
        b.endAddToListOperation();
        b.beginAddToListOperation();
        b.emitLoadArgument(0);
        b.emitLoadArgument(3);
        b.endAddToListOperation();
        b.beginReturn();
        b.emitLoadArgument(0);
        b.endReturn();
    }

    private static void parseTryCatchOperation(TestOperationsBuilder b) {
        // function tryCatch(x) {
        // try {
        // if (x < 0) throw(...)
        // } catch {
        // return 1
        // }
        // return 0

        b.beginTryCatch(1);

        b.beginIfThen();

        b.beginLessThanOperation();
        b.emitLoadArgument(0);
        b.emitConstObject(0L);
        b.endLessThanOperation();

        b.emitThrowOperation();

        b.endIfThen();

        b.beginReturn();
        b.emitConstObject(1L);
        b.endReturn();

        b.endTryCatch();

        b.beginReturn();
        b.emitConstObject(0L);
        b.endReturn();
    }

    private static void beginBooleanAnd(TestOperationsBuilder b, int i) {
        // a && b -> { l0 = a; if (isFalsey(l0)) { l0 = b }; l0 }

        b.beginBlock();
        b.beginStoreLocal(i);
    }

    private static void middleBooleanAnd(TestOperationsBuilder b, int i) {
        b.endStoreLocal();

        b.beginIfThen();

        b.beginIsFalseyOperation();
        b.emitLoadLocal(i);
        b.endIsFalseyOperation();

        b.beginStoreLocal(i);
    }

    private static void endBooleanAnd(TestOperationsBuilder b, int i) {
        b.endStoreLocal();

        b.endIfThen();

        b.emitLoadLocal(i);

        b.endBlock();
    }

    private static void parseBooleanIfThingy(TestOperationsBuilder b) {

        // function test(x, y) {
        // try {
        // return x && y && throw();
        // } catch (e) {
        // return -1;
        // }

        b.beginTryCatch(2);
        {
            b.beginReturn();
            {

                beginBooleanAnd(b, 0);
                {
                    b.emitLoadArgument(0);
                }
                middleBooleanAnd(b, 0);
                {

                    beginBooleanAnd(b, 1);
                    b.emitLoadArgument(1);
                    middleBooleanAnd(b, 1);
                    b.emitThrowOperation();
                    endBooleanAnd(b, 1);
                }
                endBooleanAnd(b, 0);
            }
            b.endReturn();

            b.beginReturn();
            b.emitConstObject(-1L);
            b.endReturn();
        }
        b.endTryCatch();

    }

}
