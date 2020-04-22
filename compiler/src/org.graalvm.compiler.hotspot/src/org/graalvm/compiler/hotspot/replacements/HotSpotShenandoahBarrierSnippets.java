package org.graalvm.compiler.hotspot.replacements;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.nodes.GraalHotSpotVMConfigNode;
import org.graalvm.compiler.hotspot.nodes.HotSpotCompressionNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.gc.G1ArrayRangePostWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1ArrayRangePreWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1PostWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1PreWriteBarrier;
import org.graalvm.compiler.nodes.gc.G1ReferentFieldReadBarrier;
import org.graalvm.compiler.nodes.gc.ShenandoahArrayRangePreWriteBarrier;
import org.graalvm.compiler.nodes.gc.ShenandoahPreWriteBarrier;
import org.graalvm.compiler.nodes.gc.ShenandoahReferentFieldReadBarrier;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.gc.G1WriteBarrierSnippets;
import org.graalvm.compiler.replacements.gc.ShenandoahBarrierSnippets;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.WordFactory;

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_METAACCESS;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;

public final class HotSpotShenandoahBarrierSnippets extends ShenandoahBarrierSnippets {
    public static final ForeignCallDescriptor SHENANDOAHWBPRECALL = new ForeignCallDescriptor("shenandoah_concmark_barrier", void.class, Object.class);
    public static final ForeignCallDescriptor VALIDATE_OBJECT = new ForeignCallDescriptor("validate_object", boolean.class, Word.class, Word.class);

    private final GraalHotSpotVMConfig config;
    private final Register threadRegister;

    public HotSpotShenandoahBarrierSnippets(GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        this.config = config;
        this.threadRegister = registers.getThreadRegister();
    }

    @Override
    protected Word getThread() {
        return HotSpotReplacementsUtil.registerAsWord(threadRegister);
    }

    @Override
    protected int wordSize() {
        return HotSpotReplacementsUtil.wordSize();
    }

    @Override
    protected int objectArrayIndexScale() {
        return ReplacementsUtil.arrayIndexScale(INJECTED_METAACCESS, JavaKind.Object);
    }

    @Override
    protected int satbQueueMarkingOffset() {
        return HotSpotReplacementsUtil.shenandoahSATBQueueMarkingOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected int satbQueueBufferOffset() {
        return HotSpotReplacementsUtil.shenandoahSATBQueueBufferOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected int satbQueueIndexOffset() {
        return HotSpotReplacementsUtil.shenandoahSATBQueueIndexOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected ForeignCallDescriptor preWriteBarrierCallDescriptor() {
        return SHENANDOAHWBPRECALL;
    }

    @Override
    protected boolean verifyOops() {
        return HotSpotReplacementsUtil.verifyOops(INJECTED_VMCONFIG);
    }

    @Override
    protected boolean verifyBarrier() {
        return ReplacementsUtil.REPLACEMENTS_ASSERTIONS_ENABLED || config.verifyBeforeGC || config.verifyAfterGC;
    }

    @Override
    protected long gcTotalCollectionsAddress() {
        return HotSpotReplacementsUtil.gcTotalCollectionsAddress(INJECTED_VMCONFIG);
    }

    @Override
    protected ForeignCallDescriptor verifyOopCallDescriptor() {
        return HotSpotForeignCallsProviderImpl.VERIFY_OOP;
    }

    @Override
    protected ForeignCallDescriptor validateObjectCallDescriptor() {
        return VALIDATE_OBJECT;
    }

    @Override
    protected ForeignCallDescriptor printfCallDescriptor() {
        return Log.LOG_PRINTF;
    }

    @Override
    protected ResolvedJavaType referenceType() {
        return HotSpotReplacementsUtil.referenceType(INJECTED_METAACCESS);
    }

    @Override
    protected long referentOffset() {
        return HotSpotReplacementsUtil.referentOffset(INJECTED_METAACCESS);
    }

    public static class Templates extends SnippetTemplate.AbstractTemplates {
        private final SnippetTemplate.SnippetInfo shenandoahPreWriteBarrier;
        private final SnippetTemplate.SnippetInfo shenandoahReferentReadBarrier;
        private final SnippetTemplate.SnippetInfo shenandoahArrayRangePreWriteBarrier;

        private final ShenandoahBarrierLowerer lowerer;

        public Templates(OptionValues options, Iterable<DebugHandlersFactory> factories, SnippetCounter.Group.Factory factory, HotSpotProviders providers, TargetDescription target, GraalHotSpotVMConfig config) {
            super(options, factories, providers, providers.getSnippetReflection(), target);
            this.lowerer = new HotSpotShenandoahBarrierSnippets.HotspotShenandoahBarrierLowerer(config, factory);

            HotSpotShenandoahBarrierSnippets receiver = new HotSpotShenandoahBarrierSnippets(config, providers.getRegisters());
            shenandoahPreWriteBarrier = snippet(ShenandoahBarrierSnippets.class, "shenandoahPreWriteBarrier", null, receiver, GC_INDEX_LOCATION, GC_LOG_LOCATION, SATB_QUEUE_MARKING_LOCATION, SATB_QUEUE_INDEX_LOCATION,
                    SATB_QUEUE_BUFFER_LOCATION);
            shenandoahReferentReadBarrier = snippet(ShenandoahBarrierSnippets.class, "shenandoahReferentReadBarrier", null, receiver, GC_INDEX_LOCATION, GC_LOG_LOCATION, SATB_QUEUE_MARKING_LOCATION,
                    SATB_QUEUE_INDEX_LOCATION, SATB_QUEUE_BUFFER_LOCATION);
            shenandoahArrayRangePreWriteBarrier = snippet(ShenandoahBarrierSnippets.class, "shenandoahArrayRangePreWriteBarrier", null, receiver, GC_INDEX_LOCATION, GC_LOG_LOCATION, SATB_QUEUE_MARKING_LOCATION,
                    SATB_QUEUE_INDEX_LOCATION, SATB_QUEUE_BUFFER_LOCATION);
        }

        public void lower(ShenandoahPreWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, shenandoahPreWriteBarrier, barrier, tool);
        }

        public void lower(ShenandoahReferentFieldReadBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, shenandoahReferentReadBarrier, barrier, tool);
        }

        public void lower(ShenandoahArrayRangePreWriteBarrier barrier, LoweringTool tool) {
            lowerer.lower(this, shenandoahArrayRangePreWriteBarrier, barrier, tool);
        }
    }

    static final class HotspotShenandoahBarrierLowerer extends ShenandoahBarrierLowerer {
        private final CompressEncoding oopEncoding;

        HotspotShenandoahBarrierLowerer(GraalHotSpotVMConfig config, SnippetCounter.Group.Factory factory) {
            super(factory);
            oopEncoding = config.useCompressedOops ? config.getOopEncoding() : null;
        }

        @Override
        public ValueNode uncompress(ValueNode expected) {
            assert oopEncoding != null;
            return HotSpotCompressionNode.uncompress(expected, oopEncoding);
        }
    }
}
