package com.oracle.truffle.api.operation.test.example;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.test.example.TestOperations.SourceOffsetProvider;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest.TestRun;

@RunWith(Parameterized.class)
public class TestOperationsTest {

    private static final TestSourceOffsetProvider PROVIDER = new TestSourceOffsetProvider();

    public static class TestSourceOffsetProvider implements SourceOffsetProvider {

        public int currentIndex(Object source) {
            return 0;
        }
    }

    @Parameter // first data value (0) is default
    public /* NOT private */ TestOperationsBuilder builder;

    @Parameters(name = "{0}")
    public static List<TestOperationsBuilder> data() {
        return Arrays.asList(TestOperationsBuilder.createBytecodeBuilder(), TestOperationsBuilder.createASTBuilder());
    }

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

    private static void parseAdd(TestOperationsBuilder b) {
        // simple test:
        // function foo(a, b) {
        // return (a + b);
        // }
        b.startBody();
        b.startSource("test", PROVIDER);
        b.startReturn();
        b.startAdd();
        b.pushLocalRead(0);
        b.pushLocalRead(1);
        b.endAdd();
        b.endReturn();
        b.endSource();
        b.endBody(2, 2);
    }

    private static void parseMax(TestOperationsBuilder b) {
        // control flow test:
        // function max(a, b) {
        // if (a > b) {
        // return a;
        // } else {
        // return b;
        // }
        b.startBody();
        b.startIf();

        b.startGreaterThan();
        b.pushLocalRead(0); // a
        b.pushLocalRead(1); // b
        b.endGreaterThan();

        b.startReturn();
        b.pushLocalRead(0); // a
        b.endReturn();

        b.startReturn();
        b.pushLocalRead(1); // b
        b.endReturn();

        b.endIf();
        b.endBody(2, 2);
    }

    @Test
    public void testAdd() {
        runTest(TestOperationsTest::parseAdd, 42, 20, 22);
    }

    @Test
    public void testMax() {
        runTest(TestOperationsTest::parseMax, 42, 42, 13);
        runTest(TestOperationsTest::parseMax, 42, 13, 42);
    }

    @Test
    public void testSumLoop() {
        runTest(TestOperationsTest::parseSumLoop, 42, 10);
    }

    private void runTest(Consumer<TestOperationsBuilder> parse, Object expectedResult, Object... args) {
        TestOperationsBuilder b = builder;
        parse.accept(b);
        OperationsNode executable = b.build();
        b.reset();
        System.out.println(executable);
        CallTarget target = Truffle.getRuntime().createCallTarget(new OperationsRootNode(executable));
        Object result = target.call(args);
        Assert.assertEquals(expectedResult, result);
    }

    private static void parseSumLoop(TestOperationsBuilder b) {
        // control flow test:
        // function sum(length) {
        // sum = 0;
        // i = 0;
        // while (i < length) {
        // sum += i;
        // }

        b.startBody();
        b.startLocalWrite(1);
        b.pushConstantInt(0);
        b.endLocalWrite();

        b.startLocalWrite(2);
        b.pushConstantInt(0);
        b.endLocalWrite();

        b.startWhile();

        b.startLessThan();
        b.pushLocalRead(1); // i
        b.pushLocalRead(0); // length
        b.endLessThan();

        b.startLocalWrite(2);
        b.startAdd();
        b.pushLocalRead(2);
        b.pushLocalRead(1);
        b.endAdd();
        b.endLocalWrite();

        b.endWhile();
        b.endBody(3, 1);
    }

}
