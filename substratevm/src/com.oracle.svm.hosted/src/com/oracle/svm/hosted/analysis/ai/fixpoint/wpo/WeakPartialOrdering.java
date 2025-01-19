package com.oracle.svm.hosted.analysis.ai.fixpoint.wpo;

import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a weak partial ordering of a control flow graph.
 */
public final class WeakPartialOrdering {

    /* List of all WpoNodes */
    private final List<WpoNode> wpoNodes = new ArrayList<>();

    /* For each HIRBlock, the set of back predecessors */
    private final Map<HIRBlock, Set<HIRBlock>> backPredecessors = new HashMap<>();

    /**
     * Constructs a weak partial ordering for the given control flow graph.
     *
     * @param cfg the control flow graph
     */
    public WeakPartialOrdering(ControlFlowGraph cfg) {
        HIRBlock root = cfg.getStartBlock();

        /* Handle base case -> there is only one block in the graph */
        if (cfg.getStartBlock().getSuccessorCount() == 0) {
            wpoNodes.add(new WpoNode(root, WpoNode.Kind.Plain, 1, 1));
            return;
        }

        new WeakPartialOrderingConstructor(cfg.getStartBlock(), wpoNodes, backPredecessors);
    }


    public int size() {
        return wpoNodes.size();
    }


    public int getEntry() {
        return wpoNodes.size() - 1;
    }


    public List<Integer> getSuccessors(int idx) {
        return wpoNodes.get(idx).getSuccessors();
    }


    public List<Integer> getPredecessors(int idx) {
        return wpoNodes.get(idx).getPredecessors();
    }


    public int getNumPredecessors(int idx) {
        return wpoNodes.get(idx).getNumPredecessors();
    }


    public int getNumPredecessorsReducible(int idx) {
        return wpoNodes.get(idx).getNumPredecessorsReducible();
    }


    public int getPostOrder(int idx) {
        return wpoNodes.get(idx).getPostOrder();
    }


    public Map<Integer, Integer> getIrreducibles(int exit) {
        return wpoNodes.get(exit).getIrreducibles();
    }


    public int getHeadOfExit(int exit) {
        return exit + 1;
    }


    public int getExitOfHead(int head) {
        return head - 1;
    }


    public HIRBlock getNode(int idx) {
        return wpoNodes.get(idx).getNode();
    }


    public WpoNode.Kind getKind(int idx) {
        return wpoNodes.get(idx).getKind();
    }


    public boolean isPlain(int idx) {
        return wpoNodes.get(idx).isPlain();
    }


    public boolean isHead(int idx) {
        return wpoNodes.get(idx).isHead();
    }


    public boolean isExit(int idx) {
        return wpoNodes.get(idx).isExit();
    }

    /**
     * Checks if an edge between two nodes is a back edge.
     *
     * @param head the head node
     * @param pred the predecessor node
     * @return true if the edge is a back edge, false otherwise
     */
    public boolean isBackEdge(HIRBlock head, HIRBlock pred) {
        Set<HIRBlock> preds = backPredecessors.get(head);
        return preds != null && preds.contains(pred);
    }
}