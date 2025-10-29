package com.oracle.svm.hosted.analysis.ai.fixpoint.wpo;

import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class WpoVertex {

    public enum Kind {
        Plain, Head, Exit
    }

    private final HIRBlock node;

    private final Kind kind;

    private final List<Integer> successors = new ArrayList<>();

    private final List<Integer> predecessors = new ArrayList<>();

    private final List<Integer> successorsLifted = new ArrayList<>();

    private int headOrExit;

    private int numPredecessors;

    private int numPredecessorsReducible;

    private final int postOrder;

    private final Map<Integer, Integer> irreducibles = new HashMap<>();

    private final int size;

    public WpoVertex(HIRBlock node, Kind kind, int size, int postOrder) {
        this.node = node;
        this.kind = kind;
        this.size = size;
        this.postOrder = postOrder;
        this.numPredecessors = 0;
        this.numPredecessorsReducible = 0;
    }

    public HIRBlock getNode() {
        return node;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isPlain() {
        return kind == Kind.Plain;
    }

    public boolean isHead() {
        return kind == Kind.Head;
    }

    public boolean isExit() {
        return kind == Kind.Exit;
    }

    public List<Integer> getSuccessors() {
        return successors;
    }

    public List<Integer> getPredecessors() {
        return predecessors;
    }

    public List<Integer> getSuccessorsLifted() {
        return successorsLifted;
    }

    public Map<Integer, Integer> getIrreducibles() {
        if (kind != Kind.Exit) {
            throw new UnsupportedOperationException("Trying to get irreducibles from non-exit");
        }
        return irreducibles;
    }

    public int getSize() {
        return size;
    }

    public int getNumPredecessors() {
        return numPredecessors;
    }

    public int getNumPredecessorsReducible() {
        return numPredecessorsReducible;
    }

    public int getPostOrder() {
        return postOrder;
    }

    public int getHead() {
        if (kind != Kind.Exit) {
            throw new UnsupportedOperationException("Trying to get head from non-exit");
        }
        return headOrExit;
    }

    public int getExit() {
        if (kind != Kind.Head) {
            throw new UnsupportedOperationException("Trying to get exit from non-head");
        }
        return headOrExit;
    }

    public void addSuccessor(int idx) {
        successors.add(idx);
    }

    public void addPredecessor(int idx) {
        predecessors.add(idx);
    }

    public boolean isSuccessor(int idx) {
        return successors.contains(idx);
    }

    public void addSuccessorLifted(int idx) {
        successorsLifted.add(idx);
    }

    public boolean isSuccessorLifted(int idx) {
        return successorsLifted.contains(idx);
    }

    public void incIrreducible(int idx) {
        if (kind != Kind.Exit) {
            throw new UnsupportedOperationException("Trying to access irreducibles from non-exit");
        }
        irreducibles.put(idx, irreducibles.getOrDefault(idx, 0) + 1);
    }

    public void incNumPredecessors() {
        numPredecessors++;
    }

    public void incNumPredecessorsReducible() {
        numPredecessorsReducible++;
    }

    public void setHeadExit(int idx) {
        if (kind == Kind.Plain) {
            throw new UnsupportedOperationException("Trying to access headOrExit from plain");
        }
    }

    @Override
    public String toString() {
        return "WpoVertex{" + "HIRBlock: " + node + "}";
    }
}
