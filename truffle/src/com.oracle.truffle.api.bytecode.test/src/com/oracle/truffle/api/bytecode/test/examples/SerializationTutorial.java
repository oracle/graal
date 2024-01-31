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
 * This tutorial explains how to use serialization with a Bytecode DSL interpreter.
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
     * serialize/deserialize bytecode. However, the serialization logic does not know how to encode
     * language constants (i.e., objects used in {@code LoadConstant} operations). We must provide
     * this logic ourselves in the form of {@link BytecodeSerializer} and
     * {@link BytecodeDeserializer} instances.
     *
     * Before we do that, let's quickly define a concrete program to use as a running example. This
     * program takes an integer n and returns information about the n-th planet in our solar system.
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

    static final BytecodeSerializer SERIALIZER = new BytecodeSerializer() {
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
    };

    /**
     * Let's check that the serializer works. Bytecode DSL defines a static {@code serialize} method
     * on the generated {@code SerializableBytecodeNodeGen} class that we can call. The method takes
     * a few arguments:
     * <ol>
     * <li>A {@link BytecodeConfig} config that specifies which metadata to keep when serializing
     * the interpreter.</li>
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
        SerializableBytecodeNodeGen.serialize(
                        BytecodeConfig.DEFAULT,
                        new DataOutputStream(output),
                        SERIALIZER,
                        PARSER);

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
        nodes.serialize(
                        BytecodeConfig.DEFAULT,
                        new DataOutputStream(output2),
                        SERIALIZER);

        byte[] serialized2 = output2.toByteArray();
        assertNotEquals(0, serialized2.length);

        // The encoded bytes should match.
        assertArrayEquals(serialized, serialized2);
    }

    /**
     * Now, we can define the deserializer. For each constant kind, the deserializer should
     * reconstruct values using the same encoding defined by the serializer.
     */
    static final BytecodeDeserializer DESERIALIZER = new BytecodeDeserializer() {
        public Object deserialize(DeserializerContext context, DataInput buffer) throws IOException {
            byte typeCode = buffer.readByte();
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
    };

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
        SerializableBytecodeNodeGen.serialize(BytecodeConfig.DEFAULT, new DataOutputStream(output), SERIALIZER, PARSER);
        byte[] serialized = output.toByteArray();

        // Now, deserialize the bytes to produce a BytecodeRootNodes instance.
        Supplier<DataInput> supplier = () -> SerializationUtils.createDataInput(ByteBuffer.wrap(serialized));
        BytecodeRootNodes<SerializableBytecodeNode> nodes = SerializableBytecodeNodeGen.deserialize(
                        SerializableBytecodeNode.LANGUAGE,
                        BytecodeConfig.DEFAULT,
                        supplier,
                        DESERIALIZER);

        // It should produce a single root node.
        assertEquals(1, nodes.count());

        // Finally, the root node should have the same semantics as the original program.
        SerializableBytecodeNode rootNode = nodes.getNode(0);
        doTest(rootNode.getCallTarget());
    }

    // TODO: different BytecodeConfig with sources, use of SerializerContext
}
