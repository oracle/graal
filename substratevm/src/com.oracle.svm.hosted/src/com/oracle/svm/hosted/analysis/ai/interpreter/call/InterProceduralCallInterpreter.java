package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.summary.FixpointCache;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.InvokeNode;

public final class InterProceduralCallInterpreter<
        Domain extends AbstractDomain<Domain>>
        implements CallInterpreter<Domain> {

    @Override
    public void execInvoke(InvokeNode invokeNode,
                           AbstractStateMap<Domain> abstractStateMap,
                           FixpointCache<Domain> fixpointCache,
                           TransferFunction<Domain> transferFunction,
                           Domain initialDomain,
                           IteratorPolicy policy,
                           DebugContext debug) {

    }
}
