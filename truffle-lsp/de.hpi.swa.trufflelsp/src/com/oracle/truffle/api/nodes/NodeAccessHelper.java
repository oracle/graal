package com.oracle.truffle.api.nodes;

public class NodeAccessHelper {
    public static void insertNode(Node parent, Node child) {
        parent.insert(child);
    }
}
