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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.Epilog;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

@RunWith(Parameterized.class)
public class PrologEpilogTest {
    @Parameters(name = "{0}")
    public static List<Object[]> getParameters() {
        return List.of(new Object[]{false}, new Object[]{true});
    }

    @Parameter public Boolean testSerialize;

    static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof Integer i) {
                buffer.writeInt(i);
            } else {
                throw new AssertionError("only ints are supported.");
            }
        }
    };

    static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            return buffer.readInt();
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
            nodes = PrologEpilogBytecodeNodeGen.create(BytecodeConfig.DEFAULT, builder);
        }
        return nodes.getNode(0);
    }

    @Test
    public void testPrologEpilogSimpleReturn() {
        PrologEpilogBytecodeNode root = parseNode(b -> {
            b.beginRoot(null);
            b.beginReturn();
            b.emitReadArgument();
            b.endReturn();
            b.endRoot();
        });
        Object[] refs = new Object[2];
        root.setRefs(refs);

        assertEquals(42, root.getCallTarget().call(42));
        assertEquals(42, refs[0]);
        assertEquals(42, refs[1]);
    }

    @Test
    public void testPrologEpilogEarlyReturn() {
        PrologEpilogBytecodeNode root = parseNode(b -> {
            // @formatter:off
            b.beginRoot(null);
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
        Object[] refs = new Object[2];
        root.setRefs(refs);

        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(true, refs[0]);
        assertEquals(42, refs[1]);

        assertEquals(123, root.getCallTarget().call(false));
        assertEquals(false, refs[0]);
        assertEquals(123, refs[1]);
    }

    @Test
    public void testPrologEpilogFinallyTry() {
        PrologEpilogBytecodeNode root = parseNode(b -> {
            // @formatter:off
            b.beginRoot(null);
            BytecodeLocal exception = b.createLocal();
            b.beginFinallyTry(exception);
                b.beginIfThen();
                    b.beginNotNull();
                        b.emitLoadLocal(exception);
                    b.endNotNull();

                    b.beginReturn();
                        b.emitLoadConstant(-1);
                    b.endReturn();
                b.endIfThen();

                b.beginIfThenElse();
                    b.emitLoadArgument(0);

                    b.beginReturn();
                        b.emitLoadConstant(42);
                    b.endReturn();

                    b.emitThrowException();
                b.endIfThenElse();
            b.endFinallyTry();
            b.endRoot();
            // @formatter:on
        });
        Object[] refs = new Object[2];
        root.setRefs(refs);

        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(true, refs[0]);
        assertEquals(42, refs[1]);

        assertEquals(-1, root.getCallTarget().call(false));
        assertEquals(false, refs[0]);
        assertEquals(-1, refs[1]);
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true)
abstract class PrologEpilogBytecodeNode extends RootNode implements BytecodeRootNode {
    // Used to validate whether prolog/epilog get called.
    private transient Object[] refs;

    protected PrologEpilogBytecodeNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    void setRefs(Object[] refs) {
        assert refs.length == 2;
        this.refs = refs;
    }

    @Prolog
    public static final class StoreFirstArg {
        @Specialization
        public static void doStoreFirstArg(VirtualFrame frame, @Bind("$root") PrologEpilogBytecodeNode root) {
            root.refs[0] = frame.getArguments()[0];
        }
    }

    @Epilog
    public static final class StoreReturnValue {
        @Specialization
        public static void doStoreReturnValue(Object returnValue, Object throwable,
                        @Bind("$root") PrologEpilogBytecodeNode root) {
            if (throwable != null) {
                if (throwable instanceof Exception ex) {
                    root.refs[1] = ex.getMessage();
                }
            } else {
                root.refs[1] = returnValue;
            }
        }
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
        public static void doThrow() {
            throw new MyException("error");
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class DuplicatePrologEpilogErrorNode extends RootNode implements BytecodeRootNode {
    protected DuplicatePrologEpilogErrorNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
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

    @Epilog
    public static final class Epilog1 {
        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog(Object returnValue, Object exceptionValue) {
        }
    }

    @ExpectError("Epilog1 is already annotated with @Epilog. A Bytecode DSL class can only declare one epilog.")
    @Epilog
    public static final class Epilog2 {
        @Specialization
        public static void doEpilog() {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadPrologErrorNode extends RootNode implements BytecodeRootNode {
    protected BadPrologErrorNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("A @Prolog annotated operation cannot have any operands. Remove the operands to resolve this.")
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
    protected BadPrologErrorNode2(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("A @Prolog annotated operation cannot have a return value. Use void as the return type.")
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
    protected BadEpilogErrorNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("An @Epilog annotated operation must have exactly two operands for a returned value and an exception. Update all specializations to take two operands to resolve this.")
    @Epilog
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog(int x) {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadEpilogErrorNode2 extends RootNode implements BytecodeRootNode {
    protected BadEpilogErrorNode2(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("An @Epilog annotated operation must have a specialization that takes Object for both operands.")
    @Epilog
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog(int x, Object exception) {
        }

        @Specialization
        @SuppressWarnings("unused")
        public static void doEpilog2(Object x, String exception) {
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class)
abstract class BadEpilogErrorNode3 extends RootNode implements BytecodeRootNode {
    protected BadEpilogErrorNode3(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @ExpectError("An @Epilog annotated operation cannot have a return value. Use void as the return type.")
    @Epilog
    public static final class BadEpilog {
        @Specialization
        @SuppressWarnings("unused")
        public static int doEpilog(Object x, Object exception) {
            return 42;
        }
    }
}
