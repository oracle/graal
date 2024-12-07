package com.oracle.svm.hosted.analysis.ai.fixpoint.wto;


import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Represents a weak topological ordering of a directed graph
 */
public final class WeakTopologicalOrdering {

    /* List of all WtoComponents */
    private final List<WtoComponent> components = new ArrayList<>();

    /* Map of HIRBlock to its depth-first number */
    private final Map<HIRBlock, Integer> dfnTable = new HashMap<>();

    /* Stack of HIRBlocks */
    private final Deque<HIRBlock> stack = new ArrayDeque<>();

    /* depth-first number */
    private int dfn = 0;

    public WeakTopologicalOrdering(ControlFlowGraph cfg) {
        visit(cfg.getStartBlock(), components);
    }

    private int visit(HIRBlock vertex, List<WtoComponent> partition) {
        int head;
        int min;
        boolean loop;

        stack.push(vertex);
        dfn++;
        head = dfn;
        dfnTable.put(vertex, head);
        loop = false;

        int successorCount = vertex.getSuccessorCount();
        for (int i = 0; i < successorCount; i++) {
            HIRBlock successor = vertex.getSuccessorAt(i);
            int successorDfn = dfnTable.getOrDefault(successor, 0);
            if (successorDfn == 0) {
                min = visit(successor, partition);
            } else {
                min = successorDfn;
            }
            if (min <= head) {
                head = min;
                loop = true;
            }
        }

        if (head == dfnTable.get(vertex)) {
            dfnTable.put(vertex, Integer.MAX_VALUE);
            HIRBlock element = stack.pop();
            if (loop) {
                List<WtoComponent> cycleComponents = new ArrayList<>();
                while (element != vertex) {
                    dfnTable.put(element, 0);
                    element = stack.pop();
                }
                partition.add(new WtoCycle(vertex, cycleComponents));
            } else {
                partition.add(new WtoVertex(vertex));
            }
        }
        return head;
    }

    public List<WtoComponent> getComponents() {
        return components;
    }
}