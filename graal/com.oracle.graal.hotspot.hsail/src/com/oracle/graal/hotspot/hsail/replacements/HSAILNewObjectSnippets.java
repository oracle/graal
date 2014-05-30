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
package com.oracle.graal.hotspot.hsail.replacements;

import static com.oracle.graal.api.code.UnsignedMath.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.hotspot.hsail.replacements.HSAILHotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.hsail.replacements.HSAILNewObjectSnippets.Options.*;
import static com.oracle.graal.nodes.PiArrayNode.*;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.*;
import static com.oracle.graal.replacements.SnippetTemplate.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.options.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;

/**
 * HSAIL-specific Snippets used for implementing NEW and NEWARRAY.
 */
public class HSAILNewObjectSnippets extends NewObjectSnippets {

    static public class Options {

        // @formatter:off
        @Option(help = "In HSAIL allocation, allow allocation from eden as fallback if TLAB is full")
        static final OptionValue<Boolean> HsailUseEdenAllocate = new OptionValue<>(false);

        @Option(help = "Estimate of number of bytes allocated by each HSAIL workitem, used to size TLABs")
        static public final OptionValue<Integer> HsailAllocBytesPerWorkitem = new OptionValue<>(64);

        // @formatter:on
    }

    private static final boolean hsailUseEdenAllocate = HsailUseEdenAllocate.getValue();

    @Snippet
    public static Object allocateInstanceAtomic(@ConstantParameter int size, Word hub, Word prototypeMarkWord, @ConstantParameter boolean fillContents, @ConstantParameter String typeContext) {
        Word thread = thread();
        boolean haveResult = false;
        if (useTLAB()) {
            Word top = atomicGetAndAddTlabTop(thread, size);
            Word end = readTlabEnd(thread);
            Word newTop = top.add(size);
            if (probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
                // writeTlabTop(thread, newTop) was done by the atomicGetAndAdd
                Object result = formatObject(hub, size, top, prototypeMarkWord, fillContents, true, false, true);
                profileAllocation("instance", size, typeContext);
                return piCast(verifyOop(result), StampFactory.forNodeIntrinsic());
            } else {
                // only one overflower will be the first overflower, detectable because
                // oldtop was still below end
                if (top.belowOrEqual(end)) {
                    // hack alert: store the last good top before overflow into pf_top
                    // we will move it back into top later when back in the VM
                    writeTlabPfTop(thread, top);
                }
                // useless logic but see notes on deopt path below
                haveResult = newTop.belowOrEqual(end);
            }
        }
        if (hsailUseEdenAllocate) {
            // originally:
            // result = NewInstanceStubCall.call(hub);

            // we could not allocate from tlab, try allocating directly from eden
            // false for no logging
            Word memory = NewInstanceStub.edenAllocate(Word.unsigned(size), false);
            if (memory.notEqual(0)) {
                new_eden.inc();
                Object result = formatObject(hub, size, memory, prototypeMarkWord, fillContents, true, false, true);
                profileAllocation("instance", size, typeContext);
                return piCast(verifyOop(result), StampFactory.forNodeIntrinsic());
            }
        }
        // haveResult test here helps avoid dropping earlier stores were seen to be dropped without
        // this.
        if (!haveResult) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        // will never get here but this keeps the compiler happy
        return Word.zero().toObject();
    }

    @Snippet
    public static Object allocateArrayAtomic(Word hub, int length, Word prototypeMarkWord, @ConstantParameter int headerSize, @ConstantParameter int log2ElementSize,
                    @ConstantParameter boolean fillContents, @ConstantParameter boolean maybeUnroll, @ConstantParameter String typeContext) {
        if (!belowThan(length, MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH)) {
            // This handles both negative array sizes and very large array sizes
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        return allocateArrayAtomicImpl(hub, length, prototypeMarkWord, headerSize, log2ElementSize, fillContents, maybeUnroll, typeContext);
    }

    private static Object allocateArrayAtomicImpl(Word hub, int length, Word prototypeMarkWord, int headerSize, int log2ElementSize, boolean fillContents, boolean maybeUnroll, String typeContext) {
        int alignment = wordSize();
        int allocationSize = computeArrayAllocationSize(length, alignment, headerSize, log2ElementSize);
        Word thread = thread();
        boolean haveResult = false;
        if (useTLAB()) {
            Word top = atomicGetAndAddTlabTop(thread, allocationSize);
            Word end = readTlabEnd(thread);
            Word newTop = top.add(allocationSize);
            if (probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
                // writeTlabTop(thread, newTop) was done by the atomicGetAndAdd
                newarray_loopInit.inc();
                // we are not in a stub so we can set useSnippetCounters to true
                Object result = formatArray(hub, allocationSize, length, headerSize, top, prototypeMarkWord, fillContents, maybeUnroll, true);
                profileAllocation("array", allocationSize, typeContext);
                return piArrayCast(verifyOop(result), length, StampFactory.forNodeIntrinsic());
            } else {
                // only one overflower will be the first overflower, detectable because
                // oldtop was still below end
                if (top.belowOrEqual(end)) {
                    // hack alert: store the last good top before overflow into pf_top
                    // we will move it back into top later when back in the VM
                    writeTlabPfTop(thread, top);
                }
                // useless logic but see notes on deopt path below
                haveResult = newTop.belowOrEqual(end);
            }
        }
        // we could not allocate from tlab, try allocating directly from eden
        if (hsailUseEdenAllocate) {
            // false for no logging
            Word memory = NewInstanceStub.edenAllocate(Word.unsigned(allocationSize), false);
            if (memory.notEqual(0)) {
                newarray_eden.inc();
                // we are not in a stub so we can set useSnippetCounters to true
                Object result = formatArray(hub, allocationSize, length, headerSize, memory, prototypeMarkWord, fillContents, maybeUnroll, true);
                profileAllocation("array", allocationSize, typeContext);
                return piArrayCast(verifyOop(result), length, StampFactory.forNodeIntrinsic());
            }
        }
        if (!haveResult) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.RuntimeConstraint);
        }
        // will never get here but this keeps the compiler happy
        return Word.zero().toObject();
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo allocateInstance = snippet(HSAILNewObjectSnippets.class, "allocateInstanceAtomic");
        private final SnippetInfo allocateArray = snippet(HSAILNewObjectSnippets.class, "allocateArrayAtomic");

        // private final SnippetInfo allocateArrayDynamic = snippet(NewObjectSnippets.class,
        // "allocateArrayDynamic");
        // private final SnippetInfo newmultiarray = snippet(NewObjectSnippets.class,
        // "newmultiarray");

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        /**
         * Lowers a {@link NewInstanceNode}.
         */
        public void lower(NewInstanceNode newInstanceNode, LoweringTool tool) {
            StructuredGraph graph = newInstanceNode.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) newInstanceNode.instanceClass();
            assert !type.isArray();
            ConstantNode hub = ConstantNode.forConstant(type.klass(), providers.getMetaAccess(), graph);
            int size = instanceSize(type);

            Arguments args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
            args.addConst("size", size);
            args.add("hub", hub);
            args.add("prototypeMarkWord", type.prototypeMarkWord());
            args.addConst("fillContents", newInstanceNode.fillContents());
            args.addConst("typeContext", MetaUtil.toJavaName(type, false));

            SnippetTemplate template = template(args);
            Debug.log("Lowering allocateInstance in %s: node=%s, template=%s, arguments=%s", graph, newInstanceNode, template, args);
            template.instantiate(providers.getMetaAccess(), newInstanceNode, DEFAULT_REPLACER, args);
        }

        /**
         * Lowers a {@link NewArrayNode}.
         */
        public void lower(NewArrayNode newArrayNode, LoweringTool tool) {
            StructuredGraph graph = newArrayNode.graph();
            ResolvedJavaType elementType = newArrayNode.elementType();
            HotSpotResolvedObjectType arrayType = (HotSpotResolvedObjectType) elementType.getArrayClass();
            Kind elementKind = elementType.getKind();
            ConstantNode hub = ConstantNode.forConstant(arrayType.klass(), providers.getMetaAccess(), graph);
            final int headerSize = HotSpotGraalRuntime.getArrayBaseOffset(elementKind);
            // lowerer extends HotSpotLoweringProvider so we can just use that
            HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) providers.getLowerer();
            int log2ElementSize = CodeUtil.log2(lowerer.arrayScalingFactor(elementKind));

            Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            args.add("length", newArrayNode.length());
            args.add("prototypeMarkWord", arrayType.prototypeMarkWord());
            args.addConst("headerSize", headerSize);
            args.addConst("log2ElementSize", log2ElementSize);
            args.addConst("fillContents", newArrayNode.fillContents());
            args.addConst("maybeUnroll", newArrayNode.length().isConstant());
            args.addConst("typeContext", MetaUtil.toJavaName(arrayType, false));

            SnippetTemplate template = template(args);
            Debug.log("Lowering allocateArray in %s: node=%s, template=%s, arguments=%s", graph, newArrayNode, template, args);
            template.instantiate(providers.getMetaAccess(), newArrayNode, DEFAULT_REPLACER, args);
        }

        private static int instanceSize(HotSpotResolvedObjectType type) {
            int size = type.instanceSize();
            assert (size % wordSize()) == 0;
            assert size >= 0;
            return size;
        }
    }

    private static final SnippetCounter.Group countersNew = SnippetCounters.getValue() ? new SnippetCounter.Group("NewInstance") : null;
    private static final SnippetCounter new_eden = new SnippetCounter(countersNew, "eden", "used edenAllocate");

    private static final SnippetCounter.Group countersNewArray = SnippetCounters.getValue() ? new SnippetCounter.Group("NewArray") : null;
    private static final SnippetCounter newarray_loopInit = new SnippetCounter(countersNewArray, "tlabLoopInit", "TLAB alloc with zeroing in a loop");
    private static final SnippetCounter newarray_eden = new SnippetCounter(countersNewArray, "eden", "used edenAllocate");
}
