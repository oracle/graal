package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.PlaceHolderAccessPathBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EnvironmentDomain<Domain extends AbstractDomain<Domain>>
        extends MapDomain<AccessPath, Domain, EnvironmentDomain<Domain>> {

    private Domain exprValue; /* Some nodes do not add any access paths to the environment, but only produce value like constants */
    private final Map<AccessPath, AccessPath> aliasMap;

    public EnvironmentDomain(Domain initialDomain) {
        super(initialDomain);
        this.exprValue = initialDomain;
        this.aliasMap = new HashMap<>();
    }

    public EnvironmentDomain(EnvironmentDomain<Domain> other) {
        super(other);
        this.exprValue = other.exprValue;
        this.aliasMap = new HashMap<>(other.aliasMap);
    }

    public void createAlias(AccessPath original, AccessPath alias) {
        aliasMap.put(alias, original);
    }

    public AccessPath resolveAlias(AccessPath alias) {
        return aliasMap.getOrDefault(alias, alias);
    }

    public boolean hasExprValue() {
        return exprValue != null;
    }
    
    public Domain getExprValue() {
        return exprValue;
    }

    public void setExprValue(Domain other) {
        this.exprValue = other.copyOf();
    }

    public Domain getValueAtPlaceHolderPrefix(String prefix, Domain defaultValue) {
        for (AccessPath accessPath : getValue().getMap().keySet()) {
            if (!(accessPath.getBase() instanceof PlaceHolderAccessPathBase placeHolderAccessPathBase)) {
                continue;
            }

            if (placeHolderAccessPathBase.toString().startsWith(prefix)) {
                return get(accessPath);
            }
        }

        return defaultValue;
    }

    public List<AccessPath> getAccessPathsWithBase(AccessPathBase base) {
        List<AccessPath> result = new ArrayList<>();
        for (AccessPath accessPath : getValue().getMap().keySet()) {
            if (accessPath.getBase().equals(base)) {
                result.add(accessPath);
            }
        }

        return result;
    }

    public List<AccessPath> getAccessPathsWithBasePrefix(String prefix) {
        List<AccessPath> result = new ArrayList<>();
        for (AccessPath accessPath : getValue().getMap().keySet()) {
            if (accessPath.getBase().toString().startsWith(prefix)) {
                result.add(accessPath);
            }
        }

        return result;
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
        return "dataFlow: " + getExprValue().toString() + System.lineSeparator() +
                "access path mapping: " + getValue().toString() + System.lineSeparator() +
                "aliases: " + aliasMap.toString();
    }
}
