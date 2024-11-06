package com.oracle.svm.hosted.analysis.ai.transfer;

import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;
import jdk.graal.compiler.graph.Node;

/**
 * Interface for transfer functions used in fixpoint analysis.
 */

public interface TransferFunction<
        Value extends AbstractValue<Value>,
        Domain extends AbstractDomain<Domain>> {
    Domain analyzeNode(Node node, Environment<Value, Domain> environment);
}