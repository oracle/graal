package com.oracle.svm.hosted.analysis.ai.fixpoint.wto;

import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.util.*;

public final class WeakTopologicalOrdering {
    private final List<WtoComponent> components;
    private final Map<HIRBlock, Integer> dfnTable;
    private final Stack<HIRBlock> stack;
    private int num;

    public WeakTopologicalOrdering(ControlFlowGraph cfg) {
        this.components = new ArrayList<>();
        this.dfnTable = new HashMap<>();
        this.stack = new Stack<>();
        this.num = 0;
        build(cfg.getStartBlock());
        Collections.reverse(components);
    }

    private void build(HIRBlock root) {
        visit(root);
    }

    private int visit(HIRBlock vertex) {
        stack.push(vertex);
        int headDfn = setDfn(vertex, ++num);
        boolean isLoop = false;

        int successorCount = vertex.getSuccessorCount();
        for (int i = 0; i < successorCount; i++) {
            var successor = vertex.getSuccessorAt(i);
            int successorDfn = getDfn(successor);
            int min;
            if (successorDfn == 0) {
                min = visit(successor);
            } else {
                min = successorDfn;
            }
            if (min <= headDfn) {
                headDfn = min;
                isLoop = true;
            }
        }

        if (headDfn == getDfn(vertex)) {
            setDfn(vertex, Integer.MAX_VALUE);
            HIRBlock element = stack.pop();
            if (isLoop) {
                List<WtoComponent> cycleComponents = new ArrayList<>();
                while (!element.equals(vertex)) {
                    setDfn(element, 0);
                    cycleComponents.add(new WtoVertex(element));
                    element = stack.pop();
                }
                components.add(new WtoCycle(vertex, cycleComponents));
            } else {
                components.add(new WtoVertex(vertex));
            }
        }
        return headDfn;
    }

    private int getDfn(HIRBlock block) {
        return dfnTable.getOrDefault(block, 0);
    }

    private int setDfn(HIRBlock block, int number) {
        if (number == 0) {
            dfnTable.remove(block);
        } else {
            dfnTable.put(block, number);
        }
        return number;
    }

    public List<WtoComponent> getComponents() {
        return components;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (WtoComponent component : components) {
            sb.append(component.toString()).append(" ");
        }
        return sb.toString().trim();
    }
}