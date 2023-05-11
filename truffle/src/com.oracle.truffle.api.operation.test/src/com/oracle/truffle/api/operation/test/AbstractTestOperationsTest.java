package com.oracle.truffle.api.operation.test;

import java.util.List;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.operation.OperationParser;

@RunWith(Parameterized.class)
public abstract class AbstractTestOperationsTest {
    protected static final TestOperationsLanguage LANGUAGE = null;

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Parameters(name = "{0}")
    public static List<Class<? extends TestOperations>> getInterpreterClasses() {
        return TestOperationsCommon.allInterpreters();
    }

    @Parameter(0) public Class<? extends TestOperations> interpreterClass;

    public <T extends TestOperationsBuilder> RootCallTarget parse(String rootName, OperationParser<T> builder) {
        return TestOperationsCommon.parse(interpreterClass, rootName, builder);
    }

    protected static void emitReturn(TestOperationsBuilder b, long value) {
        b.beginReturn();
        b.emitLoadConstant(value);
        b.endReturn();
    }

    protected static void emitAppend(TestOperationsBuilder b, long value) {
        b.beginAppenderOperation();
        b.emitLoadArgument(0);
        b.emitLoadConstant(value);
        b.endAppenderOperation();
    }

    protected static void emitThrow(TestOperationsBuilder b, long value) {
        b.beginThrowOperation();
        b.emitLoadConstant(value);
        b.endThrowOperation();
    }

}
