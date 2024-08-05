package com.oracle.graal.pointsto.reports.causality;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvent;
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvents;

public class RTAImpl extends BasicImpl<BasicImpl.ThreadContext> {
    public RTAImpl() {
        super(BasicImpl.ThreadContext::new);
    }

    @Override
    public void registerVirtualInvocation(PointsToAnalysis bb, AbstractVirtualInvokeTypeFlow invocation, AnalysisMethod concreteTargetMethod, AnalysisType concreteTargetType) {
        AnalysisMethod callingMethod = invocation.method();

        if (callingMethod == null && invocation.getTargetMethod().getContextInsensitiveVirtualInvoke(invocation.getCallerMultiMethodKey()) != invocation) {
            throw new RuntimeException("CausalityExport has made an invalid assumption!");
        }

        CausalityEvent callerEvent = callingMethod != null
                        /* TODO: Take inlining into account */
                        ? CausalityEvents.InlinedMethodCode.create(callingMethod)
                        : CausalityEvents.RootMethodRegistration.create(invocation.getTargetMethod());

        registerEdge(
                        callerEvent,
                        CausalityEvents.VirtualMethodInvoked.create(invocation.getTargetMethod()));
        registerConjunctiveEdge(
                        CausalityEvents.VirtualMethodInvoked.create(invocation.getTargetMethod()),
                        CausalityEvents.TypeInstantiated.create(concreteTargetType),
                        CausalityEvents.MethodImplementationInvoked.create(concreteTargetMethod));
    }
}
