package com.oracle.svm.hosted.analysis.ai.example.leaks.set.inter;

import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.nodes.Invoke;

public class LeaksIdSetSummary implements Summary<SetDomain<ResourceId>> {

    private final Invoke invoke;
    private final SetDomain<ResourceId> preCondition;
    private final SetDomain<ResourceId> postCondition = new SetDomain<>();

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

        return ((LeaksIdSetSummary) other).invoke.equals(invoke);
    }

    @Override
    public void finalizeSummary(SetDomain<ResourceId> calleePostCondition) {
        for (ResourceId id : calleePostCondition.getSet()) {
            postCondition.add(id);
        }
    }

    @Override
    public SetDomain<ResourceId> applySummary(SetDomain<ResourceId> domain) {
        SetDomain<ResourceId> res = new SetDomain<>();
        for (ResourceId id : domain.getSet()) {
            ResourceId newId = id.removePrefix("param\\d+").removePrefix("return#");
            res.add(newId);
        }

        return res;
    }
}
