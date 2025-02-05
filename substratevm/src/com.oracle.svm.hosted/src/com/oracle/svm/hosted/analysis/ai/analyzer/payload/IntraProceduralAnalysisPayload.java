package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Represents the payload of an intra-procedural abstract interpretation analysis.
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is running on
 */
public class IntraProceduralAnalysisPayload<Domain extends AbstractDomain<Domain>>
        extends AnalysisPayload<Domain> {

    public IntraProceduralAnalysisPayload(
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