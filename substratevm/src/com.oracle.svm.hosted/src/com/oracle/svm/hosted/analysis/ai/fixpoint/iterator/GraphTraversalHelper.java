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

    public Node getGraphStart() {
        return direction == IteratorDirection.FORWARD
                ? cfgGraph.graph.start()
                : cfgGraph.getBlocks()[cfgGraph.getBlocks().length - 1].getEndNode();
    }

    public Iterable<? extends Node> getCfgSuccessors(Node node) {
        return direction == IteratorDirection.FORWARD
                ? node.cfgSuccessors()
                : node.cfgPredecessors();
    }

    public Iterable<? extends Node> getCfgPredecessors(Node node) {
        return direction == IteratorDirection.FORWARD
                ? node.cfgPredecessors()
                : node.cfgSuccessors();
    }
}