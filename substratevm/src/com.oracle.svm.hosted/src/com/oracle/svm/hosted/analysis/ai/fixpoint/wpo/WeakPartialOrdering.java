package com.oracle.svm.hosted.analysis.ai.fixpoint.wpo;

import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.GraphTraversalHelper;
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
    private final List<WpoVertex> wpoVertices = new ArrayList<>();

    /* For each HIRBlock, the set of back predecessors */
    private final Map<HIRBlock, Set<HIRBlock>> backPredecessors = new HashMap<>();

    /**
     * Constructs a weak partial ordering for the given control flow graph.
     *
     * @param graphTraversalHelper the graph traversal helper
     */
    public WeakPartialOrdering(GraphTraversalHelper graphTraversalHelper) {
        HIRBlock root = graphTraversalHelper.getEntryBlock();

        /* Handle base case -> there is only one block in the graph */
        if (graphTraversalHelper.getSuccessorCount(root) == 0) {
            wpoVertices.add(new WpoVertex(root, WpoVertex.Kind.Plain, 1, 1));
            return;
        }

        new WeakPartialOrderingConstructor(root, wpoVertices, backPredecessors, graphTraversalHelper);
    }

    public int size() {
        return wpoVertices.size();
    }

    public int getEntry() {
        return wpoVertices.size() - 1;
    }

    public List<Integer> getSuccessors(int idx) {
        return wpoVertices.get(idx).getSuccessors();
    }

    public List<Integer> getPredecessors(int idx) {
        return wpoVertices.get(idx).getPredecessors();
    }

    public int getNumPredecessors(int idx) {
        return wpoVertices.get(idx).getNumPredecessors();
    }


    public int getNumPredecessorsReducible(int idx) {
        return wpoVertices.get(idx).getNumPredecessorsReducible();
    }

    public int getPostOrder(int idx) {
        return wpoVertices.get(idx).getPostOrder();
    }

    public Map<Integer, Integer> getIrreducibles(int exit) {
        return wpoVertices.get(exit).getIrreducibles();
    }

    public int getHeadOfExit(int exit) {
        return exit + 1;
    }

    public int getExitOfHead(int head) {
        return head - 1;
    }

    public HIRBlock getNode(int idx) {
        return wpoVertices.get(idx).getNode();
    }

    public WpoVertex.Kind getKind(int idx) {
        return wpoVertices.get(idx).getKind();
    }

    public boolean isPlain(int idx) {
        return wpoVertices.get(idx).isPlain();
    }

    public boolean isHead(int idx) {
        return wpoVertices.get(idx).isHead();
    }

    public boolean isExit(int idx) {
        return wpoVertices.get(idx).isExit();
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
