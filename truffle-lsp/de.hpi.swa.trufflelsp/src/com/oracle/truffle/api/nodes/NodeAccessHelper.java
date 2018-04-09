package com.oracle.truffle.api.nodes;

public class NodeAccessHelper {

    public static void insertChild(Node child, Node parent) {
        parent.insert(child);
    }
}
