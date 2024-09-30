package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_MILLIS;
// import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.BUBU_CACHE_ROTATEBUFFER;

import java.util.Optional;

import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.meta.joonhwan.BuboCache;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;


public class SamplingForeignCallPhase extends BasePhase<HighTierContext> {


    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    public SamplingForeignCallPhase() {
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, HighTierContext context) {

        // if (!"fibonacciHot".equals(graph.method().getName())) {
        //     return; // Skip instrumentation for other methods
        // }
        
        ForeignCallNode[] returnNodesTime =  new ForeignCallNode[graph.getNodes(ReturnNode.TYPE).count()];
        ForeignCallNode startTime = graph.add(new ForeignCallNode(JAVA_TIME_MILLIS, ValueNode.EMPTY_ARRAY));
        graph.addAfterFixed(graph.start(), startTime);

        int pointer = 0;
        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            
            try (DebugCloseable s = returnNode.asFixedNode().withNodeSourcePosition()) {
            ForeignCallNode javaCurrentCPUtime = graph.add(new ForeignCallNode(JAVA_TIME_MILLIS, ValueNode.EMPTY_ARRAY));
            graph.addBeforeFixed(returnNode, javaCurrentCPUtime);
            returnNodesTime[pointer] = javaCurrentCPUtime;
            pointer++;
            }          
        }

        Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
        ValueNode ID = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(id), StampFactory.forKind(JavaKind.Long)));

        for (ForeignCallNode returnNode : returnNodesTime) {
            
            //   SubNode Time = graph.addWithoutUnique(new SubNode(returnNode,startTime));

              try {
                //Read the buffer form the static class
                LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
                graph.addAfterFixed(returnNode, readBuffer);

                // read the pointer from the static class
                LoadFieldNode readPointer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer"))));
                graph.addAfterFixed(readBuffer, readPointer);

                // write to the read buffer the ID using the pointer as the index
                StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, readPointer.asNode(), null, null, JavaKind.Long, ID));
                graph.addAfterFixed(readPointer,writeToBufferID);

                //increment pointer
                ValueNode one = graph.addWithoutUnique(new ConstantNode(JavaConstant.forInt(1), StampFactory.forKind(JavaKind.Int)));
                AddNode pointerPlus1 = graph.addWithoutUnique(new AddNode(readPointer, one));
                
                // write to the buffer the time using the incremented pointer
                StoreIndexedNode writeToBufferStartTime = graph.add(new StoreIndexedNode(readBuffer, pointerPlus1, null, null, JavaKind.Long, startTime));
                graph.addAfterFixed(writeToBufferID,writeToBufferStartTime);

                // add one for the pointer again
                AddNode pointerPlus2 = graph.addWithoutUnique(new AddNode(pointerPlus1, one));

                // Write the end time to the buffer
                StoreIndexedNode writeToBufferEndTime = graph.add(new StoreIndexedNode(readBuffer, pointerPlus2, null, null, JavaKind.Long, returnNode));
                graph.addAfterFixed(writeToBufferStartTime, writeToBufferEndTime);

                // Increment pointer once more
                AddNode pointerPlus3 = graph.addWithoutUnique(new AddNode(pointerPlus2, one));

                // Write the changed buffer back to the static class
                StoreFieldNode writeBufferBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer")), readBuffer));
                graph.addAfterFixed(writeToBufferEndTime, writeBufferBack);
                
                //update the pointer
                StoreFieldNode writePointerBack= graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("pointer")), pointerPlus3));
                graph.addAfterFixed(writeBufferBack, writePointerBack);

            } catch (Exception e) {
                e.printStackTrace();
            }

            //  try {
            //     //Read the buffer form the static class
            //     LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
            //     graph.addAfterFixed(returnNode, readBuffer);

            //     //read the value currently store in the eleiment of the ID
            //     LoadIndexedNode readCurrentValueAtIndex = graph.add(new LoadIndexedNode(null, readBuffer, ID, null, JavaKind.Long));
            //     graph.addAfterFixed(readBuffer,readCurrentValueAtIndex);
                
            //     //Aggergate the the time with the current value of this index
            //     AddNode aggergatedValue = graph.addWithoutUnique(new AddNode(readCurrentValueAtIndex, Time));

            //     // write to the read buffer the ID using the pointer as the index
            //     StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, ID, null, null, JavaKind.Long, aggergatedValue));
            //     graph.addAfterFixed(readCurrentValueAtIndex ,writeToBufferID);

            //     // write the changed buffer back to the static class
            //     StoreFieldNode WriteBufferBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer")), readBuffer));
            //     graph.addAfterFixed(writeToBufferID,WriteBufferBack);

            // } catch (Exception e) {
            //     e.printStackTrace();
            // }
       }
    }
}
