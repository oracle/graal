package com.oracle.svm.hosted.analysis.ai.example.leaks.set.inter;

import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.nodes.Invoke;

public class LeaksIdSetSummary implements Summary<SetDomain<ResourceId>> {

    private final Invoke invoke;
    private final SetDomain<ResourceId> preCondition;
    private SetDomain<ResourceId> postCondition = new SetDomain<>();

    public LeaksIdSetSummary(Invoke invoke, SetDomain<ResourceId> preCondition) {
        this.invoke = invoke;
        this.preCondition = preCondition;
    }

    @Override
    public Invoke getInvoke() {
        return invoke;
    }

    @Override
    public SetDomain<ResourceId> getPreCondition() {
        return preCondition;
    }

    @Override
    public SetDomain<ResourceId> getPostCondition() {
        return postCondition;
    }

    @Override
    public boolean subsumesSummary(Summary<SetDomain<ResourceId>> other) {
        if (!(other instanceof LeaksIdSetSummary)) {
            return false;
        }

        if (!(invoke.getTargetMethod().equals(other.getInvoke().getTargetMethod()))) {
            return false;
        }

        return preCondition.leq(other.getPreCondition());
    }

    @Override
    public void finalizeSummary(SetDomain<ResourceId> calleePostCondition) {
        for (ResourceId id : calleePostCondition.getSet()) {
            if (id.toString().startsWith("return#")) {
                ResourceId newResourceId = id.getWithRemovedReturnPrefix();
                postCondition.add(newResourceId);
            } else {
                postCondition.add(id);
            }
        }
    }

    @Override
    public SetDomain<ResourceId> applySummary(SetDomain<ResourceId> domain) {
        SetDomain<ResourceId> result = new SetDomain<>();
        result.joinWith(domain);

        /* We need to remove prefix "param" from the resource id */
        for (ResourceId id : postCondition.getSet()) {
            ResourceId newResourceId = id.getWithRemovedParamPrefix();
            result.add(newResourceId);
        }

        /* Remove resources that were in the summary pre-condition but not in the summary post-condition */
        for (ResourceId id : preCondition.getSet()) {
            ResourceId newResourceId = id.getWithRemovedParamPrefix();
            if (!postCondition.getSet().contains(id)) {
                result.remove(newResourceId);
            }
        }

        return result;
    }
}
