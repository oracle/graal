package org.graalvm.compiler.truffle.test.bytecode;

import static com.oracle.truffle.api.bytecode.test.example.AbstractBytecodeDSLExampleTest.parseNode;

import java.util.List;
import java.util.function.Supplier;

import org.graalvm.compiler.truffle.test.PartialEvaluationTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExampleBuilder;
import com.oracle.truffle.api.bytecode.test.example.AbstractBytecodeDSLExampleTest;
import com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExample;
import com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExampleCommon;
import com.oracle.truffle.api.bytecode.test.example.BytecodeDSLExampleLanguage;

@RunWith(Parameterized.class)
public class BytecodeDSLPartialEvaluationTest extends PartialEvaluationTest {
    // @formatter:off

    private static final BytecodeDSLExampleLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends BytecodeDSLExample>> getInterpreterClasses() {
        return AbstractBytecodeDSLExampleTest.allInterpreters();
    }

    @Parameter(0) public Class<? extends BytecodeDSLExample> interpreterClass;

    private static Supplier<Object> supplier(Object result) {
        return () -> result;
    }

    private static <T extends BytecodeDSLExampleBuilder> BytecodeDSLExample parseNodeForPE(Class<? extends BytecodeDSLExample> interpreterClass, String rootName, BytecodeParser<T> builder) {
        BytecodeDSLExample result = parseNode(interpreterClass, false, rootName, builder);
        result.setUncachedInterpreterThreshold(0); // force interpreter to skip tier 0
        return result;
    }

    @Test
    public void testAddTwoConstants() {
        // return 20 + 22;

        BytecodeDSLExample root = parseNodeForPE(interpreterClass, "addTwoConstants", b -> {
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

        BytecodeDSLExample root = parseNodeForPE(interpreterClass, "addThreeConstants", b -> {
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

        BytecodeDSLExample root = parseNodeForPE(interpreterClass, "sum", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal i = b.createLocal();
            BytecodeLocal sum = b.createLocal();

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

        BytecodeDSLExample root = parseNodeForPE(interpreterClass, "sum", b -> {
            b.beginRoot(LANGUAGE);

            BytecodeLocal ex = b.createLocal();

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
