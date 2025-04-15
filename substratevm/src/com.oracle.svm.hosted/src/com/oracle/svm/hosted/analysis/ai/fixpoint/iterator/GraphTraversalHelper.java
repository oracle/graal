package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorDirection;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GraphTraversalHelper {

    private final ControlFlowGraph cfgGraph;
    private final IteratorDirection direction;

    public GraphTraversalHelper(ControlFlowGraph cfgGraph, IteratorDirection direction) {
        this.cfgGraph = cfgGraph;
        this.direction = direction;
    }

    public HIRBlock getEntryBlock() {
        return direction == IteratorDirection.FORWARD
                ? cfgGraph.getStartBlock()
                : cfgGraph.getBlocks()[cfgGraph.getBlocks().length - 1];
    }

    public HIRBlock getSuccessorAt(HIRBlock block, int idx) {
        return direction == IteratorDirection.FORWARD
                ? block.getSuccessorAt(idx)
                : block.getPredecessorAt(idx);
    }

    public int getSuccessorCount(HIRBlock block) {
        return direction == IteratorDirection.FORWARD
                ? block.getSuccessorCount()
                : block.getPredecessorCount();
    }

    public HIRBlock getPredecessorAt(HIRBlock block, int idx) {
        return direction == IteratorDirection.FORWARD
                ? block.getPredecessorAt(idx)
                : block.getSuccessorAt(idx);
    }

    public int getPredecessorCount(HIRBlock block) {
        return direction == IteratorDirection.FORWARD
                ? block.getPredecessorCount()
                : block.getSuccessorCount();
    }

    public Iterable<Node> getNodes() {
        if (direction == IteratorDirection.FORWARD) {
            return cfgGraph.graph.getNodes();
        }

        List<Node> nodeList = new ArrayList<>();
        cfgGraph.graph.getNodes().forEach(nodeList::add);
        Collections.reverse(nodeList);
        return nodeList;
    }

    public Node getBeginNode(HIRBlock block) {
        return direction == IteratorDirection.FORWARD
                ? block.getBeginNode()
                : block.getEndNode();
    }

    public Node getEndNode(HIRBlock block) {
        return direction == IteratorDirection.FORWARD
                ? block.getEndNode()
                : block.getBeginNode();
    }

    public Node getGraphStart() {
        return direction == IteratorDirection.FORWARD
                ? cfgGraph.graph.start()
                : cfgGraph.getBlocks()[cfgGraph.getBlocks().length - 1].getEndNode();
    }

    /**
     * When traversing a basic block, we know that each node has only one predecessor in that block ( except the head node )
     */
    public Node getNodePredecessor(Node node) {
        return direction == IteratorDirection.FORWARD
                ? node.predecessor()
                : node.cfgSuccessors().iterator().next();
    }

    public Iterable<? extends Node> getNodeCfgSuccessors(Node current) {
        return direction == IteratorDirection.FORWARD
                ? current.cfgSuccessors()
                : current.cfgPredecessors();
    }
}
