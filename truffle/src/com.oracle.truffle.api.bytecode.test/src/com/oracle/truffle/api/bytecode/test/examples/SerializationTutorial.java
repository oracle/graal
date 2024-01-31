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
package com.oracle.truffle.api.bytecode.test.examples;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Serialization allows you to persist bytecode nodes (say, to disk) by encoding them as an array of
 * bytes. These bytes can be deserialized to produce the original bytecode nodes. This technique can
 * be useful to avoid re-parsing a source program multiple times (similar to CPython's .pyc files).
 * <p>
 * Serialization logic is generated automatically for Bytecode DSL interpreters. A language only
 * needs to specify how to encode/decode its constants, and the generated code handles the rest.
 * This tutorial will explain how to integrate serialization with your Bytecode DSL interpreter.
 */
public class SerializationTutorial {
    /**
     * The first step to using serialization is to enable it in the generated code. Modifying your
     * {@link GenerateBytecode} specification, set {@code enableSerialization = true}. Then, rebuild
     * your project to update the generated interpreter.
     * <p>
     * When serialization is enabled, Bytecode DSL generates extra methods that you can use to
     * serialize/deserialize your bytecode nodes.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true)
    @ShortCircuitOperation(name = "Or", operator = ShortCircuitOperation.Operator.OR_RETURN_CONVERTED, booleanConverter = SerializableBytecodeNode.AsBoolean.class)
    public abstract static class SerializableBytecodeNode extends RootNode implements BytecodeRootNode {
        public static final TruffleLanguage<?> LANGUAGE = null;
        public static final Object NULL = new Object();

        // Can optionally be set after the node is built.
        public String name = null;

        protected SerializableBytecodeNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            public static int doInt(int x, int y) {
                return x + y;
            }
        }

        @Operation
        public static final class ArrayRead {
            @Specialization
            public static Object doObjectArray(Object[] arr, int i) {
                return arr[i];
            }
        }

        @Operation
        public static final class ArrayLength {
            @Specialization
            public static int doObjectArray(Object[] arr) {
                return arr.length;
            }
        }

        @Operation
        public static final class LessThan {
            @Specialization
            public static boolean doInt(int x, int y) {
                return x < y;
            }
        }

        @Operation
        public static final class AsBoolean {
            @Specialization
            public static boolean doBoolean(boolean b) {
                return b;
            }
        }
    }

    /**
     * Bytecode DSL automatically generates a serialization encoding and the logic to
     * serialize/deserialize bytecode.
     * <p>
     * The actual encoding is not exposed to the language, but the way that a bytecode node is
     * serialized is pertinent: serialization encodes the sequence of {@link BytecodeBuilder} calls
     * invoked by the {@link BytecodeParser} as bytes, and deserialization replays those calls in
     * order to rebuild the bytecode at a later time. This mechanism has a few important
     * consequences:
     * <ul>
     * <li>The serialization logic does not know how to encode non-Bytecode DSL constants passed to
     * the builder methods (e.g., {@code LoadConstant} objects). Languages must provide the
     * serialization logic themselves using {@link BytecodeSerializer} and
     * {@link BytecodeDeserializer} instances.</li>
     * <li>Since only the {@link BytecodeBuilder} calls are captured, any side effects in the parser
     * (e.g., setting fields) will not be replayed upon deserialization.</li>
     * </ul>
     * <p>
     * Before we define our own serialization logic, let's quickly define a concrete program to use
     * as a running example. This program takes an integer n and returns information about the n-th
     * planet in our solar system.
     */
    record Planet(String name, int diameterInKm) {
    }

    static final Object[] PLANETS = new Object[]{
                    new Planet("Mercury", 4879), new Planet("Venus", 12104), new Planet("Earth", 12756), new Planet("Mars", 3475),
                    new Planet("Jupiter", 142984), new Planet("Saturn", 120536), new Planet("Uranus", 51118), new Planet("Neptune", 49528)
    };

    static final BytecodeParser<SerializableBytecodeNodeGen.Builder> PARSER = b -> {
        // @formatter:off
        b.beginRoot(SerializableBytecodeNode.LANGUAGE);
            // return (n < 0 or PLANETS.length - 1 < n) ? NULL : PLANETS[n]
            b.beginReturn();
                b.beginConditional();
                    b.beginOr();
                        b.beginLessThan();
                            b.emitLoadArgument(0);
                            b.emitLoadConstant(0);
                        b.endLessThan();

                        b.beginLessThan();
                            b.beginAdd();
                                b.beginArrayLength();
                                    b.emitLoadConstant(PLANETS);
                                b.endArrayLength();

                                b.emitLoadConstant(-1);
                            b.endAdd();

                            b.emitLoadArgument(0);
                        b.endLessThan();
                    b.endOr();

                    b.emitLoadConstant(SerializableBytecodeNode.NULL);

                    b.beginArrayRead();
                        b.emitLoadConstant(PLANETS);
                        b.emitLoadArgument(0);
                    b.endArrayRead();

                b.endConditional();
            b.endReturn();
        b.endRoot();
        // @formatter:on
    };

    /**
     * Let's write a quick test for this program -- we'll want it for later.
     */
    public void doTest(RootCallTarget callTarget) {
        for (int i = 0; i < PLANETS.length; i++) {
            assertEquals(PLANETS[i], callTarget.call(i));
        }
        assertEquals(SerializableBytecodeNode.NULL, callTarget.call(-1));
        assertEquals(SerializableBytecodeNode.NULL, callTarget.call(PLANETS.length));
    }

    @Test
    public void testProgram() {
        BytecodeRootNodes<SerializableBytecodeNode> nodes = SerializableBytecodeNodeGen.create(BytecodeConfig.DEFAULT, PARSER);
        SerializableBytecodeNode rootNode = nodes.getNode(0);
        doTest(rootNode.getCallTarget());
    }

    /**
     * Now, we return to our goal of implementing serialization. As mentioned above, a language
     * provides the logic for encoding/decoding language constants by defining
     * {@link BytecodeSerializer} and {@link BytecodeDeserializer} objects. Both objects should
     * agree on an unambiguous encoding for constants.
     * <p>
     * Let's define the actual {@link BytecodeSerializer}. We define a set of type codes for each
     * kind of constant that can appear in the bytecode. For each constant kind, the serializer
     * encodes all of the information required for a value to be reconstructed during
     * deserialization.
     */
    static final byte TYPE_INT = 0;
    static final byte TYPE_STRING = 1;
    static final byte TYPE_NULL = 2;
    static final byte TYPE_OBJECT_ARRAY = 3;
    static final byte TYPE_PLANET = 4;

    static class ExampleBytecodeSerializer implements BytecodeSerializer {
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof Integer i) {
                // For ints, we encode the int value.
                buffer.writeByte(TYPE_INT);
                buffer.writeInt(i);
            } else if (object instanceof String s) {
                // For strings, we encode the String value.
                buffer.writeByte(TYPE_STRING);
                buffer.writeUTF(s); // encode the String value
            } else if (object == SerializableBytecodeNode.NULL) {
                // For NULL, the type code *is* the encoding.
                buffer.writeByte(TYPE_NULL);
            } else if (object instanceof Object[] arr) {
                // For arrays, we encode the length and then recursively encode each array element.
                buffer.writeByte(TYPE_OBJECT_ARRAY);
                buffer.writeInt(arr.length);
                for (Object o : arr) {
                    serialize(context, buffer, o);
                }
            } else if (object instanceof Planet p) {
                // For Planets, we encode the name and diameter.
                buffer.writeByte(TYPE_PLANET);
                buffer.writeUTF(p.name);
                buffer.writeInt(p.diameterInKm);
            } else {
                // It takes some trial and error to cover all of the constants used in your
                // interpreter. It can be helpful to provide a useful fallback message.
                throw new AssertionError("Unsupported constant " + object);
            }
        }
    }

    /**
     * Let's check that the serializer works. Bytecode DSL defines a static {@code serialize} method
     * on the generated {@code SerializableBytecodeNodeGen} class that we can call. The method takes
     * a few arguments:
     * <ol>
     * <li>A {@link DataOutput} buffer to write the bytes to.</li>
     * <li>The {@link BytecodeSerializer} object.</li>
     * <li>The {@link BytecodeParser BytecodeParser<Builder>} parser that gets invoked to perform
     * serialization.</li>
     * </ol>
     */
    @Test
    public void testSerialize() throws IOException {
        // Set up our output buffer.
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Invoke serialize.
        SerializableBytecodeNodeGen.serialize(new DataOutputStream(output), new ExampleBytecodeSerializer(), PARSER);

        // The results will be written to the output buffer.
        byte[] serialized = output.toByteArray();

        // Since we haven't defined the deserializer, we can't do anything with the bytes yet, but
        // we can validate that the array is non-empty.
        assertNotEquals(0, serialized.length);

        /**
         * For convenience, Bytecode DSL also defines a {@link BytecodeRootNodes#serialize} method
         * to serialize an existing {@link BytecodeRootNodes} instance. Since the parser used to
         * parse the instance is already known, this method only requires the first three arguments.
         */
        BytecodeRootNodes<SerializableBytecodeNode> nodes = SerializableBytecodeNodeGen.create(BytecodeConfig.DEFAULT, PARSER);
        ByteArrayOutputStream output2 = new ByteArrayOutputStream();
        nodes.serialize(new DataOutputStream(output2), new ExampleBytecodeSerializer());

        byte[] serialized2 = output2.toByteArray();
        assertNotEquals(0, serialized2.length);

        // The encoded bytes should match.
        assertArrayEquals(serialized, serialized2);
    }

    /**
     * Now, we can define the deserializer. For each constant kind, the deserializer should
     * reconstruct values using the same encoding defined by the serializer.
     */
    static class ExampleBytecodeDeserializer implements BytecodeDeserializer {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            byte typeCode = buffer.readByte();
            return doDeserialize(context, buffer, typeCode);
        }

        protected Object doDeserialize(DeserializerContext context, DataInput buffer, byte typeCode) throws IOException {
            return switch (typeCode) {
                case TYPE_INT -> buffer.readInt();
                case TYPE_STRING -> buffer.readUTF();
                case TYPE_NULL -> SerializableBytecodeNode.NULL;
                case TYPE_OBJECT_ARRAY -> {
                    int length = buffer.readInt();
                    Object[] result = new Object[length];
                    for (int i = 0; i < length; i++) {
                        result[i] = deserialize(context, buffer);
                    }
                    yield result;
                }
                case TYPE_PLANET -> {
                    String name = buffer.readUTF();
                    int diameter = buffer.readInt();
                    yield new Planet(name, diameter);
                }
                default -> throw new AssertionError("Unknown type code " + typeCode);
            };
        }
    }

    /**
     * Finally, we can test the serialization process end-to-end. Like with serialization, Bytecode
     * DSL defines a static {@code deserialize} method on the generated
     * {@code SerializableBytecodeNodeGen} class that we can call. The method takes a few arguments:
     * <ol>
     * <li>A {@link TruffleLanguage} language used when creating each root node</li>
     * <li>A {@link BytecodeConfig} config that specifies which metadata to parse from the
     * serialized bytes. Only metadats included during serialization will be available.</li>
     * <li>A {@link Supplier<DataInput>} a callable that supplies a {@link DataInput} to read.
     * Deserialization can happen multiple times because of reparsing. The supplier is responsible
     * for producing a fresh {@link DataInput} each time it is called.</li>
     * <li>The {@link BytecodeDeserializer} object.</li>
     * </ol>
     */
    @Test
    public void testDeserialize() throws IOException {
        // First, serialize the program to get a byte array.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SerializableBytecodeNodeGen.serialize(new DataOutputStream(output), new ExampleBytecodeSerializer(), PARSER);
        byte[] serialized = output.toByteArray();

        // Now, deserialize the bytes to produce a BytecodeRootNodes instance.
        Supplier<DataInput> supplier = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(serialized));
        BytecodeRootNodes<SerializableBytecodeNode> nodes = SerializableBytecodeNodeGen.deserialize(
                        SerializableBytecodeNode.LANGUAGE,
                        BytecodeConfig.DEFAULT,
                        supplier,
                        new ExampleBytecodeDeserializer());

        // It should produce a single root node.
        assertEquals(1, nodes.count());

        // Finally, the root node should have the same semantics as the original program.
        SerializableBytecodeNode rootNode = nodes.getNode(0);
        doTest(rootNode.getCallTarget());
    }

    /**
     * The above example should give you enough information to implement basic serialization in your
     * language. The rest of this tutorial covers some of the more advanced features of
     * serialization.
     * <p>
     * First is the ability to serialize/deserialize multiple root nodes. Serialization implicitly
     * supports multiple root nodes, but if one root node uses another root node as a constant, the
     * serializer needs a way to encode a reference to the second node. This is the purpose of the
     * {@link BytecodeSerializer.SerializerContext} parameter (and similarly, for deserialization,
     * the {@link BytecodeDeserializer.DeserializerContext} parameter). We can extend the serializer
     * and deserializer as follows.
     */
    static final byte TYPE_ROOT_NODE = 5;

    static class ExampleBytecodeSerializerWithRootNodes extends ExampleBytecodeSerializer {
        @Override
        public void serialize(SerializerContext context, DataOutput buffer, Object object) throws IOException {
            if (object instanceof SerializableBytecodeNode rootNode) {
                buffer.writeByte(TYPE_ROOT_NODE);
                // Use the context to write a reference to the root node. This root node must be
                // declared by the current parse, otherwise the behaviour is undefined.
                context.writeBytecodeNode(buffer, rootNode);
            } else {
                // Fall back on the parent implementation.
                super.serialize(context, buffer, object);
            }
        }
    }

    static class ExampleBytecodeDeserializerWithRootNodes extends ExampleBytecodeDeserializer {
        @Override
        protected Object doDeserialize(DeserializerContext context, DataInput buffer, byte typeCode) throws IOException {
            if (typeCode == TYPE_ROOT_NODE) {
                // Use the context to read the root node.
                return context.readBytecodeNode(buffer);
            } else {
                return super.doDeserialize(context, buffer, typeCode);
            }
        }
    }

    /**
     * Here's an example program that returns a different root node depending on the value of its
     * first argument.
     */
    static final BytecodeParser<SerializableBytecodeNodeGen.Builder> MULTIPLE_ROOT_NODES_PARSER = b -> {
        // @formatter:off
        b.beginRoot(SerializableBytecodeNode.LANGUAGE);
            // def plusOne(x) = x + 1
            b.beginRoot(SerializableBytecodeNode.LANGUAGE);
                b.beginReturn();
                    b.beginAdd();
                        b.emitLoadArgument(0);
                        b.emitLoadConstant(1);
                    b.endAdd();
                b.endReturn();
            SerializableBytecodeNode plusOne = b.endRoot();
            plusOne.name = "plusOne";

            // def timesTwo(x) = x + x
            b.beginRoot(SerializableBytecodeNode.LANGUAGE);
                b.beginReturn();
                    b.beginAdd();
                        b.emitLoadArgument(0);
                        b.emitLoadArgument(0);
                    b.endAdd();
                b.endReturn();
            SerializableBytecodeNode timesTwo = b.endRoot();
            timesTwo.name = "timesTwo";

            // return arg ? plusOne : timesTwo
            b.beginReturn();
                b.beginConditional();
                    b.emitLoadArgument(0);
                    b.emitLoadConstant(plusOne);
                    b.emitLoadConstant(timesTwo);
                b.endConditional();
            b.endReturn();
        SerializableBytecodeNode rootNode = b.endRoot();
        rootNode.name = "rootNode";
        // @formatter:on
    };

    /**
     * And here's some test code to ensure it works normally.
     */
    public void doTestMultipleRootNodes(RootCallTarget callTarget) {
        SerializableBytecodeNode plusOne = (SerializableBytecodeNode) callTarget.call(true);
        assertEquals(42, plusOne.getCallTarget().call(41));

        SerializableBytecodeNode timesTwo = (SerializableBytecodeNode) callTarget.call(false);
        assertEquals(42, timesTwo.getCallTarget().call(21));
    }

    /**
     * The program should work the same way after a serialization + deserialization round trip.
     */
    @Test
    public void testMultipleRootNodesRoundTrip() throws IOException {
        // First, let's parse the nodes normally and test the behaviour.
        BytecodeRootNodes<SerializableBytecodeNode> nodes = SerializableBytecodeNodeGen.create(BytecodeConfig.DEFAULT, MULTIPLE_ROOT_NODES_PARSER);
        assertEquals(3, nodes.count());
        SerializableBytecodeNode rootNode = nodes.getNode(2);
        doTestMultipleRootNodes(rootNode.getCallTarget());

        // Now, let's do a serialize + deserialize round trip and test the behaviour.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        nodes.serialize(new DataOutputStream(output), new ExampleBytecodeSerializerWithRootNodes());
        byte[] serialized = output.toByteArray();
        Supplier<DataInput> supplier = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(serialized));
        BytecodeRootNodes<SerializableBytecodeNode> roundTripNodes = SerializableBytecodeNodeGen.deserialize(
                        SerializableBytecodeNode.LANGUAGE,
                        BytecodeConfig.DEFAULT,
                        supplier,
                        new ExampleBytecodeDeserializerWithRootNodes());

        assertEquals(3, roundTripNodes.count());
        SerializableBytecodeNode roundTripRootNode = roundTripNodes.getNode(2);
        doTestMultipleRootNodes(roundTripRootNode.getCallTarget());

        /**
         * Note that our parser includes side-effecting statements to set the {@code name} field.
         * This is discouraged, but we included it for illustrative purposes. This field will be set
         * in the original node, but the side effect will *not* be captured in the serialized node.
         * Be careful not to include side-effecting behaviour in your parser.
         */
        assertEquals("rootNode", rootNode.name);
        assertNull(roundTripRootNode.name);
    }

    // TODO: different BytecodeConfig with sources, use of SerializerContext
}
