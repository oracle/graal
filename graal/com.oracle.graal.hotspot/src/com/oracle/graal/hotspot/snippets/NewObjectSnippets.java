/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.hotspot.snippets;

import static com.oracle.graal.hotspot.nodes.CastFromHub.*;
import static com.oracle.graal.hotspot.nodes.RegisterNode.*;
import static com.oracle.graal.hotspot.snippets.DirectObjectStoreNode.*;
import static com.oracle.graal.nodes.extended.UnsafeLoadNode.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;
import static com.oracle.graal.snippets.nodes.ExplodeLoopNode.*;
import static com.oracle.max.asm.target.amd64.AMD64.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Fold;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Cache;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.max.criutils.*;

/**
 * Snippets used for implementing NEW, ANEWARRAY and NEWARRAY.
 */
public class NewObjectSnippets implements SnippetsInterface {

    @Snippet
    public static Word allocate(@Parameter("size") int size) {
        Word thread = asWord(register(r15, wordKind()));
        Word top = loadWord(thread, threadTlabTopOffset());
        Word end = loadWord(thread, threadTlabEndOffset());
        Word newTop = top.plus(size);
        if (newTop.belowOrEqual(end)) {
            storeObject(thread, 0, threadTlabTopOffset(), newTop);
            return top;
        }
        return Word.zero();
    }

    @Snippet
    public static Object initializeObject(
                    @Parameter("memory") Word memory,
                    @Parameter("hub") Object hub,
                    @Parameter("initialMarkWord") Word initialMarkWord,
                    @ConstantParameter("size") int size) {

        if (memory == Word.zero()) {
            return NewInstanceStubCall.call(hub);
        }
        formatObject(hub, size, memory, initialMarkWord);
        Object instance = memory.toObject();
        return castFromHub(verifyOop(instance), hub);
    }

    @Snippet
    public static Object initializeObjectArray(
                    @Parameter("memory") Word memory,
                    @Parameter("hub") Object hub,
                    @Parameter("length") int length,
                    @Parameter("size") int size,
                    @Parameter("initialMarkWord") Word initialMarkWord,
                    @ConstantParameter("headerSize") int headerSize) {
        return initializeArray(memory, hub, length, size, initialMarkWord, headerSize, true);
    }

    @Snippet
    public static Object initializePrimitiveArray(
                    @Parameter("memory") Word memory,
                    @Parameter("hub") Object hub,
                    @Parameter("length") int length,
                    @Parameter("size") int size,
                    @Parameter("initialMarkWord") Word initialMarkWord,
                    @ConstantParameter("headerSize") int headerSize) {
        return initializeArray(memory, hub, length, size, initialMarkWord, headerSize, false);
    }

    private static Object initializeArray(Word memory, Object hub, int length, int size, Word initialMarkWord, int headerSize, boolean isObjectArray) {
        if (memory == Word.zero()) {
            return NewArrayStubCall.call(isObjectArray, hub, length);
        }
        formatArray(hub, size, length, headerSize, memory, initialMarkWord);
        Object instance = memory.toObject();
        return castFromHub(verifyOop(instance), hub);
    }

    /**
     * Maximum array length for which fast path allocation is used.
     */
    private static final int MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH = 0x00FFFFFF;

    @Snippet
    public static Object allocateArrayAndInitialize(
                    @Parameter("length") int length,
                    @ConstantParameter("alignment") int alignment,
                    @ConstantParameter("headerSize") int headerSize,
                    @ConstantParameter("log2ElementSize") int log2ElementSize,
                    @ConstantParameter("type") ResolvedJavaType type,
                    @ConstantParameter("wordKind") Kind wordKind) {
        if (UnsignedMath.aboveOrEqual(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
            // This handles both negative array sizes and very large array sizes
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.RuntimeConstraint);
        }
        int size = getArraySize(length, alignment, headerSize, log2ElementSize);
        Word memory = TLABAllocateNode.allocateVariableSize(wordKind, size);
        return InitializeArrayNode.initialize(memory, length, size, type);
    }

    public static int getArraySize(int length, int alignment, int headerSize, int log2ElementSize) {
        int size = (length << log2ElementSize) + headerSize + (alignment - 1);
        int mask = ~(alignment - 1);
        return size & mask;
    }

    private static Object verifyOop(Object object) {
        if (verifyOops()) {
            VerifyOopStubCall.call(object);
        }
        return object;
    }

    private static Word asWord(Object object) {
        return Word.fromObject(object);
    }

    private static Word loadWord(Word address, int offset) {
        Object value = loadObject(address, 0, offset, true);
        return asWord(value);
    }

    /**
     * Maximum size of an object whose body is initialized by a sequence of
     * zero-stores to its fields. Larger objects have their bodies initialized
     * in a loop.
     */
    private static final int MAX_UNROLLED_OBJECT_ZEROING_SIZE = 10 * wordSize();

    /**
     * Formats some allocated memory with an object header zeroes out the rest.
     */
    private static void formatObject(Object hub, int size, Word memory, Word headerPrototype) {
        storeObject(memory, 0, markOffset(), headerPrototype);
        storeObject(memory, 0, hubOffset(), hub);
        if (size <= MAX_UNROLLED_OBJECT_ZEROING_SIZE) {
            explodeLoop();
            for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
                storeWord(memory, 0, offset, Word.zero());
            }
        } else {
            for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
                storeWord(memory, 0, offset, Word.zero());
            }
        }
    }

    /**
     * Formats some allocated memory with an object header zeroes out the rest.
     */
    private static void formatArray(Object hub, int size, int length, int headerSize, Word memory, Word headerPrototype) {
        storeObject(memory, 0, markOffset(), headerPrototype);
        storeObject(memory, 0, hubOffset(), hub);
        storeInt(memory, 0, arrayLengthOffset(), length);
        for (int offset = headerSize; offset < size; offset += wordSize()) {
            storeWord(memory, 0, offset, Word.zero());
        }
    }

    public static class Templates {

        private final Cache cache;
        private final ResolvedJavaMethod allocate;
        private final ResolvedJavaMethod initializeObject;
        private final ResolvedJavaMethod initializeObjectArray;
        private final ResolvedJavaMethod initializePrimitiveArray;
        private final ResolvedJavaMethod allocateArrayAndInitialize;
        private final TargetDescription target;
        private final CodeCacheProvider runtime;
        private final boolean useTLAB;

        public Templates(CodeCacheProvider runtime, TargetDescription target, boolean useTLAB) {
            this.runtime = runtime;
            this.target = target;
            this.cache = new Cache(runtime);
            this.useTLAB = useTLAB;
            try {
                allocate = runtime.getResolvedJavaMethod(NewObjectSnippets.class.getDeclaredMethod("allocate", int.class));
                initializeObject = runtime.getResolvedJavaMethod(NewObjectSnippets.class.getDeclaredMethod("initializeObject", Word.class, Object.class, Word.class, int.class));
                initializeObjectArray = runtime.getResolvedJavaMethod(NewObjectSnippets.class.getDeclaredMethod("initializeObjectArray", Word.class, Object.class, int.class, int.class, Word.class, int.class));
                initializePrimitiveArray = runtime.getResolvedJavaMethod(NewObjectSnippets.class.getDeclaredMethod("initializePrimitiveArray", Word.class, Object.class, int.class, int.class, Word.class, int.class));
                allocateArrayAndInitialize = runtime.getResolvedJavaMethod(NewObjectSnippets.class.getDeclaredMethod("allocateArrayAndInitialize", int.class, int.class, int.class, int.class, ResolvedJavaType.class, Kind.class));
            } catch (NoSuchMethodException e) {
                throw new GraalInternalError(e);
            }
        }

        /**
         * Lowers a {@link NewInstanceNode}.
         */
        @SuppressWarnings("unused")
        public void lower(NewInstanceNode newInstanceNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) newInstanceNode.graph();
            HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) newInstanceNode.instanceClass();
            HotSpotKlassOop hub = type.klassOop();
            int size = type.instanceSize();
            assert (size % wordSize()) == 0;
            assert size >= 0;

            ValueNode memory;
            if (!useTLAB) {
                memory = ConstantNode.forConstant(new Constant(target.wordKind, 0L), runtime, graph);
            } else {
                TLABAllocateNode tlabAllocateNode = graph.add(new TLABAllocateNode(size, wordKind()));
                graph.addBeforeFixed(newInstanceNode, tlabAllocateNode);
                memory = tlabAllocateNode;
            }
            InitializeObjectNode initializeNode = graph.add(new InitializeObjectNode(memory, type));
            graph.replaceFixedWithFixed(newInstanceNode, initializeNode);
        }

        /**
         * Lowers a {@link NewArrayNode}.
         */
        @SuppressWarnings("unused")
        public void lower(NewArrayNode newArrayNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) newArrayNode.graph();
            ValueNode lengthNode = newArrayNode.length();
            TLABAllocateNode tlabAllocateNode;
            ResolvedJavaType elementType = newArrayNode.elementType();
            ResolvedJavaType arrayType = elementType.arrayOf();
            Kind elementKind = elementType.kind();
            final int alignment = target.wordSize;
            final int headerSize = elementKind.arrayBaseOffset();
            final Integer length = lengthNode.isConstant() ? Integer.valueOf(lengthNode.asConstant().asInt()) : null;
            int log2ElementSize = CodeUtil.log2(target.sizeInBytes(elementKind));
            if (!useTLAB) {
                ConstantNode zero = ConstantNode.forConstant(new Constant(target.wordKind, 0L), runtime, graph);
                // value for 'size' doesn't matter as it isn't used since a stub call will be made anyway
                // for both allocation and initialization - it just needs to be non-null
                ConstantNode size = ConstantNode.forInt(-1, graph);
                InitializeArrayNode initializeNode = graph.add(new InitializeArrayNode(zero, lengthNode, size, arrayType));
                graph.replaceFixedWithFixed(newArrayNode, initializeNode);
            } else if (length != null && UnsignedMath.belowThan(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
                // Calculate aligned size
                int size = getArraySize(length, alignment, headerSize, log2ElementSize);
                ConstantNode sizeNode = ConstantNode.forInt(size, graph);
                tlabAllocateNode = graph.add(new TLABAllocateNode(size, target.wordKind));
                graph.addBeforeFixed(newArrayNode, tlabAllocateNode);
                InitializeArrayNode initializeNode = graph.add(new InitializeArrayNode(tlabAllocateNode, lengthNode, sizeNode, arrayType));
                graph.replaceFixedWithFixed(newArrayNode, initializeNode);
            } else {
                Key key = new Key(allocateArrayAndInitialize).
                                add("alignment", alignment).
                                add("headerSize", headerSize).
                                add("log2ElementSize", log2ElementSize).
                                add("wordKind", target.wordKind).
                                add("type", arrayType);
                Arguments arguments = new Arguments().add("length", lengthNode);
                SnippetTemplate template = cache.get(key);
                Debug.log("Lowering allocateArrayAndInitialize in %s: node=%s, template=%s, arguments=%s", graph, newArrayNode, template, arguments);
                template.instantiate(runtime, newArrayNode, newArrayNode, arguments);
            }
        }

        @SuppressWarnings("unused")
        public void lower(TLABAllocateNode tlabAllocateNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) tlabAllocateNode.graph();
            ValueNode size;
            if (tlabAllocateNode.isSizeConstant()) {
                size = ConstantNode.forInt(tlabAllocateNode.constantSize(), graph);
            } else {
                size = tlabAllocateNode.variableSize();
            }
            Key key = new Key(allocate);
            Arguments arguments = arguments("size", size);
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering fastAllocate in %s: node=%s, template=%s, arguments=%s", graph, tlabAllocateNode, template, arguments);
            template.instantiate(runtime, tlabAllocateNode, tlabAllocateNode, arguments);
        }

        @SuppressWarnings("unused")
        public void lower(InitializeObjectNode initializeNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) initializeNode.graph();
            HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) initializeNode.type();
            assert !type.isArrayClass();
            HotSpotKlassOop hub = type.klassOop();
            int size = type.instanceSize();
            assert (size % wordSize()) == 0;
            assert size >= 0;
            Key key = new Key(initializeObject).add("size", size);
            ValueNode memory = initializeNode.memory();
            Arguments arguments = arguments("memory", memory).add("hub", hub).add("initialMarkWord", type.initialMarkWord());
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering initializeObject in %s: node=%s, template=%s, arguments=%s", graph, initializeNode, template, arguments);
            template.instantiate(runtime, initializeNode, initializeNode, arguments);
        }

        @SuppressWarnings("unused")
        public void lower(InitializeArrayNode initializeNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) initializeNode.graph();
            HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) initializeNode.type();
            ResolvedJavaType elementType = type.componentType();
            assert elementType != null;
            HotSpotKlassOop hub = type.klassOop();
            Kind elementKind = elementType.kind();
            final int headerSize = elementKind.arrayBaseOffset();
            Key key = new Key(elementKind.isObject() ? initializeObjectArray : initializePrimitiveArray).add("headerSize", headerSize);
            ValueNode memory = initializeNode.memory();
            Arguments arguments = arguments("memory", memory).add("hub", hub).add("initialMarkWord", type.initialMarkWord()).add("size", initializeNode.size()).add("length", initializeNode.length());
            SnippetTemplate template = cache.get(key);
            Debug.log("Lowering initializeObjectArray in %s: node=%s, template=%s, arguments=%s", graph, initializeNode, template, arguments);
            template.instantiate(runtime, initializeNode, initializeNode, arguments);
        }
    }

    @Fold
    private static boolean verifyOops() {
        return HotSpotGraalRuntime.getInstance().getConfig().verifyOops;
    }

    @Fold
    private static int threadTlabTopOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().threadTlabTopOffset;
    }

    @Fold
    private static int threadTlabEndOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().threadTlabEndOffset;
    }

    @Fold
    private static Kind wordKind() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordKind;
    }

    @Fold
    private static int wordSize() {
        return HotSpotGraalRuntime.getInstance().getTarget().wordSize;
    }

    @Fold
    private static int markOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().markOffset;
    }

    @Fold
    private static int hubOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().hubOffset;
    }

    @Fold
    private static int arrayLengthOffset() {
        return HotSpotGraalRuntime.getInstance().getConfig().arrayLengthOffset;
    }
}
