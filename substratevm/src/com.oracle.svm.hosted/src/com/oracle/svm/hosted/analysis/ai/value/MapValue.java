package com.oracle.svm.hosted.analysis.ai.value;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a map from variables to abstract values.
 * It is used when we are interested of multiple abstract states during the analysis
 * of a single node, for example:
 * <p>
 * Suppose doing an analysis that is focused on working with mutexes,
 * we can have a map of some unique mutex identifiers to their states (locked, unlocked, etc.)
 *
 * @param <Variable> type of the derived AbstractValue
 * @param <Domain>   type of the derived AbstractDomain
 */
public final class MapValue<
        Variable,
        Domain extends AbstractDomain<Domain>>
        extends AbstractValue<MapValue<Variable, Domain>> {
    private final Map<Variable, Domain> map;

    public MapValue() {
        this.map = new HashMap<>();
    }

    public MapValue(Variable variable, Domain value) {
        this();
        insertBinding(variable, value);
    }

    public void clear() {
        map.clear();
    }

    @Override
    public AbstractValueKind kind() {
        return map.isEmpty() ? AbstractValueKind.TOP : AbstractValueKind.VAL;
    }

    @Override
    public boolean leq(MapValue<Variable, Domain> other) {
        return map.entrySet().stream().allMatch(e -> e.getValue().leq(other.map.get(e.getKey())));
    }

    @Override
    public boolean equals(MapValue<Variable, Domain> other) {
        return map.equals(other.map);
    }

    @Override
    public AbstractValueKind joinWith(MapValue<Variable, Domain> other) {
        return joinLikeOperation(other, Domain::join);
    }

    @Override
    public AbstractValueKind widenWith(MapValue<Variable, Domain> other) {
        return joinLikeOperation(other, Domain::widen);
    }

    @Override
    public AbstractValueKind meetWith(MapValue<Variable, Domain> other) {
        return meetLikeOperation(other, Domain::meet);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MapValue{");
        map.forEach((key, value) -> sb.append(key).append(" -> ").append(value).append(", "));
        sb.append("}");
        return sb.toString();
    }

    private void insertBinding(Variable variable, Domain value) {
        if (value.isBot()) {
            throw new IllegalArgumentException("Bottom value should not be inserted");
        }
        map.put(variable, value);
    }

    private AbstractValueKind joinLikeOperation(MapValue<Variable, Domain> other, JoinOperation<Domain> operation) {
        other.map.forEach((key, value) -> map.merge(key, value, operation::apply));
        return kind();
    }

    private AbstractValueKind meetLikeOperation(MapValue<Variable, Domain> other, MeetOperation<Domain> operation) {
        try {
            other.map.forEach((key, value) -> map.merge(key, value, (v1, v2) -> {
                Domain result = operation.apply(v1, v2);
                if (result.isBot()) {
                    throw new ValueIsBottomException();
                }
                return result;
            }));
        } catch (ValueIsBottomException e) {
            clear();
            return AbstractValueKind.BOT;
        }
        return kind();
    }

    @FunctionalInterface
    private interface JoinOperation<Domain> {
        Domain apply(Domain d1, Domain d2);
    }

    @FunctionalInterface
    private interface MeetOperation<Domain> {
        Domain apply(Domain d1, Domain d2);
    }

    private static class ValueIsBottomException extends RuntimeException {
    }
}