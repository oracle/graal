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

import static com.oracle.graal.api.code.UnsignedMath.*;
import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.nodes.extended.UnsafeArrayCastNode.*;
import static com.oracle.graal.nodes.extended.UnsafeCastNode.*;
import static com.oracle.graal.snippets.Snippet.Varargs.*;
import static com.oracle.graal.snippets.SnippetTemplate.*;
import static com.oracle.graal.snippets.SnippetTemplate.Arguments.*;
import static com.oracle.graal.snippets.nodes.BranchProbabilityNode.*;
import static com.oracle.graal.snippets.nodes.ExplodeLoopNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.Snippet.VarargsParameter;
import com.oracle.graal.snippets.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.snippets.SnippetTemplate.Arguments;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.snippets.nodes.*;
import com.oracle.graal.word.*;

/**
 * Snippets used for implementing NEW, ANEWARRAY and NEWARRAY.
 */
public class NewObjectSnippets implements SnippetsInterface {

    // @formatter:off

    private static void trace(boolean enabled, String format, WordBase value) {
        if (enabled) {
            Log.printf(format, value.rawValue());
        }
    }
    @Snippet
    public static Word allocate(@Parameter("size") int size) {
        Word thread = thread();
        Word top = thread.readWord(threadTlabTopOffset());
        Word end = thread.readWord(threadTlabEndOffset());
        Word newTop = top.add(size);
        // this check might lead to problems if the TLAB is within 16GB of the address space end (checked in c++ code)
        if (newTop.belowOrEqual(end)) {
            probability(FAST_PATH_PROBABILITY);
            thread.writeWord(threadTlabTopOffset(), newTop);
            return top;
        }
        return Word.zero();
    }

    @Snippet
    public static Object initializeObject(
                    @Parameter("memory") Word memory,
                    @Parameter("hub") Word hub,
                    @Parameter("prototypeMarkWord") Word prototypeMarkWord,
                    @ConstantParameter("size") int size,
                    @ConstantParameter("fillContents") boolean fillContents,
                    @ConstantParameter("locked") boolean locked) {

        Object result;
        if (memory.equal(0)) {
            new_stub.inc();
            result = NewInstanceStubCall.call(hub);
        } else {
            probability(FAST_PATH_PROBABILITY);
            if (locked) {
                formatObject(hub, size, memory, thread().or(biasedLockPattern()), fillContents);
            } else {
                formatObject(hub, size, memory, prototypeMarkWord, fillContents);
            }
            result = memory.toObject();
        }
        return unsafeCast(verifyOop(result), StampFactory.forNodeIntrinsic());
    }

    @Snippet
    public static Object initializeArray(
                    @Parameter("memory") Word memory,
                    @Parameter("hub") Word hub,
                    @Parameter("length") int length,
                    @Parameter("allocationSize") int allocationSize,
                    @Parameter("prototypeMarkWord") Word prototypeMarkWord,
                    @ConstantParameter("headerSize") int headerSize,
                    @ConstantParameter("fillContents") boolean fillContents,
                    @ConstantParameter("locked") boolean locked) {
        if (locked) {
            return initializeArray(memory, hub, length, allocationSize, thread().or(biasedLockPattern()), headerSize, fillContents);
        } else {
            return initializeArray(memory, hub, length, allocationSize, prototypeMarkWord, headerSize, fillContents);
        }
    }

    private static Object initializeArray(Word memory, Word hub, int length, int allocationSize, Word prototypeMarkWord, int headerSize, boolean fillContents) {
        Object result;
        if (memory.equal(0)) {
            newarray_stub.inc();
            result = NewArrayStubCall.call(hub, length);
        } else {
            probability(FAST_PATH_PROBABILITY);
            newarray_loopInit.inc();
            formatArray(hub, allocationSize, length, headerSize, memory, prototypeMarkWord, fillContents);
            result = memory.toObject();
        }
        return unsafeArrayCast(verifyOop(result), length, StampFactory.forNodeIntrinsic());
    }

    /**
     * Maximum array length for which fast path allocation is used.
     */
    public static final int MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH = 0x00FFFFFF;

    @Snippet
    public static Object allocateArrayAndInitialize(
                    @Parameter("length") int length,
                    @ConstantParameter("alignment") int alignment,
                    @ConstantParameter("headerSize") int headerSize,
                    @ConstantParameter("log2ElementSize") int log2ElementSize,
                    @ConstantParameter("type") ResolvedJavaType type) {
        if (!belowThan(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
            probability(DEOPT_PATH_PROBABILITY);
            // This handles both negative array sizes and very large array sizes
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        int allocationSize = computeArrayAllocationSize(length, alignment, headerSize, log2ElementSize);
        Word memory = TLABAllocateNode.allocateVariableSize(allocationSize);
        return InitializeArrayNode.initialize(memory, length, allocationSize, type, true, false);
    }

    /**
     * Computes the size of the memory chunk allocated for an array. This size accounts for the array
     * header size, boy size and any padding after the last element to satisfy object alignment requirements.
     *
     * @param length the number of elements in the array
     * @param alignment the object alignment requirement
     * @param headerSize the size of the array header
     * @param log2ElementSize log2 of the size of an element in the array
     */
    public static int computeArrayAllocationSize(int length, int alignment, int headerSize, int log2ElementSize) {
        int size = (length << log2ElementSize) + headerSize + (alignment - 1);
        int mask = ~(alignment - 1);
        return size & mask;
    }

    /**
     * Calls the runtime stub for implementing MULTIANEWARRAY.
     */
    @Snippet
    public static Object newmultiarray(
                    @Parameter("hub") Word hub,
                    @ConstantParameter("rank") int rank,
                    @VarargsParameter("dimensions") int[] dimensions) {
        Word dims = DimensionsNode.allocaDimsArray(rank);
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < rank; i++) {
            dims.writeInt(i * 4, dimensions[i]);
        }
        return NewMultiArrayStubCall.call(hub, rank, dims);
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
    private static void formatObject(Word hub, int size, Word memory, Word compileTimePrototypeMarkWord, boolean fillContents) {
        Word prototypeMarkWord = useBiasedLocking() ? hub.readWord(prototypeMarkWordOffset()) : compileTimePrototypeMarkWord;
        memory.writeWord(markOffset(), prototypeMarkWord);
        memory.writeWord(hubOffset(), hub);
        if (fillContents) {
            if (size <= MAX_UNROLLED_OBJECT_ZEROING_SIZE) {
                new_seqInit.inc();
                explodeLoop();
                for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
                    memory.writeWord(offset, Word.zero());
                }
            } else {
                new_loopInit.inc();
                for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
                    memory.writeWord(offset, Word.zero());
                }
            }
        }
    }

    /**
     * Formats some allocated memory with an object header zeroes out the rest.
     */
    public static void formatArray(Word hub, int allocationSize, int length, int headerSize, Word memory, Word prototypeMarkWord, boolean fillContents) {
        memory.writeWord(markOffset(), prototypeMarkWord);
        memory.writeInt(arrayLengthOffset(), length);
        // store hub last as the concurrent garbage collectors assume length is valid if hub field is not null
        memory.writeWord(hubOffset(), hub);
        if (fillContents) {
            for (int offset = headerSize; offset < allocationSize; offset += wordSize()) {
                memory.writeWord(offset, Word.zero());
            }
        }
    }

    // @formatter:on

    public static class Templates extends AbstractTemplates<NewObjectSnippets> {

        private final ResolvedJavaMethod allocate;
        private final ResolvedJavaMethod initializeObject;
        private final ResolvedJavaMethod initializeArray;
        private final ResolvedJavaMethod allocateArrayAndInitialize;
        private final ResolvedJavaMethod newmultiarray;
        private final TargetDescription target;
        private final boolean useTLAB;

        public Templates(CodeCacheProvider runtime, Assumptions assumptions, TargetDescription target, boolean useTLAB) {
            super(runtime, assumptions, target, NewObjectSnippets.class);
            this.target = target;
            this.useTLAB = useTLAB;
            allocate = snippet("allocate", int.class);
            initializeObject = snippet("initializeObject", Word.class, Word.class, Word.class, int.class, boolean.class, boolean.class);
            initializeArray = snippet("initializeArray", Word.class, Word.class, int.class, int.class, Word.class, int.class, boolean.class, boolean.class);
            allocateArrayAndInitialize = snippet("allocateArrayAndInitialize", int.class, int.class, int.class, int.class, ResolvedJavaType.class);
            newmultiarray = snippet("newmultiarray", Word.class, int.class, int[].class);
        }

        /**
         * Lowers a {@link NewInstanceNode}.
         */
        @SuppressWarnings("unused")
        public void lower(NewInstanceNode newInstanceNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) newInstanceNode.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) newInstanceNode.instanceClass();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, graph);
            int size = instanceSize(type);

            ValueNode memory;
            if (!useTLAB) {
                memory = ConstantNode.defaultForKind(target.wordKind, graph);
            } else {
                ConstantNode sizeNode = ConstantNode.forInt(size, graph);
                TLABAllocateNode tlabAllocateNode = graph.add(new TLABAllocateNode(sizeNode));
                graph.addBeforeFixed(newInstanceNode, tlabAllocateNode);
                memory = tlabAllocateNode;
            }
            InitializeObjectNode initializeNode = graph.add(new InitializeObjectNode(memory, type, newInstanceNode.fillContents(), newInstanceNode.locked()));
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
            ResolvedJavaType arrayType = elementType.getArrayClass();
            Kind elementKind = elementType.getKind();
            final int alignment = target.wordSize;
            final int headerSize = HotSpotRuntime.getArrayBaseOffset(elementKind);
            final Integer length = lengthNode.isConstant() ? Integer.valueOf(lengthNode.asConstant().asInt()) : null;
            int log2ElementSize = CodeUtil.log2(target.sizeInBytes(elementKind));
            if (!useTLAB) {
                ConstantNode zero = ConstantNode.defaultForKind(target.wordKind, graph);
                // value for 'size' doesn't matter as it isn't used since a stub call will be made
                // anyway
                // for both allocation and initialization - it just needs to be non-null
                ConstantNode size = ConstantNode.forInt(-1, graph);
                InitializeArrayNode initializeNode = graph.add(new InitializeArrayNode(zero, lengthNode, size, arrayType, newArrayNode.fillContents(), newArrayNode.locked()));
                graph.replaceFixedWithFixed(newArrayNode, initializeNode);
            } else if (length != null && belowThan(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
                // Calculate aligned size
                int size = computeArrayAllocationSize(length, alignment, headerSize, log2ElementSize);
                ConstantNode sizeNode = ConstantNode.forInt(size, graph);
                tlabAllocateNode = graph.add(new TLABAllocateNode(sizeNode));
                graph.addBeforeFixed(newArrayNode, tlabAllocateNode);
                InitializeArrayNode initializeNode = graph.add(new InitializeArrayNode(tlabAllocateNode, lengthNode, sizeNode, arrayType, newArrayNode.fillContents(), newArrayNode.locked()));
                graph.replaceFixedWithFixed(newArrayNode, initializeNode);
            } else {
                Key key = new Key(allocateArrayAndInitialize).add("alignment", alignment).add("headerSize", headerSize).add("log2ElementSize", log2ElementSize).add("type", arrayType);
                Arguments arguments = new Arguments().add("length", lengthNode);
                SnippetTemplate template = cache.get(key, assumptions);
                Debug.log("Lowering allocateArrayAndInitialize in %s: node=%s, template=%s, arguments=%s", graph, newArrayNode, template, arguments);
                template.instantiate(runtime, newArrayNode, DEFAULT_REPLACER, arguments);
            }
        }

        @SuppressWarnings("unused")
        public void lower(TLABAllocateNode tlabAllocateNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) tlabAllocateNode.graph();
            ValueNode size = tlabAllocateNode.size();
            Key key = new Key(allocate);
            Arguments arguments = arguments("size", size);
            SnippetTemplate template = cache.get(key, assumptions);
            Debug.log("Lowering fastAllocate in %s: node=%s, template=%s, arguments=%s", graph, tlabAllocateNode, template, arguments);
            template.instantiate(runtime, tlabAllocateNode, DEFAULT_REPLACER, arguments);
        }

        @SuppressWarnings("unused")
        public void lower(InitializeObjectNode initializeNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) initializeNode.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) initializeNode.type();
            assert !type.isArray();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, graph);
            int size = instanceSize(type);
            Key key = new Key(initializeObject).add("size", size).add("fillContents", initializeNode.fillContents()).add("locked", initializeNode.locked());
            ValueNode memory = initializeNode.memory();
            Arguments arguments = arguments("memory", memory).add("hub", hub).add("prototypeMarkWord", type.prototypeMarkWord());
            SnippetTemplate template = cache.get(key, assumptions);
            Debug.log("Lowering initializeObject in %s: node=%s, template=%s, arguments=%s", graph, initializeNode, template, arguments);
            template.instantiate(runtime, initializeNode, DEFAULT_REPLACER, arguments);
        }

        @SuppressWarnings("unused")
        public void lower(InitializeArrayNode initializeNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) initializeNode.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) initializeNode.type();
            ResolvedJavaType elementType = type.getComponentType();
            assert elementType != null;
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, graph);
            Kind elementKind = elementType.getKind();
            final int headerSize = HotSpotRuntime.getArrayBaseOffset(elementKind);
            Key key = new Key(initializeArray).add("headerSize", headerSize).add("fillContents", initializeNode.fillContents()).add("locked", initializeNode.locked());
            ValueNode memory = initializeNode.memory();
            Arguments arguments = arguments("memory", memory).add("hub", hub).add("prototypeMarkWord", type.prototypeMarkWord()).add("allocationSize", initializeNode.allocationSize()).add("length",
                            initializeNode.length());
            SnippetTemplate template = cache.get(key, assumptions);
            Debug.log("Lowering initializeArray in %s: node=%s, template=%s, arguments=%s", graph, initializeNode, template, arguments);
            template.instantiate(runtime, initializeNode, DEFAULT_REPLACER, arguments);
        }

        @SuppressWarnings("unused")
        public void lower(NewMultiArrayNode newmultiarrayNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) newmultiarrayNode.graph();
            int rank = newmultiarrayNode.dimensionCount();
            ValueNode[] dims = new ValueNode[rank];
            for (int i = 0; i < newmultiarrayNode.dimensionCount(); i++) {
                dims[i] = newmultiarrayNode.dimension(i);
            }
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) newmultiarrayNode.type();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, graph);
            Key key = new Key(newmultiarray).add("dimensions", vargargs(new int[rank], StampFactory.forKind(Kind.Int))).add("rank", rank);
            Arguments arguments = arguments("dimensions", dims).add("hub", hub);
            SnippetTemplate template = cache.get(key, assumptions);
            template.instantiate(runtime, newmultiarrayNode, DEFAULT_REPLACER, arguments);
        }

        private static int instanceSize(HotSpotResolvedObjectType type) {
            int size = type.instanceSize();
            assert (size % wordSize()) == 0;
            assert size >= 0;
            return size;
        }
    }

    private static final SnippetCounter.Group countersNew = GraalOptions.SnippetCounters ? new SnippetCounter.Group("NewInstance") : null;
    private static final SnippetCounter new_seqInit = new SnippetCounter(countersNew, "tlabSeqInit", "TLAB alloc with unrolled zeroing");
    private static final SnippetCounter new_loopInit = new SnippetCounter(countersNew, "tlabLoopInit", "TLAB alloc with zeroing in a loop");
    private static final SnippetCounter new_stub = new SnippetCounter(countersNew, "stub", "alloc and zeroing via stub");

    private static final SnippetCounter.Group countersNewArray = GraalOptions.SnippetCounters ? new SnippetCounter.Group("NewArray") : null;
    private static final SnippetCounter newarray_loopInit = new SnippetCounter(countersNewArray, "tlabLoopInit", "TLAB alloc with zeroing in a loop");
    private static final SnippetCounter newarray_stub = new SnippetCounter(countersNewArray, "stub", "alloc and zeroing via stub");
}
