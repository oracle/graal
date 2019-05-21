package org.graalvm.compiler.nodes;

import org.graalvm.compiler.graph.NodeInterface;

public interface VectorValueNodeInterface extends NodeInterface {
    @Override
    VectorValueNode asNode();
}
