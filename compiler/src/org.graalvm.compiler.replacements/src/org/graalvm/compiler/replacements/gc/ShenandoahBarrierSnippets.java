package org.graalvm.compiler.replacements.gc;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.NullCheckNode;
import org.graalvm.compiler.nodes.gc.ShenandoahArrayRangePreWriteBarrier;
import org.graalvm.compiler.nodes.gc.ShenandoahLoadReferenceBarrier;
import org.graalvm.compiler.nodes.gc.ShenandoahPreWriteBarrier;
import org.graalvm.compiler.nodes.gc.ShenandoahReferentFieldReadBarrier;
import org.graalvm.compiler.nodes.java.InstanceOfNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.type.NarrowOopStamp;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.AssertionNode;
import org.graalvm.compiler.replacements.nodes.CStringConstant;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

public abstract class ShenandoahBarrierSnippets extends WriteBarrierSnippets implements Snippets {

    public static final byte HAS_FORWORDED = 1 << 0;

    public static final LocationIdentity GC_LOG_LOCATION = NamedLocationIdentity.mutable("Shenandoah-GC-Log");
    public static final LocationIdentity GC_INDEX_LOCATION = NamedLocationIdentity.mutable("Shenandoah-GC-Index");
    public static final LocationIdentity SATB_QUEUE_MARKING_LOCATION = NamedLocationIdentity.mutable("Shenandoah-GC-Queue-Marking");
    public static final LocationIdentity SATB_QUEUE_INDEX_LOCATION = NamedLocationIdentity.mutable("Shenandoah-GC-Queue-Index");
    public static final LocationIdentity SATB_QUEUE_BUFFER_LOCATION = NamedLocationIdentity.mutable("GShenandoah-C-Queue-Buffer");
    public static final LocationIdentity GC_STATE_LOCATION = NamedLocationIdentity.mutable("Shenandoah-GC-State");

    protected static final LocationIdentity[] KILLED_PRE_WRITE_BARRIER_STUB_LOCATIONS = new LocationIdentity[]{SATB_QUEUE_INDEX_LOCATION, SATB_QUEUE_BUFFER_LOCATION, GC_LOG_LOCATION,
            GC_INDEX_LOCATION};

    public static class Counters {
        Counters(SnippetCounter.Group.Factory factory) {
            SnippetCounter.Group countersBarriers = factory.createSnippetCounterGroup("Shenandoah Barriers");
            shenandoahAttemptedPreWriteBarrierCounter = new SnippetCounter(countersBarriers, "shenandoahAttemptedPreWriteBarrier", "Number of attempted Shenandoah Pre Write Barriers");
            shenandoahEffectivePreWriteBarrierCounter = new SnippetCounter(countersBarriers, "shenandoahEffectivePreWriteBarrier", "Number of effective Shenandoah Pre Write Barriers");
            shenandoahExecutedPreWriteBarrierCounter = new SnippetCounter(countersBarriers, "shenandoahExecutedPreWriteBarrier", "Number of executed Shenandoah Pre Write Barriers");
        }

        final SnippetCounter shenandoahAttemptedPreWriteBarrierCounter;
        final SnippetCounter shenandoahEffectivePreWriteBarrierCounter;
        final SnippetCounter shenandoahExecutedPreWriteBarrierCounter;
    }

    @Snippet
    public void shenandoahPreWriteBarrier(AddressNode.Address address, Object object, Object expectedObject, @Snippet.ConstantParameter boolean doLoad, @Snippet.ConstantParameter boolean nullCheck,
                                          @Snippet.ConstantParameter int traceStartCycle, @Snippet.ConstantParameter ShenandoahBarrierSnippets.Counters counters) {
        if (nullCheck) {
            NullCheckNode.nullCheck(address);
        }
        Word thread = getThread();
        verifyOop(object);
        Word field = Word.fromAddress(address);
        byte markingValue = thread.readByte(satbQueueMarkingOffset(), SATB_QUEUE_MARKING_LOCATION);

        boolean trace = isTracingActive(traceStartCycle);
        int gcCycle = 0;
        if (trace) {
            Pointer gcTotalCollectionsAddress = WordFactory.pointer(gcTotalCollectionsAddress());
            gcCycle = (int) gcTotalCollectionsAddress.readLong(0);
            log(trace, "[%d] Shenandoah-Pre Thread %p Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(object).rawValue());
            log(trace, "[%d] Shenandoah-Pre Thread %p Expected Object %p\n", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(expectedObject).rawValue());
            log(trace, "[%d] Shenandoah-Pre Thread %p Field %p\n", gcCycle, thread.rawValue(), field.rawValue());
            log(trace, "[%d] Shenandoah-Pre Thread %p Marking %d\n", gcCycle, thread.rawValue(), markingValue);
            log(trace, "[%d] Shenandoah-Pre Thread %p DoLoad %d\n", gcCycle, thread.rawValue(), doLoad ? 1L : 0L);
        }

        counters.shenandoahAttemptedPreWriteBarrierCounter.inc();
        // If the concurrent marker is enabled, the barrier is issued.
        if (probability(NOT_FREQUENT_PROBABILITY, markingValue != (byte) 0)) {
            // If the previous value has to be loaded (before the write), the load is issued.
            // The load is always issued except the cases of CAS and referent field.
            Object previousObject;
            if (doLoad) {
                previousObject = field.readObject(0, OnHeapMemoryAccess.BarrierType.NONE);
                if (trace) {
                    log(trace, "[%d] G1-Pre Thread %p Previous Object %p\n ", gcCycle, thread.rawValue(), Word.objectToTrackedPointer(previousObject).rawValue());
                    verifyOop(previousObject);
                }
            } else {
                previousObject = FixedValueAnchorNode.getObject(expectedObject);
            }

            counters.shenandoahEffectivePreWriteBarrierCounter.inc();
            // If the previous value is null the barrier should not be issued.
            if (probability(FREQUENT_PROBABILITY, previousObject != null)) {
                counters.shenandoahExecutedPreWriteBarrierCounter.inc();
                // If the thread-local SATB buffer is full issue a native call which will
                // initialize a new one and add the entry.
                Word indexAddress = thread.add(satbQueueIndexOffset());
                Word indexValue = indexAddress.readWord(0, SATB_QUEUE_INDEX_LOCATION);
                if (probability(FREQUENT_PROBABILITY, indexValue.notEqual(0))) {
                    Word bufferAddress = thread.readWord(satbQueueBufferOffset(), SATB_QUEUE_BUFFER_LOCATION);
                    Word nextIndex = indexValue.subtract(wordSize());
                    Word logAddress = bufferAddress.add(nextIndex);
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    Word previousOop = Word.objectToTrackedPointer(previousObject);
                    logAddress.writeWord(0, previousOop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, nextIndex, GC_INDEX_LOCATION);
                } else {
                    shenandoahPreBarrierStub(previousObject);
                }
            }
        }
    }

    @Snippet
    public void shenandoahReferentReadBarrier(AddressNode.Address address, Object object, Object expectedObject, @Snippet.ConstantParameter boolean isDynamicCheck, Word offset,
                                             @Snippet.ConstantParameter int traceStartCycle, @Snippet.ConstantParameter ShenandoahBarrierSnippets.Counters counters) {
        if (!isDynamicCheck ||
                (offset == WordFactory.unsigned(referentOffset()) && InstanceOfNode.doInstanceof(referenceType(), object))) {
            shenandoahPreWriteBarrier(address, object, expectedObject, false, false, traceStartCycle, counters);
        }
    }

    @Snippet
    public void shenandoahArrayRangePreWriteBarrier(AddressNode.Address address, int length, @Snippet.ConstantParameter int elementStride) {
        Word thread = getThread();
        byte markingValue = thread.readByte(satbQueueMarkingOffset(), SATB_QUEUE_MARKING_LOCATION);
        // If the concurrent marker is not enabled or the vector length is zero, return.
        if (probability(FREQUENT_PROBABILITY, markingValue == (byte) 0 || length == 0)) {
            return;
        }

        Word bufferAddress = thread.readWord(satbQueueBufferOffset(), SATB_QUEUE_BUFFER_LOCATION);
        Word indexAddress = thread.add(satbQueueIndexOffset());
        long indexValue = indexAddress.readWord(0, SATB_QUEUE_INDEX_LOCATION).rawValue();
        int scale = objectArrayIndexScale();
        Word start = getPointerToFirstArrayElement(address, length, elementStride);

        for (int i = 0; i < length; i++) {
            Word arrElemPtr = start.add(i * scale);
            Object previousObject = arrElemPtr.readObject(0, OnHeapMemoryAccess.BarrierType.NONE);
            verifyOop(previousObject);
            if (probability(FREQUENT_PROBABILITY, previousObject != null)) {
                if (probability(FREQUENT_PROBABILITY, indexValue != 0)) {
                    indexValue = indexValue - wordSize();
                    Word logAddress = bufferAddress.add(WordFactory.unsigned(indexValue));
                    // Log the object to be marked as well as update the SATB's buffer next index.
                    Word previousOop = Word.objectToTrackedPointer(previousObject);
                    logAddress.writeWord(0, previousOop, GC_LOG_LOCATION);
                    indexAddress.writeWord(0, WordFactory.unsigned(indexValue), GC_INDEX_LOCATION);
                } else {
                    shenandoahPreBarrierStub(previousObject);
                }
            }
        }
    }

    @Snippet
    public Object shenandoahLoadReferenceBarrier(Object value) {
        Word thread = getThread();
        verifyOop(value);
        byte gcStateValue = thread.readByte(gcStateOffset(), GC_STATE_LOCATION);
        if (probability(NOT_FREQUENT_PROBABILITY, (gcStateValue & HAS_FORWORDED) != (byte) 0)) {
            return shenandoahLoadReferenceBarrierStub(value);
        }
        return value;
    }

    protected abstract Word getThread();

    protected abstract int wordSize();

    protected abstract int objectArrayIndexScale();

    protected abstract int satbQueueMarkingOffset();

    protected abstract int satbQueueBufferOffset();

    protected abstract int satbQueueIndexOffset();

    protected abstract int gcStateOffset();

    protected abstract ForeignCallDescriptor preWriteBarrierCallDescriptor();

    protected abstract ForeignCallDescriptor loadReferenceBarrierCallDescriptor();

    // the data below is only needed for the verification logic
    protected abstract boolean verifyOops();

    protected abstract boolean verifyBarrier();

    protected abstract long gcTotalCollectionsAddress();

    protected abstract ForeignCallDescriptor verifyOopCallDescriptor();

    protected abstract ForeignCallDescriptor validateObjectCallDescriptor();

    protected abstract ForeignCallDescriptor printfCallDescriptor();

    protected abstract ResolvedJavaType referenceType();

    protected abstract long referentOffset();

    private boolean isTracingActive(int traceStartCycle) {
        return traceStartCycle > 0 && ((Pointer) WordFactory.pointer(gcTotalCollectionsAddress())).readLong(0) > traceStartCycle;
    }

    private void log(boolean enabled, String format, long value1, long value2, long value3) {
        if (enabled) {
            printf(printfCallDescriptor(), CStringConstant.cstring(format), value1, value2, value3);
        }
    }

    /**
     * Validation helper method which performs sanity checks on write operations. The addresses of
     * both the object and the value being written are checked in order to determine if they reside
     * in a valid heap region. If an object is stale, an invalid access is performed in order to
     * prematurely crash the VM and debug the stack trace of the faulty method.
     */
    private void validateObject(Object parent, Object child) {
        if (verifyOops() && child != null) {
            Word parentWord = Word.objectToTrackedPointer(parent);
            Word childWord = Word.objectToTrackedPointer(child);
            boolean success = validateOop(validateObjectCallDescriptor(), parentWord, childWord);
            AssertionNode.dynamicAssert(success, "Verification ERROR, Parent: %p Child: %p\n", parentWord.rawValue(), childWord.rawValue());
        }
    }

    private void verifyOop(Object object) {
        if (verifyOops()) {
            verifyOopStub(verifyOopCallDescriptor(), object);
        }
    }

    private void shenandoahPreBarrierStub(Object previousObject) {
        shenandoahPreBarrierStub(preWriteBarrierCallDescriptor(), previousObject);
    }

    private Object shenandoahLoadReferenceBarrierStub(Object value) {
        return shenandoahLoadReferenceBarrierStub(loadReferenceBarrierCallDescriptor(), value);
    }

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native Object verifyOopStub(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native boolean validateOop(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Word parent, Word object);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native void shenandoahPreBarrierStub(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Object object);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native Object shenandoahLoadReferenceBarrierStub(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Object value);

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native void printf(@Node.ConstantNodeParameter ForeignCallDescriptor logPrintf, Word format, long v1, long v2, long v3);

    public abstract static class ShenandoahBarrierLowerer {
        private final ShenandoahBarrierSnippets.Counters counters;

        public ShenandoahBarrierLowerer(SnippetCounter.Group.Factory factory) {
            this.counters = new ShenandoahBarrierSnippets.Counters(factory);
        }

        public void lower(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, ShenandoahPreWriteBarrier barrier, LoweringTool tool) {
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            AddressNode address = barrier.getAddress();
            args.add("address", address);
            if (address instanceof OffsetAddressNode) {
                args.add("object", ((OffsetAddressNode) address).getBase());
            } else {
                args.add("object", null);
            }

            ValueNode expected = barrier.getExpectedObject();
            if (expected != null && expected.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                expected = uncompress(expected);
            }
            args.add("expectedObject", expected);

            args.addConst("doLoad", barrier.doLoad());
            args.addConst("nullCheck", barrier.getNullCheck());
            args.addConst("traceStartCycle", traceStartCycle(barrier.graph()));
            args.addConst("counters", counters);

            templates.template(barrier, args).instantiate(templates.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, ShenandoahReferentFieldReadBarrier barrier, LoweringTool tool) {
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            // This is expected to be lowered before address lowering
            OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();
            args.add("address", address);
            args.add("object", address.getBase());

            ValueNode expected = barrier.getExpectedObject();
            if (expected != null && expected.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp) {
                expected = uncompress(expected);
            }

            args.add("expectedObject", expected);
            args.addConst("isDynamicCheck", barrier.isDynamicCheck());
            args.add("offset", address.getOffset());
            args.addConst("traceStartCycle", traceStartCycle(barrier.graph()));
            args.addConst("counters", counters);

            templates.template(barrier, args).instantiate(templates.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, ShenandoahArrayRangePreWriteBarrier barrier, LoweringTool tool) {
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("address", barrier.getAddress());
            args.add("length", barrier.getLength());
            args.addConst("elementStride", barrier.getElementStride());

            templates.template(barrier, args).instantiate(templates.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

        public void lower(SnippetTemplate.AbstractTemplates templates, SnippetTemplate.SnippetInfo snippet, ShenandoahLoadReferenceBarrier barrier, LoweringTool tool) {
            SnippetTemplate.Arguments args = new SnippetTemplate.Arguments(snippet, barrier.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("value", barrier.getValue());
            templates.template(barrier, args).instantiate(templates.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }


        private static int traceStartCycle(StructuredGraph graph) {
            return GraalOptions.GCDebugStartCycle.getValue(graph.getOptions());
        }

        protected abstract ValueNode uncompress(ValueNode value);
    }
}
