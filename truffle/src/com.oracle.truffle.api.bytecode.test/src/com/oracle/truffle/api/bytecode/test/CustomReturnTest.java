/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.bytecode.test.AbstractInstructionTest.assertFails;
import static com.oracle.truffle.api.bytecode.test.AbstractInstructionTest.assertInstructions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ExceptionHandler;
import com.oracle.truffle.api.bytecode.ExceptionHandler.HandlerKind;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.InstructionDescriptor;
import com.oracle.truffle.api.bytecode.Return;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.ErrorLanguage;
import com.oracle.truffle.api.bytecode.test.error_tests.ExpectError;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@RunWith(Parameterized.class)
public class CustomReturnTest {

    private final boolean serialize;

    public CustomReturnTest(boolean serialize) {
        this.serialize = serialize;
    }

    @Parameters(name = "serialize={0}")
    public static List<Boolean> getParameters() {
        return List.of(false, true);
    }

    @Test
    public void testCustomReturnInstructionDescriptor() {
        List<InstructionDescriptor> descriptors = CustomReturnInstructionRootNodeGen.BYTECODE.getInstructionDescriptors();
        InstructionDescriptor customReturn = getInstructionDescriptor(descriptors, "c.CustomReturn");
        assertNotNull(customReturn);
        assertFalse(customReturn.isInstrumentation());
        assertSame(customReturn, CustomReturnInstructionRootNodeGen.BYTECODE.getInstructionDescriptor(customReturn.getOperationCode()));
    }

    @Test
    public void testCustomReturnExecution() {
        CustomReturnInstructionRootNode root = parse(b -> {
            b.beginRoot();
            b.beginCustomReturn();
            b.emitLoadConstant(42);
            b.endCustomReturn();
            b.endRoot();
        });

        assertEquals("custom:42", root.getCallTarget().call());
    }

    @Test
    public void testCustomReturnConstantOperand() {
        CustomReturnInstructionRootNode root = parse(b -> {
            b.beginRoot();
            b.beginConstantOperandCustomReturn("prefix");
            b.emitLoadConstant(42);
            b.endConstantOperandCustomReturn();
            b.endRoot();
        });

        assertEquals("prefix:42", root.getCallTarget().call());
    }

    @Test
    public void testMultiOperandCustomReturnExecution() {
        CustomReturnInstructionRootNode root = parse(b -> {
            b.beginRoot();
            b.beginMultiOperandCustomReturn();
            b.emitLoadConstant("left");
            b.emitLoadConstant("right");
            b.endMultiOperandCustomReturn();
            b.endRoot();
        });

        assertEquals("right:left", root.getCallTarget().call());
    }

    @Test
    public void testCustomReturnInputBoxingElimination() {
        CustomReturnInstructionRootNode root = parse(b -> {
            b.beginRoot();
            b.beginIntCustomReturn();
            b.emitLoadArgument(0);
            b.endIntCustomReturn();
            b.endRoot();
        });

        assertInstructions(root,
                        "load.argument",
                        "c.IntCustomReturn");

        root.getBytecodeNode().setUncachedThreshold(0);
        assertEquals(43, root.getCallTarget().call(42));

        assertInstructions(root,
                        "load.argument$Int",
                        "c.IntCustomReturn$Int");
    }

    @Test
    public void testCustomReturnChildCountValidation() {
        assertInvalidBuilderUsage("Operation CustomReturn expected exactly 1 child, but 0 provided.", b -> {
            b.beginRoot();
            b.beginCustomReturn();
            b.endCustomReturn();
            b.endRoot();
        });

        assertInvalidBuilderUsage("Operation CustomReturn expected exactly 1 child, but 2 provided.", b -> {
            b.beginRoot();
            b.beginCustomReturn();
            b.emitLoadConstant(41);
            b.emitLoadConstant(42);
            b.endCustomReturn();
            b.endRoot();
        });
    }

    @Test
    public void testMultiOperandCustomReturnChildCountValidation() {
        assertInvalidBuilderUsage("Operation MultiOperandCustomReturn expected exactly 2 children, but 1 provided.", b -> {
            b.beginRoot();
            b.beginMultiOperandCustomReturn();
            b.emitLoadConstant(41);
            b.endMultiOperandCustomReturn();
            b.endRoot();
        });

        assertInvalidBuilderUsage("Operation MultiOperandCustomReturn expected exactly 2 children, but 3 provided.", b -> {
            b.beginRoot();
            b.beginMultiOperandCustomReturn();
            b.emitLoadConstant(40);
            b.emitLoadConstant(41);
            b.emitLoadConstant(42);
            b.endMultiOperandCustomReturn();
            b.endRoot();
        });
    }

    @Test
    public void testCustomReturnMarksFollowingCodeUnreachable() {
        CustomReturnInstructionRootNode root = parse(b -> {
            b.beginRoot();

            b.beginCustomReturn();
            b.emitLoadConstant(42);
            b.endCustomReturn();

            b.beginReturn();
            b.emitLoadConstant(43);
            b.endReturn();

            b.endRoot();
        });

        assertInstructions(root,
                        "load.constant",
                        "c.CustomReturn");
    }

    @Test
    public void testUnreachableReturnWithSourceSectionDoesNotRewindCatchRange() {
        Source source = Source.newBuilder("test", "return;", "unreachable-return.test").build();
        CustomReturnInstructionRootNode root = parse(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(source);
            b.beginRoot();
            b.beginTryCatch();
            b.beginBlock();
            emitThrowingReturnFollowedByUnreachableReturn(b, source);
            b.endBlock();
            b.beginCustomReturn();
            b.emitLoadConstant("caught");
            b.endCustomReturn();
            b.endTryCatch();
            b.endRoot();
            b.endSource();
        });

        assertEquals("custom:caught", root.getCallTarget().call());
        assertHandlerCoverage(root, HandlerKind.CUSTOM, 1);
    }

    @Test
    public void testUnreachableBranchWithSourceSectionDoesNotRewindCatchRange() {
        Source source = Source.newBuilder("test", "branch;", "unreachable-branch.test").build();
        CustomReturnInstructionRootNode root = parse(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(source);
            b.beginRoot();
            b.beginBlock();
            BytecodeLabel target = b.createLabel();
            b.beginTryCatch();
            b.beginBlock();
            b.beginThrowingCustomReturn();
            b.emitLoadConstant("return");
            b.endThrowingCustomReturn();
            b.beginSourceSection(0, source.getLength());
            b.emitBranch(target);
            b.endSourceSection();
            b.endBlock();
            b.beginCustomReturn();
            b.emitLoadConstant("caught");
            b.endCustomReturn();
            b.endTryCatch();
            b.emitLabel(target);
            b.endBlock();
            b.endRoot();
            b.endSource();
        });

        assertEquals("custom:caught", root.getCallTarget().call());
        assertHandlerCoverage(root, HandlerKind.CUSTOM, 1);
    }

    @Test
    public void testUnreachableReturnWithSourceSectionDoesNotRewindTryFinallyRange() {
        Source source = Source.newBuilder("test", "return;", "unreachable-return-finally.test").build();
        CustomReturnInstructionRootNode root = parse(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(source);
            b.beginRoot();
            b.beginTryFinally(() -> b.emitLoadConstant("finally"));
            b.beginBlock();
            emitThrowingReturnFollowedByUnreachableReturn(b, source);
            b.endBlock();
            b.endTryFinally();
            b.endRoot();
            b.endSource();
        });

        assertFails(() -> root.getCallTarget().call(), TestException.class);
        assertHandlerCoverage(root, HandlerKind.CUSTOM, 1);
    }

    @Test
    public void testUnreachableReturnWithSourceSectionDoesNotRewindTryCatchOtherwiseRange() {
        Source source = Source.newBuilder("test", "return;", "unreachable-return-otherwise.test").build();
        CustomReturnInstructionRootNode root = parse(BytecodeConfig.WITH_SOURCE, b -> {
            b.beginSource(source);
            b.beginRoot();
            b.beginTryCatchOtherwise(() -> b.emitLoadConstant("otherwise"));
            b.beginBlock();
            emitThrowingReturnFollowedByUnreachableReturn(b, source);
            b.endBlock();
            b.beginCustomReturn();
            b.emitLoadConstant("caught");
            b.endCustomReturn();
            b.endTryCatchOtherwise();
            b.endRoot();
            b.endSource();
        });

        assertEquals("custom:caught", root.getCallTarget().call());
        assertHandlerCoverage(root, HandlerKind.CUSTOM, 1);
    }

    @Test
    public void testUnreachableReturnWithSourceSectionDoesNotRewindTagRange() {
        runInstrumentationTest((context, instrumenter) -> {
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), createFactory(new ArrayList<>()));

            Source source = Source.newBuilder("test", "return;", "unreachable-return-tag.test").build();
            CustomReturnInstructionRootNode root = parse(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.WITH_SOURCE, b -> {
                b.beginSource(source);
                b.beginRoot();
                b.beginTag(StatementTag.class);
                b.beginBlock();
                emitThrowingReturnFollowedByUnreachableReturn(b, source);
                b.endBlock();
                b.endTag(StatementTag.class);
                b.endRoot();
                b.endSource();
            });

            assertFails(() -> root.getCallTarget().call(), TestException.class);
            assertHandlerCoverage(root, HandlerKind.TAG, 1);
        });
    }

    @Test
    public void testTagRangesRewindOnlyWhenClosed() {
        runInstrumentationTest((context, instrumenter) -> {
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), createFactory(new ArrayList<>()));

            Source source = Source.newBuilder("test", "return;", "nested-tag-rewind.test").build();
            CustomReturnInstructionRootNode root = parse(BytecodeDSLTestLanguage.REF.get(null), BytecodeConfig.WITH_SOURCE, b -> {
                b.beginSource(source);
                b.beginRoot();
                b.beginTag(StatementTag.class);
                b.beginTryFinally(() -> {
                    b.beginBlock();
                    // Labels conservatively restore reachability. Only tags whose range was closed
                    // during the unwind should be rewound when this happens.
                    b.emitLabel(b.createLabel());
                    b.endBlock();
                });
                b.beginTag(StatementTag.class);
                b.beginBlock();
                emitThrowingReturnFollowedByUnreachableReturn(b, source);
                b.endBlock();
                b.endTag(StatementTag.class);
                b.endTryFinally();
                b.endTag(StatementTag.class);
                b.endRoot();
                b.endSource();
            });

            assertFails(() -> root.getCallTarget().call(), TestException.class);
            assertHandlerCoverage(root, HandlerKind.TAG, 2);
        });
    }

    @Test
    public void testTagLeaveHasResultStackOffsetImmediate() {
        InstructionDescriptor tagLeave = getInstructionDescriptor(CustomReturnInstructionRootNodeGen.BYTECODE.getInstructionDescriptors(), "tag.leave");
        assertTrue(tagLeave.getArgumentDescriptors().stream().anyMatch(argument -> argument.getName().equals("result_stack_offset") && argument.getLength() != 0));
    }

    @Test
    public void testCustomReturnTagInstrumentation() {
        runInstrumentationTest((context, instrumenter) -> {
            CustomReturnInstructionRootNode root = parse(BytecodeDSLTestLanguage.REF.get(null), b -> {
                b.beginRoot();
                b.beginTag(StatementTag.class);
                b.beginCustomReturn();
                b.emitLoadConstant(42);
                b.endCustomReturn();
                b.endTag(StatementTag.class);
                b.endRoot();
            });

            List<Object> returnValues = new ArrayList<>();
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), createFactory(returnValues));

            assertEquals("custom:42", root.getCallTarget().call());
            assertEquals(List.of(42), returnValues);
        });
    }

    @Test
    public void testMultiOperandCustomReturnTagInstrumentation() {
        runInstrumentationTest((context, instrumenter) -> {
            List<Object> returnValues = new ArrayList<>();
            instrumenter.attachExecutionEventFactory(SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build(), createFactory(returnValues));

            CustomReturnInstructionRootNode firstOperandRoot = parse(BytecodeDSLTestLanguage.REF.get(null), b -> {
                b.beginRoot();
                b.beginTag(StatementTag.class);
                b.beginFirstOperandCustomReturn();
                b.emitLoadConstant("left");
                b.emitLoadConstant("right");
                b.endFirstOperandCustomReturn();
                b.endTag(StatementTag.class);
                b.endRoot();
            });

            assertEquals("right:left", firstOperandRoot.getCallTarget().call());
            assertEquals(List.of("left"), returnValues);

            CustomReturnInstructionRootNode secondOperandRoot = parse(BytecodeDSLTestLanguage.REF.get(null), b -> {
                b.beginRoot();
                b.beginTag(StatementTag.class);
                b.beginMultiOperandCustomReturn();
                b.emitLoadConstant("left");
                b.emitLoadConstant("right");
                b.endMultiOperandCustomReturn();
                b.endTag(StatementTag.class);
                b.endRoot();
            });

            assertEquals("right:left", secondOperandRoot.getCallTarget().call());
            assertEquals(List.of("left", "right"), returnValues);
        });
    }

    private static void emitThrowingReturnFollowedByUnreachableReturn(CustomReturnInstructionRootNodeGen.Builder b, Source source) {
        // The return instruction itself can throw and must remain covered by active handlers.
        b.beginThrowingCustomReturn();
        b.emitLoadConstant("return");
        b.endThrowingCustomReturn();

        // Processing metadata for an unreachable return must not rewind active handler ranges.
        b.beginSourceSection(0, source.getLength());
        b.beginCustomReturn();
        b.emitLoadConstant("unreachable");
        b.endCustomReturn();
        b.endSourceSection();
    }

    private static void assertHandlerCoverage(CustomReturnInstructionRootNode root, HandlerKind handlerKind, int expectedCount) {
        int throwingReturnBci = -1;
        for (Instruction instruction : root.getBytecodeNode().getInstructions()) {
            if (instruction.getName().equals("c.ThrowingCustomReturn")) {
                throwingReturnBci = instruction.getBytecodeIndex();
                break;
            }
        }
        assertTrue("Throwing custom return not found.\n" + root.getBytecodeNode().dump(), throwingReturnBci != -1);

        int coveredCount = 0;
        for (ExceptionHandler handler : root.getBytecodeNode().getExceptionHandlers()) {
            if (handler.getKind() == handlerKind && handler.getStartBytecodeIndex() <= throwingReturnBci && throwingReturnBci < handler.getEndBytecodeIndex()) {
                coveredCount++;
            }
        }
        assertEquals(root.getBytecodeNode().dump(), expectedCount, coveredCount);
    }

    private CustomReturnInstructionRootNode parse(BytecodeParser<CustomReturnInstructionRootNodeGen.Builder> parser) {
        return parse(null, BytecodeConfig.DEFAULT, parser);
    }

    private CustomReturnInstructionRootNode parse(BytecodeConfig config, BytecodeParser<CustomReturnInstructionRootNodeGen.Builder> parser) {
        return parse(null, config, parser);
    }

    private CustomReturnInstructionRootNode parse(BytecodeDSLTestLanguage language, BytecodeParser<CustomReturnInstructionRootNodeGen.Builder> parser) {
        return parse(language, BytecodeConfig.DEFAULT, parser);
    }

    private CustomReturnInstructionRootNode parse(BytecodeDSLTestLanguage language, BytecodeConfig config, BytecodeParser<CustomReturnInstructionRootNodeGen.Builder> parser) {
        BytecodeRootNodes<CustomReturnInstructionRootNode> nodes = CustomReturnInstructionRootNodeGen.create(language, config, parser);
        if (serialize) {
            nodes = doRoundTrip(language, config, nodes);
        }
        return nodes.getNode(0);
    }

    private static BytecodeRootNodes<CustomReturnInstructionRootNode> doRoundTrip(BytecodeDSLTestLanguage language, BytecodeConfig config,
                    BytecodeRootNodes<CustomReturnInstructionRootNode> nodes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            nodes.serialize(new DataOutputStream(output), SERIALIZER);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }

        Supplier<DataInput> input = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(output.toByteArray()));
        try {
            return CustomReturnInstructionRootNodeGen.deserialize(language, config, input, DESERIALIZER);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private void assertInvalidBuilderUsage(String expectedMessage, BytecodeParser<CustomReturnInstructionRootNodeGen.Builder> parser) {
        assertFails(() -> parse(parser), IllegalStateException.class, e -> assertTrue(e.getMessage().contains(expectedMessage)));
    }

    private static InstructionDescriptor getInstructionDescriptor(List<InstructionDescriptor> descriptors, String name) {
        for (InstructionDescriptor descriptor : descriptors) {
            if (descriptor.getName().equals(name)) {
                return descriptor;
            }
        }
        return null;
    }

    private static void runInstrumentationTest(BiConsumer<Context, Instrumenter> test) {
        Context context = Context.create(BytecodeDSLTestLanguage.ID);
        try {
            context.initialize(BytecodeDSLTestLanguage.ID);
            context.enter();
            Instrumenter instrumenter = context.getEngine().getInstruments().get(CustomReturnTestInstrument.ID).lookup(Instrumenter.class);
            test.accept(context, instrumenter);
        } finally {
            context.close();
        }
    }

    private static ExecutionEventNodeFactory createFactory(List<Object> returnValues) {
        return (e) -> new ExecutionEventNode() {
            @Override
            public void onReturnValue(VirtualFrame frame, Object result) {
                returnValues.add(result);
            }
        };
    }

    static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
        public void serialize(BytecodeSerializer.SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof Integer i) {
                buffer.writeByte(0);
                buffer.writeInt(i);
            } else if (object instanceof String s) {
                buffer.writeByte(1);
                buffer.writeUTF(s);
            } else if (object instanceof Source source) {
                buffer.writeByte(2);
                buffer.writeUTF(source.getLanguage());
                buffer.writeUTF(source.getName());
                buffer.writeBoolean(source.hasCharacters());
                if (source.hasCharacters()) {
                    buffer.writeUTF(source.getCharacters().toString());
                }
            } else {
                throw new AssertionError("Serializer does not handle object " + object);
            }
        }
    };

    static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(BytecodeDeserializer.DeserializerContext context, DataInput buffer) throws IOException {
            return switch (buffer.readByte()) {
                case 0 -> buffer.readInt();
                case 1 -> buffer.readUTF();
                case 2 -> {
                    String language = buffer.readUTF();
                    String name = buffer.readUTF();
                    boolean hasCharacters = buffer.readBoolean();
                    CharSequence characters = hasCharacters ? buffer.readUTF() : "";
                    CharSequence content = hasCharacters ? characters : Source.CONTENT_NONE;
                    yield Source.newBuilder(language, characters, name).content(content).build();
                }
                default -> throw new AssertionError("Deserializer does not handle object code.");
            };
        }
    };

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableUncachedInterpreter = true, enableTagInstrumentation = true, enableSerialization = true, boxingEliminationTypes = {
                    int.class})
    public abstract static class CustomReturnInstructionRootNode extends RootNode implements BytecodeRootNode {
        protected CustomReturnInstructionRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Return
        public static final class CustomReturn {
            @Specialization
            public static Object doReturn(Object value) {
                return "custom:" + value;
            }
        }

        @Return
        @ConstantOperand(type = String.class, name = "prefix")
        public static final class ConstantOperandCustomReturn {
            @Specialization
            public static Object doReturn(String prefix, Object value) {
                return prefix + ":" + value;
            }
        }

        @Return(resultOperandIndex = 0)
        public static final class FirstOperandCustomReturn {
            @Specialization
            public static Object doReturn(Object value, Object ignored) {
                return ignored + ":" + value;
            }
        }

        @Return(resultOperandIndex = 1)
        public static final class MultiOperandCustomReturn {
            @Specialization
            public static Object doReturn(Object ignored, Object value) {
                return value + ":" + ignored;
            }
        }

        @Return
        public static final class IntCustomReturn {
            @Specialization
            public static int doInt(int value) {
                return value + 1;
            }

            @Fallback
            public static Object doObject(Object value) {
                return value;
            }
        }

        @Return
        public static final class ThrowingCustomReturn {
            @Specialization
            public static Object doThrow(String message) {
                throw new TestException(message);
            }
        }
    }

    @SuppressWarnings("serial")
    static final class TestException extends AbstractTruffleException {
        TestException(String message) {
            super(message);
        }
    }

    @TruffleInstrument.Registration(id = CustomReturnTestInstrument.ID, services = Instrumenter.class)
    public static class CustomReturnTestInstrument extends TruffleInstrument {
        public static final String ID = "CustomReturnTestInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
        }
    }

    @SuppressWarnings("unused")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract static class InvalidReturnTest extends RootNode implements BytecodeRootNode {
        protected InvalidReturnTest(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @ExpectError("A @Return cannot be void. It must return a value, which becomes the value returned from the root node.")
        @Return
        public static final class CustomReturnBadReturnType {
            @Specialization
            public static void doReturn(Object result) {
            }
        }

        @ExpectError("A @Return must take at least one dynamic operand for the returned value. Add a dynamic operand to resolve this error.")
        @Return
        public static final class CustomReturnNoOperands {
            @Specialization
            public static Object doReturn() {
                return null;
            }
        }

        @ExpectError("A @Return with multiple dynamic operands must specify resultOperandIndex.")
        @Return
        public static final class CustomReturnMissingResultOperandIndex {
            @Specialization
            public static Object doReturn(Object first, Object second) {
                return first == null ? second : first;
            }
        }

        @ExpectError("Invalid resultOperandIndex 2 for @Return. The value must be between 0 and 1.")
        @Return(resultOperandIndex = 2)
        public static final class CustomReturnBadResultOperandIndex {
            @Specialization
            public static Object doReturn(Object first, Object second) {
                return first == null ? second : first;
            }
        }

        @ExpectError("Invalid resultOperandIndex -1 for @Return. The value must be between 0 and 1.")
        @Return(resultOperandIndex = -1)
        public static final class CustomReturnNegativeResultOperandIndex {
            @Specialization
            public static Object doReturn(Object first, Object second) {
                return first == null ? second : first;
            }
        }

        @ExpectError("Tag instrumentation is not enabled. The tags attribute can only be used if tag instrumentation is enabled for the parent root node. " +
                        "Enable tag instrumentation using @GenerateBytecode(... enableTagInstrumentation = true) to resolve this or remove the tags attribute.")
        @Return(tags = {StatementTag.class})
        public static final class CustomReturnTagsWithoutTagInstrumentation {
            @Specialization
            public static Object doReturn(Object result) {
                return result;
            }
        }
    }

}
