package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;

import java.util.HashMap;
import java.util.Map;

public final class EnvironmentDomain<Domain extends AbstractDomain<Domain>>
        extends MapDomain<AccessPath, Domain, EnvironmentDomain<Domain>> {

    private final Map<AccessPath, AccessPath> aliasMap;

    public EnvironmentDomain(Domain initialDomain) {
        super(initialDomain);
        this.aliasMap = new HashMap<>();
    }

    public EnvironmentDomain(EnvironmentDomain<Domain> other) {
        super(other);
        this.aliasMap = new HashMap<>(other.aliasMap);
    }

    public void createAlias(AccessPath original, AccessPath alias) {
        aliasMap.put(alias, original);
    }

    public AccessPath resolveAlias(AccessPath alias) {
        return aliasMap.getOrDefault(alias, alias);
    }

    @Override
    public Domain get(AccessPath key) {
        AccessPath resolvedKey = resolveAlias(key);
        return super.get(resolvedKey);
    }

    @Override
    public void put(AccessPath key, Domain value) {
        AccessPath resolvedKey = resolveAlias(key);
        super.put(resolvedKey, value);
    }

    @Override
    public void remove(AccessPath key) {
        AccessPath resolvedKey = resolveAlias(key);
        super.remove(resolvedKey);
    }

    @Override
    public EnvironmentDomain<Domain> copyOf() {
        return new EnvironmentDomain<>(this);
    }

    @Override
    public String toString() {
        return getValue().toString();
    }
}