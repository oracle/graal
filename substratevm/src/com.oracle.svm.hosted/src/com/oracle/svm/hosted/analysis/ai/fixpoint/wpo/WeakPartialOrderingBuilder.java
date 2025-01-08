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
    private final List<List<Integer>> backPredecessorsDfn = new ArrayList<>();

    /* List of non-back predecessors' depth-first numbers */
    private final List<List<Integer>> nonBackPredecessorsDfn = new ArrayList<>();

    /* Maps a depth-first number to the corresponding index in the wpoNodes list */
    private final Map<Integer, List<Edge>> crossForwardEdges = new HashMap<>();

    private int nextDfn = 1;
    private int nextPostDfn = 1;
    private int nextIdx = 0;
    private final List<Integer> dfnToIndex = new ArrayList<>();

    public WeakPartialOrderingBuilder(HIRBlock startBlock, List<WpoNode> wpoNodes, Map<HIRBlock, Set<HIRBlock>> backPredecessors) {
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
     * Constructs auxiliary data structures for the WPO algorithm.
     *
     * @param startBlock the start block of the control flow graph
     */
    private void constructAuxiliary(HIRBlock startBlock) {
        Deque<Tuple> stack = new ArrayDeque<>();
        List<Boolean> black = new ArrayList<>();
        List<Integer> ancestor = new ArrayList<>();
        Map<Integer, Integer> rankMap = new HashMap<>();
        Map<Integer, Integer> parentMap = new HashMap<>();
        DisjointSets dsets = new DisjointSets(rankMap, parentMap);
        stack.push(new Tuple(startBlock, false, 0));

        while (!stack.isEmpty()) {
            Tuple tuple = stack.pop();
            HIRBlock node = tuple.node;
            boolean finished = tuple.finished;
            int predDfn = tuple.predDfn;

            if (finished) {
                nodeToPostDfn.put(node, nextPostDfn++);
                int dfn = nodeToDfn.get(node);
                black.set(dfn, true);
                dsets.unionSet(dfn, predDfn);
                ancestor.set(dsets.findSet(predDfn), predDfn);
            } else if (!nodeToDfn.containsKey(node)) {
                int dfn = nextDfn++;
                dfnToNode.add(node);
                nodeToDfn.put(node, dfn);
                black.add(false);
                backPredecessorsDfn.add(new ArrayList<>());
                nonBackPredecessorsDfn.add(new ArrayList<>());
                dsets.makeSet(dfn);
                ancestor.add(dfn);
                stack.push(new Tuple(node, true, predDfn));

                for (int i = node.getSuccessorCount() - 1; i >= 0; i--) {
                    HIRBlock successor = node.getSuccessorAt(i);
                    int successorDfn = nodeToDfn.getOrDefault(successor, 0);
                    if (successorDfn == 0) {
                        stack.push(new Tuple(successor, false, dfn));
                    } else if (black.get(successorDfn)) {
                        int lca = ancestor.get(dsets.findSet(successorDfn));
                        crossForwardEdges.computeIfAbsent(lca, k -> new ArrayList<>()).add(new Edge(dfn, successorDfn));
                    } else {
                        backPredecessorsDfn.get(successorDfn).add(dfn);
                        backPredecessors.computeIfAbsent(successor, k -> new HashSet<>()).add(node);
                    }
                }

                if (predDfn != 0) {
                    nonBackPredecessorsDfn.get(dfn).add(predDfn);
                }
            }
        }
    }

    /**
     * Constructs the weak partial ordering.
     */
    private void constructWpo() {
        Map<Integer, Integer> rank = new HashMap<>();
        Map<Integer, Integer> parent = new HashMap<>();
        DisjointSets dsets = new DisjointSets(rank, parent);
        Map<Integer, Integer> rep = new HashMap<>();
        Map<Integer, Integer> exit = new HashMap<>();
        Map<Integer, Integer> size = new HashMap<>();
        Map<Integer, List<Edge>> origin = new HashMap<>();
        dfnToIndex.addAll(Collections.nCopies(2 * nextDfn, 0));

        for (int v = 1; v < nextDfn; v++) {
            dsets.makeSet(v);
            rep.put(v, v);
            exit.put(v, v);
            origin.put(v, new ArrayList<>());
            for (int u : nonBackPredecessorsDfn.get(v)) {
                origin.get(v).add(new Edge(u, v));
            }
        }

        for (int h = nextDfn - 1; h > 0; h--) {
            List<Edge> edges = crossForwardEdges.get(h);
            if (edges != null) {
                for (Edge edge : edges) {
                    int repTo = rep.get(dsets.findSet(edge.to));
                    nonBackPredecessorsDfn.get(repTo).add(edge.from);
                    origin.get(repTo).add(edge);
                }
            }

            boolean isScc = false;
            Set<Integer> exitsH = new HashSet<>();
            for (int v : backPredecessorsDfn.get(h)) {
                if (v != h) {
                    exitsH.add(rep.get(dsets.findSet(v)));
                } else {
                    isScc = true;
                }
            }
            if (!exitsH.isEmpty()) {
                isScc = true;
            }

            Set<Integer> componentsH = new HashSet<>(exitsH);
            List<Integer> worklistH = new ArrayList<>(exitsH);

            while (!worklistH.isEmpty()) {
                int v = worklistH.removeLast();
                for (int u : nonBackPredecessorsDfn.get(v)) {
                    int repU = rep.get(dsets.findSet(u));
                    if (!componentsH.contains(repU) && repU != h) {
                        componentsH.add(repU);
                        worklistH.add(repU);
                    }
                }
            }

            if (!isScc) {
                size.put(h, 1);
                addWpoNode(h, dfnToNode.get(h), WpoNode.Kind.Plain, 1);
                continue;
            }

            int sizeH = 2;
            for (int v : componentsH) {
                sizeH += size.get(v);
            }
            size.put(h, sizeH);

            int x = nextDfn++;
            addWpoNode(x, dfnToNode.get(h), WpoNode.Kind.Exit, sizeH);
            addWpoNode(h, dfnToNode.get(h), WpoNode.Kind.Head, sizeH);
            setHeadExit(h, x);

            if (exitsH.isEmpty()) {
                addSuccessor(h, x, x, false);
            } else {
                for (int xx : exitsH) {
                    addSuccessor(exit.get(xx), x, x, false);
                }
            }

            for (int v : componentsH) {
                processEdges(dsets, rep, exit, origin, v);
            }

            for (int v : componentsH) {
                dsets.unionSet(v, h);
                rep.put(dsets.findSet(v), h);
            }

            exit.put(h, x);
        }

        List<Integer> topLevels = new ArrayList<>();
        for (int v = 1; v < nextDfn; v++) {
            if (rep.get(dsets.findSet(v)) == v) {
                topLevels.add(v);
                processEdges(dsets, rep, exit, origin, v);
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

    private record DisjointSets(Map<Integer, Integer> rank, Map<Integer, Integer> parent) {
        void makeSet(int x) {
            parent.put(x, x);
            rank.put(x, 0);
        }

        int findSet(int x) {
            if (parent.get(x) != x) {
                parent.put(x, findSet(parent.get(x)));
            }
            return parent.get(x);
        }

        void unionSet(int x, int y) {
            int rootX = findSet(x);
            int rootY = findSet(y);
            if (rootX != rootY) {
                if (rank.get(rootX) > rank.get(rootY)) {
                    parent.put(rootY, rootX);
                } else {
                    parent.put(rootX, rootY);
                    if (rank.get(rootX).equals(rank.get(rootY))) {
                        rank.put(rootY, rank.get(rootY) + 1);
                    }
                }
            }
        }
    }
}