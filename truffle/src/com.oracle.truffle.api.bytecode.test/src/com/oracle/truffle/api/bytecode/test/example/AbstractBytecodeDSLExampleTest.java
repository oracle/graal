package com.oracle.truffle.api.bytecode.test.example;

import java.util.List;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeParser;

@RunWith(Parameterized.class)
public abstract class AbstractBytecodeDSLExampleTest {
    protected static final BytecodeDSLExampleLanguage LANGUAGE = null;

    @Rule public ExpectedException thrown = ExpectedException.none();

    @Parameters(name = "{0}")
    public static List<Class<? extends BytecodeDSLExample>> getInterpreterClasses() {
        return BytecodeDSLExampleCommon.allInterpreters();
    }

    @Parameter(0) public Class<? extends BytecodeDSLExample> interpreterClass;

    public <T extends BytecodeDSLExampleBuilder> RootCallTarget parse(String rootName, BytecodeParser<T> builder) {
        return BytecodeDSLExampleCommon.parse(interpreterClass, rootName, builder);
    }

    protected static void emitReturn(BytecodeDSLExampleBuilder b, long value) {
        b.beginReturn();
        b.emitLoadConstant(value);
        b.endReturn();
    }

    protected static void emitAppend(BytecodeDSLExampleBuilder b, long value) {
        b.beginAppenderOperation();
        b.emitLoadArgument(0);
        b.emitLoadConstant(value);
        b.endAppenderOperation();
    }

    protected static void emitThrow(BytecodeDSLExampleBuilder b, long value) {
        b.beginThrowOperation();
        b.emitLoadConstant(value);
        b.endThrowOperation();
    }

}
