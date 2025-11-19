package com.oracle.svm.hosted.analysis.ai.analyses.dataflow.inter;

import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;

/**
 * Summary for the interval-based data-flow analysis over {@link AbstractMemory}.
 * - Pre-condition: callee entry abstract state (as an AbstractMemory snapshot)
 * - Post-condition: abstract state joined at all return points (from callee AbstractState#getReturnDomain)
 */
public final class DataFlowIntervalAnalysisSummary implements Summary<AbstractMemory> {

    private final AbstractMemory pre;
    private AbstractMemory post;
    private boolean complete;

    public DataFlowIntervalAnalysisSummary(AbstractMemory pre) {
        this.pre = pre == null ? new AbstractMemory() : pre.copyOf();
        this.post = new AbstractMemory();
        this.post.setToTop();
    }

    @Override
    public AbstractMemory getPreCondition() {
        return pre;
    }

    @Override
    public AbstractMemory getPostCondition() {
        return post;
    }

    @Override
    public boolean subsumesSummary(Summary<AbstractMemory> other) {
        if (other == null) return false;
        // This summary subsumes "other" iff our pre-condition is >= other.pre (i.e., contains it).
        // We use lattice order: other.pre ⊑ this.pre  => this covers the other.
        return other.getPreCondition().leq(this.pre);
    }

    public boolean isComplete() {
        return complete;
    }

    public void setPostCondition(AbstractMemory postCondition) {
        this.post = postCondition;
        this.complete = true;
    }

    @Override
    public void finalizeSummary(AbstractState<AbstractMemory> calleeAbstractState) {
        if (calleeAbstractState == null) {
            this.post = new AbstractMemory();
            this.post.setToTop();
            this.complete = true;
            return;
        }
        AbstractMemory ret = calleeAbstractState.getReturnDomain();
        this.post = ret == null ? new AbstractMemory() : ret.copyOf();
        this.complete = true;
    }

    @Override
    public AbstractMemory applySummary(AbstractMemory domain) {
        if (domain == null) return post.copyOf();
        AbstractMemory out = domain.copyOf();
        out.joinWith(post);
        return out;
    }
}
