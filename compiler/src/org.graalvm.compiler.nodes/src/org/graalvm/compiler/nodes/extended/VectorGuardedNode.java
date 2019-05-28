package org.graalvm.compiler.nodes.extended;

import org.graalvm.compiler.nodes.VectorValueNodeInterface;

public interface VectorGuardedNode extends VectorValueNodeInterface {

    GuardingNode getGuard();

    void setGuard(GuardingNode guard);
}

