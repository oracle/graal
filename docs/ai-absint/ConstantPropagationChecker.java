// Prototype ConstantPropagationChecker that consumes AbstractState-like maps and produces ConstantFacts.
// This is a sketch and should be adapted to your real Checker interface in substratevm.
package docs.ai.absint;

import java.util.*;
import jdk.graal.compiler.nodes.Node;
import com.oracle.graal.pointsto.meta.AnalysisMethod;

public class ConstantPropagationChecker {
    private final FactAPI.FactAggregator agg = new FactAPI.FactAggregator();

    public void run(AnalysisMethod method, Map<Node, Object> invariants) {
        // invariants is a map from Node -> interval or boxed long if constant
        for (var e : invariants.entrySet()) {
            Node node = e.getKey();
            Object v = e.getValue();
            if (v instanceof Long l) {
                agg.add(new FactAPI.ConstantFact(method, node, l));
            }
        }
    }

    public FactAPI.FactAggregator facts() { return agg; }
}

