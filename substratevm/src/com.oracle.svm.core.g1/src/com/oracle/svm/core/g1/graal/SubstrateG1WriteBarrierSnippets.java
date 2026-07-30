/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.g1.graal;

import static com.oracle.svm.core.g1.G1Heap.GC_TOTAL_COLLECTIONS_ADDRESS_FIELD;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.guest.staging.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.SubstrateCompressionNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.Target_java_lang_ref_Reference;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.g1.G1Constants;
import com.oracle.svm.core.g1.G1Heap;
import com.oracle.svm.core.g1.nativelib.G1Library;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.gc.G1ArrayRangePostWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.G1ArrayRangePreWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.G1PostWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.G1PreWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.G1ReferentFieldReadBarrierNode;
import jdk.graal.compiler.nodes.gc.WriteBarrierNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.SnippetCounter.Group;
import jdk.graal.compiler.replacements.SnippetCounter.Group.Factory;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.gc.G1WriteBarrierSnippets;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Substrate VM-specific write barrier snippets that are used if the G1 GC is enabled.
 */
public final class SubstrateG1WriteBarrierSnippets extends G1WriteBarrierSnippets {
    private static final MetaAccessProvider INJECTED_METAACCESS = null;

    private static final LocationIdentity CARD_TABLE_ADDRESS_LOCATION = NamedLocationIdentity.mutable("GC-CardTable-Address");

    private static final SubstrateForeignCallDescriptor PRE_WRITE_BARRIER = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class,
                    "preWriteBarrierStub", NO_SIDE_EFFECT, KILLED_PRE_WRITE_BARRIER_STUB_LOCATIONS);
    private static final SubstrateForeignCallDescriptor POST_WRITE_BARRIER = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class,
                    "postWriteBarrierStub", NO_SIDE_EFFECT, KILLED_POST_WRITE_BARRIER_STUB_LOCATIONS);

    private static final SubstrateForeignCallDescriptor OUTLINED_PRE_WRITE_BARRIER = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class,
                    "outlinedPreWriteBarrierStub", NO_SIDE_EFFECT, KILLED_PRE_WRITE_BARRIER_STUB_LOCATIONS);
    private static final SubstrateForeignCallDescriptor OUTLINED_POST_WRITE_BARRIER = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class,
                    "outlinedPostWriteBarrierStub", NO_SIDE_EFFECT, KILLED_POST_WRITE_BARRIER_STUB_LOCATIONS);
    private static final SubstrateForeignCallDescriptor OUTLINED_ARRAY_RANGE_PRE_WRITE_BARRIER = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class,
                    "outlinedArrayRangePreWriteBarrierStub", NO_SIDE_EFFECT, KILLED_PRE_WRITE_BARRIER_STUB_LOCATIONS);
    private static final SubstrateForeignCallDescriptor OUTLINED_ARRAY_RANGE_POST_WRITE_BARRIER = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class,
                    "outlinedArrayRangePostWriteBarrierStub", NO_SIDE_EFFECT, KILLED_POST_WRITE_BARRIER_STUB_LOCATIONS);

    private static final SubstrateForeignCallDescriptor VERIFY_OOP = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class, "verifyOopStub", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor VALIDATE_OBJECT = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class, "validateObjectStub", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor LOG_PRINTF = SnippetRuntime.findForeignCall(SubstrateG1WriteBarrierSnippets.class, "logPrintf", NO_SIDE_EFFECT);

    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{PRE_WRITE_BARRIER, POST_WRITE_BARRIER,
                    OUTLINED_PRE_WRITE_BARRIER, OUTLINED_POST_WRITE_BARRIER, OUTLINED_ARRAY_RANGE_PRE_WRITE_BARRIER, OUTLINED_ARRAY_RANGE_POST_WRITE_BARRIER,
                    VERIFY_OOP, VALIDATE_OBJECT, LOG_PRINTF};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void preWriteBarrierStub(Object object) {
        G1Library.preWriteBarrierStub(Word.objectToUntrackedWord(object));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void postWriteBarrierStub(Word cardAddress) {
        G1Library.postWriteBarrierStub(cardAddress);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static boolean outlinedPreWriteBarrierStub(Word field, Object expectedObject, boolean doLoad) {
        ReferenceAccess referenceAccess = ReferenceAccess.singleton();
        Object previousObject = doLoad ? referenceAccess.readObjectAt(field, true) : expectedObject;
        if (previousObject == null) {
            return false;
        }
        G1Library.preWriteBarrierStub(Word.objectToUntrackedWord(previousObject));
        return true;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void outlinedPostWriteBarrierStub(Word fieldAddress) {
        Word cardAddress = readCardTableBase().add(computeCardTableOffset(fieldAddress));
        if (cardAddress.readByte(0, GC_CARD_LOCATION) != G1Constants.youngCardValue()) {
            MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_LOAD, GC_CARD_LOCATION);

            byte cardByteReload = cardAddress.readByte(0, GC_CARD_LOCATION);
            if (cardByteReload != G1Constants.dirtyCardValue()) {
                cardAddress.writeByte(0, G1Constants.dirtyCardValue(), GC_CARD_LOCATION);
                Word thread = G1Heap.javaThreadTL.getAddress();
                Word indexValue = thread.readWord(G1Constants.cardQueueIndexOffset(), CARD_QUEUE_INDEX_LOCATION);
                if (indexValue.notEqual(0)) {
                    Word bufferAddress = thread.readWord(G1Constants.cardQueueBufferOffset(), CARD_QUEUE_BUFFER_LOCATION);
                    Word nextIndex = indexValue.subtract(SubstrateTarget.getWordSize());
                    bufferAddress.writeWord(nextIndex, cardAddress, CARD_QUEUE_LOG_LOCATION);
                    thread.writeWord(G1Constants.cardQueueIndexOffset(), nextIndex, CARD_QUEUE_INDEX_LOCATION);
                } else {
                    G1Library.postWriteBarrierStub(cardAddress);
                }
            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Word readCardTableBase() {
        return G1Heap.addressOfCardTableAddress().readWord(0, CARD_TABLE_ADDRESS_LOCATION);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord computeCardTableOffset(Pointer oop) {
        return oop.unsignedShiftRight(G1Constants.cardTableShift());
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void outlinedArrayRangePreWriteBarrierStub(Word address, long length, int elementStride) {
        Word start = pointerToFirstArrayElement(address, length, elementStride);
        G1Library.arrayRangePreWriteBarrier(start, Word.unsigned(length));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void outlinedArrayRangePostWriteBarrierStub(Word address, long length, int elementStride) {
        Word start = pointerToFirstArrayElement(address, length, elementStride);
        G1Library.arrayRangePostWriteBarrier(start, Word.unsigned(length));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Word pointerToFirstArrayElement(Word address, long length, int elementStride) {
        long result = address.rawValue();
        if (elementStride < 0) {
            result += elementStride * length;
        }
        return Word.unsigned(result);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void verifyOopStub(Object object) {
        G1Library.verifyOop(Word.objectToUntrackedWord(object));
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void validateObjectStub(Word parent, Word child) {
        G1Library.validateObject(parent, child);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = true, fullyUninterruptible = true)
    @Uninterruptible(reason = "calls into native code with no transition")
    private static void logPrintf(Word format, long v1, long v2, long v3) {
        G1Library.logPrintf(format, v1, v2, v3);
    }

    @Override
    protected Word getThread() {
        return G1Heap.javaThreadTL.getAddress();
    }

    @Override
    protected int wordSize() {
        return SubstrateTarget.getWordSize();
    }

    @Override
    protected long objectArrayIndexScale() {
        return ReplacementsUtil.arrayIndexScale(INJECTED_METAACCESS, JavaKind.Object);
    }

    @Override
    protected int satbQueueMarkingActiveOffset() {
        return G1Constants.satbQueueMarkingActiveOffset();
    }

    @Override
    protected int satbQueueBufferOffset() {
        return G1Constants.satbQueueBufferOffset();
    }

    @Override
    protected int satbQueueIndexOffset() {
        return G1Constants.satbQueueIndexOffset();
    }

    @Override
    protected int cardQueueBufferOffset() {
        return G1Constants.cardQueueBufferOffset();
    }

    @Override
    protected int cardQueueIndexOffset() {
        return G1Constants.cardQueueIndexOffset();
    }

    @Override
    protected byte dirtyCardValue() {
        return G1Constants.dirtyCardValue();
    }

    @Override
    protected byte youngCardValue() {
        return G1Constants.youngCardValue();
    }

    @Override
    public byte cleanCardValue() {
        throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    protected boolean supportsLowLatencyBarriers() {
        return false;
    }

    @Override
    protected Word cardTableBase() {
        return readCardTableBase();
    }

    @Override
    protected UnsignedWord cardTableOffset(Pointer oop) {
        return computeCardTableOffset(oop);
    }

    @Override
    protected int logOfHeapRegionGrainBytes() {
        return G1Constants.logOfHeapRegionGrainBytes();
    }

    @Override
    protected ForeignCallDescriptor preWriteBarrierCallDescriptor() {
        return PRE_WRITE_BARRIER;
    }

    @Override
    protected ForeignCallDescriptor postWriteBarrierCallDescriptor() {
        return POST_WRITE_BARRIER;
    }

    @Override
    protected boolean outlinedPreBarrierStub(Word address, Object expectedObject, boolean doLoad) {
        return outlinedPreBarrierStub(OUTLINED_PRE_WRITE_BARRIER, address, expectedObject, doLoad);
    }

    @Override
    protected void outlinedPostBarrierStub(Word address) {
        outlinedPostBarrierStub(OUTLINED_POST_WRITE_BARRIER, address);
    }

    @Override
    protected void outlinedArrayRangePreBarrierStub(Word address, long length, int elementStride) {
        outlinedArrayRangeStub(OUTLINED_ARRAY_RANGE_PRE_WRITE_BARRIER, address, length, elementStride);
    }

    @Override
    protected void outlinedArrayRangePostBarrierStub(Word address, long length, int elementStride) {
        outlinedArrayRangeStub(OUTLINED_ARRAY_RANGE_POST_WRITE_BARRIER, address, length, elementStride);
    }

    @Override
    protected boolean verifyOops() {
        return false;
    }

    @Override
    protected boolean verifyBarrier() {
        return ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED;
    }

    @Override
    protected long gcTotalCollectionsAddress() {
        return G1Heap.getGcTotalCollectionsAddress();
    }

    @Override
    protected ForeignCallDescriptor verifyOopCallDescriptor() {
        return VERIFY_OOP;
    }

    @Override
    protected ForeignCallDescriptor validateObjectCallDescriptor() {
        return VALIDATE_OBJECT;
    }

    @Override
    protected ForeignCallDescriptor printfCallDescriptor() {
        return LOG_PRINTF;
    }

    @Fold
    static ResolvedJavaType getReferenceType(@InjectedParameter MetaAccessProvider metaAccessProvider) {
        return metaAccessProvider.lookupJavaType(Target_java_lang_ref_Reference.class);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native boolean outlinedPreBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word field, Object expectedObject, boolean doLoad);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void outlinedPostBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word fieldAddress);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void outlinedArrayRangeStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word address, long length, int elementStride);

    public static class Templates extends SubstrateTemplates {
        private final SnippetInfo g1PreWriteBarrier;
        private final SnippetInfo g1ReferentReadBarrier;
        private final SnippetInfo g1PostWriteBarrier;
        private final SnippetInfo g1ArrayRangePreWriteBarrier;
        private final SnippetInfo g1ArrayRangePostWriteBarrier;

        private final G1WriteBarrierLowerer lowerer;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Group.Factory factory, Providers providers) {
            super(options, providers);
            this.lowerer = new SubstrateG1WriteBarrierLowerer(factory);

            SubstrateG1WriteBarrierSnippets receiver = new SubstrateG1WriteBarrierSnippets();
            g1PreWriteBarrier = snippet(providers,
                            G1WriteBarrierSnippets.class,
                            "g1PreWriteBarrier",
                            receiver,
                            new Object[]{SATB_QUEUE_LOG_LOCATION,
                                            SATB_QUEUE_INDEX_LOCATION,
                                            SATB_QUEUE_BUFFER_LOCATION,
                                            SATB_QUEUE_MARKING_ACTIVE_LOCATION,
                                            GC_TOTAL_COLLECTIONS_ADDRESS_FIELD});
            g1ReferentReadBarrier = snippet(providers,
                            G1WriteBarrierSnippets.class,
                            "g1ReferentReadBarrier",
                            receiver,
                            new Object[]{SATB_QUEUE_LOG_LOCATION,
                                            SATB_QUEUE_INDEX_LOCATION,
                                            SATB_QUEUE_BUFFER_LOCATION,
                                            SATB_QUEUE_MARKING_ACTIVE_LOCATION,
                                            GC_TOTAL_COLLECTIONS_ADDRESS_FIELD});
            g1PostWriteBarrier = snippet(providers,
                            G1WriteBarrierSnippets.class,
                            "g1PostWriteBarrier",
                            receiver,
                            new Object[]{GC_CARD_LOCATION,
                                            CARD_QUEUE_LOG_LOCATION,
                                            CARD_QUEUE_INDEX_LOCATION,
                                            CARD_QUEUE_BUFFER_LOCATION,
                                            GC_TOTAL_COLLECTIONS_ADDRESS_FIELD,
                                            CARD_TABLE_ADDRESS_LOCATION});
            g1ArrayRangePreWriteBarrier = snippet(providers,
                            G1WriteBarrierSnippets.class,
                            "g1ArrayRangePreWriteBarrier",
                            receiver,
                            new Object[]{SATB_QUEUE_LOG_LOCATION,
                                            SATB_QUEUE_INDEX_LOCATION,
                                            SATB_QUEUE_BUFFER_LOCATION,
                                            SATB_QUEUE_MARKING_ACTIVE_LOCATION,
                                            GC_TOTAL_COLLECTIONS_ADDRESS_FIELD});
            g1ArrayRangePostWriteBarrier = snippet(providers,
                            G1WriteBarrierSnippets.class,
                            "g1ArrayRangePostWriteBarrier",
                            receiver,
                            new Object[]{GC_CARD_LOCATION,
                                            CARD_QUEUE_LOG_LOCATION,
                                            CARD_QUEUE_INDEX_LOCATION,
                                            CARD_QUEUE_BUFFER_LOCATION,
                                            CARD_TABLE_ADDRESS_LOCATION});
        }

        public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            G1PreWriteBarrierLowering g1PreBarrierLowering = new G1PreWriteBarrierLowering();
            lowerings.put(G1PreWriteBarrierNode.class, g1PreBarrierLowering);

            G1PostWriteBarrierLowering g1PostBarrierLowering = new G1PostWriteBarrierLowering();
            lowerings.put(G1PostWriteBarrierNode.class, g1PostBarrierLowering);

            G1ReferentReadBarrierLowering g1ReferentFieldReadBarrierLowering = new G1ReferentReadBarrierLowering();
            lowerings.put(G1ReferentFieldReadBarrierNode.class, g1ReferentFieldReadBarrierLowering);

            G1ArrayRangePreWriteBarrierLowering g1ArrayRangePreWriteBarrierLowering = new G1ArrayRangePreWriteBarrierLowering();
            lowerings.put(G1ArrayRangePreWriteBarrierNode.class, g1ArrayRangePreWriteBarrierLowering);

            G1ArrayRangePostWriteBarrierLowering g1ArrayRangePostWriteBarrierLowering = new G1ArrayRangePostWriteBarrierLowering();
            lowerings.put(G1ArrayRangePostWriteBarrierNode.class, g1ArrayRangePostWriteBarrierLowering);
        }

        private final class G1PreWriteBarrierLowering implements NodeLoweringProvider<G1PreWriteBarrierNode> {
            @Override
            public void lower(G1PreWriteBarrierNode barrier, LoweringTool tool) {
                lowerer.lower(Templates.this, g1PreWriteBarrier, barrier, tool);
            }
        }

        private final class G1ReferentReadBarrierLowering implements NodeLoweringProvider<G1ReferentFieldReadBarrierNode> {
            @Override
            public void lower(G1ReferentFieldReadBarrierNode barrier, LoweringTool tool) {
                lowerer.lower(Templates.this, g1ReferentReadBarrier, barrier, tool);
            }
        }

        private final class G1PostWriteBarrierLowering implements NodeLoweringProvider<G1PostWriteBarrierNode> {
            @Override
            public void lower(G1PostWriteBarrierNode barrier, LoweringTool tool) {
                lowerer.lower(Templates.this, g1PostWriteBarrier, barrier, tool);
            }
        }

        private final class G1ArrayRangePreWriteBarrierLowering implements NodeLoweringProvider<G1ArrayRangePreWriteBarrierNode> {
            @Override
            public void lower(G1ArrayRangePreWriteBarrierNode barrier, LoweringTool tool) {
                lowerer.lower(Templates.this, g1ArrayRangePreWriteBarrier, barrier, tool);
            }
        }

        private final class G1ArrayRangePostWriteBarrierLowering implements NodeLoweringProvider<G1ArrayRangePostWriteBarrierNode> {
            @Override
            public void lower(G1ArrayRangePostWriteBarrierNode barrier, LoweringTool tool) {
                lowerer.lower(Templates.this, g1ArrayRangePostWriteBarrier, barrier, tool);
            }
        }
    }

    private static final class SubstrateG1WriteBarrierLowerer extends SubstrateG1WriteBarrierSnippets.G1WriteBarrierLowerer {
        private final CompressEncoding oopEncoding;

        SubstrateG1WriteBarrierLowerer(Factory factory) {
            super(factory);
            this.oopEncoding = ImageSingletons.lookup(CompressEncoding.class);
        }

        @Override
        public ValueNode uncompress(ValueNode expected) {
            return SubstrateCompressionNode.uncompress(expected.graph(), expected, oopEncoding);
        }

        @Override
        protected boolean shouldOutline(WriteBarrierNode barrier) {
            SubstrateGCOptions.OutlineWriteBarriers outlining = SubstrateGCOptions.WriteBarrierOutlining.getValue();
            if (outlining == SubstrateGCOptions.OutlineWriteBarriers.Always) {
                return true;
            } else if (outlining == SubstrateGCOptions.OutlineWriteBarriers.Never) {
                return false;
            } else {
                assert outlining == SubstrateGCOptions.OutlineWriteBarriers.Auto;
                return GraalOptions.ReduceCodeSize.getValue(barrier.graph().getOptions());
            }
        }
    }
}

/**
 * Snippets are parsed in the AnalysisUniverse, when fields offsets are not yet available. So we
 * need to delay the field offset access until compilation.
 */
@NodeInfo(cycles = CYCLES_0, size = SIZE_1)
class ReferentOffsetNode extends FloatingNode implements Canonicalizable {
    public static final NodeClass<ReferentOffsetNode> TYPE = NodeClass.create(ReferentOffsetNode.class);

    protected ReferentOffsetNode() {
        super(TYPE, StampFactory.forKind(JavaKind.Long));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        ResolvedJavaField referentField = ReferenceInternals.getReferentField(tool.getMetaAccess());
        if (referentField instanceof SharedField) {
            int location = ((SharedField) referentField).getLocation();
            VMError.guarantee(location > 0, "Field Reference.referent not seen as used");
            return ConstantNode.forLong(location);
        }
        return this;
    }

    @NodeIntrinsic
    static native long referentOffset();
}
