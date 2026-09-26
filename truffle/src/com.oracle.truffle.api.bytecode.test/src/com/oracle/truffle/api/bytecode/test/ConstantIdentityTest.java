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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
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
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Constants that are {@link Object#equals(Object) equal} but distinct must not be merged, neither
 * in the constant pool nor during serialization. Repeated uses of the same reference must still
 * share a constant pool slot and a serialized object.
 */
@RunWith(Parameterized.class)
public class ConstantIdentityTest {

    @Parameters(name = "serialize={0}")
    public static List<Boolean> getParameters() {
        return List.of(false, true);
    }

    @Parameter public boolean serialize;

    private int serializedKeys;

    /**
     * Equality only considers {@link #id}, so two keys with different payloads are equal.
     */
    static final class Key {
        final int id;
        final String payload;

        Key(int id, String payload) {
            this.id = id;
            this.payload = payload;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Key other && other.id == id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Key(" + id + ", " + payload + ")";
        }
    }

    private final BytecodeSerializer serializer = (context, buffer, object) -> {
        if (object instanceof Boolean b) {
            buffer.writeByte(0);
            buffer.writeBoolean(b);
        } else {
            Key key = (Key) object;
            serializedKeys++;
            buffer.writeByte(1);
            buffer.writeInt(key.id);
            buffer.writeUTF(key.payload);
        }
    };

    private static final BytecodeDeserializer DESERIALIZER = (context, buffer) -> switch (buffer.readByte()) {
        case 0 -> buffer.readBoolean();
        case 1 -> new Key(buffer.readInt(), buffer.readUTF());
        default -> throw new AssertionError();
    };

    private ConstantIdentityRootNode parse(BytecodeParser<ConstantIdentityRootNodeGen.Builder> parser) {
        if (!serialize) {
            return ConstantIdentityRootNodeGen.BYTECODE.create(null, BytecodeConfig.DEFAULT, parser).getNode(0);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ConstantIdentityRootNodeGen.BYTECODE.serialize(new DataOutputStream(output), serializer, parser);
            Supplier<DataInput> input = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(output.toByteArray()));
            return ConstantIdentityRootNodeGen.BYTECODE.deserialize(null, BytecodeConfig.DEFAULT, input, DESERIALIZER).getNode(0);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Each root returns an array of loaded constants, one entry per emitted constant. Each key is
     * emitted twice: once with {@code LoadConstant} and once as a constant operand.
     */
    private ConstantIdentityRootNode parseKeys(Key[] keys) {
        return parse(b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCollect();
            for (Key key : keys) {
                b.emitLoadConstant(key);
                b.emitConstantValue(key);
            }
            b.endCollect();
            b.endReturn();
            b.endRoot();
        });
    }

    private void assertKeysPreserved(Key[] keys, int distinctKeys) {
        ConstantIdentityRootNode root = parseKeys(keys);
        Object[] result = (Object[]) root.getCallTarget().call();
        assertEquals(keys.length * 2, result.length);

        for (int i = 0; i < keys.length; i++) {
            for (int j = 0; j < keys.length; j++) {
                for (int u = 0; u < 2; u++) {
                    for (int v = 0; v < 2; v++) {
                        Object a = result[i * 2 + u];
                        Object b = result[j * 2 + v];
                        if (keys[i] == keys[j]) {
                            assertSame(a, b);
                        } else {
                            assertNotSame(a, b);
                        }
                    }
                }
            }
            Key actual = (Key) result[i * 2];
            if (!serialize) {
                assertSame(keys[i], actual);
            }
            assertEquals(keys[i].id, actual.id);
            assertEquals(keys[i].payload, actual.payload);
        }

        Object[] constants = readConstants(root.getBytecodeNode());
        assertEquals(distinctKeys, constants.length);
        if (serialize) {
            assertEquals(distinctKeys, serializedKeys);
        }
    }

    @Test
    public void testEqualButDistinctBelowThreshold() {
        Key a = new Key(1, "a");
        Key b = new Key(1, "b");
        assertKeysPreserved(new Key[]{a, b, a}, 2);
    }

    @Test
    public void testEqualButDistinctAboveThreshold() {
        int n = 20;
        Key[] keys = new Key[n * 2];
        for (int i = 0; i < n; i++) {
            Key key = new Key(42, "payload" + i);
            keys[i] = key;
            keys[n + i] = key;
        }
        assertKeysPreserved(keys, n);
    }

    /**
     * Finally handlers are emitted once per exit path by replaying the finally generator. The same
     * reference must share one constant slot across all replays and with the enclosing code, also
     * after deserialization.
     */
    @Test
    public void testFinallyGeneratorSharesConstants() {
        Key shared = new Key(1, "shared");
        Key finallyOnly = new Key(1, "finally");
        ConstantIdentityRootNode root = parse(b -> {
            b.beginRoot();
            b.beginTryFinally(() -> {
                b.beginBlock();
                b.emitLoadConstant(finallyOnly);
                b.emitConstantValue(shared);
                b.endBlock();
            });
            b.beginBlock();
            b.beginIfThen();
            b.emitLoadConstant(Boolean.TRUE);
            b.beginReturn();
            b.beginCollect();
            b.emitLoadConstant(shared);
            b.endCollect();
            b.endReturn();
            b.endIfThen();
            b.beginReturn();
            b.beginCollect();
            b.emitConstantValue(finallyOnly);
            b.endCollect();
            b.endReturn();
            b.endBlock();
            b.endTryFinally();
            b.endRoot();
        });

        Object[] constants = readConstants(root.getBytecodeNode());
        assertEquals(3, constants.length);
        Key sharedConstant = null;
        Key finallyConstant = null;
        for (Object c : constants) {
            if (c instanceof Key k) {
                if (k.payload.equals("shared")) {
                    assertEquals(null, sharedConstant);
                    sharedConstant = k;
                } else {
                    assertEquals(null, finallyConstant);
                    finallyConstant = k;
                }
            }
        }
        if (!serialize) {
            assertSame(shared, sharedConstant);
            assertSame(finallyOnly, finallyConstant);
        } else {
            assertEquals(2, serializedKeys);
        }
        Object[] result = (Object[]) root.getCallTarget().call();
        assertSame(sharedConstant, result[0]);
    }

    private static Object[] readConstants(BytecodeNode node) {
        for (Class<?> c = node.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField("constants");
                field.setAccessible(true);
                return (Object[]) field.get(node);
            } catch (NoSuchFieldException e) {
                continue;
            } catch (ReflectiveOperationException e) {
                break;
            }
        }
        fail("Failed to access constants field of " + node.getClass());
        throw new AssertionError("unreachable");
    }

}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true)
abstract class ConstantIdentityRootNode extends RootNode implements BytecodeRootNode {

    protected ConstantIdentityRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation
    static final class Collect {
        @Specialization
        static Object[] doDefault(@Variadic Object[] values) {
            return values;
        }
    }

    @Operation
    @ConstantOperand(type = Object.class)
    static final class ConstantValue {
        @Specialization
        static Object doDefault(Object value) {
            return value;
        }
    }

}
