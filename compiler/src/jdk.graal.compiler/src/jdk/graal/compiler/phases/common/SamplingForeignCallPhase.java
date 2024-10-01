package jdk.graal.compiler.phases.common;

import java.util.Optional;
import java.util.Arrays;
import java.util.List;

import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.SAMPLE_METHOD;
import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.nodes.ConstantNode;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;


public class SamplingForeignCallPhase extends BasePhase<HighTierContext> {

    //TODO: make this an argument..?
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

    public SamplingForeignCallPhase() {
    }

    private boolean shouldInstrument(StructuredGraph graph) {
        String className = graph.method().getDeclaringClass().getName().replace('/', '.').toLowerCase();
        for (String benchmark : BENCHMARK_NAMES) {
            if (className.contains(benchmark.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, HighTierContext context) {

        if (!shouldInstrument(graph)) {
            return;
        }
        //COMPILATION ID
        Long id = Long.parseLong(graph.compilationId().toString(Verbosity.ID).split("-")[1]);
        ValueNode ID = graph.addWithoutUnique(new ConstantNode(JavaConstant.forLong(id), StampFactory.forKind(JavaKind.Long)));
        
        ForeignCallNode startTime = graph.add(new ForeignCallNode(SAMPLE_METHOD, ID));
        graph.addAfterFixed(graph.start(), startTime);

        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            ForeignCallNode javaCurrentCPUtime = graph.add(new ForeignCallNode(SAMPLE_METHOD, ID));
            graph.addBeforeFixed(returnNode, javaCurrentCPUtime);       
        }
    }
}
