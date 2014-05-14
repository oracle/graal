package com.oracle.graal.phases.common.inlining.info;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.meta.MetaAccessProvider;
import com.oracle.graal.api.meta.MetaUtil;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.api.code.Assumptions.Assumption;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * Represents an inlining opportunity where the current class hierarchy leads to a monomorphic
 * target method, but for which an assumption has to be registered because of non-final classes.
 */
public class AssumptionInlineInfo extends ExactInlineInfo {

    private final Assumption takenAssumption;

    public AssumptionInlineInfo(Invoke invoke, ResolvedJavaMethod concrete, Assumption takenAssumption) {
        super(invoke, concrete);
        this.takenAssumption = takenAssumption;
    }

    @Override
    public void inline(Providers providers, Assumptions assumptions) {
        assumptions.record(takenAssumption);
        super.inline(providers, assumptions);
    }

    @Override
    public void tryToDevirtualizeInvoke(MetaAccessProvider metaAccess, Assumptions assumptions) {
        assumptions.record(takenAssumption);
        InliningUtil.replaceInvokeCallTarget(invoke, graph(), InvokeKind.Special, concrete);
    }

    @Override
    public String toString() {
        return "assumption " + MetaUtil.format("%H.%n(%p):%r", concrete);
    }
}
