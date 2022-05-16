package com.oracle.truffle.api.operation.test.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNode;

public class TestOperationsParserTest {

    private static RootCallTarget parse(Consumer<TestOperationsBuilder> builder) {
        OperationNode operationsNode = parseNode(builder);
        System.out.println(operationsNode);
        return new TestRootNode(operationsNode).getCallTarget();
    }

    private static OperationNode parseNode(Consumer<TestOperationsBuilder> builder) {
        return TestOperationsBuilder.create(OperationConfig.DEFAULT, builder).getNodes().get(0);
    }

    @Test
    public void testAdd() {
        RootCallTarget root = parse(b -> {
            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadArgument(0);
            b.emitLoadArgument(1);
            b.endAddOperation();
            b.endReturn();

            b.publish();
        });

        Assert.assertEquals(42L, root.call(20L, 22L));
        Assert.assertEquals("foobar", root.call("foo", "bar"));
        Assert.assertEquals(100L, root.call(120L, -20L));
    }

    @Test
    public void testMax() {
        RootCallTarget root = parse(b -> {
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

            b.publish();
        });

        Assert.assertEquals(42L, root.call(42L, 13L));
        Assert.assertEquals(42L, root.call(42L, 13L));
        Assert.assertEquals(42L, root.call(42L, 13L));
        Assert.assertEquals(42L, root.call(13L, 42L));
    }

    @Test
    public void testIfThen() {
        RootCallTarget root = parse(b -> {
            b.beginIfThen();

            b.beginLessThanOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(0L);
            b.endLessThanOperation();

            b.beginReturn();
            b.emitConstObject(0L);
            b.endReturn();

            b.endIfThen();

            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();

            b.publish();
        });

        Assert.assertEquals(0L, root.call(-2L));
        Assert.assertEquals(0L, root.call(-1L));
        Assert.assertEquals(0L, root.call(0L));
        Assert.assertEquals(1L, root.call(1L));
        Assert.assertEquals(2L, root.call(2L));
    }

    @Test
    public void testSumLoop() {

        // i = 0; j = 0;
        // while ( i < arg0 ) { j = j + i; i = i + 1; }
        // return j;

        RootCallTarget root = parse(b -> {
            OperationLocal locI = b.createLocal();
            OperationLocal locJ = b.createLocal();

            b.beginStoreLocal(locI);
            b.emitConstObject(0L);
            b.endStoreLocal();

            b.beginStoreLocal(locJ);
            b.emitConstObject(0L);
            b.endStoreLocal();

            b.beginWhile();
            {
                b.beginLessThanOperation();
                b.emitLoadLocal(locI);
                b.emitLoadArgument(0);
                b.endLessThanOperation();

                b.beginBlock();
                {
                    b.beginStoreLocal(locJ);
                    {
                        b.beginAddOperation();
                        b.emitLoadLocal(locJ);
                        b.emitLoadLocal(locI);
                        b.endAddOperation();
                    }
                    b.endStoreLocal();

                    b.beginStoreLocal(locI);
                    {
                        b.beginAddOperation();
                        b.emitLoadLocal(locI);
                        b.emitConstObject(1L);
                        b.endAddOperation();
                    }
                    b.endStoreLocal();
                }
                b.endBlock();
            }
            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(locJ);
            b.endReturn();

            b.publish();
        });

        Assert.assertEquals(45L, root.call(10L));
    }

    @Test
    public void testTryCatch() {
        RootCallTarget root = parse(b -> {
            b.beginTryCatch(0);

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

            b.publish();
        });

        Assert.assertEquals(1L, root.call(-1L));
        Assert.assertEquals(0L, root.call(1L));
    }

    @Test
    public void testVariableBoxingElim() {
        RootCallTarget root = parse(b -> {

            OperationLocal local0 = b.createLocal();
            OperationLocal local1 = b.createLocal();

            b.beginStoreLocal(local0);
            b.emitConstObject(0L);
            b.endStoreLocal();

            b.beginStoreLocal(local1);
            b.emitConstObject(0L);
            b.endStoreLocal();

            b.beginWhile();

            b.beginLessThanOperation();
            b.emitLoadLocal(local0);
            b.emitConstObject(100L);
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
            b.emitConstObject(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(local1);
            b.endReturn();

            b.publish();
        });

        Assert.assertEquals(4950L, root.call());
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

        // try { 1; } finally { 2; }
        // expected 1, 2

        RootCallTarget root = parse(b -> {
            b.beginFinallyTry();
            {

                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitConstObject(2L);
                b.endAppenderOperation();

                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitConstObject(1L);
                b.endAppenderOperation();
            }
            b.endFinallyTry();

            b.beginReturn();
            b.emitConstObject(0L);
            b.endReturn();

            b.publish();
        });

        testOrdering(false, root, 1L, 2L);
    }

    @Test
    public void testFinallyTryException() {

        // try { 1; throw; 2; } finally { 3; }
        // expected: 1, 3

        RootCallTarget root = parse(b -> {
            b.beginFinallyTry();
            {
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitConstObject(3L);
                b.endAppenderOperation();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.emitThrowOperation();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();
                }
                b.endBlock();
            }
            b.endFinallyTry();

            b.beginReturn();
            b.emitConstObject(0L);
            b.endReturn();

            b.publish();
        });

        testOrdering(true, root, 1L, 3L);
    }

    @Test
    public void testFinallyTryReturn() {
        RootCallTarget root = parse(b -> {
            b.beginFinallyTry();
            {
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitConstObject(1L);
                b.endAppenderOperation();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitConstObject(0L);
                    b.endReturn();
                }
                b.endBlock();
            }
            b.endFinallyTry();

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(3L);
            b.endAppenderOperation();

            b.publish();
        });

        testOrdering(false, root, 2L, 1L);
    }

    @Test
    public void testFinallyTryBranchOut() {
        RootCallTarget root = parse(b -> {

            // try { 1; goto lbl; 2; } finally { 3; } 4; lbl: 5;
            // expected: 1, 3, 5

            OperationLabel lbl = b.createLabel();

            b.beginFinallyTry();
            {
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitConstObject(3L);
                b.endAppenderOperation();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.emitBranch(lbl);

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();
                }
                b.endBlock();
            }
            b.endFinallyTry();

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(4L);
            b.endAppenderOperation();

            b.emitLabel(lbl);

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(5L);
            b.endAppenderOperation();

            b.beginReturn();
            b.emitConstObject(0L);
            b.endReturn();

            b.publish();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryCancel() {
        RootCallTarget root = parse(b -> {

            // try { 1; return; } finally { 2; goto lbl; } 3; lbl: 4;
            // expected: 1, 2, 4

            OperationLabel lbl = b.createLabel();

            b.beginFinallyTry();
            {
                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();

                    b.emitBranch(lbl);
                }
                b.endBlock();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitConstObject(0L);
                    b.endReturn();
                }
                b.endBlock();
            }
            b.endFinallyTry();

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(3L);
            b.endAppenderOperation();

            b.emitLabel(lbl);

            b.beginAppenderOperation();
            b.emitLoadArgument(0);
            b.emitConstObject(4L);
            b.endAppenderOperation();

            b.beginReturn();
            b.emitConstObject(0L);
            b.endReturn();

            b.publish();
        });

        testOrdering(false, root, 1L, 2L, 4L);
    }

    @Test
    public void testFinallyTryInnerCf() {
        RootCallTarget root = parse(b -> {

            // try { 1; return; 2 } finally { 3; goto lbl; 4; lbl: 5; }
            // expected: 1, 3, 5

            b.beginFinallyTry();
            {
                b.beginBlock();
                {
                    OperationLabel lbl = b.createLabel();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(3L);
                    b.endAppenderOperation();

                    b.emitBranch(lbl);

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(4L);
                    b.endAppenderOperation();

                    b.emitLabel(lbl);

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(5L);
                    b.endAppenderOperation();
                }
                b.endBlock();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitConstObject(0L);
                    b.endReturn();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();
                }
                b.endBlock();
            }
            b.endFinallyTry();

            b.publish();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNestedTry() {
        RootCallTarget root = parse(b -> {

            // try { try { 1; return; 2; } finally { 3; } } finally { 4; }
            // expected: 1, 3, 4

            b.beginFinallyTry();
            {
                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(4L);
                    b.endAppenderOperation();
                }
                b.endBlock();

                b.beginFinallyTry();
                {
                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(3L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();

                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(1L);
                        b.endAppenderOperation();

                        b.beginReturn();
                        b.emitConstObject(0L);
                        b.endReturn();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(2L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();
                }
                b.endFinallyTry();
            }
            b.endFinallyTry();

            b.publish();
        });

        testOrdering(false, root, 1L, 3L, 4L);
    }

    @Test
    public void testFinallyTryNestedFinally() {
        RootCallTarget root = parse(b -> {

            // try { 1; return; 2; } finally { try { 3; return; 4; } finally { 5; } }
            // expected: 1, 3, 5

            b.beginFinallyTry();
            {
                b.beginFinallyTry();
                {
                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(5L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();

                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(3L);
                        b.endAppenderOperation();

                        b.beginReturn();
                        b.emitConstObject(0L);
                        b.endReturn();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(4L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();
                }
                b.endFinallyTry();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitConstObject(0L);
                    b.endReturn();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();
                }
                b.endBlock();
            }
            b.endFinallyTry();

            b.publish();
        });

        testOrdering(false, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNestedTryThrow() {
        RootCallTarget root = parse(b -> {

            // try { try { 1; throw; 2; } finally { 3; } } finally { 4; }
            // expected: 1, 3, 4

            b.beginFinallyTry();
            {
                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(4L);
                    b.endAppenderOperation();
                }
                b.endBlock();

                b.beginFinallyTry();
                {
                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(3L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();

                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(1L);
                        b.endAppenderOperation();

                        b.emitThrowOperation();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(2L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();
                }
                b.endFinallyTry();
            }
            b.endFinallyTry();

            b.publish();
        });

        testOrdering(true, root, 1L, 3L, 4L);
    }

    @Test
    public void testFinallyTryNestedFinallyThrow() {
        RootCallTarget root = parse(b -> {

            // try { 1; throw; 2; } finally { try { 3; throw; 4; } finally { 5; } }
            // expected: 1, 3, 5

            b.beginFinallyTry();
            {
                b.beginFinallyTry();
                {
                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(5L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();

                    b.beginBlock();
                    {
                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(3L);
                        b.endAppenderOperation();

                        b.emitThrowOperation();

                        b.beginAppenderOperation();
                        b.emitLoadArgument(0);
                        b.emitConstObject(4L);
                        b.endAppenderOperation();
                    }
                    b.endBlock();
                }
                b.endFinallyTry();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.emitThrowOperation();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();
                }
                b.endBlock();
            }
            b.endFinallyTry();

            b.publish();
        });

        testOrdering(true, root, 1L, 3L, 5L);
    }

    @Test
    public void testFinallyTryNoExceptReturn() {

        // try { 1; return; 2; } finally noexcept { 3; }
        // expected: 1, 3

        RootCallTarget root = parse(b -> {
            b.beginFinallyTryNoExcept();
            {
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitConstObject(3L);
                b.endAppenderOperation();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.beginReturn();
                    b.emitConstObject(0L);
                    b.endReturn();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();
                }
                b.endBlock();
            }
            b.endFinallyTryNoExcept();

            b.publish();
        });

        testOrdering(false, root, 1L, 3L);
    }

    @Test
    public void testFinallyTryNoExceptException() {

        // try { 1; throw; 2; } finally noexcept { 3; }
        // expected: 1

        RootCallTarget root = parse(b -> {
            b.beginFinallyTryNoExcept();
            {
                b.beginAppenderOperation();
                b.emitLoadArgument(0);
                b.emitConstObject(3L);
                b.endAppenderOperation();

                b.beginBlock();
                {
                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(1L);
                    b.endAppenderOperation();

                    b.emitThrowOperation();

                    b.beginAppenderOperation();
                    b.emitLoadArgument(0);
                    b.emitConstObject(2L);
                    b.endAppenderOperation();
                }
                b.endBlock();
            }
            b.endFinallyTryNoExcept();

            b.publish();
        });

        testOrdering(true, root, 1L);
    }

    @Test
    public void testMetadata() {
        final String value = "test data";

        OperationNode node = parseNode(b -> {
            b.setTestData(value);
            b.publish();
        });

        Assert.assertEquals(value, node.getMetadata(TestOperations.TEST_DATA));
        Assert.assertEquals(value, TestOperations.TEST_DATA.getValue(node));
    }

    @Test
    public void testMetadataChange() {
        final String value = "test data";

        OperationNode node = parseNode(b -> {
            b.setTestData("some old value");
            b.setTestData(value);
            b.publish();
        });

        Assert.assertEquals(value, node.getMetadata(TestOperations.TEST_DATA));
        Assert.assertEquals(value, TestOperations.TEST_DATA.getValue(node));
    }

    @Test
    public void testMetadataDefaultValue() {
        OperationNode node = parseNode(b -> {
            b.publish();
        });

        Assert.assertEquals(TestOperations.TEST_DATA.getDefaultValue(), node.getMetadata(TestOperations.TEST_DATA));
        Assert.assertEquals(TestOperations.TEST_DATA.getDefaultValue(), TestOperations.TEST_DATA.getValue(node));
    }
}
