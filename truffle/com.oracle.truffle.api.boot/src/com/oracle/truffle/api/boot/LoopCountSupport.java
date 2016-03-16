package com.oracle.truffle.api.boot;

/**
 *
 */
public abstract class LoopCountSupport<Node> {
    /** Constructor for subclasses.
     *
     * @param nodeClazz reference to {@link com.oracle.api.nodes.Node}
     */
    protected LoopCountSupport(Class<Node> nodeClazz) {
    }

    public abstract void onLoopCount(Node sourceNode, int count);
}
