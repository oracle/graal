package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

import java.util.*;
import java.util.stream.Collectors;

/** Aggregates facts from all checkers and offers indexed queries by node and by kind. */
public final class FactAggregator {

    private final Map<Node, List<Fact>> byNode;
    private final Map<String, List<Fact>> byKind;
    private final List<Fact> all;

    private FactAggregator(List<Fact> facts) {
        this.all = List.copyOf(facts);
        Map<Node, List<Fact>> tmpByNode = new IdentityHashMap<>();
        Map<String, List<Fact>> tmpByKind = new HashMap<>();
        for (Fact f : facts) {
            tmpByNode.computeIfAbsent(f.node(), k -> new ArrayList<>()).add(f);
            tmpByKind.computeIfAbsent(f.kind(), k -> new ArrayList<>()).add(f);
        }
        this.byNode = tmpByNode.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.unmodifiableList(e.getValue()), (a,b)->a, IdentityHashMap::new));
        this.byKind = tmpByKind.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.unmodifiableList(e.getValue())));
    }

    public static FactAggregator aggregate(List<Fact> facts) {
        return new FactAggregator(facts == null ? List.of() : facts);
    }

    public List<Fact> allFacts() { return all; }

    public List<Fact> factsFor(Node n) {
        return byNode.getOrDefault(n, List.of());
    }

    public List<Fact> factsOfKind(String kind) {
        return byKind.getOrDefault(kind, List.of());
    }
}

