package org.graalvm.compiler.truffle.test.operation;

import java.util.List;
import java.util.function.Supplier;

import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.test.TestOperationsLanguage;
import com.oracle.truffle.api.operation.test.TestOperations;
import com.oracle.truffle.api.operation.test.TestOperationsBuilder;
import com.oracle.truffle.api.operation.test.TestOperationsCommon;

import static com.oracle.truffle.api.operation.test.TestOperationsCommon.parseNode;

@RunWith(Parameterized.class)
public class OperationPartialEvaluationTest extends PartialEvaluationTest {
    // @formatter:off

    private static final TestOperationsLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends TestOperations>> getInterpreterClasses() {
        return TestOperationsCommon.allInterpreters();
    }

    @Parameter(0) public Class<? extends TestOperations> interpreterClass;

    private static Supplier<Object> supplier(Object result) {
        return () -> result;
    }

    private static <T extends TestOperationsBuilder> TestOperations parseNodeForPE(Class<? extends TestOperations> interpreterClass, String rootName, OperationParser<T> builder) {
        TestOperations result = parseNode(interpreterClass, rootName, builder);
        result.setBaselineInterpreterThreshold(0); // force interpreter to skip tier 0
        return result;
    }

    @Test
    public void testAddTwoConstants() {
        // return 20 + 22;

        TestOperations root = parseNodeForPE(interpreterClass, "addTwoConstants", b -> {
            b.beginRoot(LANGUAGE);

            b.beginReturn();
            b.beginAddOperation();
            b.emitLoadConstant(20L);
            b.emitLoadConstant(22L);
            b.endAddOperation();
            b.endReturn();

            b.endRoot();
        });

        assertPartialEvalEquals(supplier(42L), root);
    }

    @Test
    public void testAddThreeConstants() {
        // return 40 + 22 + - 20;

        TestOperations root = parseNodeForPE(interpreterClass, "addThreeConstants", b -> {
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

        assertPartialEvalEquals(supplier(42L), root);
    }

    @Test
    public void testSum() {
        // i = 0;
        // sum = 0;
        // while (i < 10) {
        //   i += 1;
        //   sum += i;
        // }
        // return sum

        long endValue = 10L;

        TestOperations root = parseNodeForPE(interpreterClass, "sum", b -> {
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

        assertPartialEvalEquals(supplier(endValue * (endValue + 1) / 2), root);
    }

    @Test
    public void testTryCatch() {
        // try {
        //   throw 1;
        // } catch x {
        //   return x + 1;
        // }
        // return 3;

        TestOperations root = parseNodeForPE(interpreterClass, "sum", b -> {
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

        assertPartialEvalEquals(supplier(2L), root);
    }

}
