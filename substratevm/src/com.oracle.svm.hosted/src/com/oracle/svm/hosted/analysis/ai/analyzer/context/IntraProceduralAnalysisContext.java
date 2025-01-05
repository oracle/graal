package com.oracle.svm.hosted.analysis.ai.analyzer.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Represents the context of an intra-procedural abstract interpretation analysis.
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is running on
 */
public class IntraProceduralAnalysisContext<Domain extends AbstractDomain<Domain>>
        extends AnalysisContext<Domain> {

    public IntraProceduralAnalysisContext(
            Domain initialDomain,
            IteratorPolicy iteratorPolicy,
            AnalysisMethod root,
            DebugContext debugContext,
            NodeInterpreter<Domain> nodeInterpreter,
            CheckerManager checkerManager
    ) {
        super(initialDomain, iteratorPolicy, root, debugContext, nodeInterpreter, checkerManager);
    }
}