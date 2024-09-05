package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.JAVA_TIME_NANOS;
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


public class MethodInstrumentationPhase extends BasePhase<HighTierContext> {


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
        
        ForeignCallNode[] returnNodesTime =  new ForeignCallNode[graph.getNodes(ReturnNode.TYPE).count()];
        ForeignCallNode startTime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
        graph.addAfterFixed(graph.start(), startTime);



        int pointer = 0;
        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            
            try (DebugCloseable s = returnNode.asFixedNode().withNodeSourcePosition()) {
            ForeignCallNode javaCurrentCPUtime = graph.add(new ForeignCallNode(JAVA_TIME_NANOS, ValueNode.EMPTY_ARRAY));
            graph.addBeforeFixed(returnNode, javaCurrentCPUtime);
            returnNodesTime[pointer] = javaCurrentCPUtime;
            pointer++;
            }          
        }
        // get comp ID
        Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
        ValueNode ID = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(0), StampFactory.forKind(JavaKind.Long)));

        for (ForeignCallNode returnNode : returnNodesTime) {
            
              SubNode Time = graph.addWithoutUnique(new SubNode(returnNode,startTime));

             try {
                //Read the buffer form the static class
                LoadFieldNode readBuffer = graph.add(LoadFieldNode.create(null, null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer"))));
                graph.addAfterFixed(returnNode, readBuffer);


                //read the value currently store in the eleiment of the ID
                LoadIndexedNode readCurrentValueAtIndex = graph.add(new LoadIndexedNode(null, readBuffer, ID, null, JavaKind.Long));
                graph.addAfterFixed(readBuffer,readCurrentValueAtIndex);
                
                //Aggergate the the time with the current value of this index
                AddNode aggergatedValue = graph.addWithoutUnique(new AddNode(readCurrentValueAtIndex, Time));

                // write to the read buffer the ID using the pointer as the index
                StoreIndexedNode writeToBufferID = graph.add(new StoreIndexedNode(readBuffer, ID, null, null, JavaKind.Long, aggergatedValue));
                graph.addAfterFixed(readCurrentValueAtIndex ,writeToBufferID);

                // write the changed buffer back to the static class
                StoreFieldNode WriteBufferBack = graph.add(new StoreFieldNode(null, context.getMetaAccess().lookupJavaField(BuboCache.class.getField("Buffer")), readBuffer));
                graph.addAfterFixed(writeToBufferID,WriteBufferBack);

            } catch (Exception e) {
                e.printStackTrace();
            }
       }
    }
}
