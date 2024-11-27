/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.PrologEpilogBytecodeNode.MyException;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Parameterized.class)
public class PrologEpilogTest extends AbstractInstructionTest {
    @Parameters(name = "{0}")
    public static List<Object[]> getParameters() {
        return List.of(new Object[]{false}, new Object[]{true});
    }

    @Parameter public Boolean testSerialize;

    static final byte INT_CODE = 0;
    static final byte STRING_CODE = 1;
    static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof Integer i) {
                buffer.writeByte(INT_CODE);
                buffer.writeInt(i);
            } else if (object instanceof String s) {
                buffer.writeByte(STRING_CODE);
                buffer.writeUTF(s);
            } else {
                throw new AssertionError("only ints are supported.");
            }
        }
    };

    static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            byte code = buffer.readByte();
            switch (code) {
                case INT_CODE:
                    return buffer.readInt();
                case STRING_CODE:
                    return buffer.readUTF();
                default:
                    throw new AssertionError("bad code " + code);
            }
        }
    };

    public PrologEpilogBytecodeNode parseNode(BytecodeParser<PrologEpilogBytecodeNodeGen.Builder> builder) {
        BytecodeRootNodes<PrologEpilogBytecodeNode> nodes;
        if (testSerialize) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                PrologEpilogBytecodeNodeGen.serialize(new DataOutputStream(output), SERIALIZER, builder);
                Supplier<DataInput> input = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(output.toByteArray()));
                nodes = PrologEpilogBytecodeNodeGen.deserialize(null, BytecodeConfig.DEFAULT, input, DESERIALIZER);
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        } else {
            nodes = PrologEpilogBytecodeNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        }
        return nodes.getNode(0);
    }

    @Test
    public void testSimpleReturn() {
        // return arg0
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitReadArgument();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call(42));
        assertEquals(42, root.argument);
        assertEquals(42, root.returnValue);
        assertNull(root.thrownValue);
    }

    @Test
    public void testEarlyReturn() {
        // if (arg0) return 42
        // return 123
        PrologEpilogBytecodeNode root = parseNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginBlock();
                b.beginIfThen();
                    b.emitLoadArgument(0);

                    b.beginReturn();
                        b.emitLoadConstant(42);
                    b.endReturn();
                b.endIfThen();

                b.beginReturn();
                    b.emitLoadConstant(123);
                b.endReturn();

            b.endBlock();
            b.endRoot();
            // @formatter:on
        });

        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(true, root.argument);
        assertEquals(42, root.returnValue);
        assertNull(root.thrownValue);

        assertEquals(123, root.getCallTarget().call(false));
        assertEquals(false, root.argument);
        assertEquals(123, root.returnValue);
        assertNull(root.thrownValue);
    }

    @Test
    public void testImplicitReturn() {
        // if (arg0) return 42
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginIfThen();
            b.emitLoadArgument(0);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endIfThen();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(true, root.argument);
        assertEquals(42, root.returnValue);
        assertNull(root.thrownValue);

        assertEquals(null, root.getCallTarget().call(false));
        assertEquals(false, root.argument);
        assertNull(root.returnValue);
        assertNull(root.thrownValue);
    }

    @Test
    public void testTryFinally() {
        // @formatter:off
        // try {
        //    if (arg0) return 42 else throw "oops"
        // } finally {
        //    return -1
        // }
        // @formatter:on
        PrologEpilogBytecodeNode root = parseNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginTryFinally(() -> {
                b.beginReturn();
                    b.emitLoadConstant(-1);
                b.endReturn();
            });
                b.beginIfThenElse();
                    b.emitLoadArgument(0);

                    b.beginReturn();
                        b.emitLoadConstant(42);
                    b.endReturn();

                    b.beginThrowException();
                        b.emitLoadConstant("oops");
                    b.endThrowException();
                b.endIfThenElse();
            b.endTryFinally();
            b.endRoot();
            // @formatter:on
        });

        assertEquals(-1, root.getCallTarget().call(true));
        assertEquals(true, root.argument);
        assertEquals(-1, root.returnValue);
        assertNull(root.thrownValue);

        assertEquals(-1, root.getCallTarget().call(false));
        assertEquals(false, root.argument);
        assertEquals(-1, root.returnValue);
        assertNull(root.thrownValue);
    }

    @Test
    public void testTryCatch() {
        // @formatter:off
        // try {
        //    if (arg0) return 42 else throw "oops"
        // } catch (ex) {
        //    return -1
        // }
        // @formatter:on
        PrologEpilogBytecodeNode root = parseNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginTryCatch();
                b.beginIfThenElse();
                    b.emitLoadArgument(0);

                    b.beginReturn();
                        b.emitLoadConstant(42);
                    b.endReturn();

                    b.beginThrowException();
                        b.emitLoadConstant("oops");
                    b.endThrowException();
                b.endIfThenElse();

                b.beginReturn();
                    b.emitLoadConstant(-1);
                b.endReturn();
            b.endTryCatch();
            b.endRoot();
            // @formatter:on
        });

        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(true, root.argument);
        assertEquals(42, root.returnValue);
        assertNull(root.thrownValue);

        assertEquals(-1, root.getCallTarget().call(false));
        assertEquals(false, root.argument);
        assertEquals(-1, root.returnValue);
        assertNull(root.thrownValue);
    }

    @Test
    public void testBoxingEliminationInEpilog() {
        PrologEpilogBytecodeNode root = parseNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();
            b.endRoot();
            // @formatter:on
        });

        assertQuickenings(root, 0, 0);
        assertEquals(42, root.getCallTarget().call(42));
        assertQuickenings(root, 2, 1);
        assertEquals("foo", root.getCallTarget().call("foo"));
        assertQuickenings(root, 5, 2);
        assertEquals(42, root.getCallTarget().call(42));
        assertQuickenings(root, 5, 2); // no change
    }

    @Test
    public void testSimpleThrow() {
        // throw "something went wrong"
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginThrowException();
            b.emitLoadConstant("something went wrong");
            b.endThrowException();
            b.endRoot();
        });

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (MyException ex) {
        }

        assertEquals(42, root.argument);
        assertNull(root.returnValue);
        assertEquals("something went wrong", root.thrownValue);
    }

    @Test
    public void testThrowInReturn() {
        // return { throw "something went wrong"; arg0 }
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginBlock();
            b.beginThrowException();
            b.emitLoadConstant("something went wrong");
            b.endThrowException();
            b.emitLoadArgument(0);
            b.endBlock();
            b.endReturn();
            b.endRoot();
        });

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (MyException ex) {
        }

        assertEquals(42, root.argument);
        assertNull(root.returnValue);
        assertEquals("something went wrong", root.thrownValue);
    }

    @Test
    public void testThrowInternalErrorInProlog() {
        // internal exceptions in the prolog just bubble up
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();
            b.endRoot();
        });
        root.throwInProlog = new RuntimeException("internal error");

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (RuntimeException ex) {
        }

        assertNull(root.argument);
        assertNull(root.returnValue);
        assertNull(root.thrownValue);
        assertTrue(root.internalExceptionIntercepted);
    }

    @Test
    public void testThrowInternalErrorInReturnEpilog() {
        // internal exceptions in the return epilog just bubble up
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();
            b.endRoot();
        });
        root.throwInReturnEpilog = new RuntimeException("internal error");

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (RuntimeException ex) {
        }

        assertEquals(42, root.argument);
        assertNull(root.returnValue);
        assertNull(root.thrownValue);
        assertTrue(root.internalExceptionIntercepted);
    }

    @Test
    public void testThrowInternalErrorInExceptionalEpilog() {
        // internal exceptions in the exceptional epilog just bubble up
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginThrowException();
            b.emitLoadConstant("something went wrong");
            b.endThrowException();
            b.endRoot();
        });
        root.throwInExceptionalEpilog = new RuntimeException("internal error");

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (RuntimeException ex) {
        }

        assertEquals(42, root.argument);
        assertNull(root.returnValue);
        assertNull(root.thrownValue);
        assertTrue(root.internalExceptionIntercepted);
    }

    @Test
    public void testThrowTruffleExceptionInProlog() {
        // truffle exceptions in the prolog are handled by the exceptional epilog
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();
            b.endRoot();
        });
        root.throwInProlog = new MyException("truffle exception");

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (RuntimeException ex) {
        }

        assertNull(root.argument);
        assertNull(root.returnValue);
        assertEquals("truffle exception", root.thrownValue);
        assertTrue(!root.internalExceptionIntercepted);
    }

    @Test
    public void testThrowTruffleExceptionInReturnEpilog() {
        // truffle exceptions in the return epilog are handled by the exceptional epilog
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadArgument(0);
            b.endReturn();
            b.endRoot();
        });
        root.throwInReturnEpilog = new MyException("truffle exception");

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (RuntimeException ex) {
        }

        assertEquals(42, root.argument);
        assertNull(root.returnValue);
        assertEquals("truffle exception", root.thrownValue);
        assertTrue(!root.internalExceptionIntercepted);
    }

    @Test
    public void testThrowTruffleExceptionInExceptionalEpilog() {
        // truffle exceptions in the exceptional epilog just bubble up
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot();
            b.beginThrowException();
            b.emitLoadConstant("something went wrong");
            b.endThrowException();
            b.endRoot();
        });
        root.throwInExceptionalEpilog = new MyException("truffle exception");

        try {
            root.getCallTarget().call(42);
            fail("exception expected");
        } catch (RuntimeException ex) {
        }

        assertEquals(42, root.argument);
        assertNull(root.returnValue);
        assertNull(root.thrownValue);
        assertTrue(!root.internalExceptionIntercepted);
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true, boxingEliminationTypes = {int.class})
abstract class PrologEpilogBytecodeNode extends DebugBytecodeRootNode implements BytecodeRootNode {
    transient Object argument = null;
    transient Object returnValue = null;
    transient Object thrownValue = null;

    transient RuntimeException throwInProlog = null;
    transient RuntimeException throwInReturnEpilog = null;
    transient RuntimeException throwInExceptionalEpilog = null;
    transient boolean internalExceptionIntercepted = false;

    protected PrologEpilogBytecodeNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Prolog
    public static final class StoreFirstArg {
        @Specialization
        public static void doStoreFirstArg(VirtualFrame frame, @Bind PrologEpilogBytecodeNode root) {
            if (root.throwInProlog != null) {
                throw root.throwInProlog;
            }
            root.argument = frame.getArguments()[0];
        }
    }

    @EpilogReturn
    public static final class StoreReturnValue {
        @Specialization
        public static int doStoreReturnValueBoxingEliminated(int returnValue, @Bind PrologEpilogBytecodeNode root) {
            if (root.throwInReturnEpilog != null) {
                throw root.throwInReturnEpilog;
            }
            root.returnValue = returnValue;
            return returnValue;
        }

        @Specialization
        public static Object doStoreReturnValue(Object returnValue, @Bind PrologEpilogBytecodeNode root) {
            if (root.throwInReturnEpilog != null) {
                throw root.throwInReturnEpilog;
            }
            root.returnValue = returnValue;
            return returnValue;
        }
    }

    @EpilogExceptional
    public static final class StoreExceptionalValue {
        @Specialization
        public static void doStoreExceptionalValue(AbstractTruffleException exception, @Bind PrologEpilogBytecodeNode root) {
            if (root.throwInExceptionalEpilog != null) {
                throw root.throwInExceptionalEpilog;
            }
            root.thrownValue = exception.getMessage();
        }
    }

    @Override
    public Throwable interceptInternalException(Throwable t, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) {
        internalExceptionIntercepted = true;
        return t;
    }

    @Operation
    public static final class ReadArgument {
        @Specialization
        public static Object doReadArgument(VirtualFrame frame) {
            return frame.getArguments()[0];
        }
    }

    @Operation
    public static final class NotNull {
        @Specialization
        public static boolean doObject(Object o) {
            return o != null;
        }
    }

    public static final class MyException extends AbstractTruffleException {

        private static final long serialVersionUID = 4290970234082022665L;

        MyException(String message) {
            super(message);
        }
    }

    @Operation
    public static final class ThrowException {

        @Specialization
        public static void doThrow(String message) {
            throw new MyException(message);
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class DuplicatePrologEpilogErrorNode extends RootNode implements BytecodeRootNode {
    protected DuplicatePrologEpilogErrorNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Prolog
    public static final class Prolog1 {
        @Specialization
        public static void doProlog() {
        }
    }

    @ExpectError("Prolog1 is already annotated with @Prolog. A Bytecode DSL class can only declare one prolog.")
    @Prolog
    public static final class Prolog2 {
        @Specialization
        public static void doProlog() {
        }
    }

    @EpilogReturn
    public static final class Epilog1 {
        @Specialization
        public static Object doEpilog(Object returnValue) {
            return returnValue;
        }
    }

    @ExpectError("Epilog1 is already annotated with @EpilogReturn. A Bytecode DSL class can only declare one return epilog.")
    @EpilogReturn
    public static final class Epilog2 {
        @Specialization
        public static Object doEpilog(Object returnValue) {
            return returnValue;
        }
    }

    @EpilogExceptional
    public static final class ExceptionalEpilog1 {
        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog(AbstractTruffleException ex) {
        }
    }

    @ExpectError("ExceptionalEpilog1 is already annotated with @EpilogExceptional. A Bytecode DSL class can only declare one exceptional epilog.")
    @EpilogExceptional
    public static final class ExceptionalEpilog2 {
        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog(AbstractTruffleException ex) {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadPrologErrorNode extends RootNode implements BytecodeRootNode {
    protected BadPrologErrorNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("A @Prolog operation cannot have any dynamic operands. Remove the operands to resolve this.")
    @Prolog
    public static final class BadProlog {
        @Specialization
        @SuppressWarnings("unused")
        public static void doProlog(int x) {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadPrologErrorNode2 extends RootNode implements BytecodeRootNode {
    protected BadPrologErrorNode2(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("A @Prolog operation cannot have a return value. Use void as the return type.")
    @Prolog
    public static final class BadProlog {
        @Specialization
        public static int doProlog() {
            return 42;
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadEpilogErrorNode extends RootNode implements BytecodeRootNode {
    protected BadEpilogErrorNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("An @EpilogReturn operation must have exactly one dynamic operand for the returned value. Update all specializations to take one operand to resolve this.")
    @EpilogReturn
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static int doEpilog(int x, int y) {
            return x;
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadEpilogErrorNode2 extends RootNode implements BytecodeRootNode {
    protected BadEpilogErrorNode2(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("An @EpilogReturn operation must have a return value. The result is returned from the root node instead of the original return value. Update all specializations to return a value to resolve this.")
    @EpilogReturn
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog(int x) {
        }

        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog2(String x) {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadExceptionalEpilogErrorNode1 extends RootNode implements BytecodeRootNode {
    protected BadExceptionalEpilogErrorNode1(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("An @EpilogExceptional operation must have exactly one dynamic operand for the exception. Update all specializations to take one operand to resolve this.")
    @EpilogExceptional
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog(Object x, Object y) {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadExceptionalEpilogErrorNode2 extends RootNode implements BytecodeRootNode {
    protected BadExceptionalEpilogErrorNode2(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError({
                    "The operand type for doObject must be AbstractTruffleException or a subclass.",
                    "The operand type for doRuntimeException must be AbstractTruffleException or a subclass.",
                    "The operand type for doString must be AbstractTruffleException or a subclass.",
                    "The operand type for doPrimitive must be AbstractTruffleException or a subclass."
    })
    @EpilogExceptional
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static void doObject(Object exception) {
        }

        @Specialization
        @SuppressWarnings("unused")
        public static void doRuntimeException(RuntimeException exception) {
        }

        @Specialization
        @SuppressWarnings("unused")
        public static void doString(String exception) {
        }

        @Specialization
        @SuppressWarnings("unused")
        public static void doPrimitive(int exception) {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadExceptionalEpilogErrorNode3 extends RootNode implements BytecodeRootNode {
    protected BadExceptionalEpilogErrorNode3(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("An @EpilogExceptional operation cannot have a return value. Use void as the return type.")
    @EpilogExceptional
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static int doObject(AbstractTruffleException exception) {
            return 42;
        }
    }
}
