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
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.nodes.PiArrayNode.*;
import static com.oracle.graal.nodes.PiNode.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;
import static com.oracle.graal.replacements.nodes.ExplodeLoopNode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
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
        if (probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            return top;
        }
        return Word.zero();
    }

    @Snippet
    public static Object allocateInstance(@ConstantParameter int size, Word hub, Word prototypeMarkWord, @ConstantParameter boolean fillContents) {
        Object result;
        Word thread = thread();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        Word newTop = top.add(size);
        if (useTLAB() && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            result = formatObject(hub, size, top, prototypeMarkWord, fillContents);
        } else {
            new_stub.inc();
            result = NewInstanceStubCall.call(hub);
        }
        return piCast(verifyOop(result), StampFactory.forNodeIntrinsic());
    }

    /**
     * Maximum array length for which fast path allocation is used.
     */
    public static final int MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH = 0x00FFFFFF;

    @Snippet
    public static Object allocateArray(Word hub, int length, Word prototypeMarkWord, @ConstantParameter int headerSize, @ConstantParameter int log2ElementSize, @ConstantParameter boolean fillContents) {
        if (!belowThan(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
            // This handles both negative array sizes and very large array sizes
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return allocateArrayImpl(hub, length, prototypeMarkWord, headerSize, log2ElementSize, fillContents);
    }

    private static Object allocateArrayImpl(Word hub, int length, Word prototypeMarkWord, int headerSize, int log2ElementSize, boolean fillContents) {
        Object result;
        int alignment = wordSize();
        int allocationSize = computeArrayAllocationSize(length, alignment, headerSize, log2ElementSize);
        Word thread = thread();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        Word newTop = top.add(allocationSize);
        if (useTLAB() && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            newarray_loopInit.inc();
            result = formatArray(hub, allocationSize, length, headerSize, top, prototypeMarkWord, fillContents);
        } else {
            newarray_stub.inc();
            result = NewArrayStubCall.call(hub, length);
        }
        return piArrayCast(verifyOop(result), length, StampFactory.forNodeIntrinsic());
    }

    public static final ForeignCallDescriptor DYNAMIC_NEW_ARRAY = new ForeignCallDescriptor("dynamic_new_array", Object.class, Class.class, int.class);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native Object dynamicNewArrayStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> elementType, int length);

    @Snippet
    public static Object allocateArrayDynamic(Class<?> elementType, int length, @ConstantParameter boolean fillContents) {
        Word hub = loadWordFromObject(elementType, arrayKlassOffset());
        if (hub.equal(Word.zero()) || !belowThan(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
            return dynamicNewArrayStub(DYNAMIC_NEW_ARRAY, elementType, length);
        }

        int layoutHelper = readLayoutHelper(hub);
        //@formatter:off
        // from src/share/vm/oops/klass.hpp:
        //
        // For arrays, layout helper is a negative number, containing four
        // distinct bytes, as follows:
        //    MSB:[tag, hsz, ebt, log2(esz)]:LSB
        // where:
        //    tag is 0x80 if the elements are oops, 0xC0 if non-oops
        //    hsz is array header size in bytes (i.e., offset of first element)
        //    ebt is the BasicType of the elements
        //    esz is the element size in bytes
        //@formatter:on

        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift()) & layoutHelperHeaderSizeMask();
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift()) & layoutHelperLog2ElementSizeMask();
        Word prototypeMarkWord = hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION);

        return allocateArrayImpl(hub, length, prototypeMarkWord, headerSize, log2ElementSize, fillContents);
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
    private static Object formatObject(Word hub, int size, Word memory, Word compileTimePrototypeMarkWord, boolean fillContents) {
        Word prototypeMarkWord = useBiasedLocking() ? hub.readWord(prototypeMarkWordOffset(), PROTOTYPE_MARK_WORD_LOCATION) : compileTimePrototypeMarkWord;
        initializeObjectHeader(memory, prototypeMarkWord, hub);
        if (fillContents) {
            if (size <= MAX_UNROLLED_OBJECT_ZEROING_SIZE) {
                new_seqInit.inc();
                explodeLoop();
                for (int offset = instanceHeaderSize(); offset < size; offset += wordSize()) {
                    memory.writeWord(offset, Word.zero(), ANY_LOCATION);
                }
            } else {
                new_loopInit.inc();
                for (int offset = instanceHeaderSize(); offset < size; offset += wordSize()) {
                    memory.writeWord(offset, Word.zero(), ANY_LOCATION);
                }
            }
        }
        return memory.toObject();
    }

    /**
     * Formats some allocated memory with an object header and zeroes out the rest.
     */
    public static Object formatArray(Word hub, int allocationSize, int length, int headerSize, Word memory, Word prototypeMarkWord, boolean fillContents) {
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
        return memory.toObject();
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo allocateInstance = snippet(NewObjectSnippets.class, "allocateInstance");
        private final SnippetInfo allocateArray = snippet(NewObjectSnippets.class, "allocateArray");
        private final SnippetInfo allocateArrayDynamic = snippet(NewObjectSnippets.class, "allocateArrayDynamic");
        private final SnippetInfo newmultiarray = snippet(NewObjectSnippets.class, "newmultiarray");

        public Templates(CodeCacheProvider runtime, Replacements replacements, TargetDescription target) {
            super(runtime, replacements, target);
        }

        /**
         * Lowers a {@link NewInstanceNode}.
         */
        public void lower(NewInstanceNode newInstanceNode) {
            StructuredGraph graph = newInstanceNode.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) newInstanceNode.instanceClass();
            assert !type.isArray();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, graph);
            int size = instanceSize(type);

            Arguments args = new Arguments(allocateInstance, graph.getGuardsStage());
            args.addConst("size", size);
            args.add("hub", hub);
            args.add("prototypeMarkWord", type.prototypeMarkWord());
            args.addConst("fillContents", newInstanceNode.fillContents());

            SnippetTemplate template = template(args);
            Debug.log("Lowering allocateInstance in %s: node=%s, template=%s, arguments=%s", graph, newInstanceNode, template, args);
            template.instantiate(runtime, newInstanceNode, DEFAULT_REPLACER, args);
        }

        /**
         * Lowers a {@link NewArrayNode}.
         */
        public void lower(NewArrayNode newArrayNode) {
            StructuredGraph graph = newArrayNode.graph();
            ResolvedJavaType elementType = newArrayNode.elementType();
            HotSpotResolvedObjectType arrayType = (HotSpotResolvedObjectType) elementType.getArrayClass();
            Kind elementKind = elementType.getKind();
            ConstantNode hub = ConstantNode.forConstant(arrayType.klass(), runtime, graph);
            final int headerSize = HotSpotRuntime.getArrayBaseOffset(elementKind);
            int log2ElementSize = CodeUtil.log2(((HotSpotRuntime) runtime).getScalingFactor(elementKind));

            Arguments args = new Arguments(allocateArray, graph.getGuardsStage());
            args.add("hub", hub);
            args.add("length", newArrayNode.length());
            args.add("prototypeMarkWord", arrayType.prototypeMarkWord());
            args.addConst("headerSize", headerSize);
            args.addConst("log2ElementSize", log2ElementSize);
            args.addConst("fillContents", newArrayNode.fillContents());

            SnippetTemplate template = template(args);
            Debug.log("Lowering allocateArray in %s: node=%s, template=%s, arguments=%s", graph, newArrayNode, template, args);
            template.instantiate(runtime, newArrayNode, DEFAULT_REPLACER, args);
        }

        public void lower(DynamicNewArrayNode newArrayNode) {
            Arguments args = new Arguments(allocateArrayDynamic, newArrayNode.graph().getGuardsStage());
            args.add("elementType", newArrayNode.getElementType());
            args.add("length", newArrayNode.length());
            args.addConst("fillContents", newArrayNode.fillContents());

            SnippetTemplate template = template(args);
            template.instantiate(runtime, newArrayNode, DEFAULT_REPLACER, args);
        }

        public void lower(NewMultiArrayNode newmultiarrayNode) {
            StructuredGraph graph = newmultiarrayNode.graph();
            int rank = newmultiarrayNode.dimensionCount();
            ValueNode[] dims = new ValueNode[rank];
            for (int i = 0; i < newmultiarrayNode.dimensionCount(); i++) {
                dims[i] = newmultiarrayNode.dimension(i);
            }
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) newmultiarrayNode.type();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), runtime, graph);

            Arguments args = new Arguments(newmultiarray, graph.getGuardsStage());
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

    private static final SnippetCounter.Group countersNew = SnippetCounters.getValue() ? new SnippetCounter.Group("NewInstance") : null;
    private static final SnippetCounter new_seqInit = new SnippetCounter(countersNew, "tlabSeqInit", "TLAB alloc with unrolled zeroing");
    private static final SnippetCounter new_loopInit = new SnippetCounter(countersNew, "tlabLoopInit", "TLAB alloc with zeroing in a loop");
    private static final SnippetCounter new_stub = new SnippetCounter(countersNew, "stub", "alloc and zeroing via stub");

    private static final SnippetCounter.Group countersNewArray = SnippetCounters.getValue() ? new SnippetCounter.Group("NewArray") : null;
    private static final SnippetCounter newarray_loopInit = new SnippetCounter(countersNewArray, "tlabLoopInit", "TLAB alloc with zeroing in a loop");
    private static final SnippetCounter newarray_stub = new SnippetCounter(countersNewArray, "stub", "alloc and zeroing via stub");
}
