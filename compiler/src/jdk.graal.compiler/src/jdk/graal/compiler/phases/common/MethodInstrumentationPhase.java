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
        "xalan"       // Xalan
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

            // Increment the sample counter
            ValueNode one = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            AddNode incrementedSampleCounter = graph.addWithoutUnique(new AddNode(loadSampleCounter, one));

            // Store the updated sample counter
            StoreFieldNode storeSampleCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), incrementedSampleCounter));
            graph.addAfterFixed(loadSampleCounter, storeSampleCounter);

            // Define the sampling rate (e.g., every 100 calls)
            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(100), StampFactory.forKind(JavaKind.Int)));

            // Compare the incremented counter with the sampling rate
            LogicNode shouldSample = graph.addWithoutUnique(new IntegerEqualsNode(incrementedSampleCounter, sampleRateNode));

            // Create IfNode with the sampling condition
            IfNode ifNode = graph.add(new IfNode(shouldSample, instrumentationBegin, skipInstrumentationBegin, BranchProbabilityNode.NOT_FREQUENT_PROFILE));


            // Create Merges
            MergeNode merge = graph.add(new MergeNode());
            merge.addForwardEnd(instrumentationEnd);
            merge.addForwardEnd(skipEnd);

            // =========================
            // Instrumentation Branch
            // =========================

            // Connect instrumentationBegin to startTime
            ForeignCallNode startTime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
            graph.addAfterFixed(instrumentationBegin, startTime);

            // =========================
            // Skip Instrumentation Branch
            // =========================

            // =========================
            // Merge and Continue
            // =========================
            storeSampleCounter.setNext(ifNode);
            merge.setNext(ogStartNext);

            // =========================
            // Optional: Instrument Return Nodes
            // =========================

            // Uncomment and implement if you decide to instrument return nodes
            /*
            for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
                instrumentReturnNode(graph, context, returnNode, startTime, shouldSample, idNode);
            }
            */

            // =========================
            // Graph Verification
            // =========================

            // Optionally verify the graph after instrumentation
            // verifyGraph(graph, "after instrumentation");

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
     * Creates a sampling condition based on the sample counter.
     *
     * @param graph   the structured graph
     * @param context the high tier context
     * @return a {@link LogicNode} representing the sampling condition
     */
    private LogicNode createSamplingCondition(StructuredGraph graph, HighTierContext context) {
        LogicNode samplingCondition;
        try {
            // Load the current sample counter
            LoadFieldNode loadSampleCounter = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter"))));
            graph.addAfterFixed(graph.start(), loadSampleCounter);

            // Increment the sample counter
            ValueNode one = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
            AddNode incrementedSampleCounter = graph.addWithoutUnique(new AddNode(loadSampleCounter, one));

            // Store the updated sample counter
            StoreFieldNode storeSampleCounter = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("sampleCounter")), incrementedSampleCounter));
            graph.addAfterFixed(loadSampleCounter, storeSampleCounter);

            // Define the sampling rate (e.g., every 100 calls)
            ValueNode sampleRateNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(100), StampFactory.forKind(JavaKind.Int)));

            // Compare the incremented counter with the sampling rate
            samplingCondition = graph.addWithoutUnique(new IntegerEqualsNode(incrementedSampleCounter, sampleRateNode));
        } catch (NoSuchFieldException e) {
            // Handle the error appropriately; default to never sampling
            samplingCondition = LogicConstantNode.contradiction(graph);
        }

        return samplingCondition;
    }

    /**
     * Instruments a return node by adding timing and logging logic.
     *
     * @param graph          the structured graph
     * @param context        the high tier context
     * @param returnNode     the return node to instrument
     * @param startTime      the start time measurement node
     * @param shouldSample   the sampling condition node
     * @param idNode         the unique identifier node
     */
    private void instrumentReturnNode(StructuredGraph graph, HighTierContext context, ReturnNode returnNode, ForeignCallNode startTime, LogicNode shouldSample, ValueNode idNode) {
        try {
            FixedWithNextNode predecessor = (FixedWithNextNode) returnNode.predecessor();
            if (predecessor == null) {
                throw new RuntimeException("Return node has no predecessor");
            }

            // Create new branches based on the sampling condition
            BeginNode trueSuccessor = graph.add(new BeginNode());
            BeginNode falseSuccessor = graph.add(new BeginNode());
            IfNode ifNode = graph.add(new IfNode(shouldSample, trueSuccessor, falseSuccessor, BranchProbabilityNode.SLOW_PATH_PROFILE));

            // Insert the new IfNode before the return node in the fixed chain
            predecessor.setNext(ifNode);

            // Add end time measurement and compute the time difference in the true branch
            ForeignCallNode endTime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
            trueSuccessor.setNext(endTime);

            SubNode timeDiff = graph.addWithoutUnique(new SubNode(endTime, startTime));

            FixedWithNextNode lastNode = endTime;

            // Write the instrumentation data to the buffer
            try {
                // Load the buffer
                LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
                graph.addAfterFixed(lastNode, readBuffer);
                lastNode = readBuffer;

                // Load the current pointer
                LoadFieldNode readPointer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer"))));
                graph.addAfterFixed(lastNode, readPointer);
                lastNode = readPointer;

                // Write the unique identifier to the buffer
                StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, readPointer, null, null, JavaKind.Long, idNode));
                graph.addAfterFixed(lastNode, writeToBufferID);
                lastNode = writeToBufferID;

                // Increment the pointer
                ValueNode oneNode = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
                AddNode pointerPlus1 = graph.addWithoutUnique(new AddNode(readPointer, oneNode));

                // Write the time difference to the buffer
                StoreIndexedNode writeToBufferTime = graph.add(new StoreIndexedNode(readBuffer, pointerPlus1, null, null, JavaKind.Long, timeDiff));
                graph.addAfterFixed(lastNode, writeToBufferTime);
                lastNode = writeToBufferTime;

                // Store the updated buffer back
                StoreFieldNode writeBufferBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer")), readBuffer));
                graph.addAfterFixed(lastNode, writeBufferBack);
                lastNode = writeBufferBack;

                // Increment the pointer again
                AddNode pointerPlus2 = graph.addWithoutUnique(new AddNode(pointerPlus1, oneNode));

                // Store the updated pointer back
                StoreFieldNode writePointerBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer")), pointerPlus2));
                graph.addAfterFixed(lastNode, writePointerBack);
                lastNode = writePointerBack;
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("Field not found: " + e.getMessage(), e);
            }

            // Create a merge node for the new branches
            MergeNode mergeNode = graph.add(new MergeNode());

            // Connect the false branch to the merge
            EndNode falseEnd = graph.add(new EndNode());
            falseSuccessor.setNext(falseEnd);
            mergeNode.addForwardEnd(falseEnd);

            // Connect the true branch to the merge
            EndNode trueEnd = graph.add(new EndNode());
            lastNode.setNext(trueEnd);
            mergeNode.addForwardEnd(trueEnd);

            // Connect the merge node to the original return node
            mergeNode.setNext(returnNode);

        } catch (Throwable e) {
            throw new RuntimeException("Instrumentation of return node failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the integrity of the graph at a specific point.
     *
     * @param graph the structured graph
     * @param point the point in the graph to verify
     */
    private void verifyGraph(StructuredGraph graph, String point) {
        try {
            graph.verify();
        } catch (AssertionError e) {
            throw new RuntimeException("Graph verification failed at " + point + ": " + e.getMessage(), e);
        }
    }
}
