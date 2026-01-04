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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Yield;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class InstructionBytecodeSizeTest {

    private static final int CACHED_INSTRUCTION_SIZE = 26;
    private static final int UNCACHED_INSTRUCTION_SIZE = 26;

    // !Important: Keep these in sync with BytecodeRootNodeElement!
    // Estimated number of Java bytecodes per instruction.
    // Should be at least max(cached, uncached).
    public static final int ESTIMATED_INSTRUCTION_SIZE = 26;
    // Estimated number of java bytecodes needed for a bytecode loop
    public static final int ESTIMATED_BYTECODE_FOOTPRINT = 1000;

    @Test
    public void testEstimations() throws Exception {
        OneOperationNode node1 = parse1((b) -> {
            b.beginRoot();
            b.endRoot();
        });

        TwoOperationNode node2 = parse2((b) -> {
            b.beginRoot();
            b.endRoot();
        });

        TwentyOperationNode node20 = parse20((b) -> {
            b.beginRoot();
            b.endRoot();
        });

        ContinueAtSizes size1 = calculateSizes(node1);
        ContinueAtSizes size2 = calculateSizes(node2);
        ContinueAtSizes size20 = calculateSizes(node20);

        /*
         * These tests are expected to fail eventually due to changes. If they fail please update
         * the bytecode instruction sizes in this test and consider updating the constants used for
         * the heuristic.
         */
        ContinueAtSizes expectedSize = new ContinueAtSizes(new NameAndSize(null, CACHED_INSTRUCTION_SIZE), new NameAndSize(null, UNCACHED_INSTRUCTION_SIZE));
        assertEquals(expectedSize, computeSingleInstructionSize(size1, size2, 2));
        assertEquals(expectedSize, computeSingleInstructionSize(size1, size20, 20));

        int estimatedSize1 = ESTIMATED_BYTECODE_FOOTPRINT + (ESTIMATED_INSTRUCTION_SIZE * countInstructions(node1));
        int estimatedSize2 = ESTIMATED_BYTECODE_FOOTPRINT + (ESTIMATED_INSTRUCTION_SIZE * countInstructions(node2));
        int estimatedSize20 = ESTIMATED_BYTECODE_FOOTPRINT + (ESTIMATED_INSTRUCTION_SIZE * countInstructions(node20));

        // test that we always overestimate
        assertTrue("Expected " + size1.cached + " > " + estimatedSize1, estimatedSize1 > size1.cached.size);
        assertTrue("Expected " + size1.uncached + " > " + estimatedSize1, estimatedSize1 > size1.uncached.size);

        assertTrue("Expected " + size2.cached + " > " + estimatedSize2, estimatedSize2 > size2.cached.size);
        assertTrue("Expected " + size2.uncached + " > " + estimatedSize2, estimatedSize2 > size2.uncached.size);

        assertTrue("Expected " + size20.cached + " > " + estimatedSize20, estimatedSize20 > size20.cached.size);
        assertTrue("Expected " + size20.uncached + " > " + estimatedSize20, estimatedSize20 > size20.uncached.size);
    }

    @Test
    public void testMany() throws Exception {
        ManyInstructionNode node = parseMany((b) -> {
            b.beginRoot();
            b.endRoot();
        });
        ContinueAtSizes sizes = calculateSizes(node);
        assertTrue(String.valueOf(sizes.cached()), sizes.cached().size < 8000);
        assertTrue(String.valueOf(sizes.uncached()), sizes.uncached().size < 8000);
    }

    private static int countInstructions(BytecodeRootNode node) throws ClassNotFoundException {
        Class<?> c = Class.forName(node.getClass().getName() + "$Instructions");
        Field[] f = c.getDeclaredFields();
        int instructionCount = 0;

        for (Field field : f) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                instructionCount++;
            }
        }
        return instructionCount;

    }

    private static ContinueAtSizes computeSingleInstructionSize(ContinueAtSizes size1, ContinueAtSizes sizeN, int n) {
        int cachedDiff = sizeN.cached.size - size1.cached.size;
        int cachedSingle = cachedDiff / (n - 1);

        int uncachedDiff = sizeN.uncached.size - size1.uncached.size;
        int uncachedSingle = uncachedDiff / (n - 1);
        return new ContinueAtSizes(new NameAndSize(null, cachedSingle), new NameAndSize(null, uncachedSingle));
    }

    private static ContinueAtSizes calculateSizes(BytecodeRootNode node) {
        BytecodeNode bytecodeNode = node.getBytecodeNode();
        assertEquals(BytecodeTier.UNCACHED, bytecodeNode.getTier());
        NameAndSize uncachedSize = calculateContinueAtSize(bytecodeNode);

        bytecodeNode.setUncachedThreshold(0);
        ((RootNode) node).getCallTarget().call();

        bytecodeNode = node.getBytecodeNode();
        assertEquals(BytecodeTier.CACHED, bytecodeNode.getTier());
        NameAndSize cachedSize = calculateContinueAtSize(node.getBytecodeNode());

        return new ContinueAtSizes(cachedSize, uncachedSize);
    }

    record ContinueAtSizes(NameAndSize cached, NameAndSize uncached) {
    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableSerialization = true, //
                    enableYield = true, //
                    boxingEliminationTypes = {boolean.class}, //
                    enableUncachedInterpreter = true)
    abstract static class OneOperationNode extends RootNode implements BytecodeRootNode {

        protected OneOperationNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Op1 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableSerialization = true, //
                    enableYield = true, //
                    boxingEliminationTypes = {boolean.class}, //
                    enableUncachedInterpreter = true)
    abstract static class TwoOperationNode extends RootNode implements BytecodeRootNode {

        protected TwoOperationNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Op1 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op2 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableSerialization = true, //
                    enableYield = true, //
                    boxingEliminationTypes = {boolean.class}, //
                    enableUncachedInterpreter = true)
    abstract static class TwentyOperationNode extends RootNode implements BytecodeRootNode {

        protected TwentyOperationNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Op1 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op2 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op3 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op4 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op5 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op6 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op7 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op8 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op9 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op10 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op11 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op12 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op13 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op14 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op15 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op16 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op17 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op18 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op19 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

        @Operation
        static final class Op20 {
            @Specialization
            static int doDefault(int a, int b) {
                return a + b;
            }
        }

    }

    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                    enableSerialization = true, //
                    enableYield = true, //
                    boxingEliminationTypes = {boolean.class, int.class, byte.class, long.class, float.class, double.class}, //
                    enableUncachedInterpreter = true)
    @TypeSystemReference(CastEverythingToDoubleTypeSystem.class)
    @SuppressWarnings({"unused", "truffle"})
    abstract static class ManyInstructionNode extends RootNode implements BytecodeRootNode {

        protected ManyInstructionNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        static final class Op1 {
            @Specialization
            static double doDefault(double a0, double a1, double a2) {
                return 1.0d;
            }
        }

        @Operation
        static final class Op2 {
            @Specialization
            static double doDefault(double a0, double a1, double a3) {
                return 1.0d;
            }
        }

        @Operation
        static final class Op3 {
            @Specialization
            static double doDefault(double a0, double a1) {
                return 1.0d;
            }
        }

        @Operation
        static final class Op4 {
            @Specialization
            static double doDefault(double a0, double a1) {
                return 1.0d;
            }
        }

        @Operation
        static final class Op5 {
            @Specialization
            static double doDefault(double a0, double a1) {
                return 1.0d;
            }
        }

        // Though it is a custom operation, the yield should not be outlined.
        @Yield
        static final class MyYield {
            @Specialization
            static Object doDefault(Object result, @Bind ContinuationRootNode root, @Bind MaterializedFrame frame) {
                return result;
            }
        }
    }

    @TypeSystem
    static class CastEverythingToDoubleTypeSystem {

        @ImplicitCast
        static double intToDouble(int v) {
            return v;
        }

        @ImplicitCast
        static double byteToDouble(byte v) {
            return v;
        }

        @ImplicitCast
        static double shortToDouble(short v) {
            return v;
        }

        @ImplicitCast
        static double floatToDouble(float v) {
            return v;
        }

        @ImplicitCast
        static double floatToDouble(boolean v) {
            return v ? 1.0 : 0.0;
        }

    }

    private static OneOperationNode parse1(BytecodeParser<OneOperationNodeGen.Builder> builder) {
        BytecodeRootNodes<OneOperationNode> nodes = OneOperationNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    private static TwoOperationNode parse2(BytecodeParser<TwoOperationNodeGen.Builder> builder) {
        BytecodeRootNodes<TwoOperationNode> nodes = TwoOperationNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    private static TwentyOperationNode parse20(BytecodeParser<TwentyOperationNodeGen.Builder> builder) {
        BytecodeRootNodes<TwentyOperationNode> nodes = TwentyOperationNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    private static ManyInstructionNode parseMany(BytecodeParser<ManyInstructionNodeGen.Builder> builder) {
        BytecodeRootNodes<ManyInstructionNode> nodes = ManyInstructionNodeGen.create(null, BytecodeConfig.DEFAULT, builder);
        return nodes.getNode(nodes.count() - 1);
    }

    private static NameAndSize calculateContinueAtSize(BytecodeNode bytecodeNode) {
        int maxSize = 0;
        String methodName = null;
        for (Method m : bytecodeNode.getClass().getDeclaredMethods()) {
            if (m.getName().startsWith("continueAt")) {
                int size = BytecodeSize.getMethodCodeSize(m);
                if (size > maxSize) {
                    maxSize = size;
                    methodName = bytecodeNode.getClass().getName() + "." + m.getName() + "(...)";
                }
            }
        }
        return new NameAndSize(methodName, maxSize);
    }

    record NameAndSize(String name, int size) {
    }

    static final class BytecodeSize {

        private BytecodeSize() {
        }

        public static int getMethodCodeSize(Method method) {
            Class<?> owner = method.getDeclaringClass();
            String name = method.getName();
            String descriptor = com.oracle.truffle.api.impl.asm.Type.getMethodDescriptor(method);
            return getMethodCodeSize(owner, name, descriptor);
        }

        public static int getMethodCodeSize(Class<?> clazz, String methodName, String methodDescriptor) {
            String resourceName = clazz.getName().replace('.', '/') + ".class";

            try (InputStream in = clazz.getClassLoader().getResourceAsStream(resourceName)) {
                if (in == null) {
                    throw new IllegalArgumentException("Class file not found for " + clazz.getName());
                }
                byte[] bytes = in.readAllBytes();
                return getMethodCodeSize(bytes, methodName, methodDescriptor);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        // --- low-level parser below ---

        private static int getMethodCodeSize(byte[] classFile,
                        String methodName,
                        String methodDescriptor) {
            int index = 0;

            // magic
            int magic = readInt(classFile, index);
            index += 4;
            if (magic != 0xCAFEBABE) {
                throw new IllegalArgumentException("Not a class file");
            }

            // minor_version, major_version
            index += 2; // minor
            index += 2; // major

            // constant_pool_count
            int cpCount = readUnsignedShort(classFile, index);
            index += 2;

            Object[] cp = new Object[cpCount];

            // constant_pool[cpCount-1]
            for (int i = 1; i < cpCount;) {
                int tag = classFile[index++] & 0xFF;
                switch (tag) {
                    case 1: { // CONSTANT_Utf8
                        int len = readUnsignedShort(classFile, index);
                        index += 2;
                        String s = new String(classFile, index, len, StandardCharsets.UTF_8);
                        cp[i] = s;
                        index += len;
                        break;
                    }
                    case 3:  // int
                    case 4:  // float
                    case 9:  // fieldref
                    case 10: // methodref
                    case 11: // interfaceMethodref
                    case 12: // nameAndType
                    case 18: // invokeDynamic
                    case 17: // dynamic (if present)
                        index += 4;
                        break;
                    case 5:  // long
                    case 6:  // double
                        index += 8;
                        i++; // takes two entries
                        break;
                    case 7:  // Class
                    case 8:  // String
                    case 16: // MethodType
                        index += 2;
                        break;
                    case 15: // MethodHandle
                        index += 3;
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported constant pool tag: " + tag);
                }
                i++;
            }

            // access_flags, this_class, super_class
            index += 2; // access_flags
            index += 2; // this_class
            index += 2; // super_class

            // interfaces
            int interfacesCount = readUnsignedShort(classFile, index);
            index += 2 + (2 * interfacesCount);

            // fields
            int fieldsCount = readUnsignedShort(classFile, index);
            index += 2;
            for (int i = 0; i < fieldsCount; i++) {
                index += 2; // access_flags
                index += 2; // name_index
                index += 2; // descriptor_index
                int attributesCount = readUnsignedShort(classFile, index);
                index += 2;
                for (int j = 0; j < attributesCount; j++) {
                    index += 2; // attribute_name_index
                    int attrLength = readInt(classFile, index);
                    index += 4 + attrLength;
                }
            }

            // methods
            int methodsCount = readUnsignedShort(classFile, index);
            index += 2;

            for (int i = 0; i < methodsCount; i++) {
                index += 2; // access_flags
                int nameIndex = readUnsignedShort(classFile, index);
                index += 2;
                int descIndex = readUnsignedShort(classFile, index);
                index += 2;

                String mName = (String) cp[nameIndex];
                String mDesc = (String) cp[descIndex];

                int attributesCount = readUnsignedShort(classFile, index);
                index += 2;

                boolean match = methodName.equals(mName) && methodDescriptor.equals(mDesc);

                for (int j = 0; j < attributesCount; j++) {
                    int attrNameIndex = readUnsignedShort(classFile, index);
                    index += 2;
                    int attrLength = readInt(classFile, index);
                    index += 4;

                    String attrName = (String) cp[attrNameIndex];

                    if (match && "Code".equals(attrName)) {
                        int codeIdx = index;

                        // u2 max_stack; u2 max_locals; u4 code_length;
                        /* int maxStack = */ readUnsignedShort(classFile, codeIdx);
                        codeIdx += 2;
                        /* int maxLocals = */ readUnsignedShort(classFile, codeIdx);
                        codeIdx += 2;
                        int codeLength = readInt(classFile, codeIdx);

                        return codeLength;
                    }

                    // skip attribute_info
                    index += attrLength;
                }
            }

            throw new IllegalArgumentException(
                            "Method " + methodName + methodDescriptor + " not found or has no Code attribute");
        }

        private static int readInt(byte[] b, int idx) {
            return ((b[idx] & 0xFF) << 24) | ((b[idx + 1] & 0xFF) << 16) | ((b[idx + 2] & 0xFF) << 8) | (b[idx + 3] & 0xFF);
        }

        private static int readUnsignedShort(byte[] b, int idx) {
            return ((b[idx] & 0xFF) << 8) | (b[idx + 1] & 0xFF);
        }

    }

}
