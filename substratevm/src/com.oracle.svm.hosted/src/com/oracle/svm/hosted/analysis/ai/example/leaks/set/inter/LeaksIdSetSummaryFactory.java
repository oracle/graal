package com.oracle.svm.hosted.analysis.ai.example.leaks.set.inter;

import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.util.BigBangUtil;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.List;

public class LeaksIdSetSummaryFactory implements SummaryFactory<SetDomain<ResourceId>> {

    @Override
    public Summary<SetDomain<ResourceId>> createSummary(Invoke invoke,
                                                        SetDomain<ResourceId> callerPreCondition,
                                                        List<SetDomain<ResourceId>> arguments) {

        SetDomain<ResourceId> summaryPre = new SetDomain<>();
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();

        /* Prefix all resource args with "param" + the idx of the argument */
        for (int i = 0; i < args.size(); i++) {
            ValueNode arg = args.get(i);
            if (isResource(arg)) {
                ResourceId paramId = new ResourceId("param" + i + arguments.get(i).getSet().iterator().next());
                summaryPre.add(paramId);
            }
        }

        return new LeaksIdSetSummary(invoke, summaryPre);
    }

    private boolean isResource(ValueNode arg) {
        if (!arg.getStackKind().isObject()) {
            return false;
        }

        AllocatedObjectNode allocatedObjectNode = (AllocatedObjectNode) arg;
        ResolvedJavaType objectType = allocatedObjectNode.getVirtualObject().type();
        ResolvedJavaType autoCloseableType = BigBangUtil.getInstance().lookUpType(AutoCloseable.class);
        return autoCloseableType.isAssignableFrom(objectType);
    }
}
