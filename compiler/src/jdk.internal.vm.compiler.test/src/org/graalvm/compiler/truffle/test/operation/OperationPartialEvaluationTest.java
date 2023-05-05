package org.graalvm.compiler.truffle.test.operation;

import java.util.function.Supplier;

import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Test;

import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.test.example.OperationTestLanguage;
import com.oracle.truffle.api.operation.test.example.TestOperations;
import com.oracle.truffle.api.operation.test.example.TestOperationsGen;

public class OperationPartialEvaluationTest extends PartialEvaluationTest {
    private static final OperationTestLanguage LANGUAGE = null;

    private static TestOperations parseNode(String rootName, OperationParser<TestOperationsGen.Builder> builder) {
        OperationNodes<TestOperations> nodes = TestOperationsGen.create(OperationConfig.DEFAULT, builder);
        TestOperations op = nodes.getNodes().get(nodes.getNodes().size() - 1);
        op.setName(rootName);
        return op;
    }

    private static Supplier<Object> supplier(Object result) {
        return () -> result;
    }

    // TODO: this is a hack to force the interpreter to tier 1. we should generate a version of the
    // interpreter without tier 0.
    private static void warmup(TestOperations root, Object... args) {
        for (int i = 0; i < 16; i++) {
            root.getCallTarget().call(args);
        }
    }

    @Test
    public void testAddTwoConstants() {
        // return 20 + 22;

        TestOperations root = parseNode("addTwoConstants", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        warmup(root);

        assertPartialEvalEquals(supplier(42L), root);
    }

    @Test
    public void testAddThreeConstants() {
        // return 40 + 22 + - 20;

        TestOperations root = parseNode("addThreeConstants", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();

            b.beginAddOperation();
            b.emitLoadConstant(40L);
            b.emitLoadConstant(22L);
            b.endAddOperation();

            b.emitLoadConstant(-20L);

            b.endAddOperation();

            b.endReturn();

            b.endRoot();
        });

        warmup(root);

        assertPartialEvalEquals(supplier(42L), root);
    }

    @Test
    public void testSum() {
        // i = 0;
        // sum = 0;
        // while (i < 10) {
        // i += 1;
        // sum += i;
        // }
        // return sum

        long endValue = 10L;

        TestOperations root = parseNode("sum", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal i = b.createLocal();
            OperationLocal sum = b.createLocal();

            b.beginStoreLocal(i);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginStoreLocal(sum);
            b.emitLoadConstant(0L);
            b.endStoreLocal();

            b.beginWhile();
            b.beginLessThanOperation();
            b.emitLoadLocal(i);
            b.emitLoadConstant(endValue);
            b.endLessThanOperation();

            b.beginBlock();

            b.beginStoreLocal(i);
            b.beginAddOperation();
            b.emitLoadLocal(i);
            b.emitLoadConstant(1L);
            b.endAddOperation();
            b.endStoreLocal();

            b.beginStoreLocal(sum);
            b.beginAddOperation();
            b.emitLoadLocal(sum);
            b.emitLoadLocal(i);
            b.endAddOperation();
            b.endStoreLocal();

            b.endBlock();

            b.endWhile();

            b.beginReturn();
            b.emitLoadLocal(sum);
            b.endReturn();

            b.endRoot();
        });

        warmup(root);

        assertPartialEvalEquals(supplier(endValue * (endValue + 1) / 2), root);
    }

    @Test
    public void testTryCatch() {
        // try {
        // throw 1;
        // } catch x {
        // return x + 1;
        // }
        // return 3;

        TestOperations root = parseNode("sum", b -> {
            b.beginRoot(LANGUAGE);

            OperationLocal ex = b.createLocal();

            b.beginTryCatch(ex);

            b.beginThrowOperation();
            b.emitLoadConstant(1L);
            b.endThrowOperation();

            b.beginReturn();
            b.beginAddOperation();

            b.beginReadExceptionOperation();
            b.emitLoadLocal(ex);
            b.endReadExceptionOperation();

            b.emitLoadConstant(1L);

            b.endAddOperation();
            b.endReturn();

            b.endTryCatch();

            b.beginReturn();
            b.emitLoadConstant(3L);
            b.endReturn();

            b.endRoot();
        });

        warmup(root);

        assertPartialEvalEquals(supplier(2L), root);
    }

}
