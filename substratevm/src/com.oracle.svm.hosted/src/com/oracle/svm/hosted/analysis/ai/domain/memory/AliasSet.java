package com.oracle.svm.hosted.analysis.ai.domain.memory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A small utility to represent a may-alias set of access paths.
 */
public final class AliasSet {

    private final Set<AccessPath> paths;

    private AliasSet(Set<AccessPath> paths) {
        this.paths = Collections.unmodifiableSet(paths);
    }

    public static AliasSet of(AccessPath... ps) {
        Set<AccessPath> s = new HashSet<>();
        if (ps != null) {
            for (AccessPath p : ps) {
                if (p != null) s.add(p);
            }
        }
        return new AliasSet(s);
    }

    public static AliasSet ofSet(Set<AccessPath> set) {
        if (set == null || set.isEmpty()) return new AliasSet(Collections.emptySet());
        return new AliasSet(new HashSet<>(set));
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public boolean isSingleton() {
        return paths.size() == 1;
    }

    public Set<AccessPath> paths() {
        return paths;
    }

    public AliasSet union(AliasSet other) {
        if (other == null || other.paths.isEmpty()) return this;
        if (this.paths.isEmpty()) return other;
        Set<AccessPath> s = new HashSet<>(this.paths);
        s.addAll(other.paths);
        return new AliasSet(s);
    }

    public AliasSet intersect(AliasSet other) {
        if (other == null) return ofSet(Collections.emptySet());
        Set<AccessPath> s = new HashSet<>(this.paths);
        s.retainAll(other.paths);
        return new AliasSet(s);
    }

    public AliasSet map(Function<AccessPath, AccessPath> f) {
        Objects.requireNonNull(f);
        Set<AccessPath> s = new HashSet<>(paths.size());
        for (AccessPath p : paths) {
            AccessPath np = f.apply(p);
            if (np != null) s.add(np);
        }
        return new AliasSet(s);
    }

    public AliasSet mapField(String field) {
        return map(p -> p.appendField(field));
    }

    public AliasSet mapArrayWildcard() {
        return map(AccessPath::appendArrayWildcard);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AliasSet aliasSet)) return false;
        return paths.equals(aliasSet.paths);
    }

    @Override
    public int hashCode() {
        return paths.hashCode();
    }

    @Override
    public String toString() {
        return "AliasSet" + paths;
    }
}

