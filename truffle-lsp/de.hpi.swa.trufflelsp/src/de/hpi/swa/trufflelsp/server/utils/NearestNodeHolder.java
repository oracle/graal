package de.hpi.swa.trufflelsp.server.utils;

import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NodeLocationType;

public class NearestNodeHolder {

    private final Node nearestNode;
    private final NodeLocationType locationType;

    public NearestNodeHolder(Node nearestNode, NodeLocationType locationType) {
        this.nearestNode = nearestNode;
        this.locationType = locationType;
    }

    public Node getNearestNode() {
        return nearestNode;
    }

    public NodeLocationType getLocationType() {
        return locationType;
    }

}
