package jdk.graal.compiler.phases.common;

import java.util.Arrays;
import java.util.List;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS;

import java.util.Optional;

import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache;
import jdk.graal.compiler.nodes.*;
import jdk.graal.compiler.nodes.calc.*;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;

public class MethodInstrumentationPhase extends BasePhase<HighTierContext> {
 
    // Define the list of benchmark identifiers
    private static final List<String> BENCHMARK_NAMES = Arrays.asList(
        "sunflow",    // Sunflow
        "batik",      // Batik
        "derby",      // Derby
        "eclipse",    // Eclipse
        "fop",        // FOP
        "jfree",      // JFree
        "menalto",    // Menalto
        "sablecc",    // SableCC
        "xalan",       // Xalan
        "pmd"
        // Add other DaCapo benchmark names here
    );

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    public MethodInstrumentationPhase() {
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, HighTierContext context) {
        if (!shouldInstrument(graph)) {
            return;
        }

        try {
            // Extract a unique identifier for the compilation
            Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
            ValueNode idNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(id), StampFactory.forKind(JavaKind.Long)));

            // make endnodes
            EndNode instrumentationEnd = graph.add(new EndNode());
            EndNode skipEnd = graph.add(new EndNode());

            // Create BeginNodes for instrumentation and skip paths
            BeginNode instrumentationBegin = graph.add(new BeginNode());
            instrumentationBegin.setNext(instrumentationEnd);
            BeginNode skipInstrumentationBegin = graph.add(new BeginNode());
            skipInstrumentationBegin.setNext(skipEnd);

            // Save start.next
            FixedNode ogStartNext = graph.start().next();

            // Create sampling condition compares current sample count with sampling rate
            // inserts two nodes after the start and before the ogStartNext
            // Load the current sample counter
            LoadFieldNode loadSampleCounter = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter"))));
            graph.addAfterFixed(graph.start(), loadSampleCounter);

            ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            // AddNode incSampleCount = graph.addWithoutUnique(new AddNode(loadSampleCounter, oneConstantNode));
            // StoreFieldNode writeIncCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), incSampleCount));
            // graph.addAfterFixed(loadSampleCounter, writeIncCounter);

            // Define the sampling rate (e.g., every 100 calls)
            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1000), StampFactory.forKind(JavaKind.Int)));

            // Compare the incremented counter with the sampling rate
            LogicNode shouldSample = graph.addWithoutUnique(new IntegerEqualsNode(loadSampleCounter, sampleRateNode));

            // Create IfNode with the sampling condition
            IfNode ifNode = graph.add(new IfNode(shouldSample, instrumentationBegin, skipInstrumentationBegin, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Create Merges
            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.addForwardEnd(skipEnd);

            // Connect instrumentationBegin to startTime
            ForeignCallNode startTime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
            graph.addAfterFixed(instrumentationBegin, startTime);

            LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
            graph.addAfterFixed(startTime, readBuffer);
            //Read Pointer of Buffer index
            LoadFieldNode readPointer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer"))));
            graph.addAfterFixed(readBuffer, readPointer);
            //Write to Buffer
            StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, readPointer, null, null, JavaKind.Long, idNode));
            graph.addAfterFixed(readPointer, writeToBufferID);
            //increment ptr
            AddNode incrementPointer = graph.addWithoutUnique(new AddNode(readPointer, oneConstantNode));
            //Write startTime to Buffer
            StoreIndexedNode writeStartTime = graph.add(new StoreIndexedNode(readBuffer, incrementPointer, null, null, JavaKind.Long, startTime));
            graph.addAfterFixed(writeToBufferID, writeStartTime);
           
            StoreFieldNode writePointerBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer")), incrementPointer));
            graph.addAfterFixed(writeStartTime, writePointerBack);

            loadSampleCounter.setNext(ifNode);
            merge.setNext(ogStartNext);

            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                instrumentReturnNode(graph, context, returnNode, startTime, idNode);
            }
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Determines whether the given graph should be instrumented.
     *
     * @param graph the structured graph representing the method
     * @return {@code true} if the method should be instrumented, {@code false} otherwise
     */
    private boolean shouldInstrument(StructuredGraph graph) {
        String className = graph.method().getDeclaringClass().getName().replace('/', '.').toLowerCase();
        for (String benchmark : BENCHMARK_NAMES) {
            if (className.contains(benchmark.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Instruments a return node by adding timing and logging logic.
     *
     * @param graph          the structured graph
     * @param context        the high tier context
     * @param returnNode     the return node to instrument
     * @param startTime      the start time measurement node
     * @param idNode         the unique identifier node
     */
    private void instrumentReturnNode(StructuredGraph graph, HighTierContext context, ReturnNode returnNode, ForeignCallNode startTime, ValueNode idNode) {
        try {
            FixedWithNextNode predecessor = (FixedWithNextNode) returnNode.predecessor();
            if (predecessor == null) {
                throw new RuntimeException("Return node has no predecessor");
            }

            // make endnodes
            EndNode instrumentationEnd = graph.add(new EndNode());
            EndNode skipEnd = graph.add(new EndNode());

            // Create BeginNodes for instrumentation and skip paths
            BeginNode instrumentationBegin = graph.add(new BeginNode());
            instrumentationBegin.setNext(instrumentationEnd);
            BeginNode skipInstrumentationBegin = graph.add(new BeginNode());
            skipInstrumentationBegin.setNext(skipEnd);

            //Load sampleCounter
            LoadFieldNode loadSampleCounter = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter"))));
            graph.addAfterFixed(predecessor, loadSampleCounter);

            ValueNode oneConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            AddNode incSampleCount = graph.addWithoutUnique(new AddNode(loadSampleCounter, oneConstantNode));
            StoreFieldNode writeIncCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), incSampleCount));
            graph.addAfterFixed(loadSampleCounter, writeIncCounter);

            // MAGIC NUMBER ALERT
            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1000), StampFactory.forKind(JavaKind.Int)));

            // Compare the incremented counter with the sampling rate
            LogicNode shouldSample = graph.addWithoutUnique(new IntegerEqualsNode(loadSampleCounter, sampleRateNode));

            // Create new branches based on the sampling condition
            IfNode ifNode = graph.add(new IfNode(shouldSample, instrumentationBegin, skipInstrumentationBegin, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Create Merges
            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.addForwardEnd(skipEnd);

            // Connect instrumentationBegin to startTime
            ForeignCallNode endTime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
            graph.addAfterFixed(instrumentationBegin, endTime);
            //Read Buffer to write ID, startTime, and endTime
            LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
            graph.addAfterFixed(endTime, readBuffer);
            //Read Pointer of Buffer index
            LoadFieldNode readPointer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer"))));
            graph.addAfterFixed(readBuffer, readPointer);
            //write compilationID to buffer at curGotrent ptr
            AddNode pointerIncrement = graph.addWithoutUnique(new AddNode(readPointer, oneConstantNode));
            StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, pointerIncrement, null, null, JavaKind.Long, idNode));
            graph.addAfterFixed(readPointer, writeToBufferID);
            //increment ptr
            AddNode pointerIncrement2 = graph.addWithoutUnique(new AddNode(pointerIncrement, oneConstantNode));
            StoreIndexedNode writeEndTime = graph.add(new StoreIndexedNode(readBuffer, pointerIncrement2, null, null, JavaKind.Long, endTime));
            graph.addAfterFixed(readPointer, writeEndTime);
            // Store incremented the pointer

            AddNode pointerIncrement3 = graph.addWithoutUnique(new AddNode(pointerIncrement2, oneConstantNode));
            StoreFieldNode writePointerBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer")), pointerIncrement3));
            graph.addAfterFixed(writeEndTime, writePointerBack);
            //reset counter
            ValueNode zeroConstantNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(0), StampFactory.forKind(JavaKind.Int)));
            StoreFieldNode resetCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), zeroConstantNode));
            graph.addAfterFixed(writePointerBack, resetCounter);

            // =========================
            // Merge and Continue
            // =========================
            writeIncCounter.setNext(ifNode);
            merge.setNext(returnNode);
        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation of return node failed: " + e.getMessage(), e);
        }
    }
}