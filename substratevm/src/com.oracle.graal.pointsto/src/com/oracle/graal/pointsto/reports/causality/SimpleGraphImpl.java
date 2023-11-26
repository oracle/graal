package com.oracle.graal.pointsto.reports.causality;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvents;

public class SimpleGraphImpl extends BasicImpl<BasicImpl.ThreadContext> {
    public SimpleGraphImpl() {
        super(BasicImpl.ThreadContext::new);
    }

    @Override
    public void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
        registerEdge(
                CausalityEvents.TypeInstantiated.create(concreteTargetType),
                CausalityEvents.MethodImplementationInvoked.create(concreteTargetMethod)
        );
    }
}
