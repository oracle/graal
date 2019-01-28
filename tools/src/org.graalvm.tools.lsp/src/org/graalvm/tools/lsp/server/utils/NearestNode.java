package org.graalvm.tools.lsp.server.utils;

import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder.NodeLocationType;

import com.oracle.truffle.api.nodes.Node;

public class NearestNode {

    private final Node nearestNode;
    private final NodeLocationType locationType;

    public NearestNode(Node nearestNode, NodeLocationType locationType) {
        this.nearestNode = nearestNode;
        this.locationType = locationType;
    }

    public Node getNode() {
        return nearestNode;
    }

    public NodeLocationType getLocationType() {
        return locationType;
    }

}
