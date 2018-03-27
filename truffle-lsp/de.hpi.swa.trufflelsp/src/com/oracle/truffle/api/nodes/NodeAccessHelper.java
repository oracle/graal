package com.oracle.truffle.api.nodes;

public class NodeAccessHelper {

    public static boolean isTaggedWith(Node node, Class<?> clazz) {
        return node.isTaggedWith(clazz);
    }
}
