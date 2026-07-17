/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
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
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.InstructionDescriptor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.Return;
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
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

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
                Supplier<DataInput> input = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(output.toByteArray()));
                nodes = PrologEpilogBytecodeNodeGen.deserialize(null, BytecodeConfig.DEFAULT, input, DESERIALIZER);
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        } else {
            nodes = PrologEpilogBytecodeNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        }
        return nodes.getNode(0);
    }

    public EpilogReturnExceptionHandlerNode parseEpilogReturnExceptionHandlerNode(BytecodeParser<EpilogReturnExceptionHandlerNodeGen.Builder> builder) {
        BytecodeRootNodes<EpilogReturnExceptionHandlerNode> nodes;
        if (testSerialize) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                EpilogReturnExceptionHandlerNodeGen.serialize(new DataOutputStream(output), SERIALIZER, builder);
                Supplier<DataInput> input = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(output.toByteArray()));
                nodes = EpilogReturnExceptionHandlerNodeGen.deserialize(null, BytecodeConfig.DEFAULT, input, DESERIALIZER);
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        } else {
            nodes = EpilogReturnExceptionHandlerNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        }
        return nodes.getNode(0);
    }

    private CustomReturnEpilogBytecodeNode parseCustomReturnEpilogNode(BytecodeParser<CustomReturnEpilogBytecodeNodeGen.Builder> builder) {
        BytecodeRootNodes<CustomReturnEpilogBytecodeNode> nodes;
        if (testSerialize) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try {
                CustomReturnEpilogBytecodeNodeGen.serialize(new DataOutputStream(output), SERIALIZER, builder);
                Supplier<DataInput> input = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(output.toByteArray()));
                nodes = CustomReturnEpilogBytecodeNodeGen.deserialize(null, BytecodeConfig.DEFAULT, input, DESERIALIZER);
            } catch (IOException ex) {
                throw new AssertionError(ex);
            }
        } else {
            nodes = CustomReturnEpilogBytecodeNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        }
        return nodes.getNode(0);
    }

    private static ReturnEpilogTagRootNode parseReturnEpilogTagNode(BytecodeConfig config, BytecodeParser<ReturnEpilogTagRootNodeGen.Builder> parser) {
        BytecodeRootNodes<ReturnEpilogTagRootNode> nodes = ReturnEpilogTagRootNodeGen.create(null, config, parser);
        return nodes.getNode(0);
    }

    private static ReturnEpilogTagRootNode parseInstrumentedReturnEpilogTagNode(BytecodeConfig config, BytecodeParser<ReturnEpilogTagRootNodeGen.Builder> parser) {
        BytecodeRootNodes<ReturnEpilogTagRootNode> nodes = ReturnEpilogTagRootNodeGen.create(BytecodeDSLTestLanguage.REF.get(null), config, parser);
        return nodes.getNode(0);
    }

    private static ReturnEpilogNoRootTagRootNode parseReturnEpilogNoRootTagNode(BytecodeConfig config, BytecodeParser<ReturnEpilogNoRootTagRootNodeGen.Builder> parser) {
        BytecodeRootNodes<ReturnEpilogNoRootTagRootNode> nodes = ReturnEpilogNoRootTagRootNodeGen.create(null, config, parser);
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
    public void testTryFinallyReturnAtEnd() {
        // @formatter:off
        // try {
        //    return 42
        // } finally {
        //    if (arg0) throw "oops"
        // }
        // @formatter:on
        PrologEpilogBytecodeNode root = parseNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginTryFinally(() -> {
                b.beginIfThen();
                    b.emitLoadArgument(0);
                    b.beginThrowException();
                        b.emitLoadConstant("oops");
                    b.endThrowException();
                b.endIfThen();
            });
                b.beginReturn();
                    b.emitLoadConstant(42);
                b.endReturn();
            b.endTryFinally();
            b.endRoot();
            // @formatter:on
        });

        assertEquals(42, root.getCallTarget().call(false));
        assertEquals(false, root.argument);
        assertEquals(42, root.returnValue);
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
    public void testDuplicateQuickeningForEpilogVariants() {
        List<String> instructionNames = DuplicateQuickeningEpilogRootNodeGen.BYTECODE.getInstructionDescriptors().stream().map(InstructionDescriptor::getName).toList();
        assertTrue(instructionNames.contains("c.StoreReturnValue_offset1$Object"));
        assertTrue(instructionNames.contains("c.StoreReturnValue_offset2$Object"));
    }

    @Test
    public void testCustomReturnEpilogUsesResultOperand() {
        CustomReturnEpilogBytecodeNode firstOperandRoot = parseCustomReturnEpilogNode(b -> {
            b.beginRoot();
            b.beginFirstOperandCustomReturn();
            b.emitLoadConstant("left");
            b.emitLoadConstant("right");
            b.endFirstOperandCustomReturn();
            b.endRoot();
        });

        assertEquals("epilog(left):right", firstOperandRoot.getCallTarget().call());
        assertEquals("left", firstOperandRoot.returnValue);
        assertInstructions(firstOperandRoot,
                        "load.constant",
                        "load.constant",
                        "c.StoreReturnValue_offset2",
                        "c.FirstOperandCustomReturn");

        CustomReturnEpilogBytecodeNode secondOperandRoot = parseCustomReturnEpilogNode(b -> {
            b.beginRoot();
            b.beginSecondOperandCustomReturn();
            b.emitLoadConstant("left");
            b.emitLoadConstant("right");
            b.endSecondOperandCustomReturn();
            b.endRoot();
        });

        assertEquals("left:epilog(right)", secondOperandRoot.getCallTarget().call());
        assertEquals("right", secondOperandRoot.returnValue);
        assertInstructions(secondOperandRoot,
                        "load.constant",
                        "load.constant",
                        "c.StoreReturnValue_offset1",
                        "c.SecondOperandCustomReturn");
    }

    @Test
    public void testCustomReturnEpilogResultOperandQuickenings() {
        CustomReturnEpilogBytecodeNode firstOperandRoot = parseCustomReturnEpilogNode(b -> {
            b.beginRoot();
            b.beginFirstOperandCustomReturn();
            b.emitLoadArgument(0);
            b.emitLoadConstant("right");
            b.endFirstOperandCustomReturn();
            b.endRoot();
        });

        assertEquals("42:right", firstOperandRoot.getCallTarget().call(42));
        assertEquals(42, firstOperandRoot.returnValue);
        assertInstructions(firstOperandRoot,
                        "load.argument$Int",
                        "load.constant",
                        "c.StoreReturnValue_offset2$StoreReturnValueBoxingEliminated$unboxed",
                        "c.FirstOperandCustomReturn$IntReturn");
        assertEquals("epilog(left):right", firstOperandRoot.getCallTarget().call("left"));
        assertEquals("left", firstOperandRoot.returnValue);
        assertInstructions(firstOperandRoot,
                        "load.argument",
                        "load.constant",
                        "c.StoreReturnValue_offset2",
                        "c.FirstOperandCustomReturn");

        CustomReturnEpilogBytecodeNode secondOperandRoot = parseCustomReturnEpilogNode(b -> {
            b.beginRoot();
            b.beginSecondOperandCustomReturn();
            b.emitLoadConstant("left");
            b.emitLoadArgument(0);
            b.endSecondOperandCustomReturn();
            b.endRoot();
        });

        assertEquals("left:42", secondOperandRoot.getCallTarget().call(42));
        assertEquals(42, secondOperandRoot.returnValue);
        assertInstructions(secondOperandRoot,
                        "load.constant",
                        "load.argument$Int",
                        "c.StoreReturnValue_offset1$StoreReturnValueBoxingEliminated$unboxed",
                        "c.SecondOperandCustomReturn$IntReturn");
        assertEquals("left:epilog(right)", secondOperandRoot.getCallTarget().call("right"));
        assertEquals("right", secondOperandRoot.returnValue);
        assertInstructions(secondOperandRoot,
                        "load.constant",
                        "load.argument",
                        "c.StoreReturnValue_offset1",
                        "c.SecondOperandCustomReturn");
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

    @Test
    public void testReturnEpilogExceptionNotHandledByTryCatch() {
        EpilogReturnExceptionHandlerNode root = parseEpilogReturnExceptionHandlerNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginTryCatch();
                b.beginReturn();
                    b.emitLoadConstant(42);
                b.endReturn();

                b.beginReturn();
                    b.emitLoadConstant(123);
                b.endReturn();
            b.endTryCatch();
            b.endRoot();
            // @formatter:on
        });

        try {
            root.getCallTarget().call();
            fail("exception expected");
        } catch (MyException ex) {
            assertEquals("return epilog", ex.getMessage());
        }
        assertEquals(1, root.throwInReturnCount);
    }

    @Test
    public void testReturnEpilogExceptionNotHandledByTryFinally() {
        EpilogReturnExceptionHandlerNode root = parseEpilogReturnExceptionHandlerNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginTryFinally(() -> b.emitIncrementHandlerCount());
                b.beginReturn();
                    b.emitLoadConstant(42);
                b.endReturn();
            b.endTryFinally();
            b.endRoot();
            // @formatter:on
        });

        try {
            root.getCallTarget().call();
            fail("exception expected");
        } catch (MyException ex) {
            assertEquals("return epilog", ex.getMessage());
        }
        assertEquals(1, root.throwInReturnCount);
        assertEquals(1, root.handlerCount);
    }

    @Test
    public void testReturnEpilogExceptionNotHandledByTryCatchOtherwise() {
        EpilogReturnExceptionHandlerNode root = parseEpilogReturnExceptionHandlerNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> b.emitIncrementHandlerCount());
                b.beginReturn();
                    b.emitLoadConstant(42);
                b.endReturn();

                b.beginReturn();
                    b.emitLoadConstant(123);
                b.endReturn();
            b.endTryCatchOtherwise();
            b.endRoot();
            // @formatter:on
        });

        try {
            root.getCallTarget().call();
            fail("exception expected");
        } catch (MyException ex) {
            assertEquals("return epilog", ex.getMessage());
        }
        assertEquals(1, root.throwInReturnCount);
        assertEquals(1, root.handlerCount);
    }

    @Test
    public void testReturnEpilogExceptionNotHandledByNestedTryCatch() {
        EpilogReturnExceptionHandlerNode root = parseEpilogReturnExceptionHandlerNode(b -> {
            // @formatter:off
            b.beginRoot();
            b.beginTryCatch();
                b.beginTryCatch();
                    b.beginReturn();
                        b.emitLoadConstant(42);
                    b.endReturn();

                    b.beginReturn();
                        b.emitLoadConstant(123);
                    b.endReturn();
                b.endTryCatch();

                b.beginReturn();
                    b.emitLoadConstant(456);
                b.endReturn();
            b.endTryCatch();
            b.endRoot();
            // @formatter:on
        });

        try {
            root.getCallTarget().call();
            fail("exception expected");
        } catch (MyException ex) {
            assertEquals("return epilog", ex.getMessage());
        }
        assertEquals(1, root.throwInReturnCount);
    }

    @Test
    public void testReturnEpilogExceptionObservedByRootTag() {
        try (Context context = Context.create(BytecodeDSLTestLanguage.ID)) {
            context.initialize(BytecodeDSLTestLanguage.ID);
            context.enter();
            Instrumenter instrumenter = context.getEngine().getInstruments().get(TagTest.TagTestInstrumentation.ID).lookup(Instrumenter.class);
            ReturnEpilogTagRootNode node = parseInstrumentedReturnEpilogTagNode(BytecodeConfig.DEFAULT, b -> {
                b.beginRoot();
                b.beginReturn();
                b.emitLoadConstant(42);
                b.endReturn();
                b.endRoot();
            });
            node.throwInReturnEpilog = new MyException("return epilog");

            List<TagTest.Event> events = TagTest.attachEventListener(instrumenter, SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build());

            assertFails(() -> node.getCallTarget().call(), MyException.class);
            assertEquals(2, events.size());
            assertEquals(TagTest.EventKind.ENTER, events.get(0).kind());
            assertEquals(List.of(RootTag.class), events.get(0).tags());
            assertEquals(TagTest.EventKind.EXCEPTIONAL, events.get(1).kind());
            assertEquals(List.of(RootTag.class), events.get(1).tags());
            assertEquals(MyException.class, events.get(1).value().getClass());
        }
    }

    @Test
    public void testReturnEpilogExceptionNotObservedByOtherTags() {
        try (Context context = Context.create(BytecodeDSLTestLanguage.ID)) {
            context.initialize(BytecodeDSLTestLanguage.ID);
            context.enter();
            Instrumenter instrumenter = context.getEngine().getInstruments().get(TagTest.TagTestInstrumentation.ID).lookup(Instrumenter.class);
            ReturnEpilogTagRootNode node = parseInstrumentedReturnEpilogTagNode(BytecodeConfig.DEFAULT, b -> {
                // @formatter:off
                b.beginRoot();
                b.beginTag(ExpressionTag.class);
                    b.beginReturn();
                        b.emitLoadConstant(42);
                    b.endReturn();
                b.endTag(ExpressionTag.class);
                b.endRoot();
                // @formatter:on
            });
            node.throwInReturnEpilog = new MyException("return epilog");

            List<TagTest.Event> events = TagTest.attachEventListener(instrumenter, SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class, StandardTags.ExpressionTag.class).build());

            assertFails(() -> node.getCallTarget().call(), MyException.class);
            assertEquals(4, events.size());
            assertEquals(TagTest.EventKind.ENTER, events.get(0).kind());
            assertEquals(List.of(RootBodyTag.class), events.get(0).tags());
            assertEquals(TagTest.EventKind.ENTER, events.get(1).kind());
            assertEquals(List.of(ExpressionTag.class), events.get(1).tags());
            assertEquals(TagTest.EventKind.RETURN_VALUE, events.get(2).kind());
            assertEquals(List.of(ExpressionTag.class), events.get(2).tags());
            assertEquals(TagTest.EventKind.RETURN_VALUE, events.get(3).kind());
            assertEquals(List.of(RootBodyTag.class), events.get(3).tags());
        }
    }

    @Test
    public void testReturnEpilogSource() {
        Source s = Source.newBuilder("test", "12345678", "name").build();
        ReturnEpilogTagRootNode node = parseReturnEpilogTagNode(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(s);
            b.beginSourceSection(0, 8);
            b.beginRoot();
            b.beginSourceSection(2, 4);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endSourceSection();
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        Instruction epilog = findInstruction(bytecode, "c.LeaveValue");

        SourceSection[] sections = epilog.getSourceSections();
        assertEquals(2, sections.length);
        assertSourceSection(2, 4, sections[0]);
        assertSourceSection(0, 8, sections[1]);

        node.getRootNodes().update(ReturnEpilogTagRootNodeGen.BYTECODE.newConfigBuilder().addTag(RootTag.class).build());

        bytecode = node.getBytecodeNode();
        epilog = findInstruction(bytecode, "c.LeaveValue");
        Instruction rootLeave = findInstructionAfter(bytecode, "tag.leave", epilog.getBytecodeIndex());

        sections = epilog.getSourceSections();
        assertEquals(2, sections.length);
        assertSourceSection(2, 4, sections[0]);
        assertSourceSection(0, 8, sections[1]);

        SourceSection[] rootLeaveSections = rootLeave.getSourceSections();
        assertFalse(containsSourceSection(rootLeaveSections, 2, 4));
    }

    @Test
    public void testReturnEpilogSuffixSource() {
        Source s = Source.newBuilder("test", "12345678", "name").build();
        ReturnEpilogTagRootNode node = parseReturnEpilogTagNode(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(s);
            b.beginSourceSection();
            b.beginRoot();
            b.beginSourceSection();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endSourceSection(2, 4);
            b.endRoot();
            b.endSourceSection(0, 8);
            b.endSource();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        Instruction epilog = findInstruction(bytecode, "c.LeaveValue");

        SourceSection[] sections = epilog.getSourceSections();
        assertEquals(2, sections.length);
        assertSourceSection(2, 4, sections[0]);
        assertSourceSection(0, 8, sections[1]);

        node.getRootNodes().update(ReturnEpilogTagRootNodeGen.BYTECODE.newConfigBuilder().addTag(RootTag.class).build());

        bytecode = node.getBytecodeNode();
        epilog = findInstruction(bytecode, "c.LeaveValue");
        Instruction rootLeave = findInstructionAfter(bytecode, "tag.leave", epilog.getBytecodeIndex());

        sections = epilog.getSourceSections();
        assertEquals(2, sections.length);
        assertSourceSection(2, 4, sections[0]);
        assertSourceSection(0, 8, sections[1]);

        SourceSection[] rootLeaveSections = rootLeave.getSourceSections();
        assertFalse(containsSourceSection(rootLeaveSections, 2, 4));
    }

    @Test
    public void testReturnEpilogSourceNoRootTagStaticallyEnabled() {
        Source s = Source.newBuilder("test", "12345678", "name").build();
        ReturnEpilogNoRootTagRootNode node = parseReturnEpilogNoRootTagNode(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(s);
            b.beginSourceSection(0, 8);
            b.beginRoot();
            b.beginSourceSection(2, 4);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endSourceSection();
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        });

        Instruction epilog = findInstruction(node.getBytecodeNode(), "c.LeaveValue");

        SourceSection[] sections = epilog.getSourceSections();
        assertEquals(2, sections.length);
        assertSourceSection(2, 4, sections[0]);
        assertSourceSection(0, 8, sections[1]);
    }

    @Test
    public void testReturnEpilogLocals() {
        ReturnEpilogTagRootNode node = parseReturnEpilogTagNode(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.createLocal("rootLocal1", null);
            b.createLocal("rootLocal2", null);
            b.beginBlock();
            b.createLocal("blockLocal1", null);
            b.createLocal("blockLocal2", null);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        Instruction epilog = findInstruction(bytecode, "c.LeaveValue");

        assertEquals(4, bytecode.getLocalCount(epilog.getBytecodeIndex()));
        assertEquals(List.of("rootLocal1", "rootLocal2", "blockLocal1", "blockLocal2"), Arrays.asList(bytecode.getLocalNames(epilog.getBytecodeIndex())));

        node.getRootNodes().update(ReturnEpilogTagRootNodeGen.BYTECODE.newConfigBuilder().addTag(RootTag.class).build());

        bytecode = node.getBytecodeNode();
        epilog = findInstruction(bytecode, "c.LeaveValue");
        Instruction rootLeave = findInstructionAfter(bytecode, "tag.leave", epilog.getBytecodeIndex());

        assertEquals(4, bytecode.getLocalCount(epilog.getBytecodeIndex()));
        assertEquals(List.of("rootLocal1", "rootLocal2", "blockLocal1", "blockLocal2"), Arrays.asList(bytecode.getLocalNames(epilog.getBytecodeIndex())));
        assertEquals(0, bytecode.getLocalCount(rootLeave.getBytecodeIndex()));
    }

    @Test
    public void testReturnEpilogLocalsNoRootTagStaticallyEnabled() {
        ReturnEpilogNoRootTagRootNode node = parseReturnEpilogNoRootTagNode(BytecodeConfig.DEFAULT, b -> {
            b.beginRoot();
            b.createLocal("rootLocal1", null);
            b.createLocal("rootLocal2", null);
            b.beginBlock();
            b.createLocal("blockLocal1", null);
            b.createLocal("blockLocal2", null);
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endBlock();
            b.endRoot();
        });

        BytecodeNode bytecode = node.getBytecodeNode();
        Instruction epilog = findInstruction(bytecode, "c.LeaveValue");

        assertEquals(4, bytecode.getLocalCount(epilog.getBytecodeIndex()));
        assertEquals(List.of("rootLocal1", "rootLocal2", "blockLocal1", "blockLocal2"), Arrays.asList(bytecode.getLocalNames(epilog.getBytecodeIndex())));
    }

    private static void assertSourceSection(int startIndex, int length, SourceSection section) {
        assertEquals(startIndex, section.getCharIndex());
        assertEquals(length, section.getCharLength());
    }

    private static Instruction findInstruction(BytecodeNode bytecode, String name) {
        return findInstructionAfter(bytecode, name, -1);
    }

    private static Instruction findInstructionAfter(BytecodeNode bytecode, String name, int bci) {
        for (Instruction instruction : bytecode.getInstructionsAsList()) {
            if (instruction.getBytecodeIndex() > bci && instruction.getName().equals(name)) {
                return instruction;
            }
        }
        throw new AssertionError("No instruction named " + name + " found after bci " + bci + ".");
    }

    private static boolean containsSourceSection(SourceSection[] sections, int startIndex, int length) {
        for (SourceSection section : sections) {
            if (section.getCharIndex() == startIndex && section.getCharLength() == length) {
                return true;
            }
        }
        return false;
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, boxingEliminationTypes = {int.class})
abstract class DuplicateQuickeningEpilogRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

    protected DuplicateQuickeningEpilogRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @EpilogReturn
    static final class StoreReturnValue {
        @Specialization(rewriteOn = UnexpectedResultException.class)
        static int doInt(@SuppressWarnings("unused") Object value) throws UnexpectedResultException {
            throw new UnexpectedResultException(value);
        }

        @ForceQuickening
        @Specialization(replaces = "doInt")
        static Object doObject(Object value) {
            return value;
        }
    }

    @Return(resultOperandIndex = 0)
    static final class FirstOperandReturn {
        @Specialization
        static Object doReturn(Object value, @SuppressWarnings("unused") Object ignored) {
            return value;
        }
    }

    @Return(resultOperandIndex = 1)
    static final class SecondOperandReturn {
        @Specialization
        static Object doReturn(@SuppressWarnings("unused") Object ignored, Object value) {
            return value;
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true, boxingEliminationTypes = {int.class})
abstract class CustomReturnEpilogBytecodeNode extends DebugBytecodeRootNode implements BytecodeRootNode {
    transient Object returnValue = null;

    protected CustomReturnEpilogBytecodeNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @EpilogReturn
    public static final class StoreReturnValue {
        @Specialization
        public static int doStoreReturnValueBoxingEliminated(int returnValue, @Bind CustomReturnEpilogBytecodeNode root) {
            root.returnValue = returnValue;
            return returnValue;
        }

        @Specialization
        public static Object doStoreReturnValue(Object returnValue, @Bind CustomReturnEpilogBytecodeNode root) {
            root.returnValue = returnValue;
            return "epilog(" + returnValue + ")";
        }
    }

    // Checks for a name collision with c.StoreReturnValue_offset1.
    @Operation
    public static final class StoreReturnValueOffset1 {
        @Specialization
        public static Object doStoreReturnValueOffset1(Object value) {
            return value;
        }
    }

    @Return(resultOperandIndex = 0)
    public static final class FirstOperandCustomReturn {
        @Specialization
        public static Object doIntReturn(int value, Object ignored) {
            return value + ":" + ignored;
        }

        @Specialization
        public static Object doReturn(Object value, Object ignored) {
            return value + ":" + ignored;
        }
    }

    @Return(resultOperandIndex = 1)
    public static final class SecondOperandCustomReturn {
        @Specialization
        public static Object doIntReturn(Object ignored, int value) {
            return ignored + ":" + value;
        }

        @Specialization
        public static Object doReturn(Object ignored, Object value) {
            return ignored + ":" + value;
        }
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

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true)
abstract class EpilogReturnExceptionHandlerNode extends DebugBytecodeRootNode implements BytecodeRootNode {
    transient int throwInReturnCount;
    transient int handlerCount;

    protected EpilogReturnExceptionHandlerNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @EpilogReturn
    public static final class ThrowingReturnEpilog {
        @Specialization
        public static Object doDefault(@SuppressWarnings("unused") Object returnValue, @Bind EpilogReturnExceptionHandlerNode root) {
            root.throwInReturnCount++;
            throw new MyException("return epilog");
        }
    }

    @Operation
    public static final class IncrementHandlerCount {
        @Specialization
        public static void doDefault(@Bind EpilogReturnExceptionHandlerNode root) {
            root.handlerCount++;
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableTagInstrumentation = true)
abstract class ReturnEpilogTagRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {
    transient RuntimeException throwInReturnEpilog;

    protected ReturnEpilogTagRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @EpilogReturn
    static final class LeaveValue {
        @Specialization
        public static Object doDefault(Object returnValue, @Bind ReturnEpilogTagRootNode root) {
            if (root.throwInReturnEpilog != null) {
                throw root.throwInReturnEpilog;
            }
            return returnValue;
        }
    }
}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableTagInstrumentation = true, enableRootTagging = false)
abstract class ReturnEpilogNoRootTagRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {
    transient RuntimeException throwInReturnEpilog;

    protected ReturnEpilogNoRootTagRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @EpilogReturn
    static final class LeaveValue {
        @Specialization
        public static Object doDefault(Object returnValue, @Bind ReturnEpilogNoRootTagRootNode root) {
            if (root.throwInReturnEpilog != null) {
                throw root.throwInReturnEpilog;
            }
            return returnValue;
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
