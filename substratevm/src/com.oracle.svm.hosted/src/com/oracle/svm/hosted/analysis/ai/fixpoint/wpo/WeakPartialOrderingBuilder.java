package com.oracle.svm.hosted.analysis.ai.fixpoint.wpo;

import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * WeakPartialOrderingBuilder constructs a weak partial ordering of a control flow graph
 * NOTE: Even though this is a Builder class, it does not have a build() method, as the WPO is constructed in the constructor,
 * by modifying the provided arguments.
 */
public final class WeakPartialOrderingBuilder {

    /* List of all WpoNodes */
    private final List<WpoNode> wpoNodes;

    /* For each HIRBlock, the set of back predecessors */
    private final Map<HIRBlock, Set<HIRBlock>> backPredecessors;

    /* Maps a HIRBlock to its depth-first number */
    private final Map<HIRBlock, Integer> nodeToDfn = new HashMap<>();

    /* Maps a HIRBlock to its post-depth-first number */
    private final Map<HIRBlock, Integer> nodeToPostDfn = new HashMap<>();

    /* Maps a depth-first number to the corresponding HIRBlock */
    private final List<HIRBlock> dfnToNode = new ArrayList<>();

    /* List of back predecessors' depth-first numbers */
    private final Map<Integer, List<Integer>> backPredecessorsDfn = new HashMap<>();

    /* List of non-back predecessors' depth-first numbers */
    private final Map<Integer, List<Integer>> nonBackPredecessorsDfn = new HashMap<>();

    /* Maps a depth-first number to the corresponding index in the wpoNodes list */
    private final Map<Integer, List<Edge>> crossForwardEdges = new HashMap<>();

    private int nextDfn = 1;
    private int nextPostDfn = 1;
    private int nextIdx = 0;
    private final List<Integer> dfnToIndex = new ArrayList<>();

    public WeakPartialOrderingBuilder(HIRBlock startBlock,
                                      List<WpoNode> wpoNodes,
                                      Map<HIRBlock, Set<HIRBlock>> backPredecessors) {
        this.wpoNodes = wpoNodes;
        this.backPredecessors = backPredecessors;
        constructAuxiliary(startBlock);
        constructWpo();
    }

    private static class Edge {
        int from;
        int to;

        Edge(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    /**
     * Perform iterative DFS to create data structures needed for WPO constructions
     *
     * @param startBlock the start block of the control flow graph
     */
    private void constructAuxiliary(HIRBlock startBlock) {
        Deque<Tuple> stack = new ArrayDeque<>();
        Map<Integer, Boolean> black = new HashMap<>();
        Map<Integer, Integer> ancestorMap = new HashMap<>();
        DisjointSets dsets = new DisjointSets();
        stack.push(new Tuple(startBlock, false, 0));

        while (!stack.isEmpty()) {
            Tuple tuple = stack.pop();
            HIRBlock node = tuple.node;
            boolean finished = tuple.finished;
            int predDfn = tuple.predDfn;

            if (finished) {
                /* DFS is done with this vertex -> set the postDFN */
                nodeToPostDfn.put(node, nextPostDfn++);

                int vertex = nodeToDfn.get(node);
                /* Mark visited. */
                black.put(vertex, true);

                dsets.unionSet(vertex, predDfn);
                ancestorMap.put(dsets.findSet(predDfn), predDfn);
            } else {
                if (nodeToDfn.containsKey(node)) {
                    /* Forward edges can be ignored, as they are redundant. */
                    continue;
                }
                /* New vertex is discovered. */
                int vertex = nextDfn++;
                dfnToNode.add(node);
                nodeToDfn.put(node, vertex);
                dsets.makeSet(vertex);
                ancestorMap.put(vertex, vertex);

                /* This will be popped after all its successors are finished. */
                stack.push(new Tuple(node, true, predDfn));

                for (int i = node.getSuccessorCount() - 1; i >= 0; i--) {
                    HIRBlock successor = node.getSuccessorAt(i);
                    int successorDfn = nodeToDfn.getOrDefault(successor, 0);
                    if (successorDfn == 0) {
                        stack.push(new Tuple(successor, false, vertex));
                    } else if (black.containsKey(successorDfn)) {
                        /* cross forward edge */
                        int lca = ancestorMap.get(dsets.findSet(successorDfn));
                        crossForwardEdges.computeIfAbsent(lca, k -> new ArrayList<>()).add(new Edge(vertex, successorDfn));
                    } else {
                        /* back edge */
                        backPredecessorsDfn.computeIfAbsent(successorDfn, k -> new ArrayList<>()).add(vertex);
                    }
                }

                if (predDfn != 0) {
                    /* tree edge */
                    nonBackPredecessorsDfn.computeIfAbsent(vertex, k -> new ArrayList<>()).add(predDfn);
                }
            }
        }
    }

    /**
     * Constructs the weak partial ordering.
     */
    private void constructWpo() {
        DisjointSets dsets = new DisjointSets();
        Map<Integer, Integer> rep = new HashMap<>();
        Map<Integer, Integer> exit = new HashMap<>();
        Map<Integer, List<Edge>> origin = new HashMap<>();
        Map<Integer, Integer> size = new HashMap<>();
        dfnToIndex.addAll(Collections.nCopies(2 * nextDfn, 0));
        int dfn = nextDfn;

        // Initialization
        for (int v = 1; v < nextDfn; v++) {
            dsets.makeSet(v);
            rep.put(v, v);
            exit.put(v, v);
            origin.put(v, new ArrayList<>());
            List<Integer> nonBackPreds = nonBackPredecessorsDfn.getOrDefault(v, Collections.emptyList());
            for (int u : nonBackPreds) {
                origin.get(v).add(new Edge(u, v));
            }
        }

        // In reverse DFS order, build WPOs for SCCs bottom-up
        for (int h = nextDfn - 1; h > 0; h--) {
            List<Edge> crossFwds = crossForwardEdges.getOrDefault(h, Collections.emptyList());
            for (Edge edge : crossFwds) {
                int u = edge.from;
                int v = edge.to;
                int repV = rep.get(dsets.findSet(v));
                nonBackPredecessorsDfn.computeIfAbsent(repV, k -> new ArrayList<>()).add(u);
                origin.get(repV).add(new Edge(u, v));
            }

            boolean isSCC = false;
            Set<Integer> backPredsH = new HashSet<>();
            for (int v : backPredecessorsDfn.getOrDefault(h, Collections.emptyList())) {
                if (v != h) {
                    backPredsH.add(rep.get(dsets.findSet(v)));
                } else {
                    isSCC = true;
                }
            }
            if (!backPredsH.isEmpty()) {
                isSCC = true;
            }

            Set<Integer> nestedSCCsH = new HashSet<>(backPredsH);
            Deque<Integer> worklistH = new ArrayDeque<>(backPredsH);
            while (!worklistH.isEmpty()) {
                int v = worklistH.pop();
                for (int p : nonBackPredecessorsDfn.getOrDefault(v, Collections.emptyList())) {
                    int repP = rep.get(dsets.findSet(p));
                    if (!nestedSCCsH.contains(repP) && repP != h) {
                        nestedSCCsH.add(repP);
                        worklistH.push(repP);
                    }
                }
            }

            if (!isSCC) {
                size.put(h, 1);
                addWpoNode(h, dfnToNode.get(h - 1), WpoNode.Kind.Plain, 1);
                continue;
            }

            int sizeH = 2;
            for (int v : nestedSCCsH) {
                sizeH += size.get(v);
            }
            size.put(h, sizeH);

            int xH = dfn++;
            addWpoNode(xH, dfnToNode.get(h - 1), WpoNode.Kind.Exit, sizeH);
            addWpoNode(h, dfnToNode.get(h - 1), WpoNode.Kind.Head, sizeH);

            if (backPredsH.isEmpty()) {
                addSuccessor(h, xH, xH, false);
            } else {
                for (int p : backPredsH) {
                    addSuccessor(exit.get(p), xH, xH, false);
                }
            }

            for (int v : nestedSCCsH) {
                for (Edge edge : origin.get(v)) {
                    int u = edge.from;
                    int vv = edge.to;
                    int xU = exit.get(rep.get(dsets.findSet(u)));
                    int xV = exit.get(v);
                    if (true) {
                        addSuccessor(xU, v, xV, xV != v);
                    } else {
                        addSuccessor(xU, vv, xV, xV != v);
                    }
                }
            }

            for (int v : nestedSCCsH) {
                dsets.unionSet(v, h);
                rep.put(dsets.findSet(v), h);
            }

            exit.put(h, xH);
        }

        List<Integer> topLevels = new ArrayList<>();
        for (int v = 1; v < nextDfn; v++) {
            if (rep.get(dsets.findSet(v)) == v) {
                topLevels.add(v);
                for (Edge edge : origin.get(v)) {
                    int u = edge.from;
                    int vv = edge.to;
                    int xU = exit.get(rep.get(dsets.findSet(u)));
                    int xV = exit.get(v);
                    if (true) {
                        addSuccessor(xU, v, xV, xV != v);
                    } else {
                        addSuccessor(xU, vv, xV, xV != v);
                    }
                }
            }
        }
    }

    private void processEdges(DisjointSets dsets, Map<Integer, Integer> rep, Map<Integer, Integer> exit, Map<Integer, List<Edge>> origin, int v) {
        for (Edge edge : origin.get(v)) {
            int u = edge.from;
            int vv = edge.to;
            int xU = exit.get(rep.get(dsets.findSet(u)));
            int xV = exit.get(v);
            addSuccessor(xU, vv, xV, v != vv);
        }
    }

    private void addWpoNode(int dfn, HIRBlock node, WpoNode.Kind kind, int size) {
        dfnToIndex.set(dfn, nextIdx++);
        wpoNodes.add(new WpoNode(node, kind, size, nodeToPostDfn.get(node)));
    }

    private WpoNode dfnToWpoNode(int dfn) {
        return wpoNodes.get(dfnToIndex.get(dfn));
    }

    private int dfnToIndex(int dfn) {
        return dfnToIndex.get(dfn);
    }

    private void setHeadExit(int h, int x) {
        int idx = dfnToIndex(h);
        dfnToWpoNode(x).setHeadExit(idx);
        dfnToWpoNode(h).setHeadExit(idx - 1);
    }

    private void addSuccessor(int fromDfn, int toDfn, int exitDfn, boolean irreducible) {
        WpoNode fromNode = dfnToWpoNode(fromDfn);
        WpoNode toNode = dfnToWpoNode(toDfn);
        int toIdx = dfnToIndex(toDfn);

        if (fromNode.isSuccessor(toIdx)) {
            return;
        }

        fromNode.addSuccessor(toIdx);
        toNode.incNumPredecessors();
        toNode.addPredecessor(dfnToIndex(fromDfn));

        if (irreducible) {
            dfnToWpoNode(exitDfn).incIrreducible(toIdx);
        } else {
            toNode.incNumPredecessorsReducible();
        }
    }

    private static class Tuple {
        HIRBlock node;
        boolean finished;
        int predDfn;

        Tuple(HIRBlock node, boolean finished, int predDfn) {
            this.node = node;
            this.finished = finished;
            this.predDfn = predDfn;
        }
    }

    private static class DisjointSets {
        private final Map<Integer, Integer> rank = new HashMap<>();
        private final Map<Integer, Integer> parent = new HashMap<>();

        public void makeSet(int x) {
            parent.put(x, x);
            rank.put(x, 0);
        }

        private int findSet(int x) {
            /* This works for our use case, but it's not a proper disjoint set implementation */
            if (!parent.containsKey(x)) {
                makeSet(x);
            }

            if (parent.get(x) != x) {
                parent.put(x, findSet(parent.get(x)));
            }
            return parent.get(x);
        }

        public void unionSet(int x, int y) {
            System.out.println("Union set: " + x + " " + y);
            System.out.println("Parent: " + parent);
            int rootX = findSet(x);
            int rootY = findSet(y);

            if (rootX != rootY) {
                if (rank.get(rootX) > rank.get(rootY)) {
                    parent.put(rootY, rootX);
                } else if (rank.get(rootX) < rank.get(rootY)) {
                    parent.put(rootX, rootY);
                } else {
                    parent.put(rootY, rootX);
                    rank.put(rootX, rank.get(rootX) + 1);
                }
            }
        }
    }
}