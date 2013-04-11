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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.code.UnsignedMath.*;
import static com.oracle.graal.hotspot.replacements.HotSpotSnippetUtils.*;
import static com.oracle.graal.nodes.extended.UnsafeArrayCastNode.*;
import static com.oracle.graal.nodes.extended.UnsafeCastNode.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;
import static com.oracle.graal.replacements.nodes.BranchProbabilityNode.*;
import static com.oracle.graal.replacements.nodes.ExplodeLoopNode.*;

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
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.Snippet.VarargsParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.word.*;

/**
 * Snippets used for implementing NEW, ANEWARRAY and NEWARRAY.
 */
public class NewObjectSnippets implements Snippets {

    @Snippet
    public static Word allocate(int size) {
        Word thread = thread();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        Word newTop = top.add(size);
        /*
         * this check might lead to problems if the TLAB is within 16GB of the address space end
         * (checked in c++ code)
         */
        if (newTop.belowOrEqual(end)) {
            probability(FAST_PATH_PROBABILITY);
            writeTlabTop(thread, newTop);
            return top;
        }
        return Word.zero();
    }

    @Snippet
    public static Object initializeObject(Word memory, Word hub, Word prototypeMarkWord, @ConstantParameter int size, @ConstantParameter boolean fillContents, @ConstantParameter boolean locked) {

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
        /*
         * make sure that the unsafeCast is anchored after initialization, see ReadAfterCheckCast
         * and CheckCastSnippets
         */
        BeginNode anchorNode = BeginNode.anchor(StampFactory.forNodeIntrinsic());
        return unsafeCast(verifyOop(result), StampFactory.forNodeIntrinsic(), anchorNode);
    }

    @Snippet
    public static Object initializeArray(Word memory, Word hub, int length, int allocationSize, Word prototypeMarkWord, @ConstantParameter int headerSize, @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean locked) {
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
        BeginNode anchorNode = BeginNode.anchor(StampFactory.forNodeIntrinsic());
        return unsafeArrayCast(verifyOop(result), length, StampFactory.forNodeIntrinsic(), anchorNode);
    }

    /**
     * Maximum array length for which fast path allocation is used.
     */
    public static final int MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH = 0x00FFFFFF;

    @Snippet
    public static Object allocateArrayAndInitialize(int length, @ConstantParameter int alignment, @ConstantParameter int headerSize, @ConstantParameter int log2ElementSize,
                    @ConstantParameter boolean fillContents, @ConstantParameter ResolvedJavaType type) {
        if (!belowThan(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
            probability(DEOPT_PATH_PROBABILITY);
            // This handles both negative array sizes and very large array sizes
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        int allocationSize = computeArrayAllocationSize(length, alignment, headerSize, log2ElementSize);
        Word memory = TLABAllocateNode.allocateVariableSize(allocationSize);
        return InitializeArrayNode.initialize(memory, length, allocationSize, type, fillContents, false);
    }

    /**
     * Computes the size of the memory chunk allocated for an array. This size accounts for the
     * array header size, boy size and any padding after the last element to satisfy object
     * alignment requirements.
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
    public static Object newmultiarray(Word hub, @ConstantParameter int rank, @VarargsParameter int[] dimensions) {
        Word dims = DimensionsNode.allocaDimsArray(rank);
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < rank; i++) {
            dims.writeInt(i * 4, dimensions[i], ANY_LOCATION);
        }
        return NewMultiArrayStubCall.call(hub, rank, dims);
    }

    /**
     * Maximum size of an object whose body is initialized by a sequence of zero-stores to its
     * fields. Larger objects have their bodies initialized in a loop.
     */
    private static final int MAX_UNROLLED_OBJECT_ZEROING_SIZE = 10 * wordSize();

    /**
     * Formats some allocated memory with an object header zeroes out the rest.
     */
    private static void formatObject(Word hub, int size, Word memory, Word compileTimePrototypeMarkWord, boolean fillContents) {
        Word prototypeMarkWord = useBiasedLocking() ? hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION) : compileTimePrototypeMarkWord;
        initializeObjectHeader(memory, prototypeMarkWord, hub);
        if (fillContents) {
            if (size <= MAX_UNROLLED_OBJECT_ZEROING_SIZE) {
                new_seqInit.inc();
                explodeLoop();
                for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
                    memory.writeWord(offset, Word.zero(), ANY_LOCATION);
                }
            } else {
                new_loopInit.inc();
                for (int offset = 2 * wordSize(); offset < size; offset += wordSize()) {
                    memory.writeWord(offset, Word.zero(), ANY_LOCATION);
                }
            }
        }
    }

    /**
     * Formats some allocated memory with an object header zeroes out the rest.
     */
    public static void formatArray(Word hub, int allocationSize, int length, int headerSize, Word memory, Word prototypeMarkWord, boolean fillContents) {
        memory.writeInt(arrayLengthOffset(), length, ANY_LOCATION);
        /*
         * store hub last as the concurrent garbage collectors assume length is valid if hub field
         * is not null
         */
        initializeObjectHeader(memory, prototypeMarkWord, hub);
        if (fillContents) {
            for (int offset = headerSize; offset < allocationSize; offset += wordSize()) {
                memory.writeWord(offset, Word.zero(), ANY_LOCATION);
            }
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo allocate = snippet(NewObjectSnippets.class, "allocate");
        private final SnippetInfo initializeObject = snippet(NewObjectSnippets.class, "initializeObject");
        private final SnippetInfo initializeArray = snippet(NewObjectSnippets.class, "initializeArray");
        private final SnippetInfo allocateArrayAndInitialize = snippet(NewObjectSnippets.class, "allocateArrayAndInitialize");
        private final SnippetInfo newmultiarray = snippet(NewObjectSnippets.class, "newmultiarray");

        private final boolean useTLAB;

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target, boolean useTLAB) {
            super(runtime, replacements, target);
            this.useTLAB = useTLAB;
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
                /*
                 * value for 'size' doesn't matter as it isn't used since a stub call will be made
                 * anyway for both allocation and initialization - it just needs to be non-null
                 */
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
                Arguments args = new Arguments(allocateArrayAndInitialize);
                args.add("length", lengthNode);
                args.addConst("alignment", alignment);
                args.addConst("headerSize", headerSize);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("fillContents", newArrayNode.fillContents());
                args.addConst("type", arrayType);

                SnippetTemplate template = template(args);
                Debug.log("Lowering allocateArrayAndInitialize in %s: node=%s, template=%s, arguments=%s", graph, newArrayNode, template, args);
                template.instantiate(runtime, newArrayNode, DEFAULT_REPLACER, args);
            }
        }

        @SuppressWarnings("unused")
        public void lower(TLABAllocateNode tlabAllocateNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) tlabAllocateNode.graph();
            ValueNode size = tlabAllocateNode.size();
            Arguments args = new Arguments(allocate).add("size", size);

            SnippetTemplate template = template(args);
            Debug.log("Lowering fastAllocate in %s: node=%s, template=%s, arguments=%s", graph, tlabAllocateNode, template, args);
            template.instantiate(runtime, tlabAllocateNode, DEFAULT_REPLACER, args);
        }

        @SuppressWarnings("unused")
        public void lower(InitializeObjectNode initializeNode, LoweringTool tool) {
            StructuredGraph graph = (StructuredGraph) initializeNode.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) initializeNode.type();
            assert !type.isArray();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, graph);
            int size = instanceSize(type);
            ValueNode memory = initializeNode.memory();

            Arguments args = new Arguments(initializeObject);
            args.add("memory", memory);
            args.add("hub", hub);
            args.add("prototypeMarkWord", type.prototypeMarkWord());
            args.addConst("size", size).addConst("fillContents", initializeNode.fillContents());
            args.addConst("locked", initializeNode.locked());

            SnippetTemplate template = template(args);
            Debug.log("Lowering initializeObject in %s: node=%s, template=%s, arguments=%s", graph, initializeNode, template, args);
            template.instantiate(runtime, initializeNode, DEFAULT_REPLACER, args);
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
            ValueNode memory = initializeNode.memory();

            Arguments args = new Arguments(initializeArray);
            args.add("memory", memory);
            args.add("hub", hub);
            args.add("length", initializeNode.length());
            args.add("allocationSize", initializeNode.allocationSize());
            args.add("prototypeMarkWord", type.prototypeMarkWord());
            args.addConst("headerSize", headerSize);
            args.addConst("fillContents", initializeNode.fillContents());
            args.addConst("locked", initializeNode.locked());

            SnippetTemplate template = template(args);
            Debug.log("Lowering initializeArray in %s: node=%s, template=%s, arguments=%s", graph, initializeNode, template, args);
            template.instantiate(runtime, initializeNode, DEFAULT_REPLACER, args);
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

            Arguments args = new Arguments(newmultiarray);
            args.add("hub", hub);
            args.addConst("rank", rank);
            args.addVarargs("dimensions", int.class, StampFactory.forKind(Kind.Int), dims);
            template(args).instantiate(runtime, newmultiarrayNode, DEFAULT_REPLACER, args);
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
