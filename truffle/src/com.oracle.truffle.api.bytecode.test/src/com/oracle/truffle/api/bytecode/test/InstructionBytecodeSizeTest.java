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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.asm.ClassReader;
import com.oracle.truffle.api.impl.asm.ClassVisitor;
import com.oracle.truffle.api.impl.asm.MethodVisitor;
import com.oracle.truffle.api.impl.asm.Opcodes;
import com.oracle.truffle.api.impl.asm.commons.CodeSizeEvaluator;
import com.oracle.truffle.api.nodes.RootNode;

public class InstructionBytecodeSizeTest {

    private static final int CACHED_INSTRUCTION_SIZE = 29;
    private static final int UNCACHED_INSTRUCTION_SIZE = 29;

    // !Important: Keep these in sync with BytecodeDSLNodeFactory!
    // Estimated number of Java bytecodes per instruction. Should be max(cached, uncached).
    public static final int ESTIMATED_INSTRUCTION_SIZE = 34;
    // Estimated number of java bytecodes needed for a bytecode loop
    public static final int ESTIMATED_BYTECODE_FOOTPRINT = 2000;

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
        ContinueAtSizes expectedSize = new ContinueAtSizes(CACHED_INSTRUCTION_SIZE, UNCACHED_INSTRUCTION_SIZE);
        assertEquals(expectedSize, computeSingleInstructionSize(size1, size2, 2));
        assertEquals(expectedSize, computeSingleInstructionSize(size1, size20, 20));

        int estimatedSize1 = ESTIMATED_BYTECODE_FOOTPRINT + (ESTIMATED_INSTRUCTION_SIZE * countInstructions(node1));
        int estimatedSize2 = ESTIMATED_BYTECODE_FOOTPRINT + (ESTIMATED_INSTRUCTION_SIZE * countInstructions(node2));
        int estimatedSize20 = ESTIMATED_BYTECODE_FOOTPRINT + (ESTIMATED_INSTRUCTION_SIZE * countInstructions(node20));

        // test that we always overestimate
        assertTrue("Expected " + size1.cached + " > " + estimatedSize1, estimatedSize1 > size1.cached);
        assertTrue("Expected " + size1.uncached + " > " + estimatedSize1, estimatedSize1 > size1.uncached);

        assertTrue("Expected " + size2.cached + " > " + estimatedSize2, estimatedSize2 > size2.cached);
        assertTrue("Expected " + size2.uncached + " > " + estimatedSize2, estimatedSize2 > size2.uncached);

        assertTrue("Expected " + size20.cached + " > " + estimatedSize20, estimatedSize20 > size20.cached);
        assertTrue("Expected " + size20.uncached + " > " + estimatedSize20, estimatedSize20 > size20.uncached);
    }

    @Test
    public void testMany() throws Exception {
        ManyInstructionNode node = parseMany((b) -> {
            b.beginRoot();
            b.endRoot();
        });
        ContinueAtSizes size1 = calculateSizes(node);
        assertTrue(String.valueOf(size1.cached()), size1.cached() < 8000);
        assertTrue(String.valueOf(size1.uncached()), size1.uncached() < 8000);
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

        int cachedDiff = sizeN.cached - size1.cached;
        int cachedSingle = cachedDiff / (n - 1);

        int uncachedDiff = sizeN.uncached - size1.uncached;
        int uncachedSingle = uncachedDiff / (n - 1);

        return new ContinueAtSizes(cachedSingle, uncachedSingle);
    }

    private static ContinueAtSizes calculateSizes(BytecodeRootNode node) throws IOException {
        BytecodeNode bytecodeNode = node.getBytecodeNode();
        assertEquals(BytecodeTier.UNCACHED, bytecodeNode.getTier());
        int uncachedSize = calculateContinueAtSize(bytecodeNode);

        bytecodeNode.setUncachedThreshold(0);
        ((RootNode) node).getCallTarget().call();

        bytecodeNode = node.getBytecodeNode();
        assertEquals(BytecodeTier.CACHED, bytecodeNode.getTier());
        int cachedSize = calculateContinueAtSize(node.getBytecodeNode());

        return new ContinueAtSizes(cachedSize, uncachedSize);
    }

    record ContinueAtSizes(int cached, int uncached) {
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
            static double doDefault(double a0, double a1) {
                return 1.0d;
            }
        }

        @Operation
        static final class Op2 {
            @Specialization
            static double doDefault(double a0, double a1) {
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

    private static int calculateContinueAtSize(BytecodeNode bytecodeNode) throws IOException {
        byte[] classBytes = loadClassBytes(bytecodeNode.getClass());
        int size = getMaxMethodBytecodeSize(classBytes, "continueAt");
        return size;
    }

    private static byte[] loadClassBytes(Class<?> clazz) throws IOException {
        String className = clazz.getName().replace('.', '/') + ".class";
        try (java.io.InputStream is = clazz.getClassLoader().getResourceAsStream(className)) {
            return is.readAllBytes();
        }
    }

    private static int getMaxMethodBytecodeSize(byte[] classBytes, String pattern) {
        ClassReader reader = new ClassReader(classBytes);
        MethodSizeFinder finder = new MethodSizeFinder(Opcodes.ASM9, pattern);
        reader.accept(finder, 0);
        int maxSize = 0;
        for (var entry : finder.sizeVisitor.entrySet()) {
            CodeSizeEvaluator evaluator = entry.getValue();
            maxSize = Math.max(maxSize, evaluator.getMaxSize());
        }
        return maxSize;
    }

    static class MethodSizeFinder extends ClassVisitor {
        private String pattern;
        Map<String, CodeSizeEvaluator> sizeVisitor = new HashMap<>();

        MethodSizeFinder(int api, String name) {
            super(api);
            this.pattern = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            final MethodVisitor visitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.startsWith(pattern)) {
                return sizeVisitor.computeIfAbsent(name, (key) -> new CodeSizeEvaluator(visitor));
            }
            return visitor;
        }

    }

}
