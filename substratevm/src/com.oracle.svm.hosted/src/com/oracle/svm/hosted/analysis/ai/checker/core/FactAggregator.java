package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import jdk.graal.compiler.graph.Node;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates facts from all checkers and offers indexed queries by node and by kind.
 */
public final class FactAggregator {

    private final List<Fact> all;
    private Map<Node, List<Fact>> byNode;
    private Map<FactKind, List<Fact>> byKind;
    private final Map<Class<? extends Fact>, List<Fact>> byType = new HashMap<>();

    public FactAggregator() {
        this.all = new ArrayList<>();
        this.byNode = new IdentityHashMap<>();
        this.byKind = new HashMap<>();
    }

    FactAggregator(List<Fact> facts) {
        this.all = new ArrayList<>(facts == null ? List.of() : facts);
        rebuildIndexes();
    }

    private void rebuildIndexes() {
        Map<Node, List<Fact>> tmpByNode = new IdentityHashMap<>();
        Map<FactKind, List<Fact>> tmpByKind = new HashMap<>();
        for (Fact f : all) {
            tmpByNode.computeIfAbsent(f.node(), k -> new ArrayList<>()).add(f);
            tmpByKind.computeIfAbsent(f.kind(), k -> new ArrayList<>()).add(f);
        }
        this.byNode = tmpByNode.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.unmodifiableList(e.getValue()), (a, b) -> a, IdentityHashMap::new));
        this.byKind = tmpByKind.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.unmodifiableList(e.getValue())));
    }

    public static FactAggregator aggregate(List<Fact> facts) {
        return new FactAggregator(facts);
    }

    public List<Fact> allFacts() {
        return Collections.unmodifiableList(all);
    }

    public List<Fact> factsFor(Node n) {
        return byNode.getOrDefault(n, List.of());
    }

    public List<Fact> factsOfKind(FactKind kind) {
        return byKind.getOrDefault(kind, List.of());
    }

    public void addAll(List<Fact> facts) {
        if (facts == null || facts.isEmpty()) {
            return;
        }
        this.all.addAll(facts);
        rebuildIndexes();
    }

    public boolean isEmpty() {
        return all.isEmpty();
    }
}
