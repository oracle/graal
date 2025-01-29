package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.value.MapValue;

import java.util.HashMap;
import java.util.Map;

/**
 * An abstract domain for tracking properties of access paths by leveraging the existing MapDomain.
 * This implementation maps AccessPath objects to some abstract domain Value, using MapDomain
 * to handle the core lattice operations. We should also be able to create and resolve aliases
 *
 * @param <Domain> type of the derived {@link AbstractDomain} we are mapping access paths to
 */
public final class AccessPathMapDomain<Domain extends AbstractDomain<Domain>>
        extends MapDomain<AccessPath, Domain, AccessPathMapDomain<Domain>> {

    private final Map<AccessPath, AccessPath> aliasMap;

    public AccessPathMapDomain(Domain initialDomain) {
        super(initialDomain);
        this.aliasMap = new HashMap<>();
    }

    public AccessPathMapDomain(AccessPathMapDomain<Domain> other) {
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
    public AccessPathMapDomain<Domain> copyOf() {
        return new AccessPathMapDomain<>(this);
    }

    @Override
    public String toString() {
        return "AccessPathMapDomain{" + getValue().toString() + "}";
    }
}