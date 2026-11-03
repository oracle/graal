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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A finally handler is serialized once and deserialized once per emitted exit. Constants from that
 * handler must still be created once, in the outermost context that can decode them.
 */
public class OuterContextConstantTest {

    private int serializedTokens;
    private int deserializedTokens;
    private int serializedRoots;
    private int deserializedRoots;
    private final List<BytecodeRootNode[]> deserializedArrays = new ArrayList<>();

    /**
     * Equality is the name, so distinct instances with the same name share one serialized object.
     */
    static final class Token {
        final String name;

        Token(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Token other && other.name.equals(name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    private final BytecodeSerializer serializer = (context, buffer, object) -> {
        if (object instanceof Boolean b) {
            buffer.writeByte(0);
            buffer.writeBoolean(b);
        } else if (object instanceof Integer i) {
            buffer.writeByte(1);
            buffer.writeInt(i);
        } else if (object instanceof Token token) {
            serializedTokens++;
            buffer.writeByte(2);
            buffer.writeUTF(token.name);
        } else if (object instanceof OuterContextConstantRootNode root) {
            serializedRoots++;
            buffer.writeByte(3);
            context.writeBytecodeNode(buffer, root);
        } else if (object instanceof BytecodeRootNode[] roots) {
            buffer.writeByte(4);
            buffer.writeInt(roots.length);
            for (BytecodeRootNode root : roots) {
                context.writeBytecodeNode(buffer, root);
            }
        } else {
            throw new AssertionError(object);
        }
    };

    private final BytecodeDeserializer deserializer = (context, buffer) -> switch (buffer.readByte()) {
        case 0 -> buffer.readBoolean();
        case 1 -> buffer.readInt();
        case 2 -> {
            deserializedTokens++;
            yield new Token(buffer.readUTF());
        }
        case 3 -> {
            deserializedRoots++;
            yield context.readBytecodeNode(buffer);
        }
        case 4 -> {
            BytecodeRootNode[] roots = new BytecodeRootNode[buffer.readInt()];
            for (int i = 0; i < roots.length; i++) {
                roots[i] = context.readBytecodeNode(buffer);
            }
            deserializedArrays.add(roots);
            yield roots;
        }
        default -> throw new AssertionError();
    };

    private OuterContextConstantRootNode roundTrip(BytecodeParser<OuterContextConstantRootNodeGen.Builder> parser) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            OuterContextConstantRootNodeGen.BYTECODE.serialize(new DataOutputStream(output), serializer, parser);
            Supplier<DataInput> input = () -> SerializationUtils.createByteBufferDataInput(ByteBuffer.wrap(output.toByteArray()));
            return OuterContextConstantRootNodeGen.BYTECODE.deserialize(null, BytecodeConfig.DEFAULT, input, deserializer).getNode(0);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    public void testFinallyConstantCreatedOnce() {
        Token shared = new Token("shared");
        OuterContextConstantRootNode root = roundTrip(b -> {
            b.beginRoot();
            b.beginTryFinally(() -> {
                b.beginBlock();
                b.beginTouch();
                b.emitLoadConstant(shared);
                b.endTouch();
                b.endBlock();
            });
            b.beginBlock();
            b.beginIfThen();
            b.emitLoadConstant(Boolean.TRUE);
            b.beginReturn();
            b.emitLoadConstant(shared);
            b.endReturn();
            b.endIfThen();
            b.beginReturn();
            b.emitLoadNull();
            b.endReturn();
            b.endBlock();
            b.endTryFinally();
            b.endRoot();
        });

        Token result = (Token) root.getCallTarget().call();
        assertEquals("shared", result.name);
        assertEquals(1, serializedTokens);
        assertEquals(1, deserializedTokens);
    }

    @Test
    public void testEqualConstantsStillMerge() {
        OuterContextConstantRootNode root = roundTrip(b -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(new Token("same"));
            b.endReturn();
            b.endRoot();
            // A second root keeps another equal token out of the finally-handler path.
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(new Token("same"));
            b.endReturn();
            b.endRoot();
        });

        Token result = (Token) root.getCallTarget().call();
        assertEquals("same", result.name);
        assertEquals(1, serializedTokens);
        assertEquals(1, deserializedTokens);
    }

    @Test
    public void testRootDeclaredInFinallyStaysInThatContext() {
        OuterContextConstantRootNode root = roundTrip(b -> {
            b.beginRoot();
            b.beginTryFinally(() -> {
                b.beginBlock();
                b.beginRoot();
                b.beginReturn();
                b.emitLoadConstant(1);
                b.endReturn();
                OuterContextConstantRootNode inner = b.endRoot();
                b.beginTouch();
                b.emitLoadConstant(inner);
                b.endTouch();
                b.endBlock();
            });
            b.beginBlock();
            b.beginReturn();
            b.emitLoadNull();
            b.endReturn();
            b.endBlock();
            b.endTryFinally();
            b.endRoot();
        });

        assertNull(root.getCallTarget().call());
        assertEquals(1, serializedRoots);
        assertTrue(deserializedRoots > 1);
    }

    @Test
    public void testConstantReferencingRootsAtDifferentDepths() {
        OuterContextConstantRootNode root = roundTrip(b -> {
            b.beginRoot();
            OuterContextConstantRootNode depth0 = emitReturnRoot(b, 0);
            b.beginTryFinally(() -> {
                b.beginBlock();
                OuterContextConstantRootNode depth1 = emitReturnRoot(b, 1);
                b.beginTryFinally(() -> {
                    b.beginBlock();
                    OuterContextConstantRootNode depth2 = emitReturnRoot(b, 2);
                    emitTouch(b, new BytecodeRootNode[]{depth0});
                    emitTouch(b, new BytecodeRootNode[]{depth0, depth1});
                    emitTouch(b, new BytecodeRootNode[]{depth0, depth1, depth2});
                    b.endBlock();
                });
                emitTouch(b, Boolean.TRUE);
                b.endTryFinally();
                b.endBlock();
            });
            b.beginBlock();
            b.beginIfThen();
            b.emitLoadConstant(Boolean.TRUE);
            b.beginReturn();
            b.emitLoadNull();
            b.endReturn();
            b.endIfThen();
            b.beginReturn();
            b.emitLoadNull();
            b.endReturn();
            b.endBlock();
            b.endTryFinally();
            b.endRoot();
        });

        assertNull(root.getCallTarget().call());

        int[] counts = new int[4];
        for (BytecodeRootNode[] roots : deserializedArrays) {
            counts[roots.length]++;
            for (int i = 0; i < roots.length; i++) {
                assertEquals(i, ((RootNode) roots[i]).getCallTarget().call());
            }
        }
        // Each array is created in the context of its innermost referenced root.
        int depth1Replays = counts[2];
        assertEquals(1, counts[1]);
        assertTrue(depth1Replays > 1);
        assertTrue(counts[3] > depth1Replays);
        assertEquals(deserializedArrays.size(), counts[1] + counts[2] + counts[3]);
    }

    private static OuterContextConstantRootNode emitReturnRoot(OuterContextConstantRootNodeGen.Builder b, int value) {
        b.beginRoot();
        b.beginReturn();
        b.emitLoadConstant(value);
        b.endReturn();
        return b.endRoot();
    }

    private static void emitTouch(OuterContextConstantRootNodeGen.Builder b, Object constant) {
        b.beginTouch();
        b.emitLoadConstant(constant);
        b.endTouch();
    }

}

@GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableSerialization = true)
abstract class OuterContextConstantRootNode extends RootNode implements BytecodeRootNode {

    protected OuterContextConstantRootNode(BytecodeDSLTestLanguage language, FrameDescriptor fd) {
        super(language, fd);
    }

    @Operation
    static final class Touch {
        @Specialization
        static void doDefault(@SuppressWarnings("unused") Object value) {
        }
    }

}
