package com.oracle.truffle.tools.coverage;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.source.SourceSection;

public final class Coverage {

    private final Set<SourceSection> loadedStatements;
    private final Set<SourceSection> coveredStatements;
    private final Set<SourceSection> loadedRoots;
    private final Set<SourceSection> coveredRoots;

    Coverage() {
        loadedStatements = new HashSet<>();
        coveredStatements = new HashSet<>();
        loadedRoots = new HashSet<>();
        coveredRoots = new HashSet<>();
    }

    private Coverage(Set<SourceSection> loadedStatements, Set<SourceSection> coveredStatements, Set<SourceSection> loadedRoots, Set<SourceSection> coveredRoots) {
        this.loadedStatements = loadedStatements;
        this.coveredStatements = coveredStatements;
        this.loadedRoots = loadedRoots;
        this.coveredRoots = coveredRoots;
    }

    void addCoveredStatement(SourceSection statementSection) {
        coveredStatements.add(statementSection);
    }

    void addCoveredRoot(SourceSection rootSection) {
        coveredRoots.add(rootSection);
    }

    void addLoadedStatement(SourceSection statementSection) {
        loadedStatements.add(statementSection);
    }

    void addLoadedRoot(SourceSection rootSection) {
        loadedRoots.add(rootSection);
    }

    public Set<SourceSection> getLoadedStatements() {
        return loadedStatements;
    }

    public Set<SourceSection> getCoveredStatements() {
        return coveredStatements;
    }

    public Set<SourceSection> getLoadedRoots() {
        return loadedRoots;
    }

    public Set<SourceSection> getCoveredRoots() {
        return coveredRoots;
    }

    Coverage copy() {
        return new Coverage(
                        Collections.unmodifiableSet(this.loadedStatements),
                        Collections.unmodifiableSet(this.coveredStatements),
                        Collections.unmodifiableSet(this.loadedRoots),
                        Collections.unmodifiableSet(this.coveredRoots));
    }
}
