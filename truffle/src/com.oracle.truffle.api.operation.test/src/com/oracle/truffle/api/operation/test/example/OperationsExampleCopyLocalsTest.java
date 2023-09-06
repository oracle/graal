package com.oracle.truffle.api.operation.test.example;

import static com.oracle.truffle.api.operation.test.example.OperationsExampleCommon.parseNode;
import static org.junit.Assert.assertArrayEquals;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.frame.Frame;

@RunWith(Parameterized.class)
public class OperationsExampleCopyLocalsTest {
    protected static final OperationsExampleLanguage LANGUAGE = null;

    @Parameters(name = "{0}")
    public static List<Class<? extends OperationsExample>> getInterpreterClasses() {
        return OperationsExampleCommon.allInterpreters();
    }

    @Parameter(0) public Class<? extends OperationsExample> interpreterClass;

    @Test
    public void testCopyAllLocals() {
        /**
         * @formatter:off
         * def foo(arg0) {
         *   local1 = 42L
         *   local2 = "abcd"
         *   local3 = true
         *   CopyLocalsToFrame(<frame>, null) // copy all locals
         * }
         * @formatter:on
         */

        OperationsExample foo = parseNode(interpreterClass, "foo", b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant("abcd");
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(true);
            b.endStoreLocal();

            b.beginReturn();
            b.beginCopyLocalsToFrame();
            b.emitLoadConstant(null);
            b.endCopyLocalsToFrame();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        Frame frame = (Frame) foo.getCallTarget().call();
        Object[] locals = foo.getLocals(frame);
        assertArrayEquals(new Object[]{42L, "abcd", true}, locals);
    }

    @Test
    public void testCopySomeLocals() {
        /**
         * @formatter:off
         * def foo(arg0) {
         *   local1 = 42L
         *   local2 = "abcd"
         *   local3 = true
         *   CopyLocalsToFrame(<frame>, 2) // copy all locals
         * }
         * @formatter:on
         */

        OperationsExample foo = parseNode(interpreterClass, "foo", b -> {
            b.beginRoot(LANGUAGE);

            b.beginBlock();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(42L);
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant("abcd");
            b.endStoreLocal();

            b.beginStoreLocal(b.createLocal());
            b.emitLoadConstant(true);
            b.endStoreLocal();

            b.beginReturn();
            b.beginCopyLocalsToFrame();
            b.emitLoadConstant(2);
            b.endCopyLocalsToFrame();
            b.endReturn();

            b.endBlock();

            b.endRoot();
        });

        Frame frame = (Frame) foo.getCallTarget().call();
        Object[] locals = foo.getLocals(frame);
        assertArrayEquals(new Object[]{42L, "abcd", null}, locals);
    }
}
