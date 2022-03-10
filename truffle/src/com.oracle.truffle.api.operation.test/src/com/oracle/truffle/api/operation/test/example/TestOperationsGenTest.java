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

@RunWith(JUnit4.class)
public class TestOperationsGenTest {

    private static void parseAdd(SlOperationsBuilderino b) {
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

    private static void parseMax(SlOperationsBuilderino b) {
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

    @Test
    public void testAdd() {
        runTest(TestOperationsGenTest::parseAdd, 42L, 20L, 22L);
        runTest(TestOperationsGenTest::parseAdd, "foobar", "foo", "bar");
    }

    @Test
    public void testMax() {
        runTest(TestOperationsGenTest::parseMax, 42L, 42L, 13L);
        runTest(TestOperationsGenTest::parseMax, 42L, 13L, 42L);
    }

    @Test
    public void testSumLoop() {
        runTest(TestOperationsGenTest::parseSumLoop, 45L, 10L);
    }

    @Test
    public void testBreakLoop() {
        runTest(TestOperationsGenTest::parseBreakLoop, 6L, 5L);
        runTest(TestOperationsGenTest::parseBreakLoop, 10L, 15L);
    }

    @Test
    public void testBlockPopping() {
        runTest(TestOperationsGenTest::parseBlockPopping, 5L);
    }

    @Test
    public void testVeryComplex() {
        runTest(TestOperationsGenTest::parseVeryComplex, 10L);
    }

    @Test
    public void testVoidOperation() {
        runTest(TestOperationsGenTest::parseVoidOperation, List.of(1, 2, 3), new ArrayList(), 1, 2, 3);
    }

    @Test
    public void testTryCatchOperation() {
        runTest(TestOperationsGenTest::parseTryCatchOperation, 0L, 1L);
        runTest(TestOperationsGenTest::parseTryCatchOperation, 1L, -1L);
    }

    @Test
    public void testBooleanIfThingy() {
        runTest(TestOperationsGenTest::parseBooleanIfThingy, 1L, 1L, -1L);
        runTest(TestOperationsGenTest::parseBooleanIfThingy, 2L, -1L, 2L);
        runTest(TestOperationsGenTest::parseBooleanIfThingy, -1L, -3L, -4L);
    }

    private static void runTest(Consumer<SlOperationsBuilderino> parse, Object expectedResult, Object... args) {
        System.out.println("------------------------------------");
        SlOperationsBuilderino b = SlOperationsBuilderino.createBuilder();
        parse.accept(b);
        System.out.println(" building");
        OperationsNode executable = b.build();
        System.out.println(" dumping");
        System.out.println(executable.dump());
        b.reset();
        System.out.println(executable);
        CallTarget target = executable.getCallTarget();
        Object result = target.call(args);
        Assert.assertEquals(expectedResult, result);
    }

    private static void parseSumLoop(SlOperationsBuilderino b) {
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

    private static void parseBreakLoop(SlOperationsBuilderino b) {
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

    private static void parseBlockPopping(SlOperationsBuilderino b) {
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

    private static void parseVeryComplex(SlOperationsBuilderino b) {
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

    private static void parseVoidOperation(SlOperationsBuilderino b) {
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

    private static void parseTryCatchOperation(SlOperationsBuilderino b) {
        // function tryCatch(x) {
        // try {
        // if (x < 0) throw(...)
        // } catch {
        // return 1
        // }
        // return 0

        b.beginTryCatch();

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

    private static void beginBooleanAnd(SlOperationsBuilderino b, int i) {
        // a && b -> { l0 = a; if (isFalsey(l0)) { l0 = b }; l0 }

        b.beginBlock();
        b.beginStoreLocal(i);
    }

    private static void middleBooleanAnd(SlOperationsBuilderino b, int i) {
        b.endStoreLocal();

        b.beginIfThen();

        b.beginIsFalseyOperation();
        b.emitLoadLocal(i);
        b.endIsFalseyOperation();

        b.beginStoreLocal(i);
    }

    private static void endBooleanAnd(SlOperationsBuilderino b, int i) {
        b.endStoreLocal();

        b.endIfThen();

        b.emitLoadLocal(i);

        b.endBlock();
    }

    private static void parseBooleanIfThingy(SlOperationsBuilderino b) {

        // function test(x, y) {
        // try {
        // return x && y && throw();
        // } catch {
        // return -1;
        // }

        b.beginTryCatch();
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
