package com.oracle.svm.hosted.analysis.ai.domain.access;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.domain.MapDomain;
import jdk.graal.compiler.graph.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A map domain that maps access paths a common domain ( like intervals, signs, ... )
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class AccessPathMap<Domain extends AbstractDomain<Domain>>
        extends MapDomain<AccessPath, Domain, AccessPathMap<Domain>> {

    public AccessPathMap(Domain initialDomain) {
        super(initialDomain);
    }

    public AccessPathMap(AccessPathMap<Domain> other) {
        super(other);
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

    public void removeAllPaths() {
        super.removeAllKeys();
    }

    /**
     * Some maps will contain access paths of Nodes, we can use this method to retrieve the value
     *
     * @param node         the node we want to retrieve the value for
     * @param defaultValue the default value to return if the node is not found in the map
     * @return the value of the node or the default value
     */
    public Domain getNodeDataValue(Node node, Domain defaultValue) {
        Map<AccessPath, Domain> map = getValue().getMap();
        for (AccessPath path : map.keySet()) {
            if (path.toString().equals(node.toString())) {
                return map.get(path);
            }
        }

        /* This has to be param placeHolderAccessPath value */
        if (!map.isEmpty()) {
            return map.values().iterator().next();
        }

        return defaultValue;
    }

    /**
     * Some maps will only have a single access path with {@link PlaceHolderAccessPathBase}.
     * This method makes it easier to retrieve the only {@link Domain} inside the map.
     *
     * @return the only {@link Domain} in the map or the default value if the map is empty
     */
    public Domain getOnlyDataValue() {
        Map<AccessPath, Domain> map = getValue().getMap();
        return map.values().iterator().next();
    }

    /**
     * Some maps will only have a single access path with {@link PlaceHolderAccessPathBase}.
     * This method makes it easier to retrieve the only access path.
     *
     * @return the only access path in the map
     */
    public AccessPathBase getOnlyAccessPathBase() {
        return getOnlyAccessPath().getBase();
    }

    public AccessPath getOnlyAccessPath() {
        Map<AccessPath, Domain> map = getValue().getMap();
        return map.keySet().iterator().next();
    }

    public Set<AccessPath> getAccessPaths() {
        if (!isVal()) {
            return Collections.emptySet();
        }
        return getValue().getMap().keySet();
    }

    public List<Domain> getDomains() {
        List<Domain> result = new ArrayList<>();
        for (AccessPath accessPath : getValue().getMap().keySet()) {
            result.add(get(accessPath));
        }
        return result;
    }

    public boolean containsAccessPath(AccessPath path) {
        return getValue().getMap().containsKey(path);
    }

    @Override
    public Domain get(AccessPath key) {
        return super.get(key);
    }

    @Override
    public void put(AccessPath key, Domain value) {
        super.put(key, value);
    }

    @Override
    public void remove(AccessPath key) {
        super.remove(key);
    }

    @Override
    public AccessPathMap<Domain> copyOf() {
        return new AccessPathMap<>(this);
    }

    @Override
    public String toString() {
        if (isBot()) {
            return "AccessPathMap: ⊥";
        }
        if (isTop()) {
            return "AccessPathMap: ⊤";
        }
        return getValue().toString();
    }
}
