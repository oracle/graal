package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.checker.rule.CheckerRule;
import com.oracle.svm.hosted.analysis.ai.checker.rule.CheckerRuleResult;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.WorkListFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.transfer.IntIntervalTransferFunction;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Basic division by zero checker
 * TODO implement the actual rules
 */
public class DivisionByZeroChecker implements Checker<IntInterval> {
    private final DebugContext debug;
    private final FixpointIterator<IntInterval> fixpointIterator;
    private final CheckerRule<IntInterval> rule = new CheckerRule<>() {
        @Override
        public CheckerRuleResult evaluateRule(Node node, IntInterval domain) {
            return CheckerRuleResult.OK;
        }
    };

    public DivisionByZeroChecker(DebugContext debug, StructuredGraph graph) {
        this.debug = debug;
        TransferFunction<IntInterval> transfer = new IntIntervalTransferFunction();
        IntInterval domain = new IntInterval();
        this.fixpointIterator = new WorkListFixpointIterator<>(graph, transfer, rule, domain, debug);
    }

    @Override
    public Environment<IntInterval> check() {
        debug.log("\t" + "Running the analysis");
        var environment = fixpointIterator.iterateUntilFixpoint();
        debug.log("\t" + "Analysis result: " + environment);
        return environment;
    }

}
