package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.LatticeDomain;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;

/**
 * Abstract transfer function for fixpoint analysis.
 * It is used to define the transfer function for some nodes in the graph.
 *
 * @param <Value>  type of the derived abstract value
 * @param <Domain> type of the derived abstract domain
 */

public abstract class AbstractTransferFunction<
        Value extends AbstractValue<Value>,
        Domain extends LatticeDomain<Value, Domain>>
        implements TransferFunction {

    protected final Environment<Value, Domain> environment;

    public AbstractTransferFunction(Environment<Value, Domain> environment) {
        this.environment = environment;
    }

}