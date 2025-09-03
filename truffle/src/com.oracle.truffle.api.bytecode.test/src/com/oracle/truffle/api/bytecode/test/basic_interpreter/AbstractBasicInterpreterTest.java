/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instruction.Argument;
import com.oracle.truffle.api.bytecode.Instruction.Argument.Kind;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.bytecode.SourceInformation;
import com.oracle.truffle.api.bytecode.SourceInformationTree;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@RunWith(Parameterized.class)
public abstract class AbstractBasicInterpreterTest {

    public record TestRun(Class<? extends BasicInterpreter> interpreterClass, boolean testSerialize) {

        public boolean hasBoxingElimination() {
            return interpreterClass == BasicInterpreterWithBE.class ||
                            interpreterClass == BasicInterpreterWithStoreBytecodeIndexInFrame.class ||
                            interpreterClass == BasicInterpreterProductionBlockScoping.class ||
                            interpreterClass == BasicInterpreterProductionRootScoping.class;
        }

        public boolean hasUncachedInterpreter() {
            return interpreterClass == BasicInterpreterWithUncached.class ||
                            interpreterClass == BasicInterpreterWithStoreBytecodeIndexInFrame.class ||
                            interpreterClass == BasicInterpreterProductionBlockScoping.class ||
                            interpreterClass == BasicInterpreterProductionRootScoping.class;
        }

        @SuppressWarnings("static-method")
        public boolean hasYield() {
            return true;
        }

        public boolean hasRootScoping() {
            return interpreterClass == BasicInterpreterWithRootScoping.class ||
                            interpreterClass == BasicInterpreterProductionRootScoping.class;
        }

        public boolean hasBlockScoping() {
            return !hasRootScoping();
        }

        public boolean storesBciInFrame() {
            return interpreterClass == BasicInterpreterWithStoreBytecodeIndexInFrame.class;
        }

        @Override
        public String toString() {
            return interpreterClass.getSimpleName() + "[serialize=" + testSerialize + "]";
        }

        public int getFrameBaseSlots() {
            int baseCount = 0;
            if (hasUncachedInterpreter() || storesBciInFrame()) {
                baseCount++; // bci
            }
            if (hasYield()) {
                baseCount++;
            }
            return baseCount;
        }

        @SuppressWarnings("static-method")
        public int getVariadicsLimit() {
            if (interpreterClass == BasicInterpreterBase.class //
                            || interpreterClass == BasicInterpreterWithBE.class //
                            || interpreterClass == BasicInterpreterWithStoreBytecodeIndexInFrame.class) {
                return 4;
            } else if (interpreterClass == BasicInterpreterUnsafe.class //
                            || interpreterClass == BasicInterpreterWithOptimizations.class //
                            || interpreterClass == BasicInterpreterProductionBlockScoping.class) {
                return 8;
            } else if (interpreterClass == BasicInterpreterWithUncached.class //
                            || interpreterClass == BasicInterpreterWithRootScoping.class //
                            || interpreterClass == BasicInterpreterProductionRootScoping.class) {
                return 16;
            }

            throw CompilerDirectives.shouldNotReachHere();
        }

        public Object getDefaultLocalValue() {
            if (interpreterClass == BasicInterpreterWithOptimizations.class || interpreterClass == BasicInterpreterWithRootScoping.class) {
                return BasicInterpreter.LOCAL_DEFAULT_VALUE;
            }
            return null;
        }

    }

    public static <T extends Throwable> T assertThrowsWithMessage(String message, Class<T> expectedThrowable,
                    ThrowingRunnable runnable) {
        T error = Assert.assertThrows(expectedThrowable, runnable);
        assertTrue(String.format("Invalid message: %s", error.getMessage()), error.getMessage().contains(message));
        return error;
    }

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    public static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof Long num) {
                buffer.writeByte(0);
                buffer.writeLong(num);
            } else if (object instanceof String str) {
                buffer.writeByte(1);
                buffer.writeUTF(str);
            } else if (object instanceof Boolean bool) {
                buffer.writeByte(2);
                buffer.writeBoolean(bool);
            } else if (object.getClass().isArray()) {
                buffer.writeByte(3);
                if (object instanceof long[] longs) {
                    buffer.writeByte(1);
                    buffer.writeInt(longs.length);
                    for (long num : longs) {
                        serialize(context, buffer, num);
                    }
                } else {
                    throw new AssertionError("Serializer does not handle array of type " + object.getClass());
                }
            } else if (object instanceof BasicInterpreter rootNode) {
                buffer.writeByte(4);
                context.writeBytecodeNode(buffer, rootNode);
            } else if (object instanceof Source source) {
                buffer.writeByte(5);
                buffer.writeUTF(source.getLanguage());
                buffer.writeUTF(source.getName());
                buffer.writeUTF(source.getCharacters().toString());
            } else {
                throw new AssertionError("Serializer does not handle object " + object);
            }
        }
    };

    public static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            byte objectCode = buffer.readByte();
            return switch (objectCode) {
                case 0 -> buffer.readLong();
                case 1 -> buffer.readUTF();
                case 2 -> buffer.readBoolean();
                case 3 -> {
                    byte arrayCode = buffer.readByte();
                    yield switch (arrayCode) {
                        case 1 -> {
                            int length = buffer.readInt();
                            long[] result = new long[length];
                            for (int i = 0; i < length; i++) {
                                result[i] = (long) deserialize(context, buffer);
                            }
                            yield result;
                        }
                        default -> throw new AssertionError("Deserializer does not handle array code " + arrayCode);
                    };
                }
                case 4 -> context.readBytecodeNode(buffer);
                case 5 -> {
                    String language = buffer.readUTF();
                    String name = buffer.readUTF();
                    String characters = buffer.readUTF();
                    yield Source.newBuilder(language, characters, name).build();
                }
                default -> throw new AssertionError("Deserializer does not handle code " + objectCode);
            };
        }
    };

    @Parameters(name = "{0}")
    public static List<TestRun> getParameters() {
        List<TestRun> result = new ArrayList<>();
        for (Class<? extends BasicInterpreter> interpreterClass : allInterpreters()) {
            result.add(new TestRun(interpreterClass, false));
            result.add(new TestRun(interpreterClass, true));
        }
        return result;
    }

    public final TestRun run;

    public AbstractBasicInterpreterTest(TestRun run) {
        this.run = run;
    }

    public <T extends BasicInterpreterBuilder> RootCallTarget parse(String rootName, BytecodeParser<T> builder) {
        BytecodeRootNode rootNode = parseNode(run.interpreterClass, LANGUAGE, run.testSerialize, rootName, builder);
        return ((RootNode) rootNode).getCallTarget();
    }

    public <T extends BasicInterpreterBuilder> BasicInterpreter parseNode(String rootName, BytecodeParser<T> builder) {
        return parseNode(run.interpreterClass, LANGUAGE, run.testSerialize, rootName, builder);
    }

    public <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeWithSource(String rootName, BytecodeParser<T> builder) {
        return parseNodeWithSource(run.interpreterClass, LANGUAGE, run.testSerialize, rootName, builder);
    }

    public <T extends BasicInterpreterBuilder> BytecodeRootNodes<BasicInterpreter> createNodes(BytecodeConfig config, BytecodeParser<T> builder) {
        return createNodes(run.interpreterClass, LANGUAGE, run.testSerialize, config, builder);
    }

    public BytecodeConfig.Builder createBytecodeConfigBuilder() {
        return BasicInterpreterBuilder.invokeNewConfigBuilder(run.interpreterClass);
    }

    /**
     * Creates a root node using the given parameters.
     *
     * In order to parameterize tests over multiple different interpreter configurations
     * ("variants"), we take the specific interpreterClass as input. Since interpreters are
     * instantiated using a static {@code create} method, we must invoke this method using
     * reflection.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BasicInterpreterBuilder> BytecodeRootNodes<BasicInterpreter> createNodes(Class<? extends BasicInterpreter> interpreterClass,
                    BytecodeDSLTestLanguage language, boolean testSerialize, BytecodeConfig config, BytecodeParser<T> builder) {

        BytecodeRootNodes<BasicInterpreter> result = BasicInterpreterBuilder.invokeCreate((Class<? extends BasicInterpreter>) interpreterClass,
                        language, config, (BytecodeParser<? extends BasicInterpreterBuilder>) builder);
        if (testSerialize) {
            assertBytecodeNodesEqual(result, doRoundTrip(interpreterClass, language, config, result));
        }

        for (BasicInterpreter interpreter : result.getNodes()) {
            try {
                testIntrospectionInvariants(interpreter.getBytecodeNode());
            } catch (Throwable e) {
                throw new AssertionError("Invariant failure " + interpreter.dump(), e);
            }
        }

        return result;
    }

    protected static void testIntrospectionInvariants(BytecodeNode bytecode) {
        List<Instruction> instructions = bytecode.getInstructionsAsList();
        int instructionIndex = 0;
        int endBytecodeIndex = 0;
        for (Instruction instr : bytecode.getInstructions()) {
            assertTrue(instr.getBytecodeIndex() >= 0);
            assertSame(bytecode, instr.getBytecodeNode());
            assertTrue(instr.getLength() > 0);
            assertEquals(instr.getBytecodeIndex(), instr.getLocation().getBytecodeIndex());
            assertEquals(bytecode, instr.getLocation().getBytecodeNode());
            assertNotNull(instr.getName());
            assertTrue(instr.getNextBytecodeIndex() > 0);
            assertTrue(instr.getOperationCode() > 0);

            endBytecodeIndex = Math.max(endBytecodeIndex, instr.getNextBytecodeIndex());

            // not failing
            instr.getSourceSection();
            instr.getSourceSections();
            instr.isInstrumentation();

            assertNotNull(instr.hashCode());
            assertEquals(instr, instructions.get(instructionIndex));

            for (Argument arg : instr.getArguments()) {
                assertNotNull(arg.getName());

                switch (arg.getKind()) {
                    case BRANCH_PROFILE:
                        assertNotNull(arg.asBranchProfile());
                        break;
                    case BYTECODE_INDEX:
                        int index = arg.asBytecodeIndex();
                        if (index >= 0) {
                            assertNotNull(BytecodeLocation.get(bytecode, index));
                        }
                        break;
                    case CONSTANT:
                        assertNotNull(arg.asConstant());
                        break;
                    case INTEGER:
                        // not failing
                        arg.asInteger();
                        break;
                    case LOCAL_INDEX:
                        int localIndex = arg.asLocalIndex();
                        if (!instr.getName().contains("local.mat") &&
                                        !instr.getName().contains("clear.local")) {
                            assertNotNull(bytecode.getLocals().get(localIndex));
                        }
                        break;
                    case LOCAL_OFFSET:
                        int offset = arg.asLocalOffset();
                        assertTrue(offset >= 0);
                        if (!instr.getName().contains("local.mat") &&
                                        !instr.getName().contains("clear.local")) {
                            int count = bytecode.getLocalCount(instr.getBytecodeIndex());
                            assertTrue(offset >= 0 && offset < count);
                        }
                        break;
                    case NODE_PROFILE:
                        Node node = arg.asCachedNode();
                        if (bytecode.getTier() == BytecodeTier.CACHED) {
                            assertNotNull(node);
                            assertSame(bytecode, node.getParent());
                            assertNotNull(arg.getSpecializationInfo());
                        } else {
                            assertNull(node);
                        }
                        break;
                    case TAG_NODE:
                        TagTreeNode tag = arg.asTagNode();
                        assertNotNull(BytecodeLocation.get(bytecode, tag.getEnterBytecodeIndex()));
                        assertNotNull(BytecodeLocation.get(bytecode, tag.getReturnBytecodeIndex()));
                        assertSame(bytecode, tag.getBytecodeNode());
                        assertNotNull(tag.toString());
                        break;
                    default:
                        throw new AssertionError("New unhandled kind.");
                }

                if (instructions.size() < 100) { // keep runtime reasonable
                    if (arg.getKind() != Kind.BRANCH_PROFILE) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asBranchProfile());
                    }

                    if (arg.getKind() != Kind.BYTECODE_INDEX) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asBytecodeIndex());
                    }

                    if (arg.getKind() != Kind.CONSTANT) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asConstant());
                    }

                    if (arg.getKind() != Kind.INTEGER) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asInteger());
                    }

                    if (arg.getKind() != Kind.LOCAL_INDEX) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asLocalIndex());
                    }

                    if (arg.getKind() != Kind.LOCAL_OFFSET) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asLocalOffset());
                    }

                    if (arg.getKind() != Kind.NODE_PROFILE) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asCachedNode());
                    }

                    if (arg.getKind() != Kind.TAG_NODE) {
                        assertThrows(UnsupportedOperationException.class, () -> arg.asTagNode());
                    }
                }
                assertNotNull(arg.toString());
            }

            assertNotNull(instr.toString());

            instructionIndex++;
        }

        if (bytecode.getSourceInformation() != null) {
            assertTrue(bytecode.hasSourceInformation());
            for (SourceInformation source : bytecode.getSourceInformation()) {
                assertNotNull(source.toString());
                assertNotNull(BytecodeLocation.get(bytecode, source.getStartBytecodeIndex()));
                if (source.getEndBytecodeIndex() < endBytecodeIndex) {
                    assertNotNull(BytecodeLocation.get(bytecode, source.getEndBytecodeIndex()));
                }
                assertNotNull(source.getSourceSection());
            }
        } else {
            assertFalse(bytecode.hasSourceInformation());
        }
        List<LocalVariable> locals = bytecode.getLocals();
        for (LocalVariable local : locals) {
            assertTrue(local.getLocalOffset() >= 0);
            assertEquals(local, local);
            // call just to ensure it doesn't fail
            local.getTypeProfile();
            assertNotNull(local.toString());

            if (local.getStartIndex() != -1) {
                // block scoping
                assertNotNull(BytecodeLocation.get(bytecode, local.getStartIndex()));
                assertTrue(local.getStartIndex() < local.getEndIndex());

                if (locals.size() < 1000) {
                    assertEquals(local.getInfo(), bytecode.getLocalInfo(local.getStartIndex(), local.getLocalOffset()));
                    assertEquals(local.getName(), bytecode.getLocalName(local.getStartIndex(), local.getLocalOffset()));
                    assertTrue(local.getLocalOffset() < bytecode.getLocalCount(local.getStartIndex()));
                }
            } else {
                // root scoping
                assertEquals(-1, local.getEndIndex());
                if (locals.size() < 1000) {
                    assertEquals(local.getName(), bytecode.getLocalName(0, local.getLocalOffset()));
                    assertEquals(local.getInfo(), bytecode.getLocalInfo(0, local.getLocalOffset()));
                }
            }
        }

        SourceInformationTree tree = bytecode.getSourceInformationTree();
        if (tree != null) {

            testSourceTree(bytecode, null, tree);
        }

    }

    private static void testSourceTree(BytecodeNode bytecode, SourceInformationTree parent, SourceInformationTree tree) {
        if (parent != null) {
            assertNotNull(tree.getSourceSection());
        } else {
            tree.getSourceSection();
            // toString is too expensive to do for every tree entry.
            assertNotNull(tree.toString());
        }

        assertNotNull(BytecodeLocation.get(bytecode, tree.getStartBytecodeIndex()));

        for (SourceInformationTree child : tree.getChildren()) {
            testSourceTree(bytecode, tree, child);
        }
    }

    public static <T extends BasicInterpreter> BytecodeRootNodes<T> doRoundTrip(Class<? extends BasicInterpreter> interpreterClass, BytecodeDSLTestLanguage language, BytecodeConfig config,
                    BytecodeRootNodes<BasicInterpreter> nodes) {
        // Perform a serialize-deserialize round trip.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            nodes.serialize(new DataOutputStream(output), SERIALIZER);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        Supplier<DataInput> input = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(output.toByteArray()));
        return BasicInterpreterBuilder.invokeDeserialize((Class<? extends BasicInterpreter>) interpreterClass, language, config, input, DESERIALIZER);
    }

    public BytecodeRootNodes<BasicInterpreter> doRoundTrip(BytecodeRootNodes<BasicInterpreter> nodes) {
        return AbstractBasicInterpreterTest.doRoundTrip(run.interpreterClass, LANGUAGE, BytecodeConfig.DEFAULT, nodes);
    }

    public static <T extends BasicInterpreterBuilder> RootCallTarget parse(Class<? extends BasicInterpreter> interpreterClass, BytecodeDSLTestLanguage language, boolean testSerialize, String rootName,
                    BytecodeParser<T> builder) {
        BytecodeRootNode rootNode = parseNode(interpreterClass, language, testSerialize, rootName, builder);
        return ((RootNode) rootNode).getCallTarget();
    }

    public static <T extends BasicInterpreterBuilder> BasicInterpreter parseNode(Class<? extends BasicInterpreter> interpreterClass, BytecodeDSLTestLanguage language, boolean testSerialize,
                    String rootName, BytecodeParser<T> builder) {
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(interpreterClass, language, testSerialize, BytecodeConfig.DEFAULT, builder);
        BasicInterpreter op = nodes.getNode(0);
        op.setName(rootName);
        return op;
    }

    public static <T extends BasicInterpreterBuilder> BasicInterpreter parseNodeWithSource(Class<? extends BasicInterpreter> interpreterClass, BytecodeDSLTestLanguage language, boolean testSerialize,
                    String rootName, BytecodeParser<T> builder) {
        BytecodeRootNodes<BasicInterpreter> nodes = createNodes(interpreterClass, language, testSerialize, BytecodeConfig.WITH_SOURCE, builder);
        BasicInterpreter op = nodes.getNode(0);
        op.setName(rootName);
        return op;
    }

    private static void assertBytecodeNodesEqual(BytecodeRootNodes<BasicInterpreter> expectedBytecodeNodes, BytecodeRootNodes<BasicInterpreter> actualBytecodeNodes) {
        List<BasicInterpreter> expectedNodes = expectedBytecodeNodes.getNodes();
        List<BasicInterpreter> actualNodes = actualBytecodeNodes.getNodes();
        assertEquals(expectedNodes.size(), actualNodes.size());
        for (int i = 0; i < expectedNodes.size(); i++) {
            BasicInterpreter expectedNode = expectedNodes.get(i);
            BasicInterpreter actualNode = actualNodes.get(i);
            BytecodeNode expectedBytecode = expectedNodes.get(i).getBytecodeNode();
            BytecodeNode actualBytecode = actualNodes.get(i).getBytecodeNode();

            try {
                assertEquals(expectedNode.name, actualNode.name);
                assertArrayEquals((byte[]) readField(expectedBytecode, "bytecodes"), (byte[]) readField(actualBytecode, "bytecodes"));
                assertConstantsEqual((Object[]) readField(expectedBytecode, "constants"), (Object[]) readField(actualBytecode, "constants"));
                assertArrayEquals((int[]) readField(expectedBytecode, "handlers"), (int[]) readField(actualBytecode, "handlers"));
                assertArrayEquals((int[]) readField(expectedBytecode, "locals"), (int[]) readField(actualBytecode, "locals"));
            } catch (AssertionError e) {
                System.err.println("Expected node: " + expectedBytecode.dump());
                System.err.println("Actual node: " + actualBytecode.dump());
                throw e;
            }

        }
    }

    private static void assertConstantsEqual(Object[] expectedConstants, Object[] actualConstants) {
        assertEquals(expectedConstants.length, actualConstants.length);
        for (int i = 0; i < expectedConstants.length; i++) {
            Object expected = expectedConstants[i];
            Object actual = actualConstants[i];

            if (expected instanceof BasicInterpreter expectedRoot && actual instanceof BasicInterpreter actualRoot) {
                // We don't implement equals for root nodes (that's what we're trying to test). Make
                // sure it's at least the same name.
                assertEquals(expectedRoot.name, actualRoot.name);
            } else if (expected instanceof long[] expectedLongs && actual instanceof long[] actualLongs) {
                assertArrayEquals(expectedLongs, actualLongs);
            } else if (expected instanceof ContinuationRootNode expectedContinuation && actual instanceof ContinuationRootNode actualContinuation) {
                // The fields of a ContinuationRootNode are not exposed. At least validate they have
                // the same source root node.
                assertConstantsEqual(new Object[]{expectedContinuation.getSourceRootNode()}, new Object[]{actualContinuation.getSourceRootNode()});
            } else {
                assertEquals(expected, actual);
            }
        }
    }

    private static Object readField(BytecodeNode node, String name) {
        try {
            Field field = node.getClass().getSuperclass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(node);
        } catch (ReflectiveOperationException ex) {
            fail("Failed to access interpreter field " + name + " with introspection.");
        }
        throw new AssertionError("unreachable");
    }

    /**
     * Helper class for validating SourceInformationTrees.
     */
    record ExpectedSourceTree(boolean available, String contents, ExpectedSourceTree... children) {
        public void assertTreeEquals(SourceInformationTree actual) {
            if (!available) {
                assertTrue(!actual.getSourceSection().isAvailable());
            } else if (contents == null) {
                assertNull(actual.getSourceSection());
            } else {
                assertEquals(contents, actual.getSourceSection().getCharacters().toString());
            }
            assertEquals(children.length, actual.getChildren().size());
            for (int i = 0; i < children.length; i++) {
                children[i].assertTreeEquals(actual.getChildren().get(i));
            }
        }

        public static ExpectedSourceTree expectedSourceTree(String contents, ExpectedSourceTree... children) {
            return new ExpectedSourceTree(true, contents, children);
        }

        public static ExpectedSourceTree expectedSourceTreeUnavailable(ExpectedSourceTree... children) {
            return new ExpectedSourceTree(false, null, children);
        }
    }

    public static List<Class<? extends BasicInterpreter>> allInterpreters() {
        return List.of(BasicInterpreterBase.class, BasicInterpreterUnsafe.class, BasicInterpreterWithUncached.class, BasicInterpreterWithBE.class, BasicInterpreterWithOptimizations.class,
                        BasicInterpreterWithStoreBytecodeIndexInFrame.class,
                        BasicInterpreterWithRootScoping.class, BasicInterpreterProductionRootScoping.class, BasicInterpreterProductionBlockScoping.class);
    }

    /// Code gen helpers

    protected static void emitReturn(BasicInterpreterBuilder b, long value) {
        b.beginReturn();
        b.emitLoadConstant(value);
        b.endReturn();
    }

    protected static void emitReturnIf(BasicInterpreterBuilder b, int arg, long value) {
        b.beginIfThen();
        b.emitLoadArgument(arg);
        emitReturn(b, value);
        b.endIfThen();
    }

    protected static void emitBranchIf(BasicInterpreterBuilder b, int arg, BytecodeLabel lbl) {
        b.beginIfThen();
        b.emitLoadArgument(arg);
        b.emitBranch(lbl);
        b.endIfThen();
    }

    protected static void emitAppend(BasicInterpreterBuilder b, long value) {
        b.beginAppenderOperation();
        b.emitLoadArgument(0);
        b.emitLoadConstant(value);
        b.endAppenderOperation();
    }

    protected static void emitThrow(BasicInterpreterBuilder b, long value) {
        b.beginThrowOperation();
        b.emitLoadConstant(value);
        b.endThrowOperation();
    }

    protected static void emitThrowIf(BasicInterpreterBuilder b, int arg, long value) {
        b.beginIfThen();
        b.emitLoadArgument(arg);
        emitThrow(b, value);
        b.endIfThen();
    }
}
