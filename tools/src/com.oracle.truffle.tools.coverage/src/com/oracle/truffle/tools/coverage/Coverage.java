package com.oracle.truffle.tools.coverage;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Coverage {

    private final Set<SourceSection> loadedStatements;
    private final Set<SourceSection> coveredStatements;
    private final Set<RootNode> loadedRootNodes;
    private final Set<RootNode> coveredRootNodes;

    Coverage() {
        loadedStatements = new HashSet<>();
        coveredStatements = new HashSet<>();
        loadedRootNodes = new HashSet<>();
        coveredRootNodes = new HashSet<>();
    }

    private Coverage(Set<SourceSection> loadedStatements, Set<SourceSection> coveredStatements, Set<RootNode> loadedRootNodes, Set<RootNode> coveredRootNodes) {
        this.loadedStatements = loadedStatements;
        this.coveredStatements = coveredStatements;
        this.loadedRootNodes = loadedRootNodes;
        this.coveredRootNodes = coveredRootNodes;
    }

    void addCovered(SourceSection sourceSection) {
        coveredStatements.add(sourceSection);
    }

    void addCovered(RootNode rootNode) {
        coveredRootNodes.add(rootNode);
    }

    void addLoaded(SourceSection sourceSection) {
        loadedStatements.add(sourceSection);
    }

    void addLoaded(RootNode rootNode) {
        loadedRootNodes.add(rootNode);
    }

    public Set<SourceSection> getLoadedStatements() {
        return loadedStatements;
    }

    public Set<SourceSection> getCoveredStatements() {
        return coveredStatements;
    }

    public Set<RootNode> getLoadedRootNodes() {
        return loadedRootNodes;
    }

    public Set<RootNode> getCoveredRootNodes() {
        return coveredRootNodes;
    }

    Coverage copy() {
        return new Coverage(
                        Collections.unmodifiableSet(this.loadedStatements),
                        Collections.unmodifiableSet(this.coveredStatements),
                        Collections.unmodifiableSet(this.loadedRootNodes),
                        Collections.unmodifiableSet(this.coveredRootNodes));
    }
}
